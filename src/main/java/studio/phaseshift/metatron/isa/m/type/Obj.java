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
import studio.phaseshift.metatron.algebra.MultMonoid;
import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.algebra.Ring;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.impl.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjCleanStringSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.*;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.furi.q.DocQ.Doc.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Bytes.BYTES_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Code.CODE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Fail.FAIL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Inst.InstType.INST_TYPE;
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
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.nullOrElse;
import static studio.phaseshift.metatron.util.Tuple.Pair;

public interface Obj extends Function<Obj, Obj>, Streamable<Obj>, Iterable<Obj>, Feature.HasLogger, Cloneable /*Predicate<Obj>*/ {

    default <O extends Obj> O maybe() {
        return (O) this.c(cInt::maybe);
    }

    default <O extends Obj> O maybeSome() {
        return (O) this.c(cInt::maybeSome);
    }

    default <O extends Obj> O some() {
        return (O) this.c(cInt::some);
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

    // default boolean test(final Obj other) {
    //      return this.matches(other);

    // }

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

    default Pair<Obj, Obj> take(final cInt c) {
        return this.c().gte(c) ? Pair.with(this.c(c), this.c(this.c().minus(c))) : Pair.with(this, noobj());
    }

    default Obj take() {
        final Obj clone = this.clone();
        this.self(this.jvm(), this.tid().c("0"), this.vid());
        return clone;
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

    default boolean fastMatch(final Obj rhs) {
        return rhs.tid().isGeneric() || this.tid().matches(rhs.tid());
    }

    default boolean matches(final Obj rhs) {
        if (Obj.Helper.isAuto(rhs))
            return true;
        if (this.isNoObj() && rhs.isNoObj())
            return true;
        else if (this.isNoObj() && (rhs.tid().equals(NOOBJ_TID) || rhs.tid().cV().isZeroable()))
            return true;
        else if (this.tid().cV().isZeroable() && rhs.isNoObj())
            return true;
        else if (rhs.isNoObj())
            return false;
        /// //////////////////////////////
        // if (rhs.isUri() && this.isUri())
        //  return this.uriValue().matches(rhs.uriValue());
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
                        (this.isFail() || this.isCaughtFail() && base.equals(FAIL_TID)))) {
            return false;
        }
        if (this.isCall())
            return this.tid().cV().within(rhs.tid().cV()); // TODO: this is really flimsy.
        if (rhs.isCall())
            return this.matches(rhs.dom()) && rhs.apply(this).matches(rhs.rng());
        if (!this.c().within(rhs.c()))
            return false;
        if (rhs.isType()) {
            if (rhs.matches(T(CODE_TID)) || rhs.matches(T(INST_TID)))
                return true;
            return rhs.tid().isGeneric() ||
                    (Helper.typeInferenceMatch(this, rhs.as()) &&
                            (!rhs.asType().hasPredicate() || !rhs.apply(this).isNoObj()));
        }
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
        else if (this.isRel()) return REL_TID.c(this.c().toString());
        else if (this.isInst()) return INST_TID.c(this.c().toString()).dom(this.dom().tid()).rng(this.rng().tid());
        else if (this.isCode()) return CODE_TID.c(this.c().toString());
        else if (this.isNoObj()) return NOOBJ_TID.c(this.c().toString());
        else if (this.isFail()) return FAIL_TID.c(this.c().toString());
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

    default <O extends Obj> O orSupply(final Supplier<O> other) {
        return this.isNoObj() ? other.get() : (O) this;
    }

    default <O extends Obj> O orThrow(final RuntimeException e) {
        if (this.isNoObj())
            throw e;
        return (O) this;
    }

    default <O extends Obj> O choose(final Predicate<Obj> predicate, final Function<Obj, O> trueBranch, final Function<Obj, O> falseBranch) {
        return predicate.test(this) ? trueBranch.apply(this) : falseBranch.apply(this);
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
        return this.isInst() && (this.tid().basePath().equals(AUTO_FROM_INST_TID) || this.tid().basePath().equals(AUTO_INST_TID)) ?
                this.apply(obj) :
                this;
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

    default Type asType() {
        return (Type) this;
    }

    default Objs asObjs() {
        return (Objs) this;
    }

    default Fail asFail() {
        return (Fail) this;
    }

    String xxxValue = "%s unable to convert %s to %s";

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
        throw MTronException.of(xxxValue, this, T(tid()), INT_TYPE);
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

    default String toCleanString() {
        if (this.isStr())
            return this.strValue();
        if (this.isUri())
            return this.uriValue().toString();
        else
            return this.toString();
    }

    Obj clone();

    <O extends Obj> O self(final Object jvm, final fURI tid, final fURI vid);

    class Helper {

        private static final ObjSerializer<String> SERIALIZER = new ObjCleanStringSerializer();

        public static boolean isAuto(final Obj obj) {
            return obj.isCall() && ((Call) obj).isAuto();
        }

        public static boolean typeInferenceMatch(final Obj lhs, final Type rhs) {
            if (rhs.isBaseType()) {
                if (lhs.isObjs()) {
                    if (!lhs.tid().matches(rhs.tid()))
                        return false;
                } else if (!lhs.baseType().matches(rhs.tid()))
                    return false;
            }
            if (rhs.tid().hasPattern() && !lhs.tid().matches(rhs.tid()))
                return false;
            return null == rhs.predicate() || !rhs.apply(lhs).isNoObj();

        }

        public static int objHashCode(final Obj obj) {
            return Objects.hash((Object) obj.jvm()); /*obj.isNoObj() ? noobj().hashCode() : obj.isInst() ? obj.tid().hashCode() : Objects.hash(obj.jvm(), obj.tid().cLess());*/
        }

        public static boolean objEquals(final Obj obj, final Object other) {
            if (!(other instanceof Obj))
                return false;
            if (obj.vid() != null && obj.vid().equals(((Obj) other).vid()))
                return true;
            final BiPredicate<Obj, Obj> opt = Optimizations.optimizedEquals.get(obj.tid().basePath());
            if (null != opt)
                return opt.test(obj, (Obj) other);
            return ((obj.isNoObj() && ((Obj) other).isNoObj()) ||
                    (obj.vid() != null && Objects.equals(obj.vid(), ((Obj) other).vid())) ||
                    (Objects.equals(obj.tid(), ((Obj) other).tid()) &&
                            //Objects.equals(obj.vid(), ((Obj) other).vid()) && // TODO: ??
                            Objects.equals(obj.jvm(), ((Obj) other).jvm())));
        }

        public static boolean objcLessEquals(final Obj obj, final Object other) {
            return other instanceof Obj &&
                    ((obj.isNoObj() && ((Obj) other).isNoObj()) ||
                            (Objects.equals(obj.tid().cLess(), ((Obj) other).tid().cLess()) && // TODO: no vid checked ...
                                    Objects.equals(obj.jvm(), ((Obj) other).jvm())));
        }

        public static String objToString(final Obj obj) {
            return SERIALIZER.write(obj);
        }

        public static void objCheckAndSave(final Obj obj) {
            if (Router.loaded() &&!obj.isInstSet() && !obj.isNoObj() && !obj.isType() && !obj.matches(obj.type())) {
                if (obj.isPoly()) {
                    final String objString = obj.toString();
                    final String typeString = obj.type().toString();
                    final String matchDiffString = Poly.Helper.diffObjRecursion(obj, (obj.type().hasPredicate() && !obj.type().predicate().insts().getFirst().arg(0).isNoObj()) ?
                            obj.type().predicate().insts().getFirst().arg(0) :
                            obj.type()).toString();
                    final int width = CommonUtil.width(matchDiffString);
                    throw MTronException.of("obj does not match specified type:\n%s\n\t\n\n%s\n%s\n%s", objString, typeString, "-".repeat(width), matchDiffString);
                } else
                    throw MTronException.of("%s is not a %s".formatted(obj, obj.type()));
            }
            if (null != obj.vid())
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
                if (!BASE_TYPES.contains(bigTID.basePath()) && Router.loaded()) {
                    final Obj type = Router.readFromSpace(bigTID);
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
                    return (O) clone;
                }
            }
            if (!Objects.equals(jvm, obj.jvm()) || !tid.equals(obj.tid()) || !Objects.equals(vid, obj.vid())) {
                try {
                    final O clone = (O) obj.clone();
                    Obj.Helper.objCheckAndSave(clone, jvm, tid, vid);
                    return clone;
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
                    // instC(AS_INST_TID.dom(REL_TID).rng(REL_TID), lst(REL_TYPE), (lhs, inst) -> recurssiveAs(lhs, inst.arg(0).as())),
                    instC(IMPORT_INST_TID.dom(ALL.maybe()).rng(fURI.NOOBJ.zero()), lst(REL_TYPE), (lhs, inst) -> MTronException.wrap(() -> BootLoader.loadInstSetProvider(inst.arg(0).uriValue()).map(ServiceLoader.Provider::get).map(i -> noobj()).reduce(noobj(), (a, b) -> noobj()))),
                    instC(DEDUP_INST_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(), (lhs, inst) -> objs(lhs.stream().map(o -> o.c().gt(cInt.ZERO()) ? o.c(cInt::one) : o.c(c -> cInt.of(-1))).distinct())),
                    //  instC(DEDUP_INST_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(T(B)), (lhs, inst) -> objs(lhs.stream().map(o -> o.c().gt(cInt.ZERO()) ? o.c(cInt::one) : o.c(c -> cInt.of(-1))).map(o -> inst.arg(0).apply(o)).distinct())),
                    instC(BARRIER_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(), (lhs, inst) -> lhs),
                    instC(AS_INST_TID.dom(A).rng(A), lst(T(A)), (lhs, inst) -> lhs.clone(lhs.jvm(), inst.arg(0).tid(), lhs.vid())),
                    instC(REPEAT_INST_TID.dom(A).rng(A), lst(T(ALL), INT_TYPE), (lhs, inst) -> {
                        Obj current = lhs;
                        final int times = inst.arg(1).apply(current).intValue().intValue();
                        for (int i = 1; i <= times; i++) {
                            current = inst.arg(0).apply(current);
                        }
                        return current;
                    }),
                    // instC(EXPLAIN_INST_TID.dom(CODE_TID).rng(STR_TID), lst(), (lhs, inst) -> str(new Profile(inst.arg(0)).toString())),
                    instC(AUTO_INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(T(ALL.maybe())), (lhs, inst) -> inst.arg(0).apply(lhs)),
                    instC(AUTO_FROM_INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(T(ALL.maybe()), T(ALL.maybe())), (lhs, inst) -> !inst.arg(1).isNoObj() ? inst.arg(1) : Router.readFromSpace(inst.arg(0).uriValue()).autoResolve(lhs)),
                    instC(CATCH_INST_TID.dom(ALL).rng(ALL.maybeSome()), lst(T(ALL.maybeSome())), (lhs, inst) -> lhs.isFail() ? inst.arg(0).apply(lhs.<Fail>as().caught()) : lhs),
                    docWrap(instC(END_INST_TID.dom(ALL_STAR).rng(NOOBJ_TID.zero()), lst(), (lhs, inst) -> noobj()),
                            "terminal objs", "noobj", Map.of(), "the terminal function f(x)->0"),
                    docWrap(instC(PRINT_INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(T(ALL_STAR)), (lhs, inst) -> objs(inst.args().elements().peek(o -> inst.logger().none("%s", o.isStr() ? o.strValue() : o)).filter(a -> false).findAny().orElse(lhs).stream().peek(o -> inst.logger().none("\n")))),
                            "the rhs obj", "the lhs obj", Map.of(jnt(0), "concatenated args followed by newline written to stdout"), "a side-effect function f(x)-|>x"),
                    instC(AT_INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(T(URI_TID)), (lhs, inst) -> lhs.isNoObj() ? Router.readFromSpace(inst.arg(0).uriValue()).vid(inst.arg(0).uriValue()) : lhs.vid(inst.arg(0).uriValue())),
                    docWrap(instC(ID_INST_TID.dom(A).rng(A), lst(), (lhs, inst) -> lhs),
                            "an rhs obj", "an lhs obj", Map.of(), "the obj identity function f(x)->x"),
                    docWrap(instC(ID_INST_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(), (lhs, inst) -> lhs),
                            "the rhs obj", "the lhs obj", Map.of(), "a objs barrier identity function f(X)->X"),
                    instC(AND_INST_TID.dom(A).rng(BOOL_TID), lst(T(BOOL_TID).c(cInt::some)), (lhs, inst) -> bool(inst.args().elements().map(Obj::asBool).allMatch(Obj::boolValue))),
                    instC(OR_INST_TID.dom(A).rng(BOOL_TID), lst(T(BOOL_TID).c(cInt::some)), (lhs, inst) -> bool(inst.args().elements().map(Obj::asBool).anyMatch(Obj::boolValue))),
                    instC(APPLY_INST_TID.dom(ALL).rng(ALL_STAR), lst(T(ALL_STAR)), (lhs, inst) -> Router.global().read(lhs.uriValue().basePath().extend("apply")).apply(inst.args())),
                    instC(MAP_INST_TID.dom(A).rng(B), lst(T(B)), (lhs, inst) -> inst.arg(0)),
                    instC(MAP_INST_TID.dom(A.maybe()).rng(B.maybe()), lst(T(B.maybe())), (lhs, inst) -> inst.arg(0)),
                    instC(FILTER_INST_TID.dom(A).rng(A.maybe()), lst(T(ALL.maybe())), (lhs, inst) -> inst.arg(0).isNoObj() ? noobj() : lhs),
                    instC(SIDE_INST_TID.dom(A).rng(A), lst(T(ALL)), (lhs, inst) -> Optional.of(inst.arg(0).apply(lhs)).map(x -> (Obj) null).orElse(lhs)),
                    //  instC(MAP_INST_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> inst.arg(0)),
                    docWrap(instC(TID_INST_TID.dom(ALL).rng(URI_TID), lst(), (lhs, inst) -> lhs.tid().toUri()),
                            "any obj", "the lhs obj type id", Map.of(), "the geometric location of the lhs obj [equivalent to f(x) ~ vid(type())]"),
                    docWrap(instC(VID_INST_TID.dom(A).rng(A), lst(T(URI_TID)), (lhs, inst) -> lhs.vid(inst.arg(0).uriValue())),
                            "any obj", "a spatial location for the lhs obj", Map.of(jnt(0), "the value id for the lhs obj"), "specifies the spatial location of the lhs obj"),
                    docWrap(instC(VID_INST_TID.dom(ALL).rng(URI_TID.maybe()), lst(), (lhs, inst) -> null == lhs.vid() ? noobj() : lhs.vid().toUri()),
                            "any obj", "the lhs obj value id", Map.of(), "the spatial location of the lhs obj"),
                    docWrap(instC(ELSE_INST_TID.dom(ALL.maybe()).rng(ALL), lst(T(ALL.maybe())), (lhs, inst) -> lhs.isNoObj() ? inst.arg(0) : lhs),
                            "maybe an obj", "the lhs obj else the arg obj", Map.of(jnt(0), "the rhs obj is the lhs is noobj"), "f(lhs)->lhs if lhs is an obj, else f(noobj)->arg"),// TODO: rec args needs resolution on generics connected
                    docWrap(instC(IS_INST_TID.dom(A.maybe()).rng(A.maybe()), lst(T(BOOL_TID.maybe())), (lhs, inst) -> inst.arg(0).orElse(BOOL_FALSE).boolValue() ? lhs : noobj()),
                            "any obj", "the lhs obj if arg is true", Map.of(jnt(0), "filter lhs if false"), "filters the lhs obj"), // TODO: generics are not working for some reason
                    docWrap(instC(ISA_INST_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> lhs.matches(inst.arg(0)) ? lhs : noobj()),
                            "an obj to match", "the unaltered obj if arg matches", Map.of(jnt(0), "filter lhs if doesn't match arg"), "a filter function f(x)->{0,x}"),
                    instC(MATCHES_INST_TID.dom(ALL.maybe()).rng(BOOL_TID), lst(T(ALL.maybe())), (lhs, inst) -> bool(lhs.matches(inst.arg(0)))),
                    docWrap(instC(BLOCK_INST_TID.dom(A.maybe()).rng(B), lst(T(B)), (lhs, inst) -> inst.arg(0)),
                            "a blocked obj", "the unapplied arg", Map.of(jnt(0), "the rhs without evaluation"), "the lhs obj is halted and the arg is the rhs obj"),
                    instC(SPLIT_INST_TID.dom(ALL).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> lst(inst.arg(0).elements().map(e -> e.apply(lhs)).toList())),
                    instC(SPLIT_INST_TID.dom(ALL.maybeSome()).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> lst(inst.arg(0).elements().map(e -> e.apply(lhs)).toList())),
                    // instC(SPLIT_TID.dom(REL_TID).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> rec(lhs.<Rel>as().first(),lhs.<Rel>as().second())),
                    //instC(SPLIT_TID.dom(ALL).rng(REL_TID), lst(T(REL_TID)), (lhs, inst) -> rel(inst.arg(0).<Rel>as().first().apply(lhs), inst.arg(0).<Rel>as().second().apply(lhs))),
                    instC(SPLIT_INST_TID.dom(ALL).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> inst.arg(0).asRec().elements().map(e -> rel(e.first().apply(lhs), e.second().apply(lhs))).collect(new CommonUtil.RecCollector())),
                    // todo: allow c to generic and then the above and below instructions can be made into a single generic c inst
                    //instC(SPLIT_TID.dom(ALL.maybeSome()).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> MRec.of(inst.arg(0).recValue().entrySet().stream().map(e -> e.getKey().apply(lhs).choose(Obj::isNoObj, x -> null, x -> MRel.of(x, e.getValue().apply(lhs)))).filter(x -> !Objects.isNull(x)).collect(Collectors.toMap(a -> a.<Rel>as().first(), b -> b.<Rel>as().second(), Obj::append, LinkedHashMap<Obj, Obj>::new)))),
                    instC(SPLIT_INST_TID.dom(A.maybeSome()).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) ->
                            inst.arg(0).jvm(lhs.stream().flatMap(o -> inst.arg(0).<Rec>as()
                                            .elements()
                                            .map(Obj::<Rel>as)
                                            .map(rel -> rel.first()
                                                    .apply(o)
                                                    .andThen(oo -> oo.isNoObj() ?
                                                            rel.second(noobj()) :
                                                            rel.second(rel.second().apply(o))).apply(o))//.choose(Obj::isNoObj, NoObj.single(), r -> rel.second(rel.second().apply(o))))
                                            .map(Obj::<Rel>as)
                                            .filter(p -> !p.first().isNoObj() && !p.second().isNoObj())
                                            .map(Obj::<Rel>as))
                                    .collect(Collectors.toMap(Rel::first, Rel::second, Obj::append, LinkedHashMap::new)))),
                    instC(SPLIT_INST_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(T(A.maybeSome())), (lhs, inst) -> objs(Stream.of(inst.arg(0)).map(o -> o.apply(lhs)))),
                    instC(SPLIT_INST_TID.dom(ALL).rng(ALL.maybeSome()), lst(T(ALL.some())), (lhs, inst) -> objs(inst.arg(0).stream().map(o -> o.apply(lhs)))),
                    // instC(SPLIT_TID.dom(ALL).rng(ALL.maybeSome()), lst(T(ALL)), (lhs, inst) -> objs(inst.arg(0).apply(lhs))),
                    docWrap(instC(CHOOSE_INST_TID.dom(ALL).rng(REL_TID.maybe()), lst(T(REC_TID)), (lhs, inst) -> inst.arg(0).<Rec>as().elements().map(Obj::<Rel>as).map(e -> e.<Rel>jvm(Tuple.Pair.with(e.first().apply(lhs), e.second()))).filter(e -> !e.first().isNoObj()).findFirst().map(e -> e.<Obj>jvm(Tuple.Pair.with(e.first(), e.second().apply(lhs)))).orElse(noobj())),
                            "any obj", "the split as an objs", Map.of(jnt(0), "the branches"), "a branching function f(x):g(a)->a',g(b)->b',..."),
                    instC(MERGE_INST_TID.dom(A.maybeSome()).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> inst.arg(0).jvm(Stream.concat(lhs.stream(), inst.arg(0).elements()).toList())),
                    instC(MERGE_INST_TID.dom(A.maybeSome()).rng(ALL_STAR), lst(), (lhs, inst) -> objs(lhs.elements())),
                    instC(MERGE_INST_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(T(A.maybeSome())), (lhs, inst) -> objs(Stream.concat(lhs.stream(), inst.arg(0).stream()))),
                    instC(NOT_INST_TID.dom(ALL).rng(BOOL_TID), lst(T(BOOL_TID)), (lhs, inst) -> bool(!inst.arg(0).boolValue())),
                    instC(EQ_INST_TID.dom(ALL).rng(BOOL_TID), lst(T(ALL)), (lhs, inst) -> bool(lhs.equals(inst.arg(0)))),
                    instC(NEQ_INST_TID.dom(ALL).rng(BOOL_TID), lst(T(ALL)), (lhs, inst) -> bool(!lhs.equals(inst.arg(0)))),
                    instC(TO_INST_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(URI_TID)), (lhs, inst) -> Router.writeToSpace(inst.arg(0).uriValue(), lhs)),
                    // instC(FROM_INST_TID.dom(ALL.maybe()).rng(ALL_STAR), lst(), (lhs, inst) -> Router.stack().peekAll()),
                    instC(FROM_INST_TID.dom(ALL.maybe()).rng(ALL_STAR), lst(T(URI_TID)), (lhs, inst) -> Router.readFromSpace(inst.arg(0).uriValue())),
                    instC(REF_INST_TID.dom(ALL).rng(ALL_STAR), lst(T(ALL_STAR)), (lhs, inst) -> Router.writeToSpace(lhs.uriValue(), inst.arg(0))),
                    instC(THREAD_INST_TID.dom(A).rng(A), lst(T(ALL)), (lhs, inst) -> {
                        MTronException.wrap(() -> new Thread(() -> inst.arg(0).apply(lhs)).start());
                        return lhs;
                    }),
                    instC(SOURCE_INST_TID.dom(ALL).rng(ALL.maybeSome()), lst(T(STR_TID)), (lhs, inst) -> mParser.parseByLine(inst.arg(0).strValue())),
                    instC(TYPE_INST_TID.dom(A).rng(A), lst(), (lhs, inst) -> lhs.type()),
                    //instC(TYPE_INST_TID.dom(A.some()).rng(A.some()), lst(), (lhs, inst) -> objs(lhs).type()),
                    docWrap(instC(CC_INST_TID.dom(A.maybeSome()).rng(INT_TID), lst(), (lhs, inst) -> jnt(lhs.c().max())),
                            "any obj", "the lhs obj coefficient", Map.of(), "maps an obj to it's coefficient with a function f(lhs^c)->c"),
                    docWrap(instC(CC_INST_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(T(INT_TID)), (lhs, inst) -> lhs.c(inst.arg(0).intValue())),
                            "any obj", "the lhs obj with new coefficient", Map.of(jnt(0), "a coefficient for lhs obj"), "sets the coefficient of the lhs obj via f(lhs,c)->lhs^c"),
                    //  instC(AS_INST_TID.dom(A).rng(B), lst(T(B)), (lhs, inst) -> lhs.matches(inst.arg(0)) ? lhs.tid(inst.arg(0).tid()).c(c -> c.mult(inst.arg(0).c())) : MTronException.of("%s is not a %s", lhs, inst.arg(0)).asFail()),
                    instC(FAILURE_INST_TID.dom(ALL.maybeSome()).rng(FAIL_TID), lst(T(ALL.maybe())), (lhs, inst) -> fail(MTronException.of("%s", inst.arg(0).toString()))),
                    //instC(BARRIER_TID.dom(ALL_STAR).rng(ALL_STAR), lst(T(ALL_STAR)), (lhs, inst) -> inst.arg(0).apply(lhs)),
                    instC(PARENT_INST_TID.dom(ALL).rng(ALL.maybe()), lst(), (lhs, inst) -> {
                        lhs.logger().info("parent: %s => %s", lhs.parent(), lhs);
                        return lhs.parent();
                    }),
                    instC(COUNT_INST_TID.dom(ALL.maybeSome()).rng(INT_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> jnt(a.intValue() + b.c().max())).intValue()/* * inst.c().max()*/), jnt(0)),
                    instC(SKIP_INST_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(T(INT_TID)), (lhs, inst) -> lhs.take(cInt.of(inst.arg(0).intValue())).get1()), // retrieve
                    instC(TAKE_INST_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(T(INT_TID)), (lhs, inst) -> lhs.take(cInt.of(inst.arg(0).intValue())).get0()), // remaining
                    //instC(SUM_TID.dom(A.maybeSome()).rng(A), lst(), (lhs, inst) -> ((Semiring.O)lhs).zero().jvm(IteratorUtil.reduce(lhs.iterator(), ((Semiring.O)lhs).zero(), (a, b) -> ((Semiring.O) a).plus((Semiring.O) b)).jvm()), uri(fURI.NOOBJ)),
                    //instC(SUM_TID.dom(A.maybeSome()).rng(A), lst(), (lhs, inst) -> IteratorUtil.reduce((Iterator)lhs.iterator(), lhs.<Semiring.O>as().zero(), (a, b) -> a.plus(b)), NoObj.single()),
                    //instC(SUM_TID.dom(REAL_TID.maybeSome()).rng(REAL_TID), lst(), (lhs, inst) -> IteratorUtil.reduce(lhs.iterator(), inst.seed(), (a, b) -> real(a.realValue() + (b.realValue() * b.c().max()))), real(0.0)),
                    //instC(SUM_TID.dom(LST_TID.maybeSome()).rng(LST_TID), lst(), (lhs, inst) -> IteratorUtil.reduce(lhs.iterator(), inst.seed(), (a, b) -> lst(Stream.concat(a.lstValue().stream(), b.lstValue().stream()).toList())), lst()),
                    instC(REIFY_INST_TID.dom(ALL.maybe()).rng(REC_TID), lst(), (lhs, inst) -> rec(
                            "type", rec(
                                    "tid", rec(
                                            "scheme", nullOrElse(lhs.tid().scheme(), NoObj::noobj, MUri::uri),
                                            "authority", nullOrElse(lhs.tid().hasAuthority() ? lhs.tid() : null, NoObj::noobj, z -> rec(
                                                    "host", nullOrElse(z.host(), NoObj::noobj, MUri::uri),
                                                    "port", nullOrElse(z.port() == -1 ? null : (long) lhs.tid().port(), NoObj::noobj, MInt::jnt)
                                            )),
                                            "path", uri(lhs.tid().path()),
                                            "c", rec(
                                                    "min", jnt(lhs.tid().cV().min()),
                                                    "max", jnt(lhs.tid().cV().max())),
                                            "q", nullOrElse(lhs.tid().query() == null ? null : lhs.tid().queryMap(), NoObj::noobj,
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
                    // instC(SELECT_TID.dom(REL_TID).rng(REL_TID), lst(T(REL_TID)), (lhs, inst) -> rel(inst.arg(0).<Rel>as().first().apply(lhs.<Rel>as().first()), inst.arg(0).<Rel>as().second().apply(lhs.<Rel>as().second()))),
                    //instC(SELECT_TID.dom(ALL).rng(REC_TID.maybe()), lst(T(REC_TID)), (lhs, inst) -> inst.arg(0).<Rec>as().jvm(inst.arg(0).<Rec>as().<Rel>elementStream().map(r -> Tuple.Pair.with(r.first().apply(lhs), r.second().apply(lhs))).collect(Collectors.toMap(Tuple.Pair::get0, Tuple.Pair::get1, Obj::append, LinkedHashMap::new)))),
                    // instC(SELECT_TID.dom(ALL).rng(LST_TID.maybe()), lst(T(LST_TID)), (lhs, inst) -> inst.arg(0).<Lst>as().jvm(inst.arg(0).<Lst>as().elementStream().map(r -> r.apply(lhs)).toList())),
                    instC(REDUCE_INST_TID.dom(ALL.maybeSome()).rng(ALL), lst(T(ALL)), (lhs, inst) -> Stream.concat(inst.arg(0).<Inst>as().arg(0).stream(), lhs.stream()).reduce((a, b) -> inst.arg(0).<Inst>as().args(lst(a)).apply(b)).orElse(noobj())),
                    instC(WHERE_INST_TID.dom(ALL).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> lhs.matches(inst.arg(0)) ? lhs : noobj()),
                    instC(GROUP_INST_TID.dom(ALL.maybeSome()).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> {
                        final Map<Obj, Obj> result = new LinkedHashMap<>();
                        lhs.stream().forEach(e -> {
                            inst.arg(0).asRec().elements().forEach(kv -> {
                                if (e.isRec()) {
                                    final Obj kk = kv.first().isCall() ? kv.first().apply(e) : e.asRec().at(kv.first());
                                    if (!kk.isNoObj()) {
                                        final Obj vv = kv.second().apply(kk);
                                        if (!vv.isNoObj()) // TODO: stream through keys to get matching key for incur-append on grouping to the same key
                                            result.compute(kk, (k, v) -> (v == null) ? vv : v.append(vv));
                                    }
                                } else {
                                    final Obj kk = kv.first().isCall() ? kv.first().apply(e) : e.matches(kv.first()) ? e : noobj();
                                    if (!kk.isNoObj()) {
                                        final Obj vv = kv.second().apply(e);
                                        if (!vv.isNoObj()) // TODO: stream through keys to get matching key for incur-append on grouping to the same key
                                            result.compute(kk, (k, v) -> (v == null) ? vv : v.append(vv));
                                    }
                                }
                            });
                        });
                        return rec(result);
                    }),
                    instC(SWAP_TID.dom(A).rng(A), lst(T(B)), (lhs, inst) -> lhs.apply(inst.arg(0))),
                    instC(RSHIFT_INST_TID.dom(ALL).rng(ALL.maybe()), lst(isa_(INT_TYPE).else_(jnt(1))), (lhs, inst) -> objs(lhs.stream().filter(o -> o.isUri() || o.isLst() || o.isRec()).map(o -> rshift_(jnt(0)).apply(o)))),
                    instC(LSHIFT_INST_TID.dom(ALL).rng(ALL.maybe()), lst(isa_(INT_TYPE).else_(jnt(1))), (lhs, inst) -> objs(lhs.stream().filter(o -> o.isUri() || o.isLst() || o.isRec()).map(o -> lshift_(jnt(0)).apply(o))))));
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
}