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
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.HOP;
import static studio.phaseshift.metatron.Tokens.LHS;
import static studio.phaseshift.metatron.Tokens.TIMEOUT;
import static studio.phaseshift.metatron.isa.m.mInstSet.FILTER_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.ID_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.IS_INST_TID;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.CUT_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * PARTITION — routes each member of its lhs down the FIRST route that admits it, where {@link BranchInst} routes every
 * member to every lane (the code ring's {@code +}). Routing is the walk join of the rec algebra
 * ({@link studio.phaseshift.metatron.isa.m.type.Rec#mult}): the
 * routes are one table, {@code [k_1 => v_1, …, k_n => v_n]}, a member's key {@code k} is lifted into the relation
 * {@code [# => k]}, and the walks {@code [# => k] * table} are the routes that admit it, in written order (Marko,
 * 2026-11-27). A partition keeps the first walk, as {@code ==[_ => take(1)]} keeps the first path of a composition.
 * The join is computed here, by hand, until {@code >>} matches hosted keys ({@code //0}, {@code //1}) (Marko,
 * 2026-11-27: "do the selection manually").
 * <ul>
 *     <li>THE ARGS ARE VARARGS OF RELATIONS (Marko, 2026-11-27), whose keys SELECT or GUARD (see {@code selects}). A
 *     selector's key is what it answers for the member ({@code mod(2) => {?0 => …, ?1 => …}}), looked up in the table
 *     under it: the selector is applied to the member once, as written, and what it answers is the key, never
 *     unwrapped or applied again, so a blocked {@code |mod(2)} keys by the code it blocks and a read {@code *xyz} by
 *     what it reads. A guard that admits the member opens its whole table
 *     ({@code ?>2 => plus(10), _ => plus(20)}). Written paths factor their common key ({@code mod(2) => ?0 => x,
 *     mod(2) => ?1 => y} is {@code mod(2) => {?0 => x, ?1 => y}}), and an argument that is no relation fails the call:
 *     the positional {@code partition(f, k => v, …)} was {@code f => {k => v, …}} written otherwise, and is gone (Marko,
 *     2026-11-27). The processor hands a blocking instruction its keys raw, so each is applied here, per member, never
 *     to the gathered lhs. A selector's key of noobj forms no relation ({@code [k => noobj]} is
 *     {@code [=>]}), so its member is dropped; {@code none} is a key like any other.</li>
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

    /**
     * the filters, whose code answers its input or nothing: a key made of them guards
     */
    private static final List<fURI> FILTERS = List.of(ID_INST_TID, IS_INST_TID, ISA_INST_TID, FILTER_INST_TID);

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
        return relations(partition).stream().flatMap(relation -> {
            final fURI selected = selects(relation.first()) && relation.first().isUri() && relation.first().uriValue().hasHost() ? relation.first().uriValue() : null;
            final List<Rel> table = table(relation.second());
            final Stream<List<Obj>> chains = table.isEmpty() ? Stream.of(walk(relation.second())) : table.stream().map(PartitionInst::walk);
            return chains.map(chain -> lane(Optional.ofNullable(place(chain)).orElse(selected), chain.getLast()));
        }).toList();
    }

    /**
     * THE BODY: take each member's first walk (BranchInst routes down every one), bucket the member into the lane that
     * walk names, and run each lane on its bucket, every lane submitted before any is awaited ({@link BranchInst#await}).
     */
    public Obj route(final Obj lhs) {
        final List<Rel> relations = relations(this);
        // THE LANES, one per route and place a member's walk reaches, in the order members first reach them: a lane is its
        // route, so routes that run the same code fold their shares apart
        final Map<List<Obj>, List<Obj>> shares = new LinkedHashMap<>();
        for (final Obj member : lhs.stream().toList()) {
            // each selector's key for the member, as its walk computed it: a selector runs once per member
            final Obj[] keys = new Obj[relations.size()];
            final Optional<Walk> first = walks(relations, member, keys).findFirst();
            // A KEY OF NOOBJ FORMS NO RELATION ([k => noobj] is [=>]): a member a selector keys nothing is dropped, and a
            // member no route admits otherwise fails the call
            if (first.isEmpty() && Stream.of(keys).anyMatch(key -> null != key && key.isNoObj()))
                continue;
            final Walk walk = first.orElseThrow(() -> MTronException.of("no route of the partition admits %s: %s", member, this.args()));
            final fURI place = place(walk.steps());
            shares.computeIfAbsent(List.of(jnt(walk.relation()), jnt(walk.route()), null == place ? noobj() : uri(place), walk.steps().getLast()),
                    lane -> new ArrayList<>()).add(member);
        }
        return BranchInst.await(shares.entrySet().stream().map(share -> BranchInst.lane(lane(
                share.getKey().get(2).isNoObj() ? null : share.getKey().get(2).uriValue(), share.getKey().get(3)), objs(share.getValue()))).toList());
    }

    /**
     * ONE WALK a member takes: the relation and the route it took (-1 for a relation with no table) and the objects it
     * steps through, its last the code its lane runs.
     */
    private record Walk(int relation, int route, List<Obj> steps) {
    }

    /**
     * THE RELATIONS a partition routes by, its args, varargs of relations in written order: an argument that is no
     * relation fails the call (Marko, 2026-11-27: the positional f, k => v, … was f => {k => v, …} written otherwise).
     */
    private static List<Rel> relations(final Inst partition) {
        return partition.args().<Obj>elements().map(arg -> {
            if (!arg.isRel())
                throw MTronException.of("a partition's args are relations, k => v, never a bare %s: %s", arg, partition.args());
            return arg.asRel();
        }).filter(relation -> !uri(LHS).equals(relation.first())).toList();
    }

    /**
     * WHETHER A KEY SELECTS (Marko, 2026-11-27): a key template, or a call that computes, so what it answers is a key the
     * table under it is looked up by ({@code <//${mod(2)}> => {//0 => …, //1 => …}}). A GUARD answers its input or
     * nothing: a type, or a code whose every instruction is a filter ({@code _}, {@code is}, {@code isa},
     * {@code filter}), and a guard that admits a member opens its whole table.
     */
    private static boolean selects(final Obj key) {
        return (key.isUri() && key.uriValue().hasTemplates())
                || (key.isObjCall() && !key.asCall().insts().stream().allMatch(inst -> FILTERS.contains(inst.tid().basePath())));
    }

    /**
     * THE WALKS a member takes, lazily and in written order: a selecting relation's routes whose keys admit what it
     * answers, and a guarding relation's whole table (or its one lane) once its key admits the member. Each selector's
     * key is kept in {@code keys}, at its relation's index, as it is computed.
     */
    private static Stream<Walk> walks(final List<Rel> relations, final Obj member, final Obj[] keys) {
        return IntStream.range(0, relations.size()).boxed().flatMap(r -> {
            final Rel relation = relations.get(r);
            final List<Rel> table = table(relation.second());
            if (selects(relation.first())) {
                // THE KEY IS WHAT THE SELECTOR ANSWERS, applied to the member once as written (Marko, 2026-11-27): never
                // unwrapped, never applied again, so |mod(2) keys by the code it blocks. A key of noobj forms no relation.
                final Obj key = keys[r] = relation.first().apply(member);
                return key.isNoObj() ? Stream.empty() : IntStream.range(0, table.size())
                        .filter(t -> admits(table.get(t).first(), key))
                        .mapToObj(t -> new Walk(r, t, steps(key, table.get(t).second())));
            }
            if (!admits(relation.first(), member))
                return Stream.empty();
            return table.isEmpty() ? Stream.of(new Walk(r, -1, steps(member, relation.second())))
                    : IntStream.range(0, table.size()).mapToObj(t -> new Walk(r, t, steps(member, table.get(t))));
        });
    }

    /**
     * WHETHER A KEY ADMITS AN OBJ: a type its instances, a guard what it answers something for ({@code ?k}, {@code _}),
     * a predicate what it holds for ({@code gt(2)}), and a value its equal. A guard answers its input, so the false it
     * answers for false admits it ({@code ?false}), where a predicate's false refuses.
     */
    private static boolean admits(final Obj key, final Obj obj) {
        if (key.isType())
            return obj.test(key);
        // a value admits its equal, as ? on it does, a uri's count and query aside
        if (!key.isObjCall())
            return key.isUri() && obj.isUri()
                    ? obj.uriValue().one().qLess().equals(key.uriValue().one().qLess())
                    : obj.c(cInt.ONE()).equals(key.c(cInt.ONE()));
        final Obj answer = key.apply(obj);
        return !answer.isNoObj() && !(selects(key) && answer.isBool() && !answer.boolValue());
    }

    /**
     * A TABLE of routes: a rec's, a bag of relations ({k => v, …}), or one relation; anything else holds none.
     */
    private static List<Rel> table(final Obj value) {
        return value.isRec() ? value.asRec().<Rel>elements().toList() : value.stream().filter(Obj::isRel).map(Obj::asRel).toList();
    }

    /**
     * A WALK'S STEPS from {@code first}: it, then the objects {@code rest} steps through.
     */
    private static List<Obj> steps(final Obj first, final Obj rest) {
        final List<Obj> steps = new ArrayList<>();
        steps.add(first);
        steps.addAll(walk(rest));
        return steps;
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
