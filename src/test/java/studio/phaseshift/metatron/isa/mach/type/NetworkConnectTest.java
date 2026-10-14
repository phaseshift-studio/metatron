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
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.PEER_TID;

/**
 * CONNECT — the network\u0027s mount op, tested against the {@code loop:} transport: the in-JVM loopback
 * whose contract is the self-similarity of the router of routers — a read through a loop peer answers
 * exactly what the local read of the localized address answers. Every real transport (ws, mcp, http)
 * must preserve the same equality one authority over, so these tests are the contract the protocol
 * clients inherit, not a toy.
 */
public class NetworkConnectTest extends AbstractMetatronTest {

    private static final fURI SELF = f("loop://self");

    @AfterEach
    void retract() {
        Machine.current().network().disconnect(SELF);
    }

    @Test
    void testConnectMintsPeer() {
        final Network network = Machine.current().network();
        final Obj peer = network.connect(SELF);
        assertEquals(PEER_TID, peer.tid().basePath(), "connect must answer a peer::T");
        assertEquals(SELF, peer.asRec().<Obj>at(uri(AUTHORITY)).uriValue(), "the peer keys on the dialed authority");
        assertEquals(true, peer.asRec().<Obj>at(uri(TRANSPORT)).isObjInst(), "the transport is the reach — an inst");
        assertEquals(1L, network.peers().elements().filter(kv -> kv.first().isUri()).count(), "the dial is a member of this level");
    }

    @Test
    void testConnectIsEnsurePeered() {
        final Network network = Machine.current().network();
        final Obj first = network.connect(SELF);
        final Obj second = network.connect(SELF);
        assertEquals(first, second, "connect is ensure-peered: a dialed authority answers its existing peer, never a rebuild");
        assertEquals(1L, network.peers().elements().count(), "a re-dial must not add a second member");
    }

    @Test
    void testLoopReadIsLocalRead() {
        Machine.write(f("/t/loopback/x"), jnt(42));
        Machine.current().network().connect(SELF);
        assertEquals(jnt(42), Machine.read(f("loop://self/t/loopback/x")),
                "a read through a loop peer IS the local read of the localized address");
        assertEquals(Machine.read(f("/t/loopback/x")), Machine.read(f("loop://self/t/loopback/x")),
                "the self-similarity contract: same function, one authority over");
    }

    @Test
    void testLoopWriteIsLocalWrite() {
        Machine.current().network().connect(SELF);
        Machine.write(f("loop://self/t/loopback/y"), jnt(7));
        assertEquals(jnt(7), Machine.read(f("/t/loopback/y")),
                "a write through a loop peer lands at the localized address");
    }

    @Test
    void testUndialedAuthorityFallsThrough() {
        assertEquals(noobj(), Machine.read(f("loop://stranger/t/nothing")),
                "an authority that is neither dialed nor declared is not a peer: the read falls through and misses");
    }

    @Test
    void testDisconnectRetracts() {
        final Network network = Machine.current().network();
        network.connect(SELF);
        Machine.write(f("/t/loopback/z"), jnt(3));
        assertEquals(jnt(3), Machine.read(f("loop://self/t/loopback/z")), "reachable while dialed");
        network.disconnect(SELF);
        assertEquals(0L, network.peers().elements().count(), "disconnect removes the member");
        assertEquals(noobj(), Machine.read(f("loop://self/t/loopback/z")),
                "an undialed authority stops answering: reach is a capability, retracted with the dial");
    }
}
