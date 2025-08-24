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

import org.javatuples.*;

import java.net.*;
import java.util.*;
import java.util.function.*;

public class SObj implements BObj {

    public static class Obj implements BObj.Obj {
        final Object value;
        final URI type;

        public Obj(final Object value, final URI type) {
            if (value instanceof Obj)
                throw new IllegalArgumentException("an obj can not have an obj as a base value");
            this.value = value;
            this.type = type;
        }

        @Override
        public URI type() {
            return this.type;
        }

        @Override
        public Object value() {
            return this.value;
        }

        @Override
        public boolean equals(final Object other) {
            if (this.isNoObj())
                return null == other || Obj.of(other).isNoObj();
            if (null == other)
                return false;
            return Obj.of(other).value.equals(this.value) && Obj.of(other).type.equals(this.type);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(this.value) + Objects.hashCode(this.type);
        }

        @Override
        public String toString() {
            return null == this.value ? "noobj" : this.value.toString();
        }

        public static Obj of(final Object value) {
            if (value instanceof Obj)
                return (Obj) value;
            else if (null == value)
                return NoObj.of();
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
                return new Inst((Quartet<BObj.Uri, BObj.Lst, BiFunction<BObj.Obj, BObj.Lst, BObj.Obj>, BObj.Obj>) value);
            else
                throw new RuntimeException("unknown object type: " + value.toString());
        }
    }


    final public static class NoObj extends Obj {
        private static final NoObj NOOBJ = new NoObj();

        private NoObj() {
            super(null, NOOBJ_URI);
        }

        public static NoObj of() {
            return NOOBJ;
        }
    }

    public static class Bool extends Obj implements BObj.Bool {

        public Bool(final Boolean value) {
            super(value, BOOL_URI);
        }

        public Bool(final Boolean value, final URI type) {
            super(value, type);
        }

        public Boolean value() {
            return (Boolean) this.value;
        }

        public static Bool of(final boolean bool) {
            return new Bool(bool);
        }
    }

    public static class Int extends Obj implements BObj.Int {
        public Int(final Integer value) {
            super(value, INT_URI);
        }

        public Int(final Integer value, final URI type) {
            super(value, type);
        }

        public Integer value() {
            return (Integer) this.value;
        }

        public static Int of(final int i) {
            return new Int(i);
        }
    }

    public static class Real extends Obj implements BObj.Real {

        public Real(final Double value) {
            super(value, REAL_URI);
        }

        public Double value() {
            return (Double) this.value;
        }

    }

    public static class Str extends Obj implements BObj.Str {

        public Str(final String value) {
            super(value, STR_URI);
        }

        public String value() {
            return (String) this.value;
        }

    }

    public static class Uri extends Obj implements BObj.Uri {

        public Uri(final URI value) {
            super(value, URI_URI);
        }

        public URI value() {
            return (URI) this.value;
        }

        public static BObj.Uri of(final String uri) {
            return new Uri(URI.create(uri));
        }
    }

    public static class Lst extends Obj implements BObj.Lst {

        public Lst(final List<BObj.Obj> value) {
            super(value, LST_URI);
        }

        @Override
        public List<BObj.Obj> value() {
            return (List<BObj.Obj>) this.value;
        }

        @Override
        public Lst apply(final BObj.Obj other) {
            List<BObj.Obj> list = new ArrayList<>();
            for (int i = 0; i < this.value().size(); i++) {
                list.set(i, this.value().get(i).apply(other));
            }
            return new Lst(list);
        }

        public static BObj.Lst of() {
            return new Lst(List.of());
        }

        public static BObj.Lst single(final Object arg0) {
            return new Lst(List.of(Obj.of(arg0)));
        }

        public static BObj.Lst of(final Object arg0, final Object... args) {
            List<BObj.Obj> list = new ArrayList<>();
            list.add(Obj.of(arg0));
            for (final Object arg : args) {
                list.add(Obj.of(arg));
            }
            return new Lst(list);
        }

    }

    public static class Rec extends Obj implements BObj.Rec {

        public Rec(final Map<BObj.Obj, BObj.Obj> value) {
            super(value, REC_URI);
        }

        @Override
        public Map<BObj.Obj, BObj.Obj> value() {
            return (Map<BObj.Obj, BObj.Obj>) this.value;
        }

        @Override
        public Rec apply(final BObj.Obj other) {
            Map<BObj.Obj, BObj.Obj> map = new HashMap<>();
            return new Rec(map);
        }

    }

    public static class Inst extends Obj implements BObj.Inst {

        public Inst(final Quartet<BObj.Uri, BObj.Lst, BiFunction<BObj.Obj, BObj.Lst, BObj.Obj>, BObj.Obj> value) {
            super(value, value.getValue0().value());
        }

        public Inst(final BObj.Uri opcode, final BObj.Lst args, BiFunction<BObj.Obj, BObj.Lst, BObj.Obj> func, final BObj.Obj seed) {
            this(new Quartet<>(opcode, args, func, seed));
        }

        @Override
        public Quartet<BObj.Uri, BObj.Lst, BiFunction<BObj.Obj, BObj.Lst, BObj.Obj>, BObj.Obj> value() {
            return (Quartet<BObj.Uri, BObj.Lst, BiFunction<BObj.Obj, BObj.Lst, BObj.Obj>, BObj.Obj>) this.value;
        }

        @Override
        public Obj apply(final BObj.Obj other) {
            List<BObj.Obj> computedArgs = new ArrayList<>(this.value().getValue1().value().size());
            for (final BObj.Obj arg : this.value().getValue1().value()) {
                computedArgs.add(arg.apply(other));
            }
            return Obj.of(this.value().getValue2().apply(other, new Lst(computedArgs)));
        }
    }
}
