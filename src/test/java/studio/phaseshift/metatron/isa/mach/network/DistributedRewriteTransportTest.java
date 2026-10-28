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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.Training;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
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
import static studio.phaseshift.metatron.Tokens.M_ISA_INST_TID;
import static studio.phaseshift.metatron.Tokens.OUT;
import static studio.phaseshift.metatron.isa.m.mInstSet.MERGE_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SELECT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SUM_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.wsServer.WS_CLIENT_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE DIALED-PEER PATH — {@code law_shard}/{@code PlanInterpreter}, fired for real. The space-roster
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
        final DistributedSweep.Layout layout = DistributedSweep.layout(r.masterCode());
        assertEquals(List.of(START_INST_TID, SELECT_INST_TID, SELECT_INST_TID, MERGE_INST_TID, SUM_INST_TID),
                layout.ops(), "the master runs start·select·select·merge·sum");
        assertEquals(2, layout.lanes().size(), "two slaves");
        assertEquals(List.of(1L, 3L, 5L), layout.lane(0).part().stream().map(Obj::intValue).toList(), "slave 1's slice");
        assertEquals(List.of(2L, 4L, 6L), layout.lane(1).part().stream().map(Obj::intValue).toList(), "slave 2's slice");
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

    /**
     * ONE PLAN, EVERY FOLD WHOSE PARTIALS RECOMBINE. {@code law_shard} shards the fold and the master recombines the
     * peers' partials through its COMBINER, computed rather than named: {@code sum}/{@code prod} through themselves,
     * {@code count} through {@code sum}, {@code order(s)} through {@code kmerge(s)}. The fold runs for real across the
     * forked peers — the master's chain leads {@code start·select·merge·<combiner>} — and the recombined value is the
     * value the undistributed fold answers. Each row: the fold % its combiner's op % the recombined value.
     */
    @Training.SkipTraining(reason = "the rows run over forked peers; the distributed layout is the subject, not the value alone")
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4,5,6}.plus(1).count()  % sum    % 6",
            // a keyed fold recombines POINTWISE: the rec sum merges the partials by key (a bulked partial read as its
            // copies), then each key folds through its value's own combiner -- sum through sum, count through sum
            "{1,2,3,4,5,6}.group([mod(3)=>sum()])                      % sum % [1=>5,2=>7,0=>9]",
            "{1,2,3,4,5,6}.plus(1).group([mod(3)=>count()])            % sum % [2=>2,0=>2,1=>2]",
            "{1,2,3,4,5,6}.group([mod(2)=>prod()])                     % sum % [1=>15,0=>48]",
            "{ginger,booger,ginger,fred}.group([_=>count()])           % sum % [ginger=>2,booger=>1,fred=>1]",
            "{[a,b],[c,d],[a,b]}.group([_=>count()])                   % sum % [[a,b]=>2,[c,d]=>1]",
            "{1,2,3,4,5,6}.group([mod(3)=>group([mod(2)=>sum()])])     % sum % [1=>[1=>1,0=>4],2=>[0=>2,1=>5],0=>[1=>3,0=>6]]",
            // a bag of recs folds through the deep merge's own sum -- commutative, since a lst is an atom of the merge --
            // so the partial recs recombine through it: keys union, colliding values bag (lsts whole), equal ones bulk
            "{[a=>1],[b=>2],[a=>3]}.sum()                              % sum % [a=>{1,3},b=>2]",
            "{[a=>1],[b=>2],[a=>1],[b=>2]}.sum()                       % sum % [a=>{1,1},b=>{2,2}]",
            "{[mama=>[visited=>[arroyo]]],[mama=>[visited=>[acequia,mesa]]],[puppy=>[km=>5]]}.sum() % sum % [mama=>[visited=>{[arroyo],[acequia,mesa]}],puppy=>[km=>5]]",
            // a lst-valued group recombines too: a lst is an atom of the deep merge, so a key's sorted partials bag
            // whole -- a bag of runs, never their slots -- and kmerge merges the runs
            "{1,2,3,4,5,6}.group([mod(3)=>order(_)])                   % sum % [1=>[1,4],2=>[2,5],0=>[3,6]]",
            "{6,5,4,3,2,1}.plus(1).group([mod(2)=>order(_)])           % sum % [1=>[3,5,7],0=>[2,4,6]]",
            "{1,2,3,4,5,6}.group([mod(3)=>group([mod(2)=>order(_)])])  % sum % [1=>[1=>[1],0=>[4]],2=>[0=>[2],1=>[5]],0=>[1=>[3],0=>[6]]]",
    }, delimiter = '%')
    void testCombinerShardsOverTransport(final String source, final String combiner, final String expected) {
        final DistributedComputation c = DistributedComputation.of(source);
        final List<fURI> ops = DistributedSweep.layout(Reduction.of(c).masterCode()).ops();
        assertEquals(List.of(START_INST_TID, SELECT_INST_TID, SELECT_INST_TID, MERGE_INST_TID, M_ISA_INST_TID.extend(combiner)),
                ops.subList(0, Math.min(5, ops.size())), "the master recombines the partials through the combiner: " + ops);
        checkEquality(LOG, ObjmtronSerializer.parse(expected), c.result(), true);
    }

    /**
     * AN EFFECT STAYS HOME. The floatable run is replicated into every peer, so only a declared-{@code pure}
     * instruction floats: {@code side} is a mapper by its form but declares nothing, so a fold behind it is not
     * sharded, anywhere in the run, while the same fold behind a pure mapper is. Each row: the fold % whether the
     * master ships it to the peers (a select) % its value.
     */
    @Training.SkipTraining(reason = "the rows run over forked peers; whether the layout ships is the subject, not the value alone")
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4,5,6}.plus(1).sum()                          % true   % 27",
            "{1,2,3,4,5,6}.plus(1).side(plus(1)).sum()            % false  % 27",
            "{1,2,3,4,5,6}.plus(1).side(plus(1)).plus(1).sum()    % false  % 33",
    }, delimiter = '%')
    void testOnlyPureRunsFloat(final String source, final boolean shipped, final String expected) {
        final Code rewritten = Machine.current().compiler().rewriter().apply(ObjmtronSerializer.parse(source).asCode()).asCode();
        checkEquality(LOG, bool(shipped), bool(TransportRewriteHarness.ops(rewritten).contains(SELECT_INST_TID)), true);
        checkEquality(LOG, ObjmtronSerializer.parse(expected), TransportRewriteHarness.single(TransportRewriteHarness.evaluate(rewritten)), true);
    }
}
