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

package studio.phaseshift.metatron.isa.web.space.ws;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.FROM_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.TO_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.PEER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_CLIENT_TID;

/**
 * NETWORK CONNECT OVER A REAL WIRE — the loop test's equality carried one transport out: the dial
 * constructs a {@code ws_client} (the client type's constructor IS the dialer), its {@code send_recv}
 * becomes the peer's transport, and a read through the peer must answer exactly what the far side's
 * local read answers. The far side here is this same JVM's machine behind a real websocket, so the
 * equality is checkable without a second process.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class NetworkWsDialTest extends AbstractWebSocketServerIntegrationTest {

    @Override
    protected wsSpace createWSSpace() {
        return wsSpace.of(rec(
                uri(NAME), uri("network-dial-test"),
                uri(HOST), uri("ws://localhost:" + generatePort()),
                uri(PATTERN), uri("ws://#"),
                uri(ROUTE), rec(uri("/mtron"), uri(WS_MTRON_HANDLER_TID.toString()))
        ).jvm(), f("/sys/space/ws/network-dial-test"));
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
        network.disconnect(this.service());
        // the dial's close reaches the listener async; wait for the connection to settle so
        // each test starts with an empty reach
        final long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline && network.peers().elements().count() > 0)
            Thread.sleep(50);
    }

    @Test
    void testWsConnectMintsPeerWithClient() {
        final Obj peer = Machine.current().network().connect(this.dialURI(), WS_CLIENT_TID);
        assertEquals(PEER_TID, peer.tid().basePath(), "connect must answer a peer::T");
        assertEquals(this.service(), peer.asRec().<Obj>at(uri(AUTHORITY)).uriValue(),
                "the peer keys on the service authority — the route stays on host");
        assertEquals(this.dialURI(), peer.asRec().<Obj>at(uri(HOST)).uriValue(),
                "host remembers where it was dialed, route included — peer::T\u0027s own words");
        assertEquals(true, peer.asRec().<Obj>at(uri(TRANSPORT)).isObjInst(), "the transport is the client\u0027s send_recv");
        assertEquals(true, peer.asRec().<Obj>at(uri(CLIENT)).isRec(), "the client is retained for the hang-up");
    }

    @Test
    void testWsReadIsTheFarSidesLocalRead() {
        Machine.write(f("/t/wire/x"), jnt(42));
        Machine.current().network().connect(this.dialURI(), WS_CLIENT_TID);
        assertEquals(jnt(42), Machine.read(f("ws://localhost:" + wsPort + "/t/wire/x")),
                "the loop contract over a real wire: a read through a ws peer answers the far side\u0027s local read");
    }

    @Test
    void testWsWriteLandsAtTheFarSide() {
        Machine.current().network().connect(this.dialURI(), WS_CLIENT_TID);
        Machine.write(f("ws://localhost:" + wsPort + "/t/wire/y"), jnt(7));
        assertEquals(jnt(7), Machine.read(f("/t/wire/y")), "a write through the peer lands at the far side");
    }

    @Test
    void testWsClientDeclaresEvalFromAndTo() {
        Machine.current().network().connect(this.dialURI(), WS_CLIENT_TID);
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
        Machine.current().network().connect(this.dialURI(), WS_CLIENT_TID);
        // compile-time binding over a real wire: to(<ws address>) resolves to the ws client's own to, and applying it
        // ships start(9).to(</t/wire/z>) for the far side (this JVM behind the socket) to evaluate
        final Inst bound = instB(TO_INST_TID, lst(uri(f("ws://localhost:" + wsPort + "/t/wire/z")))).resolve(jnt(9));
        assertTrue(bound.functionClassName().endsWith("Network$AtPeer"), "the compiled to is the ws client's: " + bound.functionClassName());
        FutureObj.resolveFuture(bound.apply(jnt(9)));
        assertEquals(jnt(9), Machine.read(f("/t/wire/z")), "the bound to landed the write at the far side");
    }

    @Test
    void testEnsurePeeredOverTheWire() {
        final Obj first = Machine.current().network().connect(this.dialURI(), WS_CLIENT_TID);
        final Obj second = Machine.current().network().connect(this.dialURI(), WS_CLIENT_TID);
        assertEquals(first, second, "connect is ensure-peered: a dialed service answers its existing peer, never a second socket");
    }
}
