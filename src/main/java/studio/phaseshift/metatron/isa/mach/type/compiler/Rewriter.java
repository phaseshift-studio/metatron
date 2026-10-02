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

package studio.phaseshift.metatron.isa.mach.type.compiler;

import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;

/**
 * Rewriter — the first stage of {@code compiler::T}: lowers {@code code::T} to {@code code::T} by
 * applying rewrite rules to a fixpoint. A rewriter is a machine component ({@code rewriter::T}); the
 * concrete {@code fixpoint_rewriter::T} carries its convergence window as the {@code max} rec entry.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Rewriter extends Machine.Component {

    /**
     * Rewrite {@code code} to a fixpoint over the applicable rewrite rules.
     *
     * @param code the code to rewrite
     * @return the rewritten code
     */
    Code apply(final Obj code);
}
