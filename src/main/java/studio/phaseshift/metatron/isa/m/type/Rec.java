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

package studio.phaseshift.metatron.isa.m.type;


import studio.phaseshift.metatron.furi.C;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.ProjectionFailureException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.ID_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.ObjFactory.LOG;
import static studio.phaseshift.metatron.isa.m.type.Poly.Helper.autoToggle;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.immutableMap;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public interface Rec extends Poly<Rec, Map<Obj, Obj>> {

    Rec EMPTY_REC = rec(immutableMap());
    // Rec with coefficient {0}: noobj typed as a Rec, for a method that answers a Rec or nothing.
    // cast, not asRec(): asRec() answers a zero with noobjRec(), i.e. this field, which is still null while it initializes
    Rec REC_NOOBJ = (Rec) rec(immutableMap()).c(cInt.ZERO());

    static Rec rec_noobj() {
        return REC_NOOBJ;
    }

    @Override
    default Stream<Rel> indexedStream() {
        return this.jvm().entrySet().stream().map(kv -> rel(kv.getKey(), kv.getValue()).c(this.c()).as());
    }

    @Override
    default Rec zero() {
        return rec();
    }

    @Override
    Rec clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    Map<Obj, Obj> jvm();

    @Override
    default long count() {
        return this.jvm().size();
    }

    @Override
    default Stream<Rel> elements() {
        return this.recValue().entrySet().stream().map(kv -> rel(kv.getKey(), kv.getValue()).c(c -> c.mult(this.c())).as());
    }

    @Override
    default <OBJ extends Obj> Stream<OBJ> valueElements() {
        return this.elements().map(r -> (OBJ) r.second());
    }

    @Override
    default <O extends Obj> O jvm(final Object jvm) {
        return (O) this.clone(jvm, this.tid(), this.vid());
    }

    @Override
    default Stream<Obj> values() {
        return this.elements().map(Rel::second);
    }

    @Override
    default Stream<Obj> keys() {
        return this.elements().map(Rel::first);
    }

    @Override
    default boolean test(final Obj rhs) {
        if (Obj.Helper.isAuto(rhs))
            return true;
        if (rhs.isRec()) {
            return rhs.asRec().elements().allMatch(r -> {
                // a value is tested only under a key that matched: pairing it with every value of rhs ran unrelated
                // predicates on it (an instset tested against itself ran percent::T's ?>=0 on its docq), and a
                // predicate that runs as code is an evaluation, which files its miss. A MISSING KEY DOES NOT MATCH A
                // VALUE PATTERN: only an optional key may be absent, which the zeroable checks below decide -- a rec
                // without `services` is not a compose, and a rec without `b` is not a `[b=>2]`.
                final boolean found = this.elements()
                        .anyMatch(l -> l.jvm().get0().test(r.jvm().get0()) && l.jvm().get1().test(r.jvm().get1()));
                if (found) return true;
                boolean notFound = r.jvm().get0().c().isZeroable() && this.elements().noneMatch(l -> l.jvm().get0().test(r.jvm().get0()));
                if (notFound) return true;
                final Obj thisValue = this.at(r.jvm().get0()); // can't make this jvm()-based
                return (thisValue.isNoObj() && r.jvm().get0().c().isZeroable()) || thisValue.test(r.jvm().get1());
            });
        } else {
            return Poly.super.test(rhs);
        }
    }

    default Rec at(final Obj key, final Obj value, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
        if (key.isUri()) {
            final fURI k = key.uriValue().resolve();
            if (k.path().isEmpty())
                return this;
            final Map<Obj, Obj> map = new LinkedHashMap<>(this.recValue());
            map.compute(uri(k.path().getFirst()), (k1, v) ->
                    k.path().size() == 1 ?
                            (value.isNoObj() ? null : (null != v && v.isObjs() ? v.append(value.parent(this)) : value.parent(this))) :
                            (null != v && v.isPoly()
                                    ? v.<Poly<?, ?>>as().at(k.pretract(1).toUri(), value.parent(this), operation)
                                    : rec().at(k.pretract(1).toUri(), value.parent(this), operation)));
            final Poly<?, ?> applied1 = operation.apply(this, map);
            if (applied1 instanceof Fail fail)
                throw MTronException.of("rec at(%s): operation failed: %s", key, fail.message());
            return (Rec) applied1;
        } else {
            final Map<Obj, Obj> map = new LinkedHashMap<>(this.recValue());
            map.put(key, value);
            final Poly<?, ?> applied2 = operation.apply(this, map);
            if (applied2 instanceof Fail fail)
                throw MTronException.of("rec at(%s): operation failed: %s", key, fail.message());
            return (Rec) applied2;
        }
    }

    @Override
    default <OBJ extends Obj> OBJ at(final Obj key) {
        return Rec.Helper.atToggle(this, key, true);
    }

    @Override
    default <O extends Obj> O atDirect(final Obj key) {
        return Rec.Helper.atToggle(this, key, false);
    }

    default Rec plus(final Rec rhs) {
        return Poly.Helper.mergeObjRecursion(this, rhs).asRec();
    }

    default Rec minus(final Rec rhs) {
        return this.plus(rhs.neg());
    }

    default Rec neg() {
        return this.elements().map(rkv -> rel(rkv.first(), rkv.second().c(C::inv))).collect(new CommonUtil.RecCollector(this.tid(), this.vid()));
    }

    /**
     * THE WALK JOIN (Marko, 2026-11-24): a rec is a sum of walks, and the product joins each walk of this rec to each
     * walk of the argument where the first ends at an object the second's key meets, KEEPING the middle:
     * {@code [a=>b]·[b=>c]} is {@code [a=>(b=>c)]}. A walk is the chain of objects its arrow steps through: a rel range
     * steps on (a nested rel is a path, 2026-11-13), a rec range steps through each of its keys ({@code [a=>[b=>1]]}
     * holds the walk {@code a=>b=>1}, the trie reading the merge keeps), and any other range ends the walk, CODE
     * INCLUDED: a range is a value and is never run, so {@code [a=>5]·[_=>plus(1)]} is {@code [a=>(5=>plus(1))]}. A
     * span is the object's, never the walk's: {@code <b>{5}} is the one uri {@code b/b/b/b/b}. Only a key acts
     * ({@link Helper#splice}): a code key meets an object it answers ({@code ?>1} meets 5 and not 0), the identity
     * {@code _} meets every object, and any other key a value that tests as it. As {@code =>} nests to the right, the
     * join splices at the innermost range, so joined walks concatenate and the product is associative: the path algebra
     * of the keys' graph. An identity term {@code _=>_} adds no step, so the other term passes as it is, scaled, and
     * {@code [_=>_]} is the unity ({@link #one()}); the empty rec, with no walk to join, is the zero.
     * <p>
     * MULTIPLICITIES ARE POINTS, and they multiply onto the joined walk's outermost range, never onto a key. A rec's own
     * coefficient is read as the merge reads it: this rec's is the stream's and stays on the product, and the argument's
     * is its copies' and multiplies onto the walks it joins, so {@code [a=>1]·{2}[1=>c]} is {@code [a=>{2}(1=>c)]}. The
     * product is bilinear and distributes over the merge of recs whose multiplicities sit on their values. A bulked lhs
     * of the merge is the stream's over the whole sum ({@code {2}y + z} is {@code {2}(y + z)}), so a bulked summand
     * counts both walks: {@code [a=>b]·({2}[b=>c] + [b=>d])} is {@code [a=>{{2}(b=>c),{2}(b=>d)}]}.
     */
    default Rec mult(final Rec rhs) {
        final List<Rel> nexts = rhs.elements().toList();
        return this.d(cInt.ONE()).asRec().elements().flatMap(arrow -> nexts.stream().flatMap(next -> {
            // the argument's arrow carries its rec's coefficient on its own point: its copies, taken off the key
            final cInt copies = next.tid().d();
            final Rel right = next.d(cInt.ONE()).asRel();
            return arrow.secondDirect().stream().flatMap(value -> right.secondDirect().stream().flatMap(range -> {
                if (rel(right.firstDirect(), range).isOne())
                    return Stream.of(rec(arrow.firstDirect(), value.d(value.tid().d().mult(copies).mult(range.tid().d()))));
                if (rel(arrow.firstDirect(), value).isOne())
                    return Stream.of(rec(right.firstDirect(), range.d(range.tid().d().mult(copies).mult(value.tid().d()))));
                final List<Obj> steps = Helper.walks(range).toList();
                return Helper.walks(value).flatMap(walk -> steps.stream().map(step -> {
                    final Obj spliced = Helper.splice(walk.d(cInt.ONE()), right.firstDirect(), step.d(cInt.ONE()));
                    return spliced.isNoObj() ? rec() :
                            rec(arrow.firstDirect(), spliced.d(copies.mult(walk.tid().d()).mult(step.tid().d())));
                }));
            }));
        })).reduce(this.zero(), Rec::plus).d(this.tid().d()).asRec();
    }

    /**
     * THE UNITY OF THE WALK JOIN: the identity arrow {@code _=>_}, the walk of length zero at every object, as a rec,
     * {@code [_=>_]}, spelled as {@code _} parses (an {@code id()} of no args), so it is the rec ring's declared one.
     * {@link Rel#one()} spells its {@code id()} over {@code A}, and the join reads either as the identity, since
     * {@link Rel#isOne()} compares paths.
     */
    default Rec one() {
        final Obj id = instB(ID_INST_TID, lst());
        return rec(id, id);
    }

    @Override
    default Rec vid(final fURI vid) {
        return (Rec) Poly.super.vid(vid);
    }

    @Override
    default Rec tid(final fURI tid) {
        return (Rec) Poly.super.tid(tid);
    }

    @Override
    default Rec selfVID(final fURI vid) {
        return (Rec) Poly.super.selfVID(vid);
    }

    @Override
    default Obj append(final Obj obj) {
        if (obj.isNoObj())
            return this;
        if (this.isNoObj())
            return obj;
        return objs(this, obj);
    }

    @Override
    Rec self(final Object jvm, final fURI tid, final fURI vid);

    static <T> T wrap(final Obj obj, final Class<T> t) {
        if (null == obj || obj.isNoObj() || !obj.isRec())
            throw MTronException.of("%s is not a rec::T", obj);
        if (t.isAssignableFrom(obj.getClass()))
            return (T) obj;
        try {
            return t.getConstructor(Map.class, fURI.class, fURI.class).newInstance(new LinkedHashMap<>(obj.jvm()), obj.tid(), obj.vid());
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    final class Helper {
        public Helper() {
            // do nothing
        }

        /**
         * THE WALKS A RANGE HOLDS ({@link Rec#mult(Rec)}), each the chain of objects it steps through as a nested rel, with its
         * multiplicity on the outermost and every object inside at point one. A rel steps from its key to the walks of
         * its range, and a rec steps through each of its keys to the walks of that key's value. Any other object, code
         * included, ends the walk. The empty rec holds no walk.
         */
        static Stream<Obj> walks(final Obj range) {
            return range.stream().flatMap(member -> {
                final Obj unit = member.d(cInt.ONE());
                if (!unit.isRel() && !unit.isRec())
                    return Stream.of(member);
                return (unit.isRel() ? Stream.of(unit.asRel()) : unit.asRec().elements())
                        .flatMap(step -> walks(step.secondDirect()).map(rest ->
                                rel(step.firstDirect(), rest.d(cInt.ONE())).d(member.tid().d().mult(rest.tid().d()))));
            });
        }

        /**
         * A walk spliced at its end to a step ({@link Rec#mult(Rec)}), both at point one: the walk with the step after its
         * last object when that object meets the key, else noobj. The identity {@code _} meets every object, code
         * included. It answers whatever it is given, but the processor resolves no {@code id()} on an instruction, so
         * {@code [a=>plus(1)]·[_=>x]} joins by this clause alone. Any other code key meets an object it answers:
         * {@code ?>1} answers 5 and not 0, and on an instruction {@code count()} answers while {@code neg()},
         * {@code plus(1)} and {@code ?>1} do not. Any other key is a value, and meets a value that tests as it
         * (equality, a uri pattern's matches), never an instruction, which {@link Obj#test} would pass on its
         * coefficient alone.
         */
        static Obj splice(final Obj walk, final Obj key, final Obj step) {
            if (walk.isRel()) {
                final Obj spliced = splice(walk.asRel().secondDirect(), key, step);
                return spliced.isNoObj() ? spliced : rel(walk.asRel().firstDirect(), spliced);
            }
            final boolean meets = key.isObjCall()
                    ? key.asCall().isOne() || !key.apply(walk).isNothing()
                    : !walk.isObjCall() && walk.test(key);
            return meets ? rel(walk, step) : noobj();
        }

        public static Obj typeToRec(final Obj maybeType, final Supplier<Obj> notType) {
            if (maybeType.isType()) {
                final Type type = maybeType.asType();
                return rec(
                        uri(TID), uri(type.tid()),
                        uri(VID), uri(type.vid()),
                        uri(PRED), !type.hasPredicate() ? noobj() : type.isIsaPredicate() ?
                                rec(uri(CODE), type.predicate(), uri(ARGS), Type.Helper.typePredicateObj(type)) :
                                rec(uri(CODE), type.predicate()),
                        uri(CTOR), type.hasConstructor() ? type.constructor() : noobj());
            }
            return notType.get();
        }

        public static <OBJ extends Obj> OBJ atToggle(final Rec arec, final Obj key, final boolean doAuto) {
            if (!key.isUri())
                return autoToggle(arec, arec.jvm().getOrDefault(key, NoObj.noobj()), doAuto);
            else {
                //if (key.uriValue().isEmpty())
                //   return this.c(c -> c.mult(key.c())).as();
                final fURI keyF = key.uriValue().resolve();
                if (keyF.segmentLength() == 0)
                    return (OBJ) noobj();
                final boolean singleSegment = keyF.segmentLength() == 1;
                final String step = singleSegment ? keyF.asNode().toString() : keyF.path().getFirst();
                Obj result;
                final Uri asNode = uri(keyF.asNode());
                final cInt cKey = key.c();
                final boolean isBranch = keyF.isBranch();
                if (step.equals("..")) {
                    // UP: the ancestor keeps its own link. The re-parenting below would point it back down at arec
                    final Obj up = arec.parent();
                    return singleSegment ? up.c(c -> c.mult(cKey)).as()
                            : (OBJ) (up.isPoly() ? up.<Poly<?, ?>>as().at(uri(isBranch ? keyF.pretract(1).asBranch() : keyF.pretract(1))) : noobj());
                } else if (step.equals("+") || step.equals("#")) {
                    // a trailing '/' asks for rels at the LAST step only: an earlier step hands its values on, and the
                    // walk below carries the branch to the step it belongs to (z/+/ is z's entries, not z as a rel)
                    result = objs(isBranch && singleSegment ?
                            arec.jvm().entrySet().stream().map(e -> rel(
                                            autoToggle(arec, e.getKey(), doAuto),
                                            autoToggle(arec, e.getValue(), doAuto)))
                                    .map(o -> o.c(c -> c.mult(cKey))) :
                            arec.jvm().values().stream().map(obj -> (Obj) autoToggle(arec, obj, doAuto))
                                    .map(o -> o.c(c -> c.mult(cKey))));
                } else if (arec.jvm().containsKey(asNode)) {
                    return (OBJ) (isBranch ?
                            rel(asNode, autoToggle(arec, arec.jvm().get(asNode), doAuto)) :
                            autoToggle(arec, arec.jvm().get(asNode), doAuto)).c(c -> c.mult(cKey));
                } else { // this.recValue().containsKey(uri(step))
                    if (singleSegment && !isBranch && !doAuto) {
                        // A single-segment atDirect key may name a leaf of a deep
                        // (full-path) keyed rec.  If exactly one stored key has that
                        // node name, return its raw value directly (no pattern-
                        // collection wrapper); several matches stay an objs; none
                        // falls through.  (at() keeps its historical semantics so
                        // structural reads/type-matching are unaffected.)
                        final java.util.List<Map.Entry<Obj, Obj>> named = arec.jvm().entrySet().stream()
                                .filter(kv -> kv.getKey().isUri())
                                .filter(kv -> step.equals(kv.getKey().uriValue().asNode().name()))
                                .toList();
                        if (named.size() == 1)
                            return (OBJ) autoToggle(arec, named.get(0).getValue(), doAuto).c(c -> c.mult(cKey));
                        else if (named.size() > 1)
                            return (OBJ) objs(named.stream()
                                    .map(kv -> autoToggle(arec, kv.getValue(), doAuto).c(c -> c.mult(cKey))));
                    }
                    final Obj temp = autoToggle(arec, arec.jvm().getOrDefault(uri(step), noobj()), doAuto);
                    if (temp.isNoObj()) {
                        return (OBJ) objs(arec.jvm().entrySet()
                                .stream()
                                .filter(kv -> kv.getKey().isUri())
                                .filter(kv -> kv.getKey().uriValue().bimatches(asNode.uriValue()))
                                .map(kv -> isBranch ? rel(
                                        kv.getKey(), autoToggle(arec, kv.getValue(), doAuto)) :
                                        autoToggle(arec, kv.getValue(), doAuto)));
                    } else {
                        result = (isBranch && singleSegment ? rel(asNode, temp) : temp).c(c -> c.mult(cKey)).parent(arec);
                    }
                }
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////////
                if (singleSegment) {
                    return result.parent(arec).c(c -> c.mult(cKey)).as();
                } else {
                    final fURI nextKey = isBranch ? keyF.pretract(1).asBranch() : keyF.pretract(1);
                    return (OBJ) objs(IteratorUtil.stream(result.iterator()).filter(Obj::isPoly).map(o -> o.parent(arec).<Poly<?, ?>>as()).map(r -> r.<Poly<?, ?>>as().at(uri(nextKey))));
                }
            }
        }

        public static Map<Obj, Obj> cleanMap(final Map<Obj, Obj> jvm) {
            if (jvm.isEmpty())
                return jvm;
            try {
                jvm.remove(noobj());
                jvm.entrySet().stream().filter(e -> e.getValue() == null || e.getValue().isNoObj()).map(Map.Entry::getKey).toList().forEach(jvm::remove);
            } catch (final UnsupportedOperationException e) {
                LOG.error("underlying jvm object is immutable: %s %s", jvm.getClass().getName().toLowerCase(), jvm);
            }
            return jvm;
        }

        public static Rec stripNone(final Rec rec) {
            rec.recValue().entrySet().stream().filter(e -> e.getValue().isNone()).toList().forEach(e -> rec.jvm().remove(e.getKey()));
            return rec;
        }

        public static Obj rshiftRec(final Rec lhs, final Obj arg) {
            return arg.isNoObj() ? objs(lhs.asRec().valueElements()) : objs(arg.stream().map(k -> lhs.asRec().at(k)));
        }

        /**
         * The Universal Structural Projection logic for Records.
         *
         * @param lhs      The target record to be projected.
         * @param rulesRec The la-palette projector (a rec of predicate -> transformation/constraint).
         * @param verify   If true, operates in 'where' mode (strict validation).
         *                 If false, operates in 'select' mode (structural mutation).
         * @return A map containing the results of the projection. In verify mode,
         * this is primarily used for internal state; the real result is either
         * success or a ProjectionFailureException.
         * @throws ProjectionFailureException if verification fails or a transformation errors.
         */
        public static Map<Obj, Obj> project(final Rec lhs, final Rec rulesRec, boolean verify) {
            if (rulesRec.isNoObj()) {
                if (verify) throw ProjectionFailureException.instance();
                return mutableMap();
            }
            if (verify) {
                for (final Map.Entry<Obj, Obj> entry : rulesRec.recValue().entrySet()) {
                    if (entry.getKey().isUri()) {
                        final Obj foundValue = lhs.at(entry.getKey());
                        if (foundValue.isNoObj() || !foundValue.test(entry.getValue()))
                            throw ProjectionFailureException.instance();
                    }
                }
            }
            final Map<Obj, Obj> result = mutableMap();
            for (final Rel rel : lhs.elements().toList()) {
                for (final Map.Entry<Obj, Obj> entry : rulesRec.recValue().entrySet()) {
                    final Obj predicate = entry.getKey();
                    final Obj transformOrConstrain = entry.getValue();
                    if (predicate.test(rel.first())) {
                        if (verify) {
                            if (transformOrConstrain.isRec() && rel.second().isRec()) {
                                project(rel.second().asRec(), transformOrConstrain.asRec(), true);
                            } else if (!rel.second().test(transformOrConstrain))
                                throw ProjectionFailureException.instance();
                        } else {
                            if (transformOrConstrain.isRec() && rel.second().isRec()) {
                                result.put(rel.first(), rec(project(rel.second().asRec(), transformOrConstrain.asRec(), false)));
                            } else {
                                final Obj transformedValue = transformOrConstrain.apply(rel.second());
                                if (!transformedValue.isNothing())
                                    result.put(rel.first(), transformedValue);
                            }
                        }
                        break;
                    } else if (predicate.isUri()) {
                        final Obj newValue = lhs.at(predicate);
                        if (!newValue.isNoObj()) {
                            if (verify) {
                                if (!newValue.test(transformOrConstrain))
                                    throw ProjectionFailureException.instance();
                            } else {
                                if (transformOrConstrain.isRec() && rel.second().isRec()) {
                                    result.put(rel.first(), rec(project(rel.second().asRec(), transformOrConstrain.asRec(), false)));
                                } else {
                                    final Obj transformedValue = transformOrConstrain.apply(newValue);
                                    if (!transformedValue.isNothing())
                                        result.put(predicate, transformedValue);
                                }
                            }
                        }
                    }
                }
            }
            // In verify mode, if we reached this point without throwing an exception, the profile is satisfied.
            return result;
        }
    }

}
