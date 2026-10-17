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
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.isa.m.mInstSet.BLOCK_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SELECT_INST_TID;

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
     * Peel the slave codes out of a {@code law_shard} master: one per route, un-quoted from their
     * {@code block(block(block(...)))} nesting. The order matches {@link #routes(Code)}.
     */
    public static List<Code> slaves(final Code master) {
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
     * Peel the {@code to(uri)} routing targets in peer order out of a {@code law_shard} master's {@code select}.
     */
    public static List<fURI> routes(final Code master) {
        return master.insts().stream()
                .filter(i -> i.tid().basePath().equals(SELECT_INST_TID))
                .findFirst()
                .orElseThrow()
                .arg(0).asLst().lstValue().stream()
                .map(o -> o.asInst().arg(0).uriValue())
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
