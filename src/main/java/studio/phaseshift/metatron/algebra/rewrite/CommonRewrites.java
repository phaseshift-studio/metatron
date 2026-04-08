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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.function.BiFunction;

import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;

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
 *     tabledbSpace.class,
 *     TBLE_ISA_REWRITE_TID.extend("mql_count"),
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
 *     docdbSpace.class,
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
     * @param spaceType     The database space type (e.g., tabledbSpace.class, docdbSpace.class)
     * @param rewriteTid    The type ID for this specific rewrite
     * @param countFunction Function that executes the native count operation
     * @param <S>           The space type
     * @return The rewrite instruction
     */
    public static <S extends Space> Inst countRewrite(
            final Class<S> spaceType,
            final fURI rewriteTid,
            final BiFunction<S, fURI, Long> countFunction) {

        return RewriteBuilder.forDatabase(spaceType)
                .tid(rewriteTid)
                .rng(INT_TID)
                .match(FROM_INST_TID, COUNT_INST_TID)
                .matchPredicate(matches -> {
                    final Obj ref = matches.getFirst().arg(0);
                    // patterns operate across collections/tables and require different rewrtting
                    return !ref.isUri() || !ref.uriValue().retract(1).hasPattern();
                })
                .optimize("mql_count", (space, furi, coeff) -> {
                    final long count = countFunction.apply(space, furi);
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
            final BiFunction<S, fURI, Number> sumFunction) {

        return RewriteBuilder.forDatabase(spaceType)
                .tid(rewriteTID)
                .rng(INT_TID.maybe().some())
                .match(FROM_INST_TID, SUM_INST_TID)
                .optimize("mql_sum", (space, furi, coeff) -> {
                    final Number sum = sumFunction.apply(space, furi);
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
            final BiFunction<S, fURI, Double> meanFunction) {

        return RewriteBuilder.forDatabase(spaceType)
                .tid(rewriteTID)
                .rng(REAL_TID)
                .match(FROM_INST_TID, MEAN_INST_TID)
                .optimize("mql_mean", (space, furi, coeff) -> {
                    final double mean = meanFunction.apply(space, furi);
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
         * @param furi  The resolved fURI for the table/collection
         * @param limit The limit value from take(n)
         * @return The result (typically an Objs of rows)
         * @throws Exception if the operation fails
         */
        Obj execute(S space, fURI furi, long limit) throws Exception;
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
     *     tabledbSpace.class,
     *     TBLE_ISA_REWRITE_TID.extend("mql_limit"),
     *     (space, furi, limit) -> {
     *         String table = furi.segments().getFirst();
     *         String sql = "SELECT * FROM " + table + " LIMIT " + limit;
     *         try (Statement stmt = space.sjvm().createStatement();
     *              ResultSet rs = stmt.executeQuery(sql)) {
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

        return new LimitRewriteBuilder<>(spaceType, limitFunction)
                .tid(rewriteTID)
                .rng(ALL_STAR)
                .match(FROM_INST_TID, TAKE_INST_TID)
                .build();
    }

    /**
     * Specialized RewriteBuilder for limit operations that extracts the limit value
     * from the take() instruction and passes it to the optimization function.
     */
    private static class LimitRewriteBuilder<S extends Space> extends RewriteBuilder<S> {
        private final LimitOperation<S> limitOperation;

        LimitRewriteBuilder(final Class<S> spaceType, final LimitOperation<S> limitOperation) {
            super(spaceType);
            this.limitOperation = limitOperation;
            this.rewriteName = "mql_limit";
            // Set a dummy optimization since we override createRewriteFunction
            this.optimization = (space, furi, coeff) -> null;
        }

        @Override
        protected java.util.function.Function<java.util.Map<Inst, Inst>, java.util.List<Inst>> createRewriteFunction() {
            return map -> {
                // Extract fURI from the FROM instruction (first matched)
                final java.util.List<Inst> matchedInsts = new java.util.ArrayList<>(map.values());
                final Inst fromInst = matchedInsts.get(0);
                final Inst takeInst = matchedInsts.get(1);

                final fURI oldfURI = fromInst.arg(0).asUri().uriValue();
                final Space space = studio.phaseshift.metatron.isa.mach.type.Router.global().getSpace(oldfURI);

                // Check if this is the correct space type
                if (this.spaceType.isInstance(space) && (this.matchPredicate == null || this.matchPredicate.test(matchedInsts))) {
                    final S typedSpace = this.spaceType.cast(space);
                    final fURI expandedfURI = space.redirect(oldfURI, true);

                    // Extract limit value from take() instruction
                    final long limitValue = takeInst.arg(0).asInt().jvm();

                    LOG.debug("evaluating native limit operation on %s with limit %d in space %s",
                            expandedfURI, limitValue, space);

                    // Create the optimized instruction
                    return java.util.List.of(
                            studio.phaseshift.metatron.isa.m.type.impl.MInst.instC(
                                    this.rewriteTid.dom(fURI.Singleton.ALL.zero()).rng(this.resultTid),
                                    studio.phaseshift.metatron.isa.m.type.impl.MLst.lst(
                                            studio.phaseshift.metatron.isa.m.type.impl.MUri.uri(expandedfURI),
                                            jnt(limitValue)),
                                    (lhs, inst) -> {
                                        try {
                                            return this.limitOperation.execute(typedSpace, expandedfURI, limitValue);
                                        } catch (final Exception e) {
                                            throw studio.phaseshift.metatron.util.MTronException.of(e,
                                                    "failed to execute native limit operation");
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
            final BiFunction<S, fURI, Number> prodFunction) {

        return RewriteBuilder.forDatabase(spaceType)
                .tid(rewriteTID)
                .rng(INT_TID.maybe().some())
                .match(FROM_INST_TID, PROD_INST_TID)
                .optimize("mql_prod", (space, furi, coeff) -> {
                    final Number prod = prodFunction.apply(space, furi);
                    return (prod instanceof Double || prod instanceof Float)
                            ? real(prod.doubleValue())
                            : jnt(prod.longValue());
                })
                .build();
    }

    /**
     * Create a "has" (existence check) optimization rewrite.
     *
     * <p>Optimizes {@code from(furi).has()} to use native database EXISTS operations
     * instead of loading all records just to check if any exist.
     *
     * <p>Example SQL: {@code SELECT EXISTS(SELECT 1 FROM table LIMIT 1)}
     *
     * @param spaceType   The database space type
     * @param rewriteTID  The type ID for this specific rewrite
     * @param hasFunction Function that executes the native existence check
     * @param <S>         The space type
     * @return The rewrite instruction
     */
    public static <S extends Space> Inst hasRewrite(
            final Class<S> spaceType,
            final fURI rewriteTID,
            final BiFunction<S, fURI, Boolean> hasFunction) {

        return RewriteBuilder.forDatabase(spaceType)
                .tid(rewriteTID)
                .rng(BOOL_TID)
                .match(FROM_INST_TID, HAS_INST_TID)
                .matchPredicate(matches -> {
                    final Obj ref = matches.getFirst().arg(0);
                    // patterns operate across collections/tables and require different rewriting
                    return !ref.isUri() || !ref.uriValue().retract(1).hasPattern();
                })
                .optimize("mql_has", (space, furi, coeff) -> {
                    final boolean exists = hasFunction.apply(space, furi);
                    return studio.phaseshift.metatron.isa.m.type.impl.MBool.bool(exists);
                })
                .build();
    }

    /**
     * Functional interface for where operations that need access to the predicate.
     *
     * @param <S> The space type
     */
    @FunctionalInterface
    public interface WhereOperation<S extends Space> {
        /**
         * Execute the native where/filter operation.
         *
         * @param space     The database space
         * @param furi      The resolved fURI for the table/collection
         * @param sqlWhere  The SQL WHERE clause (e.g., "column > 5")
         * @return The filtered results (typically an Objs of rows)
         * @throws Exception if the operation fails
         */
        Obj execute(S space, fURI furi, String sqlWhere) throws Exception;
    }

    /**
     * Create a where (filter) optimization rewrite.
     *
     * <p>Optimizes {@code from(furi).where(predicate)} to use native database WHERE clauses
     * instead of loading all records and filtering in memory.
     *
     * <p>Currently supports simple predicates:
     * <ul>
     *   <li>{@code where([column=>value])} → {@code WHERE column = value}</li>
     *   <li>{@code where([column=>?>n])} → {@code WHERE column > n}</li>
     *   <li>{@code where([column=>?<n])} → {@code WHERE column < n}</li>
     *   <li>{@code where([column=>?>=n])} → {@code WHERE column >= n}</li>
     *   <li>{@code where([column=>?<=n])} → {@code WHERE column <= n}</li>
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
            final WhereOperation<S> whereFunction) {

        return new WhereRewriteBuilder<>(spaceType, whereFunction)
                .tid(rewriteTID)
                .rng(ALL_STAR)
                .match(FROM_INST_TID, WHERE_INST_TID)
                .build();
    }

    /**
     * Specialized RewriteBuilder for where operations that extracts and translates
     * the predicate from the where() instruction to SQL WHERE clause.
     */
    private static class WhereRewriteBuilder<S extends Space> extends RewriteBuilder<S> {
        private final WhereOperation<S> whereOperation;

        WhereRewriteBuilder(final Class<S> spaceType, final WhereOperation<S> whereOperation) {
            super(spaceType);
            this.whereOperation = whereOperation;
            this.rewriteName = "mql_where";
            this.optimization = (space, furi, coeff) -> null;
        }

        @Override
        protected java.util.function.Function<java.util.Map<Inst, Inst>, java.util.List<Inst>> createRewriteFunction() {
            return map -> {
                final java.util.List<Inst> matchedInsts = new java.util.ArrayList<>(map.values());
                final Inst fromInst = matchedInsts.get(0);
                final Inst whereInst = matchedInsts.get(1);

                final fURI oldfURI = fromInst.arg(0).asUri().uriValue();
                final Space space = studio.phaseshift.metatron.isa.mach.type.Router.global().getSpace(oldfURI);

                if (!this.spaceType.isInstance(space)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                // Try to translate the where predicate to SQL
                final Obj predicate = whereInst.arg(0);
                final String sqlWhere = tryTranslateToSQL(predicate);

                // If translation failed, fall back to normal execution
                if (sqlWhere == null) {
                    LOG.debug("where predicate too complex for SQL translation: %s", predicate);
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final S typedSpace = this.spaceType.cast(space);
                final fURI expandedfURI = space.redirect(oldfURI, true);

                LOG.debug("evaluating native where operation on %s with clause '%s' in space %s",
                        expandedfURI, sqlWhere, space);

                return java.util.List.of(
                        studio.phaseshift.metatron.isa.m.type.impl.MInst.instC(
                                this.rewriteTid.dom(fURI.Singleton.ALL.zero()).rng(this.resultTid),
                                studio.phaseshift.metatron.isa.m.type.impl.MLst.lst(
                                        studio.phaseshift.metatron.isa.m.type.impl.MUri.uri(expandedfURI),
                                        studio.phaseshift.metatron.isa.m.type.impl.MStr.str(sqlWhere)),
                                (lhs, inst) -> {
                                    try {
                                        return this.whereOperation.execute(typedSpace, expandedfURI, sqlWhere);
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
         * Try to translate a mtron predicate to SQL WHERE clause.
         * Returns null if the predicate is too complex to translate.
         */
        private String tryTranslateToSQL(final Obj predicate) {
            // Only handle Rec predicates for now
            if (!predicate.isRec()) {
                return null;
            }

            final java.util.List<String> conditions = new java.util.ArrayList<>();
            final var rec = predicate.asRec();

            for (final var rel : (Iterable<studio.phaseshift.metatron.isa.m.type.Rel>) rec.elements()::iterator) {
                final Obj key = rel.first();
                final Obj value = rel.second();

                // Key must be a URI (column name) - wildcards not supported yet
                if (!key.isUri()) {
                    return null;
                }
                final String columnName = key.asUri().uriValue().name();
                if (columnName == null || columnName.isEmpty() || columnName.equals("_") || columnName.equals("+")) {
                    return null; // Wildcard column names not supported
                }

                // Translate the value/predicate
                final String condition = translateCondition(columnName, value);
                if (condition == null) {
                    return null; // Complex condition, can't translate
                }
                conditions.add(condition);
            }

            if (conditions.isEmpty()) {
                return null;
            }

            return String.join(" AND ", conditions);
        }

        /**
         * Translate a single column condition to SQL.
         */
        private String translateCondition(final String columnName, final Obj value) {
            // Handle underscore wildcard - column exists / is not null
            if (value.isUri() && "_".equals(value.asUri().uriValue().toString())) {
                return columnName + " IS NOT NULL";
            }

            // Handle literal values - equality check
            if (value.isInt()) {
                return columnName + " = " + value.asInt().jvm();
            }
            if (value.isReal()) {
                return columnName + " = " + value.asReal().jvm();
            }
            if (value.isStr()) {
                return columnName + " = '" + escapeSqlString(value.asStr().jvm()) + "'";
            }
            if (value.isBool()) {
                return columnName + " = " + (value.asBool().jvm() ? "TRUE" : "FALSE");
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
                final String sqlOp = switch (op) {
                    case "gt" -> ">";
                    case "lt" -> "<";
                    case "gte" -> ">=";
                    case "lte" -> "<=";
                    case "neq" -> "<>";
                    case "eq" -> "=";
                    default -> null;
                };

                if (sqlOp != null && inst.args().count() > 0) {
                    final Obj arg = inst.arg(0);
                    if (arg.isInt()) {
                        return columnName + " " + sqlOp + " " + arg.asInt().jvm();
                    }
                    if (arg.isReal()) {
                        return columnName + " " + sqlOp + " " + arg.asReal().jvm();
                    }
                    if (arg.isStr()) {
                        return columnName + " " + sqlOp + " '" + escapeSqlString(arg.asStr().jvm()) + "'";
                    }
                }
            }

            // Can't translate this condition
            return null;
        }

        private String escapeSqlString(final String s) {
            return s.replace("'", "''");
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
         * @param space    The database space
         * @param furi     The resolved fURI for the table/collection
         * @param sqlWhere The SQL WHERE clause
         * @return The count of matching rows
         * @throws Exception if the operation fails
         */
        long execute(S space, fURI furi, String sqlWhere) throws Exception;
    }

    /**
     * Create a where+count optimization rewrite.
     *
     * <p>Optimizes {@code sql_where.count()} to use native database
     * {@code SELECT COUNT(*) FROM table WHERE conditions} instead of fetching
     * all filtered rows and counting in memory.
     *
     * <p>This rewrite composes with whereRewrite:
     * <pre>
     * from.where.count
     *   → sql_where.count      (whereRewrite)
     *   → sql_where_count      (this rewrite)
     * </pre>
     *
     * @param spaceType           The database space type
     * @param whereRewriteTID     The TID of the sql_where instruction to match
     * @param rewriteTID          The TID for this rewrite's output instruction
     * @param whereCountFunction  Function that executes the native count with where
     * @param <S>                 The space type
     * @return The rewrite instruction
     */
    public static <S extends Space> Inst whereCountRewrite(
            final Class<S> spaceType,
            final fURI whereRewriteTID,
            final fURI rewriteTID,
            final WhereCountOperation<S> whereCountFunction) {

        return new WhereCountRewriteBuilder<>(spaceType, whereRewriteTID, whereCountFunction)
                .tid(rewriteTID)
                .rng(INT_TID)
                .match(whereRewriteTID, COUNT_INST_TID)
                .build();
    }

    /**
     * Specialized RewriteBuilder for where+count operations that extracts
     * the fURI and WHERE clause from the preceding sql_where instruction.
     */
    private static class WhereCountRewriteBuilder<S extends Space> extends RewriteBuilder<S> {
        private final fURI whereRewriteTID;
        private final WhereCountOperation<S> whereCountOperation;

        WhereCountRewriteBuilder(final Class<S> spaceType, final fURI whereRewriteTID,
                                 final WhereCountOperation<S> whereCountOperation) {
            super(spaceType);
            this.whereRewriteTID = whereRewriteTID;
            this.whereCountOperation = whereCountOperation;
            this.rewriteName = "mql_where_count";
            this.optimization = (space, furi, coeff) -> null;
        }

        @Override
        protected java.util.function.Function<java.util.Map<Inst, Inst>, java.util.List<Inst>> createRewriteFunction() {
            return map -> {
                final java.util.List<Inst> matchedInsts = new java.util.ArrayList<>(map.values());
                final Inst whereInst = matchedInsts.get(0);  // sql_where instruction
                // matchedInsts.get(1) is count - we don't need it, just matching it

                // Extract fURI and sqlWhere from sql_where's args: [furi, sqlWhere]
                final Obj args = whereInst.args();
                if (!args.isLst() || args.asLst().count() < 2) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final fURI furi = args.asLst().at(0).asUri().uriValue();
                final String sqlWhere = args.asLst().at(1).asStr().jvm();

                final Space space = studio.phaseshift.metatron.isa.mach.type.Router.global().getSpace(furi);

                if (!this.spaceType.isInstance(space)) {
                    return matchedInsts.stream().map(Obj::asInst).toList();
                }

                final S typedSpace = this.spaceType.cast(space);

                LOG.debug("evaluating native where+count on %s with clause '%s' in space %s",
                        furi, sqlWhere, space);

                return java.util.List.of(
                        studio.phaseshift.metatron.isa.m.type.impl.MInst.instC(
                                this.rewriteTid.dom(fURI.Singleton.ALL.zero()).rng(this.resultTid),
                                studio.phaseshift.metatron.isa.m.type.impl.MLst.lst(
                                        studio.phaseshift.metatron.isa.m.type.impl.MUri.uri(furi),
                                        studio.phaseshift.metatron.isa.m.type.impl.MStr.str(sqlWhere)),
                                (lhs, inst) -> {
                                    try {
                                        final long count = this.whereCountOperation.execute(typedSpace, furi, sqlWhere);
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

    // ==================== TODO: Future Rewrite Implementations ====================
    //
    // 1. skipRewrite - Offset/Skip rows
    //    Pattern: from(table).skip(n)
    //    SQL: SELECT * FROM table OFFSET n
    //    Similar to limitRewrite but extracts offset from skip() instruction
    //
    // 2. dedupRewrite - Distinct rows
    //    Pattern: from(table).dedup()
    //    SQL: SELECT DISTINCT * FROM table
    //    Simple pattern match like count/sum/mean
    //
    // 3. paginationRewrite - Combined skip+take for pagination
    //    Pattern: from(table).skip(m).take(n)
    //    SQL: SELECT * FROM table LIMIT n OFFSET m
    //    Requires matching 3-instruction sequence and extracting both values
    //
    // 4. orderRewrite - Sorting rows
    //    Pattern: from(table).order(column)
    //    SQL: SELECT * FROM table ORDER BY column [ASC|DESC]
    //    Needs to extract column name from order() argument and map to SQL column
    //
    // 5. Combined order+take - Top N queries
    //    Pattern: from(table).order(column).take(n)
    //    SQL: SELECT * FROM table ORDER BY column LIMIT n
    //    Very common pattern for "top N" queries, significant optimization
    //
    // =============================================================================
}
