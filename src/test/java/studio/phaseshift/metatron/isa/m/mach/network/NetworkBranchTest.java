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

package studio.phaseshift.metatron.isa.m.mach.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.machine.MachineSpace;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.mach.type.network.PeerSpawn;
import studio.phaseshift.metatron.isa.m.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.m.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.isa.m.web.type.MIME;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.space.ws.wsServer.WS_CLIENT_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE NETWORK BRANCH IS THE NETWORK'S OWN SPACE. A machine's {@code <vid>/network/#} is answered by its network — handed
 * there by the machine's own space (MachineSpace, mounted in memory; the network is no space in memory) — from the
 * memSpace cache behind it, so a field written there lands in the network the machine holds (never a rebuilt copy), the
 * root's {@code /network/roster} is the root network's own roster, and the component itself is not a value an address
 * can overwrite. An address under no network branch is still the authority dispatch's.
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
        // the branch is answered through the machine's own space (MachineSpace), never by a network space in memory
        final Space rootSpace = root.memory().findSpace(f("/network/peer"));
        assertInstanceOf(MachineSpace.class, rootSpace, "the root's branch is in the root's own space");
        checkEquality(LOG, root.network().atDirect(uri(PATTERN)), uri("/network/#"), true);
        final Machine child = root.push(f("branch_child"));
        try {
            assertNotSame(root.network(), child.network());
            final Space own = child.memory().findSpace(child.vid().resolve().extend("network").extend("peer"));
            assertInstanceOf(MachineSpace.class, own, "a pushed frame's branch is in a machine's space");
            assertNotSame(rootSpace, own, "the frame's own, not the root's");
            assertSame(child.network(), Machine.read(f("~/network")), "~/network reads the component itself");
            assertSame(child.network(), ObjmtronSerializer.parse("*<~/network>").apply(), "a dereference hands the component back as itself");
        } finally {
            child.pop();
        }
    }

    @Test
    public void testAFieldIsWrittenOntoTheLiveNetwork() {
        final Machine child = Machine.current().push(f("branch_child"));
        final fURI host = f("ws://lab.example:8555");
        try {
            // host is a DECLARED field of network::T and nothing here reads it, so it is a safe probe; the branch
            // gate (pattern) is untouched, so reads keep answering through the network itself
            this.script("[STATE] <~/network/host> -> <ws://lab.example:8555>");
            assertEquals(host, child.network().read(f(HOST)).uriValue(),
                    "the write lands on the network the machine holds");
            this.script("*<~/network/host> % <ws://lab.example:8555>");
            this.script("[STATE] <~/network/host> -> noobj");
            assertTrue(child.network().read(f(HOST)).isNoObj(), "a noobj write is the field's deletion");
        } finally {
            child.pop();
        }
    }

    @Test
    public void testTheRootRosterIsTheRootNetworksOwnField() {
        final Obj declared = Machine.read(Network.ROOT_ROSTER);
        try {
            // a row is <host> => [<protocol> => [<transport> => uri{+}]]
            final Obj lab = rec(uri("lab"), rec(uri(MTRON), rec(uri(WS), uri("ws://10.0.0.9:8555/"))));
            Machine.write(Network.ROOT_ROSTER, rec(mutableMap(uri(MACHINE), lab)));
            final Obj field = Machine.root().network().read(f(ROSTER));
            assertTrue(field.isRec(), "the roster is the root network's own, not an entry beside it: " + field);
            checkEquality(LOG, field.asRec().at(uri(MACHINE)), lab, true);
            this.script("[STATE] </network/roster/machine/lab/mtron/ws> -> <ws://10.0.0.9:8777/>");
            checkEquality(LOG, Machine.current().network().machines().at(uri("lab")), uri("ws://10.0.0.9:8777/"), true);
        } finally {
            Machine.write(Network.ROOT_ROSTER, declared);
        }
    }

    /**
     * A ROW IN ANOTHER SHAPE IS SKIPPED, NEVER THROWN ON. Roster writes reach the store unchecked, so a row in the flat
     * {@code <host> => uri{+}} of old, or one a level short, must leave machines() — and the status report, placement
     * and plan snapshots that read it — answering for every row that is in shape.
     */
    @Test
    public void testARowInAnotherShapeIsSkipped() {
        final Obj declared = Machine.read(Network.ROOT_ROSTER);
        try {
            Machine.write(Network.ROOT_ROSTER, rec(mutableMap(uri(MACHINE), rec(mutableMap(
                    uri("flat"), uri("ws://10.0.0.7:8555/mtron"),
                    uri("short"), rec(uri(MTRON), uri("ws://10.0.0.8:8555/mtron")),
                    uri("lab"), rec(uri(MTRON), rec(uri(WS), uri("ws://10.0.0.9:8555/mtron"))))))));
            final Obj machines = Machine.current().network().machines();
            checkEquality(LOG, uri("ws://10.0.0.9:8555/mtron"), machines.asRec().at(uri("lab")), true);
            assertEquals(1, machines.asRec().jvm().size(), "only the row in shape is a machine: " + machines);
            assertFalse(Machine.current().network().status(noobj()).isFail(), "the status report reads past the others");
        } finally {
            Machine.write(Network.ROOT_ROSTER, declared);
        }
    }

    /**
     * A SPAWNED PEER IS FILED AS THE RING FILES IT, AND TAKEN BACK WITH ITS FRAME: a non-loopback authority forks
     * nothing and is declared, its host keying an mtron row the scheme-keyed view lists once however often it is
     * asked for; the frame that holds a connection to it reaps it on pop, the row taken back, and the origin can be
     * declared again.
     */
    @Test
    public void testASpawnedPeerIsAnMtronRowItsFrameTakesBack() {
        final Obj declared = Machine.read(Network.ROOT_ROSTER);
        final fURI origin = f("").scheme(WS).host("203.0.113.9").port(generatePort());
        try {
            final Machine child = Machine.current().push(f("spawn_reaper"));
            try {
                checkEquality(LOG, uri(PeerSpawn.address(origin)), uri(PeerSpawn.spawn(origin.extend(f("/spawned")))), true);
                PeerSpawn.spawn(origin.extend(f("/spawned")));
                checkEquality(LOG, uri(PeerSpawn.address(origin)), listed(origin), true);
                offline(PeerSpawn.address(origin));
            } finally {
                child.pop();
            }
            checkEquality(LOG, noobj(), listed(origin), true);
            PeerSpawn.spawn(origin.extend(f("/spawned")));
            checkEquality(LOG, uri(PeerSpawn.address(origin)), listed(origin), true);
        } finally {
            Machine.write(Network.ROOT_ROSTER, declared);
        }
    }

    /**
     * TWO SPAWNS ON ONE HOST SHARE ITS ROW: the roster keys a machine by its host ({@code machine => <host> => …}), so
     * peers spawned on one machine are one row whose mtron transport lists both addresses, and the pop that reaps one
     * takes back only its own.
     */
    @Test
    public void testTwoSpawnsOnOneHostShareItsRow() {
        final Obj declared = Machine.read(Network.ROOT_ROSTER);
        final fURI kept = f("").scheme(WS).host("203.0.113.9").port(generatePort());
        final fURI reaped = f("").scheme(WS).host("203.0.113.9").port(generatePort());
        try {
            PeerSpawn.spawn(kept.extend(f("/spawned")));
            final Machine child = Machine.current().push(f("spawn_pair"));
            try {
                PeerSpawn.spawn(reaped.extend(f("/spawned")));
                assertEquals(Set.of(PeerSpawn.address(kept), PeerSpawn.address(reaped)),
                        listed(kept).stream().map(Obj::uriValue).collect(Collectors.toSet()), "one row, both addresses");
                offline(PeerSpawn.address(reaped));
            } finally {
                child.pop();
            }
            checkEquality(LOG, uri(PeerSpawn.address(kept)), listed(kept), true);
        } finally {
            Machine.write(Network.ROOT_ROSTER, declared);
        }
    }

    /**
     * A LOCAL SPAWN FORKS A NODE THAT LIVES UNTIL ITS FRAME REAPS IT: spawn names this VM as the node's parent
     * ({@link studio.phaseshift.metatron.isa.m.mach.type.network.MachNode#PARENT}), so the node keeps serving past its
     * readiness (a parent that is no process, or not this one, would have it exit before it was ready), and the frame
     * that dialed it reaps it on pop.
     */
    @Test
    public void testALocalSpawnLivesUntilItsFrameReapsIt() throws InterruptedException {
        final Obj declared = Machine.read(Network.ROOT_ROSTER);
        final fURI origin = f("").scheme(WS).host("localhost").port(generatePort());
        try {
            final Machine child = Machine.current().push(f("spawn_local"));
            try {
                PeerSpawn.spawn(origin.extend(f("/spawned")));
                for (int poll = 0; poll < 5; poll++) {
                    assertTrue(PeerSpawn.alive(origin), "the node serves while its parent lives (poll " + poll + ")");
                    Thread.sleep(200L);
                }
                Network.Helper.connect(Machine.current().network(), PeerSpawn.address(origin));
            } finally {
                child.pop();
            }
            assertFalse(PeerSpawn.alive(origin), "the pop of the frame that dialed it reaps the node");
        } finally {
            Machine.write(Network.ROOT_ROSTER, declared);
        }
    }

    /**
     * A TAKE-BACK INTO A WIPED TABLE IS SKIPPED: the machine table written noobj after the spawn holds no count of it,
     * so the pop's undeclare takes nothing back, and the origin declared again is listed — where a take-back into the
     * absent level would leave a sign the new declaration cancels.
     */
    @Test
    public void testASpawnAfterAWipedTableIsListed() {
        final Obj declared = Machine.read(Network.ROOT_ROSTER);
        final fURI origin = f("").scheme(WS).host("203.0.113.9").port(generatePort());
        try {
            final Machine child = Machine.current().push(f("spawn_wiped"));
            try {
                PeerSpawn.spawn(origin.extend(f("/spawned")));
                offline(PeerSpawn.address(origin));
                Machine.write(Network.ROOT_ROSTER.extend(MACHINE), noobj());
            } finally {
                child.pop();
            }
            PeerSpawn.spawn(origin.extend(f("/spawned")));
            checkEquality(LOG, uri(PeerSpawn.address(origin)), listed(origin), true);
        } finally {
            Machine.write(Network.ROOT_ROSTER, declared);
        }
    }

    /**
     * A SPAWN IS FILED UNDER ITS OWN SCHEME: a wss authority's mtron endpoint is carried by wss, so the scheme-keyed
     * view lists it, and the pop that reaps it takes it back — filed under ws, it was never listed and never taken back.
     */
    @Test
    public void testAWssSpawnIsListedAndTakenBack() {
        final Obj declared = Machine.read(Network.ROOT_ROSTER);
        final fURI origin = f("").scheme(WSS).host("203.0.113.9").port(generatePort());
        try {
            final Machine child = Machine.current().push(f("spawn_wss"));
            try {
                PeerSpawn.spawn(origin.extend(f("/spawned")));
                checkEquality(LOG, uri(PeerSpawn.address(origin)), listed(origin), true);
                offline(PeerSpawn.address(origin));
            } finally {
                child.pop();
            }
            checkEquality(LOG, noobj(), listed(origin), true);
        } finally {
            Machine.write(Network.ROOT_ROSTER, declared);
        }
    }

    /**
     * A LOCAL AUTHORITY IS ws: a forked node serves ws and nothing else (MachNode), so resolve() — which forks nothing —
     * passes a loopback request with no scheme (defaulted to ws, how every boot profile and harvest spawns) or with ws,
     * and refuses one over another scheme, before spawn forks or declares anything.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // scheme % resolves to % why
            "null    % ws   % no scheme is ws: how every boot profile and harvest spawns",
            "ws      % ws   % ws passes as it is",
            "wss     % null % wss is refused: nothing on the node answers it",
            "http    % null % http is refused: nothing on the node answers it",
    }, delimiter = '%', nullValues = "null")
    void testALocalAuthorityIsWs(final String scheme, final String resolved, final String why) {
        final fURI local = f("").host("localhost").port(generatePort());
        final fURI requested = (null == scheme ? local : local.scheme(scheme)).extend(f("/spawned"));
        if (null == resolved)
            assertThrows(MTronException.class, () -> PeerSpawn.resolve(requested), why);
        else
            checkEquality(LOG, uri(local.scheme(resolved).extend(f("/spawned"))), uri(PeerSpawn.resolve(requested)), true);
    }

    /**
     * what the scheme-keyed view lists for an origin's host, the key every roster row is filed under
     */
    private static Obj listed(final fURI origin) {
        return Machine.current().network().machines().jvm().entrySet().stream()
                .filter(entry -> entry.getKey().isUri() && origin.host().equals(entry.getKey().uriValue().toString()))
                .map(Map.Entry::getValue).findFirst().orElse(noobj());
    }

    /**
     * a connection the current frame holds and a pop reaps: the construction PeerCluster dials with, at an address
     * nothing answers (TEST-NET-3), which the constructor waits out (CONNECT_TIMEOUT_MS) before indexing it offline
     * and supervised
     */
    private static void offline(final fURI address) {
        Network.Helper.index(new WebSocketRecClient(new WebSocketRec(mutableMap(
                uri(HOST), uri(address),
                uri(IN), uri(MIME.MIMEType.APPLICATION_MTRON.value),
                uri(OUT), uri(MIME.MIMEType.APPLICATION_MTRON.value)), WS_CLIENT_TID, null)));
    }

    @Test
    public void testTheComponentIsNotAValue() {
        final Machine child = Machine.current().push(f("branch_child"));
        try {
            final Network network = child.network();
            final Obj host = network.read(f(HOST));
            this.script("[STATE] <~/network> -> noobj");
            this.script("[STATE] <~/network> -> 5");
            assertSame(network, child.network());
            assertFalse(host.isNoObj());
            checkEquality(LOG, network.read(f(HOST)), host, true);
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

    /**
     * A LEVEL'S OWN ADDRESSES ARE TWO SPELLINGS OF ONE ENTRY: relative, as its own methods write it, or below its
     * branch, as its machine's space hands it. Any other spelling is the authority dispatch's, which files nothing here:
     * another level's branch, a {@code ~} path (the machine's address, not the component's) and another machine's
     * address all answer noobj and leave the entry as it was.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // address                       % lands
            "host                            % true",
            "/branch_child/network/host      % true",
            "/network/host                   % false",
            "~/network/host                  % false",
            "~                               % false",
            "ws://localhost:1/host           % false",
    }, delimiter = '%')
    void testALevelAnswersAtItsRelativeAndItsBranchAddresses(final String address, final boolean lands) {
        final Machine child = Machine.current().push(f("branch_child"));
        final Obj probe = uri("ws://door.example:8555");
        try {
            final Network network = child.network();
            final Obj before = network.read(f(HOST));
            checkEquality(LOG, network.write(f(address), probe), lands ? probe : noobj(), true);
            checkEquality(LOG, network.read(f(HOST)), lands ? probe : before, true);
            checkEquality(LOG, network.read(child.vid().resolve().extend("network").extend(HOST)), lands ? probe : before, true);
        } finally {
            child.pop();
        }
    }
}
