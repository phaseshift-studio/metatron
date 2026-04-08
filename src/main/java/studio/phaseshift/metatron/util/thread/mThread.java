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

package studio.phaseshift.metatron.util.thread;

import com.google.common.util.concurrent.AbstractFuture;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class mThread<T> extends Thread implements Future<T> {

    protected final mThreadFactory factory;
    protected final Runnable runnable;
    protected final AtomicReference<T> result = new AtomicReference<>();

    protected mThread(final mThreadFactory factory, final mRunnable<T> runnable) {
        this.factory = factory;
        this.runnable = () -> {
          runnable.get();
        };
    }

    public boolean isCoreThread() {
        return !this.isVirtualThread();
    }
    
    public void run() {
        
    }

    public abstract boolean isVirtualThread();

  
//    void interrupt();

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
        //  this.runnable.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
       return this.factory.isRunning(this.runnable.hashCode());
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        while(null == this.result.get()) {
            Thread.sleep(1);
        }
        return null;
       // return this.runnable.get();
    }

    @Override
    public T get(long timeout, @NonNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return null;
    }
}
