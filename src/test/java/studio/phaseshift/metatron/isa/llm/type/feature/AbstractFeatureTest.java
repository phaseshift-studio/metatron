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
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatResult;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.incrQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_MILLIS_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Base class for all feature tests.  Provides the free structural/behavioral
 * tests every feature must satisfy, plus a standard lifecycle loop that
 * exercises all hooks in production order.
 *
 * <p>A subclass supplies the feature under test via {@link #feature()} (with a
 * realistic config — e.g. a {@code root} under the shared {@code /usr/test}
 * memSpace for persisting features) and adds feature-specific assertions,
 * typically against the {@link ChatResult} returned by {@link #runLifecycle}.
 * The free tests run automatically for every subclass: structure, active(),
 * skill() shape, onBeforeChat short-circuit, the full lifecycle, the error
 * path, and requires() resolution.
 */
public abstract class AbstractFeatureTest extends AbstractMetatronTest {

    /**
     * Shared memSpace under which feature roots live, mounted once per test class.
     */
    protected static final fURI TEST_SPACE = f("/usr/test");
    protected static final fURI TEST_AGENT_ROOT = TEST_SPACE.extend("agent");

    @BeforeAll
    public static void mountFeatureTestSpace() {
        InstSet.importInstSet(f("/m/llm"));
        InstSet.importInstSet(MATH_ISA_TID);
        memSpace.of(f("/usr/test/#"), f("/sys/space/usr/test")).addQ(incrQ());
    }

    // ── Subclass contract ──────────────────────────────────────────

    /**
     * The feature under test — freshly constructed with a realistic config.
     */
    protected abstract <F extends Feature> F feature();

    public <F extends Feature> F feature(final Rec config) {
        return this.feature().jvm(config.jvm()).as();
    }

    // ── Free tests (run for every feature) ─────────────────────────

    @Test
    public void featureIsARec() {
        assertTrue(feature().isRec(), "feature must be a rec");
    }

    @Test
    public void featureHasNonDefaultTid() {
        assertNotEquals(REC_TID, feature().tid(), "feature tid must not be the default /m/rec");
    }

    @Test
    public void featureHasJvm() {
        assertNotNull(feature().jvm(), "feature JVM must not be null");
    }

    @Test
    public void featureRoundTripsThroughAgent() {
        final AbstractFeature f = feature();
        final Agent a = agentWith(f);
        final Obj found = a.features().lstValue().stream()
                .filter(e -> e.tid().equals(f.tid()))
                .findFirst().orElse(null);
        assertNotNull(found, "feature must be retrievable from the agent's feature list");
        assertEquals(f.tid(), found.tid(), "retrieved feature must have the same TID");
    }

    @Test
    public void activeDefaultsTrue() {
        assertTrue(feature().active(), "feature should be active by default");
    }

    @Test
    public void registeredSkillsAreWellFormed() {
        final AbstractFeature f = feature();
        final Agent a = agentWith(f);
        f.onAgentCtor(a);
        f.onBeforeChat(a);
        if (!a.hasFeature(LLM_SKILL_FEATURE_TID))
            return;
        for (final Obj skill : a.feature(LLM_SKILL_FEATURE_TID).<SkillFeature>as().skills().lstValue()) {
            assertTrue(skill.isRec(), "skill must be a rec");
            final Rec r = skill.asRec();
            assertFalse(r.at(uri(NAME)).isNoObj(), "skill must have a name");
            assertFalse(r.at(uri(DESC)).isNoObj(), "skill must have a desc");
            assertFalse(r.at(uri(CONTENT)).isNoObj(), "skill must have content");
        }
    }

    @Test
    public void onBeforeChatDoesNotShortCircuit() {
        final AbstractFeature f = feature();
        assertTrue(f.onBeforeChat(agentWith(f)).isNoObj(),
                "onBeforeChat must return noobj for a normal prompt (short-circuit is deliberate)");
    }

    @Test
    public void fullLifecycleDoesNotThrow() {
        assertValidResult(runLifecycle(feature()));
    }

    @Test
    public void errorPathDoesNotThrow() {
        runErrorPath(feature());
    }

    @Test
    public void requiresAreResolvable() {
        final AbstractFeature f = feature();
        final Set<fURI> reqs = f.requires();
        if (reqs.isEmpty())
            return;
        // Hard requires are validated (and enforced) at agent construction, so
        // a carrying agent that exists here must already resolve every one.
        final Agent a = agentWith(f);
        for (final fURI req : reqs)
            assertTrue(a.hasFeature(req), "required feature %s not present".formatted(req));
    }

    // ── The lifecycle loop ─────────────────────────────────────────

    /**
     * Run every lifecycle hook in production order against a fresh agent and
     * return the {@link ChatResult} the feature was given at completion, so
     * feature tests can assert what the feature attached to it.
     */
    protected ChatResult runLifecycle(final AbstractFeature feature) {
        return runLifecycle(feature, agentWith(feature));
    }

    protected ChatResult runLifecycle(final AbstractFeature feature, final Agent agent) {
        feature.onAgentCtor(agent);
        assertTrue(feature.onBeforeChat(agent).isNoObj(), "onBeforeChat must not short-circuit");
        feature.onPartialResponse(agent, str("chunk 1"));
        feature.onPartialResponse(agent, str("chunk 2"));
        feature.onPartialThinking(agent, str("reasoning..."));
        final Inst tool = toolCall();
        feature.onPartialToolCall(agent, tool);
        feature.beforeToolExecution(agent, tool);
        feature.onToolExecuted(agent, toolResult("my_tool", "ok"));
        final ChatResult result = chatResultOf("final response", "test prompt");
        feature.onCompleteResponse(agent, result);
        return result;
    }

    protected void runErrorPath(final AbstractFeature feature) {
        final Agent agent = agentWith(feature);
        feature.onAgentCtor(agent);
        feature.onBeforeChat(agent);
        feature.onError(agent, fail(MTronException.of("test failure")));
    }

    // ── Helpers ────────────────────────────────────────────────────

    /**
     * An agent with no features — for skill/structural probes.
     */
    protected static Agent agentDummy() {
        return agentWith();
    }

    /**
     * An agent carrying the given features, with a {@code root} pointing at
     * the shared test memSpace so persisting features work during the loop.
     */
    protected static Agent agentWith(final AbstractFeature... features) {
        return agentWith("test-agent", null, features);
    }

    /**
     * An agent with a name/desc and the given features.
     */
    protected static Agent agentWith(final String name, final String desc, final AbstractFeature... features) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(NAME), str(name));
        if (null != desc)
            map.put(uri(DESC), str(desc));
        map.put(uri(ROOT), uri(TEST_AGENT_ROOT.toString()));
        final List<Obj> featureObjs = new ArrayList<>();
        for (final AbstractFeature f : features) {
            if (!f.isNoObj())
                featureObjs.add(f);
        }
        featureObjs.addAll(gatekeepersFor(features));
        map.put(uri(FEATURE), lst(featureObjs));
        return Agent.agent(rec(map, LLM_AGENT_TID, null));
    }

    /**
     * The gateway features the given feature set requires — gatekeepers last
     * in hook order, and the tool gateway after the skill gateway (tool
     * forwarding happens in the skill gateway's {@code onBeforeChat}).
     */
    private static List<Obj> gatekeepersFor(final AbstractFeature... features) {
        final Set<fURI> needs = new LinkedHashSet<>();
        for (final AbstractFeature f : features)
            needs.addAll(f.requires());
        if (needs.contains(LLM_SKILL_FEATURE_TID))
            needs.add(LLM_TOOL_FEATURE_TID);
        final List<Obj> gate = new ArrayList<>();
        if (needs.contains(LLM_SKILL_FEATURE_TID) && !hasTid(LLM_SKILL_FEATURE_TID, features))
            gate.add(new SkillFeature(new LinkedHashMap<Obj, Obj>(), LLM_SKILL_FEATURE_TID, null));
        if (needs.contains(LLM_TOOL_FEATURE_TID) && !hasTid(LLM_TOOL_FEATURE_TID, features))
            gate.add(new ToolFeature(new LinkedHashMap<Obj, Obj>(), LLM_TOOL_FEATURE_TID, null));
        return gate;
    }

    private static boolean hasTid(final fURI tid, final AbstractFeature... features) {
        for (final AbstractFeature f : features)
            if (tid.equals(f.tid()))
                return true;
        return false;
    }

    /**
     * A standard chat_result with monos inline, as Agent.chat builds it.
     */
    protected static ChatResult chatResultOf(final String chat, final String user) {
        return ChatResult.chatResult()
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

    private void assertValidResult(final ChatResult result) {
        assertNotNull(result, "lifecycle must produce a chat_result");
        assertTrue(result.isRec(), "chat_result must be a rec");
        assertEquals(LLM_CHAT_RESULT_TID, result.tid(), "chat_result must have the chat_result tid");
        assertFalse(result.at(uri(CHAT)).isNoObj(), "chat_result must carry the chat");
    }
}
