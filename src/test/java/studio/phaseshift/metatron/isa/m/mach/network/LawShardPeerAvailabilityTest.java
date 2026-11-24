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

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.mach.type.network.Listener;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.math.cat.Place;
import studio.phaseshift.metatron.isa.m.math.cat.Places;
import studio.phaseshift.metatron.isa.m.math.cat.catInstSet;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.space.ws.AbstractWebSocketServerIntegrationTest;
import studio.phaseshift.metatron.isa.m.web.space.ws.wsServer;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;
import static studio.phaseshift.metatron.isa.m.web.space.ws.wsServer.WS_CLIENT_TID;

/**
 * {@code law_shard} IS LIVE OVER A PEER, AND A MACHINE IS NEVER ITS OWN. A {@code ws_client} dialed back at this
 * machine's own co-mounted {@code ws_server} mints a {@code /network/peer}, but that peer is the machine itself
 * ({@link Network.Helper#reachesSelf}): it is no reach ({@link Places#reachable()}) and no shard target, so
 * {@code law_shard} is not a live out-edge of the shardable chain's {@code path::T}. A dial to another machine (a
 * {@code loop} transport stands in for one) makes it live. The real listener is the point: a machine's authorities are
 * its sockets, and this is the self-dial the console demo makes.
 */
//@Isolated
//@Execution(ExecutionMode.SAME_THREAD)
public class LawShardPeerAvailabilityTest extends AbstractWebSocketServerIntegrationTest {

    /**
     * another machine, at a transport that dials nothing
     */
    private static final fURI SIBLING = f("loop://sibling:8555");

    /**
     * another machine on this one's host, at another port
     */
    private static final fURI NEIGHBOR = f("loop://localhost:8556");

    @Override
    protected Listener createWSServer() {
        return wsServer.of(rec(
                uri(NAME), uri("law-shard-peer-test"),
                uri(ORIGIN), uri(f("").scheme(WS).host("localhost").port(generatePort())),
                uri(ROUTE), rec(uri("/mtron"), uri(WS_MTRON_HANDLER_TID.toString()))));
    }

    @Test
    void testLawShardAvailableWhenPeered() {
        // the boot default: every type check on except code_resolve (Marko, 2026-11-18)
        // path::T is the category's (catInstSet), which the standard test boot does not mount; law_shard is /m's
        InstSet.importInstSet(f("/m/math"));
        InstSet.importInstSet(f("/m/math/cat"));
        Machine.current().compiler(CatTheoryCompiler.single());
        Typer.CHECKS.forEach(flag -> Typer.Helper.set(flag, Typer.defaultsOn(flag)));
        final Network network = Machine.current().network();
        final fURI self = f("").scheme(WS).host("localhost").port(this.wsPort);
        try {
            // a self-dial mints a /network/peer, the trigger law_shard's guard reads, and that peer is no reach
            Network.Helper.connect(network, self.extend(f("/mtron")), WS_CLIENT_TID);
            assertFalse(network.peers().jvm().isEmpty(), "a self-dial mints a /network/peer");
            checkEquality(LOG, noobj(), reach(), true);
            checkEquality(LOG, bool(false), bool(shardable()), true);
            // a dial to another machine is the reach law_shard needs, and all of it: the self-dial beside it is none
            Network.Helper.connect(network, SIBLING);
            checkEquality(LOG, lst(uri("//sibling")), reach(), true);
            checkEquality(LOG, bool(true), bool(shardable()), true);
            // the self rule drops a service, never a host: a sibling on this host at another port is a place
            Network.Helper.connect(network, NEIGHBOR);
            assertEquals(Set.of(uri("//sibling"), uri("//localhost")),
                    Set.copyOf(reach().asLst().<Obj>elements().toList()),
                    () -> "a same-host sibling is reach beside the self-dial: " + reach());
        } finally {
            network.drop(NEIGHBOR);
            network.drop(SIBLING);
            network.drop(self);
        }
    }

    /**
     * the machines this one can distribute to, as {@link Places#reachable()} names them
     */
    private static Obj reach() {
        final Obj places = Places.reachable();
        return places.isNoObj() ? places
                : lst(places.asLst().<Obj>elements().map(place -> (Obj) uri(Place.of(place.asRec()).obj())).toList());
    }

    /**
     * {@code start({1,2,3,4}).plus(1).sum()} lifted to {@code path::T}, whose out-edges are the live rewrites
     */
    private static Obj path() {
        return catInstSet.PATH_TYPE.constructor().apply(ObjmtronSerializer.parse("start({1,2,3,4}).plus(1).sum()"));
    }

    private static boolean shardable() {
        final Obj path = path();
        return path.isRec() && path.asRec().has(uri("law_shard"));
    }
}
