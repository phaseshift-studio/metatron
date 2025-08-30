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

import java.util.Iterator;
import java.util.Map;

import static studio.phaseshift.metatron.lang.obj.BObj.*;

public interface Struct extends Poly {

    @Override
    Map<fURI, Obj> value();

    Obj read(final fURI addr);

    void write(final fURI addr, final Obj obj);

    void append(final fURI addr, final Obj... obj);


    @Override
    default long length() {
        return this.value().size();
    }

    @Override
    default Iterator<Obj> iterator() {
        return this.value().entrySet().stream().map(kv -> kv.getValue().vid(kv.getKey())).iterator();
    }
}
