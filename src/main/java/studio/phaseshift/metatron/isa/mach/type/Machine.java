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
import studio.phaseshift.metatron.isa.mach.type.machine.BasicMemory;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicNetwork;
import studio.phaseshift.metatron.isa.sys.type.ExecutionStack;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;

import static studio.phaseshift.metatron.BootLoader.ROOT_MACHINE;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.NOOBJ;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;

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
 *     <li><b>the entry point</b> -- {@code read}, {@code write}, {@code loaded}.</li>
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
public interface Machine extends Rec, AutoCloseable {

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
        return jvmRoot();
    }

    /**
     * Where this thread stands: the machine it is evaluating in, or {@link #root()} when nothing moved it.
     * Moved by a machine dereference or {@link #current(Machine)}, never by an ordinary scope.
     */
    static Machine current() {
        final Machine machine = CURRENT.get();
        return null != machine ? machine : root();
    }

    /**
     * The frame of reference, per thread.
     */
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
    static Obj current(final Machine machine, final java.util.function.Supplier<Obj> fragment) {
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
    static Machine current(final Machine machine) {
        CURRENT.set(machine);
        return machine;
    }

    // ======================== the space funnel ========================
    // Reads and writes through root(), the machine whose spaces answer.
    //
    // WHY THE NAMES ARE read/write AND NOT read/write: Machine IS-A Space, so it already has
    // instance read(fURI)/write(fURI, Obj), and a static of the same name cannot coexist with them -- same signature,
    // same erasure. The longer names are that collision, not a preference; shortening them means either moving the
    // statics onto another type or taking the instance methods away, and neither is worth doing before the facade
    // work (memory() as the single read/write interface) settles which of the two is the entry point.
    // ======================== the entry point ========================
    static Obj read(final fURI vid) {
        return ExecutionStack.frame(ExecutionStack.exec(ExecutionStack.ExState.resolve_inst, "read " + vid),
                () -> null == ROOT_MACHINE ? noobj() : Machine.current().memory().read(vid));
    }

    static Obj read(final String vid) {
        return Machine.read(f(vid));
    }

    static Obj write(final fURI vid, final Obj obj) {
        return ExecutionStack.frame(ExecutionStack.exec(ExecutionStack.ExState.apply_inst, "write " + vid),
                () -> null == ROOT_MACHINE ? noobj() : Machine.current().memory().write(vid, obj));
    }

    static Obj write(final String vid, final Obj obj) {
        return Machine.write(f(vid), obj);
    }

    static Obj write(final Obj obj) {
        return Machine.write(obj.vid(), obj);
    }

    /**
     * The {@code /sys/mach} constant bootstrapped by {@code machInstSet.setup()}, falling back to {@link #mach0()}
     * before the constant is loaded.
     */
    static Machine defaultMachine() {
        return root();
    }

    // ======================== the address surface ========================
    // A Machine IS the address space, so these are the machine's own contract: declared here rather than inherited.

    static boolean loaded() {
        return null != ROOT_MACHINE;
    }

    /**
     * Narrow AutoCloseable's checked close() so callers need not handle an exception.
     */
    @Override
    default void close() {
    }

    // ======================== the space contract and its index ========================
    default Rec spaces() {
        return this.at(uri(SPACE)).orElse(rec());
    }

    boolean hasSpaceFor(final fURI vid);

    void addSpace(final Space space);

    void removeSpace(final fURI vid);

    <SPACE extends Space> SPACE getSpace(final fURI pattern);

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
        final Machine previous = CURRENT.get();
        Machine.current(this);
        this.push();
        try {
            return this.processor().code(this.compiler().apply(code).asCode()).apply(start);
        } finally {
            this.pop();
            if (null == previous)
                CURRENT.remove();
            else
                CURRENT.set(previous);
        }
    }

    // ======================== components ========================
    default InstSet instset() {
        final Obj proto = this.at(uri(INSTSET));
        if (proto.isNoObj())
            return new BasicInstSet();
        // apply the slot's proto whenever it is an INST -- an instLambda is an Inst but isCall() is false for it,
        // so `proto.isCall() ? proto.apply() : proto` never evaluated the seed and instset() fell through to
        // a throwaway on every call. Obj.apply() is the no-arg form that runs the lambda and yields the ISA.
        final Obj resolved = (proto.isInst() || proto.isCall()) ? proto.apply() : proto;
        return null == resolved || resolved.isNoObj() || !(resolved instanceof InstSet) ? new BasicInstSet() : (InstSet) resolved;
    }

    /**
     * The machine's memory: the facade over the world of spaces, itself a {@link Space}, and the single interface
     * for reading and writing addresses.
     */
    default Memory memory() {
        final Obj proto = this.at(uri(MEMORY));
        if (proto.isNoObj())
            return new BasicMemory();
        final Obj resolved = proto.isCall() ? proto.apply() : proto;
        return resolved instanceof Memory ? (Memory) resolved : new BasicMemory();
    }

    /**
     * This thread's frame stack, not initialised eagerly: until {@link #push()} is used there is no frame, and every
     * component accessor falls through to the machine's own slot.
     */
    /**
     * Push a child machine and make it current, returning the child.
     */
    default Machine push() {
        return this.push(CommonUtil.mintShortUUID(this.vid(), false));
    }

    /**
     * Push a child machine whose vid strictly extends this machine's by {@code extension}, and return the child.
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
        final Machine child = (Machine) this.clone(this.jvm(), this.tid(), childVID);
        if (child == this)
            throw MTronException.of("push(%s) cannot mint a child: clone() returned this machine, so the child would BE the parent", extension);
        child.parent(this);
        CURRENT.set(child);
        return child;
    }

    /**
     * Discard the current machine and release what it opened.
     */
    default Machine pop() {
        final Machine machine = CURRENT.get();
        if (null == machine)
            return this;
        final Obj parent = machine.parent();
        if (!(parent instanceof Machine parentMachine))
            return this;
        machine.close();
        CURRENT.set(parentMachine);
        return parentMachine;
    }

    /**
     * The machine's roster of peers. Created rather than defaulted: an absent slot must become a real roster, or
     * every peer registered into it vanishes silently.
     */
    default Network network() {
        final Obj proto = this.at(uri(NETWORK));
        if (proto.isNoObj())
            return new BasicNetwork();
        final Obj resolved = proto.isCall() ? proto.apply() : proto;
        return resolved instanceof Network ? (Network) resolved : new BasicNetwork();
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
     * @return the machine's compiler, creating one if the slot is unbound
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
     * @return the machine's processor, creating one if the slot is unbound
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
            public void close() {
            }

            private Memory memory = null;

            @Override
            public Memory memory() {
                if (null == this.memory)
                    this.memory = new BasicMemory();
                return this.memory;
            }

            private Network network = null;

            @Override
            public Network network() {
                if (null == this.network)
                    this.network = new BasicNetwork();
                return this.network;
            }

            private InstSet instset = null;

            @Override
            public InstSet instset() {
                if (null == this.instset)
                    this.instset = new BasicInstSet();
                return this.instset;
            }

            public Object sjvm() {
                return Map.of();
            }

            public Map<Uri, Obj> routes() {
                return Map.of();
            }

            public Stats stats() {
                return new MStats();
            }

            public Obj read(final fURI vid) {
                return noobj();
            }

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

            public void registerRedirect(final fURI small, final fURI big) {
            }

            public void unregisterRedirect(final fURI small, final fURI big) {
            }

            public void registerPrefix(final fURI prefix, final fURI vid) {
            }

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
