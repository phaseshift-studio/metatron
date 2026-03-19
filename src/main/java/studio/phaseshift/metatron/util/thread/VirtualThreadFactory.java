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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class VirtualThreadFactory implements ThreadFactory {

    private Map<String,Thread> threads = new HashMap<>();
    
    public Thread storeThread(final String name, final VThread<?> thread) {
        return this.threads.put(name, thread);
    }
    
    public Thread getThread(final String name) {
        return this.threads.get(name);
    }
    
    public void killThread(final String name) {
        final Thread thread = this.threads.remove(name);
        if (null != thread)
            thread.interrupt();
    }
 

   @Override
    public Thread newThread(final Runnable runnable) {
        final Thread thread = new VThread<>(this, runnable instanceof Supplier ? (Supplier<?>) runnable : () -> {
            runnable.run();
            return null;
        });
        thread.setName("metatron-virtual-%d");
        this.storeThread(thread.getName(), (VThread<?>) thread);
        return thread;
    }
}
