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

package studio.phaseshift.metatron.isa.m.type.impl;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Monad;
import studio.phaseshift.metatron.isa.sys.type.Router;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.m.mInstSet.OBJS_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RunningMonads implements Obj {

    protected Map<Inst, Monad> instIndex = new ConcurrentHashMap<>();

    public RunningMonads(final Iterable<Monad> monads) {
        monads.forEach(this::append);
    }

    public static RunningMonads of() {
        return new RunningMonads(List.of());
    }

    public static RunningMonads of(final Iterable<Monad> monads) {
        return new RunningMonads(monads);
    }

    @Override
    public RunningMonads append(final Obj monad) {
        assert monad instanceof Monad;
        monad.forEach(o -> this.instIndex.compute(o.<Monad>as().inst(), (inst, value) -> null == value ? o.as() : value.obj(value.obj().append(o.<Monad>as().obj()))));
        Router.global().stats().incrRunningMonads(1L);
        return this;
    }


    @Override
    public cInt c() {
        return this.instIndex.values().stream().map(Monad::obj).map(Obj::c).reduce(cInt.ZERO(), cInt::plus);
    }

   /* @Override
    public Obj c(final Function<cInt, cInt> func) {
        // throw MTronException.of("can not update the c of an objs programmatically: %s", this);
        this.instIndex.values().forEach(obj -> this.instIndex.computeIfPresent(obj, (k, v) -> v.c(func.apply(v.c()))));
        return this;
    }*/

    @Override
    public Monad take() {
        if (this.instIndex.isEmpty())
            return null;
        for (final Inst key : this.instIndex.keySet()) {
            final Monad value = this.instIndex.remove(key);
            Router.global().stats().incrRunningMonads(-1L);
            return value;
        }
        return null;
    }

    @Override
    public cInt uniqueC() {
        return cInt.of((long) this.instIndex.size());
    }


    @Override
    public Iterable<Monad> jvm() {
        return this.instIndex.values();
    }

    @Override
    public fURI tid() {
        return OBJS_TID;
    }

    @Override
    public fURI vid() {
        return null;
    }

    @Override
    public RunningMonads clone(final Object jvm, final fURI tid, final fURI vid) {
        return new RunningMonads((Iterable<Monad>) jvm);
    }

    @Override
    public RunningMonads clone() {
        try {
            return (RunningMonads) super.clone();
        } catch (final CloneNotSupportedException e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public RunningMonads self(Object jvm, fURI tid, fURI vid) {
        return this;
    }


    @Override
    public Iterator<Obj> iterator() {
        return (Iterator) this.instIndex.values().iterator();
    }

    @Override
    public Stream<Obj> stream() {
        return (Stream) this.instIndex.values().stream();
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
}
