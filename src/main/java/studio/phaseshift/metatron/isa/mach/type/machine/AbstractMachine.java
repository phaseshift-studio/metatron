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
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.*;
import studio.phaseshift.metatron.isa.mach.type.memory.BasicMemory;
import studio.phaseshift.metatron.isa.mach.type.network.BasicNetwork;
import studio.phaseshift.metatron.isa.mach.type.network.PeerSpawn;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.BootLoader.CLOSING;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;

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
    private final GraphittyLogger LOG = Graphitty.log(this);
    protected final Stats iostats = new MStats();

    private Memory resolvedMemory = null;
    private Network resolvedNetwork = null;
    private InstSet resolvedInstSet = null;
    /**
     * this machine's own sphere, bound by {@code bootstrap} — the machine's, never a clone's (see {@link #clone})
     */
    private MachineSpace space = null;

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

    /**
     * Bind this machine's memory. FIELD ONLY — never {@code at()}: a rec write on a machine rec fails its own type
     * check ({@code objCheckAndSave} -> {@code Lst.test}, a null list element), which is why a component is seeded
     * through the constructor map or written straight into {@code jvm()} — see {@link Machine#push(fURI)}, which
     * rebinds the child's memory and instset and puts both into the child's own rec.
     */
    @Override
    public Machine memory(final Memory memory) {
        this.resolvedMemory = memory;
        return this;
    }

    /**
     * FIELD ONLY, exactly as {@link #memory(Memory)}: the rec write fails the machine rec's own type
     * check, and {@link Machine#push} puts the component into the child's rec itself. Clearing through
     * the setter is also what defeats the clone-carried cache: a pushed child's {@code resolvedNetwork}
     * arrives pointing at the PARENT's component until this rebinds it.
     */
    @Override
    public Machine network(final Network network) {
        this.resolvedNetwork = network;
        return this;
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

    /**
     * Bind this machine's instruction set. FIELD ONLY — never {@code at()}, for the same reason as
     * {@link #memory(Memory)}: the rec write fails its own type check. {@link Machine#push(fURI)} also puts the
     * child's ISA into the child's own rec, so an ADDRESS read of the slot answers with the same object the accessor
     * returns.
     */
    @Override
    public Machine instset(final InstSet instset) {
        this.resolvedInstSet = instset;
        return this;
    }

    @Override
    public MachineSpace space() {
        return this.space;
    }

    /**
     * FIELD ONLY, as the components' setters: the sphere is the machine's view of itself, not a slot of its rec.
     */
    @Override
    public Machine space(final MachineSpace space) {
        this.space = space;
        return this;
    }

    public AbstractMachine(final fURI vid) {
        this(ALL, vid);
    }

    public AbstractMachine(final fURI pattern, final fURI vid) {
        this(seedConfig(pattern), MACH_MACHINE_TID, vid);
    }

    /**
     * the pattern/vid scaffolding, with the memory bound as ITSELF — an instance needs nothing resolved, which is
     * what lets the memory slot stop being a deferred seed (see BasicMachine.of)
     */
    private static Map<Obj, Obj> seedConfig(final fURI pattern) {
        final Memory memory = seedMemory();
        return new ConcurrentHashMap<>(Map.of(
                uri(PATTERN), uri(pattern),
                PRIMARY, uri(M_ISA_TID),
                uri(MEMORY), memory));
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
        // Mount this machine's own infra space (pattern <vid>/#) and embed the machine rec in it. Mounted FIRST so a
        // fail thrown during component adoption (below) already has a space to land in (~/sys/fail).
        this.bootstrap();
        // Adopt the components. `at` is what sets a value's parent to the rec holding it, and
        // Machine.Component.machine() walks exactly that — seeding through the constructor map alone would leave
        // every component's machine() quietly answering mach0().
        // Parent the RESOLVED components, not the slot values: the slots hold templates now, and parenting one
        // would leave the memory's own machine() answering mach0().
        this.memory().parent(this);
        this.network().parent(this);
        // the ISA is the machine's own component too, so it must be parented like the other two: `Component.machine()`
        // walks parent(), and a shared component that cannot name its owner cannot resolve against it.
        this.instset().parent(this);
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
        // bound as THEMSELVES, not as deferred seeds: a slot already holding its component is handed straight back
        // by the accessor, so nothing is applied and no compiler is needed while the machine is being built
        if (!jvm.containsKey(uri(MEMORY))) {
            final BasicMemory memory = new BasicMemory();
            jvm.put(uri(MEMORY), memory);
        }
        if (!jvm.containsKey(uri(NETWORK))) {
            final BasicNetwork network = new BasicNetwork();
            jvm.put(uri(NETWORK), network);
        }
        // No INSTSET seed is needed: BasicMachine declares the slot unbound, and instset()'s cache turns that
        // into ONE stable empty BasicInstSet per machine. A seed here would only add a second source of truth.
        return jvm;
    }


    @Override
    public synchronized void close() {
        // BEFORE the ownership guard: a machine holding a spawned peer must release its process whether or not this
        // machine owns the memory index (a pushed child does not — its memory is a union over the parent's).
        PeerSpawn.reap(this);
        // A FRAME'S REACH ENDS WITH THE FRAME. The network level this machine OWNS -- a pushed child's NetworkUnion,
        // the root's at shutdown -- is closed: its supervision stops and every dial it made is hung up. Until this,
        // a pop retracted the child's LISTING (the level was discarded) while its sockets stayed open. Ownership is
        // the component's own parent link, so a clone that still points at another machine's level closes nothing.
        final Network network = this.network();
        if (network.machine() == this)
            network.close();
        // A pushed child shares its parent's memory (inheritance = walk parent()), so the child does not own the
        // index and must not clear it: clearing the shared memory here is what emptied the root's spaces on every
        // pop(). Only the machine the memory was parented to — the root — tears the index down.
        if (this.memory().machine() != this)
            return;
        // Suppress the logger's "no space embedded logger" fallback while this frame's own log sink is torn down:
        // the sink lives in the sphere's ~/sys, which closes after the spaces below, so any close-log made once it is
        // gone would read ~/sys/log back as empty and print the notice once per closed space.
        CLOSING = true;
        try {
            // Snapshot the keys first: removeSpace mutates the index, and removing while iterating the live
            // entry set is a ConcurrentModificationException — which surfaced as a shutdown that reported the
            // entry set rather than the spaces it had closed.
            this.memory().spaces().jvm().keySet().stream()
                    .filter(Obj::isUri)
                    .toList()
                    .forEach(key -> {
                        try {
                            this.memory().removeSpace(key.uriValue());
                        } catch (final Exception e) {
                            LOG.warn(e);
                        }
                    });
            // THE SPHERE GOES LAST, still inside CLOSING — DROPPED, NEVER CLOSED: ~/sys leaves the address surface the
            // way the sys space used to leave memory's index (removeSpace only logs; it closes nothing). Its values are
            // not the frame's to close: the system thread's executor lives in ~/sys, and closing it from one of its own
            // threads waits on itself forever (measured: every test JVM spun in ExecutorService.close at teardown).
            this.space = null;
        } catch (final Exception e) {
            throw MTronException.of(e);
        } finally {
            CLOSING = false;
        }
    }

    public Stats stats() {
        if (Machine.loaded())
            return this.iostats;
        throw MTronException.of("machine not loaded");
    }


    public Machine clone(final Object jvm, final fURI tid, final fURI vid) {
        // set the fields on the COPY, never on this: that is what distinguishes vid() (copy then set) from
        // selfVID() (set in place), and the difference is the whole point of the pair.
        // A null jvm keeps the copy's OWN map (a clone() copies it), which is what makes a machine's rec the
        // machine's: Machine.push(fURI) passes null so a child can neither answer a slot read with its parent's
        // component nor write one into the parent's rec.
        final Machine copy = (Machine) this.clone().self(jvm, tid, vid);
        // A COPIED CACHE WOULD ANSWER FOR THE WRONG MACHINE: the field is copied too, so a clone would report
        // whatever its source had resolved with. push() rebinds memory and instset explicitly; clearing here means a
        // clone can never be answered by a stale component.
        if (copy instanceof AbstractMachine abstractCopy)
            abstractCopy.resolvedInstSet = null;
        // THE SPHERE IS KEPT: a copy of a machine is that machine — the one read back at its address by move, or carried
        // through an instruction and popped back to — so it answers the same ~/sys. A pushed CHILD is a copy too, and
        // its bootstrap binds a sphere of its own over this one. (Clearing it here lost a frame's ~/sys whenever a move
        // or a pop landed on a copy: measured in SysIsolationScriptTest and MemoryIsolationScriptTest.)
        return copy;
    }

}
