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

package studio.phaseshift.metatron.isa.llm.type.feature;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.TokenCalculator;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatFrame;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.ALGORITHM;
import static studio.phaseshift.metatron.Tokens.EST;
import static studio.phaseshift.metatron.Tokens.IN;
import static studio.phaseshift.metatron.Tokens.MAX;
import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.Tokens.ROOT;
import static studio.phaseshift.metatron.Tokens.SESSION;
import static studio.phaseshift.metatron.Tokens.TOKEN;
import static studio.phaseshift.metatron.Tokens.OUT;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_TOKEN_MESSAGE_FEATURE_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The token-windowed message provider reports a chat's provider-reported usage:
 * {@code onCompleteResponse} writes the input/output token counts the
 * {@code LLMFactory}-attached listener accumulated, and {@code onBeforeChat}
 * starts every chat from zero.
 */
public class TokenMessageFeatureTest extends AbstractFeatureTest {

    private static final fURI SESSION_VID = f("/usr/test/tokmsg/session/1");

    @BeforeAll
    public static void seedSessionPolicy() {
        // the free onBeforeChat tests run without a chat, so the session policy
        // row must already stand in this class's test memSpace
        Router.writeToSpace(SESSION_VID, AbstractMessageFeature.createSession("tokmsg", "default", "token_window", 50));
    }

    @Override
    protected TokenMessageFeature feature() {
        return new TokenMessageFeature(mutableMap(
                uri(NAME), str("tokmsg"),
                uri(ROOT), uri("/usr/test/tokmsg"),
                uri(SESSION), uri("/usr/test/tokmsg/session/1"),
                uri(ALGORITHM), rec(mutableMap(uri(NAME), uri("token_window"), uri(MAX), jnt(50))))
                , LLM_TOKEN_MESSAGE_FEATURE_TID, null);
    }

    /**
     * One model call that reported usage — the same call the listener sees
     * in the response stream.
     */
    private static final SystemMessage SYSTEM = SystemMessage.from("you are a careful helper");
    private static final UserMessage USER = UserMessage.from("the question");

    /**
     * One model call — the request this side sent, then the response the
     * provider reported — in the order the listener sees them.
     */
    private static void chatOnce(final TokenCalculator calculator, final int input, final int output,
                                 final ChatMessage... sent) {
        final ChatRequest request = ChatRequest.builder().messages(sent).build();
        calculator.onRequest(new ChatModelRequestContext(request, ModelProvider.OLLAMA, Map.of()));
        final ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("a reply"))
                .tokenUsage(new TokenUsage(input, output, input + output))
                .build();
        calculator.onResponse(new ChatModelResponseContext(response, request, ModelProvider.OLLAMA, Map.of()));
    }

    @Test
    void completionReportsUsageAccumulatedOverTheChatModelCalls() {
        final TokenMessageFeature feature = feature();
        final Agent agent = agentWith(feature);
        chatOnce(feature.tokenCalculator(), 40, 10, SYSTEM, USER); // the user turn
        chatOnce(feature.tokenCalculator(), 8, 2, SYSTEM, USER);   // the tool-loop follow-up re-sends its window
        final ChatFrame result = chatResultOf("the answer", "the question");
        feature.onCompleteResponse(agent, result);
        final Rec token = result.at(uri(TOKEN));
        assertTrue(token.isRec(), "the chat_result carries the token info under the token key");
        assertEquals(48, token.asRec().at(uri(IN)).intValue().intValue(), "token.in sums the chat's model calls");
        assertEquals(12, token.asRec().at(uri(OUT)).intValue().intValue(), "token.out sums the chat's model calls");
    }

    @Test
    void completionEstimatesTheInputCompositionByMessageKind() {
        final TokenMessageFeature feature = feature();
        chatOnce(feature.tokenCalculator(), 40, 10, SYSTEM, USER);
        chatOnce(feature.tokenCalculator(), 8, 2, SYSTEM, USER); // re-sends its window — est follows the latest request
        final ChatFrame result = chatResultOf("the answer", "the question");
        feature.onCompleteResponse(agentWith(feature), result);
        final Rec est = result.at(uri(TOKEN)).asRec().at(uri(EST)).asRec();
        assertTrue(est.isRec(), "the est breakdown sits under the token key");
        assertEquals(estOf(SYSTEM), est.asRec().at(uri("system")).intValue().intValue(),
                "est.system is the estimator over the latest request's system message, not a sum over the chat");
        assertEquals(estOf(USER), est.asRec().at(uri("user")).intValue().intValue(),
                "est.user is the estimator over the latest request's user message, not a sum over the chat");
    }

    private static int estOf(final ChatMessage message) {
        return AbstractMessageFeature.DefaultTokenCountEstimator.singleton().estimateTokenCountInMessage(message);
    }

    @Test
    void aChatWithNoReportedUsageCarriesNoTokenInfo() {
        final TokenMessageFeature feature = feature();
        final ChatFrame result = chatResultOf("ok", "hi");
        feature.onCompleteResponse(agentWith(feature), result);
        assertTrue(result.at(uri(TOKEN)).isNoObj(), "a zero must not be reported as usage");
    }

    @Test
    void onBeforeChatStartsTheNextChatFresh() {
        final TokenMessageFeature feature = feature();
        final Agent agent = agentWith(feature);
        chatOnce(feature.tokenCalculator(), 40, 10, SYSTEM, USER); // the previous turn
        assertTrue(feature.onBeforeChat(agent).isNoObj(), "onBeforeChat must not short-circuit");
        assertEquals(0, feature.tokenCalculator().inputTokens(), "the previous turn's usage must not leak into the new chat");
        assertEquals(0, feature.tokenCalculator().outputTokens(), "the previous turn's usage must not leak into the new chat");
    }

    @Test
    void calculatorForFindsTheMessageFeaturesAccumulator() {
        final TokenMessageFeature feature = feature();
        final Agent agent = agentWith(feature);
        final Optional<TokenCalculator> found = AbstractMessageFeature.calculatorFor(agent);
        assertEquals(feature.tokenCalculator(), found.orElseThrow(), "the very calculator instance the model will be attached to");
    }

    @Test
    void calculatorForIsEmptyWithoutAMessageFeature() {
        assertTrue(AbstractMessageFeature.calculatorFor(agentDummy()).isEmpty(), "no message feature, nothing to report");
    }
}
