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

package studio.phaseshift.metatron.isa.llm.space;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.feature.MessageFeature;
import studio.phaseshift.metatron.isa.llm.type.feature.ToolFeature;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.incrQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * The tool request/result pairing invariant of the message ledger.
 *
 * <p>LangChain4j adds an {@code AiMessage} carrying {@code tool_requests} to
 * the chat memory <em>before</em> it executes the tools ({@code ToolService}
 * adds the ai message, then runs each executor, then appends each result), so
 * the memory list is legitimately short of results the first time the store
 * sees a tool-calling turn.  Writing the message then is the corruption this
 * class guards against: an assistant message with {@code tool_calls} whose
 * tool messages never follow.</p>
 *
 * <p>The guarantee: the ai message reaches the ledger only once every request
 * has a {@code tool_result}, and its results are written immediately after it,
 * in request order.  The sequence simulated here is exactly LangChain4j's:</p>
 *
 * <ol>
 *   <li>{@code chatMemory.add(aiMessage)} → {@code updateMessages} with the ai
 *       message and no results — nothing may be written,</li>
 *   <li>each tool executes ({@code onToolExecuted} stages its result) and its
 *       result message is appended to the memory → {@code updateMessages}
 *       again per result — still nothing while any request is unanswered,</li>
 *   <li>the last result lands → the ai message and every result are written as
 *       one group.</li>
 * </ol>
 */
public class ToolRequestResultPairingTest extends AbstractMetatronTest {

    private static final fURI TEST_SPACE = f("/usr/test");

    @BeforeAll
    public static void mountLedgerSpace() {
        InstSet.importInstSet(f("/m/llm"));
        InstSet.importInstSet(MATH_ISA_TID);
        memSpace.of(f("/usr/test/#"), f("/sys/space/usr/test")).addQ(incrQ());
    }

    // ── Rig ─────────────────────────────────────────────────────────

    /**
     * A tool-calling agent — a tool channel (the pairing owner, optional) and
     * a message feature (its session is the ledger scope) — under the
     * scenario's own ledger root, so scenarios never share a ledger.
     */
    private static Agent pairingAgent(final String scenario, final ToolFeature toolFeature) {
        final Rec algorithm = rec(mutableMap(uri(NAME), uri("message_window"), uri(MAX), jnt(50)));
        final MessageFeature messageFeature = new MessageFeature(mutableMap(
                uri(SESSION), uri(sessionVID(scenario).toString()),
                uri(ALGORITHM), algorithm), LLM_MESSAGE_FEATURE_TID, null);
        final List<Obj> features = new ArrayList<>();
        features.add(messageFeature);
        if (null != toolFeature)
            features.add(toolFeature);
        return Agent.agent(rec(mutableMap(
                uri(NAME), str("pairing-agent"),
                uri(ROOT), uri(agentRoot(scenario).toString()),
                uri(FEATURE), lst(features)), LLM_AGENT_TID, null));
    }

    private static fURI agentRoot(final String scenario) {
        return TEST_SPACE.extend("pairing").extend(scenario);
    }

    private static fURI sessionVID(final String scenario) {
        return agentRoot(scenario).extend(SESSION).extend("1");
    }

    /**
     * The store LC4j drives — built exactly as a chat builds it
     * ({@code MessageFeature.onBeforeChat} creates the session and the store,
     * and the tool channel resolves the same instance to close a turn), so the
     * rig exercises production wiring rather than a stand-in.
     */
    private static SpaceChatSessionStore store(final Agent agent) {
        final MessageFeature messageFeature = agent.feature(LLM_MESSAGE_FEATURE_TID).<MessageFeature>as();
        messageFeature.onBeforeChat(agent);
        return messageFeature.store();
    }

    /** Every ledger entry of the scenario, oldest → newest (append order). */
    private static List<Rec> ledger(final String scenario) {
        return Router.readFromSpace(agentRoot(scenario).extend(MESSAGE).extend("+/"))
                .stream()
                .map(Obj::asRel)
                .sorted(Comparator.comparing(rel -> Integer.parseInt(rel.first().uriValue().name())))
                .map(rel -> rel.second().asRec())
                .toList();
    }

    private static AiMessage aiCalling(final int toolCalls) {
        final List<ToolExecutionRequest> requests = new ArrayList<>();
        for (int i = 0; i < toolCalls; i++)
            requests.add(ToolExecutionRequest.builder()
                    .id("call-" + i)
                    .name("tool_" + i)
                    .arguments("{\"n\":" + i + "}")
                    .build());
        return AiMessage.from("calling %d tools".formatted(toolCalls), requests);
    }

    /** A tool result as the agent dispatches it to {@code onToolExecuted}. */
    private static Rec stagedResult(final int i) {
        return rec(uri(NAME), str("tool_" + i),
                uri(TOOL_ARGUMENTS), str("{\"n\":" + i + "}"),
                uri(RESULT), str("value " + i),
                uri(CONTENTS), str("call-" + i));
    }

    private static List<String> toolCallIds(final Rec aiMessage) {
        return aiMessage.at(uri(TOOL_REQUESTS)).asLst().elements()
                .map(request -> request.asRec().at(uri(CONTENTS)).strValue())
                .toList();
    }

    private static List<String> expectedCallIds(final int toolCalls) {
        return IntStream.range(0, toolCalls).mapToObj(i -> "call-" + i).toList();
    }

    // ── The invariant ───────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource(value = {
            // toolCalls % stagedByToolChannel % scenario     % desc
            "1  % true               % one-staged   % a single request waits for the single result",
            "2  % true               % two-staged   % no request is written ahead of the last result",
            "3  % true               % three-staged % a three-call turn lands whole",
            "1  % false              % one-memory   % the result exists only in the memory list",
            "2  % false              % two-memory   % two results exist only in the memory list",
            "3  % false              % three-memory % three results exist only in the memory list"
    }, delimiter = '%')
    void aiMessageWithToolRequestsWaitsForItsResults(final int toolCalls, final boolean stagedByToolChannel,
                                                     final String scenario, final String desc) {
        final ToolFeature toolFeature = new ToolFeature(new LinkedHashMap<>(), LLM_TOOL_FEATURE_TID, null);
        final Agent agent = pairingAgent(scenario, toolFeature);
        final SpaceChatSessionStore store = store(agent);
        final List<ChatMessage> memory = new ArrayList<>(List.of(UserMessage.from("run the tools"), aiCalling(toolCalls)));

        // 1. langchain4j: the ai message enters the memory BEFORE the tools run
        store.updateMessages(sessionVID(scenario), memory);
        assertEquals(0, ledger(scenario).size(),
                "an ai message with unanswered tool_requests must never reach the ledger: " + desc);

        // 2. the tool loop: tool i executes, result i is appended to the memory
        for (int i = 0; i < toolCalls; i++) {
            if (stagedByToolChannel)
                toolFeature.onToolExecuted(agent, stagedResult(i));
            memory.add(ToolExecutionResultMessage.from("call-" + i, "tool_" + i, "value " + i));
            store.updateMessages(sessionVID(scenario), memory);
            if (i < toolCalls - 1)
                assertEquals(0, ledger(scenario).size(),
                        "the ai message must wait for every result, not just the first: " + desc);
        }

        // 3. the group landed whole: ai message first, then its results in request order
        final List<Rec> ledger = ledger(scenario);
        assertEquals(toolCalls + 1, ledger.size(), desc);
        assertEquals(AI_MESSAGE_TID, ledger.getFirst().tid(), desc);
        assertEquals(expectedCallIds(toolCalls), toolCallIds(ledger.getFirst()), desc);
        for (int i = 0; i < toolCalls; i++) {
            assertEquals(TOOL_RESULT_MESSAGE_TID, ledger.get(i + 1).tid(),
                    "result %d must follow the ai message directly: %s".formatted(i, desc));
            assertEquals("call-" + i, ledger.get(i + 1).at(uri(CONTENTS)).strValue(), desc);
            assertEquals("value " + i, ledger.get(i + 1).at(uri(TEXT)).strValue(), desc);
        }
        assertEquals(0, ToolPairGate.parked(store), "a completed group leaves nothing parked: " + desc);
    }

    // ── Idempotence ─────────────────────────────────────────────────

    @Test
    void aPublishedGroupIsNeverWrittenTwice() {
        final ToolFeature toolFeature = new ToolFeature(new LinkedHashMap<>(), LLM_TOOL_FEATURE_TID, null);
        final Agent agent = pairingAgent("idempotent", toolFeature);
        final SpaceChatSessionStore store = store(agent);
        final List<ChatMessage> memory = new ArrayList<>(List.of(UserMessage.from("run the tools"), aiCalling(2)));

        toolFeature.onToolExecuted(agent, stagedResult(0));
        toolFeature.onToolExecuted(agent, stagedResult(1));
        memory.add(ToolExecutionResultMessage.from("call-0", "tool_0", "value 0"));
        memory.add(ToolExecutionResultMessage.from("call-1", "tool_1", "value 1"));
        store.updateMessages(sessionVID("idempotent"), memory);
        assertEquals(3, ledger("idempotent").size(), "the ai message and both of its results");

        // the same memory list offered again — the read side stamps _w, the
        // write side knows the group, neither may duplicate the ledger
        store.updateMessages(sessionVID("idempotent"), memory);
        assertEquals(3, ledger("idempotent").size(), "a republished group must not duplicate the ledger");
    }

    // ── The scope a group is written in ─────────────────────────────

    /**
     * A group is one turn, so it is written in one scope.
     *
     * <p>Its results are staged while their tools run, and a slow tool outlives the
     * turn: an interrupted turn unwinds to the idle depth ({@code 0}) before the last
     * results land, so a result stamped on arrival carries a depth its own request
     * does not.  The store projects the ledger per session/depth, so such a group is
     * <em>torn</em> in the model's window — the assistant message keeps its
     * {@code tool_calls} and loses the tool messages that answer them, and every
     * later chat is rejected with "insufficient tool messages following tool_calls
     * message".  Measured on the drstynx ledger: two results staged at depth 0
     * against a request at depth 1 made 1048 otherwise valid rows unreadable.
     */
    @Test
    void aGroupIsWrittenInTheScopeOfItsOwnRequest() {
        final ToolFeature toolFeature = new ToolFeature(new LinkedHashMap<>(), LLM_TOOL_FEATURE_TID, null);
        final Agent agent = pairingAgent("scoped", toolFeature);
        final SpaceChatSessionStore store = store(agent);
        final String session = sessionVID("scoped").toString();

        final Rec request = rec(mutableMap(
                        uri(TEXT), str("calling a tool"),
                        uri(SESSION), uri(session),
                        uri(DEPTH), jnt(1),
                        uri(CHAT_ID), jnt(1),
                        uri(TOOL_REQUESTS), lst(rec(mutableMap(
                                uri(NAME), uri("tool_0"),
                                uri(CONTENTS), str("call-0"),
                                uri(TEXT), str("tool_0({})")), TOOL_REQUEST_MESSAGE_TID, null))),
                AI_MESSAGE_TID, null);
        // staged after the turn unwound: the depth the agent is at, not the turn's
        final Rec late = rec(mutableMap(
                        uri(NAME), uri("tool_0"),
                        uri(CONTENTS), str("call-0"),
                        uri(TEXT), str("value 0"),
                        uri(SESSION), uri(session),
                        uri(DEPTH), jnt(0),
                        uri(CHAT_ID), jnt(1)),
                TOOL_RESULT_MESSAGE_TID, null);

        ToolPairGate.offer(store, request, Map.of());
        ToolPairGate.stage(store, "call-0", late);

        final List<Rec> ledger = ledger("scoped");
        assertEquals(2, ledger.size(), "the request and the result that answers it");
        assertEquals(1, ledger.get(1).at(uri(DEPTH)).intValue().intValue(),
                "the result belongs to the turn that asked for it, not to whatever depth the agent drifted to");
    }

    // ── The interrupted turn ────────────────────────────────────────

    @Test
    void anInterruptedTurnClosesWithALostResult() {
        final ToolFeature toolFeature = new ToolFeature(new LinkedHashMap<>(), LLM_TOOL_FEATURE_TID, null);
        final Agent agent = pairingAgent("interrupted", toolFeature);
        final SpaceChatSessionStore store = store(agent);

        store.updateMessages(sessionVID("interrupted"),
                List.of(UserMessage.from("run the tools"), aiCalling(1)));
        assertEquals(0, ledger("interrupted").size(), "parked while the request is unanswered");

        // Agent.chat()'s finally closes the channel for an abandoned request
        toolFeature.handleOrphanToolRequests(agent, Set.of("call-0"));

        final List<Rec> ledger = ledger("interrupted");
        assertEquals(2, ledger.size(), "the turn is recorded with the result that says it was lost");
        assertEquals(AI_MESSAGE_TID, ledger.getFirst().tid());
        assertEquals(TOOL_RESULT_MESSAGE_TID, ledger.get(1).tid());
        assertEquals("call-0", ledger.get(1).at(uri(CONTENTS)).strValue(),
                "the join key stays the tool call id — the window rules read it");
        assertEquals("lost_tool_result", ledger.get(1).at(uri(NAME)).uriValue().name());
    }

    @Test
    void anInterruptedTurnKeepsTheResultsThatDidArrive() {
        final ToolFeature toolFeature = new ToolFeature(new LinkedHashMap<>(), LLM_TOOL_FEATURE_TID, null);
        final Agent agent = pairingAgent("partial", toolFeature);
        final SpaceChatSessionStore store = store(agent);
        final List<ChatMessage> memory = new ArrayList<>(List.of(UserMessage.from("run the tools"), aiCalling(2)));

        store.updateMessages(sessionVID("partial"), memory);
        toolFeature.onToolExecuted(agent, stagedResult(0));
        memory.add(ToolExecutionResultMessage.from("call-0", "tool_0", "value 0"));
        store.updateMessages(sessionVID("partial"), memory);
        assertEquals(0, ledger("partial").size(), "one of two results is not enough");

        toolFeature.handleOrphanToolRequests(agent, Set.of("call-1"));

        final List<Rec> ledger = ledger("partial");
        assertEquals(3, ledger.size());
        assertEquals(AI_MESSAGE_TID, ledger.getFirst().tid());
        assertEquals("call-0", ledger.get(1).at(uri(CONTENTS)).strValue());
        assertEquals("value 0", ledger.get(1).at(uri(TEXT)).strValue(), "the result that arrived is kept");
        assertEquals("call-1", ledger.get(2).at(uri(CONTENTS)).strValue());
        assertEquals("lost_tool_result", ledger.get(2).at(uri(NAME)).uriValue().name(),
                "the request nothing answered is recorded as lost");
    }

    // ── No tool channel / no requests ───────────────────────────────

    @Test
    void aiMessageWithoutAToolChannelIsWrittenWithoutItsRequests() {
        final Agent agent = pairingAgent("no-channel", null);
        final SpaceChatSessionStore store = store(agent);

        store.updateMessages(sessionVID("no-channel"),
                List.of(UserMessage.from("run the tools"), aiCalling(1)));

        final List<Rec> ledger = ledger("no-channel");
        assertEquals(1, ledger.size(), "the assistant turn is still recorded");
        assertEquals(AI_MESSAGE_TID, ledger.getFirst().tid());
        assertTrue(ledger.getFirst().at(uri(TOOL_REQUESTS)).isNoObj(),
                "requests that can never be answered are dropped, not written orphaned");
    }

    @Test
    void aiMessageWithoutToolRequestsIsWrittenImmediately() {
        final ToolFeature toolFeature = new ToolFeature(new LinkedHashMap<>(), LLM_TOOL_FEATURE_TID, null);
        final Agent agent = pairingAgent("plain", toolFeature);
        final SpaceChatSessionStore store = store(agent);

        store.updateMessages(sessionVID("plain"), List.of(UserMessage.from("say hi"), AiMessage.from("hi")));

        final List<Rec> ledger = ledger("plain");
        assertEquals(1, ledger.size());
        assertEquals(AI_MESSAGE_TID, ledger.getFirst().tid());
        assertEquals("hi", ledger.getFirst().at(uri(TEXT)).strValue());
    }

    // ── What the next chat reads back ───────────────────────────────

    @Test
    void theWindowReadBackHoldsThePairInRequestOrder() {
        final ToolFeature toolFeature = new ToolFeature(new LinkedHashMap<>(), LLM_TOOL_FEATURE_TID, null);
        final Agent agent = pairingAgent("readback", toolFeature);
        final SpaceChatSessionStore store = store(agent);
        final List<ChatMessage> memory = new ArrayList<>(List.of(UserMessage.from("run the tools"), aiCalling(2)));

        // the user turn is written by ChatFeature in a live chat — seed it here
        MessageBuilder.buildUserMessage().text("run the tools")
                .session(sessionVID("readback")).depth(agent.chatDepth()).chatId(agent.chatId()).time()
                .create(store.ledgerWritePath());

        store.updateMessages(sessionVID("readback"), memory);
        toolFeature.onToolExecuted(agent, stagedResult(0));
        toolFeature.onToolExecuted(agent, stagedResult(1));
        memory.add(ToolExecutionResultMessage.from("call-0", "tool_0", "value 0"));
        memory.add(ToolExecutionResultMessage.from("call-1", "tool_1", "value 1"));
        store.updateMessages(sessionVID("readback"), memory);

        // the read the next chat performs — the pair must arrive intact
        final List<ChatMessage> window = store.getMessages(sessionVID("readback"));
        assertEquals(4, window.size(), "user, ai, result, result");
        assertInstanceOf(UserMessage.class, window.get(0));
        final AiMessage readAi = assertInstanceOf(AiMessage.class, window.get(1));
        assertEquals(expectedCallIds(2),
                readAi.toolExecutionRequests().stream().map(ToolExecutionRequest::id).toList(),
                "every request survives the round trip");
        assertEquals("call-0", assertInstanceOf(ToolExecutionResultMessage.class, window.get(2)).id());
        assertEquals("call-1", assertInstanceOf(ToolExecutionResultMessage.class, window.get(3)).id());
    }
}
