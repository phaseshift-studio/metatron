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
import studio.phaseshift.metatron.isa.mach.type.Memory;
import studio.phaseshift.metatron.util.MTronException;

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

    @Override
    public Obj read(final fURI vid) {
        final Space space = this.findSpace(vid);
        return null != space ? space.read(vid) : this.previous.read(vid);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        final Space space = this.findSpace(vid);
        return null != space ? space.write(vid, obj) : this.own().write(vid, obj);
    }

    // ======================== redirect to current, else previous ========================

    @Override
    public fURI redirect(final fURI furi, final boolean big) {
        return null != this.current ? this.current.redirect(furi, big) : this.previous.redirect(furi, big);
    }

    @Override
    public fURI alignPrefix(final fURI vid) {
        return null != this.current ? this.current.alignPrefix(vid) : this.previous.alignPrefix(vid);
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
