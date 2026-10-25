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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.impl.MFail;
import studio.phaseshift.metatron.isa.mach.type.machine.AbstractMachine;
import studio.phaseshift.metatron.isa.mach.type.machine.MachineSpace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

/**
 * THE MACHINE'S OWN SPHERE (Marko, 2026-10-25). Each machine holds a {@link MachineSpace} that {@code Machine.read} and
 * {@code Machine.write} ask FIRST: the machine itself, its components and its system information, which it keeps in its
 * own store ({@code ~/sys}). It is not in memory, and it declines everything else under the machine's vid — the
 * machine's data — so memory's spaces answer that exactly as before. A child's sphere is its own vid; an address outside
 * every enclosing sphere, or carrying a scheme or a host, is no sphere's.
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

    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "/                                  % true",
            "/network/roster                    % true",
            "/sys/fail/_?incrq                  % true",
            "/memory?docq                       % true",
            "/scratch/x                         % false",
            "ws://10.0.0.9:8555/                % false",
            "ws://10.0.0.9:8555/network/roster  % false",
            "ws://10.0.0.9:8555/sys/log         % false",
            "ide:command                        % false"})
    public void testTheRootsSphereCoversOnlyItsOwnPaths(final String address, final boolean covered) {
        // the root's vid, /, prefixes every path, so a scheme or a host must be refused on its own: a peer's /network or
        // /sys is the peer's, never answered by this machine's sphere
        assertEquals(covered, Machine.root().space().covers(f(address)), address);
    }

    @Test
    public void testSysIsTheSpheresOwnStore() {
        final Machine machine = Machine.current();
        // "I would have sys/# simply go into MachineSpace" (Marko, 2026-10-25): the log sink is in the sphere's store, and
        // memory — whose catch-all still covers the address — holds no ~/sys at all
        final fURI log = machine.vid().resolve().extend("sys").extend("log");
        assertTrue(machine.space().ownsWrite(log), "~/sys is the sphere's to write");
        assertFalse(machine.space().read(log).isNoObj(), "the log sink is in the sphere's own store");
        assertTrue(machine.memory().read(log).isNoObj(), "memory holds no ~/sys");
        final fURI tally = machine.vid().resolve().extend("sys").extend("tally");
        try {
            Machine.write(f("~/sys/tally"), jnt(3));
            checkEquality(LOG, jnt(3), machine.space().read(tally), true);
            checkEquality(LOG, jnt(3), Machine.read(f("~/sys/tally")), true);
            assertTrue(machine.memory().read(tally).isNoObj(), "a ~/sys write lands in the sphere, never in memory");
        } finally {
            Machine.write(f("~/sys/tally"), noobj());
        }
        assertTrue(machine.space().read(tally).isNoObj(), "and a noobj write is its deletion");
    }

    @Test
    public void testASpaceBeneathSysStillGainsItsAddresses() {
        // a space mounted beneath ~/sys (a tbleSpace's schema instset at /sys/space/<name>/instset/# is one) claims its
        // addresses more specifically than ~/sys does, so it answers them — as when ~/sys was a space in memory
        final fURI vid = f("/sys/space/sphere_yield/space");
        final memSpace beneath = memSpace.of(f("/sys/space/sphere_yield/#"), vid);
        try {
            Machine.write(f("/sys/space/sphere_yield/tally"), jnt(4));
            checkEquality(LOG, jnt(4), beneath.read(f("/sys/space/sphere_yield/tally")), true);
            assertTrue(Machine.root().space().read(f("/sys/space/sphere_yield/tally")).isNoObj(), "the sphere declines it");
            assertFalse(Machine.root().space().ownsWrite(f("/sys/space/sphere_yield/tally")), "and does not own its write");
            checkEquality(LOG, jnt(4), Machine.read(f("/sys/space/sphere_yield/tally")), true);
            // the rest of ~/sys is still the sphere's
            assertTrue(Machine.root().space().ownsWrite(f("/sys/tally")));
        } finally {
            Machine.root().memory().removeSpace(vid);
        }
    }

    @Test
    public void testAFailIsMintedInTheSphere() {
        final Machine machine = Machine.current();
        // a fail mints its own key under ~/sys/fail through the sphere's ?incrq
        final Fail minted = MFail.fail("a fail the sphere keeps for its machine");
        assertNotNull(minted.vid(), "the fail was written, and carries the key it was minted at");
        try {
            assertTrue(machine.space().covers(minted.vid()), "its key is in the machine's ~/sys");
            assertFalse(machine.space().read(minted.vid()).isNoObj(), "it lives in the sphere's store");
            assertTrue(machine.memory().read(minted.vid()).isNoObj(), "not in memory");
        } finally {
            Machine.write(minted.vid(), noobj());
        }
    }

    @Test
    public void testAQueryOnAComponentIsMemorys() {
        final MachineSpace sphere = Machine.root().space();
        // the q-processors that answer a ?docq are memory's: the component is not its own documentation
        assertSame(Machine.root().memory(), sphere.read(f("/memory")), "the component reads live");
        assertTrue(sphere.read(f("/memory?docq")).isNoObj(), "a query on it is declined to memory");
        assertTrue(sphere.read(f("/.?docq")).isNoObj(), "and so is a query on the machine itself");
    }

    @Test
    public void testACopyOfAMachineKeepsItsSphere() {
        final Machine child = Machine.current().push(f("sphere_copy"));
        try {
            Machine.write(f("~/sys/tally"), jnt(9));
            // a copy is the same machine — what move reads back at its address, or a pop lands on — so it answers the
            // same ~/sys; only a pushed child binds a sphere of its own
            final Machine copy = ((AbstractMachine) child).clone(null, child.tid(), child.vid());
            assertSame(child.space(), copy.space(), "a copy keeps the machine's sphere");
            checkEquality(LOG, jnt(9), copy.space().read(child.vid().resolve().extend("sys").extend("tally")), true);
        } finally {
            child.pop();
        }
    }

    @Test
    public void testAChildsSysIsItsOwnAndLeavesWithIt() {
        final Machine child = Machine.current().push(f("sphere_sys"));
        final fURI tally = child.vid().resolve().extend("sys").extend("tally");
        try {
            Machine.write(f("~/sys/tally"), jnt(5));
            checkEquality(LOG, jnt(5), child.space().read(tally), true);
            assertTrue(Machine.root().space().read(f("/sys/tally")).isNoObj(), "the root's ~/sys is untouched");
        } finally {
            child.pop();
        }
        // from the root, the popped frame's ~/sys is in no sphere, and memory never held it
        assertTrue(Machine.read(tally).isNoObj(), "a frame's ~/sys leaves with the frame");
    }
}
