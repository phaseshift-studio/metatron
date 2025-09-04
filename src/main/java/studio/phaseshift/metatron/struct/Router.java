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

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.Palette;
import studio.phaseshift.metatron.ui.Graphitty;

public interface Router extends BObj.Obj {

    fURI ROUTER_TID = fURI.of("router");

    static Router global() {
        return BootLoader.ROUTER;
    }

    @Override
    default fURI tid() {
        return ROUTER_TID;
    }

    BObj.Obj read(final fURI vid);

    BObj.Obj write(final fURI vid, final BObj.Obj obj);

    void registerStruct(final Struct struct);

    default String toString(final Palette palette) {
        return Graphitty.string("!b%s!g:[!yrouter!g]!!".formatted(this.tid().toString()));
    }
}
