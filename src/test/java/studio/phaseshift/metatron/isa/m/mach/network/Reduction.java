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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.parser.mFluent;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static studio.phaseshift.metatron.Tokens.PART;
import static studio.phaseshift.metatron.Tokens.SPLIT;
import static studio.phaseshift.metatron.isa.m.mInstSet.BRANCH_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.start_;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;

/**
 * The reduction (master/slave) reading of a {@link DistributedComputation}: one local master whose code carries the
 * fold, and 1..N slaves — the branch's lanes — each running its dealt slice through the floatable run and the reducer. This is the first topology
 * interpretation — the {@code law_shard}/{@code PlanInterpreter} peel. The general shape it reads stays topology-free,
 * so peer-to-peer and embarrassingly parallel will be their own readings of the same {@code peers()} list.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Reduction {

    private final Code masterCode;
    private final List<Code> slaveCodes;
    private final Obj complete;
    private final List<Obj> partials;

    private Reduction(final Code masterCode, final List<Code> slaveCodes, final Obj complete, final List<Obj> partials) {
        this.masterCode = masterCode;
        this.slaveCodes = slaveCodes;
        this.complete = complete;
        this.partials = partials;
    }

    /**
     * Interpret a computation as a reduction: the local peer is the master (its result is the fold), the remote peers
     * are the slaves (their results are the partials).
     */
    public static Reduction of(final DistributedComputation computation) {
        final DistributedComputation.PeerRun master = computation.peers().stream()
                .filter(DistributedComputation.PeerRun::isLocal)
                .findFirst()
                .orElseThrow();
        final List<DistributedComputation.PeerRun> slaves = computation.peers().stream()
                .filter(p -> !p.isLocal())
                .toList();
        return new Reduction(
                master.code(),
                slaves.stream().map(DistributedComputation.PeerRun::code).toList(),
                master.result(),
                slaves.stream().map(DistributedComputation.PeerRun::result).toList());
    }

    /**
     * The keyed lanes of the fan-out {@code branch}, {@code place => program} in key order — or empty when the code
     * carries no such branch.
     * <p>
     * THE MARKER IS THE EMITTER'S OWN PREFIX, not "the first branch anywhere". {@code PlanInterpreter.shard} emits
     * exactly {@code <seed>.branch(place => program, …).<combiner>…}, so a shard-planned code has its {@code start} at 0
     * and the keyed branch at 1. A user {@code branch} in a chain the plan declined must NOT read as a fan-out: its
     * members are not the plan's lanes, so {@link #slaves(Code)}/{@link #routes(Code)} read no lanes rather than misread.
     */
    private static List<Rel> lanes(final Code master) {
        final List<Inst> insts = master.insts();
        if (2 > insts.size()
                || !START_INST_TID.equals(insts.getFirst().tid().basePath())
                || !BRANCH_INST_TID.equals(insts.get(1).tid().basePath())
                || !insts.get(1).args().isRec())
            return List.of();
        return insts.get(1).args().asRec().elements().toList();
    }

    /**
     * Each lane's split as its key states it, in key order — the key's own {@code ?split}, else the branch's, which is
     * every key's default ({@code BranchInst#routes}): {@code part}, {@code {2}part}, {@code copy}. Empty when the code
     * was not shard-planned.
     */
    public static List<String> splits(final Code master) {
        final List<Rel> lanes = lanes(master);
        return lanes.isEmpty() ? List.of() : lanes.stream()
                .map(lane -> lane.first().uriValue())
                .map(key -> key.hasQ(SPLIT) ? key.q(SPLIT) : master.insts().get(1).tid().q(SPLIT))
                .toList();
    }

    /**
     * The code each lane RUNS, one per route and in the same order: {@code start(part).program}, exactly as the branch
     * builds it where the lane's key points ({@code BranchInst#lane}). The part is the one the branch deals the lane at
     * run time, by its rule ({@code BranchInst#deal}): member {@code j} of the seed goes to slot {@code j mod W} of the
     * summed weight {@code W}, the slots dealt to the weighted keys in key order, {@code w} apiece ({@code {w}part},
     * one for a bare {@code part}), while a copy key takes the whole seed. Empty when the code was not shard-planned.
     */
    public static List<Code> slaves(final Code master) {
        final List<Rel> lanes = lanes(master);
        if (lanes.isEmpty())
            return List.of();
        final List<Obj> seed = TransportRewriteHarness.seedOf(master);
        final long[] weights = splits(master).stream().mapToLong(split -> {
            final Obj share = ObjmtronSerializer.parse(split);
            return PART.equals(share.uriValue().name()) ? share.c().min() : 0L;
        }).toArray();
        final long total = Arrays.stream(weights).sum();
        final List<List<Obj>> parts = new ArrayList<>();
        for (final long weight : weights)
            parts.add(0 == weight ? new ArrayList<>(seed) : new ArrayList<>());
        for (int j = 0; 0 < total && j < seed.size(); j++) {
            int lane = 0;
            for (long slot = j % total; slot >= weights[lane]; lane++)
                slot -= weights[lane];
            parts.get(lane).add(seed.get(j));
        }
        final List<Code> slaves = new ArrayList<>();
        for (int r = 0; r < lanes.size(); r++) {
            final mFluent<?> slave = start_(objs(parts.get(r)));
            slave.addInsts(lanes.get(r).second().asCall().insts());
            slaves.add(slave);
        }
        return slaves;
    }

    /**
     * The routing targets in lane order: a lane's key, its query (the weight) aside. The home key {@code <~>} is a path,
     * not a host — {@code null}, which makes that lane the local place; every other lane answers at its key's
     * authority. Empty when the code was not shard-planned.
     */
    public static List<fURI> routes(final Code master) {
        return lanes(master).stream()
                .map(lane -> lane.first().uriValue())
                .map(key -> null == key.host() ? null : key.qLess())
                .toList();
    }

    public Code masterCode() {
        return this.masterCode;
    }

    public List<Code> slaveCodes() {
        return this.slaveCodes;
    }

    public Obj complete() {
        return this.complete;
    }

    public List<Obj> partials() {
        return this.partials;
    }
}
