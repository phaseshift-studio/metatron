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

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import java.net.InetSocketAddress;

import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.FROM_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.TO_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
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

    @BeforeAll
    static void importWeb() {
        InstSet.importInstSet(WEB_ISA_TID);
    }

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
    void testOverrideOfDispatchesToThePeersOwnBodies() {
        final Network network = Machine.current().network();
        network.connect(SELF);
        // the loop peer declares exactly one maximal override — eval (identity). from/to derive from it, not declared.
        assertTrue(network.overrideOf(SELF, EVAL_INST_TID).isObjInst(),
                "the loop peer declares its eval override in its mini-instset");
        assertEquals(noobj(), network.overrideOf(SELF, FROM_INST_TID),
                "from is not declared on the loop peer — it derives from eval");
    }

    @Test
    void testSubmitEvaluatesCodeOnThePeersEvalOverride() {
        final Network network = Machine.current().network();
        network.connect(SELF);
        final Obj code = ObjmtronSerializer.parse("1.plus(2)");
        assertEquals(jnt(3), network.submit(SELF, code),
                "submit evaluates code on the peer's eval override — the loop peer answers the local evaluation");
    }

    @Test
    void testReadDispatchesToANativeFromOverride() {
        // a READ-ONLY client: it overrides `from` (reads the localized uri locally) and declares NO eval, hence no
        // transport — the read ladder must reach it through its `from` override alone.
        final Obj fromBody = instLambda((lhs, inst) -> Machine.read(inst.arg(0).uriValue().localize()));
        final Obj client = rec(uri(HOST), uri(f("loop://reader")),
                uri(INST), rec(uri(FROM_INST_TID), fromBody));
        Network.Helper.index(client);
        try {
            Machine.write(f("/t/reader/x"), jnt(42));
            assertEquals(jnt(42), Machine.read(f("loop://reader/t/reader/x")),
                    "a read through a from-only peer dispatches to its `from` override — the local read of the localized uri");
        } finally {
            Machine.current().network().disconnect(f("loop://reader"));
        }
    }

    @Test
    void testWriteDispatchesToANativeToOverride() {
        // a WRITE-ONLY client: it overrides `to` (writes arg(0) to arg(1) locally) and declares NO eval, hence no
        // transport — the write ladder must reach it through its `to` override alone.
        final Obj toBody = instLambda((lhs, inst) -> Machine.write(inst.arg(1).uriValue().localize(), inst.arg(0)));
        final Obj client = rec(uri(HOST), uri(f("loop://sink")),
                uri(INST), rec(uri(TO_INST_TID), toBody));
        Network.Helper.index(client);
        try {
            Machine.write(f("loop://sink/t/sink/y"), jnt(7));
            assertEquals(jnt(7), Machine.read(f("/t/sink/y")),
                    "a write through a to-only peer dispatches to its `to` override — the local write to the localized uri");
        } finally {
            Machine.current().network().disconnect(f("loop://sink"));
        }
    }

    @Test
    void testHttpClientGetRoundTrip() throws Exception {
        final HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/hello", exchange -> {
            final byte[] body = "world".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (final var os = exchange.getResponseBody()) {
                os.write(body);
            }
            exchange.close();
        });
        server.start();
        final int port = server.getAddress().getPort();
        final String authority = "http://localhost:" + port;
        try {
            // a bare read auto-connects: it dials the http_client (inst => {from => GET}), indexes it as a peer, and
            // dispatches the GET through its `from` override
            assertEquals(str("world"), Machine.read(f(authority + "/hello")),
                    "a read through an http peer GETs the resource via its `from` override");
            assertTrue(Machine.current().network().overrideOf(f(authority), FROM_INST_TID).isObjInst(),
                    "the bare http read dialed and indexed the http_client as a peer");
        } finally {
            server.stop(0);
            Machine.current().network().disconnect(f(authority));
        }
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
