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
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicInstSet;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicMachine;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicMemory;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicNetwork;
import studio.phaseshift.metatron.isa.sys.type.ExecutionStack;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;

import static studio.phaseshift.metatron.BootLoader.ROOT_MACHINE;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.SYS;

/**
 * Machine: a SPACE OF SPACES -- the address space resolution happens in. Its members ({@code instset}, {@code memory},
 * {@code network}, {@code compiler}, {@code processor}) are rec entries, the shape of {@code machine::T}; {@link Memory}
 * is the facade over the world of spaces, while {@link Compiler} lowers code and {@link Processor} runs it and neither
 * is an address space.
 * <p>
 * Three names, three questions: {@link #jvmRoot()} is this JVM's bootstrap; {@link #root()} is the top of the
 * containment this thread is in, and is what resolution reads; {@link #current()} is where the thread stands.
 * <p>
 * This interface carries four roles, and the source is grouped in this order:
 * <ol>
 *     <li><b>identity and place</b> -- {@code jvmRoot}, {@code root}, {@code mach0}, {@code defaultMachine},
 *     {@code descendsFrom}: where things are and who contains whom.</li>
 *     <li><b>the perspective</b> -- {@code CURRENT} with {@code current()} and the two {@code withPerspective} forms.</li>
 *     <li><b>the entry point</b> -- {@code readFromSpace}, {@code writeToSpace}, {@code loaded}.</li>
 *     <li><b>the space contract and its index</b> -- {@code spaces}, {@code pattern}, {@code read}, {@code write},
 *     {@code addSpace}, {@code removeSpace}, {@code getSpace}, {@code getSpaceFor}, {@code mount}, and the route
 *     table's {@code register*}: three registries -- spaces, frames, routes.</li>
 *     <li><b>execution</b> -- {@code apply}, {@code move}, {@code push}, {@code pop}.</li>
 *     <li><b>components</b> -- {@code compiler}, {@code processor}, {@code instset}, {@code memory}, {@code network},
 *     each in a frame-aware form and an {@code own*} slot form.</li>
 *     <li><b>frames</b> -- {@code FRAME} (the cursor) and {@code FRAMES} (the address index), with {@code frameAt},
 *     {@code frameRegister}, {@code forget}, and the non-allocating {@code *View} and
 *     {@code inherited*} accessors.</li>
 *     <li><b>reachability</b> -- {@code own}, {@code isPeer}, {@code selfAuthorities}.</li>
 *     <li><b>inner types</b> -- {@code Frame}, {@code Machine0}, {@code Component}.</li>
 * </ol>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Machine extends Space {

    static Machine mach0() {
        return Helper.Machine0.single();
    }

    /**
     * The machine ROOT — always, whatever perspective this thread is standing in. The bootstrap constant.
     */
    // ======================== identity and place ========================
    static Machine jvmRoot() {
        return null == ROOT_MACHINE ? mach0() : (Machine) ROOT_MACHINE;
    }

    /**
     * The top of the containment this thread is in: the machine that owns its addresses and whose spaces answer.
     * Resolution reads THIS, never {@link #current()} -- a machine that EXECUTES code need not be able to RESOLVE it.
     */
    static Machine root() {
        // THE TOP OF THE CONTAINMENT I AM IN -- the machine whose spaces answer, and the machine that grants the
        // addresses. It is DERIVED, not stored: today every chain is rooted at this JVM's bootstrap, so this
        // coincides with jvmRoot(). It stops coinciding the moment a caller ARRIVES somewhere else, and it becomes
        // an actual walk up previous() then -- with nothing to store and nothing to go stale. There is deliberately
        // No stored slot: a stored answer once let a landed-on machine masquerade as the outermost one.
        return jvmRoot();
    }

    /**
     * Where this thread stands: the machine it is evaluating in, or {@link #root()} when nothing moved it.
     * Moved by a machine dereference or {@link #withPerspective(Machine)}, never by an ordinary scope.
     */
    static Machine current() {
        final Machine machine = CURRENT.get();
        return null != machine ? machine : root();
    }

    /** The frame of reference, per thread. */
    ThreadLocal<Machine> CURRENT = new ThreadLocal<>();

    /**
     * The frame of reference, per thread: the machine this thread is evaluating in, or {@link #root()} when nothing
     * moved it. A worker starts where its caller stood and publishes where it ended.
     */

    /**
     * Evaluate a fragment in another machine's frame of reference, restoring this thread's on the way out.
     * The scoped form: in at the dereference, out when the fragment that followed it ends.
     */
    // ======================== the perspective ========================
    static Obj withPerspective(final Machine machine, final java.util.function.Supplier<Obj> fragment) {
        final Machine previous = CURRENT.get();
        CURRENT.set(machine);
        try {
            return fragment.get();
        } finally {
            if (null == previous)
                CURRENT.remove();
            else
                CURRENT.set(previous);

        }
    }

    /**
     * Move this thread's perspective and leave it there -- no lambda, no restore. Use this form when the move IS the
     * statement; use the two-arg form when it is scoped to one fragment.
     */
    static Machine withPerspective(final Machine machine) {
        CURRENT.set(machine);
        return machine;
    }

    // ======================== the space funnel ========================
    // Reads and writes through root(), the machine whose spaces answer.
    //
    // WHY THE NAMES ARE readFromSpace/writeToSpace AND NOT read/write: Machine IS-A Space, so it already has
    // instance read(fURI)/write(fURI, Obj), and a static of the same name cannot coexist with them -- same signature,
    // same erasure. The longer names are that collision, not a preference; shortening them means either moving the
    // statics onto another type or taking the instance methods away, and neither is worth doing before the facade
    // work (memory() as the single read/write interface) settles which of the two is the entry point.
    // ======================== the entry point ========================
    static Obj readFromSpace(final fURI vid) {
        return ExecutionStack.frame(ExecutionStack.exec(ExecutionStack.ExState.resolve_inst, "read " + vid),
                () -> null == ROOT_MACHINE ? noobj() : Machine.root().read(vid));
    }

    static Obj readFromSpace(final String vid) {
        return Machine.readFromSpace(f(vid));
    }

    static Obj writeToSpace(final fURI vid, final Obj obj) {
        return ExecutionStack.frame(ExecutionStack.exec(ExecutionStack.ExState.apply_inst, "write " + vid),
                () -> null == ROOT_MACHINE ? noobj() : Machine.root().write(vid, obj));
    }

    static Obj writeToSpace(final String vid, final Obj obj) {
        return Machine.writeToSpace(f(vid), obj);
    }

    static Obj writeToSpace(final Obj obj) {
        return Machine.writeToSpace(obj.vid(), obj);
    }

    /**
     * The {@code /sys/mach} constant bootstrapped by {@code machInstSet.setup()}, falling back to {@link #mach0()}
     * before the constant is loaded.
     */
    static Machine defaultMachine() {
        final Obj machine = Machine.readFromSpace(SYS.extend(MACH));
        return machine.isNoObj() ? mach0() : machine.as();
    }

        // ======================== the address surface ========================
    // A Machine IS the address space, so these are the machine's own contract: declared here rather than inherited.

    static boolean loaded() {
        return null != ROOT_MACHINE;
    }

    // ======================== the space contract and its index ========================
    default Rec spaces() {
        return this.at(uri(SPACE)).orElse(rec());
    }

    @Override
    default fURI pattern() {
        return ALL;
    }

    default Obj read(final String vid) {
        return this.read(f(vid));
    }

    default Obj write(final String vid, final Obj obj) {
        return this.write(f(vid), obj);
    }

    default Obj[] write(final Object... vidObj) {
        int count = (int) ((double) vidObj.length / 2.0d);
        final Obj[] result = new Obj[count];
        for (int i = 0; i < vidObj.length; i = i + 2) {
            result[--count] = this.write(f(vidObj[i].toString()), (Obj) vidObj[i + 1]);
        }
        return result;
    }

    boolean hasSpaceFor(final fURI vid);

    void addSpace(final Space space);

    /**
     * Mount a space HERE, where the level IS the audience: on a machine it is visible to that machine and everything
     * below; on a frame's machine it belongs to that frame alone.
     * <p>
     * It must undo the constructor's self-registration -- removing by the key the space was STORED under, its vid when
     * it has one -- and register at the chosen level through the frame-aware memory, or the mount leaks to the root or
     * to every sibling.
     */
    default Machine mount(final Space space) {
        if (null == space)
            return this;
        final fURI key = null == space.vid() ? space.pattern() : space.vid();
        Machine.root().removeSpace(key);
        final Frame frame = Machine.frame();
        final Memory where = (null != frame && frame.machine() == this) ? frame.memory() : this.memory();
        where.addSpace(space);
        // and clean the root AFTER the add as well: a frame's union searches its INHERITED side, so a space that
        // reached the shared level would still answer from the frame while being visible to everything else. Mounting
        // ON the root is the global mount, so that case is exempt -- there the root copy IS the mount.
        final Machine authority = Machine.root();
        if (this != authority)
            authority.removeSpace(key);
        return this;
    }

    void removeSpace(final fURI vid);

    <SPACE extends Space> SPACE getSpace(final fURI pattern);

    void registerRedirect(final fURI small, final fURI big);

    void unregisterRedirect(final fURI small, final fURI big);

    void registerPrefix(final fURI prefix, final fURI vid);

    <SPACE extends Space> SPACE getSpaceFor(final fURI vid);

    interface Component extends Rec {
        default Machine machine() {
            Obj mach = this.parent();
            while (!mach.isNoObj() && !(mach instanceof Machine))
                mach = mach.parent();
            return mach instanceof Machine ? mach.as() : mach0();
        }
    }

    @Override
    // ======================== execution ========================
    default Obj apply(final Obj call) {
        return this.apply(call.asCode(), noobj());
    }

    /**
     * The machine's single source of truth for execution: compile once, then run the compiled code against
     * {@code start}. The processor does not re-resolve; runtime resolution is memoized by the compiler's resolver.
     */
    default Obj apply(final Code code, final Obj start) {
        // The lexical frame. Everything this code application compiles and runs happens inside a frame of this
        // machine, and pop() releases exactly what that frame introduced — nothing it inherited.
        //
        // It goes here rather than in the per-inst descent because the descents already have their own frame:
        // Processor.Helper.invokeCore pushes the current instruction's args as the INNER frame. This is the
        // LEXICAL one, and it belongs at the boundary where code is entered.
        //
        // pop() is in a finally because a frame that is not released leaks its imports, its bindings and its
        // peers — and an exception is exactly when you least want that.
        // APPLYING A MACHINE MOVES THE FRAME OF REFERENCE, AND LEAVES IT MOVED. You do not get a pointer back --
        // you name your way home (`*<./>`), which is the whole model. The frame, by contrast, IS scoped: push/pop
        // unwinds, because braces unwind.
        //
        // The frame unwinds here; the CURRENT does not. Applying a machine leaves you standing in it, and the
        // root is containment -- it does not change when you move within it.
        Machine.withPerspective(this);
        this.push();
        try {
            return this.processor().code(this.compiler().apply(code).asCode()).apply(start);
        } finally {
            this.pop();
        }
    }

    /**
     * The machine's own ISA slot, ignoring any frame, with its proto applied as the sibling accessors do.
     * Applying is not optional: the slot holds a proto, so returning it raw hands back the lambda, not an ISA.
     */
    // ======================== components ========================
    default InstSet ownInstset() {
        final Obj proto = this.at(uri(INSTSET));
        if (proto.isNoObj())
            return new BasicInstSet();
        // apply the slot's proto whenever it is an INST -- an instLambda is an Inst but isCall() is false for it,
        // so `proto.isCall() ? proto.apply() : proto` never evaluated the seed and ownInstset() fell through to
        // a throwaway on every call. Obj.apply() is the no-arg form that runs the lambda and yields the ISA.
        final Obj resolved = (proto.isInst() || proto.isCall()) ? proto.apply() : proto;
        // an unbound slot resolves to nothing, and "no ISA" is an EMPTY ISA rather than a failure — but it is this
        // machine's own BasicInstSet, not the shared instset0(), so an import has somewhere private to land.
        // instanceof, not isInstSet(): the mtron-type check rejected the seeded BasicInstSet and fell through to the
        // throwaway, so every call returned a different object and a machine-level write had nowhere to persist.
        // ownMemory() has always used instanceof Memory for exactly this reason — this mirrors it.
        return null == resolved || resolved.isNoObj() || !(resolved instanceof InstSet) ? new BasicInstSet() : (InstSet) resolved;
    }

    /**
     * The instruction set in effect for the current frame, or the machine's own slot when none is pushed.
     * The ISA question, as {@link #memory()} is the binding question.
     */
    default InstSet instset() {
        final Frame frame = FRAME.get();
        return null == frame ? this.ownInstset() : frame.instset();
    }

    /**
     * The memory in effect for the current frame: the facade over the world of spaces, itself a {@link Space}, and the
     * single interface for reading and writing addresses.
     * Its level is the frame's when one is live, the machine's otherwise.
     */
    default Memory memory() {
        final Frame frame = FRAME.get();
        return null == frame ? this.ownMemory() : frame.memory();
    }

    /**
     * the machine's OWN memory slot, ignoring any frame in effect
     */
    default Memory ownMemory() {
        final Obj proto = this.at(uri(MEMORY));
        if (proto.isNoObj())
            return new BasicMemory();
        // A slot holds a TEMPLATE; applying it is what yields the instance. The lambda closes over a memory
        // built at construction, so this hands back that object rather than constructing one — which is what
        // keeps the read path allocation-free while the slot read stays small. Same shape as ownInstset().
        final Obj resolved = proto.isCall() ? proto.apply() : proto;
        return resolved instanceof Memory ? (Memory) resolved : new BasicMemory();
    }

    /**
     * This thread's frame stack, not initialised eagerly: until {@link #push()} is used there is no frame, and every
     * component accessor falls through to the machine's own slot.
     */
    // ======================== frames ========================
    ThreadLocal<Frame> FRAME = new ThreadLocal<>();

    /**
     * This thread's live frames, keyed by address -- the downward link {@link #FRAME} cannot provide, since the cursor
     * only walks up. Ownership is the visibility boundary: another thread's frames are simply absent.
     */
    ThreadLocal<Map<fURI, Frame>> FRAMES = new ThreadLocal<>();

    /**
     * Push a frame for this machine and make it current, returning {@code this} for fluency.
     * The machine is not the frame: two pushes of one machine would share a set of component views.
     */
    default Machine push() {
        // The convenience: a frame nobody named. mintShortUUID is called with retryIfCollision=FALSE because its
        // check is a `readFromSpace` and push() runs on the machine's EXECUTION path (the scoped-import block
        // below), where a read is the shape that overflowed the stack once already. What is given up is only the
        // 32-bit birthday guarantee, and only among live siblings under one parent. The exact and cheaper check is
        // the parent's LIVE CHILDREN — no read, no materialization, and it is the structure Frame.next() needs — and
        // it lands with the frame tree (t-murdnd40-rpdahi), which is where this becomes `true` or a direct check.
        return this.push(CommonUtil.mintShortUUID(this.vid(), false));
    }

    /**
     * Apply the frame algebra's one morphism: a uri names the step and its coefficient the direction, so push and pop
     * are one operation and its inverse. Ascending is defined only when it undoes the step that brought us here.
     */
    default Obj move(final fURI extension) {
        final Frame frame = FRAME.get();
        final fURI here = (null == frame) ? this.vid() : frame.machine().vid();
        if (null == extension || null == here)
            return noobj();
        if (extension.isId() || extension.c().isZero())
            return extension.c().isZero() ? noobj() : this; // the identity pushes nothing; zero is the empty function
        if (extension.c().isNeg()) {
            // Ascend, but only the step that brought us here: this is ⟨u⟩⁻¹, defined iff ⟨u⟩ would have landed on
            // this frame. Answered from the ADDRESS alone — `here` minus its last segment, recomposed with the name
            // — deliberately, because a frame pushed from a machine that had no frame records parent == null, so
            // demanding a parent frame would make the inverse undefined at the very first level.
            // f(name) rather than asNode(): asNode() carries the {-1} coefficient into the product, and the question
            // here is about the address this step would have produced.
            if (null == frame || here.isId())
                return noobj();
            final fURI parentURI = here;
            if (!parentURI.extend(extension.name()).resolve().equals(here))
                return noobj();
            final Frame ancestor = frameAt(parentURI);   // a live ancestor, when it was itself pushed
            frame.machine().pop();
            // the ancestor frame if there is one, else the machine that lives at that address — the root is
            // registered in space even though it was never pushed, so both levels answer
            return (null != ancestor) ? ancestor.machine() : readFromSpace(parentURI);
        }
        return this.push(extension);
    }

    /**
     * Push a child frame whose vid strictly extends this machine's by {@code extension}, and return the child.
     * The extension is a uri because it is an expression: multi-segment, templated and composed steps all work.
     */
    default Machine push(final fURI extension) {
        final fURI parentVID = this.vid();
        // A machine with NO vid (test machines, and any rootless construct) has no address to extend, so the
        // extension becomes the child's address outright — and the descendant rule below does not apply, because
        // there is nothing to descend from. Only when there IS a parent address does a frame have to stay under it.
        final fURI childVID = ((null == parentVID) ? extension.resolve() : parentVID.resolve().extend(extension.name()).resolve()).c(extension.c());
        if (null != parentVID && !childVID.removeSubpath(parentVID.resolve()).asNode().pathString().equals(childVID.name()))
            // print the RESOLVED forms, because the raw ones are what made this message hard to act on: the check
            // compares resolved segment lists, so a raw `/.` and a raw `aaa` say nothing about which comparison
            // failed. A frame's address must strictly extend its parent's, and now the message shows both sides.
            throw MTronException.of("push(%s) does not descend from %s: resolved %s is not a strict descendant of %s",
                    extension, parentVID, childVID, parentVID.resolve());
        final Machine clone = (Machine) this.clone(this.jvm(), this.tid(), childVID);
        // Why the 3-arg clone rather than clone().selfVID(childVID): AbstractSpace.self() PINS an already-set vid
        // (`null == this.vid() ? vid : this.vid()`), so on a clone — which already carries the parent's vid —
        // selfVID is a silent no-op for the vid and the child would keep the parent's address. The 3-arg clone sets
        // the fields directly, which is what `Obj.vid(fURI)` relies on everywhere else. The guard below turns the
        // one remaining failure mode (a clone that is not a clone) into a named error instead of a moved parent.
        if (clone == this)
            throw MTronException.of("push(%s) cannot mint a child: clone() returned this machine, so the child would BE the parent", extension);
        final Machine child = clone;
        final Frame frame = new Frame(child, FRAME.get());
        FRAME.set(frame);
        frameRegister(childVID, frame);
        return child;
    }

    /**
     * The strict-descendant test: the child's path must have the parent's as a prefix and be strictly longer.
     */
    static boolean descendsFrom(final fURI child, final fURI parent) {
        if (null == child || null == parent)
            return false;
        final List<String> parentPath = parent.resolve().path();
        final List<String> childPath = child.resolve().path();
        return childPath.size() > parentPath.size() && childPath.subList(0, parentPath.size()).equals(parentPath);
    }

    /**
     * The frame AT this address for THIS thread, or null. A frame another thread owns is not visible here — that is
     * the ownership rule doing its job, not a miss. Null vid frames are never registered, so they resolve to null.
     */
    static Frame frameAt(final fURI vid) {
        final Map<fURI, Frame> live = FRAMES.get();
        return (null == live || null == vid) ? null : live.get(vid);
    }

        private static void frameRegister(final fURI vid, final Frame frame) {
        if (null == vid)
            return; // an addressable frame is one with an address; an unaddressed one is honestly unregistered
        Map<fURI, Frame> live = FRAMES.get();
        if (null == live) {
            live = new LinkedHashMap<>();
            FRAMES.set(live);
        }
        live.put(vid, frame);
    }

    private static void forget(final Frame frame) {
        final Map<fURI, Frame> live = FRAMES.get();
        if (null == live)
            return;
        live.remove(frame.machine().vid());
        if (live.isEmpty())
            FRAMES.remove(); // do not leave a per-thread structure behind once its owner has unwound
    }

    /**
     * Discard the current frame and release what it opened. {@code close()} reaches only {@code current()}, so a
     * frame never releases what it inherited.
     */
    default Machine pop() {
        final Frame frame = FRAME.get();
        if (null == frame) return this;
        FRAME.set(frame.parent());     // ← the parent is right here already
        forget(frame);
        frame.close();
        return FRAME.get() == null ? this : FRAME.get().machine();
    }

    static Frame frame() {
        return FRAME.get();
    }

    /**
     * The current frame's memory if it has already been materialized, else null -- and it must never allocate:
     * constructing an Obj resolves a type through a read, so materializing here would recurse.
     */
    static Memory frameMemory() {
        final Frame frame = FRAME.get();
        return null == frame ? null : frame.memoryView();
    }

    /**
     * the current frame's network if it has already been materialized, else null — never allocates
     */
    static Network frameNetwork() {
        final Frame frame = FRAME.get();
        return null == frame ? null : frame.networkView();
    }

    /**
     * The memory resolution reads through: the frame's if it already has one, else the machine's own.
     * It never materializes a frame, because a read that allocated would recurse.
     */
    default Memory resolutionMemory() {
        // Resolution reads the frame's memory if it HAS one, else the machine's own. Do NOT extend this to a frame's
        // ANCESTRY when it has none: that breaks resolution system-wide (plus a
        // StackOverflow in the unions), because a live frame is present for every scoped execution, not only when
        // current() is frame-aware.
        final Memory frame = frameMemory();
        return null != frame ? frame : this.ownMemory();
    }

    /**
     * the network resolution reads through — same rule, and the same reason
     */
    default Network resolutionNetwork() {
        final Network frame = frameNetwork();
        return null != frame ? frame : this.ownNetwork();
    }

    /**
     * Whether this machine answers for the address. Reachability belongs to the network; this routes to the one in
     * effect rather than to a default that knows nothing of the roster or the index.
     */
    // ======================== reachability ========================
    default boolean own(final fURI vid) {
        return this.resolutionNetwork().own(vid);
    }

    default boolean isPeer(final fURI vid) {
        return this.resolutionNetwork().isPeer(vid);
    }

    /**
     * The hosts this machine owns: the {@code host} of every mounted space that declares one -- declared
     * configuration, never derived from traffic, so a uri cannot make itself a peer.
     * Recomputed per frame rather than cached.
     */
    default Set<String> selfAuthorities() {
        final Set<String> authorities = new LinkedHashSet<>();
        this.resolutionMemory().spaces().jvm().values().stream()
                .filter(v -> v instanceof Space)
                .map(v -> (Obj) ((Space) v).at(uri(HOST)))
                .filter(Obj::isUri)
                .map(host -> host.uriValue().authority())
                .filter(Objects::nonNull)
                .forEach(authorities::add);
        return authorities;
    }

    /**
     * the peers in effect for the current frame; with no frame pushed this is the machine's own roster, so
     * composition only appears once {@link #push()} is used.
     */
    default Network network() {
        final Frame frame = FRAME.get();
        return null == frame ? this.ownNetwork() : frame.network();
    }

    /**
     * the machine's OWN roster, ignoring any frame in effect. Created rather than defaulted, for the same reason
     * as memory: an absent slot must become a real roster, or every peer registered into it vanishes silently.
     */
    default Network ownNetwork() {
        final Obj proto = this.at(uri(NETWORK));
        if (proto.isNoObj())
            return new BasicNetwork();
        final Obj resolved = proto.isCall() ? proto.apply() : proto;
        return resolved instanceof Network ? (Network) resolved : new BasicNetwork();
    }

        /**
         * One frame: the machine it belongs to, plus the component views it has materialized.
         * Views are cached on first access, and a frame that touches none allocates nothing.
         */
    // ======================== inner types ========================
    final class Frame {

        private final Machine machine;
        private final Frame parent;
        private InstSet instset;
        private Memory memory;
        private Network network;

        private Frame(final Machine machine, final Frame parent) {
            this.machine = machine;
            this.parent = parent;
        }

        Machine machine() {
            return this.machine;
        }

        Frame parent() {
            return this.parent;
        }

        /**
         * the cached view, or null when this component has never been touched. Non-materializing: reading it must
         * not be what forms the view, or the laziness would be unobservable (and the test for it impossible).
         */
        InstSet instsetView() {
            return this.instset;
        }

        Memory memoryView() {
            return this.memory;
        }

        Network networkView() {
            return this.network;
        }

        InstSet instset() {
            if (null == this.instset)
                // The frame's OWN side is a FRESH BasicInstSet, exactly as this frame's memory is a fresh BasicMemory
                // and its network a fresh BasicNetwork. That is what makes a frame private: writes land in the
                // frame's own level, never in the machine's slot and never in the library instset an import came
                // from. Reading the machine's slot here instead would share one ISA across every frame.
                this.instset = new InstSetUnion(this.inheritedInstset(), new BasicInstSet());
            return this.instset;
        }

        Memory memory() {
            if (null == this.memory) {
                final BasicMemory current = new BasicMemory();
                final MemoryUnion union = new MemoryUnion(this.inheritedMemory(), current);
                // The two sides answer for their own level, so a union needs two machines rather than one:
                // `previous().machine()` is the machine it inherited from, `current().machine()` is this one.
                // That only holds if each side's parent is right, and `parent` is what machine() walks.
                // `previous` is left alone on purpose — it is installed on the outer machine, and adopting it
                // here would silently make previous().machine() answer for THIS frame.
                union.parent(this.machine);
                current.parent(union);
                this.memory = union;
            }
            return this.memory;
        }

        Network network() {
            if (null == this.network) {
                final BasicNetwork current = new BasicNetwork();
                final NetworkUnion union = new NetworkUnion(this.inheritedNetwork(), current);
                union.parent(this.machine);
                current.parent(union);
                this.network = union;
            }
            return this.network;
        }

        private InstSet inheritedInstset() {
            return null == this.parent ? this.machine.ownInstset() : this.parent.instset();
        }

        private Memory inheritedMemory() {
            return null == this.parent ? this.machine.ownMemory() : this.parent.memory();
        }

        private Network inheritedNetwork() {
            return null == this.parent ? this.machine.ownNetwork() : this.parent.network();
        }

        /**
         * Revoke reachability before the spaces it was used to reach, so no peer can read a space that is midway
         * through closing: a read of a gone address fails loudly instead of resolving into a half-closed space.
         */
        private void close() {
            CommonUtil.close(this.instset);
            CommonUtil.close(this.network);
            CommonUtil.close(this.memory);
        }
    }

        /** @return this machine with the given instruction set bound */
    default Machine instset(final InstSet instset) {
        CommonUtil.close(this.atDirect(uri(INSTSET)));
        return this.at(uri(INSTSET), instset, MUTABLE).as();
    }

    default Machine instset(final Call instsetReference) {
        CommonUtil.close(this.atDirect(uri(INSTSET)));
        return this.at(uri(INSTSET), instsetReference, MUTABLE).as();
    }

        /** @return the machine's compiler, creating one if the slot is unbound */
    default Compiler compiler() {
        final Obj protoCompiler = this.atDirect(COMPILER).orThrow(MTronException.of("machine has no compiler: %s", this.type().vid()));
        if (protoCompiler.isCall())
            return protoCompiler.apply().as();
        return protoCompiler.as();
    }

        /** @return this machine with the given compiler bound */
    default Machine compiler(final Compiler compiler) {
        return this.at(uri(COMPILER), compiler, MUTABLE).as();
    }

    default Machine compiler(final Call templateCompiler) {
        return this.at(uri(COMPILER), templateCompiler, MUTABLE).as();

    }


        /** @return the machine's processor, creating one if the slot is unbound */
    default Processor processor() {
        final Obj protoProcessor = this.atDirect(PROCESSOR).orThrow(MTronException.of("machine has no processor: %s", this.type().vid()));
        if (protoProcessor.isCall())
            return protoProcessor.apply().as();
        return protoProcessor.clone().as();
    }

        /** @return this machine with the given processor bound */
    default Machine processor(final Processor processor) {
        return this.at(uri(PROCESSOR), processor, MUTABLE).as();
    }

    default Machine processor(final Call templateProcessor) {
        return this.at(uri(PROCESSOR), templateProcessor, MUTABLE).as();
    }

    class Helper {

            /**
             * The zero machine: no spaces, components or route table, and every mutator a no-op, so nothing written
             * against it can reach a live machine. It is the fallback {@link Machine#root()} serves before boot, and
             * constructing it has no side effects.
             */
        public static final class Machine0 extends MRec implements Machine {
            private static final Machine0 INSTANCE = new Machine0();

            public static Machine0 single() {
                return INSTANCE;
            }

            private Machine0() {
                super(Map.of(), MACH_MACHINE_TID.zero(), null);
            }

            @Override
            public Object sjvm() {
                return Map.of();
            }

            @Override
            public Map<Uri, Obj> routes() {
                return Map.of();
            }

            @Override
            public Stats stats() {
                return new MStats();
            }

            @Override
            public Obj read(final fURI vid) {
                return noobj();
            }

            @Override
            public Obj write(final fURI vid, final Obj obj) {
                return noobj();
            }

            @Override
            public boolean hasSpaceFor(final fURI vid) {
                return false;
            }

            @Override
            public void addSpace(final Space space) {
            }

            @Override
            public void removeSpace(final fURI vid) {
            }

            @Override
            public <SPACE extends Space> SPACE getSpace(final fURI pattern) {
                return null;
            }

            @Override
            public void registerRedirect(final fURI small, final fURI big) {
            }

            @Override
            public void unregisterRedirect(final fURI small, final fURI big) {
            }

            @Override
            public void registerPrefix(final fURI prefix, final fURI vid) {
            }

            @Override
            public fURI redirect(final fURI furi, final boolean big) {
                return NOOBJ;
            }

            @Override
            public <SPACE extends Space> SPACE getSpaceFor(final fURI vid) {
                return noobjSpace.single();
            }

        }

    }
}
