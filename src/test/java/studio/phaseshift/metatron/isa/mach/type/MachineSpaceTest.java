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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.mach.type.machine.MachineSpace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

/**
 * THE MACHINE'S OWN SPHERE (Marko, 2026-10-25). Each machine holds a {@link MachineSpace} that {@code Machine.read} and
 * {@code Machine.write} ask FIRST: the machine itself, its components and its system information. It is not in memory,
 * and it declines everything else under the machine's vid — the machine's data — so memory's spaces answer that exactly
 * as before. A child's sphere is its own vid; an address outside every enclosing sphere is no sphere's.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class MachineSpaceTest extends AbstractMetatronTest {

    @AfterEach
    public void cleanup() {
        this.resetToRoot();
    }

    @Test
    public void testTheRootsSphereIsTheRootAndItsComponents() {
        final Machine root = Machine.root();
        final MachineSpace sphere = root.space();
        assertNotNull(sphere, "every machine with a vid holds its own sphere");
        assertTrue(sphere.covers(f("/")), "the machine itself");
        assertTrue(sphere.covers(f("/network/roster")), "a component");
        assertTrue(sphere.covers(f("/sys/log")), "its system information");
        assertFalse(sphere.covers(f("/scratch/x")), "the machine's data is not the machine");
        assertNotSame(sphere, root.memory().findSpace(f("/network")), "the sphere is not a space in memory");
        assertSame(root, Machine.read(f("/.")), "/. is the root machine, answered by its sphere");
        assertSame(root.memory(), Machine.read(f("/memory")), "a component reads live");
        assertSame(root.network(), Machine.read(f("/network")), "the network reads as itself");
    }

    @Test
    public void testAChildsSphereIsItsOwnAndTheRootsIsReachedByWalkingUp() {
        final Machine parent = Machine.current();
        final Machine child = parent.push(f("sphere_child"));
        try {
            assertNotSame(parent.space(), child.space(), "a pushed frame binds a sphere of its own");
            assertSame(child.memory(), Machine.read(f("~/memory")), "~ is the child's own sphere");
            assertSame(child.network(), Machine.read(f("~/network")));
            // an address in no child's sphere is an enclosing machine's: the walk goes up the machine chain to the root
            assertSame(Machine.root().network(), Machine.read(f("/network")), "the root's components stay reachable from a child");
            final fURI childNetwork = child.vid().resolve().extend("network");
            assertSame(child.space(), Machine.Helper.sphereOf(child, childNetwork));
            assertSame(Machine.root().space(), Machine.Helper.sphereOf(child, f("/network")));
        } finally {
            child.pop();
        }
    }

    @Test
    public void testASiblingsSphereIsNoSphereOfMine() {
        final Machine parent = Machine.current();
        final Machine sibling = parent.push(f("sphere_sibling"));
        final fURI siblingNetwork = sibling.vid().resolve().extend("network");
        sibling.pop();
        final Machine child = parent.push(f("sphere_child"));
        try {
            assertNull(Machine.Helper.sphereOf(child, siblingNetwork), "a sibling's components are in neither my sphere nor the root's");
        } finally {
            child.pop();
        }
    }

    @Test
    public void testTheMachinesDataIsDeclinedToMemory() {
        final Machine child = Machine.current().push(f("sphere_data"));
        try {
            Machine.write(f("~/scratch/x"), jnt(7));
            checkEquality(LOG, jnt(7), Machine.read(f("~/scratch/x")), true);
            // the sphere declined it: the data lives in memory, at the machine's own data level
            final fURI at = child.vid().resolve().extend("scratch").extend("x");
            assertFalse(child.space().covers(at), "data under the vid is not the machine's sphere");
            checkEquality(LOG, jnt(7), child.memory().read(at), true);
        } finally {
            child.pop();
        }
    }

    @Test
    public void testSysIsStillMemorysInTheFirstSlice() {
        final Machine machine = Machine.current();
        // the logger, the reflected recs and the thread registry still reach ~/sys through memory directly, so the sphere
        // covers ~/sys but declines it, and memory answers — the log sink is there
        final fURI log = machine.vid().resolve().extend("sys").extend("log");
        assertTrue(machine.space().covers(log));
        assertTrue(machine.space().read(log).isNoObj(), "the first slice declines ~/sys");
        assertFalse(Machine.read(f("~/sys/log")).isNoObj(), "and memory answers it, as before");
    }
}
