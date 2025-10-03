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

package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.lang.fURI;

import java.util.List;

public interface Lst extends Poly {

    @Override
    Lst clone(final Object value, final fURI tid, final fURI vid);

    @Override
    List<Obj> value();

    @Override
    default long count() {
        return this.value().size();
    }

    @Override
    default Iterable<Obj> elements() {
        return this.value();
    }

    @Override
    default <O extends Obj> O at(final Obj key) {
        if (key.isInt())
            return (O) this.value().get(key.<Int>as().intValue().intValue());
        else {//if(key.isUri()) {
            throw new RuntimeException("bad");
            //  return NoObj.single();
        }
    }

}