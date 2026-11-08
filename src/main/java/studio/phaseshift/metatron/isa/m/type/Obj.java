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

import org.zeroturnaround.exec.ProcessExecutor;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.algebra.rewrite.Derivation;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Addressable;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.mach.type.processor.monad.StatefulMonad;
import studio.phaseshift.metatron.isa.m.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.isa.m.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.isa.m.type.impl.*;
import studio.phaseshift.metatron.isa.m.web.type.MIME;
import studio.phaseshift.metatron.isa.m.web.type.ObjSerializer;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronUISerializer;
import studio.phaseshift.metatron.util.*;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.lang.System.lineSeparator;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.SUBQ_PATTERN;
import static studio.phaseshift.metatron.furi.q.QCollection.SUBQ_SUB_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_MONAD_TYPE;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.DATETIME_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.Fail.FAIL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Type.TYPE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.type.ObjSerializer.OBJ_SERIAL_TID;
import static studio.phaseshift.metatron.util.CommonUtil.*;
import static studio.phaseshift.metatron.util.Tuple.Pair;

public interface Obj extends PlatonicObj, Function<Obj, Obj>, Streamable<Obj>, Iterable<Obj>, Feature.HasLogger, Feature.HasThread, Cloneable, Predicate<Obj> {

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

    default <O extends Obj> O parent(final Obj parent) {
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

    default fURI typeId() {
        return this.isType() ? this.vid() : this.tid();
    }

    default Type type() {
        return this.isType() ? T(this.vid()) : T(this.tid()); // null == Machine.authority() || this.isInst() ? MType.of(this.tid()) : Machine.authority().read(this.tid()).orElse(MType.of(this.tid()));
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
                return objs.getFirst();
            return objs(objs);
        }
    }

    @Override
    default Obj apply(final Obj other) {
        return this;//.c(c -> c.mult(other.c()));
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
            // identity, not a claim: erasure is explicit here
            if (lhsType.hasVID() && Type.Helper.testByIdentity(lhsType.vid(), rhsType.vid()))
                return true;
            if (rhsType.tid().isGeneric())
                return lhsType.vid().c().within(rhsType.tid().c());
            if (rhsType.vid() != null && rhsType.vid().isGeneric() && lhsType.vid().c().within(rhsType.vid().c()))
                return true;
            lhsType = lhsType.parentType();
        }
        return false;
    }

    default boolean testNominally(final Obj rhs) {
        Type lhsType = Obj.Helper.specificType(this);
        final Type rhsType = Obj.Helper.specificType(rhs);
        if (lhsType.isGeneric() || rhsType.isGeneric())
            return lhsType.tid().basePath().equals(rhsType.tid().basePath()) && lhsType.c().within(rhsType.c());
        // A base type is nominally compatible with any base-or-nominal type of the same base form
        // (rec ⇝ bad_person::T is a re-tag), but NOT with a constructed/predicated subtype
        // (uri ⇝ file::T is a parse). testNominally strips coefficients.
        if (lhsType.isBaseType())
            return (rhsType.isBaseType() || rhsType.isNominal())
                    // identity, not a claim: erasure is explicit here
                    && Type.Helper.testByIdentity(lhsType.baseTypeID(), rhsType.baseTypeID())
                    && Type.Helper.testTypePoly(this, rhsType);
        while (true) {
            // identity, not a claim: erasure is explicit here
            if (Type.Helper.testByIdentity(lhsType.vid(), rhsType.vid()))
                return Type.Helper.testTypePoly(this, rhsType);
            if (lhsType.isBaseType())
                break;
            lhsType = lhsType.parentType();
        }
        return false;
    }

    default boolean test(final Obj rhs) {
        if (this == rhs) return true;
        return Obj.Helper.testObjs(this, rhs);
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

    default fURI baseTypeID() {
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
            // a base type is its own base; without this the walk reaches root `#` (which reports
            // isBaseType() == true) and returns `#` instead of the concrete base tid
            if (this.asType().isBaseType())
                return this.tid();
            final Type parent = this.asType().parentType();
            if (parent.isBaseType() || this.equals(parent))
                return parent.tid();
            else return parent.baseTypeID();
        } else return this.tid();
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

    default <O extends Obj> O orThrow(final String format, final Object... params) {
        return orThrow(MTronException.of(format, params));
    }

    default <O extends Obj> O as() {
        // raw by design: the engine's own fail machinery (MFail.incrStackWrap)
        // writes fails to the fail space and casts the result back with as(), so
        // this must not gate on Fail
        return (O) this;
    }

    default <O extends Obj> boolean is(final Class<O> clazz) {
        return clazz.isAssignableFrom(this.getClass());
    }

    default boolean isMono() {
        return this instanceof Mono;
    }

    default boolean isNoObj() {
        // {0} is the only zero: a zero coefficient, or a uri whose content is a zero address, whatever its name
        return this.c().isZero() || (this.jvm() instanceof fURI furi && furi.isZero());
    }

    default boolean isNothing() {
        return this.isNoObj() || this.isNone() || this.isFail();
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

    default boolean isRel() {
        return this instanceof Rel;
    }

    default boolean isLst() {
        return this instanceof Lst;
    }

    default boolean isRec() {
        return this instanceof Rec;
    }

    /**
     * Checks if current obj is an inst.
     * Note that noobj implements inst.
     * Use {@link #isObjInst} for non-noobj verification.
     *
     * @return the obj cast to an inst
     */
    default boolean isInst() {
        return this instanceof Inst;
    }

    default boolean isMonad() {
        return this instanceof StatefulMonad;
    }

    default boolean isMachine() {
        return this instanceof Machine;
    }

    default boolean isAddressable() {
        return this instanceof Addressable;
    }


    default Obj autoResolve(final Obj obj) {
        // A CODE IS AS AUTOMATIC AS ITS HEAD (Code.isAuto): !*<../b>.plus(4) fires the dereference, then adds 4 to it.
        // Only an auto head fires one; a code that starts from an initial inst stays code (a poly holds programs)
        final boolean auto = this.isAuto();
        if (!auto && !(this.isInst() && this.asInst().isInitial()))
            return this;
        // an auto inst reads the poly holding it; an initial inst maps nothing to objs, so it runs from noobj
        return this.apply(auto ? obj : noobj()).c(c -> obj.isNoObj() ? c : c.mult(obj.c()));
        //   return Obj.Helper.getAutoPointer(this).map(Router::read).orElse(this);
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

    default boolean isSpace() {
        return this instanceof Space;
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
        return this.tid().basePath().equals(AUTO_FROM_INST_TID);
    }

    default boolean isAutoAt() {
        return this.tid().basePath().equals(AUTO_AT_INST_TID);
    }

    default Obj as(final Type type) {
        if (this.test(type))
            return type.hasConstructor() ? Obj.Helper.objClone(this, this.jvm(), type.vidOrTid(), this.vid()) : this.tid(type.vidOrTid());
        else
            throw MTronException.of("%s is not a %s [%s]", this, type, type.isNominal() ? "nominal" : "structural");
    }

    default Bool asBool() {
        try {
            // a Bool casts to itself, zero or not (the zero root is a parent type); any other zero is Bool's noobj
            return this instanceof Bool || !this.isNoObj() ? (Bool) this : Bool.bool_noobj();
        } catch (final Exception e) {
            if (this.isFail() || this.isCaughtFail())
                throw this.asFail().asException();
            throw MTronException.of(e);
        }
    }

    default Bytes asBytes() {
        try {
            // a Bytes casts to itself, zero or not (the zero root is a parent type); any other zero is Bytes's noobj
            return this instanceof Bytes || !this.isNoObj() ? (Bytes) this : Bytes.bytes_noobj();
        } catch (final Exception e) {
            if (this.isFail() || this.isCaughtFail())
                throw this.asFail().asException();
            throw MTronException.of(e);
        }
    }

    default Int asInt() {
        try {
            return this.isNoObj() ? Int.int_noobj() : (Int) this;
        } catch (final Exception e) {
            if (this.isFail() || this.isCaughtFail())
                throw this.asFail().asException();
            throw MTronException.of(e);
        }
    }

    default Real asReal() {
        try {
            return this.isNoObj() ? Real.real_noobj() : (Real) this;
        } catch (final Exception e) {
            if (this.isFail() || this.isCaughtFail())
                throw this.asFail().asException();
            throw MTronException.of(e);
        }
    }

    default Str asStr() {
        try {
            return this.isNoObj() ? Str.str_noobj() : (Str) this;
        } catch (final Exception e) {
            if (this.isFail() || this.isCaughtFail())
                throw this.asFail().asException();
            throw MTronException.of(e);
        }
    }

    default Uri asUri() {
        try {
            return this.isNoObj() ? Uri.uri_noobj() : (Uri) this;
        } catch (final Exception e) {
            if (this.isFail() || this.isCaughtFail())
                throw this.asFail().asException();
            throw MTronException.of(e);
        }
    }

    default Rec asRec() {
        // a cast onto a fail would surface as a bare ClassCastException with the
        // original failure reason lost — name it
        if (this instanceof Fail fail)
            throw MTronException.of("asRec onto a fail: %s", fail.message());
        try {
            return this.isNoObj() ? Rec.rec_noobj() : (Rec) this;
        } catch (final Exception e) {
            if (this.isFail() || this.isCaughtFail())
                throw this.asFail().asException();
            throw MTronException.of(e);
        }
    }

    default Lst asLst() {
        try {
            return this.isNoObj() ? Lst.lst_noobj() : (Lst) this;
        } catch (final Exception e) {
            if (this.isFail() || this.isCaughtFail())
                throw this.asFail().asException();
            throw MTronException.of(e);
        }
    }

    default Rel asRel() {
        try {
            return this.isNoObj() ? Rel.rel_noobj() : (Rel) this;
        } catch (final Exception e) {
            if (this.isFail() || this.isCaughtFail())
                throw this.asFail().asException();
            throw MTronException.of(e);
        }
    }

    default Poly<?, ?> asPoly() {
        try {
            return (Poly<?, ?>) this;
        } catch (final Exception e) {
            if (this.isFail() || this.isCaughtFail())
                throw this.asFail().asException();
            throw MTronException.of(e);
        }
    }

    /**
     * Casts the current obj to an inst.
     * Note that noobj implements inst.
     *
     * @return the obj cast to an inst
     */
    default Inst asInst() {
        try {
            return (Inst) this;
        } catch (final Exception e) {
            if (this.isFail() || this.isCaughtFail())
                throw this.asFail().asException();
            throw MTronException.of(e);
        }
    }

    default Code asCode() {
        try {
            // a Code casts to itself, zero or not (the zero root is a parent type); any other zero is Code's noobj
            return this instanceof Code || !this.isNoObj() ? (Code) this : Code.code_noobj();
        } catch (final Exception e) {
            if (this.isFail() || this.isCaughtFail())
                throw this.asFail().asException();
            throw MTronException.of(e);
        }
    }

    default Call asCall() {
        try {
            return (Call) this;
        } catch (final Exception e) {
            if (this.isFail() || this.isCaughtFail())
                throw this.asFail().asException();
            throw MTronException.of(e);
        }
    }

    default StatefulMonad asMonad() {
        try {
            return (StatefulMonad) this;
        } catch (final Exception e) {
            if (this.isFail() || this.isCaughtFail())
                throw this.asFail().asException();
            throw MTronException.of(e);
        }
    }

    default Machine asMachine() {
        try {
            return (Machine) this;
        } catch (final Exception e) {
            if (this.isFail() || this.isCaughtFail())
                throw this.asFail().asException();
            throw MTronException.of(e);
        }
    }

    default Type asType() {
        try {
            // a Type casts to itself, zero or not (the zero root is a parent type); any other zero is Type's noobj
            return this instanceof Type || !this.isNoObj() ? (Type) this : Type.type_noobj();
        } catch (final Exception e) {
            if (this.isFail() || this.isCaughtFail())
                throw this.asFail().asException();
            throw MTronException.of(e);
        }
    }

    default Objs asObjs() {
        try {
            return (Objs) this;
        } catch (final Exception e) {
            if (this.isFail() || this.isCaughtFail())
                throw this.asFail().asException();
            throw MTronException.of(e);
        }
    }

    default Fail asFail() {
        try {
            return (Fail) this;
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    /**
     * The conversion diagnostic. The offending obj is rendered with {@link #toCleanString()} — its TEXT, not its
     * mtron spelling: a str offender used to read {@code 'a'} (quotes and all), and every message that embeds it
     * (which is then written as a str on the wire) doubled those quotes. A diagnostic says what the value IS.
     */
    String xxxValue = "%s [%s] unable to convert %s";

    default Throwable failValue() {
        if (this.isFail() || this.isCaughtFail())
            return this.jvm();
        throw MTronException.of(xxxValue, this.toCleanString(), T(this.typeId()), FAIL_TYPE);
    }

    default boolean boolValue() {
        if (this.isBool())
            return this.jvm();
        if (this.isFail() || this.isCaughtFail())
            throw this.asFail().asException();
        throw MTronException.of(xxxValue, this.toCleanString(), T(tid()), BOOL_TYPE);
    }

    default ByteBuffer bytesValue() {
        if (this.isBytes())
            return this.jvm();
        if (this.isFail() || this.isCaughtFail())
            throw this.asFail().asException();
        throw MTronException.of(xxxValue, this.toCleanString(), T(tid()), BYTES_TYPE);
    }

    default Long intValue() {
        if (this.isInt())
            return this.jvm();
        if (this.isFail() || this.isCaughtFail())
            throw this.asFail().asException();
        throw MTronException.of(xxxValue, this.toCleanString(), this.isType() ? "type" : "value", T(tid()), INT_TYPE);
    }

    default Double realValue() {
        if (this.isReal())
            return this.jvm();
        if (this.isFail() || this.isCaughtFail())
            throw this.asFail().asException();
        throw MTronException.of(xxxValue, this.toCleanString(), T(tid()), REAL_TYPE);
    }

    default String strValue() {
        if (this.isStr())
            return this.jvm();
        if (this.isFail() || this.isCaughtFail())
            throw this.asFail().asException();
        throw MTronException.of(xxxValue, this.toCleanString(), T(tid()), STR_TYPE);
    }

    default fURI uriValue() {
        if (this.isUri())
            return this.jvm();
        if (this.isFail() || this.isCaughtFail())
            throw this.asFail().asException();
        throw MTronException.of(xxxValue, this.toCleanString(), T(tid()), URI_TYPE);
    }

    default List<Obj> lstValue() {
        if (this.isLst())
            return this.jvm();
        if (this.isFail() || this.isCaughtFail())
            throw this.asFail().asException();
        throw MTronException.of(xxxValue, this.toCleanString(), T(tid()), LST_TYPE);
    }

    default Iterable<Obj> objsValue() {
        if (this.isObjs())
            return this.jvm();
        if (this.isFail() || this.isCaughtFail())
            throw this.asFail().asException();
        throw MTronException.of(xxxValue, this.toCleanString(), T(tid()), OBJS_TID);
    }


    default Map<Obj, Obj> recValue() {
        if (this.isRec())
            return this.jvm();
        if (this.isFail() || this.isCaughtFail())
            throw this.asFail().asException();
        throw MTronException.of(xxxValue, this.toCleanString(), T(tid()), REC_TYPE);
    }

    default Pair<Obj, Obj> relValue() {
        if (this.isRel())
            return this.jvm();
        if (this.isFail() || this.isCaughtFail())
            throw this.asFail().asException();
        throw MTronException.of(xxxValue, this.toCleanString(), T(tid()), REL_TYPE);
    }

    default Tuple.Triplet<Poly<?, ?>, Inst.f, Obj> instValue() {
        if (this.isInst())
            return this.jvm();
        if (this.isFail() || this.isCaughtFail())
            throw this.asFail().asException();
        throw MTronException.of(xxxValue, this.toCleanString(), T(tid()), INST_TYPE);
    }

    default List<Inst> codeValue() {
        if (this.isCode())
            return this.jvm();
        if (this.isFail() || this.isCaughtFail())
            throw this.asFail().asException();
        throw MTronException.of(xxxValue, this.toCleanString(), T(tid()), CODE_TYPE);
    }

    default Obj typeValue() {
        if (this.isType())
            return this.jvm();
        if (this.isFail() || this.isCaughtFail())
            throw this.asFail().asException();
        throw MTronException.of(xxxValue, this.toCleanString(), T(tid()), TYPE_TYPE);
    }

    default List<Obj> monadValue() {
        if (this.isMonad())
            return this.jvm();
        if (this.isFail() || this.isCaughtFail())
            throw this.asFail().asException();
        throw MTronException.of(xxxValue, this.toCleanString(), T(tid()), MACH_MONAD_TYPE);
    }

    default boolean hasVID() {
        return null != this.vid();
    }

    default String toCleanString() {
        return Str.Helper.cleanString(this, true);
    }

    default String toShortString() {
        if (this.isType() && null != this.vid())
            return this.vid().small().name() + (this.tid().isOne() ? "" : ("{" + this.tid().c().toString() + "}")) + "::T";
        return this.toString();
    }

    Obj clone();

    <O extends Obj> O self(final Object jvm, final fURI tid, final fURI vid);

    default void delete() {
        // a LOCAL write: deleted from the machine's memory, where its space (~/sys, the fail stacks) is mounted
        if (null != this.vid())
            Machine.current().memory().write(this.vid(), noobj());
    }

    /**
     * Returns the current obj stored at this obj's vid, or {@code this} if no vid is set.
     */
    default Obj load() {
        return null == this.vid() ? this : this.selfJVM(Machine.read(this.vid()).jvm());
    }

    default Obj save() {
        return null == this.vid() ? this : Machine.write(this.vid(), this);
    }

    default boolean booleanCheck() {
        if (this.isNoObj() || this.isFail())
            return false;
        if (this.isBool())
            return this.boolValue();
        else return true;
    }

    class Helper {

        private Helper() {
            // do nothing
        }

        private static final ObjSerializer<String> SERIALIZER = ObjmtronSerializer.single();

        public static fURI specificTypeId(final Obj obj) {
            return obj.isType() && obj.hasVID() ? obj.vid() : obj.tid();
        }

        public static Type specificType(final Obj obj) {
            return obj.isType() ? obj.asType() : obj.type();
        }

        public static Set<Inst> isaInsts() {
            return Set.of(
                    isaOf(INT_TID), isaOf(REC_TID), isaOf(REL_TID), isaOf(BOOL_TID),
                    isaOf(LST_TID), isaOf(URI_TID), isaOf(STR_TID), isaOf(REAL_TID),
                    isaOf(BYTES_TID), isaOf(CODE_TID), isaOf(M_ISA_INST_TID));
        }

        private static Inst isaOf(final fURI tid) {
            return instC(ISA_INST_TID.dom(tid).rng(tid.maybe()), lst(T(ALL.any())), (lhs, inst) -> lhs.test(inst.arg(0)) ? lhs : noobj());
        }
        
        /*public static Set<Inst> isaInsts() {
            return Set.of(
                    instC(ISA_INST_TID.dom(A).rng(A.maybe()), lst(T(B)), (lhs, inst) -> inst.arg(0).isObjCall() ? (inst.arg(0).apply(lhs).isNoObj() ? noobj() : lhs) : (lhs.test(inst.arg(0)) ? lhs : noobj())));
        }*/

        public static boolean isAuto(final Obj obj) {
            return obj.isObjCall() && obj.asCall().insts().getFirst().tid().basePath().equals(AUTO_INST_TID);
        }

        /**
         * Consolidated type-check: is {@code lhs} an instance of type {@code rhs}?
         * <p>
         * Single canonical entry-point for the universal checks (auto, noobj,
         * same-VID, ObjCall, coefficient, URI) followed by delegation to
         * {@link Type.Helper#typeCheck(Obj, Obj)} for type-specific logic.
         * All {@code test()} overrides route through here for non-structural
         * comparisons.
         */
        public static boolean testObjs(final Obj lhs, final Obj rhs) {
            // ── universal quick-exits ──
            if (isAuto(rhs) || rhs instanceof FutureObj<?> || lhs instanceof FutureObj<?>)
                return true;
            if (lhs.isNoObj())
                return rhs.tid().c().isZeroable();
            if (rhs.isNoObj())
                return lhs.c().isZeroable();
            // a fibered rec is also what the object at one of its fibers is; the fiber of a multiplicity of recs is
            // that multiplicity of objects, judged apart so the stored object is never re-typed
            if (rhs.isType() && lhs.isRec() && (lhs.tid().hasPoly() || !REC_TID.basePath().equals(lhs.tid().basePath()))) {
                final Type type = lhs.type();
                if (type.isFibered() && type.tid().poly().stream()
                        .map(fiber -> lhs.asRec().<Obj>at(f(fiber.trim()).basePath()))
                        .filter(at -> !at.isNoObj())
                        .anyMatch(at -> at.c().mult(lhs.c()).within(rhs.c()) && at.test(rhs.c(at.c()))))
                    return true;
            }
            // testNominally compares names, and a nominal type's coefficient is on its tid (T(person{2}) is
            // person::T with tid rec{2}), so the coefficient is judged here
            if (rhs.isType() && rhs.asType().isNominal()) {
                return lhs.c().within(rhs.c()) && lhs.testNominally(rhs);
            }
            //if (!lhs.baseTypeID().test(rhs.baseTypeID()))
            //    return false;
            // if (!lhs.isObjCall() && rhs.isType() && rhs.asType().isNominal() &&
            //         ((lhs.isType() && lhs.asType().isRefinementOf(rhs.asType())) || (lhs.type().isRefinementOf(rhs.asType()))))
            //    return false;
            // ── same-VID fast-path (coefficient + predicate guard) ──
            if (null != lhs.vid() && Objects.equals(lhs.vid(), rhs.vid()))
                return lhs.c().within(rhs.c()) &&
                        (!rhs.isType() || !rhs.asType().hasPredicate() ||
                                (lhs.isType() && Objects.equals(lhs.asType().predicate(), rhs.asType().predicate())));

            // ── ObjCall handling ──
            if (rhs.isObjCall() && !rhs.asCall().isPredicate(lhs)) {
                return ((!Typer.Helper.instDom() || Obj.Helper.specificType(lhs).test(rhs.dom())) &&
                        (!Typer.Helper.instRng() || Obj.Helper.specificType(lhs).test(rhs.rng())));
            }

            // ── non-base type with TID→VID match: predicate-aware early exit ──
            if (rhs.isType()) {
                final Type rhsType = rhs.asType();
                if (!lhs.isCall() && rhsType.isNominal()) {
                    return Type.Helper.nominalTypeChecker(lhs, rhsType);
                } else if (!rhs.asType().isBaseType()
                        && Type.Helper.testByIdentity(lhs.tid(), rhs.vid())) { // identity, not a claim: erasure is explicit here
                    // a name match says nothing of multiplicity: the coefficient is judged as on the nominal path
                    return lhs.c().within(rhs.c()) && (rhs.asType().isRootType() || !rhs.asType().hasPredicate() ||
                            (lhs.isType()
                                    ? Objects.equals(lhs.asType().predicate(), rhs.asType().predicate())
                                    : !rhs.asType().apply(lhs).isNothing()));
                }
            }

            // ── URI structural match ──
            if (rhs.isUri() && lhs.isUri() &&
                    !lhs.uriValue().test(rhs.uriValue()) && !lhs.uriValue().big().test(rhs.uriValue().big()))
                return false;

            // ── lhs is ObjCall ──
            if (lhs.isObjCall())
                return lhs.tid().c().within(rhs.tid().c());

            // ── rhs is ObjCall: apply and recurse on range ──
            if (rhs.isObjCall())
                return rhs.apply(lhs).test(rhs.rng());

            // ── coefficient bounds ──
            if (!lhs.c().within(rhs.c()))
                return false;

            // ── root type (T::T or ALL without predicate) accepts everything ──
            if (rhs.isType() && !rhs.asType().hasPredicate() &&
                    (rhs.asType().isRootType() || rhs.tid().equals(ALL) || Objects.equals(rhs.vid(), ALL)))
                return true;

            // ── delegate type-specific logic ──
            if (rhs.isType())
                return Type.Helper.typeCheck(lhs, rhs);

            // ── fallback: TID + JVM equality ──
            if (!lhs.tid().test(rhs.tid()))
                return false;
            if (lhs.isUri() && rhs.isUri())
                return lhs.uriValue().big().test(rhs.uriValue().big());
            if (lhs.isObjs() && rhs.isObjs()) {
                return Objects.equals(
                        IteratorUtil.set(lhs.objsValue().iterator()),
                        IteratorUtil.set(rhs.objsValue().iterator()));
            }
            return Objects.equals(lhs.jvm(), rhs.jvm());
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

        public static Optional<Obj> getAuto(final Obj obj) {
            if (isAuto(obj))
                return Optional.of(obj.asInst().arg(0));
            else
                return Optional.empty();
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

        /**
         * Every zero-coefficient obj is the one zero ({@code int{0} == str{0} == noobj}), and {@link #objEquals} says so,
         * so every zero hashes as noobj: an {@code int{0}} key must find a {@code noobj} one.
         */
        public static int objHashCode(final Obj obj) {
            return obj.isNoObj() ? noobj().hashCode() : Objects.hash((Object) obj.jvm());
        }

        public static boolean objEquals(final Obj obj, final Object other) {
            if (!(other instanceof Obj otherObj))
                return false;
            if (obj.isNoObj() && otherObj.isNoObj())
                return true;
            if (!Objects.equals(obj.vid(), otherObj.vid()))
                return false;
            if (obj.isObjs() && otherObj.isObjs()) {
                final Set<Obj> objSet = new HashSet<>(obj.jvm());
                final Set<Obj> otherSet = new HashSet<>(otherObj.jvm());
                return objSet.equals(otherSet);
            }
           /* if (obj.isObjs() || otherObj.isObjs()) {
                return Objects.equals(objs(obj), objs(otherObj));
            }*/
            return Objects.equals(obj.tid(), otherObj.tid()) &&
                    Objects.equals(obj.jvm(), otherObj.jvm());
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
            if (Machine.loaded() && null != obj.jvm() && !obj.isType() && !obj.isNoObj() && !obj.isCall() && !obj.isInstSet() && !ObjFactory.Helper.baseTID(obj).test(obj.type().baseTypeID().basePath()))
                throw new TypeMismatchException(obj, obj.type(), "%s [%s] is not a %s", obj, obj.jvm().getClass().getSimpleName().toLowerCase(), obj.type());
            if (!obj.isType() && obj.tid().isGeneric())
                throw new TypeMismatchException(obj, obj.type(), "%s value can not be typed generic %s::T", obj.jvm(), obj.tid());
            if (Typer.Helper.typePred()) {
                // A GENERIC POLY BINDS AT CONSTRUCTION. A poly written WITH a value is a declaration, not a free
                // signature: `lst[A,A]::[1,2]` gives both indices one type, so the value's own elements decide that A
                // is int and the value is then typed `lst[int,int]` (its tid, and so what .type() reports). This is
                // the same path a concrete poly takes — a value that contradicts the declaration (`lst[A,A]::[1,'x']`)
                // or does not fill every index (`lst[A,B]::[1]`) keeps its declared tid and is failed by the verdict
                // below, so there is ONE error for "the value does not match the poly", whether the poly was concrete
                // or bound-from-generics. `rec` is deliberately not bound: its bracket names fields.
                final boolean genericPoly = Machine.loaded() && obj.isLst() && obj.tid().hasPoly() &&
                        Lst.LstType.hasGenericSlot(obj.tid().poly());
                final fURI boundTID = genericPoly ? Lst.LstType.boundTID(obj.asLst(), obj.tid()) : obj.tid();
                if (null != boundTID && !boundTID.equals(obj.tid()))
                    obj.selfTID(boundTID);
                // ONE resolution serves the verdict AND the message. obj.type() is a live registry read, so a
                // second call can answer a DIFFERENT type than the one the verdict was computed against -- the
                // error then prints a type the verdict never saw, and its field diff shows no mismatch.
                final Type objType = Machine.loaded() && !obj.isInstSet() && !obj.isNoObj() && !obj.isType() ?
                        obj.type() : null;
                // AN EPHEMERAL LABEL CANNOT CONVICT. the resolution answers a label (tid==vid, no predicate)
                // exactly when the registered form is unreachable at this instant -- a forward reference still
                // being declared, or a resolution racing the registration. testing a structural instance
                // against the label rejects it for a reason no diff can show, so a label's verdict is
                // "unverifiable", never "mismatch" -- the leniency the nominal fallthrough already gives these.
                if (null != objType && !objType.isEphemeral() && (null == boundTID || !obj.test(objType))) {
                    if (obj.isPoly()) {
                        // THE VERDICT EXECUTES, THE DIFF COMPARES. the test path runs the type's predicate call
                        // through the processor (Type.apply -> obj.test(predicate().apply(obj))), while the
                        // recursion diff below only matches shapes -- so when predicate EXECUTION is what failed
                        // (an unresolved isa/matches inst, a deref the resolver cannot reach), the diff shows
                        // every field matching and the real reason lives in the apply result: a fail carries the
                        // resolver's own exception, which the boolean verdict swallows. re-run the apply here,
                        // for the message alone.
                        String applyNote = "";
                        try {
                            final Obj applied = objType.apply(obj);
                            if (applied.isFail() && null != applied.failValue())
                                applyNote = "\n" + indent("predicate apply failed: " + applied.failValue().getMessage(), 2);
                            else if (applied.isNoObj())
                                applyNote = "\n" + indent("predicate apply failed on execution", 2);
                            else if (!obj.test(applied))
                                applyNote = "\n" + indent("predicate apply failed on coercion: " + applied, 2);
                            else if (objType.vid().hasPoly())
                                applyNote = "\n" + indent("predicate apply failed on poly", 2);
                            else
                                applyNote = "\n" + indent("predicate apply failed on corrupted instset", 2);
                        } catch (final RuntimeException whyless) {
                            applyNote = "\n" + indent("predicate apply failed on re-run: " + whyless.getMessage(), 2);
                        }
                        // the type and the walk that failed are the whole content of this message, and both are
                        // deep structures.  Rendered through toString() each collapsed onto ONE line — the type
                        // ran to ~600 columns and its diff to a run-on of nested parens — so the console showed a
                        // wall that wrapped into something no reader could follow.  toString() is the PLAIN
                        // serializer (legal mtron, deliberately one line); the diagnostic instance nests and
                        // indents, and — this text travels to logs, tests and other processes — carries no link
                        // or color markup.
                        final String matchDiffString = ObjmtronUISerializer.diagnostic().write(Poly.Helper.diffTypeRecursion(obj, objType));
                        final String objTypeString = ObjmtronUISerializer.diagnostic().write(objType);
                        final String objTidString = ObjmtronUISerializer.diagnostic().write(obj.tid(obj.baseTypeID()));
                        final int width = Math.max(Math.max(
                                CommonUtil.width(matchDiffString),
                                CommonUtil.width(objTypeString)), CommonUtil.width(objTidString));
                        // the bracketed wrapper is what makes indent() reach the FIRST line too: indent() leaves
                        // line one alone, so an unwrapped type would hang at column 0 while its every
                        // continuation sat at column 2.
                        throw new TypeMismatchException(obj, objType,
                                "obj does not match %s::T\n%s\n%s\n%s\n%s\n%s%s",
                                obj.tid(),
                                indent("[" + objTidString, 2),
                                indent("X=>", 6),
                                indent(objTypeString, 2),
                                indent("─".repeat(width), 2),
                                indent(matchDiffString + "]", 2),
                                applyNote);
                    } else {
                        // name the level that rejected the value, not just the leaf. A type's constraints are
                        // its whole predicate stack, so for a nested stack the leaf may be the very predicate
                        // that PASSED — young::-3 fails nat's ?>=0 while young's own ?<25 accepts it.
                        final Type attemptedType = objType;
                        final Type rejectedAt = Type.Helper.rejectedBy(obj, attemptedType);
                        final String level = (null == rejectedAt || rejectedAt.equals(attemptedType)) ? "" :
                                "%n\trejected at the %s level of the predicate stack (%s)".formatted(
                                        null == rejectedAt.vid() ? "root" : rejectedAt.vid().name(), rejectedAt);
                        throw new TypeMismatchException(obj, attemptedType, "%s is not a %s%s", obj.selfTID(obj.baseTypeID()), attemptedType, level);
                    }
                }
            }
        }

        public static void objCheckAndSave(final Obj obj) {
            objTypeCheck(obj);
            // RO_TEST (read-only type-test mode): check only, skip the incidental space write
            // A Space (and an InstSet, which is a Space) is a container registered via addSpace/import, not a value
            // to be written at its own vid — writing it back into itself is what a catch-all used to swallow.
            if (!BootLoader.RO_TEST && null != obj.vid() && !obj.isType() && !obj.isSpace())
                Machine.write(obj.vid(), obj);
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
                if (!BASE_TYPES.contains(bigTID.basePath()) && Machine.loaded()) {
                    Obj type = Machine.read(bigTID);
                    if (!type.isNoObj() && type.isType()) {

                        final Obj protoObj = MObjFactory.of().toObj(jvm, null, vid, clazz);
                        if (!protoObj.baseTypeID().test(type.baseTypeID()))
                            throw MTronException.of("%s is not a %s".formatted(protoObj, type));
                        if (type.asType().hasConstructor()) {
                            final O constructedObj = type.asType().constructor().apply(protoObj).as();
                            if (constructedObj.isFail())
                                // a failed constructor must not slip past the generic return
                                // cast (it would surface as a bare CCE with the cause
                                // lost) — always name the reason, type_ctor or not (the
                                // objClone sibling does exactly this)
                                throw MTronException.of("unable to construct %s::T: %s", tid, constructedObj);
                            constructedObj.self(constructedObj.jvm(), bigTID, vid);
                            if (null != vid)
                                Machine.write(vid, constructedObj);
                            return constructedObj;
                        }
                    }
                }
            }
            return MObjFactory.of().toObj(jvm, tid, vid, clazz);
        }

        /**
         * Clone recursion depth — a self-referential obj (an inst whose tid is
         * {@code #{*}} references the inst itself) re-enters objClone without
         * end; the gauge turns that runaway into a named failure instead of a
         * StackOverflowError.
         */
        private static final int MAX_CLONE_DEPTH = 128;
        private static final ThreadLocal<Integer> CLONE_DEPTH = ThreadLocal.withInitial(() -> 0);

        public static <O extends Obj> O objClone(final Obj obj, final Object jvm, final fURI tid, final fURI vid) {
            final int depth = CLONE_DEPTH.get() + 1;
            if (depth > MAX_CLONE_DEPTH)
                throw MTronException.of("clone depth limit exceeded (%d) — self-referential obj at %s", MAX_CLONE_DEPTH, obj);
            CLONE_DEPTH.set(depth);
            try {
                if (!Objects.equals(tid, obj.tid())) {
                    final Obj type = Machine.read(tid);
                    if (!type.isNoObj() && type.isType() && type.<Type>as().hasConstructor()) {
                        final Obj clone = type.<Type>as().constructor().apply(obj);
                        if (clone.isFail())
                            throw MTronException.of(clone.<Fail>as().jvm());
                        return (O) clone.selfTID(tid);
                    }
                }
                if (null != jvm && !Objects.equals(jvm, obj.jvm()) || !Objects.equals(tid, obj.tid()) || !Objects.equals(vid, obj.vid())) {
                    try {
                        final O clone = (O) obj.clone();
                        Obj.Helper.objCheckAndSave(clone, jvm, tid, null == vid || vid.isEmpty() ? null : vid);
                        return (O) clone.selfTID(tid);
                    } catch (final Exception e) {
                        throw MTronException.of(e);
                    }
                }
                return (O) obj;
            } finally {
                CLONE_DEPTH.set(depth - 1);
            }
        }

        /**
         * A DEEP clone of {@code obj}, unbounded — see {@link #deepClone(Obj, long)}.
         */
        public static Obj deepClone(final Obj obj) {
            return deepClone(obj, Long.MAX_VALUE);
        }

        /**
         * A deep copy of {@code obj} to {@code depth} container levels, with NO vid — one that kept it would overwrite the original at that address, which is the {@code *} (anonymous copy) side rather than the {@code @} (write-back) side.
         */
        public static Obj deepClone(final Obj obj, final long depth) {
            if (null == obj)
                return noobj();
            final int nested = DEEP_CLONE_DEPTH.get() + 1;
            if (nested > MAX_DEEP_CLONE_DEPTH)
                // name the obj by tid and vid: rendering a cyclic obj trips the serializer's own guard in place of this one
                throw MTronException.of("deep clone depth limit exceeded (%d) — self-referential obj %s@%s",
                        MAX_DEEP_CLONE_DEPTH, obj.tid(), obj.vid());
            DEEP_CLONE_DEPTH.set(nested);
            try {
                if (obj.isObjs())
                    // materialized, not lazy: the stream must be consumed inside this thread-local frame
                    return objs(obj.stream().map(member -> deeper(member, depth)).toList());
                if (obj.isRec()) {
                    // raw recValue, not elements(): elements() multiplies each child's coefficient by the container's
                    final Map<Obj, Obj> container = new LinkedHashMap<>();
                    obj.recValue().forEach((key, value) -> container.put(key, deeper(value, depth - 1)));
                    return obj.clone().self(container, obj.tid(), null);
                }
                if (obj.isLst()) {
                    final List<Obj> container = new ArrayList<>(obj.lstValue().size());
                    obj.lstValue().forEach(member -> container.add(deeper(member, depth - 1)));
                    return obj.clone().self(container, obj.tid(), null);
                }
                if (obj.isRel()) {
                    final Pair<Obj, Obj> sides = obj.relValue();
                    return obj.clone().self(Pair.with(deeper(sides.get0(), depth - 1), deeper(sides.get1(), depth - 1)),
                            obj.tid(), null);
                }
                return null == obj.vid() ? obj.clone() : obj.clone().selfVID(null);
            } finally {
                DEEP_CLONE_DEPTH.set(nested - 1);
            }
        }

        /**
         * the bound an unbounded deep clone descends before the structure is a cycle rather than a nesting
         */
        private static final int MAX_DEEP_CLONE_DEPTH = 128;
        private static final ThreadLocal<Integer> DEEP_CLONE_DEPTH = ThreadLocal.withInitial(() -> 0);

        /**
         * One level down: {@code depth} goes negative at the bound, and a negative depth means the recursion has
         * bottomed out there, so the child is shared as it stands rather than rebuilt.
         */
        private static Obj deeper(final Obj obj, final long depth) {
            return depth < 0 ? obj : deepClone(obj, depth);
        }
    }

    final class ObjType {
        public static Set<Inst> insts() {
            // THE TYPERS — as, type(), vid() and tid() have the OBJECT'S TYPE/IDENTITY as their subject, not its
            // value: each retypes the ONE obj, so they are opaque to a wrapper's {self} ∪ components fan-out, and the
            // rewriter neither floats one across a coefficient change nor merges two of them (Inst.Form.typer).
            // Declared here, beside the registrations below, because the facet is per OPERATION: every
            // specialization of `as` carries it (the `::` construction included) — a form is a fact about the
            // op, not about one dom/rng registration.
            InstBuilder.declareForm(Inst.Form.typer, AS_INST_TID, TYPE_INST_TID, VID_INST_TID, TID_INST_TID);
            return new LinkedHashSet<>(List.of(
                    InstBuilder.build()
                            .tid(NATIVE_INST_TID)
                            .dom(A.maybe(), "any obj")
                            .rng(B.maybeSome(), "the objs parsed from the command output")
                            .argLst(STR_TYPE, "the command to execute")
                            .desc("a command execution function \\(f(x) \\mapsto x'\\): executes the arg command and parses its output as objs")
                            .body((lhs, inst) -> {
                                final Str command = inst.arg(0).asStr();
                                return MTronException.wrap(() ->
                                        ObjmtronSerializer.parse(new String(
                                                new ProcessExecutor().commandSplit(command.strValue())
                                                        .readOutput(true)
                                                        .execute()
                                                        .getOutput()
                                                        .getBytes())));
                            })
                            .create(),
                    InstBuilder.build()
                            .tid(UNION_INST_TID)
                            .dom(A, "any obj")
                            .rng(A.maybe(), "the lhs if any arg type matches, noobj otherwise")
                            .argLst(T(B), T(B.maybe()), T(B.maybe()), T(B.maybe()), T(B.maybe()), T(B.maybe()), T(B.maybe()), T(B.maybe()))
                            .desc("a union type test function \\(f(x) \\in \\{x, \\emptyset\\}\\): maps the lhs if it matches any of the arg types")
                            .body((lhs, inst) -> inst.args().lstValue().stream().anyMatch(o -> o.test(lhs)) ? lhs : noobj())
                            .create(),
                    InstBuilder.build()
                            .tid(SERIALIZE_INST_TID)
                            .dom(A, "any obj")
                            .rng(B, "the serial form of the lhs obj")
                            .argLst(T(OBJ_SERIAL_TID), "the serializer to apply to the lhs")
                            .desc("a serialization function \\(f(x) \\mapsto x'\\): serializes the lhs obj with the arg serializer")
                            .body((lhs, inst) -> {
                                final Object serialization = inst.arg(0).<ObjSerializer<?>>as().write(lhs);
                                try {
                                    return MObjFactory.of().toObj(serialization);
                                } catch (final Exception e) {
                                    inst.logger().warn("unable to serialize %s with %s: %s", lhs, inst.arg(0), e);
                                    return str(serialization.toString());
                                }
                            })
                            .create(),
                    InstBuilder.build()
                            .tid(FORK_INST_TID)
                            .dom(A.maybeSome(), "any obj")
                            .rng(A.maybeSome(), "the lhs obj")
                            .argLst(ALL_TYPE, "the code to apply asynchronously to the lhs")
                            .desc("a fork function \\(f(x) \\mapsto x\\): the arg code applied to the lhs in a virtual thread, immediately yielding the lhs")
                            .body((lhs, inst) -> {
                                VirtualThread.virtual(inst.arg(0)).applyAsync(lhs);
                                return lhs;
                            })
                            .create(),
                    InstBuilder.build()
                            .tid(RANGE_INST_TID)
                            .dom(A.maybeSome(), "any objs")
                            .rng(A.maybeSome(), "a window of the lhs objs")
                            .argLst(INT_TYPE, "the starting offset", isa_(INT_TYPE).else_(jnt(0)).tryToInst(), "the window length (defaults to 0)")
                            .desc("a windowing function \\(f(X, i, n) \\mapsto X'\\): the objs starting at offset i for a span of n")
                            .body((lhs, inst) -> lhs.take(cInt.of(inst.arg(0).intValue())).get1().take(cInt.of(inst.arg(1).intValue())).get0())
                            .create(),
                    InstBuilder.build()
                            .tid(KMERGE_INST_TID)
                            .dom(LST_TID.poly(A.maybeSome()).maybeSome(), "maybe some sorted lists")
                            .rng(LST_TID.poly(A.maybeSome()), "a lst k-way merged by the arg obj")
                            .argLst(ALL_TYPE, "the obj to merge by")
                            .desc("a sorted merge function \\(f(X) \\mapsto X'\\): k-way merges the lhs sorted lists by the arg obj")
                            .body((lhs, inst) -> {
                                if (lhs.isNoObj())
                                    return noobj();
                                final ObjProjectionComparator cmp = ObjProjectionComparator.of(inst.arg(0));
                                final List<List<Obj>> streams = lhs.elements()
                                        .map(o -> o.stream().flatMap(s -> s.<Obj>elements()).toList())
                                        .toList();
                                final PriorityQueue<int[]> heap = new PriorityQueue<>((a, b) -> cmp.compare(streams.get(a[0]).get(a[1]), streams.get(b[0]).get(b[1])));
                                for (int i = 0; i < streams.size(); i++)
                                    if (!streams.get(i).isEmpty())
                                        heap.add(new int[]{i, 0});
                                final List<Obj> merged = new ArrayList<>();
                                while (!heap.isEmpty()) {
                                    final int[] top = heap.poll();
                                    merged.add(streams.get(top[0]).get(top[1]));
                                    if (top[1] + 1 < streams.get(top[0]).size())
                                        heap.add(new int[]{top[0], top[1] + 1});
                                }
                                final List<Obj> collapsed = new ArrayList<>();
                                for (final Obj o : merged) {
                                    if (!collapsed.isEmpty() && collapsed.getLast().clessEquals(o))
                                        collapsed.set(collapsed.size() - 1, collapsed.getLast().c(c -> c.plus(o.c())));
                                    else
                                        collapsed.add(o);
                                }
                                return lst(collapsed);
                            })
                            .law(Law.monoidic, Law.commutative, Law.pure)
                            .create(),
                    InstBuilder.build()
                            .tid(M_ISA_INST_TID.extend("via"))
                            .dom(A, "any obj")
                            .rng(B, "the lhs obj after the conversion chain")
                            .argLst(REL_TYPE, "a chain rel of the types to convert through")
                            .desc("a conversion function \\(f(x) \\mapsto x'\\): applies successive as_ conversions following the chain of types in the arg")
                            .body((lhs, inst) -> {
                                Rel currentTransform = inst.arg(0).asRel();
                                Obj currentObj = lhs;
                                while (!currentTransform.isNoObj()) {
                                    currentObj = as_(currentTransform.first().asType()).apply(currentObj);
                                    final Obj nextTransform = currentTransform.second();
                                    if (nextTransform.isRel())
                                        currentTransform = nextTransform.asRel();
                                    else {
                                        return as_(nextTransform.asType()).apply(currentObj);
                                    }
                                }
                                throw MTronException.of("transformation path must be a chain rel of types: %s", inst.arg(0));
                            })
                            .create(),
                    InstBuilder.build()
                            .tid(AS_INST_TID)
                            .dom(A, "any obj")
                            .rng(NOOBJ_TID.zero(), "noobj")
                            .argLst()
                            .desc("a casting function \\(f(x) \\mapsto \\emptyset\\): casts the lhs obj to noobj")
                            .body((lhs, inst) -> noobj())
                            .create(),
                    InstBuilder.build()
                            .tid(AS_INST_TID)
                            .dom(A, "any obj")
                            .rng(STR_TID, "a clean str rendering of the lhs obj")
                            .argLst(STR_TYPE, "the target type (str::T)")
                            .desc("a string function \\(f(x) \\mapsto s\\): the lhs obj rendered as a clean str")
                            .body((lhs, inst) -> str(Str.Helper.cleanString(lhs), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), null))
                            .create(),
                    InstBuilder.build()
                            .tid(AS_INST_TID)
                            .dom(A, "any obj")
                            .rng(B, "the lhs obj as the arg type")
                            .argLst(ALL_TYPE, "the type to cast to")
                            .desc("a type casting function \\(f(x) \\mapsto x\\)")
                            .body((lhs, inst) -> inst.arg(0).isType() ? lhs.as(inst.arg(0).asType()) : fail(MTronException.of("%s is not a %s", lhs, inst.arg(0))))
                            .create(),
                    InstBuilder.build()
                            .tid(IMPORT_INST_TID)
                            .dom(ALL.maybe(), "any obj")
                            .rng(SPACE_TID.maybeSome(), "the imported instset space")
                            .argLst(URI_TYPE, "the uri of the instset to import", T(URI_TID.maybe()), "an optional namespace prefix to prevent short name collisions")
                            .desc("an import function: registers the instset at the arg uri into the current space")
                            .body((lhs, inst) -> MTronException.wrap(() ->
                                    objs((Stream) InstSet.importInstSetStream(inst.arg(0).uriValue(), inst.arg(1).isNoObj() ? null : inst.arg(1).uriValue()))))
                            .create(),
                    InstBuilder.build()
                            .tid(DEDUP_INST_TID)
                            .dom(A.maybeSome(), "any objs")
                            .rng(A.maybeSome(), "the deduplicated objs")
                            .argLst()
                            .desc("a deduplication function \\(f(x\\{c\\}) \\mapsto x\\{1\\}\\)")
                            .body((lhs, inst) -> objs(lhs.stream().map(o -> o.c().gt(cInt.ZERO()) ? o.c(cInt::one) : o.c(c -> cInt.of(-1))).distinct()))
                            .create(),
                    InstBuilder.build()
                            .tid(BARRIER_INST_TID)
                            .dom(A.maybeSome(), "any objs")
                            .rng(A.maybeSome(), "the objs as a rec")
                            .argLst(URI_TYPE, "the rec to merge into")
                            .desc("a rec merging function \\(f(X) \\mapsto X'\\)")
                            .body((lhs, inst) -> {
                                try {
                                    final fURI mailbox = inst.arg(0).uriValue();
                                    // ONE REPORT, FIRST DELIVERY WINS. A report stored between the subscribe and the read reaches
                                    // the barrier twice -- published, and read -- and is still one report.
                                    final CompletableFuture<Obj> report = new CompletableFuture<>();
                                    // SUBSCRIBE FIRST. A subscription only sees writes made AFTER it is registered, so it must be in
                                    // place before any peer can report -- otherwise a fast shard reports into the void. It must be
                                    // a sub::T: subq takes any other obj for the code ITSELF, and a rec applied answers itself, so
                                    // the barrier's code never ran. The code receives the publication, pub::[mailbox, report].
                                    Machine.write(mailbox.addQ(SUBQ_PATTERN.toString()), rec(mutableMap(
                                            uri(TARGET), uri(mailbox),
                                            uri(CODE), instLambda(pub -> {
                                                final Obj published = pub.asLst().at(1);
                                                if (!published.isNoObj())
                                                    report.complete(published);
                                                return noobj();
                                            })), SUBQ_SUB_TID, null));
                                    // THEN READ, BEFORE WAITING. A peer may have reported before we subscribed, and that write is
                                    // invisible to the subscription -- so reading first is what keeps the barrier from waiting
                                    // forever. Waiting before reading inverts the whole point of subscribe-then-read.
                                    final Obj already = Machine.read(mailbox);
                                    if (!already.isNoObj())
                                        report.complete(already);
                                    return lhs.append(report.get());
                                } catch (final Exception e) {
                                    throw MTronException.of(e);
                                }
                            })
                            .create(),
                    InstBuilder.build()
                            .tid(BARRIER_INST_TID)
                            .dom(A.maybeSome(), "any objs")
                            .rng(A.maybeSome(), "the objs as is")
                            .argLst()
                            .desc("a passthrough function \\(f(X) \\mapsto X\\)")
                            .body((lhs, inst) -> lhs)
                            .create(),
                    InstBuilder.build()
                            .tid(AS_INST_TID)
                            .dom(A, "any obj")
                            .rng(B, "the lhs obj as the arg type")
                            .argLst(T(B), "the type to construct from the lhs")
                            .desc("a type construction function \\(f(x) \\mapsto x\\)")
                            .body((lhs, inst) -> lhs.tid(inst.arg(0).asType().vid()))
                            .create(),
                    InstBuilder.build()
                            .tid(LOOP_INST_TID.q(MONAD_IN, "state/loop"))
                            .dom(A, "any obj")
                            .rng(INT_TID, "the unchanged lhs obj")
                            .argLst()
                            .desc("a loop state function \\(f(x) \\mapsto x\\): the loop frame marker over the monad")
                            .body((lhs, inst) -> lhs)
                            .create(),
                    InstBuilder.build()
                            .tid(PATH_INST_TID.q(MONAD_IN, "state/path"))
                            .dom(A, "any obj")
                            .rng(LST_TID, "the unchanged lhs obj")
                            .argLst()
                            .desc("a path state function \\(f(x) \\mapsto x\\): the path frame marker over the monad")
                            .body((lhs, inst) -> lhs)
                            .create(),
                    InstBuilder.build()
                            .tid(REPEAT_INST_TID.q(MONAD_IN, "+").q(MONAD_OUT, "+"))
                            .dom(A, "any obj")
                            .rng(A.maybeSome(), "the repeated application results")
                            .args(rec(
                                    uri(ESC).maybe().asUri(), T(A.maybeSome()),
                                    uri(CODE), T(A.maybeSome()),
                                    uri(UNTIL).maybe(), BOOL_TYPE,
                                    uri(EMIT).maybe(), BOOL_TYPE))
                            .argDocs(Map.of(
                                    uri(ESC).maybe(), "the escape predicate",
                                    uri(CODE), "the code to repeat",
                                    uri(UNTIL).maybe(), "the exit predicate (true stops the loop)",
                                    uri(EMIT).maybe(), "the emit predicate"))
                            .desc("a repetition function: loopback application of the code with escape/exit/emit predicates")
                            .body((lhs, inst) -> {
                                StatefulMonad.REPEAT_ITERS.incrementAndGet();
                                final long book0 = System.nanoTime();
                                final Obj escPredicate = inst.arg(f(ESC), 0);
                                final Obj repeatedApply = inst.arg(f(CODE), 1);
                                final Obj untilPredicate = inst.arg(f(UNTIL), 2);
                                final Obj emitPredicate = inst.arg(f(EMIT), 3);
                                final List<Obj> toEmit = new ArrayList<>();
                                final StatefulMonad monad = StatefulMonad.of(lhs);
                                StatefulMonad.T_REPEAT_BOOK.addAndGet(System.nanoTime() - book0);
                                final long esc0 = System.nanoTime();
                                final boolean esc = escPredicate.apply(monad).booleanCheck();
                                StatefulMonad.T_REPEAT_ESC.addAndGet(System.nanoTime() - esc0);
                                if (esc) {
                                    toEmit.add(monad.loopback(false).nextInst());
                                } else {
                                    if (monad.isNoObj() || monad.obj().isNoObj()) {
                                        toEmit.add(monad.loopback(false).nextInst());
                                    } else {
                                        // fresh entry pushes a loop frame; a loopback continuation does not
                                        final long book1 = System.nanoTime();
                                        final boolean loopback = monad.isLoopback();
                                        final StatefulMonad m = monad.loopback(false);
                                        final StatefulMonad repeatMonad = loopback ? m : m.pushLoop();
                                        StatefulMonad.T_REPEAT_BOOK.addAndGet(System.nanoTime() - book1);
                                        final Obj emitCode = emitPredicate.isCall() ? emitPredicate.<Call>as().asCode() : emitPredicate;
                                        final long emit0 = System.nanoTime();
                                        final boolean emit = emitCode.apply(repeatMonad).booleanCheck();
                                        StatefulMonad.T_REPEAT_EMIT.addAndGet(System.nanoTime() - emit0);
                                        if (emit)
                                            toEmit.add(repeatMonad.popLoop().nextInst());
                                        // wrap the predicate in code so a single-inst until (is(gt(10))) goes
                                        // through the monad lens like the multi-inst form (loop().is(gt(3))) —
                                        // applied bare, is(gt(10)) receives the whole monad and never terminates.
                                        final Obj untilCode = untilPredicate.isCall() ? untilPredicate.<Call>as().asCode() : untilPredicate;
                                        final long until0 = System.nanoTime();
                                        final boolean until = untilCode.apply(repeatMonad).booleanCheck();
                                        StatefulMonad.T_REPEAT_UNTIL.addAndGet(System.nanoTime() - until0);
                                        if (until) {
                                            if (!emit)
                                                toEmit.add(repeatMonad.popLoop().nextInst());
                                        } else {
                                            final long code0 = System.nanoTime();
                                            final Obj codeResult = repeatedApply.apply(repeatMonad.obj());
                                            StatefulMonad.T_REPEAT_CODE.addAndGet(System.nanoTime() - code0);
                                            final long book2 = System.nanoTime();
                                            final StatefulMonad monadX = repeatMonad.obj(codeResult).incrLoop(1).loopback(true);
                                            StatefulMonad.T_REPEAT_BOOK.addAndGet(System.nanoTime() - book2);
                                            toEmit.add(monadX);
                                        }
                                    }
                                }
                                return objs(toEmit);
                            })
                            .create(),
                    InstBuilder.build()
                            .tid(AUTO_INST_TID)
                            .dom(ALL.maybe(), "any obj")
                            .rng(ALL.maybeSome(), "the result of applying the arg to the lhs")
                            .argLst(T(ALL.maybe()), "the inst to apply to the lhs")
                            .desc("an auto function \\(f(x) \\mapsto x'\\): the arg applied to the lhs (sugar'd !)")
                            .body((lhs, inst) -> inst.arg(0).apply(lhs))
                            .create(),
                    InstBuilder.build()
                            .tid(AUTO_FROM_INST_TID)
                            .dom(ALL.maybe(), "any obj")
                            .rng(ALL.maybeSome(), "the obj at the arg uri")
                            .argLst(T(ALL.maybe()), "the uri to dereference")
                            .desc("an auto dereference function: reads the obj at the arg uri upon inst access (sugar'd !*)")
                            .body((lhs, inst) -> {
                                final fURI target = inst.arg(0).uriValue();
                                final Obj resolved;
                                if (lhs.isPoly() && target.segments(0, "").equals("..")) {
                                    // A ..-LED URI IS RELATIVE TO THE REFERENCE, not the machine: its first .. leaves
                                    // the reference's own key, so the rest is read from the poly at() fired it with
                                    // (the poly holding it), and a bare .. is that poly, so a poly can refer to itself
                                    final fURI rest = target.pretract(1);
                                    resolved = 0 == rest.segmentLength() ? lhs : lhs.<Poly<?, ?>>as().at(rest.toUri());
                                } else
                                    resolved = Machine.read(target).autoResolve(lhs);
                                // A TYPE IS ITS NAME, so it comes back as itself, as from() hands back a type: a
                                // dereferenced value is detached, but a type stripped of its vid is a nameless type
                                // that no slot can be checked against (an auto_from element in a lst type slot NPE'd
                                // in Type.Helper.typeCheck)
                                return resolved.isType() ? resolved : resolved.vid(null);
                            })
                            .create(),
                    InstBuilder.build()
                            .tid(AUTO_AT_INST_TID)
                            .dom(ALL.maybe(), "any obj")
                            .rng(ALL.maybeSome(), "the obj at the arg uri")
                            .argLst(T(ALL.maybe()), "the uri to dereference")
                            .desc("an auto dereference function: reads the obj at the arg uri upon inst access, preserving its spatial location (sugar'd !@)")
                            .body((lhs, inst) -> {
                                final Obj resolved = Machine.read(inst.arg(0).uriValue()).autoResolve(lhs);
                                return resolved.hasVID() ? resolved : resolved.selfVID(inst.arg(0).uriValue());
                            })
                            .create(),
                    InstBuilder.build()
                            .tid(AUTO_TO_INST_TID)
                            .dom(ALL.maybe(), "any obj")
                            .rng(ALL, "the uri (if possible) that refers to the obj arg")
                            .argLst(ALL_TYPE, "the obj to reference")
                            .desc("like !*(vid), except that the obj arg is converted to an !* reference (an inverse dereference) immediately upon inst access (no inst apply required).")
                            .body((lhs, inst) -> (null == lhs.vid() || lhs.isAutoFrom()) ? lhs : auto_from_(lhs.vid()).tryToInst())
                            .create(),
                    InstBuilder.build()
                            .tid(CATCH_INST_TID)
                            .dom(A, "any obj")
                            .rng(C.maybeSome(), "uncaught fails go to arg, others mapped by identity")
                            .argLst(T(B.maybeSome()), "the obj triggered on an uncaught fail")
                            .desc("a catch function f(x)->x")
                            .body((lhs, inst) -> lhs.isFail() && !lhs.isCaughtFail() ? inst.arg(0).apply(lhs.asFail().caught()).c(c -> c.mult(lhs.c())) : lhs)
                            .create(),
                    InstBuilder.build()
                            .tid(END_INST_TID)
                            .dom(ALL_STAR, "terminal objs")
                            .rng(NOOBJ_TID, "noobj")
                            .argLst()
                            .desc("the terminal function \\(f(x) \\mapsto \\emptyset\\)")
                            .body((lhs, inst) -> noobj())
                            .create(),
                    InstBuilder.build()
                            .tid(PRINTLN_INST_TID)
                            .dom(ALL.maybe(), "the rhs obj")
                            .rng(ALL.maybeSome(), "the lhs obj")
                            .argLst(T(ALL_STAR), "concatenated args followed by newline written to stdout")
                            .desc("a side-effect function \\(f(x) \\mapsto x\\)")
                            .body((lhs, inst) -> objs(inst.args().elements().peek(o -> inst.logger().none(o.isStr() ? o.strValue() : o.toString())).filter(x -> false).findAny().orElse(lhs).stream().peek(x -> inst.logger().none(lineSeparator()))))
                            .create(),
                    InstBuilder.build()
                            .tid(PRINT_INST_TID)
                            .dom(ALL.maybe(), "the rhs obj")
                            .rng(ALL.maybeSome(), "the lhs obj")
                            .argLst(T(ALL_STAR), "concatenated args followed by newline written to stdout")
                            .desc("a side-effect function \\(f(x) \\mapsto x\\)")
                            .body((lhs, inst) -> objs(inst.args().elements().peek(o -> inst.logger().none(o.isStr() ? o.strValue() : o.toString())).reduce(noobj(), (a, b) -> noobj()).orElse(lhs)))
                            .create(),
                    InstBuilder.build()
                            .tid(CASCADE_INST_TID)
                            .dom(ALL.maybe(), "any obj")
                            .rng(ALL.maybeSome(), "the result of applying the arg to the lhs")
                            .argLst(T(LST_TID.maybe()), "the inst to apply to the lhs")
                            .desc("an auto function \\(f(x) \\mapsto x'\\): the arg applied to the lhs (sugar'd !)")
                            .body((lhs, inst) -> inst.arg(0).orElse(lhs).asLst().values().reduce(lhs, (a, b) -> b.apply(a)))
                            .create(),
                    InstBuilder.build()
                            .tid(AT_INST_TID)
                            .dom(A.maybe(), "any obj")
                            .rng(B.maybeSome(), "the obj at the arg uri")
                            .argLst(T(URI_TID), "the uri or uri pattern to read")
                            .desc("a spatial read function: reads the obj at the arg uri, preserving its spatial location (sugar'd @)")
                            .body((lhs, inst) -> {
                                final fURI pattern = inst.arg(0).uriValue();
                                if (pattern.hasPattern()) {
                                    return objs(Machine.read(pattern.asBranch()).stream().map(x -> x.asRel().second().selfVID(x.asRel().first().uriValue())));
                                } else {
                                    final Obj resolved = Machine.read(pattern);
                                    return resolved.hasVID() ? resolved : resolved.selfVID(pattern);
                                }
                            })
                            .create(),
                    InstBuilder.build()
                            .tid(ID_INST_TID)
                            .dom(A, "an rhs obj")
                            .rng(A, "an lhs obj")
                            .argLst()
                            .desc("the obj identity function \\(f(x) \\mapsto x\\)")
                            .body((lhs, inst) -> lhs)
                            .create(),
                    // TODO: do we gut this and rely fully on the non-barried form?
                    InstBuilder.build()
                            .tid(ID_INST_TID)
                            .dom(A.maybeSome(), "the rhs obj")
                            .rng(A.maybeSome(), "the lhs obj")
                            .argLst()
                            .desc("a objs barrier identity function \\(f(X) \\mapsto X\\)")
                            .body((lhs, inst) -> lhs)
                            .create(),
                    InstBuilder.build()
                            .tid(AND_INST_TID)
                            .dom(A, "any objs")
                            .rng(BOOL_TID, "true if all objs are true")
                            .argLst(BOOL_TYPE, BOOL_TYPE, BOOL_TYPE.maybe(), BOOL_TYPE.maybe(), BOOL_TYPE.maybe(), BOOL_TYPE.maybe(), BOOL_TYPE.maybe(), BOOL_TYPE.maybe())
                            .desc("logical \\(\\texttt{and}\\) function \\(f(X) \\mapsto \\mathtt{true}\\) if all \\(X\\) are true")
                            .body((lhs, inst) -> bool(inst.args().elements().map(o -> o.orElse(BOOL_TRUE)).allMatch(Obj::boolValue)))
                            .create(),
                    InstBuilder.build()
                            .tid(OR_INST_TID)
                            .dom(A, "any objs")
                            .rng(BOOL_TID, "true if any objs are true")
                            .argLst(BOOL_TYPE, BOOL_TYPE, BOOL_TYPE.maybe(), BOOL_TYPE.maybe(), BOOL_TYPE.maybe(), BOOL_TYPE.maybe(), BOOL_TYPE.maybe(), BOOL_TYPE.maybe())
                            .desc("logical \\(\\texttt{or}\\) function \\(f(X) \\mapsto \\mathtt{true}\\) if any \\(X\\) are true")
                            .body((lhs, inst) -> bool(inst.args().elements().map(o -> o.orElse(BOOL_FALSE)).anyMatch(Obj::boolValue)))
                            .create(),
                    InstBuilder.build()
                            .tid(APPLY_INST_TID)
                            .dom(INST_TID, "the lhs inst obj")
                            .rng(ALL_STAR, "the result of the lhs inst application")
                            .args(rec(uri(LHS).maybe().asUri(), T(ALL_STAR), uri(ARGS).maybe(), LST_TYPE.maybe()))
                            .argDocs(Map.of(uri(LHS), "the obj to apply the lhs inst to",
                                    uri(ARGS), "the inst args (optional)"))
                            .desc("an apply function \\(f(x) \\mapsto x'\\): applies the lhs inst to the given obj with the given args")
                            .body((lhs, inst) -> (inst.args().count() == 1 ? lhs.asInst() : lhs.asInst().args(inst.arg(1).asPoly())).apply(inst.arg(0)))
                            .create(),
                    instC(APPLY_INST_TID.dom(ALL).rng(ALL_STAR), lst(ALL_MAYBE_TYPE), (lhs, inst) -> lhs.apply(inst.arg(0))),
                    InstBuilder.build()
                            .tid(MAP_INST_TID)
                            .dom(A.maybe(), "maybe some obj")
                            .rng(B.maybe(), "the lhs obj applied to the arg obj")
                            .argLst(T(B.maybe()), "any obj")
                            .desc("applies the lhs obj to the arg obj to yield the rhs obj")
                            .body((lhs, inst) -> inst.arg(0).apply(lhs))
                            .law(Law.pure)
                            .create(),
                    InstBuilder.build()
                            .tid(FILTER_INST_TID)
                            .dom(A, "any obj")
                            .rng(A.maybe(), "the lhs if the arg predicate holds, noobj otherwise")
                            .argLst(T(ALL.maybe()), "the predicate to apply to the lhs")
                            .desc("a filter function \\(f(x) \\in \\{x, \\emptyset\\}\\): the lhs mapped if the arg predicate passes")
                            .body((lhs, inst) -> inst.arg(0).apply(lhs).booleanCheck() ? lhs : noobj())
                            .law(Law.partial, Law.pure)
                            .create(),
                    InstBuilder.build()
                            .tid(SIDE_INST_TID)
                            .dom(A, "any obj")
                            .rng(A, "the lhs obj")
                            .argLst(ALL_TYPE, "any obj applied by lhs obj")
                            .desc("passes lhs obj through after applying itself to inst arg obj")
                            .example("1.side(plus(2).to(x)) [-- 1 [x=>3] --]")
                            .body((lhs, inst) -> Optional.of(inst.arg(0).apply(lhs)).map(x -> (Obj) null).orElse(lhs))
                            .create(),
                    InstBuilder.build()
                            .tid(TID_INST_TID)
                            .dom(ALL, "any obj")
                            .rng(URI_TID, "the lhs obj type id")
                            .argLst()
                            .desc("the spatial location of the lhs obj [equivalent to f(x) ~ vid(type())]")
                            .body((lhs, inst) -> lhs.tid().toUri())
                            .create(),
                    InstBuilder.build()
                            .tid(VID_INST_TID)
                            .dom(A, "any obj")
                            .rng(URI_TID.maybe(), "a spatial location for the lhs obj")
                            .argLst(jnt(0), "the value id for the lhs obj")
                            .args(lst())
                            .desc("specifies the spatial location of the lhs obj")
                            .example("1@abc.vid() [-- abc --]")
                            .body((lhs, inst) -> null == lhs.vid() ? noobj() : uri(lhs.vid()))
                            .create(),
                    InstBuilder.build()
                            .tid(VID_INST_TID)
                            .dom(A, "any obj")
                            .rng(A, "a spatial location for the lhs obj")
                            .argLst(T(URI_TID), "the value id for the lhs obj")
                            .desc("specifies the spatial location of the lhs obj")
                            .example("1@abc.vid() [-- abc --]")
                            .body((lhs, inst) -> lhs.vid(inst.arg(0).uriValue()))
                            .create(),
                    InstBuilder.build()
                            .tid(ELSE_INST_TID)
                            .dom(ALL.maybe(), "maybe an obj")
                            .rng(ALL.maybe(), "the lhs obj else the arg obj")
                            .argLst(T(ALL.maybe()), "the rhs obj is the lhs is noobj")
                            .desc("""
                                    \\[ f(\\mathtt{lhs}) = \\left\\{ \\begin{aligned} \\mathtt{lhs} & \\quad \\text{if } \\mathtt{lhs} \\neq \\emptyset \\\\ \\mathtt{arg}_0 & \\quad \\text{otherwise.} \\end{aligned} \\right. \\]
                                    """)
                            .body((lhs, inst) -> lhs.isNoObj() ? inst.arg(0).apply(lhs) : lhs)
                            .create(),// TODO: rec args needs resolution on generics connected
                    InstBuilder.build()
                            .tid(IS_INST_TID)
                            .dom(A.maybe(), "any obj")
                            .rng(A.maybe(), "the lhs obj if arg is true")
                            .argLst(isa_(T(BOOL_TID)).else_(BOOL_FALSE).tryToInst(), "filter lhs if false")
                            .desc("filters the lhs obj")
                            .body((lhs, inst) -> inst.arg(0).orElse(BOOL_FALSE).boolValue() ? lhs : noobj())
                            .law(Law.partial, Law.pure)
                            .create(), // TODO: generics are not working for some reason
                    InstBuilder.build()
                            .tid(SORTA_INST_TID)
                            .dom(ALL, "an obj to match taxonomically")
                            .rng(ALL.maybe(), "the unaltered obj if arg matches")
                            .argLst(ALL_TYPE, "filter lhs if doesn't match arg")
                            .desc("checks whether the obj is nominally the arg type or a refinement of the arg type")
                            .body((lhs, inst) -> lhs.testNominally(inst.arg(0)) ? lhs : noobj())
                            .create(),
                    InstBuilder.build()
                            .tid(MATCHES_INST_TID)
                            .dom(ALL.maybe(), "any obj")
                            .rng(BOOL_TID, "whether the lhs matches the arg type")
                            .argLst(T(ALL.maybe()), "the type to match the lhs against")
                            .desc("a matching function \\(f(x) \\mapsto b\\): structurally checks whether the lhs obj matches the arg type")
                            .body((lhs, inst) -> bool(lhs.test(inst.arg(0))))
                            .create(),
                    InstBuilder.build()
                            .tid(BLOCK_INST_TID)
                            .dom(A.maybe(), "maybe an obj")
                            .rng(B.some(), "the arg without an applied lhs")
                            .argLst(T(B.some()), "the unapplied rhs")
                            .desc("the lhs obj is halted and the arg is the rhs obj")
                            .body((lhs, inst) -> inst.arg(0))
                            .create(),
                    InstBuilder.build()
                            .tid(SPLIT_INST_TID)
                            .dom(ALL, "any obj")
                            .rng(ALL.maybeSome(), "the branch results as objs")
                            .argLst(T(ALL.some()), "the branches to split into")
                            .desc("a split function \\(f(x) \\mapsto x'\\): the lhs applied through each branch (sugar'd -<[...]>-)")
                            .body((lhs, inst) -> objs(inst.arg(0).stream().map(o -> o.apply(lhs))))
                            .create(),
                    InstBuilder.build()
                            .tid(SPLIT_INST_TID)
                            .dom(ALL, "any obj")
                            .rng(LST_TID, "a lst of the branch results")
                            .argLst(LST_TYPE, "the branches to split into")
                            .desc("a branching function: the lhs applied through each branch, the results as a lst")
                            .body((lhs, inst) -> lst(inst.arg(0).stream().map(o -> o.apply(lhs).c(lhs.c().mult(o.c()))).collect(new CommonUtil.LstCollector())))
                            .create(),
                    InstBuilder.build()
                            .tid(CHOOSE_INST_TID)
                            .dom(ALL, "any obj")
                            .rng(REL_TID.maybe(), "the split as an objs")
                            .argLst(T(REC_TID), "the branches")
                            .desc("a branching function f(x):g(a)->a',g(b)->b',...")
                            .body((lhs, inst) -> inst.arg(0).<Rec>as().elements().map(Obj::<Rel>as).map(e -> e.<Rel>jvm(Tuple.Pair.with(e.first().apply(lhs), e.second()))).filter(e -> !e.first().isNoObj()).findFirst().map(e -> e.<Obj>jvm(Tuple.Pair.with(e.first(), e.second().apply(lhs)))).orElse(noobj()))
                            .create(),
                    /**
                     * instC(MERGE_INST_TID.dom(A.maybeSome()).rng(LST_TID), lst(LST_TYPE), (lhs, inst) ->
                     *                             inst.arg(0).clone(Stream.concat(lhs.stream(), inst.arg(0).elements()).toList(),
                     *                                     inst.arg(0).type().isBaseType() ?
                     *                                             inst.arg(0).tid().poly(Type.Helper.generateLCD(objs(lhs).stream().map(Obj::type).collect(Collectors.toSet()), LST_TID).vid()) :
                     *                                             inst.arg(0).type().vid(),
                     *                                     inst.arg(0).vid())),
                     */
                    InstBuilder.build()
                            .tid(MERGE_INST_TID)
                            .dom(A.maybeSome(), "any objs")
                            .rng(LST_TID, "the lhs objs merged into the arg lst")
                            .argLst(LST_TYPE, "the lst to merge into")
                            .desc("a merging function: prepends the lhs objs to the arg lst (sugar'd >-)")
                            .body((lhs, inst) -> inst.arg(0).jvm(Stream.concat(lhs.stream(), inst.arg(0).elements()).toList()))
                            .create(),
                    InstBuilder.build()
                            .tid(MERGE_INST_TID)
                            .dom(A.maybeSome(), "objs")
                            .rng(ALL_STAR, "the merged objs")
                            .argLst(T(ALL_STAR), "the objs to merge with the lhs")
                            .desc("a merging function: combines the arg objs with the lhs objs (sugar'd >-)")
                            .body((lhs, inst) -> objs(Stream.concat(inst.args().elements(), lhs.elements())))
                            .create(),
                    InstBuilder.build()
                            .tid(MERGE_INST_TID)
                            .dom(A.maybeSome(), "any objs")
                            .rng(A.maybeSome(), "the merged objs")
                            .argLst(T(A.maybeSome()), "the objs to merge with the lhs")
                            .desc("a superposition function \\(f(X) \\mapsto X'\\): merges the lhs objs with the arg objs (sugar'd >-)")
                            .body((lhs, inst) -> objs(Stream.concat(lhs.stream(), inst.arg(0).stream())))
                            .create(),
                    InstBuilder.build()
                            .tid(NOT_INST_TID)
                            .dom(ALL, "any obj")
                            .rng(BOOL_TID, "the negated truth value of the arg")
                            .argLst(T(ALL.maybe()), "the obj to negate")
                            .desc("a negation function \\(f(x) \\mapsto x'\\): true if the arg obj is falsy")
                            .body((lhs, inst) -> bool(!inst.arg(0).booleanCheck()))
                            .create(),
                    InstBuilder.build()
                            .tid(EQ_INST_TID)
                            .dom(A, "any objs")
                            .rng(BOOL_TID, "true if lhs equals rhs")
                            .argLst(T(A), "the rhs obj")
                            .desc("an equality function \\[ f(\\mathtt{lhs}) = \\left\\{ \\begin{aligned} \\mathtt{true} & \\quad \\text{if } \\mathtt{lhs} = \\mathtt{arg}_0 \\\\ \\mathtt{false} & \\quad \\text{otherwise.} \\end{aligned} \\right. \\]")
                            .body((lhs, inst) -> Inst.Helper.alignLHSType(lhs, inst.arg(0)).map(l -> Objects.equals(l, inst.arg(0))).map(MBool::bool).orElse(BOOL_FALSE))
                            .create(),
                    InstBuilder.build()
                            .tid(NEQ_INST_TID)
                            .dom(A, "any objs")
                            .rng(BOOL_TID, "true if lhs does not equal rhs")
                            .argLst(T(A), "the rhs obj")
                            .desc("an inequality function \\[ f(\\mathtt{lhs}) = \\left\\{ \\begin{aligned} \\mathtt{true} & \\quad \\text{if } \\mathtt{lhs} \\neq \\mathtt{arg}_0 \\\\ \\mathtt{false} & \\quad \\text{otherwise.} \\end{aligned} \\right. \\]")
                            .body((lhs, inst) -> Inst.Helper.alignLHSType(lhs, inst.arg(0)).map(l -> !Objects.equals(l, inst.arg(0))).map(MBool::bool).orElse(BOOL_TRUE))
                            .create(),
                    InstBuilder.build()
                            .tid(TO_INST_TID)
                            .dom(A.maybe(), "any obj")
                            .rng(A.maybe(), "writes the lhs obj to the arg uri")
                            .argLst(T(URI_TID), "the uri to write to")
                            .desc("associates the lhs obj to the arg uri")
                            .body((lhs, inst) -> Machine.write(inst.arg(0).uriValue(), lhs))
                            .create(),
                    // instC(FROM_INST_TID.dom(ALL.maybe()).rng(ALL_STAR), lst(), (lhs, inst) -> Router.stack().peekAll()),
                    InstBuilder.build()
                            .tid(REF_INST_TID)
                            .dom(ALL, "a uri reference")
                            .rng(ALL_STAR, "writes the arg obj to the lhs uri")
                            .argLst(T(ALL_STAR), "an obj to be the referent of the uri")
                            .desc("associates the arg obj to the lhs uri (inverse of /m/inst/to)")
                            .body((lhs, inst) -> Machine.write(lhs.uriValue(), inst.arg(0)))
                            .create(),
                    InstBuilder.build()
                            .tid(SOURCE_INST_TID)
                            .dom(A.maybe(), "maybe an obj")
                            .rng(B.maybeSome(), "result of evaluating source code")
                            .argLst(STR_TYPE, "the source code to evaluate")
                            .desc("evaluates source code")
                            .body((lhs, inst) -> {
                                final Str source = inst.arg(0).asStr();
                                final MIME.MIMEType contentType = MIME.MIMEType.fromType(source, MIME.MIMEType.APPLICATION_MTRON);
                                return contentType.exec(source);
                            })
                            .create(),
                    InstBuilder.build()
                            .tid(TYPE_INST_TID)
                            .dom(ALL, "any obj")
                            .rng(ALL, "the lhs obj type")
                            .argLst()
                            .desc("the type of the lhs obj")
                            .example("6.type()-<[tid(),vid()]      [-- [/m/int, /m/int] base types are those where vid==tid --]")
                            .example("nat::6.type()-<[tid(),vid()] [-- [/m/int, nat] non-base types tids are the types they are refining (super type) --]")
                            .body((lhs, inst) -> lhs.isType() ? lhs.asType().parentType() : lhs.type())
                            .create(),
                    InstBuilder.build()
                            .tid(CC_INST_TID)
                            .dom(A.maybeSome(), "any obj")
                            .rng(INT_TID, "the lhs obj coefficient")
                            .argLst()
                            .desc("maps an obj to it's coefficient with a function f(lhs^c)->c")
                            .body((lhs, inst) -> jnt(lhs.c().max()))
                            .create(),
                    InstBuilder.build()
                            .tid(CC_INST_TID)
                            .dom(A, "any obj")
                            .rng(A.maybeSome(), "the lhs obj with new coefficient")
                            .argLst(T(INT_TID), "a coefficient for lhs obj")
                            .desc("sets the coefficient of the lhs obj via f(lhs,c)->lhs^c")
                            .body((lhs, inst) -> lhs.c(inst.arg(0).intValue()))
                            .create(),
                    InstBuilder.build()
                            .tid(THROW_INST_TID)
                            .dom(ALL.maybeSome(), "any obj")
                            .rng(FAIL_TID, "a fail::T carrying the arg message")
                            .argLst(T(ALL.maybe()), "the failure message")
                            .desc("a throwing function \\(f(x) \\mapsto \\bot\\): fails with the arg message")
                            .body((lhs, inst) -> fail(MTronException.of("%s", inst.arg(0).isStr()
                                    // A STRING'S MESSAGE IS ITS TEXT, NOT ITS SPELLING: toString() of a str is its mtron literal
                                    // ('bad'), and a fail's message is written as a str on the wire — so the quoted spelling would
                                    // be quoted again (fail::["'bad'"]) and the reader would see quotes that belong to the notation.
                                    ? inst.arg(0).strValue()
                                    : inst.arg(0).toString())))
                            .create(),
                    InstBuilder.build()
                            .tid(PARENT_INST_TID)
                            .dom(ALL, "any obj")
                            .rng(ALL.maybe(), "the parent of the lhs obj")
                            .argLst()
                            .desc("a parent function \\(f(x) \\mapsto x'\\): the parent link of the lhs obj")
                            .body((lhs, inst) -> lhs.parent())
                            .create(),
                    InstBuilder.build()
                            .tid(COUNT_INST_TID)
                            .dom(A.maybeSome(), "any objs")
                            .rng(INT_TID, "the count of objs")
                            .argLst()
                            .desc("counts the number of objs")
                            .seed(jnt(0))
                            .body((lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> jnt(a.intValue() + b.c().max())).intValue()/* * inst.c().max()*/))
                            .law(Law.pure)
                            .derivation(new Derivation(start_(uri(A)).count_(), start_(uri(A)).map_(jnt(1)).sum_()))
                            .create(),
                    InstBuilder.build()
                            .tid(SKIP_INST_TID)
                            .dom(ALL.maybeSome(), "any objs")
                            .rng(ALL.maybeSome(), "the objs after skipping")
                            .argLst(INT_TYPE, "the number of objs to skip")
                            .desc("skips the first n objs")
                            .body((lhs, inst) -> lhs.take(cInt.of(inst.arg(0).intValue())).get1()) // tail
                            .create(),
                    InstBuilder.build()
                            .tid(TAKE_INST_TID)
                            .dom(ALL.maybeSome(), "any objs")
                            .rng(ALL.maybeSome(), "the objs before skipping")
                            .argLst(INT_TYPE, "the number of objs to take")
                            .desc("takes the first n objs")
                            .body((lhs, inst) -> lhs.take(cInt.of(inst.arg(0).intValue())).get0()) // head
                            .create(),
                    // Mutation box: detach the anchor (no auto-write during compute),
                    // compute in-memory (IMMUTABLE), then atomically write the result.
                    InstBuilder.build()
                            .tid(UPDATE_INST_TID)
                            .dom(A)
                            .rng(B.maybeSome())
                            .argLst(T(B.maybeSome()))
                            .desc("selectively mutate a poly component or an entire mono")
                            .example("@a >>= [b=>[c=>2]]      [-- [b=>[c=>2]]@a     --]")
                            .example("@a >>= [b=>[c=>+3]]     [-- [b=>[c=>5]]@a     --]")
                            .example("@a >>= [b=>+[c=>4]]     [-- [b=>[c=>{5,4}]]@a --]")
                            .example("@a >>= [b=>[c=>sum()]]  [-- [b=>[c=>9]]@a     --]")
                            .body((lhs, inst) -> {
                                if (lhs.hasVID() && !inst.arg(0).isPoly() && !inst.arg(0).isCall())
                                    return Machine.write(lhs.vid(), inst.arg(0));
                                final Obj detached = lhs.clone().selfVID(null);
                                final Obj result = Poly.Helper.updateRecursion(detached, inst.arg(0), IMMUTABLE);
                                if (lhs.hasVID())
                                    Machine.write(lhs.vid(), result);
                                return result;//.selfVID(lhs.vid());
                            })
                            .create(),
                    InstBuilder.build()
                            .tid(EXPLAIN_INST_TID)
                            .dom(ALL.maybe(), "any obj")
                            .rng(EXPLANATION_TID, "an explanation::T analysis of the expression")
                            .argLst()
                            .desc("a structured explanation of the expression (an explanation::T with desc/per_inst/format)")
                            .body((lhs, inst) -> {
                                // explain_rewrite handles normal case; bare explain() is a no-op
                                return lhs;
                            })
                            .create(),
                    InstBuilder.build()
                            .tid(REIFY_INST_TID)
                            .dom(A, "any obj")
                            .rng(REC_TID, "a rec describing the lhs obj structure")
                            .argLst()
                            .desc("a reify function \\(f(x) \\mapsto x'\\): describes the lhs obj as a rec of type/vid/jvm components")
                            .body((lhs, inst) -> rec(
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
                                                                    rec(jnt(0), MObjFactory.of().toObj(lhs.jvm())))))))
                            .create(),
                    InstBuilder.build()
                            .tid(REDUCE_INST_TID)
                            .dom(A.maybeSome(), "any objs")
                            .rng(A, "the result of applying the arg inst to each obj")
                            .argLst(T(ALL.maybe()), "the inst to apply to each obj")
                            .desc("a reduce function \\(f(X) \\mapsto x\\)")
                            .body((lhs, inst) -> Stream.concat(inst.arg(0).<Inst>as().arg(0).stream(), lhs.stream()).reduce((a, b) -> inst.arg(0).<Inst>as().args(lst(a)).apply(b)).orElse(noobj()))
                            .create(),
                    InstBuilder.build()
                            .tid(GROUP_INST_TID)
                            .dom(A.maybeSome(), "any objs")
                            .rng(REC_TID, "a rec of the grouped results")
                            .argLst(T(REC_TID), "the grouping key rec, the value inst computing each group")
                            .desc("a grouping function \\(f(X) \\mapsto x'\\): groups the objs by the key rec and computes each group (sugar'd %==)")
                            .body((lhs, inst) -> {
                                final Map<Obj, Obj> result = new LinkedHashMap<>();
                                lhs.stream().forEach(e -> inst.arg(0).asRec().elements().forEach(kv -> {
                                    final Obj kk = kv.first().isObjCall() || kv.first().isInst()
                                            ? kv.first().apply(e)
                                            : (e.isRec() ? e.asRec().at(kv.first()) : e);
                                    if (!kk.isNoObj()) // TODO: if the group value is not a barrier, then process immediately.
                                        // a key is an identity, not a multiplicity: a bulked member's coefficient is counted by
                                        // the group it joins ({2}x keys as x), never carried on the key
                                        result.compute(kk.c(cInt::one), (k, v) -> (v == null) ? lst(kv.second(), e) : v.asLst().at(jnt(1), v.asLst().at(jnt(1)).append(e), MUTABLE));
                                }));
                                return result.entrySet().stream()
                                        .map(kv -> rel(
                                                kv.getKey(),  // key
                                                kv.getValue().asLst().at(0).apply(kv.getValue().asLst().at(jnt(1)))))  // compute barriered value
                                        .collect(new CommonUtil.RecCollector());
                            })
                            .law(Law.pure)
                            .create(),
                    InstBuilder.build()
                            .tid(EVAL_INST_TID)
                            .dom(CODE_TID, "code")
                            .rng(A.maybeSome(), "the value of evaluating the lhs code, optionally at the arg uri")
                            .argLst((Obj) URI_TYPE.maybe(), "the uri to evaluate at (optional)")
                            .desc("evaluates the lhs code, optionally at the arg uri")
                            .body((lhs, inst) -> lhs.apply(noobj()))
                            .create(),
                    InstBuilder.build()
                            .tid(PARSE_INST_TID)
                            .dom(ALL.maybe(), "can be any obj")
                            .rng(ALL.maybeSome(), "the result of parsing arg into obj")
                            .argLst(STR_TYPE, "a str encoding of an obj")
                            .desc("parses the arg str based on str mime type (default application/mtron)")
                            .example("""
                                    parse(application/json::'{"a":[1,2,3]}') [-- [a=>[1,2,3]] --]
                                    """)
                            .example("""
                                    parse('[a=>[b=>c]]')                     [-- [a=>[b=>c]]  --]
                                    """)
                            .body((lhs, inst) -> MIME.MIMEType.of(inst.arg(0).tid().toString(), MIME.MIMEType.APPLICATION_MTRON).serializer().inputBytes(inst.arg(0).strValue()))
                            .create(),
                    //  "|-<[_,_].swap(||mult(_))  [-- [mult(_),mult(_)] --]"
                    InstBuilder.build()
                            .tid(RSHIFT_INST_TID)
                            .dom(A, "any obj")
                            .rng(B.maybeSome(), "the obj descended into from the lhs")
                            .argLst(T(C.maybeSome()), "the depth or path to descend by (default 1)")
                            .desc("a shift function \\(f(x) \\mapsto x'\\): descends into the lhs obj (sugar'd >>)")
                            .body((lhs, inst) -> {
                                // DATETIME_TYPE is registry-driven (set during math instset setup)
                                // and is null in a VM where the math set is not registered
                                if (null != DATETIME_TYPE && lhs.isUri() && lhs.asUri().testNominally(DATETIME_TYPE))
                                    return datetimeRshift(lhs.asUri(), inst.arg(0));
                                else if (lhs.isRec())
                                    return Rec.Helper.rshiftRec(lhs.asRec(), inst.arg(0));
                                else if (lhs.isLst())
                                    return Lst.Helper.rshiftLst(lhs.asLst(), inst.arg(0));
                                else if (lhs.isUri())
                                    return Uri.Helper.rshiftUri(lhs.asUri(), inst.arg(0));
                                else if (lhs.isRel())
                                    return Rel.Helper.rshiftRel(lhs.asRel(), inst.arg(0));
                                else if (lhs.isObjs())
                                    return objs(lhs.asObjs().stream().flatMap(o -> inst.apply(o).stream()));
                                else return noobj();
                            })
                            .create(),
                    InstBuilder.build()
                            .tid(LSHIFT_INST_TID)
                            .dom(A, "any obj")
                            .rng(B.maybeSome(), "the obj ascended to from the lhs")
                            .argLst(T(C.maybeSome()), "the depth or path to ascend by (default 1)")
                            .desc("a shift function \\(f(x) \\mapsto x'\\): ascends out of the lhs obj (sugar'd <<)")
                            .body((lhs, inst) -> {
                                final Obj arg = inst.arg(0);
                                if (lhs.isUri()) {
                                    // uri << — pure uri arithmetic, no obj parent link: the uri
                                    // carries its own parent.  a/b/c << => a/b, a/b/c << 2 => a,
                                    // a/b/c/d << c/d => a/b (retract a matching postfix).
                                    final fURI u = lhs.uriValue();
                                    if (arg.isNoObj())
                                        return uri(u.retract(1));
                                    if (arg.isInt())
                                        return uri(u.retract(arg.intValue().intValue()));
                                    if (arg.isUri()) {
                                        final fURI postfix = arg.uriValue();
                                        return u.hasPostfix(postfix.toString())
                                                ? uri(u.retract(postfix.segmentLength())) : noobj();
                                    }
                                    return noobj();
                                }
                                // every other obj ascends its parent link, so << never descends: a rec's keys and a
                                // rel's first are its dom(). A ..-led path ascends, then walks down to a sibling
                                if (arg.isNoObj())
                                    return lhs.parent();
                                if (arg.isUri() && arg.uriValue().segments(0, "").equals(".."))
                                    return arg.uriValue().segmentLength() > 1
                                            ? auto_(map_(lhs.parent()).rshift_(arg.uriValue().pretract(1).toUri())).tryToInst()
                                            : lhs.parent();
                                return noobj();
                            })
                            .create()));
        }
    }

    private static Obj datetimeRshift(final Uri lhs, final Obj arg) {
        if (arg.isNoObj())
            return objs(jnt(Integer.parseInt(lhs.uriValue().host().split("\\.")[0])), // year
                    jnt(Integer.parseInt(lhs.uriValue().host().split("\\.")[1])),      // month
                    jnt(lhs.uriValue().port()),                                        // day
                    jnt(Integer.parseInt(lhs.uriValue().path().get(lhs.uriValue().path().size() - 4))),  // hour
                    jnt(Integer.parseInt(lhs.uriValue().path().get(lhs.uriValue().path().size() - 3))),  // minute
                    jnt(Integer.parseInt(lhs.uriValue().path().get(lhs.uriValue().path().size() - 2))),  // second
                    jnt(Integer.parseInt(lhs.uriValue().path().get(lhs.uriValue().path().size() - 1))),  // millis
                    str(lhs.uriValue().qMap().get("tz")));                              // tz
        return objs(arg.stream().map(k -> {
            final String key = k.asUri().uriValue().toString();
            return switch (key) {
                case "year" -> jnt(Integer.parseInt(lhs.uriValue().host().split("\\.")[0]));
                case "month" -> jnt(Integer.parseInt(lhs.uriValue().host().split("\\.")[1]));
                case "day" -> jnt(lhs.uriValue().port());
                case "hour" -> jnt(Integer.parseInt(lhs.uriValue().path().get(lhs.uriValue().path().size() - 4)));
                case "minute" -> jnt(Integer.parseInt(lhs.uriValue().path().get(lhs.uriValue().path().size() - 3)));
                case "second" -> jnt(Integer.parseInt(lhs.uriValue().path().get(lhs.uriValue().path().size() - 2)));
                case "millis" -> jnt(Integer.parseInt(lhs.uriValue().path().get(lhs.uriValue().path().size() - 1)));
                case "tz" -> str(lhs.uriValue().qMap().get("tz"));
                default -> Uri.Helper.rshiftUri(lhs, lst(k)).elements().findFirst().orElse(noobj());
            };
        }));
    }

}