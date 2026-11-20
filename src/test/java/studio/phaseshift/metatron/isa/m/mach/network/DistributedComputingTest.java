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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.m.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.isa.m.web.type.MIME;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.BRANCH_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.PROFILING_TID;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.CAT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.space.ws.wsServer.WS_CLIENT_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE PATTERN MENU AS AN EXECUTABLE TABLE — one test method per row of the menu in
 * {@code docs/skills/mtron/references/distributed-computing-mtron.md}, so the reference and the
 * behaviour cannot drift: a row that regresses turns exactly one method red.
 *
 * <p>Every method has the same three-column shape, and the three columns are the three claims a
 * distributed pattern makes:
 *
 * <ol>
 *   <li><b>code</b> — the <em>pre-write motif</em>, the mtron a user writes before any rewrite fires;</li>
 *   <li><b>expectedRewriteForm</b> — the <em>rewrite motif</em>, the layout the plan is expected to emit.
 *       Stated as the master's op sequence ({@code branch().sum()}) rather than
 *       the full chain, because the branch's keys name the forked peers' ports, which no row
 *       could name: {@code checkCodeRewrite} is called with
 *       {@code checkTIDs=true}, which compares the op sequence and the inst count, not the args. The
 *       shipped <em>lane</em> codes are asserted structurally by the sibling suites
 *       ({@link DistributedRewriteTransportTest}, {@link TreeShardTransportTest},
 *       {@link ParallelFanOutTest}), which read them through {@link Reduction} and
 *       {@link DistributedSweep#layout};</li>
 *   <li><b>expectedResult</b> — the value, which must be identical whether the chain ran sharded or whole
 *       (that equality <em>is</em> Thm 19 for the row's law).</li>
 * </ol>
 *
 * <p>{@code checkCodeRewrite} evaluates three programs and demands one answer: the original, the expected
 * rewrite form, and the actual compilation. So a row proves the layout <em>and</em> that distributing did not
 * change the answer.
 *
 * <h2>the menu (A–W)</h2>
 *
 * <p>{@code ✅} = the lowering is written; an empty box = the law is sound but the lowering is unwritten (the
 * row is present below as a {@code @Disabled} stub naming what it still needs). The law column is the reason
 * the row is sound — distribution is a theorem, not a feature.
 *
 * <table border="1">
 *   <caption>the distributed-computing pattern menu</caption>
 *   <tr><th>row</th><th>pattern</th><th>law</th><th>pre-write motif</th><th></th></tr>
 *   <tr><td>A</td><td>homomorphism shard</td><td>h(a ⊎ b) = h(a) ⊕ h(b)</td><td>{@code {1,2,3,4}.plus(1).count()}</td><td>✅</td></tr>
 *   <tr><td>B</td><td>data-local shard</td><td>from(p) = Σ_a from_a(p)</td><td>{@code *<//usr/data/+>.plus(1).sum()}</td><td></td></tr>
 *   <tr><td>C</td><td>authority push-down</td><td>from · count ↦ native_count</td><td>{@code *<ws://host:8555/n/+>.plus(1).sum()}</td><td></td></tr>
 *   <tr><td>D</td><td>parallel fan-out</td><td>⟨x⟩(a+b) = ⟨x⟩a + ⟨x⟩b</td><td>any shard (the transport, not a fold)</td><td>✅</td></tr>
 *   <tr><td>E</td><td>tree reduction</td><td>(a+b+c+d)·r = ((a·r+b·r)·C + (c·r+d·r)·C)·C</td><td>{@code {1,…,8}.plus(1).sum?hop=2()}</td><td>✅</td></tr>
 *   <tr><td>F</td><td>ordered fold</td><td>a·(b·c) = (a·b)·c</td><td>an lst-partitioned fold (streams never shard)</td><td></td></tr>
 *   <tr><td>G</td><td>keyed reduce</td><td>combiner(group([k⇒h])) = sum·select([_⇒combiner(h)])</td><td>{@code {1,2,3,4,5,6}.group([mod(3)=>sum()])}</td><td>✅</td></tr>
 *   <tr><td>H</td><td>redundant / hedged shards</td><td>x ⊕ x = x</td><td>a partition shipped to r peers</td><td>✅ ({@code ?split=copy})</td></tr>
 *   <tr><td>I</td><td>span/route interpreter</td><td>plan ↦ layout (infrastructure)</td><td>{@code [c]==[as(cat:path::T)]==[>>law_shard>>result]}</td><td></td></tr>
 *   <tr><td>J</td><td>algebraic aggregates</td><td>mean ≡ (sum, count)·div</td><td>{@code {...}.mean()}</td><td></td></tr>
 *   <tr><td>K</td><td>limit / top-k push-down</td><td>take(k)·⊕ = take(k)·⊕·take(k)</td><td>{@code {...}.order(s)==[{k}_]}</td><td>✅</td></tr>
 *   <tr><td>L</td><td>mergeable sketches</td><td>ĥ(a ⊎ b) = ĥ(a) ⊕ ĥ(b)</td><td>{@code {...}.hll()}</td><td></td></tr>
 *   <tr><td>M</td><td>matrix multiplication</td><td>A·(B+C) = A·B + A·C</td><td>{@code mtrx::[…]}</td><td></td></tr>
 *   <tr><td>N</td><td>iterative / BSP</td><td>x* = x ∨ f(x) ∨ f²(x) ∨ ⋯</td><td>a sharded superstep repeated</td><td></td></tr>
 *   <tr><td>O</td><td>broadcast constants</td><td>ca·1b = 1a·1b·c1 (Thm 6)</td><td>{@code {...}.plus(B).sum()}</td><td>✅</td></tr>
 *   <tr><td>P</td><td>computed seeds</td><td>[0,n) = ⊎_i index_i</td><td>Monte Carlo — a count + an RNG seed per peer</td><td></td></tr>
 *   <tr><td>Q</td><td>monad migration</td><td>authority(m) — ship the monad, not the value</td><td>a remote read mid-chain</td><td></td></tr>
 *   <tr><td>R</td><td>durable results</td><td>id : partial ↦ address</td><td>{@code to(</usr/job/round/shard>)}</td><td></td></tr>
 *   <tr><td>S</td><td>master participates</td><td>⊕_i a_i·r over {master} ∪ peers</td><td>{@code {1,…,8}.plus(1).sum()}</td><td>✅</td></tr>
 *   <tr><td>T</td><td>reference frame migration</td><td>move(a) … move(b) … (machine groupoid)</td><td>{@code {1,2,3}.plus(5).move(//host/a).plus(2).sum()}</td><td>✅</td></tr>
 *   <tr><td>U</td><td>profile collation</td><td>p̂(a ⊎ b) = p̂(a) ⊕ p̂(b), ⊕ = keyed union, the figures add or fold</td><td>{@code {1,2,3,4,5}.plus(10).sum().profile()>>format}</td><td>✅</td></tr>
 *   <tr><td>V</td><td>generative run</td><td>a^[m,n] = Σ_k a^k (the unrolls are independent terms)</td><td>{@code start(0).{0,1000}plus(1).sum()}</td><td>✅</td></tr>
 *   <tr><td>W</td><td>embarrassingly parallel</td><td>(a+b)·f = a·f + b·f, f floatable, no cut</td><td>{@code {1,…,8}.plus(1)}</td><td>✅</td></tr>
 * </table>
 *
 * <h2>why this suite needs a real cluster</h2>
 *
 * <p>{@code law_shard}'s guard reads {@code network().peers()} — the <em>dialed</em> set, which is reach and
 * therefore never inherited. A test that only pushes a frame, or only writes a roster row, leaves that map
 * empty and the plan declines to the identity; the rewrite then equals the original and a layout assertion
 * would be asserting nothing. So each peer here is a real forked JVM ({@link PeerCluster}) and is dialed
 * through its own {@code ws_client}, which self-registers into {@code /network/peer} — the exact trigger
 * the guard reads.
 *
 * <p>Type checking is disabled for this suite: typing <em>across</em> distributed executions is unsolved
 * (a shipped tail answers {@code #{*}}, so the master cannot re-derive the fold's int-ness). That is the same
 * posture the manual demo and the sibling transport suites take, and it is why the expected result is a value
 * rather than a type.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DistributedComputingTest extends AbstractMetatronTest {

    private static PeerCluster cluster;
    private static final List<WebSocketRecClient> clients = new ArrayList<>();

    @BeforeAll
    static void startPeers() throws Exception {
        // the boot default: every type check on except code_resolve (Marko, 2026-11-18)
        // catRewrite.law_shard is registered by catInstSet, which the standard test boot does not mount
        // (BootLoader.load mounts only /m, /sys, /m/mach). Import its parent (/m/math) first, then it.
        InstSet.importInstSet(MATH_ISA_TID);
        InstSet.importInstSet(CAT_ISA_TID);
        Machine.current().compiler(CatTheoryCompiler.single());
        Typer.CHECKS.forEach(flag -> Typer.Helper.set(flag, Typer.defaultsOn(flag)));
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
     * the {@code peer::T} into {@code /network/peer} — the idiomatic construction, spelled
     * {@code ws_client::[host=>…]} in mtron.
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

    // ================================================================================================
    // A — homomorphism shard. The combiner is COMPUTED, never declared: sum/prod are self-combining,
    // count recombines through sum (count ≡ map(1)·sum), and a fold that declares only monoidic (mean)
    // or is not commutative (str sum) correctly does NOT shard.
    // ================================================================================================

    @ParameterizedTest
    @CsvSource(value = {
            // the canonical row: the seed bulk factors, the floatable run replicates, the fold recombines at home
            "{1,2,3,4,5,6}.plus(1).sum()       % branch().sum() % 27",
            // count is DERIVED -- its combiner is sum, so the master's last op is sum, not count
            "{1,2,3,4}.plus(1).count()         % branch().sum() % 4",
            // prod is self-combining: the master re-applies prod
            "{1,2,3,4}.plus(1).prod()          % branch().prod() % 120",
            // a filter handed an effect does not float, so it stays home and only the fold ships over its bag
            "{1,2,3,4,5,6}.is(print(_).gt(2)).sum()  % start().is().branch().sum() % 18",
    }, delimiter = '%')
    void test_A_homomorphism_shard(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }

    /**
     * B -- data-local shard: from(p) = Σ_a from_a(p). Needs {@code ?split=owner}: one part per ADDRESS OWNER,
     * so an addressed seed ({@code from}, {@code *<uri>}) can be partitioned by where the data lives.
     */
    @Test
    void test_B_data_local_shard() {
        LOG.warn("menu row B (data-local shard) currently not implemented");
    }

    /**
     * C -- authority push-down: from·count ↦ native_count (Thm 5 + a pure chain). B with a single owner — the
     * chain is delegated to the space that owns the data instead of being sharded over peers.
     */
    @Test
    void test_C_authority_push_down() {
        LOG.warn("menu row C (authority push-down) currently not implemented");
    }

    // ================================================================================================
    // D — parallel fan-out. Not a fold: it is the transport every shard rides. Every keyed lane of the
    // branch is a virtual::T, submitted before any is awaited, so the master pays max RTT rather than Σ RTT,
    // and the branch awaits each lane through its rhs. The observable is therefore the SAME emitted shape
    // as A: the concurrency is the branch's own, not a second layout.
    // ================================================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4,5,6}.plus(1).sum()                 % branch().sum()                                                                    % 27",
            "{1,2,3,4,5,6}.is(gt(2)).sum()               % branch(start().is(gt(2)).sum(),start().is(gt(2)).sum()).sum()                     % 18",
            "{1,2,3,4,5,6}.is(gt(2)).sum().mult(2)       % branch(start().is(gt(2)).sum(),start().is(gt(2)).sum()).sum().mult(2)             % 36",
            "{1,2,3,4,5,6}.is(gt(2)).is(lt(6)).sum()     % branch(start().is(gt(2)).is(lt(6)).sum(),start().is(gt(2)).is(lt(6)).sum()).sum() % 12",
    }, delimiter = '%')
    void test_D_parallel_fan_out(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }

    // ================================================================================================
    // E — tree reduction. ?hop is a TREE shape: the height (how many times a rule may re-plan what it
    // produced) and, as a leading coefficient, the arity (children per node). The form is RESTATED at
    // each level ({2}3 ⇒ {2}2 ⇒ {2}1 ⇒ 0) and rides the slave's FOLD, never its seed.
    // ================================================================================================

    @ParameterizedTest
    @CsvSource(value = {
            // height 2, at most 2 children: the master shards to one peer, which shards again
            "{1,2,3,4,5,6,7,8}.plus(1).sum?hop={2}2()   % branch().sum() % 44",
            // height 1 is the star -- today's default, stated explicitly
            "{1,2,3,4,5,6,7,8}.plus(1).sum?hop=1()      % branch().sum() % 44",
    }, delimiter = '%')
    void test_E_tree_reduction(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }

    /**
     * F -- ordered fold: a·(b·c) = (a·b)·c, associativity alone. Needs {@code ?split=block}: an ORDERED seed.
     * Everything {@code law_shard} sees is a bag (a stream has no order), so lowering it today would reorder
     * the answer.
     */
    @Test
    void test_F_ordered_fold() {
        LOG.warn("menu row F (ordered fold) currently not implemented");
    }


    // ================================================================================================
    // G — keyed reduce. group([k⇒h]) is a homomorphism into the recs of h's monoid, keyed by k: the
    // partials merge by key (the rec sum is the deep merge; colliding partials append into a bag), then
    // each key folds through h's own combiner -- the POINTWISE combiner, which no single edge names.
    // The master therefore ends with one MORE op than A: the per-key select.
    // ================================================================================================

    @Disabled("layout unverified: the master's per-key select and the rec key order need pinning before the row is green")
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4,5,6}.group([mod(3)=>sum()])  % branch().sum().select() % [0=>9,1=>5,2=>7]",
    }, delimiter = '%')
    void test_G_keyed_reduce(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }

    // ================================================================================================
    // H — redundant / hedged shards. ?split=copy ships the WHOLE seed to every lane and folds (x ⊕ x = x),
    // which is sound only behind idempotence: the emitter gates on the cut's COMBINER declaring
    // `idempotent`, and answers the unchanged chain (never a fan-out that quietly answers twice the sum)
    // when it does not. The legality suite owns the gate itself; this row owns the positive case.
    // ================================================================================================

    @Disabled("needs an idempotent, commutative fold in the vocabulary to exercise the positive copy case")
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4}.sum?split=copy()   % branch().sum() % 10",
    }, delimiter = '%')
    void test_H_redundant_shards(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }

    // ================================================================================================
    // K — limit / top-k push-down: take(k)·⊕ = take(k)·⊕·take(k). Keeping the first k positions commutes with
    // the sorted merge, as the first k of a merge draw only on the first k of each run: kmerge is CAUSAL. On a
    // lst, take k is a span of identity slots, ==[{k}_]: the span lays k slots along the select's bracket, and a
    // select keeps only the positions its pattern covers. So top-k is order(s)==[{k}_], with no new instruction,
    // and the lowering is one step of the emitter: a cut whose combiner is causal absorbs that select, so each lane
    // is start(part).order(s)==[{k}_] and the master kmerge(s)==[{k}_], and a lane ships at most k members. The
    // gate is the select's shape: a run of identity slots is a projection, while a select that maps is not, so it
    // stays home.
    // ================================================================================================

    @ParameterizedTest
    @CsvSource(value = {
            // the canonical row: each lane sorts its part and keeps its first two; the master merges and keeps two
            "{2,1,4,3}.order()==[{2}_]                  % branch(start().order().select(),start().order().select()).kmerge().select()                         % [1,2]",
            // a selector rides into both folds: descending runs merge descending, so the top two are the largest
            "{5,3,9,1,7}.order(neg())==[{2}_]           % branch(start().order(neg()).select(),start().order(neg()).select()).kmerge(neg()).select()     % [9,7]",
            // the floatable run before the cut ships with it, and the select still follows the fold
            "{1,2,3,4,5,6}.plus(10).order()==[{3}_]     % branch(start().plus(10).order().select(),start().plus(10).order().select()).kmerge().select()   % [11,12,13]",
            // a computed bag: the effect runs home, the branch deals what it hands on, and each lane is order.select
            "{4,2,3,1}.print(_).order()==[{2}_]         % start().print().branch(order().select(),order().select()).kmerge().select()                      % [1,2]",
            // k = 1 is the minimum
            "{5,3,9,1,7}.order()==[{1}_]                % branch(start().order().select(),start().order().select()).kmerge().select()                         % [1]",
            // k past the end keeps what there is: no lane pads its run, and the master answers the whole merge
            "{2,1}.order()==[{5}_]                      % branch(start().order().select(),start().order().select()).kmerge().select()                         % [1,2]",
            // mass at a position: the literal bulks {2}1 and {2}3, and a position keeps its whole mass, so k counts values
            "{3,1,3,1,2}.order()==[{2}_]                % branch(start().order().select(),start().order().select()).kmerge().select()                         % [{2}1,2]",
            // equals made at run time straddle the lanes: each lane keeps its own 0 and 1, and kmerge adds their mass
            "{1,2,3,4,5,6}.mod(3).order()==[{2}_]       % branch(start().mod().order().select(),start().mod().order().select()).kmerge().select()         % [{2}0,{2}1]",
            // ...and held home the answer is the same (Thm 19): order bulks the equals it meets, as kmerge does
            "{1,2,3,4,5,6}.mod(3).order?hop=0()==[{2}_] % start().mod().order().select()                                                                      % [{2}0,{2}1]",
            // two top-ks in one job (Marko, 2026-11-20): every cut plans its own run, and >- unrolls the lst the first
            // master answers into its integers, which the second cut deals: both sorts ship
            "{1,2,3,4,5}.order(neg())==[{4}_]==[{4}mult(-1)]>-.order(neg())==[{2}_] % branch(start().order(neg()).select(),start().order(neg()).select()).kmerge(neg()).select().select().merge().branch(order(neg()).select(),order(neg()).select()).kmerge(neg()).select() % [-2,-3]",
            // ...and a map after the unroll floats, so it ships with the second sort
            "{1,2,3,4,5}.order(neg())==[{4}_]>-.mult(-1).order(neg())==[{2}_] % branch(start().order(neg()).select(),start().order(neg()).select()).kmerge(neg()).select().merge().branch(mult(-1).order(neg()).select(),mult(-1).order(neg()).select()).kmerge(neg()).select() % [-2,-3]",
            // one member: nothing to deal, so nothing ships and the select runs where it stands
            "7.order()==[{2}_]                          % start().order().select()                                                                            % [7]",
            // ...and so is the lst a fold answers: without >- the second sort meets one obj, the lst, and stays home
            "{1,2,3,4,5}.order(neg())==[{4}_]==[{4}mult(-1)].order(neg())==[{2}_] % branch(start().order(neg()).select(),start().order(neg()).select()).kmerge(neg()).select().select().order(neg()).select() % [[-5,-4,-3,-2]]",
            // a select that MAPS is no projection: pushed into the lanes it would add 1 twice, so it stays home
            "{2,1,4,3}.order()==[{2}+1]                 % branch(start().order(),start().order()).kmerge().select()                                           % [2,3]",
            // k = 0 keeps nothing: on an axis a zero span lays no slot, so the pattern is empty, and it stays home
            "{2,1,4,3}.order()==[{0}_]                  % branch(start().order(),start().order()).kmerge().select()                                           % [,]",
            // ...and an empty bag ships as any bag does: no lane is dealt a member, and kmerge of no runs is the empty lst,
            // what the same sort answers held home (order of nothing is [,])
            "{2,1,4,3}.order()==[{0}_]>-.order()==[{1}_] % branch(start().order(),start().order()).kmerge().select().merge().branch(order().select(),order().select()).kmerge().select() % [,]",
            "{1,2}.is(gt(5)).order()>-.order()==[{1}_]   % branch(start().is().order(),start().is().order()).kmerge().merge().branch(order().select(),order().select()).kmerge().select() % [,]",
    }, delimiter = '%')
    void test_K_limit_topk_push_down(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }

    // ================================================================================================
    // THE TOPOLOGY VIEW (Marko, 2026-11-20): explain()>>ext/topology/format draws a sharded chain's point coefficients
    // alone: the seed, each lane's share and what its instructions leave, the merge at //local and what the master's
    // instructions leave. A coefficient is the product of the contracts' coefficients read left to right. The lanes
    // carry the network's names for their peers, so these rows read the coefficients and the joints, not the places.
    // ================================================================================================

    @ParameterizedTest
    @CsvSource(value = {
            // fifteen dealt to two lanes, eight and seven, each summed to one; the two merge at home and sum to one
            "{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15}.sum().explain()>>ext/topology/format.has('[{]15[}]──┤').count()                    % 1",
            "{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15}.sum().explain()>>ext/topology/format.has('─►[{]8[}]──[{]1[}]─┐').count()         % 1",
            "{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15}.sum().explain()>>ext/topology/format.has('─►[{]7[}]──[{]1[}]─┘').count()         % 1",
            "{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15}.sum().explain()>>ext/topology/format.has('├──//local─►[{]2[}]──[{]1[}]').count() % 1",
            // the drawing is appended to the explanation's own table
            "{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15}.sum().explain()>>format.has('├──//local─►[{]2[}]──[{]1[}]').count()             % 1",
            // a map keeps its lane's count and the fold leaves one: start, plus and sum in each lane
            "{1,2,3,4,5,6}.plus(1).sum().explain()>>ext/topology/format.has('─►[{]3[}]──[{]3[}]──[{]1[}]─').count()                 % 1",
            // a span range is a sum of terms, one per exponent: each lane holds the one seed and turns it into its share of
            // the sixteen unrolls. Marko's three-peer run (11-20) drew the seed as {3} and every stage as {1}
            "0.{0,15}plus(1).mult(10).sum().explain()>>ext/topology/format.has('[{]1[}]──┤').count()                                     % 1",
            "0.{0,15}plus(1).mult(10).sum().explain()>>ext/topology/format.has('─►[{]1[}]──[{]11[}]──[{]11[}]──[{]1[}]─┐').count()     % 1",
            "0.{0,15}plus(1).mult(10).sum().explain()>>ext/topology/format.has('─►[{]1[}]──[{]5[}]───[{]5[}]───[{]1[}]─┘').count()     % 1",
            "0.{0,15}plus(1).mult(10).sum().explain()>>ext/topology/format.has('[{]1[}]──[{]1[}]──[{]1[}]──[{]1[}]').count()           % 0",
            "0.{0,15}plus(1).mult(10).sum().explain()>>ext/topology/format.has('├──//local─►[{]2[}]──[{]1[}]').count()                % 1",
            // the branch states what its lanes answer, a lst per lane and never #{*}, so the kmerge after it binds its own
            // dom (lst[{*}A{*}]{*}) and answers one lst
            "{2,1,4,3}.order().explain()>>per_inst>>1>>c_rng                                                                % 1",
            "{2,1,4,3}.order().explain()>>ext/topology/format.has('//local─►[{]2[}]──[{]1[}]').count()                    % 1",
            // two top-ks in one job draw two fans, and the second is dealt what the merge left, a bag of unknown size
            "{1,2,3,4,5}.order(neg())==[{4}_]==[{4}mult(-1)]>-.order(neg())==[{2}_].explain()>>ext/topology/format.has('[{][*][}]──┤').count() % 1",
    }, delimiter = '%')
    void test_topology_explain_extension(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    // ================================================================================================
    // O — broadcast constants. A constant rides as an ARGUMENT of the floatable run (Thm 6: the
    // coefficient floats through non-reduce chains), so it changes nothing about the fan-out: the layout
    // is A's. It is "free today -- it needs no plumbing", which is exactly why it is worth a row: the
    // absence of a special case IS the claim.
    // ================================================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4,5,6}.plus(10).sum()                                    % branch(start().plus().sum(),start().plus().sum()).sum()                          % 81",
            "{1,2,3,4,5,6}.print(_).plus(10).sum()                           % start().print().branch(plus().sum(), plus().sum()).sum()                         % 81",
            "0.{1,6}plus(1).plus(10).sum()                                   % branch(start().plus().plus().sum(),start().plus().plus().sum()).sum()            % 81",
            "0.{1,6}plus(1).plus(10).sum().{1,6}plus(2).prod()               % branch().sum().branch().prod()                                                   % 462307158495",
            "0.{1,6}plus(1).plus(10).sum().print(_).{1,6}plus(2).prod()      % branch().sum().print().branch().prod()                                           % 462307158495"
    }, delimiter = '%')
    void test_O_broadcast_constants(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }

    // ================================================================================================
    // S — master participates. ?place=[local,peer] puts a lane at distance zero: the master folds its OWN
    // slice and ships the rest, instead of only recombining. The emitted shape is A's; what differs is
    // that one of the branch's keys is <~> -- the home lane. The ANSWER must be unchanged, which is the
    // row's real assertion: taking a share must not double-count.
    // ================================================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4,5,6,7,8}.plus(1).sum?place=[local,peer]()   % branch().sum() % 44",
    }, delimiter = '%')
    void test_S_master_participates(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }

    // ================================================================================================
    // T — reference frame migration. The odd one out: NOT a fold. move() relocates the frame of reference
    // (the machine the code runs in), it is law-free, and it is realized by extractMove in the compiler,
    // not by a plan -- the chain is cut at the first host-bearing move and the tail is shipped as a
    // PROGRAM (the quine rule), so the receiver re-plans it and the one dumb step propagates fractally.
    // Its emitted shape is therefore a cut, not a branch, which is why it gets its own
    // assertion rather than a row in the same table.
    // ================================================================================================

    @Disabled("the move seam is pinned by MoveSeamsTest/MoveRemoteTest; the faithful row here needs a per-peer //host alias that a forked cluster can name")
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3}.plus(5).move(//host/a).plus(2).sum()   % start().plus().cut() % 27",
    }, delimiter = '%')
    void test_T_reference_frame_migration(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }

    // ================================================================================================
    // W — embarrassingly parallel. No cut: the run floats to the end of the chain, so right distributivity
    // alone, (a+b)·f = a·f + b·f, is the whole law -- the map, filter and flatmap rings are right
    // distributive. Each place runs the run on its share, and the branch's own merge (its +) is the only
    // recombination: A's layout without a combiner, and the answer is every member of the stream (eight in,
    // eight out for a mapper). An instruction that does not float ends the run; the rest of the chain runs
    // at home over the branch's bag. Floating is Inst.isPure: an instruction handed an effect keeps the run home.
    // ================================================================================================

    @ParameterizedTest
    @CsvSource(value = {
            // the canonical row: one mapper, eight ints in, eight ints out -- the lanes' streams are the answer
            "{1,2,3,4,5,6,7,8}.plus(1)                    % branch(start().plus(),start().plus())                  % {2,3,4,5,6,7,8,9}",
            // a run of mappers ships whole: every lane runs the full run on its share
            "{1,2,3,4,5,6,7,8}.plus(1).mult(2)            % branch(start().plus().mult(),start().plus().mult())    % {4,6,8,10,12,14,16,18}",
            // a filter floats too: each lane keeps its own survivors
            "{1,2,3,4,5,6,7,8}.is(gt(4))                  % branch(start().is(gt(4)),start().is(gt(4)))            % {5,6,7,8}",
            // a flatmapper floats too (merge declares pure): each lane unrolls its own lsts
            "{[1,2],[3,4],[5,6],[7,8]}.merge().plus(1)    % branch(start().merge().plus(),start().merge().plus())  % {2,3,4,5,6,7,8,9}",
            // an effect ends the run: the run ships, and print sees the merged stream at home
            "{1,2,3,4,5,6,7,8}.plus(1).print(_)           % branch(start().plus(),start().plus()).print()          % {2,3,4,5,6,7,8,9}",
            // the index set: one obj and a span range, split by work as V's is, and no fold after the branch
            "start(0).{0,7}plus(1)                        % branch(start().plus(),start().plus())                  % {0,1,2,3,4,5,6,7}",
            // one obj and no index set: nothing to deal, so nothing ships
            "5.plus(1)                                    % start().plus()                                         % 6",
            // a pure filter handed an effect is not pure: the run stays home, and the print runs here, once per member
            "{1,2,3,4,5,6,7,8}.is(print(_).gt(4))         % start().is()                                           % {5,6,7,8}",
            // FLOATABLE.IMPURE.FLOATABLE: the effect brings the members home, and the run after it goes out again, dealt
            "{1,2,3,4,5,6,7,8}.plus(1).print(_).mult(2)   % branch(start().plus(),start().plus()).print().branch(mult(),mult())   % {4,6,8,10,12,14,16,18}",
            "{1,2,3,4,5,6,7,8}.plus(1).is(print(_).gt(4)).mult(2)   % branch(start().plus(),start().plus()).is().branch(mult(),mult())   % {10,12,14,16,18}",
            // IMPURE.FLOATABLE: an effect at the head runs home, and the floatable run after it is still found and dealt
            "{1,2,3,4,5,6,7,8}.print(_).plus(1)           % start().print().branch(plus(),plus())                  % {2,3,4,5,6,7,8,9}",
            "{1,2,3,4,5,6,7,8}.print(_).plus(1).mult(2)   % start().print().branch(plus().mult(),plus().mult())    % {4,6,8,10,12,14,16,18}",
            // FLOATABLE** — an effect nested deep in an argument (plus's arg's arg) keeps the run home; its pure twin ships
            "{1,2,3,4,5,6,7,8}.plus(plus(print(_).mult(0)))   % start().plus()                                     % {2,4,6,8,10,12,14,16}",
            "{1,2,3,4,5,6,7,8}.plus(plus(mult(0)))        % branch(start().plus(),start().plus())                  % {2,4,6,8,10,12,14,16}",
    }, delimiter = '%')
    void test_W_embarrassingly_parallel(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }

    /**
     * W, FLOATABLE** THROUGH A BODY: an instruction whose mtron body holds an effect deep inside it (its plus's plus's
     * print) is not pure, so its run stays home. Defined on THIS machine, where the peers are dialed: a {@code @TestData}
     * frame dials nothing, and a cut from it is refused for that reason rather than for the body's.
     * <p>
     * A user instruction cannot ship yet even when its body is pure: its peers would have to run it (context distribution),
     * so this row holds only the negative case. {@code InstTest#testAnUndeclaredInstructionIsAsPureAsItsBody} pins the
     * body walk both ways.
     */
    @Test
    void test_W_an_instruction_whose_body_holds_an_effect_stays_home() {
        ObjmtronSerializer.parse("noisy_computation?int<=int(){plus(plus(print(_).mult(0)))}@noisy_computation").apply();
        try {
            checkCodeRewrite(LOG, "{1,2,3,4,5,6,7,8}.noisy_computation()", "start().noisy_computation()",
                    "{2,4,6,8,10,12,14,16}", true);
        } finally {
            Machine.write("noisy_computation", noobj());
        }
    }

    // ================================================================================================
    // THE UNWRITTEN ROWS (B, C, F, I, J, L, M, N, P, Q, R). Each is SOUND -- the law holds -- and each
    // needs a premise the emitter does not hold yet, which is why the interpreter DECLINES it to the identity
    // rather than approximating a layout. These stubs WARN rather than fail: "not implemented" is a logged
    // target, not a silent absence, and a red suite is reserved for behaviour that regressed. When a row's
    // lowering lands, replace its body with that row's parameterized test in the three-column shape above.

    /**
     * I -- span/route interpreter (infrastructure): plan ↦ layout. {@code PlanInterpreter} lowering
     * {@code plan.routes()} plus a real span {@code [(lo, hi, role)]}.
     */
    @Test
    void test_I_span_route_interpreter() {
        LOG.warn("menu row I (span/route interpreter) currently not implemented");
    }

    /**
     * J -- algebraic aggregates: mean ≡ (sum, count)·div. Needs tuple partials plus a FINALIZER that runs once
     * at the master (Thm 21 keeps the finalizer a reducer, so it never ships).
     */
    @Test
    void test_J_algebraic_aggregates() {
        LOG.warn("menu row J (algebraic aggregates) currently not implemented");
    }

    /**
     * L -- mergeable sketches: ĥ(a ⊎ b) = ĥ(a) ⊕ ĥ(b). Plugs into A / E / H with no new lowering, once the
     * sketch declares its monoid theory.
     */
    @Test
    void test_L_mergeable_sketches() {
        LOG.warn("menu row L (mergeable sketches) currently not implemented");
    }

    /**
     * M -- matrix multiplication: A·(B+C) = A·B + A·C. A decomposition into O (addressed union, rows) + A
     * (matrix sum, inner k) + G (blocks, 2-D) rather than one new rule.
     */
    @Test
    void test_M_matrix_multiplication() {
        LOG.warn("menu row M (matrix multiplication) currently not implemented");
    }

    /**
     * N -- iterative / BSP: x* = x ∨ f(x) ∨ f²(x) ∨ ⋯. Needs a superstep — the emitted layout wrapped in
     * {@code repeat}/{@code loop}, with the combiner as the barrier between rounds.
     */
    @Test
    void test_N_iterative_bsp() {
        LOG.warn("menu row N (iterative / BSP) currently not implemented");
    }

    /**
     * P -- computed seeds: [0, n) = ⊎_i index_i. Needs {@code ?split=index}: ship a count and an RNG seed per
     * peer and NO data at all (O(1) message per peer).
     */
    @Test
    void test_P_computed_seeds() {
        LOG.warn("menu row P (computed seeds) currently not implemented");
    }

    /**
     * Q -- monad migration: authority(m) — ship the monad, not the value. C per step, so the continuation
     * ships instead of the value.
     */
    @Test
    void test_Q_monad_migration() {
        LOG.warn("menu row Q (monad migration) currently not implemented");
    }

    /**
     * R -- durable results: id : partial ↦ address. Needs the {@code space} shape plus a
     * {@code shard_result::[authority, round, status, value]} rec written at an addressed result space.
     */
    @Test
    void test_R_durable_results() {
        LOG.warn("menu row R (durable results) currently not implemented");
    }

    /**
     * U -- profile collation: p̂(a ⊎ b) = p̂(a) ⊕ p̂(b). A report is a commutative monoid (its places index by machine,
     * its figures add or fold), so a profile distributes for the same reason A does: each lane ends in
     * {@code profile()} and answers its own report, and the master answers ONE profiling::T, its own run timed around
     * the branch (so its apply holds the gather wait no lane can report), with each lane's report under {@code place},
     * keyed by the machine that made it.
     * <p>
     * THE REWRITE FORM IS ONE INSTRUCTION: the plan ends the master's run in a bare {@code profile_collate()}, and the
     * {@code profile_analysis} rule captures the run before it and mints the collator over it. What the collator
     * captured must END IN THE BRANCH: were the analysis to run before {@code law_shard}, it would capture the
     * undistributed chain (start, plus, sum) and describe a computation that never happened, in a report well formed
     * either way. Only the rewriter orders the two rules (the capture rule's ?zindex runs it last), and this assertion is what notices.
     * <p>
     * THE RESULT IS THE NEST'S SHAPE, folded bottom-up as each node collates: the depth, the leaves (how many, and the
     * objs their runs carried, a coefficient sum, so the bulked {@code {2}3} counts twice) and the places per level.
     * Every axis a run factors collates: the seed's members, a computed bag the branch deals, and the run axis's
     * unrolls (V), where a lane's objs are the terms it unrolled. The format, the user's {@code >>format}, opens with
     * the master's header, leads with the summary over the root's places, and names every place.
     * <p>
     * THE TREE IS {@link TreeShardTransportTest}'s. Here no node below the root can re-plan: the forked peers mount no
     * category and dial no one, and {@code ?place=[local,peer]} lands both lanes on the peers (no home lane), so the
     * master's own lane cannot be an interior node either.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // the star: two peers, a part each
            "{1,3,3,4,5}.plus(10).sum().profile()              % /m/inst/profile_collate() % [depth=>1,leaf=>[count=>2,c_sum=>5],level=>[2]]",
            // a computed seed: a filter handed an effect stays home (A's row), so the master's run is start.is.branch,
            // and the branch deals the survivors when it runs
            "{1,2,3,4,5,6}.is(print(_).gt(2)).sum().profile()  % /m/inst/profile_collate() % [depth=>1,leaf=>[count=>2,c_sum=>4],level=>[2]]",
            // the run axis (V): one seed copied to both lanes, the unrolls {0,3} split by work, so a leaf's objs are the
            // terms it unrolled (3 and 1), not the one seed its start answers
            "start(0).{0,3}plus(1).sum().profile()             % /m/inst/profile_collate() % [depth=>1,leaf=>[count=>2,c_sum=>4],level=>[2]]",
    }, delimiter = '%')
    void test_U_profile_collation(final String code, final String expectedRewriteForm, final String expectedResult) {
        // COLUMN 2 -- THE REWRITE FORM: the collator alone, over the master's run, which ends in the branch
        final Code compiled = Machine.parse(code).asCode().rewrite().asCode();
        checkEquality(LOG, lst(Machine.parse(expectedRewriteForm).asCode().insts().stream().map(inst -> (Obj) uri(inst.tid().basePath())).toList()),
                lst(compiled.insts().stream().map(inst -> (Obj) uri(inst.tid().basePath())).toList()), true);
        checkEquality(LOG, uri(BRANCH_INST_TID), uri(compiled.insts().getFirst().arg(0).asCode().insts().getLast().tid().basePath()), true);
        // COLUMN 3 -- THE NEST: one profiling::T at the root, the master's own run ending in its branch
        final Rec report = Machine.evaluate(Machine.parse(code).asCode()).asRec();
        checkEquality(LOG, uri(PROFILING_TID), uri(report.tid().basePath()), true);
        checkEquality(LOG, uri(BRANCH_INST_TID.name()), report.at(uri(PER_INST)).elements().reduce((a, b) -> b).orElseThrow().asRec().at(uri(NAME)), true);
        checkEquality(LOG, ObjmtronSerializer.parse(expectedResult.trim()), rec(
                uri(DEPTH), report.at(uri(DEPTH)),
                uri(LEAF), rec(uri(COUNT), report.at(uri(f(LEAF).extend(COUNT))), uri(C_SUM), report.at(uri(f(LEAF).extend(C_SUM)))),
                uri(LEVEL), lst(report.at(uri(LEVEL)).elements().map(level -> (Obj) level.asRec().at(uri(COUNT))).toList())), true);
        // EVERY PLACE AT EVERY LEVEL: a profiling::T keyed by the machine that made it, naming its machine, its insts and
        // its stages; the critical path starts at one of the root's places
        final List<Rec> nest = new ArrayList<>(List.of(report));
        for (int i = 0; i < nest.size(); i++) {
            final Obj places = nest.get(i).at(uri(PLACE));
            if (!places.isNoObj())
                places.asRec().<Rel>elements().forEach(place -> {
                    checkEquality(LOG, place.first(), uri(place.second().asRec().at(uri(MACHINE)).uriValue().pathEncoding()), true);
                    checkEquality(LOG, bool(true), bool(place.second().asRec().at(uri(INSTS)).isInt()), true);
                    checkEquality(LOG, bool(true), bool(place.second().asRec().at(uri(f(STAGE).extend(APPLY).extend(MIN))).isReal()), true);
                    nest.add(place.second().asRec());
                });
        }
        checkEquality(LOG, bool(true), bool(report.at(uri(PLACE)).asRec().<Rel>elements()
                .anyMatch(place -> place.first().equals(report.at(uri(PATH)).elements().findFirst().orElseThrow()))), true);
        // THE FORMAT, as a user asks for it: every place's machine, and the summary over the root's places
        final Obj format = Machine.evaluate(Machine.parse(code + ">>format").asCode());
        LOG.debug("profile of %s: %s", code, format);
        checkEquality(LOG, bool(true), bool(format.isStr()), true);
        nest.stream().skip(1).forEach(place -> checkEquality(LOG, bool(true),
                bool(format.strValue().contains(place.at(uri(MACHINE)).uriValue().pathEncoding().toString())), true));
        for (final String line : List.of("master", "gathered in", "straggler", "imbalance", "efficiency", "tree", "critical"))
            checkEquality(LOG, bool(true), bool(format.strValue().contains(line)), true);
    }

    /**
     * AN ABSENT SELECTOR IS ADMITTED, SO THE ARGLESS {@code order()} SHARDS AS {@code order(_)} DOES. The {@code ?decq}
     * claim for {@code order} declares the derivation {@code order(B{?}) ↦ split([id]).kmerge(B{?})}: a call passing no
     * selector binds {@code B} to noobj, which {@code B{0,1}} admits (Marko 2026-11-08: a default is coefficient
     * admission, not an arity-keyed claim), so {@code Law.Helper.combiner} reads {@code kmerge()} off the derivation and
     * the vertex gets its {@code law_shard} out-edge. A passed selector is carried into the merge: the {@code neg()} row's
     * runs are descending, and a {@code kmerge} merging them in natural order would interleave them. Declaring
     * {@code order} self-combining would still be UNSOUND: the master would sort the sorted lsts instead of merging their
     * runs.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4,5}.plus(10).order(_)                               % branch(start().plus(10).order(),start().plus(10).order()).kmerge()                        % [11,12,13,14,15]",
            "{1,2,3,4,5}.plus(10).order()                                % branch(start().plus(10).order(),start().plus(10).order()).kmerge()                        % [11,12,13,14,15]",
            "{1,2,3,4,5}.plus(10).order(neg())                           % branch(start().plus(10).order(neg()),start().plus(10).order(neg())).kmerge(neg())         % [15,14,13,12,11]",
            //"{1,2,3,4,5}.plus(10).order(neg()).select({5}plus(1)).sum()  % branch().kmerge().select().sum()         % [15,14,13,12,11]",
            "{1,2,3,4,5}.plus(10).order(neg()).merge().plus(1).sum()     % branch(start().plus(10).order(),start().plus(10).order()).kmerge().merge().branch(plus(1).sum(),plus(1).sum()).sum()                                                  % 70",
    }, delimiter = '%')
    void test_order_recombines_with_or_without_a_selector(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }
}
