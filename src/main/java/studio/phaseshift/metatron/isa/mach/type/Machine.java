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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.thread.mThread;

import java.util.Map;
import java.util.function.Consumer;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.ALL_STAR;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MONAD_TID;

/*
 * Machine — a thread of execution that processes code through a monadic
 * step-loop with barriers, halted collection, and lifecycle control.
 *
 * A Machine IS-A {@code mThread}: it can be started, paused, resumed, and
 * stopped.  The machine's state (code, running queue, barriers, halted
 * objects) lives in its jvm map, making it fully queryable through the
 * mtron URI graph.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Machine extends mThread {

    Type MACH_MACHINE_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(MACH_MACHINE_TID)
            .isaPredicate(rec(
                    uri(HALTED), T(ALL_STAR),
                    uri(RUN), T(MACH_MONAD_TID.maybeSome()),
                    uri(BARRIER), LST_TYPE))
            .create();

    // ======================== Machine state ========================

    /**
     * @return the machine state as a jvm map (code, running, barriers, halted)
     */
    Map<Obj, Obj> jvm();

    /**
     * @return the code this machine is executing
     */
    Code code();

    /**
     * Return a copy of this machine with the given code substituted.
     */
    Machine code(final Code code);

    /**
     * @return the running monad queue
     */
    Obj running();

    /**
     * @return the barrier monad queue
     */
    Lst barriers();

    /**
     * @return the collection of halted objects produced during execution
     */
    Obj halted();

    // ======================== Halt callback ========================

    /**
     * Register a callback invoked for each halted object.
     */
    Machine onHalt(final Consumer<Obj> halted);

    /**
     * @return the current onHalt callback
     */
    Consumer<Obj> onHalt();

    // ======================== Resolution ========================

    /**
     * Resolve the machine's code against the given input, creating initial
     * and barrier monads in the running/barriers queues.
     */
    Machine resolve(final Obj lhs);

    // ======================== Cloning ========================

    Machine clone(final Object jvm, final fURI tid, final fURI vid);
}
