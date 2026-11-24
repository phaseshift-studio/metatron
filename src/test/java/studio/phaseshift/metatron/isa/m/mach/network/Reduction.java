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
import studio.phaseshift.metatron.isa.m.mach.machInstSet;
import studio.phaseshift.metatron.isa.m.type.impl.BranchInst;

import java.util.List;

import static studio.phaseshift.metatron.Tokens.HOP;
import static studio.phaseshift.metatron.Tokens.PART;
import static studio.phaseshift.metatron.Tokens.SPLIT;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;

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
     * The fan-out {@code branch}, or null when the code carries none.
     * <p>
     * THE FIRST LAYOUT IS THE FAN-OUT read here ({@code BranchInst#isLayout}: a branch whose lanes are headed by the
     * cuts their moves compile to, or that deals its lhs). {@code PlanInterpreter.shard} emits one per cut whose run
     * ships: at the head when the plan dealt a literal seed into the lanes
     * ({@code branch?<={0}(cut(<p>, start(part)…), …).<combiner>}), after the prefix that stays home when the bag is
     * computed ({@code <prefix>.branch?<=T{*}&split=part(…).<combiner>}), and after each later cut's combiner. The plan
     * never plans around a layout, so a chain it declined holds none it emitted; a test reads a later branch by handing
     * this reader the chain from that branch on.
     */
    private static Inst branch(final Code master) {
        return master.insts().stream().filter(BranchInst::isLayout).findFirst().orElse(null);
    }

    /**
     * The fan-out's lanes, in their order ({@code BranchInst#lanes}), or empty when the code was not shard-planned.
     */
    private static List<BranchInst.Lane> lanes(final Code master) {
        final Inst branch = branch(master);
        return null == branch ? List.of() : BranchInst.lanes(branch);
    }

    /**
     * Each lane's split as the layout states it, in lane order: {@code part} for a lane of weight one, {@code {2}part}
     * for a weight of two (the point on its head), and the branch's own split for a lane of weight zero ({@code copy},
     * or none). Empty when the code was not shard-planned.
     */
    public static List<String> splits(final Code master) {
        final Inst branch = branch(master);
        return null == branch ? List.of() : lanes(master).stream()
                .map(lane -> 0 == lane.weight() ? branch.tid().q(SPLIT) : 1 == lane.weight() ? PART : "{" + lane.weight() + "}" + PART)
                .toList();
    }

    /**
     * The code each lane RUNS where it lands, one per lane and in the same order: the lane's program, with the budget
     * its cut hands on stated on its head, as a place is shipped it (machInstSet.Helper#budgeted). A lane the plan
     * seeded runs exactly its program, {@code start(part).<run>.<fold>}: the plan dealt it that part. A lane of a
     * computed bag is dealt its part when the branch runs, so the plan names none, and its code is its program alone.
     * Empty when the code was not shard-planned.
     */
    public static List<Code> slaves(final Code master) {
        return lanes(master).stream()
                .map(lane -> (Code) code(lane.home() ? lane.program() : machInstSet.Helper.budgeted(lane.program(), lane.place().q(HOP))))
                .toList();
    }

    /**
     * The routing targets in lane order: a lane's place, its query (the budget) aside. The home lane is the local
     * place — {@code null}, as is a lane to another machine of this server's tree; every other lane answers at its
     * place's authority. Empty when the code was not shard-planned.
     */
    public static List<fURI> routes(final Code master) {
        return lanes(master).stream()
                .map(lane -> lane.home() || null == lane.place().host() ? null : lane.place().qLess())
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
