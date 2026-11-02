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

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.algebra.Algebra;
import studio.phaseshift.metatron.algebra.PendingWrites;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE TYPE BUILDER — one chain declares a type, and {@code create()} hands the whole declaration to the machine as
 * WRITES: the type at its {@code vid}, its documentation at {@code ?docq} and its algebra at {@code ?decq}. A builder
 * runs during its instruction set's static initialization, so those writes are QUEUED
 * ({@link PendingWrites}) and replayed when the owning set mounts — which is the half these tests read, since a test
 * VM is already up and its mount already happened.
 *
 * <p>Every test declares into the {@code /m/typebuilder/} namespace and drains under THAT prefix rather than under
 * {@code /m}: the queue is process-wide and surefire runs test classes concurrently, so a drain must publish the
 * writes in its own jurisdiction and leave every other class's alone.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TypeBuilderTest extends AbstractMetatronTest {

    /**
     * THE NAMESPACE every declaration in this class owns — the jurisdiction each drain below names.
     */
    private static final fURI TEST_NAMESPACE = f("/m/typebuilder");

    /**
     * (a) THE TYPE THE CHAIN STATED COMES BACK: its two addresses, its refinement (a structural {@code isa} claim)
     * and, through the same value, the predicate the refinement composed.
     */
    @Test
    void testTheBuilderCreatesTheTypeItStated() {
        final Rec shape = rec(uri("radius"), INT_TYPE, uri("label"), STR_TYPE);
        final Type disc = TypeBuilder.build()
                .tid(REC_TID)
                .vid(TEST_NAMESPACE.extend("disc"), "a circle with a radius and a label")
                .isaPredicate(shape)
                .create();
        assertEquals(TEST_NAMESPACE.extend("disc"), disc.vid(), "the type answers to the vid the builder stated");
        assertEquals(REC_TID, disc.tid(), "the type refines the tid the builder stated");
        assertTrue(disc.isIsaPredicate(), "a structural refinement is an isa predicate, got " + disc.predicate());
        assertEquals(shape, disc.isPredicateObj(), "the isa predicate's argument is the rec the chain stated");
    }

    /**
     * (b) NOTHING IS WRITTEN — the type and its documentation WAIT in the queue, at exactly the addresses the owning
     * instruction set will replay them to.
     */
    @Test
    void testCreateQueuesTheTypeAndItsDocumentation() {
        final fURI vid = TEST_NAMESPACE.extend("queued");
        final Type type = TypeBuilder.build()
                .tid(INT_TID)
                .vid(vid, "an int that is only ever queued")
                .example("4.plus(5) [-- 9 --]")
                .create();
        assertTrue(PendingWrites.has(vid), "the type itself is queued at its vid");
        assertTrue(PendingWrites.has(PendingWrites.docq(vid)), "the documentation is queued at ?docq");
        final PendingWrites.Write write = PendingWrites.writes().stream()
                .filter(candidate -> candidate.address().equals(vid)).findFirst().orElse(null);
        assertNotNull(write, "a write is pending at " + vid);
        assertSame(type, write.value(), "the queued value IS the type create() answered");
    }

    /**
     * (c) ROLE SUGAR — the algebra vocabulary composes the conventional theory instance the roles describe, and each
     * operation role is a {@code !*} pointer to the operation rather than a copy of it.
     */
    @Test
    void testRoleSugarComposesTheConventionalTheory() {
        final Inst plus = instB(PLUS_INST_TID.dom(INT_TID).rng(INT_TID), lst(INT_TYPE));
        final Inst mult = instB(MULT_INST_TID.dom(INT_TID).rng(INT_TID), lst(INT_TYPE));
        final Inst neg = instB(NEG_INST_TID.dom(INT_TID).rng(INT_TID), lst(INT_TYPE));
        // ring: both operations, both identities, and the additive inverse
        final Type ring = TypeBuilder.build()
                .tid(INT_TID)
                .vid(TEST_NAMESPACE.extend("ring"))
                .add(plus).mul(mult).zero(jnt(0)).one(jnt(1)).inv(neg)
                .create();
        final Algebra ringAlgebra = queuedAlgebra(ring.vid());
        assertTrue(ringAlgebra.models(f("ring")), "all five roles compose a ring, got " + ringAlgebra);
        assertFalse(ringAlgebra.models(f("add_group")), "a ring is not ALSO declared as its additive group alone");
        final Rec ringBlock = ringAlgebra.theory(f("ring"));
        assertEquals(plus.tid().basePath(), roleAddress(ringBlock, ADD).basePath(), "the add role binds plus");
        assertEquals(mult.tid().basePath(), roleAddress(ringBlock, MUL).basePath(), "the mul role binds mult");
        assertEquals(neg.tid().basePath(), roleAddress(ringBlock, INV).basePath(), "the inv role binds neg");
        assertEquals(jnt(0), ringBlock.at(uri(ZERO)), "zero holds the literal the chain stated");
        assertEquals(jnt(1), ringBlock.at(uri(ONE)), "one holds the literal the chain stated");
        // add_group: the additive side alone (no multiplicative side), spelled op/id/inv as the algebras spell it
        final Type group = TypeBuilder.build()
                .tid(INT_TID)
                .vid(TEST_NAMESPACE.extend("group"))
                .add(plus).zero(jnt(0)).inv(neg)
                .create();
        final Algebra groupAlgebra = queuedAlgebra(group.vid());
        assertTrue(groupAlgebra.models(f("add_group")), "add+zero+inv compose an add_group, got " + groupAlgebra);
        assertFalse(groupAlgebra.models(f("ring")), "an additive group is not a ring");
        final Rec groupBlock = groupAlgebra.theory(f("add_group"));
        assertEquals(plus.tid().basePath(), roleAddress(groupBlock, OP).basePath(), "the group's op role binds plus");
        assertEquals(neg.tid().basePath(), roleAddress(groupBlock, INV).basePath(), "the group's inv role binds neg");
        assertEquals(jnt(0), groupBlock.at(uri(ID)), "the group's id holds the additive identity");
    }

    /**
     * (c, continued) AN EXPLICIT ALGEBRA OVERRIDES THE ROLES — a caller who writes the algebra means exactly that
     * block, so the roles present are not composed on top of it.
     */
    @Test
    void testAnExplicitAlgebraOverridesTheRoles() {
        final Inst plus = instB(PLUS_INST_TID.dom(INT_TID).rng(INT_TID), lst(INT_TYPE));
        final Type field = TypeBuilder.build()
                .tid(REAL_TID)
                .vid(TEST_NAMESPACE.extend("explicit"))
                .add(plus).zero(jnt(0))
                .algebra(Algebra.of(rec(uri("field"), rec(uri(ADD), jnt(0)))))
                .create();
        final Algebra algebra = queuedAlgebra(field.vid());
        assertTrue(algebra.models(f("field")), "the declared block is what is staged, got " + algebra);
        assertFalse(algebra.models(f("add_monoid")), "the roles present do not compose a second instance");
    }

    /**
     * (d) THE DRAIN PUBLISHES THE DECLARATION — after the owning set replays the log, the type is readable at its
     * address, its algebra is readable beside it through {@code ?decq}, and its documentation through {@code ?docq}.
     * The staged writes STAY staged: a declaration is not a one-shot, and a re-mounted set mounts a fresh space whose
     * {@code ?decq} region must be filled again.
     */
    @Test
    void testDrainPublishesTheTypeItsAlgebraAndItsDocumentation() {
        final fURI vid = TEST_NAMESPACE.extend("lawful");
        final Type lawful = TypeBuilder.build()
                .tid(INT_TID)
                .vid(vid, "a lawful probe")
                .law(rec(uri("ring"), rec(uri(ZERO), jnt(0), uri(ONE), jnt(1))))
                .create();
        PendingWrites.drainUnder(TEST_NAMESPACE);
        assertTrue(PendingWrites.has(vid), "the type's own write is published AND retained for the next mount");
        assertTrue(PendingWrites.has(PendingWrites.decq(vid)), "the algebra's write is published and retained");
        final Obj read = Machine.read(vid);
        assertEquals(vid, read.vid(), "the type landed at the address it was written at, got " + read);
        assertTrue(read.isType(), "a type written at its vid reads back as a type, got " + read);
        final Obj law = QCollection.decOf(lawful);
        assertTrue(law.isRec() && law.asRec().has(f("ring")),
                "the algebra is readable beside the type through ?decq, got " + law);
        final Obj doc = Machine.read(PendingWrites.docq(vid));
        assertTrue(doc.isRec(), "the documentation is readable through ?docq, got " + doc);
        assertEquals("a lawful probe", doc.asRec().at(uri(DESC)).strValue(), "the doc string survives the round trip");
    }

    /**
     * (e) DECLARE AFTER THE FACT — {@code declare(type, algebra)} queues the same {@code ?decq} write a builder would
     * have queued, for a type that was already created without one.
     */
    @Test
    void testDeclareAttachesAnAlgebraToAnExistingType() {
        final fURI vid = TEST_NAMESPACE.extend("later");
        final Type later = TypeBuilder.build().tid(INT_TID).vid(vid).create();
        assertFalse(PendingWrites.has(PendingWrites.decq(vid)), "the type declared no algebra of its own");
        TypeBuilder.declare(later, Algebra.of(rec(uri("add_monoid"), rec(uri(ID), jnt(0)))));
        assertTrue(PendingWrites.has(PendingWrites.decq(vid)),
                "the declaration is queued at " + PendingWrites.decq(vid));
        PendingWrites.drainUnder(TEST_NAMESPACE);
        final Obj law = QCollection.decOf(later);
        assertTrue(law.isRec() && law.asRec().has(f("add_monoid")),
                "the later declaration reaches ?decq, got " + law);
    }

    // ============================ helpers ============================

    /**
     * The algebra QUEUED at a type's {@code ?decq}, read off the queue before any drain — so a test can state what a
     * chain composed without depending on a mounted space.
     */
    private static Algebra queuedAlgebra(final fURI vid) {
        final PendingWrites.Write write = PendingWrites.writes().stream()
                .filter(candidate -> candidate.address().equals(PendingWrites.decq(vid)))
                .findFirst().orElse(null);
        assertNotNull(write, "an algebra is queued at " + PendingWrites.decq(vid));
        return Algebra.of(write.value().asRec());
    }

    /**
     * The operation address an algebra's role binds, read WITHOUT dereferencing the {@code !*} pointer (the target is
     * the contract the operation was minted with).
     */
    private static fURI roleAddress(final Rec block, final String role) {
        final fURI address = Obj.Helper.getPointer(block.atDirect(uri(role))).orElse(null);
        assertNotNull(address, "the role " + role + " binds an operation address");
        return address;
    }
}
