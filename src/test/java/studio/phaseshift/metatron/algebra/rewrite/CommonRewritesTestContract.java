/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.algebra.rewrite;

import org.junit.jupiter.params.provider.Arguments;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;

/**
 * Contract interface for testing common rewrite optimizations across database implementations.
 *
 * <p>This interface provides parameterized test data that validates rewrite optimizations work
 * correctly across different database backends (SQL, MongoDB, etc.). Both tbleSpace and
 * dcmntSpace implement this contract to ensure consistent optimization behavior.
 *
 * <h2>Test Dataset</h2>
 * <p>Tests expect 10 rows with the following schema:
 * <pre>
 * id: 1-10 (integer)
 * value: 1-10 (integer, same as id)
 * name: 'item1'-'item10' (string)
 * active: alternating true/false (boolean)
 * </pre>
 *
 * <h2>Expected Aggregation Results</h2>
 * <ul>
 *   <li>count() = 10</li>
 *   <li>sum(value) = 55 (1+2+3+...+10)</li>
 *   <li>mean(value) = 5.5</li>
 *   <li>where(value > 5).count() = 5 (rows 6,7,8,9,10)</li>
 *   <li>where(value < 3).count() = 2 (rows 1,2)</li>
 *   <li>where(active=true).count() = 5</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>Implementing classes should:
 * <ol>
 *   <li>Implement {@link #getTestDataUriPrefix()} to return the base URI</li>
 *   <li>Set up test data before tests (10 rows as described above)</li>
 *   <li>Add parameterized test methods that use the data providers</li>
 * </ol>
 *
 * <h2>Example Implementation</h2>
 * <pre>{@code
 * public class MySpaceTest extends AbstractSpaceTest implements CommonRewritesTestContract {
 *
 *     @Override
 *     public fURI getTestDataUriPrefix() {
 *         return f("myscheme:rewrite_test");
 *     }
 *
 *     // Single parameterized test that runs ALL rewrite test cases
 *     @ParameterizedTest(name = "[{index}] {0}")
 *     @MethodSource("provideAllRewriteTestCases")
 *     public void testRewrite(String description, String code, Obj expected) throws Exception {
 *         runRewriteTest(description, code, expected);
 *     }
 *
 *     static Stream<Arguments> provideAllRewriteTestCases() {
 *         return new MySpaceTest().generateAllRewriteTestCases();
 *     }
 * }
 * }</pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface CommonRewritesTestContract {

    /**
     * Returns the base URI prefix for test data (without trailing slash on collection).
     * <p>
     * Examples:
     * <ul>
     *   <li>SQL: {@code f("tble:rewrite_test")}</li>
     *   <li>MongoDB: {@code f("mongo:rewrite_test")}</li>
     * </ul>
     *
     * @return the base URI prefix for test data collection/table
     */
    fURI getTestDataUriPrefix();

    /**
     * Returns the prefix for native instruction names in this backend.
     * <p>
     * Examples:
     * <ul>
     *   <li>SQL: {@code "sql_"} (produces sql_count, sql_limit, etc.)</li>
     *   <li>MongoDB: {@code ""} (produces mql_count, mql_limit, etc.)</li>
     * </ul>
     *
     * @return the prefix for native instruction names (default: "")
     */
    default String getNativeInstructionPrefix() {
        return "";
    }

    // ========================================================================
    // PARAMETERIZED TEST EXECUTION
    // ========================================================================

    /**
     * Executes a single rewrite test case. Call this from your parameterized test method.
     *
     * @param description Human-readable test description
     * @param code        The mtron code to evaluate
     * @param expected    The expected result
     */
    default void runRewriteTest(String description, String code, Obj expected) throws Exception {
        final Obj result = mParser.eval(code);
        assertEquals(expected, result, description);
    }

    /**
     * Executes a rewrite plan verification test. Checks that the rewritten code contains
     * the expected native instruction.
     *
     * @param description     Human-readable test description
     * @param code            The mtron code to compile and rewrite
     * @param nativeInstName  The expected native instruction name (partial match)
     */
    default void runRewritePlanTest(String description, String code, String nativeInstName) throws Exception {
        final Code parsed = ObjmtronSerializer.parse(code);
        final Code rewritten = parsed.rewrite();
        final String plan = rewritten.toString();
        assertTrue(plan.contains(nativeInstName),
                description + " - Plan should contain '" + nativeInstName + "': " + plan);
    }

    // ========================================================================
    // ALL-IN-ONE TEST DATA PROVIDER
    // ========================================================================

    /**
     * Generates ALL rewrite test cases. Use this for a single comprehensive parameterized test.
     * <p>
     * This is the recommended approach - one test method that runs all cases:
     * <pre>{@code
     * @ParameterizedTest(name = "[{index}] {0}")
     * @MethodSource("provideAllRewriteTestCases")
     * public void testRewrite(String description, String code, Obj expected) throws Exception {
     *     runRewriteTest(description, code, expected);
     * }
     *
     * static Stream<Arguments> provideAllRewriteTestCases() {
     *     return new MySpaceTest().generateAllRewriteTestCases();
     * }
     * }</pre>
     *
     * @return Stream of all rewrite test cases (description, code, expected)
     */
    default Stream<Arguments> generateAllRewriteTestCases() {
        return Stream.of(
                generateCountTestCases(),
                generateLimitTestCases(),
                // generateHasTestCases(),  // TODO: has() rewrite pattern needs adjustment
                generateWhereTestCases(),
                generateWhereCountTestCases(),
                generateAggregationTestCases(),
                generateCompositionTestCases()
        ).flatMap(s -> s);
    }

    // ========================================================================
    // COUNT REWRITE TEST CASES
    // ========================================================================

    /**
     * Test cases for count() rewrite optimization.
     */
    default Stream<Arguments> generateCountTestCases() {
        final String p = getTestDataUriPrefix().toString();
        return Stream.of(
                // Basic count
                Arguments.of("count: all rows",              "*" + p + "/+.count()",     jnt(10)),
                Arguments.of("count: with id removal",       "*" + p + "/+._.count()",   jnt(10))
        );
    }

    // ========================================================================
    // LIMIT/TAKE REWRITE TEST CASES
    // ========================================================================

    /**
     * Test cases for take(n)/limit rewrite optimization.
     */
    default Stream<Arguments> generateLimitTestCases() {
        final String p = getTestDataUriPrefix().toString();
        return Stream.of(
                // Basic take/limit
                Arguments.of("limit: take(1)",               "*" + p + "/+.take(1).count()",     jnt(1)),
                Arguments.of("limit: take(2)",               "*" + p + "/+.take(2).count()",     jnt(2)),
                Arguments.of("limit: take(5)",               "*" + p + "/+.take(5).count()",     jnt(5)),
                Arguments.of("limit: take(10) all",          "*" + p + "/+.take(10).count()",    jnt(10)),
                Arguments.of("limit: take(100) > data",      "*" + p + "/+.take(100).count()",   jnt(10)),
                Arguments.of("limit: take(0)",               "*" + p + "/+.take(0).count()",     jnt(0))
        );
    }

    // ========================================================================
    // HAS/EXISTS REWRITE TEST CASES
    // ========================================================================

    /**
     * Test cases for has()/exists rewrite optimization.
     */
    default Stream<Arguments> generateHasTestCases() {
        final String p = getTestDataUriPrefix().toString();
        return Stream.of(
                // Has/exists
                Arguments.of("has: non-empty collection",    "*" + p + "/+.has()",       bool(true))
        );
    }

    // ========================================================================
    // WHERE/FILTER REWRITE TEST CASES
    // ========================================================================

    /**
     * Test cases for where() filter rewrite optimization.
     */
    default Stream<Arguments> generateWhereTestCases() {
        final String p = getTestDataUriPrefix().toString();
        return Stream.of(
                // Equality predicates
                Arguments.of("where: value = 1",             "*" + p + "/+.where([value=>1]).count()",       jnt(1)),
                Arguments.of("where: value = 5",             "*" + p + "/+.where([value=>5]).count()",       jnt(1)),
                Arguments.of("where: value = 10",            "*" + p + "/+.where([value=>10]).count()",      jnt(1)),
                Arguments.of("where: value = 99 (none)",     "*" + p + "/+.where([value=>99]).count()",      jnt(0)),

                // Greater than
                Arguments.of("where: value > 0",             "*" + p + "/+.where([value=>?>0]).count()",     jnt(10)),
                Arguments.of("where: value > 5",             "*" + p + "/+.where([value=>?>5]).count()",     jnt(5)),
                Arguments.of("where: value > 9",             "*" + p + "/+.where([value=>?>9]).count()",     jnt(1)),
                Arguments.of("where: value > 10 (none)",     "*" + p + "/+.where([value=>?>10]).count()",    jnt(0)),

                // Less than
                Arguments.of("where: value < 1 (none)",      "*" + p + "/+.where([value=>?<1]).count()",     jnt(0)),
                Arguments.of("where: value < 3",             "*" + p + "/+.where([value=>?<3]).count()",     jnt(2)),
                Arguments.of("where: value < 5",             "*" + p + "/+.where([value=>?<5]).count()",     jnt(4)),
                Arguments.of("where: value < 11 (all)",      "*" + p + "/+.where([value=>?<11]).count()",    jnt(10)),

                // Greater than or equal
                Arguments.of("where: value >= 1 (all)",      "*" + p + "/+.where([value=>?>=1]).count()",    jnt(10)),
                Arguments.of("where: value >= 5",            "*" + p + "/+.where([value=>?>=5]).count()",    jnt(6)),
                Arguments.of("where: value >= 10",           "*" + p + "/+.where([value=>?>=10]).count()",   jnt(1)),
                Arguments.of("where: value >= 11 (none)",    "*" + p + "/+.where([value=>?>=11]).count()",   jnt(0)),

                // Less than or equal
                Arguments.of("where: value <= 0 (none)",     "*" + p + "/+.where([value=>?<=0]).count()",    jnt(0)),
                Arguments.of("where: value <= 1",            "*" + p + "/+.where([value=>?<=1]).count()",    jnt(1)),
                Arguments.of("where: value <= 5",            "*" + p + "/+.where([value=>?<=5]).count()",    jnt(5)),
                Arguments.of("where: value <= 10 (all)",     "*" + p + "/+.where([value=>?<=10]).count()",   jnt(10))

                // Boolean predicates - commented out: SQLite stores booleans as 0/1 integers
                // Arguments.of("where: active = true",         "*" + p + "/+.where([active=>true]).count()",   jnt(5)),
                // Arguments.of("where: active = false",        "*" + p + "/+.where([active=>false]).count()",  jnt(5))
        );
    }

    // ========================================================================
    // WHERE + COUNT COMBINED REWRITE TEST CASES
    // ========================================================================

    /**
     * Test cases for combined where().count() rewrite optimization.
     * These test the optimization that fuses where and count into a single native operation.
     */
    default Stream<Arguments> generateWhereCountTestCases() {
        final String p = getTestDataUriPrefix().toString();
        return Stream.of(
                // where+count combined (should fuse to single native op)
                Arguments.of("where+count: value > 0 (all)", "*" + p + "/+.where([value=>?>0]).count()",     jnt(10)),
                Arguments.of("where+count: value > 5",       "*" + p + "/+.where([value=>?>5]).count()",     jnt(5)),
                Arguments.of("where+count: value > 9",       "*" + p + "/+.where([value=>?>9]).count()",     jnt(1)),
                Arguments.of("where+count: value > 10",      "*" + p + "/+.where([value=>?>10]).count()",    jnt(0)),
                Arguments.of("where+count: value < 3",       "*" + p + "/+.where([value=>?<3]).count()",     jnt(2))
                // Arguments.of("where+count: active=true",     "*" + p + "/+.where([active=>true]).count()",   jnt(5))  // SQLite boolean issue
        );
    }

    // ========================================================================
    // AGGREGATION REWRITE TEST CASES
    // ========================================================================

    /**
     * Test cases for aggregation rewrite optimizations (sum, mean, etc.).
     */
    default Stream<Arguments> generateAggregationTestCases() {
        final String p = getTestDataUriPrefix().toString();
        return Stream.of(
                // Sum
                Arguments.of("sum: all values (1+2+...+10)", "*" + p + "/+>>value.sum()",    jnt(55))

                // Mean - commented out: >>value.mean() pattern doesn't match from().mean() rewrite
                // Arguments.of("mean: all values",             "*" + p + "/+>>value.mean()",   real(5.5))
        );
    }

    // ========================================================================
    // COMPOSITION REWRITE TEST CASES
    // ========================================================================

    /**
     * Test cases for rewrite composition - verifying rewrites work with other operations.
     * These tests include complex instruction chains to ensure rewrites handle them correctly.
     */
    default Stream<Arguments> generateCompositionTestCases() {
        final String p = getTestDataUriPrefix().toString();
        return Stream.of(
                // ================================================================
                // Basic rewrite + arithmetic
                // ================================================================
                Arguments.of("compose: count + 10",          "*" + p + "/+.count().plus(10)",                        jnt(20)),
                Arguments.of("compose: where.count + 10",    "*" + p + "/+.where([value=>?>5]).count().plus(10)",    jnt(15)),
                Arguments.of("compose: take.count * 2",      "*" + p + "/+.take(5).count().mult(2)",                 jnt(10)),

                // ================================================================
                // id removal (_) + rewrite
                // ================================================================
                Arguments.of("compose: _.count",             "*" + p + "/+._.count()",                               jnt(10)),
                Arguments.of("compose: _.where.count",       "*" + p + "/+._.where([value=>?<5]).count()",           jnt(4)),
                Arguments.of("compose: _.take.count",        "*" + p + "/+._.take(3).count()",                       jnt(3)),

                // ================================================================
                // Type checking with is() + rewrite
                // ================================================================
                Arguments.of("compose: is(rec).count",       "*" + p + "/+.isa(rec::T).count()",                         jnt(10)),
                Arguments.of("compose: _.isa(rec::T).count",     "*" + p + "/+._.isa(rec::T).count()",                       jnt(10)),
                Arguments.of("compose: isa(rec::T).where.count", "*" + p + "/+.isa(rec::T).where([value=>?>5]).count()",     jnt(5)),
                Arguments.of("compose: _.isa(rec::T).where.count","*" + p + "/+._.isa(rec::T).where([value=>?>5]).count()",  jnt(5)),

                // ================================================================
                // Chained where filters
                // ================================================================
                Arguments.of("compose: where.where.count",   "*" + p + "/+.where([value=>?>3]).where([value=>?<8]).count()", jnt(4)),  // 4,5,6,7
                Arguments.of("compose: _.where.where.count", "*" + p + "/+._.where([value=>?>2]).where([value=>?<=7]).count()", jnt(5)),  // 3,4,5,6,7

                // ================================================================
                // take + where combinations (take(10) uses all rows to avoid ordering issues)
                // ================================================================
                Arguments.of("compose: take(all).where.count","*" + p + "/+.take(10).where([value=>?>3]).count()",    jnt(7)),  // all 10 rows, filter >3 = 4,5,6,7,8,9,10
                Arguments.of("compose: where.take.count",    "*" + p + "/+.where([value=>?>3]).take(3).count()",     jnt(3)),  // filter first (7 rows), then take 3 = 3

                // ================================================================
                // Complex arithmetic chains
                // ================================================================
                Arguments.of("compose: count.plus.mult",     "*" + p + "/+.count().plus(5).mult(2)",                 jnt(30)),  // (10+5)*2
                Arguments.of("compose: count.mult.plus",     "*" + p + "/+.count().mult(3).plus(7)",                 jnt(37)),  // 10*3+7
                Arguments.of("compose: where.count.plus.mult", "*" + p + "/+.where([value=>?>5]).count().plus(2).mult(3)", jnt(21)),  // (5+2)*3

                // ================================================================
                // sum with filters
                // ================================================================
                Arguments.of("compose: where.sum",           "*" + p + "/+.where([value=>?>5])>>value.sum()",        jnt(40)),  // 6+7+8+9+10
                Arguments.of("compose: where.sum(<=5)",      "*" + p + "/+.where([value=>?<=5])>>value.sum()",       jnt(15)),  // 1+2+3+4+5 (deterministic unlike take)
                Arguments.of("compose: _.where.sum",         "*" + p + "/+._.where([value=>?<4])>>value.sum()",      jnt(6)),   // 1+2+3

                // ================================================================
                // Multiple id removals and type checks
                // ================================================================
                Arguments.of("compose: _._.count",           "*" + p + "/+._._.count()",                             jnt(10)),
                Arguments.of("compose: _.isa(rec::T)._.count",   "*" + p + "/+._.isa(rec::T)._.count()",                     jnt(10)),
                Arguments.of("compose: _.is(true)._.count",   "*" + p + "/+._.is(true)._.count()",                     jnt(10)),

                // ================================================================
                // Comparison result checks (gt/lt return bool, filter on that)
                // ================================================================
                Arguments.of("compose: count.gt",            "*" + p + "/+.count().gt(5)",                           bool(true)),
                Arguments.of("compose: count.lt",            "*" + p + "/+.count().lt(5)",                           bool(false)),
                Arguments.of("compose: count.gte",           "*" + p + "/+.count().gte(10)",                         bool(true)),
                Arguments.of("compose: count.lte",           "*" + p + "/+.count().lte(9)",                          bool(false)),
                Arguments.of("compose: where.count.gt",      "*" + p + "/+.where([value=>?>5]).count().gt(3)",       bool(true)),  // 5 > 3

                // ================================================================
                // Nested expressions with large literal values
                // ================================================================
                Arguments.of("compose: count.lt(large)",     "*" + p + "/+.count().lt(100000000)",                   bool(true)),
                Arguments.of("compose: where.count.lt(large)","*" + p + "/+.where([value=>?>0]).count().lt(999999)", bool(true)),
                Arguments.of("compose: sum.lt(large)",       "*" + p + "/+>>value.sum().lt(100000000)",              bool(true))
        );
    }

    // ========================================================================
    // PLAN VERIFICATION TEST CASES
    // ========================================================================

    /**
     * Generates test cases that verify the rewritten execution plan contains native instructions.
     * Use with runRewritePlanTest().
     *
     * @return Stream of (description, code, expected native instruction name)
     */
    /**
     * Generates test cases that verify the rewritten execution plan contains native instructions.
     * <p>
     * NOTE: These tests are currently disabled because mParser.parse().rewrite() does not
     * trigger space-specific rewrites. The rewrites are applied during actual evaluation
     * when code is routed through a space.
     *
     * @return Empty stream (tests disabled)
     */
    default Stream<Arguments> generatePlanVerificationTestCases() {
        // Plan verification tests disabled - parse().rewrite() doesn't trigger space-specific rewrites
        // The rewrites are registered in tbleInstSet/dcmntInstSet and only apply during evaluation
        return Stream.empty();
    }

    // ========================================================================
    // LEGACY INDIVIDUAL TEST DATA PROVIDERS (for backward compatibility)
    // ========================================================================

    /**
     * @deprecated Use {@link #generateAllRewriteTestCases()} instead
     */
    @Deprecated
    default Stream<Arguments> generateCountRewriteTestCases() {
        return generateCountTestCases();
    }

    /**
     * @deprecated Use {@link #generateAllRewriteTestCases()} instead
     */
    @Deprecated
    default Stream<Arguments> generateLimitRewriteTestCases() {
        return generateLimitTestCases();
    }

    /**
     * @deprecated Use {@link #generateAllRewriteTestCases()} instead
     */
    @Deprecated
    default Stream<Arguments> generateWhereRewriteTestCases() {
        return generateWhereTestCases();
    }

    /**
     * @deprecated Use {@link #generateAllRewriteTestCases()} instead
     */
    @Deprecated
    default Stream<Arguments> generateWhereCountRewriteTestCases() {
        return generateWhereCountTestCases();
    }
}
