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
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.machine.AbstractMachine;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicInstSet;
import studio.phaseshift.metatron.isa.mach.type.memory.BasicMemory;
import studio.phaseshift.metatron.isa.mach.type.memory.MemoryUnion;
import studio.phaseshift.metatron.isa.mach.type.network.BasicNetwork;
import studio.phaseshift.metatron.isa.mach.type.network.PeerSpawn;
import studio.phaseshift.metatron.isa.mach.type.network.RemoteMachine;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;

import static studio.phaseshift.metatron.BootLoader.ROOT_MACHINE;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_REMOTE_MACHINE_TID;

/**
 * Machine: a SPACE OF SPACES -- the address space resolution happens in. Its members ({@code instset}, {@code memory},
 * {@code network}, {@code compiler}, {@code processor}) are rec entries, the shape of {@code machine::T}; {@link Memory}
 * is the facade over the world of spaces, while {@link Compiler} lowers code and {@link Processor} runs it and neither
 * is an address space.
 * <p>
 * Three names, three questions: {@link #jvmRoot()} is this JVM's bootstrap; {@link #root()} is the top of the
 * containment this thread is in, and is what resolution reads; {@link #current()} is where the thread stands.
 * <p>
 * There is no separate Frame object: <b>the machine IS the frame</b>. {@code push()} clones a child that owns its own
 * rec and its own component levels, and the enclosing machine is reached through each accumulating component's
 * {@code previous} link.
 * <p>
 * The members, by role:
 * <ol>
 *     <li><b>identity and place</b> -- {@code jvmRoot}, {@code root}, {@code mach0}, {@code defaultMachine}: where
 *     things are and who contains whom.</li>
 *     <li><b>the perspective</b> -- {@code CURRENT}, {@code current()}, {@code relativeToCurrent}: where THIS thread
 *     stands, which is what lets the machine be the frame.</li>
 *     <li><b>the address surface</b> -- {@code read}, {@code write}, {@code loaded}: the one way in, asking the
 *     network, then data memory, then instruction memory.</li>
 *     <li><b>the component contract</b> -- {@link Component} and its {@code machine()} walk.</li>
 *     <li><b>execution</b> -- {@code push}, {@code pop}, {@code move}. {@code apply} is inherited identity here: the
 *     re-homing of {@code CURRENT} as an apply side effect is disabled, so dereference stays pure and {@code move} is
 *     the only context switch.</li>
 *     <li><b>components</b> -- {@code compiler}, {@code processor}, {@code instset}, {@code memory}, {@code network},
 *     each with its setter and its {@code bootstrap}.</li>
 *     <li><b>inner types</b> -- {@link Component}, {@link Helper}, and {@link Helper.Machine0}, the zero machine.</li>
 * </ol>
 * <p>
 * The concrete machine is an address space ({@code AbstractMachine extends AbstractSpace}), but the space contract --
 * {@code addSpace}, {@code removeSpace}, {@code getSpaceFor}, {@code spaces}, and the route table -- is
 * {@link Memory}'s, reached through {@code memory()}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Machine extends Rec, AutoCloseable {

    // ======================== identity and place ========================

    static Machine mach0() {
        return Helper.Machine0.single();
    }

    /**
     * The machine ROOT — always, whatever perspective this thread is standing in. The bootstrap constant.
     */
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

    // ======================== the perspective ========================

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
     * The frame of reference, per thread: the machine this thread is evaluating in, or {@link #root()} when nothing
     * moved it. A worker starts where its caller stood and publishes where it ended.
     */
    ThreadLocal<Machine> CURRENT = new ThreadLocal<>();

    /**
     * Evaluate a fragment in another machine's frame of reference, restoring this thread's on the way out.
     * The scoped form: in at the dereference, out when the fragment that followed it ends.
     */
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

    // ======================== the address surface ========================
    // The one way in. A read asks the network, then data memory, then instruction memory; a write mirrors it. The
    // space contract itself -- addSpace/removeSpace/getSpaceFor/spaces and the route table -- is Memory's, reached
    // through memory().

    static Obj read(final fURI vid) {
        if (null == ROOT_MACHINE)
            return noobj();
        // Authority dispatch comes FIRST, before the handoff to Memory: an address carrying an authority a peer has
        // declared is that peer's to answer, and Memory only ever sees an address already localized. Network.read
        // answers noobj for an address with no authority, or one whose authority is not in the roster, so the local
        // path is untouched (an empty roster is the normal case).
        final Machine machine = Machine.current();
        final Obj dispatched = machine.network().read(vid);
        if (!dispatched.isNoObj())
            return dispatched;
        // Local routing (~ resolution, relative→stack, absolute→space) lives in Memory.read, so there is exactly one
        // place a read is turned into an address.
        final Obj inMemory = machine.memory().read(vid);
        if (!inMemory.isNoObj())
            return inMemory;
        // ... AND A READ READS INSTRUCTION MEMORY. A read is the language's ONE way in, so it answers from the
        // machine's own instset when data memory has nothing: that is where an instruction, a type or a declared
        // name comes from once it is not also mounted as a space, and it is what makes a local declaration shadow an
        // import. Fetched through here, NOT by a caller reaching for instset() directly — a selector may do that as
        // an optimization, but the way instructions are fetched is this read.
        // It is asked on a MISS rather than before memory because an ISA read scans that ISA's tables and walks the
        // frame chain, and every read in the VM comes through this method. Once the ISA mounts are dropped (nothing
        // of an instruction set is in the data index) this becomes the only source for its jurisdiction, and the
        // order stops mattering.
        if (Helper.isReadingInstructionMemory())
            return noobj();
        Helper.readingInstructionMemory(true);
        try {
            // readDeclared, NOT read: this is a FALLBACK, and a fallback must not cause work on the reader's
            // behalf. A schema instset may implement read() as "materialize me, then answer"
            // (SQLSchemaInstSet populates a table's schema on demand, which is right for a deliberate read of
            // that schema) — asking that here triggered a catalog walk from a logger's type lookup while a
            // tbleSpace was still constructing, and the database connection was not open yet.
            return machine.instset().readDeclared(vid);
        } finally {
            Helper.readingInstructionMemory(false);
        }
    }

    static Obj read(final String vid) {
        return Machine.read(f(vid));
    }

    static Obj write(final fURI vid, final Obj obj) {
        if (null == ROOT_MACHINE)
            return noobj();
        // Authority dispatch first, exactly as read: a declared peer's authority takes the write over the wire.
        // Network.write answers noobj for an undeclared authority, so the local path is untouched — but a declared
        // peer that is silent answers a fail, which must NOT be swallowed by the local fall-through.
        final Machine machine = Machine.current();
        final Obj dispatched = machine.network().write(vid, obj);
        if (!dispatched.isNoObj())
            return dispatched;
        // Local pass-through to Memory (see read).
        return machine.memory().write(vid, obj);
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

    static boolean loaded() {
        return null != ROOT_MACHINE;
    }


    // ======================== the component contract ========================

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

    /**
     * Apply code to a lhs <em>in this machine</em>: compile it against the machine's compiler, then run the compiled
     * form on its processor. This is the single place a {@link Code} hands off to a machine, which is what makes the
     * machine the unit of execution — a machine that executes elsewhere (a {@code RemoteMachine}) overrides this one
     * method and every caller above it (the console, {@code eval}, a nested code) follows without knowing.
     */
    default Obj applyCode(final Obj lhs, final Code code) {
        final Call call = code.tryToInst();
        if (call.isCode()) {
            // wrapStart prepends start(value) for a value lhs; a monadic lhs passes through unchanged and rides
            // START so the monad's loop/state context survives into the processor.
            final Code compiled = this.compiler().apply(Code.wrapStart(lhs, call.as())).asCode();
            return this.processor().code(compiled).apply(lhs.isMonad() ? lhs : noobj());
        }
        // single inst: dispatch by the inst's own monad flag. A monadic inst (loop()) receives the monad; a value
        // inst is resolved and applied against the monad's obj.
        final boolean monadic = call.isInst() && call.resolve(lhs).tid().hasQ(MONAD_IN);
        final Obj arg = lhs.isMonad() && !monadic ? lhs.asMonad().obj() : lhs;
        return call.resolve(arg).apply(arg);
    }

    // ======================== components ========================
    /**
     * The machine's instruction set: instruction memory, and the ONE locus for every instset the machine uses.
     * <p>
     * The slot holds the ISA as <b>itself</b> — an instance bound at construction, the way {@link #memory()} and
     * {@link #network()} are, and never a template the accessor applies. That reversal is the point: an ISA is a
     * component the machine owns, so it is created WITH the machine rather than materialized from a lambda.
     * <p>
     * A machine with no ISA yet gets a stable empty one ({@code AbstractMachine} memoizes it) rather than a slot
     * write: a pushed child shares its parent's rec map, and a rec write on a machine rec fails its own type check.
     */
    default InstSet instset() {
        final Obj proto = this.atDirect(uri(INSTSET));
        return proto instanceof InstSet isa ? isa : new BasicInstSet();
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
        if (this.hasVID()) {
            // The infra PATTERN is the resolved vid (/. → /#): /thread must match, and the root's "local" is
            // everything. But the machine rec is written at the UNRESOLVED node vid (/.): a rec at a node is a
            // single value, whereas at the branch / it would fan out into /q, /instset, … entries.
            final fURI resolved = this.vid().resolve();
            final memSpace infra = memSpace.unregistered(resolved.extend(ALL), resolved.extend("space"));
            infra.addQ(QCollection.docQ());
            infra.addQ(QCollection.incrQ());
            infra.addQ(QCollection.subQ());
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
        // A BARE SEGMENT, minted with NO base. push(extension) already anchors the child under this machine
        // (childVID = parentVID.extend(extension)), so seeding the base with this.vid() applied the parent's vid
        // TWICE: the child resolved to /parent/parent/<segment>, which the descendant check below rejects. The
        // result was that any push past depth 0 threw, while the first push from the root worked — because
        // mintShortUUID treats a null or /. base as "mint absolutely" and yields the segment alone. Passing null
        // asks for exactly that, so depth 0 is unchanged and the nest now goes as deep as the frame stack.
        return this.push(CommonUtil.mintShortUUID(null, false));
    }

    /**
     * Push a child machine whose vid strictly extends this machine's by {@code extension}, and return the child.
     * The extension is a uri because it is an expression: multi-segment, templated and composed steps all work.
     * <p>
     * An extension carrying an <b>authority</b> is a different boundary: it names another mtron instance rather
     * than another frame. That machine is forked as a separate OS process when the authority is on this same
     * physical machine (a loopback host), and merely declared as a peer otherwise — see {@link PeerSpawn}.
     */
    default Machine push(final fURI extension) {
        // AN AUTHORITY IS A PROCESS BOUNDARY. Nothing below applies: a peer has its own vid, its own memory and
        // its own lifetime, so it can neither descend from this machine nor share its memory. A HOST is the test
        // rather than hasAuthority(), which also demands an explicit port — a pushed host without one gets a free
        // port allocated, and the handle's vid then carries it.
        if (null != extension && extension.hasHost()) {
            // RESOLVE FIRST: the frame's address encodes the port, so the port has to exist before it is derived.
            final fURI peer = PeerSpawn.resolve(extension);
            // THE FRAME SPAWNS ITSELF, and it LIVES AT A LOCAL ADDRESS derived from the peer and anchored on the
            // ROOT: <root>/network/peer/<scheme>/<host>/<port>/<namespace>. Because the address is a path in THIS
            // machine, the frame registers where every machine does, ~-relative state (~/fail, ~/thread) stays
            // local, and a plain local read reaches it — no remote uri is ever written through the wire.
            //
            // ANCHORED ON THE ROOT, not on the current frame: a peer is a machine-level entity, and anchoring on
            // the frame that happens to be current would nest the same peer's address under the pushed frame when a
            // second push for the same authority happens from inside it (measured: <frame>/network/peer/…/worker
            // for the second push where the first got /network/peer/…/worker).
            //
            // ITS CONNECTION IS THE FRAME'S OWN NETWORK LEVEL: network.peer is <service-uri> => peer::T, and the
            // entry push was asked for carries the FULL declared address — the only thing that knows where the wire
            // goes and what namespace the node mounts. The reach (the transport) is filled in by the frame's own
            // constructor, and the level is built HERE rather than in that constructor because constructing a
            // component inside a frame re-enters type resolution while that frame is current.
            final BasicNetwork network = new BasicNetwork(CommonUtil.mutableMap(
                    uri(PEER), rec(CommonUtil.mutableMap(
                            PeerSpawn.service(peer),
                            rec(uri(AUTHORITY), uri(peer.toString()))))));
            return Machine.current(new RemoteMachine(
                    CommonUtil.mutableMap(
                            uri(PATTERN), uri(ALL),
                            uri(NETWORK), network),
                    MACH_REMOTE_MACHINE_TID, Network.Helper.peerPath(Machine.root().vid(), peer)));
        }
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
        // THE CHILD OWNS ITS REC. Passing null keeps the clone's OWN copy of the map instead of handing the child
        // this machine's map object. While the map was shared, `machine.at(<component key>)` — which is how an
        // ADDRESS read of `/xyz/instset` is answered — reported the ENCLOSING machine's component, while the
        // accessor (instset()) reported the child's: the same slot read two ways, disagreeing. A component belongs to
        // the machine that holds it, so the rec holding it must be the machine's too. The child still starts with a
        // SNAPSHOT of this machine's keys (clone() copies them), which is all the machine rec ever offered — `at()`
        // does not walk the parent chain.
        final Machine child = (Machine) this.clone(null, this.tid(), childVID);
        if (child == this)
            throw MTronException.of("push(%s) cannot mint a child: clone() returned this machine, so the child would BE the parent", extension);
        child.parent(this);
        // The child's memory is a union over this machine's memory: its own fresh level over the enclosing one.
        // Parent the union to the CHILD (not to the parent machine): Component.machine() walks parent(), and the
        // read path's getSpaceFor/readAbsolute/writeAbsolute use it to decide whether the machine answers for its
        // own rec keys. AbstractMachine.memory(Memory) stores only the resolvedMemory field, which is why the
        // parent link must be set explicitly here.
        final MemoryUnion memory = new MemoryUnion(this.memory());
        memory.parent(child);
        child.memory(memory);
        // The child's instruction set is its OWN level over this machine's — the same move as the memory union
        // above, and for the same reason: a component belongs to the machine that holds it, so a declaration or an
        // import made while standing in the child lands in the CHILD's level and the enclosing machine's vocabulary
        // is untouched.
        final BasicInstSet childInstSet = new BasicInstSet(this.instset());
        child.instset(childInstSet);
        // ... and the child's own components go into ITS rec, so that an ADDRESS read of a component slot
        // (`*</xyz/instset`) answers with the child's component exactly as the accessor does. Written into the map
        // directly rather than through at(): a rec write on a machine rec fails its own type check (objCheckAndSave
        // -> Lst.test, a null list element), which is why every component is seeded through the constructor map —
        // see AbstractMachine.withMemory — rather than assigned into a live machine.
        child.jvm().put(uri(MEMORY), memory);
        child.jvm().put(uri(INSTSET), childInstSet);
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
        // A pushed peer's lifetime is its frame's: the process this machine forked is reaped with the frame.
        PeerSpawn.reap(machine);
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
        // NO close of the outgoing value: it is a COMPONENT now rather than a template, and on a pushed child the
        // value being replaced is the ENCLOSING machine's ISA — closing it would release the parent's vocabulary.
        return this.at(uri(INSTSET), instset, MUTABLE).as();
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
         * INSTRUCTION MEMORY IS BEING READ — this thread is inside the instset hop of {@link Machine#read(fURI)}. A
         * read made FROM an ISA read is a data read: answering one resolves the dom/rng types of the pattern, and
         * resolving a type is itself a read, so without this the hop re-entered itself until the stack died
         * (measured: seven StackOverflowErrors in MachPushTest, where reads happen while a frame is mid-construction
         * and memory has not been populated yet).
         */
        private static final ThreadLocal<Boolean> READING_INSTRUCTION_MEMORY = ThreadLocal.withInitial(() -> false);

        static boolean isReadingInstructionMemory() {
            return READING_INSTRUCTION_MEMORY.get();
        }

        static void readingInstructionMemory(final boolean reading) {
            READING_INSTRUCTION_MEMORY.set(reading);
        }

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
