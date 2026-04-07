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
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.Singleton;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public interface Lst extends Poly<Lst, List<Obj>>, PlusMonoid.O<Lst> {

    public static final Type LST_TYPE = Type.Builder.build()
            .tid(LST_TID)
            .vid(LST_TID).create();

    @Override
    default Stream<Rel> indexedStream() {
        final AtomicInteger i = new AtomicInteger(0);
        return this.jvm().stream().map(e -> rel(jnt(i.getAndIncrement()), e).c(c -> this.c()).as());
    }

    @Override
    Lst clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    List<Obj> jvm();

    @Override
    default long count() {
        return this.jvm().size();
    }

    default Lst add(final Obj obj) {
        return this.add(obj, IMMUTABLE);
    }

    default Lst add(final Obj obj, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
        final ArrayList<Obj> newList = new ArrayList<>(this.lstValue());
        newList.add(obj);
        return (Lst) operation.apply(this, newList);
    }

    @Override
    default boolean has(final Obj key) {
        return key.isInt() ? this.jvm().size() > key.intValue().intValue() :
                key.isUri() &&
                        !key.uriValue().isEmpty() &&
                        CommonUtil.isInt(key.uriValue().path().getFirst()) &&
                        this.jvm().size() > Integer.valueOf(key.uriValue().path().getFirst());
    }

    default <OBJ extends Obj> Stream<OBJ> elements() {
        return (Stream) IteratorUtil.stream(this.jvm()).map(e -> e.autoResolve(this).c(c -> c.mult(this.c())));
    }

    @Override
    default Stream<Obj> values() {
        return this.elements();
    }

    @Override
    default Stream<Obj> keys() {
        return this.indexedStream().map(Rel::first);
    }

    default Lst at(final Obj key, final Obj value, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
        if (key.isInt()) {
            final ArrayList<Obj> newList = new ArrayList<>(this.lstValue());
            if (value.isNoObj())
                newList.remove(key.intValue().intValue());
            else
                newList.set(key.intValue().intValue(), value);
            return this.clone(newList, this.tid(), this.vid());
        } else if (key.isUri()) {
            final Int k = jnt(Long.parseLong(key.uriValue().path().get(0)));
            if (key.uriValue().path().size() == 1) {
                return this.at(k, value, operation);
            } else {
                final Obj v = this.jvm().get(k.intValue().intValue());
                if (v.isPoly()) {
                    return this.at(k, v.<Poly<?, ?>>as().at(uri(key.<Uri>as().uriValue().pretract(1)), value, operation), operation).as();
                } else {
                    throw MTronException.of("unknown key value for lst: %s => %s", key, value);
                }
            }
        } else {
            throw MTronException.of("unknown key for lst: %s", key);
        }
    }

    default <OBJ extends Obj> OBJ at(final int index) {
        return this.at(jnt(index));
    }

    @Override
    default <OBJ extends Obj> OBJ at(final Obj key) {
        final cInt cKey = key.c();
        if (key.isInt())
            return (OBJ) ((this.jvm().size() > key.intValue()) ? this.jvm().get(key.<Int>as().intValue().intValue()).autoResolve(this) : noobj()).parent(this).c(c -> c.mult(cKey));
        else if (key.isUri()) {
            // LOG.info("key: %s", key);
            // if (key.uriValue().isEmpty())
            //            return this.c(c -> c.mult(cKey)).as();

            if (key.uriValue().segmentLength() == 0)
                return (OBJ) noobj();
            final String step = key.uriValue().segments().getFirst();
            final boolean isBranch = key.uriValue().isBranch();
            Stream<Obj> result;
            // LOG.info("step: %s", step);
            if (step.equals(Singleton.WILD_ONE.toString()) || step.equals(ALL.toString())) {
                result = isBranch ? (Stream) this.indexedStream() : this.elements();
            } else {
                if (!CommonUtil.isInt(step))
                    return (OBJ) noobj();
                //throw MTronException.of("path segment is not an int: %s", step);
                final Int k = jnt(Long.parseLong(step));
                if (this.jvm().size() <= k.intValue().intValue())
                    return (OBJ) noobj();
                result = isBranch ? Stream.of(rel(uri(step), this.at(k.intValue().intValue()))) : Stream.of(this.at(k.intValue().intValue()));
            }
            if (key.uriValue().segmentLength() == 1) {
                return (OBJ) objs(result.filter(x -> !x.isNoObj()).map(x -> x.c(c -> c.mult(cKey)).parent(this)));
            } else {
                return (OBJ) objs(result.filter(x -> !x.isNoObj()).filter(Obj::isPoly).map(x -> (Poly<?, ?>) x.c(c -> c.mult(cKey)).parent(this))
                        .map(r -> isBranch ?
                                r.at(uri(key.<Uri>as().uriValue().pretract(1).asBranch())) :
                                r.at(uri(key.<Uri>as().uriValue().pretract(1)))));
            }
        } else {
            throw MTronException.of("unknown key for lst: %s", key);
        }
    }

    @Override
    default Lst c(final Function<cInt, cInt> f) {
        return (Lst) Poly.super.c(f);
    }

    @Override
    default Lst plus(final Lst rhs) {
        final List<Obj> list = new ArrayList<>();
        this.elements().map(e -> e.c(c -> c.mult(this.c()))).forEach(list::add);
        rhs.elements().map(e -> e.c(c -> c.mult(rhs.c()))).forEach(list::add);
        return this.<Lst>jvm(list).c(cInt::one);
    }

    @Override
    default Lst zero() {
        return lst(List.of());
    }

    @Override
    default boolean test(final Obj rhs) {
        if (rhs.isLst()) {
            //if (rhs.lstValue().size() > this.lstValue().size())
            //    return false;
            for (int i = 0; i < rhs.lstValue().size(); i++) {
                final Obj r = rhs.lstValue().get(i).autoResolve(this);
                if (this.lstValue().size() <= i) {
                    if (!r.c().isZeroable())
                        return false;
                    else continue;
                }
                final Obj l = this.lstValue().get(i).autoResolve(this);
                if (!l.test(r))
                    return false;
            }
            return true;
        } else {
            return Poly.super.test(rhs);
        }
    }

    public static final class LstType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(AS_INST_TID.dom(LST_TID).rng(LST_TID), lst(LST_TYPE), (lhs, inst) -> lhs.tid(inst.arg(0).vidOrTid())), // TODO: vidOrTid -- sketchy
                    instC(AS_INST_TID.dom(LST_TID).rng(REC_TID), lst(REC_TYPE), (lhs, inst) -> Poly.Helper.transformLstToRec(lhs.asLst(), inst.arg(0).vidOrTid(), null)),
                    instC(AS_INST_TID.dom(LST_TID).rng(CODE_TID), lst(T(CODE_TID)), (lhs, inst) -> code(lhs.asLst().jvm().stream().map(Obj::asInst).toList())),
                    instC(REVERSE_INST_TID.dom(LST_TID).rng(LST_TID), lst(), (lhs, inst) -> lhs.jvm(lhs.asLst().jvm().reversed())),
                    instC(PLUS_INST_TID.dom(LST_TID).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> lhs.jvm(Stream.concat(lhs.elements(), inst.arg(0).elements()).toList())),
                    instC(MULT_INST_TID.dom(LST_TID).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> lhs.jvm(lhs.elements().flatMap(a -> inst.arg(0).elements().map(b -> rel(a, b))).toList())),
                    instC(RSHIFT_INST_TID.dom(LST_TID).rng(ALL_STAR), lst(T(ALL.maybeSome())), (lhs, inst) -> objs(inst.arg(0).orElse((Obj) uri(Singleton.WILD_ONE.toString())).stream().map(k -> lhs.asLst().at(k)))),
                    // instC(LSHIFT_INST_TID.dom(LST_TID).rng(ALL_STAR), lst(isa_(INT_TYPE).else_(jnt(1))), (lhs, inst) -> lhs.parent()),
                    instC(ZERO_INST_TID.dom(LST_TID).rng(LST_TID), lst(), (lhs, inst) -> lhs.asLst().zero()),
                    instC(SPLIT_INST_TID.dom(ALL).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> lst(inst.arg(0).elements().map(e -> e.apply(lhs)).toList())),
                    instC(MERGE_INST_TID.dom(LST_TID).rng(ALL_STAR), lst(), (lhs, inst) -> objs(lhs.elements())),
                    instC(MERGE_INST_TID.dom(LST_TID).rng(ALL_STAR), lst(URI_TYPE), (lhs, inst) -> uri(lhs.elements().map(e -> e.uriValue().toString()).reduce("", (a, b) -> a + inst.arg(0).uriValue().toString() + b).substring(1))),
                    instC(MERGE_INST_TID.dom(LST_TID).rng(ALL_STAR), lst(STR_TYPE), (lhs, inst) -> str(lhs.elements().map(Obj::strValue).reduce("", (a, b) -> a + inst.arg(0).strValue() + b).substring(1))),
                    instC(HAS_INST_TID.dom(LST_TID).rng(LST_TID.maybe()), lst(T(ALL)), (lhs, inst) -> lhs.<Lst>as().elements().anyMatch(r -> r.test(inst.arg(0))) ? lhs : noobj()),
                    instC(WITHIN_INST_TID.dom(LST_TID).rng(LST_TID), lst(T(ALL_STAR)), (lhs, inst) -> lst(inst.arg(0).apply(objs(lhs.stream().flatMap(Obj::elements))).stream().toList())),
                    instC(SUM_INST_TID.dom(LST_TID.maybeSome()).rng(LST_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> ((Lst) a).plus((Lst) b)).lstValue()), lst()),
                    instC(SELECT_INST_TID.dom(LST_TID).rng(LST_TID.maybe()), lst(T(LST_TID)), (lhs, inst) -> Poly.Helper.selectLstRecursion(lhs.asLst(), inst.arg(0).asLst())),
                    instC(UPDATE_INST_TID.dom(LST_TID).rng(LST_TID), lst(LST_TYPE), (lhs, inst) -> inst.arg(0).elements().reduce(lhs, (a, b) -> a.asLst().add(b, MUTABLE))),
                    instC(POW_INST_TID.dom(LST_TID).rng(LST_TID), lst(INT_TYPE), (lhs, inst) -> {
                        int pow = inst.arg(0).intValue().intValue();
                        Lst l = lhs.clone(lhs.jvm(), lhs.tid(), null);
                        for (int i = 0; i < pow; i++) {
                            l = l.jvm(l.elements().flatMap(a -> inst.arg(0).elements().map(b -> rel(a, b))).toList());
                        }
                        return lhs.jvm(l);
                    })
            ));
        }
    }


}