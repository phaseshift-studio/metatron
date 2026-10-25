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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.*;
import studio.phaseshift.metatron.isa.web.space.http.httpSpace;
import studio.phaseshift.metatron.isa.web.space.ws.wsServer;
import studio.phaseshift.metatron.util.CommonUtil;

import java.io.Closeable;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The NETWORK's two membership questions, kept apart.
 * <p>
 * "Is this address <em>mine</em>?" and "is this address a <em>declared peer</em>?" are different questions with
 * different answers, and blurring them is what left the authority guard with no answer at all. This suite pins:
 *
 * <ul>
 *   <li><b>mine is derived, not declared</b> — the authorities this machine answers on come from the {@code host}
 *       every mounted LISTENER declares, so there is no self-authority declaration to drift out of step</li>
 *   <li><b>a backend is not mine</b> — a database's or a broker's {@code host} is where this machine dials out
 *       to, and it carries the login; it is never an authority, so discovery never announces it</li>
 *   <li><b>aliases collapse</b> — a server bound to {@code 0.0.0.0} is reachable as {@code localhost}, and getting
 *       that wrong makes the boundary forward a request to itself</li>
 *   <li><b>membership is never emergent</b> — a uri cannot make itself a peer by being addressed, and an undeclared
 *       authority is answered {@code noobj} rather than dialed</li>
 *   <li><b>a CONNECTION is the source of peers</b>, the known-server roster is the source of "may I reach it", and
 *       neither makes an address "mine"</li>
 * </ul>
 */
public class NetworkIdentityTest extends AbstractMetatronTest {

    /**
     * Register a space that CARRIES a host without listening on it — the shape of every backend space: a
     * tblespace's database, an mqttspace's broker. Registration writes the space at its own vid, which needs an
     * owner for the pattern — hence the BOOTING guard the other machine suites use.
     */
    private static void mountSpaceWithHost(final fURI vid, final fURI host) {
        final boolean booting = BootLoader.BOOTING;
        BootLoader.BOOTING = true;
        try {
            Machine.current().memory().addSpace(memSpace.of(rec(
                    uri(PATTERN), uri(vid.toString() + "/#"),
                    uri(HOST), uri(host.toString())), vid));
        } finally {
            BootLoader.BOOTING = booting;
        }
    }

    /**
     * Mount a real LISTENER: its server binds the host, which is the only place an authority of this machine comes
     * from. A websocket server is the network's own listener (ws_server::T), withdrawn when it closes. An http server
     * is still a listening space, patterned as the boot's are ({@code http://#}): a web space indexes a cache beside
     * itself under its RETRACTED pattern, so a {@code <vid>/#} pattern would retract onto the vid and evict the
     * listener it serves.
     */
    private static Closeable mountListener(final String scheme, final fURI host, final fURI vid) {
        if (!"http".equals(scheme))
            return wsServer.serve(host, rec());
        final Space space = httpSpace.of(rec(mutableMap(uri(PATTERN), uri(scheme + "://#"), uri(HOST), uri(host.toString()))), vid);
        assertTrue(space.listens(), "an http space listens");
        return () -> {
            unmount(space);
            space.close();
        };
    }

    /**
     * Unmount by vid AND by pattern, which is where that cache is indexed. Removing a space closes nothing — the
     * caller closes what it built.
     */
    private static void unmount(final Space space) {
        Machine.current().memory().removeSpace(space.vid());
        Machine.current().memory().removeSpace(space.pattern());
    }

    /**
     * "Mine" is a fact about what LISTENS, never a second declaration — so mounting a server is what makes this
     * machine answer on its authority. One row per kind of server a machine mounts.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "ws   % a websocket server binds its host",
            "http % and so does an http server",
    }, delimiter = '%')
    public void testAMountedListenersHostIsAnAuthority(final String scheme, final String desc) {
        final fURI host = f(scheme + "://0.0.0.0:" + generatePort());
        final fURI vid = f("/sys/space/network-identity-" + scheme);
        assertFalse(Network.Helper.authorities().contains(host), "nothing has claimed that authority yet");
        final Closeable listener = mountListener(scheme, host, vid);
        try {
            assertTrue(Network.Helper.authorities().contains(host),
                    desc + ", so its host IS an authority this machine answers on: " + Network.Helper.authorities());
        } finally {
            CommonUtil.close(listener);
        }
        assertFalse(Network.Helper.authorities().contains(host), "a closed listener answers on nothing");
    }

    /**
     * A BACKEND IS NOT MINE. A space's {@code host} is as often where it dials OUT to — a database, a broker, a graph
     * server — and that connection string carries the login: {@code boot/space/tble/mariadb.mtron} puts
     * {@code ?user=…&password=…} on it. None of it is an address this machine answers on, so none of it is an
     * authority, and discovery — which announces the authorities — never multicasts it. The scheme decides
     * nothing: the gremlin row is {@code ws://} exactly as a listener is.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "mariadb://127.0.0.1:3306/metatron?user=archivist&password=quill-and-ink % quill-and-ink % a database login rides the query",
            "mariadb://archivist:quill-and-ink@127.0.0.1:3306/metatron              % quill-and-ink % or the userinfo",
            "mariadb://archivist:7341@127.0.0.1:3306/metatron                       % 7341          % where a numeric password parses as the PORT",
            "mqtt://ginger.local:1883                                                % ginger.local  % a broker on another box is that box's",
            "ws://ginger.local:8182/gremlin                                          % ginger.local  % and a ws backend is still a backend",
    }, delimiter = '%')
    public void testABackendsHostIsNeverAnAuthority(final String host, final String forbidden, final String desc) {
        final fURI vid = f("/sys/space/network-identity-backend");
        mountSpaceWithHost(vid, f(host));
        try {
            assertFalse(Network.Helper.authorities().contains(f(host)), desc + ": " + Network.Helper.authorities());
            assertTrue(Network.Helper.authorities().stream().noneMatch(mine -> mine.toString().contains(forbidden)),
                    desc + " — and no authority carries any of it: " + Network.Helper.authorities());
        } finally {
            Machine.current().memory().removeSpace(vid);
        }
    }

    /**
     * ALIASES COLLAPSE. The boot binds the wildcard while a peer addresses loopback; if those are not one service,
     * the boundary forwards a request to itself — so this is a loop guard, not a nicety. Pinned on
     * {@link Network.Helper#sameService} itself: it is the comparison every reach decision goes through
     * ({@code peerOf}, {@code isPeer}, the ensure-peered scan).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "ws://0.0.0.0:19193 % ws://localhost:19193/x % true  % localhost and 0.0.0.0 are one service",
            "ws://0.0.0.0:19193 % ws://127.0.0.1:19193/x % true  % and so is 127.0.0.1",
            "ws://0.0.0.0:19193 % ws://[::1]:19193/x     % true  % and the IPv6 loopback",
            "ws://0.0.0.0:19193 % ws://localhost:19194/x % false % a different port is a different service",
            "ws://0.0.0.0:19193 % ws://elsewhere:19193/x % false % and a different host certainly is",
    }, delimiter = '%')
    public void testAliasesCollapseIntoOneService(final String bound, final String addressed, final boolean same,
                                                  final String desc) {
        assertEquals(same, Network.Helper.sameService(f(bound), f(addressed)), desc);
    }

    /**
     * Membership is declared, never emergent: addressing a host does not make it a peer, and an undeclared
     * authority is answered {@code noobj} — no dial, no throw. That is the fail-closed rule, behaviourally.
     */
    @Test
    public void testMembershipIsNeverEmergentAndFailsClosed() {
        final Network network = Machine.current().network();
        final fURI stranger = f("ws://never-declared:19999/x");
        assertFalse(network.isPeer(stranger), "a uri must not make itself a peer merely by being addressed");
        assertTrue(Network.Helper.authorities().stream().noneMatch(mine -> Network.Helper.sameService(mine, stranger)),
                "and it is not mine either: " + Network.Helper.authorities());
        final Obj read = network.read(stranger);
        assertTrue(read.isNoObj(), "an undeclared authority resolves to noobj rather than dialing: " + read);
    }

    /**
     * TWO QUESTIONS, TWO HOMES. The known-server roster ({@code /sys/peer}) answers "may this frame reach that
     * address at all"; the CONNECTIONS the machine holds ({@code network.peer}) are what {@link Network#peers()}
     * reports, declared and never emergent.
     * <p>
     * A DETACHED cluster is used on purpose: it has real server addresses and a real roster written by
     * {@link PeerCluster#connect()}, but no processes and no sockets, so this pins membership and identity without
     * going near the wire. Reaching a peer is the next slice, and that is what {@code NetworkTest} asserts.
     */
    @Test
    public void testPeersAreTheMachinesConnections() throws Exception {
        final Network network = Machine.current().network();
        final Obj wasConnected = network.atDirect(uri(PEER));
        assertTrue(Network.Helper.peerAuthorities().isEmpty(), "nothing is declared yet: " + Network.Helper.peerAuthorities());
        final PeerCluster cluster = PeerCluster.detached(2, "/n", 21001).connect();
        try {
            assertTrue(network.isPeer(f("ws://localhost:21001/usr/x")),
                    "a server the roster declares IS a peer this frame may dial");
            assertTrue(Network.Helper.peerAuthorities().isEmpty(),
                    "but the roster is the known servers, NOT the connections the machine holds: " + Network.Helper.peerAuthorities());
            // THE CONNECTION IS THE MACHINE'S OWN REACH, and declaring one is what peers() reports. A member sits at
            // its dial's own path and carries the service it reaches as its authority.
            network.at(uri(PEER), rec(mutableMap(
                    uri(Network.Helper.keyOf(f("ws://localhost:21001/n"))),
                    rec(mutableMap(uri(AUTHORITY), uri("ws://localhost:21001"))))), MUTABLE);
            assertEquals(Set.of(f("ws://localhost:21001")), Network.Helper.peerAuthorities(),
                    "the members' authorities ARE the peers — declared, not emergent");
            assertTrue(Network.Helper.authorities().stream()
                            .noneMatch(mine -> Network.Helper.sameService(mine, f("ws://localhost:21001"))),
                    "a declared peer is not mine: " + Network.Helper.authorities());
        } finally {
            cluster.close();
            Machine.write(Network.ROOT_ROSTER, rec(mutableMap()));
            // "no connections" is the ABSENT field, not an empty rec: writing [] as the connections map fails the
            // network's positional predicate (measured — an empty rec's key is noobj, which is not a uri::T), and
            // `at` with noobj removes the entry, which is the state a machine with nothing dialed is actually in.
            network.at(uri(PEER), wasConnected, MUTABLE);
        }
    }

    /**
     * The slot holds the COMPONENT, not a template. A deferred seed — an inst the accessor has to apply — is what
     * forced the machine's isaPredicate to admit an INST alongside a network, made the slot unresolvable while the
     * machine was being constructed (a Call needs a compiler, and the root machine has none yet), and kept the
     * network slot from reading as the roster. Memory and network are bound as themselves; nothing is applied to
     * hand them back.
     */
    @Test
    public void testComponentSlotsAreBoundNotDeferred() {
        final Machine machine = Machine.current();
        final Obj network = machine.atDirect(uri(NETWORK));
        final Obj memory = machine.atDirect(uri(MEMORY));
        final Obj compiler = machine.atDirect(uri(COMPILER));
        final Obj processor = machine.atDirect(uri(PROCESSOR));
        assertFalse(network.isInst(), "the network slot is not a template: " + network);
        assertFalse(memory.isInst(), "the memory slot is not a template: " + memory);
        assertFalse(compiler.isInst(), "the compiler slot is not a template: " + compiler);
        assertFalse(processor.isInst(), "the processor slot is not a template: " + processor);
        assertTrue(network instanceof Network, "the network slot holds the roster itself: " + network);
        assertTrue(memory instanceof Memory, "the memory slot holds the memory itself: " + memory);
        assertTrue(compiler instanceof Compiler, "the compiler slot holds the compiler itself: " + compiler);
        assertTrue(processor instanceof Processor, "the processor slot holds the processor itself: " + processor);
    }

    /**
     * The KNOWN-SERVER ROSTER is ONE SHARED ADDRESS, not per-machine state. {@code /sys/peer} is an absolute address
     * in a space a child reaches through its unioned memory, so a declaration made while standing in a child IS the
     * declaration the parent reads.
     * <p>
     * That is now the deliberate difference between the two homes: the CONNECTIONS a machine holds live on the
     * component's {@code peer} field (which is what {@code NetworkUnion} makes
     * frame-local: never inherited, never shared), while the known servers stay one list for the whole system. Pinned so the split
     * stays a decision rather than an accident.
     */
    @Test
    public void testTheRosterIsOneSharedAddressToday() {
        final Network parent = Machine.current().network();
        Machine.write(Network.ROOT_ROSTER, rec(mutableMap()));
        final Machine child = Machine.current().push(f("network-roster-scope"));
        Machine.write(Network.ROOT_ROSTER, rec(mutableMap(
                uri("ws://declared-in-child:21011"),
                rec(mutableMap(uri(AUTHORITY), uri("ws://declared-in-child:21011"))))));
        child.pop();
        try {
            assertTrue(parent.roster().jvm().keySet().stream()
                            .anyMatch(key -> key.isUri()
                                    && "ws://declared-in-child:21011".equals(key.uriValue().toString())),
                    "the roster is one shared address: a declaration made while standing in a child is the "
                            + "parent's too — so the known servers are global, not per-machine: " + parent.roster());
        } finally {
            Machine.write(Network.ROOT_ROSTER, rec(mutableMap()));
        }
    }

    /**
     * A PEER KEY IS A LEGAL PATH: the dial's scheme, every label of its host, its port and its route, one segment
     * each — no dot and no colon survives into a segment, no {@code ?q} reaches the key, and a loopback alias is
     * spelled {@code localhost}, so the aliases of one service share one member. Each row: the dial % its key.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "ws://peer:8555/mtron                % ws/peer/8555/mtron",
            "ws://peer.local:8555/mtron          % ws/peer/local/8555/mtron",
            "ws://10.0.0.7:8555/mtron            % ws/10/0/0/7/8555/mtron",
            "ws://10.0.0.8:8555/mtron            % ws/10/0/0/8/8555/mtron",
            "ws://localhost:8555/mtron?name=me   % ws/localhost/8555/mtron",
            "ws://127.0.0.1:8555/mtron           % ws/localhost/8555/mtron",
            "ws://peer:8555                      % ws/peer/8555",
            "loop://self                         % loop/self",
    }, delimiter = '%')
    public void testAPeerKeyIsALegalPath(final String dial, final String key) {
        assertEquals(f(key), Network.Helper.keyOf(f(dial)), "the key of " + dial);
    }
}
