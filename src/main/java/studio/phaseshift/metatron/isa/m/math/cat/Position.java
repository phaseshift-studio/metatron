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

/**
 * The position axis: the six relations an edge takes part in, derived from its endpoints' place in
 * the graph. Enum constants are lowercase so {@code name()} is the mtron label.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public enum Position {
    /**
     * another inst of the same family maps the same dom to the same rng — a redundant registration
     */
    duplicate,
    /**
     * contested by an incomparable sibling that overlaps — dispatch has no most-specific winner
     */
    ambiguous,
    /**
     * contested by an incomparable, disjoint sibling — dispatch stays total
     */
    incomparable,
    /**
     * an opposing edge exists — a candidate inverse pair
     */
    coupling,
    /**
     * endpoints linked by a chain of couplings — one orbit of the reversible core
     */
    isochain,
    /**
     * mutually reachable and round trip idempotent — a subobject retraction
     */
    retract;
}
