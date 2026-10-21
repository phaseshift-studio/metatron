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

import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.MTronException;

import static studio.phaseshift.metatron.Tokens.QPROC;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableList;

/**
 * Something a {@code vid} can be read from and written to — the one contract every data source a
 * {@link studio.phaseshift.metatron.isa.mach.type.Machine} dispatches to shares.
 * <p>
 * The machine is the gatekeeper of a read: {@code Machine.read} asks its {@code network} (authority dispatch),
 * then its {@code memory} (data memory), then its {@code instset} (instruction memory), and each of those is an
 * {@code Addressable} — the same {@code read}/{@code write} signatures, the same {@code noobj}-means-decline
 * convention, so the dispatch is a walk over uniform sources rather than three special cases. A {@link Space} is
 * the storage-shaped refinement of this contract (pattern, routes); a component that is not a place —
 * the network, and eventually instruction memory — is an {@code Addressable} without being a {@code Space}.
 * <p>
 * <b>Q-procs belong to the contract, not to the storage shape.</b> {@link #qs()} declares the query processors
 * a source supports, and the interception itself is one implementation ({@link QProc.Layer}) applied where a vid
 * is handed to a source. That split is deliberate: a source says WHAT it supports and never re-derives the
 * order in which pre/post/broadcast hooks run, so an already-unordered set of hand-rolled sequences
 * ({@code AbstractSpace}, {@code mqttSpace}, {@code AbstractInstSet}) has one place to converge on.
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
     * THE Q-PROCS this source declares: the processors whose interception applies to reads and writes through
     * it. A source backed by a {@link Rec} carries them in its own {@code ?qproc} entry — reflected into mtron
     * and travelling with the rec — while a source with no rec behind it declares nothing.
     * <p>
     * Declaring is all a source does with q-procs: {@link QProc.Layer} applies them at the dispatch. A q-proc
     * attached here therefore affects a read or write that is HANDED to this source, not one this source
     * performs on itself by calling its own {@code read}/{@code write} directly.
     */
    default Lst qs() {
        if (this instanceof Rec rec) {
            final Obj qs = rec.at(uri(QPROC));
            if (!qs.isNoObj())
                return qs.asLst();
        }
        return lst(mutableList());
    }

    /**
     * Does this source declare the given q-proc, matched by its pattern?
     */
    default boolean hasQ(final fURI qPattern) {
        return this.qs().lstValue().stream().anyMatch(q -> qPattern.test(q.<QProc>as().pattern()));
    }

    /**
     * Declare a q-proc on this source: added to the {@code ?qproc} entry of the rec behind it.
     *
     * @param qProc the processor to declare
     * @return this source
     * @throws MTronException when the source has no rec to declare it in
     */
    default Addressable addQ(final QProc qProc) {
        if (!(this instanceof Rec rec))
            throw MTronException.of("source %s cannot carry q-procs: it has no rec to declare them in", this.pattern());
        final Obj key = uri(QPROC);
        if (rec.at(key).isNoObj())
            rec.at(key, lst(), MUTABLE);
        rec.at(key).asLst().lstValue().add(qProc);
        return this;
    }

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
     * Does this source declare ANY q-proc? The layer's fast path.
     * <p>
     * This is one map lookup and NO allocation, which is why it is separate from {@link #qs()} (which
     * materializes an empty list when nothing is declared). The layer sits on {@code Machine.read}/{@code write} —
     * the hottest path in the VM — and the overwhelming majority of sources declare nothing, so the common case
     * must not allocate on the way to being left alone.
     */
    default boolean hasQProcs() {
        return this instanceof Rec rec && !rec.at(uri(QPROC)).isNoObj();
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
