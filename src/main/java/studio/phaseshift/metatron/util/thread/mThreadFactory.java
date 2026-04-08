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

package studio.phaseshift.metatron.util.thread;

import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mThreadFactory implements ThreadFactory {

    private final GraphittyLogger LOG = Graphitty.log(this);

    private final AtomicInteger counter = new AtomicInteger(0);
    private final Map<Integer, Thread> threads = new HashMap<>();
    private final Map<Integer, Object> results = new HashMap<>();

    public void killThread(final int threadId) {
        final Thread thread = this.threads.remove(threadId);
        if (null != thread) {
            thread.interrupt();
            LOG.info("interrupted thread {{y}}%d{{X}}", threadId);
        } else {
            LOG.warn("thread {{y}}%d{{X}} not found", threadId);
        }
    }

    public <T> Optional<T> getResult(final int threadId) {
        return Optional.ofNullable((T) this.results.remove(threadId));
    }

    public boolean isRunning(final int threadId) {
        return this.threads.containsKey(threadId);
    }


    @Override
    public Thread newThread(final Runnable runnable) {
        final Thread thread;
        if (runnable instanceof mRunnable.mVirtualRunnable) {
            thread = new mVirtualThread<>(this, (mRunnable.mVirtualRunnable<?>) runnable);
            thread.setName("m-virtual-thread-%d");
        } else if (runnable instanceof mRunnable.mCoreRunnable) {
            thread = new mCoreThread<>(this, (mRunnable.mCoreRunnable<?>) runnable);
            thread.setName("m-core-thread-%d");
        } else {
            thread = new Thread(runnable);            
            thread.setName("m-jvm-thread-%d");
        }
       
        this.threads.put(this.counter.incrementAndGet(), thread);
        return thread;
    }
}
