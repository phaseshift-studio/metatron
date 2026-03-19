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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class VThread<T> extends Thread implements Iterator<T> {
    private final VirtualThreadFactory factory;
    private final Supplier<T> runnable;
    private final List<T> result = new ArrayList<>();

    public VThread(final VirtualThreadFactory factory, final Supplier<T> runnable) {
        this.factory = factory;
        this.runnable = runnable;
    }

    @Override
    public void run() {
        while (!this.isInterrupted()) {
            this.result.add(this.runnable.get());
        }
    }
    
    @Override
    public boolean hasNext() {
        return this.result != null && !this.result.isEmpty();
    }

    @Override
    public T next() {
        if (this.result == null || this.result.isEmpty())
            return null;
        return this.result.getFirst();
    }
}
