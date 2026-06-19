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

package studio.phaseshift.metatron.isa.mach.type.thread;

import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;

import java.util.concurrent.TimeUnit;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/*
 * mThread — the unified thread lifecycle contract.
 *
 * Both {@code AbstractThread} (and its subclasses {@code VirtualThread},
 * {@code CoreThread}) and {@code Machine} (and its subclasses like
 * {@code SwarmMachine}) share this contract.  A thread is a callable
 * object with a lifecycle: it can be started, paused, resumed, and
 * stopped, and it yields a result.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface mThread {

    // ======================== Lifecycle ========================

    /**
     * Stop this thread.  After this call, the thread is in the STOP state
     * and cannot be resumed.  Any underlying Java thread is interrupted.
     */
    void stop();

    /**
     * Pause this thread at the next cycle boundary.  No-op if the thread
     * is already stopped.  Only effective for looping/iterating threads.
     */
    void pause();

    /**
     * Resume a paused thread.  No-op if the thread is already running.
     */
    void resume();

    // ======================== State ========================

    /**
     * @return the current lifecycle state (RUN, PAUSE, or STOP)
     */
    Uri state();

    // ======================== Execution ========================

    /**
     * Synchronously call this thread with the given input and return the
     * result.  Blocks until execution completes.
     *
     * @param input the input object
     * @return the result of execution
     */
    Obj apply(final Obj input);

    /**
     * Asynchronously call this thread with the given input.  Returns a
     * {@code FutureObj} immediately; the caller can block on
     * {@link FutureObj#get()} or use {@link #result()} to wait for
     * completion.
     *
     * @param input the input object
     * @return a future that will hold the result
     */
    FutureObj<Obj> applyAsync(final Obj input);

    /**
     * Asynchronously call this thread with {@code noobj()} as input.
     * Convenience for {@code applyAsync(noobj())}.
     *
     * @return a future that will hold the result
     */
    default FutureObj<Obj> applyAsync() {
        return this.applyAsync(noobj());
    }

    // ======================== Result ========================

    /**
     * @return the result of the last execution, or noobj if none
     */
    Obj result();

    /**
     * Block for up to {@code timeout} {@code unit}s waiting for a result.
     * Throws {@code MTronException} on timeout.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @return the result object
     */
    Obj result(final long timeout, final TimeUnit unit);

    // ======================== Source ========================

    /**
     * @return the Obj (thread) that spawned this thread, or noobj if none
     */
    Obj source();

    // ======================== Future ========================

    /**
     * @return the {@code FutureObj} backing this thread's result
     */
    FutureObj<Obj> future();
}
