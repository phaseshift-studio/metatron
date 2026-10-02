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
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import static org.junit.jupiter.api.Assertions.*;
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
            inner.push();
            final MemoryUnion memory = (MemoryUnion) inner.memory();
            assertSame(inner, memory.machine(), "the union belongs to the machine of its own frame");
            assertSame(inner, memory.current().machine(), "current() is this frame's level");
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
}
