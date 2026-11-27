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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static studio.phaseshift.metatron.Tokens.HASH_FURI;
import static studio.phaseshift.metatron.Tokens.HOP;
import static studio.phaseshift.metatron.Tokens.LHS;
import static studio.phaseshift.metatron.Tokens.TIMEOUT;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.CUT_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * PARTITION — routes each member of its lhs down the FIRST route that admits it, where {@link BranchInst} routes every
 * member to every lane (the code ring's {@code +}). Routing is the walk join of the rec algebra
 * ({@link studio.phaseshift.metatron.isa.m.type.Rec#mult}): the
 * routes are one table, {@code [k_1 => v_1, …, k_n => v_n]}, a member's key {@code k} is lifted into the relation
 * {@code [# => k]}, and the walks {@code [# => k] * table} are the routes that admit it, in written order (Marko,
 * 2026-11-27). A partition keeps the first walk, as {@code ==[_ => take(1)]} keeps the first path of a composition.
 * <ul>
 *     <li>THE KEY is what the SELECTOR, the first argument, answers for the member ({@code partition(mod(2), ?0 => …)},
 *     and {@code _} for the member itself): the selector is applied to the member once, and what it answers is applied
 *     to the member in turn, so a stored function ({@code *xyz}) keys it too. A key of noobj forms no relation
 *     ({@code [k => noobj]} is {@code [=>]}), so its member is dropped; {@code none} is a key like any other.</li>
 *     <li>A ROUTE'S KEY ADMITS A KEY as the walk join admits an end: a value by address ({@code 0}, {@code <0>},
 *     {@code //a}), a type its instances ({@code int::T}), a predicate what it holds for ({@code ?>2}), and {@code _}
 *     every key, so {@code _} written last is the {@code else}. A key no route admits fails the call.</li>
 *     <li>A WALK RUNS ITS CODE AT THE LAST PLACE IT PASSES: {@code 0 => //ginger => plus(1)} runs {@code plus(1)} at
 *     {@code //ginger}, a hosted route key ({@code //1 => code}) is the place of its own lane, and a selector that
 *     answers a place ({@code <//${mod(2)}>}) runs the code its route names there, unless the route brings it home
 *     ({@code _ => <~> => code}). The place's {@code ?timeout} times the lane's cut, and its {@code ?hop} rides with it.
 *     A walk that passes no place runs here.</li>
 *     <li>A PARTITION IS A BARRIER INSTRUCTION (Marko, 2026-11-27): its dom, {@code {*}} by default, gathers the whole
 *     lhs once, so each lane receives its share once, where a branch's default is one member at a time. A smaller
 *     maximum makes it a lazy barrier: {@code {n}} buckets a page of n, and {@code {?}} routes one member at a time. A
 *     lane whose share of a call is empty runs nothing.</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PartitionInst extends MInst {

    public PartitionInst(final Tuple.Triplet<Poly, Inst.f, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * THE LANES a reader of the layout reads (BranchInst#isLayout, Topology), one per route in written order, each at
     * the last place its walk passes: a place in the route ({@code k => //a => code}, a hosted {@code k}), else the
     * selector's when it names one (a hosted key template), else here. Each lane's weight is one, a dealt lane's: a lane
     * receives its routes' share, never the whole lhs.
     */
    public static List<BranchInst.Lane> lanes(final Inst partition) {
        final List<Obj> args = partition.args().<Obj>elements().filter(arg -> !uri(LHS).equals(arg)).toList();
        final Obj selector = args.isEmpty() || args.getFirst().isRel() ? null : args.getFirst();
        final fURI selected = null != selector && selector.isUri() && selector.uriValue().hasHost() ? selector.uriValue() : null;
        return args.stream().filter(Obj::isRel).map(Obj::asRel).filter(route -> !uri(LHS).equals(route.first())).map(route -> {
            final List<Obj> walk = walk(route);
            final fURI place = Optional.ofNullable(place(walk)).orElse(selected);
            return lane(place, walk.getLast());
        }).toList();
    }

    /**
     * THE BODY: key each member once, take the first walk that admits its key, bucket the member into the lane that walk
     * names, and run each lane on its bucket, every lane submitted before any is awaited ({@link BranchInst#await}).
     */
    public Obj route(final Obj lhs) {
        final List<Obj> args = this.args().<Obj>elements().filter(arg -> !uri(LHS).equals(arg)).toList();
        final Obj selector = args.isEmpty() || args.getFirst().isRel() ? null : args.getFirst();
        final List<Rel> routes = args.stream().filter(Obj::isRel).map(Obj::asRel).filter(route -> !uri(LHS).equals(route.first())).toList();
        // THE LANES, one per route and place a member's walk reaches, in the order members first reach them: a lane is its
        // route, so routes that run the same code fold their shares apart
        final Map<List<Obj>, List<Obj>> shares = new LinkedHashMap<>();
        for (final Obj member : lhs.stream().toList()) {
            // what the selector answers is applied to the member in turn: a value answers itself, and a stored function
            // (*xyz: an instruction, a code or a key template) keys the member
            final Obj key = null == selector ? member : selector.apply(member).apply(member);
            // A KEY OF NOOBJ FORMS NO RELATION ([k => noobj] is [=>]): the member is dropped
            if (key.isNoObj())
                continue;
            // THE FIRST ROUTE THAT ADMITS THE KEY, in written order: an acting key (?k, a type, a guard, _) as the walk join
            // admits an end ([# => k] * [route]), and a value as ? on it does, by equality, its count and query aside
            final Map.Entry<Integer, List<Obj>> admitted = IntStream.range(0, routes.size()).boxed()
                    .map(r -> Map.entry(r, routes.get(r).first().isObjCall() || routes.get(r).first().isType()
                            ? rec(uri(HASH_FURI), key).mult(rec(routes.get(r).first(), routes.get(r).second())).elements()
                            .flatMap(arrow -> arrow.secondDirect().stream()).findFirst()
                            : Optional.of((Obj) rel(key, routes.get(r).second())).filter(walk -> equal(key, routes.get(r).first()))))
                    .filter(walk -> walk.getValue().isPresent())
                    .map(walk -> Map.entry(walk.getKey(), walk(walk.getValue().get())))
                    .findFirst()
                    .orElseThrow(() -> MTronException.of("no route of the partition admits %s: %s keys %s so", key, selector, member));
            final fURI place = place(admitted.getValue());
            // THE PROGRAM IS THE ROUTE'S, never the walk's copy: the join multiplies the member's count onto the range it
            // reaches, and the member carries that count into its lane already
            shares.computeIfAbsent(List.of(jnt(admitted.getKey()), null == place ? noobj() : uri(place), walk(routes.get(admitted.getKey())).getLast()),
                    lane -> new ArrayList<>()).add(member);
        }
        return BranchInst.await(shares.entrySet().stream().map(share -> BranchInst.lane(lane(
                share.getKey().get(1).isNoObj() ? null : share.getKey().get(1).uriValue(), share.getKey().get(2)), objs(share.getValue()))).toList());
    }

    /**
     * WHETHER A VALUE KEY ADMITS A KEY, as ? on it does: by equality, a uri's count and query aside.
     */
    private static boolean equal(final Obj key, final Obj route) {
        return key.isUri() && route.isUri()
                ? key.uriValue().one().qLess().equals(route.uriValue().one().qLess())
                : key.c(cInt.ONE()).equals(route.c(cInt.ONE()));
    }

    /**
     * A WALK as the objects it steps through, a nested rel unrolled: {@code k => //a => code} is {@code [k, //a, code]},
     * its last the code its lane runs.
     */
    private static List<Obj> walk(final Obj arrow) {
        final List<Obj> walk = new ArrayList<>();
        Obj step = arrow;
        while (step.isRel()) {
            walk.add(step.asRel().first());
            step = step.asRel().second();
        }
        walk.add(step);
        return walk;
    }

    /**
     * THE LAST PLACE A WALK PASSES before its code, or null for here: a hosted uri on it, or home ({@code <~>},
     * {@code <.>}), which brings the lane back here past a place a selector answered ({@code _ => <~> => code}).
     */
    private static fURI place(final List<Obj> walk) {
        return walk.subList(0, walk.size() - 1).stream()
                .filter(step -> step.isUri() && (step.uriValue().hasHost() || BranchInst.home(step.uriValue())))
                .reduce((first, second) -> second).map(Obj::uriValue).filter(at -> !BranchInst.home(at)).orElse(null);
    }

    /**
     * A LANE running {@code code} at {@code place} or here: at a place, the cut its program runs in, timed by the
     * place's {@code ?timeout} as a keyed branch's lane is (BranchInst#lanes), the place's {@code ?hop} riding with it.
     * A place a key template computes per member is drawn as written.
     */
    private static BranchInst.Lane lane(final fURI place, final Obj code) {
        final List<Inst> program = BranchInst.program(code);
        final Obj tail = program.isEmpty() ? code(new ArrayList<>()) : code(program).tryToInst();
        if (null == place)
            return new BranchInst.Lane(null, program, 1L, tail);
        final fURI at = null == place.q(HOP) ? place.qLess() : place.qLess().q(HOP, place.q(HOP));
        return new BranchInst.Lane(at, program, 1L, instB(null == place.q(TIMEOUT) ? CUT_INST_TID : CUT_INST_TID.q(TIMEOUT, place.q(TIMEOUT)),
                lst(uri(place.hasTemplates() ? at : Machine.Helper.align(Machine.current(), at)), tail)));
    }
}
