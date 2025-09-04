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
import studio.phaseshift.metatron.lang.inst.BInst;
import studio.phaseshift.metatron.lang.inst.BInst.Gather;
import studio.phaseshift.metatron.lang.inst.BInst.Initial;
import studio.phaseshift.metatron.lang.inst.BInst.Scatter;
import studio.phaseshift.metatron.lang.inst.BInst.Terminal;
import studio.phaseshift.metatron.ui.ObjStringSerializer;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.lang.inst.SInst.BLOCK_URI;

public interface BObj extends Cloneable {

    fURI OBJ_URI = new fURI("obj");
    fURI NOOBJ_URI = new fURI("noobj");
    fURI BOOL_URI = new fURI("bool");
    fURI INT_URI = new fURI("int");
    fURI REAL_URI = new fURI("real");
    fURI STR_URI = new fURI("str");
    fURI URI_URI = new fURI("uri");
    fURI LST_URI = new fURI("lst");
    fURI REC_URI = new fURI("rec");
    fURI INST_URI = new fURI("inst");
    fURI CODE_URI = new fURI("code");
    fURI OBJS_URI = new fURI("objs");
    fURI REL_URI = new fURI("rel");

    Set<fURI> MTRON_CORE_TYPES = Set.of(OBJ_URI, NOOBJ_URI, BOOL_URI, INT_URI, REAL_URI, STR_URI, URI_URI, LST_URI, REC_URI, INST_URI, CODE_URI, OBJS_URI, REL_URI);

    interface Obj extends Function<Obj, Obj>, Iterable<Obj>, Cloneable {
        Object value();

        fURI tid();

        fURI vid();

        Obj vid(final fURI furi);

        Obj clone();

        default String toString(final Palette palette) {
            return ObjStringSerializer.build().palette(palette).hideTypesMatching(MTRON_CORE_TYPES).simpleColon(true).create().write(this);
        }

        @Override
        default Obj apply(final Obj other) {
            return this;
        }


        default boolean matches(final Obj rhs) {
            return this.equals(rhs);
        }

        <O extends Obj> O clone(final Object value);

        @Override
        default Iterator<Obj> iterator() {
            return this.isObjs() ? ((Iterable<Obj>) this.value()).iterator() : IteratorUtil.of(this);
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

      /*  default boolean isRec() {
            return this instanceof Rec;
        }*/

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

        default Long intValue() {
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

    /*    default Map<Obj, Obj> recValue() {
            if (this.isRec())
                return ((Rec) this).value();
            throw new IllegalStateException("obj is not an rec");
        }*/

        default Pair<Obj, Obj> relValue() {
            if (this.isRel())
                return ((Rel) this).value();
            throw new IllegalStateException("obj is not an rel");
        }
    }


    final class NoObj implements Obj, Inst {
        private final static NoObj NOOBJ = new NoObj();

        private NoObj() {
        }

        public fURI vid() {
            return null;
        }

        public NoObj vid(final fURI id) {
            return NOOBJ;
        }

        public fURI tid() {
            return NOOBJ_URI;
        }

        @Override
        public Triplet value() {
            return null;
        }

        @Override
        public Obj apply(final Obj lhs) {
            return NOOBJ;
        }

        @Override
        public boolean matches(final Obj rhs) {
            return false;
        }

        @Override
        public <O extends Obj> O clone(Object value) {
            return (O) NOOBJ;
        }

        public static NoObj of() {
            return NOOBJ;
        }

        @Override
        public String toString() {
            return this.toString(Palette.GLOBAL);
        }

        @Override
        public Iterator<Obj> iterator() {
            return IteratorUtil.of(this);
        }

        @Override
        public NoObj clone() {
            return NOOBJ;
        }
    }

    interface Mono extends Obj {
    }

    interface Poly extends Obj, Iterable<Obj> {
        long length();

        default Poly append(final Obj... obj) {
            return this; // TODO: this is cause I'm too lazy to implement it for every poly right now
        }

        @Override
        Iterator<Obj> iterator();
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
        Long value();

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
        fURI value();

        @Override
        default boolean matches(final Obj rhs) {
            return rhs.isUri() ? this.value().matches(rhs.uriValue()) : this.equals(rhs);
        }

        @Override
        default Obj apply(final Obj lhs) {
            if (lhs instanceof final Rel rel)
                return rel.apply(this);
            else if (lhs instanceof final Lst l) {
                return new SObj.Lst(l.value().stream().map(this::apply).toList(), lhs.tid(), null);
            } else {
                return this;
                //return lhs.matches(this) ? lhs : NoObj.of();
            }
        }
    }

    interface Rel extends Poly {
        @Override
        Pair<Obj, Obj> value();

        @Override
        default Obj apply(final Obj lhs) {
            return this.matches(lhs) ? this.range() : NoObj.of();
        }

        @Override
        default boolean matches(final Obj rhs) {
            return rhs.matches(this.domain()) || this.equals(rhs);
        }

        @Override
        default long length() {
            return 2;
        }

        @Override
        default Iterator<Obj> iterator() {
            return (Iterator) this.value().iterator();
        }

        default Obj domain() {
            return this.value().getValue0();
        }

        default Obj range() {
            return this.value().getValue1();
        }

    }

    interface Lst extends Poly {
        @Override
        List<Obj> value();

        @Override
        Lst apply(final Obj other);

        @Override
        default long length() {
            return this.value().size();
        }

        default Obj get(final int index) {
            return this.value().get(index);
        }

        @Override
        default Lst append(final Obj... obj) {
            final List<Obj> l = new ArrayList<>(this.value());
            Collections.addAll(l, obj);
            return new SObj.Lst(l, this.tid(), null);
        }

        @Override
        default Iterator<Obj> iterator() {
            return this.value().iterator();
        }
    }

   /* interface Rec extends Poly {
        @Override
        Map<Obj, Obj> value();

        @Override
        Rec apply(final Obj other);

        default <O extends Obj> O get(final Obj key) {
            return (O) this.value().getOrDefault(key, NoObj.of());
        }

        @Override
        default long length() {
            return this.value().size();
        }

        @Override
        default Rec append(final Obj... obj) {
            final Map<Obj, Obj> l = new HashMap<>(this.value());
            for (int i = 0; i < obj.length; i = i + 2) {
                l.put(obj[i], obj[i + 1]);
            }
            return new SObj.Rec(l, this.tid());
        }

        @Override
        default Iterator<Obj> iterator() {
            return this.value().entrySet().stream().map(kv -> SObj.Lst.of(List.of(kv.getKey(), kv.getValue()))).iterator();
        }
    }*/

    class InstF {

        private final boolean bi;
        final Object func;

        public InstF(final BiFunction<Obj, Lst, Obj> func) {
            this.bi = true;
            this.func = func;
        }

        public InstF(final Function<Obj, Obj> func) {
            this.bi = false;
            this.func = func;
        }

        public Obj apply(final Obj lhs, final Lst args) {
            return this.bi ? ((BiFunction<Obj, Lst, Obj>) this.func).apply(lhs, args) : ((Function<Obj, Obj>) this.func).apply(lhs);
        }

        public Obj apply(final Obj lhs) {
            return this.bi ? ((BiFunction<Obj, Lst, Obj>) this.func).apply(lhs, new SObj.Lst(List.of(), LST_URI, null)) : ((Function<Obj, Obj>) this.func).apply(lhs);
        }

        public static InstF of(final BiFunction<Obj, Lst, Obj> func) {
            return null == func ? null : new InstF(func);
        }

        public static InstF of(final Function<Obj, Obj> func) {
            return null == func ? null : new InstF(func);
        }

        @Override
        public String toString() {
            return ObjUtil.isLambda(this.func) ? "λ" : this.func.toString();
        }

    }

    interface Inst extends Poly {
        @Override
        Triplet<Poly, InstF, Obj> value();

        default Poly args() {
            return this.value().getValue0();
        }

        default Obj args(int index) {
            return IteratorUtil.index(this.args().iterator(), index, NoObj.of());
        }

        default Obj seed() {
            return this.value().getValue2();
        }

        default InstF f() {
            return this.value().getValue1();
        }

        default boolean resolved() {
            return null != this.f();
        }

        @Override
        default Obj apply(final Obj lhs) {
            /*final List<Obj> computedArgs = new ArrayList<>((int) this.args().length());
            for (final Obj arg : this.args()) {
                computedArgs.add(arg.apply(lhs));
            }*/
            final Inst resolvedInst = BInst.SymbolTable.resolve(lhs, this);
            if (resolvedInst.isBlocking())
                return resolvedInst.args(0);
            else {
                final InstF instF = null == this.f() ? resolvedInst.f() : this.f();
                return SObj.Obj.of(instF.apply(lhs, (Lst) resolvedInst.args()));
            }
        }

        //Inst nextInst();

        @Override
        default long length() {
            return this.value().getSize();
        }

        @Override
        default Iterator<Obj> iterator() {
            return this.isNoObj() ? IteratorUtil.of() : (Iterator) this.value().iterator();
        }

        default boolean isBlocking() {
            return this.tid().equals(BLOCK_URI);
        }

        default boolean isInitial() {
            return this instanceof Initial;
        }

        default boolean isGather() {
            return this instanceof Gather;
        }

        default boolean isScatter() {
            return this instanceof Scatter;
        }

        default boolean isBarrier() {
            return this.isInitial() || this.isGather();
        }

        default boolean isTerminal() {
            return this instanceof Terminal;
        }
    }

    interface Code extends Poly {
        @Override
        List<Inst> value();

        @Override
        Obj apply(final Obj lhs);

        default Inst nextInst(final Inst current) {
            for (int i = 0; i < this.value().size(); i++) {
                if (this.value().get(i).equals(current) && i < (this.value().size() - 1))
                    return this.value().get(i + 1);
            }
            return NoObj.of();
        }

        @Override
        default long length() {
            return this.value().size();
        }

        @Override
        default Iterator<Obj> iterator() {
            return (Iterator) this.value().iterator();
        }
    }

    interface Objs extends Poly {
        @Override
        Iterable<Obj> value();

        Objs append(final SObj.Obj obj);

        @Override
        default Iterator<Obj> iterator() {
            return this.value().iterator();
        }

        @Override
        default long length() {
            return IteratorUtil.count(this.value());
        }
    }
}
