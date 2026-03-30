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

package studio.phaseshift.metatron.isa.mach.type.machine;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MObjs;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Monad;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractMachine implements Machine {

    protected final GraphittyLogger LOG = Graphitty.log(this);
    public static Supplier<Obj> RUNNING_SUPPLIER = ListMonad::of;

    Code code;
    Obj running;
    Lst barriers;
    Obj halted;
    fURI tid;
    fURI vid;
    Consumer<Obj> onHalt;
    final AtomicBoolean interrupted = new AtomicBoolean(false);
    final AtomicBoolean paused = new AtomicBoolean(false);
    final AtomicInteger infiniteFailCounter = new AtomicInteger(0);

    @Override
    public Map<Obj, Obj> jvm() {
        return Map.of(uri(CODE), this.code, uri(RUNNING), this.running, uri(BARRIER), this.barriers, uri(HALTED), this.halted);
    }

    @Override
    public Obj clone() {
        return this;
    }

    @Override
    public <OBJ extends Obj> OBJ self(Object jvm, fURI tid, fURI vid) {
        final Map<Obj, Obj> map = (Map<Obj, Obj>) jvm;
        this.code = map.get(uri(CODE)).as();
        this.running = map.getOrDefault(uri(RUNNING), RUNNING_SUPPLIER.get());
        this.barriers = map.getOrDefault(uri(BARRIER), lst(new LinkedList<>())).as();
        this.halted = map.getOrDefault(uri(HALTED), MObjs.objs0());
        this.tid = tid;
        this.vid = vid;
        return (OBJ) this;
    }


    protected boolean doProcessing() {
        return !this.interrupted.get() && !this.paused.get();
    }

    @Override
    public Fail interrupt() {
        this.interrupted.set(true);
        this.paused.set(false);
        return fail(MTronException.of("machine interrupted"));
    }

    @Override
    public NoObj pause() {
        if (this.interrupted.get())
            throw MTronException.of("cannot pause interrupted machine");
        this.paused.set(true);
        return noobj();
    }

    @Override
    public Obj run() {
        if (this.interrupted.get())
            throw MTronException.of("cannot resume interrupted machine");
        this.paused.set(false);
        return this.apply();
    }

    protected AbstractMachine(final Map<Obj, Obj> map, final fURI tid, final fURI vid) {
        this.code = map.get(uri(CODE)).as();
        this.tid = tid;
        this.vid = vid;
        this.onHalt = o -> {
            if(this.halted.isObjs())
                ((List<Obj>)this.halted.asObjs().jvm()).add(o);
            else
                this.halted.append(o);
        };
        this.running = map.getOrDefault(uri(RUNNING), RUNNING_SUPPLIER.get());
        this.barriers = map.getOrDefault(uri(BARRIER), lst(new LinkedList<>())).as();
        this.halted = map.getOrDefault(uri(HALTED), MObjs.objs0());
    }

    @Override
    public Obj halted() {
        return this.halted;
    }

    @Override
    public Lst barriers() {
        return this.barriers;
    }

    @Override
    public Obj running() {
        return this.running;
    }

    @Override
    public Code code() {
        return this.code;
    }

    @Override
    public fURI tid() {
        return this.tid;
    }

    @Override
    public fURI vid() {
        return this.vid;
    }

    public Machine onHalt(final Consumer<Obj> onHalt) {
        this.onHalt = onHalt;
        return this;
    }

    public Consumer<Obj> onHalt() {
        return this.onHalt;
    }

    @Override
    public Machine clone(final Object jvm, final fURI tid, final fURI vid) {
        try {
            final AbstractMachine clone = (AbstractMachine) super.clone();
            clone.tid = tid;
            clone.vid = vid;
            clone.code = ((Map<Obj, Obj>) jvm).get(uri(CODE)).as();
            clone.running = ((Map<Obj, Obj>) jvm).getOrDefault(uri(RUNNING), RUNNING_SUPPLIER.get());
            clone.barriers = ((Map<Obj, Obj>) jvm).getOrDefault(uri(BARRIER), lst(new LinkedList<>())).as();
            clone.halted = ((Map<Obj, Obj>) jvm).getOrDefault(uri(HALTED), MObjs.objs0());
            return clone;
        } catch (final CloneNotSupportedException e) {
            throw MTronException.of(e);
        }
    }

    protected Monad split(final Monad monad) {
        if (monad.obj().unique() && (monad.inst().dom().c().isOne() || monad.inst().dom().c().isAny()))
            return monad;
        if (monad.inst().dom().c().isZero() && !monad.obj().c().isZeroable()) // TODO: create a less than window c.ltFull()
            throw MTronException.of("monad obj coefficient is greater than inst domain coefficient: " +
                    "\n\tobj       => %s" +
                    "\n\t\\_c       => %s" +
                    "\n\tinst     X=> %s" +
                    "\n\t\\_dom_c  X=> %s", monad.obj(), monad.obj().c(), monad.inst(), monad.inst().dom().c());
        final Tuple.Pair<Obj, Obj> pair =
                monad.obj().c().gte(monad.inst().dom().c()) ?
                        monad.obj().take(monad.inst().dom().c().most()) :
                        monad.obj().take(monad.obj().c().most());
        if (!pair.get1().isNoObj())
            this.running().append(monad.obj(pair.get1()));
        LOG.trace("{{g}}=>{{/g}} splitting monad %s / %s (inst: %s)", pair.get0(), pair.get1(), monad.inst());
        return monad.obj(pair.get0());
    }

    public String toString() {
        return Obj.Helper.objToString(this);
    }

    public int hashCode() {
        return Obj.Helper.objHashCode(this);
    }

    public boolean equals(Object other) {
        return Obj.Helper.objEquals(this, other);
    }
}
