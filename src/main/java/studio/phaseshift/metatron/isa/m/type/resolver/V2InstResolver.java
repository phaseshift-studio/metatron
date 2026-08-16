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

package studio.phaseshift.metatron.isa.m.type.resolver;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.*;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.NOOBJ_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

/**
 * Tiered instruction resolver that owns the full resolution pipeline.
 * <h3>Resolution Tiers</h3>
 * <ol>
 *   <li><b>Strict matching</b> — full type compatibility via
 *       {@code isRefinementOf()} then {@code test()}, generics binding,
 *       argument resolution. Scored by specificity.</li>
 *   <li><b>Lenient matching</b> — relaxed domain check (coefficient-only),
 *       skipped predicate evaluation. Still requires generics and args.</li>
 * </ol>
 * <p>
 * If no candidate succeeds through either tier, resolution returns {@code null}.
 * There is no runtime global-router fallback — the resolver is the final word.
 * <h3>Generic Binding</h3>
 * Generics (ALL_CAPS type names like {@code A}, {@code T}) are bound from the
 * LHS type and user arguments. A domain generic bound to the LHS type propagates
 * to the range and arguments when they share the same generic parameter. This
 * preserves type information through instruction chains (e.g.,
 * {@code print?A<=A(str::T)} binds {@code A→str} from the LHS and propagates
 * to the range, so the next instruction sees {@code str} not {@code #}).
 *
 * @see InstResolver
 */
public class V2InstResolver implements InstResolver {

    private static final int SCORE_REFINEMENT_MATCH = 2000;
    private static final int SCORE_TEST_MATCH = 1000;
    private static final int SCORE_EXACT_VID_MATCH = 800;
    private static final int SCORE_CONCRETE_ARG = 500;
    private static final int SCORE_EXACT_ARG_MATCH = 400;
    private static final int SCORE_CONCRETE_RNG = 200;
    private static final int SCORE_HAS_BODY = 100;
    private static final int SCORE_COEFFICIENT_ALIGNMENT = 50;

    /**
     * A successfully transformed candidate with its specificity score.
     */
    private record Match(Inst resolved, int score) {
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    @Override
    public Inst resolveInst(final Obj lhs, final Inst userInst) {
        final GraphittyLogger LOG = Graphitty.log(lhs);

        // Already resolved
        if (userInst.hasf())
            return userInst;

        if (userInst.isNoObj())
            return null;

        // ---- fetch candidates ----
        final Stream<Obj> candidates = fetchCandidates(lhs, userInst);
        final List<Inst> apiInsts = candidates
                .filter(Obj::isObjInst)
                .map(Obj::asInst)
                .toList();

        if (apiInsts.isEmpty()) {
            LOG.debug("no candidates found for %s", userInst.tid().basePath());
            return null;
        }

        LOG.trace("resolving %s against %d candidates for lhs %s", userInst.tid().basePath(), apiInsts.size(), lhs);

        // ---- tier 1: strict matching ----
        Inst result = tryResolve(lhs, userInst, apiInsts, true);
        if (result != null)
            return result;

        // ---- tier 2: lenient matching ----
        LOG.trace("strict resolution failed for %s, trying lenient", userInst.tid().basePath());
        result = tryResolve(lhs, userInst, apiInsts, false);
        if (result != null)
            return result;

        LOG.debug("unable to resolve: %s", userInst);
        return null;
    }

    // ========================================================================
    // CANDIDATE FETCHING
    // ========================================================================

    /**
     * Fetch candidate instructions from multiple sources with escalating URI specificity.
     * <p>
     * Sources (tried in order, merged into a single stream):
     * <ol>
     *   <li>LHS record field — if the LHS is a rec, check for instructions stored at
     *       the base path of the user instruction's type ID.</li>
     *   <li>Instruction space — the primary source; reads from the router at the base
     *       path of the user instruction's type ID.</li>
     * </ol>
     *
     * @param lhs      the left-hand-side object
     * @param userInst the user instruction being resolved
     * @return stream of candidate Objs (expected to be Inst objects)
     */
    private Stream<Obj> fetchCandidates(final Obj lhs, final Inst userInst) {
        final fURI basePath = userInst.tid().basePath();

        // Source 1: LHS record — instructions embedded in the object's structure
        Stream<Obj> fromLhs = Stream.empty();
        if (lhs.isRec()) {
            final Obj at = lhs.asRec().at(basePath);
            if (!at.isNoObj())
                fromLhs = at.stream();
        }

        // Source 2: Instruction space — the primary lookup
        final Stream<Obj> fromSpace = Router.readFromSpace(basePath).stream();

        return Stream.concat(fromLhs, fromSpace);
    }

    // ========================================================================
    // RESOLUTION ENGINE
    // ========================================================================

    /**
     * Try to resolve the user instruction against the candidate API instructions.
     *
     * @param lhs      the left-hand-side object
     * @param userInst the user instruction
     * @param apiInsts the candidate API instructions
     * @param strict   true for strict mode (full type checking), false for lenient
     * @return the best matching resolved instruction, or null if none match
     */
    private Inst tryResolve(final Obj lhs, final Inst userInst, final List<Inst> apiInsts, final boolean strict) {
        final List<Match> matches = new ArrayList<>();

        for (final Inst apiInst : apiInsts) {
            final Match match = transformCandidate(lhs, userInst, apiInst, strict);
            if (match != null)
                matches.add(match);
        }

        if (matches.isEmpty())
            return null;

        // Single candidate — no scoring needed
        if (matches.size() == 1)
            return matches.getFirst().resolved;

        // Multiple candidates — select best by score
        return matches.stream()
                .max(Comparator.comparingInt(Match::score))
                .map(Match::resolved)
                .orElse(null);
    }

    /**
     * Attempt to transform a single API candidate into a resolved instruction.
     * <p>
     * The transformation pipeline:
     * <ol>
     *   <li>Pre-filter: arg count compatibility, inst-on-inst domain guard</li>
     *   <li>Apply user dom/rng hints</li>
     *   <li>Handle as() special case</li>
     *   <li>Bind generics</li>
     *   <li>Domain check</li>
     *   <li>Resolve arguments</li>
     *   <li>Handle initial-inst range inference</li>
     *   <li>Transfer user coefficient</li>
     *   <li>Score specificity</li>
     * </ol>
     *
     * @param lhs      the left-hand-side object
     * @param userInst the user instruction
     * @param apiInst  the candidate API instruction
     * @param strict   true for strict domain checking, false for lenient
     * @return a Match with the resolved instruction and score, or null if this candidate fails
     */
    private Match transformCandidate(final Obj lhs, final Inst userInst, final Inst apiInst, final boolean strict) {
        // --- pre-filter: arg count ---
        if (!(apiInst.args().isEmpty() && userInst.args().isEmpty())
                && !apiInst.args().isRec()
                && apiInst.args().count() < userInst.args().count()) {
            return null;
        }

        // --- pre-filter: inst-on-inst domain guard ---
        if (lhs.isInst() && !apiInst.dom().baseTypeID().equals(M_ISA_INST_TID)) {
            return null;
        }

        // --- apply user dom/rng hints ---
        Inst transformed = userInst.hasDom() ? apiInst.dom(userInst.dom()) : apiInst;
        transformed = userInst.hasRng() ? transformed.rng(userInst.rng()) : transformed;

        // --- handle as() special case ---
        if (userInst.tid().basePath().equals(AS_INST_TID)) {
            final Obj arg0 = userInst.arg(0);
            transformed = transformed.rng(arg0.isNoObj() ? NOOBJ_TYPE : arg0.asType());
        }

        // --- bind generics ---
        if (!lhs.isInst()) {
            transformed = bindGenerics(lhs, transformed, userInst);
            if (transformed == null)
                return null;
        }

        // --- domain check ---
        if (!lhs.isInst()) {
            final boolean domainOk = checkDomain(lhs, transformed.dom(), strict);
            if (!domainOk)
                return null;
        }

        // --- resolve arguments ---
        final Poly<?, ?> resolvedArgs = Inst.Helper.resolveArgs(userInst, transformed, lhs);
        if (resolvedArgs == null)
            return null;
        transformed = transformed.args(resolvedArgs);

        // --- initial inst range inference ---
        if (transformed.isInitial()) {
            transformed = transformed.rng(transformed.arg(0).type());
        }

        // --- transfer user coefficient ---
        transformed = transformed.c(userInst.c());

        // --- score ---
        final int score = scoreSpecificity(lhs, userInst, apiInst);

        return new Match(transformed, score);
    }

    // ========================================================================
    // DOMAIN CHECKING
    // ========================================================================

    /**
     * Check whether the LHS object is compatible with the instruction's domain.
     * <p>
     * Fast path: {@code isRefinementOf()} — a nominal VID-based walk up the type
     * hierarchy. This catches most cases (subtype relationships) without evaluating
     * predicates or doing structural type comparison.
     * <p>
     * Slow path: {@code test()} — full structural type checking with predicate
     * evaluation. Only invoked when the fast path fails.
     * <p>
     * Lenient mode: only checks coefficient compatibility via {@code c().within()}.
     *
     * @param lhs    the left-hand-side object
     * @param dom    the instruction's domain type
     * @param strict true for full type checking, false for coefficient-only
     * @return true if the LHS is compatible with the domain
     */
    private boolean checkDomain(final Obj lhs, final Type dom, final boolean strict) {
        if (!strict) {
            return lhs.c().within(dom.c());
        }
        if (lhs.isNoObj())
            return true;

        // Same logic as Inst.Helper.filterOnDomainAllowUnique
        if (lhs.testByID(dom) || lhs.test(dom))
            return true;

        // Multiplicity relaxation: if dom is singular but LHS is plural, try element-wise
        if (dom.c().isOne() && lhs.c().gt(dom.c().ONE()) && lhs.c(dom.c().ONE()).testByID(dom))
            return true;

        return false;
    }

    // ========================================================================
    // GENERIC BINDING
    // ========================================================================

    /**
     * Bind generic type parameters (ALL_CAPS names like {@code A}, {@code T}) to
     * concrete types derived from the LHS and user arguments.
     * <p>
     * Binding order:
     * <ol>
     *   <li>Domain generic ← LHS type (the most important binding)</li>
     *   <li>Range generic ← domain binding (propagates A→A in A&lt;=A patterns)</li>
     *   <li>Argument generics ← user argument types</li>
     *   <li>Re-bind range generic ← any new bindings from args</li>
     * </ol>
     * <p>
     * This fixes the classic "hail mary" issue in the original {@code bindGenerics}
     * where domain rebinding at the end didn't propagate to the range.
     *
     * @param lhs      the left-hand-side object
     * @param apiInst  the candidate API instruction (may contain generics)
     * @param userInst the user instruction (provides concrete types)
     * @return the instruction with generics bound, or null if binding fails
     */
    private Inst bindGenerics(final Obj lhs, final Inst apiInst, final Obj userInst) {
        final GraphittyLogger LOG = Graphitty.log(lhs);
        final Map<fURI, fURI> generics = new LinkedHashMap<>();
        Inst result = apiInst;

        // Step 1: Bind domain generic from LHS type
        if (result.dom().tid().one().isGeneric() && !lhs.isNoObj()) {
            final fURI domGeneric = result.dom().tid().one();
            final fURI lhsTypeId = Obj.Helper.specificTypeId(lhs);
            if (!lhsTypeId.isGeneric()) {
                generics.put(domGeneric, lhsTypeId);
                result = result.dom(lhs.type().c(result.dom().c()).as());
                LOG.trace("bound dom generic %s → %s", domGeneric, lhsTypeId);
            }
        }

        // Step 2: Bind range generic from existing bindings (propagates A<=A)
        if (result.rng().tid().one().isGeneric() && generics.containsKey(result.rng().tid().one())) {
            final fURI bound = generics.get(result.rng().tid().one());
            result = result.rng(T(bound.c(result.rng().c())));
            LOG.trace("bound rng generic %s → %s (from dom)", result.rng().tid().one(), bound);
        }

        // Step 3: Bind argument generics from user args
        if (!result.args().isEmpty() && userInst.isObjInst()) {
            final Inst userI = userInst.asInst();
            if (result.args().isLst()) {
                final List<Obj> resolvedArgs = new ArrayList<>();
                for (int i = 0; i < result.args().count(); i++) {
                    Obj apiArg = result.arg(i);
                    final Obj userArg = Optional.ofNullable(userI.arg(i)).orElse(noobj());

                    // Bind generic arg type from user arg type
                    if (apiArg.tid().isGeneric() && !userArg.isNoObj() && !userArg.isObjCall()) {
                        final fURI argGeneric = apiArg.tid().one();
                        final fURI userArgTypeId = Obj.Helper.specificTypeId(userArg);
                        if (!userArgTypeId.isGeneric()) {
                            // Check for conflicting prior binding
                            final fURI lastBinding = generics.get(argGeneric);
                            if (lastBinding != null && !userArgTypeId.test(lastBinding)) {
                                LOG.warn("generic conflict: %s bound to %s, now %s — first binding wins", argGeneric, lastBinding, userArgTypeId);
                            }
                            generics.putIfAbsent(argGeneric, userArgTypeId);
                        }
                    }

                    // Resolve nested generic in arg type
                    if (apiArg.tid().one().isGeneric() && generics.containsKey(apiArg.tid().one())) {
                        apiArg = apiArg.tid(generics.get(apiArg.tid().one())).c(apiArg.c());
                    }

                    // Recursively bind generics in nested instruction args
                    if (apiArg.isObjInst()) {
                        apiArg = bindGenerics(lhs, apiArg.asInst(), userArg);
                        if (apiArg == null) return null;
                    }

                    // Type compatibility check (non-strict — binding is prerequisite, not verdict)
                    if (!apiArg.isObjCall() && !userArg.isNoObj()
                            && !userArg.tid().isGeneric()
                            && !userArg.tid().one().isGeneric()
                            && !userArg.test(apiArg)) {
                        // Don't fail here — the domain check and resolveArgs will verify
                        // This is just a hint; generics binding is best-effort
                    }

                    resolvedArgs.add(apiArg);
                }
                result = result.args(lst(resolvedArgs));
            } else if (result.args().isRec()) {
                // Rec args: bind generics in each key-value pair
                final Map<Obj, Obj> newArgs = new LinkedHashMap<>();
                for (final Map.Entry<Obj, Obj> kv : result.args().recValue().entrySet()) {
                    Obj argVal = kv.getValue();
                    if (argVal.tid().isGeneric() && generics.containsKey(argVal.tid().one())) {
                        argVal = argVal.tid(generics.get(argVal.tid().one())).c(argVal.c());
                    }
                    newArgs.put(kv.getKey(), argVal);
                }
                result = result.args(rec(newArgs));
            }
        }

        // Step 4: Re-bind range generic — may have new bindings from args
        if (result.rng().tid().one().isGeneric()) {
            final fURI rngGeneric = result.rng().tid().one();
            if (generics.containsKey(rngGeneric)) {
                result = result.rng(T(generics.get(rngGeneric).c(result.rng().c())));
            }
        }

        // Step 5: Final domain rebind — if domain is still generic, try to bind from LHS
        if (result.dom().tid().one().isGeneric() && !lhs.isNoObj()) {
            final fURI domGeneric = result.dom().tid().one();
            if (generics.containsKey(domGeneric)) {
                result = result.dom(T(generics.get(domGeneric).c(result.dom().c())));
            } else {
                // Last resort: bind directly from LHS
                result = result.dom(lhs.type().c(result.dom().c()).as());
                result = result.tid(apiOrUser(result.tid(), userInst.tid(), generics));
            }
        }

        LOG.trace("generics mapped: %s via %s → %s", lhs, userInst, result);
        return result;
    }

    /**
     * Merge API and user instruction type IDs, preferring user-specified dom/rng
     * but falling back to generic bindings. Equivalent to the original
     * {@code Helper.apiOrUser()}.
     */
    private static fURI apiOrUser(final fURI apiInstTid, final fURI userInstTid, final Map<fURI, fURI> bindings) {
        fURI result = apiInstTid;
        if (userInstTid.hasDom()) {
            if (apiInstTid.dom().one().isGeneric())
                bindings.put(apiInstTid.dom().one(), userInstTid.dom().one());
            result = result.dom(userInstTid.dom());
        } else if (apiInstTid.dom().one().isGeneric()) {
            result = result.dom(bindings.getOrDefault(apiInstTid.dom().one(), apiInstTid.dom())).c(apiInstTid.dom().c());
        }
        if (userInstTid.hasRng()) {
            if (apiInstTid.rng().one().isGeneric())
                bindings.put(apiInstTid.rng().one(), userInstTid.rng().one());
            result = result.rng(userInstTid.rng());
        } else if (apiInstTid.rng().one().isGeneric()) {
            result = result.rng(bindings.getOrDefault(apiInstTid.rng().one(), apiInstTid.rng())).c(apiInstTid.dom().c());
        } else if (result.dom().one().isGeneric()) {
            result = result.dom(bindings.getOrDefault(result.dom().one(), result.dom())).c(result.dom().c());
        }
        return result;
    }

    // ========================================================================
    // SCORING
    // ========================================================================

    /**
     * Score an API instruction based on how specifically it matches the LHS and user args.
     * Higher scores indicate more specific (preferred) matches.
     * <p>
     * Scoring dimensions (in order of importance):
     * <ul>
     *   <li><b>Domain match quality</b> — refinement (2000) vs test (1000) vs exact VID (800)</li>
     *   <li><b>Argument specificity</b> — concrete arg type (500), exact match (400)</li>
     *   <li><b>Range specificity</b> — concrete range (200)</li>
     *   <li><b>Has function body</b> — already implemented in Java (100)</li>
     *   <li><b>Coefficient alignment</b> — domain/lhs coefficient compatibility (50)</li>
     * </ul>
     */
    private int scoreSpecificity(final Obj lhs, final Inst userInst, final Inst apiInst) {
        int score = 0;

        final fURI apiDomId = Obj.Helper.specificTypeId(apiInst.dom());
        final fURI apiRngId = Obj.Helper.specificTypeId(apiInst.rng());
        final fURI lhsId = Obj.Helper.specificTypeId(lhs);

        final boolean apiDomIsConcrete = !apiDomId.isGeneric() && !apiDomId.hasPattern();

        // --- Domain scoring ---
        if (apiDomIsConcrete) {
            // User-specified domain: heavy bonus when the API candidate's domain
            // matches what the user explicitly requested (e.g., sum?real<=real{*})
            if (userInst.hasDom()) {
                final fURI userDomId = Obj.Helper.specificTypeId(userInst.dom());
                if (apiDomId.basePath().equals(userDomId.basePath())) {
                    score += 3000;
                }
            }
            if (lhs.isType() && lhs.asType().isRefinementOf(apiInst.dom())) {
                score += SCORE_REFINEMENT_MATCH;
                if (lhsId.basePath().equals(apiDomId.basePath())) {
                    score += SCORE_EXACT_VID_MATCH;
                }
            } else if (lhs.test(apiInst.dom())) {
                score += SCORE_TEST_MATCH;
            }
        }

        // --- Argument scoring ---
        if (!apiInst.args().isEmpty() && !userInst.args().isEmpty()) {
            final Obj apiFirstArg = apiInst.arg(0);
            final Obj userFirstArg = userInst.arg(0);

            if (apiFirstArg != null && !apiFirstArg.isNoObj()) {
                if (!apiFirstArg.isType() && apiFirstArg.equals(userFirstArg)) {
                    // Exact value match (e.g., default arg values)
                    score += 1000;
                } else if (!Obj.Helper.specificTypeId(apiFirstArg).isGeneric()) {
                    score += SCORE_CONCRETE_ARG;
                    if (userFirstArg != null && !userFirstArg.isNoObj()
                            && Obj.Helper.specificTypeId(userFirstArg).basePath()
                            .equals(Obj.Helper.specificTypeId(apiFirstArg).basePath())) {
                        score += SCORE_EXACT_ARG_MATCH;
                    }
                }
            }

            // as() range-to-argument alignment
            if (Obj.Helper.specificTypeId(apiInst).basePath().equals(AS_INST_TID)
                    && userFirstArg != null
                    && (userFirstArg.isNoObj() || userFirstArg.isType())
                    && !apiRngId.isGeneric()) {
                final fURI requestedTypeId = Obj.Helper.specificTypeId(userFirstArg);
                if (!requestedTypeId.isGeneric()) {
                    if (apiRngId.basePath().equals(requestedTypeId.basePath())) {
                        // Exact range match — heavy bonus
                        score += 2000;
                    } else if (userFirstArg.isType()
                            && userFirstArg.asType().isRefinementOf(apiInst.rng())) {
                        // Requested type is a refinement of the API's range
                        // (e.g., reck::T refines rec::T). Bonus for close match.
                        score += 500;
                    } else {
                        // Concrete range mismatch — heavy penalty so generic-range
                        // or matching candidates outscore wrong-range ones.
                        // Prevents as?int<=bool from being selected for 1.as(int::T).
                        score -= 10000;
                    }
                }
            }
        }

        // --- Range scoring ---
        if (!apiRngId.isGeneric()) {
            score += SCORE_CONCRETE_RNG;
        }

        // --- Function body bonus ---
        if (apiInst.hasf()) {
            score += SCORE_HAS_BODY;
        }

        // --- Coefficient alignment ---
        if (lhs.c().within(apiInst.dom().c()) || apiInst.dom().c().within(lhs.c())) {
            score += SCORE_COEFFICIENT_ALIGNMENT;
        }

        return score;
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    private static Poly<?, ?> lst(final List<Obj> items) {
        return studio.phaseshift.metatron.isa.m.type.impl.MLst.lst(items);
    }

    private static Poly<?, ?> rec(final Map<Obj, Obj> items) {
        return studio.phaseshift.metatron.isa.m.type.impl.MRec.rec(items);
    }
}
