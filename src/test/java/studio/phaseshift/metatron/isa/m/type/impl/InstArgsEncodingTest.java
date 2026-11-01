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

package studio.phaseshift.metatron.isa.m.type.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.INT_TID;
import static studio.phaseshift.metatron.Tokens.URI_TID;
import static studio.phaseshift.metatron.Tokens.REC_TID;
import static studio.phaseshift.metatron.Tokens.STR_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.ALL_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE `args` COMPONENT OF A TID — the declared argument types {@code MInst.ARGS_IN_TID} puts there, and the two
 * consumers that read the declaration back: the law-signature matcher ({@code mInstSet.AlgebraTool.matches}) and
 * argument admission at resolution ({@link Inst.Helper#admitsArgs}).
 * <p>
 * The flag is OFF by default and it is read where an inst is MINTED, so every scenario here mints its own probe,
 * never a registered instruction, and restores the flag afterwards: surefire reuses forks across classes, and a
 * leaked flag would re-shape the tids of every instset that registered after it.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class InstArgsEncodingTest extends AbstractMetatronTest {

    /**
     * A minting address no instset registers, so a probe is a fresh contract and never a write into a live space.
     */
    private static final fURI PROBE_TID = f("/m/declared_arg_probe");

    /**
     * The address a USER call is minted at — an inst that names an operation without claiming its declaration.
     */
    private static final fURI CALL_TID = f("/m/declared_arg_call");

    private boolean savedArgsInTid;

    @BeforeEach
    void rememberArgsInTid() {
        this.savedArgsInTid = MInst.ARGS_IN_TID;
    }

    @AfterEach
    void restoreArgsInTid() {
        MInst.ARGS_IN_TID = this.savedArgsInTid;
    }

    /**
     * A probe instruction: {@code int x int -> int}, with the given declared arguments.
     */
    private Inst probe(final Poly<?, ?> args) {
        return MInst.instC(this.probeTid(), args, (lhs, inst) -> lhs);
    }

    private fURI probeTid() {
        return PROBE_TID.dom(INT_TYPE.vid()).rng(INT_TYPE.vid());
    }

    @Test
    void theFlagIsOffByDefaultAndAddsNoKeyToTheTid() {
        // a build that OPTS IN (`-Dmtron.inst.args-in-tid=true`) is not the default case this test states
        Assumptions.assumeFalse(Boolean.getBoolean("mtron.inst.args-in-tid"),
                "the encoding was opted into for this build");
        assertFalse(MInst.ARGS_IN_TID, "the declaration in the tid must be opt-in");
        final Inst off = this.probe(lst(STR_TYPE));
        assertFalse(off.tid().hasArgs());
        assertFalse(off.tid().toString().contains("args"));
        assertEquals(this.probeTid(), off.tid());
        // the switch is the only difference: on encodes, and off again renders byte-identically to before
        final String before = off.tid().toString();
        MInst.ARGS_IN_TID = true;
        final String encoded = this.probe(lst(STR_TYPE)).tid().toString();
        MInst.ARGS_IN_TID = false;
        final String after = this.probe(lst(STR_TYPE)).tid().toString();
        assertEquals(before, after);
        assertNotEquals(before, encoded);
    }

    @Test
    void theDeclaredArgumentTypesAreWrittenIntoTheTid() {
        MInst.ARGS_IN_TID = true;
        final Inst inst = this.probe(lst(STR_TYPE));
        assertTrue(inst.tid().hasArgs());
        assertEquals(List.of(STR_TYPE.vid()), inst.tid().args());
        final String rendered = inst.tid().toString();
        assertTrue(rendered.contains("args=[" + STR_TYPE.vid() + "]"), rendered);
        // the canonical render round-trips: one fURI has exactly one spelling
        assertEquals(rendered, f(rendered).toString());
    }

    @Test
    void severalDeclaredArgumentTypesKeepTheirOrder() {
        MInst.ARGS_IN_TID = true;
        final Inst inst = this.probe(lst(STR_TYPE, URI_TYPE));
        assertEquals(List.of(STR_TYPE.vid(), URI_TYPE.vid()), inst.tid().args());
        final String rendered = inst.tid().toString();
        assertTrue(rendered.contains("args=[" + STR_TYPE.vid() + "," + URI_TYPE.vid() + "]"), rendered);
        assertEquals(rendered, f(rendered).toString());
    }

    @Test
    void aKnownZeroArgumentListDeclaresTheBareArgsKey() {
        MInst.ARGS_IN_TID = true;
        // ZERO IS A CLAIM: the bare `args` key says "takes no arguments", which is not the same as no claim at all
        final Inst inst = this.probe(lst());
        assertTrue(inst.tid().hasQ(MInst.ARGS_KEY), inst.tid().toString());
        assertTrue(inst.tid().hasArgs());
        assertTrue(inst.tid().args().isEmpty());
        final String rendered = inst.tid().toString();
        assertTrue(rendered.contains("args"), rendered);
        assertFalse(rendered.contains("args="), rendered);          // a BARE key carries no value
        assertEquals(rendered, f(rendered).toString());             // and the bare key round-trips
        // NO CLAIM is a tid minted with no argument Poly at all
        final Inst unknown = MInst.instC(this.probeTid(), null, (lhs, ignored) -> lhs);
        assertFalse(unknown.tid().hasQ(MInst.ARGS_KEY));
        assertEquals(this.probeTid(), unknown.tid());
    }

    @Test
    void aValueArgumentIsEncodedByItsSpecificTypeId() {
        MInst.ARGS_IN_TID = true;
        final Inst inst = this.probe(lst(jnt(5)));
        assertEquals(List.of(INT_TYPE.vid()), inst.tid().args());
        final String rendered = inst.tid().toString();
        assertTrue(rendered.contains("args=[/m/int]"), rendered);
    }

    @Test
    void aNamedArgumentListIsEncodedPositionallyInInsertionOrder() {
        MInst.ARGS_IN_TID = true;
        // NAMES ARE NOT REPRESENTABLE in an ordered component: the types are written in insertion order, and the
        // names stay on the argument Poly
        final Inst inst = MInst.instC(this.probeTid(),
                rec(uri("first"), T(STR_TID.maybe()), uri("age"), T(INT_TID)), (lhs, ignored) -> lhs);
        assertFalse(inst.args().isLst());
        assertTrue(inst.tid().hasQ(MInst.ARGS_KEY));
        assertEquals(List.of(T(STR_TID.maybe()).vid(), INT_TYPE.vid()), inst.tid().args());
        final String rendered = inst.tid().toString();
        assertTrue(rendered.contains("str{?}"), rendered);          // the coefficient survives the trip
        assertEquals(rendered, f(rendered).toString());
    }

    @Test
    void anElementThatItselfCarriesArgsIsNoClaim() {
        MInst.ARGS_IN_TID = true;
        // an argument type whose own address carries `args` cannot be written inside the outer list's bracket text
        final Inst nested = this.probe(lst(MInst.instC(f("/m/isa"), lst(INT_TYPE), (lhs, ignored) -> lhs)));
        assertFalse(nested.tid().hasQ(MInst.ARGS_KEY));
        assertEquals(this.probeTid(), nested.tid());
    }

    @Test
    void anIsaPredicateIsRecognizedWhenItsInstDeclaresArgs() {
        // the SQL schema generator reads a type's isa predicate object; a predicate inst whose tid declares its
        // argument types must still be recognized as an isa (the tid equality that answered "no predicate" was the NPE)
        MInst.ARGS_IN_TID = true;
        final Type type = Type.Builder.build()
                .tid(REC_TID)
                .vid(f("/m/type_test/an_isa_with_args"))
                .isaPredicate(rec(T(URI_TID.maybe()), ALL_TYPE))
                .create();
        assertTrue(type.isIsaPredicate(), type.toString());
        assertTrue(type.isPredicateObj().isRec(), "the predicate object is the rec the schema generator walks");
    }

    @Test
    void fullSignatureCompletesAnInstWithoutTheFlag() {
        // the explicit route works regardless of the mint-time switch — so state it: this scenario is the OFF one,
        // and a build that opted in (-Dmtron.inst.args-in-tid=true) must not change what it is testing
        MInst.ARGS_IN_TID = false;
        final Inst plain = this.probe(lst(STR_TYPE, URI_TYPE));
        assertFalse(plain.tid().hasQ(MInst.ARGS_KEY));
        final Inst completed = Inst.Helper.fullSignature(plain);
        assertEquals(List.of(STR_TYPE.vid(), URI_TYPE.vid()), completed.tid().args());
        assertEquals(this.probeTid().args(List.of(STR_TYPE.vid(), URI_TYPE.vid())), completed.tid());
        // idempotent: a tid that already carries its args is returned as it stands
        assertSame(completed, Inst.Helper.fullSignature(completed));
        // zero IS a claim: an empty argument list, and an inst minted with a null argument slot (which now reads an
        // empty lst), both complete to the BARE `args` key
        assertTrue(Inst.Helper.fullSignature(this.probe(lst())).tid().hasQ(MInst.ARGS_KEY));
        final Inst nullArgs = MInst.instC(this.probeTid(), null, (lhs, ignored) -> lhs);
        final Inst completedNullArgs = Inst.Helper.fullSignature(nullArgs);
        assertTrue(completedNullArgs.tid().hasQ(MInst.ARGS_KEY), "a null argument slot reads an empty lst, a zero claim");
        assertSame(completedNullArgs, Inst.Helper.fullSignature(completedNullArgs));
    }

    @Test
    void aDeclaredArgumentContractAdmitsOnlyItsShape() {
        MInst.ARGS_IN_TID = true;
        final Inst candidate = this.probe(lst(URI_TYPE));
        assertTrue(candidate.tid().hasArgs());
        // the right base form at the right arity
        assertTrue(Inst.Helper.admitsArgs(candidate, MInst.instB(CALL_TID, lst(uri("mtron.phaseshift.studio")))));
        // the wrong base form
        assertFalse(Inst.Helper.admitsArgs(candidate, MInst.instB(CALL_TID, lst(jnt(5)))));
        // the wrong arity
        assertFalse(Inst.Helper.admitsArgs(candidate, MInst.instB(CALL_TID,
                lst(uri("mtron.phaseshift.studio"), uri("metatron.phaseshift.studio")))));
        // a nested call's argument type cannot be read here, so it admits
        assertTrue(Inst.Helper.admitsArgs(candidate, MInst.instB(CALL_TID, lst(MInst.instB(f("/m/plus"), lst())))));
        // a candidate that declares ZERO admits a zero-argument call and refuses one that supplies an argument
        final Inst declaresZero = this.probe(lst());
        assertTrue(declaresZero.tid().hasQ(MInst.ARGS_KEY));
        assertTrue(Inst.Helper.admitsArgs(declaresZero, MInst.instB(CALL_TID, lst())));
        assertFalse(Inst.Helper.admitsArgs(declaresZero, MInst.instB(CALL_TID, lst(jnt(5)))));
        // a candidate that declares NOTHING (no argument Poly at all) claims nothing and admits anything
        final Inst undeclared = MInst.instC(this.probeTid(), null, (lhs, ignored) -> lhs);
        assertFalse(undeclared.tid().hasQ(MInst.ARGS_KEY));
        assertTrue(Inst.Helper.admitsArgs(undeclared, MInst.instB(CALL_TID, lst(jnt(5)))));
        assertTrue(Inst.Helper.admitsArgs(undeclared, MInst.instB(CALL_TID, lst(
                uri("mtron.phaseshift.studio"), uri("metatron.phaseshift.studio")))));
    }

    @Test
    void theLawMatcherReadsTheDeclaredArgumentContract() {
        MInst.ARGS_IN_TID = true;
        // magmadic is the binary carrier law: inst?dom=A&rng=A&args=[A]
        final Inst intArgument = this.probe(lst(INT_TYPE));
        assertTrue(intArgument.tid().hasArgs());
        assertTrue(mInstSet.AlgebraTool.matches(Law.magmadic, intArgument));
        // the same dom/rng, a declared argument of another base form: the contract does not have the law's shape
        final Inst uriArgument = this.probe(lst(URI_TYPE));
        assertTrue(uriArgument.tid().hasArgs());
        assertFalse(mInstSet.AlgebraTool.matches(Law.magmadic, uriArgument));
        // with no declaration the argument VALUE is the only contract, and an int value still matches
        MInst.ARGS_IN_TID = false;
        final Inst valueArgument = this.probe(lst(jnt(5)));
        assertFalse(valueArgument.tid().hasArgs());
        assertTrue(mInstSet.AlgebraTool.matches(Law.magmadic, valueArgument));
    }

    @Test
    void theDeclaredContractWinsOverTheArgumentValues() {
        // the tid is written by hand here, so the argument VALUES cannot re-key it: what the law reads is the claim
        MInst.ARGS_IN_TID = false;
        final Inst inst = MInst.instC(this.probeTid().args(INT_TYPE.vid()), lst(URI_TYPE, URI_TYPE), (lhs, ignored) -> lhs);
        assertTrue(inst.tid().hasArgs());
        assertEquals(List.of(INT_TYPE.vid()), inst.tid().args());
        assertEquals(2, inst.args().count());
        // one declared int argument is the contract, so the binary carrier law matches despite two uri values
        assertTrue(mInstSet.AlgebraTool.matches(Law.magmadic, inst));
    }

    @Test
    void aDeclaredContractLeavesAMintedInstItsArgumentValues() {
        MInst.ARGS_IN_TID = true;
        final Inst inst = this.probe(lst(STR_TYPE));
        // the declaration is an ADDITION to the tid: the inst still carries the argument Poly it was minted with
        assertEquals(1, inst.args().count());
        assertEquals(STR_TYPE, inst.args().lstValue().getFirst());
    }
}
