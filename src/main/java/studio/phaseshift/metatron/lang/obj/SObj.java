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
import org.javatuples.Triplet;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.SMonoid.Monoid;
import studio.phaseshift.metatron.struct.Router;
import studio.phaseshift.metatron.ui.Palette;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.*;

import static studio.phaseshift.metatron.lang.inst.SInst.DOM_URI;
import static studio.phaseshift.metatron.lang.inst.SInst.RNG_URI;

public class SObj implements BObj {

    public abstract static class Obj implements BObj.Obj, Cloneable {
        protected Object value;
        protected fURI tid;
        protected fURI vid;

        public Obj(final Object value, final fURI tid, final fURI vid) {
            if (null == tid)
                throw new IllegalArgumentException("every obj must have a type id (tid)");
            if (value instanceof Obj)
                throw new IllegalArgumentException("an obj can not have an obj as a base value");
            this.tid = tid;
            this.value = value;
            this.vid = vid;
            if (null != this.vid && Router.global().hasStruct(this.vid))
                Router.global().write(this.vid, this);
        }

        @Override
        public fURI vid() {
            return this.vid;
        }

        @Override
        public Obj vid(final fURI id) {
            if (this.vid == id)
                return this;
            Obj clone = this.clone();
            clone.vid = id;
            return clone;
        }

        @Override
        public Obj clone() {
            try {
                Obj clone = (Obj) super.clone();
                clone.tid = this.tid;
                clone.vid = this.vid;
                clone.value = this.value;
                return clone;
            } catch (final CloneNotSupportedException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public <O extends BObj.Obj> O clone(final Object value) {
            O clone = (O) this.clone();
            ((SObj.Obj) clone).value = value;
            return clone;
        }

        @Override
        public fURI tid() {
            return this.tid;
        }

        @Override
        public Object value() {
            return this.value;
        }

        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof BObj.Obj))
                return false;
            final BObj.Obj otherObj = (BObj.Obj) other;
            if (this.isNoObj())
                return otherObj.isNoObj();
            if (otherObj.isNoObj())
                return false;
            return otherObj.value().equals(this.value) && otherObj.tid().equals(this.tid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.value, this.tid);
        }

        @Override
        public String toString() {
            return this.toString(Palette.GLOBAL);
        }

        public static BObj.Obj of(final Object value) {
            return SObj.Obj.of(value, null, null);
        }

        public static BObj.Obj of(final Object value, final fURI tid, final fURI vid) {
            if (null == value)
                return BObj.NoObj.of();
            else if (value instanceof BObj.Obj)
                return (BObj.Obj) value;
            else if (value instanceof Boolean)
                return new Bool((Boolean) value, null == tid ? BOOL_URI : tid, vid);
            else if (value instanceof Integer)
                return new Int((Integer) value, null == tid ? INT_URI : tid, vid);
            else if (value instanceof Double)
                return new Real((Double) value, null == tid ? REAL_URI : tid, vid);
            else if (value instanceof String)
                return new Str((String) value, null == tid ? STR_URI : tid, vid);
            else if (value instanceof fURI)
                return new Uri((fURI) value, null == tid ? URI_URI : tid, vid);
            else if (value instanceof Pair)
                return new Rel((Pair<BObj.Obj, BObj.Obj>) value, null == tid ? REL_URI : tid, vid);
            else if (value instanceof List)
                return new Lst((List) value, null == tid ? LST_URI : tid, vid);
            else if (value instanceof Map)
                return new Rec((Map) value, null == tid ? REC_URI : tid, vid);
            else if (value instanceof Triplet<?, ?, ?>)
                return new Inst((Triplet<BObj.Poly, InstF, BObj.Obj>) value, null == tid ? INST_URI : tid, vid);
            else {
                try {
                    return new Uri(new fURI(value.toString()), null == tid ? URI_URI : tid, vid);
                } catch (final IllegalArgumentException e) {
                    throw new RuntimeException("unknown object type: " + value.toString());
                }
            }

        }
    }

    public static class Bool extends Obj implements BObj.Bool {

        public Bool(final boolean value, final fURI tid, final fURI vid) {
            super(value, tid, vid);
        }

        public Boolean value() {
            return (Boolean) this.value;
        }

        public static BObj.Bool of(final boolean bool) {
            return new Bool(bool, BOOL_URI, null);
        }

    }

    public static class Int extends Obj implements BObj.Int {
        public Int(final long value, final fURI tid, final fURI vid) {
            super(value, tid, vid);
        }

        public Long value() {
            return (Long) this.value;
        }

        public static BObj.Int of(final long value) {
            return new Int(value, INT_URI, null);
        }
    }

    public static class Real extends Obj implements BObj.Real {

        public Real(final Double value, final fURI tid, final fURI vid) {
            super(value, tid, vid);
        }

        public Double value() {
            return (Double) this.value;
        }

        public static BObj.Real of(final double value) {
            return new Real(value, REAL_URI, null);
        }

    }

    public static class Str extends Obj implements BObj.Str {

        public Str(final String value, final fURI tid, final fURI vid) {
            super(value, tid, vid);
        }

        public String value() {
            return (String) this.value;
        }

        public static BObj.Str of(final String value) {
            return new Str(value, STR_URI, null);
        }

    }

    public static class Uri extends Obj implements BObj.Uri {

        public Uri(final fURI value, final fURI tid, final fURI vid) {
            super(value, tid, vid);
        }

        public fURI value() {
            return (fURI) this.value;
        }

        public static BObj.Uri of(final String uri) {
            return new Uri(new fURI(uri), URI_URI, null);
        }

        /*public BObj.Obj apply(final BObj.Obj lhs) {
            if (lhs.isRec()) {
                //System.out.println(kv.getValue());
                return new Objs(lhs.recValue().entrySet().stream().filter(kv -> !kv.getKey().apply(lhs).isNoObj()).map(Map.Entry::getValue).toList(), OBJS_URI, null);
            } else if (lhs.isUri()) {
                return lhs.uriValue().matches(this.uriValue()) ? this : NoObj.of();
            } else {
                return this;
            }
        }*/
    }

    public static class Rel extends Obj implements BObj.Rel {
        public Rel(final Pair<BObj.Obj, BObj.Obj> value, final fURI tid, final fURI vid) {
            super(value, tid, vid);
        }

        @Override
        public Pair<BObj.Obj, BObj.Obj> value() {
            return (Pair) this.value;
        }


        public static BObj.Rel of(final BObj.Obj domain, final BObj.Obj range) {
            return new Rel(Pair.with(domain, range), REL_URI, null);
        }

        @Override
        public BObj.Obj get(final fURI key) {
            return key.equals(DOM_URI) ? this.domain() : (key.equals(RNG_URI) ? this.range() : NoObj.of());
        }
    }

    public static class Lst extends Obj implements BObj.Lst {
        public Lst(final List<BObj.Obj> value, final fURI tid, final fURI vid) {
            super(value, tid, vid);
        }

        @Override
        public List<BObj.Obj> value() {
            return (List<BObj.Obj>) this.value;
        }

        @Override
        public Lst apply(final BObj.Obj lhs) {
            final int size = this.value().size();
            List<BObj.Obj> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(this.value().get(i).apply(lhs));
            }
            return new Lst(list, this.tid, this.vid);
        }

        public static BObj.Lst of(final Object... args) {
            return new Lst(Arrays.stream(args).map(Obj::of).toList(), LST_URI, null);
        }

        @Override
        public BObj.Obj get(final fURI key) {
            return NoObj.of();
        }
    }

    public static class Rec extends Obj implements BObj.Rec {

        public Rec(final Map<BObj.Obj, BObj.Obj> value, final fURI tid, final fURI vid) {
            super(value, tid, vid);
        }

        @Override
        public Map<BObj.Obj, BObj.Obj> value() {
            return (Map<BObj.Obj, BObj.Obj>) this.value;
        }

        @Override
        public Rec apply(final BObj.Obj other) {
            final Map<BObj.Obj, BObj.Obj> map = new LinkedHashMap<>();
            for (final Map.Entry<BObj.Obj, BObj.Obj> entry : this.value().entrySet()) {
                if (other.isUri() && entry.getKey().isUri()) {
                    if (other.matches(entry.getKey()) || entry.getKey().matches(other))
                        map.put(other, entry.getValue().apply(other));
                } else {
                    final BObj.Obj keyApply = entry.getKey().apply(other);
                    // if (!keyApply.isNoObj()) {
                    if (!keyApply.isNoObj()) {
                        final BObj.Obj valueApply = entry.getValue().apply(other);
                        if (!valueApply.isNoObj()) {
                            final BObj.Obj current = map.get(other);
                            if (null == current)
                                map.put(keyApply, valueApply);
                            else if (current.isObjs())
                                map.put(keyApply, ((Objs) current).append(valueApply));
                            else
                                map.put(keyApply, Objs.of(List.of(current, valueApply)));
                        }
                        // System.out.println(other + ";;;" + map);
                    }
                }
            }
            return new SObj.Rec(map, this.tid, this.vid);
        }

        public static BObj.Rec of(final BObj.Obj... kvs) {
            final Map<BObj.Obj, BObj.Obj> l = new LinkedHashMap<>();
            for (int i = 0; i < kvs.length; i = i + 2) {
                l.put(kvs[i], kvs[i + 1]);
            }
            return new SObj.Rec(l, REC_URI, null);
        }

        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof BObj.Rec))
                return false;
            final BObj.Rec r = (BObj.Rec) other;
            if (this.length() != ((BObj.Rec) other).length() || !r.tid().equals(this.tid()))
                return false;
            for (final Map.Entry<BObj.Obj, BObj.Obj> e : r.value().entrySet()) {
                if (!this.get(e.getKey()).equals(e.getValue()))
                    return false;
            }
            return true;
        }

        public BObj.Obj get(final int index) {
            return IteratorUtil.index(this.value().values().iterator(), index, NoObj.of());
        }

      /*  public BObj.Obj get(final fURI key) {
            final BObj.Uri uriKey = new SObj.Uri(key, URI_URI, fURI.NONE);
            final Map<BObj.Obj, BObj.Obj> map = new LinkedHashMap<>();
            for (final Map.Entry<BObj.Obj, BObj.Obj> entry : this.value().entrySet()) {
                if (entry.getKey().matches(uriKey)) {
                    if (!entry.getValue().isNoObj()) {
                        final BObj.Obj current = map.get(uriKey);
                        if (null == current)
                            map.put(uriKey, entry.getValue());
                        else if (current.isObjs())
                            map.put(uriKey, ((Objs) current).append(entry.getValue()));
                        else
                            map.put(uriKey, Objs.of(List.of(current, entry.getValue())));
                    }
                }
            }
            return new SObj.Rec(map, REC_URI, fURI.NONE);
        }*/

    }

    public static class Objs extends Obj implements BObj.Objs {

        public Objs(final Iterable<BObj.Obj> value, final fURI tid, final fURI vid) {
            super(value, tid, vid);
        }

        @Override
        public BObj.Objs append(final Obj obj) {
            final List<BObj.Obj> list = new ArrayList<>();
            for (final BObj.Obj a : this.value()) {
                list.add(a);
            }
            list.add(obj);
            return new Objs(list, this.tid, this.vid);
        }

        @Override
        public Iterable<BObj.Obj> value() {
            return (Iterable<BObj.Obj>) this.value;
        }

        @Override
        public BObj.Objs apply(final BObj.Obj other) {
            return new Objs(IteratorUtil.map(this.value(), o -> o.apply(other)), this.tid, this.vid);
        }

        public static BObj.Objs of(final Iterable<BObj.Obj> objs) {
            return new Objs(objs, OBJS_URI, null);
        }

        public BObj.Obj get(final int index) {
            return IteratorUtil.index(this.iterator(), index, NoObj.of());
        }

        public BObj.Obj get(final fURI key) {
            return NoObj.of();
        }
    }

    public static class Code extends Obj implements BObj.Code {
        public Code(final List<BObj.Inst> value, final fURI tid, final fURI vid) {
            super(value, tid, vid);

        }

        @Override
        public List<BObj.Inst> value() {
            return (List<BObj.Inst>) this.value;
        }

        @Override
        public BObj.Obj apply(final BObj.Obj lhs) {
            return new Monoid(this, lhs).next();
        }

        public static BObj.Code of(final BObj.Inst... insts) {
            return Code.of(Arrays.asList(insts));
        }

        public static BObj.Code of(final List<BObj.Inst> insts) {
            return new Code(insts, CODE_URI, null);
        }

        @Override
        public boolean matches(final BObj.Obj lhs) {
            return !this.apply(lhs).isNoObj();
        }

        @Override
        public BObj.NoObj get(final fURI key) {
            return NoObj.of();
        }
    }

    public static class Inst extends Obj implements BObj.Inst {

        public Inst(final Triplet<BObj.Poly, InstF, BObj.Obj> value, final fURI tid, final fURI vid) {
            super(value, tid, vid);
        }

        public Inst(final fURI tid, final BObj.Obj... args) {
            this(new Triplet<>(new SObj.Lst(Arrays.asList(args), LST_URI, null), null, NoObj.of()), tid, null);
        }

        @Override
        public Triplet<BObj.Poly, InstF, BObj.Obj> value() {
            return (Triplet<BObj.Poly, InstF, BObj.Obj>) this.value;
        }

        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof Inst otherInst))
                return false;
            return this.tid().equals(otherInst.tid()) && this.args().equals(otherInst.args());
        }

        @Override
        public Inst clone(final Object triplet) {
            Inst clone = (Inst) super.clone();
            clone.value = triplet;
            return clone;
        }
    }
}
