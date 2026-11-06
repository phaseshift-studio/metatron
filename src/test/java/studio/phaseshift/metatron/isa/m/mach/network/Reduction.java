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
import studio.phaseshift.metatron.isa.m.type.Rel;

import java.util.List;

import static studio.phaseshift.metatron.Tokens.SPLIT;
import static studio.phaseshift.metatron.isa.m.mInstSet.BRANCH_INST_TID;
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
     * The keyed lanes of the fan-out {@code branch}, {@code place => program} in key order — or empty when the code
     * carries no such branch.
     * <p>
     * THE FIRST KEYED BRANCH IS THE FAN-OUT. {@code PlanInterpreter.shard} emits exactly one: at the head when the plan
     * dealt a literal seed into the lanes ({@code branch?<={0}(<p> => start(part)…, …).<combiner>}), after the prefix
     * that stays home when the bag is computed ({@code <prefix>.branch?<=T{*}&split=part(…).<combiner>}). The plan
     * never plans around a keyed branch, so a chain it declined holds none it emitted.
     */
    private static List<Rel> lanes(final Code master) {
        return master.insts().stream()
                .filter(inst -> BRANCH_INST_TID.equals(inst.tid().basePath()) && inst.args().isRec())
                .findFirst()
                .map(inst -> inst.args().asRec().elements().toList())
                .orElse(List.of());
    }

    /**
     * Each lane's split as its key states it, in key order — the key's own {@code ?split}, else the branch's, which is
     * every key's default ({@code BranchInst#routes}): {@code part}, {@code {2}part}, {@code copy}. Empty when the code
     * was not shard-planned.
     */
    public static List<String> splits(final Code master) {
        final Inst branch = master.insts().stream()
                .filter(inst -> BRANCH_INST_TID.equals(inst.tid().basePath()) && inst.args().isRec())
                .findFirst().orElse(null);
        return null == branch ? List.of() : lanes(master).stream()
                .map(lane -> lane.first().uriValue())
                .map(key -> key.hasQ(SPLIT) ? key.q(SPLIT) : branch.tid().q(SPLIT))
                .toList();
    }

    /**
     * The code each lane RUNS, one per route and in the same order. A lane the plan seeded runs exactly its program,
     * {@code start(part).<run>.<fold>}: the plan dealt it that part. A lane of a computed bag is dealt its part when the
     * branch runs, so the plan names none, and its code is its program alone. Empty when the code was not
     * shard-planned.
     */
    public static List<Code> slaves(final Code master) {
        return lanes(master).stream()
                .map(lane -> (Code) code(lane.second().asCall().insts()))
                .toList();
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
