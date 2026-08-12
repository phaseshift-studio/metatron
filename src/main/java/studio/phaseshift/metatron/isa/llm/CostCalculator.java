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

    public CostCalculator(final Double inRate, final Double outRate, final fURI currencyTID) {
        this.costPerInputToken = inRate / MILLION;
        this.costPerOutputToken = outRate / MILLION;
        this.currencyTID = currencyTID;
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

    public double getInputCost() {
        return this.inputCost;
    }

    public double getOutputCost() {
        return this.outputCost;
    }

    public double getTotalCost() {
        return this.inputCost + this.outputCost;
    }

    public fURI getCurrencyTID() {
        return this.currencyTID;
    }
}