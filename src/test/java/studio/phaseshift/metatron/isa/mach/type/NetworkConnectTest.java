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
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.SUBQ_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.FROM_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.TO_INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
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
    void testAnOverrideIsTheNativeInstructionWithItsOwnF() {
        final Network network = Machine.current().network();
        network.connect(SELF);
        // an override IS the instruction it replaces with another f(): the native eval's tid (its dom and rng) and its
        // argument types — the function is the one thing that differs
        final Inst eval = network.overrideOf(SELF, EVAL_INST_TID).asInst();
        final Inst definition = Network.Helper.nativeOf(EVAL_INST_TID);
        assertEquals(definition.tid(), eval.tid(), "the override's tid is the native eval's: the same dom and rng");
        assertEquals(definition.args(), eval.args(), "the override's argument types are the native eval's");
        assertNotEquals(definition.f(), eval.f(), "the function is the one thing an override changes");
        assertTrue(Network.Helper.accepts(EVAL_INST_TID, eval), "the type system accepts the override as eval");
    }

    @Test
    void testDispatchIsTheNativeCallWithTheOverridesF() {
        // the call an override receives is the instruction exactly as a program issues it — nothing repacked, no seat
        // moved: from(<uri>) with the address as read; obj.to(<uri>) with the obj as the lhs, as the native to takes it
        final List<Inst> calls = new ArrayList<>();
        final List<Obj> lhss = new ArrayList<>();
        final Obj from = Network.Helper.override(FROM_INST_TID, (lhs, inst) -> {
            calls.add(inst);
            lhss.add(lhs);
            return jnt(1);
        });
        final Obj to = Network.Helper.override(TO_INST_TID, (lhs, inst) -> {
            calls.add(inst);
            lhss.add(lhs);
            return lhs;
        });
        final Obj client = rec(uri(HOST), uri(f("loop://probe")),
                uri(INST), rec(uri(FROM_INST_TID), from, uri(TO_INST_TID), to));
        Network.Helper.index(client);
        try {
            assertEquals(jnt(1), Machine.read(f("loop://probe/t/probe/x")), "the read answers the from override's result");
            assertEquals(jnt(7), Machine.write(f("loop://probe/t/probe/y"), jnt(7)), "the write answers the to override's result");
            assertEquals(2, calls.size(), "one call per dispatch");
            assertEquals(FROM_INST_TID, calls.get(0).tid().basePath(), "the read reaches the override as the from call");
            assertEquals(lst(uri(f("loop://probe/t/probe/x"))), calls.get(0).args(), "from's one argument: the address as read");
            assertEquals(noobj(), lhss.get(0), "a bare read has no lhs");
            assertEquals(TO_INST_TID, calls.get(1).tid().basePath(), "the write reaches the override as the to call");
            assertEquals(lst(uri(f("loop://probe/t/probe/y"))), calls.get(1).args(), "to's one argument: the address");
            assertEquals(jnt(7), lhss.get(1), "the written obj is the lhs, exactly as the native to takes it");
        } finally {
            Machine.current().network().disconnect(f("loop://probe"));
        }
    }

    @Test
    void testSubmitShipsTheCodeRawThroughEval() {
        // the eval call BLOCKS: its code is a payload for the peer to evaluate, so it must arrive unevaluated — were it
        // computed here, the override would receive the local value (42) instead of the read
        Machine.write(f("/t/evaluator/x"), jnt(42));
        final List<Inst> calls = new ArrayList<>();
        final Obj eval = Network.Helper.override(EVAL_INST_TID, (lhs, inst) -> {
            calls.add(inst);
            return inst.arg(0).apply(lhs);
        });
        final Obj client = rec(uri(HOST), uri(f("loop://evaluator")), uri(INST), rec(uri(EVAL_INST_TID), eval));
        Network.Helper.index(client);
        try {
            final Obj code = from_(f("/t/evaluator/x")).tryToInst();
            assertEquals(jnt(42), Machine.current().network().submit(f("loop://evaluator"), code),
                    "the override evaluates the code it was shipped");
            assertEquals(EVAL_INST_TID, calls.getFirst().tid().basePath(), "submit reaches the override as the eval call");
            assertTrue(calls.getFirst().tid().hasQ(BLOCK), "the eval call blocks: the code is a payload, not a computation");
            assertEquals(code, calls.getFirst().arg(0), "the code arrives raw — the peer evaluates it, never this side");
        } finally {
            Machine.current().network().disconnect(f("loop://evaluator"));
        }
    }

    @Test
    void testAnInstOfAnotherSignatureIsNotAnOverride() {
        // `from` takes an address: a body declared on another signature (a bare lambda — not the from instruction, no
        // uri argument) is not accepted by the type system as from, so the peer does not override from with it
        final Obj client = rec(uri(HOST), uri(f("loop://imposter")),
                uri(INST), rec(uri(FROM_INST_TID), instLambda((lhs, inst) -> jnt(13))));
        Network.Helper.index(client);
        try {
            assertEquals(noobj(), Machine.current().network().overrideOf(f("loop://imposter"), FROM_INST_TID),
                    "an inst whose signature is not from's is not an override of from");
            assertEquals(noobj(), Machine.read(f("loop://imposter/t/imposter/x")),
                    "with no accepted override (no eval, no transport either) the read falls through and misses");
        } finally {
            Machine.current().network().disconnect(f("loop://imposter"));
        }
    }

    @Test
    void testResolutionBindsAPeersToAtCompileTime() {
        // COMPILE-TIME BINDING: resolving to(<peer address>) answers the native to — its signature, its binding to the
        // lhs — with the PEER'S body as its f(), so the compiled code says whose to runs. The body is the client's
        // override, run inside the network's q-proc layer (Network.AtPeer).
        final Obj client = rec(uri(HOST), uri(f("loop://binder")),
                uri(INST), rec(uri(TO_INST_TID), Network.Helper.override(TO_INST_TID, (lhs, inst) -> Machine.write(inst.arg(0).uriValue().localize(), lhs))));
        Network.Helper.index(client);
        try {
            final Inst bound = instB(TO_INST_TID, lst(uri(f("loop://binder/t/binder/z")))).resolve(jnt(5));
            assertEquals(TO_INST_TID, bound.tid().basePath(), "the bound call is still the to instruction");
            assertTrue(bound.functionClassName().endsWith("Network$AtPeer"), "its f() is the peer's body, named as such: " + bound.functionClassName());
            bound.apply(jnt(5));
            assertEquals(jnt(5), Machine.read(f("/t/binder/z")), "applying the bound call runs the peer's to");
            // the same call through the language reaches the peer's to (end to end; the binding itself is asserted above)
            ObjmtronSerializer.parse("6.to(<loop://binder/t/binder/w>)").apply();
            assertEquals(jnt(6), Machine.read(f("/t/binder/w")), "a program's .to(<peer address>) runs the peer's to");
        } finally {
            Machine.current().network().disconnect(f("loop://binder"));
        }
    }

    @Test
    void testResolutionBindsAPeersFromAtCompileTime() {
        final Obj client = rec(uri(HOST), uri(f("loop://lookup")),
                uri(INST), rec(uri(FROM_INST_TID), Network.Helper.override(FROM_INST_TID, (lhs, inst) -> Machine.read(inst.arg(0).uriValue().localize()))));
        Network.Helper.index(client);
        try {
            Machine.write(f("/t/lookup/x"), jnt(42));
            final Inst bound = instB(FROM_INST_TID, lst(uri(f("loop://lookup/t/lookup/x")))).resolve(noobj());
            assertEquals(FROM_INST_TID, bound.tid().basePath(), "the bound call is still the from instruction");
            assertTrue(bound.functionClassName().endsWith("Network$AtPeer"), "its f() is the peer's body: " + bound.functionClassName());
            assertEquals(jnt(42), bound.apply(noobj()), "applying the bound call runs the peer's from");
        } finally {
            Machine.current().network().disconnect(f("loop://lookup"));
        }
    }

    @Test
    void testResolutionLeavesALocalAddressNative() {
        // no authority, no peer: the to stays the native one, which writes through Machine.write as always
        final Inst resolved = instB(TO_INST_TID, lst(uri(f("/t/native/z")))).resolve(jnt(5));
        assertTrue(resolved.hasf(), "the local to resolves");
        assertTrue(!resolved.functionClassName().contains("AtPeer"), "and keeps the native body: " + resolved.functionClassName());
    }

    @Test
    void testACompileTimeBoundToStillPassesTheNetworksLayer() throws Exception {
        // the layer is the one place a vid is handed to a source: a local subscription to the peer's namespace must
        // still see a write that compiled straight to the peer's to (it broadcasts once the body has written)
        final fURI fired = f("/t/layered/fired");
        Machine.write(fired, noobj());
        final Obj client = rec(uri(HOST), uri(f("loop://layered")),
                uri(INST), rec(uri(TO_INST_TID), Network.Helper.override(TO_INST_TID, (lhs, inst) -> Machine.write(inst.arg(0).uriValue().localize(), lhs))));
        Network.Helper.index(client);
        try {
            final Obj code = ObjmtronSerializer.parse("[code=>>>1.to(" + fired + ")]").apply().asRec().at(uri(CODE));
            Machine.write(f("loop://layered/t/layered/#").addQ("subq"), code);
            final Inst bound = instB(TO_INST_TID, lst(uri(f("loop://layered/t/layered/a")))).resolve(str("payload"));
            assertTrue(bound.functionClassName().endsWith("Network$AtPeer"), "the write is compile-time bound to the peer");
            bound.apply(str("payload"));
            assertEquals(str("payload"), Machine.read(f("/t/layered/a")), "the peer's to wrote");
            final long deadline = System.currentTimeMillis() + 3000;   // the broadcast runs on a virtual thread
            while (System.currentTimeMillis() < deadline && Machine.read(fired).isNoObj())
                Thread.sleep(25);
            assertEquals(str("payload"), Machine.read(fired), "and the network's subscription saw it: the layer was not bypassed");
        } finally {
            Machine.current().network().qs().lstValue().stream()
                    .filter(q -> q.tid().equals(SUBQ_TID))
                    .forEach(q -> q.asRec().at(uri(OBJ)).asLst().lstValue().clear());
            Machine.current().network().disconnect(f("loop://layered"));
        }
    }

    @Test
    void testReadDispatchesToANativeFromOverride() {
        // a READ-ONLY client: it overrides `from` (reads the localized uri locally) and declares NO eval, hence no
        // transport — the read ladder must reach it through its `from` override alone.
        final Obj fromBody = Network.Helper.override(FROM_INST_TID, (lhs, inst) -> Machine.read(inst.arg(0).uriValue().localize()));
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
        // a WRITE-ONLY client: it overrides `to` (writes its lhs to the localized arg(0) — the native to's own shape)
        // and declares NO eval, hence no transport — the write ladder must reach it through its `to` override alone.
        final Obj toBody = Network.Helper.override(TO_INST_TID, (lhs, inst) -> Machine.write(inst.arg(0).uriValue().localize(), lhs));
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
