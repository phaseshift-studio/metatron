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

import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Objs;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Common rewrite patterns that work across multiple database types.
 *
 * <p>This class provides factory methods for creating standard optimization rewrites
 * (count, sum, mean) that can be reused across different database implementations.
 * Each factory method takes a database-specific function that performs the actual
 * native operation.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // In tbleInstSet:
 * CommonRewrites.countRewrite(
 *     tbleSpace.class,
 *     TBLE_ISA_REWRITE_TID.extend("native_count"),
 *     (space, furi) -> {
 *         String table = furi.segments().getFirst();
 *         try (Statement stmt = space.sjvm().createStatement();
 *              ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
 *             return rs.next() ? (long) rs.getInt(1) : 0L;
 *         }
 *     }
 * )
 *
 * // In dcmntInstSet:
 * CommonRewrites.countRewrite(
 *     dcmntSpace.class,
 *     DOC_ISA_REWRITE_TID.extend("mql_count"),
 *     (space, furi) -> space.database.getCollection(furi.segments().getFirst()).countDocuments()
 * )
 * }</pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class CommonRewrites {

    private CommonRewrites() {
        // Utility class - no instantiation
    }

    /**
     * Create a count optimization rewrite.
     *
     * <p>Optimizes {@code from(furi).count()} to use native database COUNT operations
     * instead of loading all records and counting in memory.
     *
     * @param spaceType     The database space type (e.g., tbleSpace.class, dcmntSpace.class)
     * @param rewriteTid    The type ID for this specific rewrite
     * @param countFunction Function that executes the native count operation
     * @param <S>           The space type
     * @return The rewrite instruction
     */
    public static <S extends Space> Inst countRewrite(
            final Class<S> spaceType,
            final fURI rewriteTid,
            final BiFunction<S, DataPath, Long> countFunction) {
        return countRewrite(spaceType, rewriteTid, countFunction, null);
    }

    /**
     * Create a count optimization rewrite with an optional space-aware guard.
     *
     * @param matchSpacePredicate optional predicate receiving the typed space and
     *                            matched instructions; return {@code false} to skip
     *                            the rewrite (e.g., when the collection is not a
     *                            real table)
     * @see #countRewrite(Class, fURI, BiFunction)
     */
    public static <S extends Space> Inst countRewrite(
            final Class<S> spaceType,
            final fURI rewriteTid,
            final BiFunction<S, DataPath, Long> countFunction,
            final BiPredicate<S, List<Inst>> matchSpacePredicate) {

        return RewriteBuilder.forDatabase(spaceType)
                .tid(rewriteTid)
                .rng(INT_TID)
                // ALL (wildcard) catches both FROM_INST_TID (*) and AT_INST_TID (@);
                // the matchPredicate below rejects non-source instructions.
                .match(ALL, COUNT_INST_TID)
                .matchPredicate(matches -> {
                    final Inst first = matches.getFirst().asInst();
                    // Only FROM (*) and AT (@) instructions carry a data-path URI
                    if (!first.tid().test(FROM_INST_TID) && !first.tid().test(AT_INST_TID))
                        return false;
                    final Obj ref = first.arg(0);
                    // only apply when the path ends at collection level (no field/extensions)
                    // URIs with extensions (e.g., /V/1/OUT/+) represent traversals, not simple counts
                    if (!ref.isUri()) return true;
                    // Resolve through the space first so that space-prefixed paths
                    // (e.g., /usr/dr/message/+) are correctly decomposed — otherwise
                    // the space prefix segments are misidentified as collection/entry,
                    // and the real collection lands in "field", blocking the rewrite.
                    final fURI rawUri = ref.uriValue();
                    final Space space = studio.phaseshift.metatron.isa.mach.type.Router.global().getSpaceFor(rawUri);
                    final fURI resolvedUri = space != null ? space.redirect(rawUri, true) : rawUri;
                    final DataPath dp = DataPath.withoutDB(resolvedUri);
                    // An empty field segment (from asBranch() trailing "/")
                    // is a branch marker, not a real field — allow it.
                    return !dp.collectionIsWildcard()
                            && !dp.hasExtension()
                            && (dp.field() == null || dp.field().isEmpty());
                })
                .matchSpacePredicate(matchSpacePredicate)
                .optimize("from_count", (space, dp, coeff) -> {
                    final long count = countFunction.apply(space, dp);
                    return jnt(count).c(c -> c.mult((cInt) coeff));
                })
                .build();
    }

    /**
     * Create a sum optimization rewrite.
     *
     * <p>Optimizes {@code from(furi).sum()} to use native database SUM operations
     * instead of loading all records and summing in memory.
     *
     * @param spaceType   The database space type
     * @param rewriteTID  The type ID for this specific rewrite
     * @param sumFunction Function that executes the native sum operation
     * @param <S>         The space type
     * @return The rewrite instruction
     */
    public static <S extends Space> Inst sumRewrite(
            final Class<S> spaceType,
            final fURI rewriteTID,
            final BiFunction<S, DataPath, Number> sumFunction) {
        return sumRewrite(spaceType, rewriteTID, sumFunction, null);
    }

    public static <S extends Space> Inst sumRewrite(
            final Class<S> spaceType,
            final fURI rewriteTID,
            final BiFunction<S, DataPath, Number> sumFunction,
            final BiPredicate<S, List<Inst>> matchSpacePredicate) {

        return RewriteBuilder.forDatabase(spaceType)
                .tid(rewriteTID)
                .rng(A)
                .match(FROM_INST_TID, SUM_INST_TID)
                .matchFromOrAt()
                .matchSpacePredicate(matchSpacePredicate)
                .optimize("from_sum", (space, dp, coeff) -> {
                    final Number sum = sumFunction.apply(space, dp);
                    return (sum instanceof Double || sum instanceof Float)
                            ? real(sum.doubleValue())
                            : jnt(sum.longValue());
                })
                .build();
    }

    /**
     * Create a mean (average) optimization rewrite.
     *
     * <p>Optimizes {@code from(furi).mean()} to use native database AVG operations
     * instead of loading all records and computing the mean in memory.
     *
     * @param spaceType    The database space type
     * @param rewriteTID   The type ID for this specific rewrite
     * @param meanFunction Function that executes the native mean/average operation
     * @param <S>          The space type
     * @return The rewrite instruction
     */
    public static <S extends Space> Inst meanRewrite(
            final Class<S> spaceType,
            final fURI rewriteTID,
            final BiFunction<S, DataPath, Double> meanFunction) {
        return meanRewrite(spaceType, rewriteTID, meanFunction, null);
    }

    public static <S extends Space> Inst meanRewrite(
            final Class<S> spaceType,
            final fURI rewriteTID,
            final BiFunction<S, DataPath, Double> meanFunction,
            final BiPredicate<S, List<Inst>> matchSpacePredicate) {

        return RewriteBuilder.forDatabase(spaceType)
                .tid(rewriteTID)
                .rng(REAL_TID)
                .match(FROM_INST_TID, MEAN_INST_TID)
                .matchFromOrAt()
                .matchSpacePredicate(matchSpacePredicate)
                .optimize("from_mean", (space, dp, coeff) -> {
                    final double mean = meanFunction.apply(space, dp);
                    return real(mean);
                })
                .build();
    }

    /**
     * Functional interface for limit operations that need access to the limit value.
     *
     * @param <S> The space type
     */
    @FunctionalInterface
    public interface LimitOperation<S extends Space> {
        /**
         * Execute the native limit operation.
         *
         * @param space The database space
         * @param dp    The decomposed DataPath for the table/collection
         * @param limit The limit value from take(n)
         * @return The result (typically an Objs of rows)
         * @throws Exception if the operation fails
         */
        Obj execute(S space, DataPath dp, long limit) throws Exception;
    }

    /**
     * Create a limit optimization rewrite.
     *
     * <p>Optimizes {@code from(furi).take(n)} to use native database LIMIT operations
     * instead of loading all records and taking the first n in memory.
     *
     * <p>Example usage:
     * <pre>{@code
     * CommonRewrites.limitRewrite(
     *     tbleSpace.class,
     *     TBLE_ISA_REWRITE_TID.extend("native_limit"),
     *     (space, furi, limit) -> {
     *         String table = furi.segments().getFirst();
     *         String query = "SELECT * FROM " + table + " LIMIT " + limit;
     *         try (Statement stmt = space.sjvm().createStatement();
     *              ResultSet rs = stmt.executeQuery(query)) {
     *             return ObjSQLSerializer.readLimitedAsRecObjs(rs, (int) limit);
     *         }
     *     }
     * )
     * }</pre>
     *
     * @param spaceType     The database space type
     * @param rewriteTID    The type ID for this specific rewrite
     * @param limitFunction Function that executes the native limit operation (receives space, furi, and limit value)
     * @param <S>           The space type
     * @return The rewrite instruction
     */
    public static <S extends Space> Inst limitRewrite(
            final Class<S> spaceType,
            final fURI rewriteTID,
            final LimitOperation<S> limitFunction) {
        return limitRewrite(spaceType, rewriteTID, limitFunction, null);
    }

    public static <S extends Space> Inst limitRewrite(
            final Class<S> spaceType,
            final fURI rewriteTID,
            final LimitOperation<S> limitFunction,
            final BiPredicate<S, List<Inst>> matchSpacePredicate) {

        return new LimitRewriteBuilder<>(spaceType, limitFunction, matchSpacePredicate)
                .tid(rewriteTID)
                .rng(ALL_STAR)
                .match(FROM_INST_TID, TAKE_INST_TID)
                .matchFromOrAt()
                .build();
    }

    /**
     * Specialized RewriteBuilder for limit operations that extracts the limit value
     * from the take() instruction and passes it to the optimization function.
     */
    private static class LimitRewriteBuilder<S extends Space> extends RewriteBuilder<S> {
        private final LimitOperation<S> limitOperation;

        LimitRewriteBuilder(final Class<S> spaceType, final LimitOperation<S> limitOperation) {
            this(spaceType, limitOperation, null);
        }

        LimitRewriteBuilder(final Class<S> spaceType, final LimitOperation<S> limitOperation,
                            final BiPredicate<S, List<Inst>> matchSpacePredicate) {
            super(spaceType);
            this.limitOperation = limitOperation;
            this.matchSpacePredicate = matchSpacePredicate;
            this.rewriteName = "from_take";
            // Set a dummy optimization since we override createRewriteFunction
            this.optimization = (space, furi, coeff) -> null;
        }

        @Override
        protected Function<Map<Inst, Inst>, List<Inst>> createRewriteFunction() {
            return map -> {
                // Extract fURI from the FROM instruction (first matched)
                final List<Inst> matchedInsts = new ArrayList<>(map.values());
                final Inst fromInst = matchedInsts.get(0);
                final Inst takeInst = matchedInsts.get(1);

                final fURI oldfURI = fromInst.arg(0).asUri().uriValue();
                final Space space = studio.phaseshift.metatron.isa.mach.type.Router.global().getSpaceFor(oldfURI);

                // Check if this is the correct space type
                if (this.spaceType.isInstance(space) && (this.matchPredicate == null || this.matchPredicate.test(matchedInsts))) {
                    final S typedSpace = this.spaceType.cast(space);

                    // Check space-aware predicate (e.g., table-existence guard)
                    if (this.matchSpacePredicate != null && !this.matchSpacePredicate.test(typedSpace, matchedInsts)) {
                        LOG.debug("matchSpacePredicate rejected limit rewrite for URI %s", oldfURI);
                        return matchedInsts.stream().map(Obj::asInst).toList();
                    }

                    final fURI expandedfURI = space.redirect(oldfURI, true);
                    final DataPath dp = DataPath.withoutDB(expandedfURI);

                    // Extract limit value from take() instruction
                    final long limitValue = takeInst.arg(0).asInt().jvm();

                    LOG.debug("evaluating native limit operation on %s with limit %d in space %s",
                            expandedfURI, limitValue, space);

                    // Create the optimized instruction
                    return List.of(instC(this.rewriteTid.dom(ALL_STAR).rng(this.resultTid), lst(uri(expandedfURI), jnt(limitValue)),
                                    (lhs, inst) -> {
                                        try {
                                            return this.limitOperation.execute(typedSpace, dp, limitValue);
                                        } catch (final Exception e) {
                                            throw MTronException.of(e);
                                        }
                                    }
                            )
                    );
                }

                // Not the right space type - return original instructions
                return matchedInsts.stream().map(Obj::asInst).toList();
            };
        }
    }

    /**
     * Create a product optimization rewrite.
     *
     * <p>Optimizes {@code from(furi).prod()} to use native database operations
     * where supported.
     *
     * @param spaceType    The database space type
     * @param rewriteTID   The type ID for this specific rewrite
     * @param prodFunction Function that executes the native product operation
     * @param <S>          The space type
     * @return The rewrite instruction
     */
    public static <S extends Space> Inst prodRewrite(
            final Class<S> spaceType,
            final fURI rewriteTID,
            final BiFunction<S, DataPath, Number> prodFunction) {

        return RewriteBuilder.forDatabase(spaceType)
                .tid(rewriteTID)
                .rng(INT_TID.maybe().some())
                .match(FROM_INST_TID, PROD_INST_TID)
                .optimize("from_prod", (space, dp, coeff) -> {
                    final Number prod = prodFunction.apply(space, dp);
                    return (prod instanceof Double || prod instanceof Float)
                            ? real(prod.doubleValue())
                            : jnt(prod.longValue());
                })
                .build();
    }

    /**
     * Functional interface for select/projection operations.
     *
     * @param <S> The space type
     */
    @FunctionalInterface
    public interface SelectOperation<S extends Space> {
        /**
         * Execute the native select/projection operation.
         *
         * @param space  The database space
         * @param furi   The resolved fURI for the table/collection
         * @param fields The list of field/field names to select
         * @return The projected results (typically an Objs of rows with only selected fields)
         * @throws Exception if the operation fails
         */
        Obj execute(S space, DataPath dp, java.util.List<String> fields) throws Exception;
    }

    /**
     * Create a select/projection optimization rewrite.
     *
     * <p>Optimizes {@code from(furi).>>{field1,field2}} to use native database SELECT projections
     * instead of loading all fields and projecting in memory.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code *table/+>>{name,age}} → {@code SELECT name, age FROM table}</li>
     *   <li>{@code *collection/+>>{title,year}} → MongoDB projection {@code {title: 1, year: 1}}</li>
     * </ul>
     *
     * @param spaceType      The database space type
     * @param rewriteTID     The type ID for this specific rewrite
     * @param selectFunction Function that executes the native select operation
     * @param <S>            The space type
     * @return The rewrite instruction
     */
    public static <S extends Space> Inst selectRewrite(
            final Class<S> spaceType,
            final fURI rewriteTID,
            final SelectOperation<S> selectFunction) {
        return selectRewrite(spaceType, rewriteTID, selectFunction, null);
    }

    public static <S extends Space> Inst selectRewrite(
            final Class<S> spaceType,
            final fURI rewriteTID,
            final SelectOperation<S> selectFunction,
            final BiPredicate<S, List<Inst>> matchSpacePredicate) {

        return new SelectRewriteBuilder<>(spaceType, selectFunction, matchSpacePredicate)
                .tid(rewriteTID)
                .rng(ALL_STAR)
                .match(FROM_INST_TID, RSHIFT_INST_TID)
                .matchFromOrAt()
                .build();
    }

    /**
     * Specialized RewriteBuilder for select/projection operations that extracts
     * the field list from the rshift (>>) instruction.
     */
    private static class SelectRewriteBuilder<S extends Space> extends RewriteBuilder<S> {
        private final SelectOperation<S> selectOperation;

        SelectRewriteBuilder(final Class<S> spaceType, final SelectOperation<S> selectOperation) {
            this(spaceType, selectOperation, null);
        }

        SelectRewriteBuilder(final Class<S> spaceType, final SelectOperation<S> selectOperation,
                             final BiPredicate<S, List<Inst>> matchSpacePredicate) {
            super(spaceType);
            this.selectOperation = selectOperation;
            this.matchSpacePredicate = matchSpacePredicate;
            this.rewriteName = "from_select";
            this.optimization = (space, furi, coeff) -> null;
        }

        @Override
        protected java.util.function.Function<java.util.Map<Inst, Inst>, java.util.List<Inst>> createRewriteFunction() {
            return map -> {
                final java.util.List<Inst> matchedInsts = new java.util.ArrayList<>(map.values());
                final Inst fromInst = matchedInsts.get(0);
                final Inst rshiftInst = matchedInsts.get(1);

                final fURI oldfURI = fromInst.arg(0).asUri().uriValue();
                final Space space = studio.phaseshift.metatron.isa.mach.type.Router.global().getSpaceFor(oldfURI);

                if (!this.spaceType.isInstance(space)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                if (this.matchPredicate != null && !this.matchPredicate.test(matchedInsts)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                // Extract field names from the rshift argument
                // >>{name, age} -> arg(0) is a rec/lst with the field names
                final Obj fieldsArg = rshiftInst.arg(0);
                final java.util.List<String> fields = extractColumnNames(fieldsArg);

                // If we couldn't extract fields, fall back to normal execution
                if (fields == null || fields.isEmpty()) {
                    LOG.debug("select fields too complex for native projection: %s", fieldsArg);
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final S typedSpace = this.spaceType.cast(space);

                // Check space-aware predicate (e.g., table-existence guard)
                if (this.matchSpacePredicate != null && !this.matchSpacePredicate.test(typedSpace, matchedInsts)) {
                    LOG.debug("matchSpacePredicate rejected select rewrite for URI %s", oldfURI);
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final fURI expandedfURI = space.redirect(oldfURI, true);
                final DataPath dp = DataPath.withoutDB(expandedfURI);

                LOG.debug("evaluating native select operation on %s with fields %s in space %s",
                        expandedfURI, fields, space);

                // Create a list of field name strings for the instruction args
                final java.util.List<Obj> colObjs = fields.stream()
                        .map(c -> (Obj) str(c))
                        .toList();

                return java.util.List.of(
                        instC(
                                this.rewriteTid.dom(ALL_STAR).rng(this.resultTid),
                                lst(
                                        uri(expandedfURI),
                                        lst(colObjs)),
                                (lhs, inst) -> {
                                    try {
                                        return this.selectOperation.execute(typedSpace, dp, fields);
                                    } catch (final Exception e) {
                                        throw studio.phaseshift.metatron.util.MTronException.of(e,
                                                "failed to execute native select operation");
                                    }
                                }
                        )
                );
            };
        }

        /**
         * Extract field names from the rshift argument.
         * Handles both rec syntax {name, age} and list syntax [name, age].
         */
        private java.util.List<String> extractColumnNames(final Obj fieldsArg) {
            if (fieldsArg == null || fieldsArg.isNoObj()) {
                return null;
            }

            final java.util.List<String> fields = new java.util.ArrayList<>();

            if (fieldsArg.isRec()) {
                // Rec syntax: {name, age} - keys are the field names
                for (final var rel : (Iterable<studio.phaseshift.metatron.isa.m.type.Rel>) fieldsArg.asRec().elements()::iterator) {
                    final Obj key = rel.first();
                    if (key.isUri()) {
                        fields.add(key.uriValue().name());
                    } else if (key.isStr()) {
                        fields.add(key.strValue());
                    } else {
                        return null; // Complex key, can't translate
                    }
                }
            } else if (fieldsArg.isLst()) {
                // List syntax: [name, age]
                for (final Obj item : fieldsArg.asLst().lstValue()) {
                    if (item.isUri()) {
                        fields.add(item.uriValue().name());
                    } else if (item.isStr()) {
                        fields.add(item.strValue());
                    } else {
                        return null; // Complex item, can't translate
                    }
                }
            } else if (fieldsArg.isUri()) {
                // Single field: >>name
                fields.add(fieldsArg.uriValue().name());
            } else if (fieldsArg.isStr()) {
                // Single field as string
                fields.add(fieldsArg.strValue());
            } else {
                return null; // Unknown format
            }

            return fields;
        }
    }

    /**
     * Functional interface for where operations that need access to the predicate.
     *
     * @param <S> The space type
     */
    /**
     * Comparison operators extracted from metatron predicates.
     */
    public enum ComparisonOp {
        EQ("="),
        GT(">"),
        LT("<"),
        GTE(">="),
        LTE("<="),
        NEQ("<>"),
        EXISTS(null);

        private final String symbol;

        ComparisonOp(final String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }

    /**
     * Formats a single predicate condition into a backend-specific string.
     * Each query language provides its own implementation.
     */
    public interface ConditionFormatter {
        /**
         * Format an equality condition: field = value
         */
        String equality(String field, String value);

        /**
         * Format a comparison: field op value
         */
        String comparison(String field, ComparisonOp op, String value);

        /**
         * Format an existence check (field IS NOT NULL or equivalent)
         */
        String exists(String field);

        /**
         * Escape a string literal for the backend
         */
        default String escapeLiteral(String s) {
            return s;
        }
    }

    /**
     * Joins translated predicate conditions into a single backend-specific
     * filter expression.  Each query language provides its own implementation.
     * <p>
     * Example:  {@code conds -> String.join(" AND ", conds)}
     * Gremlin:       single-condition only for now.
     */
    @FunctionalInterface
    public interface PredicateJoiner {
        String join(List<String> conditions);
    }

    @FunctionalInterface
    public interface WhereOperation<S extends Space> {
        /**
         * Execute the native where/filter operation.
         *
         * @param space        The database space
         * @param dp           The decomposed DataPath
         * @param filterClause The native filter clause
         * @return The filtered results
         * @throws Exception if the operation fails
         */
        Obj execute(S space, DataPath dp, String filterClause) throws Exception;
    }

    /**
     * Create a where (filter) optimization rewrite.
     *
     * <p>Optimizes {@code from(furi).where(predicate)} to use native database WHERE clauses
     * instead of loading all records and filtering in memory.
     *
     * <p>Currently supports simple predicates:
     * <ul>
     *   <li>{@code where([field=>value])} → {@code WHERE field = value}</li>
     *   <li>{@code where([field=>?>n])} → {@code WHERE field > n}</li>
     *   <li>{@code where([field=>?<n])} → {@code WHERE field < n}</li>
     *   <li>{@code where([field=>?>=n])} → {@code WHERE field >= n}</li>
     *   <li>{@code where([field=>?<=n])} → {@code WHERE field <= n}</li>
     * </ul>
     *
     * <p>Complex predicates that cannot be translated will cause the rewrite to fail,
     * falling back to normal mtron execution.
     *
     * @param spaceType     The database space type
     * @param rewriteTID    The type ID for this specific rewrite
     * @param whereFunction Function that executes the native where operation
     * @param <S>           The space type
     * @return The rewrite instruction
     */
    public static <S extends Space> Inst whereRewrite(
            final Class<S> spaceType,
            final fURI rewriteTID,
            final WhereOperation<S> whereFunction,
            final PredicateJoiner predicateJoiner,
            final ConditionFormatter conditionFormatter) {
        return whereRewrite(spaceType, rewriteTID, whereFunction, predicateJoiner, conditionFormatter, null);
    }

    public static <S extends Space> Inst whereRewrite(
            final Class<S> spaceType,
            final fURI rewriteTID,
            final WhereOperation<S> whereFunction,
            final PredicateJoiner predicateJoiner,
            final ConditionFormatter conditionFormatter,
            final BiPredicate<S, List<Inst>> matchSpacePredicate) {

        return new WhereRewriteBuilder<>(spaceType, whereFunction, predicateJoiner, conditionFormatter, matchSpacePredicate)
                .tid(rewriteTID)
                .rng(ALL_STAR)
                .match(FROM_INST_TID, WHERE_INST_TID)
                .matchFromOrAt()
                .build();
    }

    /**
     * Specialized RewriteBuilder for where operations that extracts and translates
     * the predicate from the where() instruction to a native filter clause.
     */
    private static class WhereRewriteBuilder<S extends Space> extends RewriteBuilder<S> {
        private final WhereOperation<S> whereOperation;
        private final PredicateJoiner predicateJoiner;
        private final ConditionFormatter conditionFormatter;

        WhereRewriteBuilder(final Class<S> spaceType, final WhereOperation<S> whereOperation,
                            final PredicateJoiner predicateJoiner,
                            final ConditionFormatter conditionFormatter) {
            this(spaceType, whereOperation, predicateJoiner, conditionFormatter, null);
        }

        WhereRewriteBuilder(final Class<S> spaceType, final WhereOperation<S> whereOperation,
                            final PredicateJoiner predicateJoiner,
                            final ConditionFormatter conditionFormatter,
                            final BiPredicate<S, List<Inst>> matchSpacePredicate) {
            super(spaceType);
            this.whereOperation = whereOperation;
            this.predicateJoiner = predicateJoiner;
            this.conditionFormatter = conditionFormatter;
            this.matchSpacePredicate = matchSpacePredicate;
            this.rewriteName = "from_where";
            this.optimization = (space, furi, coeff) -> null;
        }

        @Override
        protected java.util.function.Function<java.util.Map<Inst, Inst>, java.util.List<Inst>> createRewriteFunction() {
            return map -> {
                final java.util.List<Inst> matchedInsts = new java.util.ArrayList<>(map.values());
                final Inst fromInst = matchedInsts.get(0);
                final Inst whereInst = matchedInsts.get(1);

                final fURI oldfURI = fromInst.arg(0).asUri().uriValue();
                final Space space = studio.phaseshift.metatron.isa.mach.type.Router.global().getSpaceFor(oldfURI);

                if (!this.spaceType.isInstance(space)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                if (this.matchPredicate != null && !this.matchPredicate.test(matchedInsts)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                // Try to translate the where predicate to backend-agnostic conditions
                final Obj predicate = whereInst.arg(0);
                final List<String> conditions = tryTranslatePredicate(predicate);

                // If translation failed, fall back to normal execution
                if (conditions == null) {
                    LOG.debug("where predicate too complex for native translation: %s", predicate);
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                // Let the backend join conditions into its native filter expression
                final String filterClause = this.predicateJoiner.join(conditions);

                final S typedSpace = this.spaceType.cast(space);

                // Check space-aware predicate (e.g., table-existence guard)
                if (this.matchSpacePredicate != null && !this.matchSpacePredicate.test(typedSpace, matchedInsts)) {
                    LOG.debug("matchSpacePredicate rejected where rewrite for URI %s", oldfURI);
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final fURI expandedfURI = space.redirect(oldfURI, true);
                final DataPath dp = DataPath.withoutDB(expandedfURI);

                LOG.debug("evaluating native where operation on %s with clause '%s' in space %s",
                        expandedfURI, filterClause, space);

                return java.util.List.of(
                        instC(
                                this.rewriteTid.dom(ALL_STAR).rng(this.resultTid),
                                lst(uri(expandedfURI), str(filterClause)),
                                (lhs, inst) -> {
                                    // Barrier re-application guard: if the SwarmMachine
                                    // already accumulated results via the gather barrier,
                                    // just return those results; re-querying g.V().has()
                                    // would double them.
                                    if (lhs instanceof Objs)
                                        return lhs;
                                    try {
                                        return this.whereOperation.execute(typedSpace, dp, filterClause);
                                    } catch (final Exception e) {
                                        throw studio.phaseshift.metatron.util.MTronException.of(e,
                                                "failed to execute native where operation");
                                    }
                                }
                        )
                );
            };
        }

        /**
         * Try to translate a metatron predicate to a native filter clause.
         * Returns null if the predicate is too complex to translate.
         */
        private List<String> tryTranslatePredicate(final Obj predicate) {
            // Only handle Rec predicates for now
            if (!predicate.isRec()) {
                return null;
            }

            final java.util.List<String> conditions = new java.util.ArrayList<>();
            final var rec = predicate.asRec();

            for (final var rel : (Iterable<studio.phaseshift.metatron.isa.m.type.Rel>) rec.elements()::iterator) {
                final Obj key = rel.first();
                final Obj value = rel.second();

                // Key must be a URI (field name) - wildcards not supported yet
                if (!key.isUri()) {
                    return null;
                }
                final String fieldName = key.asUri().uriValue().name();
                if (fieldName == null || fieldName.isEmpty() || fieldName.equals("_") || fieldName.equals("+")) {
                    return null; // Wildcard field names not supported
                }

                // Translate the value/predicate
                final String condition = translateCondition(fieldName, value);
                if (condition == null) {
                    return null; // Complex condition, can't translate
                }
                conditions.add(condition);
            }

            if (conditions.isEmpty()) {
                return null;
            }

            return conditions;
        }

        /**
         * Translate a single field condition to a native filter.
         */
        private String translateCondition(final String fieldName, final Obj value) {
            // Handle underscore wildcard - field exists / is not null
            if (value.isUri() && "_".equals(value.asUri().uriValue().toString())) {
                return this.conditionFormatter.exists(fieldName);
            }

            // Handle URI values — quote the full URI string for equality.
            // E.g., /usr/dr/session/1 → session = '/usr/dr/session/1'
            if (value.isUri()) {
                return this.conditionFormatter.equality(fieldName,
                        quoteLiteral(value.asUri().uriValue().toString()));
            }

            // Handle literal values - equality check
            if (value.isInt()) {
                return this.conditionFormatter.equality(fieldName, String.valueOf(value.asInt().jvm()));
            }
            if (value.isReal()) {
                return this.conditionFormatter.equality(fieldName, String.valueOf(value.asReal().jvm()));
            }
            if (value.isStr()) {
                return this.conditionFormatter.equality(fieldName,
                        quoteLiteral(value.asStr().jvm()));
            }
            if (value.isBool()) {
                return this.conditionFormatter.equality(fieldName,
                        value.asBool().jvm() ? "true" : "false");
            }

            // Handle comparison instructions like ?>5, ?<10, etc.
            // These are parsed as is(gt(5)), is(lt(10)), etc.
            if (value.isInst()) {
                Inst inst = value.asInst();
                String op = inst.tid().name();

                // Unwrap "is" instruction: is(gt(5)) -> gt(5)
                if ("is".equals(op) && inst.args().count() > 0 && inst.arg(0).isInst()) {
                    inst = inst.arg(0).asInst();
                    op = inst.tid().name();
                }

                // Check for comparison operators
                final ComparisonOp nativeOp = switch (op) {
                    case "gt" -> ComparisonOp.GT;
                    case "lt" -> ComparisonOp.LT;
                    case "gte" -> ComparisonOp.GTE;
                    case "lte" -> ComparisonOp.LTE;
                    case "neq" -> ComparisonOp.NEQ;
                    case "eq" -> ComparisonOp.EQ;
                    default -> null;
                };

                if (nativeOp != null && inst.args().count() > 0) {
                    final Obj arg = inst.arg(0);
                    if (arg.isInt()) {
                        return this.conditionFormatter.comparison(fieldName, nativeOp,
                                String.valueOf(arg.asInt().jvm()));
                    }
                    if (arg.isReal()) {
                        return this.conditionFormatter.comparison(fieldName, nativeOp,
                                String.valueOf(arg.asReal().jvm()));
                    }
                    if (arg.isStr()) {
                        return this.conditionFormatter.comparison(fieldName, nativeOp,
                                quoteLiteral(arg.asStr().jvm()));
                    }
                }
            }

            // Can't translate this condition
            return null;
        }

        /**
         * Quote a string literal using the backend's escaping rules.
         */
        private String quoteLiteral(final String s) {
            final String escaped = this.conditionFormatter.escapeLiteral(s);
            return "'" + escaped + "'";
        }
    }

    /**
     * Functional interface for where+count operations.
     *
     * @param <S> The space type
     */
    @FunctionalInterface
    public interface WhereCountOperation<S extends Space> {
        /**
         * Execute the native count with where filter.
         *
         * @param space        The database space
         * @param dp           The resolved data path
         * @param filterClause The native filter clause
         * @return The count of matching rows
         * @throws Exception if the operation fails
         */
        long execute(S space, DataPath dp, String filterClause) throws Exception;
    }

    /**
     * Create a where+count optimization rewrite.
     *
     * <p>Optimizes {@code native_where.count()} to use native database
     * {@code SELECT COUNT(*) FROM table WHERE conditions} instead of fetching
     * all filtered rows and counting in memory.
     *
     * <p>This rewrite composes with whereRewrite:
     * <pre>
     * from.where.count
     *   → native_where.count      (whereRewrite)
     *   → native_where.count      (this rewrite)
     * </pre>
     *
     * @param spaceType          The database space type
     * @param whereRewriteTID    The TID of the native where instruction to match
     * @param rewriteTID         The TID for this rewrite's output instruction
     * @param whereCountFunction Function that executes the native count with where
     * @param <S>                The space type
     * @return The rewrite instruction
     */
    public static <S extends Space> Inst whereCountRewrite(
            final Class<S> spaceType,
            final fURI whereRewriteTID,
            final fURI rewriteTID,
            final WhereCountOperation<S> whereCountFunction) {
        return whereCountRewrite(spaceType, whereRewriteTID, rewriteTID, whereCountFunction, null);
    }

    public static <S extends Space> Inst whereCountRewrite(
            final Class<S> spaceType,
            final fURI whereRewriteTID,
            final fURI rewriteTID,
            final WhereCountOperation<S> whereCountFunction,
            final BiPredicate<S, List<Inst>> matchSpacePredicate) {

        return new WhereCountRewriteBuilder<>(spaceType, whereRewriteTID, whereCountFunction, matchSpacePredicate)
                .tid(rewriteTID)
                .rng(INT_TID)
                .match(whereRewriteTID, COUNT_INST_TID)
                .build();
    }

    /**
     * Specialized RewriteBuilder for where+count operations that extracts
     * the fURI and WHERE clause from the preceding native where instruction.
     */
    private static class WhereCountRewriteBuilder<S extends Space> extends RewriteBuilder<S> {
        private final fURI whereRewriteTID;
        private final WhereCountOperation<S> whereCountOperation;

        WhereCountRewriteBuilder(final Class<S> spaceType, final fURI whereRewriteTID,
                                 final WhereCountOperation<S> whereCountOperation) {
            this(spaceType, whereRewriteTID, whereCountOperation, null);
        }

        WhereCountRewriteBuilder(final Class<S> spaceType, final fURI whereRewriteTID,
                                 final WhereCountOperation<S> whereCountOperation,
                                 final BiPredicate<S, List<Inst>> matchSpacePredicate) {
            super(spaceType);
            this.whereRewriteTID = whereRewriteTID;
            this.whereCountOperation = whereCountOperation;
            this.matchSpacePredicate = matchSpacePredicate;
            this.rewriteName = "from_where_count";
            this.optimization = (space, furi, coeff) -> null;
        }

        @Override
        protected java.util.function.Function<java.util.Map<Inst, Inst>, java.util.List<Inst>> createRewriteFunction() {
            return map -> {
                final java.util.List<Inst> matchedInsts = new java.util.ArrayList<>(map.values());
                final Inst whereInst = matchedInsts.get(0);  // native where instruction
                // matchedInsts.get(1) is count - we don't need it, just matching it

                // Extract fURI and filterClause from native where instruction's args: [furi, filterClause]
                final Obj args = whereInst.args();
                if (!args.isLst() || args.asLst().count() < 2) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final fURI furi = args.asLst().at(0).asUri().uriValue();
                final String filterClause = args.asLst().at(1).asStr().jvm();

                final Space space = studio.phaseshift.metatron.isa.mach.type.Router.global().getSpaceFor(furi);

                if (!this.spaceType.isInstance(space)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final S typedSpace = this.spaceType.cast(space);

                // Check space-aware predicate (e.g., table-existence guard)
                if (this.matchSpacePredicate != null && !this.matchSpacePredicate.test(typedSpace, matchedInsts)) {
                    LOG.debug("matchSpacePredicate rejected where+count rewrite for URI %s", furi);
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final DataPath dp = DataPath.withoutDB(furi);

                LOG.debug("evaluating native where+count on %s with clause '%s' in space %s",
                        furi, filterClause, space);

                return java.util.List.of(
                        instC(
                                this.rewriteTid.dom(ALL_STAR).rng(this.resultTid),
                                lst(
                                        uri(furi),
                                        str(filterClause)),
                                (lhs, inst) -> {
                                    try {
                                        final long count = this.whereCountOperation.execute(typedSpace, dp, filterClause);
                                        return jnt(count);
                                    } catch (final Exception e) {
                                        throw studio.phaseshift.metatron.util.MTronException.of(e,
                                                "failed to execute native where+count operation");
                                    }
                                }
                        )
                );
            };
        }
    }

    /**
     * Functional interface for where+limit operations.
     *
     * @param <S> The space type
     */
    @FunctionalInterface
    public interface WhereLimitOperation<S extends Space> {
        /**
         * Execute the native filtered+limited query.
         *
         * @param space        The database space
         * @param dp           The decomposed DataPath for the table/collection
         * @param filterClause The native filter clause
         * @param limit        The limit value from take(n)
         * @return The filtered and limited results
         * @throws Exception if the operation fails
         */
        Obj execute(S space, DataPath dp, String filterClause, long limit) throws Exception;
    }

    /**
     * Create a where+limit optimization rewrite.
     *
     * <p>Optimizes {@code native_where.take(n)} to use native database
     * {@code SELECT * FROM table WHERE conditions LIMIT n} instead of
     * fetching all filtered rows and taking the first n in memory.
     *
     * <p>This rewrite composes with whereRewrite:
     * <pre>
     * from.where.take
     *   → native_where.take        (whereRewrite)
     *   → native_where_limit       (this rewrite)
     * </pre>
     *
     * @param spaceType          The database space type
     * @param whereRewriteTID    The TID of the native where instruction to match
     * @param rewriteTID         The TID for this rewrite's output instruction
     * @param whereLimitFunction Function that executes the native where+limit operation
     * @param <S>                The space type
     * @return The rewrite instruction
     */
    public static <S extends Space> Inst whereLimitRewrite(
            final Class<S> spaceType,
            final fURI whereRewriteTID,
            final fURI rewriteTID,
            final WhereLimitOperation<S> whereLimitFunction) {
        return whereLimitRewrite(spaceType, whereRewriteTID, rewriteTID, whereLimitFunction, null);
    }

    public static <S extends Space> Inst whereLimitRewrite(
            final Class<S> spaceType,
            final fURI whereRewriteTID,
            final fURI rewriteTID,
            final WhereLimitOperation<S> whereLimitFunction,
            final BiPredicate<S, List<Inst>> matchSpacePredicate) {

        return new WhereLimitRewriteBuilder<>(spaceType, whereRewriteTID, whereLimitFunction, matchSpacePredicate)
                .tid(rewriteTID)
                .rng(ALL_STAR)
                .match(whereRewriteTID, TAKE_INST_TID)
                .build();
    }

    /**
     * Specialized RewriteBuilder for where+limit operations that extracts
     * the fURI and WHERE clause from the preceding native where instruction
     * and the limit value from the take() instruction.
     */
    private static class WhereLimitRewriteBuilder<S extends Space> extends RewriteBuilder<S> {
        private final fURI whereRewriteTID;
        private final WhereLimitOperation<S> whereLimitOperation;

        WhereLimitRewriteBuilder(final Class<S> spaceType, final fURI whereRewriteTID,
                                 final WhereLimitOperation<S> whereLimitOperation) {
            this(spaceType, whereRewriteTID, whereLimitOperation, null);
        }

        WhereLimitRewriteBuilder(final Class<S> spaceType, final fURI whereRewriteTID,
                                 final WhereLimitOperation<S> whereLimitOperation,
                                 final BiPredicate<S, List<Inst>> matchSpacePredicate) {
            super(spaceType);
            this.whereRewriteTID = whereRewriteTID;
            this.whereLimitOperation = whereLimitOperation;
            this.matchSpacePredicate = matchSpacePredicate;
            this.rewriteName = "from_where_limit";
            this.optimization = (space, furi, coeff) -> null;
        }

        @Override
        protected java.util.function.Function<java.util.Map<Inst, Inst>, java.util.List<Inst>> createRewriteFunction() {
            return map -> {
                final java.util.List<Inst> matchedInsts = new java.util.ArrayList<>(map.values());
                final Inst whereInst = matchedInsts.get(0);   // native where instruction
                final Inst takeInst = matchedInsts.get(1);    // take(n) instruction

                // Extract fURI and filterClause from the where instruction's args: [furi, filterClause]
                final Obj args = whereInst.args();
                if (!args.isLst() || args.asLst().count() < 2) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final fURI furi = args.asLst().at(0).asUri().uriValue();
                final String filterClause = args.asLst().at(1).asStr().jvm();

                // Extract limit value from take() instruction
                final long limitValue = takeInst.arg(0).asInt().jvm();

                final Space space = studio.phaseshift.metatron.isa.mach.type.Router.global().getSpaceFor(furi);

                if (!this.spaceType.isInstance(space)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final S typedSpace = this.spaceType.cast(space);

                // Check space-aware predicate (e.g., table-existence guard)
                if (this.matchSpacePredicate != null && !this.matchSpacePredicate.test(typedSpace, matchedInsts)) {
                    LOG.debug("matchSpacePredicate rejected where+limit rewrite for URI %s", furi);
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final DataPath dp = DataPath.withoutDB(furi);

                LOG.debug("evaluating native where+limit on %s with clause '%s' and limit %d in space %s",
                        furi, filterClause, limitValue, space);

                return java.util.List.of(
                        instC(
                                this.rewriteTid.dom(ALL_STAR).rng(this.resultTid),
                                lst(
                                        uri(furi),
                                        str(filterClause),
                                        jnt(limitValue)),
                                (lhs, inst) -> {
                                    try {
                                        return this.whereLimitOperation.execute(typedSpace, dp, filterClause, limitValue);
                                    } catch (final Exception e) {
                                        throw studio.phaseshift.metatron.util.MTronException.of(e,
                                                "failed to execute native where+limit operation");
                                    }
                                }
                        )
                );
            };
        }
    }

    /**
     * Functional interface for skip/offset operations that need access to the skip value.
     *
     * @param <S> The space type
     */
    @FunctionalInterface
    public interface SkipOperation<S extends Space> {
        /**
         * Execute the native skip/offset operation.
         *
         * @param space The database space
         * @param dp    The decomposed DataPath for the table/collection
         * @param skip  The skip value from skip(n)
         * @return The result (typically an Objs of rows)
         * @throws Exception if the operation fails
         */
        Obj execute(S space, DataPath dp, long skip) throws Exception;
    }

    /**
     * Create a skip (offset) optimization rewrite.
     *
     * <p>Optimizes {@code from(furi).skip(n)} to use native database OFFSET
     * instead of loading all records and skipping in memory.
     *
     * @param spaceType    The database space type
     * @param rewriteTID   The type ID for this specific rewrite
     * @param skipFunction Function that executes the native skip operation (receives space, dp, and skip value)
     * @param <S>          The space type
     * @return The rewrite instruction
     */
    public static <S extends Space> Inst skipRewrite(
            final Class<S> spaceType,
            final fURI rewriteTID,
            final SkipOperation<S> skipFunction) {
        return skipRewrite(spaceType, rewriteTID, skipFunction, null);
    }

    public static <S extends Space> Inst skipRewrite(
            final Class<S> spaceType,
            final fURI rewriteTID,
            final SkipOperation<S> skipFunction,
            final BiPredicate<S, List<Inst>> matchSpacePredicate) {

        return new SkipRewriteBuilder<>(spaceType, skipFunction, matchSpacePredicate)
                .tid(rewriteTID)
                .rng(ALL_STAR)
                .match(FROM_INST_TID, SKIP_INST_TID)
                .matchFromOrAt()
                .build();
    }

    /**
     * Specialized RewriteBuilder for skip/offset operations that extracts the skip value
     * from the skip() instruction and passes it to the optimization function.
     */
    private static class SkipRewriteBuilder<S extends Space> extends RewriteBuilder<S> {
        private final SkipOperation<S> skipOperation;

        SkipRewriteBuilder(final Class<S> spaceType, final SkipOperation<S> skipOperation) {
            this(spaceType, skipOperation, null);
        }

        SkipRewriteBuilder(final Class<S> spaceType, final SkipOperation<S> skipOperation,
                           final BiPredicate<S, List<Inst>> matchSpacePredicate) {
            super(spaceType);
            this.skipOperation = skipOperation;
            this.matchSpacePredicate = matchSpacePredicate;
            this.rewriteName = "from_skip";
            this.optimization = (space, furi, coeff) -> null;
        }

        @Override
        protected Function<Map<Inst, Inst>, List<Inst>> createRewriteFunction() {
            return map -> {
                final List<Inst> matchedInsts = new ArrayList<>(map.values());
                final Inst fromInst = matchedInsts.get(0);
                final Inst skipInst = matchedInsts.get(1);

                final fURI oldfURI = fromInst.arg(0).asUri().uriValue();
                final Space space = studio.phaseshift.metatron.isa.mach.type.Router.global().getSpaceFor(oldfURI);

                if (!this.spaceType.isInstance(space)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                if (this.matchPredicate != null && !this.matchPredicate.test(matchedInsts)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final S typedSpace = this.spaceType.cast(space);

                if (this.matchSpacePredicate != null && !this.matchSpacePredicate.test(typedSpace, matchedInsts)) {
                    LOG.debug("matchSpacePredicate rejected skip rewrite for URI %s", oldfURI);
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final fURI expandedfURI = space.redirect(oldfURI, true);
                final DataPath dp = DataPath.withoutDB(expandedfURI);
                final long skipValue = skipInst.arg(0).asInt().jvm();

                LOG.debug("evaluating native skip operation on %s with skip %d in space %s",
                        expandedfURI, skipValue, space);

                return List.of(instC(
                        this.rewriteTid.dom(ALL_STAR).rng(this.resultTid),
                        lst(uri(expandedfURI), jnt(skipValue)),
                        (lhs, inst) -> {
                            try {
                                return this.skipOperation.execute(typedSpace, dp, skipValue);
                            } catch (final Exception e) {
                                throw MTronException.of(e);
                            }
                        }
                ));
            };
        }
    }

    /**
     * Functional interface for offset+limit operations.
     *
     * @param <S> The space type
     */
    @FunctionalInterface
    public interface OffsetLimitOperation<S extends Space> {
        /**
         * Execute the native offset+limit (pagination) operation.
         *
         * @param space The database space
         * @param dp    The decomposed DataPath for the table/collection
         * @param skip  The skip/offset value
         * @param limit The limit value
         * @return The result (paged rows)
         * @throws Exception if the operation fails
         */
        Obj execute(S space, DataPath dp, long skip, long limit) throws Exception;
    }

    /**
     * Create an offset+limit (pagination) optimization rewrite.
     *
     * <p>Optimizes {@code sql_offset.take(n)} to use native database
     * {@code SELECT * FROM table LIMIT n OFFSET m} instead of separate
     * offset and limit operations.
     *
     * <p>This rewrite composes with offsetRewrite (skipRewrite): the first
     * rewrite turns {@code from().skip()} into {@code sql_offset}, then this
     * rewrite fuses {@code sql_offset.take()} into a single paginated query.
     *
     * @param spaceType           The database space type
     * @param offsetRewriteTID    The TID of the native offset/skip instruction to match
     * @param rewriteTID          The TID for this rewrite's output instruction
     * @param offsetLimitFunction Function that executes the native offset+limit operation
     * @param <S>                 The space type
     * @return The rewrite instruction
     */
    public static <S extends Space> Inst offsetLimitRewrite(
            final Class<S> spaceType,
            final fURI offsetRewriteTID,
            final fURI rewriteTID,
            final OffsetLimitOperation<S> offsetLimitFunction) {
        return offsetLimitRewrite(spaceType, offsetRewriteTID, rewriteTID, offsetLimitFunction, null);
    }

    public static <S extends Space> Inst offsetLimitRewrite(
            final Class<S> spaceType,
            final fURI offsetRewriteTID,
            final fURI rewriteTID,
            final OffsetLimitOperation<S> offsetLimitFunction,
            final BiPredicate<S, List<Inst>> matchSpacePredicate) {

        return new OffsetLimitRewriteBuilder<>(spaceType, offsetRewriteTID, offsetLimitFunction, matchSpacePredicate)
                .tid(rewriteTID)
                .rng(ALL_STAR)
                .match(offsetRewriteTID, TAKE_INST_TID)
                .build();
    }

    /**
     * Specialized RewriteBuilder for offset+limit operations that extracts
     * skip and limit values and fuses them into a single paginated query.
     */
    private static class OffsetLimitRewriteBuilder<S extends Space> extends RewriteBuilder<S> {
        private final fURI offsetRewriteTID;
        private final OffsetLimitOperation<S> offsetLimitOperation;

        OffsetLimitRewriteBuilder(final Class<S> spaceType, final fURI offsetRewriteTID,
                                  final OffsetLimitOperation<S> offsetLimitOperation) {
            this(spaceType, offsetRewriteTID, offsetLimitOperation, null);
        }

        OffsetLimitRewriteBuilder(final Class<S> spaceType, final fURI offsetRewriteTID,
                                  final OffsetLimitOperation<S> offsetLimitOperation,
                                  final BiPredicate<S, List<Inst>> matchSpacePredicate) {
            super(spaceType);
            this.offsetRewriteTID = offsetRewriteTID;
            this.offsetLimitOperation = offsetLimitOperation;
            this.matchSpacePredicate = matchSpacePredicate;
            this.rewriteName = "from_skip_take";
            this.optimization = (space, furi, coeff) -> null;
        }

        @Override
        protected Function<Map<Inst, Inst>, List<Inst>> createRewriteFunction() {
            return map -> {
                final List<Inst> matchedInsts = new ArrayList<>(map.values());
                final Inst offsetInst = matchedInsts.get(0);  // native sql_offset instruction
                final Inst takeInst = matchedInsts.get(1);    // take(n) instruction

                // Extract fURI and skip value from sql_offset instruction's args: [furi, skip]
                final Obj args = offsetInst.args();
                if (!args.isLst() || args.asLst().count() < 2) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final fURI furi = args.asLst().at(0).asUri().uriValue();
                final long skipValue = args.asLst().at(1).asInt().jvm();
                final long limitValue = takeInst.arg(0).asInt().jvm();

                final Space space = studio.phaseshift.metatron.isa.mach.type.Router.global().getSpaceFor(furi);

                if (!this.spaceType.isInstance(space)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final S typedSpace = this.spaceType.cast(space);

                if (this.matchSpacePredicate != null && !this.matchSpacePredicate.test(typedSpace, matchedInsts)) {
                    LOG.debug("matchSpacePredicate rejected offset+limit rewrite for URI %s", furi);
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final DataPath dp = DataPath.withoutDB(furi);

                LOG.debug("evaluating native offset+limit on %s with skip %d, limit %d in space %s",
                        furi, skipValue, limitValue, space);

                return List.of(instC(
                        this.rewriteTid.dom(ALL_STAR).rng(this.resultTid),
                        lst(uri(furi), jnt(skipValue), jnt(limitValue)),
                        (lhs, inst) -> {
                            try {
                                return this.offsetLimitOperation.execute(typedSpace, dp, skipValue, limitValue);
                            } catch (final Exception e) {
                                throw MTronException.of(e, "failed to execute native offset+limit operation");
                            }
                        }
                ));
            };
        }
    }

    // Planned rewrite implementations: see docs/ai/rewrite-roadmap.md

    // =========================================================================
    //  Shared helpers
    // =========================================================================

    /**
     * Extract column names from a select/rshift argument.
     * Handles rec syntax {name, age}, list syntax [name, age],
     * single URI, and single Str.
     */
    static List<String> extractColumnNames(final Obj fieldsArg) {
        if (fieldsArg == null || fieldsArg.isNoObj()) {
            return null;
        }
        final List<String> fields = new ArrayList<>();
        if (fieldsArg.isRec()) {
            for (final var rel : (Iterable<studio.phaseshift.metatron.isa.m.type.Rel>) fieldsArg.asRec().elements()::iterator) {
                final Obj key = rel.first();
                if (key.isUri()) fields.add(key.uriValue().name());
                else if (key.isStr()) fields.add(key.strValue());
                else return null;
            }
        } else if (fieldsArg.isLst()) {
            for (final Obj item : fieldsArg.asLst().lstValue()) {
                if (item.isUri()) fields.add(item.uriValue().name());
                else if (item.isStr()) fields.add(item.strValue());
                else return null;
            }
        } else if (fieldsArg.isUri()) {
            fields.add(fieldsArg.uriValue().name());
        } else if (fieldsArg.isStr()) {
            fields.add(fieldsArg.strValue());
        } else {
            return null;
        }
        return fields;
    }

    // =========================================================================
    //  WHERE + OFFSET (skip) composed rewrite
    // =========================================================================

    @FunctionalInterface
    public interface WhereOffsetOperation<S extends Space> {
        Obj execute(S space, DataPath dp, String filterClause, long skip) throws Exception;
    }

    public static <S extends Space> Inst whereOffsetRewrite(
            final Class<S> spaceType,
            final fURI whereRewriteTID,
            final fURI rewriteTID,
            final WhereOffsetOperation<S> whereOffsetFunction) {
        return whereOffsetRewrite(spaceType, whereRewriteTID, rewriteTID, whereOffsetFunction, null);
    }

    public static <S extends Space> Inst whereOffsetRewrite(
            final Class<S> spaceType,
            final fURI whereRewriteTID,
            final fURI rewriteTID,
            final WhereOffsetOperation<S> whereOffsetFunction,
            final BiPredicate<S, List<Inst>> matchSpacePredicate) {

        return new WhereOffsetRewriteBuilder<>(spaceType, whereRewriteTID, whereOffsetFunction, matchSpacePredicate)
                .tid(rewriteTID)
                .rng(ALL_STAR)
                .match(whereRewriteTID, SKIP_INST_TID)
                .build();
    }

    private static class WhereOffsetRewriteBuilder<S extends Space> extends RewriteBuilder<S> {
        private final fURI whereRewriteTID;
        private final WhereOffsetOperation<S> whereOffsetOperation;

        WhereOffsetRewriteBuilder(final Class<S> spaceType, final fURI whereRewriteTID,
                                  final WhereOffsetOperation<S> whereOffsetOperation) {
            this(spaceType, whereRewriteTID, whereOffsetOperation, null);
        }

        WhereOffsetRewriteBuilder(final Class<S> spaceType, final fURI whereRewriteTID,
                                  final WhereOffsetOperation<S> whereOffsetOperation,
                                  final BiPredicate<S, List<Inst>> matchSpacePredicate) {
            super(spaceType);
            this.whereRewriteTID = whereRewriteTID;
            this.whereOffsetOperation = whereOffsetOperation;
            this.matchSpacePredicate = matchSpacePredicate;
            this.rewriteName = "from_where_skip";
            this.optimization = (space, furi, coeff) -> null;
        }

        @Override
        protected Function<Map<Inst, Inst>, List<Inst>> createRewriteFunction() {
            return map -> {
                final List<Inst> matchedInsts = new ArrayList<>(map.values());
                final Inst whereInst = matchedInsts.get(0);
                final Inst skipInst = matchedInsts.get(1);

                final Obj args = whereInst.args();
                if (!args.isLst() || args.asLst().count() < 2) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final fURI furi = args.asLst().at(0).asUri().uriValue();
                final String filterClause = args.asLst().at(1).asStr().jvm();
                final long skipValue = skipInst.arg(0).asInt().jvm();

                final Space space = studio.phaseshift.metatron.isa.mach.type.Router.global().getSpaceFor(furi);

                if (!this.spaceType.isInstance(space)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final S typedSpace = this.spaceType.cast(space);

                if (this.matchSpacePredicate != null && !this.matchSpacePredicate.test(typedSpace, matchedInsts)) {
                    LOG.debug("matchSpacePredicate rejected where+offset rewrite for URI %s", furi);
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final DataPath dp = DataPath.withoutDB(furi);

                LOG.debug("evaluating native where+offset on %s with clause '%s' and skip %d in space %s",
                        furi, filterClause, skipValue, space);

                return List.of(instC(
                        this.rewriteTid.dom(ALL_STAR).rng(this.resultTid),
                        lst(uri(furi), str(filterClause), jnt(skipValue)),
                        (lhs, inst) -> {
                            try {
                                return this.whereOffsetOperation.execute(typedSpace, dp, filterClause, skipValue);
                            } catch (final Exception e) {
                                throw MTronException.of(e, "failed to execute native where+offset operation");
                            }
                        }
                ));
            };
        }
    }

    // =========================================================================
    //  WHERE + ORDER composed rewrite
    // =========================================================================

    @FunctionalInterface
    public interface WhereOrderOperation<S extends Space> {
        Obj execute(S space, DataPath dp, String filterClause, List<String> columns) throws Exception;
    }

    public static <S extends Space> Inst whereOrderRewrite(
            final Class<S> spaceType,
            final fURI whereRewriteTID,
            final fURI rewriteTID,
            final WhereOrderOperation<S> whereOrderFunction) {
        return whereOrderRewrite(spaceType, whereRewriteTID, rewriteTID, whereOrderFunction, null);
    }

    public static <S extends Space> Inst whereOrderRewrite(
            final Class<S> spaceType,
            final fURI whereRewriteTID,
            final fURI rewriteTID,
            final WhereOrderOperation<S> whereOrderFunction,
            final BiPredicate<S, List<Inst>> matchSpacePredicate) {

        return new WhereOrderRewriteBuilder<>(spaceType, whereRewriteTID, whereOrderFunction, matchSpacePredicate)
                .tid(rewriteTID)
                .rng(ALL_STAR)
                .match(whereRewriteTID, ORDER_INST_TID)
                .build();
    }

    private static class WhereOrderRewriteBuilder<S extends Space> extends RewriteBuilder<S> {
        private final fURI whereRewriteTID;
        private final WhereOrderOperation<S> whereOrderOperation;

        WhereOrderRewriteBuilder(final Class<S> spaceType, final fURI whereRewriteTID,
                                 final WhereOrderOperation<S> whereOrderOperation) {
            this(spaceType, whereRewriteTID, whereOrderOperation, null);
        }

        WhereOrderRewriteBuilder(final Class<S> spaceType, final fURI whereRewriteTID,
                                 final WhereOrderOperation<S> whereOrderOperation,
                                 final BiPredicate<S, List<Inst>> matchSpacePredicate) {
            super(spaceType);
            this.whereRewriteTID = whereRewriteTID;
            this.whereOrderOperation = whereOrderOperation;
            this.matchSpacePredicate = matchSpacePredicate;
            this.rewriteName = "from_where_order";
            this.optimization = (space, furi, coeff) -> null;
        }

        @Override
        protected Function<Map<Inst, Inst>, List<Inst>> createRewriteFunction() {
            return map -> {
                final List<Inst> matchedInsts = new ArrayList<>(map.values());
                final Inst whereInst = matchedInsts.get(0);
                final Inst orderInst = matchedInsts.get(1);

                final Obj args = whereInst.args();
                if (!args.isLst() || args.asLst().count() < 2) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final fURI furi = args.asLst().at(0).asUri().uriValue();
                final String filterClause = args.asLst().at(1).asStr().jvm();

                // Extract columns from order's arg (same logic as OrderRewriteBuilder)
                final Obj columnSpecArg = orderInst.arg(0);
                final Obj columnSpec;
                if (columnSpecArg.isInst()) {
                    columnSpec = columnSpecArg.asInst().arg(0);
                } else {
                    columnSpec = columnSpecArg;
                }
                final List<String> columns = extractColumnNames(columnSpec);
                if (columns == null || columns.isEmpty()) {
                    LOG.debug("where+order columns too complex for native translation: %s", columnSpecArg);
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }
                
                final Space space = Router.global().getSpaceFor(furi);

                if (!this.spaceType.isInstance(space)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final S typedSpace = this.spaceType.cast(space);

                if (this.matchSpacePredicate != null && !this.matchSpacePredicate.test(typedSpace, matchedInsts)) {
                    LOG.debug("matchSpacePredicate rejected where+order rewrite for URI %s", furi);
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final DataPath dp = DataPath.withoutDB(furi);

                LOG.debug("evaluating native where+order on %s with clause '%s' and columns %s in space %s",
                        furi, filterClause, columns, space);

                return List.of(instC(
                        this.rewriteTid.dom(ALL_STAR).rng(this.resultTid),
                        lst(uri(furi), str(filterClause), lst(columns.stream().<Obj>map(col -> str(col)).toList())),
                        (lhs, inst) -> {
                            try {
                                return this.whereOrderOperation.execute(typedSpace, dp, filterClause, columns);
                            } catch (final Exception e) {
                                throw MTronException.of(e, "failed to execute native where+order operation");
                            }
                        }
                ));
            };
        }
    }

    // =========================================================================
    //  WHERE + ORDER + OFFSET composed rewrite
    // =========================================================================

    @FunctionalInterface
    public interface WhereOrderOffsetOperation<S extends Space> {
        Obj execute(S space, DataPath dp, String filterClause, List<String> columns, long skip) throws Exception;
    }

    public static <S extends Space> Inst whereOrderOffsetRewrite(
            final Class<S> spaceType,
            final fURI whereOrderRewriteTID,
            final fURI rewriteTID,
            final WhereOrderOffsetOperation<S> whereOrderOffsetFunction) {
        return whereOrderOffsetRewrite(spaceType, whereOrderRewriteTID, rewriteTID, whereOrderOffsetFunction, null);
    }

    public static <S extends Space> Inst whereOrderOffsetRewrite(
            final Class<S> spaceType,
            final fURI whereOrderRewriteTID,
            final fURI rewriteTID,
            final WhereOrderOffsetOperation<S> whereOrderOffsetFunction,
            final BiPredicate<S, List<Inst>> matchSpacePredicate) {

        return new WhereOrderOffsetRewriteBuilder<>(spaceType, whereOrderRewriteTID, whereOrderOffsetFunction, matchSpacePredicate)
                .tid(rewriteTID)
                .rng(ALL_STAR)
                .match(whereOrderRewriteTID, SKIP_INST_TID)
                .build();
    }

    private static class WhereOrderOffsetRewriteBuilder<S extends Space> extends RewriteBuilder<S> {
        private final fURI whereOrderRewriteTID;
        private final WhereOrderOffsetOperation<S> whereOrderOffsetOperation;

        WhereOrderOffsetRewriteBuilder(final Class<S> spaceType, final fURI whereOrderRewriteTID,
                                       final WhereOrderOffsetOperation<S> whereOrderOffsetOperation) {
            this(spaceType, whereOrderRewriteTID, whereOrderOffsetOperation, null);
        }

        WhereOrderOffsetRewriteBuilder(final Class<S> spaceType, final fURI whereOrderRewriteTID,
                                       final WhereOrderOffsetOperation<S> whereOrderOffsetOperation,
                                       final BiPredicate<S, List<Inst>> matchSpacePredicate) {
            super(spaceType);
            this.whereOrderRewriteTID = whereOrderRewriteTID;
            this.whereOrderOffsetOperation = whereOrderOffsetOperation;
            this.matchSpacePredicate = matchSpacePredicate;
            this.rewriteName = "from_where_order_skip";
            this.optimization = (space, furi, coeff) -> null;
        }

        @Override
        protected Function<Map<Inst, Inst>, List<Inst>> createRewriteFunction() {
            return map -> {
                final List<Inst> matchedInsts = new ArrayList<>(map.values());
                final Inst whereOrderInst = matchedInsts.get(0);
                final Inst skipInst = matchedInsts.get(1);

                final Obj args = whereOrderInst.args();
                if (!args.isLst() || args.asLst().count() < 3) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final fURI furi = args.asLst().at(0).asUri().uriValue();
                final String filterClause = args.asLst().at(1).asStr().jvm();
                final List<String> columns = args.asLst().at(2).asLst().lstValue().stream()
                        .map(o -> o.strValue()).toList();
                final long skipValue = skipInst.arg(0).asInt().jvm();

                final Space space = studio.phaseshift.metatron.isa.mach.type.Router.global().getSpaceFor(furi);

                if (!this.spaceType.isInstance(space)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final S typedSpace = this.spaceType.cast(space);

                if (this.matchSpacePredicate != null && !this.matchSpacePredicate.test(typedSpace, matchedInsts)) {
                    LOG.debug("matchSpacePredicate rejected where+order+offset rewrite for URI %s", furi);
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final DataPath dp = DataPath.withoutDB(furi);

                LOG.debug("evaluating native where+order+offset on %s clause='%s' columns=%s skip=%d in space %s",
                        furi, filterClause, columns, skipValue, space);

                return List.of(instC(
                        this.rewriteTid.dom(ALL_STAR).rng(this.resultTid),
                        lst(uri(furi), str(filterClause), lst(columns.stream().<Obj>map(col -> str(col)).toList()), jnt(skipValue)),
                        (lhs, inst) -> {
                            try {
                                return this.whereOrderOffsetOperation.execute(typedSpace, dp, filterClause, columns, skipValue);
                            } catch (final Exception e) {
                                throw MTronException.of(e, "failed to execute native where+order+offset operation");
                            }
                        }
                ));
            };
        }
    }

    // =========================================================================
    //  WHERE + OFFSET + LIMIT composed rewrite
    // =========================================================================

    @FunctionalInterface
    public interface WhereOffsetLimitOperation<S extends Space> {
        Obj execute(S space, DataPath dp, String filterClause, long skip, long limit) throws Exception;
    }

    public static <S extends Space> Inst whereOffsetLimitRewrite(
            final Class<S> spaceType,
            final fURI whereOffsetRewriteTID,
            final fURI rewriteTID,
            final WhereOffsetLimitOperation<S> whereOffsetLimitFunction) {
        return whereOffsetLimitRewrite(spaceType, whereOffsetRewriteTID, rewriteTID, whereOffsetLimitFunction, null);
    }

    public static <S extends Space> Inst whereOffsetLimitRewrite(
            final Class<S> spaceType,
            final fURI whereOffsetRewriteTID,
            final fURI rewriteTID,
            final WhereOffsetLimitOperation<S> whereOffsetLimitFunction,
            final BiPredicate<S, List<Inst>> matchSpacePredicate) {

        return new WhereOffsetLimitRewriteBuilder<>(spaceType, whereOffsetRewriteTID, whereOffsetLimitFunction, matchSpacePredicate)
                .tid(rewriteTID)
                .rng(ALL_STAR)
                .match(whereOffsetRewriteTID, TAKE_INST_TID)
                .build();
    }

    private static class WhereOffsetLimitRewriteBuilder<S extends Space> extends RewriteBuilder<S> {
        private final fURI whereOffsetRewriteTID;
        private final WhereOffsetLimitOperation<S> whereOffsetLimitOperation;

        WhereOffsetLimitRewriteBuilder(final Class<S> spaceType, final fURI whereOffsetRewriteTID,
                                       final WhereOffsetLimitOperation<S> whereOffsetLimitOperation) {
            this(spaceType, whereOffsetRewriteTID, whereOffsetLimitOperation, null);
        }

        WhereOffsetLimitRewriteBuilder(final Class<S> spaceType, final fURI whereOffsetRewriteTID,
                                       final WhereOffsetLimitOperation<S> whereOffsetLimitOperation,
                                       final BiPredicate<S, List<Inst>> matchSpacePredicate) {
            super(spaceType);
            this.whereOffsetRewriteTID = whereOffsetRewriteTID;
            this.whereOffsetLimitOperation = whereOffsetLimitOperation;
            this.matchSpacePredicate = matchSpacePredicate;
            this.rewriteName = "from_where_skip_take";
            this.optimization = (space, furi, coeff) -> null;
        }

        @Override
        protected Function<Map<Inst, Inst>, List<Inst>> createRewriteFunction() {
            return map -> {
                final List<Inst> matchedInsts = new ArrayList<>(map.values());
                final Inst whereOffsetInst = matchedInsts.get(0);
                final Inst takeInst = matchedInsts.get(1);

                final Obj args = whereOffsetInst.args();
                if (!args.isLst() || args.asLst().count() < 3) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final fURI furi = args.asLst().at(0).asUri().uriValue();
                final String filterClause = args.asLst().at(1).asStr().jvm();
                final long skipValue = args.asLst().at(2).asInt().jvm();
                final long limitValue = takeInst.arg(0).asInt().jvm();

                final Space space = studio.phaseshift.metatron.isa.mach.type.Router.global().getSpaceFor(furi);

                if (!this.spaceType.isInstance(space)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final S typedSpace = this.spaceType.cast(space);

                if (this.matchSpacePredicate != null && !this.matchSpacePredicate.test(typedSpace, matchedInsts)) {
                    LOG.debug("matchSpacePredicate rejected where+offset+limit rewrite for URI %s", furi);
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final DataPath dp = DataPath.withoutDB(furi);

                LOG.debug("evaluating native where+offset+limit on %s with clause '%s', skip %d, limit %d in space %s",
                        furi, filterClause, skipValue, limitValue, space);

                return List.of(instC(
                        this.rewriteTid.dom(ALL_STAR).rng(this.resultTid),
                        lst(uri(furi), str(filterClause), jnt(skipValue), jnt(limitValue)),
                        (lhs, inst) -> {
                            try {
                                return this.whereOffsetLimitOperation.execute(typedSpace, dp, filterClause, skipValue, limitValue);
                            } catch (final Exception e) {
                                throw MTronException.of(e, "failed to execute native where+offset+limit operation");
                            }
                        }
                ));
            };
        }
    }

    // =========================================================================
    //  ORDER BY rewrite
    // =========================================================================

    @FunctionalInterface
    public interface OrderOperation<S extends Space> {
        Obj execute(S space, DataPath dp, List<String> columns) throws Exception;
    }

    public static <S extends Space> Inst orderRewrite(
            final Class<S> spaceType,
            final fURI rewriteTID,
            final OrderOperation<S> orderFunction) {
        return orderRewrite(spaceType, rewriteTID, orderFunction, null);
    }

    public static <S extends Space> Inst orderRewrite(
            final Class<S> spaceType,
            final fURI rewriteTID,
            final OrderOperation<S> orderFunction,
            final BiPredicate<S, List<Inst>> matchSpacePredicate) {

        return new OrderRewriteBuilder<>(spaceType, orderFunction, matchSpacePredicate)
                .tid(rewriteTID)
                .rng(ALL_STAR)
                .match(FROM_INST_TID, ORDER_INST_TID)
                .matchFromOrAt()
                .build();
    }

    private static class OrderRewriteBuilder<S extends Space> extends RewriteBuilder<S> {
        private final OrderOperation<S> orderOperation;

        OrderRewriteBuilder(final Class<S> spaceType, final OrderOperation<S> orderOperation) {
            this(spaceType, orderOperation, null);
        }

        OrderRewriteBuilder(final Class<S> spaceType, final OrderOperation<S> orderOperation,
                            final BiPredicate<S, List<Inst>> matchSpacePredicate) {
            super(spaceType);
            this.orderOperation = orderOperation;
            this.matchSpacePredicate = matchSpacePredicate;
            this.rewriteName = "from_order";
            this.optimization = (space, furi, coeff) -> null;
        }

        @Override
        protected Function<Map<Inst, Inst>, List<Inst>> createRewriteFunction() {
            return map -> {
                final List<Inst> matchedInsts = new ArrayList<>(map.values());
                final Inst fromInst = matchedInsts.get(0);
                final Inst orderInst = matchedInsts.get(1);

                // Extract columns from order's arg — unwrap select()/rshift() wrapper
                final Obj columnSpecArg = orderInst.arg(0);
                final Obj columnSpec;
                if (columnSpecArg.isInst()) {
                    // order(select(name)) or order(rshift(name))
                    // → columnSpec is select/rshift's arg(0)
                    columnSpec = columnSpecArg.asInst().arg(0);
                } else {
                    columnSpec = columnSpecArg;
                }
                final List<String> columns = extractColumnNames(columnSpec);

                if (columns == null || columns.isEmpty()) {
                    LOG.debug("order columns too complex for native translation: %s", columnSpecArg);
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final fURI oldfURI = fromInst.arg(0).asUri().uriValue();
                final Space space = studio.phaseshift.metatron.isa.mach.type.Router.global().getSpaceFor(oldfURI);

                if (!this.spaceType.isInstance(space)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                if (this.matchPredicate != null && !this.matchPredicate.test(matchedInsts)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final S typedSpace = this.spaceType.cast(space);

                if (this.matchSpacePredicate != null && !this.matchSpacePredicate.test(typedSpace, matchedInsts)) {
                    LOG.debug("matchSpacePredicate rejected order rewrite for URI %s", oldfURI);
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final fURI expandedfURI = space.redirect(oldfURI, true);
                final DataPath dp = DataPath.withoutDB(expandedfURI);

                LOG.debug("evaluating native order on %s by %s in space %s", expandedfURI, columns, space);

                return List.of(instC(
                        this.rewriteTid.dom(ALL_STAR).rng(this.resultTid),
                        lst(uri(expandedfURI), lst(columns.stream().map(c -> (Obj) str(c)).toList())),
                        (lhs, inst) -> {
                            try {
                                return this.orderOperation.execute(typedSpace, dp, columns);
                            } catch (final Exception e) {
                                throw MTronException.of(e, "failed to execute native order operation");
                            }
                        }
                ));
            };
        }
    }

    // =========================================================================
    //  DISTINCT / DEDUP rewrite
    // =========================================================================

    @FunctionalInterface
    public interface DedupOperation<S extends Space> {
        Obj execute(S space, DataPath dp, List<String> columns) throws Exception;
    }

    public static <S extends Space> Inst dedupRewrite(
            final Class<S> spaceType,
            final fURI rewriteTID,
            final DedupOperation<S> dedupFunction) {
        return dedupRewrite(spaceType, rewriteTID, dedupFunction, null);
    }

    public static <S extends Space> Inst dedupRewrite(
            final Class<S> spaceType,
            final fURI rewriteTID,
            final DedupOperation<S> dedupFunction,
            final BiPredicate<S, List<Inst>> matchSpacePredicate) {

        return new DedupRewriteBuilder<>(spaceType, dedupFunction, matchSpacePredicate)
                .tid(rewriteTID)
                .rng(ALL_STAR)
                .match(FROM_INST_TID, DEDUP_INST_TID)
                .matchFromOrAt()
                .build();
    }

    private static class DedupRewriteBuilder<S extends Space> extends RewriteBuilder<S> {
        private final DedupOperation<S> dedupOperation;

        DedupRewriteBuilder(final Class<S> spaceType, final DedupOperation<S> dedupOperation) {
            this(spaceType, dedupOperation, null);
        }

        DedupRewriteBuilder(final Class<S> spaceType, final DedupOperation<S> dedupOperation,
                            final BiPredicate<S, List<Inst>> matchSpacePredicate) {
            super(spaceType);
            this.dedupOperation = dedupOperation;
            this.matchSpacePredicate = matchSpacePredicate;
            this.rewriteName = "from_dedup";
            this.optimization = (space, furi, coeff) -> null;
        }

        @Override
        protected Function<Map<Inst, Inst>, List<Inst>> createRewriteFunction() {
            return map -> {
                final List<Inst> matchedInsts = new ArrayList<>(map.values());
                final Inst fromInst = matchedInsts.get(0);
                final Inst dedupInst = matchedInsts.get(1);

                // Extract columns from dedup's arg — unwrap select()/rshift() wrapper
                final Obj columnSpecArg = dedupInst.arg(0);
                final Obj columnSpec;
                if (columnSpecArg.isInst()) {
                    columnSpec = columnSpecArg.asInst().arg(0);
                } else {
                    columnSpec = columnSpecArg;
                }
                final List<String> columns = extractColumnNames(columnSpec);

                if (columns == null || columns.isEmpty()) {
                    LOG.debug("dedup columns too complex for native translation: %s", columnSpecArg);
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final fURI oldfURI = fromInst.arg(0).asUri().uriValue();
                final Space space = studio.phaseshift.metatron.isa.mach.type.Router.global().getSpaceFor(oldfURI);

                if (!this.spaceType.isInstance(space)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                if (this.matchPredicate != null && !this.matchPredicate.test(matchedInsts)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final S typedSpace = this.spaceType.cast(space);

                if (this.matchSpacePredicate != null && !this.matchSpacePredicate.test(typedSpace, matchedInsts)) {
                    LOG.debug("matchSpacePredicate rejected dedup rewrite for URI %s", oldfURI);
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final fURI expandedfURI = space.redirect(oldfURI, true);
                final DataPath dp = DataPath.withoutDB(expandedfURI);

                LOG.debug("evaluating native dedup on %s by %s in space %s", expandedfURI, columns, space);

                return List.of(instC(
                        this.rewriteTid.dom(ALL_STAR).rng(this.resultTid),
                        lst(uri(expandedfURI), lst(columns.stream().map(c -> (Obj) str(c)).toList())),
                        (lhs, inst) -> {
                            try {
                                return this.dedupOperation.execute(typedSpace, dp, columns);
                            } catch (final Exception e) {
                                throw MTronException.of(e, "failed to execute native dedup operation");
                            }
                        }
                ));
            };
        }
    }
}
