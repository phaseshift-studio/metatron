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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.SkipWhenPortUnavailable;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.AgentTest;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MStr;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_BASH_FEATURE_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_MILLIS_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * {@code BashFeature} — the allow/reject command filter around the bash tool.
 *
 * <p>Two independent guards, both applied before any process is spawned:
 * <ul>
 *   <li><b>allow</b> — a whitelist matched with {@code Pattern.matcher(cmd).matches()}
 *       (whole command must match at least one pattern);</li>
 *   <li><b>reject</b> — a blacklist matched with {@code Pattern.matcher(cmd).find()}
 *       (pattern may appear anywhere in the command line).</li>
 * </ul>
 *
 * <p>The {@code reject} side using {@code find()} is what lets a single
 * {@code \brm\b} entry block {@code rm} in every wrapped position
 * ({@code sudo rm …}, {@code … && rm …}, {@code sh -c 'rm …'}, {@code /bin/rm …})
 * — while a naive whole-command pattern would only block a leading {@code rm}.
 * {@code \b} word boundaries keep it from false-positiving on words that merely
 * end in {@code rm} (e.g. {@code confirm}).
 *
 * <p>Every command that is expected to be rejected targets a harmless,
 * non-existent path; if a guard ever regressed and let it through, the worst
 * case is a failed {@code rm} of nothing — never a destructive run.
 */
@SkipWhenPortUnavailable(value = 11434)
@Isolated
public class BashFeatureTest extends AbstractFeatureTest {

    @Override
    protected BashFeature feature() {
        return new BashFeature(mutableMap(), LLM_BASH_FEATURE_TID, null);
    }

    // ── helpers ────────────────────────────────────────────────────────


    /**
     * Run {@code bashFeature.onBeforeChat}, then return the registered bash
     * {@link Inst} so the test can invoke the tool directly (no LLM in the loop).
     */

    /**
     * Invoke the bash tool with a single command (plus a safe 10s timeout).
     */
    private static Obj run(final Inst bash, final String command) {
        final Obj result = bash.args(rec(uri(CMD), str(command), uri(TIMEOUT), real(10000.0, MATH_MILLIS_TID, null))).apply(noobj());
        STATIC_LOG.debug("result: %s", result);
        return result;
    }

    /**
     * Invoke the bash tool with an explicit agent-supplied TIMEOUT.
     */
    private static Obj run(final Inst bash, final String command, final Obj timeout) {
        final Obj result = bash.args(rec(uri(CMD), str(command), uri(TIMEOUT), timeout)).apply(noobj());
        STATIC_LOG.debug("result: %s", result);
        return result;
    }

    /**
     * Invoke the bash tool with no TIMEOUT argument, so the BashFeature-level
     * TIMEOUT (or the built-in default) is what applies.
     */
    private static Obj runNoAgentTimeout(final Inst bash, final String command) {
        final Obj result = bash.args(rec(uri(CMD), str(command))).apply(noobj());
        STATIC_LOG.debug("result: %s", result);
        return result;
    }

    /**
     * Assert the command was rejected (i.e. the guard threw before any exec).
     */
    private static void assertRejected(final Inst bash, final String command, final String errorMessageFragment) {
        final Obj result = run(bash, command);
        assertTrue(result.isFail(), "expected the guard to reject before exec: " + command);
        assertTrue(result.toCleanString().contains(null == errorMessageFragment ? "" : errorMessageFragment), "expected a reject failure, got: %s".formatted(result.toCleanString()));
    }

    private static void assertAccepted(final Inst bash, final String command, final String resultMessageFragment) {
        final Obj result = run(bash, command);
        assertFalse(result.isFail(), "expected the guard to accept before exec: " + command);
        assertTrue(result.toCleanString().contains(null == resultMessageFragment ? "" : resultMessageFragment), "expected a result fragment, got: %s".formatted(result.toCleanString()));
    }

    @ParameterizedTest
    @CsvSource(value = {
            // command                                                      allow list             reject list         fail?     expected result / error text
            // ── no guard: any command runs ────────────────────────────────────────────────────────────────────────────
            "echo metatron works                                          % null                % null                % false   % metatron works",
            // ── allow: whole-command whitelist (full match) ──────────────────────────────────────────────────────────
            "ls                                                           % .*                  % null                % false   % null",
            "echo metatron bash work                                      % echo .*             % null                % false   % metatron bash work",
            "echo hello world                                             % echo hello world    % null                % false   % hello world",
            "echo hello world extra                                       % echo hello world    % null                % true    % allowed patterns do not match",
            "ls -la                                                       % echo .*             % null                % true    % allowed patterns do not match",
            "echo which pattern won                                       % cat .* ~ echo .*    % null                % false   % which pattern won",
            // ── reject: blacklist, matched anywhere (find) ───────────────────────────────────────────────────────────
            "rm /tmp/metatron_bash_harmless_none                          % null                % \\brm\\b            % true    % reject patterns match",
            "echo prep && rm /tmp/metatron_bash_harmless_none             % null                % \\brm\\b            % true    % reject patterns match",
            "bash -c \"rm /tmp/metatron_bash_harmless_none\"             % null                % \\brm\\b            % true    % reject patterns match",
            "sudo rm /tmp/metatron_bash_harmless_none                     % null                % \\brm\\b            % true    % reject patterns match",
            "mkfs.ext4 /dev/null                                          % null                % \\b(rm|mkfs)\\b     % true    % reject patterns match",
            "echo please confirm the plan                                 % null                % \\brm\\b            % false   % please confirm the plan",
            "echo metatron is safe                                        % null                % \\brm\\b            % false   % metatron is safe",
            // ── both allow AND reject: allow passes, reject still guards ─────────────────────────────────────────────
            "echo hello                                                   % echo .* ~ ls .*     % \\brm\\b            % false   % hello",
            "echo hi && rm /tmp/metatron_bash_harmless_none               % echo .* ~ ls .*     % \\brm\\b            % true    % reject patterns match",
            "echo all clear                                               % echo .* ~ ls .*     % \\bsecret\\b         % false   % all clear",
            "echo secret_free                                             % echo .* ~ ls .*     % \\bsecret\\b         % false   % secret_free",
            "echo the secret is out                                       % echo .* ~ ls .*     % \\bsecret\\b         % true    % reject patterns match",
            // ── bash -c shell semantics: variables, substitution, quotes, ;, pipes, redirect, benign stderr ──────────
            "x=metatron; echo $x                                          % .*                  % null                % false   % metatron",
            "echo $(echo inner)                                           % .*                  % null                % false   % inner",
            "echo 'single quoted'                                         % .*                  % null                % false   % single quoted",
            "echo a; echo b                                               % .*                  % null                % false   % b",
            "echo abc12 | grep 1                                          % .*                  % null                % false   % abc12",
            "echo redirected > /tmp/metatron_bash_redirect_none.txt       % .*                  % null                % false   % null",
            "dd if=/dev/null of=/dev/null                                 % .*                  % null                % false   % null"
    }, delimiter = '%', nullValues = "null")
    public void testCommandRejectAllow(final String command, final String allowList, final String rejectList, final boolean fail, final String messageFragment) {
        final Lst allowed = null == allowList ? lst() : lst(Arrays.stream(allowList.split("~")).map(String::trim).map(MStr::str));
        final Lst rejected = null == rejectList ? lst() : lst(Arrays.stream(rejectList.split("~")).map(String::trim).map(MStr::str));
        final BashFeature feature = feature(rec(uri(ALLOW), allowed, uri(REJECT), rejected));
        final Agent agent = agentWith(feature, AgentTest.toolFeature());
        final Inst bash = AgentTest.findTool(agent, feature, "bash");
        if (fail) {
            assertRejected(bash, command, messageFragment);
        } else {
            assertAccepted(bash, command, messageFragment);
        }

    }

    /// #3 — a non-zero exit status must surface as a fail (and no longer throw on benign stderr).
    @Test
    public void testNonZeroExitSurfacesAsFail() {
        final BashFeature feature = feature(rec(uri(ALLOW), lst(str(".*"))));
        final Agent agent = agentWith(feature, AgentTest.toolFeature());
        final Inst bash = AgentTest.findTool(agent, feature, "bash");
        final Obj result = run(bash, "false");
        assertTrue(result.isFail(), "a non-zero exit must surface as a fail, got: %s".formatted(result));
        assertTrue(result.toCleanString().contains("terminated with unexpected exit"), "expected the exit status in the failure text, got: %s".formatted(result.toCleanString()));
    }

    // ── TIMEOUT: agent-supplied vs BashFeature-level ──────────────────────

    /// The agent's TIMEOUT must be applied: shorter than a slow command, it cuts it off.
    @Test
    public void testAgentTimeoutKillsSlowCommand() {
        final BashFeature feature = feature(rec(uri(ALLOW), lst(str(".*"))));
        final Agent agent = agentWith(feature, AgentTest.toolFeature());
        final Inst bash = AgentTest.findTool(agent, feature, "bash");
        final long start = System.nanoTime();
        final Obj result = run(bash, "sleep 2", real(300.0, MATH_MILLIS_TID, null));
        final long elapsedMs = (System.nanoTime() - start) / 1000000L;
        assertTrue(elapsedMs < 1500, "the 300ms agent TIMEOUT should cut sleep 2 off well before 2s; took %d ms (agent TIMEOUT not applied?)".formatted(elapsedMs));
        assertTrue(result.isFail(), "sleep 2 under a 300ms agent TIMEOUT must not report success, got: %s".formatted(result));
    }

    /// The agent's TIMEOUT must override a shorter BashFeature-level TIMEOUT.
    @Test
    public void testAgentTimeoutOverridesFeatureTimeout() {
        final BashFeature feature = feature(rec(uri(ALLOW), lst(str(".*")), uri(TIMEOUT), real(300.0, MATH_MILLIS_TID, null)));
        final Agent agent = agentWith(feature, AgentTest.toolFeature());
        final Inst bash = AgentTest.findTool(agent, feature, "bash");
        final long start = System.nanoTime();
        final Obj result = run(bash, "sleep 1", real(3000.0, MATH_MILLIS_TID, null));
        final long elapsedMs = (System.nanoTime() - start) / 1000000L;
        assertFalse(result.isFail(), "agent TIMEOUT (3000ms) must override the feature TIMEOUT (300ms) so sleep 1 completes; got: %s".formatted(result));
        assertTrue(elapsedMs >= 800, "sleep 1 should run to completion (~1s) under the 3000ms agent TIMEOUT, not be cut at 300ms; took %d ms".formatted(elapsedMs));
    }

    /// When the agent omits TIMEOUT, the BashFeature-level TIMEOUT must apply (not the 30s built-in default).
    @Test
    public void testFeatureTimeoutAppliesWhenAgentOmitsTimeout() {
        final BashFeature feature = feature(rec(uri(ALLOW), lst(str(".*")), uri(TIMEOUT), real(300.0, MATH_MILLIS_TID, null)));
        final Agent agent = agentWith(feature, AgentTest.toolFeature());
        final Inst bash = AgentTest.findTool(agent, feature, "bash");
        final long start = System.nanoTime();
        final Obj result = runNoAgentTimeout(bash, "sleep 2");
        final long elapsedMs = (System.nanoTime() - start) / 1000000L;
        assertTrue(elapsedMs < 1500, "the 300ms feature TIMEOUT should cut sleep 2 off well before 2s; took %d ms (feature TIMEOUT not applied?)".formatted(elapsedMs));
        assertTrue(result.isFail(), "sleep 2 under a 300ms feature TIMEOUT must not report success, got: %s".formatted(result));
    }

    // ── ENV: environment variables (uri => value) applied to every bash call ──

    /// A feature-level env var must be visible to the command.
    @Test
    public void testEnvVariableIsVisibleToCommand() {
        final BashFeature feature = feature(rec(uri(ENV), rec(uri("MARKO_TEST_VAR"), str("metatron-env-value"))));
        final Agent agent = agentWith(feature, AgentTest.toolFeature());
        final Inst bash = AgentTest.findTool(agent, feature, "bash");
        final Obj result = run(bash, "echo $MARKO_TEST_VAR");
        assertFalse(result.isFail(), "env test command should succeed: %s".formatted(result));
        assertTrue(result.toCleanString().contains("metatron-env-value"), "expected the env value in the output, got: %s".formatted(result.toCleanString()));
    }

    /// A feature-level env var must override an inherited one (here USER) for the command.
    @Test
    public void testEnvOverridesInheritedVariable() {
        final BashFeature feature = feature(rec(uri(ENV), rec(uri("USER"), str("metatron-env-user"))));
        final Agent agent = agentWith(feature, AgentTest.toolFeature());
        final Inst bash = AgentTest.findTool(agent, feature, "bash");
        final Obj result = run(bash, "echo $USER");
        assertFalse(result.isFail(), "echo $USER should succeed: %s".formatted(result));
        assertTrue(result.toCleanString().contains("metatron-env-user"), "env USER must override the inherited value, got: %s".formatted(result.toCleanString()));
    }

    /// A secret-ish env value must not leak into the tool's output / failure text.
    @Test
    public void testEnvSecretNotLeakedInFailureOutput() {
        final BashFeature feature = feature(rec(uri(ENV), rec(uri("SECRET_API_TOKEN"), str("top-secret-xyz-9876"))));
        final Agent agent = agentWith(feature, AgentTest.toolFeature());
        final Inst bash = AgentTest.findTool(agent, feature, "bash");
        final Obj result = run(bash, "false");
        assertTrue(result.isFail(), "false must fail: %s".formatted(result));
        final String text = result.toCleanString();
        assertFalse(text.contains("top-secret-xyz-9876"), "the secret env value must not leak into the output: %s".formatted(text));
        assertFalse(text.contains("SECRET_API_TOKEN"), "the env variable name must not leak into the output: %s".formatted(text));
    }
}
