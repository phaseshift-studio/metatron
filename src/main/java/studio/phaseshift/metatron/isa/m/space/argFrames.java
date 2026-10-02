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

package studio.phaseshift.metatron.isa.m.space;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.ArrayDeque;
import java.util.Deque;

import static studio.phaseshift.metatron.isa.m.mInstSet.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.Inst.ARGS_FURI;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The argument frames of the monads currently being evaluated — a stack of applied-args recs, walked as a
 * <b>tree walk</b>: the current monad's frame first, then outward.
 * <p>
 * Deliberately <b>not a {@code Space}</b>. Every vid it ever sees is <em>relative</em>, so it needs none of the
 * absolute machinery — no index, no {@code findSpace}, no {@code spaces()}, no qprocs. It is a scoped binding
 * chain, and the previous implementation's cost came entirely from carrying an address-space contract it could
 * not use: the {@code root} spill existed only to make a write readable back for the {@code AbstractSpaceTest}
 * cases, and {@code size()-2} existed to hide the current frame from itself.
 * <p>
 * <b>Writing is correct here</b>, which is the point of the design. {@code Processor.applyArgs} builds a
 * <em>fresh</em> args rec per application — bound values, plus {@code lhs} — and nothing caches it, so
 * {@code cinst.args()} belongs to this monad alone. A binding written during an instruction lives in that
 * frame and dies with it when the frame is popped, so restoration on return is automatic: there is nothing to
 * save and nothing to unwind, only a reference to drop.
 * <p>
 * The walk starts at the <b>current</b> frame rather than skipping it. Its args are the current monad's scope,
 * so a parameter is an ordinary binding and shadows an outer name in the normal way.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class argFrames extends MRec {

    private final Deque<Poly<?, ?>> frames = new ArrayDeque<>();

    public argFrames() {
        super(CommonUtil.mutableMap(), null, null);
    }

    /** push the applied args of the monad now being evaluated; this becomes the current frame */
    public void push(final Poly<?, ?> args) {
        this.frames.push(args);
    }

    public boolean pop() {
        return null != this.frames.poll();
    }

    public void clear() {
        this.frames.clear();
    }

    public boolean isEmpty() {
        return this.frames.isEmpty();
    }

    public int depth() {
        return this.frames.size();
    }

    /** the current monad's frame — its args, which are its scope */
    public Obj peek() {
        final Poly<?, ?> current = this.frames.peek();
        return null == current ? noobj() : current.as();
    }

    /**
     * Resolve an argument reference, mirroring {@code stackSpace.read} exactly so it can stand in for it.
     * <p>
     * The head frame is the instruction <b>now applying</b>, and an argument reference belongs to the
     * instruction whose <em>body</em> is running — the enclosing one. So the walk skips the head and proceeds
     * outward, which is what the old {@code size()-2} loop start encoded. It is not "hide the current scope":
     * arguments are a separate, sigil-addressed namespace and were never reachable as names.
     * <p>
     * Answers {@code noobj} when nothing holds it; the caller falls through to resolution, as before.
     */
    public Obj read(final fURI vid) {
        final boolean isArgs = vid.path().getFirst().equals(ARGS_FURI.toString());
        boolean applying = true;   // the head is the instruction applying, not the one whose body this is
        for (final Poly<?, ?> frame : this.frames) {   // ArrayDeque iterates head -> tail, i.e. current -> outer
            if (applying) {
                applying = false;
                continue;
            }
            if (isArgs)
                return vid.asNode().segmentLength() == 1 ? frame.as() : frame.at(uri(vid.pretract(1)));
            final Obj hit = frame.at(vid.basePath().toUri());
            if (!hit.isNoObj())
                return hit;
        }
        return noobj();
    }

    /**
     * Bind in the applying frame. In place, so the write is visible for the rest of that monad's evaluation —
     * and gone when the frame is popped. No {@code root}: a binding that outlived its monad would not be a scope.
     */
    public Obj write(final fURI vid, final Obj obj) {
        final Poly<?, ?> current = this.frames.peek();
        return null == current ? noobj() : current.at(vid.toUri(), obj, MUTABLE);
    }
}
