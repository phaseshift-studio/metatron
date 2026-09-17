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

package studio.phaseshift.metatron.isa.llm.type;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.feature.AbstractFeature;
import studio.phaseshift.metatron.isa.llm.type.feature.AgentFixture;
import studio.phaseshift.metatron.isa.llm.type.feature.Feature;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.incrQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_MILLIS_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Base class for agent-level tests.  Provides the boot fixture (a shared {@code /usr/test}
 * memSpace), the {@link AgentFixture} builder, and the free structural tests every agent must
 * satisfy — well-formed (name + root), a list of features, and every offered service resolving.
 *
 * <p>A subclass supplies the agent under test via {@link #agent()}.  {@code AbstractFeatureTest}
 * (in the {@code feature} package) extends this and supplies an agent carrying the feature under
 * test, so the agent free tests run automatically for every feature too.
 */
public abstract class AbstractAgentTest extends AbstractMetatronTest {

    /**
     * Shared memSpace under which agent/feature roots live, mounted once per test class.
     */
    protected static final fURI TEST_SPACE = f("/usr/test");
    protected static final fURI TEST_AGENT_ROOT = TEST_SPACE.extend("agent");

    @BeforeAll
    public static void mountAgentTestSpace() {
        InstSet.importInstSet(LLM_ISA_TID);
        InstSet.importInstSet(MATH_ISA_TID);
        memSpace.of(f("/usr/test/#"), f("/sys/space/usr/test")).addQ(incrQ());
    }

    // ── Subclass contract ──────────────────────────────────────────

    /**
     * The agent under test — freshly built with a realistic config.
     */
    protected abstract Agent agent();

    // ── Free tests (run for every agent) ───────────────────────────

    @Test
    public void agentIsWellFormed() {
        final Agent a = this.agent();
        assertNotNull(a, "agent must not be null");
        assertFalse(a.at(NAME).isNoObj(), "agent must carry a name");
        assertTrue(a.has(ROOT), "agent must carry a root");
    }

    @Test
    public void featuresIsAList() {
        final Agent a = this.agent();
        assertTrue(a.features().isLst(), "agent features must be a lst");
    }

    @Test
    public void everyOfferResolves() {
        final Agent a = this.agent();
        for (final Obj f : a.features().elements().toList()) {
            if (!(f instanceof Feature feature))
                continue;
            for (final fURI tid : feature.offers())
                assertFalse(a.service(tid).isNoObj(), "offered service %s must resolve on the agent".formatted(tid));
        }
    }

    /**
     * A {@link RecordingFeature} carried by the agent records the hooks in the canonical
     * declaration order — the agent dispatches the lifecycle stages to every feature in turn.
     */
    @Test
    public void hooksDispatchInDeclarationOrder() {
        final RecordingFeature f = RecordingFeature.record("order-probe");
        final Agent a = agentWith(f);
        runLifecycleThrough(Feature.Stage.on_complete_response, f, a);
        assertEquals(
                List.of("onAgentCtor", "onBeforeChat", "onPartialResponse", "onPartialThinking",
                        "onPartialToolCall", "onToolExecuted", "onToolResult", "onCompleteResponse"),
                f.phases(), "hooks dispatch in the canonical declaration order");
    }

    // ── Builders + helpers (shared with AbstractFeatureTest) ────────

    /**
     * An agent carrying the given features, with a {@code root} pointing at the shared test
     * memSpace.  The {@code requires()} closure is auto-resolved by {@link AgentFixture}.
     */
    protected static Agent agentWith(final AbstractFeature... features) {
        return AgentFixture.builder().feature(features).build();
    }

    /**
     * An agent with no features — for structural/service probes.
     */
    protected static Agent agentDummy() {
        return agentWith();
    }

    /**
     * An agent with a name/desc and the given features.
     */
    protected static Agent agentWith(final String name, final String desc, final AbstractFeature... features) {
        return AgentFixture.builder().name(name).desc(desc).feature(features).build();
    }

    /**
     * A standard chat_result with monos inline, as {@code Agent.chat} builds it.
     */
    protected static ChatFrame chatResultOf(final String chat, final String user) {
        return ChatFrame.chatFrame()
                .put("chat", str(chat))
                .put("user", str(user))
                .put("time", real(42.0, MATH_MILLIS_TID, null));
    }

    protected static Inst toolCall() {
        return instLambda((lhs, inst) -> noobj());
    }

    protected static Rec toolResult(final String name, final String result) {
        return rec(uri(NAME), str(name), uri(RESULT), str(result));
    }

    /**
     * Dispatch exactly one lifecycle stage, with realistic args.
     */
    protected static void dispatch(final Feature.Stage stage, final Feature f, final Agent a) {
        switch (stage) {
            case on_agent_ctor -> f.onAgentCtor(a);
            case on_before_chat -> f.onBeforeChat(a);
            case on_partial_response -> f.onPartialResponse(a, str("chunk"));
            case on_partial_thinking -> f.onPartialThinking(a, str("thought"));
            case on_partial_tool_call -> f.onPartialToolCall(a, toolCall());
            case on_tool_executed -> f.onToolExecuted(a, toolResult("probe", "ok"));
            case on_tool_result -> f.onToolResult(a, toolResult("probe", "ok"), "call-1");
            case on_complete_response -> f.onCompleteResponse(a, chatResultOf("final", "prompt"));
            case on_error -> f.onError(a, fail(MTronException.of("test failure")));
        }
    }

    /**
     * Run every lifecycle stage in declaration order, stopping after {@code stage}.
     */
    protected static void runLifecycleThrough(final Feature.Stage stage, final Feature f, final Agent a) {
        for (final Feature.Stage s : Feature.Stage.values()) {
            dispatch(s, f, a);
            if (s == stage)
                return;
        }
    }
}
