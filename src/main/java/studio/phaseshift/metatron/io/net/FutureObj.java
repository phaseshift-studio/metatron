/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.io.net;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.mtron.MObj;
import studio.phaseshift.metatron.util.MTronException;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.space.Space.MTRON_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FutureObj<T extends Obj> extends MObj implements Future<T> {

    public static final fURI FUTURE_TID = MTRON_TID.extend("future");

    private final String tag;
    private boolean isCanceled;

    public FutureObj(final String tag) {
        super(new AtomicReference<T>(), FUTURE_TID, fURI.NULL);
        this.tag = tag;
        this.isCanceled = false;
    }

    public String tag() {
        return this.tag;
    }

    public void setObj(final T obj) {
        if (this.isCanceled)
            throw MTronException.of("future obj has already been canceled");
        this.jvm().set(obj);
    }

    @Override
    public AtomicReference<T> jvm() {
        return (AtomicReference<T>) this.jvm;
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        return this.jvm().get() == null && (this.isCanceled = true);
    }

    @Override
    public boolean isCancelled() {
        return this.isCanceled;
    }

    @Override
    public boolean isDone() {
        return this.isCanceled || this.jvm != null;
    }


    @Override
    public T get() throws InterruptedException, ExecutionException {
        if (this.isCanceled)
            throw new InterruptedException("future has already been canceled");
        if (null == this.jvm().get())
            throw new ExecutionException(MTronException.of("future obj isn't manifest"));
        return this.jvm().get();
    }

    @Override
    public T get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        final long endTime = unit.convert(Duration.ofMillis(timeout)) + System.currentTimeMillis();
        while (System.currentTimeMillis() < endTime) {
            if (null != this.jvm().get()) {
                return this.jvm().get();
            }
           // Thread.currentThread().wait(100);
        }
        return this.jvm().get();
    }

    public T get(final long timeoutMs) {
        try {
            return this.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

}
