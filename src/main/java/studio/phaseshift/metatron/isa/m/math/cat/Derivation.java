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

package studio.phaseshift.metatron.isa.m.math.cat;

import studio.phaseshift.metatron.isa.m.type.Code;

/**
 * A derivation — the declaration that an instruction is definable as a composition of other instructions,
 * expressed as a rewrite pair over the generic fURIs ({@code A}…{@code G}).
 *
 * <p>For example, {@code div} derives from {@code mult ∘ inv}: the {@code lhs} {@code start(A).div(B)} is the
 * derived instruction and the {@code rhs} {@code start(A).mult(inv(B))} is its primitive expansion. The
 * category only <em>declares</em> that the rule is possible; an actual rewrite instruction applies it.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public record Derivation(Code lhs, Code rhs) {
}
