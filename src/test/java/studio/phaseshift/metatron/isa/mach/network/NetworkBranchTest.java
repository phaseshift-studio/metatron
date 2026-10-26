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

package studio.phaseshift.metatron.isa.mach.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE NETWORK BRANCH IS THE COMPONENT. A machine's {@code <vid>/network/#} is answered by its network itself — handed
 * there by the machine's own sphere (MachineSpace), not mounted in memory — so a field written there lands on the network the machine holds
 * (never a rebuilt copy), the root's {@code /network/roster} is the root network's own roster, and the component itself
 * is not a value an address can overwrite. An address under no network branch is still the authority dispatch's.
 */
//@Isolated
//@Execution(ExecutionMode.SAME_THREAD)
public class NetworkBranchTest extends AbstractMetatronTest {

    @AfterEach
    public void cleanup() {
        this.resetToRoot();
    }

    @Test
    public void testEachMachinesBranchIsItsOwnNetwork() {
        final Machine root = Machine.current();
        // the branch is answered through the machine's own sphere (MachineSpace), never by a space in memory
        assertSame(root.space(), Machine.Helper.sphereOf(root, f("/network/peer")), "the root's branch is in the root's own sphere");
        assertNotSame(root.network(), root.memory().findSpace(f("/network/peer")), "the network is not a space in memory");
        checkEquality(LOG, root.network().atDirect(uri(PATTERN)), uri("/network/#"), true);
        final Machine child = root.push(f("branch_child"));
        try {
            assertNotSame(root.network(), child.network());
            assertSame(child.space(), Machine.Helper.sphereOf(child, child.vid().resolve().extend("network").extend("peer")),
                    "a pushed frame's branch is in that frame's own sphere");
            assertSame(child.network(), Machine.read(f("~/network")), "~/network reads the component itself");
            assertSame(child.network(), ObjmtronSerializer.parse("*<~/network>").apply(), "a dereference hands the component back as itself");
        } finally {
            child.pop();
        }
    }

    @Test
    public void testAFieldIsWrittenOntoTheLiveNetwork() {
        final Machine child = Machine.current().push(f("branch_child"));
        try {
            this.script("[STATE] <~/network/name> -> 'lab'");
            assertEquals(Optional.of("lab"), child.network().name(), "the write lands on the network the machine holds");
            this.script("*<~/network/name> % 'lab'");
            // the component is a space, so a step into it is read through the space, beneath its branch
            // bare, not <~/network>: the uri's closing '>' would take the first '>' of the step and leave '>name'
            checkEquality(LOG, ObjmtronSerializer.parse("*~/network>>name").apply().vid(null), str("lab"), true);
            this.script("[STATE] <~/network/name> -> noobj");
            assertEquals(Optional.empty(), child.network().name(), "a noobj write is the field's deletion");
        } finally {
            child.pop();
        }
    }

    @Test
    public void testTheRootRosterIsTheRootNetworksOwnField() {
        final Obj declared = Machine.read(Network.ROOT_ROSTER);
        try {
            Machine.write(Network.ROOT_ROSTER, rec(mutableMap(uri(MACHINE), rec(mutableMap(uri("lab"), uri("ws://10.0.0.9:8555/"))))));
            final Obj field = Machine.root().network().atDirect(uri(ROSTER));
            assertTrue(field.isRec(), "the roster is a field of the root network, not an entry beside it: " + field);
            checkEquality(LOG, field.asRec().at(uri(MACHINE)), rec(uri("lab"), uri("ws://10.0.0.9:8555/")), true);
            this.script("[STATE] </network/roster/machine/lab> -> <ws://10.0.0.9:8777/>");
            checkEquality(LOG, Machine.current().network().machines().at(uri("lab")), uri("ws://10.0.0.9:8777/"), true);
        } finally {
            Machine.write(Network.ROOT_ROSTER, declared);
        }
    }

    @Test
    public void testTheComponentIsNotAValue() {
        final Machine child = Machine.current().push(f("branch_child"));
        try {
            final Network network = child.network();
            final Obj host = network.atDirect(uri(HOST));
            this.script("[STATE] <~/network> -> noobj");
            this.script("[STATE] <~/network> -> 5");
            assertSame(network, child.network());
            assertFalse(host.isNoObj());
            checkEquality(LOG, network.atDirect(uri(HOST)), host, true);
            assertSame(network, Machine.read(f("~/network")), "the component still answers its branch");
        } finally {
            child.pop();
        }
    }

    @Test
    public void testAnAddressOffTheBranchIsTheAuthorityDispatchs() {
        final Network network = Machine.current().network();
        // a ws:// read with no peer behind it: the remote ladder answers noobj, never the local branch
        assertTrue(network.read(f("ws://localhost:1/network/peer")).isNoObj());
        assertTrue(network.read(f("/elsewhere/host")).isNoObj(), "a path off the branch is the dispatch's, and it answers no hostless address");
        assertFalse(network.read(f("/network/host")).isNoObj(), "a path on the branch is the space's");
    }
}
