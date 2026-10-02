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
import studio.phaseshift.metatron.isa.sys.type.ExecutionStack;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicMachine;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicMemory;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicNetwork;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static studio.phaseshift.metatron.BootLoader.ROUTER;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.fURI.Singleton.NOOBJ;
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

    static Machine current() {
        return null == ROUTER ? mach0() : (Machine) ROUTER;
    }

    // ======================== the space funnel ========================
    // These belong on Machine; they delegate to Router only while Router still exists, so the bulk rename of
    // Machine.readFromSpace/writeToSpace to Machine.* is provably identical rather than a reimplementation.
    // Inlining the bodies is a separate step, after which Router can go.

    static Obj readFromSpace(final fURI vid) {
        return ExecutionStack.frame(ExecutionStack.exec(ExecutionStack.ExState.resolve_inst, "read " + vid),
                () -> null == ROUTER ? noobj() : Machine.current().read(vid));
    }

    static Obj readFromSpace(final String vid) {
        return Machine.readFromSpace(f(vid));
    }

    static Obj writeToSpace(final fURI vid, final Obj obj) {
        return ExecutionStack.frame(ExecutionStack.exec(ExecutionStack.ExState.apply_inst, "write " + vid),
                () -> null == ROUTER ? noobj() : Machine.current().write(vid, obj));
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
        return null != ROUTER;
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
        this.push();
        try {
            return this.processor().code(this.compiler().apply(code).asCode()).apply(start);
        } finally {
            this.pop();
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
     * Push a frame for this machine and make it current.
     * <p>
     * Returns {@code this} for fluency, and note what that implies: <b>the machine is not the frame</b>. Two
     * pushes of the same machine would share one set of component views, so the pushed machine must belong to the
     * frame. That is also why a frame's state cannot live on the machine — a machine is a value that may be
     * evaluated by several threads at once, while the frame stack is per-thread.
     */
    default Machine push() {
        FRAME.set(new Frame(this, FRAME.get()));
        return this;
    }

    /**
     * Discard the current frame and release what it opened. {@code close()} reaches only {@code current()}, so a
     * frame never releases what it inherited.
     */
    default void pop() {
        final Frame frame = FRAME.get();
        if (null != frame) {
            FRAME.set(frame.parent());
            frame.close();
        }
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
         * satisfies {@link Machine} as well as {@link Router}, so the zero of addressing is also
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
