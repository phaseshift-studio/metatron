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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.AbstractAgentTest;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatFrame;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Base class for all feature tests.  Extends {@link AbstractAgentTest}, so the agent free tests
 * (well-formed, features list, offer resolution) run automatically; then adds the free
 * structural/behavioral tests every feature must satisfy, plus a standard lifecycle loop that
 * exercises every hook in production order.
 *
 * <p>A subclass supplies the feature under test via {@link #feature()} (with a realistic config —
 * e.g. a {@code root} under the shared {@code /usr/test} memSpace for persisting features) and adds
 * feature-specific assertions, typically against the {@link ChatFrame} returned by
 * {@link #runLifecycle}.
 */
public abstract class AbstractFeatureTest extends AbstractAgentTest {

    // ── Subclass contract ──────────────────────────────────────────

    /**
     * The feature under test — freshly constructed with a realistic config.
     */
    protected abstract <F extends Feature> F feature();

    @SuppressWarnings("unchecked")
    public <F extends Feature> F feature(final Rec config) {
        return (F) this.feature().jvm(config.jvm()).as();
    }

    /**
     * An agent carrying the feature under test — the {@link AbstractAgentTest} contract, so the
     * agent free tests run against the feature's home agent.
     */
    @Override
    protected Agent agent() {
        return agentWith(feature());
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
    public void offersRequiresUsesAreDeclared() {
        final Feature f = feature();
        assertNotNull(f.offers(), "offers() must return a set");
        assertNotNull(f.requires(), "requires() must return a set");
        assertNotNull(f.uses(), "uses() must return a set");
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
        // requires() speaks service tids, so resolve against offers(), not feature tids.
        final Agent a = agentWith(f);
        for (final fURI req : reqs)
            assertFalse(a.service(req).isNoObj(), "required service %s not provided".formatted(req));
    }

    /**
     * Every prefix of the lifecycle runs against a fresh agent without throwing.  The stages are
     * the {@link Feature.Stage} enumeration — the canonical hook list, one row per stage — and
     * each row runs the pipeline in declaration order up to and including that stage, so a later
     * stage sees the state its predecessors built (the hooks are a pipeline, not independent).
     */
    @ParameterizedTest
    @EnumSource(Feature.Stage.class)
    public void lifecyclePrefixDoesNotThrow(final Feature.Stage stage) {
        final AbstractFeature f = feature();
        final Agent a = agentWith(f);
        assertDoesNotThrow(() -> runLifecycleThrough(stage, f, a), "lifecycle through %s must not throw".formatted(stage));
    }

    // ── The lifecycle loop ─────────────────────────────────────────

    /**
     * Run every lifecycle hook in production order against a fresh agent and return the
     * {@link ChatFrame} the feature was given at completion, so feature tests can assert what the
     * feature attached to it.
     */
    protected ChatFrame runLifecycle(final AbstractFeature feature) {
        return runLifecycle(feature, agentWith(feature));
    }

    protected ChatFrame runLifecycle(final AbstractFeature feature, final Agent agent) {
        feature.onAgentCtor(agent);
        assertTrue(feature.onBeforeChat(agent).isNoObj(), "onBeforeChat must not short-circuit");
        feature.onPartialResponse(agent, str("chunk 1"));
        feature.onPartialResponse(agent, str("chunk 2"));
        feature.onPartialThinking(agent, str("reasoning..."));
        final Inst tool = toolCall();
        feature.onPartialToolCall(agent, tool);
        feature.beforeToolExecution(agent, tool);
        feature.onToolExecuted(agent, toolResult("my_tool", "ok"));
        final ChatFrame result = chatResultOf("final response", "test prompt");
        feature.onCompleteResponse(agent, result);
        return result;
    }

    protected void runErrorPath(final AbstractFeature feature) {
        final Agent agent = agentWith(feature);
        feature.onAgentCtor(agent);
        feature.onBeforeChat(agent);
        feature.onError(agent, fail(MTronException.of("test failure")));
    }

    // ── Tool-through-the-stack rigging ──────────────────────────────

    /**
     * Locate a tool registered into the agent's {@link ToolFeature} by
     * {@code feature.onBeforeChat(agent)}, matched by regex against its flattened tid (e.g.
     * {@code "bash"} matches {@code /m/llm/.../bash}).
     */
    protected static Inst findTool(final Agent agent, final AbstractFeature feature, final String toolNameRegex) {
        feature.onBeforeChat(agent);
        return agent.feature(LLM_TOOL_FEATURE_TID).<ToolFeature>as()
                .tools().elements()
                .map(t -> t.asRec().at(uri(INST)).<Obj>as())
                .filter(Obj::isObjInst)
                .map(Obj::asInst)
                .filter(i -> Pattern.compile(toolNameRegex).matcher(i.tid().toString()).find())
                .findFirst()
                .orElseThrow(() -> new AssertionError("tool not registered: " + toolNameRegex));
    }

    /**
     * Invoke a registered tool through the <em>full</em> {@code mTool} spec/executor stack — the
     * same path LC4j uses when an agent calls a tool — rather than applying the inst directly.
     * Arguments are round-tripped through JSON and parsed schema-aware against the tool's declared
     * arg types (exactly as {@link studio.phaseshift.metatron.isa.llm.mToolExecutor} does), so a
     * {@code uri}/{@code code}/{@code inst}/{@code str} argument arrives as its declared type
     * instead of a guessed string.
     * <p>
     * Returns the raw {@link Obj} result (recovered from the executor's result stash), so callers
     * can assert on {@code fail::T} and typed results exactly as production code sees them — not a
     * lossy JSON re-parse.
     */
    protected static Obj runToolThroughStack(final Agent agent, final AbstractFeature feature,
                                             final String toolNameRegex, final Rec arguments) {
        final Inst tool = findTool(agent, feature, toolNameRegex);
        final Tuple.Pair<ToolSpecification, ToolExecutor> specExec =
                mTool.mtronInstToolSpecification(mTool.mtronInstToDocs(tool));
        final String callId = "test-" + UUID.randomUUID();
        final String argsJson = ObjJSONSerializer.simple().write(arguments).toString();
        final ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id(callId)
                .name(specExec.get0().name())
                .arguments(argsJson)
                .build();
        specExec.get1().execute(request, null);
        final Obj raw = mTool.resultStash.remove(callId);
        return null == raw ? str(specExec.get0().name() + " returned no stashed result") : raw;
    }

    private void assertValidResult(final ChatFrame result) {
        assertNotNull(result, "lifecycle must produce a chat_result");
        assertTrue(result.isRec(), "chat_result must be a rec");
        assertEquals(LLM_CHAT_RESULT_TID, result.tid(), "chat_result must have the chat_result tid");
        assertFalse(result.at(uri(CHAT)).isNoObj(), "chat_result must carry the chat");
    }
}
