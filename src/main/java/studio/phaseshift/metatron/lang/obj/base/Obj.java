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

package studio.phaseshift.metatron.lang.obj.base;

import org.javatuples.Pair;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface Obj extends Function<Obj, Obj>, Iterable<Obj> {

    public static final fURI TID = fURI.of("obj");

    <O extends Object> O value();

    fURI tid();

    fURI vid();

    <O extends Obj> O clone(final Object value, final fURI tid, final fURI vid);

    default Obj value(final Object newValue) {
        return this.clone(newValue, this.tid(), this.vid());
    }

    default Obj tid(final fURI newTid) {
        return this.clone(this.value(), newTid, this.vid());
    }

    default Obj vid(final fURI newVid) {
        return this.clone(this.value(), this.tid(), newVid);
    }

    @Override
    default Obj apply(final Obj other) {
        return this;
    }

    default boolean matches(final Obj rhs) {
        return this.equals(rhs);
    }

    @Override
    default Iterator<Obj> iterator() {
        return this.isNoObj() ? IteratorUtil.of() : (this.isObjs() ? this.objsValue().iterator() : IteratorUtil.of(this));
    }

    default <O extends Obj> O orElse(final O other) {
        return this.isNoObj() ? other : (O) this;
    }

    default <O extends Obj> O orElseThrow(final RuntimeException e) {
        if (this.isNoObj())
            throw e;
        return (O) this;
    }

    default <O extends Obj> O as() {
        return (O) this;
    }

    default boolean isNoObj() {
        return this == NoObj.single();
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

    default boolean boolValue() {
        if (this.isBool())
            return this.value();
        throw MTronException.of("%s is a %s is not a %s", this, tid().toUri(), Bool.TID.toUri());
    }

    default Long intValue() {
        if (this.isInt())
            return this.value();
        throw MTronException.of("%s is a %s is not an %s", this, tid().toUri(), Int.TID.toUri());
    }

    default Double realValue() {
        if (this.isReal())
            return this.value();
        throw MTronException.of("%s is a %s is not a %s", this, tid().toUri(), Real.TID.toUri());
    }

    default String strValue() {
        if (this.isStr())
            return this.value();
        throw MTronException.of("%s is a %s is not a %s", this, tid().toUri(), Str.TID.toUri());
    }

    default fURI uriValue() {
        if (this.isUri())
            return this.value();
        throw MTronException.of("%s is a %s is not a %s", this, tid().toUri(), Uri.TID.toUri());
    }

    default List<Obj> lstValue() {
        if (this.isLst())
            return this.value();
        throw MTronException.of("%s is a %s is not a %s", this, tid().toUri(), Lst.TID.toUri());
    }

    default Iterable<Obj> objsValue() {
        if (this.isObjs())
            return this.value();
        throw MTronException.of("%s is a %s is not an %s", this, tid().toUri(), Objs.TID.toUri());
    }

    default Map<Obj, Obj> recValue() {
        if (this.isRec())
            return this.value();
        throw MTronException.of("%s is a %s is not a %s", this, tid().toUri(), Rec.TID.toUri());
    }

    default Pair<Obj, Obj> relValue() {
        if (this.isRel())
            return this.value();
        throw MTronException.of("%s is a %s is not a %s", this, tid().toUri(), Rel.TID.toUri());
    }

    default List<Inst> codeValue() {
        if (this.isCode())
            return this.value();
        throw MTronException.of("%s is a %s is not a %s", this, tid().toUri(), Code.TID.toUri());
    }
}