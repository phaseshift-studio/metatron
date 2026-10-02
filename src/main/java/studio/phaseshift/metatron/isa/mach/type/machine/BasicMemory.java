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

package studio.phaseshift.metatron.isa.mach.type.machine;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Memory;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.SPACE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MEMORY_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * BasicMemory — one level of a machine's memory: its own relative bindings, and the spaces it holds.
 * <p>
 * It is a {@link MRec} and holds <b>no state in Java fields</b>, for the same reason {@link BasicMachine} does
 * not: everything has to be reachable from mtron. The relative bindings <em>are</em> this rec, and the absolute
 * index is the {@code space} entry inside it — so {@code >>space} reaches the index from the language, and no
 * part of memory hides behind an accessor the language cannot call.
 * <p>
 * The two projections therefore come from one rec: {@link #stack()} is this memory itself (it is the relative
 * rec), and {@link #spaces()} is the {@code space} entry within it.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BasicMemory extends MRec implements Memory {

    public BasicMemory() {
        this(mutableMap(), null);
    }

    /**
     * @param jvm the relative bindings — the rec this memory <em>is</em>
     * @param vid the address of this memory, if it has one; a frame's memory does not
     */
    public BasicMemory(final Map<Obj, Obj> jvm, final fURI vid) {
        super(jvm, MACH_MEMORY_TID, vid);
        // The index is created here, at construction, and never lazily: `spaces()` is reached from `findSpace`,
        // which is on the read path, and allocating an Obj there re-enters type resolution and `read` itself.
        // Anything the read path touches has to already exist.
        this.spaces();
    }

    // ======================== hot slot read ========================

    /**
     * The index is consulted on every address resolution, so it is read from the raw jvm map rather than through
     * {@code Rec.at}, skipping the rec recursion {@code at} does for uri keys. Same shape as {@link BasicMachine}'s
     * processor and compiler short-circuits, and for the same reason: this is the hottest lookup in the machine.
     */
    @Override
    public <OBJ extends Obj> OBJ at(final Obj key) {
        if (key.equals(uri(SPACE)))
            return (OBJ) this.jvm().getOrDefault(uri(SPACE), noobj());
        return super.at(key);
    }

    // ======================== the two projections ========================

    /**
     * The relative projection is this memory: the bindings <em>are</em> the rec.
     */
    @Override
    public Rec stack() {
        return this;
    }

    /**
     * This level's index. Created on first access rather than defaulted to a throwaway — {@code Obj.orElse}
     * evaluates its fallback eagerly, so a missing index would swallow every space registered into it, silently,
     * which is exactly the failure a space index must never have.
     */
    @Override
    public Rec spaces() {
        final Obj spaces = this.jvm().getOrDefault(uri(SPACE), noobj());
        if (spaces.isRec())
            return spaces.asRec();
        final Rec fresh = rec(mutableMap());
        this.jvm().put(uri(SPACE), fresh);
        return fresh;
    }

    // ======================== index maintenance ========================

    @Override
    public void addSpace(final Space space) {
        this.spaces().jvm().put(null == space.vid() ? space.pattern().toUri() : space.vid().toUri(), space);
    }

    @Override
    public void removeSpace(final fURI pattern) {
        if (null == pattern)
            return;
        this.spaces().jvm().keySet().stream()
                .filter(k -> k.isUri() && k.uriValue().test(pattern))
                .toList()
                .forEach(k -> this.spaces().jvm().remove(k));
    }

    // ======================== lookup ========================

    @Override
    public <SPACE extends Space> SPACE findSpace(final fURI vid) {
        return Memory.mostSpecific(this.spaces(), vid);
    }

    @Override
    public <SPACE extends Space> SPACE getSpace(final fURI vid) {
        return (SPACE) this.spaces().jvm().values().stream()
                .map(Obj::<Space>as)
                .filter(s -> s.vid().test(vid))
                .findFirst()
                .orElse(null);
    }

    /**
     * Release the spaces this level holds, and nothing else.
     * <p>
     * This is what makes the lease real: a frame's spaces were created by that frame, so closing the frame's
     * memory collects them, and an address that existed for the computation stops existing with it. Nothing
     * inherited is reachable from here — an inherited space is in the previous level, not this one — so a frame
     * cannot close its parent's resources by mistake.
     */
    @Override
    public void close() {
        this.spaces().jvm().values().stream()
                .filter(v -> v instanceof AutoCloseable)
                .forEach(CommonUtil::close);
        this.spaces().jvm().clear();
    }

    @Override
    public boolean hasSpaceFor(final fURI vid) {
        return this.spaces().jvm().values().stream()
                .map(Obj::<Space>as)
                .anyMatch(s -> vid.test(s.pattern()));
    }
}
