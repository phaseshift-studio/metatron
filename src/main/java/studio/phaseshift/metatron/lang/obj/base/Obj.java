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

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Iterator;
import java.util.function.Function;

interface Obj extends Function<Obj, Obj>, Iterable<Obj>, Cloneable {
    Object value();

    fURI tid();

    fURI vid();

    Obj vid(final fURI furi);

    Obj clone();

    /*default String toString(final Palette palette) {
        Graphitty.global().write(this);
    }*/

    // @Override
    default Obj apply(final Obj other) {
        return this;
    }


    default boolean matches(final Obj rhs) {
        return this.equals(rhs);
    }

    <O extends Obj> O clone(final Object value);

    /*@Override
    default Iterator<Obj> iterator() {
        return this.isObjs() ? ((Iterable<Obj>) this.value()).iterator() : IteratorUtil.of(this);
    }

    default <O extends Obj> O orElse(final O other) {
        return this.isNoObj() ? other : (O) this;
    }

    default <O extends Obj> O orElseThrow(final RuntimeException e) {
      //  if (this.isNoObj())
       //     throw e;
        return (O) this;
    }*/

    default <O extends Obj> O as() {
        return (O) this;
    }
/*
    default boolean isNoObj() {
        return null == this.value();
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

    default boolean isMono() {
        return this instanceof Mono;
    }



    default boolean boolValue() {
        if (this.isBool())
            return ((Bool) this).value();
        throw new IllegalStateException("obj is not an bool");
    }

    default int intValue() {
        if (this.isInt())
            return ((Int) this).value();
        throw new IllegalStateException("obj is not an int");
    }

    default double realValue() {
        if (this.isReal())
            return ((Real) this).value();
        throw new IllegalStateException("obj is not an real");
    }

    default String strValue() {
        if (this.isStr())
            return ((Str) this).value();
        throw new IllegalStateException("obj is not an str");
    }

    default fURI uriValue() {
        if (this.isUri())
            return ((Uri) this).value();
        throw new IllegalStateException("obj is not an uri");
    }

    default List<Obj> lstValue() {
        if (this.isLst())
            return ((Lst) this).value();
        throw new IllegalStateException("obj is not an lst");
    }

    default Map<Obj, Obj> recValue() {
        if (this.isRec())
            return ((Rec) this).value();
        throw new IllegalStateException("obj is not an rec");
    }

    default Pair<Obj, Obj> relValue() {
        if (this.isRel())
            return ((Rel) this).value();
        throw new IllegalStateException("obj is not an rel");
    }

 */
}
