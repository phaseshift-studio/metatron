/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.obj;

import org.javatuples.Pair;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.mtron.MObjs;
import studio.phaseshift.metatron.lang.obj.mtron.MType;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.*;

public interface Obj extends Function<Obj, Obj>, Iterable<Obj> {

    default String simpeToString() {
        return Graphitty.string("{{b}}%s{{g}}::{{m}}@{{b}}%s{{/b}}", this.tid().toString(), null == this.vid() ? "<nospace>" : this.vid().toString());
    }

    <O extends Object> O value();

    fURI tid();

    fURI vid();

    default Obj resolve(final Obj lhs) {
        return this;
    }

    default fURI vidOrTid() {
        return this.vid() == null ? this.tid() : this.vid();
    }

    default Type type() {
        return MType.of(this, this.tid());
    }

    default GraphittyLogger logger() {
        return Graphitty.log(this);
    }

    <O extends Obj> O clone(final Object value, final fURI tid, final fURI vid);

    default <O extends Obj> O value(final Object newValue) {
        return this.clone(newValue, this.tid(), this.vid());
    }

    default <O> O valueAs() {
        return this.value();
    }

    default <O extends Obj> Stream<O> stream() {
        return this.isNoObj() ? Stream.empty() : (Stream<O>) IteratorUtil.stream(this);
    }

    default Obj tid(final fURI newTid) {
        return this.clone(this.value(), newTid, this.vid());
    }

    default Obj tid(final String newTid) {
        return this.clone(this.value(), fURI.of(newTid), this.vid());
    }

    default Obj vid(final fURI newVid) {
        return this.clone(this.value(), this.tid(), newVid);
    }

    default boolean inSpace() {
        return null != this.vid();
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
            return MObjs.of(objs);
        }
    }

    @Override
    default Obj apply(final Obj other) {
        return this;
    }

    default Obj apply() {
        return this.apply(NoObj.single());
    }

    default boolean matches(final Obj rhs) {
        if (this.isNoObj() && rhs.isNoObj())
            return true;
        else if (this.isNoObj() && rhs.tid().coefficientValue().isNoObjable())
            return true;
        else if (rhs.isNoObj())
            return false;
        final fURI base = this.tid().basePath();
        if (BASE_TYPES.contains(base) &&
                !(this instanceof Objs) &&
                !(this instanceof Type) &&
                !(this instanceof Bool && base.equals(BOOL_TID) ||
                        this instanceof Int && base.equals(INT_TID) ||
                        this instanceof Real && base.equals(REAL_TID) ||
                        this instanceof Str && base.equals(STR_TID) ||
                        this instanceof Uri && base.equals(URI_TID) ||
                        this instanceof Rec && base.equals(REC_TID) ||
                        this instanceof Lst && base.equals(LST_TID) ||
                        this instanceof Rel && base.equals(REL_TID) ||
                        this instanceof Inst && base.equals(INST_TID) ||
                        this instanceof Code && base.equals(CODE_TID))) {
            return false;
        }
        if (this.isCall()) {
            //return true;
            return this.tid().coefficientValue().within(rhs.tid().coefficientValue()); // TODO: this is really flimsy.
        }
        if (rhs.isCall())
            return this.rng().matches(rhs.dom());// && rhs.apply(this).matches(rhs.rng());
        if (rhs.isType())
            return this.tid().matches(rhs.tid()) && (rhs.value() == null || this.matches(rhs.<Type>as().value()));
        return this.tid().matches(rhs.tid()) &&
                Objects.equals(this.value(), rhs.value());
    }

    @Override
    default Iterator<Obj> iterator() {
        return this.isNoObj() ? IteratorUtil.of() : (this.isObjs() ? this.objsValue().iterator() : IteratorUtil.of(this));
    }

    default Type dom() {
        return T(fURI.ANY.maybe());
    }

    default Type rng() {
        return T(this.tid());
    }

    default <O extends Obj> O orElse(final O other) {
        return this.isNoObj() ? other : (O) this;
    }

    default <O extends Obj> O orElseThrow(final RuntimeException e) {
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

    default boolean isNoObj() {
        return this == NoObj.single() || this.tid().basePath().equals(fURI.NONE) || this.tid().coefficientValue().isZero(); // TODO: consolidate the logic
    }

    default boolean isBool() {
        return this instanceof Bool;
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

    default boolean boolValue() {
        if (this.isBool())
            return this.value();
        throw MTronException.of("%s is a %s, not a %s", this, tid().toUri(), BOOL_TID.toUri());
    }

    default Long intValue() {
        if (this.isInt())
            return this.value();
        throw MTronException.of("%s is a %s, not an %s", this, tid().toUri(), INT_TID.toUri());
    }

    default Double realValue() {
        if (this.isReal())
            return this.value();
        throw MTronException.of("%s is a %s, not a %s", this, tid().toUri(), REAL_TID.toUri());
    }

    default String strValue() {
        if (this.isStr())
            return this.value();
        throw MTronException.of("%s is a %s, not a %s", this, tid().toUri(), STR_TID.toUri());
    }

    default fURI uriValue() {
        if (this.isUri())
            return this.value();
        throw MTronException.of("%s is a %s, not a %s", this, tid().toUri(), URI_TID.toUri());
    }

    default List<Obj> lstValue() {
        if (this.isLst())
            return this.value();
        throw MTronException.of("%s is a %s, not a %s", this, tid().toUri(), LST_TID.toUri());
    }

    default Iterable<Obj> objsValue() {
        if (this.isObjs())
            return this.value();
        throw MTronException.of("%s is a %s, not an %s", this, tid().toUri(), OBJS_TID.toUri());
    }

    default Map<Obj, Obj> recValue() {
        if (this.isRec())
            return this.value();
        throw MTronException.of("%s is a %s, not a %s", this, tid().toUri(), REC_TID.toUri());
    }

    default Pair<Obj, Obj> relValue() {
        if (this.isRel())
            return this.value();
        throw MTronException.of("%s is a %s, not a %s", this, tid().toUri(), REL_TID.toUri());
    }

    default List<Inst> codeValue() {
        if (this.isCode())
            return this.value();
        throw MTronException.of("%s is a %s, not a %s", this, tid().toUri(), CODE_TID.toUri());
    }

    default Obj typeValue() {
        if (this.isType())
            return this.value();
        throw MTronException.of("%s is a %s, not a %s", this, tid().toUri(), fURI.of("<type>").toUri());
    }
}