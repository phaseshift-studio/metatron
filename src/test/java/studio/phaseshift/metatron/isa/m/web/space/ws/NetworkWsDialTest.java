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

package studio.phaseshift.metatron.isa.m.web.space.ws;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.mach.type.network.Listener;
import studio.phaseshift.metatron.isa.m.mach.type.thread.FutureObj;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.PEER_TID;
import static studio.phaseshift.metatron.isa.m.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;
import static studio.phaseshift.metatron.isa.m.web.space.ws.wsServer.WS_CLIENT_TID;

/**
 * NETWORK CONNECT OVER A REAL WIRE — the loop test's equality carried one transport out: the dial
 * constructs a {@code ws_client} (the client type's constructor IS the dialer), its mini-instset
 * becomes the peer's reach, and a read through the peer must answer exactly what the far side's
 * local read answers. The far side here is this same JVM's machine behind a real websocket, so the
 * equality is checkable without a second process.
 */
//@Isolated
//@Execution(ExecutionMode.SAME_THREAD)
public class NetworkWsDialTest extends AbstractWebSocketServerIntegrationTest {

    @Override
    protected Listener createWSServer() {
        return wsServer.of(rec(
                uri(NAME), uri("network-dial-test"),
                uri(ORIGIN), uri("ws://localhost:" + generatePort()),
                uri(ROUTE), rec(uri("/mtron"), uri(WS_MTRON_HANDLER_TID.toString()))));
    }

    private studio.phaseshift.metatron.furi.fURI dialURI() {
        return f("ws://localhost:" + this.wsPort + "/mtron");
    }

    private studio.phaseshift.metatron.furi.fURI service() {
        return f("ws://localhost:" + this.wsPort);
    }

    @AfterEach
    void retractPeer() throws Exception {
        final Network network = Machine.current().network();
        network.drop(this.service());
        // the dial's close reaches the listener async; wait for the connection to settle so
        // each test starts with an empty reach
        final long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline && network.peers().elements().count() > 0)
            Thread.sleep(50);
    }

    @Test
    void testWsConnectMintsPeerWithClient() {
        final Obj peer = Network.Helper.connect(Machine.current().network(), this.dialURI(), WS_CLIENT_TID);
        assertEquals(PEER_TID, peer.tid().basePath(), "connect must answer a peer::T");
        assertEquals(this.service(), peer.asRec().<Obj>at(uri(ORIGIN)).uriValue(),
                "the peer keys on the service authority — the route stays on host");
        assertEquals(this.dialURI(), peer.asRec().<Obj>at(uri(HOST)).uriValue(),
                "host remembers where it was dialed, route included — peer::T\u0027s own words");
        assertTrue(peer.asRec().atDirect(uri("transport")).isNoObj(), "a peer::T carries no transport: it is talked to with its client's inst");
        assertTrue(Network.Helper.status(peer.asRec().at(uri(CLIENT))), "the ws client is live while its socket is open");
        assertEquals(true, peer.asRec().<Obj>at(uri(CLIENT)).isRec(), "the client is retained for the hang-up");
    }

    @Test
    void testAHungUpWsClientReadsNotLive() {
        final Obj client = Network.Helper.connect(Machine.current().network(), this.dialURI(), WS_CLIENT_TID).asRec().at(uri(CLIENT));
        assertTrue(Network.Helper.status(client), "live while its socket is open");
        // the status is derived on read — the socket's own state — so the very client reads false once it is hung up,
        // with no field to update
        Machine.current().network().drop(this.service());
        assertFalse(Network.Helper.status(client), "the same client reads not live once its socket is closed");
    }

    @Test
    void testWsReadIsTheFarSidesLocalRead() {
        Machine.write(f("/t/wire/x"), jnt(42));
        Network.Helper.connect(Machine.current().network(), this.dialURI(), WS_CLIENT_TID);
        assertEquals(jnt(42), Machine.read(f("ws://localhost:" + wsPort + "/t/wire/x")),
                "the loop contract over a real wire: a read through a ws peer answers the far side\u0027s local read");
    }

    @Test
    void testWsWriteLandsAtTheFarSide() {
        Network.Helper.connect(Machine.current().network(), this.dialURI(), WS_CLIENT_TID);
        Machine.write(f("ws://localhost:" + wsPort + "/t/wire/y"), jnt(7));
        assertEquals(jnt(7), Machine.read(f("/t/wire/y")), "a write through the peer lands at the far side");
    }

    @Test
    void testWsClientDeclaresEvalFromAndTo() {
        Network.Helper.connect(Machine.current().network(), this.dialURI(), WS_CLIENT_TID);
        final Network network = Machine.current().network();
        // the ws client's mini-instset: each instruction on its native signature with the client's own wire body
        for (final fURI instTID : List.of(EVAL_INST_TID, FROM_INST_TID, TO_INST_TID)) {
            final Obj override = network.overrideOf(this.service(), instTID);
            assertTrue(override.isObjInst(), "the ws client declares " + instTID);
            assertTrue(Network.Helper.accepts(instTID, override.asInst()), "on " + instTID + "'s own signature");
        }
    }

    @Test
    void testCompiledToBindsTheWsClientsTo() {
        Network.Helper.connect(Machine.current().network(), this.dialURI(), WS_CLIENT_TID);
        // compile-time binding over a real wire: to(<ws address>) resolves to the ws client's own to, and applying it
        // ships start(9).to(</t/wire/z>) for the far side (this JVM behind the socket) to evaluate
        final Inst bound = instB(TO_INST_TID, lst(uri(f("ws://localhost:" + wsPort + "/t/wire/z")))).resolve(jnt(9));
        assertTrue(bound.functionClassName().endsWith("Network$Helper$AtPeer"), "the compiled to is the ws client's: " + bound.functionClassName());
        FutureObj.resolveFuture(bound.apply(jnt(9)));
        assertEquals(jnt(9), Machine.read(f("/t/wire/z")), "the bound to landed the write at the far side");
    }

    @Test
    void testASocketThatBringsItsOwnInstKeepsIt() {
        // a client provides its own custom to(): a socket spec that carries an inst keeps it — the socket declares its
        // eval/from/to only for a spec that carries none (no socket is opened: construction alone decides)
        final Obj custom = Network.Helper.override(TO_INST_TID, (lhs, inst) -> lhs);
        final WebSocketRec bespoke = new WebSocketRec(new java.util.LinkedHashMap<>(java.util.Map.of(uri(INST), rec(uri(TO_INST_TID), custom))), WS_CLIENT_TID, null);
        assertEquals(custom, Network.Helper.overrideIn(bespoke, TO_INST_TID), "the spec's own to is the socket's to");
        assertTrue(!Network.Helper.overrideIn(bespoke, EVAL_INST_TID).isObjInst(), "and nothing is declared beside it");
        final WebSocketRec plain = new WebSocketRec(new java.util.LinkedHashMap<>(), WS_CLIENT_TID, null);
        for (final fURI instTID : List.of(EVAL_INST_TID, FROM_INST_TID, TO_INST_TID))
            assertTrue(Network.Helper.overrideIn(plain, instTID).isObjInst(), "a plain socket declares " + instTID);
    }

    @Test
    void testEnsurePeeredOverTheWire() {
        final Obj first = Network.Helper.connect(Machine.current().network(), this.dialURI(), WS_CLIENT_TID);
        final Obj second = Network.Helper.connect(Machine.current().network(), this.dialURI(), WS_CLIENT_TID);
        assertEquals(first, second, "connect is ensure-peered: a dialed service answers its existing peer, never a second socket");
    }
}
