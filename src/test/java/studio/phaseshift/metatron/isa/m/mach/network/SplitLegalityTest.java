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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.algebra.rewrite.Env;
import studio.phaseshift.metatron.algebra.rewrite.PlanInterpreter;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.Law;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.math.cat.catRewrite;
import studio.phaseshift.metatron.isa.m.parser.mFluent;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.InstBuilder;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.SPLIT;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.Tokens.BOOL_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.MERGE_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SELECT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SUM_INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.start_;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import studio.phaseshift.metatron.isa.m.mach.type.Network;

/**
 * {@code ?split} AT THE EMITTER (C7 of {@code distributed-qparams-implementation.md}): the partition axis is READ —
 * chain order, earliest statement, the plan's own route failing that — and only the two kinds whose premise this
 * emitter can check are LOWERED. {@code stream} is the identity (today's stride, byte for byte); {@code copy} is the
 * whole seed on every lane, legal only when the cut's combiner declares {@code idempotent}; and
 * {@code block}/{@code key}/{@code owner}/{@code index} DECLINE to the unchanged chain, because their premises
 * (order, a key, an addressed seed, a generator) are not held here and a different-but-plausible layout would compute
 * an answer the listing does not say.
 *
 * <p><b>Why a fold is built here.</b> The shipped law table declares no idempotent REDUCER: {@code or}/{@code and}
 * on a {@code bool} declare {@code idempotent} but are mappers ({@code bool⇒bool}), so they never reach the cut, and
 * every shardable fold ({@code sum}, {@code count}, the keyed folds) is non-idempotent. The legal-copy row therefore
 * declares one beside its body ({@link InstBuilder#drain}), which is the table's own {@code ?lawq} route — the
 * production gate is exercised for real, with the one fold the tree does not ship.
 *
 * <p><b>Hermetic.</b> {@code loop://} peers only (the {@link PlacementEquivalenceTest} pattern): no forked process and
 * no cluster, and the emitter is lowered as the function of {@code (plan, code, env)} it is (C4's seam).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SplitLegalityTest extends AbstractMetatronTest {

    /** The two loop peers a split row fans out over. */
    private static final fURI PEER = f("loop://localhost:8555");
    private static final fURI GINGER = f("loop://ginger:8555");
    /** A stated home authority — a test VM listens on nothing. */
    private static final fURI MINE = f("loop://mantis:8555");

    /** The whole fold that is OR: idempotent, so replicating its seed onto every lane is sound. Not in the table. */
    private static Inst disjoin;

    @BeforeEach
    void setup() {
        TypeCheck.disable(TypeCheck.values());
        InstSet.importInstSet(f("/m/math"));
        InstSet.importInstSet(f("/m/math/cat"));
        Machine.current().compiler(CatTheoryCompiler.single());
        Network.Helper.connect(Machine.current().network(), PEER);
        Network.Helper.connect(Machine.current().network(), GINGER);
        disjoin = InstBuilder.build()
                .tid(f("/m/inst/disjoin"), "the idempotent disjunction fold")
                .dom(BOOL_TID.maybeSome(), "zero or more bools").rng(BOOL_TID, "one disjoined bool")
                .argLst().seed(bool(false))
                .body((lhs, inst) -> bool(lhs.stream().anyMatch(Obj::boolValue)))
                .law(Law.commutative, Law.monoidic, Law.idempotent, Law.pure)
                .create();
        InstBuilder.drain(disjoin);
    }

    @AfterEach
    void teardown() {
        Machine.current().network().drop(PEER);
        Machine.current().network().drop(GINGER);
    }

    /**
     * STREAM IS THE IDENTITY — no key, the bare kind, and the unbounded coefficient are the SAME emission, byte for
     * byte: today's stride, one part per lane, the layout C6 left. Each row: the stated value (empty for no key).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "'   '",
            "stream",
            "{*}stream",
    }, delimiter = '%')
    void testStreamIsTheIdentity(final String stated) {
        final DistributedSweep.Layout plain = DistributedSweep.layout(lower("start({1,2,3,4,5,6}).plus(1).sum()"));
        final DistributedSweep.Layout splitted = DistributedSweep.layout(
                lower("start" + (stated.isBlank() ? "" : "?split=" + stated) + "({1,2,3,4,5,6}).plus(1).sum()"));
        assertEquals(plain.routes(), splitted.routes(), "?split=" + stated + " ships to the same places: " + splitted.emitted());
        assertEquals(plain.ops(), splitted.ops(), "the master's chain is untouched: " + splitted.emitted());
        assertEquals(parts(plain), parts(splitted), "the same slices, not a second part count: " + splitted.emitted());
        assertEquals(budgets(plain), budgets(splitted), "and the same hop budgets: " + splitted.emitted());
        assertEquals(List.of(START_INST_TID, SELECT_INST_TID, SELECT_INST_TID, MERGE_INST_TID, SUM_INST_TID),
                splitted.ops(), "today's layout, pinned: " + splitted.emitted());
        assertEquals(List.of(3, 3), parts(splitted), "the stride partition divides the seed exactly: " + splitted.emitted());
        checkEquality(LOG, jnt(27), evaluate(splitted.emitted()), true);
    }

    /**
     * COPY ON AN IDEMPOTENT FOLD IS LEGAL — one part, and it is the WHOLE seed, replicated onto every lane; the
     * partials recombine through the same idempotent fold, so the answer is the undistributed one. Idempotence is what
     * makes the replicas free rather than a double count.
     */
    @Test
    void testCopyOnAnIdempotentFoldReplicatesTheSeed() {
        final DistributedSweep.Layout copied = DistributedSweep.layout(lower(disjunction("copy")));
        assertEquals(2, copied.lanes().size(), "copy keeps one lane per place: " + copied.emitted());
        assertEquals(List.of(2, 2), parts(copied),
                "every lane carries the whole seed (both members, no slice): " + copied.emitted());
        checkEquality(LOG, bool(true), evaluate(copied.emitted()), true);
        // THE SAME FOLD UNDER THE DEFAULT PARTITION SLICES: the copy row is the partition, not the fold
        final DistributedSweep.Layout sliced = DistributedSweep.layout(lower(disjunction("stream")));
        assertEquals(List.of(1, 1), parts(sliced), "the stream partition divides the same seed: " + sliced.emitted());
        checkEquality(LOG, bool(true), evaluate(sliced.emitted()), true);
    }

    /**
     * COPY ON A NON-IDEMPOTENT FOLD DECLINES — {@code sum}'s partials would double count if the seed were replicated,
     * so the decision is the IDENTITY: the chain that already stands here, byte for byte, and nothing ships.
     */
    @Test
    void testCopyOnSumDeclines() {
        declines("start?split=copy({1,2,3,4,5,6}).plus(1).sum()");
        declines("start?split=copy({1,2,3,4,5,6}).count()");
    }

    /**
     * A BAG HAS NO ORDER, so {@code block} — contiguous runs, recombined in order — DECLINES: lowering it would
     * reorder the answer. The identity, not a stream relabelled as blocks.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "block",
            "{2}block",
    }, delimiter = '%')
    void testBlockOnABagDeclines(final String stated) {
        declines("start?split=" + stated + "({1,2,3,4,5,6}).plus(1).sum()");
    }

    /**
     * THE KINDS THIS CHUNK DOES NOT LOWER decline too, and each is the unchanged chain: {@code key} needs the
     * bucketing read, {@code owner} an addressed seed, {@code index} a generator (C7b).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "key",
            "owner",
            "index",
    }, delimiter = '%')
    void testTheUnloweredKindsDecline(final String stated) {
        declines("start?split=" + stated + "({1,2,3,4,5,6}).plus(1).sum()");
    }

    /**
     * A PARTITION THAT CANNOT BE READ IS REFUSED, LOUDLY — a decision the author stated and the emitter dropped would
     * compute elsewhere than the listing says. These are the shapes the object grammar leaves behind: a name that is
     * no kind ({@code abc}), a quoted str where a value was required ({@code "ab"}), a product of factors (a
     * {@code lst}, C7b's) and a value the parser cannot read ({@code \x}). Each row: the stated value % the text the
     * refusal must name.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "abc      % ?split=abc",
            "\"ab\"    % ?split=\"ab\"",
            "[block,key] % ?split=[block,key]",
            "\\x       % ?split=\\x",
    }, delimiter = '%')
    void testAnUnreadablePartitionIsRefused(final String stated, final String expected) {
        final Code code = ObjmtronSerializer.parse("start?split=" + stated + "({1,2,3,4}).plus(1).sum()").asCode();
        final MTronException refused = assertThrows(MTronException.class,
                () -> PlanInterpreter.apply(catRewrite.planOf("law_shard"), code, env()),
                "a partition that cannot be read must be refused, not defaulted: " + stated);
        assertTrue(refused.getMessage().contains(expected),
                "the refusal names the key and the text it could not read: " + refused.getMessage());
    }

    /// /////////////////////////////////////////////////////////////////////////////////////
    /// THE ONE LOWERING, THE ENVIRONMENTS AND THE ONE FOLD
    /// /////////////////////////////////////////////////////////////////////////////////////

    /** The disjunction over a bag of bools — {@code {{2}true,{2}false}} is the seed {@code objs} folds them into. */
    private static Code disjunction(final String split) {
        final mFluent<?> chain = start_(objs(bool(true), bool(false), bool(true), bool(false)));
        chain.q(SPLIT, uri(split));
        chain.addInst(disjoin);
        return chain;
    }

    /** The lanes' part sizes, in lane order. */
    private static List<Integer> parts(final DistributedSweep.Layout layout) {
        return layout.lanes().stream().map(lane -> lane.part().size()).toList();
    }

    /** The lanes' stated hop budgets, in lane order. */
    private static List<String> budgets(final DistributedSweep.Layout layout) {
        return layout.lanes().stream().map(DistributedSweep.Lane::budget).toList();
    }

    /**
     * A row that must DECLINE: the emitted code is the chain that was handed in, byte for byte, and no lane ships.
     */
    private static void declines(final String source) {
        final Code original = ObjmtronSerializer.parse(source).asCode();
        final Code emitted = PlanInterpreter.apply(catRewrite.planOf("law_shard"), original, env());
        assertEquals(original.toString(), emitted.toString(), "a decline is the unchanged chain: " + source);
        assertTrue(DistributedSweep.layout(emitted).lanes().isEmpty(), "and nothing ships: " + source);
    }

    /** The one lowering, against the two live loop peers. */
    private static Code lower(final String source) {
        return lower(ObjmtronSerializer.parse(source).asCode());
    }

    /** The same lowering of an already-built chain. */
    private static Code lower(final Code source) {
        return PlanInterpreter.apply(catRewrite.planOf("law_shard"), source, env());
    }

    /** The live peer map and the roster, with a stated home authority: the split rows ship to the peers. */
    private static Env env() {
        final Machine machine = Machine.current();
        return Env.of(machine.network().peers(), machine.network().machines(), java.util.Set.of(MINE));
    }

    /** The value of an emitted shard, read as the harness reads it. */
    private static Obj evaluate(final Code code) {
        return TransportRewriteHarness.single(TransportRewriteHarness.evaluate(code)).clone().selfVID(null);
    }
}
