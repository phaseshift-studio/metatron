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
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.PEER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_CLIENT_TID;

/**
 * NETWORK CONNECT OVER A REAL WIRE — the loop test\u0027s equality carried one transport out: the dial
 * constructs a {@code ws_client} (the client type\u0027s constructor IS the dialer), its {@code send_recv}
 * becomes the peer\u0027s transport, and a read through the peer must answer exactly what the far side\u0027s
 * local read answers. The far side here is this same JVM\u0027s machine behind a real websocket, so the
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
    void retractPeer() {
        Machine.current().network().disconnect(this.service());
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
    void testEnsurePeeredOverTheWire() {
        final Obj first = Machine.current().network().connect(this.dialURI(), WS_CLIENT_TID);
        final Obj second = Machine.current().network().connect(this.dialURI(), WS_CLIENT_TID);
        assertEquals(first, second, "connect is ensure-peered: a dialed service answers its existing peer, never a second socket");
    }
}
