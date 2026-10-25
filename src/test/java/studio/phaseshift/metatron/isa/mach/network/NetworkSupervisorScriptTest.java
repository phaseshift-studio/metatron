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

import studio.phaseshift.metatron.isa.mach.type.network.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.StatefulCSVSource;
import studio.phaseshift.metatron.StatefulParametrizedTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.network.NetworkSupervisor;
import studio.phaseshift.metatron.isa.web.space.ws.wsServer;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.fail;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * PEER SUPERVISION AND ADDRESSING, OVER LIVE SOCKETS — connection on creation, destruction on deletion, and everything
 * between kept true. A member sits at its dial's own path ({@code ~/network/peer/ws/localhost/<port>/mtron}), one per
 * surface: it is read and deleted there, and dialing the same surface again replaces it. {@code NetworkWireScriptTest} pins what a dialed connection CARRIES; this suite pins what happens to
 * it when it breaks: a far side that goes away is a failure (the peer stays listed, offline, and is redialed in
 * place), and only the owner's hang-up — the client closed or deleted, the frame popped — retracts it.
 * <p>
 * The far side is this JVM's own machine behind a real websocket, so a server can be stopped and started again ON
 * THE SAME PORT — which is exactly a peer restarting.
 *
 * <pre>
 *   [RESERVE a]             allocate a port with nothing listening yet; binds {{a}} = ws://localhost:port, {{a_port}}
 *   [WS_SERVER a]           RESERVE a, then serve /mtron and /eval (two surfaces of one service) on it
 *   [WS_SERVER_STOP a]      close a's server: the far side goes away
 *   [WS_SERVER_START a]     serve /mtron on a's port again: the far side comes back
 *   [AWAIT_PEERS n]         wait until this machine holds exactly n peers
 *   [AWAIT_ONLINE uri]      wait until the peer for uri's service is online (a redial has the backoff cap to land)
 *   [AWAIT_OFFLINE uri]     wait until the peer for uri's service is listed and offline
 *   [HANG_UP uri]           the owner's close of that peer's client
 *   [HOLD uri name]         keep that peer's client (and this level) under a name, to watch it from elsewhere
 *   [PROBE_PEERS]           /m/networkProbe/peers      = how many peers this machine holds
 *   [PROBE_ONLINE uri]      /m/networkProbe/online     = is the peer for uri online
 *   [PROBE_HELD name]       /m/networkProbe/connected, hungup, supervised = the held client's link and its level
 *   [PROBE_STATUS]          /m/networkProbe/status     = the kind the status method reports for the first peer
 *   [NETWORK_CLEAR]         disconnect every peer and close every server
 * </pre>
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class NetworkSupervisorScriptTest extends AbstractMetatronTest {

    private static final fURI PROBE = f("/m/networkProbe");
    /**
     * longer than the backoff cap, so an awaited redial is always given its next attempt
     */
    private static final long AWAIT_LINK_MS = NetworkSupervisor.BACKOFF_MAX_MS + 5_000L;

    private final Map<String, Integer> ports = new LinkedHashMap<>();
    private final Map<String, Listener> servers = new LinkedHashMap<>();
    private final Map<String, Obj> held = new LinkedHashMap<>();
    private final Map<String, Network> heldLevels = new LinkedHashMap<>();
    /**
     * a fresh space vid for every server start, so a restart never collides with the one it replaces
     */
    private int generation = 0;

    @BeforeAll
    public static void importWeb() {
        InstSet.importInstSet(WEB_ISA_TID);
        // the scripts' store: a space must CLAIM /t/peer/# or the far side's localized read has nowhere to resolve
        final boolean booting = BootLoader.BOOTING;
        BootLoader.BOOTING = true;
        try {
            Machine.current().memory().addSpace(memSpace.of(rec(uri(PATTERN), uri("/t/peer/#")), f("/t/peer")));
        } finally {
            BootLoader.BOOTING = booting;
        }
    }

    private Network network() {
        return Machine.current().network();
    }

    private Obj peer(final String address) {
        return this.network().peerOf(Network.Helper.serviceOf(f(address)));
    }

    private Obj client(final String address) {
        final Obj peer = this.peer(address);
        if (!peer.isRec())
            fail("no peer for " + address + " among " + this.network().peers());
        return peer.asRec().atDirect(uri(CLIENT));
    }

    @Override
    protected Map<String, Directive> directives() {
        final Map<String, Directive> all = new LinkedHashMap<>();
        all.put("RESERVE", args -> this.reserve(args[0]));
        all.put("WS_SERVER", args -> {
            this.reserve(args[0]);
            this.start(args[0]);
        });
        all.put("WS_SERVER_STOP", args -> CommonUtil.close(this.servers.remove(args[0])));
        all.put("WS_SERVER_START", args -> this.start(args[0]));
        all.put("AWAIT_PEERS", args -> this.await("peers == " + args[0], 5_000L,
                () -> this.network().peers().elements().count() == Long.parseLong(args[0])));
        all.put("AWAIT_ONLINE", args -> this.await(args[0] + " online", AWAIT_LINK_MS,
                () -> Network.Helper.online(this.peer(args[0]))));
        all.put("AWAIT_OFFLINE", args -> this.await(args[0] + " offline", AWAIT_LINK_MS,
                () -> this.peer(args[0]).isRec() && !Network.Helper.online(this.peer(args[0]))));
        all.put("HANG_UP", args -> CommonUtil.close(this.client(args[0])));
        all.put("HOLD", args -> {
            this.held.put(args[1], this.client(args[0]));
            this.heldLevels.put(args[1], this.network());
        });
        all.put("PROBE_PEERS", args -> this.probe("peers", jnt(this.network().peers().elements().count())));
        all.put("PROBE_ONLINE", args -> this.probe("online", bool(Network.Helper.online(this.peer(args[0])))));
        all.put("PROBE_HELD", args -> {
            final Network.Link link = (Network.Link) this.held.get(args[0]);
            this.probe("connected", bool(link.connected()));
            this.probe("hungup", bool(link.hungUp()));
            // supervised = the level's filed supervisor rec is live (~/network/supervisor), not the static registry
            this.probe("supervised", bool(this.heldLevels.get(args[0]).at(uri(SUPERVISOR)) instanceof NetworkSupervisor supervisor && supervisor.supervising()));
        });
        // the status METHOD, applied to this level the way `*/network/status` applies it
        all.put("PROBE_STATUS", args -> {
            final Obj first = this.network().status(this.network()).stream().findFirst().orElse(noobj());
            final Obj status = first.isRec() ? first.asRec().at(uri(STATUS)) : noobj();
            this.probe("status", status.isRec() ? status.asRec().at(uri(KIND)) : noobj());
        });
        all.put("NETWORK_CLEAR", args -> this.clear());
        return all;
    }

    private void reserve(final String name) {
        final int port = generatePort();
        this.ports.put(name, port);
        this.bind(name, "ws://localhost:" + port);
        this.bind(name + "_port", String.valueOf(port));
    }

    private void start(final String name) {
        final int port = this.ports.get(name);
        this.servers.put(name, wsServer.of(rec(
                uri(NAME), uri("supervision-" + name),
                uri(HOST), uri("ws://localhost:" + port),
                // two surfaces of one service, so a script can dial both and see two members
                uri(ROUTE), rec(uri("/mtron"), uri(WS_MTRON_HANDLER_TID.toString()),
                        uri("/eval"), uri(WS_MTRON_HANDLER_TID.toString()))
        )));
        CommonUtil.sleepThread(300);
    }

    private void clear() {
        this.network().peers().keys().map(Obj::uriValue).toList().forEach(key -> this.network().disconnect(key));
        this.servers.values().forEach(CommonUtil::close);
        this.servers.clear();
        this.held.clear();
        this.heldLevels.clear();
    }

    @AfterEach
    public void cleanup() {
        // a script that failed inside a pushed frame must not leave the next one standing there
        this.resetToRoot();
        this.clear();
    }

    private void probe(final String name, final Obj value) {
        Machine.write(PROBE.extend(name), value);
    }

    /**
     * A bounded wait for a condition a redial or a close is still settling into — the script waits rather than
     * racing, and fails in the condition's own words rather than with a timeout stack.
     */
    private void await(final String what, final long ms, final BooleanSupplier condition) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean())
                return;
            Thread.sleep(50);
        }
        fail("timed out awaiting " + what + " (peers " + this.network().peers() + ")");
    }

    /**
     * A FAR SIDE THAT GOES AWAY IS A FAILURE, NOT A HANG-UP: the peer stays listed, offline, while the supervisor
     * redials it — and when the far side comes back on the same port the same one peer is online again, carrying
     * reads, with no second socket and nothing re-declared.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[STATE] ws_client::[pattern=><{{a}}/mtron?name=survivor>]",
            "[AWAIT_PEERS 1]",
            "[STATE] </t/peer/x> -> 42",
            "*<{{a}}/t/peer/x> % 42                        [-- the wire answers --]",
            "[WS_SERVER_STOP a]",
            "[AWAIT_OFFLINE {{a}}]                         [-- the far side went away: the link is down --]",
            "[PROBE_PEERS]",
            "*/m/networkProbe/peers % 1                    [-- a failure, not a hang-up: the peer stays listed --]",
            "[WS_SERVER_START a]",
            "[AWAIT_ONLINE {{a}}]                          [-- the supervisor redialed it, in place --]",
            "[PROBE_PEERS]",
            "*/m/networkProbe/peers % 1                    [-- the same one peer, never a second --]",
            "*<{{a}}/t/peer/x> % 42                        [-- and the wire answers again --]"
    })
    public void testAFarSideRestartIsRedialed() {
    }

    /**
     * A REQUEST DOES NOT WAIT FOR THE BACKOFF. While the far side is down a request fails fast (its on-demand redial
     * is refused); the first request after the far side is back redials on demand and goes out on the connection
     * that answers.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[STATE] ws_client::[pattern=><{{a}}/mtron?name=eager>]",
            "[AWAIT_PEERS 1]",
            "[STATE] </t/peer/y> -> 7",
            "[WS_SERVER_STOP a]",
            "[AWAIT_OFFLINE {{a}}]",
            "*<{{a}}/t/peer/y>.catch('down') % 'down'      [-- the far side is down: the request fails, and fast --]",
            "[WS_SERVER_START a]",
            "*<{{a}}/t/peer/y> % 7                         [-- the first request after the restart redials on demand --]"
    })
    public void testTheFirstRequestAfterARestartRedials() {
    }

    /**
     * A DIAL THAT DID NOT TAKE IS STILL A DECLARED PEER. Constructing a client before its far side is up indexes an
     * offline peer, and the peer comes online when the far side does — construction is connection, eventually.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[RESERVE a]                                   [-- a port with nothing behind it yet --]",
            "[STATE] ws_client::[pattern=><{{a}}/mtron?name=early>]",
            "[AWAIT_PEERS 1]                               [-- declared, though nothing answered --]",
            "[PROBE_ONLINE {{a}}]",
            "*/m/networkProbe/online % false",
            "[WS_SERVER_START a]",
            "[AWAIT_ONLINE {{a}}]                          [-- it came up when the far side did --]",
            "[STATE] </t/peer/z> -> 'late'",
            "*<{{a}}/t/peer/z> % 'late'"
    })
    public void testADialBeforeTheFarSideIsUpComesOnline() {
    }

    /**
     * DESTRUCTION ON DELETION. A client held at an address is deleted there, close-before-clear closes it, and the
     * owner's close retracts its peer at once — no supervisor tick, no dead member left behind.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[STATE] </t/peer/handle> -> ws_client::[pattern=><{{a}}/mtron?name=owned>]",
            "[AWAIT_PEERS 1]",
            "[STATE] </t/peer/handle> -> noobj              [-- deleting the handle closes the client --]",
            "[PROBE_PEERS]",
            "*/m/networkProbe/peers % 0                    [-- and the owner's hang-up took its peer out of the map --]"
    })
    public void testDeletingTheClientRetractsItsPeer() {
    }

    /**
     * A HANG-UP IS FINAL, AND A FRESH DIAL IS FRESH. The owner's close drops the peer; constructing a client for the
     * same service afterwards is a new connection with a new peer, not the dead one handed back.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[STATE] ws_client::[pattern=><{{a}}/mtron?name=first>]",
            "[AWAIT_PEERS 1]",
            "[HANG_UP {{a}}]                               [-- the owner closes the client --]",
            "[PROBE_PEERS]",
            "*/m/networkProbe/peers % 0                    [-- an explicit shutdown removes the peer --]",
            "[STATE] ws_client::[pattern=><{{a}}/mtron?name=second>]",
            "[AWAIT_PEERS 1]                               [-- a fresh construction is a fresh connection --]",
            "[PROBE_ONLINE {{a}}]",
            "*/m/networkProbe/online % true",
            "[STATE] </t/peer/w> -> 3",
            "*<{{a}}/t/peer/w> % 3"
    })
    public void testAHangUpIsFinalAndAFreshDialIsFresh() {
    }

    /**
     * A FRAME'S REACH ENDS WITH THE FRAME. A client dialed standing in a pushed machine belongs to that machine's
     * level; the pop hangs it up as a retraction (so nothing redials it) and stops the level's supervision.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[STATE] *~.push(dialer)",
            "[STATE] ws_client::[pattern=><{{a}}/mtron?name=framed>]",
            "[AWAIT_PEERS 1]                               [-- the dial is the child frame's own reach --]",
            "[HOLD {{a}} framed]",
            "[PROBE_HELD framed]",
            "*/m/networkProbe/supervised % true            [-- the child's level is supervised --]",
            "[STATE] *~.pop(_)",
            "[PROBE_PEERS]",
            "*/m/networkProbe/peers % 0                    [-- the root never held it --]",
            "[PROBE_HELD framed]",
            "*/m/networkProbe/connected % false            [-- the pop hung the child's connection up --]",
            "*/m/networkProbe/hungup % true                [-- as a retraction, so nothing will redial it --]",
            "*/m/networkProbe/supervised % false           [-- and the level's supervision ended with it --]"
    })
    public void testAPopHangsUpTheFramesDials() {
    }

    /**
     * THE STATUS METHOD REPORTS THE LINK. A dialed peer is probed through the transport its peer::T carries (it was
     * reported `known`, the rec mistaken for a transport), and a link that is down reports down without a trip.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[STATE] ws_client::[pattern=><{{a}}/mtron?name=reported>]",
            "[AWAIT_PEERS 1]",
            "[PROBE_STATUS]",
            "*/m/networkProbe/status % 'up'                [-- the dialed peer answered the probe --]",
            "[WS_SERVER_STOP a]",
            "[AWAIT_OFFLINE {{a}}]",
            "[PROBE_STATUS]",
            "*/m/networkProbe/status % 'down'              [-- and a link that is down reports down --]"
    })
    public void testTheStatusMethodReportsTheLink() {
    }

    /**
     * A PEER SITS AT ITS DIAL'S OWN PATH. The key is the dial uri as a path, one per surface — scheme, host, port,
     * route, no {@code ?q} — so the member is addressable at {@code /network/peer/...}, field by field.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[STATE] ws_client::[pattern=><{{a}}/mtron?name=addressed>]",
            "[AWAIT_PEERS 1]",
            "*</network/peer/ws/localhost/{{a_port}}/mtron/authority> % <{{a}}>   [-- the dial's own path names the member --]",
            "*</network/peer/ws/localhost/{{a_port}}/mtron/host> % <{{a}}/mtron?name=addressed> [-- the dial, identity included, is its host --]"
    })
    public void testAPeerIsAddressableAtItsDialsPath() {
    }

    /**
     * DESTRUCTION ON DELETION, AT THE PEER'S OWN ADDRESS: writing noobj there is the owner's hang-up — the member
     * leaves the map and its client is closed for good.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[STATE] ws_client::[pattern=><{{a}}/mtron?name=deleted>]",
            "[AWAIT_PEERS 1]",
            "[HOLD {{a}} deleted]",
            "[STATE] </network/peer/ws/localhost/{{a_port}}/mtron> -> noobj",
            "[PROBE_PEERS]",
            "*/m/networkProbe/peers % 0                    [-- the member left the map --]",
            "[PROBE_HELD deleted]",
            "*/m/networkProbe/connected % false            [-- its connection is closed --]",
            "*/m/networkProbe/hungup % true                [-- as the owner's hang-up: nothing will redial it --]"
    })
    public void testDeletingThePeersAddressHangsItUp() {
    }

    /**
     * NO TWO EQUIVALENT CONNECTIONS. Dialing the same surface again closes the connection the level held and the
     * new one takes its place — the identity a dial announces is not part of the surface.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[STATE] ws_client::[pattern=><{{a}}/mtron?name=first>]",
            "[AWAIT_PEERS 1]",
            "[HOLD {{a}} first]",
            "[STATE] ws_client::[pattern=><{{a}}/mtron?name=second>]",
            "[PROBE_PEERS]",
            "*/m/networkProbe/peers % 1                    [-- one member for the surface, never two --]",
            "[PROBE_HELD first]",
            "*/m/networkProbe/connected % false            [-- the connection it replaced was closed --]",
            "[STATE] </t/peer/r> -> 5",
            "*<{{a}}/t/peer/r> % 5                         [-- and the new one carries the wire --]"
    })
    public void testARedialOfTheSameSurfaceReplacesIt() {
    }

    /**
     * ONE MEMBER PER SURFACE: two routes of one service are two members, side by side, and each is retracted on
     * its own.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[STATE] ws_client::[pattern=><{{a}}/mtron?name=one>]",
            "[STATE] ws_client::[pattern=><{{a}}/eval?name=two>]",
            "[AWAIT_PEERS 2]                               [-- the two surfaces of one service coexist --]",
            "[STATE] </network/peer/ws/localhost/{{a_port}}/eval> -> noobj",
            "[AWAIT_PEERS 1]                               [-- deleting one leaves the other --]",
            "*</network/peer/ws/localhost/{{a_port}}/mtron/authority> % <{{a}}>",
            "[STATE] </t/peer/s> -> 'surface'",
            "*<{{a}}/t/peer/s> % 'surface'                 [-- and the wire still reaches the service --]"
    })
    public void testTwoSurfacesOfOneServiceAreTwoPeers() {
    }

    /**
     * A FRAME'S MEMBERS SIT AT ITS OWN PATH. {@code ~} is the frame's own reach; the root's map never held them.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[STATE] *~.push(dialer)",
            "[STATE] ws_client::[pattern=><{{a}}/mtron?name=framed>]",
            "[AWAIT_PEERS 1]",
            "*<~/network/peer/ws/localhost/{{a_port}}/mtron/authority> % <{{a}}>   [-- the frame's own member --]",
            "*</network/peer/ws/localhost/{{a_port}}/mtron/authority> % noobj      [-- the root never held it --]",
            "[STATE] *~.pop(_)"
    })
    public void testAFramesPeersSitAtItsOwnPath() {
    }

    /**
     * CONSTRUCTION IS CONNECTION. {@code network_supervisor::[=>]} mints the level's supervisor, files it at
     * {@code ~/network/supervisor}, and its {@code status} reads {@code supervising} — a first-class rec, not a
     * sidecar.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[STATE] network_supervisor::[=>]",
            "*<~/network/supervisor/status> % <supervising>   [-- construction filed it, supervising --]"
    })
    public void testAConstructionFilesTheSupervisor() {
    }

    /**
     * A DIAL FILES THE SUPERVISOR. Indexing a link starts the supervision, which mints and files the supervisor —
     * the address is live from the first connection, not only from an explicit construction.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[STATE] ws_client::[pattern=><{{a}}/mtron?name=supervised>]",
            "[AWAIT_PEERS 1]",
            "*<~/network/supervisor/status> % <supervising>   [-- the dial's supervision filed it --]"
    })
    public void testADialFilesTheSupervisor() {
    }
}
