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

package studio.phaseshift.metatron.struct;

import org.javatuples.Pair;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.ui.Palette;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.Optional;
import java.util.function.Predicate;

import static studio.phaseshift.metatron.lang.obj.BObj.Obj;
import static studio.phaseshift.metatron.lang.obj.BObj.Poly;

public interface Struct extends Poly {

    @Override
    Object value();

    fURI pattern();

    Obj read(final fURI addr);

    Obj write(final fURI addr, final Obj obj);

    void append(final fURI addr, final Obj... obj);

    @Override
    default Obj vid(final fURI vid) {
        throw new IllegalStateException("structs must umount to change value id (vid)");
    }

    default String toString(final Palette palette) {
        return Graphitty.string("{{b}}%s{{g}}:[{{y}}pattern{{g}}=>{{y}}%s{{g}}]{{X}}".formatted(this.tid().toString(), this.pattern().toString()));
    }

    @Override
    default Obj clone() {
        throw new IllegalStateException(new CloneNotSupportedException("structs can not be cloned"));
    }

    @Override
    default Obj get(final int index) {
        return BObj.NoObj.of();
    }

    default Optional<Pair<fURI, Poly>> locateBasePoly(final fURI furi, Predicate<Obj> polyFilter) {
        if (null == polyFilter)
            polyFilter = o -> true;
        fURI newFuri = furi.clone();
        BObj.Obj obj = BObj.NoObj.of();
        while (!newFuri.segments().isEmpty()) {
            obj = this.read(newFuri);
            if (!obj.isNoObj() && polyFilter.test(obj)) // obj->is_poly() || obj->is_objs() || obj->is_code())
                break;
            newFuri = newFuri.retract();
        }
        return obj.isPoly() || obj.isObjs() ? Optional.of(Pair.with(newFuri, obj.<Poly>as())) : Optional.empty();
    }
}
