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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;

/**
 * MonadProcessor — a {@code Processor} that schedules monads. This is the nominal marker for the
 * monadic strategy, and the home of the three monad queues: {@code running} (the active mailbox),
 * {@code barriers} (monads parked at a barrier), and {@code halted} (the completed results). A
 * distributed monadic system is the same triple one level up — each shard owns its own run/barrier/
 * halt, and a coordinator aggregates the {@code halt} of its children.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface MonadProcessor extends Processor {

    /**
     * @return the running monad queue
     */
    Obj running();

    /**
     * @return the barrier monad queue
     */
    Lst barriers();

    /**
     * @return the collection of halted objects produced during execution
     */
    Obj halted();
}
