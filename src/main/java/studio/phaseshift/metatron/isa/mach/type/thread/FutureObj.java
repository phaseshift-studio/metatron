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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MObj;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_TID;


/*
 * A cancellable, blocking future backed by an {@link AtomicReference}.
 * <p>
 * The value slot is an {@code AtomicReference<T>} stored in the inherited
 * {@link #jvm} field.  Blocking operations use {@code synchronized (this)}
 * with {@link Object#wait()} / {@link Object#notifyAll()} rather than
 * busy-spinning, so a blocked {@code get()} does not consume CPU.
 * <p>
 * Cancellation sets the {@code volatile} flag {@code isCanceled} and
 * wakes all waiters; it does <em>not</em> replace the {@code jvm} slot,
 * preserving the type invariant that {@code jvm} is always an
 * {@code AtomicReference}.
 *
 * @param <T> the Obj type held by this future
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FutureObj<T extends Obj> extends MObj implements Future<T> {

    public static final int DEFAULT_TIMEOUT_MS = 2000;
    public static final fURI FUTURE_TID = M_ISA_TID.extend("future");

    private final UUID tag;
    private volatile boolean isCanceled;
    private int timeout = DEFAULT_TIMEOUT_MS;

    public FutureObj(final UUID tag) {
        super();
        this.jvm = new AtomicReference<T>();
        this.tid = FUTURE_TID;
        this.vid = null;
        this.tag = tag;
        this.isCanceled = false;
    }

    public UUID tag() {
        return this.tag;
    }

    public FutureObj<T> timeout(final int millis) {
        this.timeout = millis;
        return this;
    }

    @Override
    public AtomicReference<T> jvm() {
        return (AtomicReference<T>) this.jvm;
    }

    /**
     * Sets the result value and wakes all threads blocked in {@link #get()}.
     * Must not be called after {@link #cancel(boolean)}.
     */
    public void setObj(final T obj) {
        synchronized (this) {
            if (this.isCanceled)
                throw MTronException.of("future obj has already been canceled");
            ((AtomicReference<T>) this.jvm).set(obj);
            this.notifyAll();
        }
    }

    /**
     * Cancels this future.  Wakes all blocked {@code get()} callers so
     * they receive a {@link CancellationException}.  Idempotent: returns
     * {@code false} if already canceled.
     */
    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        synchronized (this) {
            if (this.isCanceled)
                return false;
            this.isCanceled = true;
            this.notifyAll();
            return true;
        }
    }

    @Override
    public boolean isCancelled() {
        return this.isCanceled;
    }

    @Override
    public boolean isDone() {
        return this.isCanceled || ((AtomicReference<T>) this.jvm).get() != null;
    }

    // ---- Delegating methods: resolve the future, then delegate ----

    @Override
    public Iterator<Obj> iterator() {
        return this.get(this.timeout).iterator();
    }

    @Override
    public <O extends Obj> O clone(final Object jvm, final fURI tid, final fURI vid) {
        return this.get(this.timeout).clone(jvm, tid, vid);
    }

    @Override
    public <O extends Obj> O self(final Object jvm, final fURI tid, final fURI vid) {
        if (this.isDone())
            return this.get(this.timeout).self(jvm, tid, vid);
        else
            return MObjFactory.of().toObj(jvm, this.tid(), this.vid());
    }

    @Override
    public Stream<Obj> stream() {
        return this.get(this.timeout).stream();
    }

    @Override
    public boolean isNoObj() {
        return this.get(this.timeout).isNoObj();
    }

    @Override
    public boolean isObjs() {
        return this.get(this.timeout).isObjs();
    }

    @Override
    public <O extends Obj> Stream<O> elements() {
        return this.get(this.timeout).elements();
    }

    // ---- Blocking get variants ----

    /**
     * Blocks until a result is available or this future is canceled.
     * Uses {@link Object#wait()} inside a {@code synchronized} block so
     * the waiting thread does not consume CPU.
     *
     * @throws InterruptedException  if the waiting thread is interrupted
     * @throws CancellationException if the future was canceled
     * @throws ExecutionException    reserved for future use
     */
    @Override
    public T get() throws InterruptedException, ExecutionException {
        synchronized (this) {
            if (this.isCanceled)
                throw new CancellationException("future has been canceled");
            while (null == ((AtomicReference<T>) this.jvm).get()) {
                if (this.isCanceled)
                    throw new CancellationException("future was canceled while waiting");
                this.wait();
            }
            return ((AtomicReference<T>) this.jvm).get();
        }
    }

    /**
     * Blocks up to the given timeout for a result.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of {@code timeout}
     * @throws TimeoutException      if the timeout elapses with no result
     * @throws CancellationException if the future was canceled
     */
    @Override
    public T get(final long timeout, final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        final long timeoutMs = unit.toMillis(timeout);
        final long endTime = System.currentTimeMillis() + timeoutMs;
        synchronized (this) {
            if (this.isCanceled)
                throw new CancellationException("future has already been canceled");
            while (null == ((AtomicReference<T>) this.jvm).get()) {
                if (this.isCanceled)
                    throw new CancellationException("future was canceled while waiting");
                final long remaining = endTime - System.currentTimeMillis();
                if (remaining <= 0)
                    throw new TimeoutException("future get timed out after " + timeoutMs + " ms");
                this.wait(remaining);
            }
            return ((AtomicReference<T>) this.jvm).get();
        }
    }

    /**
     * Convenience: blocking get with a millisecond timeout.
     * Wraps checked exceptions in {@link MTronException}.
     */
    public T get(final long timeoutMs) {
        try {
            return this.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    // ---- Object overrides ----

    @Override
    public String toString() {
        try {
            return this.isDone() ? this.get(this.timeout).toString() : super.toString();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public Obj resolve(final Obj lhs) {
        return this.get(this.timeout).resolve(lhs);
    }

    @Override
    public Obj apply(final Obj lhs) {
        return this.get(this.timeout).apply(lhs);
    }

    /**
     * Unwraps a {@code FutureObj} to its resolved value if the given
     * object is a future, otherwise returns it as-is.
     */
    public static <O extends Obj> O resolveFuture(final Obj future) {
        try {
            return future instanceof FutureObj ? ((FutureObj<O>) future).get(DEFAULT_TIMEOUT_MS) : (O) future;
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }
}
