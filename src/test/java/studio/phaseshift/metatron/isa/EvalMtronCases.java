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

package studio.phaseshift.metatron.isa;

import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared {@code eval_mtron} corner-case data + assertion, reused by every avenue
 * that invokes a tool: the full-stack MCP server test ({@code mcp_mtronTest}) and
 * the direct tool-call path ({@code mTool}/{@code mToolExecutor}).
 * <p>
 * Each row is {@code [code, expected]} where {@code expected} is either the literal
 * mtron result or {@code "<ERROR>"} for an expected failure.  The exact result text
 * is not asserted yet (only error/no-error) — tightening it depends on the
 * result round-trip being faithful for objs/rel/type, which is deferred.
 */
public final class EvalMtronCases {

    public static final String[][] CASES = {
            // ── overloaded-string corner cases (str / uri / code / fail) ─────
            {"1", "1"},
            {"\"text with literal placeholder\"", "\"text with literal placeholder\""},
            {"hello", "hello"},
            {"\"hello\"", "hello"},
            {"<hello>", "hello"},
            {"1-<[_,_]", "[1,1]"},
            {"1-<[_,_", "<ERROR>"},
            {"1+a", "<ERROR>"},
            // ── easy: scalar arithmetic ─────────────────────────────────────
            {"1.plus(2)", "3"},
            {"1.plus(1.plus(1))", "3"},
            {"2.plus?dom=int(1)", "3"},
            {"math('1+2')", "3.0"},
            // ── medium: collections ─────────────────────────────────────────
            {"{1,2,3}.plus(2)", "{3,4,5}"},
            {"{1,2,3,4}.take(2)", "{1,2}"},
            {"{1,2,3,4}.skip(2)", "{3,4}"},
            {"{1,2,3,4}.prod()", "24"},
            {"{1,2,3,4}.count()", "4"},
            {"{1,2,3,4}.map(+2)", "{3,4,5,6}"},
            // ── complicated: reduce / repeat / lambda / math ────────────────
            {"{1,2,3,4,5}.reduce(|plus(0))", "15"},
            {"{1,2,3,4,5}.reduce(|mult(2))", "240"},
            {"1.repeat(code=>plus(1),until=>is(gt(10)))", "11"},
            {"{1,2,3,4}.sum{2}().count()", "2"},
            {"1.inst(a=>plus(2)){ plus(*a) }", "4"},
            {"10.to(a).plus(10).to(b).math('a+b')", "30.0"},
            // ── uri merge (bare letters must survive as uri, not str) ───────
            {"{a,b,c}.prod()", "a/b/c"},
            // ── error cases (type/coefficient mismatch) ─────────────────────
            {"{1,2,3}.map?int<=real(1)", "<ERROR>"},
            {"{4}1.plus?int{5}<=int{5}(2)", "<ERROR>"},
    };

    /**
     * Run every case through {@code eval} and assert the error/no-error outcome.
     *
     * @param log   logger for per-case visibility
     * @param label avenue label for the log line (e.g. "http", "ws", "tool")
     * @param eval  maps a code string to its evaluated result (a {@code fail} on error)
     */
    public static void run(final GraphittyLogger log, final String label, final Function<String, Obj> eval) {
        for (final String[] c : CASES) {
            final String code = c[0];
            final String expected = c[1];
            final Obj result = eval.apply(code);
            log.warn("eval_mtron[%s](%s) => %s (expected %s)", label, code, result, expected);
            if ("<ERROR>".equals(expected))
                assertTrue(result.isFail(), label + ": expected fail for code: " + code + " but got: " + result);
            else
                assertFalse(result.isFail(), label + ": expected success for code: " + code + " but got: " + result);
        }
    }

    private EvalMtronCases() {
    }
}
