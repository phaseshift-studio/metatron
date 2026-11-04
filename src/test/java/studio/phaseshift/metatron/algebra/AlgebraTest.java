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

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE LAW BLOCK AS A VALUE — what an {@link Algebra} says about the theories a type models, and the two properties
 * that make it safe to hand around: it is the rec it views (so nothing about introspection, serialization or selection
 * changes), and every change is a copy (so a view can never write back into the block a law table answered).
 *
 * <p>The fixture is the shape {@code LawTable.typeLaws(Type)} answers — theory-instance name to declaration rec, in
 * declaration order — modeled on int's declared ring: {@code ring => [zero => 0, one => 1]} and
 * {@code group => [op => plus, id => 0]}. A real block is read in the sibling {@code AbstractAlgebraTest} hierarchy;
 * here the block is written by hand so the order and the values under test are unambiguous.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class AlgebraTest extends AbstractMetatronTest {

    /**
     * A type's law block: two theory instances, ring before group — the order the declaration was written in.
     */
    private static Rec laws() {
        // A THEORY INSTANCE IS A WITNESS, NOT A VIEW: a ring states its operations (add/mul are inst::T) and a
        // group states its inverse -- a rec that omits them is a fragment, and the theory type is right to refuse it.
        final Map<Obj, Obj> ring = new LinkedHashMap<>();
        ring.put(uri(ADD), plus_(INT_TYPE).tryToInst());
        ring.put(uri(MUL), mult_(INT_TYPE).tryToInst());
        ring.put(uri(ZERO), jnt(0));
        ring.put(uri(ONE), jnt(1));
        final Map<Obj, Obj> group = new LinkedHashMap<>();
        group.put(uri(OP), plus_(INT_TYPE).tryToInst());
        group.put(uri(ID), jnt(0));
        group.put(uri(INV), neg_().tryToInst());
        final Map<Obj, Obj> block = new LinkedHashMap<>();
        block.put(uri(RING), rec(ring, RING_THEORY_TID, null));
        block.put(uri("group"), rec(group, GROUP_THEORY_TID, null));
        return rec(block);
    }

    /**
     * A ring with one more role — the declaration a copy-on-write extension writes in place of the original.
     */
    private static Rec richerRing() {
        final Map<Obj, Obj> ring = new LinkedHashMap<>();
        ring.put(uri(ADD), plus_(INT_TYPE).tryToInst());
        ring.put(uri(MUL), mult_(INT_TYPE).tryToInst());
        ring.put(uri(ZERO), jnt(0));
        ring.put(uri(ONE), jnt(1));
        ring.put(uri(INV), neg_().tryToInst());   // the extra role this extension adds
        return rec(ring, RING_THEORY_TID, null);
    }

    /**
     * A block declares its theories in its own spell order, and each name answers through both spellings — the address
     * the block is keyed by and the language text a reader has in hand.
     */
    @Test
    void theoriesAreListedInDeclarationOrder() {
        final Algebra algebra = Algebra.of(laws());

        assertEquals(List.of(f(RING), f("group")), algebra.theories());
        assertTrue(algebra.models(f(RING)));
        assertTrue(algebra.models(f("group")));
        assertFalse(algebra.models(f("field")));

        final Obj zero = algebra.theory(RING).at(uri(ZERO));
        final Obj one = algebra.theory(f(RING)).at(uri(ONE));
        final Obj op = algebra.theory("group").at(uri(OP));
        assertEquals(jnt(0), zero);
        assertEquals(jnt(1), one);
        assertEquals(plus_(INT_TYPE).tryToInst(), op);
    }

    /**
     * A value that declares no theory answers an empty algebra rather than a null, so a caller never has to test the
     * view before asking it a question — the same answer for a noobj, for an empty rec, and for no rec at all.
     */
    @Test
    void aValueThatDeclaresNoTheoriesAnswersEmpty() {
        final Algebra fromNoObj = Algebra.of(noobj().asRec());
        final Algebra fromEmptyRec = Algebra.of(rec());
        final Algebra fromNothing = Algebra.of(null);

        assertTrue(fromNoObj.theories().isEmpty());
        assertEquals(rec0(), fromNoObj.theory(RING));
        assertFalse(fromNoObj.models(f(RING)));
        assertTrue(fromEmptyRec.theories().isEmpty());
        assertTrue(fromNothing.theories().isEmpty());
        assertEquals(rec0(), fromNothing.theory(f(RING)));
    }

    /**
     * A change is a copy: the receiver keeps the declaration it had, the child answers the one written over it, and
     * neither writes into the block the view was built from.
     */
    @Test
    void withIsCopyOnWrite() {
        final Rec laws = laws();
        final Algebra original = Algebra.of(laws);
        final Rec declaredRing = original.theory(RING);

        final Rec richerRing = richerRing();
        final Algebra amended = original.with(f(RING), richerRing);
        assertEquals(richerRing, amended.theory(RING));
        // the extension adds a FIFTH role (neg); mul is part of every legitimate ring, so it cannot be the added one
        assertEquals(neg_().tryToInst(), amended.theory(RING).at(uri(INV)));
        assertEquals(declaredRing, original.theory(RING));
        assertTrue(original.theory(RING).at(uri(INV)).isNoObj());

        final Algebra extended = original.with(f("field"), rec(new LinkedHashMap<>()));
        assertTrue(extended.models(f("field")));
        assertFalse(original.models(f("field")));
        assertEquals(2, original.theories().size());
        assertEquals(3, extended.theories().size());
        // the source block is the table's: the copy-on-write never reaches it
        assertEquals(2, laws.jvm().size());
        assertEquals(declaredRing, Algebra.of(laws).theory(RING));
    }

    /**
     * The view IS the rec — an Algebra answers itself as the rec it already is, and is handed back unchanged when it
     * comes in through the factory again, so no boundary in the JVM can tell the two apart.
     */
    @Test
    void theViewIsTheRecItViews() {
        final Algebra algebra = Algebra.of(laws());

        assertSame(algebra, algebra.toRec());
        assertSame(algebra, Algebra.of(algebra));
    }

    /**
     * The view still inspects as the rec it is: the block's entries read back at their addresses exactly as the Java
     * accessors answer them, and the view owns its map — a write through it never reaches the block it came from.
     */
    @Test
    void theViewInspectsAsTheRecItIs() {
        final Rec laws = laws();
        final Algebra algebra = Algebra.of(laws);

        assertTrue(algebra.isRec());
        assertEquals(REC_TID, algebra.tid());
        final Obj ring = algebra.at(uri(RING));
        assertTrue(ring.isRec());
        assertEquals(algebra.theory(RING), ring.asRec());
        assertEquals(jnt(1), ring.asRec().at(uri(ONE)));

        algebra.jvm().put(uri("semilattice"), rec(new LinkedHashMap<>()));
        assertEquals(3, algebra.theories().size());
        assertEquals(2, laws.jvm().size());
    }
}
