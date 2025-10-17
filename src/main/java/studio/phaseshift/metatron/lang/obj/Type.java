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
 */

package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.algebra.Ring;
import studio.phaseshift.metatron.algebra.Semiring;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.mtron.MType;
import studio.phaseshift.metatron.lang.obj.mtron.c.cInt;

public interface Type extends Obj, Semiring<Type> {

    @Override
    Type clone(final Object value, final fURI tid, final fURI vid);

    @Override
    Obj value();

    @Override
    default Type dom() {
        return this;
    }

    @Override
    default Type rng() {
        return this;
    }

    @Override
    default Obj clone() {
        return null;
    }

    @Override
    default fURI tid() {
        return null;
    }

    @Override
    default fURI vid() {
        return null;
    }

    @Override
    default Obj apply(final Obj obj) {
       // if (!obj.rng().tid().matches(this.tid()))
       //     return NoObj.single();
        return null == this.value() || obj.matches(this.value().apply(obj)) ?
                obj :
                NoObj.single();
    }

    @Override
    default Type plus(final Type other) {
        if (this.isNoObj())
            return other;
        if (other.isNoObj())
            return this;
        fURI t = this.tid().plus(other.tid());
        Obj value = null == this.value() ? other.value() : null == other.value() ? this.value() : this.value().<Call>as().plus(other.value().<Call>as());
        return this.tid(t).value(value);
    }

    @Override
    default Type zero() {
        return this.tid(this.tid().zero()).value(null);
    }
}
