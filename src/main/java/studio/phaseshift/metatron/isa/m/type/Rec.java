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


import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.util.*;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.map_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.ObjFactory.LOG;
import static studio.phaseshift.metatron.isa.m.type.Poly.Helper.autoToggle;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.immutableMap;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public interface Rec extends Poly<Rec, Map<Obj, Obj>>, PlusMonoid.O<Rec> {

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

    @Override
    default Rec plus(final Rec rhs) {
        return Poly.Helper.mergeObjRecursion(this, rhs).asRec();
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
                    result = arec.parent();
                } else if (step.equals("+") || step.equals("#")) {
                    result = objs(isBranch ?
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
                        result = (isBranch ? rel(asNode, temp) : temp).c(c -> c.mult(cKey)).parent(arec);
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

        public static Obj lshiftRec(final Rec lhs, final Obj arg) {
            if (arg.isNoObj())
                return objs(lhs.asRec().keys());
            else if (arg.isUri() && arg.uriValue().segments(0, "").equals(".."))
                return arg.uriValue().segmentLength() > 1 ? auto_(map_(lhs.parent()).rshift_(arg.uriValue().pretract(1).toUri())).tryToInst() : lhs.parent();
            else return noobj();
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

    /*
     * WHAT REMAINS HERE, and why: NOTHING. Both `as` conversions this class registered are gone -- rec->lst and
     * rec->uri are the LOSSY directions of their pairs (a rec of pairs re-keys and reorders through a lst, and only
     * a rec of uri components reads back as a uri), so they declare NO inverse anywhere and live in mInsts.recs()
     * beside the rec operations. The SOUND uri->rec direction, whose inverse is rec->uri, still cannot leave
     * Uri.UriType because its body calls Uri.Helper.parseQ (package-private there), so it keeps its own law row;
     * no row declares rec->uri any more. The class stays as the carrier's registration hook.
     */
    final class RecType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.<Inst>of());
        }
    }
}
