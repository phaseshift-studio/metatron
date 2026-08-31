/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for the terms of the License.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.CostCalculator;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatResult;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Real;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SESSION_FEATURE_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Tracks LLM cost during chat using real token data from {@link CostCalculator}.
 * Pricing is configured on this feature itself (not the model):
 *
 * <pre>
 * cost_feature::[root             =&gt; /usr/dr/cost,
 *                 cost =&gt; [in_cost  =&gt; usd_currency::0.065,
 *                           out_cost =&gt; usd_currency::0.001]]
 * </pre>
 * <p>
 * During {@code onBeforeChat} it creates a {@link CostCalculator} from its own
 * pricing config and stores it on the Agent.  The calculator is wired into
 * LangChain4j by {@code LLMFactory.createChatInteraction}, accumulates real
 * token costs during streaming, and {@code onCompleteResponse} writes the
 * final totals to space at {@code root/in}, {@code root/out}, {@code root/total}.
 */
public class CostFeature extends AbstractFeature {

    private static final fURI ROOT = f("root");
    private final fURI currencyTID;
    private final CostCalculator calculator;
    private fURI sessionVID;
    /**
     * The cost row written for the current chat — attached to the chat_result as a ref.
     */
    private Obj lastCost;

    private record Cost(Real in, Real out, Real total) {
    }

    public CostFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.currencyTID = this.at(f(RATE).extend(IN)).orElse(real(0.0)).tid();
        this.calculator = new CostCalculator(this.at(f(RATE).extend(IN)).realValue(), this.at(f(RATE).extend(OUT)).realValue(), this.currencyTID);
    }

    @Override
    public void onAgentCtor(final Agent agent) {
        // Create calculator and store on Agent; LLMFactory will pick it up
        Router.readFromSpace(this.at(ROOT).uriValue().extend("+")).stream().filter(x -> x.asRec().has(SESSION)).filter(x -> x.asRec().at(SESSION).uriValue().equals(this.sessionVID)).findFirst().orElse(rec());
        this.calculator.setCost(this.at(f(COST).extend(IN)).orElse(real(0.0)).realValue(), this.at(f(COST).extend(IN)).orElse(real(0.0)).realValue());
        this.sessionVID = agent.feature(LLM_SESSION_FEATURE_TID).orElse(rec()).at(SESSION).orElse(uri("")).uriValue();
        agent.costCalculator().set(this.calculator);
    }


    @Override
    public void onCompleteResponse(final Agent agent, final ChatResult result) {
        final Cost cost = persistCost();
        result.putRef("cost", this.lastCost);
        LOG.debug("running cost: %s => %s", cost, this.at(TO));
        if (!this.at(TO).isNoObj())
            this.at(TO).asInst().args(lst(cost.in(), cost.out(), cost.total())).apply(jnt(1));
    }

    @Override
    public void onError(final Agent agent, final Fail fail) {
        // Finalize cost even on error — whatever accumulated is still useful
        final Cost cost = persistCost();
        if (!this.at(TO).isNoObj())
            this.at(TO).asInst().args(lst(cost.in(), cost.out(), cost.total())).apply(jnt(1));
    }

    /**
     * Read cost data from the Agent blackboard and persist to space.
     * The blackboard is populated by Agent.chat() Phase 3 right before
     * feature hooks fire, so features can read it here.
     */
    public Cost persistCost() {
        final fURI root = this.at(ROOT).orThrow("no cost_feature root provided").uriValue();
        final Real inCost = real(this.calculator.getInputCost(), this.currencyTID, null);
        final Real outCost = real(this.calculator.getOutputCost(), this.currencyTID, null);
        final Real totalCost = real(this.calculator.getTotalCost(), this.currencyTID, null);
        try {
            // Write in/out/total to space so other features (e.g., AuditFeature) can read it
            this.lastCost = Router.writeToSpace(this.at(ROOT).uriValue().extend("_").addQ(INCRQ), rec(uri(SESSION), uri(this.sessionVID), uri(TIME), mathInstSet.nowDatetime(), uri(IN), inCost, uri(OUT), outCost, uri(TOTAL), totalCost));
            LOG.debug("persisted cost to %s: in=%.4f, out=%.4f, total=%.4f", root.toString(), inCost.realValue(), outCost.realValue(), totalCost.realValue());
        } catch (final Exception e) {
            LOG.warn("failed to persist cost data: %s", e.getMessage());
        }
        return new Cost(inCost, outCost, totalCost);
    }
}
