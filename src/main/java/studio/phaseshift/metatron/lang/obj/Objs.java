/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Collection;

public interface Objs extends Obj {

    @Override
    Objs clone(final Object value, final fURI tid, final fURI vid);

    @Override
    Iterable<Obj> value();

    @Override
    default Objs append(final Obj obj){
        if(obj.isNoObj())
            return this;
        if(this.value() instanceof Collection<?>) {
            obj.iterator().forEachRemaining(o -> this.<Collection<Obj>>valueAs().add(o));
            return this;
        } else {
            throw MTronException.of("unable to add to underlying iterable: %s", this.value().getClass());
        }
    }

}