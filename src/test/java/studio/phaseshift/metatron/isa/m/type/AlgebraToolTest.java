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

package studio.phaseshift.metatron.isa.m.type;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.Law;
import studio.phaseshift.metatron.isa.m.mInstSet.AlgebraTool;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.CLAIMED;
import static studio.phaseshift.metatron.Tokens.FAILED;
import static studio.phaseshift.metatron.Tokens.GOT;
import static studio.phaseshift.metatron.Tokens.HOLDS;
import static studio.phaseshift.metatron.Tokens.INST;
import static studio.phaseshift.metatron.Tokens.INT_TID;
import static studio.phaseshift.metatron.Tokens.LAW;
import static studio.phaseshift.metatron.Tokens.LHS;
import static studio.phaseshift.metatron.Tokens.OK;
import static studio.phaseshift.metatron.Tokens.REASON;
import static studio.phaseshift.metatron.Tokens.RHS;
import static studio.phaseshift.metatron.Tokens.ROLE;
import static studio.phaseshift.metatron.Tokens.TYPE;
import static studio.phaseshift.metatron.Tokens.UNCHECKABLE;
import static studio.phaseshift.metatron.Tokens.UNCHECKED;
import static studio.phaseshift.metatron.isa.m.mInstSet.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.MULT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.PLUS_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE CLAIMED-LAW ORACLE — a claimed law is an argument, never a store read: {@link AlgebraTool} tests it against
 * known-good probe operands and answers {@code holds}, {@code failed}, {@code unchecked} (a role the law needs is not
 * declared) or {@code uncheckable} (no runnable test). The cases below are the faces of that report: a true claim, a
 * false one, an over-claim, a claim with no equation behind it, and one instruction analyzed on its own.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class AlgebraToolTest extends AbstractMetatronTest {

    /**
     * A TRUE CLAIM — int declares a ring (an add_group and a mult_monoid), so its declared monoidicity,
     * commutativity and (multiplicative) distributivity all hold.
     */
    @Test
    public void intDeclaresTheLawsItClaims() {
        final AlgebraTool.Report report = AlgebraTool.analyzeType(INT_TYPE,
                List.of(Law.monoidic, Law.commutative, Law.right_distributive));
        assertTrue(report.ok(), "int's declared ring holds the claims, got " + report.toRec());
        assertFalse(report.holds().lstValue().isEmpty(), "int's ring must hold something, got " + report.toRec());
        assertTrue(report.failed().lstValue().isEmpty(), "no claim fails on int, got " + report.toRec());
        assertTrue(report.unchecked().lstValue().isEmpty(), "no claim is unchecked on int, got " + report.toRec());
        assertTrue(laws(report.holds()).contains(Law.right_distributive.name()),
                "int's multiplication distributes over addition, got " + report.toRec());
        // the rec is the report's interface to mtron: every documented key is there
        for (final Obj key : List.<Obj>of(uri(TYPE), uri(INST), uri(CLAIMED), uri(HOLDS), uri(FAILED),
                uri(UNCHECKED), uri(UNCHECKABLE), uri(OK)))
            assertFalse(report.toRec().asRec().atDirect(key).isNoObj(),
                    "the report carries " + key + ", got " + report.toRec());
    }

    /**
     * A DELIBERATELY FALSE CLAIM — str concatenation is a monoid, not a commutative one: {@code "abc"·"xyz"} is
     * {@code "abcxyz"} while {@code "xyz"·"abc"} is {@code "xyzabc"}. The failure names the law and records what was
     * actually computed.
     */
    @Test
    public void strConcatenationIsNotCommutative() {
        final AlgebraTool.Report report = AlgebraTool.analyzeType(STR_TYPE, List.of(Law.commutative));
        assertFalse(report.ok(), "str concat is not commutative, got " + report.toRec());
        assertTrue(laws(report.failed()).contains(Law.commutative.name()),
                "the failure names commutative, got " + report.toRec());
        final Obj failure = report.failed().lstValue().getFirst();
        final Lst got = failure.asRec().atDirect(uri(GOT)).asLst();
        assertEquals(2, got.lstValue().size(), "a failure records BOTH computed sides, got " + failure);
        assertNotEquals(got.lstValue().get(0), got.lstValue().get(1),
                "a failure's two sides differ, got " + failure);
        assertFalse(failure.asRec().atDirect(uri(LHS)).strValue().equals(failure.asRec().atDirect(uri(RHS)).strValue()),
                "a failure's two sides differ, got " + failure);
    }

    /**
     * AN OVER-CLAIM — str's algebra declares a concatenation monoid and no inverse, so an {@code involution} claim is
     * not false, it is unsupported: the missing {@code inv} role is named in {@code unchecked}.
     */
    @Test
    public void involutionOnStrIsUnchecked() {
        final AlgebraTool.Report report = AlgebraTool.analyzeType(STR_TYPE, List.of(Law.involution));
        assertFalse(report.ok(), "str declares no inverse, so involution cannot be verified, got " + report.toRec());
        assertTrue(laws(report.unchecked()).contains(Law.involution.name()),
                "the unchecked claim names involution, got " + report.toRec());
        assertTrue(report.failed().lstValue().isEmpty(), "an unchecked claim is not a failure, got " + report.toRec());
        final Obj entry = report.unchecked().lstValue().getFirst();
        assertTrue(entry.asRec().has(uri(ROLE)) && entry.asRec().atDirect(uri(ROLE)).uriValue().name().equals("inv"),
                "the unchecked claim names the missing role, got " + entry);
    }

    /**
     * AN UNCHECKABLE CLAIM — {@code pure} is a declared effect, not an equation, so it carries no runnable test: it is
     * reported with a reason and is NOT a failure, so the report stays {@code ok}.
     */
    @Test
    public void pureIsUncheckableNotAFailure() {
        final AlgebraTool.Report report = AlgebraTool.analyzeType(STR_TYPE, List.of(Law.pure));
        assertTrue(report.ok(), "an uncheckable claim is not a failure, got " + report.toRec());
        assertTrue(laws(report.uncheckable()).contains(Law.pure.name()),
                "the uncheckable claim names pure, got " + report.toRec());
        assertTrue(report.failed().lstValue().isEmpty(), "an uncheckable claim is not a failure, got " + report.toRec());
        final Obj entry = report.uncheckable().lstValue().getFirst();
        assertFalse(entry.asRec().atDirect(uri(REASON)).strValue().isBlank(),
                "an uncheckable law records a one-line reason, got " + entry);
    }

    /**
     * ONE INSTRUCTION AT A TIME — {@code analyzeTypeInst} checks the claims against exactly one operation of the
     * type, so the same law can be asked of the additive and the multiplicative side separately. int's {@code plus}
     * is a commutative monoid; str's concatenation is a monoid but not a commutative one.
     */
    @Test
    public void oneInstructionAtATime() {
        final AlgebraTool.Report intPlus = AlgebraTool.analyzeTypeInst(INT_TYPE, instB(PLUS_INST_TID, lst()),
                List.of(Law.monoidic, Law.commutative));
        assertTrue(intPlus.ok(), "int's plus is a commutative monoid, got " + intPlus.toRec());
        assertEquals(2, intPlus.holds().lstValue().size(), "both claims hold on plus, got " + intPlus.toRec());
        assertTrue(intPlus.inst().isUri() && intPlus.inst().uriValue().basePath().equals(PLUS_INST_TID.basePath()),
                "the report names the instruction analyzed, got " + intPlus.toRec());

        final AlgebraTool.Report strPlus = AlgebraTool.analyzeTypeInst(STR_TYPE, instB(PLUS_INST_TID, lst()),
                List.of(Law.commutative));
        assertFalse(strPlus.ok(), "str's concat is not commutative, got " + strPlus.toRec());
        assertTrue(laws(strPlus.failed()).contains(Law.commutative.name()),
                "the failure names commutative, got " + strPlus.toRec());
    }

    /**
     * THE NUMERIC PIN — a law's report carries the OBJs each side evaluated to, one per side, so the verdict can be
     * read instead of trusted. int's addition commutes as {@code 23+12 = 12+23 = 35}, and the pair says exactly that;
     * a {@code str} on either side would fail the pin. Then the same claim over a probe that is NOT an int (the
     * {@code verify(plus?int<=int, …) "got"=>'12'} bug) is REFUSED as {@code uncheckable} with the carrier mismatch
     * named, because two sides agreeing over a str operand are no evidence about int's addition.
     */
    @Test
    public void intPlusReportsBothSidesAndRefusesAStrProbe() {
        final AlgebraTool.Report report = AlgebraTool.analyzeTypeInst(INT_TYPE, instB(PLUS_INST_TID, lst()),
                List.of(Law.commutative));
        assertTrue(report.ok(), "int's plus commutes, got " + report.toRec());
        assertTrue(laws(report.holds()).contains(Law.commutative.name()),
                "commutative holds on plus, got " + report.toRec());
        final Lst got = report.holds().lstValue().getFirst().asRec().atDirect(uri(GOT)).asLst();
        assertEquals(2, got.lstValue().size(), "the report records BOTH evaluated sides, got " + report.toRec());
        assertEquals(jnt(35), got.lstValue().get(0), "23.plus(12) is 35, got " + report.toRec());
        assertEquals(jnt(35), got.lstValue().get(1), "12.plus(23) is 35, got " + report.toRec());
        assertEquals(2, got.toString().split("35", -1).length - 1,
                "both sides render as 35, got " + report.toRec());

        // a str probe is not an int operand: the pair is refused, never compared
        final AlgebraTool.Report degenerate;
        AlgebraTool.registerSample(INT_TID, str("23"));
        try {
            degenerate = AlgebraTool.analyzeTypeInst(INT_TYPE, instB(PLUS_INST_TID, lst()),
                    List.of(Law.commutative));
        } finally {
            AlgebraTool.registerSample(INT_TID, jnt(23)); // int's real sample (see IntTest#sample), restored for any test order
        }
        assertTrue(degenerate.ok(), "a degenerate probe is uncheckable, not a failure, got " + degenerate.toRec());
        assertTrue(degenerate.holds().lstValue().isEmpty(),
                "a str operand cannot make plus commute, got " + degenerate.toRec());
        assertTrue(laws(degenerate.uncheckable()).contains(Law.commutative.name()),
                "the degenerate claim is uncheckable, got " + degenerate.toRec());
        assertEquals("the probe is not a value of the carrier",
                degenerate.uncheckable().lstValue().getFirst().asRec().atDirect(uri(REASON)).strValue(),
                "the refusal names the carrier mismatch, got " + degenerate.toRec());
    }

    /**
     * ABSORPTION IS A PROPERTY OF THE OPERATION, NOT A BROKEN PROBE — bool's {@code or} absorbs its probe
     * ({@code true ∨ false = true}), and the claim is still TRUE. A precondition that refuses a law whenever
     * {@code a·b = a} (the retired {@code "the operand does not reach the operation"} reason) turned this holding
     * claim into {@code uncheckable}; this test is the pin that the rule stays gone, so the claim is JUDGED with both
     * evaluated sides rendering {@code true}.
     */
    @Test
    public void boolOrAbsorbsItsProbeAndStillCommutes() {
        final AlgebraTool.Report report = AlgebraTool.analyzeTypeInst(BOOL_TYPE, instB(PLUS_INST_TID, lst()),
                List.of(Law.commutative));
        assertTrue(report.ok(), "bool's or commutes, got " + report.toRec());
        assertTrue(report.uncheckable().lstValue().isEmpty(),
                "an absorbing probe is not a degenerate one, so nothing is refused, got " + report.toRec());
        assertTrue(laws(report.holds()).contains(Law.commutative.name()),
                "commutative holds on or, got " + report.toRec());
        final Lst got = got(report, Law.commutative);
        assertEquals(2, got.lstValue().size(), "the report records BOTH evaluated sides, got " + report.toRec());
        assertEquals(List.of(bool(true), bool(true)), got.lstValue(),
                "true∨false and false∨true are both true, got " + report.toRec());
        assertEquals(2, got.toString().split("true", -1).length - 1,
                "both sides render as true, got " + report.toRec());
    }

    /**
     * THE SAME GUARD ON THE OTHER BOOLEAN OPERATION — bool's {@code and} is commutative and absorbing on
     * {@code false}: it collapses {@code true ∧ false} to its absorbing element exactly as {@code or} collapses to
     * {@code true}, which is why a rule keyed on "the product equals an operand" must judge both operations instead
     * of refusing them. Both claims are judged, both sides rendering {@code false}.
     */
    @Test
    public void boolAndIsCommutativeAndAbsorbingOnFalse() {
        final AlgebraTool.Report report = AlgebraTool.analyzeTypeInst(BOOL_TYPE, instB(MULT_INST_TID, lst()),
                List.of(Law.commutative, Law.absorbing));
        assertTrue(report.ok(), "bool's and commutes and false absorbs it, got " + report.toRec());
        assertTrue(report.uncheckable().lstValue().isEmpty(),
                "an absorbing probe is not a degenerate one, so nothing is refused, got " + report.toRec());
        assertEquals(2, report.holds().lstValue().size(), "both claims hold on and, got " + report.toRec());
        assertTrue(laws(report.holds()).containsAll(List.of(Law.commutative.name(), Law.absorbing.name())),
                "the holds name commutative and absorbing, got " + report.toRec());
        final Lst commutative = got(report, Law.commutative);
        assertEquals(List.of(bool(false), bool(false)), commutative.lstValue(),
                "true∧false and false∧true are both false, got " + report.toRec());
        assertEquals(2, commutative.toString().split("false", -1).length - 1,
                "both sides render as false, got " + report.toRec());
        final Lst absorbing = got(report, Law.absorbing);
        assertEquals(List.of(bool(false), bool(false)), absorbing.lstValue(),
                "true∧false is the absorbing false, got " + report.toRec());
    }

    /**
     * The {@code got} pair a judged claim in the report carries, for the named law.
     */
    private static Lst got(final AlgebraTool.Report report, final Law law) {
        return report.holds().lstValue().stream()
                .filter(entry -> entry.asRec().atDirect(uri(LAW)).uriValue().name().equals(law.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no judged " + law.name() + " claim in " + report.toRec()))
                .asRec().atDirect(uri(GOT)).asLst();
    }

    /**
     * The law names a verdict list carries — one {@code {law=>…}} rec per claim tried.
     */
    private static List<String> laws(final Lst laws) {
        return laws.lstValue().stream()
                .map(obj -> obj.asRec().atDirect(uri(LAW)).uriValue().name())
                .toList();
    }
}
