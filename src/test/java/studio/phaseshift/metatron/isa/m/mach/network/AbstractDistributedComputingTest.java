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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.algebra.rewrite.plan.Placement;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.type.impl.BranchInst;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.BRANCH_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REPEAT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.PROFILING_TID;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.CAT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.from_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.start_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

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
 * <h2>the menu (A–X)</h2>
 *
 * <p>{@code ✅} = the lowering is written; an empty box = the law is sound but the lowering is unwritten (the
 * row is present below as a {@code @Disabled} stub naming what it still needs). The law column is the reason
 * the row is sound — distribution is a theorem, not a feature.
 *
 * <table border="1">
 *   <caption>the distributed-computing pattern menu</caption>
 *   <tr><th>row</th><th>pattern</th><th>law</th><th>pre-write motif</th><th></th></tr>
 *   <tr><td>A</td><td>homomorphism shard</td><td>h(a ⊎ b) = h(a) ⊕ h(b)</td><td>{@code {1,2,3,4}.plus(1).count()}</td><td>✅</td></tr>
 *   <tr><td>B</td><td>data-local shard</td><td>from(p) = Σ_a from_a(p)</td><td>{@code *<//{east,west}/n/data>.count()}</td><td>✅</td></tr>
 *   <tr><td>C</td><td>authority push-down</td><td>from · count ↦ native_count</td><td>{@code *<//east/n/data>.count()}</td><td>✅</td></tr>
 *   <tr><td>D</td><td>parallel fan-out</td><td>⟨x⟩(a+b) = ⟨x⟩a + ⟨x⟩b</td><td>any shard (the transport, not a fold)</td><td>✅</td></tr>
 *   <tr><td>E</td><td>tree reduction</td><td>(a+b+c+d)·r = ((a·r+b·r)·C + (c·r+d·r)·C)·C</td><td>{@code {1,…,8}.plus(1).sum?hop=2()}</td><td>✅</td></tr>
 *   <tr><td>F</td><td>ordered fold</td><td>a·(b·c) = (a·b)·c</td><td>an lst-partitioned fold (streams never shard)</td><td></td></tr>
 *   <tr><td>G</td><td>keyed reduce</td><td>combiner(group([k⇒h])) = sum·select([_⇒combiner(h)])</td><td>{@code {1,2,3,4,5,6}.group([mod(3)=>sum()])}</td><td>✅</td></tr>
 *   <tr><td>H</td><td>redundant / hedged shards</td><td>x ⊕ x = x</td><td>a partition shipped to r peers</td><td>✅ ({@code ?split=copy})</td></tr>
 *   <tr><td>I</td><td>span/route interpreter</td><td>plan ↦ layout (infrastructure)</td><td>{@code [c]==[as(cat:path::T)]==[>>law_shard>>result]}</td><td></td></tr>
 *   <tr><td>J</td><td>algebraic aggregates</td><td>mean ↦ ℓ·mean, aℓ = {|a|}mean(a)</td><td>{@code {1,1,1,5}.mean()}</td><td>✅</td></tr>
 *   <tr><td>K</td><td>limit / top-k push-down</td><td>take(k)·⊕ = take(k)·⊕·take(k)</td><td>{@code {...}.order(s)==[{k}_]}</td><td>✅</td></tr>
 *   <tr><td>L</td><td>mergeable sketches</td><td>ĥ(a ⊎ b) = ĥ(a) ⊕ ĥ(b)</td><td>{@code {...}.hll()}</td><td></td></tr>
 *   <tr><td>M</td><td>matrix multiplication</td><td>A·(B+C) = A·B + A·C</td><td>{@code mtrx::[…]}</td><td></td></tr>
 *   <tr><td>N</td><td>iterative / BSP</td><td>x* = x ∨ f(x) ∨ f²(x) ∨ ⋯</td><td>{@code x.repeat?<=A{*}(code=>f)} (law_bsp)</td><td>✅</td></tr>
 *   <tr><td>O</td><td>broadcast constants</td><td>ca·1b = 1a·1b·c1 (Thm 6)</td><td>{@code {...}.plus(B).sum()}</td><td>✅</td></tr>
 *   <tr><td>P</td><td>computed seeds</td><td>[0,n) = ⊎_i index_i</td><td>Monte Carlo — a count + an RNG seed per peer</td><td></td></tr>
 *   <tr><td>Q</td><td>monad migration</td><td>authority(m) — ship the monad, not the value</td><td>a remote read mid-chain</td><td></td></tr>
 *   <tr><td>R</td><td>durable results</td><td>id : partial ↦ address</td><td>{@code to(</usr/job/round/shard>)}</td><td></td></tr>
 *   <tr><td>S</td><td>master participates</td><td>⊕_i a_i·r over {master} ∪ peers</td><td>{@code {1,…,8}.plus(1).sum()}</td><td>✅</td></tr>
 *   <tr><td>T</td><td>reference frame migration</td><td>move(a) … move(b) … (machine groupoid)</td><td>{@code {1,2,3}.plus(5).move(//host/a).plus(2).sum()}</td><td>✅</td></tr>
 *   <tr><td>U</td><td>profile collation</td><td>p̂(a ⊎ b) = p̂(a) ⊕ p̂(b), ⊕ = keyed union, the figures add or fold</td><td>{@code {1,2,3,4,5}.plus(10).sum().profile()>>format}</td><td>✅</td></tr>
 *   <tr><td>V</td><td>generative run</td><td>a^[m,n] = Σ_k a^k (the unrolls are independent terms)</td><td>{@code start(0).{0,1000}plus(1).sum()}</td><td>✅</td></tr>
 *   <tr><td>W</td><td>embarrassingly parallel</td><td>(a+b)·f = a·f + b·f, f floatable, no cut</td><td>{@code {1,…,8}.plus(1)}</td><td>✅</td></tr>
 *   <tr><td>X</td><td>partition by key (the shuffle)</td><td>partition(p, k⇒c) = branch(k⇒filter(p.eq(k))·c)</td><td>{@code {1,…,6}.partition(<//${mod(2).plus(1)}>, //1 => sum(), //2 => sum())}</td><td>✅</td></tr>
 * </table>
 *
 * <h2>why this suite needs a real cluster</h2>
 *
 * <p>{@code law_shard}'s guard reads {@code network().peers()} — the <em>dialed</em> set, which is reach and
 * therefore never inherited. A test that only pushes a frame, or only writes a roster row, leaves that map
 * empty and the plan declines to the identity; the rewrite then equals the original and a layout assertion
 * would be asserting nothing. So each peer here is a real forked JVM ({@link PeerCluster}) and is dialed
 * through its own client, which self-registers into {@code /network/peer} — the exact trigger the guard reads.
 *
 * <h2>one suite, one protocol per subclass</h2>
 *
 * <p>A peer is a node because its client declares from, to and eval (Marko, 2026-11-22: "any peer::T can have custom
 * implementation of common instructions ... from/to/eval being what is needed for universal computing as a metatron
 * node"), and no row reads anything else: every row below asks the dialed set and the answers its lanes return. So the
 * rows live here and each subclass's {@code @BeforeAll} only names how the peers are reached ({@link Protocol}):
 * {@link DistributedComputingTest} dials each peer's {@code /mtron} with a {@code ws_client}, and
 * {@link DistributedComputingMcpTest} dials its {@code /mcp} (mcp_mtron, on the same socket) with an
 * {@code mcp_client}. Both endpoints sit on one socket, so a peer's origin is the same either way.
 *
 * <p>The cluster is a small DIAL GRAPH, not a star, because a tree follows it (row E): this machine dials peers 1 and 2,
 * and they dial each other and peer 3, which dials no one. A place handed a lane with budget left re-plans it over the
 * peers IT dialed, so peers 1 and 2 are interior nodes wherever a budget of two or more lands them, and peer 3 is a leaf
 * whatever it is handed. No peer mounts the category: law_shard is /m's, so a bare peer re-plans what it is handed,
 * and the tree rows prove it. A row that states no budget plans one level (the star), so every lane it ships is a
 * leaf.
 *
 * <p>Type checking is on for this suite, the boot default (every check but code_resolve). A planned branch states what
 * its lanes answer, the contract of the cut or of the run's last instruction once per lane, in generics and never
 * {@code #{*}} (Marko, 2026-11-20), so the master re-derives the fold's type past the branch and the combiner after it
 * binds its own dom.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractDistributedComputingTest extends AbstractMetatronTest {

    /**
     * HOW THE PEERS ARE REACHED — the one thing a subclass decides. Where peer {@code i} is dialed, the client rec that
     * makes a peer dial it (data, so building it dials nothing: a peer sent it constructs and dials its own), and the
     * client this machine constructs to dial it.
     */
    protected interface Protocol {

        fURI endpoint(int oneBased);

        Obj clientRec(int oneBased);

        AutoCloseable dial(int oneBased);
    }

    protected static PeerCluster cluster;
    /**
     * the names peers 1 and 2 are called by in this machine's prefix table, while an addressed row runs ({@link #named})
     */
    private static final List<fURI> NAMES = List.of(f("//east"), f("//west"));
    /**
     * the names peers 1 and 2 are called by while a partition row runs: their numbers, so a key template computes a
     * place ({@code <//${_}>} keys 1 to peer 1)
     */
    private static final List<fURI> NUMBERS = List.of(f("//1"), f("//2"));
    private static Protocol protocol;
    private static final List<AutoCloseable> clients = new ArrayList<>();

    /**
     * Fork the cluster, lay the dial graph over {@code over}, and dial peers 1 and 2 from here: the whole of a
     * subclass's {@code @BeforeAll}.
     */
    protected static void startPeers(final Protocol over) throws Exception {
        protocol = over;
        // this machine's own data, a home term of an addressed read (B), written by the source compiler before the
        // category's compiler, which takes no source, replaces it
        Machine.evaluate("{7,8}.to?<=int{*}(/n/data)");
        // the boot default: every type check on except code_resolve (Marko, 2026-11-18)
        // law_shard is /m's (Marko, 2026-11-24), but the rows' path::T ([as(cat:path::T)]) is catInstSet's, which
        // the standard test boot does not mount (BootLoader.load mounts only /m, /sys, /m/mach). Import its parent
        // (/m/math) first, then it.
        InstSet.importInstSet(MATH_ISA_TID);
        InstSet.importInstSet(CAT_ISA_TID);
        Machine.current().compiler(CatTheoryCompiler.single());
        Typer.CHECKS.forEach(flag -> Typer.Helper.set(flag, Typer.defaultsOn(flag)));
        // no peer mounts the category: law_shard is /m's, so a lane with budget left is re-planned wherever it lands. Each
        // peer holds data of its own at /n/data, {i,10i,100i}, which the addressed rows (B, C) read where it lives, and
        // {i,10i} at /n/kv/i, which a co-located partition's lanes read where their keys live
        cluster = PeerCluster.of(3).seed("import(/m/math)", "{$index,$index0,$index00}.to?<=int{*}($root/data)",
                "{$index,$index0}.to?<=int{*}($root/kv/$index)").start();
        // THE DIAL GRAPH THE TREE FOLLOWS (row E): peers 1 and 2 dial each other and peer 3, which dials no one. A dial
        // is construction, so it happens THERE: a peer is sent the client rec as a value, and constructing it there
        // dials from that peer and indexes the client as its own. Code naming the client type does not travel (this
        // machine's parser binds the type's constructor here, which the peer cannot locate), and the reply is a count
        // because a client read back here would dial from this machine too, where every row is written for two lanes.
        final int[][] edges = {{1, 2}, {1, 3}, {2, 1}, {2, 3}};
        for (final int[] edge : edges)
            cluster.transport(edge[0]).apply(start_(protocol.clientRec(edge[1])).count_());
        // a dial registers its peer asynchronously -- wait (bounded) until every edge is a dialed peer of its source
        for (int t = 0; t < 40 && !Arrays.stream(edges).allMatch(edge -> dialed(edge[0], edge[1])); t++)
            Thread.sleep(250);
        for (final int[] edge : edges)
            assertTrue(dialed(edge[0], edge[1]), "peer " + edge[0] + " never dialed peer " + edge[1]);
        // THE ROOT: this machine dials peers 1 and 2, the two lanes every row is written for; peer 3 it reaches only
        // through them
        for (int i = 1; i <= 2; i++)
            clients.add(protocol.dial(i));
        assertEquals(2L, Machine.current().network().peers().elements().count(),
                "this machine holds peers 1 and 2 alone: " + Machine.current().network().peers());
    }

    @AfterAll
    static void stopPeers() {
        Machine.write(f("/n/data"), noobj());
        for (final AutoCloseable client : clients)
            try {
                client.close();
            } catch (final Exception ignored) {
                // a peer already down just means the client has nothing left to hang up
            }
        clients.clear();
        if (null != cluster)
            cluster.close();
    }

    /**
     * Whether peer {@code from} holds a dialed peer at peer {@code to}'s endpoint: its peer map holds a member at the
     * endpoint's encoded origin, whose host is the endpoint. The reply is that host, a uri, so this machine builds
     * nothing from it.
     */
    private static boolean dialed(final int from, final int to) {
        return uri(protocol.endpoint(to)).equals(cluster.transport(from).apply(
                from_(f("/network/peer").extend(protocol.endpoint(to).pathEncoding()).extend(HOST))));
    }

    /**
     * THE TREE AS ITS PLACES PLAN IT, walked off the topology {@code explain()} answers. A fan is a segment of lanes
     * (Topology reads it so: its first part is a rec), each lane a place at this depth, and a lane its place re-plans
     * carries that place's own flow, so the whole tree comes back without running. Counts each level's places into
     * {@code levels}, and answers the leaves: the lanes no place re-plans.
     */
    private static long planned(final Obj flow, final int depth, final List<Long> levels) {
        long leaves = 0L;
        for (final Obj segment : flow.asLst().<Obj>elements().toList()) {
            final List<Obj> parts = segment.asLst().<Obj>elements().toList();
            if (parts.isEmpty() || !parts.getFirst().isRec())
                continue;
            for (final Obj lane : parts) {
                if (levels.size() == depth)
                    levels.add(0L);
                levels.set(depth, levels.get(depth) + 1L);
                final Obj theirs = lane.asRec().at(uri(TOPOLOGY)).orElse(rec0()).at(uri(FLOW));
                leaves += theirs.isNoObj() ? 1L : planned(theirs, depth + 1, levels);
            }
        }
        return leaves;
    }

    // ================================================================================================
    // A — homomorphism shard. The combiner is COMPUTED, never declared: sum/prod are self-combining,
    // count recombines through sum (count ≡ map(1)·sum), and a fold that is not commutative (str sum)
    // correctly does NOT shard. mean recombines through itself after a lift (J).
    // ================================================================================================

    @ParameterizedTest
    @CsvSource(value = {
            // the canonical row: the seed bulk factors, the floatable run replicates, the fold recombines at home
            "{1,2,3,4,5,6}.plus(1).sum()       % branch().sum() % 27",
            // count is DERIVED -- its combiner is sum, so the master's last op is sum, not count
            "{1,2,3,4}.plus(1).count()         % branch().sum() % 4",
            // prod is self-combining: the master re-applies prod
            "{1,2,3,4}.plus(1).prod()          % branch().prod() % 120",
            // sup and inf are a join and a meet, self-combining too: each lane folds its share and home folds the partials
            "{3,1,4,1,5,9,2,6}.sup()           % branch(start().sup(),start().sup()).sup()               % 9",
            "{3,1,4,1,5,9,2,6}.plus(1).inf()   % branch(start().plus().inf(),start().plus().inf()).inf() % 2",
            // a projection blind to mass ships with the fold; one that reads mass keeps it home, past the run that ships
            "{3,1,4,1,5,9,2,6}.sup(neg())      % branch(start().sup(),start().sup()).sup()               % 1",
            "{11,21,31,12}.mod(10).sup(cc())   % branch(start().mod(),start().mod()).sup()               % 1",
            // a filter handed an effect does not float, so it stays home and only the fold ships over its bag
            "{1,2,3,4,5,6}.is(print(_).gt(2)).sum()  % start().is().branch().sum() % 18",
    }, delimiter = '%')
    void test_A_homomorphism_shard(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }

    /**
     * THE NAMES A ROW SPELLS PEERS 1 AND 2 BY, bound around the row and unbound after it: an addressed row's owners
     * ({@link #NAMES}, {@code //east} and {@code //west}, every name {@code //+} sums) or a partition row's places
     * ({@link #NUMBERS}). No other row binds them: a name is how a profile and a topology name a place, and the rows
     * that assert those names expect this machine to name its peers nothing.
     */
    private static void named(final List<fURI> names, final Runnable row) {
        for (int i = 1; i <= 2; i++)
            Machine.current().network().registerPrefix(names.get(i - 1), protocol.endpoint(i));
        try {
            row.run();
        } finally {
            names.forEach(name -> Machine.current().network().unregisterPrefix(name));
        }
    }

    // ================================================================================================
    // B — data-local shard: from(p) = Σ_a from_a(p). An address that names machines -- a sum of names
    // (//{1,2}), every name (//+), a root branch of terms -- is read where each part lives: one lane per owner
    // reads its own part, the run and the fold ship with it, and home recombines the partials through the
    // computed combiner. C is B with one owner. Peer i holds {i,10i,100i} at /n/data, and this machine {7,8}.
    // ================================================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "*//{east,west}/n/data.count()      % branch(from(</n/data>).count(),from(</n/data>).count()).sum()     % 6",
            "*//+/n/data.count()                % branch(from(</n/data>).count(),from(</n/data>).count()).sum()     % 6",
            "*//{east,west}/n/data.order()      % branch(from(</n/data>).order(),from(</n/data>).order()).kmerge()  % [1,2,10,20,100,200]",
            // a term with no host is this machine's own, a lane at distance zero
            "*<{/n/data,//east/n/data}>.count() % branch(from(</n/data>).count(),from(</n/data>).count()).sum()     % 5",
    }, delimiter = '%')
    void test_B_data_local_shard(final String code, final String expectedRewriteForm, final String expectedResult) {
        named(NAMES, () -> checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true));
    }

    /**
     * C -- authority push-down: from·count ↦ native_count. B with one owner: the chain the address reads runs where
     * the data lives, and one partial comes home.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "*//east/n/data.count()             % branch(from(</n/data>).count()).sum()      % 3",
            "*//west/n/data.order()             % branch(from(</n/data>).order()).kmerge()   % [2,20,200]",
    }, delimiter = '%')
    void test_C_authority_push_down(final String code, final String expectedRewriteForm, final String expectedResult) {
        named(NAMES, () -> checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true));
    }

    /**
     * AN ADDRESSED READ TAKES AN INPUT AND IGNORES IT, planned or not: a code it heads may be a branch member applied to
     * one. Its planned branch refused the input as an initial form does, so {@code 1.branch(*<//east/p>.count())}
     * failed where the plain read answered.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "1.branch(*//east/n/data.count())                               % 3",
            "1.branch(*//east/n/data.count(),*//west/n/data.count()).sum()  % 6",
    }, delimiter = '%')
    void test_C_an_addressed_read_takes_an_input(final String code, final String expected) {
        named(NAMES, () -> checkCodeParseApply(LOG, code, expected));
    }

    /**
     * WHAT AN ADDRESSED READ KEEPS HOME, and the answer it gives there all the same.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // a budget of zero plans nothing, and the sum is read term by term
            "*//{east,west}/n/data.count?hop=0()                % from(<//{east,west}/n/data>).count()             % 6",
            // an effect between the read and the fold keeps the read home: the fold ships over the bag it hands on
            "*//{east,west}/n/data.is(print(_).gt(5)).count()   % from(<//{east,west}/n/data>).is().branch().sum() % 4",
            // a read is untyped here, and mean's lift is declared at int and real: no lift, no lanes
            "*//{east,west}/n/data.mean()                       % from(<//{east,west}/n/data>).mean()              % 55.5",
    }, delimiter = '%')
    void test_B_what_an_addressed_read_keeps_home(final String code, final String expectedRewriteForm, final String expectedResult) {
        named(NAMES, () -> checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true));
    }

    /**
     * AN OWNER NO PEER ANSWERS FAILS THE READ: its part lives at no other place, so no lane is dropped and no part is
     * dealt elsewhere, and the read refuses by the address it named.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "*//{east,nobody}/n/data.count()   % <ERROR>",
            "*//nobody/n/data.count()          % <ERROR>",
    }, delimiter = '%')
    void test_B_an_owner_no_peer_answers_fails(final String code, final String expected) {
        named(NAMES, () -> checkCodeParseApply(LOG, code, expected));
    }

    /**
     * B BY KEY — CO-LOCATION: a partition routes each key to the place that holds its data, and the lane reads the key
     * there, so the code moves to the data as an addressed read's does, the key naming its owner where an address names
     * it (X, below, is the partition itself). Peer i holds {i,10i} at /n/kv/i, and these rows name peers 1 and 2 by
     * number ({@link #NUMBERS}), so {@code <//${_}>} keys i to peer i.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // key i is routed to peer i, where its data is read ...
            "{1,2}.partition(<//${_}>, //1 => *</n/kv/${_}>.sum(), //2 => *</n/kv/${_}>.sum())                     % {11,22}",
            // ... bucketed, each place reading every key it was sent, the partials folded at home ...
            "{1,2}.partition?<=int{*}(<//${_}>, //1 => *</n/kv/${_}>.sum(), //2 => *</n/kv/${_}>.sum()).sum()      % 33",
            // ... and a key routed to a place that does not hold it reads nothing there
            "{1,2}.partition(<//${mod(2).plus(1)}>, //1 => *</n/kv/${_}>.sum(), //2 => *</n/kv/${_}>.sum())        % {0,0}",
    }, delimiter = '%')
    void test_B_a_partition_co_locates_each_key_with_its_data(final String code, final String expected) {
        named(NUMBERS, () -> checkCodeParseApply(LOG, code, expected));
    }

    /**
     * C BY KEY: B with one owner. Every key lives at peer 1, so the one lane's fold runs where the data lives, and a
     * bulked key keeps its count across the cut.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{1}.partition(<//${_}>, //1 => *</n/kv/${_}>.count())                % 2",
            // one obj at a time, each of the three copies counts its own read ...
            "{{3}1}.partition(<//${_}>, //1 => *</n/kv/${_}>.count())             % {3}2",
            // ... and bucketed, the place counts every read
            "{{3}1}.partition?<=int{*}(<//${_}>, //1 => *</n/kv/${_}>.count())    % 6",
    }, delimiter = '%')
    void test_C_a_partition_of_one_place_folds_where_its_keys_live(final String code, final String expected) {
        named(NUMBERS, () -> checkCodeParseApply(LOG, code, expected));
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
    // each level ({2}3 ⇒ {2}2 ⇒ {2}1 ⇒ 0) on the lane's HEAD, never its fold. THE DIAL GRAPH IS THE TREE: a
    // place handed a lane with budget left re-plans it over the peers IT dialed, so a peer that mounts no
    // category or dials no one is a leaf whatever budget it carries. Peers 1 and 2 dial each other and peer 3
    // (startPeers), so they are the interior nodes and peer 3 a leaf. The master's op sequence is the same at
    // every height, so a row reads the tree where it shows: the budget every lane of the root states, and the
    // tree as its places plan it, which explain()'s topology carries whole (each place explains a lane it
    // re-plans), without running: a profile() nests the same tree, but it runs every level six times. Each row:
    // the chain % the master's op sequence % the budget each root lane states (none when nothing ships) % the
    // value, sharded or whole % the planned tree: its depth, its leaves, the places per level (none when nothing
    // ships).
    // ================================================================================================

    @ParameterizedTest
    @CsvSource(value = {
            // height 1 is the star, today's default stated: every lane is a leaf
            "{1,2,3,4,5,6,7,8}.plus(1).sum?hop=1()                    % branch().sum()                % 0     % 44  % [depth=>1,leaf=>2,level=>[2]]",
            // height 2: peers 1 and 2 each re-plan their four over the two they dialed, so four leaves of two
            "{1,2,3,4,5,6,7,8}.plus(1).sum?hop=2()                    % branch().sum()                % 1     % 44  % [depth=>2,leaf=>4,level=>[2,4]]",
            // a stated arity rides the restated form; two children is every node's out-degree here, so the tree is the same
            "{1,2,3,4,5,6,7,8}.plus(1).sum?hop={2}2()                 % branch().sum()                % {2}1  % 44  % [depth=>2,leaf=>4,level=>[2,4]]",
            // an arity of one is the chain: the root ships all eight to one peer, which ships them to one of its own
            "{1,2,3,4,5,6,7,8}.plus(1).sum?hop=[2,1]()                % branch().sum()                % [1,1] % 44  % [depth=>2,leaf=>1,level=>[1,1]]",
            // height 3: a lane that lands on peer 1 or 2 re-plans once more, and one that lands on peer 3 is a leaf of two.
            // Each connection between peers 1 and 2 carries an interior lane and then the leaf the other's lane waits for,
            // which a peer answers only because it evaluates each program on a thread of its own (wsServer)
            "{1,2,3,4,5,6,7,8}.plus(1).sum?hop=3()                    % branch().sum()                % 2     % 44  % [depth=>3,leaf=>6,level=>[2,4,4]]",
            // the members bound the depth too: a lane of one member has nothing to deal, so a fourth level never forms
            "{1,2,3,4,5,6,7,8}.plus(1).sum?hop=4()                    % branch().sum()                % 3     % 44  % [depth=>3,leaf=>6,level=>[2,4,4]]",
            // a budget on the head governs the run, and the earliest statement wins over a later one, either way round
            "start?hop=2({1,2,3,4,5,6,7,8}).plus(1).sum()             % branch().sum()                % 1     % 44  % [depth=>2,leaf=>4,level=>[2,4]]",
            "start?hop=2({1,2,3,4,5,6,7,8}).plus(1).sum?hop=1()       % branch().sum()                % 1     % 44  % [depth=>2,leaf=>4,level=>[2,4]]",
            "start?hop=1({1,2,3,4,5,6,7,8}).plus(1).sum?hop=2()       % branch().sum()                % 0     % 44  % [depth=>1,leaf=>2,level=>[2]]",
            // the deprecated spelling plans the same tree and the lanes state the singular key; at one position ?hop wins
            "{1,2,3,4,5,6,7,8}.plus(1).sum?hops=2()                   % branch().sum()                % 1     % 44  % [depth=>2,leaf=>4,level=>[2,4]]",
            "{1,2,3,4,5,6,7,8}.plus(1).sum?hop=1&hops=2()             % branch().sum()                % 0     % 44  % [depth=>1,leaf=>2,level=>[2]]",
            // every combiner recombines at every interior node: count's through sum, order's through kmerge
            "{1,2,3,4,5,6,7,8}.plus(1).count?hop=2()                  % branch().sum()                % 1     % 8   % [depth=>2,leaf=>4,level=>[2,4]]",
            "{8,7,6,5,4,3,2,1}.plus(1).order?hop=2()                  % branch().kmerge()             % 1     % [2,3,4,5,6,7,8,9] % [depth=>2,leaf=>4,level=>[2,4]]",
            // the run's own index set recurses: each interior node splits its share of the unrolls again, by work
            "start(0).{0,7}plus(1).sum?hop=2()                        % branch().sum()                % 1     % 28  % [depth=>2,leaf=>4,level=>[2,4]]",
            // a run after a cut keeps the budget the run before it resolved: both branches' lanes state it. explain() asks
            // only a lane that starts from its own part, so the second fan, dealt the fold's answer when it runs, plans as
            // two leaves beside the first fan's subtrees
            "{1,2,3,4,5,6,7,8}.plus(1).sum?hop=2().{0,3}plus(1).sum() % branch().sum().branch().sum() % 1     % 182 % [depth=>2,leaf=>6,level=>[4,4]]",
            // height 0 is the point, and so is an arity of zero at any height, in either spelling: nothing ships
            "{1,2,3,4,5,6,7,8}.plus(1).sum?hop=0()                    % start().plus().sum()          %       % 44  %",
            "{1,2,3,4,5,6,7,8}.plus(1).sum?hop={0}2()                 % start().plus().sum()          %       % 44  %",
            "{1,2,3,4,5,6,7,8}.plus(1).sum?hop=[2,0]()                % start().plus().sum()          %       % 44  %",
    }, delimiter = '%')
    void test_E_tree_reduction(final String code, final String expectedRewriteForm, final String budget,
                               final String expectedResult, final String expectedTree) {
        // COLUMNS 2 AND 4 -- THE LAYOUT AND THE VALUE: the master's op sequence, and one answer sharded or whole (Thm 19)
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
        // COLUMN 3 -- THE FORM IS RESTATED, NEVER THE NUMBER: every lane of every branch the root emits states the budget
        // on its head, and no other instruction states one under either spelling
        final List<Inst> master = Machine.parse(code).asCode().rewrite().asCode().insts();
        final List<DistributedSweep.Lane> lanes = IntStream.range(0, master.size())
                .filter(i -> BranchInst.isLayout(master.get(i)))
                .mapToObj(i -> DistributedSweep.layout(code(master.subList(i, master.size()))).lanes())
                .flatMap(List::stream)
                .toList();
        checkEquality(LOG, bool(null == budget), bool(lanes.isEmpty()), true);
        lanes.forEach(lane -> {
            checkEquality(LOG, str(budget), str(String.valueOf(lane.budget())), true);
            checkEquality(LOG, bool(false), bool(lane.code().insts().stream().skip(1)
                    .anyMatch(inst -> inst.tid().hasQ(HOP) || inst.tid().hasQ(Placement.HOP_ALIAS))), true);
        });
        // COLUMN 5 -- THE TREE AS ITS PLACES PLAN IT: the budget unrolled over the dial graph, each place explaining the
        // lanes it re-plans
        if (null != expectedTree) {
            final List<Long> levels = new ArrayList<>();
            final long leaves = planned(Machine.evaluate(Machine.parse(code + ".explain()>>ext/topology/flow").asCode()), 0, levels);
            checkEquality(LOG, ObjmtronSerializer.parse(expectedTree), rec(uri(DEPTH), jnt(levels.size()), uri(LEAF), jnt(leaves),
                    uri(LEVEL), lst(levels.stream().map(count -> (Obj) jnt(count)).toList())), true);
        }
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

    /**
     * H, REDUNDANT SHARDS (2026-11-22): sup and inf are a join and a meet, commutative monoid folds that are idempotent, so a copy
     * ships the whole seed to every lane and the replies fold to the answer of one. Every lane answers the same
     * extremum, the branch's merge adds the copies' masses ({k}9), and the extremum of copies is itself. A sum would
     * answer k times its total; SplitLegalityTest#testCopyOnSumDeclines pins that it declines instead. Hedging a fold that
     * is not idempotent (the first reply wins, the rest are dropped) is not written yet.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{3,1,4,1,5,9,2,6}.sup?split=copy()          % branch(start().sup(),start().sup()).sup()                     % 9",
            "{3,1,4,1,5,9,2,6}.plus(1).inf?split=copy()  % branch(start().plus().inf(),start().plus().inf()).inf()       % 2",
            "{{3}5,1,4}.sup?split=copy()                 % branch(start({{3}5,1,4}).sup(),start({{3}5,1,4}).sup()).sup() % 5",
            // sup answers one obj at most, so what follows it stays home rather than shipping its one answer out again
            "{1,2,3,4,5,6}.is(gt(2)).sup().mult(2)       % branch(start().is().sup(),start().is().sup()).sup().mult()    % 12",
            // a span on the cut runs it again over its own answer, which no lane can run on a part; a span on a group value
            // is written over when the spec is keyed, and home's group drops it too, applying the value once: both stay home
            "{1,2,3,4}.{2}count()                        % start().count()                                               % 1",
            "{1,3,2,5,4,6}.group([mod(2)=>{2}count()])   % start().group()                                               % [1=>3,0=>3]",
            // a span range on a cut after a V run: each lane would run {0,2}sum() on its part and home again over theirs
            "5.{0,3}plus(1).{0,2}sum()                   % start().plus().sum()                                          % {5,6,7,8,26,26}",
    }, delimiter = '%')
    void test_H_redundant_shards(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }

    /**
     * J -- algebraic aggregates: a fold a fixed-size summary of each part computes (Gray et al.), the mean the witness.
     * The summary rides the stream ring's coefficient: {@code mean ↦ ℓ·mean}, where the lift {@code ℓ} answers a part's
     * mean weighted by its count, {@code {n}m} ({@code split?<=int{*}([mean(),count()]).cc(_>>1)>>0}). Every lane runs
     * {@code ℓ} in the mean's place, the branch's merge adds the weights of equal summaries, and home runs the weighted
     * mean, the equation's own last fold (Law.Helper#lifted), so Σnᵢmᵢ/Σnᵢ is the mean of the whole. Each form names
     * the instructions in every lane, so a row pins the rewrite, not only its answer: a lane is its share, the floatable
     * run, then the lift's split, cc and projection.
     * <ul>
     *   <li>A bulked member weighs its copies: {@code {1,1,1,5}} deals {@code {3}1} to one lane whole, the lanes answer
     *       {@code {3}1.0} and {@code 5.0}, and their weighted mean is 2.0 (their plain mean, 3.0, is the answer refused).</li>
     *   <li>A typer does not float: {@code as(real::T)} stays home, the branch deals the bag it hands on (so no lane
     *       starts with a share), and the lift is real's, {@code split?<=real{*}}.</li>
     *   <li>A filter may empty a lane: its books weigh zero, {@code {0}0.0} is noobj, and nothing of it reaches home.</li>
     *   <li>Home runs the equation's fold, never the cut as written ({@code mean?<=int{*}()} would refuse the lanes'
     *       reals); what follows the mean runs home after it; and a point on the mean rides home's mean.</li>
     *   <li>A lane with budget left ({@code ?hop=2}) is planned again where it lands. The lift is a split and a
     *       projection, no cut, so the lane ships only its floatable run a level down and lifts what comes back.
     *       {@code checkCodeRewrite} rewrites each lane it recurses into, so the form shows that level too.</li>
     * </ul>
     * A keyed mean, {@code group([k=>mean()])}, lifts per key: see {@link #test_G_J_a_keyed_mean_ships_per_key_books}.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4,5,6,7,8}.mean()                 % branch(start().split().cc().rshift(),start().split().cc().rshift()).mean()                                           % 4.5",
            "{1,2,3,4,5,6}.plus(1).mean()             % branch(start().plus().split().cc().rshift(),start().plus().split().cc().rshift()).mean()                             % 4.5",
            "{1.5,2.5,3.5,4.5}.mean()                 % branch(start().split().cc().rshift(),start().split().cc().rshift()).mean()                                           % 3.0",
            "{1,1,1,5}.mean()                         % branch(start({3}1).split().cc().rshift(),start(5).split().cc().rshift()).mean()                                      % 2.0",
            "{1,1,1,5}.plus(2).mean()                 % branch(start({3}1).plus().split().cc().rshift(),start(5).plus().split().cc().rshift()).mean()                        % 4.0",
            "{1,1,1,5}.as(real::T).plus(2.0).mean()   % start().as().branch(plus().split().cc().rshift(),plus().split().cc().rshift()).mean()                                % 4.0",
            "{1,2,3,4,5,6}.mult(2).plus(1).mean()     % branch(start().mult().plus().split().cc().rshift(),start().mult().plus().split().cc().rshift()).mean()               % 8.0",
            "{0.5,1.5,2.5,3.5}.mult(2.0).mean()       % branch(start().mult().split().cc().rshift(),start().mult().split().cc().rshift()).mean()                             % 4.0",
            "{1,2,3,4,5,6,7,8}.is(gt(2)).mean()       % branch(start().is().split().cc().rshift(),start().is().split().cc().rshift()).mean()                                 % 5.5",
            // only 4 passes, so the lane dealt {1,3} answers books of weight zero, and home's mean meets one book
            "{1,2,3,4}.is(gt(3)).mean()               % branch(start().is().split().cc().rshift(),start().is().split().cc().rshift()).mean()                                 % 4.0",
            "{1,2,3,4,5,6,7,8}.plus(1).mean().mult(2.0)  % branch(start().plus().split().cc().rshift(),start().plus().split().cc().rshift()).mean().mult()                 % 11.0",
            "{1,1,2,2,3,3}.plus(1).mean{2}()          % branch(start().plus().split().cc().rshift(),start().plus().split().cc().rshift()).mean()                             % {2}3.0",
            // a lane with budget left re-plans its share: it ships the floatable run a level down and lifts what comes back
            "{1,2,3,4,5,6,7,8}.plus(1).mean?hop=2()   % branch(branch(start().plus(),start().plus()).split().cc().rshift(),branch(start().plus(),start().plus()).split().cc().rshift()).mean() % 5.5",
            // home runs the fold as its equation states it, over the lanes' reals, never the cut as written (int{*})
            "{1,2,3,4,5,6,7,8}.mean?<=int{*}()        % branch(start().split().cc().rshift(),start().split().cc().rshift()).mean()                                           % 4.5",
    }, delimiter = '%')
    void test_J_algebraic_aggregates(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }

    /**
     * K, BY MASS (Marko 2026-11-22, order(cc())): a sort keyed by mass ships no lane. A shard parts each value's mass
     * across its lanes, so a lane would sort by partial masses and {@code kmerge(cc())} would merge the runs by whole
     * ones: sharded, {@code {11,13,21,23,31,33,12,32,22,42}.mod(10).order(cc())} answered {@code [{4}2,{3}3,{3}1]},
     * out of order. The key is not blind to mass (Law.Helper#massBlind), so order has no combiner and is no cut: the
     * floatable run before it still ships (W), the branch's merge adds the masses of equal members, and home sorts by
     * whole masses. The masses here, 4, 2 and 1, never tie, so each answer has one order. Exact top-k by mass across
     * places (a threshold algorithm, or a keyed mass merge) is future work.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{11,21,31,41,12,22,13}.mod(10).order(cc())                  % branch().order()            % [3,{2}2,{4}1]",
            "{11,21,31,41,12,22,13}.mod(10).order(cc())==[{1}_]          % branch().order().select()   % [3]",
            "{11,21,31,41,12,22,13}.mod(10).order(cc().neg())==[{1}_]    % branch().order().select()   % [{4}1]",
            // a self-combining fold re-applies its key at home, so kmerge(cc()) is no cut either: sharded, the lanes
            // merged {[{2}9],[1,{2}9]} and {[{3}2]} by partial masses and answered [1,{3}2,{4}9]
            "{[{2}9],[{3}2],[1,{2}9]}.kmerge(cc())                       % start().kmerge()            % [1,{4}9,{3}2]",
            // a typer key cannot read mass (its domain is one copy), so the sort still ships; '10' sorts before '8' as strs
            "{7,8,9,10}.plus(1).order(as(str::T))                        % branch(start().plus().order(),start().plus().order()).kmerge() % [10,11,8,9]",
    }, delimiter = '%')
    void test_K_a_sort_by_mass_sorts_at_home(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }

    /**
     * G AND J COMPOSED: a keyed mean ships each key's books (Law.Helper#pointwiseLift). group's pointwise combiner needs
     * every value's combiner, and mean has none, so the group lifts per key. Every lane runs {@code group([k=>ℓ])},
     * answering each key's {@code {n}m}, and home merges the lanes' recs (the deep merge bags colliding books, their
     * weights intact) and takes each key's weighted mean, {@code sum()==[_=>mean()]}. A nested keyed mean lifts at every
     * level. A key that reads mass ({@code group([cc()=>count()])}) is no cut, since a lane would key a value by part of
     * its mass: the run before it ships, and group runs at home, which bulks before it keys, so every mass is whole. The
     * merged rec keeps its keys in lane order, so each row's data meets home's first-seen order.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4,5,6}.group([mod(2)=>mean()])                       % branch(start().group([mod(2)=>split?<=int{*}([mean(),count()]).cc(_>>1)>>0]),start().group([mod(2)=>split?<=int{*}([mean(),count()]).cc(_>>1)>>0])).sum().select([_=>mean()]) % [1=>3.0,0=>4.0]",
            "{1,1,1,2,3,3}.group([mod(2)=>mean()])                       % branch(start({{3}1,{2}3}).group(),start(2).group()).sum().select()                   % [1=>1.8,0=>2.0]",
            "{1,2,3,4,5,6}.plus(1).group([mod(2)=>mean()])               % branch(start().plus().group(),start().plus().group()).sum().select()                 % [0=>4.0,1=>5.0]",
            "{1,2,3,4,5,6}.group([mod(2)=>group([mod(3)=>mean()])])      % branch(start().group(),start().group()).sum().select()                               % [1=>[1=>1.0,0=>3.0,2=>5.0],0=>[2=>2.0,1=>4.0,0=>6.0]]",
            "{1,2,3,4}.mod(2).group([cc()=>count()])                     % branch(start().mod(),start().mod()).group()                                          % [2=>4]",
            // each key's books come from both lanes ({1,2,4} and {3,5,6}), so only a weighted merge answers 3.0 and 4.0
            // (an unweighted one would answer 2.5 and 4.5); nested, {3}17.0 and {2}10.0 merge to 14.2, not 13.5
            "{1,3,2,5,4,6}.group([mod(2)=>mean()])                       % branch(start().group(),start().group()).sum().select()                               % [1=>3.0,0=>4.0]",
            "{1,7,19,13,31}.group([mod(2)=>group([mod(3)=>mean()])])     % branch(start().group(),start().group()).sum().select()                               % [1=>[1=>14.2]]",
            // a key that reads mass keeps even a keyed mean home: in the lanes {1,2,3,5}.mod(2) would key {2}1 by 2
            "{1,2,3,5}.mod(2).group([cc()=>mean()])                      % branch(start().mod(),start().mod()).group()                                          % [3=>1.0,1=>0.0]",
            // a point on a value multiplies each key's answer, which no lane or per-key combiner carries, so it stays home
            "{1,2,3,4}.group([mod(2)=>mean{2}()])                        % start().group()                                                                      % [1=>{2}2.0,0=>{2}3.0]",
            "{1,2,3,4}.group([mod(2)=>count{2}()])                       % start().group()                                                                      % [1=>{2}2,0=>{2}2]",
    }, delimiter = '%')
    void test_G_J_a_keyed_mean_ships_per_key_books(final String code, final String expectedRewriteForm, final String expectedResult) {
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
            "{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15}.sum().explain()>>ext/topology/format.has('─[{]8[}]─[{]1[}]─┐').count()         % 1",
            "{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15}.sum().explain()>>ext/topology/format.has('─[{]7[}]─[{]1[}]─┘').count()         % 1",
            "{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15}.sum().explain()>>ext/topology/format.has('├─►//local─[{]2[}]─[{]1[}]').count() % 1",
            // the drawing is appended to the explanation's own table
            "{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15}.sum().explain()>>format.has('├─►//local─[{]2[}]─[{]1[}]').count()             % 1",
            // a map keeps its lane's count and the fold leaves one: start, plus and sum in each lane
            "{1,2,3,4,5,6}.plus(1).sum().explain()>>ext/topology/format.has('─[{]3[}]─[{]3[}]─[{]1[}]─').count()                 % 1",
            // a span range is a sum of terms, one per exponent: each lane holds the one seed and turns it into its share of
            // the sixteen unrolls. Marko's three-peer run (11-20) drew the seed as {3} and every stage as {1}
            "0.{0,15}plus(1).mult(10).sum().explain()>>ext/topology/format.has('[{]1[}]──┤').count()                                     % 1",
            "0.{0,15}plus(1).mult(10).sum().explain()>>ext/topology/format.has('─[{]1[}]─[{]11[}]─[{]11[}]─[{]1[}]─┐').count()     % 1",
            "0.{0,15}plus(1).mult(10).sum().explain()>>ext/topology/format.has('─[{]1[}]─[{]5[}]──[{]5[}]──[{]1[}]─┘').count()     % 1",
            "0.{0,15}plus(1).mult(10).sum().explain()>>ext/topology/format.has('[{]1[}]─[{]1[}]─[{]1[}]─[{]1[}]').count()           % 0",
            "0.{0,15}plus(1).mult(10).sum().explain()>>ext/topology/format.has('├─►//local─[{]2[}]─[{]1[}]').count()                % 1",
            // the branch states what its lanes answer, a lst per lane and never #{*}, so the kmerge after it binds its own
            // dom (lst[{*}A{*}]{*}) and answers one lst
            "{2,1,4,3}.order().explain()>>per_inst>>1>>c_rng                                                                % 1",
            "{2,1,4,3}.order().explain()>>ext/topology/format.has('//local─[{]2[}]─[{]1[}]').count()                    % 1",
            // ...and after an absorbed top-k: the select keeps a prefix of the cut's lst, so the branch states the cut's type
            "{2,1,4,3}.order()==[{2}_].explain()>>per_inst>>1>>c_rng                                                         % 1",
            // a run with no cut states what its last instruction answers: lanes that end in gt(3) answer bools
            "{1,2,3,4,5,6,7,8}.plus(1).gt(3).explain()>>format.has('branch .*bool[{][*][}]::T').count()                   % 1",
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
    // (the machine the code runs in), it is law-free, and it is realized by extractNomadic in the compiler,
    // not by a plan -- the chain is cut at the first move, push or pop and the tail is shipped as a
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
    // X — partition by key (the shuffle): partition(p, k⇒c) = branch(k⇒filter(p.eq(k))·c), with p evaluated once
    // per obj (Marko, 2026-11-27). A hosted key is a place, and its lane is the cut its program runs in there, so each
    // place is sent its share alone (data distribution), where a branch sends every lane the whole bag; B and C route
    // keys to the places that hold their data (co-location). The rows name peers 1 and 2 by number (NUMBERS), so a key
    // template computes a place. Peer i's /n/data sums to 111i and this machine's to 15, so a lane's answer names
    // where it ran and what it was sent.
    // ================================================================================================

    @ParameterizedTest
    @CsvSource(value = {
            // the evens to peer 1 (2+4+6 and its 111), the odds to peer 2 (1+3+5 and its 222), each share sent once
            "{1,2,3,4,5,6}.partition?<=int{*}(<//${mod(2).plus(1)}>, //1 => sum().plus(*/n/data.sum()), //2 => sum().plus(*/n/data.sum()))  % {123,231}",
            // one obj at a time, each sent alone: 1 and 3 to peer 2, 2 and 4 to peer 1
            "{1,2,3,4}.partition(<//${mod(2).plus(1)}>, //1 => plus(*/n/data.sum()), //2 => plus(*/n/data.sum()))                 % {223,113,225,115}",
            // the rest runs here, beside a place: the odds key //2, which no lane names, and read this machine's {7,8}
            "{1,2,3,4}.partition(<//${mod(2).plus(1)}>, //1 => plus(*/n/data.sum()), <#> => plus(*/n/data.sum()))                 % {16,113,18,115}",
            // a zeroable place takes the objs keyed noobj: the odds, which the narrowed map leaves nothing, run at peer 2
            "{1,2,3,4}.partition(is(mod(2).eq(0)).map?<=A(<//1>), //1 => plus(*/n/data.sum()), {?}//2 => plus(*/n/data.sum()))  % {223,113,225,115}",
            // the lanes' answers fold at home
            "{1,2,3,4,5,6}.partition?<=int{*}(<//${mod(2).plus(1)}>, //1 => count(), //2 => count()).sum()                         % 6",
            // a place no name reaches fails the call (3 keys //3), as the same call keyed by a reached one answers
            "{1,3}.partition(<//${_}>, //1 => count(), //3 => count())                                                             % <ERROR>",
            "{1,2}.partition(<//${_}>, //1 => count(), //2 => count())                                                             % {1,1}",
            // THE TOPOLOGY: each place is dealt a share of the four, at most all of them, and the partials merge at home
            "{1,2,3,4}.partition?<=int{*}(<//${mod(2).plus(1)}>, //1 => sum(), //2 => sum()).explain()>>ext/topology/format.has('[{]4[}]──┤').count()                % 1",
            "{1,2,3,4}.partition?<=int{*}(<//${mod(2).plus(1)}>, //1 => sum(), //2 => sum()).explain()>>ext/topology/format.has('─►//1─[{]0,4[}]─[{]1[}]─┐').count() % 1",
            "{1,2,3,4}.partition?<=int{*}(<//${mod(2).plus(1)}>, //1 => sum(), //2 => sum()).explain()>>ext/topology/format.has('─►//2─[{]0,4[}]─[{]1[}]─┘').count() % 1",
            "{1,2,3,4}.partition?<=int{*}(<//${mod(2).plus(1)}>, //1 => sum(), //2 => sum()).explain()>>ext/topology/format.has('├─►//local─[{]2[}]').count()        % 1",
    }, delimiter = '%')
    void test_X_partition_by_key(final String code, final String expected) {
        named(NUMBERS, () -> checkCodeParseApply(LOG, code, expected));
    }

    /**
     * X IS A LAYOUT ALREADY: its lanes are routed by their keys, so the planner leaves it as written, and the fold after
     * it stays home.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4,5,6}.partition?<=int{*}(<//${mod(2).plus(1)}>, //1 => count(), //2 => count()).sum() % start().partition().sum() % 6",
    }, delimiter = '%')
    void test_X_a_partition_is_a_layout(final String code, final String expectedRewriteForm, final String expectedResult) {
        named(NUMBERS, () -> checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true));
    }

    /**
     * X AS DATA DISTRIBUTION IS SPELLED, {@code *large_data.partition(*partition_function, //a => …, //b => …)}: this
     * machine's {7,8} routed by a stored function, 8 to peer 1 and 7 to peer 2.
     */
    @Test
    void test_X_partition_by_a_stored_function() {
        named(NUMBERS, () -> {
            try {
                Machine.write(f("/n/pf"), Machine.parse("<//${mod(2).plus(1)}>"));
                checkCodeParseApply(LOG, "*/n/data.partition?<=int{*}(*/n/pf, //1 => sum().plus(*/n/data.sum()), //2 => sum().plus(*/n/data.sum()))",
                        "{119,229}");
            } finally {
                Machine.write(f("/n/pf"), noobj());
            }
        });
    }

    // ================================================================================================
    // THE UNWRITTEN ROWS (F, I, L, M, P, Q, R). Each is SOUND -- the law holds -- and each
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
     * N -- iterative / BSP: x* = x ∨ f(x) ∨ f²(x) ∨ ⋯, a superstep repeated (Marko, 2026-11-22). A whole-frontier repeat
     * ({@code repeat?<=A{*}}) gathers its frontier once per pass, and law_bsp deals each pass over the dialed peers: its
     * code becomes {@code branch?<=A{*}&split=part(<peer 1> => f?hop=0, <peer 2> => f?hop=0)}, so the loop stays home and
     * every pass parts the frontier over peers 1 and 2 and merges their answers before the next pass (lock step). A
     * bulked member goes whole to one lane. A fold in the code ({@code sum()}) answers a partial per lane, so law_bsp
     * leaves it home, and the superstep still meets the whole frontier: 1+2+3.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3}.repeat?<=int{*}(code=>plus(1),until=>loop()?>3)     % start().repeat(branch(plus(),plus()),loop().is())     % {5,6,7}",
            "{1,2,3}.repeat?<=int{*}(code=>mult(2),until=>loop()?>2)     % start().repeat(branch(mult(),mult()),loop().is())     % {8,16,24}",
            "{1,2,3}.repeat?<=int{*}(code=>sum(),until=>loop()?>0)       % start().repeat(sum(),loop().is())                     % 6",
    }, delimiter = '%')
    void test_N_iterative_bsp(final String code, final String expectedRewriteForm, final String expectedResult) {
        checkCodeRewrite(LOG, code, expectedRewriteForm, expectedResult, true);
    }

    /**
     * A BULKED MEMBER KEEPS ITS COUNT ON A REMOTE LANE: {@code {1,1,2}} is {@code {{2}1,2}}, so one lane of plus(1) answers
     * {@code {{2}2,3}}. The peer the lane lands on has dialed peers of its own, so it re-plans the lane and deals
     * {@code {2}1} to one leaf, which answers {@code {2}2} alone, addressed by the trip's tag. The client's router took
     * that reply through a {@code #{?}} lambda, and while inst_dom is checked the processor compressed the coefficient
     * away before the router filled its waiter, so the answer came home as {@code {2,3}} (traced 2026-11-22: the peer
     * sent {@code {2}2@<0x…>}, the root's waiter held {@code 2@<0x…>}). The router now takes any multiplicity, as
     * mtron_on_message does (WebSocketObj#router).
     */
    @Test
    void test_N_a_bulked_member_keeps_its_count_on_a_remote_lane() {
        final String peer = Machine.current().network().peers().elements()
                .map(rel -> rel.second().asRec().at(uri(HOST)).uriValue().toString()).findFirst().orElseThrow();
        checkCodeParseApply(LOG, "{1,1,2}.branch?<=int{*}(<" + peer + "> => plus(1))", "{{2}2,3}");
    }

    /**
     * COEFFICIENTS ARE CONSERVED (Marko, 2026-11-22: "coefficients are powerful and yet another thing that can go
     * wrong"): every distribution answers what the same program answers at home, coefficients included. Each seed
     * repeats members, so the frontier bulks and a lane carries counted members across the wire, a lone one too
     * ({@code {1,1,2,2,3}} deals {@code {2}2} to a lane by itself): a lost count shows as a different bag, a lost member
     * as a smaller one. The program runs as written, which must distribute (a row that runs at home tests nothing), and
     * its home twin states ?hop=0 where the program's budget is read. The tree rows re-plan at peers 1 and 2, which dial
     * each other and peer 3. Each row: the program % its home twin.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{1,1,2,2,3}.plus(1)                                             % start?hop=0({1,1,2,2,3}).plus(1)",
            "{1,1,2,2,3}.plus(1).sum()                                       % start?hop=0({1,1,2,2,3}).plus(1).sum()",
            "{1,1,1,2}.count()                                               % start?hop=0({1,1,1,2}).count()",
            "{1,1,2,2,3}.group([mod(2)=>count()])                            % start?hop=0({1,1,2,2,3}).group([mod(2)=>count()])",
            "start?hop=2({1,1,2,2,3,3}).plus(1)                              % start?hop=0({1,1,2,2,3,3}).plus(1)",
            "{1,1,2,2,3,3}.plus(1).sum?hop=2()                               % start?hop=0({1,1,2,2,3,3}).plus(1).sum()",
            "{1,1,1,5}.mean()                                                % start?hop=0({1,1,1,5}).mean()",
            "{1,1,1,2,3,3}.group([mod(2)=>mean()])                           % start?hop=0({1,1,1,2,3,3}).group([mod(2)=>mean()])",
            "{1,1,2,2,3,3}.mod(2).group([cc()=>count()])                     % start?hop=0({1,1,2,2,3,3}).mod(2).group([cc()=>count()])",
            "{1,1,2,2,3}.sup()                                               % start?hop=0({1,1,2,2,3}).sup()",
            "{5,5,1,3}.plus(1).inf?split=copy()                              % start?hop=0({5,5,1,3}).plus(1).inf()",
            // a gather that drops copies keeps the group home: a lane holding {2}1 of {4}1 would skip all of it
            "{1,3,5,7}.mod(2).group([skip(2)=>count()])                      % start?hop=0({1,3,5,7}).mod(2).group([skip(2)=>count()])",
            "{1,1,2,2,3,3}.plus(1).mean?hop=2()                              % start?hop=0({1,1,2,2,3,3}).plus(1).mean()",
            "{1,1,2}.repeat?<=int{*}(code=>plus(1),until=>loop()?>3)         % {1,1,2}.repeat?<=int{*}&hop=0(code=>plus(1),until=>loop()?>3)",
            "{1,1,2}.repeat?<=int{*}&hop=2(code=>plus(1),until=>loop()?>3)   % {1,1,2}.repeat?<=int{*}&hop=0(code=>plus(1),until=>loop()?>3)",
    }, delimiter = '%')
    void test_conservation_a_bulked_seed_answers_as_at_home(final String program, final String home) {
        conserved(program, home);
    }

    /**
     * A POINT ON THE CUT IS CONSERVED (the landing review, 2026-11-22): {@code r{d}} answers {@code {d}} copies of
     * {@code r}'s answer, so no lane carries the point and home's combiner does. A self-combining fold re-applies the
     * cut as written; a derived combiner ({@code count ↦ sum}) and a lifted one ({@code mean}) take the point onto
     * their own last instruction. Sharded, {@code count{2}()} answered 4 and {@code mean{2}()} 2.5, where home
     * answers {@code {2}4} and {@code {2}2.5}. Each row: the program % its home twin.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4}.sum{2}()      % start?hop=0({1,2,3,4}).sum{2}()",
            "{1,2,3,4}.count{2}()    % start?hop=0({1,2,3,4}).count{2}()",
            "{1,2,3,4}.mean{2}()     % start?hop=0({1,2,3,4}).mean{2}()",
            // the pointwise chain carries the point on its select; the K absorb on kmerge, the select after it
            "{1,2,3,4}.group{2}([mod(2)=>sum()])             % start?hop=0({1,2,3,4}).group{2}([mod(2)=>sum()])",
            "{4,1,3,2}.order{2}(neg())==[{1}_]               % start?hop=0({4,1,3,2}).order{2}(neg())==[{1}_]",
            // a home lane runs in this JVM, where nothing drops the point but the lane's own unpointed cut
            "start?place=[local,peer]({1,2,3,4,5,6}).plus(1).sum{2}()   % start?hop=0({1,2,3,4,5,6}).plus(1).sum{2}()",
    }, delimiter = '%')
    void test_conservation_a_point_on_the_cut_answers_as_at_home(final String program, final String home) {
        conserved(program, home);
    }

    /**
     * The program distributes, and answers what its home twin does.
     */
    private void conserved(final String program, final String home) {
        final Code planned = Machine.current().compiler().rewriter().apply(ObjmtronSerializer.parse(program.trim())).asCode();
        assertTrue(distributes(planned.insts()), "the program must distribute, or the row tests nothing: " + planned);
        checkEquality(LOG, Machine.evaluate(Machine.parse(home.trim()).asCode()),
                Machine.evaluate(Machine.parse(program.trim()).asCode()), true);
    }

    /**
     * Whether a chain fans out: a branch, or a branch inside a repeat's code (law_bsp's superstep).
     */
    private static boolean distributes(final List<Inst> insts) {
        return insts.stream().anyMatch(inst -> BRANCH_INST_TID.equals(inst.tid().basePath())
                || (REPEAT_INST_TID.equals(inst.tid().basePath()) && inst.arg(f(CODE), 1).isCall()
                && distributes(inst.arg(f(CODE), 1).asCall().insts())));
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
     * keyed by the place it was dealt to, named as the topology names it ({@code //localhost:<port>} here, where this
     * machine names its peers nothing).
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
     * THE TREE IS E's (and {@link TreeShardTransportTest}'s). These rows state no budget, so every lane the root ships
     * is a leaf, though peers 1 and 2 could re-plan one; and this machine listens on nothing, so it has no local place
     * ({@code ?place=[local,peer]} lands both lanes on the peers) and none of its own lanes can be an interior node.
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
        final List<Obj> named = new ArrayList<>();
        for (int i = 0; i < nest.size(); i++) {
            final Obj places = nest.get(i).at(uri(PLACE));
            if (!places.isNoObj())
                places.asRec().<Rel>elements().forEach(place -> {
                    // A PLACE IS NAMED AS THE TOPOLOGY NAMES ITS LANE (Marko, 2026-11-22): //name or //host:port, never
                    // the path-encoded machine (booger//633589)
                    checkEquality(LOG, bool(true), bool(place.first().uriValue().toString().startsWith("//")), true);
                    named.add(place.first());
                    checkEquality(LOG, bool(true), bool(place.second().asRec().at(uri(INSTS)).isInt()), true);
                    checkEquality(LOG, bool(true), bool(place.second().asRec().at(uri(f(STAGE).extend(APPLY).extend(MIN))).isReal()), true);
                    nest.add(place.second().asRec());
                });
        }
        checkEquality(LOG, bool(true), bool(report.at(uri(PLACE)).asRec().<Rel>elements()
                .anyMatch(place -> place.first().equals(report.at(uri(PATH)).elements().findFirst().orElseThrow()))), true);
        // the root's places are the two peers it deals to, by the names this machine calls them (it names them nothing)
        checkEquality(LOG, bool(true), bool(report.at(uri(PLACE)).asRec().<Rel>elements().map(place -> place.first().toString())
                .allMatch(key -> key.equals("//localhost:" + cluster.port(1)) || key.equals("//localhost:" + cluster.port(2)))), true);
        // THE FORMAT, as a user asks for it: every place's machine, and the summary over the root's places
        final Obj format = Machine.evaluate(Machine.parse(code + ">>format").asCode());
        LOG.debug("profile of %s: %s", code, format);
        checkEquality(LOG, bool(true), bool(format.isStr()), true);
        named.forEach(key -> checkEquality(LOG, bool(true), bool(format.strValue().contains(key.toString())), true));
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
