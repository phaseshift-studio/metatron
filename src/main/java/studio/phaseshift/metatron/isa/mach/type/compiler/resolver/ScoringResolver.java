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

package studio.phaseshift.metatron.isa.mach.type.compiler.resolver;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.resolver.InstSelector;
import studio.phaseshift.metatron.isa.m.type.resolver.Resolver;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.MONAD_IN;
import static studio.phaseshift.metatron.Tokens.MONAD_OUT;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_SCORING_RESOLVER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * ScoringResolver — the concrete {@code scoring_resolver::T}: the resolution stage of
 * {@code compiler::T}. It carries no config (empty rec) and owns both halves of the work:
 * <ul>
 *   <li><b>whole-code threading</b> — {@link Resolver.Helper#resolveCode} threads the output type
 *       of each inst as the input type of the next;</li>
 *   <li><b>per-instruction scoring</b> — {@link #resolveInst} scores candidate instructions by
 *       specificity and selects the best match, so this resolver is also the default
 *       {@link InstSelector}.</li>
 * </ul>
 *
 * Scoring criteria (higher is better):
 * <ul>
 *   <li><b>Domain specificity (1000 pts)</b>: Non-generic domain type</li>
 *   <li><b>Domain exact match (500 pts)</b>: Domain base path matches lhs type exactly</li>
 *   <li><b>Argument specificity (500 pts)</b>: First argument has non-generic type</li>
 *   <li><b>Argument exact match (250 pts)</b>: First argument type matches user argument exactly</li>
 *   <li><b>Range specificity (100 pts)</b>: Non-generic range type</li>
 * </ul>
 * This follows the same pattern used by {@code AbstractMachine.getSpace()} which uses
 * {@code min(Comparator.comparing(Space::pattern))} to select the most specific space.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ScoringResolver extends MRec implements Resolver, InstSelector {

    private static final ScoringResolver INSTANCE = new ScoringResolver(mutableMap(), MACH_SCORING_RESOLVER_TID, null);

    public static ScoringResolver single() {
        return INSTANCE;
    }

    public ScoringResolver() {
        this(mutableMap(), MACH_SCORING_RESOLVER_TID, null);
    }

    public ScoringResolver(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * A candidate instruction paired with its original (pre-transformation) form
     * for scoring purposes.
     */
    private record ScoredCandidate(Inst original, Inst transformed, int score) {
    }

    // fine-grained resolve-stage timing (read by the profile() instruction). times accumulate
    // across all resolveInst calls; resetTimings() zeroes them before a profiling window.
    public static final AtomicLong T_RESOLVE = new AtomicLong(0);
    public static final AtomicLong T_BIND = new AtomicLong(0);
    public static final AtomicLong T_COMPOSE = new AtomicLong(0);

    public static void resetTimings() {
        T_RESOLVE.set(0);
        T_BIND.set(0);
        T_COMPOSE.set(0);
    }

    @Override
    public Code apply(final Obj code) {
        return resolveCode(noobj(), code.asCode());
    }

    /**
     * Resolve a full instruction chain — threads the output type of each inst as the input type of
     * the next via {@link Resolver.Helper#resolveCode} — with the active {@link InstSelector} for
     * per-instruction selection (which is this resolver's own {@link #resolveInst} by default).
     */
    public static Code resolveCode(final Obj lhs, final Code code) {
        return Resolver.Helper.resolveCode(lhs, code, InstSelector.get());
    }

    @Override
    public Inst resolveInst(final Obj lhs, final Inst userInst) {
        if (userInst.hasf())
            return userInst;
        if (userInst.isNoObj())
            return null;
        /////////////////////// FROM/AS FAST RESOLUTION ///////////////////////
        if (!userInst.hasRng()) {
            final Optional<fURI> fromOrAt = Inst.Helper.isFromOrAtInstToUri(userInst);
            if (fromOrAt.isPresent()) {
                if (!fromOrAt.get().hasPattern()) {
                    final Obj fromOrAtObj = Machine.read(fromOrAt.get());
                    if (!fromOrAtObj.isNothing() && !fromOrAtObj.isCall()) {
                        userInst.logger().debug("fast from/at() resolution: %s", fromOrAt.get());
                        return Inst.Helper.bindQ(lhs, userInst, Machine.read(userInst.tid()).asInst().args(lst(fromOrAt.get().toUri())).rng(T(fromOrAtObj.typeId().maybeSome())));
                    }
                }
                return Inst.Helper.bindQ(lhs, userInst, Machine.read(userInst.tid()).asInst().args(lst(uri(fromOrAt.get()))).rng(T(ALL.maybeSome())));
            }
        }
        // a cast names its target type in its own argument, and the general path rebinds the resolved contract's
        // rng to exactly that type before applying it (see the AS_INST_TID branch below) -- that rebinding is what
        // makes the resulting tag the named type rather than the contract's own rng. This fast path returns the
        // contract un-rebound, so it serves only calls that name no type; a cast falls through and is resolved,
        // scored and rebound by the general path.
        if (userInst.tid().big().test(AS_INST_TID) && !userInst.args().elements().anyMatch(Obj::isType)) {
            final List<Obj> result = Machine.read(AS_INST_TID
                    .dom(Obj.Helper.specificTypeId(userInst.hasDom() ? userInst.dom() : lhs))
                    .rng(Obj.Helper.specificTypeId(userInst.arg(0)))).stream().toList();
            if (!result.isEmpty()) {
                userInst.logger().debug("fast as() resolution: %s", result);
                return Inst.Helper.bindQ(lhs, userInst, result.getFirst().asInst());
            }
        }
        /////////////////////////////////////////////////////////////////////

        final fURI basePath = userInst.tid().basePath();
        if (lhs.isPoly()) {
            final Obj fetched = Resolver.Helper.getPolyAutoInst(lhs.asPoly(), uri(basePath));
            if (fetched.isObjInst())
                return Inst.Helper.bindQ(lhs, userInst, fetched.asInst());
        }
        final long t0 = System.nanoTime();
        final Obj fetched = Machine.read(basePath);
        T_RESOLVE.addAndGet(System.nanoTime() - t0);
        return Inst.Helper.bindQ(lhs, userInst, resolve(lhs, userInst, fetched.stream()));
    }

    private boolean checkArgs(final Poly<?, ?> userInstArgs, final Poly<?, ?> instArgs) {
        if (instArgs.isEmpty()) // user_args and inst_args have no values, easy true
            return userInstArgs.isEmpty();
        if (userInstArgs.count() > instArgs.count()) // user_args can be less dude to {?}-zeroable inst_args
            return false;
        if (instArgs.isLst()) {
            final List<Obj> instList = instArgs.lstValue();
            final List<Obj> userList = userInstArgs.lstValue();
            for (int i = 0; i < instList.size(); i++) {
                final Obj instArg = instList.get(i);
                if (instArg.isObjCall())
                    continue;
                if (userList.size() <= i) {
                    if (!instList.get(i).tid().isZeroable())
                        return false;
                } else {
                    final Obj userArg = userList.get(i);
                    if (userArg.isObjCall())
                        continue;
                    if (!Obj.Helper.specificType(userArg).baseTypeID().bimatches(Obj.Helper.specificType(instArg).baseTypeID()))
                        return false;
                }
            }
        } else {
            int counter = -1; // rec inst_args, but lst user_args (index by counter)
            for (final Map.Entry<Obj, Obj> entry : instArgs.recValue().entrySet()) {
                counter++;
                if (entry.getValue().isObjCall())
                    continue;
                final Obj userValue = userInstArgs.at(entry.getKey()).orElse(userInstArgs.at(jnt(counter)));
                if (userValue.isObjCall())
                    continue;
                if (userValue.isNoObj()) {
                    if (!entry.getKey().tid().isZeroable())
                        return false;
                } else if (!Obj.Helper.specificType(userValue).baseTypeID().bimatches(Obj.Helper.specificType(entry.getValue()).baseTypeID()))
                    return false;
            }
        }
        return true;
    }

    private Inst resolve(final Obj lhs, final Inst userInst, final Stream<Obj> candidates) {
        //final GraphittyLogger LOG = Graphitty.log(lhs);
        if (userInst.isNoObj())
            return null;

        // Collect viable candidates after cheap pre-filters (before expensive bindGenerics + resolveArgs)
        final List<Inst> viable = candidates
                .filter(Obj::isObjInst)
                .map(Obj::asInst)
                .filter(i -> (userInst.tid().basePath().equals(AS_INST_TID) && 1 == i.args().count() && userInst.args().count() == 1) ||
                        (i.tid().hasQ(MONAD_IN) || i.tid().hasQ(MONAD_OUT)) ||
                        this.checkArgs(userInst.args(), i.args()))
                //.filter(i -> (i.args().isEmpty() && userInst.args().isEmpty()) || i.args().count() >= userInst.args().count())
                .filter(i -> !lhs.isInst() || (i.dom().baseTypeID().equals(Tokens.M_ISA_INST_TID)))
                .toList();

        if (viable.isEmpty())
            return null;

        // Short-circuit: single candidate needs no scoring
        if (viable.size() == 1) {
            return transformCandidate(lhs, userInst, viable.getFirst());
        }

        // Multiple candidates: score by specificity and select best
        return viable.stream()
                .filter(apiInst -> apiInst.dom().isGeneric() || !apiInst.dom().isNominal() || Obj.Helper.specificType(lhs).testByID(apiInst.dom()))
                .map(apiInst -> {
                    final long t0 = System.nanoTime();
                    final int score = scoreSpecificity(lhs, userInst, apiInst);
                    Inst transformed = userInst.hasDom() ? apiInst.dom(apiInst.dom().c(userInst.dom().c()).as()) : apiInst;
                    transformed = userInst.hasRng() ? transformed.rng(userInst.rng()) : transformed;
                    transformed = userInst.tid().basePath().equals(AS_INST_TID) ? transformed.rng(userInst.arg(0).isNoObj() ? NOOBJ_TYPE : Obj.Helper.specificType(userInst.arg(0))) : transformed;
                    final long t1 = System.nanoTime();
                    transformed = lhs.isInst() ? transformed : Inst.Helper.bindGenerics(lhs, transformed, userInst);
                    final long t2 = System.nanoTime();
                    T_RESOLVE.addAndGet(t1 - t0);
                    T_BIND.addAndGet(t2 - t1);
                    return new ScoredCandidate(apiInst, transformed, score);
                })
                .filter(sc -> sc.transformed != null)
                .filter(sc -> lhs.isInst() || Inst.Helper.filterOnDomainAllowUnique(lhs, sc.transformed))
                .map(sc -> {
                    final long t0 = System.nanoTime();
                    final Poly<?, ?> resolvedArgs = Inst.Helper.resolveArgs(userInst, sc.transformed, lhs);
                    if (null == resolvedArgs)
                        return null;
                    final ScoredCandidate r = new ScoredCandidate(sc.original, sc.transformed.args(resolvedArgs), sc.score);
                    final long t1 = System.nanoTime();
                    T_COMPOSE.addAndGet(t1 - t0);
                    return r;
                })
                .filter(Objects::nonNull)
                .map(sc -> {
                    final long t0 = System.nanoTime();
                    Inst result = sc.transformed.isInitial() ? sc.transformed.rng(sc.transformed.arg(0).type()) : sc.transformed;
                    result = result.c(userInst.c());
                    final long t1 = System.nanoTime();
                    T_COMPOSE.addAndGet(t1 - t0);
                    return new ScoredCandidate(sc.original, result, sc.score);
                })
                .max(Comparator.comparingInt(ScoredCandidate::score))
                .map(ScoredCandidate::transformed)
                .orElse(null);
    }

    /**
     * Apply the full transformation pipeline to a single candidate without scoring overhead.
     * Used when there is only one viable candidate — no need to wrap/unwrap in ScoredCandidate.
     */
    private Inst transformCandidate(final Obj lhs, final Inst userInst, final Inst apiInst) {
        final long t0 = System.nanoTime();
        Inst transformed = userInst.hasDom() ? apiInst.dom(userInst.dom()) : apiInst;
        transformed = userInst.hasRng() ? transformed.rng(userInst.rng()) : transformed;
        transformed = userInst.tid().basePath().equals(AS_INST_TID) ? transformed.rng(userInst.arg(0).isNoObj() ? NOOBJ_TYPE : userInst.arg(0).asType()) : transformed;
        final long t1 = System.nanoTime();
        transformed = lhs.isInst() ? transformed : Inst.Helper.bindGenerics(lhs, transformed, userInst);
        if (transformed == null)
            return null;
        if (!lhs.isInst() && !Inst.Helper.filterOnDomainAllowUnique(lhs, transformed))
            return null;
        final long t2 = System.nanoTime();
        final Poly<?, ?> resolvedArgs = Inst.Helper.resolveArgs(userInst, transformed, lhs);
        if (resolvedArgs == null)
            return null;
        transformed = transformed.args(resolvedArgs);
        transformed = transformed.isInitial() ? transformed.rng(transformed.arg(0).type()) : transformed;
        transformed = transformed.c(userInst.c());
        final long t3 = System.nanoTime();
        T_RESOLVE.addAndGet(t1 - t0);
        T_BIND.addAndGet(t2 - t1);
        T_COMPOSE.addAndGet(t3 - t2);
        return transformed;
    }

    /**
     * Score an API instruction based on how specific its type signature is.
     * Higher scores indicate more specific (preferred) instructions.
     *
     * @param lhs      the left-hand-side object
     * @param userInst the user instruction being resolved
     * @param apiInst  the candidate API instruction
     * @return specificity score (higher is more specific)
     */
    private int scoreSpecificity(final Obj lhs, final Inst userInst, final Inst apiInst) {
        int score = 0;
        final fURI apiDomID = Obj.Helper.specificTypeId(apiInst.dom());
        final fURI apiRngID = Obj.Helper.specificTypeId(apiInst.rng());
        final fURI lhsID = Obj.Helper.specificTypeId(lhs);

        // domain specificity (most important - 1000 points)
        if (!apiDomID.isGeneric() && !apiDomID.hasPattern()) {
            score += 1000;
            // bpnus for exact domain match (500 points)
            if (lhsID.basePath().equals(apiDomID.basePath())) {
                score += 500;
            }
            // bonus for more specific dom/rng matches
            if (!apiInst.dom().isBaseType())
                score += 500;
            if (!apiInst.rng().isBaseType())
                score += 500;
        }

        // Argument specificity (500 points for non-generic first arg)
        if (!apiInst.args().isEmpty() && !userInst.args().isEmpty()) {
            final Obj apiFirstArg = apiInst.arg(0);
            final Obj userFirstArg = userInst.arg(0);
            if (apiFirstArg != null && !apiFirstArg.isNoObj() && !Obj.Helper.specificTypeId(apiFirstArg).isGeneric()) {
                score += 500;
                // Bonus for exact argument match (250 points)
                if (userFirstArg != null && !userFirstArg.isNoObj()
                        && Obj.Helper.specificTypeId(userFirstArg).basePath().equals(Obj.Helper.specificTypeId(apiFirstArg).basePath())) {
                    score += 250;
                }
            }

            // Range-to-argument alignment (critical for as() instructions specifically)
            // When user passes a Type argument to as(), heavily favor instructions whose range matches that type
            // e.g., as(skill::T) should strongly prefer as?skill<=dir over as?file<=uri
            // IMPORTANT: Only apply this to actual 'as' instructions, not constructors or other instructions
            if (Obj.Helper.specificTypeId(apiInst).basePath().equals(AS_INST_TID) && userFirstArg != null && (userFirstArg.isNoObj() || userFirstArg.isType()) && !apiRngID.isGeneric()) {
                // Extract the actual type being requested (the Type's tid, not the Type object's own tid)
                final fURI requestedTypeTid = Obj.Helper.specificTypeId(userFirstArg);
                if (!requestedTypeTid.isGeneric() && apiRngID.basePath().equals(requestedTypeTid.basePath())) {
                    // Huge bonus: the API's output type matches what the user asked for
                    score += 2000;
                }
            }
        }

        // Range specificity (100 points - less important than dom/args)
        if (!apiRngID.isGeneric()) {
            score += 100;
        }

        // Function-body bonus: a candidate with an inst-f is preferable to one without
        if (apiInst.hasf()) {
            score += 50;
        }

        return score;
    }
}