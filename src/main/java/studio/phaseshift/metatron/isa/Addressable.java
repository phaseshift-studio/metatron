/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.isa;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * Something a {@code vid} can be read from and written to — the one contract every data source a
 * {@link studio.phaseshift.metatron.isa.mach.type.Machine} dispatches to shares.
 * <p>
 * The machine is the gatekeeper of a read: {@code Machine.read} asks its {@code network} (authority dispatch),
 * then its {@code memory} (data memory), then its {@code instset} (instruction memory), and each of those is an
 * {@code Addressable} — the same {@code read}/{@code write} signatures, the same {@code noobj}-means-decline
 * convention, so the dispatch is a walk over uniform sources rather than three special cases. A {@link Space} is
 * the storage-shaped refinement of this contract (pattern, q-procs, routes); a component that is not a place —
 * the network, and eventually instruction memory — is an {@code Addressable} without being a {@code Space}.
 * <p>
 * The decline convention: a source that cannot answer for a vid returns {@code noobj} rather than throwing, so
 * the caller falls through to the next source. An uncovered address is an ordinary answer, not an error.
 */
public interface Addressable {

    /**
     * read the obj at {@code vid}, or {@code noobj} when this source does not answer for it.
     */
    Obj read(final fURI vid);

    /**
     * write {@code obj} at {@code vid}, or answer {@code noobj} when this source does not accept it.
     */
    Obj write(final fURI vid, final Obj obj);

    default Obj read(final String vid) {
        return this.read(f(vid));
    }

    default Obj write(final String vid, final Obj obj) {
        return this.write(f(vid), obj);
    }
}
