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

package studio.phaseshift.metatron.isa.m.type;


import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.Tokens.C;
import static studio.phaseshift.metatron.furi.fURI.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.Helper.selectRecRecursion;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.immutableMap;

public interface Rec extends Poly<Rec, Map<Obj, Obj>>, PlusMonoid.O<Rec> {

    Type REC_TYPE = Type.Builder.build().tid(REC_TID).vid(REC_TID).create();
    Rec EMPTY_REC = rec(immutableMap());

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
    default Rec jvm(final Object jvm) {
        return this.clone(jvm, this.tid(), this.vid());
    }

    @Override
    default Stream<Obj> values() {
        return this.elements().map(Rel::second);
    }

    @Override
    default boolean test(final Obj rhs) {
        if (Obj.Helper.isAuto(rhs))
            return true;
        if (rhs.isRec()) {
            return rhs.asRec().elements().allMatch(r -> {
                final boolean found = this.elements()
                        .map(l -> Tuple.Pair.with(l.jvm().get0().test(r.jvm().get0()), l.jvm().get1().test(r.jvm().get1())))
                        .anyMatch(pair -> pair.get0() && pair.get1());
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
            final fURI k = key.uriValue();
            if (k.segments().isEmpty())
                return this;
            final Map<Obj, Obj> map = new LinkedHashMap<>(this.recValue());
            map.compute(uri(k.segments().getFirst()), (k1, v) ->
                    k.segments().size() == 1 ?
                            (value.isNoObj() ? null : (null != v && v.isObjs() ? v.append(value.parent(this)) : value.parent(this))) :
                            (null != v && v.isRec() ? v.asRec() : rec()).at(k.pretract().toUri(), value.parent(this), operation));
            return (Rec) operation.apply(this, map);
        } else {
            final Map<Obj, Obj> map = new LinkedHashMap<>(this.recValue());
            map.put(key, value);
            return (Rec) operation.apply(this, map);
        }
    }

    @Override
    default <OBJ extends Obj> OBJ at(final Obj key) {
        if (!key.isUri())
            return this.jvm().getOrDefault(key, NoObj.noobj()).autoResolve(this).parent(this);
        else {
            final boolean singleSegment = key.uriValue().pathLength() == 1;
            final String step = singleSegment ? key.uriValue().asNode().toString() : key.uriValue().segments().getFirst();
            Obj result;
            final Uri asNode = uri(key.uriValue().asNode());
            final boolean isBranch = key.uriValue().isBranch();
            if (step.equals(ONE_WILD_STRING) || step.equals(ALL_WILD_STRING)) {
                result = objs(isBranch ?
                        this.jvm().entrySet().stream().map(e -> rel(e.getKey().autoResolve(this), e.getValue().autoResolve(this))).map(o -> o.c(c -> c.mult(this.c()))).map(o -> o.parent(this)) :
                        this.jvm().values().stream().map(obj -> obj.autoResolve(this)).map(o -> o.c(c -> c.mult(this.c()))).map(o -> o.parent(this)));
            } else if (this.jvm().containsKey(asNode)) {
                return (isBranch ?
                        rel(asNode, this.jvm().get(asNode)) :
                        this.jvm().get(asNode).autoResolve(this)).c(c -> c.mult(this.c())).parent(this);
            } else { // this.recValue().containsKey(uri(step))
                final Obj temp = this.jvm().getOrDefault(uri(step), NoObj.noobj()).autoResolve(this).parent(this);
                result = (isBranch ? rel(asNode, temp) : temp).c(c -> c.mult(this.c())).parent(this);
            }
            /// ///////////////////////////////////////////////////////////////////////////////////////////////////////
            if (singleSegment) {
                return result.parent(this);
            } else {
                final fURI nextKey = isBranch ? key.uriValue().pretract().asBranch() : key.uriValue().pretract();
                return (OBJ) objs(IteratorUtil.stream(result.iterator()).filter(Obj::isPoly).map(o -> o.parent(this).<Poly<?, ?>>as()).map(r -> r.<Poly>as().at(uri(nextKey))));
            }
        }
    }

    @Override
    default Rec plus(final Rec rhs) {
        final Map<Obj, Obj> newMap = new LinkedHashMap<>(this.recValue());
        rhs.elements().forEach(o -> newMap.compute(o.jvm().get0(), (k, v) -> null == v ? o.jvm().get1() :
                v.isPlusMonoid() && o.isPlusMonoid() ? (Obj) v.<PlusMonoid.O>as().plus(o.jvm().get1().<PlusMonoid.O>as()) :
                        v.append(o.jvm().get1())));
        return this.jvm(newMap);
    }

   /* default Obj apply(final Obj lhs) {
        if (lhs.isRec())
            return Poly.Helper.applyRecRecursion(lhs.asRec(), this);
        else return this;
    }*/


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

    final class RecType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    // instC(AS_INST_TID.dom(REC_TID).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> lhs.tid(inst.arg(0).vidOrTid())),
                    instC(AS_INST_TID.dom(REC_TID).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> Poly.Helper.transformRecToLst(lhs.asRec(), inst.arg(0).tid(), fnull)),
                    // instC(AS_INST_TID.dom(REC_TID).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> Optional.of(lhs).filter(o ->o.matches(inst.arg(0))).map(o-> o.tid(inst.arg(0).tid())).orElseThrow(() -> MTronException.of("unable to resolve %s to %s", lhs, inst.arg(0)))),
                    instC(AS_INST_TID.dom(REC_TID).rng(URI_TID), lst(URI_TYPE), (lhs, inst) -> {
                        final Rec lhsRec = lhs.asRec();
                        fURI furi = new fURI();
                        if (lhsRec.has(SCHEME))
                            furi = furi.scheme(lhsRec.at(SCHEME).asUri().uriValue().toString());
                        if (lhsRec.has(HOST))
                            furi = furi.host(lhsRec.at(HOST).asUri().uriValue().toString());
                        if (lhsRec.has(PORT))
                            furi = furi.port(lhsRec.at(PORT).asInt().intValue().intValue());
                        if (lhsRec.has(PATH))
                            furi = furi.path(lhsRec.at(PATH).asLst().elements().map(Obj::uriValue).map(fURI::toString).filter(s -> !s.isEmpty()).collect(Collectors.joining("/")));
                        if (lhsRec.has(C))
                            furi = furi.c(cInt.of(lhsRec.at("c/min").asInt().intValue(), lhsRec.at("c/max").asInt().intValue()).toString());
                        if (lhsRec.has(Q))
                            furi = furi.queryMap(lhsRec.at(Q).asRec().elements().map(e -> Tuple.Pair.with(e.first().toCleanString(), e.second().toCleanString())).collect(Collectors.toMap(Tuple.Pair::get0, Tuple.Pair::get1)));
                        return uri(furi);
                    }),
                    instC(REVERSE_INST_TID.dom(REC_TID).rng(REC_TID), lst(), (lhs, inst) -> new ArrayList<Rel>(lhs.asRec().elements().toList()).reversed().stream().collect(new CommonUtil.RecCollector(lhs.tid(), lhs.vid()))),
                    instC(HAS_INST_TID.dom(REC_TID).rng(REC_TID.maybe()), lst(T(ALL), T(ALL)), (lhs, inst) -> {
                        final Obj result = inst.arg(1).apply(lhs.asRec().at(inst.arg(0)));
                        return result.isFail() ? result : (result.booleanCheck() ? lhs : noobj());
                    }),
                    instC(HAS_INST_TID.dom(REC_TID).rng(REC_TID.maybe()), lst(T(ALL)), (lhs, inst) -> inst.arg(0).isRel() ?
                            (lhs.<Rec>as().elements().anyMatch(r -> r.test(inst.arg(0))) ? lhs : noobj()) :
                            (lhs.<Rec>as().elements().map(Rel::first).anyMatch(r -> r.test(inst.arg(0))) ? lhs : noobj())),
                    instC(GET_INST_TID.dom(REC_TID).rng(ALL_STAR), lst(T(URI_TID)), (lhs, inst) -> objs(lhs.stream().map(r -> r.<Rec>as().at(inst.arg(0))))),
                    instC(SPLIT_INST_TID.dom(ALL).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> inst.arg(0).asRec().elements().map(e -> rel(e.first().apply(lhs), e.second().apply(lhs))).collect(new CommonUtil.RecCollector())),
                    instC(MERGE_INST_TID.dom(REC_TID).rng(REL_TID.maybeSome()), lst(), (lhs, inst) -> objs(lhs.elements())),
                    //instC(MERGE_INST_TID.dom(REC_TID).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> inst.arg(0).<Rec>as().plus(lhs.as())),//objs(lhs.elementStream())),
                    instC(DOM_INST_TID.dom(REC_TID).rng(ALL_STAR), lst(), (lhs, inst) -> objs(lhs.recValue().keySet())),
                    instC(RNG_INST_TID.dom(REC_TID).rng(ALL_STAR), lst(), (lhs, inst) -> objs(lhs.recValue().values())),
                    instC(RSHIFT_INST_TID.dom(REC_TID).rng(ALL_STAR), lst(T(ALL.maybeSome())), (lhs, inst) -> objs(inst.arg(0).orElse((Obj) uri("+")).stream().map(k -> lhs.asRec().at(k)))),
                    // instC(LSHIFT_INST_TID.dom(REC_TID).rng(ALL_STAR), lst(), (lhs, inst) -> lhs.parent()),
                    instC(PLUS_INST_TID.dom(REC_TID).rng(REC_TID), lst(T(REC_TID.maybeMaybe())), (lhs, inst) -> lhs.jvm(lhs.asRec().plus(inst.arg(0).asRec()).recValue())),
                    instC(MPLUS_INST_TID.dom(REC_TID).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> inst.arg(0).<Rec>as().elements().map(Obj::<Obj>as).reduce(lhs.<Rec>as(), (a, b) -> a.<Rec>as().at(((Rel) b).first(), ((Rel) b).second(), MUTABLE))),
                    instC(SELECT_INST_TID.dom(REC_TID).rng(REC_TID.maybe()), lst(T(REC_TID)), (lhs, inst) -> selectRecRecursion(lhs.asRec(), inst.arg(0).asRec())),
                    instC(SELECT_INST_TID.dom(REC_TID).rng(ALL.maybe()), lst(T(URI_TID)), (lhs, inst) -> lhs.asRec().at(inst.arg(0))),
                    //  instC(SELECT_INST_TID.dom(REC_TID).rng(REC_TID.maybe()), lst(T(URI_TID).c(cInt.of(2,null)).asType()), (lhs, inst) -> inst.args().elements().map(u -> rel(u,lhs.asRec().at(u))).collect(new CommonUtil.RecCollector())),
                    instC(SELECT_INST_TID.dom(REC_TID).rng(REC_TID.maybe()), lst(T(URI_TID.c("2,"))), (lhs, inst) -> inst.arg(0).stream().map(u -> rel(u, lhs.asRec().at(u))).collect(new CommonUtil.RecCollector())),
                    instC(UPDATE_INST_TID.dom(REC_TID).rng(REC_TID.maybe()), lst(REC_TYPE), (lhs, inst) -> Poly.Helper.updateRecRecursion(lhs.asRec(), inst.arg(0).asRec(), MUTABLE)),// inst.arg(0).asRec().elements().map(r -> lhs.asRec().at(r.first(), r.second(), MUTABLE)).filter(o -> false).findFirst().orElse(lhs.as())),
                    instC(WITHIN_INST_TID.dom(REC_TID).rng(REC_TID), lst(T(ALL_STAR)), (lhs, inst) -> rec(lhs.elements().map(r -> inst.arg(0).apply(r).<Rel>as()))),
                    instC(SUM_INST_TID.dom(REC_TID.maybeSome()).rng(REC_TID), lst(), (lhs, inst) -> lhs.stream().map(Obj::asRec).reduce(rec(), Rec::plus))
            ));


        }


    }
}