/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.mach.type.machine;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MLst;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.furi.c.cInt.C_SOME;
import static studio.phaseshift.metatron.furi.c.cInt.C_ZERO;

/*
 * A monad queue backed by a mutable list.  Extends {@code MLst} so it is
 * serialized as an {@code Lst} and flows through the type system without
 * special cases.  Overrides {@code c()}, {@code take()}, {@code append()},
 * and {@code uniqueC()} to provide monad-specific statistics tracking.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ListMonad extends MLst {

    public ListMonad(final List<Obj> jvm, final fURI vid) {
        super(jvm, null, vid);
    }

    @Override
    public Obj append(final Obj obj) {
        this.jvm().add(obj);
        Router.global().stats().monadicStats().incrRunningMonads(1L);
        return this;
    }

    @Override
    public cInt c() {
        return this.jvm().isEmpty() ? C_ZERO : C_SOME;
    }

    @Override
    public Obj take() {
        if (this.jvm().isEmpty())
            return null;
        Router.global().stats().monadicStats().incrRunningMonads(-1L);
        return this.jvm().removeFirst();
    } // TODO: explore removeLast() as a way of simulating chained iterators

    @Override
    public cInt uniqueC() {
        return cInt.of((long) this.jvm().size());
    }

    public static ListMonad of() {
        return new ListMonad(new ArrayList<>(), null);
    }
}
