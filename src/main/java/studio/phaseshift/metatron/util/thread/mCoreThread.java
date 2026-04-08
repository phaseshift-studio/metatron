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

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mCoreThread<T> extends mThread<T> {

    public mCoreThread(final mThreadFactory factory, final mRunnable.mCoreRunnable<T> runnable) {
        super(factory, runnable);
    }

    @Override
    public void run() {
        this.runnable.run();
    }

    @Override
    public boolean isVirtualThread() {
        return false;
    }
}


