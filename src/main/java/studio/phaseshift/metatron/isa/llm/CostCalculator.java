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
import studio.phaseshift.metatron.isa.m.type.Rec;

import static studio.phaseshift.metatron.Tokens.IN;
import static studio.phaseshift.metatron.Tokens.OUT;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CostCalculator implements ChatModelListener, EmbeddingModelListener {
    private final double costPerInputToken;
    private final double costPerOutputToken;
    private double totalCost = 0;

    public CostCalculator(final Rec cost) {
        this.costPerInputToken = cost.at(IN).asReal().realValue();
        this.costPerOutputToken = cost.at(OUT).asReal().realValue();
    }

    @Override
    public void onResponse(final ChatModelResponseContext responseContext) {
        TokenUsage tokenUsage = responseContext.chatResponse().tokenUsage();
        if (tokenUsage != null) {
            int inputTokens = tokenUsage.inputTokenCount() != null ? tokenUsage.inputTokenCount() : 0;
            int outputTokens = tokenUsage.outputTokenCount() != null ? tokenUsage.outputTokenCount() : 0;

            double callCost = (inputTokens * costPerInputToken) + (outputTokens * costPerOutputToken);
            totalCost += callCost;
        }
    }
    
    @Override
    public void onResponse(final EmbeddingModelResponseContext responseContext) {
        TokenUsage tokenUsage = responseContext.response().tokenUsage();
        if (tokenUsage != null) {
            int inputTokens = tokenUsage.inputTokenCount() != null ? tokenUsage.inputTokenCount() : 0;
            int outputTokens = tokenUsage.outputTokenCount() != null ? tokenUsage.outputTokenCount() : 0;

            double callCost = (inputTokens * costPerInputToken) + (outputTokens * costPerOutputToken);
            totalCost += callCost;
        }
    }   

    public double getTotalCost() {
        return totalCost;
    }
}