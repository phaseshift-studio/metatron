/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.llm;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Real;
import studio.phaseshift.metatron.isa.m.type.Rec;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CostCalculator implements ChatModelListener, EmbeddingModelListener {
    private static final double MILLION = 1_000_000.0;

    /**
     * Costs are configured per-million tokens; normalize to per-token at construction.
     */
    private final double costPerInputToken;
    private final double costPerOutputToken;
    private double inputCost = 0;
    private double outputCost = 0;
    private final fURI currencyTID;

    public CostCalculator(final Real inRate, final Real outRate) {
        this.costPerInputToken = inRate.realValue() / MILLION;
        this.costPerOutputToken = outRate.realValue() / MILLION;
        this.currencyTID = inRate.typeId();
    }

    public void setCost(final double inCost, final double outCost) {
        this.inputCost = inCost;
        this.outputCost = outCost;
    }

    private void updateCosts(final TokenUsage tokenUsage) {
        if (tokenUsage != null) {
            final int inputTokens = tokenUsage.inputTokenCount() != null ? tokenUsage.inputTokenCount() : 0;
            final int outputTokens = tokenUsage.outputTokenCount() != null ? tokenUsage.outputTokenCount() : 0;

            this.inputCost += inputTokens * costPerInputToken;
            this.outputCost += outputTokens * costPerOutputToken;
        }
    }

    @Override
    public void onResponse(final ChatModelResponseContext responseContext) {
        this.updateCosts(responseContext.chatResponse().tokenUsage());

    }

    @Override
    public void onResponse(final EmbeddingModelResponseContext responseContext) {
        this.updateCosts(responseContext.response().tokenUsage());
    }

    public Real getInputCost() {
        return real(this.inputCost, this.currencyTID, null);
    }

    public Real getOutputCost() {
        return real(this.outputCost, this.currencyTID, null);
    }

    public Real getTotalCost() {
        return real(this.inputCost + this.outputCost, this.currencyTID, null);
    }

    public Rec getCost() {
        return rec(IN, this.getInputCost(), OUT, this.getOutputCost(), TOTAL, this.getTotalCost());
    }

    public fURI getCurrencyTID() {
        return this.currencyTID;
    }
}