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

package studio.phaseshift.metatron.algebra.rewrite;

import org.junit.jupiter.params.provider.Arguments;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

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

    /**
     * Returns the URI of this backend's InstSet for fetching rewrite instructions.
     * Default is null (not available). Override in backends that support it.
     */
    default fURI getRewriteInstUri() {
        return null;
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
        final Obj result = ObjmtronSerializer.parse(code).apply();
        assertEquals(expected, result, description);
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
                generateSkipTestCases(),
                generateSkipLimitTestCases(),
                // generateHasTestCases(),  // TODO: has() rewrite pattern needs adjustment
                generateWhereTestCases(),
                generateWhereCountTestCases(),
                generateWhereLimitTestCases(),
                generateWhereOffsetTestCases(),
                generateWhereOffsetLimitTestCases(),
                generateWhereOrderTestCases(),
                generateWhereOrderOffsetTestCases(),
                generateOrderTestCases(),
                generateDedupTestCases(),
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
                Arguments.of("count: all rows", "*" + p + "/+.count()", jnt(10)),
                Arguments.of("count: with id removal", "*" + p + "/+._.count()", jnt(10))
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
                Arguments.of("limit: take(1)", "*" + p + "/+.take(1).count()", jnt(1)),
                Arguments.of("limit: take(2)", "*" + p + "/+.take(2).count()", jnt(2)),
                Arguments.of("limit: take(5)", "*" + p + "/+.take(5).count()", jnt(5)),
                Arguments.of("limit: take(10) all", "*" + p + "/+.take(10).count()", jnt(10)),
                Arguments.of("limit: take(100) > data", "*" + p + "/+.take(100).count()", jnt(10)),
                Arguments.of("limit: take(0)", "*" + p + "/+.take(0).count()", jnt(0)),
                // @ (anchor) — same results via sql_limit rewrite
                Arguments.of("limit: @ take(2)", "@" + p + "/+.take(2).count()", jnt(2)),
                Arguments.of("limit: @ take(5)", "@" + p + "/+.take(5).count()", jnt(5))
        );
    }

    // ========================================================================
    // SKIP/OFFSET REWRITE TEST CASES
    // ========================================================================

    /**
     * Test cases for skip(n)/offset rewrite optimization.
     */
    default Stream<Arguments> generateSkipTestCases() {
        final String p = getTestDataUriPrefix().toString();
        return Stream.of(
                // Basic skip/offset — chain .count() to verify remaining row count
                Arguments.of("skip: skip(0)", "*" + p + "/+.skip(0).count()", jnt(10)),
                Arguments.of("skip: skip(1)", "*" + p + "/+.skip(1).count()", jnt(9)),
                Arguments.of("skip: skip(3)", "*" + p + "/+.skip(3).count()", jnt(7)),
                Arguments.of("skip: skip(5)", "*" + p + "/+.skip(5).count()", jnt(5)),
                Arguments.of("skip: skip(9)", "*" + p + "/+.skip(9).count()", jnt(1)),
                Arguments.of("skip: skip(10) all", "*" + p + "/+.skip(10).count()", jnt(0)),
                Arguments.of("skip: skip(100) past end", "*" + p + "/+.skip(100).count()", jnt(0)),
                // @ (anchor)
                Arguments.of("skip: @ skip(3)", "@" + p + "/+.skip(3).count()", jnt(7))
        );
    }

    // ========================================================================
    // SKIP + LIMIT COMBINED REWRITE TEST CASES
    // ========================================================================

    /**
     * Test cases for combined skip(n).take(m) rewrite optimization.
     * These test the optimization that fuses offset and limit into a single
     * native {@code SELECT ... LIMIT m OFFSET n} operation.
     */
    default Stream<Arguments> generateSkipLimitTestCases() {
        final String p = getTestDataUriPrefix().toString();
        return Stream.of(
                // skip + take combined → LIMIT + OFFSET pagination
                Arguments.of("skip+limit: skip(0).take(5)", "*" + p + "/+.skip(0).take(5).count()", jnt(5)),
                Arguments.of("skip+limit: skip(2).take(3)", "*" + p + "/+.skip(2).take(3).count()", jnt(3)),
                Arguments.of("skip+limit: skip(5).take(5)", "*" + p + "/+.skip(5).take(5).count()", jnt(5)),
                Arguments.of("skip+limit: skip(8).take(5)", "*" + p + "/+.skip(8).take(5).count()", jnt(2)),
                Arguments.of("skip+limit: skip(10).take(3)", "*" + p + "/+.skip(10).take(3).count()", jnt(0)),
                Arguments.of("skip+limit: skip(100).take(10)", "*" + p + "/+.skip(100).take(10).count()", jnt(0)),
                Arguments.of("skip+limit: skip(3).take(0)", "*" + p + "/+.skip(3).take(0).count()", jnt(0))
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
                Arguments.of("has: non-empty collection", "*" + p + "/+.has()", bool(true))
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
                Arguments.of("where: value = 1", "*" + p + "/+.where([value=>1]).count()", jnt(1)),
                Arguments.of("where: value = 5", "*" + p + "/+.where([value=>5]).count()", jnt(1)),
                Arguments.of("where: value = 10", "*" + p + "/+.where([value=>10]).count()", jnt(1)),
                Arguments.of("where: value = 99 (none)", "*" + p + "/+.where([value=>99]).count()", jnt(0)),

                // Greater than
                Arguments.of("where: value > 0", "*" + p + "/+.where([value=>?>0]).count()", jnt(10)),
                Arguments.of("where: value > 5", "*" + p + "/+.where([value=>?>5]).count()", jnt(5)),
                Arguments.of("where: value > 9", "*" + p + "/+.where([value=>?>9]).count()", jnt(1)),
                Arguments.of("where: value > 10 (none)", "*" + p + "/+.where([value=>?>10]).count()", jnt(0)),

                // Less than
                Arguments.of("where: value < 1 (none)", "*" + p + "/+.where([value=>?<1]).count()", jnt(0)),
                Arguments.of("where: value < 3", "*" + p + "/+.where([value=>?<3]).count()", jnt(2)),
                Arguments.of("where: value < 5", "*" + p + "/+.where([value=>?<5]).count()", jnt(4)),
                Arguments.of("where: value < 11 (all)", "*" + p + "/+.where([value=>?<11]).count()", jnt(10)),

                // Greater than or equal
                Arguments.of("where: value >= 1 (all)", "*" + p + "/+.where([value=>?>=1]).count()", jnt(10)),
                Arguments.of("where: value >= 5", "*" + p + "/+.where([value=>?>=5]).count()", jnt(6)),
                Arguments.of("where: value >= 10", "*" + p + "/+.where([value=>?>=10]).count()", jnt(1)),
                Arguments.of("where: value >= 11 (none)", "*" + p + "/+.where([value=>?>=11]).count()", jnt(0)),

                // Less than or equal
                Arguments.of("where: value <= 0 (none)", "*" + p + "/+.where([value=>?<=0]).count()", jnt(0)),
                Arguments.of("where: value <= 1", "*" + p + "/+.where([value=>?<=1]).count()", jnt(1)),
                Arguments.of("where: value <= 5", "*" + p + "/+.where([value=>?<=5]).count()", jnt(5)),
                Arguments.of("where: value <= 10 (all)", "*" + p + "/+.where([value=>?<=10]).count()", jnt(10)),

                // Boolean predicates - commented out: SQLite stores booleans as 0/1 integers
                // Arguments.of("where: active = true",         "*" + p + "/+.where([active=>true]).count()",   jnt(5)),
                // Arguments.of("where: active = false",        "*" + p + "/+.where([active=>false]).count()",  jnt(5)),
                // @ (anchor) — where fires on AT source, then count chains
                Arguments.of("where: @ value = 5", "@" + p + "/+.where([value=>5]).count()", jnt(1))
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
                Arguments.of("where+count: value > 0 (all)", "*" + p + "/+.where([value=>?>0]).count()", jnt(10)),
                Arguments.of("where+count: value > 5", "*" + p + "/+.where([value=>?>5]).count()", jnt(5)),
                Arguments.of("where+count: value > 9", "*" + p + "/+.where([value=>?>9]).count()", jnt(1)),
                Arguments.of("where+count: value > 10", "*" + p + "/+.where([value=>?>10]).count()", jnt(0)),
                Arguments.of("where+count: value < 3", "*" + p + "/+.where([value=>?<3]).count()", jnt(2)),
                // @ (anchor) — where fires on AT source, then sql_where_count composes
                Arguments.of("where+count: @ value > 5", "@" + p + "/+.where([value=>?>5]).count()", jnt(5))
                // Arguments.of("where+count: active=true",     "*" + p + "/+.where([active=>true]).count()",   jnt(5))  // SQLite boolean issue
        );
    }

    // ========================================================================
    // WHERE + LIMIT COMBINED REWRITE TEST CASES
    // ========================================================================

    /**
     * Test cases for combined where().take(n) rewrite optimization.
     * These test the optimization that fuses sql_where and take into
     * a single native WHERE+LIMIT operation.
     *
     * <p>Important: where filters first, then take limits. The count
     * at the end verifies the combined result.
     */
    default Stream<Arguments> generateWhereLimitTestCases() {
        final String p = getTestDataUriPrefix().toString();
        return Stream.of(
                // where filters to 7 rows (values 4-10), take 2
                Arguments.of("where+limit: >3 take(2)", "*" + p + "/+.where([value=>?>3]).take(2).count()", jnt(2)),
                // where filters to 7 rows, take 5
                Arguments.of("where+limit: >3 take(5)", "*" + p + "/+.where([value=>?>3]).take(5).count()", jnt(5)),
                // where filters to 7 rows, take 10 (only 7 available)
                Arguments.of("where+limit: >3 take(10)", "*" + p + "/+.where([value=>?>3]).take(10).count()", jnt(7)),
                // where filters to 5 rows (values 6-10), take 1
                Arguments.of("where+limit: >5 take(1)", "*" + p + "/+.where([value=>?>5]).take(1).count()", jnt(1)),
                // where filters to 5 rows, take 3
                Arguments.of("where+limit: >5 take(3)", "*" + p + "/+.where([value=>?>5]).take(3).count()", jnt(3)),
                // where filters to 5 rows, take 5 (all)
                Arguments.of("where+limit: >5 take(5)", "*" + p + "/+.where([value=>?>5]).take(5).count()", jnt(5)),
                // where filters to 5 rows, take 10 (only 5 available)
                Arguments.of("where+limit: >5 take(10)", "*" + p + "/+.where([value=>?>5]).take(10).count()", jnt(5)),
                // where filters to 2 rows (values 1-2), take 1
                Arguments.of("where+limit: <3 take(1)", "*" + p + "/+.where([value=>?<3]).take(1).count()", jnt(1)),
                // where filters to 2 rows, take 2 (all)
                Arguments.of("where+limit: <3 take(2)", "*" + p + "/+.where([value=>?<3]).take(2).count()", jnt(2)),
                // where filters to 2 rows, take 5 (only 2)
                Arguments.of("where+limit: <3 take(5)", "*" + p + "/+.where([value=>?<3]).take(5).count()", jnt(2)),
                // where matches 0 rows, take anything
                Arguments.of("where+limit: >10 take(1)", "*" + p + "/+.where([value=>?>10]).take(1).count()", jnt(0)),
                // take 0 always yields 0
                Arguments.of("where+limit: >3 take(0)", "*" + p + "/+.where([value=>?>3]).take(0).count()", jnt(0)),
                // where filters to 6 rows (values 5-10), take 1
                Arguments.of("where+limit: >=5 take(1)", "*" + p + "/+.where([value=>?>=5]).take(1).count()", jnt(1)),
                // where filters to 4 rows (values 1-4), take 2
                Arguments.of("where+limit: <5 take(2)", "*" + p + "/+.where([value=>?<5]).take(2).count()", jnt(2)),
                // where filters to 5 rows (values 1-5), take 3
                Arguments.of("where+limit: <=5 take(3)", "*" + p + "/+.where([value=>?<=5]).take(3).count()", jnt(3))
                // Boolean predicates commented out: SQLite stores booleans as 0/1 integers
                // Arguments.of("where+limit: active=true take(2)", "*" + p + "/+.where([active=>true]).take(2).count()", jnt(2))
        );
    }

    // ========================================================================
    // WHERE + OFFSET COMBINED REWRITE TEST CASES
    // ========================================================================

    default Stream<Arguments> generateWhereOffsetTestCases() {
        final String p = getTestDataUriPrefix().toString();
        return Stream.of(
                // where filters to 7 rows (values 4-10), skip slices from there
                Arguments.of("where+offset: >3 skip(0)", "*" + p + "/+.where([value=>?>3]).skip(0).count()", jnt(7)),
                Arguments.of("where+offset: >3 skip(2)", "*" + p + "/+.where([value=>?>3]).skip(2).count()", jnt(5)),
                Arguments.of("where+offset: >3 skip(6)", "*" + p + "/+.where([value=>?>3]).skip(6).count()", jnt(1)),
                Arguments.of("where+offset: >3 skip(10)", "*" + p + "/+.where([value=>?>3]).skip(10).count()", jnt(0)),
                // where filters to 5 rows (values 6-10)
                Arguments.of("where+offset: >5 skip(2)", "*" + p + "/+.where([value=>?>5]).skip(2).count()", jnt(3)),
                // where filters to 2 rows (values 1-2)
                Arguments.of("where+offset: <3 skip(1)", "*" + p + "/+.where([value=>?<3]).skip(1).count()", jnt(1)),
                // where matches 0 rows
                Arguments.of("where+offset: >10 skip(0)", "*" + p + "/+.where([value=>?>10]).skip(0).count()", jnt(0))
        );
    }

    // ========================================================================
    // WHERE + OFFSET + LIMIT COMBINED REWRITE TEST CASES
    // ========================================================================

    default Stream<Arguments> generateWhereOffsetLimitTestCases() {
        final String p = getTestDataUriPrefix().toString();
        return Stream.of(
                // where filters to 7 rows (values 4-10), skip 2, take 3 → rows 6,7,8 (values 6,7,8)
                Arguments.of("where+offset+limit: >3 skip(2).take(3)", "*" + p + "/+.where([value=>?>3]).skip(2).take(3).count()", jnt(3)),
                // where filters to 7 rows, skip 5, take 4 → only 2 available
                Arguments.of("where+offset+limit: >3 skip(5).take(4)", "*" + p + "/+.where([value=>?>3]).skip(5).take(4).count()", jnt(2)),
                // where filters to 7 rows, skip 10, take 3 → none
                Arguments.of("where+offset+limit: >3 skip(10).take(3)", "*" + p + "/+.where([value=>?>3]).skip(10).take(3).count()", jnt(0)),
                // take 0 always yields 0
                Arguments.of("where+offset+limit: >3 skip(2).take(0)", "*" + p + "/+.where([value=>?>3]).skip(2).take(0).count()", jnt(0)),
                // where+offset+limit: values < 5 (4 rows), skip 1, take 2
                Arguments.of("where+offset+limit: <5 skip(1).take(2)", "*" + p + "/+.where([value=>?<5]).skip(1).take(2).count()", jnt(2))
        );
    }

    // ========================================================================
    // ORDER BY REWRITE TEST CASES
    // ========================================================================

    default Stream<Arguments> generateOrderTestCases() {
        final String p = getTestDataUriPrefix().toString();
        return Stream.of(
                // order + count verifies the rewrite fires (ordering doesn't change count)
                Arguments.of("order: by name count", "*" + p + "/+.order(select(name)).count()", jnt(10)),
                // order + take
                Arguments.of("order: by name take(3)", "*" + p + "/+.order(select(name)).take(3).count()", jnt(3)),
                Arguments.of("order: by value take(5)", "*" + p + "/+.order(select(value)).take(5).count()", jnt(5)),
                // order + where + take (order before where)
                Arguments.of("order: by name take(1)", "*" + p + "/+.order(select(name)).take(1).count()", jnt(1))
        );
    }

    // ========================================================================
    // WHERE + ORDER COMBINED REWRITE TEST CASES
    // ========================================================================

    default Stream<Arguments> generateWhereOrderTestCases() {
        final String p = getTestDataUriPrefix().toString();
        return Stream.of(
                // where+order — chain .count() to verify row count (order doesn't change it)
                Arguments.of("where+order: >5 order by value count",
                        "*" + p + "/+.where([value=>?>5]).order(select(value)).count()", jnt(5)),
                Arguments.of("where+order: >3 order by name count",
                        "*" + p + "/+.where([value=>?>3]).order(select(name)).count()", jnt(7)),
                // @ (anchor) — verify VID stamping through composed rewrite
                Arguments.of("where+order: @ >5 order by value count",
                        "@" + p + "/+.where([value=>?>5]).order(select(value)).count()", jnt(5))
        );
    }

    // ========================================================================
    // WHERE + ORDER + OFFSET COMBINED REWRITE TEST CASES
    // ========================================================================

    default Stream<Arguments> generateWhereOrderOffsetTestCases() {
        final String p = getTestDataUriPrefix().toString();
        return Stream.of(
                // where+order+offset — chain .count() to verify row count after skip
                Arguments.of("where+order+offset: >3 order by value skip(2)",
                        "*" + p + "/+.where([value=>?>3]).order(select(value)).skip(2).count()", jnt(5)),
                Arguments.of("where+order+offset: >5 order by name skip(1)",
                        "*" + p + "/+.where([value=>?>5]).order(select(name)).skip(1).count()", jnt(4)),
                Arguments.of("where+order+offset: >3 order by value skip(10) past end",
                        "*" + p + "/+.where([value=>?>3]).order(select(value)).skip(10).count()", jnt(0)),
                // @ (anchor)
                Arguments.of("where+order+offset: @ >3 order by value skip(2)",
                        "@" + p + "/+.where([value=>?>3]).order(select(value)).skip(2).count()", jnt(5))
        );
    }

    // ========================================================================
    // DISTINCT / DEDUP REWRITE TEST CASES
    // ========================================================================

    default Stream<Arguments> generateDedupTestCases() {
        final String p = getTestDataUriPrefix().toString();
        return Stream.of(
                // names are all unique (item1..item10) → all 10 remain
                Arguments.of("dedup: name count", "*" + p + "/+.dedup(select(name)).count()", jnt(10)),
                // values are all unique (1..10) → all 10 remain
                Arguments.of("dedup: value count", "*" + p + "/+.dedup(select(value)).count()", jnt(10)),
                // multi-column dedup (all unique combos → 10)
                Arguments.of("dedup: multi-column", "*" + p + "/+.dedup(select([name=>_,value=>_])).count()", jnt(10))
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
                Arguments.of("sum: all values (1+2+...+10)", "*" + p + "/+>>value.sum()", jnt(55))

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
                Arguments.of("compose: count + 10", "*" + p + "/+.count().plus(10)", jnt(20)),
                Arguments.of("compose: where.count + 10", "*" + p + "/+.where([value=>?>5]).count().plus(10)", jnt(15)),
                Arguments.of("compose: take.count * 2", "*" + p + "/+.take(5).count().mult(2)", jnt(10)),

                // ================================================================
                // id removal (_) + rewrite
                // ================================================================
                Arguments.of("compose: _.count", "*" + p + "/+._.count()", jnt(10)),
                Arguments.of("compose: _.where.count", "*" + p + "/+._.where([value=>?<5]).count()", jnt(4)),
                Arguments.of("compose: _.take.count", "*" + p + "/+._.take(3).count()", jnt(3)),

                // ================================================================
                // Type checking with is() + rewrite
                // ================================================================
                Arguments.of("compose: is(rec).count", "*" + p + "/+.isa(rec::T).count()", jnt(10)),
                Arguments.of("compose: _.isa(rec::T).count", "*" + p + "/+._.isa(rec::T).count()", jnt(10)),
                Arguments.of("compose: isa(rec::T).where.count", "*" + p + "/+.isa(rec::T).where([value=>?>5]).count()", jnt(5)),
                Arguments.of("compose: _.isa(rec::T).where.count", "*" + p + "/+._.isa(rec::T).where([value=>?>5]).count()", jnt(5)),

                // ================================================================
                // Chained where filters
                // ================================================================
                Arguments.of("compose: where.where.count", "*" + p + "/+.where([value=>?>3]).where([value=>?<8]).count()", jnt(4)),  // 4,5,6,7
                Arguments.of("compose: _.where.where.count", "*" + p + "/+._.where([value=>?>2]).where([value=>?<=7]).count()", jnt(5)),  // 3,4,5,6,7

                // ================================================================
                // take + where combinations (take(10) uses all rows to avoid ordering issues)
                // ================================================================
                Arguments.of("compose: take(all).where.count", "*" + p + "/+.take(10).where([value=>?>3]).count()", jnt(7)),  // all 10 rows, filter >3 = 4,5,6,7,8,9,10
                Arguments.of("compose: where.take.count", "*" + p + "/+.where([value=>?>3]).take(3).count()", jnt(3)),  // filter first (7 rows), then take 3 = 3

                // ================================================================
                // Complex arithmetic chains
                // ================================================================
                Arguments.of("compose: count.plus.mult", "*" + p + "/+.count().plus(5).mult(2)", jnt(30)),  // (10+5)*2
                Arguments.of("compose: count.mult.plus", "*" + p + "/+.count().mult(3).plus(7)", jnt(37)),  // 10*3+7
                Arguments.of("compose: where.count.plus.mult", "*" + p + "/+.where([value=>?>5]).count().plus(2).mult(3)", jnt(21)),  // (5+2)*3

                // ================================================================
                // sum with filters
                // ================================================================
                Arguments.of("compose: where.sum", "*" + p + "/+.where([value=>?>5])>>value.sum()", jnt(40)),  // 6+7+8+9+10
                Arguments.of("compose: where.sum(<=5)", "*" + p + "/+.where([value=>?<=5])>>value.sum()", jnt(15)),  // 1+2+3+4+5 (deterministic unlike take)
                Arguments.of("compose: _.where.sum", "*" + p + "/+._.where([value=>?<4])>>value.sum()", jnt(6)),   // 1+2+3

                // ================================================================
                // Multiple id removals and type checks
                // ================================================================
                Arguments.of("compose: _._.count", "*" + p + "/+._._.count()", jnt(10)),
                Arguments.of("compose: _.isa(rec::T)._.count", "*" + p + "/+._.isa(rec::T)._.count()", jnt(10)),
                Arguments.of("compose: _.is(true)._.count", "*" + p + "/+._.is(true)._.count()", jnt(10)),

                // ================================================================
                // Comparison result checks (gt/lt return bool, filter on that)
                // ================================================================
                Arguments.of("compose: count.gt", "*" + p + "/+.count().gt(5)", bool(true)),
                Arguments.of("compose: count.lt", "*" + p + "/+.count().lt(5)", bool(false)),
                Arguments.of("compose: count.gte", "*" + p + "/+.count().gte(10)", bool(true)),
                Arguments.of("compose: count.lte", "*" + p + "/+.count().lte(9)", bool(false)),
                Arguments.of("compose: where.count.gt", "*" + p + "/+.where([value=>?>5]).count().gt(3)", bool(true)),  // 5 > 3

                // ================================================================
                // Nested expressions with large literal values
                // ================================================================
                Arguments.of("compose: count.lt(large)", "*" + p + "/+.count().lt(100000000)", bool(true)),
                Arguments.of("compose: where.count.lt(large)", "*" + p + "/+.where([value=>?>0]).count().lt(999999)", bool(true)),
                Arguments.of("compose: sum.lt(large)", "*" + p + "/+>>value.sum().lt(100000000)", bool(true))
        );
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
