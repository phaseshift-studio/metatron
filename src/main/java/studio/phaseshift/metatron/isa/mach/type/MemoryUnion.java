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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicMemory;

import java.util.LinkedHashMap;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.Tokens.PREVIOUS;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MEMORY_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;

/**
 * The memory of a frame: this frame's relative bindings read through to the ones it inherited.
 * <p>
 * Memory is a <b>Rec</b> — a relative-URI space, which is exactly what a method frame stack is. And a Rec is the
 * right container rather than merely a convenient one, because {@code >>} navigates <em>through</em> it:
 *
 * <pre>
 *   [a=>[b=>[c=>[d=>5]]]]>>a/b/c/d   =>  5
 *   [a=>[b=>[c=>[d=>5]]]]>>a/b/+     =>  [d=>5]
 *   [a=>[b=>[c=>[d=>5]]]]>>a/b/+/d   =>  5
 * </pre>
 *
 * {@code Rec.Helper.rshiftRec} is written against {@code lhs.asRec().at(k)}, so a Rec gets path navigation and
 * wildcards for free — and a "Rec-ish" type that does not implement {@code Rec} could not be passed to it at all.
 * That is the whole reason this extends {@link MRec} rather than exposing a smaller {@code at}/{@code at} surface.
 *
 * <h2>Read-through is right for exact keys and wrong for patterns</h2>
 * An exact key is <em>shadowed</em>: the frame's binding wins and the parent's is invisible. A <b>pattern</b> is
 * not shadowed — {@code a/b/+} matches whatever each side holds at {@code a/b}, and answering from only one side
 * would silently lose half the result. So {@link #at} branches on whether the key is patterned, which is why this
 * is not a uniform "first non-noobj wins".
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MemoryUnion extends MRec implements Memory, ComponentUnion<Memory> {

    private final Memory current;

    /**
     * @param previous the memory this frame inherited
     * @param current  the memory this frame introduced — the only one it writes to or closes
     */
    public MemoryUnion(final Memory previous, final Memory current) {
        // the inherited map is unused: every accessor below delegates. Memory is not indexed by the Router -- it
        // has no vid -- which is what makes it a service's own bookkeeping and ephemeral.
        super(new LinkedHashMap<>(), MACH_MEMORY_TID, null);
        // The inherited side lives in the rec, so `>>previous` and previous() cannot disagree — one
        // source of truth, and mtron can reach it.
        this.jvm().put(uri(PREVIOUS), null == previous ? new BasicMemory() : previous);
        this.current = null == current ? new BasicMemory() : current;
    }

    @Override
    public Memory previous() {
        return (Memory) this.jvm().getOrDefault(uri(PREVIOUS), noobj());
    }

    @Override
    public Memory current() {
        return this.current;
    }

    // ======================== the two projections ========================

    /**
     * The relative projection is this union: it already reads through — {@link #at} answers from this frame first
     * and falls back to the one it inherited. Returning {@code this} rather than a merged copy is load-bearing,
     * because a write goes through {@code stack().at(k, v, MUTABLE)} and a copy would swallow it.
     */
    @Override
    public Rec stack() {
        return this;
    }

    /**
     * The absolute projection: both levels' indices in one rec, innermost winning on a shared key.
     * <p>
     * Materialized, because this is the reflection view — what mtron reads as {@code >>space}. Resolution does
     * not come through here: {@link #findSpace} walks the levels without building a map, and
     * {@code getSpaceFor} is the hottest call in the machine.
     */
    @Override
    public Rec spaces() {
        final Rec merged = rec(new LinkedHashMap<>(this.previous().spaces().jvm()));
        merged.jvm().putAll(this.current.spaces().jvm());
        return merged;
    }

    // ======================== index maintenance ========================

    /** a frame registers into its own level and nowhere else, so a pop takes its spaces with it */
    @Override
    public void addSpace(final Space space) {
        this.current.addSpace(space);
    }

    @Override
    public void removeSpace(final fURI pattern) {
        this.current.removeSpace(pattern);
    }

    // ======================== lookup ========================

    /**
     * The most specific space in the whole chain. Each level answers for itself and the patterns are compared,
     * rather than the innermost level winning outright — an address denotes the same thing at every depth, so the
     * more specific claim wins wherever it was made.
     */
    @Override
    public <SPACE extends Space> SPACE findSpace(final fURI vid) {
        final SPACE mine = this.current.findSpace(vid);
        final SPACE theirs = this.previous().findSpace(vid);
        if (null == mine)
            return theirs;
        if (null == theirs)
            return mine;
        return mine.pattern().compareTo(theirs.pattern()) <= 0 ? mine : theirs;
    }

    @Override
    public <SPACE extends Space> SPACE getSpace(final fURI vid) {
        final SPACE mine = this.current.getSpace(vid);
        return null != mine ? mine : this.previous().getSpace(vid);
    }

    @Override
    public boolean hasSpaceFor(final fURI vid) {
        return this.current.hasSpaceFor(vid) || this.previous().hasSpaceFor(vid);
    }

    /**
     * frame first; but if the key is a <b>pattern</b> then both sides' matches belong to the answer, because a
     * pattern is a question and not a name.
     */
    @Override
    public <OBJ extends Obj> OBJ at(final Obj key) {
        // a hot slot: `previous` is the inherited side, not a binding, so reading it must not go
        // through the union and come back empty
        if (key.equals(uri(PREVIOUS)))
            return (OBJ) this.jvm().getOrDefault(uri(PREVIOUS), noobj());
        final Obj mine = this.current.at(key);
        final Obj theirs = this.previous().at(key);
        if (mine.isNoObj())
            return (OBJ) theirs;
        if (theirs.isNoObj())
            return (OBJ) mine;
        return (OBJ) (patterned(key) ? objs(mine, theirs) : mine);
    }

    /**
     * a frame writes only what it introduced
     */
    @Override
    public Rec at(final Obj key, final Obj value, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
        return this.current.at(key, value, operation);
    }

    /**
     * {@code >>} with no argument asks for the values, so the union has to answer with both sides' — otherwise a
     * navigation that reads through {@link #at} would look empty at the root.
     */
    @Override
    public <OBJ extends Obj> Stream<OBJ> valueElements() {
        return Stream.<OBJ>concat(this.current.valueElements(), this.previous().valueElements());
    }

    @Override
    public Stream<Obj> keys() {
        return Stream.concat(this.current.keys(), this.previous().keys());
    }

    /**
     * Release what this frame introduced, and nothing else.
     * <p>
     * Load-bearing rather than decorative: {@link MRec} declares its own state, and an inherited <b>class
     * method shadows an interface default</b> — without this override {@link ComponentUnion#close()} would never
     * run and the frame would silently leak everything it introduced.
     */
    @Override
    public void close() {
        ComponentUnion.super.close();
    }

    /**
     * a patterned key asks a question of the whole structure; an unpatterned one names a single binding
     */
    private static boolean patterned(final Obj key) {
        return key.isUri() && key.uriValue().hasPattern();
    }
}
