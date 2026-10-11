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

package studio.phaseshift.metatron.isa.mach.type.memory;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.TypeGraph;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.ComponentUnion;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Memory;
import studio.phaseshift.metatron.util.MTronException;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MEMORY_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A {@link Memory} that is a union of a frame's own level ({@link #current()}) and its enclosing level
 * ({@link #previous()}), the latter possibly itself a union — so a walk recurses to the root.
 * <p>
 * The union rule is the space rule: a space is the atomic unit of access. A read answers from the nearest level
 * that declares the address; a write to an address an ancestor already declares is written through to that
 * ancestor; a write to a new address materializes this level's own memory and lands there. Popping the frame
 * closes only {@link #current()}, so the ancestor's spaces (and any write-through) survive.
 * <p>
 * <b>A name is not an address.</b> The walk above is for <em>addresses</em> — vids that are absolute, or that
 * carry a scheme or an authority. A <em>relative</em> vid is a NAME: it is bound in this level's own frame (the
 * thread's arg stack, whose root frame is this machine's), and it is answered here and nowhere else. Letting a
 * relative vid into the walk is what published a machine-local binding to an enclosing machine: the ancestor's
 * catch-all {@code /#} space claims every relative vid, so {@code who -> 1} written in a child landed in the
 * root's space and was readable from a sibling. The split is the one {@link Memory#write} already makes.
 */
public class MemoryUnion extends MRec implements Memory, ComponentUnion<Memory> {

    private Memory current;
    private final Memory previous;

    public MemoryUnion(final Memory previous) {
        super(mutableMap(), MACH_MEMORY_TID, null);
        this.previous = previous;
        this.current = new BasicMemory();
    }

    // ======================== the union ========================

    @Override
    public Memory current() {
        return this.current;
    }

    @Override
    public Memory previous() {
        return this.previous;
    }

    /**
     * This level's own memory.
     */
    private Memory own() {
        return this.current;
    }

    @Override
    public void close() {
        try {
            if (null != this.current)
                this.current.close();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    // ======================== the union walk ========================

    @Override
    public boolean hasSpaceFor(final fURI vid) {
        return (null != this.current && this.current.hasSpaceFor(vid)) || this.previous.hasSpaceFor(vid);
    }

    @Override
    public <SPACE extends Space> SPACE findSpace(final fURI vid) {
        if (null != this.current && this.current.hasSpaceFor(vid))
            return this.current.findSpace(vid);
        return this.previous.findSpace(vid);
    }

    @Override
    public <SPACE extends Space> SPACE getSpace(final fURI vid) {
        if (null != this.current && this.current.hasSpaceFor(vid))
            return this.current.getSpace(vid);
        return this.previous.getSpace(vid);
    }

    /**
     * A vid that names an <em>address</em> rather than a machine-local name — the split {@link Memory#write}
     * makes, and which the fall-through below is written for. Only an address may be answered by an enclosing
     * level once no space has spoken.
     */
    private static boolean isAddress(final fURI vid) {
        return vid.isAbsolute() || vid.hasScheme() || vid.hasHost();
    }

    @Override
    public Obj read(final fURI vid) {
        if (vid.hasPrefix(f("~"))) {
            final fURI home = Machine.current().vid();
            return this.read(vid.equals(f("~")) ? home : home.extend(vid.pretract(1)).resolve());
        }
        // A NAME is answered by the innermost frame that bound it, so THIS level answers first and only a miss
        // falls through. Asking `previous` first is what let an ancestor's frame answer for a child — and it is
        // also why the ordering matters rather than the walk: an ancestor's catch-all # space covers every name,
        // so a name must be given to the level that owns it before any enclosing space is consulted. A miss still
        // falls through, because a short name may really be an address an enclosing level holds (`count` is
        // resolved by the root's `big()` escalation to `/m/inst/count`).
        if (!isAddress(vid)) {
            final Obj local = this.own().read(vid);
            return local.isNoObj() ? this.previous.read(vid) : local;
        }
        // An ADDRESS walks the levels' spaces, nearest first, and falls through to the enclosing level.
        final Space space = this.findSpace(vid);
        if (null != space) {
            final Obj result = space.read(vid);
            if (!result.isNoObj())
                return result;
            // the nearest level declared the vid but answered noobj — the enclosing level may still have it.
        }
        return this.previous.read(vid);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        if (vid.hasPrefix(f("~"))) {
            final fURI resolved = Machine.current().vid().extend(vid.pretract(1)).resolve();
            if (vid.equals(obj.vid()))
                obj.selfVID(resolved);
            return this.write(resolved, obj);
        }
        // A NAME binds in this level's own frame and is never written through to an enclosing level — which is
        // how a child's binding reached the root machine and was readable from its siblings. No alignPrefix here,
        // mirroring Memory.write: a write routes on the vid it was given.
        if (!isAddress(vid))
            return this.own().write(vid, obj);
        final Space space = this.findSpace(vid);
        return null != space ? space.write(vid, obj) : this.own().write(vid, obj);
    }

    // ======================== redirect to current, else previous ========================

    @Override
    public fURI redirect(final fURI furi, final boolean big) {
        // current's routes win; fall through to previous when current has no route. BasicMemory.redirect echoes the
        // vid unchanged (base path intact) when its routing tables don't cover it — that echo is the "not found here".
        if (null != this.current) {
            final fURI resolved = this.current.redirect(furi, big);
            if (!resolved.basePath().equals(furi.basePath()))
                return resolved;
        }
        return this.previous.redirect(furi, big);
    }

    @Override
    public fURI alignPrefix(final fURI vid) {
        // prefix table is inherited: current's prefixes win, then the enclosing level's.
        if (null != this.current) {
            final fURI aligned = this.current.alignPrefix(vid);
            if (!aligned.equals(vid))
                return aligned;
        }
        return this.previous.alignPrefix(vid);
    }

    @Override
    public Rec spaces() {
        return null != this.current ? this.current.spaces() : this.previous.spaces();
    }

    @Override
    public Rec rootFrame() {
        return null != this.current ? this.current.rootFrame() : this.previous.rootFrame();
    }

    @Override
    public TypeGraph typeGraph() {
        return null != this.current ? this.current.typeGraph() : this.previous.typeGraph();
    }

    // ======================== writes land in this level ========================

    @Override
    public void registerRedirect(final fURI small, final fURI big) {
        this.own().registerRedirect(small, big);
    }

    @Override
    public void unregisterRedirect(final fURI small, final fURI big) {
        this.own().unregisterRedirect(small, big);
    }

    @Override
    public void registerPrefix(final fURI prefix, final fURI vid) {
        this.own().registerPrefix(prefix, vid);
    }

    @Override
    public void addSpace(final Space space) {
        // Never evict the parent's space of the same pattern — install ours alongside it and shadow.
        if (this.previous.hasSpaceFor(space.pattern()))
            this.logger().warn("shadowing parent space for pattern %s", space.pattern());
        this.own().addSpace(space);
    }

    @Override
    public void removeSpace(final fURI pattern) {
        this.own().removeSpace(pattern);
    }
}
