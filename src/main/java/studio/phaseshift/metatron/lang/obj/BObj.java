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
import org.jline.jansi.*;
import studio.phaseshift.metatron.lang.*;
import studio.phaseshift.metatron.util.*;

import java.util.*;
import java.util.function.*;

import static org.jline.jansi.Ansi.*;

public interface BObj {

    public static final fURI OBJ_URI = fURI.create("m:obj");
    public static final fURI NOOBJ_URI = fURI.create("m:noobj");
    public static final fURI BOOL_URI = fURI.create("m:bool");
    public static final fURI INT_URI = fURI.create("m:int");
    public static final fURI REAL_URI = fURI.create("m:real");
    public static final fURI STR_URI = fURI.create("m:str");
    public static final fURI URI_URI = fURI.create("m:uri");
    public static final fURI LST_URI = fURI.create("m:lst");
    public static final fURI REC_URI = fURI.create("m:rec");
    public static final fURI INST_URI = fURI.create("m:inst");
    public static final fURI CODE_URI = fURI.create("m:code");
    public static final fURI OBJS_URI = fURI.create("m:objs");


    interface Obj extends Function<Obj, Obj> {
        Object value();

        fURI type();

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
                        .reset()
                        .toString();
        }

        @Override
        default Obj apply(final Obj other) {
            return this;
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

        public fURI type() {
            return NOOBJ_URI;
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
    }

    interface Rec extends Poly {
        @Override
        Map<Obj, Obj> value();

        @Override
        Rec apply(final Obj other);
    }

    interface Inst extends Poly {
        @Override
        Triplet<Lst, BiFunction<Obj, Lst, Obj>, Obj> value();

        @Override
        default Obj apply(final Obj lhs) {
            final int size = this.value().getValue0().value().size();
            final List<Obj> args = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                args.set(i, this.value().getValue0().value().get(i).apply(lhs));
            }
            return this.value().getValue1().apply(lhs, this.value().getValue0());
        }

        default String opcode() {
            return this.type().hostOrSegment();
        }
    }

    interface Code extends Poly {
        @Override
        List<Inst> value();

        @Override
        default Obj apply(final Obj lhs) {
            List<Obj> a = List.of(lhs);
            List<Obj> b = new ArrayList<>();
            for (final Obj o : a) {
                for (final Inst i : this.value()) {
                    b.add(i.apply(o));
                }
            }
            return SObj.Lst.of(b);
        }
    }

    interface Objs extends Poly {
        @Override
        Iterator<Obj> value();
    }
}
