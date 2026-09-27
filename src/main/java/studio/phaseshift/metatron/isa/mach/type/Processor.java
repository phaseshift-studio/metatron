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

import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.thread.mThread;

import java.util.function.Consumer;

import static studio.phaseshift.metatron.Tokens.CODE;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code0;

/**
 * Processor — runs compiled {@code code::T} to {@code obj}. A processor is the machine's execution
 * axis (a thread) <em>and</em> an {@code obj} (its state lives in a rec's jvm map), so it is both
 * an {@code mThread} and an {@code Obj}. The monad-shaped surface (run/barrier/halt queues) lives on
 * the {@code monad_processor::T} refinement, not here — a processor need not be monadic. Resolution
 * is the {@code Obj#resolve(Obj)} contract inherited from {@code Obj}, not a distinct axis.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Processor extends mThread, Machine.Component {

    /**
     * Redeclared abstract to fold the abstract {@code mThread.apply} and the default
     * {@code Obj.apply} into one contract — a processor is a callable obj, and the concrete
     * processor (a thread) supplies the single implementation.
     */
    @Override
    Obj apply(final Obj input);

    /**
     * @return the code this processor is executing
     */
    default Code code() {
        return this.at(CODE).orElse(code0());
    }

    /**
     * @return a copy of this processor with the given onHalt callback registered
     */
    Processor onHalt(final Consumer<Obj> halted);

    /**
     * @return the current onHalt callback
     */
    Consumer<Obj> onHalt();
}
