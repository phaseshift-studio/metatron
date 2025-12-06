/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.lang.db.vec.type;

import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Poly;

import java.util.Vector;
import java.util.function.BiFunction;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Vec extends Poly<Vec, Vector<?>> {

    @Override
    default Vec at(final Obj key, final Obj value, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
        return this;
    }
}
