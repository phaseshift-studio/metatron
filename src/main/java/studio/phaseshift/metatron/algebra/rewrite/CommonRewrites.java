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
 * // In docInstSet:
 * CommonRewrites.countRewrite(
 *     docSpace.class,
 *     DOC_ISA_REWRITE_TID.extend("native_count"),
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
     * @param spaceType     The database space type (e.g., tbleSpace.class, docSpace.class)
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
                .resultTid(INT_TID)
                .match(FROM_INST_TID, COUNT_INST_TID)
                .optimize("native_count", (space, furi, coeff) -> {
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
     * @param rewriteTid  The type ID for this specific rewrite
     * @param sumFunction Function that executes the native sum operation
     * @param <S>         The space type
     * @return The rewrite instruction
     */
    public static <S extends Space> Inst sumRewrite(
            final Class<S> spaceType,
            final fURI rewriteTid,
            final BiFunction<S, fURI, Number> sumFunction) {

        return RewriteBuilder.forDatabase(spaceType)
                .tid(rewriteTid)
                .resultTid(INT_TID.maybe().some())
                .match(FROM_INST_TID, SUM_INST_TID)
                .optimize("native_sum", (space, furi, coeff) -> {
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
     * @param rewriteTid   The type ID for this specific rewrite
     * @param meanFunction Function that executes the native mean/average operation
     * @param <S>          The space type
     * @return The rewrite instruction
     */
    public static <S extends Space> Inst meanRewrite(
            final Class<S> spaceType,
            final fURI rewriteTid,
            final BiFunction<S, fURI, Double> meanFunction) {

        return RewriteBuilder.forDatabase(spaceType)
                .tid(rewriteTid)
                .resultTid(REAL_TID)
                .match(FROM_INST_TID, MEAN_INST_TID)
                .optimize("native_mean", (space, furi, coeff) -> {
                    final double mean = meanFunction.apply(space, furi);
                    return real(mean);
                })
                .build();
    }

    /**
     * Create a product optimization rewrite.
     *
     * <p>Optimizes {@code from(furi).prod()} to use native database operations
     * where supported.
     *
     * @param spaceType    The database space type
     * @param rewriteTid   The type ID for this specific rewrite
     * @param prodFunction Function that executes the native product operation
     * @param <S>          The space type
     * @return The rewrite instruction
     */
    public static <S extends Space> Inst prodRewrite(
            final Class<S> spaceType,
            final fURI rewriteTid,
            final BiFunction<S, fURI, Number> prodFunction) {

        return RewriteBuilder.forDatabase(spaceType)
                .tid(rewriteTid)
                .resultTid(INT_TID.maybe().some())
                .match(FROM_INST_TID, PROD_INST_TID)
                .optimize("native_prod", (space, furi, coeff) -> {
                    final Number prod = prodFunction.apply(space, furi);
                    return (prod instanceof Double || prod instanceof Float)
                            ? real(prod.doubleValue())
                            : jnt(prod.longValue());
                })
                .build();
    }
}
