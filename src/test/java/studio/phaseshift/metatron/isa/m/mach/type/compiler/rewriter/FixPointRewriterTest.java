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

package studio.phaseshift.metatron.isa.m.mach.type.compiler.rewriter;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.ANALYSIS;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * The rule order of {@code fixpoint_rewriter::T}, and the cache that remembers it.
 * <p>
 * Two different claims are pinned here. The first is the ORDER itself: an ANALYSIS rule captures the code before
 * its marker and replaces the chain, so it must run after the rewriting it reports on — and the sort that
 * arranges it is STABLE, so registration order survives within each group. The second is that the order is
 * REMEMBERED rather than re-derived: the same rules must answer the same list (one sort, not one per code value),
 * while anything that makes the rules different — a different rule, or the same rules in a different order — must
 * answer afresh. That last one is the property a cache breaks quietly, because a stale order looks exactly like a
 * fresh one until an analysis rule swallows a chain it should have reported.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FixPointRewriterTest extends AbstractMetatronTest {

    /**
     * A rewrite rule, minted the way an instset declares one: code in, code out, named by its tid.
     */
    private static Inst rule(final String name) {
        return InstSet.Helper.rewriter(f(name), code -> code);
    }

    /**
     * A capture (analysis) rule — the declared marker is the {@code ?analysis} query, which is what
     * {@code profile_analysis} and {@code explain_analysis} carry (see {@code mInstSet}), not a spelling of the name.
     */
    private static Inst analysisRule(final String name) {
        return InstSet.Helper.rewriter(f(name).addQ(ANALYSIS), code -> code);
    }

    /**
     * A rule set, in registration order — the shape {@code instset().rewrites()} answers.
     */
    private static Set<Inst> rules(final Inst... rules) {
        return new LinkedHashSet<>(List.of(rules));
    }

    /**
     * The repeat case is a HIT, and the hit is the cached LIST — not merely an equal one. A fresh container with
     * the same rules is the whole point: the instset union allocates a new set on every call, so a cache keyed on
     * that container would never hit.
     */
    @Test
    void shouldAnswerTheSameOrderForTheSameRules() {
        final Inst zulu = rule("zulu");
        final Inst analysis = analysisRule("alpha_analysis");
        final Inst yankee = rule("yankee");
        final Set<Inst> first = rules(zulu, analysis, yankee);
        final Set<Inst> again = new LinkedHashSet<>(first); // a different set, the same rules
        final List<Inst> ordered = FixPointRewriter.orderedRules(first);
        assertSame(ordered, FixPointRewriter.orderedRules(first), "the same rules are ordered once");
        assertSame(ordered, FixPointRewriter.orderedRules(again), "and the container they arrive in is not the key");
        assertIterableEquals(List.of(zulu, yankee, analysis), ordered);
    }

    /**
     * The applied order: analysis rules last, registration order otherwise (the sort is stable).
     */
    @Test
    void shouldRunAnalysisRulesLastAndKeepRegistrationOrderOtherwise() {
        final Inst zulu = rule("zulu");
        final Inst analysis = analysisRule("alpha_analysis");
        final Inst yankee = rule("yankee");
        assertIterableEquals(List.of(zulu, yankee, analysis),
                FixPointRewriter.orderedRules(rules(zulu, analysis, yankee)),
                "the analysis rule captures what the others rewrite, so it runs after them");
    }

    /**
     * THE KEY IS ORDERED. The same three rules registered in a different order are a different pass — the stable
     * sort keeps the new order — so the cache must not answer the earlier arrangement.
     */
    @Test
    void shouldNotReuseAnOrderForTheSameRulesInADifferentOrder() {
        final Inst zulu = rule("zulu");
        final Inst analysis = analysisRule("alpha_analysis");
        final Inst yankee = rule("yankee");
        final List<Inst> oneWay = FixPointRewriter.orderedRules(rules(zulu, analysis, yankee));
        final List<Inst> otherWay = FixPointRewriter.orderedRules(rules(yankee, zulu, analysis));
        assertNotSame(oneWay, otherWay, "a different registration order is a different rule set");
        assertIterableEquals(List.of(zulu, yankee, analysis), oneWay);
        assertIterableEquals(List.of(yankee, zulu, analysis), otherWay, "registration order survives within a group");
    }

    /**
     * A different rule is a different order — no rule set may borrow another's arrangement.
     */
    @Test
    void shouldNotReuseAnOrderForDifferentRules() {
        final Inst zulu = rule("zulu");
        final Inst yankee = rule("yankee");
        final Inst xray = rule("xray");
        final List<Inst> two = FixPointRewriter.orderedRules(rules(zulu, yankee));
        final List<Inst> three = FixPointRewriter.orderedRules(rules(zulu, yankee, xray));
        assertNotSame(two, three, "a larger vocabulary is a different rule set");
        assertIterableEquals(List.of(zulu, yankee, xray), three);
    }

    /**
     * No rules is not a miss, and it must not mint a list to share: an empty order is a constant.
     */
    @Test
    void shouldAnswerNoRulesWithTheEmptyOrder() {
        assertEquals(List.of(), FixPointRewriter.orderedRules(rules()));
        assertEquals(List.of(), FixPointRewriter.orderedRules(null));
    }

    /**
     * The cache is bounded, so the bound has to be exercised: more distinct rule sets than the budget must still
     * answer each one correctly, whichever of them the eviction threw away.
     */
    @Test
    void shouldStillOrderEveryRuleSetPastTheCacheBudget() {
        for (int i = 0; i < 40; i++) {
            final Inst zulu = rule("zulu" + i);
            final Inst analysis = analysisRule("alpha" + i + "_analysis");
            assertIterableEquals(List.of(zulu, analysis),
                    FixPointRewriter.orderedRules(rules(analysis, zulu)),
                    "rule set " + i + " is ordered on its own merits, cached or not");
        }
    }

    /**
     * THE MARKER IS DECLARED, NOT SPELLED. A rule that is named like a capture rule but carries no {@code ?analysis}
     * query is an ordinary rule and runs with the others — which is what pairs the rule with the marker it replaces
     * without making the ordering depend on a name that a rename could change.
     */
    @Test
    void shouldOrderByTheDeclaredMarkerAndNotByTheName() {
        final Inst spelled = rule("beta_analysis");
        final Inst declared = analysisRule("gamma_analysis");
        assertIterableEquals(List.of(spelled, declared),
                FixPointRewriter.orderedRules(rules(declared, spelled)),
                "the suffix alone is not the marker: the ?analysis query is");
    }
}
