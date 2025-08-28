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
import studio.phaseshift.metatron.lang.*;
import studio.phaseshift.metatron.util.*;

import java.util.*;
import java.util.function.*;

public class SObj implements BObj {

    public static class Obj implements BObj.Obj {
        final Object value;
        fURI type;

        public Obj(final Object value, final fURI type) {
            if (value instanceof Obj)
                throw new IllegalArgumentException("an obj can not have an obj as a base value");
            this.value = value;
            this.type = type;
        }

        @Override
        public fURI type() {
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
            return this.toString(Palette.STANDARD);
        }

        public static Obj of(final Object value, final fURI type) {
            Obj o = Obj.of(value);
            o.type = type;
            return o;
        }

        public static Obj of(final Object value) {
            if (null == value)
                return NoObj.of();
            else if (value instanceof Obj)
                return (Obj) value;
            else if (value instanceof Boolean)
                return new Bool((Boolean) value);
            else if (value instanceof Integer)
                return new Int((Integer) value);
            else if (value instanceof Double)
                return new Real((Double) value);
            else if (value instanceof String)
                return new Str((String) value);
            else if (value instanceof fURI)
                return new Uri((fURI) value);
            else if (value instanceof List)
                return new Lst((List) value);
            else if (value instanceof Map)
                return new Rec((Map) value);
            else if (value instanceof Triplet<?, ?, ?>)
                return new Inst((Triplet<BObj.Lst, BiFunction<BObj.Obj, BObj.Lst, BObj.Obj>, BObj.Obj>) value, INST_URI);
            else {
                try {
                    return new Uri(fURI.create(value.toString()));
                } catch (final IllegalArgumentException e) {
                    throw new RuntimeException("unknown object type: " + value.toString());
                }
            }

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

        public Bool(final Boolean value, final fURI type) {
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

        public Int(final Integer value, final fURI type) {
            super(value, type);
        }

        public Integer value() {
            return (Integer) this.value;
        }

        public static Int of(final String type, final int i) {
            return new Int(i, fURI.create(type));
        }

        public static Int of(final fURI type, final int i) {
            return new Int(i, type);
        }

        public static Int of(final int i) {
            return new Int(i);
        }
    }

    public static class Real extends Obj implements BObj.Real {

        public Real(final Double value, final fURI type) {
            super(value, type);
        }

        public Real(final Double value) {
            super(value, REAL_URI);
        }

        public Double value() {
            return (Double) this.value;
        }

    }

    public static class Str extends Obj implements BObj.Str {

        public Str(final String value, final fURI type) {
            super(value, type);
        }

        public Str(final String value) {
            super(value, STR_URI);
        }

        public String value() {
            return (String) this.value;
        }

    }

    public static class Uri extends Obj implements BObj.Uri {

        public Uri(final fURI value, final fURI type) {
            super(value, type);
        }

        public Uri(final fURI value) {
            super(value, URI_URI);
        }
        
        public fURI value() {
            return (fURI) this.value;
        }

        public static BObj.Uri of(final String uri) {
            return new Uri(new fURI(uri));
        }
    }

    public static class Lst extends Obj implements BObj.Lst {
        public Lst(final List<BObj.Obj> value, final fURI type) {
            super(value, type);
        }

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

        public Rec(final Map<BObj.Obj, BObj.Obj> value, final fURI type) {
            super(value, type);
        }

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

    public static class Objs extends Obj implements BObj.Objs {

        public Objs(final Iterator<BObj.Obj> value) {
            super(value, OBJS_URI);
        }

        @Override
        public Iterator<BObj.Obj> value() {
            return (Iterator<BObj.Obj>) this.value;
        }

        @Override
        public BObj.Objs apply(final BObj.Obj other) {
            return SObj.Objs.of(IteratorUtil.map(this.value(), o -> o.apply(other)));
        }

        public static BObj.Objs of(final Iterator<BObj.Obj> objs) {
            return new Objs(objs);
        }

        public static BObj.Objs single(final BObj.Obj obj) {
            return new Objs(IteratorUtil.of(obj));
        }

    }

    public static class Code extends Obj implements BObj.Code {
        public Code(final List<BObj.Inst> value) {
            super(value, CODE_URI);
        }

        @Override
        public List<BObj.Inst> value() {
            return (List<BObj.Inst>) this.value;
        }

        public static BObj.Code of(final BObj.Inst inst0, final BObj.Inst... insts) {
            List<BObj.Inst> list = new ArrayList<>();
            list.add(inst0);
            Collections.addAll(list, insts);
            return new Code(list);
        }

    }

    public static class Inst extends Obj implements BObj.Inst {

        public Inst(final Triplet<BObj.Lst, BiFunction<BObj.Obj, BObj.Lst, BObj.Obj>, BObj.Obj> value, final fURI type) {
            super(value, type);
        }

        @Override
        public Triplet<BObj.Lst, BiFunction<BObj.Obj, BObj.Lst, BObj.Obj>, BObj.Obj> value() {
            return (Triplet<BObj.Lst, BiFunction<BObj.Obj, BObj.Lst, BObj.Obj>, BObj.Obj>) this.value;
        }

        @Override
        public Obj apply(final BObj.Obj lhs) {
            List<BObj.Obj> computedArgs = new ArrayList<>(this.value().getValue0().value().size());
            for (final BObj.Obj arg : this.value().getValue0().value()) {
                computedArgs.add(arg.apply(lhs));
            }
            return Obj.of(this.value().getValue1().apply(lhs, new Lst(computedArgs)));
        }
    }
}
