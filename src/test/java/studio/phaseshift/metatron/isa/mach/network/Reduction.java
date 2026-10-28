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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.Tokens.CODE;
import static studio.phaseshift.metatron.isa.m.mInstSet.BLOCK_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SELECT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The reduction (master/slave) reading of a {@link DistributedComputation}: one local master whose code carries the
 * fold, and 1..N remote slaves whose code carries a round-robin slice and the reducer. This is the first topology
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
     * The shipped members of the fan-out {@code select}: one virtual route per lane, in lane order — or empty when the
     * code carries no such {@code select}.
     * <p>
     * THE MARKER IS THE EMITTER'S OWN PREFIX, not "the first select anywhere". {@code PlanInterpreter.shard} emits
     * exactly {@code start([…]).select(fan-out).select(>>rhs).merge.…}, so a shard-planned code has {@code start} at 0
     * and the fan-out {@code select} at 1, and the gather select below ships nothing. A user {@code select} in a chain
     * the plan declined must NOT read as a fan-out: its members are not routes, and {@link #slaves(Code)} would then
     * walk the start seed and fail to answer a code. That is what tells {@code slaves}/{@code routes} to read no lanes
     * rather than throw.
     */
    private static List<Obj> shipped(final Code master) {
        final List<Inst> insts = master.insts();
        if (2 > insts.size()
                || !START_INST_TID.equals(insts.getFirst().tid().basePath())
                || !SELECT_INST_TID.equals(insts.get(1).tid().basePath()))
            return List.of();
        return insts.get(1).arg(0).asLst().lstValue();
    }

    /**
     * Peel the slave codes out of a {@code law_shard} master: one per route, un-quoted from whatever
     * {@code block(...)} nesting there is (a law_shard slave ships bare, so usually none). The order matches
     * {@link #routes(Code)}. Empty when the code was not shard-planned.
     */
    public static List<Code> slaves(final Code master) {
        if (shipped(master).isEmpty())
            return List.of();
        final List<Code> slaves = new ArrayList<>();
        for (final Obj summand : master.insts().getFirst().arg(0).asLst().lstValue()) {
            Obj inner = summand;
            while (true) {
                if (inner.isCode()) {
                    final Code code = inner.asCode();
                    if (1 != code.insts().size() || !BLOCK_INST_TID.equals(code.insts().getFirst().tid().basePath()))
                        break;
                    inner = code.insts().getFirst().arg(0);
                } else if (inner.isInst() && BLOCK_INST_TID.equals(inner.asInst().tid().basePath())) {
                    inner = inner.asInst().arg(0);
                } else {
                    break;
                }
            }
            slaves.add(inner.asCode());
        }
        return slaves;
    }

    /**
     * One shipped route's {@code eval}: a fan-out route is {@code virtual::[code=>eval(<target>)]}, so the ship is the
     * thread's {@code code} slot, peeled off that rather than off the select member itself (a route that never wrapped
     * is a bare inst, and answers as itself).
     */
    public static Inst ship(final Obj route) {
        final Obj eval = route.isRec() && !route.asRec().at(uri(CODE)).isNoObj() ? route.asRec().at(uri(CODE)) : route;
        return eval.isObjCall() ? eval.asCall().insts().getFirst() : eval.asInst();
    }

    /**
     * Peel the routing targets in peer order out of a {@code law_shard} master's fan-out {@code select}. A target is
     * the addressless {@code eval()}'s absent argument — {@code null} — which makes that lane the local place; every
     * other lane answers at its target's authority. Empty when the code was not shard-planned.
     */
    public static List<fURI> routes(final Code master) {
        return shipped(master).stream()
                .map(Reduction::ship)
                .map(eval -> eval.args().isEmpty() ? null : eval.arg(0).uriValue())
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
