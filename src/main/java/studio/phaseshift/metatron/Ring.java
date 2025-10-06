/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

package studio.phaseshift.metatron;

/*
@author Marko A. Rodriguez (http://markorodriguez.com)
*/
public interface Ring<R extends Ring<R>> {

    R plus(final R r);

    R mult(final R r);

    R neg();

    default R minus(final R r) {
        return this.plus(r.neg());
    }


    R one();

    R zero();

    default boolean isZero() {
        return this.equals(this.zero());
    }

    default boolean isOne() {
        return this.equals(this.one());
    }

}
