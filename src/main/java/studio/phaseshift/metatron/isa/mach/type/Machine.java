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
import static studio.phaseshift.metatron.isa.m.type.InstSet.instset0;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.SYS;

/**
 * Machine — the container that binds an ISA to its lowering and execution axes. A Machine IS-A
 * {@code Router}: it holds spaces, so a machine is a memory hierarchy — its own address space plus
 * the nested spaces of its instset, compiler and processor. The three members are held as rec
 * entries ({@code instset}, {@code compiler}, {@code processor}), which is exactly the shape of the
 * {@code machine::T} structural type.
 * <p>
 * Execution is <em>not</em> on this axis: {@link Processor} (a thread) runs code and
 * {@link Compiler} (a rec) lowers it — they are siblings of {@code Machine}, not refinements. A
 * machine <em>contains</em> them.
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
    static Machine root() {
        return null == ROOT_MACHINE ? mach0() : (Machine) ROOT_MACHINE;
    }

    /**
     * This thread's CURRENT machine — the one it is evaluating in, which is the root until a machine dereference
     * moves it. Two names because they are two things: the root is a constant, the current machine is a perspective.
     * Everything that resolves asks for the CURRENT machine, so a fragment teleported into another machine resolves
     * against THAT machine — which is the point — while boot and anything that genuinely means the root call root().
     * The perspective moves only when a deref yields a machine (withPerspective), never on an ordinary scope: moving
     * it per scope is what broke resolution (measured: 210 failures + 836 errors).
     */
    /**
     * The RESOLUTION AUTHORITY — the root, always, and deliberately NOT the perspective. Measured twice: answering
     * with a frame's machine loses the spaces (210 failures + 836 errors), and answering with the perspective loses
     * them again (1218 errors) — same signature both times, "no active space supports pattern /m/mach/machine".
     * The reason is the same both times: apply(Code, Obj) runs constantly on machines that carry only their OWN
     * memory — clones pushed as frames, component sub-machines — and those have no space index. A machine that
     * EXECUTES code is not necessarily a machine that can RESOLVE it. Use perspective() for "where am I standing".
     */
    static Machine current() {
        final Machine authority = AUTHORITY.get();
        return null != authority ? authority : root();
    }

    /** Where this thread is standing: the root until a machine application or dereference moves it. */
    static Machine perspective() {
        final Machine machine = PERSPECTIVE.get();
        return null != machine ? machine : root();
    }

    /**
     * This thread's current PERSPECTIVE — the machine whose frame of reference it is evaluating in. Deliberately not
     * the frame stack: frames scope BRACES (they nest and unwind per instruction), while the perspective moves only
     * when a dereference yields a MACHINE and restores when the fragment that followed it ends. That separation is the
     * whole point — the perspective is a value passed between machines, not a register resolution walks. `current()`
     * still answers with the ROOT on purpose: making it frame-aware loses the spaces (measured: 210 failures + 836
     * errors), because a frame is live for every scoped execution while a perspective moves only on a machine deref.
     */
    ThreadLocal<Machine> PERSPECTIVE = new ThreadLocal<>();

    /**
     * The machine whose SPACES answer for resolution right now — a reference, not a JVM constant, which is the whole
     * point: "fall through to the root" would mean the root of whichever JVM this happens to be, and that is the wrong
     * root the moment you are standing inside a machine on another one. So the authority is captured when you land:
     * teleporting to a machine that can resolve makes THAT machine your root, and teleporting to one that cannot
     * (a clone executing local code, a component sub-machine) leaves the authority exactly where it was.
     */
    ThreadLocal<Machine> AUTHORITY = new ThreadLocal<>();

    /**
     * Evaluate a fragment in another machine's frame of reference, restoring this thread's on the way out. This is the
     * scoped half of the teleport: in at the dereference, out when the fragment that followed it ends — the same
     * try/finally shape as a scoped block, but keyed on the MACHINE and triggered by a machine deref, never by an
     * ordinary scope.
     */
    static Obj withPerspective(final Machine machine, final java.util.function.Supplier<Obj> fragment) {
        final Machine previous = PERSPECTIVE.get();
        final Machine previousAuthority = AUTHORITY.get();
        PERSPECTIVE.set(machine);
        try {
            return fragment.get();
        } finally {
            if (null == previous)
                PERSPECTIVE.remove();
            else
                PERSPECTIVE.set(previous);
            if (null == previousAuthority)
                AUTHORITY.remove();
            else
                AUTHORITY.set(previousAuthority);
        }
    }

    /**
     * Move this thread's perspective and LEAVE it there — no lambda, no automatic restore. The perspective does not
     * change again until another call to either overload, which is the "name your way home" half of the model: you
     * are not handed a return pointer, you address where you want to be (`*</.>` takes you back). Use the two-arg
     * form when the move is scoped to one fragment; use this one when the move IS the statement.
     */
    static Machine withPerspective(final Machine machine) {
        PERSPECTIVE.set(machine);
        return machine;
    }

    /**
     * You ARRIVED here — this machine was resolved from an address (`mach://`, `http://`, another JVM's space). What
     * you land on becomes the authority as well: its root is your root while you stand there. This is the ONLY thing
     * that moves the authority, which is why "whose root" never needs a JVM constant. Contrast withPerspective(),
     * which is for EXECUTING in a machine (a frame, a clone): same JVM, same root, so the authority stays put.
     */
    static Machine arriveAt(final Machine machine) {
        PERSPECTIVE.set(machine);
        AUTHORITY.set(machine);
        return machine;
    }

    /**
     * Whether a machine can answer for itself: it has spaces of its own. A machine that EXECUTES code need not be one
     * that can RESOLVE it — a clone pushed as a frame carries only its own memory — and asking this at teleport time
     * rather than per read keeps the hot path a single ThreadLocal lookup.
     */
    // ======================== the space funnel ========================
    // These belong on Machine; they delegate to Router only while Router still exists, so the bulk rename of
    // Machine.readFromSpace/writeToSpace to Machine.* is provably identical rather than a reimplementation.
    // Inlining the bodies is a separate step, after which Router can go.

    static Obj readFromSpace(final fURI vid) {
        return ExecutionStack.frame(ExecutionStack.exec(ExecutionStack.ExState.resolve_inst, "read " + vid),
                () -> null == ROOT_MACHINE ? noobj() : Machine.current().read(vid));
    }

    static Obj readFromSpace(final String vid) {
        return Machine.readFromSpace(f(vid));
    }

    static Obj writeToSpace(final fURI vid, final Obj obj) {
        return ExecutionStack.frame(ExecutionStack.exec(ExecutionStack.ExState.apply_inst, "write " + vid),
                () -> null == ROOT_MACHINE ? noobj() : Machine.current().write(vid, obj));
    }

    static Obj writeToSpace(final String vid, final Obj obj) {
        return Machine.writeToSpace(f(vid), obj);
    }

    static Obj writeToSpace(final Obj obj) {
        return Machine.writeToSpace(obj.vid(), obj);
    }

    /**
     * The default machine — the {@code /sys/mach} constant bootstrapped by {@code machInstSet.setup()}.
     * Falls back to {@link #mach0()} before the constant is loaded.
     */
    static Machine defaultMachine() {
        final Obj machine = Machine.readFromSpace(SYS.extend(MACH));
        return machine.isNoObj() ? mach0() : machine.as();
    }

    static Machine accessMachine(final fURI uri, final Machine defaultMachine) {
        fURI running = uri;
        while (!running.isEmpty()) {
            try {
                final Obj machine = Machine.readFromSpace(running);
                if (MACH_MACHINE_TID.extend(ALL).test(machine.tid()))
                    return Rec.wrap(machine, BasicMachine.class);
                running = running.retract(1);
            } catch (final MTronException e) {
                break; // TODO: allow no active space pass through
            }
        }
        return defaultMachine;
    }

    // ======================== the address surface ========================
    // A Machine IS the address space, so these are the machine's own contract. They used to be inherited from
    // Router; Router is gone and Space is the parent now, so they are declared here rather than borrowed.

    static boolean loaded() {
        return null != ROOT_MACHINE;
    }

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
    default Obj apply(final Obj call) {
        return this.apply(call.asCode(), noobj());
    }

    /**
     * The machine's single source of truth for code execution: compile once (rewrite → resolve →
     * type) then run the compiled code against {@code start} on this machine's processor. The
     * processor does not re-resolve — the compiled code short-circuits via its
     * {@code isResolved(true)} gate, and runtime (element-type-dependent) resolution is memoized by
     * the compiler's resolver.
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
        final Machine previous = Machine.current();
        Machine.withPerspective(this);
        this.push();
        try {
            return this.processor().code(this.compiler().apply(code).asCode()).apply(start);
        } finally {
            this.pop();
            Machine.withPerspective(previous);
        }
    }

    /**
     * @return the machine's instruction set (ISA), or {@code null} when none is bound
     */
    /**
     * The machine's own ISA slot, applying its proto construction exactly as {@link #compiler()} and
     * {@link #processor()} do.
     * <p>
     * The application is not optional: the slot holds a <em>proto</em> — {@code BasicMachine.of} binds
     * {@code instLambda(ignore -> null)} — so returning the slot's value directly hands back the lambda rather
     * than an ISA, and any caller that casts it fails. This accessor had no callers until frames needed it, which
     * is why the mismatch went unnoticed; the sibling accessors have always applied their protos.
     */
    default InstSet ownInstset() {
        final Obj proto = this.at(uri(INSTSET));
        if (proto.isNoObj())
            return instset0();
        final Obj resolved = proto.isCall() ? proto.apply() : proto;
        // an unbound slot resolves to nothing, and "no ISA" is the empty ISA rather than a failure
        return null == resolved || resolved.isNoObj() || !resolved.isInstSet() ? instset0() : resolved.as();
    }

    /**
     * the ISA in effect for the <em>current frame</em>. With no frame pushed this is exactly the machine's own
     * slot, so composition is introduced by {@link #push()} and nothing changes until it is used.
     */
    default InstSet instset() {
        final Frame frame = FRAME.get();
        return null == frame ? this.ownInstset() : frame.instset();
    }

    /**
     * the memory in effect for the current frame — a relative-URI space
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
     * The current thread's frame stack. Deliberately <b>not</b> initialised eagerly: until {@link #push()} is
     * used there is no frame, and every component accessor falls straight through to the machine's own slot —
     * which is what makes this inert until frames are actually pushed.
     */
    ThreadLocal<Frame> FRAME = new ThreadLocal<>();

    /**
     * The live frames of THIS thread — the owner — keyed by the frame's own address, which encodes its whole parent
     * chain. This is the DOWNWARD link the frame stack cannot provide: {@code parent} walks up, and a
     * {@code ThreadLocal<Frame>} holds only the cursor, so without this nothing could resolve an address at all.
     * <p>
     * Deliberately a plain ThreadLocal, never eagerly initialized and never touched on a read path, for the same
     * reason FRAME is: materializing anything while resolving would recurse. A frame another thread pushed is
     * simply not in this map — ownership is the visibility boundary, so no lock is needed and one thread's frames
     * cannot appear in another's view.
     */
    ThreadLocal<Map<fURI, Frame>> FRAMES = new ThreadLocal<>();

    /**
     * Push a frame for this machine and make it current.
     * <p>
     * Returns {@code this} for fluency, and note what that implies: <b>the machine is not the frame</b>. Two
     * pushes of the same machine would share one set of component views, so the pushed machine must belong to the
     * frame. That is also why a frame's state cannot live on the machine — a machine is a value that may be
     * evaluated by several threads at once, while the frame stack is per-thread.
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
     * The frame algebra's ONE morphism application, and the only frame operation mtron needs to see.
     * <p>
     * NOT named `apply`: `apply(Obj)` is the engine's code-application path and a uri IS an Obj, so an
     * `apply(fURI)` overload silently captures every uri argument — including the ones that mean "apply this uri as
     * code/inst" — and the damage surfaces far away as a cast failure (`argFrames cannot be cast to Inst`). The
     * algebra's name belongs on the INST (in the machine's rec, `<./+>`); this method is its Java face.
     * <p>
     * A uri names a morphism and its coefficient names the direction, so push/pop are not two operations but one
     * operation and its inverse — the groupoid of frames, where the extension IS the name:
     * <pre>
     *   apply(&lt;.&gt;)            -&gt; this          the ring's identity: the zero displacement pushes NO frame
     *   apply(+1 u)         -&gt; Machine(here·u) descend; refused when u does not strictly extend here
     *   apply({-1} u)       -&gt; the parent      ascend, IFF this step is the one that brought us here
     *   apply({-1} u)       -&gt; noobj           otherwise: you cannot invert a step you did not take
     *   apply({0} u)        -&gt; noobj           the zero morphism is the empty function
     * </pre>
     * The guard on the inverse is what makes this total over the morphisms: {@code ⟨u⟩⁻¹∘⟨u⟩ = 1} holds only when
     * the frame standing here is the one {@code u} would have produced, so ascending is defined exactly when it
     * undoes the step that was taken — otherwise it is undefined rather than a silent mis-pop.
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
     * Push a CHILD frame whose vid strictly extends this machine's vid by {@code extension}, and return the CHILD.
     * <p>
     * The extension is a URI rather than a label because it is an EXPRESSION: a multi-segment, templated or
     * composed extension all work through the same path arithmetic. Composition is {@code mult}, and since a `..`
     * segment is arithmetic, `..`, `../..` and an absolute `/other/path` all collapse into the single violation
     * below rather than aliasing a sibling or an ancestor.
     * <p>
     * The child is minted with {@code clone().selfVID(...)} — a clone whose vid is then set — which mints the
     * address WITHOUT writing it back to space. {@code vid(...)} would additionally write to space, and that cost
     * is only worth paying when the frame must be referenceable from outside by address.
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
     * The strict-descendant test, shared by push() and by the children enumeration so the two cannot disagree about
     * what "under" means: the child's path must have the parent's as a PREFIX and be strictly longer.
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

    /**
     * The addresses of the live frames strictly UNDER parentVID — zero or more, which is why Frame.next() is
     * {@code {*}} and not {@code {?}}: a leaf and a freshly pushed frame are both honest zeros. Addresses only; no
     * component is touched, so enumerating the tree never materializes it (a ComponentUnion's own next() would).
     */
    static List<fURI> frameAddressesUnder(final fURI parentVID) {
        final Map<fURI, Frame> live = FRAMES.get();
        if (null == live || null == parentVID)
            return List.of();
        return live.keySet().stream().filter(vid -> descendsFrom(vid, parentVID)).toList();
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
     * The current frame's memory <b>if it has already been materialized</b>, else null. Never allocates.
     * <p>
     * This exists because the read path must not allocate. Constructing an Obj runs its type check, the type
     * check resolves a type through {@code Machine.readFromSpace}, and that lands back in {@code read} — so a
     * lookup that materialized a frame would recurse until the stack died. Resolution therefore takes the frame's
     * memory only when a frame already has one, which today means only after something wrote.
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
     * The memory resolution reads through: the frame's if it already has one, else the machine's own. It never
     * materializes a frame — a lookup that did would allocate a component on the read path, and constructing an
     * Obj runs a type check that resolves a type through the router and lands back in {@code read}.
     */
    default Memory resolutionMemory() {
        // Resolution reads the frame's memory if it HAS one, else the machine's own. Do NOT extend this to a frame's
        // ANCESTRY when it has none: measured, that breaks resolution system-wide (209 failures + 1450 errors, plus a
        // StackOverflow in the unions), because a live frame is present for every scoped execution, not only when
        // current() is frame-aware. Two attempts to make a live frame answer for itself have now failed here.
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
     * Reachability questions are answered by the network, and {@code Machine} only routes them to the one in
     * effect. Without these the {@code Router} defaults would answer instead, and they know nothing about the
     * roster or the index.
     */
    default boolean own(final fURI vid) {
        return this.resolutionNetwork().own(vid);
    }

    default boolean isPeer(final fURI vid) {
        return this.resolutionNetwork().isPeer(vid);
    }

    /**
     * The authorities this machine owns — the {@code host} of every mounted space that declares one. Declared
     * configuration, never derived from traffic: a URI must not be able to make itself a peer.
     * <p>
     * It lives here rather than on {@link Network} because it is derived from the memory's index, and it reads
     * through {@link #resolutionMemory()} so that asking it allocates nothing on the read path. It is recomputed
     * rather than cached: with a frame-scoped index there is no longer one answer per router, and ownership is
     * per-frame — a machine that mounts a space naming a host owns that authority for its frame's lifetime.
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
     * One frame of reference: the machine it belongs to, plus the component views it has materialized.
     * <p>
     * A view is created <b>on first access and then cached</b>, and the caching is not an optimisation — forming
     * a fresh union per access would mean two calls in the same frame wrote into two different {@code current()}s,
     * so a write followed by a read would silently miss it. A frame that never touches a component still
     * allocates nothing for it.
     */
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
                this.instset = new InstSetUnion(this.inheritedInstset(), instset0());
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
         * Order matters: reachability is revoked before the spaces it was used to reach, so no peer can read a
         * space that is midway through closing. A read of a gone address then fails loudly — which is the whole
         * point of the lease — instead of resolving into a half-closed space.
         */
        private void close() {
            CommonUtil.close(this.instset);
            CommonUtil.close(this.network);
            CommonUtil.close(this.memory);
        }
    }

    /**
     * @return this machine with the given instruction set bound
     */
    default Machine instset(final InstSet instset) {
        CommonUtil.close(this.atDirect(uri(INSTSET)));
        return this.at(uri(INSTSET), instset, MUTABLE).as();
    }

    default Machine instset(final Call instsetReference) {
        CommonUtil.close(this.atDirect(uri(INSTSET)));
        return this.at(uri(INSTSET), instsetReference, MUTABLE).as();
    }

    /**
     * @return the machine's compiler, or {@code null} when none is bound
     */
    default Compiler compiler() {
        final Obj protoCompiler = this.atDirect(COMPILER).orThrow(MTronException.of("machine has no compiler: %s", this.type().vid()));
        if (protoCompiler.isCall())
            return protoCompiler.apply().as();
        return protoCompiler.as();
    }

    /**
     * @return this machine with the given compiler bound
     */
    default Machine compiler(final Compiler compiler) {
        return this.at(uri(COMPILER), compiler, MUTABLE).as();
    }

    default Machine compiler(final Call templateCompiler) {
        return this.at(uri(COMPILER), templateCompiler, MUTABLE).as();

    }


    /**
     * @return the machine's processor, or {@code null} when none is bound
     */
    default Processor processor() {
        final Obj protoProcessor = this.atDirect(PROCESSOR).orThrow(MTronException.of("machine has no processor: %s", this.type().vid()));
        if (protoProcessor.isCall())
            return protoProcessor.apply().as();
        return protoProcessor.clone().as();
    }

    /**
     * @return this machine with the given processor bound
     */
    default Machine processor(final Processor processor) {
        return this.at(uri(PROCESSOR), processor, MUTABLE).as();
    }

    default Machine processor(final Call templateProcessor) {
        return this.at(uri(PROCESSOR), templateProcessor, MUTABLE).as();
    }

    class Helper {

        /**
         * The zero machine — {@code machine::T.zero()}: no spaces, no instset, no compiler, no
         * processor, and no route table. It is the fallback {@link Machine#current()} serves before
         * boot.
         * <p>
         * It is deliberately inert: every mutator is a no-op, so nothing written against the zero
         * can reach the live router, and — unlike a machine built by {@code BasicMachine.of}, which carries the
         * {@code +/#} stack space and real registration — constructing it has no side effects. It
         * satisfies {@link Machine}, so the zero of addressing is also
         * the zero of execution.
         */
        public static final class Machine0 extends MRec implements Machine {
            private static final Machine0 INSTANCE = new Machine0();

            /**
             * The machine's identity rendering — {@code machine::[pattern=>#]@/vid}.
             */
            public static String routerToString(final Machine machine) {
                return machine.tid() + "::[pattern=>#]@" + machine.vid();
            }

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
