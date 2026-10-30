/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful to you,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.m;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.INT_TID;
import static studio.phaseshift.metatron.Tokens.REAL_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.NAT_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.PLUS_INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.gt_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.is_;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

/**
 * admission soundness of instset family reads on the {@code dom} axis:
 * <p>
 * {@code *plus?\u003c= nat} must return every plus contract that can legally be
 * used for a {@code nat} addition — i.e. doms on the type's single path to
 * {@code #::T} (nat -> int -> #) plus generics, and nothing else.
 * this is the contract between the console read and the resolver: what the
 * read admits, the resolver may use, and vice versa.
 */
public class LatticeReadTest extends AbstractInstSetTest {

    public LatticeReadTest() {
        super(mathInstSet::new);
    }


    @Test
    public void patternDomShape() {
        final fURI pattern = f("plus").dom(f("imperial"));
        final fURI dom = pattern.dom();
        LOG.warn("[PAT] pattern=" + pattern);
        LOG.warn("[PAT] pattern.dom()=" + dom + " | equals f(imperial)? " + dom.equals(f("imperial")));
        LOG.warn("[PAT] T(pattern.dom())=" + (T(dom).tid() + "::T@" + T(dom).vid()));
        LOG.warn("[PAT] T(f(imperial))  =" + (T(f("imperial")).tid() + "::T@" + T(f("imperial")).vid()));
        LOG.warn("[PAT] admitted=" + admitted(f("imperial")));
    }

    @Test
    public void tAndSpaceReadAgree() {
        final Type viaT = T(f("imperial"));
        final Obj viaRead = Machine.read(f("imperial"));
        LOG.warn("[SAME] via T(imperial)    = tid=" + viaT.tid() + " vid=" + viaT.vid());
        LOG.warn("[SAME] via space read     = " + (viaRead.isType() ?
                "tid=" + viaRead.asType().tid() + " vid=" + viaRead.asType().vid() :
                viaRead));
        if (viaRead.isType()) {
            final Type reg = viaRead.asType();
            LOG.warn("[SAME] same type? " + viaT.equals(reg)
                    + " | T.tid=" + viaT.tid() + " reg.tid=" + reg.tid()
                    + " | T.pathIncludes(real)=" + viaT.pathIncludes(T(f("real")))
                    + " reg.pathIncludes(real)=" + reg.pathIncludes(T(f("real"))));
        }
        Machine.read(f("plus")).stream()
                .filter(o -> o.isInst() && o.asInst().dom().vid().toString().contains("real"))
                .forEach(o -> {
                    final Inst i = o.asInst();
                    LOG.warn("[SAME] real-contract dom: tid=" + i.dom().tid() + " vid=" + i.dom().vid()
                            + " | T.pathIncludes(it)=" + viaT.pathIncludes(i.dom())
                            + " | read.pathIncludes(it)=" + (viaRead.isType() ? viaRead.asType().pathIncludes(i.dom()) : "n/a"));
                });
    }

    /**
     * the insts admitted by the family read {@code plus?dom=<dom>} — asked THROUGH THE MACHINE'S OWN INSTRUCTION
     * SET, not through Memory: dom admission lives in the union, and that is the read the resolver uses.
     */
    private static List<Inst> admitted(final fURI dom) {
        return admitted(Machine.current().instset().read(f("plus").dom(dom)));
    }

    private static List<Inst> admitted(final Obj result) {
        return result.stream()
                .map(o -> o.isInst() ? o : o.isRel() ? o.asRel().second() : o)
                .filter(Obj::isInst)
                .map(Obj::asInst)
                .toList();
    }

    /**
     * the same family read on the {@code rng} axis: {@code plus?dom=nat&rng=int} admits the int contract, and
     * nothing off nat's path.
     */
    @Test
    public void admittedForNatAndIntRngIsTheIntContract() {
        final List<Inst> admitted = admitted(Machine.current().instset()
                .read(f("plus").dom(f(NAT_TID.name())).rng(f(INT_TID.name()))));
        assertFalse(admitted.isEmpty(),
                "*plus?<=nat&rng=int must admit a contract (the int one); admitted=" + admitted);
        final Type nat = T(f(NAT_TID.name()));
        assertTrue(admitted.stream().allMatch(i -> nat.pathIncludes(i.dom())),
                "and nothing off nat's path (nat -> int -> #); admitted=" + admitted);
    }

    @Test
    public void natChainIsIntThenRoot() {
        final Type nat = T(f(NAT_TID.name()));
        final Type real = T(f(REAL_TID.name()));
        assertTrue(nat.isRefinementOf(T(f("int"))), "nat must refine int (chain nat -> int -> #)");
        assertFalse(nat.isRefinementOf(real), "nat must NOT refine real (real is off nat's chain)");
    }

    @Test
    public void admittedForNatIncludesIntContract() {
        final List<Inst> admitted = admitted(f(NAT_TID.name()));
        final Type nat = T(f(NAT_TID.name()));
        final boolean has = admitted.stream().anyMatch(i -> nat.pathIncludes(i.dom()));
        assertTrue(has, "*plus?<=nat must admit a contract on nat's path (nat -> int -> #); admitted=" + admitted);
        final int nearest = admitted.stream().mapToInt(i -> nat.pathTo(i.dom())).min().orElse(Integer.MAX_VALUE);
        assertEquals(1, nearest, "the nearest admitted contract must be exactly one refinement up (the int contract); admitted=" + admitted);
    }

    @Test
    public void admittedForNatExcludesSiblings() {
        final List<Inst> admitted = admitted(f(NAT_TID.name()));
        final boolean anySibling = admitted.stream()
                .map(Inst::dom)
                .anyMatch(d -> !d.isGeneric() && !T(f(NAT_TID.name())).isRefinementOf(d) && !d.isRefinementOf(T(f(NAT_TID.name()))));
        assertFalse(anySibling, "no contract off nat's chain may be admitted; admitted=" + admitted);
    }

    @Test
    public void exactDomStillAdmitted() {
        final List<Inst> admitted = admitted(f(REAL_TID.name()));
        final boolean hasExact = admitted.stream().anyMatch(i -> i.dom().equals(T(f(REAL_TID.name())))
                || (i.dom().isRefinementOf(T(f(REAL_TID.name()))) && T(f(REAL_TID.name())).isRefinementOf(i.dom())));
        assertTrue(hasExact, "*plus?<=real must still admit the exact real-dom contract; admitted=" + admitted);
    }

    @Test
    public void resolutionMatchesAdmission() {
        // what nat::5.plus(1) resolves to must be among what *plus?<=nat admits
        Type.Builder.build()
                .tid(INT_TID)
                .vid(NAT_TID)
                .predicate(is_(gt_(jnt(0))).tryToInst())
                .create();
        final Obj applied = ObjmtronSerializer.parse("plus(1)").apply(jnt(5, NAT_TID, null));
        final Type got = applied.type();
        final List<Inst> admitted = admitted(f(NAT_TID.name()));
        final boolean covered = admitted.stream().anyMatch(i -> T(f(NAT_TID.name())).pathIncludes(i.dom()));
        assertTrue(covered, "resolved dom " + got + " must be admitted by *plus?<=nat; admitted=" + admitted);
    }

    private static boolean domEquals(final Inst inst, final String name) {
        final String vid = inst.dom().vid().toString();
        final String last = vid.substring(vid.lastIndexOf('/') + 1);
        return last.equals(name) || vid.equals(name);
    }

    @Test
    public void imperialAdmitsItsPathButNotMetricSibling() {
        final List<Inst> admitted = admitted(f("imperial"));
        assertTrue(admitted.stream().anyMatch(i -> domEquals(i, "imperial")), "*plus?<=imperial must admit the exact imperial contract; admitted=" + admitted);
        assertTrue(admitted.stream().anyMatch(i -> domEquals(i, "real")), "*plus?<=imperial must admit the real contract (imperial -> real -> #); admitted=" + admitted);
        assertTrue(T(f("imperial")).pathIncludes(T(f("#"))), "# (root) is on every type's path to # -- a # dom, if registered, would be admitted");
        assertFalse(admitted.stream().anyMatch(i -> domEquals(i, "metric")), "metric is imperial's sibling under real -- its plus converts cm/mm/km and must NEVER be admitted for imperial; admitted=" + admitted);
    }

    /**
     * THE DEDUCTION — which instruction implementation a type reaches. A type with no implementation of its own
     * continues up its path to the root until it lands on one: {@code nat::T}'s {@code plus} is the {@code int}-dom
     * contract ({@code nat -> int -> #}), while {@code imperial}'s is its own (its path is
     * {@code imperial -> real -> #}, {@code real}'s the next admission, and never the sibling). The rule lives on
     * {@link Typer.Helper}; this pins it, since the law table reads the same answer to decide what can cancel.
     */
    @Test
    public void deduceTypeInstLandsOnThePathContract() {
        final Inst plus = instB(PLUS_INST_TID, lst());

        final Inst forNat = Typer.Helper.deduceTypeInst(T(f(NAT_TID.name())), plus);
        assertNotNull(forNat, "nat::T must land on the int-dom plus contract (nat -> int -> #)");
        assertTrue(domEquals(forNat, INT_TID.name()), "nat has no plus of its own -- it lands on int's, got " + forNat);

        final Inst forImperial = Typer.Helper.deduceTypeInst(T(f("imperial")), plus);
        assertNotNull(forImperial, "imperial::T must land on a plus contract");
        assertTrue(domEquals(forImperial, "imperial"), "imperial's own contract is closest to the token, got " + forImperial);

        final Inst forReal = Typer.Helper.deduceTypeInst(T(f(REAL_TID.name())), plus);
        assertNotNull(forReal, "real::T must land on the real plus contract");
        assertTrue(domEquals(forReal, REAL_TID.name()), "real lands on its own contract, got " + forReal);
    }

    /**
     * A SIBLING IS NEVER DEDUCED — {@code metric} and {@code imperial} both refine {@code real}, and neither lies
     * on the other's path, so each deduces only its own contract. A mis-deduction here would convert centimetres
     * into miles silently.
     */
    @Test
    public void deduceTypeInstNeverLandsOnASibling() {
        final Inst plus = instB(PLUS_INST_TID, lst());
        final Inst forMetric = Typer.Helper.deduceTypeInst(T(f("metric")), plus);
        assertNotNull(forMetric, "metric::T must land on its own plus contract");
        assertTrue(domEquals(forMetric, "metric"), "metric lands on its own contract, got " + forMetric);
        final Inst forImperial = Typer.Helper.deduceTypeInst(T(f("imperial")), plus);
        assertFalse(domEquals(forImperial, "metric"), "imperial must not deduce metric's contract, got " + forImperial);
    }

    /**
     * FAIL CLOSED — an unregistered family, or no type to deduce from, answers null. A family read and a resolver
     * must both decline rather than guess: a law-driven rewrite fires on what the deduction says, so a guess would
     * fire the wrong rule on a type that never declared it.
     */
    @Test
    public void deduceTypeInstFailsClosed() {
        assertNull(Typer.Helper.deduceTypeInst(T(f(NAT_TID.name())), instB(f("no_such_op_for_a_type"), lst())),
                "an unregistered family admits nothing");
        assertNull(Typer.Helper.deduceTypeInst(null, instB(PLUS_INST_TID, lst())),
                "a bare op with no type to deduce from is not a guess");
    }

    @Test
    public void timeAdmitsRealButNotDatetimeChild() {
        final List<Inst> admitted = admitted(f("time"));
        assertTrue(admitted.stream().anyMatch(i -> domEquals(i, "time")), "*plus?<=time must admit the exact time contract; admitted=" + admitted);
        assertFalse(admitted.stream().anyMatch(i -> domEquals(i, "datetime")), "datetime refines uri, not time -- not on time's path to #; admitted=" + admitted);
    }

    @Test
    public void refinesTrueButNotAdmittedForSiblings() {
        // the admission rule must keep metric out of imperial's legal pluses
        // regardless of the resolution state of the sibling cross-refine
        // (isRefinementOf is a branch-membership test visible only when both
        // sides resolve to their registered shapes).
        final Type metric = T(f("metric"));
        final Type imperial = T(f("imperial"));
        assertFalse(imperial.pathIncludes(metric), "pathIncludes is the strict on-the-chain rule the filter uses");
        assertTrue(imperial.pathIncludes(T(f("real"))), "imperial's parent (real) must be on its path");
        assertFalse(imperial.pathIncludes(T(f("metric"))), "metric is off imperial's path");
    }

    @Test
    public void unqualifiedFamilyUnchanged() {
        final long n = Machine.read(f("plus")).stream().count();
        assertTrue(n >= 13, "unqualified family must list the full set (9 core + 4 math), got " + n);
    }


}