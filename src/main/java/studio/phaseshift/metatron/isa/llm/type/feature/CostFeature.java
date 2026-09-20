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
import studio.phaseshift.metatron.isa.llm.type.ChatFrame;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;

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
    public static final fURI FEATURE_TID = studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_COST_FEATURE_TID;
    private final CostCalculator calculator;

    public CostFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.calculator = new CostCalculator(this.at(f(RATE).extend(IN)), this.at(f(RATE).extend(OUT)));
    }

    public CostCalculator getCalculator() {
        return this.calculator;
    }


    @Override
    public void onCompleteResponse(final Agent agent, final ChatFrame result) {
        result.putRef("cost", persistCost(agent));
    }

    @Override
    public void onError(final Agent agent, final Fail fail) {
        // Finalize cost even on error — whatever accumulated is still useful
        persistCost(agent);

    }

    /**
     * Read cost data from the Agent blackboard and persist to space.
     * The blackboard is populated by Agent.chat() Phase 3 right before
     * feature hooks fire, so features can read it here.
     */
    public Rec persistCost(final Agent agent) {
        final Rec cost = Router.writeToSpace(this.getRoot(agent).extend("_").addQ(INCRQ), agent.getChatPath().toRec().plus(this.calculator.getCost())).as();
        LOG.status(DEBUG, "💰 cost total: %.4f [in: %.4f out: %.4f] (%s)", cost.at(TOTAL).realValue(), cost.at(IN).realValue(), cost.at(OUT).realValue(), this.calculator.getCurrencyTID().name());
        if (this.has(TO)) this.at(TO).asInst().args(cost).apply(cost);
        return cost;
    }
}
