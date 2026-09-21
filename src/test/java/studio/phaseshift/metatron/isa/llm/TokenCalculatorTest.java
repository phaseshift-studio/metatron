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
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.llm.type.feature.AbstractMessageFeature;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-chat usage listener: sums what the provider reports over the chat's
 * model calls (a tool loop is several calls), ignores a call that reports
 * nothing, and starts fresh on reset.
 */
public class TokenCalculatorTest extends AbstractMetatronTest {

    private static ChatRequest chatRequest() {
        return ChatRequest.builder().messages(UserMessage.from("the question")).build();
    }

    private static ChatModelResponseContext call(final int input, final int output) {
        final ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("a reply"))
                .tokenUsage(new TokenUsage(input, output, input + output))
                .build();
        return new ChatModelResponseContext(response, chatRequest(), ModelProvider.OLLAMA, Map.of());
    }

    private static ChatModelResponseContext callWithNoUsage() {
        final ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from("a reply")).build();
        return new ChatModelResponseContext(response, chatRequest(), ModelProvider.OLLAMA, Map.of());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "40% 10% 0% 0% % a single call is reported as-is",
            "40% 10% 8% 2%  % the second call adds to the first, the tool loop is many calls",
            "0% 0% 12% 5%   % a zero first call still leaves a later call intact"
    })
    void accumulatesAcrossModelCalls(final int firstIn, final int firstOut, final int secondIn, final int secondOut, final String desc) {
        final TokenCalculator calculator = new TokenCalculator();
        calculator.onResponse(call(firstIn, firstOut));
        calculator.onResponse(call(secondIn, secondOut));
        assertEquals(firstIn + secondIn, calculator.inputTokens(), desc);
        assertEquals(firstOut + secondOut, calculator.outputTokens(), desc);
        assertEquals(firstIn + secondIn + firstOut + secondOut, calculator.totalTokens(), desc);
    }

    @Test
    void aCallThatReportsNoUsageIsIgnored() {
        final TokenCalculator calculator = new TokenCalculator();
        calculator.onResponse(call(40, 10));
        calculator.onResponse(callWithNoUsage());
        calculator.onResponse(call(5, 1));
        assertEquals(45, calculator.inputTokens(), "an unreported usage neither zeros nor shifts the count");
        assertEquals(11, calculator.outputTokens(), "an unreported usage neither zeros nor shifts the count");
    }

    @Test
    void resetStartsAFreshChat() {
        final TokenCalculator calculator = new TokenCalculator();
        calculator.onResponse(call(40, 10));
        calculator.reset();
        assertEquals(0, calculator.inputTokens(), "reset clears the input count");
        assertEquals(0, calculator.outputTokens(), "reset clears the output count");
        calculator.onResponse(call(7, 3));
        assertEquals(7, calculator.inputTokens(), "after reset only the new chat's usage counts");
    }

    // ── estimated input composition (onRequest) ─────────────────────

    private static final SystemMessage SYSTEM = SystemMessage.from("you are a careful helper");
    private static final UserMessage USER = UserMessage.from("the question with many words in it");
    private static final AiMessage AI = AiMessage.from("the answer with some words");
    private static final ToolExecutionResultMessage TOOL_RESULT = ToolExecutionResultMessage.from("call-1", "probe", "the tool result text");

    private static int estimated(final ChatMessage message) {
        return AbstractMessageFeature.DefaultTokenCountEstimator.singleton().estimateTokenCountInMessage(message);
    }

    private static ChatModelRequestContext requestWith(final ChatMessage... messages) {
        final ChatRequest request = ChatRequest.builder().messages(messages).build();
        return new ChatModelRequestContext(request, ModelProvider.OLLAMA, Map.of());
    }

    @Test
    void estimatesEachMessageKind() {
        final TokenCalculator calculator = new TokenCalculator();
        calculator.onRequest(requestWith(SYSTEM, USER, AI, TOOL_RESULT));
        final Map<String, Long> est = calculator.estimates();
        assertEquals(4, est.size(), "one entry per message kind: " + est);
        assertEquals(estimated(SYSTEM), (long) est.get("system"), "the system share");
        assertEquals(estimated(USER), (long) est.get("user"), "the user share");
        assertEquals(estimated(AI), (long) est.get("ai"), "the ai share");
        assertEquals(estimated(TOOL_RESULT), (long) est.get("tool"), "the tool-result share, under the ledger's tool name");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "1 % a single request measures once",
            "3 % a tool loop re-sending the same window must not multiply the fill"
    })
    void estimatesTrackTheCurrentRequest(final int calls, final String desc) {
        final TokenCalculator calculator = new TokenCalculator();
        for (int i = 0; i < calls; i++)
            calculator.onRequest(requestWith(SYSTEM, USER));
        final Map<String, Long> est = calculator.estimates();
        assertEquals((long) estimated(SYSTEM), (long) est.get("system"), desc);
        assertEquals((long) estimated(USER), (long) est.get("user"), desc);
        assertEquals(0L, (long) est.getOrDefault("ai", 0L), desc);
    }

    @Test
    void estimationTracksTheGrownRequest() {
        // the tool loop grows the context: the est of the larger, later
        // request is the one that stands — the earlier fill is replaced,
        // not summed
        final TokenCalculator calculator = new TokenCalculator();
        calculator.onRequest(requestWith(SYSTEM, USER));
        calculator.onRequest(requestWith(SYSTEM, USER, AI, TOOL_RESULT));
        final Map<String, Long> est = calculator.estimates();
        assertEquals(4, est.size(), "the later request's kinds: " + est);
        assertEquals((long) estimated(TOOL_RESULT), (long) est.get("tool"), "the grown tool share, under the ledger's tool name");
        assertEquals((long) estimated(SYSTEM), (long) est.get("system"), "the unchanged system share is re-measured, not doubled");
    }

    @Test
    void resetClearsTheEstimates() {
        final TokenCalculator calculator = new TokenCalculator();
        calculator.onRequest(requestWith(SYSTEM, USER));
        calculator.onResponse(call(40, 10));
        calculator.reset();
        assertTrue(calculator.estimates().isEmpty(), "reset drops the previous turn's composition");
        assertEquals(0, calculator.inputTokens(), "reset clears the usage too");
    }
}
