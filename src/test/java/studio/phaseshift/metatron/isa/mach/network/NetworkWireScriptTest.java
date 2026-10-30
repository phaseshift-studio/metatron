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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.StatefulCSVSource;
import studio.phaseshift.metatron.StatefulParametrizedTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.machInstSet;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.network.Listener;
import studio.phaseshift.metatron.isa.web.space.ws.wsServer;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsServer.WS_CLIENT_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * THE LIVE-WIRE SCRIPT HARNESS. {@code NetworkIsolationScriptTest} drives the KNOWN-SERVER ROSTER (what the
 * system knows, detached — no sockets); this suite drives the CONNECTIONS (what the machine has DIALED), over
 * real websockets, in the same script form — so one wire permutation is a handful of lines, not a Java method,
 * and a large space of {op} x {transport} x {identity} x {retraction} patterns stays writable in short order.
 * <p>
 * What this suite adds to the script language is the VARIABLE: a directive that creates a dynamic fact (an
 * allocated port) binds a name, and later lines speak it as {@code {{name}}} — the lines stay static text while
 * the facts stay runtime. {@code {{ws_client}}} is pre-bound to the ws protocol client\u0027s vid.
 *
 * <pre>
 *   [WS_SERVER a]          start a ws_server on a fresh port with the /mtron route; binds {{a}} = ws://localhost:port
 *   [AWAIT_PEERS n]        wait (max 3s) until this machine holds exactly n peers
 *   [PROBE_PEERS]          /m/networkProbe/peers  = how many peers the machine holds
 *   [PROBE_Q_LEAK]         /m/networkProbe/qleak  = 1 iff any roster KEY carries a ?q processor (must stay 0)
 *   [NETWORK_CLEAR]        disconnect every peer and close every script-started server
 * </pre>
 */
//@Isolated
//@Execution(ExecutionMode.SAME_THREAD)
public class NetworkWireScriptTest extends AbstractMetatronTest {

    /**
     * where a PROBE directive leaves its answer, so a Java-side fact becomes a mtron observation
     */
    private static final fURI PROBE = f("/m/networkProbe");

    /**
     * the servers scripts started, closed after each script so no socket leaks onward
     */
    private final List<Listener> servers = new ArrayList<>();

    public NetworkWireScriptTest() {
        this.bind("ws_client", WS_CLIENT_TID.toString());
    }

    @BeforeAll
    public static void importWeb() {
        InstSet.importInstSet(WEB_ISA_TID);
        // the scripts' test store: a space must CLAIM /t/wire/# or the far side's localized read has
        // nowhere to resolve — the wire ships the address, the mount answers it.
        final boolean booting = BootLoader.BOOTING;
        BootLoader.BOOTING = true;
        try {
            Machine.current().memory().addSpace(memSpace.of(rec(
                    uri(PATTERN), uri("/t/wire/#")), f("/t/wire")));
        } finally {
            BootLoader.BOOTING = booting;
        }
    }

    private Network network() {
        return Machine.current().network();
    }

    @Override
    protected Map<String, Directive> directives() {
        return Map.of(
                "WS_SERVER", args -> this.bind(args[0], this.startServer(args[0])),
                "AWAIT_PEERS", args -> this.await("peers == " + args[0],
                        () -> this.network().peers().elements().count() == Long.parseLong(args[0])),
                "PROBE_PEERS", args -> {
                    LOG.debug("probe peers net=%s count=%s keys=%s", System.identityHashCode(this.network()), this.network().peers().elements().count(), this.network().peers().keys().toList());
                    this.probe("peers", jnt(this.network().peers().elements().count()));
                },
                "PROBE_Q_LEAK", args -> this.probe("qleak", jnt(this.network().peers().keys()
                        .map(Obj::uriValue).anyMatch(key -> !key.qMap().isEmpty()) ? 1 : 0)),
                // THE SHAPE AUDIT: walk every entry of the peer map and verify the peer::T contract —
                // uri key, q-clean key, rec value tid\u0027d peer::T, authority present, the key the dial\u0027s
                // own path, a client that declares an inst. Writes \u0027ok\u0027 or the FIRST violation\u0027s own words, so the
                // script\u0027s failure message names the broken field rather than a count.
                "PROBE_SHAPE", args -> this.probe("shape", str(this.auditPeerShape())),
                // retraction is a DIRECTIVE, deliberately not an inst: the language's retraction is the
                // match-write ==[<x> => close()] (write-to-noobj, the universal close), and until that
                // lands the scripts must not speak an inst the language will not have.
                // construction IS connection now (no connect inst exists, as no add-space inst does):
                // the directive is the java sugar for scripts, one line per dial
                "CONNECT", args -> {
                    if (args.length > 1)
                        Network.Helper.connect(this.network(), f(args[0]), f(args[1]));
                    else
                        Network.Helper.connect(this.network(), f(args[0]));
                },
                "DISCONNECT", args -> this.network().drop(f(args[0])),
                "NETWORK_CLEAR", args -> this.clearNetwork());
    }

    private String auditPeerShape() {
        final java.util.List<studio.phaseshift.metatron.isa.m.type.Rel> entries =
                this.network().peers().elements().toList();
        if (entries.size() < 2)
            return "expected 2+ peers, found " + entries.size();
        for (final studio.phaseshift.metatron.isa.m.type.Rel entry : entries) {
            final studio.phaseshift.metatron.isa.m.type.Obj key = entry.first();
            final studio.phaseshift.metatron.isa.m.type.Obj peer = entry.second();
            if (!key.isUri())
                return "key is not a uri: " + key;
            if (!key.uriValue().qMap().isEmpty())
                return "key carries a ?q processor: " + key;
            if (!peer.isRec())
                return "peer is not a rec: " + key + " => " + peer;
            if (!peer.testNominally(machInstSet.PEER_TYPE))
                return "peer is not tid\u0027d peer::T: " + key + " => " + peer.tid();
            final studio.phaseshift.metatron.isa.m.type.Obj authority = peer.asRec().at(uri(ORIGIN));
            if (!authority.isUri())
                return "peer has no authority uri: " + key;
            // the key is the dial\u0027s own address as a path, one per surface -- what pathEncoding() makes of where it was dialed
            final studio.phaseshift.metatron.isa.m.type.Obj host = peer.asRec().at(uri(HOST));
            final fURI dialed = host.isUri() ? host.uriValue() : authority.uriValue();
            if (!dialed.pathEncoding().equals(key.uriValue()))
                return "key is not the dial\u0027s path: " + key + " vs " + dialed.pathEncoding();
            final studio.phaseshift.metatron.isa.m.type.Obj client = peer.asRec().at(uri(CLIENT));
            if (!client.isRec() || !client.asRec().at(uri(INST)).isRec())
                return "peer's client declares no inst: " + key;
            final studio.phaseshift.metatron.isa.m.type.Obj name = peer.asRec().at(uri(NAME));
            if (!name.isNoObj() && !name.isStr())
                return "a named peer\u0027s name must be a str: " + key + " => " + name;
        }
        return "ok";
    }

    /**
     * Poll until something accepts on host:port. A fixed sleep assumes the ws_server is listening
     * within it, which under load it need not be — the client then reports "socket ... is not
     * connected" against a port that was never up. The probe connection is closed immediately;
     * the ws server sees a failed handshake and ignores it.
     */
    private static void waitUntilAccepting(final String host, final int port, final long timeoutMs) {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (java.net.Socket probe = new java.net.Socket()) {
                probe.connect(new java.net.InetSocketAddress(host, port), 250);
                return;
            } catch (final java.io.IOException e) {
                CommonUtil.sleepThread(25);
            }
        }
        throw new IllegalStateException("nothing accepted on " + host + ":" + port + " within " + timeoutMs + "ms");
    }

    private String startServer(final String name) {
        final int port = generatePort();
        this.bind(name + "_ip", "ws://127.0.0.1:" + port); // the loopback alias, for the same-service scripts
        this.servers.add(wsServer.of(rec(
                uri(NAME), uri("wire-script-" + name),
                uri(ORIGIN), uri("ws://127.0.0.1:" + port),
                uri(ROUTE), rec(uri("/mtron"), uri(WS_MTRON_HANDLER_TID.toString()))
        )));
        waitUntilAccepting("127.0.0.1", port, 10_000);
        return "ws://localhost:" + port;   // the CLIENT still dials the name: that is the alias under test
    }

    /**
     * THE LOOP WIRE, STAGE BY STAGE — the script's loop probe reads 0 and the strict [STATE] stays silent,
     * so connect answers noobj somewhere in the dial chain. Each stage asserts by NAME, so the failure
     * says which link broke instead of showing its shadow three lines later.
     */
    @Test
    public void testLoopStages() {
        final studio.phaseshift.metatron.isa.m.type.Obj idc = Machine.read(machInstSet.ID_CLIENT_TID);
        org.junit.jupiter.api.Assertions.assertTrue(idc.isType() && idc.asType().hasConstructor(),
                "stage 1 — id_client::T must resolve to a type with a constructor, got: " + idc);
        final studio.phaseshift.metatron.isa.m.type.Obj dialed = Network.Helper.dial(f("loop://self"), null);
        org.junit.jupiter.api.Assertions.assertTrue(!dialed.isNoObj() && !dialed.isFail(),
                "stage 2 — the loop dial must construct a client, got: " + dialed);
        final studio.phaseshift.metatron.isa.m.type.Obj peer = Network.Helper.connect(this.network(), f("loop://self"));
        org.junit.jupiter.api.Assertions.assertTrue(peer.isRec(), "stage 3 — java connect must mint, got: " + peer);
        org.junit.jupiter.api.Assertions.assertEquals(1L, this.network().peers().elements().count(),
                "stage 3b — the live network holds the java-minted peer");
        this.network().drop(f("loop://self"));
        // stage 4: construction IS connection — the ctor runs and, as a background behavior of a valid
        // construction, the client indexes itself into the network (the space model: no add-space inst)
        final studio.phaseshift.metatron.isa.m.type.Obj viaCtor = idc.asType().constructor().apply(
                rec(uri(HOST), uri(f("loop://self"))));
        org.junit.jupiter.api.Assertions.assertTrue(viaCtor.isRec(),
                "stage 4 — the ctor must answer the client, got: " + viaCtor);
        org.junit.jupiter.api.Assertions.assertEquals(1L, this.network().peers().elements().count(),
                "stage 4b — and the valid construction self-indexed into the LIVE network");
    }

    /**
     * THE WS WIRE, JAVA ONLY — NetworkWsDialTest's green path replayed inside THIS suite's boot (plain
     * AbstractMetatronTest + importWeb, no integration base). Green here and red in the script isolates
     * the mtron layer; red here isolates the boot environment.
     */
    @Test
    public void testWsStagesJava() {
        final String a = this.startServer("stages");
        final studio.phaseshift.metatron.isa.m.type.Obj peer =
                Network.Helper.connect(this.network(), f(a + "/mtron"), WS_CLIENT_TID);
        org.junit.jupiter.api.Assertions.assertTrue(peer.isRec(), "java ws connect must mint, got: " + peer);
        Machine.write(f("/t/wire/stage"), jnt(42));
        final studio.phaseshift.metatron.isa.m.type.Obj answer = Machine.read(f(a + "/t/wire/stage"));
        org.junit.jupiter.api.Assertions.assertEquals(jnt(42), answer,
                "java ws read-through must answer the far side's local read");
    }

    @AfterEach
    public void cleanup() {
        this.clearNetwork();
    }

    private void clearNetwork() {
        this.network().peers().keys().map(Obj::uriValue).toList()
                .forEach(key -> this.network().drop(key));
        this.servers.forEach(CommonUtil::close);
        this.servers.clear();
    }

    private void probe(final String name, final Obj value) {
        Machine.write(PROBE.extend(name), value);
    }

    /**
     * A bounded wait for a peer-count condition — a script line may assert on a count the connection\u0027s
     * async close/reach settlement is still catching up to, so it waits rather than racing, and fails with
     * the condition\u0027s own words rather than a timeout stack.
     */
    private void await(final String what, final java.util.function.BooleanSupplier condition) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean())
                return;
            Thread.sleep(50);
        }
        fail("timed out awaiting " + what);
    }

    /**
     * THE IDENTITY CLIENT: dialing loop: constructs an id_client, and a read or write through the loop peer
     * IS the local operation — the executable form of the self-similarity contract, with no socket.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[CONNECT loop://self]",
            "[PROBE_PEERS]",
            "*/m/networkProbe/peers % 1                     [-- the identity peer minted --]",
            "[STATE] </t/wire/a> -> 42",
            "*<loop://self/t/wire/a> % 42                   [-- a read through the loop peer IS the local read --]",
            "[STATE] <loop://self/t/wire/b> -> 7",
            "*/t/wire/b % 7                                  [-- and a write through it lands locally --]",
            "[DISCONNECT loop://self]",
            "[PROBE_PEERS]",
            "*/m/networkProbe/peers % 0                     [-- disconnect retracts exactly what was dialed --]"
    })
    public void testLoopIdentity() {
    }

    /**
     * THE WIRE: the loop equalities carried over a real websocket — read-through equals the far side\u0027s local
     * read, write-through lands at the far side — plus the two facts the connection itself establishes: the
     * connection q is spent (never leaked into a key), and connect is ensure-peered (a second dial
     * of the same service answers the existing peer, never a second socket).
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[CONNECT {{a}}/mtron?clientq=scripted {{ws_client}}]",
            "[STATE] </t/wire/x> -> 42",
            "*<{{a}}/t/wire/x> % 42                          [-- read through the wire == the far side local read --]",
            "[STATE] <{{a}}/t/wire/y> -> 7",
            "*/t/wire/y % 7                                   [-- write through the wire lands at the far side --]",
            "[AWAIT_PEERS 1]                                     [-- the sole outbound peer: the loopback listener declines its co-mount --]",
            "[PROBE_Q_LEAK]",
            "*/m/networkProbe/qleak % 0                      [-- the processor was spent, never leaked into a key --]",
            "[CONNECT {{a}}/mtron {{ws_client}}]",
            "[PROBE_PEERS]",
            "*/m/networkProbe/peers % 1                      [-- ensure-peered: the re-dial answered the existing peer --]",
            "[DISCONNECT {{a}}]",
            "[AWAIT_PEERS 0]                                     [-- hang-up retracts the dial --]"
    })
    public void testWsWire() {
    }

    /**
     * TWO SERVERS, ONE MACHINE: each dial mints its own peer, the reaches are independent, and retraction is
     * per-service — closing one leaves the other\u0027s peer standing.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[WS_SERVER b]",
            "[CONNECT {{a}}/mtron?clientq=alpha {{ws_client}}]",
            "[CONNECT {{b}}/mtron?clientq=beta {{ws_client}}]",
            "[AWAIT_PEERS 2]                                     [-- two dialed --]",
            "[STATE] </t/wire/two> -> 11",
            "*<{{a}}/t/wire/two> % 11",
            "*<{{b}}/t/wire/two> % 11                        [-- both wires reach the same far-side store --]",
            "[DISCONNECT {{a}}]",
            "[AWAIT_PEERS 1]                                     [-- retraction is per-service: b\u0027s peer stands --]"
    })
    public void testTwoServers() {
    }

    /**
     * VALUE SHAPES OVER THE WIRE: every type the store holds must survive the trip — ints, strs, bools,
     * lsts, recs — and the far side\u0027s store must stay a SPACE through the wire: a rec written whole is
     * field-addressable, an overwrite wins, writing noobj removes (the universal close, over the wire),
     * and an absent path answers noobj rather than inventing.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[CONNECT {{a}}/mtron {{ws_client}}]",
            "<{{a}}/t/wire/i> -> 42                           % 42",
            "*<{{a}}/t/wire/i> % 42",
            "<{{a}}/t/wire/s> -> 'hello'                      % 'hello'",
            "*<{{a}}/t/wire/s> % 'hello'",
            "<{{a}}/t/wire/b> -> true                         % true",
            "*<{{a}}/t/wire/b> % true",
            "<{{a}}/t/wire/l> -> [1,2,3]                      % [1,2,3]",
            "*<{{a}}/t/wire/l> % [1,2,3]",
            "<{{a}}/t/wire/r> -> [x=>1,y=>2]                  % [x=>1,y=>2]",
            "*<{{a}}/t/wire/r/x> % 1                          [-- a rec written whole is field-addressable through the wire --]",
            "<{{a}}/t/wire/r/y> -> 9                          % 9",
            "*/t/wire/r/y % 9                                  [-- a field written through the wire lands in the same map --]",
            "<{{a}}/t/wire/i> -> 43                           % 43",
            "*<{{a}}/t/wire/i> % 43                           [-- an overwrite wins --]",
            "[STATE] <{{a}}/t/wire/i> -> noobj",
            "*<{{a}}/t/wire/i> % noobj                        [-- writing noobj removes: the universal close, over the wire --]",
            "*<{{a}}/t/wire/never-written> % noobj            [-- an absent path answers nothing rather than inventing --]"
    })
    public void testWireValueShapes() {
    }

    /**
     * THE SELF-SIMILARITY CONTRACT AS ONE LINE: with the identity peer and a real ws peer standing side by
     * side on the same machine, a read through EITHER equals the local read — stated with the script\u0027s
     * 3-column equality, which is the contract\u0027s own shape: lhs % rhs % true.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[CONNECT loop://self]",
            "[CONNECT {{a}}/mtron {{ws_client}}]",
            "[STATE] </t/wire/e> -> 42",
            "*<loop://self/t/wire/e> % */t/wire/e % true       [-- loop read == local read --]",
            "*<{{a}}/t/wire/e> % */t/wire/e % true             [-- ws read == local read --]",
            "*<loop://self/t/wire/e> % *<{{a}}/t/wire/e> % true  [-- hence loop == ws: transport is invisible to value --]",
            "[DISCONNECT loop://self]",
            "[DISCONNECT {{a}}]",
            "[AWAIT_PEERS 0]"
    })
    public void testTransportIsInvisibleToValue() {
    }

    /**
     * ALIASING AT THE BOUNDARY: localhost and 127.0.0.1 are the SAME service (ensure-peered collapses
     * loopback aliases — one socket, not two), and a disconnect-reconnect cycle mints a FRESH reach that works.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[CONNECT {{a}}/mtron {{ws_client}}]",
            "[CONNECT {{a_ip}}/mtron {{ws_client}}]",
            "[PROBE_PEERS]",
            "*/m/networkProbe/peers % 1                       [-- 127.0.0.1 IS localhost: the alias collapsed to the existing peer --]",
            "[DISCONNECT {{a}}]",
            "[AWAIT_PEERS 0]",
            "[CONNECT {{a}}/mtron {{ws_client}}]",
            "[STATE] </t/wire/again> -> 7",
            "*<{{a}}/t/wire/again> % 7                        [-- a reconnect after hang-up is a fresh, working reach --]"
    })
    public void testAliasingAtTheBoundary() {
    }


    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[WS_SERVER b]",
            "[CONNECT loop://self]",
            "[CONNECT {{a}}/mtron?clientq=alpha {{ws_client}}]",
            "[CONNECT {{b}}/mtron?clientq=beta {{ws_client}}]",
            "[AWAIT_PEERS 3]                                     [-- loop + two dialed --]",
            "[PROBE_SHAPE]",
            "*/m/networkProbe/shape % \u0027ok\u0027              [-- every entry honors the peer::T contract --]",
            "[DISCONNECT loop://self]",
            "[PROBE_SHAPE]",
            "*/m/networkProbe/shape % \u0027ok\u0027              [-- and the shape survives a retraction --]"
    })
    public void testNetworkStructureUnderLoad() {
    }

    /**
     * THE IDIOMATIC DIAL IS A CONSTRUCTION — ws_client::[pattern=>...] — and indexing is the background
     * behavior of a valid ctor, exactly as a space indexes into Memory. No connect inst exists, as no
     * add-space inst does. This script speaks the construction in mtron; if the bare type name does not
     * resolve in this boot, the failure is a resolution fact worth knowing, not a wire fact.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "[WS_SERVER a]",
            "[STATE] ws_client::[pattern=><{{a}}/mtron?clientq=ctor>]",
            "[AWAIT_PEERS 1]                                     [-- the ctor indexed itself: the one outbound peer --]",
            "[STATE] </t/wire/c> -> 42",
            "*<{{a}}/t/wire/c> % 42                          [-- and the self-indexed reach carries reads --]"
    })
    public void testConstructionIsConnection() {
    }

    /**
     * NOTHING IS EMERGENT ON THE WIRE EITHER: an undialed scheme answers noobj and mints nothing, and a dial to
     * a scheme with no default dialer and no handler answers noobj too — reach is always explicit.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[NETWORK_CLEAR]",
            "*<ws://never-dialed:19999/t/x> % noobj          [-- addressing an undialed authority answers nothing --]",
            "[PROBE_PEERS]",
            "*/m/networkProbe/peers % 0                      [-- and mints nothing --]",
            "[CONNECT xyz://unknown:1/x]",
            "[PROBE_PEERS]",
            "*/m/networkProbe/peers % 0                      [-- a scheme with no default dialer declines the dial --]"
    })
    public void testNothingIsEmergent() {
    }
}
