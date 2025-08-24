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

package studio.phaseshift.metatron.lang;

import org.javatuples.Quartet;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class S implements Base {

    public static class Obj implements Base.Obj {
        final Object value;

        public Obj(final Object value) {
            this.value = value;
        }

        public Object value() {
            return this.value;
        }

        @Override
        public boolean equals(final Object other) {
            return (null == this.value && null == Obj.of(other).value) || Obj.of(other).value().equals(this.value);
        }

        @Override
        public int hashCode() {
            return null == this.value ? -13435467 : this.value.hashCode();
        }

        public static Obj of(final Object value) {
            if (value instanceof Obj)
                return (Obj) value;
            else if (null == value)
                return S.NoObj.of();
            else if (value instanceof Boolean)
                return new Bool((Boolean) value);
            else if (value instanceof Integer)
                return new Int((Integer) value);
            else if (value instanceof Double)
                return new Real((Double) value);
            else if (value instanceof String)
                return new Str((String) value);
            else if (value instanceof URI)
                return new Uri((URI) value);
            else if (value instanceof List)
                return new Lst((List) value);
            else if (value instanceof Map)
                return new Rec((Map) value);
            else if (value instanceof Quartet<?, ?, ?, ?>)
                return new Inst((Quartet<Base.Uri, Base.Lst, BiFunction<Base.Obj, Base.Lst, Base.Obj>, Base.Obj>) value);
            else
                throw new RuntimeException("unknown object type: " + value.toString());
        }
    }


    final public static class NoObj extends S.Obj {
        private static final S.NoObj NOOBJ = new S.NoObj();

        private NoObj() {
            super(null);
        }

        public static NoObj of() {
            return NOOBJ;
        }
    }

    public static class Bool extends Obj implements Base.Bool {

        public Bool(final Boolean value) {
            super(value);
        }

        public Boolean value() {
            return (Boolean) this.value;
        }

        public static Bool of(final boolean bool) {
            return new Bool(bool);
        }

    }

    public static class Int extends Obj implements Base.Int {
        public Int(final Integer value) {
            super(value);
        }

        public Integer value() {
            return (Integer) this.value;
        }

        public static Int of(final int i) {
            return new Int(i);
        }
    }

    public static class Real extends Obj implements Base.Real {

        public Real(final Double value) {
            super(value);
        }

        public Double value() {
            return (Double) this.value;
        }

    }

    public static class Str extends Obj implements Base.Str {

        public Str(final String value) {
            super(value);
        }

        public String value() {
            return (String) this.value;
        }

    }

    public static class Uri extends Obj implements Base.Uri {

        public Uri(final URI value) {
            super(value);
        }

        public URI value() {
            return (URI) this.value;
        }

        public static Base.Uri of(final String uri) {
            return new Uri(URI.create(uri));
        }
    }

    public static class Lst extends Obj implements Base.Lst {

        public Lst(final List<Base.Obj> value) {
            super(value);
        }

        @Override
        public List<Base.Obj> value() {
            return (List<Base.Obj>) this.value;
        }

        @Override
        public Lst apply(final Base.Obj other) {
            List<Base.Obj> list = new ArrayList<>();
            for (int i = 0; i < this.value().size(); i++) {
                list.set(i, this.value().get(i).apply(other));
            }
            return new S.Lst(list);
        }

        public static Base.Lst of() {
            return new Lst(List.of());
        }

        public static Base.Lst single(final Object arg0) {
            return new Lst(List.of(Obj.of(arg0)));
        }

        public static Base.Lst of(final Object arg0, final Object... args) {
            List<Base.Obj> list = new ArrayList<>();
            list.add(Obj.of(arg0));
            for (final Object arg : args) {
                list.add(Obj.of(arg));
            }
            return new Lst(list);
        }

    }

    public static class Rec extends Obj implements Base.Rec {

        public Rec(final Map<Base.Obj, Base.Obj> value) {
            super(value);
        }

        @Override
        public Map<Base.Obj, Base.Obj> value() {
            return (Map<Base.Obj, Base.Obj>) this.value;
        }

        @Override
        public Rec apply(final Base.Obj other) {
            Map<Base.Obj, Base.Obj> map = new HashMap<>();
            return new S.Rec(map);
        }

    }

    public static class Inst extends Obj implements Base.Inst {

        public Inst(final Quartet<Base.Uri, Base.Lst, BiFunction<Base.Obj, Base.Lst, Base.Obj>, Base.Obj> value) {
            super(value);
        }

        public Inst(final Base.Uri opcode, final Base.Lst args, BiFunction<Base.Obj, Base.Lst, Base.Obj> func, final Base.Obj seed) {
            this(new Quartet<>(opcode, args, func, seed));
        }

        @Override
        public Quartet<Base.Uri, Base.Lst, BiFunction<Base.Obj, Base.Lst, Base.Obj>, Base.Obj> value() {
            return (Quartet<Base.Uri, Base.Lst, BiFunction<Base.Obj, Base.Lst, Base.Obj>, Base.Obj>) this.value;
        }

        @Override
        public S.Obj apply(final Base.Obj other) {
            List<Base.Obj> computedArgs = new ArrayList<>(this.value().getValue1().value().size());
            for (final Base.Obj arg : this.value().getValue1().value()) {
                computedArgs.add(arg.apply(other));
            }
            return Obj.of(this.value().getValue2().apply(other, new Lst(computedArgs)));
        }
    }
}
