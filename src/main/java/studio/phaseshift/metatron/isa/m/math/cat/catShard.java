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

package studio.phaseshift.metatron.isa.m.math.cat;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import java.util.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;


/**
 * The distribution kernel ({@code law_shard}) — Thm 19, {@code (a+b)·c = (a·c+b·c)·c}: the guard is
 * zero-residue on the shape (forms and laws only) and world-residue on the instantiation (the peer
 * roster), so on a lone machine the rewrite is the identity.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class catShard {

    /**
     * The op addresses the emitted layout references. Neither instruction exists yet: {@code barrier(k)} is the
     * k-ary gather point (the SwarmProcessor side) and {@code to(uri)} ships its summand to the peer at that
     * connection uri ({@code to()} argless = stay local). The rewrite emits them unresolved by address, the way
     * any chain instruction travels before resolution.
     */
    private static final fURI BARRIER_INST_TID = M_ISA_TID.extend(INST).extend("barrier");
    private static final fURI TO_INST_TID = M_ISA_TID.extend(INST).extend("to");

    /**
     * Thm 19, \((a+b) \cdot c = (a \cdot c + b \cdot c) \cdot c\), as a code \(\to\) code rewrite. The guard is
     * zero-residue on the SHAPE (forms and laws only: a seeded chain, an elementwise run, a monoidic commutative
     * reducer at the cut) and world-residue on the INSTANTIATION (the seed's bulk factors the data, the peer
     * roster supplies the targets) -- so on a lone machine, or on a chain the laws do not license, the rewrite is
     * the identity. Emits {@code [master, slave...]==[to(), to(a), ...]}: the lst is the ring's \(+\), select
     * routes each summand, the master gathers the partials behind a barrier and reapplies the reducer, and
     * whatever followed the reducer stays home on the master's chain.
     */
    static Code shard(final Code code) {
        final List<Inst> insts = code.insts();
        // --- the shape guard: [initial, {*}elementwise, reducer] ---
        if (3 > insts.size() || !START_INST_TID.equals(insts.get(0).tid().basePath()))
            return code;                                            // needs a seeded chain
        final List<Obj> seed = insts.get(0).args().lstValue();
        if (2 > seed.size())
            return code;                                            // one summand -- nothing to factor
        final Type carrier = catLiveness.carrier(code);
        int cut = -1;
        for (int i = 1; i < insts.size(); i++) {
            final LawTable.Declared declared = LawTable.declared(insts.get(i).tid().basePath(), carrier);
            if (null == declared)
                return code;                                        // an undeclared op proves nothing -- fail closed
            final Inst.Form form = Inst.Form.of(insts.get(i).tid(declared.tid()));
            if (Inst.Form.reducer == form || Inst.Form.join == form) {
                // Thm 19's license: monoidic (partials regroup) and commutative (order-free merge --
                // without it the barrier must reorder, which is the ordered sibling rule, not this one)
                if (!catLiveness.declares(declared, Law.monoidic) || !catLiveness.declares(declared, Law.commutative))
                    return code;
                cut = i;
                break;
            }
            // Thm 5/6: only an elementwise (floatable) form may be replicated across the factors
            if (Inst.Form.mapper != form && Inst.Form.filter != form && Inst.Form.flatmapper != form)
                return code;
        }
        if (1 > cut)
            return code;                                            // no anchor (Thm 17: nothing blocks)
        // --- the world's contribution: the roster decides the fan-out ---
        final List<Obj> targets = shardTargets();
        if (targets.isEmpty())
            return code;                                            // a lone machine: the rule is the identity
        final int k = Math.min(targets.size(), seed.size());
        // --- emit (a·c + b·c)·c ---
        final Inst reducer = insts.get(cut);
        final List<Obj> summands = new ArrayList<>();
        final List<Obj> routes = new ArrayList<>();
        // the master: gather k partials behind the barrier, reduce once more, then run the tail at home
        final List<Inst> master = new ArrayList<>();
        master.add(instB(BARRIER_INST_TID, lst(jnt(k))));           // barrier(k): the dom coefficient is the partial count
        master.add(reducer);
        master.addAll(insts.subList(cut + 1, insts.size()));
        summands.add(code(master));
        routes.add(instB(TO_INST_TID, lst()));                      // to() -- stay
        // the slaves: one factor of the bulk each, the run replicated, the reducer applied locally
        for (int s = 0; s < k; s++) {
            final List<Obj> part = new ArrayList<>();
            for (int j = s; j < seed.size(); j += k)
                part.add(seed.get(j));                              // round-robin factorization of the bulk
            final List<Inst> slave = new ArrayList<>();
            slave.add(instB(START_INST_TID, lst(part)));
            slave.addAll(insts.subList(1, cut));
            slave.add(reducer);
            summands.add(code(slave));
            routes.add(instB(TO_INST_TID, lst(targets.get(s))));
        }
        // [master, slave...] == [to(), to(a), ...] -- the lst is +, select routes each summand pointwise
        return code(List.of(
                instB(START_INST_TID, lst(lst(summands))),
                instB(SELECT_INST_TID, lst(lst(routes)))));
    }

    /**
     * The fan-out targets: this machine's own reachable peers (never inherited, never shared), keyed by
     * connection uri in the network component's peer index. Empty on a lone machine, which gates the rule
     * out of the default schedule there.
     */
    private static List<Obj> shardTargets() {
        try {
            return new ArrayList<>(Machine.current().network().peers().jvm().keySet());
        } catch (final Exception e) {
            return List.of();                                       // no network component / headless boot
        }
    }
}
