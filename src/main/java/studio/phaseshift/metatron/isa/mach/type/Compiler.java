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

import studio.phaseshift.metatron.isa.m.type.Rec;

/**
 * Compiler — lowers {@code code::T} to {@code code::T} against an {@code instset::T} (the machine's
 * ISA). A compiler is a pure, stateless rec: no lifecycle, no execution — its whole contract is the
 * {@code apply(code) -> code} lowering. Concrete strategies (e.g. {@code fixpoint_compiler::T})
 * refine it.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Compiler extends Rec {
    // marker — the lowering contract is the Rec's apply(code::T) -> code::T
}
