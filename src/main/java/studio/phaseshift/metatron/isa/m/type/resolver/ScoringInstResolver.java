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

package studio.phaseshift.metatron.isa.m.type.resolver;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;

/**
 * Instruction resolver that scores candidates by specificity and selects the best match.
 * <p>
 * This resolver addresses the "resolve miss" problem where generic instructions
 * (e.g., {@code A.as(type)}) were being selected over more specific ones
 * (e.g., {@code str.as(int)}) due to insertion order dependence.
 * <p>
 * Scoring criteria (higher is better):
 * <ul>
 *   <li><b>Domain specificity (1000 pts)</b>: Non-generic domain type</li>
 *   <li><b>Domain exact match (500 pts)</b>: Domain base path matches lhs type exactly</li>
 *   <li><b>Argument specificity (500 pts)</b>: First argument has non-generic type</li>
 *   <li><b>Argument exact match (250 pts)</b>: First argument type matches user argument exactly</li>
 *   <li><b>Range specificity (100 pts)</b>: Non-generic range type</li>
 * </ul>
 * <p>
 * This follows the same pattern used by {@code BasicRouter.getSpace()} which uses
 * {@code min(Comparator.comparing(Space::pattern))} to select the most specific space.
 */
public class ScoringInstResolver implements InstResolver {

    /**
     * A candidate instruction paired with its original (pre-transformation) form
     * for scoring purposes.
     */
    private record ScoredCandidate(Inst original, Inst transformed, int score) {
    }

    @Override
    public Inst resolve(final Obj lhs, final Inst userInst, final Stream<Obj> candidates) {
        final GraphittyLogger LOG = Graphitty.log(lhs);
        if (userInst.isNoObj())
            return null;
        return candidates
                .filter(Obj::isObjInst)
                .map(Obj::asInst)
                // Basic compatibility filters (same as FirstFindInstResolver)
                .filter(i -> (i.args().isEmpty() && userInst.args().isEmpty()) || i.args().isRec() || i.args().count() >= userInst.args().count())
                .filter(i -> !lhs.isInst() || (i.dom().baseType().equals(M_ISA_INST_TID)))
                // Score BEFORE transformation (capture original specificity)
                //.filter(apiInst -> !userInst.hasDom() || userInst.dom().test(apiInst.dom()))
                //.filter(apiInst -> !userInst.hasRng() || userInst.rng().test(apiInst.rng()))
                .map(apiInst -> {
                    final int score = scoreSpecificity(lhs, userInst, apiInst);
                    // Apply transformations
                    Inst transformed = userInst.hasDom() ? apiInst.dom(userInst.dom()) : apiInst;
                    transformed = userInst.hasRng() ? transformed.rng(userInst.rng()) : transformed;
                    transformed = userInst.tid().basePath().equals(AS_INST_TID) ? transformed.rng(userInst.arg(0).asType()) : transformed;
                    transformed = lhs.isInst() ? transformed : Inst.Helper.bindGenerics(lhs, transformed, userInst);
                    int scoreBoost = 0;
                    return new ScoredCandidate(apiInst, transformed, score + scoreBoost);
                })
                // .peek(i ->  LOG.info("transformed inst: %s score %s", i.transformed, i.score))
                .filter(sc -> sc.transformed != null)
               .filter(sc -> lhs.isInst() || Inst.Helper.filterOnDomainAllowUnique(lhs,sc.transformed)) // lhs.test(sc.transformed.dom()))
          //      .map(sc -> Inst.Helper.filterOnDomainAllowUnique(lhs,sc.transformed) ? new ScoredCandidate(sc.original, sc.transformed.c(lhs.c()), sc.score) : sc)
                // Resolve args
                .map(sc -> {
                    final Poly<?, ?> resolvedArgs = Inst.Helper.resolveArgs(userInst, sc.transformed, lhs);
                    if (null == resolvedArgs)
                        return null;
                    return new ScoredCandidate(sc.original, sc.transformed.args(resolvedArgs), sc.score);
                })
                .filter(Objects::nonNull)
                // Apply final transformations
                .map(sc -> {
                    Inst result = sc.transformed.isInitial() ? sc.transformed.rng(sc.transformed.arg(0).type()) : sc.transformed;
                    result = result.c(userInst.c());
                    return new ScoredCandidate(sc.original, result, sc.score);
                })
                // Select highest scoring candidate
                .max(Comparator.comparingInt(ScoredCandidate::score))
                .map(sc -> {
                    /*LOG.info("resolved %s with score %d (dom=%s, rng=%s)",
                            sc.transformed.tid().basePath(), sc.score,
                            sc.original.dom().tid(), sc.original.rng().tid());*/
                    return sc.transformed;
                })
                .orElse(null);
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
        final fURI apiDomTid = apiInst.dom().tid();
        final fURI apiRngTid = apiInst.rng().tid();
        final fURI lhsTid = lhs.tid();

        // Domain specificity (most important - 1000 points)
        if (!apiDomTid.isGeneric() && !apiDomTid.hasPattern()) {
            score += 1000;
            // Bonus for exact domain match (500 points)
            if (lhsTid.basePath().equals(apiDomTid.basePath())) {
                score += 500;
            }
        }

        // Argument specificity (500 points for non-generic first arg)
        if (!apiInst.args().isEmpty() && !userInst.args().isEmpty()) {
            final Obj apiFirstArg = apiInst.arg(0);
            final Obj userFirstArg = userInst.arg(0);
            if (apiFirstArg != null && !apiFirstArg.isNoObj() && !apiFirstArg.tid().isGeneric()) {
                score += 500;
                // Bonus for exact argument match (250 points)
                if (userFirstArg != null && !userFirstArg.isNoObj()
                        && userFirstArg.tid().basePath().equals(apiFirstArg.tid().basePath())) {
                    score += 250;
                }
            }

            // Range-to-argument alignment (critical for as() instructions specifically)
            // When user passes a Type argument to as(), heavily favor instructions whose range matches that type
            // e.g., as(skill::T) should strongly prefer as?skill<=dir over as?file<=uri
            // IMPORTANT: Only apply this to actual 'as' instructions, not constructors or other instructions
            if (apiInst.tid().basePath().equals(AS_INST_TID) && userFirstArg != null && userFirstArg.isType() && !apiRngTid.isGeneric()) {
                // Extract the actual type being requested (the Type's tid, not the Type object's own tid)
                final fURI requestedTypeTid = userFirstArg.asType().tid();
                if (!requestedTypeTid.isGeneric() && apiRngTid.basePath().equals(requestedTypeTid.basePath())) {
                    // Huge bonus: the API's output type matches what the user asked for
                    score += 2000;
                }
            }
        }

        // Range specificity (100 points - less important than dom/args)
        if (!apiRngTid.isGeneric()) {
            score += 100;
        }

        return score;
    }
}
