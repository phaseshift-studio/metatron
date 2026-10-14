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
     * The JURISDICTION: the abstract pattern of addresses this source answers for. A {@link Space}'s is a path
     * (`/usr/#`), an instruction set's is its tid prefix (`/m/math/cat/#`), a network's is the authorities it can
     * reach. Dispatch anywhere is "the most specific jurisdiction covering the vid" — one algorithm over uniform
     * sources.
     */
    fURI pattern();

    /**
     * INDEX a constructed obj into this source — the one verb for "a thing that exists enters the
     * jurisdiction": Memory indexes a space, Network indexes a protocol client, an instruction set indexes
     * an inst family. Construction is decoupled from infrastructure indexing (an obj exists whether or not
     * an index observed it), so indexing is a BACKGROUND behavior upon successful construction and this
     * method must never be load-bearing for the obj's own validity. Default: this source does not index.
     */
    default Obj index(final Obj member) {
        return studio.phaseshift.metatron.isa.m.type.NoObj.noobj();
    }

    /**
     * DROP a member from this source — the dual of {@link #index}: {@code id -> noobj}, the universal
     * close. What indexing granted, dropping retracts: Memory drops a space's claim, Network hangs up a
     * peer. Answers what was dropped, or {@code noobj} when the id held nothing here. Default: this
     * source drops nothing.
     */
    default Obj drop(final fURI id) {
        return studio.phaseshift.metatron.isa.m.type.NoObj.noobj();
    }

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
