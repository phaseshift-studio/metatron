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

package studio.phaseshift.metatron.isa.mach.type;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The frame's component views are formed <b>on access and cached</b>.
 * <p>
 * The caching is the load-bearing half, not a performance nicety. If each call to {@code memory()} formed a fresh
 * union, two calls in the same frame would be two unions over the same parent — so a write through the first would
 * be invisible to a read through the second, and within a single frame a binding would silently vanish. That is
 * why the frame holds the view rather than a boolean saying "not yet diverged".
 * <p>
 * The other consequence is placement. A frame's views cannot live on the {@code Machine}: a machine is a value
 * that may be evaluated by several threads at once, so a shared cache would let two threads write into one
 * {@code current()}. The frame stack is per-thread, and so is everything the frame holds.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MachineFrameTest extends AbstractMetatronTest {

    @AfterEach
    public void unwind() {
        // a failed assertion must not leave a frame on this thread's stack
        while (null != Machine.frame())
            Machine.frame().machine().pop();
    }

    /**
     * the property that motivated the cache: repeated access in one frame is one view, so a write is seen
     */
    @Test
    public void testAccessWithinAFrameYieldsOneView() {
        final Machine machine = Machine.defaultMachine().push();
        try {
            final Rec first = machine.memory();
            final Rec second = machine.memory();
            assertSame(first, second, "two accesses in one frame must be the same view");

            first.at(uri("n"), jnt(7), MUTABLE);
            assertEquals(7, second.at(uri("n")).intValue(),
                    "a write through one access must be visible through the other");
        } finally {
            machine.pop();
        }
    }

    /**
     * distinct frames get distinct views — a write in a child is not visible to the parent
     */
    @Test
    public void testFramesDoNotShareTheirWrites() {
        final Machine machine = Machine.defaultMachine().push();
        try {
            machine.memory().at(uri("outer"), jnt(1), MUTABLE);
            final Rec outer = machine.memory();

            machine.push();
            machine.memory().at(uri("inner"), jnt(2), MUTABLE);
            assertEquals(2, machine.memory().at(uri("inner")).intValue(), "the frame sees its own write");
            assertEquals(1, machine.memory().at(uri("outer")).intValue(), "and reads through to the parent");
            machine.pop();

            assertTrue(machine.memory().at(uri("inner")).isNoObj(),
                    "the child's binding must be gone once the frame is popped");
            assertEquals(1, machine.memory().at(uri("outer")).intValue(),
                    "and the parent's binding must have survived the pop");
            assertSame(outer, machine.memory(), "the parent's view is the same one it had before");
        } finally {
            machine.pop();
        }
    }

    /**
     * {@code previous} is <b>the rec entry</b>, so {@code >>previous} and {@code previous()} read the same place
     * and cannot disagree. Keeping it in a Java field would have put the inherited level somewhere the language
     * that composes these machines cannot see.
     */
    @Test
    public void testPreviousIsTheRecEntry() {
        final Machine machine = Machine.defaultMachine().push();
        try {
            final MemoryUnion memory = (MemoryUnion) machine.memory();
            assertSame(memory.previous(), memory.at(uri("previous")),
                    "the accessor and the rec entry must be one source of truth");
            assertSame(machine.ownMemory(), memory.previous(),
                    "a first frame inherits the machine's own memory");
        } finally {
            machine.pop();
        }
    }

    /**
     * A union spans two levels, so it carries <b>two machines</b>: {@code current().machine()} is this frame's,
     * {@code previous().machine()} is the one it inherited from. That is only true because {@code parent} is set
     * on each side rather than looked up — and {@code previous} is deliberately not re-parented, since adopting
     * it into this union would make it answer for the wrong machine.
     */
    @Test
    public void testAUnionCarriesTwoMachinesAcrossANest() {
        final Machine outer = Machine.defaultMachine().push();
        // mach0() rather than constructing one: every located Obj writes itself into a space on construction
        // (objCheckAndSave -> Machine.writeToSpace), so a machine built with a vid no space covers throws
        // post-boot. That is fail-closed working — and it is why the ROOT, whose vid is /, only constructs
        // during boot, where the throw is suppressed.
        final Machine inner = Machine.mach0();
        try {
            // push() MINTS, so the frame belongs to the CHILD it returns, not to the machine that pushed. Tracking
            // the returned machine is the migration this test needed: the nesting and the two-level union are
            // unchanged, only which machine answers for the frame's own level.
            final Machine innerFrame = inner.push();
            final MemoryUnion memory = (MemoryUnion) inner.memory();
            assertSame(innerFrame, memory.machine(), "the union belongs to the machine of its own frame");
            assertSame(innerFrame, memory.current().machine(), "current() is this frame's level");
            assertSame(outer, memory.previous().machine(),
                    "previous() is the level it inherited from — a different machine");
            inner.pop();
        } finally {
            outer.pop();
        }
    }

    /**
     * a frame allocates nothing for a component it never touches
     */
    @Test
    public void testAnUntouchedComponentIsNotMaterialized() {
        final Machine machine = Machine.defaultMachine().push();
        try {
            // touch memory only
            machine.memory();
            assertNull(Machine.frame().instsetView(), "instset was never touched, so it must not be formed");
            assertNotNull(Machine.frame().memoryView(), "memory was touched, so it is formed");
        } finally {
            machine.pop();
        }
    }

    /**
     * with no frame pushed the accessors are exactly the machine's own slots — which is what makes this change
     * inert until frames are actually used
     */
    @Test
    public void testNoFrameFallsThroughToTheMachine() {
        assertNull(Machine.frame(), "no frame is pushed on this thread");
        final Machine machine = Machine.defaultMachine();
        assertFalse(machine.instset() instanceof InstSetUnion,
                "with no frame there is no composition: the accessor is the machine's own slot");
        assertFalse(machine.memory() instanceof MemoryUnion,
                "with no frame there is no composition here either");
    }

    /*
     * push(fURI) mints a CHILD whose address strictly extends its parent's, and refuses an extension that escapes.
     * The refusal matters as much as the minting: `..` is arithmetic, so `push(f(".."))` would otherwise address a
     * SIBLING or an ANCESTOR while claiming to be a child — and Frame.next() would then report siblings as
     * children.
     */
    @Test
    public void testPushMintsAChildWithADerivedVid() {
        final Machine parent = Machine.defaultMachine();
        final Machine child = parent.push(f("m1"));
        assertNotSame(parent, child, "push must mint a CHILD, never return the parent");
        assertEquals(parent.vid().extend("m1"), child.vid(), "the child's vid extends the parent's");
        assertSame(child, Machine.frame().machine(), "the current frame now belongs to the child");
        // nest one deeper: the chain encodes the ancestry, which is the property the address space is built on
        final Machine grandchild = child.push(f("m2"));
        assertEquals(child.vid().extend("m2"), grandchild.vid(), "the grandchild's vid extends the child's");
        assertSame(grandchild, Machine.frame().machine());
        grandchild.pop();
        assertSame(child, Machine.frame().machine(), "pop returns to the parent's frame, not to none");
        child.pop();
        assertNull(Machine.frame(), "the outermost pop leaves no frame on this thread");
    }

    @Test
    public void testPushRefusesANonDescendant() {
        final Machine parent = Machine.defaultMachine().push(f("m1"));
        assertThrows(MTronException.class, () -> parent.push(f("..")), "a `..` extension escapes the parent and must be refused");
        parent.pop();
    }


    /*
     * The frame tree's DOWNWARD links. A frame is reachable at its own address while it is live and gone the moment
     * its owner pops. parent-only links cannot do this — they walk up, and the ThreadLocal holds only the cursor —
     * which is why the per-owner registry exists.
     */
    @Test
    public void testALiveFrameResolvesByItsAddress() {
        final Machine child = Machine.defaultMachine().push(f("m1"));
        assertSame(Machine.frame(), Machine.frameAt(child.vid()), "a live frame resolves at its own address");
        child.pop();
        assertNull(Machine.frameAt(child.vid()), "and is unreachable the moment it is popped");
    }

    @Test
    public void testSiblingFramesResolveIndependently() {
        final Machine root = Machine.defaultMachine();
        final Machine m1 = root.push(f("m1"));
        final Machine m11 = m1.push(f("m11"));
        final Machine m12 = m1.push(f("m12"));
        assertEquals(List.of(m11.vid(), m12.vid()), Machine.frameAddressesUnder(m1.vid()),
                "the children of m1 in push order — and m1 itself is not one of them");
        assertSame(Machine.frame(), Machine.frameAt(m12.vid()), "the deepest frame is the current one");
        m12.pop();
        assertNull(Machine.frameAt(m12.vid()), "the popped sibling is gone");
        assertSame(m11, Machine.frameAt(m11.vid()).machine(), "and its sibling is untouched by that");
        m11.pop();
        m1.pop();
    }

    @Test
    public void testAFrameIsOwnedByItsThreadNotGlobal() throws Exception {
        final Machine child = Machine.defaultMachine().push(f("m1"));
        final fURI address = child.vid();
        final AtomicBoolean seenElsewhere = new AtomicBoolean(false);
        final Thread other = new Thread(() -> seenElsewhere.set(null != Machine.frameAt(address)));
        other.start();
        other.join();
        assertFalse(seenElsewhere.get(), "another thread's registry is its own — a frame is owned, not global");
        assertSame(Machine.frame(), Machine.frameAt(address), "while its owner still resolves it");
        child.pop();
    }


    /*
     * apply(uri) is the frame algebra's single morphism application: the coefficient chooses the direction, so
     * push/pop are one operation and its inverse rather than two operations.
     */
    @Test
    @Disabled
    public void testApplyDescendsOnAPositiveCoefficient() {
        final Machine root = Machine.defaultMachine();
        final Obj child = root.move(f("m1"));
        assertEquals(root.vid().extend("m1"), child.<Machine>as().vid(), "a positive coefficient descends");
        assertSame(child.<Machine>as(), Machine.frame().machine(), "and the frame belongs to the child");
        assertEquals(root, child.<Machine>as().move(f("m1{-1}")).<Machine>as(), "{-1} on the same name ascends to the parent");
        assertNull(Machine.frame(), "and leaves no frame behind");
    }

    @Test
    @Disabled
    public void testApplyInverseIsUndefinedForAStepNotTaken() {
        final Machine root = Machine.defaultMachine();
        assertTrue(root.move(f("nope{-1}")).isNoObj(),
                "you cannot invert a step you did not take — undefined, not a silent mis-pop");
        assertTrue(root.move(f(".{-1}")).isNoObj() || root == root.move(f(".")), "the identity pushes nothing");
        assertEquals(root, root.move(f(".")), "`<.>` is the ring's identity and returns the receiver");
    }


    /*
     * The perspective is a value carried BETWEEN machines, not the frame stack: it moves when a deref yields a
     * machine and restores when the fragment ends. current() intentionally does not follow it.
     */
    @Test
    public void testAPerspectiveMovesAndRestores() {
        final Machine root = Machine.authority();
        assertEquals(Machine.root(), Machine.current(), "with nothing set, the frame of reference is the root");
        final Machine child = root.push(studio.phaseshift.metatron.util.CommonUtil.mintShortUUID(root.vid(), false));
        final Obj returned = Machine.withPerspective(child, () -> {
            assertEquals(child, Machine.current(), "inside the fragment, the frame of reference is the other machine");
            assertEquals(root, Machine.authority(), "current() stays the root, so resolution is untouched");
            return child;
        });
        assertEquals(child, returned, "the fragment's value comes back out");
        assertEquals(root, Machine.current(), "and the frame of reference restores when the fragment ends");
    }


    /*
     * The persistent overload: the perspective stays where it was put until something moves it again. No return
     * pointer is handed out — you address your way home.
     */
    @Test
    public void testAPerspectiveCanPersist() {
        final Machine root = Machine.root();
        final Machine child = root.push(studio.phaseshift.metatron.util.CommonUtil.mintShortUUID(root.vid(), false));
        assertEquals(child, Machine.withPerspective(child), "the overload returns the machine it moved to");
        assertEquals(child, Machine.current(), "and leaves the frame of reference there");
        assertEquals(root, Machine.root(), "root() is still the root");
        Machine.withPerspective(root);
        assertEquals(root, Machine.current(), "only another call moves it — you name your way home");
    }

}
