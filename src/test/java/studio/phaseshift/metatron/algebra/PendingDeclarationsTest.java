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

package studio.phaseshift.metatron.algebra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.algebra.rewrite.Derivation;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static studio.phaseshift.metatron.Tokens.ONE;
import static studio.phaseshift.metatron.Tokens.ZERO;
import static studio.phaseshift.metatron.algebra.Law.commutative;
import static studio.phaseshift.metatron.algebra.Law.monoidic;
import static studio.phaseshift.metatron.algebra.Law.pure;
import static studio.phaseshift.metatron.algebra.Law.right_distributive;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.RING_THEORY_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE STAGING AREA'S CONTRACT — what an instruction set may declare before its space exists, and what a reader is
 * promised while those declarations wait for the drain that publishes them.
 *
 * <p>The four properties under test are the ones a rewrite pass and a drain depend on: a row round-trips, its identity
 * is the OPERATION rather than the argument types it was minted with, the replay order is the declaration order
 * (never a hash map's), and the type algebra is keyed by its vid's base path. The ledger counts both facets and a null
 * address stages nothing, because this is the write path a static initializer runs through.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PendingDeclarationsTest extends AbstractMetatronTest {

    /**
     * The staging area is process-wide, so each test starts by emptying the ledger — the same one-entry-at-a-time
     * discipline the class documents, not a reset of anything the store holds.
     */
    @BeforeEach
    void clearStagedDeclarations() {
        PendingDeclarations.clear();
    }

    /**
     * A defining equation of the shape the instsets declare: {@code minus ≡ plus ∘ neg}, parsed into the two code
     * values a {@link Derivation} carries.
     */
    private static Derivation minusDerivation() {
        final Code lhs = ObjmtronSerializer.parse("start(A).minus(B)");
        final Code rhs = ObjmtronSerializer.parse("start(A).plus(map(B).neg())");
        return new Derivation(lhs, rhs);
    }

    /**
     * A type's algebra in the shape a law block has it: a ring instance binding its two roles. Only the identity of
     * the value matters here — the staging stores the reference, it never rewrites it.
     */
    private static Algebra ringAlgebra() {
        final Map<Obj, Obj> ring = new LinkedHashMap<>();
        ring.put(uri(ZERO), jnt(0));
        ring.put(uri(ONE), jnt(1));
        final Map<Obj, Obj> block = new LinkedHashMap<>();
        block.put(uri("ring"), rec(ring, RING_THEORY_TID, null));
        return Algebra.of(rec(block));
    }

    /**
     * A declaration round-trips: the row the staging answers is the row that was staged, and each facet it carries —
     * the process laws in order, the inverse, the defining equation — comes back equal.
     */
    @Test
    void anInstRowRoundTrips() {
        final fURI probe = f("/m/inst/probe?rng=/m/int&dom=/m/int&args=[/m/int]");
        final Derivation derivation = minusDerivation();
        final PendingDeclarations.InstRow staged = PendingDeclarations.declareInst(probe,
                List.of(commutative, monoidic), f("/m/inst/neg"), derivation);

        assertNotNull(staged, "a declaration is staged the moment it is made");
        final PendingDeclarations.InstRow found = PendingDeclarations.findInst(probe);
        assertEquals(staged, found, "the staged row answers its own lookup");
        assertEquals(List.of(commutative, monoidic), found.laws(), "the process laws round-trip in order");
        assertEquals(f("/m/inst/neg"), found.inverse(), "the declared inverse round-trips");
        assertEquals(derivation, found.derivation(), "the defining equation round-trips");
    }

    /**
     * A row's identity is the OPERATION's: a declaration made at the fully minted address answers a lookup at the
     * address with the {@code args} component erased, and a lookup at a different dom/rng is a different operation and
     * answers nothing.
     */
    @Test
    void aRowIsKeyedByTheOperationNotItsArguments() {
        final PendingDeclarations.InstRow staged = PendingDeclarations.declareInst(
                f("/m/inst/probe?rng=/m/int&dom=/m/int&args=[/m/int]"), List.of(pure), null, null);

        assertSame(staged, PendingDeclarations.findInst(f("/m/inst/probe?rng=/m/int&dom=/m/int")),
                "the args component is how the instruction was minted, not what it declares");
        assertNull(PendingDeclarations.findInst(f("/m/inst/probe?rng=/m/real&dom=/m/real")),
                "a different dom/rng is a different operation");
    }

    /**
     * Three declarations replay in the order they were made — both the address list and the view's own key sequence —
     * and the view is a read-only snapshot rather than a map a caller could write through.
     */
    @Test
    void rowsReplayInDeclarationOrder() {
        PendingDeclarations.declareInst(f("/m/inst/probe?rng=/m/int&dom=/m/int"), List.of(commutative), null, null);
        PendingDeclarations.declareInst(f("/m/inst/probe?rng=/m/real&dom=/m/real"), List.of(right_distributive), null, null);
        PendingDeclarations.declareInst(f("/m/inst/probe?rng=/m/str&dom=/m/str"), List.of(monoidic), null, null);

        final List<fURI> declared = List.of(
                f("/m/inst/probe?rng=/m/int&dom=/m/int"),
                f("/m/inst/probe?rng=/m/real&dom=/m/real"),
                f("/m/inst/probe?rng=/m/str&dom=/m/str"));
        assertEquals(declared, PendingDeclarations.instOrder(), "the address list is the declaration order");
        assertEquals(declared, List.copyOf(PendingDeclarations.instRows().keySet()),
                "the ordered view replays the same order");
        assertThrows(UnsupportedOperationException.class,
                () -> PendingDeclarations.instRows().put(f("/m/inst/probe?rng=/m/bool&dom=/m/bool"),
                        new PendingDeclarations.InstRow(List.of(), null, null)),
                "the view is a read-only snapshot");
    }

    /**
     * A type's algebra is staged, found and listed by the vid's BASE PATH: the query a spelling carries is a lesser of
     * the address, so a lookup at a queried spelling answers the declaration made at the type's own address. A type
     * that staged none answers null, which is the honest answer rather than an empty algebra nobody declared.
     */
    @Test
    void anAlgebraIsKeyedByItsTypesBasePath() {
        final Algebra ring = ringAlgebra();
        final Algebra staged = PendingDeclarations.declareAlgebra(f("/m/int"), ring);

        assertSame(ring, staged, "the staged algebra is the one handed in, not a copy");
        assertSame(ring, PendingDeclarations.findAlgebra(f("/m/int")));
        assertEquals(Map.of(f("/m/int"), ring), PendingDeclarations.algebras());
        assertSame(ring, PendingDeclarations.findAlgebra(f("/m/int?dom=/m/int")),
                "the query is erased: the key is the type's base path");
        assertNull(PendingDeclarations.findAlgebra(f("/m/real")), "a type that staged none answers null");
        assertThrows(UnsupportedOperationException.class,
                () -> PendingDeclarations.algebras().put(f("/m/real"), ring),
                "the view is a read-only snapshot");
    }

    /**
     * The ledger counts both facets and counts them by ADDRESS, so a re-declaration does not inflate it. A null
     * address or a null algebra stages nothing and answers null instead of throwing — this write path runs inside
     * static initialization, where a missing address is not worth failing a boot over.
     */
    @Test
    void sizeCountsBothFacetsAndNullsStageNothing() {
        assertEquals(0, PendingDeclarations.size(), "an empty ledger");
        assertNull(PendingDeclarations.declareInst(null, List.of(pure), null, null), "a null address stages no row");
        assertNull(PendingDeclarations.findInst(null), "a null address answers no row");
        assertNull(PendingDeclarations.declareAlgebra(null, ringAlgebra()), "a null vid stages no algebra");
        assertNull(PendingDeclarations.declareAlgebra(f("/m/int"), null), "a null algebra stages nothing");
        assertNull(PendingDeclarations.findAlgebra(null), "a null vid answers no algebra");
        assertEquals(0, PendingDeclarations.size(), "nulls leave the ledger untouched");

        PendingDeclarations.declareInst(f("/m/inst/probe?rng=/m/int&dom=/m/int"), List.of(pure), null, null);
        assertEquals(1, PendingDeclarations.size(), "one instruction row");
        PendingDeclarations.declareAlgebra(f("/m/int"), ringAlgebra());
        assertEquals(2, PendingDeclarations.size(), "the ledger counts both facets");
        PendingDeclarations.declareInst(f("/m/inst/probe?rng=/m/int&dom=/m/int"), List.of(pure), null, null);
        PendingDeclarations.declareAlgebra(f("/m/int"), ringAlgebra());
        assertEquals(2, PendingDeclarations.size(), "re-declaring an address does not inflate the ledger");
    }
}
