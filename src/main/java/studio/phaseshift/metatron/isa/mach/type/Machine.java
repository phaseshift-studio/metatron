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

import studio.phaseshift.metatron.BootLoader;
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
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyObjLogger;
import studio.phaseshift.metatron.isa.sys.LogObj;
import studio.phaseshift.metatron.isa.web.space.mdns.handler.mdns_peerHandler;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.BootLoader.ROOT_MACHINE;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.*;
import static studio.phaseshift.metatron.isa.web.space.mdns.handler.mdns_peerHandler.DEFAULT_SERVICE;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

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

    /**
     * The frame of reference, per thread: the machine this thread is evaluating in, or {@link #root()} when nothing
     * moved it. A worker starts where its caller stood and publishes where it ended.
     */
    ThreadLocal<Machine> CURRENT = new ThreadLocal<>();

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
        // THE ROUTER OF ROUTERS ALIGNS THE NAME ONCE. a prefixed spelling (ide:command) names the same
        // resource as its aligned form (/m/ide/inst/command), and each component keys by the canonical
        // form -- network authority, memory's index, the instset tables -- so the gatekeeper aligns HERE,
        // once, and every dispatch below sees one name. memory also aligns internally (it owns the
        // tables, for now), which is idempotent; the fallback to instruction memory does not, and a raw
        // spelling can never match tables keyed by full identity. a scheme-less vid passes through
        // untouched: alignPrefix normalizes to coefficient one, which must not be imposed on an ordinary
        // read, and a RELATIVE name stays relative -- "in the frame" is memory's contract to interpret.
        final fURI aligned = vid.hasScheme() ? machine.memory().alignPrefix(vid) : vid;
        final fURI name = aligned.equals(vid.one()) ? vid : aligned;
        final Obj dispatched = machine.network().read(name);
        if (!dispatched.isNoObj())
            return dispatched;
        // Local routing (~ resolution, relative→stack, absolute→space) lives in Memory.read, so there is exactly one
        // place a read is turned into an address.
        final Obj inMemory = machine.memory().read(name);
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
            return machine.instset().readDeclared(name);
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
        // aligned once at the gatekeeper, exactly as read -- a prefixed spelling must land where its
        // aligned form lands, on the wire and in the index alike.
        final fURI alignedW = vid.hasScheme() ? machine.memory().alignPrefix(vid) : vid;
        final fURI name = alignedW.equals(vid.one()) ? vid : alignedW;
        final Obj dispatched = machine.network().write(name, obj);
        if (!dispatched.isNoObj())
            return dispatched;
        // Local pass-through to Memory (see read).
        return machine.memory().write(name, obj);
    }

    static Obj write(final String vid, final Obj obj) {
        return Machine.write(f(vid), obj);
    }

    static Obj write(final Obj obj) {
        return Machine.write(obj.vid(), obj);
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
     * Bind this machine's network — used by {@link #push(fURI)} to hand a child frame its own
     * {@link studio.phaseshift.metatron.isa.mach.type.network.NetworkUnion} level. The close on the
     * displaced value is the close-before-clear contract: whatever reach the old level held hangs up.
     */
    default Machine network(final Network network) {
        CommonUtil.close(this.atDirect(uri(NETWORK)));
        return this.at(uri(NETWORK), network, MUTABLE).as();
    }

    /**
     * Mount this machine's own infra space and embed the machine rec in it. The space has pattern {@code <vid>/#},
     * so {@code ~/thread}, {@code ~/sys/fail}, {@code ~/sys/log} (and the rec's own entries — {@code ~/memory},
     * {@code ~/processor} …) all resolve here as space values, not through any vid intercept. Called for the root
     * in {@link AbstractMachine}'s constructor and for each child in {@link #push(fURI)}.
     * <p>
     * Everything here goes through {@code this.memory()} at a resolved address, because bootstrap runs before the machine is reachable through {@code Machine.write} or {@code ~}.
     */
    default Machine bootstrap() {
        if (this.hasVID()) {
            // The infra PATTERN is the resolved vid (/. → /#): /thread must match, and the root's "local" is
            // everything. But the machine rec is written at the UNRESOLVED node vid (/.): a rec at a node is a
            // single value, whereas at the branch / it would fan out into /q, /instset, … entries.
            final fURI resolved = this.vid().resolve();
            final memSpace sysSpace = memSpace.unregistered(resolved.extend("sys").extend(ALL), resolved.extend("sys").extend(SPACE));
            sysSpace.addQ(QCollection.docQ());
            sysSpace.addQ(QCollection.subQ());
            sysSpace.addQ(QCollection.incrQ());
            sysSpace.addQ(QCollection.mimeQ());
            sysSpace.addQ(QCollection.lockQ());
            this.memory().addSpace(sysSpace);
            // This machine's OWN address space, at <vid>/# (the root's is /#): what a vid-less `#` cache used to
            // claim by accident, before it was made unregistered. It must be PER MACHINE — a single root /# also
            // matches /xyz/iso, so a child's write would land in the root and outlive its frame. The vid-scoped
            // pattern is more specific than /# and less specific than every mounted space, so mostSpecific gives
            // each frame its own level: own, no leak up, no leak across — by construction rather than by accident.
            final memSpace machineSpace = memSpace.unregistered(resolved.extend(ALL), resolved.extend(SPACE));
            // The machine's own address space carries the same q-processors as its infra space: an address that
            // lands here (a frame that mints no infra of its own — a peer frame, say — resolves ~/sys/fail into
            // this catch-all) must still find the processor its ?q names, or the write is refused as unattached.
            machineSpace.addQ(QCollection.docQ());
            machineSpace.addQ(QCollection.subQ());
            machineSpace.addQ(QCollection.incrQ());
            machineSpace.addQ(QCollection.mimeQ());
            machineSpace.addQ(QCollection.lockQ());
            this.memory().addSpace(machineSpace);
            // the machine rec embeds at its own vid: ~ → <vid> reaches it, and its entries are space embeddings.
            this.memory().write(this.vid(), this);
            // this machine's log sink: a frame with none answers noobj, and GraphittyObjLogger.doLog then logs every level
            final fURI vid = this.vid().resolve().extend(Helper.SYS_DIR.extend(LOGG));
            GraphittyObjLogger.setLogger(Helper.LOG_SINK);
            this.memory().write(vid, LogObj.sink(Helper.logLevels(this), vid));
            // DISCOVERY SOURCES come up with the ROOT, once, and only after its infra exists: a source is a
            // handler that announces this machine and writes what it hears into the roster. Guarded on the root's
            // VID rather than ROOT_MACHINE — that constant is assigned AFTER this constructor returns, so it is
            // still null here. One responder per host, not per frame: the roster is read at the root.
            if (ROOT_MACHINE_VID.equals(this.vid())) {
                //this.at(NETWORK).asRec().at(uri(SOURCE), rec(uri(webInstSet.MDNS_TID.name()), uri(webInstSet.MDNS_TID)), MUTABLE);
                //this.logger().error("starting sources: %s", this.network().source());
                //   Helper.startSources(this);
            }
        }
        return this;
    }

    /**
     * This thread's frame stack, not initialized eagerly: until {@link #push()} is used there is no frame, and every
     * component accessor falls through to the machine's own slot.
     */
    /**
     * Push a child machine and make it current, returning the child.
     */
    default Machine push() {
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
            // machine, the frame registers where every machine does, ~-relative state (~/sys/fail, ~/thread) stays
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
            final BasicNetwork network = new BasicNetwork(mutableMap(
                    uri(PEER), rec(mutableMap(
                            PeerSpawn.service(peer),
                            rec(uri(AUTHORITY), uri(peer.toString()))))));
            return Machine.current(new RemoteMachine(
                    mutableMap(
                            uri(PATTERN), uri(ALL),
                            uri(NETWORK), network),
                    MACH_REMOTE_MACHINE_TID, Network.Helper.peerPath(peer)));
        }
        final fURI parentVID = this.vid();
        // The ADDRESS ARITHMETIC IS SHARED with move and pop (Helper.frameAddress): `.`, `..` and `~` mean the
        // same thing to all three, so a spelling cannot mean "up" to one instruction and something else to
        // another. What differs is only what the instruction then DOES with the address — push mints it, move
        // walks to it, pop discards it. A machine with NO vid (test machines, and any rootless construct) has no
        // address to extend, so the extension becomes the child's address outright and the descendant rule below
        // does not apply: only a frame that HAS a parent address has to stay under it.
        final fURI childVID = Helper.frameAddress(this, extension);
        if (null != parentVID && !Helper.descends(parentVID, childVID))
            // print the RESOLVED forms, because the raw ones are what made this message hard to act on: the check
            // compares resolved segment lists, so a raw `/.` and a raw `aaa` say nothing about which comparison
            // failed. A frame's address must strictly extend its parent's, and now the message shows both sides.
            // This is the guard for a caller that hands push an escape DIRECTLY: the push INSTRUCTION no longer
            // reaches it for `..`, because it resolves the address and mints the leaf from the address's parent
            // (push(<../b>) == move(<..>).push(<b>)). So the caller here is Java, not a frame address.
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
        // The child's NETWORK is its OWN level too — the same move a third time, under the network's two
        // laws: KNOWLEDGE IS SHARED by addressing (every level reads the one roster at the root address, so
        // no plumbing here), and REACH IS NOT — peers are neither inherited from the parent (not even
        // shadowed) nor shared with children. A connection belongs to exactly the machine that dialed it,
        // so a pop retracts precisely this machine's dials and a sibling never sees them.
        final studio.phaseshift.metatron.isa.mach.type.network.NetworkUnion childNetwork =
                new studio.phaseshift.metatron.isa.mach.type.network.NetworkUnion();
        childNetwork.parent(child);
        // the SETTER, not just the rec: AbstractMachine caches resolvedNetwork, and the child's clone
        // carries the parent's cached field — without the setter the accessor answers the parent's
        // component forever and the rec slot is dead weight (measured: net= identity equal across push).
        child.network(childNetwork);
        child.jvm().put(uri(MEMORY), memory);
        child.jvm().put(uri(INSTSET), childInstSet);
        child.jvm().put(uri(NETWORK), childNetwork);
        // THE CHILD IS CURRENT WHILE IT BOOTSTRAPS. bootstrap writes the child's own infra (the machine rec at
        // its vid, the per-machine log sink, ~-relative state), and every one of those writes belongs to the
        // CHILD's level. Left on the parent, each self-write landed in the parent's catch-all space instead,
        // so the parent answered for the child's own address with a synthesized rec — and a sibling could not
        // read the child back at its vid, which is what `move(<child>)` resolves.
        CURRENT.set(child);
        child.bootstrap();
        // register the child rec in the ROOT's space (global): read(childVID) from ANY machine reaches it, so
        // move(childVID) works from a sibling at any depth. the child's data stays in its own infra, so isolation holds.
        Machine.root().memory().write(childVID, child);
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
        this.logger().info("closing machine %s", this.vid());
        this.memory().close();
        this.instset().close();
        this.network().close();
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
        /*final Obj protoNetwork = this.atDirect(NETWORK).orThrow(MTronException.of("machine has no network: %s", this.type().vid()));
        if (protoNetwork.isCall())
            return protoNetwork.apply().as();
        return protoNetwork.clone().as();*/

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
         * The machine-local system directory: {@code ~/sys}, one level below the component slots so {@code ~}'s direct children stay the components.
         */
        public static final fURI SYS_DIR = f("sys");

        /**
         * every started discovery source, closed with the VM
         */
        private static final List<Closeable> SOURCES = new ArrayList<>();

        /**
         * Start the discovery handlers the root network declares ({@link Network#source()}). A handler that is
         * never started is a roster that never fills, so this is what makes a source mean anything at all.
         */
        private static volatile boolean SOURCES_CLOSED = false;

        static Machine startSources(final Machine machine) {
            InstSet.importInstSet(WEB_ISA_TID);
            machine.network().source().elements().toList().forEach(entry -> {
                machine.logger().error("loading source: %s", entry);
                Machine.write(machine.vid().extend(NETWORK).extend(SOURCE).extend("mdns"), new mdns_peerHandler(mutableMap(uri(SERVICE), uri(DEFAULT_SERVICE)), null));
            });
            return machine;
        }

        /**
         * stop every discovery source — a responder that outlives the VM keeps announcing a dead address
         */
        public static void closeSources() {
            synchronized (SOURCES) {
                SOURCES_CLOSED = true;
                SOURCES.forEach(CommonUtil::close);
                SOURCES.clear();
            }
        }

        /**
         * Resolve a frame ADDRESS against the frame it is written in — the ONE definition of what {@code .},
         * {@code ..} and {@code ~} mean to a frame instruction, shared by {@link Machine#push(fURI) push},
         * {@code move} and {@code pop} so the three cannot drift apart.
         * <p>
         * The arithmetic is fURI's, deliberately. {@code .} and {@code ..} are kept at parse and folded HERE,
         * because this is the first place the BASE is known: {@code ..} reduces (it pops a segment of the frame's
         * address), so {@code ../b} is the sibling {@code b}, {@code ~/..} is the parent frame, and {@code .} is
         * the frame itself.
         * <p>
         * {@code ~} is consumed BEFORE the fold rather than left inside it: the fold pops whatever precedes a
         * {@code ..}, so a base left in the path would be eaten by its own displacement ({@code ~/..} would
         * collapse to the empty path instead of naming the parent).
         * <p>
         * An absolute base popped PAST the root <b>saturates</b> — it stays AT the root — rather than becoming a
         * displacement. A displacement ({@code ../../..}) is a direction, not a place, and a frame instruction
         * has to stand somewhere; saturation is also exactly the no-op {@link Machine#pop()} already performs
         * when a frame has no parent. A machine with no vid (a test machine, any rootless construct) has nothing
         * to descend from, so there the target becomes the address outright — which is what makes
         * {@code push(<a/b>)} mint a two-segment address on a vid-less machine.
         *
         * @param here   the frame the address is written in — the base for a relative target
         * @param target the address as spelled, with {@code .}/{@code ..}/{@code ~} still in it
         * @return the resolved address, or {@code here}'s own vid when the target is null
         */
        public static fURI frameAddress(final Machine here, final fURI target) {
            if (null == target)
                return null == here ? null : here.vid();
            // a wire address names a peer across a process boundary, not a place in this tree: taken as written
            if (target.hasScheme() || target.hasHost())
                return target;
            final fURI home = null == here ? null : here.vid();
            final boolean fromHome = target.hasPrefix(f("~"));
            if (fromHome && target.equals(f("~")))
                return null == home ? f("/") : home.resolve();
            if (!fromHome && target.isAbsolute())
                return target.resolve();
            if (null == home)
                return target.resolve(); // no address to descend from: the extension IS the address
            final fURI base = home.resolve();
            final fURI composed = base.extend(fromHome ? target.pretract(1) : target).resolve();
            // saturation: an absolute base popped past the root is the root, never a displacement
            if (base.isAbsolute() && (composed.isEmpty() || composed.isRelative()))
                return f("/");
            return composed;
        }

        /**
         * Whether {@code candidate} is a STRICT DESCENDANT of {@code ancestor} — the candidate's segments begin
         * with the ancestor's and go at least one deeper. (Descendant rather than child, deliberately: the
         * extension is an expression, so {@code a/b} and {@code a/../b} both mint through the same path arithmetic,
         * and a multi-segment extension addresses a deeper node of the SAME branch.)
         * <p>
         * Expressed in fURI's own terms — {@code resolve()}, {@code asNode()}, {@code pathLength()} and
         * {@code hasPrefix()} — and that is the point: the string form this replaces
         * ({@code child.removeSubpath(parent).pathString().equals(child.name())}) answers TRUE for a
         * single-segment escape — {@code /esc-b} under {@code /esc-a} — because the failed replacement leaves the
         * WHOLE path to be compared against its own last segment, and they agree. The segment-wise test also keeps
         * {@code a/../b} legal (it reduces back inside the parent).
         * <p>
         * WHO THIS GUARDS NOW. A frame address that crosses a level is no longer a caller error: the push
         * INSTRUCTION resolves the address and mints its leaf from the frame at the address's PARENT, so
         * {@code push(<../b>) == move(<..>).push(<b>)} and never arrives here. This is the guard for
         * {@link Machine#push(fURI)} called DIRECTLY, where a single machine must still be the parent of what it
         * mints and an escape has no parent to be re-homed to.
         * <p>
         * {@code asNode()} on both sides, because the root's address is the bare absolute marker ({@code /} is the
         * one-element path {@code [""]}) whose empty segment is the branch slash rather than a segment; as a node it
         * has NO segments, which is what makes every real address its descendant.
         */
        public static boolean descends(final fURI ancestor, final fURI candidate) {
            final fURI base = ancestor.resolve().asNode();
            final fURI child = candidate.resolve().asNode();
            return child.pathLength() > base.pathLength() && child.hasPrefix(base);
        }

        /**
         * The {@code ~}-relative log sink, left unexpanded so {@code GraphittyObjLogger} reads each frame's own.
         */
        public static final fURI LOG_SINK = f("~/sys/log");

        /**
         * The levels a machine's log sink starts with — the enclosing machine's, else the boot profile's default.
         */
        static Rec logLevels(final Machine machine) {
            final Obj parent = machine.parent();
            if (parent instanceof Machine enclosing && enclosing.hasVID()) {
                final Obj sink = enclosing.memory().read(enclosing.vid().resolve().extend(SYS_DIR.extend(LOGG)));
                if (sink.isRec()) {
                    final Obj levels = sink.asRec().at(uri(LEVEL));
                    if (levels.isRec())
                        return levels.asRec();
                }
            }
            return BootLoader.LOG_LEVELS;
        }

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
