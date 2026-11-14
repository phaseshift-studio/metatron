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

package studio.phaseshift.metatron.isa.m.mach.type;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.machine.AbstractMachine;
import studio.phaseshift.metatron.isa.m.mach.type.machine.MachineSpace;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MFail;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE MACHINE'S OWN SPACE (Marko, 2026-10-25), mounted in its memory (2026-11-05: "necessary and sufficient"). Each
 * machine mounts a {@link MachineSpace} at {@code <vid>/#}, and memory's ordinary routing reaches it: it answers the
 * machine itself and its components live, and keeps its system information ({@code ~/sys}) and its data in its own
 * store. A more specific space still wins its own addresses. A child's space is its own vid; an address outside every
 * enclosing machine's parts, or carrying a scheme or a host, is no machine's part.
 */
//@Isolated
//@Execution(ExecutionMode.SAME_THREAD)
public class MachineSpaceTest extends AbstractMetatronTest {

    @AfterEach
    public void cleanup() {
        this.resetToRoot();
    }

    /**
     * a machine's own space, as memory finds it at the space's own vid ({@code <vid>/space})
     */
    private static MachineSpace spaceOf(final Machine machine) {
        return machine.memory().findSpace(machine.vid().resolve().extend("space"));
    }

    @Test
    public void testTheRootsSpaceIsTheRootAndItsComponents() {
        final Machine root = Machine.root();
        final MachineSpace space = spaceOf(root);
        assertNotNull(space, "every machine with a vid mounts its own space");
        assertTrue(space.covers(f("/")), "the machine itself");
        assertTrue(space.covers(f("/network/roster")), "a component");
        assertTrue(space.covers(f("/sys/log")), "its system information");
        assertFalse(space.covers(f("/scratch/x")), "the machine's data is not one of its parts");
        assertSame(space, root.memory().findSpace(f("/network")), "memory routes a component to the machine's space");
        assertSame(space, root.memory().findSpace(f("/scratch/x")), "and the machine's data");
        assertSame(root, Machine.read(f("/.")), "/. is the root machine, answered by its space");
        assertSame(root.memory(), root.memory().read(f("/memory")), "memory alone answers a component, live");
        assertSame(root.network(), Machine.read(f("/network")), "the network reads as itself");
    }

    @Test
    public void testAChildsSpaceIsItsOwnAndTheRootsIsReachedByWalkingUp() {
        final Machine parent = Machine.current();
        final Machine child = parent.push(f("space_child"));
        try {
            assertNotSame(spaceOf(parent), spaceOf(child), "a pushed frame mounts a space of its own");
            assertSame(child.memory(), Machine.read(f("~/memory")), "~ is the child's own space");
            assertSame(child.network(), Machine.read(f("~/network")));
            // an address the child's space does not cover is an enclosing level's: memory walks up to the root
            assertSame(Machine.root().network(), Machine.read(f("/network")), "the root's components stay reachable from a child");
            assertSame(spaceOf(child), child.memory().findSpace(child.vid().resolve().extend("network")));
            assertSame(spaceOf(Machine.root()), child.memory().findSpace(f("/network")));
        } finally {
            child.pop();
        }
    }

    @Test
    public void testASiblingsComponentsAreNoPartOfMine() {
        final Machine parent = Machine.current();
        final Machine sibling = parent.push(f("space_sibling"));
        final fURI siblingNetwork = sibling.vid().resolve().extend("network");
        sibling.pop();
        final Machine child = parent.push(f("space_child"));
        try {
            assertFalse(spaceOf(child).covers(siblingNetwork), "a sibling's components are not my parts");
            assertFalse(spaceOf(Machine.root()).covers(siblingNetwork), "nor the root's");
        } finally {
            child.pop();
        }
    }

    @Test
    public void testTheMachinesDataIsInItsSpace() {
        final Machine child = Machine.current().push(f("space_data"));
        try {
            Machine.write(f("~/scratch/x"), jnt(7));
            checkEquality(LOG, jnt(7), Machine.read(f("~/scratch/x")), true);
            // data under the vid is no part of the machine, so the space keeps it in its own store
            final fURI at = child.vid().resolve().extend("scratch").extend("x");
            assertFalse(spaceOf(child).covers(at), "data under the vid is not one of the machine's parts");
            assertSame(spaceOf(child), child.memory().findSpace(at), "memory routes it to the machine's space");
            checkEquality(LOG, jnt(7), spaceOf(child).read(at), true);
        } finally {
            child.pop();
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "/                                  % true",
            "/network/roster                    % true",
            "/processor/fail/_?incrq            % true",
            "/processor/computer/7/fail/1       % true",
            "/memory?docq                       % true",
            "/scratch/x                         % false",
            "ws://10.0.0.9:8555/                % false",
            "ws://10.0.0.9:8555/network/roster  % false",
            "ws://10.0.0.9:8555/sys/log         % false",
            "ide:command                        % false"})
    public void testTheRootsSpaceCoversOnlyItsOwnParts(final String address, final boolean covered) {
        // the root's vid, /, prefixes every path, so a scheme or a host must be refused on its own: a peer's /network or
        // /sys is the peer's, never one of this machine's parts
        assertEquals(covered, spaceOf(Machine.root()).covers(f(address)), address);
    }

    @Test
    public void testSysIsInTheMachinesSpace() {
        final Machine machine = Machine.current();
        // "I would have sys/# simply go into MachineSpace" (Marko, 2026-10-25): the log sink is in the space's store, and
        // memory reaches it there as it reaches every address the space covers
        final fURI log = machine.vid().resolve().extend("sys").extend("log");
        assertSame(spaceOf(machine), machine.memory().findSpace(log), "memory routes ~/sys to the machine's space");
        assertFalse(spaceOf(machine).read(log).isNoObj(), "the log sink is in the space's own store");
        final fURI tally = machine.vid().resolve().extend("sys").extend("tally");
        try {
            Machine.write(f("~/sys/tally"), jnt(3));
            checkEquality(LOG, jnt(3), spaceOf(machine).read(tally), true);
            checkEquality(LOG, jnt(3), Machine.read(f("~/sys/tally")), true);
        } finally {
            Machine.write(f("~/sys/tally"), noobj());
        }
        assertTrue(spaceOf(machine).read(tally).isNoObj(), "and a noobj write is its deletion");
    }

    @Test
    public void testASpaceBeneathSysStillGainsItsAddresses() {
        // a space mounted beneath ~/sys (a tbleSpace's schema instset at /sys/space/<name>/instset/# is one) claims its
        // addresses more specifically than the machine's space does, so it answers them
        final fURI vid = f("/sys/space/beneath/space");
        final memSpace beneath = memSpace.of(f("/sys/space/beneath/#"), vid);
        try {
            Machine.write(f("/sys/space/beneath/tally"), jnt(4));
            assertSame(beneath, Machine.root().memory().findSpace(f("/sys/space/beneath/tally")), "the more specific space gains it");
            checkEquality(LOG, jnt(4), beneath.read(f("/sys/space/beneath/tally")), true);
            assertTrue(spaceOf(Machine.root()).read(f("/sys/space/beneath/tally")).isNoObj(), "the machine's space holds no copy");
            checkEquality(LOG, jnt(4), Machine.read(f("/sys/space/beneath/tally")), true);
            // the rest of ~/sys is still the machine's space's
            assertSame(spaceOf(Machine.root()), Machine.root().memory().findSpace(f("/sys/tally")));
        } finally {
            Machine.root().memory().removeSpace(vid);
        }
    }

    @Test
    public void testAFailIsMintedInTheMachinesSpace() {
        final Machine machine = Machine.current();
        // a fail mints its own key under ~/processor/fail through the space's ?incrq
        final Fail minted = MFail.fail("a fail the space keeps for its machine");
        assertNotNull(minted.vid(), "the fail was written, and carries the key it was minted at");
        try {
            assertTrue(spaceOf(machine).covers(minted.vid()), "its key is in the machine's ~/processor");
            assertSame(spaceOf(machine), machine.memory().findSpace(minted.vid()), "memory routes it to the machine's space");
            assertFalse(Machine.read(minted.vid()).isNoObj(), "it reads back");
            assertFalse(machine.processor().at(uri(f("fail").extend(minted.vid().name()))).isNoObj(),
                    "it lives in the machine's processor");
        } finally {
            Machine.write(minted.vid(), noobj());
        }
    }

    /**
     * A PUSHED MACHINE'S PROCESSOR IS NEW (Marko, 2026-11-13): constructed from a shallow copy of its parent's, never
     * cloned, and the constructor makes what it is handed its own. A shared fail stack or computer index would be a
     * nested rec that every write rewrites in place, so the parent would file the child's fails and index its
     * computers. The stacks are compared by size, not by key: each counts its own keys from one.
     */
    @Test
    public void testAPushedMachinesProcessorIsNew() {
        final Machine parent = Machine.current().push(f("processor_parent"));
        try {
            MFail.fail("filed by the parent, before the push");
            final int parentFails = parent.processor().at(uri("fail")).asRec().jvm().size();
            final Machine child = parent.push(f("child"));
            try {
                assertNotSame(parent.processor(), child.processor(), "the child's processor is its own");
                assertTrue(child.processor().at(uri("fail")).isNoObj(), "it holds none of the parent's fails");
                assertTrue(child.processor().computers().isEmpty(), "and has minted no computer");
                final int parentComputers = parent.processor().computers().jvm().size();
                MFail.fail("filed by the child");
                Machine.parse("1.plus(2)").apply(noobj());
                assertEquals(1, child.processor().at(uri("fail")).asRec().jvm().size(), "the child files its own fails");
                assertFalse(child.processor().computers().isEmpty(), "and indexes its own computers");
                assertEquals(parentFails, parent.processor().at(uri("fail")).asRec().jvm().size(),
                        "neither reaches the parent: its fails");
                assertEquals(parentComputers, parent.processor().computers().jvm().size(), "nor its computers");
            } finally {
                child.pop();
            }
        } finally {
            parent.pop();
        }
    }

    @Test
    public void testAWriteUnderAComponentThatIsNoPartOfItReadsBack() {
        // ~/memory/note is no field of the memory: the write lands in the space's store, and the read finds it there
        // once the component has no answer
        final fURI note = Machine.root().vid().resolve().extend("memory").extend("note");
        try {
            Machine.write(f("~/memory/note"), jnt(11));
            checkEquality(LOG, jnt(11), Machine.read(f("~/memory/note")), true);
            checkEquality(LOG, jnt(11), spaceOf(Machine.root()).read(note), true);
        } finally {
            Machine.write(f("~/memory/note"), noobj());
        }
        assertTrue(Machine.read(note).isNoObj(), "and a noobj write is its deletion");
    }

    @Test
    public void testAQueryOnAComponentIsTheSpacesQProcs() {
        final MachineSpace space = spaceOf(Machine.root());
        // the q-processors that answer a ?docq are the space's: the component is not its own documentation
        assertSame(Machine.root().memory(), space.read(f("/memory")), "the component reads live");
        final Obj doc = space.read(f("/memory?docq"));
        assertFalse(doc.isNoObj(), "a query on it is answered by the space's q-processors");
        assertNotSame(Machine.root().memory(), doc, "not by the component");
    }

    @Test
    public void testACopyOfAMachineReachesItsSpace() {
        final Machine child = Machine.current().push(f("space_copy"));
        try {
            Machine.write(f("~/sys/tally"), jnt(9));
            // a copy is the same machine — what move reads back at its address, or a pop lands on — so it reaches the
            // same space through the memory it shares; only a pushed child mounts a space of its own
            final Machine copy = ((AbstractMachine) child).clone(null, child.tid(), child.vid());
            assertSame(spaceOf(child), spaceOf(copy), "a copy reaches the machine's space");
            checkEquality(LOG, jnt(9), copy.memory().read(child.vid().resolve().extend("sys").extend("tally")), true);
        } finally {
            child.pop();
        }
    }

    @Test
    public void testAChildsSysIsItsOwnAndLeavesWithIt() {
        final Machine child = Machine.current().push(f("space_sys"));
        final fURI tally = child.vid().resolve().extend("sys").extend("tally");
        try {
            Machine.write(f("~/sys/tally"), jnt(5));
            checkEquality(LOG, jnt(5), spaceOf(child).read(tally), true);
            assertTrue(spaceOf(Machine.root()).read(f("/sys/tally")).isNoObj(), "the root's ~/sys is untouched");
        } finally {
            child.pop();
        }
        // from the root, the popped frame's ~/sys left with the frame's space
        assertTrue(Machine.read(tally).isNoObj(), "a frame's ~/sys leaves with the frame");
    }
}
