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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.output.TokenUsage;
import studio.phaseshift.metatron.isa.llm.type.feature.AbstractMessageFeature;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.AI;
import static studio.phaseshift.metatron.Tokens.SYSTEM;
import static studio.phaseshift.metatron.Tokens.USER;

/**
 * The per-chat token accounting: two data streams, deliberately kept apart.
 *
 * <ul>
 * <li><b>Actual usage</b> — {@link #inputTokens()} / {@link #outputTokens()}:
 * the provider-reported {@link TokenUsage} of every model call the chat makes.
 * A tool loop is several calls, and the final {@code ChatResponse} carries
 * only the last call's usage, so the total exists only in the per-call stream,
 * which is why this is a {@link ChatModelListener} rather than a read at the
 * end of the chat.</li>
 * <li><b>Estimated composition</b> — {@link #estimates()}: the
 * {@code AbstractMessageFeature.DefaultTokenCountEstimator} applied to each
 * message of every request, keyed by message kind.  No provider reports a
 * per-message breakdown, so the only place one can exist is the request
 * itself — this listener sees it in {@code onRequest}.  The per-kind numbers
 * are heuristic (chars/4) and do NOT sum to the provider's {@code in}: the
 * provider bills the rendered prompt, which carries overhead this side never
 * sees.</li>
 * </ul>
 *
 * <p>Both streams are per-chat by design, unlike {@link CostCalculator}
 * (a running cost): {@code AbstractMessageFeature.onBeforeChat} resets this
 * at the start of each turn, and its {@code onCompleteResponse} reports
 * whatever the turn accumulated — actual in/out and est by kind.
 */
public class TokenCalculator implements ChatModelListener {

    /**
     * The kind fallback for message types this listener does not name.
     */
    public static final String OTHER = "other";

    /**
     * The message-result kind the request streams carry.
     */
    public static final String TOOL_RESULT = "tool_result";

    private long inputTokens = 0;
    private long outputTokens = 0;
    private final Map<String, Long> estimates = new LinkedHashMap<>();

    @Override
    public void onRequest(final ChatModelRequestContext requestContext) {
        final ChatRequest request = requestContext.chatRequest();
        if (null == request || null == request.messages())
            return;
        for (final ChatMessage message : request.messages()) {
            final long est = AbstractMessageFeature.DefaultTokenCountEstimator.singleton()
                    .estimateTokenCountInMessage(message);
            this.estimates.merge(kindOf(message), est, Long::sum);
        }
    }

    @Override
    public void onResponse(final ChatModelResponseContext responseContext) {
        final TokenUsage usage = responseContext.chatResponse().tokenUsage();
        if (null != usage) {
            if (null != usage.inputTokenCount())
                this.inputTokens += usage.inputTokenCount();
            if (null != usage.outputTokenCount())
                this.outputTokens += usage.outputTokenCount();
        }
    }

    /**
     * Begin a fresh chat: drop what the previous turn accumulated.
     */
    public void reset() {
        this.inputTokens = 0;
        this.outputTokens = 0;
        this.estimates.clear();
    }

    /**
     * The message kind a request message belongs to — the ledger's own
     * message names, so the est keys read back onto the message vocabulary.
     */
    private static String kindOf(final ChatMessage message) {
        if (message instanceof SystemMessage)
            return SYSTEM;
        if (message instanceof UserMessage)
            return USER;
        if (message instanceof AiMessage)
            return AI;
        if (message instanceof ToolExecutionResultMessage)
            return TOOL_RESULT;
        return OTHER;
    }

    public long inputTokens() {
        return this.inputTokens;
    }

    public long outputTokens() {
        return this.outputTokens;
    }

    public long totalTokens() {
        return this.inputTokens + this.outputTokens;
    }

    /**
     * The estimated input composition of the chat so far: estimated tokens by
     * message kind, accumulated over every request the chat made, in encounter
     * order.  Estimates only — see the class javadoc for why they will not
     * sum to the provider's input count.
     */
    public Map<String, Long> estimates() {
        return new LinkedHashMap<>(this.estimates);
    }
}
