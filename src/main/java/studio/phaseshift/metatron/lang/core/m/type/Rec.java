/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.lang.core.m.type;


import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.obj.NoObj;
import studio.phaseshift.metatron.lang.jre.ObjFieldReflection;
import studio.phaseshift.metatron.lang.jre.ObjReflection;
import studio.phaseshift.metatron.util.Common;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.Poly.Helper.selectRecRecursion;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

public interface Rec extends Poly<Rec, Map<Obj, Obj>>, PlusMonoid.O<Rec> {

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
        return this.recValue().entrySet().stream().map(kv -> rel(kv.getKey().autoResolve(this), kv.getValue().autoResolve(this)).c(c -> c.mult(this.c())).as());
    }

    @Override
    default Rec jvm(final Object jvm) {
        return this.clone(jvm, this.tid(), this.vid());
    }

    default Rec at(final Obj key, final Obj value, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
        return this.put(key, value, operation);
    }

    @Override
    default boolean matches(final Obj rhs) {
        if (rhs.isRec()) {
            return rhs.asRec().elements().allMatch(r -> {
                final boolean found = this.elements()
                        .map(l -> Tuple.Pair.with(l.first().matches(r.<Rel>as().first()), l.second().matches(r.<Rel>as().second())))
                        .anyMatch(pair -> pair.get0() && pair.get1());
                if (found) return true;
                boolean notFound = (r.<Rel>as().first().c().isZeroable() && this.elements().noneMatch(l -> l.first().matches(r.<Rel>as().first())));
                if (notFound) return true;
                final Obj thisValue = this.at(r.first());
                return (thisValue.isNoObj() && r.asRel().first().c().isZeroable()) || thisValue.matches(r.second());
            });
        } else {
            return Poly.super.matches(rhs);
        }
    }

    @Override
    default <O extends Obj> O at(final Obj key) {
        if (!key.isUri())
            return (O) this.jvm().getOrDefault(key, NoObj.noobj()).autoResolve(this);
        else {
            final boolean singleSegment = key.uriValue().segments().size() == 1;
            final String step = singleSegment ? key.uriValue().toString() : key.uriValue().segments().getFirst();
            Obj result;
            final Uri asNode = uri(key.uriValue().asNode());
            if (this.recValue().containsKey(asNode))
                return (O) (key.uriValue().isBranch() ? rel(asNode, this.recValue().get(asNode)) : this.recValue().get(asNode)).autoResolve(this);
            if (null != this.getClass().getAnnotation(ObjReflection.class)) {
                final O reflectObj = ObjFieldReflection.Helper.recAt(this, step);
                if (!reflectObj.isNoObj())
                    return reflectObj;
            }
            if (step.equals("+") || step.equals("#")) {
                result = key.uriValue().isBranch() ? objs((Stream) this.elements()) : objs(this.recValue().values().stream().map(v -> v.autoResolve(this)));
            } else {
                final Obj temp = this.jvm().getOrDefault(uri(step), NoObj.noobj()).autoResolve(this);
                result = key.uriValue().isBranch() ? rel(key.uriValue().asNode().toUri(), temp) : temp;
            }
            /// ///////////////////////////////////////////////////////////////////////////////////////////////////////
            if (singleSegment) {
                return (O) result.autoResolve(this);
            } else {
                final fURI nextKey = key.uriValue().isBranch() ? key.<Uri>as().uriValue().pretract().asBranch() : key.<Uri>as().uriValue().pretract();
                return (O) objs(IteratorUtil.stream(result.iterator()).filter(Obj::isPoly).map(r -> r.<Poly>as().at(uri(nextKey))));
            }
        }
    }

    default Rec put(final Obj key, final Obj value) {
        if (key.isNoObj()) return this;
        return this.put(key, value, IMMUTABLE);
    }

    default Rec put(final Obj key, final Obj value, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
        final fURI k = key.uriValue();
        if (k.segments().isEmpty())
            return this;
        final Map<Obj, Obj> map = new LinkedHashMap<>(this.recValue());
        map.compute(uri(k.segments().getFirst()), (k1, v) ->
                k.segments().size() == 1 ?
                        (value.isNoObj() ? null : (null != v && v.isObjs() ? v.append(value) : value)) :
                        (null != v && v.isRec() ? v.<Rec>as() : rec()).put(k.pretract().toUri(), value, operation));
        return (Rec) operation.apply(this, map);
    }

    @Override
    default Rec plus(final Rec rhs) {
        final Map<Obj, Obj> newMap = new LinkedHashMap<>(this.recValue());
        rhs.stream().flatMap(Obj::<Obj>elements).map(Obj::<Rel>as).forEach(o -> newMap.compute(o.first(), (k, v) -> null == v ? o.second() : v.isPlusMonoid() ? (Obj) v.<PlusMonoid.O>as().plus(o.second().<PlusMonoid.O>as()) : v.append(o.second())));
        return this.jvm(newMap);
    }

    default <O extends Obj> O at(final String key) {
        return this.at(uri(key));
    }

    default Rec put(final fURI key, final Obj value) {
        return this.put(uri(key), value);
    }

    default Rec put(final String key, final Obj value) {
        return this.put(uri(key), value);
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
    default Obj append(final Obj obj) {
        if (obj.isNoObj())
            return this;
        if (this.isNoObj())
            return obj;
        return objs(this, obj);
    }

    @Override
    Rec self(final Object jvm, final fURI tid, final fURI vid);

    public static final class RecType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(HAS_INST_TID.dom(REC_TID).rng(REC_TID.maybe()), lst(T(ALL)), (lhs, inst) -> inst.arg(0).isRel() ?
                            (lhs.<Rec>as().elements().anyMatch(r -> r.matches(inst.arg(0))) ? lhs : noobj()) :
                            (lhs.<Rec>as().elements().map(Rel::first).anyMatch(r -> r.matches(inst.arg(0))) ? lhs : noobj())),
                    instC(HAS_INST_TID.dom(REC_TID).rng(REC_TID.maybe()), lst(T(ALL), T(BOOL_TID)), (lhs, inst) -> inst.arg(1).apply(lhs.asRec().at(inst.arg(0))).boolValue() ? lhs : noobj()),
                    instC(GET_INST_TID.dom(REC_TID).rng(ALL_STAR), lst(T(URI_TID)), (lhs, inst) -> objs(lhs.stream().map(r -> r.<Rec>as().at(inst.arg(0))))),
                    instC(MERGE_INST_TID.dom(REC_TID).rng(REL_TID.maybeSome()), lst(), (lhs, inst) -> objs(lhs.elements())),
                    instC(MERGE_INST_TID.dom(REC_TID).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> inst.arg(0).<Rec>as().plus(lhs.as())),//objs(lhs.elementStream())),
                    instC(DOM_INST_TID.dom(REC_TID).rng(ALL_STAR), lst(), (lhs, inst) -> objs(lhs.recValue().keySet())),
                    instC(RNG_INST_TID.dom(REC_TID).rng(ALL_STAR), lst(), (lhs, inst) -> objs(lhs.recValue().values())),
                    instC(RSHIFT_INST_TID.dom(REC_TID).rng(ALL_STAR), lst(isa_(T(INT_TID)).else_(jnt(1))), (lhs, inst) -> Common.loop(lhs, o -> objs(o.stream().filter(Obj::isRec).flatMap(r -> r.<Rec>as().elements().map(Rel::second))), inst.arg(0).intValue().intValue())),
                    instC(LSHIFT_INST_TID.dom(REC_TID).rng(ALL_STAR), lst(isa_(T(INT_TID)).else_(jnt(1))), (lhs, inst) -> Common.loop(lhs, o -> objs(o.stream().filter(Obj::isRec).flatMap(r -> r.<Rec>as().elements().map(Rel::first))), inst.arg(0).intValue().intValue())),
                    instC(PLUS_INST_TID.dom(REC_TID).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> lhs.jvm(Stream.concat(lhs.<Rec>as().elements(), inst.arg(0).<Rec>as().elements().map(Obj::<Rel>as)).collect(Collectors.toMap(Rel::first, Rel::second, Obj::append, LinkedHashMap::new)))),
                    instC(MPLUS_INST_TID.dom(REC_TID).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> inst.arg(0).<Rec>as().elements().map(Obj::<Obj>as).reduce(lhs.<Rec>as(), (a, b) -> a.<Rec>as().put(((Rel) b).first(), ((Rel) b).second(), MUTABLE))),
                    instC(SELECT_INST_TID.dom(REC_TID).rng(REC_TID.maybe()), lst(T(REC_TID)), (lhs, inst) -> selectRecRecursion(lhs.asRec(), inst.arg(0).asRec())),
                    instC(WITHIN_INST_TID.dom(REC_TID).rng(REC_TID), lst(T(ALL_STAR)), (lhs, inst) -> rec(lhs.elements().map(r -> inst.arg(0).apply(r).<Rel>as()))),
                    instC(SPLIT_INST_TID.dom(ALL).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> rec(inst.arg(0).asRec().elements().map(e -> e.first().apply(lhs).choose(Obj::isNoObj, x -> null, x -> rel(x, e.second().apply(lhs)))).filter(x -> !Objects.isNull(x))))
                    ));


        }


    }
}