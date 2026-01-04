/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.lang.core.mach.type.impl;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.c.cInt.C_SOME;
import static studio.phaseshift.metatron.furi.c.cInt.C_ZERO;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.OBJS_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ListMonad implements Obj {

    private List<Obj> jvm;
    private fURI vid;

    public ListMonad(final List<Obj> jvm, final fURI vid) {
        this.jvm = jvm;
        this.vid = vid;
    }

    @Override
    public Obj append(final Obj obj) {
        this.jvm.add(obj);
        return this;
    }

    @Override
    public cInt c() {
        return this.jvm.isEmpty() ? C_ZERO : C_SOME;
    }

    @Override
    public Obj take() {
        return this.jvm.isEmpty() ? null : this.jvm.removeFirst();
    }

    @Override
    public <J> J jvm() {
        return (J) this.jvm;
    }

    @Override
    public fURI tid() {
        return OBJS_TID;
    }

    @Override
    public fURI vid() {
        return this.vid;
    }

    @Override
    public <O extends Obj> O clone(final Object jvm, final fURI tid, final fURI vid) {
        final ListMonad clone = (ListMonad) this.clone();
        clone.jvm = (List<Obj>) jvm;
        clone.vid = vid;
        return (O) clone;
    }

    @Override
    public Obj clone() {
        return new ListMonad(this.jvm, this.vid);
    }

    @Override
    public cInt uniqueC() {
        return cInt.of((long) this.jvm.size());
    }


    @Override
    public ListMonad self(final Object jvm, final fURI tid, final fURI vid) {
        this.jvm = (List<Obj>) jvm;
        this.vid = vid;
        return this;

    }


    @Override
    public Iterator<Obj> iterator() {
        return this.jvm.iterator();
    }

    @Override
    public Stream<Obj> stream() {
        return this.jvm.stream();
    }

    @Override
    public String toString() {
        return Helper.objToString(this);
    }

    @Override
    public int hashCode() {
        return Helper.objHashCode(this);
    }

    @Override
    public boolean equals(final Object other) {
        return Helper.objEquals(this, other);
    }

    public static ListMonad of() {
        return new ListMonad(new ArrayList<>(), null);
    }
    
    
}
