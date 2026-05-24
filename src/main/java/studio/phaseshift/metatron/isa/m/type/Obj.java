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

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.algebra.MultMonoid;
import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.algebra.Ring;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.impl.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Monad;
import studio.phaseshift.metatron.isa.mach.type.PCMonad;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.type.Content;
import studio.phaseshift.metatron.util.*;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.lang.System.lineSeparator;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Bool.*;
import static studio.phaseshift.metatron.isa.m.type.Bytes.BYTES_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Code.CODE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Fail.FAIL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Rel.REL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Type.TYPE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer.OBJ_SERIAL_TID;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MONAD_TID;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MONAD_TYPE;
import static studio.phaseshift.metatron.isa.mach.type.monad.BasicPCMonad.pcmonad;
import static studio.phaseshift.metatron.util.CommonUtil.indent;
import static studio.phaseshift.metatron.util.CommonUtil.nullOrElse;
import static studio.phaseshift.metatron.util.Tuple.Pair;

public interface Obj extends Function<Obj, Obj>, Streamable<Obj>, Iterable<Obj>, Feature.HasLogger, Cloneable, Predicate<Obj> {

    default <O extends Obj> O maybe() {
        return (O) this.c(cInt::maybe);
    }

    default <O extends Obj> O maybeSome() {
        return (O) this.c(cInt::maybeSome);
    }

    default <O extends Obj> O some() {
        return (O) this.c(cInt::some);
    }

    default <O extends Obj> O any() {
        return (O) this.c(cInt::any);
    }

    default <O extends Obj> O antiMaybe() {
        return (O) this.c(cInt::antiMaybe);
    }

    default <O extends Obj> O antiMaybeSome() {
        return (O) this.c(cInt::antiMaybeSome);
    }

    default <O extends Obj> O antiSome() {
        return (O) this.c(cInt::antiSome);
    }

    <J> J jvm();

    fURI tid();

    fURI vid();

    default <O extends Obj> O parent(final Poly<?, ?> parent) {
        return (O) this;
    }

    default Obj parent() {
        return noobj();
    }

    default boolean isResolved(final boolean nested) {
        return true;
    }

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
        return this.tid().c();
    }

    default Obj c(final cInt c) {
        return this.c(x -> c);
    }

    default Obj c(final Function<cInt, cInt> func) {
        final cInt oldC = this.c();
        final cInt newC = func.apply(oldC);
        return Objects.equals(oldC, newC) ? this : this.tid(this.tid().c(null == newC || newC.isOne() ? null : newC));
    }

    default Obj c(final Long exact) {
        return this.c(cInt.of(exact));
    }

    default Pair<Obj, Obj> take(final cInt c) {
        return Pair.with(this.c(c), this.c(this.c().minus(c)));
    }

    default Obj take() {
        final Obj clone = this.clone();
        this.self(this.jvm(), this.tid().zero(), this.vid());
        return clone;
    }

    default Obj resolve(final Obj lhs) {
        return this;
    }

    default fURI vidOrTid() {
        return this.vid() == null ? this.tid() : this.vid();
    }

    default Type type() {
        return this.isType() ? T(this.vid()) : T(this.tid()); // null == Router.global() || this.isInst() ? MType.of(this.tid()) : Router.global().read(this.tid()).orElse(MType.of(this.tid()));
    }

    <O extends Obj> O clone(final Object jvm, final fURI tid, final fURI vid);

    default <O extends Obj> O jvm(final Object jvm) {
        return this.clone(jvm, this.tid(), this.vid());
    }

    default <O> O jvmAs() {
        return this.jvm();
    }

    default Stream<Obj> stream() {
        return this.isNoObj() ? Stream.empty() : Stream.of(this);
    }

    default <O extends Obj> Stream<O> elements() {
        return (Stream) this.stream();
    }

    default Obj tid(final fURI tid) {
        final fURI bigTID = tid.big();
        return this.tid().equals(bigTID) ? this : this.clone(this.jvm(), bigTID, this.vid());
    }

    default Obj tid(final String tid) {
        return this.tid(f(tid));
    }

    default Obj vid(final fURI vid) {
        return this.clone(this.jvm(), this.tid(), vid);
    }

    default Obj selfVID(final fURI vid) {
        return this.self(this.jvm(), this.tid(), vid);
    }

    default Obj selfTID(final fURI tid) {
        return this.self(this.jvm(), tid, this.vid());
    }

    default Obj selfJVM(final Object jvm) {
        return this.self(jvm, this.tid(), this.vid());
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

    default boolean testByID(final Obj rhs) {
        if (this.isType() && !rhs.isType())
            return false;
        final Type rhsType = rhs.isType() ? rhs.asType() : rhs.type();
        if (this.c().isZero()) return rhsType.c().isZeroable();
        if (rhsType.isRootType())
            return this.c().within(rhsType.c());
        Type lhsType = this.isType() ? this.asType() : this.type();
        if (lhsType.isGeneric() && rhsType.isGeneric())
            return lhsType.tid().test(rhsType.tid());// lhsType.c().within(rhsType.c());//&& lhsType.tid().basePath().equals(rhsType.tid().basePath());

        if (rhsType.hasPredicate()) {
            if (lhsType.isType()) {
                if (!lhsType.hasPredicate() || !Objects.equals(lhsType.predicate(), rhsType.predicate()))
                    return false;
            } else if (rhsType.predicate().apply(this).isNoObj())
                return false;
        }
        while (!lhsType.isRootType()) {
            if (lhsType.vid().test(rhsType.vid()))
                return true;
            if (rhsType.tid().isGeneric())
                return lhsType.vid().c().within(rhsType.tid().c());
            if (rhsType.vid() != null && rhsType.vid().isGeneric() && lhsType.vid().c().within(rhsType.vid().c()))
                return true;
            lhsType = lhsType.parentType();
        }
        return false;
    }

    default boolean test(final Obj rhs) {
        if (Obj.Helper.isAuto(rhs))
            return true;
        else if (this.isNoObj())
            return (rhs.tid().c().isZeroable() || rhs.tid().equals(NOOBJ_TID));
        else if (rhs.isNoObj())
            return this.c().isZeroable();
        if (rhs.isObjCall() && !rhs.asCall().isPredicate(this))
            return true;
        if (rhs.isType() && !rhs.asType().isBaseType() && this.tid().test(rhs.vid()))
            return rhs.asType().isRootType() ?
                    this.c().within(rhs.c()) :
                    (!rhs.asType().hasPredicate() || !rhs.asType().predicate().apply(this).isNoObj());
        /// //////////////////////////////
        if (rhs.isUri() && this.isUri() && !this.uriValue().test(rhs.uriValue()))
            return false;
        /*final fURI base = this.tid().basePath();
        if (BASE_TYPES.contains(base) &&
                !(this instanceof Objs) &&
                !((this.isBool() && base.equals(BOOL_TID)) ||
                        (this.isBytes() && base.equals(BYTES_TID)) ||
                        (this.isInt() && base.equals(INT_TID)) ||
                        (this.isReal() && base.equals(REAL_TID)) ||
                        (this.isStr() && base.equals(STR_TID)) ||
                        (this.isUri() && base.equals(URI_TID)) ||
                        (this.isRec() && base.equals(REC_TID)) ||
                        (this.isLst() && base.equals(LST_TID)) ||
                        (this.isRel() && base.equals(REL_TID)) ||
                        (this.isInst() && base.equals(M_ISA_INST_TID)) ||
                        (this.isCode() && base.equals(CODE_TID)) ||
                        (this.isType() && base.equals(TYPE_TID)) ||
                        (this.isFail() || this.isCaughtFail() && base.equals(FAIL_TID)))) {
            return false;
        }*/
        if (this.isObjCall())
            return this.tid().c().within(rhs.tid().c()); // TODO: this is really flimsy.
        if (rhs.isObjCall()) {
            return rhs.apply(this).test(rhs.rng());
        }
        if (!this.c().within(rhs.c()))
            return false;
        if (rhs.isType()) {
            return Type.Helper.typeCheck(this, rhs);
        }
        return this.tid().test(rhs.tid()) &&
                Objects.equals(this.jvm(), rhs.jvm());
    }

    @Override
    default Iterator<Obj> iterator() {
        return this.isNoObj() ? IteratorUtil.of() : (this.isObjs() ? this.objsValue().iterator() : IteratorUtil.of(this));
    }

    default boolean isNone() {
        return this.isUri() && this.uriValue().basePath().toString().equals("none");
    }

    static Obj none() {
        return NONE;
    }

    default Type dom() {
        return T(ALL.maybe());
    }

    default Type rng() {
        return T(this.tid());
    }

    default fURI baseType() {
        if (this.type().isRootType()) return ALL.c(this.c());
        else if (this.isBool()) return BOOL_TID.c(this.c());
        else if (this.isBytes()) return BYTES_TID.c(this.c());
        else if (this.isInt()) return INT_TID.c(this.c());
        else if (this.isReal()) return REAL_TID.c(this.c());
        else if (this.isStr()) return STR_TID.c(this.c());
        else if (this.isUri()) return URI_TID.c(this.c());
        else if (this.isLst()) return LST_TID.c(this.c());
        else if (this.isRec()) return REC_TID.c(this.c());
        else if (this.isRel()) return REL_TID.c(this.c());
        else if (this.isInst()) return M_ISA_INST_TID.c(this.c()).dom(this.dom().tid()).rng(this.rng().tid());
        else if (this.isCode()) return CODE_TID.c(this.c());
        else if (this.isNoObj()) return NOOBJ_TID.c(this.c());
        else if (this.isFail()) return FAIL_TID.c(this.c());
        else if (this.isType()) {
            final Type parent = this.asType().parentType();
            if (parent.isBaseType())
                return parent.tid();
            else return parent.baseType();
        }
       /* else if (this.isType()) {
            if(null != this.vid()) {
                final Obj temp = Router.readFromSpace(this.vid());
                if (temp.isType()) {
                    return temp.tid();
                }
            }
            return this.tid();
        }*/
        else return this.tid();
    }

    default <O extends Obj> O orElse(final O other) {
        return this.isNoObj() ? other : (O) this;
    }

    default <O extends Obj> boolean ifPresent(final Consumer<O> consumer) {
        if (this.isNoObj())
            return false;
        consumer.accept((O) this);
        return true;
    }

    default <O extends Obj> O orSupply(final Supplier<O> other) {
        return this.isNoObj() ? other.get() : (O) this;
    }

    default <O extends Obj> O orThrow(final RuntimeException e) {
        if (this.isNoObj())
            throw e;
        return (O) this;
    }

    default <O extends Obj> O as() {
        return (O) this;
    }

    default <O extends Obj> boolean is(final Class<O> clazz) {
        return clazz.isAssignableFrom(this.getClass());
    }

    default boolean isMono() {
        return this instanceof Mono;
    }

    default boolean isNoObj() {
        return this.c().isZero();
    }

    default boolean isFail() {
        return this instanceof Fail;
    }

    default boolean isCaughtFail() {
        return this instanceof MFail.MCaughtFail;
    }

    default boolean isUncaughtFail() {
        return this.isFail() && !(this instanceof MFail.MCaughtFail);
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

    default boolean isObjInst() {
        return this.isInst() && !this.isNoObj();
    }

    default boolean isRing() {
        return this instanceof Ring.O;
    }

    default boolean isPlusMonoid() {
        return this instanceof PlusMonoid.O;
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

    default boolean isMonad() {
        return this instanceof PCMonad;
    }

    default Obj autoResolve(final Obj obj) {
        final fURI base = this.tid().basePath();
        return this.isInst() && (base.equals(AUTO_FROM_INST_TID) || base.equals(AUTO_AT_INST_TID) || base.equals(AUTO_INST_TID)) ?
                this.apply(obj).c(c->obj.isNoObj() ? c : c.mult(obj.c())) :
                this;
        //   return Obj.Helper.getAutoPointer(this).map(Router::readFromSpace).orElse(this);
    }

    default Obj dereference() {
        return this.autoResolve(noobj());
    }

    default Obj reference() {
        return (null == this.vid() || this.isAutoFrom()) ? this : auto_from_(this.vid()).tryToInst();
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

    default boolean isAuto() {
        return Obj.Helper.isAuto(this);
    }

    default boolean isAutoFrom() {
        return Obj.Helper.isAutoPointer(this);
    }

    default Obj as(final Type type) {
        if (!type.hasPredicate() && !type.hasConstructor() && this.tid().equals(type.vid()))
            return this;
        if (type.hasPredicate()) {
            boolean match;
            try {
                match = this.test(type);
            } catch (final Exception e) {
                match = false;
            }
            if (!match)
                throw MTronException.of("%s is not a %s\n%s", this, type.predicate(), indent(Poly.Helper.diffObjRecursion(this, Type.Helper.typePredicateObj(type)).toString(), 2));
        }
        //return type.hasConstructor() ? type.constructor().apply(this).selfTID(type.vid()) : this.tid(type.vidOrTid());
        return type.hasConstructor() ? Obj.Helper.objClone(this, this.jvm(), type.vidOrTid(), this.vid()) : this.tid(type.vidOrTid());
    }

    default Bool asBool() {
        return (Bool) this;
    }

    default Bytes asBytes() {
        return (Bytes) this;
    }

    default Int asInt() {
        return (Int) this;
    }

    default Real asReal() {
        return (Real) this;
    }

    default Str asStr() {
        return (Str) this;
    }

    default Uri asUri() {
        return (Uri) this;
    }

    default Rec asRec() {
        return (Rec) this;
    }

    default Lst asLst() {
        return (Lst) this;
    }

    default Rel asRel() {
        return (Rel) this;
    }

    default Inst asInst() {
        return (Inst) this;
    }

    default Code asCode() {
        return (Code) this;
    }

    default Call asCall() {
        return (Call) this;
    }

    default PCMonad asMonad() {
        return (PCMonad) this;
    }

    default Type asType() {
        return (Type) this;
    }

    default Objs asObjs() {
        return (Objs) this;
    }

    default Fail asFail() {
        return (Fail) this;
    }

    String xxxValue = "%s [%s] unable to convert %s";

    default Pair<Throwable, Fail> failValue() {
        if (this.isFail() || this.isCaughtFail())
            return this.jvm();
        throw MTronException.of(xxxValue, this, T(tid()), FAIL_TYPE);
    }

    default boolean boolValue() {
        if (this.isBool())
            return this.jvm();
        throw MTronException.of(xxxValue, this, T(tid()), BOOL_TYPE);
    }

    default ByteBuffer bytesValue() {
        if (this.isBytes())
            return this.jvm();
        throw MTronException.of(xxxValue, this, T(tid()), BYTES_TYPE);
    }

    default Long intValue() {
        if (this.isInt())
            return this.jvm();
        throw MTronException.of(xxxValue, this, this.isType() ? "type" : "value", T(tid()), INT_TYPE);
    }

    default Double realValue() {
        if (this.isReal())
            return this.jvm();
        throw MTronException.of(xxxValue, this, T(tid()), REAL_TYPE);
    }

    default String strValue() {
        if (this.isStr())
            return this.jvm();
        throw MTronException.of(xxxValue, this, T(tid()), STR_TYPE);
    }

    default fURI uriValue() {
        if (this.isUri())
            return this.jvm();
        throw MTronException.of(xxxValue, this, T(tid()), URI_TYPE);
    }

    default List<Obj> lstValue() {
        if (this.isLst())
            return this.jvm();
        throw MTronException.of(xxxValue, this, T(tid()), LST_TYPE);
    }

    default Iterable<Obj> objsValue() {
        if (this.isObjs())
            return this.jvm();
        throw MTronException.of(xxxValue, this, T(tid()), OBJS_TID);
    }


    default Map<Obj, Obj> recValue() {
        if (this.isRec())
            return this.jvm();
        throw MTronException.of(xxxValue, this, T(tid()), REC_TYPE);
    }

    default Pair<Obj, Obj> relValue() {
        if (this.isRel())
            return this.jvm();
        throw MTronException.of(xxxValue, this, T(tid()), REL_TYPE);
    }

    default Tuple.Triplet<Poly<?, ?>, Inst.f, Obj> instValue() {
        if (this.isInst())
            return this.jvm();
        throw MTronException.of(xxxValue, this, T(tid()), INST_TYPE);
    }

    default List<Inst> codeValue() {
        if (this.isCode())
            return this.jvm();
        throw MTronException.of(xxxValue, this, T(tid()), CODE_TYPE);
    }

    default Obj typeValue() {
        if (this.isType())
            return this.jvm();
        throw MTronException.of(xxxValue, this, T(tid()), TYPE_TYPE);
    }

    default List<Obj> monadValue() {
        if (this.isMonad())
            return this.jvm();
        throw MTronException.of(xxxValue, this, T(tid()), MACH_MONAD_TYPE);
    }

    default String toCleanString() {
        if (this.isStr())
            return this.strValue();
        if (this.isUri())
            return this.uriValue().toString();
        else
            return this.toString();
    }

    default String toShortString() {
        if (this.isType() && null != this.vid())
            return this.vid().small().name() + (this.tid().isOne() ? "" : ("{" + this.tid().c().toString() + "}")) + "::T";
        return this.toString();
    }

    Obj clone();

    <O extends Obj> O self(final Object jvm, final fURI tid, final fURI vid);

    default void delete() {
        if (null != this.vid())
            Router.global().write(this.vid(), noobj());
    }

    /**
     * Returns the current obj stored at this obj's vid, or {@code this} if no vid is set.
     */
    default Obj load() {
        return null == this.vid() ? this : this.selfJVM(Router.readFromSpace(this.vid()).jvm());
    }

    default Obj save() {
        return null == this.vid() ? this : Router.global().write(this.vid(), this);
    }

    default boolean booleanCheck() {
        if (this.isNoObj() || this.isFail())
            return false;
        if (this.isBool())
            return this.boolValue();
        else return true;
    }

    class Helper {

        private static final ObjSerializer<String> SERIALIZER = new ObjmtronSerializer();

        public static boolean isAuto(final Obj obj) {
            return obj.isObjCall() && obj.tid().basePath().toString().startsWith("auto");
        }

        public static boolean isAutoPointer(final Obj obj) {
            return obj.tid().basePath().equals(AUTO_FROM_INST_TID) ||
                    obj.tid().basePath().equals(AUTO_AT_INST_TID);
        }

        public static boolean isPointer(final Obj obj) {
            return obj.tid().path().equals(FROM_INST_TID.path()) ||
                    obj.tid().path().equals(AT_INST_TID.path()); // potentially much faster than basePath().equals()
        }

        public static fURI autoPointerVID(final Obj autoPointerInst) {
            return autoPointerInst.asInst().arg(0).uriValue();
        }

        public static Optional<fURI> getAutoPointer(final Obj obj) {
            if (isAutoPointer(obj))
                return Optional.of(autoPointerVID(obj));
            else
                return Optional.empty();
        }

        public static Optional<fURI> getPointer(final Obj obj) {
            if (isAutoPointer(obj))
                return Optional.of(autoPointerVID(obj));
            else if (isPointer(obj))
                return Optional.of(obj.asInst().arg(0).uriValue());
            else
                return Optional.empty();
        }

        public static int objHashCode(final Obj obj) {
            return Objects.hash((Object) obj.jvm()); /*obj.isNoObj() ? noobj().hashCode() : obj.isInst() ? obj.tid().hashCode() : Objects.hash(obj.jvm(), obj.tid().one());*/
        }

        public static boolean objEquals(final Obj obj, final Object other) {
            if (!(other instanceof Obj))
                return false;
            if (obj.isNoObj() && ((Obj) other).isNoObj())
                return true;
            if (!Objects.equals(obj.vid(), ((Obj) other).vid()))
                return false;
            //final BiPredicate<Obj, Obj> opt = Optimizations.optimizedEquals.get(obj.tid().basePath());
            //if (null != opt)
            //    return opt.test(obj, (Obj) other);
            if (obj.isObjs() && ((Obj) other).isObjs()) {
                final Set<Obj> objSet = new HashSet<>(obj.jvm());
                final Set<Obj> otherSet = new HashSet<>(((Obj) other).jvm());
                return objSet.equals(otherSet);
            }
            return Objects.equals(obj.tid(), ((Obj) other).tid()) &&
                    Objects.equals(obj.jvm(), ((Obj) other).jvm());
        }

        public static boolean objcLessEquals(final Obj obj, final Object other) {
            return other instanceof Obj &&
                    ((obj.isNoObj() && ((Obj) other).isNoObj()) ||
                            (Objects.equals(obj.tid().one(), ((Obj) other).tid().one()) && // TODO: no vid checked ...
                                    Objects.equals(obj.jvm(), ((Obj) other).jvm())));
        }

        public static String objToString(final Obj obj) {
            return SERIALIZER.write(obj);
        }

        /**
         * Check that {@code obj} satisfies its declared type (when type-checking is enabled).
         * Throws {@link MTronException} on failure. Does NOT trigger a space write.
         */
        public static void objTypeCheck(final Obj obj) {
            if (TypeCheck.obj_write.enabled()) {
                if (Router.loaded() && !obj.isInstSet() && !obj.isNoObj() && !obj.isType() && !obj.test(obj.type())) {
                    if (obj.isPoly()) {
                        final String matchDiffString = Poly.Helper.diffTypeRecursion(obj, obj.type()).toString();
                        final int width = Math.max(Math.max(
                                CommonUtil.width(matchDiffString),
                                CommonUtil.width(obj.toString())), CommonUtil.width(obj.type().toString()));
                        throw MTronException.of("obj does not match specified type:\n%s\n%s\n%s\n%s\n%s",
                                indent(obj.tid(obj.baseType()).toString(), 2),
                                indent("X=>", 6),
                                indent(obj.type().toString(), 2), indent("-".repeat(width), 2), indent(matchDiffString, 2));
                    } else
                        throw MTronException.of("%s is not a %s".formatted(obj, obj.type()));
                }
            }
        }

        public static void objCheckAndSave(final Obj obj) {
            objTypeCheck(obj);
            if (null != obj.vid() && !obj.isType())
                Router.writeToSpace(obj.vid(), obj);
        }

        public static void objCheckAndSave(final Obj obj, final Object jvm, final fURI tid, final fURI vid) {
            objCheckAndSave(obj, jvm, tid, vid, false);
        }

        public static void objCheckAndSave(final Obj obj, final Object jvm, final fURI tid, final fURI vid,
                                           final boolean forceSave) {
            final Object oldJVM = obj.jvm();
            final fURI oldTID = obj.tid();
            final fURI oldVID = obj.vid();
            final boolean save = forceSave || (!Objects.equals(obj.vid(), vid) || !Objects.equals(obj.tid().basePath(), tid.basePath()) || !Objects.equals(obj.jvm(), jvm));
            obj.self(jvm, tid, vid);
            try {
                if (save)
                    Obj.Helper.objCheckAndSave(obj);
            } catch (final MTronException e) {
                obj.self(oldJVM, oldTID, oldVID);
                throw e;
            }
        }

        public static <O extends Obj> O construct(final Class<O> clazz, final Object jvm, final fURI tid,
                                                  final fURI vid) {
            if (null != tid) {
                final fURI bigTID = tid.big();
                if (TypeCheck.type_ctor.enabled() && !BASE_TYPES.contains(bigTID.basePath()) && Router.loaded()) {
                    Obj type = Router.readFromSpace(bigTID);
                    if (!type.isNoObj() && type.isType() && type.asType().hasConstructor()) {
                        final Obj protoObj = MObjFactory.of().toObj(jvm, null, vid, clazz);
                        final O constructedObj = type.asType().constructor().apply(protoObj).as();
                        if (constructedObj.isFail())
                            throw MTronException.of(constructedObj.<Fail>as().jvm().get0());
                        else {
                            constructedObj.self(constructedObj.jvm(), bigTID, vid);
                            if (null != vid)
                                Router.writeToSpace(vid, constructedObj);
                            return constructedObj;
                        }
                    }
                }
            }
            return MObjFactory.of().toObj(jvm, tid, vid, clazz);
        }

        public static <O extends Obj> O objClone(final Obj obj, final Object jvm, final fURI tid, final fURI vid) {
            if (!Objects.equals(tid, obj.tid())) {
                final Obj type = Router.readFromSpace(tid);
                if (!type.isNoObj() && type.isType() && type.<Type>as().hasConstructor()) {
                    final Obj clone = type.<Type>as().constructor().apply(obj);
                    if (clone.isFail())
                        throw MTronException.of(clone.<Fail>as().jvm().get0());
                    return (O) clone.selfTID(tid);
                }
            }
            if (!Objects.equals(jvm, obj.jvm()) || !tid.equals(obj.tid()) || !Objects.equals(vid, obj.vid())) {
                try {
                    final O clone = (O) obj.clone();
                    Obj.Helper.objCheckAndSave(clone, jvm, tid, null == vid || vid.isEmpty() ? null : vid);
                    return (O) clone.selfTID(tid);
                } catch (final Exception e) {
                    throw MTronException.of(e);
                }
            }
            return (O) obj;
        }

        public static void logLockedObj(final Obj obj) {
            Router.global().logger().warn("obj vid/tid locked: %s", obj);
        }
    }

    final class ObjType {
        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(INSIDE_INST_TID.dom(A).rng(A.maybe()), lst(LST_TYPE), (lhs, inst) -> inst.arg(0).lstValue().contains(lhs) ? lhs : noobj()),
                    instC(SERIALIZE_INST_TID.dom(A).rng(B), lst(T(OBJ_SERIAL_TID)), (lhs, inst) -> {
                        final Object serialization = inst.arg(0).<ObjSerializer<?>>as().write(lhs);
                        try {
                            return MObjFactory.of().toObj(serialization);
                        } catch (final Exception e) {
                            inst.logger().warn("unable to serialize %s with %s: %s", lhs, inst.arg(0), e);
                            return str(serialization.toString());
                        }
                    }),
                    instC(FORK_INST_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(ALL_TYPE), (lhs, inst) -> {
                        BootLoader.getExecutor().submit(() -> {
                            final Obj forking = inst.arg(0);
                            forking.apply(lhs);
                        });
                        return lhs;
                    }),
                    instC(RANGE_INST_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(INT_TYPE, isa_(INT_TYPE).else_(jnt(0)).tryToInst()), (lhs, inst) -> lhs.take(cInt.of(inst.arg(0).intValue())).get1().take(cInt.of(inst.arg(1).intValue())).get0()),
                    docWrap(instC(ORDER_INST_TID.dom(A.maybeSome()).rng(A.maybeSome()).q(BLOCK, null), lst(ALL_TYPE), (lhs, inst) -> objs(lhs.stream().sorted(new ObjSelectComparator(inst.arg(0))))),
                            "any objs", "the objs sorted by the arg obj", Map.of(jnt(0), "the obj to sort by"), "a sorting function \\(f(X)\\to X'\\)"),
                    instC(AS_INST_TID.dom(A).rng(NOOBJ_TID.zero()), lst(), (lhs, inst) -> noobj()),
                    docWrap(instC(AS_INST_TID.dom(A).rng(B), lst(ALL_TYPE), (lhs, inst) -> inst.arg(0).isType() ? lhs.as(inst.arg(0).asType()) : fail(MTronException.of("%s is not a %s", lhs, inst.arg(0)))),
                            "any obj", "the lhs obj as the arg type", Map.of(jnt(0), "the type to cast to"), "a type casting function \\(f(x)\\to x\\)"),
                    instC(IMPORT_INST_TID.dom(ALL.maybe()).rng(SPACE_TID.maybeSome()), lst(URI_TYPE, T(URI_TID.maybe())),
                            (lhs, inst) -> MTronException.wrap(() ->
                                    objs((Stream) InstSet.importInstSetStream(inst.arg(0).uriValue(), inst.arg(1).isNoObj() ? null : inst.arg(1).uriValue())))),
                    docWrap(instC(DEDUP_INST_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(), (lhs, inst) -> objs(lhs.stream().map(o -> o.c().gt(cInt.ZERO()) ? o.c(cInt::one) : o.c(c -> cInt.of(-1))).distinct())),
                            "any objs", "the deduplicated objs", Map.of(), "a deduplication function \\(f({c}X) \\to {1<=c}X\\)"),
                    instC(BARRIER_INST_TID.dom(ALL_STAR).rng(LST_TID), lst(LST_TYPE), (lhs, inst) -> lhs.stream().reduce(inst.arg(0), (a, b) -> a.asLst().add(b))),
                    docWrap(instC(BARRIER_INST_TID.dom(REL_TID.maybeSome()).rng(REC_TID), lst(REC_TYPE), (lhs, inst) -> lhs.stream().reduce(inst.arg(0), (a, b) -> a.asRec().at(b.asRel().first(), b.asRel().second()))),
                            "any objs", "the objs as a rec", Map.of(jnt(0), "the rec to merge into"), "a rec merging function \\(f(X)\\to X\\)"),
                    docWrap(instC(BARRIER_INST_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(), (lhs, inst) -> lhs),
                            "any objs", "the objs as is", Map.of(), "a passthrough function \\(f(X) \\to \\parallel X \\)"),
                    docWrap(instC(BARRIER_INST_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(T(A.maybeSome())), (lhs, inst) -> inst.arg(0).append(lhs)),
                            "any objs", "the objs appended to the arg objs", Map.of(jnt(0), "the objs to append"), "an append function \\(f(X)\\to X\\)"),
                    docWrap(instC(AS_INST_TID.dom(A).rng(A), lst(T(A)), (lhs, inst) -> lhs.as(inst.arg(0).asType())),
                            "any obj", "the lhs obj as the arg type", Map.of(jnt(0), "the type to construct from the lhs"), "a type construction function \\(f(x)\\to x\\)"),
                    instC(REPEAT_INST_TID.dom(A).rng(A.maybeSome()).addQ(MONAD), rec(uri(CODE), T(A.maybeSome()), uri(UNTIL), BOOL_TYPE), (lhs, inst) -> {
                        try {
                            final PCMonad monad = lhs.isMonad() ? lhs.asMonad() : pcmonad(lhs);
                            if (monad.isNoObj() || monad.obj().isNoObj()) return monad.nextInst();
                            final Obj breakPredicate = inst.arg(1);
                            if (breakPredicate.apply(monad.obj()).booleanCheck()) {
                                return monad.updateLoop(0).nextInst();
                            }
                            final Obj repeatedApply = inst.arg(0);
                            return monad.updateLoop(1).obj(repeatedApply.apply(monad.obj()));
                        } catch (final Exception e) {
                            e.printStackTrace();
                            throw e;
                        }
                    }),
                   /* instC(REPEAT_INST_TID.dom(A).rng(A.maybeSome()).addQ(MONAD), lst(ALL_TYPE, ALL_TYPE), (lhs, inst) -> {
                        Obj current = ((PCMonad) lhs).obj();
                        final Obj repeatedApply = inst.arg(0);
                        final int times = inst.arg(1).apply(current).intValue().intValue();
                        final boolean moreThanOne = repeatedApply.dom().c().most().gt(cInt.ONE());
                        for (int i = 1; i <= times; i++) {
                            current = moreThanOne ?
                                    inst.arg(0).apply(current) :
                                    objs(current.stream().map(repeatedApply::apply));
                        }
                        return current;
                    }),*/
                    instC(AUTO_INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(T(ALL.maybe())), (lhs, inst) -> inst.arg(0).apply(lhs)),
                    /*docWrap(*/instC(AUTO_FROM_INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(T(ALL.maybe()), T(ALL.maybe())), (lhs, inst) -> (!inst.arg(1).isNoObj() ? inst.arg(1) : Router.readFromSpace(inst.arg(0).uriValue()).autoResolve(lhs)).vid(null)),
                    //"any obj", "the obj referred to by the uri arg", Map.of(jnt(0), "the uri to dereference"),"like from(uri), except that dereferencing happens immediately upon accessing the instruction (no inst apply required)."),
                    instC(AUTO_AT_INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(T(ALL.maybe()), T(ALL.maybe())), (lhs, inst) -> !inst.arg(1).isNoObj() ? inst.arg(1) : Router.readFromSpace(inst.arg(0).uriValue()).orSupply(() -> inst.arg(1).vid(inst.arg(0).uriValue())).autoResolve(lhs).selfVID(inst.arg(0).uriValue())),
                    docWrap(instC(AUTO_TO_INST_TID.dom(ALL.maybe()).rng(ALL), lst(ALL_TYPE), (lhs, inst) -> (null == lhs.vid() || lhs.isAutoFrom()) ? lhs : auto_from_(lhs.vid()).tryToInst()),
                            "any obj", "the uri (if possible) that refers to the obj arg", Map.of(jnt(0), "the obj to reference"), "like !*(vid), except that the obj arg is converted to an !* reference (an inverse dereference) immediately upon inst access (no inst apply required)."),
                    docWrap(instC(CATCH_INST_TID.dom(A).rng(C.maybeSome()), lst(T(B.maybeSome())), (lhs, inst) -> lhs.isFail() && !lhs.isCaughtFail() ? inst.arg(0).apply(lhs.asFail().caught()).c(c -> c.mult(lhs.c())) : lhs),
                            "any obj", "uncaught fails go to arg, others mapped by identity", Map.of(jnt(0), "the obj triggered on an uncaught fail"), "a catch function f(x)->x"),
                    docWrap(instC(END_INST_TID.dom(ALL_STAR).rng(NOOBJ_TID.zero()), lst(), (lhs, inst) -> noobj()),
                            "terminal objs", "noobj", Map.of(), "the terminal function \\(f(x)\\to \\emptyset\\)"),
                    docWrap(instC(PRINTLN_INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(T(ALL_STAR)), (lhs, inst) -> objs(inst.args().elements().peek(o -> inst.logger().none("%s", o.isStr() ? o.strValue() : o.toString())).filter(x -> false).findAny().orElse(lhs).stream().peek(x -> inst.logger().none(lineSeparator())))),
                            "the rhs obj", "the lhs obj", Map.of(jnt(0), "concatenated args followed by newline written to stdout"), "a side-effect function \\(f(x)\\nearrow x\\)"),
                    docWrap(instC(PRINT_INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(T(ALL_STAR)), (lhs, inst) -> objs(inst.args().elements().peek(o -> inst.logger().none("%s", o.isStr() ? o.strValue() : o.toString())).filter(x -> false).findAny().orElse(lhs))),
                            "the rhs obj", "the lhs obj", Map.of(jnt(0), "concatenated args followed by newline written to stdout"), "a side-effect function \\(f(x)\\nearrow x\\)"),
                    instC(AT_INST_TID.dom(ALL.maybe()).rng(A.maybeSome()), lst(T(URI_TID)), (lhs, inst) -> {
                        final fURI pattern = inst.arg(0).uriValue();
                        if (pattern.hasPattern()) {
                            return objs(Router.readFromSpace(pattern.asBranch()).stream().map(x -> x.asRel().second().selfVID(x.asRel().first().uriValue())));
                        } else
                            return Router.readFromSpace(pattern).selfVID(pattern);
                    }),
                    docWrap(instC(ID_INST_TID.dom(A).rng(A), lst(), (lhs, inst) -> lhs),
                            "an rhs obj", "an lhs obj", Map.of(), "the obj identity function \\(f(x)\\to x\\)"),
                    docWrap(instC(ID_INST_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(), (lhs, inst) -> lhs),
                            "the rhs obj", "the lhs obj", Map.of(), "a objs barrier identity function \\(f(X)\\to X\\)"),
                    docWrap(instC(AND_INST_TID.dom(A).rng(BOOL_TID), lst(BOOL_TYPE, BOOL_TYPE, BOOL_TYPE.maybe(), BOOL_TYPE.maybe(), BOOL_TYPE.maybe(), BOOL_TYPE.maybe(), BOOL_TYPE.maybe(), BOOL_TYPE.maybe()), (lhs, inst) -> bool(inst.args().elements().map(o -> o.orElse(BOOL_TRUE)).allMatch(Obj::boolValue))),
                            "any objs", "true if all objs are true", Map.of(), "logical \\(\\texttt{and}\\) function \\(f(X)\\to \\tt{true}\\) if all \\(X\\) are true"),
                    docWrap(instC(OR_INST_TID.dom(A).rng(BOOL_TID), lst(BOOL_TYPE, BOOL_TYPE, BOOL_TYPE.maybe(), BOOL_TYPE.maybe(), BOOL_TYPE.maybe(), BOOL_TYPE.maybe(), BOOL_TYPE.maybe(), BOOL_TYPE.maybe()), (lhs, inst) -> bool(inst.args().elements().map(o -> o.orElse(BOOL_FALSE)).anyMatch(Obj::boolValue))),
                            "any objs", "true if any objs are true", Map.of(), "logical \\(\\texttt{or}\\) function \\(f(X)\\to \\tt{true}\\) if any \\(X\\) are true"),
                    instC(APPLY_INST_TID.dom(ALL).rng(ALL_STAR), lst(T(ALL_STAR)), (lhs, inst) -> lhs.isInst() ?
                            lhs.asInst().apply(inst.args()) :
                            Router.global().read(lhs.uriValue().basePath().extend("apply")).apply(inst.args())),
                    instC(MAP_INST_TID.dom(A).rng(B), lst(T(B)), (lhs, inst) -> inst.arg(0)),
                    docWrap(instC(MAP_INST_TID.dom(A.maybe()).rng(B.maybe()), lst(T(B.maybe())), (lhs, inst) -> inst.arg(0)), "maybe some obj", "the lhs obj applied to the arg obj", Map.of(jnt(0), "any obj"), "applies the lhs obj to the arg obj to yield the rhs obj"),
                    instC(FILTER_INST_TID.dom(A).rng(A.maybe()), lst(T(ALL.maybe())), (lhs, inst) -> inst.arg(0).isNoObj() ? noobj() : lhs),
                    instC(SIDE_INST_TID.dom(A).rng(A), lst(ALL_TYPE), (lhs, inst) -> Optional.of(inst.arg(0).apply(lhs)).map(x -> (Obj) null).orElse(lhs)),
                    docWrap(instC(TID_INST_TID.dom(ALL).rng(URI_TID), lst(), (lhs, inst) -> lhs.tid().toUri()),
                            "any obj", "the lhs obj type id", Map.of(), "the spatial location of the lhs obj [equivalent to f(x) ~ vid(type())]"),
                    docWrap(instC(VID_INST_TID.dom(A).rng(URI_TID.maybe()), lst(), (lhs, inst) -> null == lhs.vid() ? noobj() : uri(lhs.vid())),
                            "any obj", "a spatial location for the lhs obj", Map.of(jnt(0), "the value id for the lhs obj"), "specifies the spatial location of the lhs obj", "1@abc.vid() [-- abc --]"),
                    docWrap(instC(VID_INST_TID.dom(A).rng(A), lst(T(URI_TID)), (lhs, inst) -> lhs.vid(inst.arg(0).uriValue())),
                            "any obj", "a spatial location for the lhs obj", Map.of(jnt(0), "the value id for the lhs obj"), "specifies the spatial location of the lhs obj", "1@abc.vid() [-- abc --]"),
                    docWrap(instC(ELSE_INST_TID.dom(ALL.maybe()).rng(ALL), lst(T(ALL.maybe())), (lhs, inst) -> lhs.isNoObj() ? inst.arg(0) : lhs),
                            "maybe an obj", "the lhs obj else the arg obj", Map.of(jnt(0), "the rhs obj is the lhs is noobj"), "\\[ f(\\tt{lhs}) = \\left\\{ \\begin{aligned} \\tt{lhs} & \\quad \\text{if } \\tt{lhs} \\neq \\emptyset \\\\ \\tt{arg}_0 & \\quad \\text{otherwise.} \\end{aligned} \\right. \\]"),// TODO: rec args needs resolution on generics connected
                    docWrap(instC(IS_INST_TID.dom(A.maybe()).rng(A.maybe()), lst(isa_(T(BOOL_TID)).else_(BOOL_FALSE).tryToInst()), (lhs, inst) -> inst.arg(0).orElse(BOOL_FALSE).boolValue() ? lhs : noobj()),
                            "any obj", "the lhs obj if arg is true", Map.of(jnt(0), "filter lhs if false"), "filters the lhs obj"), // TODO: generics are not working for some reason
                    docWrap(instC(ISA_INST_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(ALL_TYPE), (lhs, inst) -> lhs.test(inst.arg(0)) ? lhs : noobj()),
                            "an obj to match", "the unaltered obj if arg matches", Map.of(jnt(0), "filter lhs if doesn't match arg"), "a filter function \\(f(x)\\to \\{\\emptyset \\cup x\\}\\)"),
                    instC(MATCHES_INST_TID.dom(ALL.maybe()).rng(BOOL_TID), lst(T(ALL.maybe())), (lhs, inst) -> bool(lhs.test(inst.arg(0)))),
                    docWrap(instC(BLOCK_INST_TID.dom(A.maybe()).rng(B.some()), lst(T(B.some())), (lhs, inst) -> inst.arg(0)),
                            "maybe an obj", "the arg without an applied lhs", Map.of(jnt(0), "the unapplied rhs"), "the lhs obj is halted and the arg is the rhs obj"),
                    instC(SPLIT_INST_TID.dom(ALL).rng(ALL.maybeSome()), lst(T(ALL.some())), (lhs, inst) -> objs(inst.arg(0).stream().map(o -> o.apply(lhs)))),
                    docWrap(instC(CHOOSE_INST_TID.dom(ALL).rng(REL_TID.maybe()), lst(T(REC_TID)), (lhs, inst) -> inst.arg(0).<Rec>as().elements().map(Obj::<Rel>as).map(e -> e.<Rel>jvm(Tuple.Pair.with(e.first().apply(lhs), e.second()))).filter(e -> !e.first().isNoObj()).findFirst().map(e -> e.<Obj>jvm(Tuple.Pair.with(e.first(), e.second().apply(lhs)))).orElse(noobj())),
                            "any obj", "the split as an objs", Map.of(jnt(0), "the branches"), "a branching function f(x):g(a)->a',g(b)->b',..."),
                    instC(MERGE_INST_TID.dom(A.maybeSome()).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> inst.arg(0).jvm(Stream.concat(lhs.stream(), inst.arg(0).elements()).toList())),
                    instC(MERGE_INST_TID.dom(A.maybeSome()).rng(ALL_STAR), lst(T(ALL_STAR)), (lhs, inst) -> objs(Stream.concat(inst.args().elements(), lhs.elements()))),
                    instC(MERGE_INST_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(T(A.maybeSome())), (lhs, inst) -> objs(Stream.concat(lhs.stream(), inst.arg(0).stream()))),
                    instC(NOT_INST_TID.dom(ALL).rng(BOOL_TID), lst(BOOL_TYPE), (lhs, inst) -> bool(!inst.arg(0).boolValue())),
                    docWrap(instC(EQ_INST_TID.dom(A).rng(BOOL_TID), lst(T(A)), (lhs, inst) -> Inst.Helper.alignLHSType(lhs, inst.arg(0)).map(l -> Objects.equals(l, inst.arg(0))).map(MBool::bool).orElse(BOOL_FALSE)),
                            "any objs", "true if lhs equals rhs", Map.of(jnt(0), "the rhs obj"), "an equality function \\[ f(\\tt{lhs}) = \\left\\{ \\begin{aligned} \\tt{true} & \\quad \\text{if } \\tt{lhs} == \\tt{arg}_0 \\\\ \\tt{false} & \\quad \\text{otherwise.} \\end{aligned} \\right. \\]"),
                    docWrap(instC(NEQ_INST_TID.dom(A).rng(BOOL_TID), lst(T(A)), (lhs, inst) -> Inst.Helper.alignLHSType(lhs, inst.arg(0)).map(l -> !Objects.equals(l, inst.arg(0))).map(MBool::bool).orElse(BOOL_TRUE)),
                            "any objs", "true if lhs does not equal rhs", Map.of(jnt(0), "the rhs obj"), "an inequality function \\[ f(\\tt{lhs}) = \\left\\{ \\begin{aligned} \\tt{true} & \\quad \\text{if } \\tt{lhs} \\neq \tt{arg}_0 \\\\ \\tt{false} & \\quad \\text{otherwise.} \\end{aligned} \\right. \\]"),
                    docWrap(instC(TO_INST_TID.dom(A.maybe()).rng(A.maybe()), lst(T(URI_TID)), (lhs, inst) -> Router.writeToSpace(inst.arg(0).uriValue(), lhs)),
                            "any obj", "writes the lhs obj to the arg uri", Map.of(jnt(0), "the uri to write to"), "associates the lhs obj to the arg uri"),
                    // instC(FROM_INST_TID.dom(ALL.maybe()).rng(ALL_STAR), lst(), (lhs, inst) -> Router.stack().peekAll()),
                    docWrap(instC(FROM_INST_TID.dom(ALL.maybe()).rng(B.maybeSome()), lst(T(URI_TID)), (lhs, inst) -> Router.readFromSpace(inst.arg(0).isInt() ? f("" + inst.arg(0).intValue()) : inst.arg(0).uriValue()).clone().selfVID(null)), // TODO: only resolves when explicit mono args (not code args)
                            "any obj", "the obj referred to by the arg uri", Map.of(jnt(0), "the uri to dereference"), "dereferences a uri to an obj (sugar'd *)",
                            "*abc        [-- obj at abc                            --]",
                            "abc.*_      [-- obj at abc via dynamic arg generation --]",
                            "c.*ab${_}   [-- obj at abc via uri template parameter --]",
                            "from(abc)   [-- obj at abc non-sugar form             --]"),
                    docWrap(instC(REF_INST_TID.dom(ALL).rng(ALL_STAR), lst(T(ALL_STAR)), (lhs, inst) -> Router.writeToSpace(lhs.uriValue(), inst.arg(0))),
                            "a uri reference", "writes the arg obj to the lhs uri", Map.of(jnt(0), "an obj to be the referent of the uri"), "associates the arg obj to the lhs uri (inverse of /m/inst/to)"),
                    /*docWrap(instC(REF_APPLY_INST_TID.dom(URI_TID).rng(A.maybeSome()), lst(T(A.maybeSome())), (lhs, inst) -> {
                                final Obj read = Router.readFromSpace(lhs.uriValue());
                                return Router.writeToSpace(lhs.uriValue(), inst.arg(0).apply(read));
                            }),
                            "a uri reference", "writes to lhs uri the result of applying the current lhs uri referent to the inst arg", Map.of(jnt(0), "the obj to apply to the lhs uri referent"), "associates the arg obj to the lhs uri (inverse of /m/inst/to)"),
                    */
                    docWrap(instC(SOURCE_INST_TID.dom(A.maybe()).rng(B.maybeSome()), lst(STR_TYPE), (lhs, inst) -> {
                                final Str source = inst.arg(0).asStr();
                                final Content.ContentType contentType = Content.ContentType.fromType(source, Content.ContentType.APPLICATION_MTRON);
                                return contentType.exec(source);
                            }),
                            "maybe an obj", "result of evaluating source code", Map.of(jnt(0), "the source code to evaluate"), "evaluates source code"),
                    docWrap(instC(TYPE_INST_TID.dom(ALL).rng(ALL), lst(), (lhs, inst) -> lhs.isType() ? lhs.asType().parentType() : lhs.type()),
                            "any obj", "the lhs obj type", Map.of(), "the type of the lhs obj",
                            "6.type()-<[tid(),vid()]      [-- [/m/int, /m/int] base types are those where vid==tid --]",
                            "nat::6.type()-<[tid(),vid()] [-- [/m/int, nat] non-base types tids are the types they are refining (super type) --]"),
                    docWrap(instC(CC_INST_TID.dom(A.maybeSome()).rng(INT_TID), lst(), (lhs, inst) -> jnt(lhs.c().max())),
                            "any obj", "the lhs obj coefficient", Map.of(), "maps an obj to it's coefficient with a function f(lhs^c)->c"),
                    docWrap(instC(CC_INST_TID.dom(A).rng(A.maybeSome()), lst(T(INT_TID)), (lhs, inst) -> lhs.c(inst.arg(0).intValue())),
                            "any obj", "the lhs obj with new coefficient", Map.of(jnt(0), "a coefficient for lhs obj"), "sets the coefficient of the lhs obj via f(lhs,c)->lhs^c"),
                    instC(THROW_INST_TID.dom(ALL.maybeSome()).rng(FAIL_TID), lst(T(ALL.maybe())), (lhs, inst) -> fail(MTronException.of("%s", inst.arg(0).toString()))),
                    instC(PARENT_INST_TID.dom(ALL).rng(ALL.maybe()), lst(), (lhs, inst) -> lhs.parent()),
                    docWrap(instC(COUNT_INST_TID.dom(A.maybeSome()).rng(INT_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> jnt(a.intValue() + b.c().max())).intValue()/* * inst.c().max()*/), jnt(0)),
                            "any objs", "the count of objs", Map.of(), "counts the number of objs"),
                    docWrap(instC(SKIP_INST_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(T(INT_TID)), (lhs, inst) -> lhs.take(cInt.of(inst.arg(0).intValue())).get1()), // retrieve
                            "any objs", "the objs after skipping", Map.of(jnt(0), "the number of objs to skip"), "skips the first n objs"),
                    docWrap(instC(TAKE_INST_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(T(INT_TID)), (lhs, inst) -> lhs.take(cInt.of(inst.arg(0).intValue())).get0()), // remaining
                            "any objs", "the objs before skipping", Map.of(jnt(0), "the number of objs to take"), "takes the first n objs"),
                    instC(UPDATE_INST_TID.dom(A).rng(B), lst(T(B)), (lhs, inst) -> Poly.Helper.updateRecursion(lhs.as(), inst.arg(0).as(), MUTABLE)),
                    instC(REIFY_INST_TID.dom(A).rng(REC_TID), lst(), (lhs, inst) -> rec(
                            "type", rec(
                                    "tid", rec(
                                            "scheme", nullOrElse(lhs.tid().scheme(), NoObj::noobj, MUri::uri),
                                            "authority", nullOrElse(lhs.tid().hasAuthority() ? lhs.tid() : null, NoObj::noobj, z -> rec(
                                                    "host", nullOrElse(z.host(), NoObj::noobj, MUri::uri),
                                                    "port", nullOrElse(z.port() == -1 ? null : (long) lhs.tid().port(), NoObj::noobj, MInt::jnt)
                                            )),
                                            "path", uri(lhs.tid().pathString()),
                                            "poly", ((Optional) lhs.tid().polyParsed()).orElse(noobj()),
                                            "c", rec(
                                                    "min", jnt(lhs.tid().c().min()),
                                                    "max", jnt(lhs.tid().c().max())),
                                            "q", nullOrElse(lhs.tid().qMap() == null ? null : lhs.tid().qMap(), NoObj::noobj,
                                                    q -> rec(q.entrySet().stream().map(kv -> rel(uri(kv.getKey()), uri(kv.getValue())))))),
                                    "obj", rec(
                                            "value", lhs.type(),
                                            "params", nullOrElse(lhs.type().predicate() == null && lhs.type().constructor() == null ? null : lhs, NoObj::noobj, t -> rec(
                                                    "predicate", nullOrElse(t.type().predicate(), NoObj::noobj, r -> r),
                                                    "constructor", nullOrElse(t.type().constructor(), NoObj::noobj, r -> r))))),
                            "value", rec(
                                    "vid", nullOrElse(lhs.vid(), NoObj::noobj, fURI::toUri),
                                    "obj", rec(
                                            "value", MObjFactory.of().createOrFail(lhs.jvm()),
                                            "jvm", rec(
                                                    "class", uri(lhs.jvm().getClass().getCanonicalName()),
                                                    "projection", lhs.jvm() instanceof Tuple ?
                                                            rec(IteratorUtil.indexedStream(lhs.<Tuple>jvmAs().iterator()).map(p -> rel(jnt(p.get0()), MObjFactory.of().createOrFail(p.get1())))) :
                                                            rec(jnt(0), MObjFactory.of().toObj(lhs.jvm()))))))),
                    docWrap(instC(REDUCE_INST_TID.dom(ALL.maybeSome()).rng(ALL), lst(ALL_TYPE), (lhs, inst) -> Stream.concat(inst.arg(0).<Inst>as().arg(0).stream(), lhs.stream()).reduce((a, b) -> inst.arg(0).<Inst>as().args(lst(a)).apply(b)).orElse(noobj())),
                            "any objs", "the result of applying the arg inst to each obj", Map.of(jnt(0), "the inst to apply to each obj"), "a reduce function \\(f(X) \\to x\\)"),
                    docWrap(instC(WHERE_INST_TID.dom(A).rng(A.maybe()), lst(T(B)), (lhs, inst) -> inst.arg(0).isObjCall() ? (inst.arg(0).apply(lhs).isNoObj() ? noobj() : lhs) : (lhs.test(inst.arg(0)) ? lhs : noobj())),
                            "any obj", "filter the lhs obj based on whether the arg yields noobj or not", Map.of(jnt(0), "the inst to filter objs by"), "a filter function \\(f(x)\\to \\{\\emptyset \\cup x\\}\\)"),
                    instC(GROUP_INST_TID.dom(ALL.maybeSome()).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> {
                        final Map<Obj, Obj> result = new LinkedHashMap<>();
                        lhs.stream().forEach(e -> inst.arg(0).asRec().elements().forEach(kv -> {
                            final Obj kk = kv.first().isObjCall() ? kv.first().apply(e) : (e.isRec() ? e.asRec().at(kv.first()) : e);
                            if (!kk.isNoObj()) // TODO: if the group value is not a barrier, then process immediately.
                                result.compute(kk, (k, v) -> (v == null) ? lst(kv.second(), e) : v.asLst().at(jnt(1), v.asLst().at(jnt(1)).append(e), MUTABLE));
                        }));
                        return result.entrySet().stream()
                                .map(kv -> rel(
                                        kv.getKey(),  // key
                                        kv.getValue().asLst().at(0).apply(kv.getValue().asLst().at(jnt(1)))))  // compute barriered value
                                .collect(new CommonUtil.RecCollector());
                    }),
                    docWrap(instC(EVAL_INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(ALL_TYPE), (lhs, inst) -> inst.arg(0)),
                            "can be any obj", "the result of applying the lhs to the arg", Map.of(jnt(0), "the mtron obj to evaluate"), "evaluates an mtron obj"),
                    instC(SWAP_TID.dom(A).rng(A), lst(T(B)), (lhs, inst) -> lhs.apply(inst.arg(0))),
                    instC(RSHIFT_INST_TID.dom(A).rng(B.maybeSome()), lst(T(C.maybeSome())), (lhs, inst) -> {
                        if (lhs.isRec())
                            return Rec.Helper.rshiftRec(lhs.asRec(), inst);
                        else if (lhs.isLst())
                            return Lst.Helper.rshiftLst(lhs.asLst(), inst);
                        else if (lhs.isUri())
                            return Uri.Helper.rshiftUri(lhs.asUri(), inst);
                        else if (lhs.isRel())
                            return Rel.Helper.rshiftRel(lhs.asRel(), inst);
                        else if (lhs.isObjs())
                            return objs(lhs.asObjs().stream().flatMap(o -> inst.apply(o).stream()));
                        else return noobj();
                    }),
                    instC(LSHIFT_INST_TID.dom(A).rng(B.maybeSome()), lst(), (lhs, inst) -> lhs.parent())));
        }
    }

    public static class ObjComparator implements Comparator<Obj> {

        private final Inst inst;

        public ObjComparator(final Inst inst) {
            this.inst = inst;
        }

        @Override
        public int compare(Obj o1, Obj o2) {
            return inst.args(lst(o1)).apply(o2).asInt().intValue().intValue();
        }
    }

    public static class ObjSelectComparator implements Comparator<Obj> {

        private final Obj selector;

        public ObjSelectComparator(final Obj selector) {
            this.selector = selector;
        }

        @Override
        public int compare(final Obj o1, final Obj o2) {
            final Object v1 = this.selector.apply(o1).jvm();
            final Object v2 = this.selector.apply(o2).jvm();
            if (!(v1 instanceof Comparable))
                throw MTronException.of("selector %s does not return a comparable value for %s", this.selector, v1);
            if (!(v2 instanceof Comparable))
                throw MTronException.of("selector %s does not return a comparable value for %s", this.selector, v2);
            return ((Comparable) v1).compareTo(v2);
        }
    }
}