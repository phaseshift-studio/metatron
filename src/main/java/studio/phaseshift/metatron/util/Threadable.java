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

package studio.phaseshift.metatron.util;

import java.io.Closeable;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Threadable extends Closeable {

    Thread getThread();

    @Override
    default void close() {
        this.interrupt();
    }

    default void start() {
        if (null != this.getThread())
            this.getThread().start();
        else throw MTronException.of(new IllegalStateException("no thread available"));
    }

    default void interrupt() {
        if (null != this.getThread())
            this.getThread().interrupt();
        else throw MTronException.of(new IllegalStateException("no thread available"));
    }

    default void join() {
        if (null != this.getThread()) {
            try {
                this.getThread().join();
            } catch (final InterruptedException e) {
                throw MTronException.of(e);
            }
        }
    }
}