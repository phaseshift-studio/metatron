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

package studio.phaseshift.metatron.algebra.rewrite.db;

import studio.phaseshift.metatron.algebra.rewrite.RewriterBuilder;
import studio.phaseshift.metatron.furi.C;
import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractDataPathSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.AT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.FROM_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.RSHIFT_INST_TID;
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
 *     .tid(TBLE_ISA_REWRITE_TID.extend("sql_count"))
 *     .match(FROM_INST_TID, COUNT_INST_TID)
 *     .optimize("from_count", (space, furi, coeff) -> {
 *         String table = furi.segments().getFirst();
 *         try (Statement stmt = space.sjvm().createStatement();
 *              ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
 *             return rs.next() ? jnt(rs.getInt(1)).c(c -> c.mult(coeff)) : jnt(0);
 *         }
 *     })
 *     .build();
 * }</pre>
 *
 * @param <S> The specific Space type this rewrite applies to
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DBRewriteBuilder<S extends Space> {

    protected static final GraphittyLogger LOG = Graphitty.log(DBRewriteBuilder.class);

    protected final Class<S> spaceType;
    protected final List<fURI> matchPattern = new ArrayList<>();
    protected Predicate<List<Inst>> matchPredicate = null;
    protected boolean requiresFromOrAt = false;
    protected BiPredicate<S, List<Inst>> matchSpacePredicate = null;
    protected String rewriteName;
    protected fURI rewriteTid;
    protected fURI resultTid;
    protected NativeOptimization<S> optimization;
    protected NativeOptimizationWithURI<S> optimizationWithURI;

    protected DBRewriteBuilder(final Class<S> spaceType) {
        this.spaceType = spaceType;
    }

    /**
     * Create a new rewrite builder for a specific database space type.
     *
     * @param spaceType The class of the space to optimize for
     * @param <S>       The space type
     * @return A new builder instance
     */
    public static <S extends Space> DBRewriteBuilder<S> forDatabase(final Class<S> spaceType) {
        return new DBRewriteBuilder<>(spaceType);
    }

    /**
     * Set the type ID for this rewrite instruction.
     *
     * @param tid The rewrite type ID
     * @return This builder for chaining
     */
    public DBRewriteBuilder<S> tid(final fURI tid) {
        this.rewriteTid = tid;
        return this;
    }

    /**
     * Set the result type ID for the optimized instruction.
     *
     * @param rngTID The result type ID (e.g., INT_TID, REAL_TID)
     * @return This builder for chaining
     */
    public DBRewriteBuilder<S> rng(final fURI rngTID) {
        this.rewriteTid = this.rewriteTid.rng(rngTID);
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
    public DBRewriteBuilder<S> match(final fURI... instTIDs) {
        this.matchPattern.addAll(Arrays.asList(instTIDs));
        return this;
    }

    public DBRewriteBuilder<S> matchPredicate(final Predicate<List<Inst>> matchPredicate) {
        this.matchPredicate = matchPredicate;
        return this;
    }

    /**
     * Replaces the first match-position TID (typically {@code FROM_INST_TID})
     * with {@code ALL} so the rewrite fires for both {@code *} and {@code @}
     * source instructions.  A FROM/AT guard is merged into the
     * {@link #matchPredicate} — if the first matched instruction is neither
     * {@code FROM_INST_TID} nor {@code AT_INST_TID}, the rewrite is skipped,
     * and so it is when its source is not one the plan reads: every entry of one
     * collection and nothing below it for a plan that answers entries
     * ({@link #answersEveryEntry}), every entry and the field below it for a sum
     * or a mean ({@link #readsEveryEntry}).
     *
     * <p>Safe to call after {@link #match(fURI...)} — replaces the first
     * element of the match pattern.  A second call ANDs its {@code reads} with
     * the first's.
     *
     * @param reads the sources the plan stands in for
     * @return this builder
     */
    public DBRewriteBuilder<S> matchFromOrAt(final Predicate<DataPath> reads) {
        // Replace first match-position TID with ALL so both FROM and AT match.
        if (!this.matchPattern.isEmpty()) {
            this.matchPattern.set(0, ALL);
        }
        this.requiresFromOrAt = true;
        // Merge FROM/AT guard with any existing matchPredicate.
        final Predicate<List<Inst>> existing = this.matchPredicate;
        this.matchPredicate = matches -> {
            final Inst first = matches.getFirst().asInst();
            if (!first.tid().basePath().equals(FROM_INST_TID) && !first.tid().basePath().equals(AT_INST_TID))
                return false;
            if (first.arg(0).isUri()) {
                final fURI source = first.arg(0).uriValue();
                final Space space = Machine.current().memory().getSpaceFor(source);
                if (!reads.test(sourcePath(space, null == space ? source : space.redirect(source, true))))
                    return false;
            }
            if (existing != null && !existing.test(matches))
                return false;
            return true;
        };
        return this;
    }

    /**
     * A PLAN READS EVERY ENTRY OF ONE COLLECTION (2026-11-25), so it stands in only for a source that does:
     * {@code collection/+}. A source naming one entry ({@code table/1}), the collection itself (its type) or every level
     * below it ({@code table/#}) was pushed down as every row: {@code *db:rewrite_test/1.count()} answered 10 and
     * {@code >>value.sum()} 55 for one row of value 1. The kv pushdowns, which read the path below a table as a key
     * prefix, never ask. A sum or a mean reads the field below the entry as well ({@code collection/+/field}), which
     * its space vets; a plan that answers entries asks {@link #answersEveryEntry}.
     */
    static boolean readsEveryEntry(final DataPath source) {
        return source.hasCollection() && !source.collectionIsWildcard() && fURI.Singleton.WILD_ONE.name().equals(source.entry());
    }

    /**
     * A PLAN THAT ANSWERS ENTRIES STANDS IN ONLY FOR A SOURCE THAT ANSWERS THEM (2026-11-26): every entry of one
     * collection ({@link #readsEveryEntry}) and nothing below it. A source with a field answers the field:
     * {@code *mongo:rewrite_test/+/name.take(10)} walks the ten names, and mql_limit answered ten documents, as the take,
     * skip, where, select, order and dedup plans of every space answered entries for {@code collection/+/field}. A
     * trailing {@code /} leaves no field, so a plan reads {@code collection/+/} as {@code collection/+}; the one shape
     * measured agrees (the walk's filtered count of {@code rewrite_test/+/} is the rows' 5, as sql_where_count's).
     */
    static boolean answersEveryEntry(final DataPath source) {
        return readsEveryEntry(source) && null == source.field() && !source.hasExtension();
    }

    /**
     * THE DATA PATH OF A ROUTED SOURCE, AS ITS SPACE TAKES IT APART ({@link AbstractDataPathSpace#dataPath}). A
     * graph space's routed path keeps the space's prefix where no route strips it ({@code /g/person/+}), and
     * {@link DataPath#withoutDB} took that prefix for the collection. A table or document read takes a path's first
     * segment, absolute or not: a rule of the plans' own, an absolute path read as db/collection, had {@code mql_count}
     * count collection {@code movie} for {@code *moviedb:movie/+} routed to {@code /moviedb/}, whose read takes
     * collection {@code moviedb}, and {@code sql_count} count table {@code users} for {@code /rewrite_test/users/+}.
     */
    static DataPath sourcePath(final Space space, final fURI routed) {
        return space instanceof AbstractDataPathSpace<?> data ? data.dataPath(routed) : DataPath.withoutDB(routed);
    }

    /**
     * THE SOURCE A PLAN READS, ITS FIELD ACCESS FOLDED IN: {@code from(x) >> field} reads {@code from(x/field)}, so its
     * data path resolves the column.
     */
    private static fURI fieldSource(final List<Inst> matched) {
        final fURI source = matched.getFirst().arg(0).uriValue();
        return matched.stream()
                .filter(m -> m.tid().basePath().equals(RSHIFT_INST_TID.basePath()) && m.arg(0).isUri())
                .findFirst()
                .map(m -> source.extend(m.arg(0).uriValue()))
                .orElse(source);
    }

    /**
     * THE FIELD A SUM OR A MEAN AGGREGATES (2026-11-26): the data path its plan reads (the source, the shift after it
     * folded in, taken apart as the space takes it), or null when that names no single field. With none, the walk's sum
     * of entries is their join and their mean fails, where {@code $sum: 1} counted the documents and SUM and AVG over
     * no column answered 0 and 0.0. A wildcard field ({@code +/+}) and a wildcard below the field ({@code +/points/+},
     * whose walk sums the sub-document's values, where {@code $sum: "$points.+"} answered 0) name no single field
     * either, nor do a shift whose key is no uri, which the fold leaves out ({@code +/value>>'x'} summed value, where
     * the walk sums nothing to 0), and a branched path ({@code +/{value,id}}, two fields; a branched shift,
     * {@code >>{value,id}}, is no uri and is declined as one). A space declines what else it cannot
     * aggregate, such as a path below a field that holds none.
     */
    public static DataPath fieldPath(final Space space, final List<Inst> matches) {
        if (matches.stream().anyMatch(m -> m.tid().basePath().equals(RSHIFT_INST_TID.basePath()) && !m.arg(0).isUri()))
            return null;
        final fURI folded = fieldSource(matches);
        if (folded.hasBranches())
            return null;
        final DataPath source = sourcePath(space, space.redirect(folded, true));
        return source.hasField() && !source.fieldIsWildcard() && !source.extensionIsWildcard() ? source : null;
    }

    /**
     * Set a space-aware match predicate for the rewrite.
     *
     * <p>Unlike {@link #matchPredicate(Predicate)}, this predicate receives the
     * typed space instance alongside the matched instructions.  Use it for
     * guards that depend on space state — for example, checking whether a
     * collection name corresponds to an actual database table.
     *
     * <p>The predicate is AND-ed with {@link #matchPredicate}: if either
     * returns {@code false} the rewrite is skipped and the original
     * instructions are preserved.
     *
     * @param matchSpacePredicate a predicate receiving the typed space and
     *                            the matched instructions
     * @return This builder for chaining
     */
    public DBRewriteBuilder<S> matchSpacePredicate(final BiPredicate<S, List<Inst>> matchSpacePredicate) {
        this.matchSpacePredicate = matchSpacePredicate;
        return this;
    }

    /**
     * Define the native optimization with a type-safe lambda.
     *
     * <p>The optimization function receives the typed space, the resolved fURI,
     * and the coefficient from the instruction chain, and should return the
     * optimized result.
     *
     * @param name         The name of the native operation (for logging/debugging)
     * @param optimization The optimization function
     * @return This builder for chaining
     */
    public DBRewriteBuilder<S> optimize(final String name, final NativeOptimization<S> optimization) {
        this.rewriteName = name;
        this.optimization = optimization;
        return this;
    }

    /**
     * Define the native optimization with a URI-aware lambda.
     *
     * <p>Like {@link #optimize(String, NativeOptimization)} but the lambda also
     * receives the expanded fURI (after space routing).  Use this when the
     * optimization needs the URI itself — for example, to strip the space
     * prefix and match against an internal key-value store.
     */
    public DBRewriteBuilder<S> optimizeWithURI(final String name, final NativeOptimizationWithURI<S> optimization) {
        this.rewriteName = name;
        this.optimizationWithURI = optimization;
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
        if (this.optimization == null && this.optimizationWithURI == null) {
            throw MTronException.of("optimization function must be set");
        }
        if (this.resultTid == null) {
            // Default to ALL if not specified
            this.resultTid = ALL;
        }

        return InstSet.Helper.rewriter(this.rewriteTid, code -> {
            // early bail: skip the pattern search unless the code contains every non-wildcard
            // match tid. a cheap pre-check that avoids the search + space resolution for codes
            // that can never match (e.g. a projection rule on a code with no rshift/from).
            final List<fURI> required = this.matchPattern.stream().filter(tid -> !ALL.equals(tid)).toList();
            if (!required.isEmpty() && !required.stream().allMatch(r ->
                    code.codeValue().stream().anyMatch(i -> i.tid().basePath().equals(r.basePath())))) {
                return code;
            }
            // from/at rewrites also need a source instruction (* or @) in the code; the match
            // pattern replaced it with ALL, so the tid pre-check above cannot see it.
            if (this.requiresFromOrAt && code.codeValue().stream().noneMatch(i ->
                    i.tid().basePath().equals(FROM_INST_TID.basePath()) || i.tid().basePath().equals(AT_INST_TID.basePath()))) {
                return code;
            }
            return code.selfJVM(RewriterBuilder.search(code.codeValue())
                    .match(this.matchPattern.stream()
                            .map(tid -> instB(tid, lst()))
                            .toList())
                    .rewrite(this.createRewriteFunction())
            ).asCode();
        });
    }

    /**
     * Create the rewrite function that will be applied when the pattern matches.
     */
    protected Function<Map<Inst, Inst>, List<Inst>> createRewriteFunction() {
        return map -> {
            final List<Inst> matched = map.values().stream().toList();
            // ONLY A SPACE'S SOURCE IS REWRITTEN (Marko, 2026-11-12): a rewrite whose space has no native instruction to
            // call (sql(), gremlin()) is the identity. A match whose first instruction reads no uri -- a seed such as
            // start({1,2,3}) or a filter, which a wildcard first position also matches -- is left as it stands, never
            // dereferenced into a fail; the space type and the match predicate below decide the rest.
            if (!matched.getFirst().arg(0).isUri())
                return matched;
            final fURI oldfURI = fieldSource(matched);
            final Space space = Machine.current().memory().getSpaceFor(oldfURI);

            // Check if this is the correct space type
            if (this.spaceType.isInstance(space) && (this.matchPredicate == null || this.matchPredicate.test(map.values().stream().toList()))) {
                final S typedSpace = this.spaceType.cast(space);

                // Check space-aware predicate (e.g., table-existence guard)
                if (this.matchSpacePredicate != null && !this.matchSpacePredicate.test(typedSpace, map.values().stream().toList())) {
                    LOG.debug("matchSpacePredicate rejected rewrite %s for URI %s", this.rewriteName, oldfURI);
                    return map.values().stream().map(Obj::asInst).toList();
                }

                final fURI expandedfURI = space.redirect(oldfURI, true);

                // the point of the last instruction in the chain: the copies it emits. Not c(), which is span x point;
                // a matched step's span is the pattern's (RewriterBuilder.instsMatch)
                final C<?, ?> coeff = map.values().stream()
                        .reduce((first, second) -> second)
                        .map(inst -> inst.tid().d())
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
    protected Inst createOptimizedInst(final S typedSpace, final fURI expandedfURI, final C<?, ?> coeff) {
        final DataPath dp = sourcePath(typedSpace, expandedfURI);
        return instC(
                this.rewriteTid.dom(ALL.zero()).rng(this.resultTid),
                lst(uri(expandedfURI)),
                (lhs, inst) -> {
                    try {
                        if (this.optimizationWithURI != null)
                            return this.optimizationWithURI.execute(typedSpace, dp, expandedfURI, coeff);
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
         * @param space       The database space
         * @param dp          The decomposed DataPath for the operation target
         * @param coefficient The coefficient from the instruction chain
         * @return The result object
         * @throws Exception if the operation fails
         */
        Obj execute(final S space, final DataPath dp, final C<?, ?> coefficient) throws Exception;
    }

    /**
     * Functional interface for URI-aware native database optimizations.
     *
     * <p>Like {@link NativeOptimization} but also receives the expanded
     * fURI (after space routing) — useful when the optimization needs
     * the raw URI segments, e.g., to strip a space prefix and match
     * against an internal key-value store.
     *
     * @param <S> The space type
     */
    @FunctionalInterface
    public interface NativeOptimizationWithURI<S extends Space> {
        /**
         * Execute the native database operation.
         *
         * @param space       The database space
         * @param dp          The decomposed DataPath for the operation target
         * @param expandedURI The expanded fURI after space routing
         * @param coefficient The coefficient from the instruction chain
         * @return The result object
         * @throws Exception if the operation fails
         */
        Obj execute(final S space, final DataPath dp, final fURI expandedURI, final C<?, ?> coefficient) throws Exception;
    }
}
