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

import org.javatuples.Triplet;
import org.jline.jansi.Ansi;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.inst.BInst;
import studio.phaseshift.metatron.lang.inst.BInst.Gather;
import studio.phaseshift.metatron.lang.inst.BInst.Initial;
import studio.phaseshift.metatron.lang.inst.BInst.Scatter;
import studio.phaseshift.metatron.lang.inst.BInst.Terminal;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.jline.jansi.Ansi.ansi;

public interface BObj extends Cloneable {

    public static final fURI OBJ_URI = new fURI("obj");
    public static final fURI NOOBJ_URI = new fURI("noobj");
    public static final fURI BOOL_URI = new fURI("bool");
    public static final fURI INT_URI = new fURI("int");
    public static final fURI REAL_URI = new fURI("real");
    public static final fURI STR_URI = new fURI("str");
    public static final fURI URI_URI = new fURI("uri");
    public static final fURI LST_URI = new fURI("lst");
    public static final fURI REC_URI = new fURI("rec");
    public static final fURI INST_URI = new fURI("inst");
    public static final fURI CODE_URI = new fURI("code");
    public static final fURI OBJS_URI = new fURI("objs");


    interface Obj extends Function<Obj, Obj>, Iterable<Obj> {
        Object value();

        fURI type();

        fURI id();

        Obj id(final fURI furi);

        Obj clone();


        default String toString(final Palette palette) {
            if (this.isNoObj())
                return ansi().fg(palette.typeC()).a("noobj").reset().toString();
            else if (this instanceof final Inst inst)
                return ansi()
                        .fg(palette.typeC()).
                        a(this.type())
                        .fg(palette.formC())
                        .a("(")
                        .fg(palette.valueC())
                        .a(inst.value().getValue0())
                        .fg(palette.formC())
                        .a(")[")
                        .fg(palette.valueC())
                        .a(ObjUtil.isLambda(inst.value().getValue1()) ? "λ" : inst.value().getValue1())
                        .fg(palette.formC())
                        .a(']')
                        .reset()
                        .toString();
            else if (this.isLst()) {
                Ansi s = ansi()
                        .fg(palette.typeC())
                        .a(this.type())
                        .fg(palette.formC())
                        .a('[');
                for (int i = 0; i < this.<Lst>as().value().size(); i++) {
                    s = s.a(this.<Lst>as().value().get(i));
                    if (i != this.<Lst>as().value().size() - 1)
                        s = s.fg(palette.formC()).a(',');
                }
                return s.fg(palette.formC()).a(']').toString();
            } else if (this.isRec()) {
                Ansi s = ansi()
                        .fg(palette.typeC())
                        .a(this.type())
                        .fg(palette.formC())
                        .a('[');
                List<Map.Entry<Obj, Obj>> kv = new ArrayList<>(this.<Rec>as().value().entrySet());
                for (int i = 0; i < kv.size(); i++) {
                    s = s.a(kv.get(i).getKey()).fg(palette.formC()).a("=>").a(kv.get(i).getValue());
                    if (i != kv.size() - 1)
                        s = s.fg(palette.formC()).a(',');
                }
                return s.fg(palette.formC()).a(']').toString();
            } else
                return ansi()
                        .fg(palette.typeC())
                        .a(this.type())
                        .fg(palette.formC())
                        .a('[')
                        .fg(palette.valueC())
                        .a(this.value())
                        .fg(palette.formC())
                        .a(']')
                        .fg(palette.form2C())
                        .a(null == this.id() ? "" : "@")
                        .fg(palette.typeC())
                        .a(null == this.id() ? "" : this.id())
                        .reset()
                        .toString();
        }

        @Override
        default Obj apply(final Obj other) {
            return this;
        }


        <O extends Obj> O clone(final Object value);

        @Override
        default Iterator<Obj> iterator() {
            return this.isObjs() ? ((Iterable<Obj>) this.value()).iterator() : IteratorUtil.of(this);
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

        default fURI uriValue() {
            if (this.isUri())
                return ((SObj.Uri) this).value();
            throw new IllegalStateException("obj is not an uri");
        }
    }


    final class NoObj implements Obj, Inst {
        private final static NoObj NOOBJ = new NoObj();

        private NoObj() {
        }

        public fURI id() {
            return null;
        }

        public NoObj id(final fURI id) {
            return NOOBJ;
        }

        public fURI type() {
            return NOOBJ_URI;
        }

        @Override
        public Triplet value() {
            return null;
        }

        @Override
        public Obj apply(final Obj other) {
            return other;
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
            return this.toString(Palette.STANDARD);
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
        fURI value();

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

        @Override
        default long length() {
            return this.value().size();
        }

        @Override
        default Iterator<Obj> iterator() {
            return this.value().iterator();
        }
    }

    interface Rec extends Poly {
        @Override
        Map<Obj, Obj> value();

        @Override
        Rec apply(final Obj other);

        @Override
        default long length() {
            return this.value().size();
        }

        @Override
        default Iterator<Obj> iterator() {
            return this.value().entrySet().stream().map(kv -> (Obj) new SObj.Lst(List.of(kv.getKey(), kv.getValue()))).iterator();
        }
    }

    interface InstF extends BiFunction<Obj, Lst, Obj> {
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

        default InstF function() {
            return this.value().getValue1();
        }

        @Override
        default Obj apply(final Obj lhs) {
            final List<Obj> computedArgs = new ArrayList<>((int) this.args().length());
            for (final Obj arg : this.args()) {
                computedArgs.add(arg.apply(lhs));
            }
            final InstF instF = null == this.function() ? BInst.SymbolTable.resolve(lhs, this).function() : this.function();
            return SObj.Obj.of(instF.apply(lhs, new SObj.Lst(computedArgs)));
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
