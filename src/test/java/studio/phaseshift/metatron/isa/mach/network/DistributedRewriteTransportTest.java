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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.isa.web.type.MIME;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.IN;
import static studio.phaseshift.metatron.Tokens.OUT;
import static studio.phaseshift.metatron.isa.m.mInstSet.MERGE_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SELECT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SUM_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_CLIENT_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE TRANSPORT-ROSTER PATH — {@code law_shard}/{@code PlanInterpreter}, fired for real. The space-roster
 * suite ({@code DistributedRewritePhasesTest}) never makes {@code network().peers()} non-empty, so it only
 * exercises {@code sum_gather}; this suite constructs a {@code ws_client} per peer, which dials and
 * self-registers into {@code /network/peers} — the exact trigger {@code law_shard}'s guard reads. Peers are
 * real OS processes ({@link PeerCluster}), so the only thing between this VM and a peer is the socket.
 */
public class DistributedRewriteTransportTest extends AbstractMetatronTest {

    private static PeerCluster cluster;
    private static final List<WebSocketRecClient> clients = new ArrayList<>();

    @BeforeAll
    static void startPeers() throws Exception {
        // typing is not solved across distributed executions yet — run type-check-free, as the manual demo did
        TypeCheck.disable(TypeCheck.values());
        // catRewrite.law_shard is registered by catInstSet, which the standard test boot does not mount
        // (BootLoader.load mounts only /m, /sys, /m/mach). Import its parent (/m/math) first, then it.
        InstSet.importInstSet(f("/m/math"));
        InstSet.importInstSet(f("/m/math/cat"));
        Machine.current().compiler(CatTheoryCompiler.single());
        cluster = PeerCluster.of(2).start();
        for (int i = 1; i <= cluster.size(); i++)
            clients.add(dial(i));
    }

    @AfterAll
    static void stopPeers() {
        for (final WebSocketRecClient client : clients)
            try {
                client.close();
            } catch (final Exception ignored) {
                // a peer already down just means the client has nothing left to hang up
            }
        if (null != cluster)
            cluster.close();
    }

    /**
     * Construct a {@code ws_client} to one peer: the constructor dials, and {@link Network.Helper#index} mints
     * the {@code peer::T} into {@code /network/peers} — the idiomatic construction, as {@code ws_client::[host=>…]}.
     */
    private static WebSocketRecClient dial(final int oneBased) {
        final WebSocketRec wsRec = new WebSocketRec(mutableMap(
                uri(HOST), uri("ws://localhost:" + cluster.port(oneBased) + PeerCluster.MTRON_ROUTE),
                uri(IN), uri(MIME.MIMEType.APPLICATION_MTRON.value),
                uri(OUT), uri(MIME.MIMEType.APPLICATION_MTRON.value)), WS_CLIENT_TID, null);
        final WebSocketRecClient client = new WebSocketRecClient(wsRec);
        Network.Helper.index(client);
        return client;
    }

    @Test
    void testTransportPeersAreHeld() {
        assertFalse(Machine.current().network().peers().jvm().isEmpty(),
                "constructing a ws_client dials and self-registers into /network/peers");
        assertEquals(2, Machine.current().network().peers().jvm().size(),
                "one held connection per forked peer");
    }

    @Test
    void testDistributedRewriteFiresOverTransport() {
        final DistributedComputation c = DistributedComputation.of("{1,2,3,4,5,6}.plus(1).sum()");

        // the general shape: a flat peer list, no master/slave naming
        assertEquals(3, c.original().insts().size(), "the original is start·plus·sum");
        assertEquals(3, c.peers().size(), "one local peer + two remote peers");
        assertEquals(27, c.result().asInt().intValue(), "the aggregate");

        // the reduction reading: master (fold) + slaves (slice + reducer)
        final Reduction r = Reduction.of(c);
        assertEquals(List.of(START_INST_TID, SELECT_INST_TID, MERGE_INST_TID, SUM_INST_TID),
                TransportRewriteHarness.ops(r.masterCode()), "the master runs start·select·merge·sum");
        assertEquals(2, r.slaveCodes().size(), "two slaves");
        assertSlice(TransportRewriteHarness.seedOf(r.slaveCodes().get(0)), 1, 3, 5);
        assertSlice(TransportRewriteHarness.seedOf(r.slaveCodes().get(1)), 2, 4, 6);
        assertEquals(27, r.complete().asInt().intValue(), "the fold");
        assertEquals(12, r.partials().get(0).asInt().intValue(), "slave 1 partial");
        assertEquals(15, r.partials().get(1).asInt().intValue(), "slave 2 partial");

        // provenance: which FutureObj (hex vid) carried each partial, from the shared base frame
        final Rec base = Machine.current().memory().stackBaseFrame();
        final List<Map.Entry<Obj, Obj>> partials = TransportRewriteHarness.partials(base);
        assertEquals(2, partials.size(), "two partials, one per peer");
        assertTrue(partials.stream().anyMatch(e -> FutureObj.resolveFuture(e.getValue()).asInt().intValue() == 12)
                        && partials.stream().anyMatch(e -> FutureObj.resolveFuture(e.getValue()).asInt().intValue() == 15),
                "the partials are 12 and 15: " + partials);
        for (final Map.Entry<Obj, Obj> e : partials)
            assertTrue(e.getKey().isUri() && e.getKey().uriValue().name().matches("[0-9a-f]{8}"),
                    "each partial is stamped with a minted 8-hex vid: " + e.getKey());
    }

    private static void assertSlice(final List<Obj> slice, final int... expected) {
        assertEquals(expected.length, slice.size(), "slice size");
        for (int i = 0; i < expected.length; i++)
            assertEquals(expected[i], slice.get(i).asInt().intValue(), "slice element " + i);
    }
}
