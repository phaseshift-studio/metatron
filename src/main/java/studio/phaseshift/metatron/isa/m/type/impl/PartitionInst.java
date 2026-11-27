/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.m.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static studio.phaseshift.metatron.isa.m.mach.machInstSet.CUT_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * PARTITION — routes each member of its lhs to the lane its partition function keys, where {@link BranchInst}
 * routes every member to every lane (the code ring's {@code +}). It means a branch whose lanes are guarded by the
 * fibres of one function,
 * <pre>
 *   partition(p, k_1 => c_1, …, k_n => c_n)  =  branch(k_1 => filter(p.eq(k_1)).c_1, …, k_n => filter(p.eq(k_n)).c_n)
 * </pre>
 * so the branch's laws hold of it, and its body evaluates p once per member where the guarded branch would evaluate
 * every guard on every member: a switch, not a chain of tests.
 * <ul>
 *     <li>THE PARTITION FUNCTION is applied to each member, and what it answers is applied to the member in turn: a
 *     value answers itself, and a stored function ({@code *xyz}, an instruction, a code or a key template) keys the
 *     member. So {@code mod(2)}, a key template {@code <${mod(2)}>} and {@code *xyz} all key a member. A key is a uri,
 *     or an int read as its decimal spelling.</li>
 *     <li>A LANE'S KEY IS ITS PLACE OR ITS LABEL: a hosted key ({@code //a}) is a place, and the lane is the cut its
 *     program runs in there, as a keyed branch's lane is; any other key ({@code <0>}) is a label, and the lane runs
 *     here. A key naming two lanes names their branch, and the member goes to each. A {@code <#>} lane takes every
 *     member no other key names, a member keyed noobj included; with none, such a member fails the call.</li>
 *     <li>THE DOM COEFFICIENT IS THE GRAIN, as for a branch: {@code {?}} routes one member at a time, {@code {n}}
 *     buckets a page of n, and {@code {*}} buckets the whole lhs, so each lane receives its share once. A lane whose
 *     share of a call is empty runs nothing.</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PartitionInst extends MInst {

    public PartitionInst(final Tuple.Triplet<Poly, Inst.f, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * THE LANES, one per route {@code key => program} in the order written: a hosted key's lane is the cut its program
     * runs in at that place, and any other key's a home lane. Each lane's weight is one, a dealt lane's, as a reader of
     * a layout reads it: a lane receives its key's share, never the whole lhs (a weight of zero).
     */
    public static List<BranchInst.Lane> lanes(final Inst partition) {
        return routes(partition).stream().map(route -> {
            final fURI key = route.first().uriValue();
            final List<Inst> program = BranchInst.program(route.second());
            final Obj tail = program.isEmpty() ? code(new ArrayList<>()) : code(program).tryToInst();
            return key.hasHost()
                    ? new BranchInst.Lane(key, program, 1L, instB(CUT_INST_TID, lst(uri(Machine.Helper.align(Machine.current(), key)), tail)))
                    : new BranchInst.Lane(null, program, 1L, tail);
        }).toList();
    }

    /**
     * The routes, every argument after the partition function.
     */
    private static List<Rel> routes(final Inst partition) {
        return partition.args().<Obj>elements().skip(1).map(Obj::asRel).toList();
    }

    /**
     * THE BODY: key each member once, bucket it into the lane its key names, and run each lane on its bucket, every
     * lane submitted before any is awaited ({@link BranchInst#await}).
     */
    public Obj route(final Obj lhs) {
        final Obj function = this.arg(0);
        // keys compare at coefficient one: a bulked member's key carries its count
        final List<fURI> keys = routes(this).stream().map(route -> route.first().uriValue().one()).toList();
        final int rest = keys.indexOf(fURI.Singleton.f("#").one());
        final List<List<Obj>> shares = keys.stream().map(key -> (List<Obj>) new ArrayList<Obj>()).toList();
        for (final Obj member : lhs.stream().toList()) {
            // what the function answers is applied to the member in turn: a value answers itself, and a stored function
            // (*xyz: an instruction, a code or a key template) keys the member
            final Obj answer = function.apply(member).apply(member);
            if (!answer.isNoObj() && !answer.isUri() && !answer.isInt())
                throw MTronException.of("a partition key is a uri or an int: %s keys %s as %s", function, member, answer);
            // a member keyed noobj is in no fibre, so it is the rest's
            final fURI key = answer.isUri() ? answer.uriValue().one()
                    : answer.isInt() ? fURI.Singleton.f(answer.intValue().toString())
                    : null;
            // A KEY NAMING TWO LANES NAMES THEIR BRANCH: the member goes to each, as the guarded branch sends it
            final int[] named = IntStream.range(0, keys.size()).filter(r -> keys.get(r).equals(key)).toArray();
            if (0 == named.length && -1 == rest)
                throw MTronException.of("no lane of the partition is keyed %s: %s keys %s so, and no <#> lane takes the rest", answer, function, member);
            (0 == named.length ? IntStream.of(rest) : IntStream.of(named)).forEach(r -> shares.get(r).add(member));
        }
        final List<BranchInst.Lane> lanes = lanes(this);
        return BranchInst.await(IntStream.range(0, lanes.size())
                .mapToObj(r -> shares.get(r).isEmpty() ? noobj() : BranchInst.lane(lanes.get(r), objs(shares.get(r))))
                .toList());
    }
}
