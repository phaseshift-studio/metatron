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
 * MERCHANTABILITY or FITNESS TO A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.llm.type.feature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SKILL_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_TOOL_FEATURE_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_MILLIS_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.SYS_BASH_INST_TID;

/**
 * {@code ToolFeature} — the gateway to the agent's tool registry: a single
 * entry point ({@code addTool}) that anything publishing a tool goes through
 * (features directly, or the skill gateway on behalf of a skill's tools);
 * the registry is projected onto the agent's LC4j tool bag in
 * {@code onBeforeChat}.
 */
public class ToolFeatureTest extends AbstractFeatureTest {

    @Override
    protected ToolFeature feature() {
        return new ToolFeature(new LinkedHashMap<Obj, Obj>(), LLM_TOOL_FEATURE_TID, null);
    }

    // ── bash tool (the system /m/sys/inst/bash instruction, modulated by q-params) ──

    /**
     * The system bash instruction with {@code allow}/{@code reject}/{@code env}
     * modulators attached as q-params — the same shape {@code tool_feature} config
     * uses: {@code !*bash?reject=['\brm\b']&env=[USER=>'x']}.
     * <p>
     * Reading {@code /m/sys/inst/bash?reject=[...]} copies the q-params onto the
     * instruction's tid, so the body's {@code inst.tid().qValue(...)} picks them up.
     */
    private static Inst bashTool(final String allow, final String reject, final String env) {
        final List<String> qs = new ArrayList<>();
        if (null != allow) qs.add(ALLOW + "=" + mtronLst(allow));
        if (null != reject) qs.add(REJECT + "=" + mtronLst(reject));
        if (null != env) qs.add(ENV + "=" + env);
        final String uri = SYS_BASH_INST_TID + (qs.isEmpty() ? "" : "?" + String.join("&", qs));
        return Router.readFromSpace(uri).asInst();
    }

    /** Convert a {@code ~}-separated list of regex patterns into a mtron lst literal. */
    private static String mtronLst(final String patterns) {
        return "[" + Arrays.stream(patterns.split("~"))
                .map(String::trim)
                .map(p -> "'" + p + "'")
                .collect(Collectors.joining(",")) + "]";
    }

    /** Invoke the bash tool through the full mTool spec/executor stack with a safe 10s timeout. */
    private static Obj bash(final Agent agent, final ToolFeature tf, final String command) {
        return runToolThroughStack(agent, tf, "bash", rec(uri(CMD), str(command), uri(TIMEOUT), real(10000.0, MATH_MILLIS_TID, null)));
    }

    /** Invoke the bash tool with an explicit agent-supplied TIMEOUT. */
    private static Obj bash(final Agent agent, final ToolFeature tf, final String command, final Obj timeout) {
        return runToolThroughStack(agent, tf, "bash", rec(uri(CMD), str(command), uri(TIMEOUT), timeout));
    }

    // ── the registry itself ─────────────────────────────────────────

    @Test
    public void testUpsertByToolName() {
        final ToolFeature tf = feature();
        tf.addTool(mTool.tool(rec(uri(INST), instLambda((lhs, inst) -> noobj()), uri(NAME), uri("alpha_tool"), uri(DESC), str("first edition"))));
        tf.addTool(mTool.tool(rec(uri(INST), instLambda((lhs, inst) -> noobj()), uri(NAME), uri("alpha_tool"), uri(DESC), str("second edition"))));
        tf.addTool(mTool.tool(rec(uri(INST), instLambda((lhs, inst) -> noobj()), uri(NAME), uri("beta_tool"), uri(DESC), str("steady state"))));
        final Lst tools = tf.tools();
        assertEquals(2, tools.lstValue().size(), "registering the same name upserts");
        assertEquals("second edition", tools.at(0).asRec().at(uri(DESC)).strValue(), "the later registration wins");
    }

    // ── publishing + projection ─────────────────────────────────────

    @Test
    public void testToolFeaturePublishesUsageSkill() {
        final ToolFeature tf = feature();
        final SkillFeature gateway = new SkillFeature(new LinkedHashMap<Obj, Obj>(), LLM_SKILL_FEATURE_TID, null);
        final Agent a = agentWith(gateway, tf);
        tf.onBeforeChat(a);
        assertTrue(gateway.skills().lstValue().stream()
                        .anyMatch(s -> "tool_feature".equals(s.asRec().at(uri(NAME)).uriValue().name())),
                "onBeforeChat should publish the tool feature's usage skill to the skill gateway");
    }

    @Test
    public void testDirectAddToolLandsInProjection() {
        final ToolFeature tf = feature();
        final Agent a = agentWith(tf);
        tf.addTool(mTool.tool(rec(uri(INST), instLambda((lhs, inst) -> noobj()), uri(NAME), uri("direct_tool"), uri(DESC), str("a directly registered tool"))));
        tf.onBeforeChat(a);
        assertEquals(2, tf.tools().lstValue().size(), "directly added tools persist in the registry across chats");
    }

    // ── bash tool: allow/reject modulators via q-params ─────────────

    @ParameterizedTest
    @CsvSource(value = {
            // command                                                      allow list             reject list         fail?     expected result / error text
            "echo metatron works                                          % null                % null                % false   % metatron works",
            "ls                                                           % .*                  % null                % false   % null",
            "echo metatron bash work                                      % echo .*             % null                % false   % metatron bash work",
            "echo hello world                                             % echo hello world    % null                % false   % hello world",
            "echo hello world extra                                       % echo hello world    % null                % true    % allowed patterns do not match",
            "ls -la                                                       % echo .*             % null                % true    % allowed patterns do not match",
            "echo which pattern won                                       % cat .* ~ echo .*    % null                % false   % which pattern won",
            "rm /tmp/metatron_bash_harmless_none                          % null                % \\brm\\b            % true    % reject patterns match",
            "echo prep && rm /tmp/metatron_bash_harmless_none             % null                % \\brm\\b            % true    % reject patterns match",
            "bash -c \"rm /tmp/metatron_bash_harmless_none\"             % null                % \\brm\\b            % true    % reject patterns match",
            "sudo rm /tmp/metatron_bash_harmless_none                     % null                % \\brm\\b            % true    % reject patterns match",
            "mkfs.ext4 /dev/null                                          % null                % \\b(rm|mkfs)\\b     % true    % reject patterns match",
            "echo please confirm the plan                                 % null                % \\brm\\b            % false   % please confirm the plan",
            "echo metatron is safe                                        % null                % \\brm\\b            % false   % metatron is safe",
            "echo hello                                                   % echo .* ~ ls .*     % \\brm\\b            % false   % hello",
            "echo hi && rm /tmp/metatron_bash_harmless_none               % echo .* ~ ls .*     % \\brm\\b            % true    % reject patterns match",
            "echo all clear                                               % echo .* ~ ls .*     % \\bsecret\\b         % false   % all clear",
            "echo secret_free                                             % echo .* ~ ls .*     % \\bsecret\\b         % false   % secret_free",
            "echo the secret is out                                       % echo .* ~ ls .*     % \\bsecret\\b         % true    % reject patterns match",
            "x=metatron; echo $x                                          % .*                  % null                % false   % metatron",
            "echo $(echo inner)                                           % .*                  % null                % false   % inner",
            "echo 'single quoted'                                         % .*                  % null                % false   % single quoted",
            "echo a; echo b                                               % .*                  % null                % false   % b",
            "echo abc12 | grep 1                                          % .*                  % null                % false   % abc12",
            "echo redirected > /tmp/metatron_bash_redirect_none.txt       % .*                  % null                % false   % null",
            "dd if=/dev/null of=/dev/null                                 % .*                  % null                % false   % null"
    }, delimiter = '%', nullValues = "null")
    public void testBashCommandRejectAllow(final String command, final String allowList, final String rejectList, final boolean fail, final String messageFragment) {
        final ToolFeature tf = feature();
        final Agent agent = agentWith(tf);
        tf.addTool(mTool.tool(bashTool(allowList, rejectList, null)));
        final Obj result = bash(agent, tf, command);
        if (fail) {
            assertTrue(result.isFail(), "expected the guard to reject before exec: " + command);
            assertTrue(result.toCleanString().contains(null == messageFragment ? "" : messageFragment), "expected a reject failure, got: %s".formatted(result.toCleanString()));
        } else {
            assertFalse(result.isFail(), "expected the guard to accept before exec: " + command);
            assertTrue(result.toCleanString().contains(null == messageFragment ? "" : messageFragment), "expected a result fragment, got: %s".formatted(result.toCleanString()));
        }
    }

    @Test
    public void testBashNonZeroExitSurfacesAsFail() {
        final ToolFeature tf = feature();
        final Agent agent = agentWith(tf);
        tf.addTool(mTool.tool(bashTool(".*", null, null)));
        final Obj result = bash(agent, tf, "false");
        assertTrue(result.isFail(), "a non-zero exit must surface as a fail, got: %s".formatted(result));
        assertTrue(result.toCleanString().contains("terminated with unexpected exit"), "expected the exit status in the failure text, got: %s".formatted(result.toCleanString()));
    }

    // ── bash tool: TIMEOUT argument ─────────────────────────────────

    @Test
    public void testBashAgentTimeoutKillsSlowCommand() {
        final ToolFeature tf = feature();
        final Agent agent = agentWith(tf);
        tf.addTool(mTool.tool(bashTool(".*", null, null)));
        final long start = System.nanoTime();
        final Obj result = bash(agent, tf, "sleep 2", real(300.0, MATH_MILLIS_TID, null));
        final long elapsedMs = (System.nanoTime() - start) / 1000000L;
        assertTrue(elapsedMs < 1500, "the 300ms agent TIMEOUT should cut sleep 2 off well before 2s; took %d ms (agent TIMEOUT not applied?)".formatted(elapsedMs));
        assertTrue(result.isFail(), "sleep 2 under a 300ms agent TIMEOUT must not report success, got: %s".formatted(result));
    }

    // ── bash tool: ENV modulator via q-param ────────────────────────

    @Test
    public void testBashEnvVariableIsVisibleToCommand() {
        final ToolFeature tf = feature();
        final Agent agent = agentWith(tf);
        tf.addTool(mTool.tool(bashTool(null, null, "[MARKO_TEST_VAR=>'metatron-env-value']")));
        final Obj result = bash(agent, tf, "echo $MARKO_TEST_VAR");
        assertFalse(result.isFail(), "env test command should succeed: %s".formatted(result));
        assertTrue(result.toCleanString().contains("metatron-env-value"), "expected the env value in the output, got: %s".formatted(result.toCleanString()));
    }

    @Test
    public void testBashEnvOverridesInheritedVariable() {
        final ToolFeature tf = feature();
        final Agent agent = agentWith(tf);
        tf.addTool(mTool.tool(bashTool(null, null, "[USER=>'metatron-env-user']")));
        final Obj result = bash(agent, tf, "echo $USER");
        assertFalse(result.isFail(), "echo $USER should succeed: %s".formatted(result));
        assertTrue(result.toCleanString().contains("metatron-env-user"), "env USER must override the inherited value, got: %s".formatted(result.toCleanString()));
    }

    @Test
    public void testBashEnvSecretNotLeakedInFailureOutput() {
        final ToolFeature tf = feature();
        final Agent agent = agentWith(tf);
        tf.addTool(mTool.tool(bashTool(null, null, "[SECRET_API_TOKEN=>'top-secret-xyz-9876']")));
        final Obj result = bash(agent, tf, "false");
        assertTrue(result.isFail(), "false must fail: %s".formatted(result));
        final String text = result.toCleanString();
        assertFalse(text.contains("top-secret-xyz-9876"), "the secret env value must not leak into the output: %s".formatted(text));
        assertFalse(text.contains("SECRET_API_TOKEN"), "the env variable name must not leak into the output: %s".formatted(text));
    }
}
