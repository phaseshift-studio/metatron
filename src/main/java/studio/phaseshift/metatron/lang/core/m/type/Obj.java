/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

import studio.phaseshift.metatron.algebra.MultMonoid;
import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.algebra.Ring;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.facade.FObj;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Streamable;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.util.Tuple.Pair;

public interface Obj extends Function<Obj, Obj>, Streamable<Obj>, Iterable<Obj>, Feature.HasLogger, Cloneable {

    private static boolean typeInferenceMatch(final Obj lhs, final Type rhs) {
        if (lhs.tid().matches(rhs.tid()))
            return true;
        if (rhs.isBaseType())
            return lhs.baseType().matches(rhs.tid()); // matches any abstract type to it's base type as long as within the coefficient boundaries
        if (rhs.tid().isZero() && !lhs.isNoObj()) // TODO: hack because zero can be the empty string and noobj string. fix.
            return false;
        if (rhs.tid().hasPattern() && !lhs.tid().matches(rhs.tid()))
            return false;
        return null == rhs.predicate() || !rhs.predicate().apply(lhs).isNoObj();

    }

    default <O extends Obj> O maybe() {
        return (O) this.c(cInt::maybe);
    }

    <J> J jvm();

    fURI tid();

    fURI vid();

    default boolean unique() {
        return uniqueC().equals(cInt.ONE());
    }

    default cInt uniqueC() {
        return cInt.ONE();
    }

    default boolean clessEquals(final Object other) {
        return Helper.objcLessEquals(this, other);
    }

    default cInt c() {
        return this.tid().cV();
    }

    default Obj c(final cInt c) {
        return this.c(oldC -> c);
    }

    default Obj c(final Function<cInt, cInt> func) {
        final cInt oldC = this.c();
        final cInt newC = func.apply(oldC);
        return Objects.equals(oldC, newC) ? this : this.tid(this.tid().c(null == newC || newC.isOne() ? null : newC.toString()));
    }

    default Obj c(final Long exact) {
        return this.c(cInt.of(exact));
    }

    default Obj c(final Long min, final Long max) {
        return this.c(cInt.of(min, max));
    }

    default Pair<Obj, Obj> take(final cInt c) {
        if (c.lte(this.tid().cV())) {
            final Obj remaining = this.tid(this.tid().c(this.tid().cV().minus(c).toString()));
            final Obj result = this.tid(this.tid().c(c.toString()));
            return Pair.with(result, remaining);
        } else {
            return Pair.with(noobj(), this);
        }
    }

    default Pair<Obj, Obj> take(final Inst inst) {
        if (!inst.tid().hasDom() || (this.uniqueC().equals(cInt.ONE()) && inst.dom().c().gt(cInt.ZERO())))
            return Pair.with(this, noobj());
        else if (inst.dom().isZero() || this.isNoObj())
            return Pair.with(noobj(), this);
        else if (this.c().within(inst.dom().c()))
            return Pair.with(this, noobj());
        else if (inst.dom().c().most().within(this.c()))
            return Pair.with(this.c(inst.dom().c().most()), this.c(c -> c.minus(inst.dom().c().most())));
        else if (inst.dom().c().least().within(this.c()))
            return Pair.with(this.c(inst.dom().c().min()), this.c(c -> c.minus(inst.dom().c().least())));
        else { // if the obj can't be split, just return it (will typically lead to an evaluation error)
            return Pair.with(this, noobj());
        }
    }

    default Obj take() {
        throw MTronException.of("%s can not be taken from", this);
    }

    default Obj resolve(final Obj lhs) {
        return this;
    }

    default fURI vidOrTid() {
        return this.vid() == null ? this.tid() : this.vid();
    }

    default Type type() {
        return T(this.tid()); // null == Router.global() || this.isInst() ? MType.of(this.tid()) : Router.global().read(this.tid()).orElse(MType.of(this.tid()));
    }

    <O extends Obj> O clone(final Object jvm, final fURI tid, final fURI vid);

    default <O extends Obj> O jvm(final Object jvm) {
        return this.clone(jvm, this.tid(), this.vid());
    }

    default <O> O jvmAs() {
        return this.jvm();
    }

    default Stream<Obj> stream() {
        return this.isNoObj() ? Stream.empty() : IteratorUtil.stream(this);
    }

    default <O extends Obj> Stream<O> elements() {
        return (Stream) this.stream();
    }

    default Obj tid(final fURI tid) {
        if (this.tid().basePath().equals(tid))
            return this.tid().equals(tid) ? this : this.clone(this.jvm(), tid, this.vid());
        if (BASE_TYPES.contains(tid.basePath()) && this instanceof FObj<?>) // unwrap a facade
            return ((FObj<?>) this).base().tid(tid).vid(this.vid());
        return this.clone(this.jvm(), tid, this.vid());
    }

    default Obj tid(final String tid) {
        return this.tid(f(tid));
    }

    /*default boolean inSpace() {
        return null != this.vid();
    }*/

    default Obj vid(final fURI vid) {
        return this.clone(this.jvm(), this.tid(), vid);
    }

    default Obj append(final Obj obj) {
        if (obj.isNoObj())
            return this;
        if (this.isObjs())
            return this.<Objs>as().append(obj);
        else {
            final List<Obj> objs = new ArrayList<>();
            if (!this.isNoObj())
                objs.add(this);
            if (!obj.isNoObj())
                objs.add(obj);
            if (objs.isEmpty())
                return noobj();
            if (objs.size() == 1)
                return objs.get(0);
            return objs(objs);
        }
    }

    @Override
    default Obj apply(final Obj other) {
        return this;//.c(c -> c.mult(other.c())); // need to redefine equality (no c() test)
    }

    default Obj apply() {
        return this.apply(noobj());
    }

    default boolean matches(final Obj rhs) {
        if (this.isNoObj() && rhs.isNoObj())
            return true;
        else if (this.isNoObj() && rhs.tid().cV().isNoObjable())
            return true;
        else if (rhs.isNoObj())
            return false;
        final fURI base = this.tid().basePath();
        if (BASE_TYPES.contains(base) &&
                !(this instanceof Objs) &&
                !(this instanceof Type) &&
                !((this.isBool() && base.equals(BOOL_TID)) ||
                        (this.isBytes() && base.equals(BYTES_TID)) ||
                        (this.isInt() && base.equals(INT_TID)) ||
                        (this.isReal() && base.equals(REAL_TID)) ||
                        (this.isStr() && base.equals(STR_TID)) ||
                        (this.isUri() && base.equals(URI_TID)) ||
                        (this.isRec() && base.equals(REC_TID)) ||
                        (this.isLst() && base.equals(LST_TID)) ||
                        (this.isRel() && base.equals(REL_TID)) ||
                        (this.isInst() && base.equals(INST_TID)) ||
                        (this.isCode() && base.equals(CODE_TID)) ||
                        (this.isFail() && base.equals(FAIL_TID)))) {
            return false;
        }
        if (this.isCall())
            return this.tid().cV().within(rhs.tid().cV()); // TODO: this is really flimsy.
        if (rhs.isCall())
            return this.matches(rhs.dom()) && rhs.apply(this).matches(rhs.rng());// && rhs.apply(this).matches(rhs.rng());
        if (!this.c().within(rhs.c()))
            return false;
        if (rhs.isType())
            return rhs.tid().isGeneric() ||
                    (typeInferenceMatch(this, rhs.as()) &&
                            (rhs.<Type>as().predicate() == null || this.isObjs() || !rhs.apply(this).isNoObj()));
        return this.tid().matches(rhs.tid()) &&
                Objects.equals(this.jvm(), rhs.jvm());
    }

    @Override
    default Iterator<Obj> iterator() {
        return this.isNoObj() ? IteratorUtil.of() : (this.isObjs() ? this.objsValue().iterator() : IteratorUtil.of(this));
    }

    default Type dom() {
        return T(fURI.ALL.maybe());
    }

    default Type rng() {
        return T(this.tid());
    }

    default fURI baseType() {
        if (this.isBool()) return BOOL_TID.c(this.c().toString());
        else if (this.isBytes()) return BYTES_TID.c(this.c().toString());
        else if (this.isInt()) return INT_TID.c(this.c().toString());
        else if (this.isReal()) return REAL_TID.c(this.c().toString());
        else if (this.isStr()) return STR_TID.c(this.c().toString());
        else if (this.isUri()) return URI_TID.c(this.c().toString());
        else if (this.isLst()) return LST_TID.c(this.c().toString());
        else if (this.isRec()) return REC_TID.c(this.c().toString());
        else if (this.isInst()) return INST_TID.c(this.c().toString()).dom(this.dom().tid()).rng(this.rng().tid());
        else if (this.isCode()) return CODE_TID.c(this.c().toString());
        else if (this.isNoObj()) return NOOBJ_TID.c(this.c().toString());
        else if (this.isFail()) return FAIL_TID.c(this.c().toString());
        else return this.tid();
    }

    default <O extends Obj> O orElse(final O other) {
        return this.isNoObj() ? other : (O) this;
    }

    default <O extends Obj> O orSupply(final Supplier<O> other) {
        return this.isNoObj() ? other.get() : (O) this;
    }

    default <O extends Obj> O orThrow(final RuntimeException e) {
        if (this.isNoObj())
            throw e;
        return (O) this;
    }

    /*default <O extends Obj> void ifExists(final Consumer<O> function) {
        if (!this.isNoObj())
            function.accept((O) this);
    }

    default <O extends Obj> O andIf(final Predicate<O> predicate) {
        return predicate.test((O) this) ? (O) this : (O) noobj();
    }*/

    default <O extends Obj> O choose(final Predicate<Obj> predicate, final Function<Obj, O> trueBranch, final Function<Obj, O> falseBranch) {
        return predicate.test(this) ? trueBranch.apply(this) : falseBranch.apply(this);
    }

    default <O extends Obj> O as() {
        return (O) this;
    }

    default <F extends FObj<?>> F as(final Class<F> facade) {
        return facade.isAssignableFrom(this.getClass()) ?
                (F) this :
                (F) objs(this.stream().map(x -> MTronException.wrap(() -> (Obj) facade.getMethod("of", Obj.class).invoke(null, x))).map(Obj::<F>as));
    }

    default <O extends Obj> boolean is(final Class<O> clazz) {
        return clazz.isAssignableFrom(this.getClass());
    }

    default boolean isNoObj() {
        return this.c().isZero();
    }

    default boolean isFail() {
        return this instanceof Fail;
    }

    default boolean isBool() {
        return this instanceof Bool;
    }

    default boolean isBytes() {
        return this instanceof Bytes;
    }

    default boolean isInt() {
        return this instanceof Int;
    }

    default boolean isReal() {
        return this instanceof Real;
    }

    default boolean isStr() {
        return this instanceof Str;
    }

    default boolean isUri() {
        return this instanceof Uri;
    }

    default boolean isCall() {
        return this instanceof Call;
    }

    default boolean isObjCall() {
        return this instanceof Call && !this.isNoObj();
    }

    default boolean isRing() {
        return this instanceof Ring;
    }

    default boolean isPlusMonoid() {
        return this instanceof PlusMonoid;
    }

    default boolean isMultMonoid() {
        return this instanceof MultMonoid;
    }

    default boolean isRel() {
        return this instanceof Rel;
    }

    default boolean isLst() {
        return this instanceof Lst;
    }

    default boolean isRec() {
        return this instanceof Rec;
    }

    default boolean isInst() {
        return this instanceof Inst;
    }

    default Obj autoResolve(final Obj obj) {
        return this.isInst() && this.tid().basePath().equals(AUTO_INST_TID) ? this.apply(obj).autoResolve(obj) : this;
    }

    default Obj autoResolve() {
        return this.autoResolve(noobj());
    }

    default boolean isAutoResolve() {
        return this.isInst() && this.tid().basePath().equals(AUTO_INST_TID);
    }

    default boolean isInstObj() {
        return this instanceof Inst && !this.isNoObj();
    }

    default boolean isInstSet() {
        return this instanceof InstSet;
    }

    default boolean isObjs() {
        return this instanceof Objs;
    }

    default boolean isCode() {
        return this instanceof Code;
    }

    default boolean isPoly() {
        return this instanceof Poly;
    }

    default boolean isType() {
        return this instanceof Type;
    }


    String xxxValue = "%s is a %s, not a %s";

    default Throwable failValue() {
        if (this.isFail())
            return this.jvm();
        throw MTronException.of(xxxValue, this, tid().toUri(), FAIL_TID.toUri());
    }

    default boolean boolValue() {
        if (this.isBool())
            return this.jvm();
        throw MTronException.of(xxxValue, this, tid().toUri(), BOOL_TID.toUri());
    }

    default ByteBuffer bytesValue() {
        if (this.isBytes())
            return this.jvm();
        throw MTronException.of(xxxValue, this, tid().toUri(), BYTES_TID.toUri());
    }

    default Long intValue() {
        if (this.isInt())
            return this.jvm();
        throw MTronException.of(xxxValue, this, tid().toUri(), INT_TID.toUri());
    }

    default Double realValue() {
        if (this.isReal())
            return this.jvm();
        throw MTronException.of(xxxValue, this, tid().toUri(), REAL_TID.toUri());
    }

    default String strValue() {
        if (this.isStr())
            return this.jvm();
        throw MTronException.of(xxxValue, this, tid().toUri(), STR_TID.toUri());
    }

    default fURI uriValue() {
        if (this.isUri())
            return this.jvm();
        throw MTronException.of(xxxValue, this, tid().toUri(), URI_TID.toUri());
    }

    default List<Obj> lstValue() {
        if (this.isLst())
            return this.jvm();
        throw MTronException.of(xxxValue, this, tid().toUri(), LST_TID.toUri());
    }

    default Iterable<Obj> objsValue() {
        if (this.isObjs())
            return this.jvm();
        throw MTronException.of(xxxValue, this, tid().toUri(), OBJS_TID.toUri());
    }

    default Map<Obj, Obj> recValue() {
        if (this.isRec())
            return this.jvm();
        throw MTronException.of(xxxValue, this, tid().toUri(), REC_TID.toUri());
    }

    default Pair<Obj, Obj> relValue() {
        if (this.isRel())
            return this.jvm();
        throw MTronException.of(xxxValue, this, tid().toUri(), REL_TID.toUri());
    }

    default List<Inst> codeValue() {
        if (this.isCode())
            return this.jvm();
        throw MTronException.of(xxxValue, this, tid().toUri(), CODE_TID.toUri());
    }

    default Obj typeValue() {
        if (this.isType())
            return this.jvm();
        throw MTronException.of(xxxValue, this, tid().toUri(), fURI.of("<type>").toUri());
    }

    Obj clone();

    <O extends Obj> O self(final Object jvm, final fURI tid, final fURI vid);

    class Helper {

        public static int objHashCode(final Obj obj) {
            return obj.isNoObj() ? noobj().hashCode() : obj.isInst() ? obj.tid().hashCode() : Objects.hash(obj.jvm(), obj.tid().cLess());
        }

        public static boolean objEquals(final Obj obj, final Object other) {
            return other instanceof Obj &&
                    ((obj.isNoObj() && ((Obj) other).isNoObj()) ||
                            (Objects.equals(obj.tid(), ((Obj) other).tid()) &&
                                    Objects.equals(obj.vid(), ((Obj) other).vid()) && // TODO: ??
                                    Objects.equals(obj.jvm(), ((Obj) other).jvm())));
        }

        public static boolean objcLessEquals(final Obj obj, final Object other) {
            return other instanceof Obj &&
                    ((obj.isNoObj() && ((Obj) other).isNoObj()) ||
                            (Objects.equals(obj.tid().cLess(), ((Obj) other).tid().cLess()) && // TODO: no vid checked ...
                                    Objects.equals(obj.jvm(), ((Obj) other).jvm())));
        }

        public static String objToString(final Obj obj) {
            return Graphitty.string(obj);
        }

        public static void objCheckAndSave(final Obj obj) {
            if (!obj.isInstSet() && !obj.isNoObj() && !obj.isType() && !obj.matches(obj.type()))
                throw MTronException.of("[{{r}}type error{{/r}}] %s is not a %s".formatted(obj, obj.type()));
            if (null != obj.vid() && !obj.isType())
                Router.writeToSpace(obj.vid(), obj);
        }

        public static <O extends Obj> O objClone(final Obj obj, final Object jvm, final fURI tid, final fURI vid) {
            Object realjvm = jvm;
            Obj clone = null;
            if (BASE_TYPES.contains(tid.basePath()) && obj instanceof FObj<?>)
                return ((FObj<?>) obj).base().clone(jvm instanceof Obj ? ((Obj) jvm).jvm() : jvm, tid, vid);
            if (!Objects.equals(tid, obj.tid())) {
                final Obj type = Router.readFromSpace(tid);
                if (!type.isNoObj() && type.isType() && type.<Type>as().hasConstructor()) {
                    clone = type.<Type>as().constructor().apply(obj);
                    if (clone.isFail())
                        throw (MTronException) clone.<Fail>as().jvm();
                    realjvm = clone.jvm();
                }
            }
            if (!Objects.equals(realjvm, obj.jvm()) || !tid.equals(obj.tid()) || !Objects.equals(vid, obj.vid())) {
                try {
                    clone = null == clone ? obj.clone() : clone;
                    clone.self(realjvm, tid, vid);
                    Obj.Helper.objCheckAndSave(clone);
                    return (O) clone;
                } catch (final Exception e) {
                    throw MTronException.of(e);
                }
            }
            return (O) obj;
        }
    }
}