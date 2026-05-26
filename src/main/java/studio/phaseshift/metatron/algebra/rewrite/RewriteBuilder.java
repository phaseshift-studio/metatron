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

import studio.phaseshift.metatron.furi.C;
import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Fluent builder API for creating database optimization rewrites.
 *
 * <p>This builder simplifies the creation of instruction rewrites that optimize
 * database operations by replacing generic instruction sequences with native
 * database operations.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Optimize from().count() to use native COUNT(*)
 * Inst countRewrite = RewriteBuilder.forDatabase(tbleSpace.class)
 *     .tid(TBLE_ISA_REWRITE_TID.extend("mql_count"))
 *     .match(FROM_INST_TID, COUNT_INST_TID)
 *     .optimize("sql_count", (space, furi, coeff) -> {
 *         String table = furi.segments().getFirst();
 *         try (Statement stmt = space.sjvm().createStatement();
 *              ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
 *             return rs.next() ? jnt(rs.getInt(1)).c(c -> c.mult(coeff)) : jnt(0);
 *         }
 *     })
 *     .build();
 * }</pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 * @param <S> The specific Space type this rewrite applies to
 */
public class RewriteBuilder<S extends Space> {

    protected static final GraphittyLogger LOG = Graphitty.log(RewriteBuilder.class);

    protected final Class<S> spaceType;
    protected final List<fURI> matchPattern = new ArrayList<>();
    protected Predicate<List<Inst>> matchPredicate = null;
    protected String rewriteName;
    protected fURI rewriteTid;
    protected fURI resultTid;
    protected NativeOptimization<S> optimization;

    protected RewriteBuilder(final Class<S> spaceType) {
        this.spaceType = spaceType;
    }

    /**
     * Create a new rewrite builder for a specific database space type.
     *
     * @param spaceType The class of the space to optimize for
     * @param <S> The space type
     * @return A new builder instance
     */
    public static <S extends Space> RewriteBuilder<S> forDatabase(final Class<S> spaceType) {
        return new RewriteBuilder<>(spaceType);
    }

    /**
     * Set the type ID for this rewrite instruction.
     *
     * @param tid The rewrite type ID
     * @return This builder for chaining
     */
    public RewriteBuilder<S> tid(final fURI tid) {
        this.rewriteTid = tid;
        return this;
    }

    /**
     * Set the result type ID for the optimized instruction.
     *
     * @param rngTID The result type ID (e.g., INT_TID, REAL_TID)
     * @return This builder for chaining
     */
    public RewriteBuilder<S> rng(final fURI rngTID) {
        this.resultTid = rngTID;
        return this;
    }

    /**
     * Match a sequence of instructions to optimize.
     *
     * <p>The rewrite will only apply when this exact sequence of instructions
     * is found in the code being optimized.
     *
     * @param instTIDs The instruction type IDs to match (in order)
     * @return This builder for chaining
     */
    public RewriteBuilder<S> match(final fURI... instTIDs) {
        this.matchPattern.addAll(Arrays.asList(instTIDs));
        return this;
    }
    
    public RewriteBuilder<S> matchPredicate(final Predicate<List<Inst>> matchPredicate) {
        this.matchPredicate = matchPredicate;
        return this;
    }

    /**
     * Define the native optimization with a type-safe lambda.
     *
     * <p>The optimization function receives the typed space, the resolved fURI,
     * and the coefficient from the instruction chain, and should return the
     * optimized result.
     *
     * @param name The name of the native operation (for logging/debugging)
     * @param optimization The optimization function
     * @return This builder for chaining
     */
    public RewriteBuilder<S> optimize(final String name, final NativeOptimization<S> optimization) {
        this.rewriteName = name;
        this.optimization = optimization;
        return this;
    }

    /**
     * Build the final Inst rewrite.
     *
     * <p>This creates an instruction that will search for the match pattern
     * in code and replace it with the optimized native operation.
     *
     * @return The rewrite instruction
     */
    public Inst build() {
        if (this.rewriteTid == null) {
            throw MTronException.of("rewrite TID must be set");
        }
        if (this.matchPattern.isEmpty()) {
            throw MTronException.of("match pattern must be set");
        }
        if (this.optimization == null) {
            throw MTronException.of("optimization function must be set");
        }
        if (this.resultTid == null) {
            // Default to ALL if not specified
            this.resultTid = ALL;
        }

        return InstSet.Helper.rewriter(this.rewriteTid, code ->
                code.selfJVM(Rewriter.search(code.codeValue())
                        .match(this.matchPattern.stream()
                                .map(tid -> instB(tid, lst()))
                                .toList())
                        .rewrite(this.createRewriteFunction())
                ).asCode());
    }

    /**
     * Create the rewrite function that will be applied when the pattern matches.
     */
    protected Function<Map<Inst, Inst>, List<Inst>> createRewriteFunction() {
        return map -> {
            // Extract fURI from the first instruction (FROM instruction)
            final fURI oldfURI = map.values().iterator().next().arg(0).asUri().uriValue();
            final Space space = Router.global().getSpaceFor(oldfURI);

            // Check if this is the correct space type
            if (this.spaceType.isInstance(space) && (this.matchPredicate == null || this.matchPredicate.test(map.values().stream().toList()))) {
                final S typedSpace = this.spaceType.cast(space);
                final fURI expandedfURI = space.redirect(oldfURI, true);

                // Extract coefficient from the last instruction in the chain
                final C<?,?> coeff = map.values().stream()
                        .reduce((first, second) -> second)
                        .map(Inst::c)
                        .orElse(cInt.C_ONE);

                LOG.debug("evaluating native %s operation on %s in space %s", this.rewriteName, expandedfURI, space);

                // Create the optimized instruction
                return List.of(this.createOptimizedInst(typedSpace, expandedfURI, coeff));
            }

            // not the right space type or the final match predicate failed - return original instructions
            
            return map.values().stream().map(Obj::asInst).toList();
        };
    }

    /**
     * Create the optimized instruction that executes the native database operation.
     */
    protected Inst createOptimizedInst(final S typedSpace, final fURI expandedfURI, final C<?,?> coeff) {
        final DataPath dp = DataPath.ofSpaceRelative(expandedfURI, null);
        return instC(
                this.rewriteTid.dom(ALL.zero()).rng(this.resultTid),
                lst(uri(expandedfURI)),
                (lhs, inst) -> {
                    try {
                        return this.optimization.execute(typedSpace, dp, coeff);
                    } catch (final Exception e) {
                        throw MTronException.of(e, "failed to execute native %s operation", this.rewriteName);
                    }
                }
        );
    }

    /**
     * Functional interface for native database optimizations.
     *
     * @param <S> The space type
     */
    @FunctionalInterface
    public interface NativeOptimization<S extends Space> {
        /**
         * Execute the native database operation.
         *
         * @param space  The database space
         * @param dp     The decomposed DataPath for the operation target
         * @param coefficient The coefficient from the instruction chain
         * @return The result object
         * @throws Exception if the operation fails
         */
        Obj execute(final S space, final DataPath dp, final C<?,?> coefficient) throws Exception;
    }
}
