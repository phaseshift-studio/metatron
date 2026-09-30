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

package studio.phaseshift.metatron.isa.m.type.resolver;

import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;

/**
 * InstResolver — the resolution stage of {@code compiler::T}: lowers {@code code::T} to
 * {@code code::T} by threading the type through the instruction chain and resolving one inst at a
 * time. A resolver is a machine component ({@code resolver::T}); the concrete
 * {@code scoring_resolver::T} owns the threading algorithm and delegates each instruction to the
 * active {@link InstSelector}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Resolver extends Machine.Component {

    /**
     * Resolve {@code code} against {@code noobj} (the compile-time lhs — the element type threads
     * through the initial inst's own argument).
     *
     * @param code the code to resolve
     * @return the resolved code
     */
    Code apply(final Obj code);
}
