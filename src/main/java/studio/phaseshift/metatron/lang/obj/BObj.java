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

import org.javatuples.Quartet;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface BObj {

    interface Obj extends Function<Obj, Obj> {
        Object value();

        @Override
        default Obj apply(final Obj other) {
            return this;
        }

        default <O extends Obj> O as() {
            return (O) this;
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

        default boolean isLst() {
            return this instanceof Lst;
        }

        default boolean isRec() {
            return this instanceof Rec;
        }

        default boolean isInst() {
            return this instanceof Inst;
        }

        default boolean boolValue() {
            if (this.isBool())
                return ((SObj.Bool) this).value();
            throw new IllegalStateException("obj is not an bool");
        }

        default int intValue() {
            if (this.isInt())
                return ((SObj.Int) this).value();
            throw new IllegalStateException("obj is not an int");
        }

        default double realValue() {
            if (this.isReal())
                return ((SObj.Real) this).value();
            throw new IllegalStateException("obj is not an real");
        }

        default String strValue() {
            if (this.isStr())
                return ((SObj.Str) this).value();
            throw new IllegalStateException("obj is not an str");
        }
    }


    final class NoObj implements Obj {
        private final static NoObj NOOBJ = new NoObj();

        private NoObj() {
        }

        @Override
        public Object value() {
            return null;
        }

        @Override
        public Obj apply(final Obj other) {
            return other;
        }

        public static NoObj of() {
            return NOOBJ;
        }
    }

    interface Mono extends Obj {
    }

    interface Poly extends Obj {
    }

    interface Bool extends Mono {
        @Override
        Boolean value();

        @Override
        default Bool apply(final Obj other) {
            return this;
        }
    }

    interface Int extends Mono {
        @Override
        Integer value();

        @Override
        default Int apply(final Obj other) {
            return this;
        }
    }

    interface Real extends Mono {
        @Override
        Double value();

        @Override
        default Real apply(final Obj other) {
            return this;
        }
    }

    interface Str extends Mono {
        @Override
        String value();

        @Override
        default Str apply(final Obj other) {
            return this;
        }
    }

    interface Uri extends Mono {
        @Override
        URI value();

        @Override
        default Uri apply(final Obj other) {
            return this;
        }
    }

    interface Lst extends Poly {
        @Override
        List<Obj> value();

        @Override
        Lst apply(final Obj other);
    }

    interface Rec extends Poly {
        @Override
        Map<Obj, Obj> value();

        @Override
        Rec apply(final Obj other);
    }

    interface Inst extends Poly {
        @Override
        Quartet<Uri, Lst, BiFunction<Obj, Lst, Obj>, Obj> value();

        @Override
        default Obj apply(final Obj other) {
            final int size = this.value().getValue1().value().size();
            final List<Obj> args = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                args.set(i, this.value().getValue1().value().get(i).apply(other));
            }
            return this.value().getValue2().apply(other, this.value().getValue1());
        }

    }
}
