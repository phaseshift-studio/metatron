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

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Palette;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.Iterator;
import java.util.Map;

import static studio.phaseshift.metatron.lang.obj.BObj.Obj;
import static studio.phaseshift.metatron.lang.obj.BObj.Poly;

public interface Struct extends Poly {

    @Override
    Map<Obj, Obj> value();

    fURI pattern();

    Obj read(final fURI addr);

    Obj write(final fURI addr, final Obj obj);

    void append(final fURI addr, final Obj... obj);

    @Override
    default Obj vid(final fURI vid) {
        throw new IllegalStateException("structs must umount to change value id (vid)");
    }

    default String toString(final Palette palette) {
        return Graphitty.parse("!b%s!g:[!ypattern!g=>!y%s!g]!!".formatted(this.tid().toString(), this.pattern().toString()));
    }

    @Override
    default Obj clone() {
        throw new IllegalStateException(new CloneNotSupportedException("structs can not be cloned"));
    }
}
