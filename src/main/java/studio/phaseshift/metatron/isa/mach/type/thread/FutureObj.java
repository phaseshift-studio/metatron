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

package studio.phaseshift.metatron.isa.mach.type.thread;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MObj;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.util.MTronException;

import java.time.Duration;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;


/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FutureObj<T extends Obj> extends MObj implements Future<T> {

    public static final int DEFAULT_TIMEOUT_MS = 2000;
    public static final fURI FUTURE_TID = M_ISA_TID.extend("future");

    private final UUID tag;
    private boolean isCanceled;

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

    @Override
    public AtomicReference<T> jvm() {
        return (AtomicReference<T>) this.jvm;
    }

    public void setObj(final T obj) {
        if (this.isCanceled)
            throw MTronException.of("future obj has already been canceled");
        ((AtomicReference<T>) this.jvm).set(obj);
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        this.jvm = fail(MTronException.of("future obj canceled"));
        return this.isCanceled = true;
    }

    @Override
    public boolean isCancelled() {
        return this.isCanceled;
    }

    @Override
    public boolean isDone() {
        return this.isCanceled || ((AtomicReference<T>) this.jvm).get() != null;
    }

    @Override
    public Iterator<Obj> iterator() {
        return this.get(DEFAULT_TIMEOUT_MS).iterator();
    }

    @Override
    public <O extends Obj> O clone(final Object jvm, final fURI tid, final fURI vid) {
        return this.get(DEFAULT_TIMEOUT_MS).clone(jvm, tid, vid);
    }

    @Override
    public <O extends Obj> O self(final Object jvm, final fURI tid, final fURI vid) {
        if (this.isDone())
            return this.get(DEFAULT_TIMEOUT_MS).self(jvm, tid, vid).self(jvm, tid, vid);
        else
            return MObjFactory.of().toObj(jvm, this.tid(), this.vid());
    }

    @Override
    public Stream<Obj> stream() {
        return this.get(DEFAULT_TIMEOUT_MS).stream();
    }

    @Override
    public boolean isNoObj() {
        return this.get(DEFAULT_TIMEOUT_MS).isNoObj();
    }

    @Override
    public boolean isObjs() {
        return this.get(DEFAULT_TIMEOUT_MS).isObjs();
    }


    @Override
    public <O extends Obj> Stream<O> elements() {
        return this.get(DEFAULT_TIMEOUT_MS).elements();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        if (this.isCanceled)
            throw new InterruptedException("future has already been canceled");
        while (true) {
            if (null != ((AtomicReference<T>) this.jvm).get())
                return ((AtomicReference<T>) this.jvm).get();
            Thread.yield();
        }
    }

    @Override
    public T get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        final long endTime = unit.convert(Duration.ofMillis(timeout)) + System.currentTimeMillis();
        while (System.currentTimeMillis() < endTime) {
            if (null != ((AtomicReference<T>) this.jvm).get()) {
                return ((AtomicReference<T>) this.jvm).get();
            }
            Thread.yield();
            // Thread.currentThread().wait(100);
        }
        return ((AtomicReference<T>) this.jvm).get();
    }

    public T get(final long timeoutMs) {
        try {
            return this.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public String toString() {
        try {
            return this.isDone() ? this.get().toString() : super.toString();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public Obj resolve(final Obj lhs) {
        return this.get(3000).resolve(lhs);
    }

    @Override
    public Obj apply(final Obj lhs) {
        return this.get(3000).apply(lhs);
    }

    public static <O extends Obj> O resolveFuture(final Obj future) {
        try {
            return future instanceof FutureObj ? ((FutureObj<O>) future).get(3000) : (O) future;
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }
}
