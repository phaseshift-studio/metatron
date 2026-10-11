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
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.machine.AbstractMachine;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicInstSet;
import studio.phaseshift.metatron.isa.mach.type.memory.BasicMemory;
import studio.phaseshift.metatron.isa.mach.type.memory.MemoryUnion;
import studio.phaseshift.metatron.isa.mach.type.network.BasicNetwork;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;

import static studio.phaseshift.metatron.BootLoader.ROOT_MACHINE;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
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
        return null == ROOT_MACHINE ? mach0() : ROOT_MACHINE;
    }

    /**
     * The top of the containment this thread is in: the machine that owns its addresses and whose spaces answer.
     * Resolution reads THIS, never {@link #current()} -- a machine that EXECUTES code need not be able to RESOLVE it.
     */
    static Machine root() {
        return null == ROOT_MACHINE ? mach0() : ROOT_MACHINE;
    }

    /**
     * Where this thread stands: the machine it is evaluating in, or {@link #root()} when nothing moved it.
     * Moved by a machine dereference or {@link #current(Machine)}, never by an ordinary scope.
     */
    static Machine current() {
        final Machine machine = CURRENT.get();
        return null != machine ? machine : root();
    }

    static fURI relativeToCurrent(final fURI furi) {
        return Machine.current().vid().extend(furi);
    }

    static fURI relativeToCurrent(final String furi) {
        return Machine.relativeToCurrent(f(furi));
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

    static Obj read(final fURI vid) {
        if (null == ROOT_MACHINE)
            return noobj();
        // Pure pass-through: the single path to Memory. All routing (~ resolution, relative→stack, absolute→space)
        // lives in Memory.read, so there is exactly one place a read is turned into an address.
        return Machine.current().memory().read(vid);
    }

    static Obj read(final String vid) {
        return Machine.read(f(vid));
    }

    static Obj write(final fURI vid, final Obj obj) {
        if (null == ROOT_MACHINE)
            return noobj();
        // Pure pass-through: the single path to Memory (see read). No network-first, no prefix stripping.
        return Machine.current().memory().write(vid, obj);
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


    // ======================== the space contract and its index ========================

    interface Component extends Rec {
        default Machine machine() {
            Obj mach = this.parent();
            while (!mach.isNoObj() && !(mach instanceof Machine))
                mach = mach.parent();
            return mach instanceof Machine ? mach.as() : mach0();
        }
    }

    // @Override  (disabled with the apply() below)
    // ======================== execution ========================
    // TEMPORARILY DISABLED: this apply() re-homed CURRENT (Machine.current(this)) as a side effect, which made a bare
    // machine dereference (*X) into a context switch. Dereference must stay pure; the explicit move() instruction in
    // /m/mach is now the ONLY context switch. Until that is re-integrated, Machine inherits Obj.apply() (identity).
    /*
    default Obj apply(final Obj call) {
        return this.apply(call.asCode(), noobj());
    }

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
    */

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
        final Obj proto = this.atDirect(uri(MEMORY));
        if (proto.isNoObj()) {
            final Memory memory = new BasicMemory();
            this.at(uri(MEMORY), memory, MUTABLE);
            return memory;
        } else if (proto instanceof Memory) {
            return (Memory) proto;
        } else {
            final Memory memory = (proto.isInst() || proto.isCall()) ? proto.apply().as() : proto.as();
            this.at(uri(MEMORY), memory, MUTABLE);
            return memory;
        }
    }

    /**
     * Bind this machine's memory — used by {@link #push()} to hand a child frame its {@link MemoryUnion} over the
     * parent's memory.
     */
    default Machine memory(final Memory memory) {
        CommonUtil.close(this.atDirect(uri(MEMORY)));
        return this.at(uri(MEMORY), memory, MUTABLE).as();
    }

    /**
     * Mount this machine's own infra space and embed the machine rec in it. The space has pattern {@code <vid>/#},
     * so {@code ~/thread}, {@code ~/fail}, {@code ~/log} (and the rec's own entries — {@code ~/memory},
     * {@code ~/processor} …) all resolve here as space values, not through any vid intercept. Called for the root
     * in {@link AbstractMachine}'s constructor and for each child in {@link #push(fURI)}.
     */
    default Machine bootstrap() {
        if (null != this.vid()) {
            // The infra PATTERN is the resolved vid (/. → /#): /thread must match, and the root's "local" is
            // everything. But the machine rec is written at the UNRESOLVED node vid (/.): a rec at a node is a
            // single value, whereas at the branch / it would fan out into /q, /instset, … entries.
            final fURI resolved = this.vid().resolve();
            final memSpace infra = memSpace.unregistered(resolved.extend(ALL), resolved.extend("space"));
            infra.addQ(QCollection.docQ());
            infra.addQ(QCollection.incrQ());
            this.memory().addSpace(infra);
            // the machine rec embeds at its own vid: ~ → <vid> reaches it, and its entries are space embeddings.
            this.memory().write(this.vid(), this);
        }
        return this;
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
        final fURI childVID = ((null == parentVID) ? extension.resolve() : parentVID.resolve().extend(extension).resolve());
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
        // The child's memory is a union over this machine's memory: its own fresh level over the enclosing one.
        // Parent the union to the CHILD (not to the parent machine): Component.machine() walks parent(), and the
        // read path's getSpaceFor/readAbsolute/writeAbsolute use it to decide whether the machine answers for its
        // own rec keys. AbstractMachine.memory(Memory) stores only the resolvedMemory field (never at(), so the
        // shared jvm stays untouched), which is why the parent link must be set explicitly here.
        final MemoryUnion memory = new MemoryUnion(this.memory());
        memory.parent(child);
        child.memory(memory);
        child.bootstrap();
        // register the child rec in the ROOT's space (global): read(childVID) from ANY machine reaches it, so
        // move(childVID) works from a sibling at any depth. the child's data stays in its own infra, so isolation holds.
        Machine.root().memory().write(childVID, child);
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

    @Override
    default void close() {
        this.pop();
    }

    /**
     * The machine's roster of peers. Created rather than defaulted: an absent slot must become a real roster, or
     * every peer registered into it vanishes silently.
     * <p>
     * Resolved the same way {@link #memory()} is, and for the same three reasons. A slot that already holds a
     * network is handed back <b>as itself</b> — no apply, so a component can be bound at construction, before
     * there is any machine to compile with. Whatever is resolved is written <b>back into the slot</b>, so the
     * machine ends up holding its real components rather than a default that is re-minted on every read. And a
     * deferred seed (an inst) is applied, so the older spelling still works. Leaving the result unretained is what
     * kept this slot a template.
     */
    default Network network() {
        final Obj proto = this.atDirect(uri(NETWORK));
        if (proto.isNoObj()) {
            final Network network = new BasicNetwork();
            this.at(uri(NETWORK), network, MUTABLE);
            return network;
        } else if (proto instanceof Network) {
            return (Network) proto;
        } else {
            // an instLambda is an Inst whose isCall() is false, so the isInst() arm is what runs a deferred seed
            final Network network = (proto.isInst() || proto.isCall()) ? proto.apply().as() : proto.as();
            this.at(uri(NETWORK), network, MUTABLE);
            return network;
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

            public Obj read(final fURI vid) {
                return noobj();
            }

            public Obj write(final fURI vid, final Obj obj) {
                return noobj();
            }

        }

    }
}
