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
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.HOP;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import studio.phaseshift.metatron.isa.m.mach.type.Network;

/**
 * The emitter's equivalence obligation (§12.5): a {@code law_shard} lowered over a loop peer — the local transport,
 * no forked process — produces the same tree layout the forked-peer suite asserts, and evaluates to the value the
 * undistributed fold answers. The loop transport applies the shipped shard against the local machine, so this is the
 * fast, deterministic guard for the {@code Env}-driven emitter.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
//@Isolated
//@Execution(ExecutionMode.SAME_THREAD)
public class PlacementEquivalenceTest extends AbstractMetatronTest {

    @BeforeEach
    void setup() {
        // typing is not solved across distributed executions yet — run type-check-free, as the forked-peer suite does
        InstSet.importInstSet(f("/m/math"));
        InstSet.importInstSet(f("/m/math/cat"));
        Machine.current().compiler(CatTheoryCompiler.single());
        Typer.CHECKS.forEach(flag -> Typer.Helper.set(flag, false));
    }

    @AfterEach
    void teardown() {
        Machine.current().network().drop(f("loop://localhost:8555"));
        Machine.current().network().drop(f("loop://ginger:8555"));
    }

    /**
     * One loop peer fans the shard out through the local transport, so the distributed layout recombines to the
     * undistributed fold's value. Each row: the fold % its value.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4,5,6}.plus(1).sum() % 27",
            "{1,2,3,4,5,6}.count()     % 6",
    }, delimiter = '%')
    void testShardEquivalentToLocalFold(final String source, final int expected) {
        Network.Helper.connect(Machine.current().network(), f("loop://localhost:8555"));
        final Code compiled = Machine.current().compiler().rewriter()
                .apply(ObjmtronSerializer.parse(source).asCode()).asCode();
        // the emitter fired the tree layout: the master is branch·sum, the lanes seeded with their parts
        assertEquals(List.of(BRANCH_INST_TID, SUM_INST_TID),
                DistributedSweep.layout(compiled).ops(), "the master recombines through the combiner: " + compiled);
        // NO START STANDS HERE: every lane carries its own seed, so the branch takes nothing and is the initial form
        assertTrue(compiled.insts().getFirst().isInitial(), "the seeded branch is the initial form: " + compiled);
        checkEquality(LOG, jnt(expected),
                TransportRewriteHarness.single(TransportRewriteHarness.evaluate(compiled)).clone().selfVID(null), true);
    }

    /**
     * THE ORDERED SHARD: each lane sorts its part and the master k-way merges the runs. A lane answers its run as ONE
     * lst, so the branch's bag of lanes keeps every run whole and {@code kmerge} reads runs, never their slots. Two loop
     * peers, so there are two runs to merge. Each row: the fold % its value.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{5,3,1,4,2}.plus(10).order(_) % [11,12,13,14,15]",
    }, delimiter = '%')
    void testOrderedShardMergesWholeRuns(final String source, final String expected) {
        Network.Helper.connect(Machine.current().network(), f("loop://localhost:8555"));
        Network.Helper.connect(Machine.current().network(), f("loop://ginger:8555"));
        final Code compiled = Machine.current().compiler().rewriter()
                .apply(ObjmtronSerializer.parse(source).asCode()).asCode();
        final DistributedSweep.Layout layout = DistributedSweep.layout(compiled);
        assertEquals(List.of(BRANCH_INST_TID, KMERGE_INST_TID), layout.ops(),
                "the master k-way merges the lanes' runs: " + compiled);
        assertEquals(2, layout.lanes().size(), "two lanes, two runs: " + compiled);
        checkEquality(LOG, ObjmtronSerializer.parse(expected),
                TransportRewriteHarness.single(TransportRewriteHarness.evaluate(compiled)).clone().selfVID(null), true);
    }

    /**
     * THE MID-EXPRESSION CUT (Marko, 2026-11-05: {@code repeat() [[ plus(10).order ]]}): what cannot float stays home —
     * here the repeat that generates the bag — and its output is the seed the branch deals when it runs. The master
     * keeps the prefix, the lanes carry the floatable run and the fold, and no lane holds a part of its own. Two loop
     * peers. Each row: the source % the master's ops % its value.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "0.repeat(code=>+1,until=>loop().?>20,emit=>true).plus(10).order(_) % start,repeat,branch,kmerge % [10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31]",
            "0.repeat(code=>+1,until=>loop().?>20,emit=>true).plus(10).sum()    % start,repeat,branch,sum    % 451",
    }, delimiter = '%')
    void testAComputedPrefixStaysHomeAndFeedsTheBranch(final String source, final String ops, final String expected) {
        Network.Helper.connect(Machine.current().network(), f("loop://localhost:8555"));
        Network.Helper.connect(Machine.current().network(), f("loop://ginger:8555"));
        final Code compiled = Machine.current().compiler().rewriter()
                .apply(ObjmtronSerializer.parse(source).asCode()).asCode();
        final DistributedSweep.Layout layout = DistributedSweep.layout(compiled);
        assertEquals(List.of(ops.trim().split(",")), layout.ops().stream().map(op -> op.name()).toList(),
                "the prefix stays home and the branch takes its bag: " + compiled);
        assertEquals(2, layout.lanes().size(), "one lane per peer: " + compiled);
        assertTrue(layout.lanes().stream().allMatch(lane -> lane.part().isEmpty()),
                "a computed bag is dealt when the branch runs, so no lane holds a part of its own: " + compiled);
        checkEquality(LOG, ObjmtronSerializer.parse(expected),
                TransportRewriteHarness.single(TransportRewriteHarness.evaluate(compiled)).clone().selfVID(null), true);
    }

    /**
     * A LANE IS COMPILED WHERE IT RUNS (Marko, 2026-11-05): compiling the master compiles the master's own chain and
     * never a lane's program. A host-keyed lane ships raw for its peer's compiler, and a machine-keyed lane is
     * compiled by that machine's compiler when it runs, so no instruction of a lane may carry a bound function. The
     * plan's branch and a hand-written one keep their lanes the same way. Each row: the source.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4,5}.plus(10).sum()",
            "{1,2,3,4,5}.branch?<=int{*}&split=part(<loop://localhost:8555> => plus(10).sum()).sum()",
    }, delimiter = '%')
    void testLanesStayRawWhenTheMasterCompiles(final String source) {
        Network.Helper.connect(Machine.current().network(), f("loop://localhost:8555"));
        final Code compiled = Machine.current().compiler().apply(ObjmtronSerializer.parse(source).asCode()).asCode();
        final List<Obj> lanes = compiled.insts().stream()
                .filter(inst -> BRANCH_INST_TID.equals(inst.tid().basePath()) && inst.args().isRec())
                .flatMap(inst -> inst.args().asRec().elements().map(Rel::second))
                .toList();
        assertFalse(lanes.isEmpty(), "the master branches: " + compiled);
        for (final Obj lane : lanes)
            assertTrue(lane.asCall().insts().stream().noneMatch(Inst::hasf),
                    "a lane is compiled where it runs, never by the master: " + lane + " in " + compiled);
    }

    /**
     * THE ALIAS IS STRIPPED TOO — a budget stated with the deprecated {@code ?hops} on a copied body instruction is
     * read by the root (2, so the slave's FOLD states 1) and stripped from every copied instruction: a slave handed
     * to a peer never carries the level above's budget under either spelling, or it would re-plan with 2.
     */
    @Test
    void testAliasedBudgetReadByRootAndStrippedFromSlave() {
        Network.Helper.connect(Machine.current().network(), f("loop://localhost:8555"));
        final Code source = ObjmtronSerializer.parse("{1,2,3,4}.plus?hops=2(1).sum()").asCode();
        assertTrue(source.insts().stream().skip(1).anyMatch(i -> i.tid().hasQ("hops")),
                "the body instruction states the deprecated budget: " + source);
        final Code compiled = Machine.current().compiler().rewriter().apply(source).asCode();
        final DistributedSweep.Layout layout = DistributedSweep.layout(compiled);
        assertFalse(layout.lanes().isEmpty(), "a deprecated budget on a body instruction still shards: " + compiled);
        final DistributedSweep.Lane lane = layout.lane(0);
        assertEquals("1", lane.budget(), "the root read ?hops=2 and shipped one level fewer: " + compiled);
        assertFalse(lane.code().insts().stream().skip(1).limit(lane.code().insts().size() - 2L)
                        .anyMatch(i -> i.tid().hasQ(HOP) || i.tid().hasQ("hops")),
                "no copied instruction but the fold carries the level above's budget: " + lane.code());
    }
}
