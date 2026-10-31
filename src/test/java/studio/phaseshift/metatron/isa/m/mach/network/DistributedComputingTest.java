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
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.m.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.isa.m.web.type.MIME;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
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
 *       Stated as the master's op sequence ({@code start().select().select().merge().sum()}) rather than
 *       the full chain, because the emitted routes carry minted virtual-thread vids
 *       ({@code @~/sys/thread/<id>}) that no row could name: {@code checkCodeRewrite} is called with
 *       {@code checkTIDs=true}, which compares the op sequence and the inst count, not the vids. The
 *       shipped <em>slave</em> codes are asserted structurally by the sibling suites
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
 * <h2>the menu (A–T)</h2>
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
 *   <tr><td>I</td><td>span/route interpreter</td><td>plan ↦ layout (infrastructure)</td><td>{@code [c]==[as(cat:path::T)]==[lower(>>law_shard)]}</td><td></td></tr>
 *   <tr><td>J</td><td>algebraic aggregates</td><td>mean ≡ (sum, count)·div</td><td>{@code {...}.mean()}</td><td></td></tr>
 *   <tr><td>K</td><td>limit / top-k push-down</td><td>take(k)·⊕ = take(k)·⊕·take(k)</td><td>{@code {...}.order(>>).take(k)}</td><td></td></tr>
 *   <tr><td>L</td><td>mergeable sketches</td><td>ĥ(a ⊎ b) = ĥ(a) ⊕ ĥ(b)</td><td>{@code {...}.hll()}</td><td></td></tr>
 *   <tr><td>M</td><td>matrix multiplication</td><td>A·(B+C) = A·B + A·C</td><td>{@code mtrx::[…]}</td><td></td></tr>
 *   <tr><td>N</td><td>iterative / BSP</td><td>x* = x ∨ f(x) ∨ f²(x) ∨ ⋯</td><td>a sharded superstep repeated</td><td></td></tr>
 *   <tr><td>O</td><td>broadcast constants</td><td>ca·1b = 1a·1b·c1 (Thm 6)</td><td>{@code {...}.plus(B).sum()}</td><td>✅</td></tr>
 *   <tr><td>P</td><td>computed seeds</td><td>[0,n) = ⊎_i index_i</td><td>Monte Carlo — a count + an RNG seed per peer</td><td></td></tr>
 *   <tr><td>Q</td><td>monad migration</td><td>authority(m) — ship the monad, not the value</td><td>a remote read mid-chain</td><td></td></tr>
 *   <tr><td>R</td><td>durable results</td><td>id : partial ↦ address</td><td>{@code to(</usr/job/round/shard>)}</td><td></td></tr>
 *   <tr><td>S</td><td>master participates</td><td>⊕_i a_i·r over {master} ∪ peers</td><td>{@code {1,…,8}.plus(1).sum()}</td><td>✅</td></tr>
 *   <tr><td>T</td><td>reference frame migration</td><td>move(a) … move(b) … (machine groupoid)</td><td>{@code {1,2,3}.plus(5).move(//host/a).plus(2).sum()}</td><td>✅</td></tr>
 *   <tr><td>U</td><td>profile collation</td><td>p̂(a ⊎ b) = p̂(a) ⊕ p̂(b), ⊕ = keyed union with times added</td><td>{@code {1,2,3,4,5}.plus(10).sum().profile()>>format}</td><td></td></tr>
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
        // typing is not solved across distributed executions yet -- run type-check-free, as the manual demo did
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
            "{1,2,3,4,5,6}.plus(1).sum()       % start().select().select().merge().sum() % 27",
            // count is DERIVED -- its combiner is sum, so the master's last op is sum, not count
            "{1,2,3,4}.plus(1).count()         % start().select().select().merge().sum() % 4",
            // prod is self-combining: the master re-applies prod
            "{1,2,3,4}.plus(1).prod()          % start().select().select().merge().prod() % 120",
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
    // D — parallel fan-out. Not a fold: it is the transport every shard rides. Each round trip is
    // submitted at once inside a virtual thread, so the master pays max RTT rather than Σ RTT, and the
    // gather reads each thread's future off its rhs slot. The observable is therefore the SAME emitted
    // shape as A, with the routes wrapped in virtual:: and the gather present as a second select.
    // ================================================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4,5,6}.plus(1).sum()       % start().select().select().merge().sum() % 27",
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
            "{1,2,3,4,5,6,7,8}.plus(1).sum?hop={2}2()   % start().select().select().merge().sum() % 44",
            // height 1 is the star -- today's default, stated explicitly
            "{1,2,3,4,5,6,7,8}.plus(1).sum?hop=1()      % start().select().select().merge().sum() % 44",
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
            "{1,2,3,4,5,6}.group([mod(3)=>sum()])  % start().select().select().merge().sum().select() % [0=>9,1=>5,2=>7]",
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
            "{1,2,3,4}.sum?split=copy()   % start().select().select().merge().sum() % 10",
    }, delimiter = '%')
    void test_H_redundant_shards(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }

    // ================================================================================================
    // O — broadcast constants. A constant rides as an ARGUMENT of the floatable run (Thm 6: the
    // coefficient floats through non-reduce chains), so it changes nothing about the fan-out: the layout
    // is A's. It is "free today -- it needs no plumbing", which is exactly why it is worth a row: the
    // absence of a special case IS the claim.
    // ================================================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4,5,6}.plus(10).sum()      % start().select().select().merge().sum() % 81",
    }, delimiter = '%')
    void test_O_broadcast_constants(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }

    // ================================================================================================
    // S — master participates. ?place=[local,peer] puts a lane at distance zero: the master folds its OWN
    // slice and ships the rest, instead of only recombining. The emitted shape is A's; what differs is
    // that one of the routes is the addressless eval() -- the home lane. The ANSWER must be unchanged,
    // which is the row's real assertion: taking a share must not double-count.
    // ================================================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4,5,6,7,8}.plus(1).sum?place=[local,peer]()   % start().select().select().merge().sum() % 44",
    }, delimiter = '%')
    void test_S_master_participates(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }

    // ================================================================================================
    // T — reference frame migration. The odd one out: NOT a fold. move() relocates the frame of reference
    // (the machine the code runs in), it is law-free, and it is realized by extractMove in the compiler,
    // not by a plan -- the chain is cut at the first host-bearing move and the tail is shipped as a
    // PROGRAM (the quine rule), so the receiver re-plans it and the one dumb step propagates fractally.
    // Its emitted shape is therefore a cut, not start/select/select/merge, which is why it gets its own
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
    // THE UNWRITTEN ROWS (B, C, F, I, J, K, L, M, N, P, Q, R). Each is SOUND -- the law holds -- and each
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
     * K -- limit / top-k push-down: take(k)·⊕ = take(k)·⊕·take(k). Needs one general capability — copy the
     * post-cut tail onto the LEAVES, so each shard returns its own top-k before shipping. {@code take(k)}
     * declares no law today, so a law-gated push has nothing to read; the honest gate is the morphism's
     * computed {@code class = retraction} (metadata {@code catRewrite} names as unconsumed).
     */
    @Test
    void test_K_limit_topk_push_down() {
        LOG.warn("menu row K (limit / top-k push-down) currently not implemented");
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
     * U -- profile collation: p̂(a ⊎ b) = p̂(a) ⊕ p̂(b), where ⊕ is keyed union with per-op times added, so the
     * report is a commutative monoid and the row is sound for the same reason A is.
     * <p>
     * {@code profile()} already captures the <em>emitted</em> chain — the master's
     * {@code start/select/select/merge/<fold>}, with the gather's wait visible (measured live: 38.5 ms of a
     * 38.8 ms apply, while every other inst is ≈0) — but the far side is one number, because
     * {@code profile_analysis} captures only the machine it runs on. Collation ships each shard's own report
     * home: same {@code profile_analysis} rule applied to the slave code, and the master's combiner swapped
     * from the fold to a collator.
     * <p>
     * TWO THINGS THE ROW MUST PIN WHEN IT LANDS. (1) ORDER: {@code profile_analysis} REMOVES the chain it
     * captures, so if it ever runs before {@code law_shard} the fold vanishes from the code and the
     * distribution silently degrades to a local-only measurement — same output shape, different meaning. The
     * profiled chain must therefore be asserted to be the 5-op shard layout, not the 3-op original. (2) THE
     * MASTER'S OWN COST: the gather's wall time cannot be reconstructed from the shards, since they run
     * concurrently — so the collated report must carry the master's row too, and
     * {@code Σ(shard times) ≤ master gather wait}.
     */
    /**
     * U -- profile collation: p̂(a ⊎ b) = p̂(a) ⊕ p̂(b), ⊕ = keyed union with per-op times added, so the report
     * is a commutative monoid and the row is sound for the same reason A is.
     * <p>
     * THE ASSERTION THAT MATTERS IS {@code insts}: the profile reports how many instructions the captured
     * program had, so 5 (start, select, select, merge, &lt;collate&gt;) proves profiling saw the SHARDED chain.
     * If {@code profile_analysis} ever runs before {@code law_shard} the fold is gone from the code, the chain
     * stays at 3, and this row is what notices — {@code profile_analysis} removes what it captures, so the two
     * rules are ordered by registration alone and nothing else pins them.
     * <p>
     * DISABLED — A REAL DISAGREEMENT, not a missing feature. The analysis inst is currently minted under the
     * REWRITE tid {@code /m/inst/rewrite/profile_analysis}, while the inst this class documents (and
     * {@code machInstSet.PROFILE_ANALYSIS_TID}) is {@code /m/inst/profile_analysis}. The op-sequence
     * comparison cannot even express the difference: spelling {@code /m/inst/rewrite/profile_analysis()} in
     * the expected form parses back down to {@code /m/inst/profile_analysis}, so no literal in a CSV can name
     * what the implementation emits. Re-enable once the two addresses agree — the rule and the inst it mints
     * should not share an address that the inst namespace cannot spell.
     */
    /**
     * THE INDEX — the spec for the emitter work still outstanding, kept as an executable description rather
     * than prose. TODAY the value is a FLAT profiling::T (keys: format, machine, stage, insts, resolve,
     * rewrite, apply, per_inst, flow, cache) because the slaves return INTEGERS: their code carries no
     * profile() end-cap, so the master has partials to fold, not reports to index.
     * <p>
     * The measurement that closes the question "are sum() and profile() fighting over the cut?": no —
     * {@code per_inst} is [start, select, select, merge, sum], i.e. law_shard fired and profile_analysis
     * captured the SHARD layout. Distribution is not the problem; the missing END CAP is.
     * <p>
     * What must land in PlanInterpreter.shard(): when a chain's tail is profile(), append profile() to every
     * SLAVE (the peer's own compiler then mints profile_analysis over its part and answers a profiling::T),
     * end the master in {@code profile_collate()} instead of the fold, and include the master's OWN report —
     * its gather wait, which no shard can supply — as one more entry of the gathered multiplicity.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{1,3,3,4,5}.plus(10).sum().profile()  % start().select().select().merge().profile_collate() % 2",
    }, delimiter = '%')
    void test_U_profiling_index(final String code, final String expectedRewriteForm, final String expectedResult) {
        // COLUMN 2 -- THE REWRITE FORM: the emitted master chain, op by op. A profiling chain must end in
        // profile_collate, NOT in the fold: once each slave answers a profiling::T there are no partials to
        // recombine, so a layout ending in sum() means the end caps did not reach the leaves.
        final var compiled = Machine.parse(code).asCode().rewrite().asCode();
        final var expected = Machine.parse(expectedRewriteForm).asCode();
        assertEquals(expected.insts().size(), compiled.insts().size(), "op count for " + code + ", was: " + compiled);
        for (int i = 0; i < compiled.insts().size(); i++)
            assertEquals(expected.insts().get(i).tid().basePath(), compiled.insts().get(i).tid().basePath(),
                    "op " + i + " of " + compiled);
        // COLUMN 3 -- THE ROW COUNT of the collated index: one per place a report was made.
        final var collated = Machine.evaluate(compiled);
        assertTrue(collated.isRec(), "the collated profile is one rec keyed by where each report was made, was: " + collated);
        final var rows = collated.asRec().jvm();
        assertTrue(rows.size() >= 2, "one row per place a report was made; keys=" + rows.keySet());
        assertEquals(Integer.parseInt(expectedResult.trim()), rows.size(),
                "collated rows for " + code + ", keys=" + rows.keySet());
        rows.forEach((where, report) -> {
            assertTrue(report.isRec(), "each row is a profiling::T, not a bare time: " + where);
            assertFalse(report.asRec().at(uri("machine")).isNoObj(), "row " + where + " names its machine");
            assertFalse(report.asRec().at(uri("insts")).isNoObj(), "row " + where + " counts the insts it profiled");
            assertFalse(report.asRec().at(uri("stage")).isNoObj(), "row " + where + " carries per-stage timings");
        });
    }

    /**
     * the key the master's own report occupies in a collated profile.
     */
    private static final String SHARD_MASTER = "master";

    /**
     * A KNOWN GAP, PINNED SO IT CANNOT ROT QUIETLY: the argless {@code order()} does not shard, while
     * {@code order(>>)} does.
     * <p>
     * The law row for {@code order} declares a {@code Derivation} keyed on the SELECTOR-bearing spelling
     * ({@code start(A).order(B) ↦ start(A).split([id]).kmerge(B)}) and {@code pure}, and no
     * {@code monoidic}/{@code commutative}. {@code LawTable.combiner} therefore falls through to the
     * derivation, and {@code Derivation.bind} refuses an arity mismatch
     * ({@code derived.args().count() != inst.args().count()}: 1 vs 0). No combiner means no
     * {@code law_shard} out-edge on the vertex -- correct, because the master cannot know the merge key
     * without the selector. Declaring {@code order} self-combining would be UNSOUND, not optimistic: the
     * master would re-apply {@code order} to a stream of sorted lsts and sort the lists instead of merging
     * their runs.
     * <p>
     * The honest fix is to key law rows by (basePath, ARITY) as well as by dom/rng signature, and register
     * the argless derivation {@code start(A).order() ↦ start(A).split([id]).kmerge()}; entries are keyed by
     * signature today, so the argless spelling has nowhere to put its own row. Until then this test records
     * that the selector-less spelling declines, so nobody "fixes" it by making order self-combining.
     */
   /* @Disabled("known gap, deliberately pinned: shrink this to the passing half once law rows are keyed by arity")
    @ParameterizedTest
    @CsvSource(value = {
            // the selector-bearing spelling DOES distribute
            "{1,2,3,4,5}.plus(10).order(>>)   % start().select().select().merge().kmerge() % [11,12,13,14,15]",
            // the argless spelling does NOT: it stays whole at home, so its master is the original chain
            "{1,2,3,4,5}.plus(10).order()     % start().plus().order()                    % [11,12,13,14,15]",
    }, delimiter = '%')
    void test_order_arity_keys_the_combiner(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }*/
}
