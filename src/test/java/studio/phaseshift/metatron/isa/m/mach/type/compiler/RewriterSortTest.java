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

package studio.phaseshift.metatron.isa.m.mach.type.compiler;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.POST;
import static studio.phaseshift.metatron.Tokens.PRE;
import static studio.phaseshift.metatron.Tokens.ZINDEX;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * The pass order every rewriter applies its rules in ({@link Rewriter.Helper#sort}), and the cache that remembers it.
 * <p>
 * Two different claims are pinned here. The first is the ORDER itself, as the rules declare it: {@code ?zindex} ranks a
 * rule within its instset's grouping and registration breaks ties, so an ANALYSIS rule, which captures the code before
 * its marker and replaces the chain, runs after the rewriting it reports on; {@code ?pre} and {@code ?post} order a
 * rule against other groupings' families; and a declaration on a cycle is void, never a grouping's own rank. The second
 * is that the order is REMEMBERED rather than re-derived: the same rules must answer the same list (one sort, not one
 * per code value), while anything that makes the rules different — a different rule, or the same rules in a different
 * order — must answer afresh. That last one is the property a cache breaks quietly, because a stale order looks exactly
 * like a fresh one until an analysis rule swallows a chain it should have reported.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RewriterSortTest extends AbstractMetatronTest {

    /**
     * A rewrite rule, minted the way an instset declares one: code in, code out, named by its tid.
     */
    private static Inst rule(final String name) {
        return InstSet.Helper.rewriter(f(name), code -> code);
    }

    /**
     * A rule filed in an instset's rewrite folder ({@code <instset>/inst/rewrite/<name>}), with the place it declares:
     * the families it waits for ({@code ?pre}), the families that wait for it ({@code ?post}), each a
     * {@code [{+}uri::T]}, and its rank in its own folder ({@code ?zindex}).
     */
    private static Inst rule(final String tid, final String pre, final String post, final int zindex) {
        final fURI ranked = f(tid).q(ZINDEX, zindex);
        final fURI waits = null == pre ? ranked : ranked.q(PRE, pre);
        final fURI declared = null == post ? waits : waits.q(POST, post);
        return InstSet.Helper.rewriter(declared, code -> code);
    }

    /**
     * A rule at a stated place in the pass, {@code ?zindex=z} (Marko, 2026-11-20): a capture rule states a big one
     * ({@code profile_analysis} and {@code explain_analysis} carry 1000, see {@code mInstSet}), and a rule that must
     * follow another states that one's plus one. The place is declared, never a spelling of the name.
     */
    private static Inst rule(final String name, final int zindex) {
        return InstSet.Helper.rewriter(f(name).q(ZINDEX, zindex), code -> code);
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
        final Inst analysis = rule("alpha_analysis", 1000);
        final Inst yankee = rule("yankee");
        final Set<Inst> first = rules(zulu, analysis, yankee);
        final Set<Inst> again = new LinkedHashSet<>(first); // a different set, the same rules
        final List<Inst> ordered = Rewriter.Helper.sort(first);
        assertSame(ordered, Rewriter.Helper.sort(first), "the same rules are ordered once");
        assertSame(ordered, Rewriter.Helper.sort(again), "and the container they arrive in is not the key");
        assertIterableEquals(List.of(zulu, yankee, analysis), ordered);
    }

    /**
     * The applied order: a bigger ?zindex later, registration order among equals (the sort is stable), so the capture
     * rule runs after the unset rules it reports on.
     */
    @Test
    void shouldRunAnalysisRulesLastAndKeepRegistrationOrderOtherwise() {
        final Inst zulu = rule("zulu");
        final Inst analysis = rule("alpha_analysis", 1000);
        final Inst yankee = rule("yankee");
        assertIterableEquals(List.of(zulu, yankee, analysis),
                Rewriter.Helper.sort(rules(zulu, analysis, yankee)),
                "the analysis rule captures what the others rewrite, so it runs after them");
    }

    /**
     * THE KEY IS ORDERED. The same three rules registered in a different order are a different pass — the stable
     * sort keeps the new order — so the cache must not answer the earlier arrangement.
     */
    @Test
    void shouldNotReuseAnOrderForTheSameRulesInADifferentOrder() {
        final Inst zulu = rule("zulu");
        final Inst analysis = rule("alpha_analysis", 1000);
        final Inst yankee = rule("yankee");
        final List<Inst> oneWay = Rewriter.Helper.sort(rules(zulu, analysis, yankee));
        final List<Inst> otherWay = Rewriter.Helper.sort(rules(yankee, zulu, analysis));
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
        final List<Inst> two = Rewriter.Helper.sort(rules(zulu, yankee));
        final List<Inst> three = Rewriter.Helper.sort(rules(zulu, yankee, xray));
        assertNotSame(two, three, "a larger vocabulary is a different rule set");
        assertIterableEquals(List.of(zulu, yankee, xray), three);
    }

    /**
     * No rules is not a miss, and it must not mint a list to share: an empty order is a constant.
     */
    @Test
    void shouldAnswerNoRulesWithTheEmptyOrder() {
        assertEquals(List.of(), Rewriter.Helper.sort(rules()));
        assertEquals(List.of(), Rewriter.Helper.sort(null));
    }

    /**
     * The cache is bounded, so the bound has to be exercised: more distinct rule sets than the budget must still
     * answer each one correctly, whichever of them the eviction threw away.
     */
    @Test
    void shouldStillOrderEveryRuleSetPastTheCacheBudget() {
        for (int i = 0; i < 40; i++) {
            final Inst zulu = rule("zulu" + i);
            final Inst analysis = rule("alpha" + i + "_analysis", 1000);
            assertIterableEquals(List.of(zulu, analysis),
                    Rewriter.Helper.sort(rules(analysis, zulu)),
                    "rule set " + i + " is ordered on its own merits, cached or not");
        }
    }

    /**
     * THE PLACE IS DECLARED, NOT SPELLED. A rule that is named like a capture rule but states no {@code ?zindex} is an
     * ordinary rule and runs with the others, so the ordering never depends on a name that a rename could change.
     */
    @Test
    void shouldOrderByTheDeclaredMarkerAndNotByTheName() {
        final Inst spelled = rule("beta_analysis");
        final Inst declared = rule("gamma_analysis", 1000);
        assertIterableEquals(List.of(spelled, declared),
                Rewriter.Helper.sort(rules(declared, spelled)),
                "the suffix alone is not the place: the ?zindex query is");
    }

    /**
     * PLUS ONE FOLLOWS (Marko, 2026-11-20): "make it big if you want it after everything, else make it +1 the thing you
     * need it to go after". A rule at 1001 runs right after the capture rule at 1000, and both after the unset rules.
     */
    @Test
    void shouldRunAPlusOneRuleRightAfterTheRuleItFollows() {
        final Inst follower = rule("follower", 1001);
        final Inst capture = rule("capture_analysis", 1000);
        final Inst plain = rule("plain");
        assertIterableEquals(List.of(plain, capture, follower),
                Rewriter.Helper.sort(rules(follower, capture, plain)),
                "1001 follows 1000, and both follow the unset 0");
    }

    /**
     * ?PRE: every rule of each named family runs first, whatever zindex it states. Database pushdowns and a move
     * extraction ranked late still shape the chain before the rule that waits for them, and that rule's own grouping
     * keeps its rank: the capture rule (1000) still follows it. A zindex sort alone would run them all after it.
     */
    @Test
    void shouldRunEveryPreFamilyFirst() {
        final Inst shard = rule("/m/inst/rewrite/shard", "[/m/db/+/inst/rewrite,/m/mach/inst/rewrite]", null, 500);
        final Inst capture = rule("/m/inst/rewrite/capture", null, null, 1000);
        final Inst unit = rule("/m/inst/rewrite/unit", null, null, 0);
        final Inst push = rule("/m/db/tble/inst/rewrite/push", null, null, 1200);
        final Inst walk = rule("/m/db/grph/inst/rewrite/walk", null, null, 1100);
        final Inst move = rule("/m/mach/inst/rewrite/move", null, null, 900);
        assertIterableEquals(List.of(unit, move, walk, push, shard, capture),
                Rewriter.Helper.sort(rules(shard, capture, unit, push, walk, move)),
                "every named family runs before the rule that waits for it, and the capture still follows that rule");
    }

    /**
     * ?POST: every rule of the named family runs after, whatever zindex the declaring rule states (900), while a rule
     * of a family it does not name keeps its zindex place. A zindex sort alone would run the declaring rule last.
     */
    @Test
    void shouldRunAPostFamilyAfter() {
        final Inst unit = rule("/m/inst/rewrite/unit", null, null, 0);
        final Inst move = rule("/m/mach/inst/rewrite/move", null, null, 0);
        final Inst early = rule("/m/math/geo/inst/rewrite/native", null, "[/m/inst/rewrite]", 900);
        assertIterableEquals(List.of(move, early, unit), Rewriter.Helper.sort(rules(unit, move, early)),
                "the family named by ?post waits for the rule; the unnamed family does not");
    }

    /**
     * THE DECLARATIONS NEED NOT FORM A POSET (Marko, 2026-11-24): X waits for Y's family and Y for X's. The two
     * contradict each other, so both are void and zindex ranks the pair; the order still exists, and nothing loops.
     */
    @Test
    void shouldVoidTwoDeclarationsThatContradictEachOther() {
        final Inst tble = rule("/m/db/tble/inst/rewrite/x", "[/m/math/geo/inst/rewrite]", null, 5);
        final Inst geo = rule("/m/math/geo/inst/rewrite/y", "[/m/db/tble/inst/rewrite]", null, 0);
        assertIterableEquals(List.of(geo, tble), Rewriter.Helper.sort(rules(tble, geo)),
                "each waits for the other, so neither does, and the lower zindex goes first");
    }

    /**
     * ONLY THE CONTRADICTION IS VOID. X waits for Y's family and for W's, and Y waits for X's: the X-Y pair cancels, so
     * Y (100) is free to run first, while X still waits for W (900) as it declared. Releasing one rule of the cycle
     * instead would keep Y waiting for X.
     */
    @Test
    void shouldKeepEveryDeclarationOutsideTheCycle() {
        final Inst w = rule("/m/w/inst/rewrite/w", null, null, 900);
        final Inst x = rule("/m/x/inst/rewrite/x", "[/m/y/inst/rewrite,/m/w/inst/rewrite]", null, 0);
        final Inst y = rule("/m/y/inst/rewrite/y", "[/m/x/inst/rewrite]", null, 100);
        assertIterableEquals(List.of(y, w, x), Rewriter.Helper.sort(rules(w, x, y)),
                "the cycle's declarations are void; X's wait for W is not");
    }

    /**
     * A GROUPING'S RANK IS NEVER BROKEN, even by a declaration that contradicts it: Z waits for b and is waited for by
     * a, but the grouping runs a (0) before b (10), so the two declarations close a cycle through that rank and are
     * void. Z then ranks by its zindex between them.
     */
    @Test
    void shouldNeverBreakAGroupingsRankForADeclaration() {
        final Inst a = rule("/f1/inst/rewrite/a", null, null, 0);
        final Inst b = rule("/f1/inst/rewrite/b", null, null, 10);
        final Inst z = rule("/f2/inst/rewrite/z", "[/f1/inst/rewrite/b]", "[/f1/inst/rewrite/a]", 5);
        assertIterableEquals(List.of(a, z, b), Rewriter.Helper.sort(rules(a, b, z)),
                "a runs before b as its grouping ranks them, so z's contradicting declarations are void");
    }
}
