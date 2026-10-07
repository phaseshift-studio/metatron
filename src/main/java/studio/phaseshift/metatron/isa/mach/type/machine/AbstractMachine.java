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

package studio.phaseshift.metatron.isa.mach.type.machine;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.space.noobjSpace;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MObjs;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.impl.ObjectMap;
import studio.phaseshift.metatron.isa.mach.type.*;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.SYS;

/**
 * AbstractMachine — the machine's concrete body: the address index, the short-name tables and the
 * authority guard, plus the {@code apply}/{@code clone}/{@code stats}/{@code close} plumbing.
 * <p>
 * {@link Machine} is the contract; this class is the one implementation of it. Before the takeover
 * it was {@code BasicRouter}, and {@code Machine} was layered on top of it — which is why a machine
 * could not be its own root. Now the arrow points the other way: a machine is the container, and
 * the address space is one of the things it holds.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractMachine extends MRec implements Machine {

    public static final Uri PRIMARY = uri("primary");
    private static final Set<fURI> READ_AS_NOOBJ = Set.of(ALL.maybeSome(), ALL.maybe(), ALL);
    private final GraphittyLogger LOG = Graphitty.log(this);
    protected final Stats iostats = new MStats();

    private Memory resolvedMemory = null;
    private Network resolvedNetwork = null;
    private InstSet resolvedInstSet = null;

    /**
     * Resolve each slot template exactly once. The first resolution happens in the constructor, where the
     * components are parented, so by the time the resolution path reads through these it is a field read. That
     * matters: {@code memory()} calls this from inside {@code read}, and a slot that re-applied its
     * template on every access would allocate there — re-entering type resolution and {@code read} itself.
     * <p>
     * {@code Machine.super} is legal here rather than in {@link BasicMachine} because this class implements
     * {@code Machine} directly; a subclass implementing it only through this one cannot reach the default.
     */
    @Override
    public Memory memory() {
        if (null == this.resolvedMemory)
            this.resolvedMemory = Machine.super.memory();
        return this.resolvedMemory;
    }

    @Override
    public Network network() {
        if (null == this.resolvedNetwork)
            this.resolvedNetwork = Machine.super.network();
        return this.resolvedNetwork;
    }

    @Override
    public InstSet instset() {
        if (null == this.resolvedInstSet)
            this.resolvedInstSet = Machine.super.instset();
        return this.resolvedInstSet;
    }

    public AbstractMachine(final fURI vid) {
        this(ALL, vid);
    }

    public AbstractMachine(final fURI pattern, final fURI vid) {
        this(seedConfig(pattern), MACH_MACHINE_TID, vid);
    }

    /**
     * the pattern/vid scaffolding, with the memory built once and closed over by its slot
     */
    private static Map<Obj, Obj> seedConfig(final fURI pattern) {
        final Memory memory = seedMemory();
        return new ConcurrentHashMap<>(Map.of(
                uri(PATTERN), uri(pattern),
                PRIMARY, uri(M_ISA_TID),
                uri(MEMORY), instLambda(ignore -> memory)));
    }

    /**
     * The root memory: empty bindings, and an index that already holds the {@code +/#} stack space — the same
     * seed the router carried, now one level in where the index actually lives.
     */
    private static Memory seedMemory() {
        final Memory memory = new BasicMemory();
        return memory;
    }

    public AbstractMachine(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(withMemory(jvm), tid, vid);
        // per-machine fail stack: a fail lands under this machine's own address. Mounted FIRST so a fail thrown
        // during component adoption (below) already has a space to land in.
        if (null != this.vid()) {
            final memSpace failSpace = memSpace.of(this.vid().extend("fail").extend(ALL), null);
            failSpace.addQ(QCollection.incrQ());
            this.memory().addSpace(failSpace);
        }
        // Adopt the components. `at` is what sets a value's parent to the rec holding it, and
        // Machine.Component.machine() walks exactly that — seeding through the constructor map alone would leave
        // every component's machine() quietly answering mach0().
        // Parent the RESOLVED components, not the slot values: the slots hold templates now, and parenting one
        // would leave the memory's own machine() answering mach0().
        this.memory().parent(this);
        this.network().parent(this);
        // DO NOT mount the machine's own ISA overlay into the memory index. A BasicInstSet claims `/m/#` -- the SAME
        // pattern the library space claims -- so mounting it creates a same-pattern collision and `mostSpecific`
        // breaks the tie arbitrarily: writes land in the empty overlay while the library is what should answer.
        // Measured: mInstSetTest went red with writes that did not read back. This is the collision
        // AbstractMachine.addSpace's NOTE says should become an overlap check with a loud conflict.
        // The facade reaches the ISA through the overlay's REFERENCES (which are spaces in their own right), not by
        // making the overlay a sibling of the library.
        //LOG.info("local router at %s", this.vid.toUri());
    }

    /**
     * Every machine has a memory from the moment it exists. Guaranteeing it here rather than creating one on
     * demand is what keeps the read path allocation-free — an {@code memory()} that constructed one lazily
     * would do so inside {@code read}, and building an Obj there re-enters type resolution and {@code read}.
     */
    private static Map<Obj, Obj> withMemory(final Map<Obj, Obj> jvm) {
        // built eagerly and closed over, so applying a slot never constructs
        if (!jvm.containsKey(uri(MEMORY))) {
            final BasicMemory memory = new BasicMemory();
            jvm.put(uri(MEMORY), instLambda(ignore -> memory));
        }
        if (!jvm.containsKey(uri(NETWORK))) {
            final BasicNetwork network = new BasicNetwork();
            jvm.put(uri(NETWORK), instLambda(ignore -> network));
        }
        // No INSTSET seed is needed: BasicMachine declares the slot unbound, and instset()'s cache turns that
        // into ONE stable empty BasicInstSet per machine. A seed here would only add a second source of truth.
        return jvm;
    }


    @Override
    public synchronized void close() {
        // A pushed child shares its parent's memory (inheritance = walk parent()), so the child does not own the
        // index and must not clear it: clearing the shared memory here is what emptied the root's spaces on every
        // pop(). Only the machine the memory was parented to — the root — tears the index down.
        if (this.memory().machine() != this)
            return;
        try {
            // Snapshot the keys first: removeSpace mutates the index, and removing while iterating the live
            // entry set is a ConcurrentModificationException — which surfaced as a shutdown that reported the
            // entry set rather than the spaces it had closed.
            this.spaces().jvm().keySet().stream()
                    .filter(Obj::isUri)
                    .toList()
                    .forEach(key -> {
                        try {
                            this.removeSpace(key.uriValue());
                        } catch (final Exception e) {
                            LOG.warn(e);
                        }
                    });
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    public Stats stats() {
        if (Machine.loaded())
            return this.iostats;
        throw MTronException.of("machine not loaded");
    }

    @Override
    public synchronized void addSpace(final Space space) {
        this.memory().addSpace(space);
    }

    @Override
    public synchronized void removeSpace(final fURI pattern) {
        this.memory().removeSpace(pattern);
    }

    // ======================== authority dispatch ========================

    /**
     * The declared peer roster: a rec of {@code <authority-uri> => <transport-inst>}. The value is the
     * transport — an inst that takes the message ({@code from(localized)} for a read, {@code start(localized)
     * .ref(obj)} for a write) and returns the peer's response. Keeping it an inst is what keeps the Router
     * free of any transport dependency: swapping ws for http, mqtt or a gRPC client is a roster change.
     * <p>
     * It lives at {@code /sys/peer}, beside {@code /sys/thread}, and <em>not</em> under {@code /sys/mach}: the
     * Machine claims {@code /sys/mach} and {@code AbstractSpace}'s default writer is a no-op, so a roster
     * written there is silently dropped — a failure mode worth remembering, because the symptom is a peer that
     * quietly resolves to the local wildcard-host space instead and <em>appears to work</em>.
     */
    public static fURI peerRosterPath() {
        return SYS.extend(PEER);
    }

    /**
     * The address index is the machine's Memory, so a machine's spaces are exactly the spaces its memory holds —
     * there is no second place a space could be registered. The root's memory holds every global space; a frame's
     * holds only what that frame introduced, which is what makes a pop take them with it.
     */
    @Override
    public Rec spaces() {
        // resolutionMemory, not memory(): reading must never materialize a frame. Materializing constructs a
        // component, constructing an Obj runs its type check, and the type check reads through the router — which
        // lands back in read(). Only an explicit memory() call forms a frame's views.
        return this.memory().spaces();
    }

    /**
     * The memory resolution reads through: the frame's if it already has one, else the machine's own. It never
     * materializes a frame, because a lookup that did would allocate an Obj on the read path — and allocating an
     * Obj runs a type check, which resolves a type through the router, which lands back in {@code read}.
     */

    @Override
    public <S extends Space> S getSpaceFor(final fURI match) {
        return this.memory().getSpaceFor(match);
    }

    @Override
    public <SPACE extends Space> SPACE getSpace(final fURI vid) {
        return this.memory().getSpace(vid);
    }

    public Obj read(final fURI vid) {
        if (null == vid || NOOBJ.equals(vid.basePath()) || vid.isZero() || READ_AS_NOOBJ.contains(vid))
            return noobj();
        if (vid.equals(this.vid()) || vid.equals(vid.id()))
            return this;
        final fURI readableVID = this.memory().alignPrefix(vid);
        /// ///////////////////
        if (readableVID.isGeneric())
            return T(readableVID);
        // Resolution belongs to Memory — the guard and the prefix alignment above are the machine's, and the
        // big() fallback below is too, but *which space answers* is memory's question and is asked in one place.
        if (readableVID.test(STACK_PATTERN)) {
            final Obj stackObj = Memory.argStack().read(readableVID.basePath());
            if (!stackObj.isNoObj())
                return stackObj;
        }
        // readAbsolute, not read: the machine resolves addresses through spaces whatever the vid's shape. The
        // relative branch is Memory's own contract, where a relative vid means "in the frame" — letting it claim
        // the machine's vids sent bulk relative writes to the argument stack instead of the space they named.
        final Obj obj = this.memory().readAbsolute(readableVID);
        if (obj.isNoObj()) {
            final fURI bigVID = readableVID.big();
            if (!bigVID.equals(readableVID))
                return this.read(bigVID);
        }
        // todo c(mult vid.c())
        return obj;
    }

    /**
     * DELIBERATELY NOT SYNCHRONIZED. Serializing this did fix a measured lost sibling, but it puts a lock on EVERY
     * write through a machine -- the hottest path there is -- and that lock can hold a thread that has nothing to do
     * with the two writers being reconciled. The signature it produces is a hang that cannot be caught by a bounded
     * wait, because the waiting thread never reaches its wait: it is blocked entering a write. Two attempts to
     * serialize this pair of races (here, and again in the space's writer) both deadlocked, which says the fix
     * belongs where the read-modify-write actually is, not on the callers.
     */
    public Obj write(final fURI vid, final Obj obj) {
        if (null == vid) {
            LOG.warn("the provided write uri was null");
            return noobj();
        }
        final fURI writableVID = this.memory().alignPrefix(vid);
        /// ///////////////
        // A machine's own address is the machine. read() already says so (`vid.equals(this.vid())` -> this);
        // write had no matching guard, and it needs one now that the root's vid is `/`, which no space covers.
        // The auto-registration path (objCheckAndSave) writes the machine back at its own vid on every mutation
        // of its rec — with vid /sys/router that silently resolved through /sys/#, and at `/` it would instead
        // fail-closed for a write that is already true. Anything else at `/` still falls through and throws.
        if (obj == this && writableVID.equals(this.vid()))
            return obj;
        LOG.trace("writing %s {{g}}=>{{b}} %s{{X}} at %s", obj, vid, writableVID);
        // as in read(): which space answers is Memory's question, asked in one place. write() carries the
        // type-graph invalidation, so there is a single invalidation point for every write path.
        return this.memory().write(writableVID, obj);
    }

    @Override
    public boolean hasSpaceFor(final fURI vid) {
        // alignment is the ISA's business (a short name is a redirect), so it happens here and the memory sees an
        // address it can resolve. Memory never renames an address.
        return this.memory().hasSpaceFor(this.memory().alignPrefix(vid));
    }


    /**
     * A machine is a callable obj: {@code machine.apply(code)} compiles then runs. The stub this
     * replaces returned {@code null}; re-pinning it to the {@link Machine} contract is the one thing
     * a concrete machine base owes the interface.
     */
    @Override
    public Obj apply(final Obj call) {
        return Machine.super.apply(call);
    }

    public Machine clone() {
        // A real shallow copy, as MObj already provides: the override exists only because Machine.clone() narrows the
        // return type, and stubbing it made `Obj.vid(fURI)` (which IS this.clone(jvm, tid, vid)) a no-op on a
        // machine — so `clone().selfVID(v)` silently retargeted the PARENT instead of minting a child.
        // MObj.clone() already wraps the checked exception, so super.clone() here is unchecked.
        return (Machine) super.clone();
    }

    public Machine clone(final Object jvm, final fURI tid, final fURI vid) {
        // set the fields on the COPY, never on this: that is what distinguishes vid() (copy then set) from
        // selfVID() (set in place), and the difference is the whole point of the pair.
        return (Machine) this.clone().self(jvm, tid, vid);
    }

    @Override
    public Machine self(final Object jvm, final fURI tid, final fURI vid) {
        return (Machine) super.self(jvm, null == tid ? this.tid() : tid, null == vid ? this.vid() : vid);
    }

    /*@Override
    public String toString() {
        return 
    }*/
}
