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

/**
 * Typer — the final stage of {@code compiler::T}: the runtime type-assertion pass over
 * {@code code::T}. A typer is a machine component ({@code typer::T}) whose rec flags
 * ({@code inst_dom}, {@code inst_rng}, {@code type_pred}) select which assertions run per machine,
 * replacing the global {@code TypeCheck} registry.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Typer extends Machine.Component {

    /**
     * Type-check {@code code} according to this typer's enabled assertions.
     *
     * @param code the code to type-check
     * @return the type-checked code
     */
    Code apply(Obj code);
}
