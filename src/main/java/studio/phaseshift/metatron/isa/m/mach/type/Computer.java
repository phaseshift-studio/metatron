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

package studio.phaseshift.metatron.isa.m.mach.type;

import studio.phaseshift.metatron.isa.m.mach.type.thread.mThread;
import studio.phaseshift.metatron.isa.m.type.Rec;

/**
 * computer::T — a computing instance: a thread (the watchdog for the chip) whose {@link #cache()} is the swappable,
 * persistable computation state. A {@code Processor} is the architecture; a {@code Computer} is the instance that
 * architecture mints ({@code processor.apply(code)}). The lifecycle is inherited from {@code mThread} verbatim —
 * {@code apply}/{@code applyAsync} start, {@code stop} interrupts, {@code pause}/{@code resume} gate — and the
 * computer adds only two things: {@link #add(mThread)} (a worker; the computer instructs it how to be useful) and
 * {@link #cache()} (the "L1 cache" — the current working set, distinct from the machine's {@code memory}).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Computer extends mThread {

    /**
     * add a worker; the computer instructs it how to be useful (swarm: run the monadic loop over the cache).
     */
    Computer add(mThread worker);

    /**
     * the swappable, persistable computation state — a single Rec addressed at {@code /processor/<vid>/cache/<field>}.
     * One key to swap, one Rec to persist.
     */
    Rec cache();
}
