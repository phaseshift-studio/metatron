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

package studio.phaseshift.metatron.furi;

import studio.phaseshift.metatron.lang.mtron.type.Obj;

import java.util.Optional;

public interface Q extends Obj {

    @Override
    fURI jvm();

    Optional<OnWrite> onWrite();

    Optional<OnRead> onRead();

    interface OnWrite {
        default Optional<Obj> preWrite(final fURI source, final fURI vid, final Obj obj) {
            return Optional.empty();
        }

        default Optional<Obj> postWrite(final fURI source, final fURI vid, final Obj oldObj, final Obj newObj) {
            return Optional.empty();
        }

        default Optional<Obj> qlessWrite(final fURI source, final fURI vid, final Obj obj) {
            return Optional.empty();
        }
    }

    interface OnRead {
        default Optional<Obj> preRead(final fURI source, final fURI vid) {
            return Optional.empty();
        }

        default Optional<Obj> postRead(final fURI source, final fURI vid, final Obj obj) {
            return Optional.empty();
        }
    }

}
