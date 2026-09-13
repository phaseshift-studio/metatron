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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.mToolExecutor;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.incrQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The mid-chat channel in both directions: what the user says mid-turn reaches the
 * model inside the tool result of the call it answered, what the model says
 * mid-turn reaches the user as it arrives, and both are written to the ledger
 * under a subtype that marks them as mid-iteration rather than a real turn.
 *
 * <p>Each test gets its own agent root — the ledger space is shared and static, so
 * a common root would let one test's writes answer another test's read.
 */
public class MidChatChannelTest extends AbstractMetatronTest {

    private static final AtomicInteger ROOTS = new AtomicInteger();

    @BeforeAll
    public static void mountMidChatSpace() {
        InstSet.importInstSet(f("/m/llm"));
        // the two-arg form also registers the math: scheme — pushMidChatMessage
        // evaluates !math:datetime_now() to stamp how long a message waited
        InstSet.importInstSet(MATH_ISA_TID, f("math"));
        memSpace.of(f("/usr/test/#"), f("/sys/space/usr/test")).addQ(incrQ());
    }

    // ── inbound: the user speaking mid-turn ────────────────────────

    @Test
    void testEnvelopeCarriesPendingMessagesAndDrainsThem() {
        final MidChatFeature mid = midchat();
        final Agent agent = agentFor(mid);
        agent.pushMidChatMessage(rec(MESSAGE, str("are you there?"), METADATA, rec()));
        final Obj envelope = mid.onToolResult(agent, str("tool output"), "call_1");
        assertTrue(envelope.isRec(), "a message pending makes the result an envelope rec");
        assertEquals("tool output", envelope.asRec().at(uri(RESULT)).strValue(), "the tool result is preserved");
        assertFalse(envelope.asRec().at(uri(PENDING_MESSAGES)).isNoObj(), "and the user's message rides with it");
        assertTrue(agent.popMidChatMessages().isEmpty(), "the stack is drained exactly once");
    }

    @Test
    void testEnvelopePassesThroughWhenNothingIsPending() {
        final MidChatFeature mid = midchat();
        final Agent agent = agentFor(mid);
        final Obj envelope = mid.onToolResult(agent, str("tool output"), "call_1");
        assertTrue(envelope.isStr(), "with nothing pending the payload is handed back untouched");
        assertEquals("tool output", envelope.strValue(), "verbatim");
    }

    /**
     * The stage is dispatched from the agent, not called directly: the executor runs
     * outside the turn, so folding the payload over the features is the agent's to
     * perform (see {@code Agent.dispatchToolResult}, called by {@code mToolExecutor}).
     *
     * <p>Two things are being pinned here.  The hook exists only because the feature
     * was built through its type constructor — that is what wires every stage, this
     * one included.  And a feature with no {@code on_tool_result} at all, like the
     * ToolFeature riding along here, must pass the payload through rather than erase
     * it with the {@code noobj} a missing hook evaluates to.
     */
    @Test
    void testTheToolResultStageFoldsOverTheAgentsFeatures() {
        final MidChatFeature mid = midchatThroughItsType(f("/usr/test/midchat" + ROOTS.incrementAndGet()));
        final Agent agent = agentFor(mid);
        agent.pushMidChatMessage(rec(MESSAGE, str("are you there?"), METADATA, rec()));

        final Obj folded = agent.dispatchToolResult(str("tool output"), "call_1");

        assertTrue(folded.isRec(), "the wired stage folded the payload into an envelope");
        assertEquals("tool output", folded.asRec().at(uri(RESULT)).strValue(), "the tool output survives the fold");
        assertFalse(folded.asRec().at(uri(PENDING_MESSAGES)).isNoObj(), "and the user's message rides with it");

        final Obj passthrough = agent.dispatchToolResult(str("more output"), "call_2");
        assertTrue(passthrough.isStr(), "once the stack is drained the payload passes through untouched");
        assertEquals("more output", passthrough.strValue(), "verbatim");
    }

    /**
     * The whole inbound path, executor included.  {@code dispatchToolResult} covers the
     * fold; this covers {@code mToolExecutor}, which is where a wrong agent reference
     * or an early return would strand the message on the stack for good — the failure
     * the user sees as "my mid-chat messages never reach the model".
     */
    @Test
    void testAMidChatMessageRidesOutOnTheNextToolResult() {
        final MidChatFeature mid = midchatThroughItsType(f("/usr/test/midchat" + ROOTS.incrementAndGet()));
        final Agent agent = agentFor(mid);
        agent.pushMidChatMessage(rec(MESSAGE, str("are you still with me?"), METADATA, rec()));

        final Inst tool = instC(f("/m/test/probe"), rec(uri("query"), STR_TYPE),
                (lhs, i) -> str("scanned " + i.arg(0)));
        final String payload = new mToolExecutor(tool).agent(agent).execute(
                ToolExecutionRequest.builder()
                        .id("call_1")
                        .name("m_test_probe")
                        .arguments("{\"query\":\"signals\"}")
                        .build(),
                "memory");

        assertTrue(payload.contains("are you still with me?"),
                "the message rides out inside the tool result the model is handed: " + payload);
        assertTrue(agent.popMidChatMessages().isEmpty(),
                "and the stack is drained exactly once, so it does not pile up");
    }

    /**
     * A mid-chat message is addressed to the <b>agent</b>, not to a Java object: the
     * caller that pushes it is never the instance running the turn, because
     * {@code Agent.agent(rec)} constructs a fresh Agent on every call — every
     * {@code @dr.chat(...)} and every console line gets its own.  So the stack has to
     * belong to the agent's address, which is the one thing those instances agree on.
     * A per-instance stack belongs to the pusher alone: the turn pops an empty one and
     * the message sits on {@code dr/message_stack} forever, which is exactly what the
     * user sees.
     */
    @Test
    void testAMessagePushedByAnotherInstanceStillReachesTheTurn() {
        final MidChatFeature mid = midchatThroughItsType(f("/usr/test/midchat" + ROOTS.incrementAndGet()));
        final fURI address = f("/usr/test/agent" + ROOTS.incrementAndGet());
        final Rec config = rec(mutableMap(
                        uri(NAME), str("test-agent"),
                        uri(ROOT), uri(address.toString()),
                        uri(FEATURE), lst(new SkillFeature(mutableMap(), LLM_SKILL_FEATURE_TID, null),
                                new ToolFeature(mutableMap(), LLM_TOOL_FEATURE_TID, null), mid)),
                LLM_AGENT_TID, address);

        final Agent turn = Agent.agent(config);   // the instance streaming the turn
        final Agent caller = Agent.agent(config); // the console: a fresh Agent per call
        caller.pushMidChatMessage(rec(MESSAGE, str("are you still with me?"), METADATA, rec()));

        final Obj folded = turn.dispatchToolResult(str("tool output"), "call_1");

        assertTrue(folded.isRec(), "the message the caller pushed must reach the turn: " + folded);
        assertEquals("are you still with me?", folded.asRec().at(uri(PENDING_MESSAGES)).asLst().elements()
                .findFirst().orElse(noobj()).asRec().at(uri(MESSAGE)).strValue());
    }

    @Test
    void testInboundMessagesCarryTheUserMidChatSubtype() {
        final MidChatFeature mid = midchat();
        final Agent agent = agentFor(mid);
        agent.pushMidChatMessage(rec(MESSAGE, str("still there?"), METADATA, rec()));
        mid.onToolResult(agent, str("tool output"), "call_1");
        final Obj written = firstWithSub(rootOf(agent), USER_MIDCHAT_TID);
        assertTrue(written.isRec(), "the user's message reached the ledger with the midchat subtype");
        assertEquals("still there?", written.asRec().at(uri(TEXT)).strValue(), "keeping the words the user actually sent");
        assertEquals(USER_MESSAGE_TID, written.tid(),
                "while remaining an ordinary user message as far as LC4j is concerned");
    }

    @Test
    void testOrphanMidChatMessagesReachTheLedger() {
        final MidChatFeature mid = midchat();
        final Agent agent = agentFor(mid);
        agent.pushMidChatMessage(rec(MESSAGE, str("still there?"), METADATA, rec()));
        mid.handleOrphanMidChatMessages(agent);
        assertTrue(agent.popMidChatMessages().isEmpty(), "the stack is drained");
        assertTrue(firstWithSub(rootOf(agent), USER_MIDCHAT_TID).isRec(),
                "a message with no tool call left to carry it is published, not dropped");
    }

    /**
     * The production shape: each call gets its own agent, built from its own
     * construction of the config — so the two instances hold <em>separate</em>
     * materializations of the feature.  Sharing one config rec is not a test of this:
     * the feature objects are then the same object, and a field on the feature would
     * pass while it is the addressable subspace that has to carry the message.
     */
    @Test
    void testAMessagePushedByAnIndependentlyBuiltAgentStillReachesTheTurn() {
        final fURI address = f("/usr/test/agent" + ROOTS.incrementAndGet());

        final Agent turn = Agent.agent(freshAgentConfig(address));
        final Agent caller = Agent.agent(freshAgentConfig(address));
        caller.pushMidChatMessage(rec(MESSAGE, str("are you still with me?"), METADATA, rec()));

        final Obj folded = turn.dispatchToolResult(str("tool output"), "call_1");

        assertTrue(folded.isRec(), "a message pushed by an independently built agent must reach the turn: " + folded);
    }

    /** A config carrying its own feature instances — the shape every call gets. */
    private static Rec freshAgentConfig(final fURI address) {
        return rec(mutableMap(
                        uri(NAME), str("test-agent"),
                        uri(ROOT), uri(address.toString()),
                        uri(FEATURE), lst(new SkillFeature(mutableMap(), LLM_SKILL_FEATURE_TID, null),
                                new ToolFeature(mutableMap(), LLM_TOOL_FEATURE_TID, null),
                                midchatThroughItsType(address))),
                LLM_AGENT_TID, address);
    }

    // ── outbound: the model speaking mid-turn ─────────────────────

    @Test
    void testMidChatRemarkIsRelayedWithItsSubtype() {
        final MidChatFeature mid = midchat();
        final Agent agent = agentFor(mid);
        mid.onPartialThinking(agent, str(
                "I checked. <<txt:midchat>>still tracing the argument routing<</txt:midchat>> Then I ran another."));
        final Obj written = firstWithSub(rootOf(agent), AI_MIDCHAT_TID);
        assertTrue(written.isRec(), "the remark reached the ledger with the ai midchat subtype");
        assertEquals("still tracing the argument routing", written.asRec().at(uri(TEXT)).strValue(),
                "carrying only the remark — the markup and the surrounding thought are gone");
        assertEquals(AI_MESSAGE_TID, written.tid(), "while remaining an ordinary ai message to LC4j");
    }

    @Test
    void testARemarkSplitAcrossChunksIsRelayedExactlyOnce() {
        final MidChatFeature mid = midchat();
        final Agent agent = agentFor(mid);
        mid.onPartialThinking(agent, str("thinking... <<txt:mid"));
        mid.onPartialThinking(agent, str("chat>>half a thought<</txt:mid"));
        mid.onPartialThinking(agent, str("chat>> and on."));
        assertEquals(1L, countWithSub(rootOf(agent), AI_MIDCHAT_TID),
                "a marker split across three streamed chunks is relayed once, not twice or never");
        assertEquals("half a thought", firstWithSub(rootOf(agent), AI_MIDCHAT_TID).asRec().at(uri(TEXT)).strValue(),
                "and reassembled from its parts");
    }

    @Test
    void testPlainThinkingIsNotRelayed() {
        final MidChatFeature mid = midchat();
        final Agent agent = agentFor(mid);
        mid.onPartialThinking(agent, str("just thinking out loud, no marker at all"));
        assertTrue(firstWithSub(rootOf(agent), AI_MIDCHAT_TID).isNoObj(), "thinking with no watermark relays nothing");
    }

    // ── helpers ────────────────────────────────────────────────────

    private static MidChatFeature midchat() {
        return new MidChatFeature(mutableMap(), LLM_MIDCHAT_FEATURE_TID, null);
    }

    /**
     * Built the way a boot file or an agent config builds one — through the type, whose
     * constructor runs {@code createStageLambdas} and so wires the feature's stage
     * hooks.  A directly constructed feature has no hooks and dispatches nothing.
     */
    private static MidChatFeature midchatThroughItsType(final fURI root) {
        return ObjmtronSerializer.parse("midchat_feature::[root => %s]".formatted(root)).apply().as();
    }

    private static Agent agentFor(final MidChatFeature mid) {
        final fURI root = f("/usr/test/agent" + ROOTS.incrementAndGet());
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(NAME), str("test-agent"));
        map.put(uri(ROOT), uri(root.toString()));
        map.put(uri(FEATURE), lst(new SkillFeature(mutableMap(), LLM_SKILL_FEATURE_TID, null),
                new ToolFeature(mutableMap(), LLM_TOOL_FEATURE_TID, null),
                new ThinkFeature(mutableMap(), LLM_THINK_FEATURE_TID, null), mid));
        return Agent.agent(rec(map, LLM_AGENT_TID, null));
    }

    private static fURI rootOf(final Agent agent) {
        return agent.at(uri(ROOT)).uriValue();
    }

    /**
     * The ledger messages written under an agent root.
     *
     * <p>{@code stream()} makes this total: a wildcard read that matched several
     * things comes back as an {@code objs}, which streams its members, while every
     * other obj streams itself — so one match and many take the same path.  The
     * leaves are rels carrying the message as their second.
     */
    private static List<Rec> ledgerMessages(final fURI root) {
        final Obj rows = Router.readFromSpace(root.extend(MESSAGE).extend("+/"));
        if (rows.isNoObj())
            return List.of();
        final List<Rec> messages = new ArrayList<>();
        rows.stream()
                .map(Obj::asRel)
                .map(Rel::second)
                .filter(Obj::isRec)
                .map(Obj::asRec)
                .forEach(messages::add);
        return messages;
    }

    private static boolean hasSub(final Rec message, final fURI subtype) {
        final Obj sub = message.at(uri(SUB));
        return sub.isUri() && sub.uriValue().equals(subtype);
    }

    /** The first ledger message carrying the given subtype; {@code noobj} if none. */
    private static Obj firstWithSub(final fURI root, final fURI subtype) {
        return ledgerMessages(root).stream()
                .filter(message -> hasSub(message, subtype))
                .findFirst()
                .map(message -> (Obj) message)
                .orElse(noobj());
    }

    private static long countWithSub(final fURI root, final fURI subtype) {
        return ledgerMessages(root).stream().filter(message -> hasSub(message, subtype)).count();
    }
}
