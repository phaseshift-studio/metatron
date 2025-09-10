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

import org.javatuples.Triplet;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface Inst extends Obj {
    public static final fURI TID = fURI.of("inst");

    @Override
    Inst clone(final Object value, final fURI tid, final fURI vid);

    @Override
    Triplet<Poly, f, Obj> value();

    default Poly args() {
        return this.value().getValue0();
    }

    default Inst.f f() {
        return this.value().getValue1();
    }

    default Obj seed() {
        return this.value().getValue2();
    }

    public static class f {

        private final boolean bi;
        final Object func;

        public f(final BiFunction<Obj, Inst, Obj> func) {
            this.bi = true;
            this.func = func;
        }

        public f(final Function<Obj, Obj> func) {
            this.bi = false;
            this.func = func;
        }

        public Obj apply(final Obj lhs, final Inst inst) {
            return this.bi ? ((BiFunction<Obj, Inst, Obj>) this.func).apply(lhs, inst) : ((Function<Obj, Obj>) this.func).apply(lhs);
        }

        public static f of(final BiFunction<Obj, Inst, Obj> func) {
            return null == func ? null : new f(func);
        }

        public static f of(final Function<Obj, Obj> func) {
            return null == func ? null : new f(func);
        }

        @Override
        public String toString() {
            return ObjUtil.isLambda(this.func) ? "<j>" : this.func.toString();
        }

    }
}