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
import studio.phaseshift.metatron.isa.m.space.noobjSpace;
import studio.phaseshift.metatron.isa.m.space.variableStack;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.TypeGraph;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.util.MTronException;

import java.io.Closeable;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import static studio.phaseshift.metatron.BootLoader.BOOTING;
import static studio.phaseshift.metatron.furi.fURI.Singleton.NOOBJ;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The <b>Memory</b> component of a Machine: the address space a machine holds, in both of its aspects.
 * <p>
 * It is a {@link Rec} — via {@link Machine.Component} — for a reason that is not structural convenience: memory
 * is <em>reflected into mtron</em>, so the language that composes machines can read and write it. A component
 * reachable only through Java accessors is invisible to the language that is supposed to be in control of it.
 * <p>
 * The two aspects are separated by {@link fURI#isAbsolute()} and nothing else:
 * <ul>
 *   <li><b>relative</b> — the bindings of the frame you are in. A name, shadowed by the innermost frame that
 *       declared it, visible only while that frame is on the stack, never addressable by anyone else.</li>
 *   <li><b>absolute</b> — the index of spaces. An address, resolved to the space that covers it. A space created
 *       in a frame belongs to that frame, and is reachable by its peers for exactly as long as the frame
 *       lives.</li>
 * </ul>
 * <b>Two projections, one chain.</b> {@link #spaces()} and {@link #stack()} walk the <em>same</em> parent chain
 * and call themselves on each parent — one gathers the absolute index, the other the relative bindings. They
 * differ only in the combine rule, and the rule follows from what they hold: a pattern is a question whose answer
 * may be spread across the whole chain, so it merges; a name is a single thing, so the innermost owner wins.
 * <p>
 * <b>What is per-level and what is composed.</b> {@link #findSpace} answers for <em>this level only</em> and
 * returns null on a miss, which is what makes it the right primitive for a walk that must not stop early.
 * {@link #getSpaceFor} is the contract callers depend on. {@link #spaces()} materializes, so it is for reflection
 * and never for resolution — {@code getSpaceFor} is the hottest call in the machine and must not build a map.
 * <p>
 * Memory never <em>renames</em> an address. Prefix alignment belongs to the ISA and authority dispatch belongs to
 * the {@link Network}; both happen before the handoff, so a memory receives an address that is already localized
 * and already aligned.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Memory extends Space, Machine.Component, Closeable {

    /**
     * A Memory claims EVERYTHING it presents: its jurisdiction is the world of spaces it facades, so its pattern is
     * ALL. This is what distinguishes it from the spaces it holds, each of which claims only its own subgraph.
     */
    @Override
    default fURI pattern() {
        return studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
    }

    /**
     * A Memory's JVM context is its own rec map.
     */
    @Override
    default Object sjvm() {
        return this.jvm();
    }

    /**
     *
     * The route table is a memory rec entry ({@code route => [=>]}).
     */
    @Override
    default Map<Uri, Obj> routes() {
        return Map.of();
    }

    @Override
    default Stats stats() {
        return new MStats();
    }

    fURI redirect(final fURI vid, final boolean big);

    void registerRedirect(final fURI small, final fURI big);

    void unregisterRedirect(final fURI small, final fURI big);

    void registerPrefix(final fURI prefix, final fURI vid);

    /**
     * Resolve a short name against the prefix + redirect tables before routing.
     */
    fURI alignPrefix(final fURI vid);

    /**
     * The per-instruction <b>arg stack</b>, one per thread: the args of the instruction currently being applied,
     * as the inner frame of the evaluator.
     * <p>
     * <b>Temporary.</b> This is the old {@code Router.THREAD_STACK}, moved here so that {@code Router} can be
     * deleted before the arg frame itself moves. It is a flat {@code Stack<Poly>} whose top entry is the args of
     * the inst in flight — which is why its reader needs a {@code size()-2} offset to avoid mistaking args for a
     * lexical scope. Once args are a key in a frame rather than an entry on a stack, both the stack and the
     * offset have nothing left to do, and this field goes with them.
     */

    /**
     * The argument frames of the monads being evaluated: a stack of applied-args recs, resolved by a walk that
     * skips the instruction now applying (an argument reference belongs to the instruction whose body is
     * running). A sigil-addressed namespace — never a name scope — which is why {@code argFrames} is not a
     * {@code Space}: it has no addresses, no index, and no qprocs.
     */
    ThreadLocal<variableStack> ARG_STACK = ThreadLocal.withInitial(() -> new variableStack(fURI.Singleton.f("+/#")));

    /**
     * this thread's arg stack — see {@link #ARG_STACK} for why it exists and why it is temporary
     */
    static variableStack argStack() {
        return ARG_STACK.get();
    }

    /**
     * Read through the chain: a relative vid resolves as a binding, an absolute one to the space that covers it.
     * The routing rule lives here and only here, so every level of memory routes identically.
     */
    /*default Obj read(final fURI vid) {
        // "here" has no spelling in either aspect below: it is the zero displacement, so it names no entry in the
        // frame chain and no path in the space index. Resolve it to the CURRENT FRAME's address (or the root's when
        // no frame is live) and let the ordinary absolute path do the work — which is exactly the semantics wanted:
        // inside a frame `.` is that frame's interior, at the root it is the root's.
        if (vid.isId())
            return this.readAbsolute(hereVID());
        return (vid.isAbsolute() || vid.hasScheme() || vid.hasHost()) ? this.readAbsolute(vid) : argStack().read(vid);
    }*/

    /**
     * An absolute address is a space's to answer. When no space covers it the machine in effect answers with its
     * own rec — which is what makes {@code /} the root: the machine's own keys are addressable there
     * ({@code &#42;/processor}, {@code &#42;/memory}), and no space has to be mounted inside itself.
     * <p>
     * The machine is asked with {@code at}, <b>never</b> {@code read}: a read would come straight back here and
     * a root read would re-enter itself.
     */
    default Obj readAbsolute(final fURI vid) {
        // findSpace, not getSpaceFor: the machine's own keys are the fallback, not a space, and resolving them is
        // this method's job — getSpaceFor must never return the machine cast to a Space (Machine is a Rec).
        final Space space = this.findSpace(vid);
        if (null != space) {
            final Obj result = space.read(vid);
            if (!result.isNoObj())
                return result;
            // the space covered the vid but answered noobj (the /# infra catch-all does this for /memory, /processor,
            // …) — fall through so the machine's own rec keys still answer.
        }
        final Machine machine = this.machine();
        if (null != machine) {
            final Obj key = machine.at(uri(rootRelative(vid)));
            if (!key.isNoObj())
                return key;
        }
        // No space and no machine key: answer noobj, do NOT throw. Throwing here would pre-empt the caller's big()
        // fallback (small→big routing: count → /m/inst/count) and the walk-up to an enclosing machine — the exact
        // two things a relative vid needs to resolve through a child's union memory.
        return noobj();
    }

    /**
     * Write into the frame you are in: a relative vid lands in this frame's own bindings, an absolute one in the
     * space that covers it. A frame writes what it introduced and nothing else.
     */
    default Obj write(final fURI vid, final Obj obj) {
        // every write may touch a cached type resolution; invalidate before routing (relative or absolute)
        if (null == vid)
            return obj;
        // ~ resolves to the current machine's vid, before the relative/absolute split: ~/sys → /xyz/sys.
        // The extension resolves (. collapses): ~/thread → /thread — the selfVID stamp then carries the resolved vid.
        if (vid.hasPrefix(f("~"))) {
            final fURI resolved = Machine.current().vid().extend(vid.pretract(1)).resolve();
            // the obj being written under a ~ vid carries that vid — stamp it to the resolved form so both the
            // memory address and the obj's own vid are absolute (MObj's constructor writes obj.vid() == this vid).
            if (vid.equals(obj.vid()))
                obj.selfVID(resolved);
            return this.write(resolved, obj);
        }
        this.typeGraph().onWrite(vid);
        //     if (vid.isId())
        //         return this.writeAbsolute(hereVID(), obj);
        if (vid.isAbsolute() || vid.hasScheme() || vid.hasHost())
            return this.writeAbsolute(vid, obj);
        this.stack().write(vid, obj);
        return obj;
    }

    /**
     * The space-resolving half of {@link #write}, split out for the same reason {@link #readAbsolute} is: a
     * Machine's write is <em>always</em> space-resolving, whatever the vid's shape. Routing the machine through
     * {@link #write} let the relative branch claim relative vids and send them to the argument stack, so a bulk
     * wildcard write landed on the stack instead of the space it named.
     */
    default Obj writeAbsolute(final fURI vid, final Obj obj) {
        final Space space = this.findSpace(vid);
        if (null != space)
            return space.write(vid, obj);
        final Machine machine = this.machine();
        if (null != machine) {
            final Obj key = machine.at(uri(rootRelative(vid)));
            if (!key.isNoObj())
                return machine.at(uri(rootRelative(vid)), obj, MUTABLE);
        }
        // same as readAbsolute: answer obj, do NOT throw — a relative ~/ write resolves via the ~ branch before this,
        // and a child's union write falls through to its own level, so throwing here only cuts off the fallbacks.
        return obj;
    }

    /**
     * The absolute projection: the index of spaces in scope, walking the chain and calling {@code spaces()} on
     * each parent. This is what mtron reaches as {@code >>space}.
     * <p>
     * A <b>merge</b> rather than a shadow, because the index is not a binding: two levels' spaces are both live,
     * so even an exact key has to combine. And an address means the same thing at every depth, so the winner is
     * the most specific pattern in scope, not the innermost level.
     */
    Rec spaces();

    /**
     * The machine-scoped persistent root frame — the relative bindings that survive frame pops and thread
     * boundaries (a console's {@code a -> 13} lands here and is visible from every later command thread), but
     * are dropped when the machine itself is reset. Not the per-thread arg frames: those live in
     * {@link #argStack()}'s {@code sjvm}.
     */
    Rec rootFrame();

    /**
     * The relative projection: the bindings in scope, walking the <em>same</em> chain and calling {@code stack()}
     * on each parent instead of {@code spaces()}.
     * <p>
     * A <b>shadow</b> rather than a merge, because these are names: the innermost frame that bound one owns it.
     * <p>
     * This replaces {@code variableStack}. The chain is the frame chain, so the {@code size()-2} offset a flat
     * {@code Stack<Poly>} needed — to keep the current instruction's arg frame from being read as a lexical scope
     * — has no reason to exist: args are a key in a frame, and scopes are the chain.
     */
    default Space stack() {
        return argStack();
    }

    /**
     * The machine-scoped type memo: the types this machine has resolved (plus inherited parent types), grown for
     * the machine's lifetime and dropped when the machine is popped. The first entry in the machine's cache.
     */
    TypeGraph typeGraph();

    /**
     * register a space at <em>this</em> level. A frame registers into its own level and nowhere else, which is
     * what lets a popped frame take its spaces with it.
     */
    void addSpace(final Space space);

    /**
     * deregister the spaces at this level matching {@code pattern}.
     */
    void removeSpace(final fURI pattern);

    /**
     * The most specific space at <b>this level</b> covering {@code vid}, or {@code null} when none does.
     * <p>
     * Null-on-miss rather than a fallback is the whole point: a walk asks each level in turn, and a level that
     * answered with the stack, or threw, or returned the noobj space, would end the walk at the wrong depth.
     */
    <SPACE extends Space> SPACE findSpace(final fURI vid);

    /**
     * the space registered under exactly this vid, or null.
     */
    <SPACE extends Space> SPACE getSpace(final fURI vid);

    /**
     * whether <em>this level</em> holds a space covering {@code vid}.
     */
    boolean hasSpaceFor(final fURI vid);

    /**
     * The space covering {@code vid}: each level in turn via {@link #findSpace}, then the stack, then the
     * boot/no-boot fallback. This is the address-resolution contract.
     */
    default <SPACE extends Space> SPACE getSpaceFor(final fURI vid) {
        if (vid.test(NOOBJ))
            return noobjSpace.single();
        final SPACE space = this.findSpace(vid);
        if (null != space)
            return space;
        // The machine's own keys (/processor, /memory, …) are the read/write path's fallback, handled in
        // readAbsolute/writeAbsolute — never returned here as a cast-to-Space machine (Machine is a Rec, not a Space).
        if (!BOOTING)
            throw MTronException.of("no active space supports pattern %s", vid.toUri(false));
        return noobjSpace.single();
    }

    /**
     * {@code /processor} becomes {@code processor}: the root machine's keys are relative to it, and keeping the
     * whole remaining path (not just {@code name()}) is what lets {@code /memory/previous} walk into the memory
     * rather than collapsing to {@code previous} and missing.
     */
    static String rootRelative(final fURI vid) {
        final String path = vid.basePath().toString();
        return path.startsWith("/") ? path.substring(1) : path;
    }

    static <SPACE extends Space> SPACE mostSpecific(final Rec spaces, final fURI vid) {
        // snapshot first: the space index is a mutable LinkedHashMap, and addSpace can land a space on another
        // thread mid-iteration (or re-entrantly during boot), which trips the map's fail-fast iterator
        final Optional<SPACE> space = new java.util.ArrayList<Obj>(spaces.jvm().values()).stream()
                .map(Obj::<SPACE>as)
                .filter(s -> vid.basePath().test(s.pattern()))
                .min(Comparator
                        .comparing((Space s) -> !s.pattern().hasScheme())
                        .thenComparing(Space::pattern));
        return space.orElse(null);
    }
}
