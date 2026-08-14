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
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.m.type.impl.MInst;
import studio.phaseshift.metatron.isa.m.type.impl.MObjs;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.PCMonad;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.CODE_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs0;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_SWARM_MACHINE_TID;
import static studio.phaseshift.metatron.isa.mach.type.monad.BasicPCMonad.pcmonad;

/*
 * SwarmMachine — a monadic execution engine that IS a VirtualThread.
 *
 * A SwarmMachine processes code through a monadic step-loop with barrier
 * synchronization and halted-object collection.  It extends {@code VirtualThread}
 * so execution runs on a cheap virtual thread; lifecycle control (stop, pause,
 * resume) is inherited from {@code AbstractThread}.
 *
 * Machine state lives in the jvm map:
 *   {@code /code}     — the Code being executed
 *   {@code /run}      — the active monad queue (ListMonad)
 *   {@code /barrier}  — the barrier monad queue (LinkedList)
 *   {@code /halted}   — collected halted objects
 *   {@code /start}    — the input object (set by apply())
 *   {@code /result}   — the final result (set when processing completes)
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SwarmMachine extends VirtualThread implements Machine {

    public static final int MAX_FAILS = 10;


    protected final GraphittyLogger LOG = Graphitty.log(this);
    private static final Supplier<Obj> RUNNING_SUPPLIER = ListMonad::of;
    private static final AtomicLong MACHINE_COUNTER = new AtomicLong(0);

    private Consumer<Obj> onHalt;
    private final AtomicInteger infiniteFailCounter = new AtomicInteger(0);

    // ======================== Constructors & factories ========================

    protected SwarmMachine(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, null == vid ? f("/sys/machine").extend(String.valueOf(MACHINE_COUNTER.incrementAndGet())) : vid);
        // Ensure machine state fields exist with defaults
        this.jvm().putIfAbsent(uri(RUN), RUNNING_SUPPLIER.get());
        this.jvm().putIfAbsent(uri(BARRIER), lst(new LinkedList<>()));
        this.jvm().putIfAbsent(uri(HALTED), MObjs.objs0());
        // RESULT is an auto-pointer to HALTED — always live, never stale
        this.jvm().put(uri(RESULT), auto_from_(uri(this.vid().extend(HALTED))).tryToInst());
        this.onHalt = makeOnHalt();
    }

    public static SwarmMachine of(final Call code) {
        return new SwarmMachine(
                new LinkedHashMap<>(Map.of(uri(CODE), code.isCode() ? code.as() : new MCode(code.insts(), CODE_TID, null))),
                MACH_SWARM_MACHINE_TID, null);
    }

    public static SwarmMachine machine(final Map<Obj, Obj> machineState, final fURI tid, final fURI vid) {
        return new SwarmMachine(new LinkedHashMap<>(machineState), tid, vid);
    }

    public static Machine of(final Obj start, final Code code) {
        if (!start.isNoObj()) {
            final List<Inst> prepended = new ArrayList<>();
            if (start.isMonad()) {
                // Monad start: no START inst - the monad's obj + state seed the code's
                // first inst via resolve()/runMonadicLoop; the monad's own inst (e.g.
                // repeat) must not be re-applied as the next instruction.
                prepended.addAll(code.codeValue());
            } else {
                prepended.add(MInst.instB(mInstSet.START_INST_TID, lst(start)));
                prepended.addAll(code.codeValue());
            }
            return new SwarmMachine(
                    new LinkedHashMap<>(Map.of(uri(CODE), MCode.of(prepended))),
                    MACH_SWARM_MACHINE_TID, null);
        } else {
            return SwarmMachine.of(code);
        }
    }

    // ======================== Machine state accessors ========================

    @Override
    public Map<Obj, Obj> jvm() {
        return super.jvm();
    }

    @Override
    public Code code() {
        return this.jvm().getOrDefault(uri(CODE), noobj()).as();
    }

    @Override
    public SwarmMachine code(final Code code) {
        final Map<Obj, Obj> map = new LinkedHashMap<>(this.jvm());
        map.put(uri(CODE), code);
        return this.clone(map, this.tid(), this.vid());
    }

    @Override
    public Obj halted() {
        return this.jvm().getOrDefault(uri(HALTED), MObjs.objs0());
    }

    @Override
    public Lst barriers() {
        return this.jvm().getOrDefault(uri(BARRIER), lst(new LinkedList<>())).as();
    }

    @Override
    public Obj running() {
        return this.jvm().computeIfAbsent(uri(RUN), k -> RUNNING_SUPPLIER.get());
    }

    // ======================== Halt callback ========================

    /**
     * Create a fresh onHalt callback bound to this machine instance.
     * Uses direct jvm access (not Rec.at()) to guarantee HALTED is always
     * found and mutated in-place.
     */
    private Consumer<Obj> makeOnHalt() {
        return o -> {
            final Obj h = this.jvm().get(uri(HALTED));
            if (null != h && h.isObjs())
                ((List<Obj>) h.asObjs().jvm()).add(o);
            else if (null != h)
                h.append(o);
        };
    }

    @Override
    public Machine onHalt(final Consumer<Obj> onHalt) {
        this.onHalt = onHalt;
        return this;
    }

    @Override
    public Consumer<Obj> onHalt() {
        return this.onHalt;
    }

    // ======================== Lifecycle (overrides for machine semantics) ========================

    /**
     * Block until the machine completes, then return the halted objects.
     * Overrides the non-blocking {@code AbstractThread.result()} which just
     * reads the RESULT field without waiting.
     */
    @Override
    public Obj result() {
        synchronized (this) {
            while (!this.jvm().getOrDefault(uri(STATE), noobj()).equals(uri(STOP))) {
                try {
                    this.wait(10);
                } catch (final InterruptedException e) {
                    if (null != this.thread)
                        this.thread.interrupt();
                    throw MTronException.of("interrupted while waiting for machine result");
                }
            }
            return this.at(RESULT); // auto-resolves !*<self>/halted
        }
    }

    /**
     * Allow pausing without requiring a LOOP key (unlike vanilla AbstractThread
     * which only allows pausing of looping threads).
     */
    @Override
    public void pause() {
        synchronized (this) {
            if (this.at(STATE).equals(uri(STOP))) {
                this.logger().warn("cannot pause a stopped machine");
                return;
            }
            this.jvm().put(uri(STATE), uri(PAUSE));
            this.notifyAll();
        }
    }

    // ======================== Resolution ========================

    @Override
    public SwarmMachine resolve(final Obj lhs) {
        final Obj resolveLhs = lhs.isMonad() ? lhs.asMonad().obj() : lhs;
        final Code resolvedCode = this.code().resolve(resolveLhs);
        final SwarmMachine mach = this.code(resolvedCode);
        for (final Inst inst : mach.code().jvm()) {
            if (inst.isInitial()) {
                LOG.trace("  {{g}}==>{{/g}} creating {{y}}initial{{/y}} monad at %s", inst);
                this.running().append(pcmonad(noobj(), inst, lhs.isMonad() ? lhs.asMonad().state() : rec0(), resolvedCode));
            } else if (inst.isGather()) {
                LOG.trace("  {{m}}==|{{/m}} creating {{y}}barrier{{/y}} monad at %s", inst);
                final PCMonad m = pcmonad(objs0(), inst, lhs.isMonad() ? lhs.asMonad().state() : rec0(), resolvedCode);
                mach.barriers().<LinkedList<Obj>>jvmAs().add(m);
            }
        }
        return mach;
    }

    // ======================== Execution ========================

    /**
     * Synchronous apply — runs the monadic loop on the calling thread
     * and returns the halted objects directly.  This is the standard
     * execution path for {@code Code.apply()} and {@code Space.resolveApply()}.
     */
    @Override
    public Obj apply(final Obj other) {
        synchronized (this) {
            if (null != this.thread && this.thread.getState() != Thread.State.NEW) {
                this.logger().warn("machine currently running, ignoring %s", other);
                return this;
            }
            this.jvm().put(uri(START), other);
        }
        return runMonadicLoop();
    }

    /**
     * The monadic processing loop — resolves code, creates initial and barrier
     * monads, and processes the running/barrier queues until completion or
     * interruption.  Shared by {@link #apply(Obj)} (sync) and
     * {@link #createTask()} (async via {@link #applyAsync(Obj)}).
     */
    private Obj runMonadicLoop() {
        Router.global().stats().monadicStats().resetMonads();
        final Code code = this.resolve(this.at(START)).code();
        if (this.running().c().isZero()) {
            final Obj start = this.at(START);
            this.running().append(pcmonad(start.isMonad() ? start.asMonad().obj() : start, code.insts().getFirst(), start.isMonad() ? start.asMonad().state() : rec0(), code));
        }

        // Monadic processing loop
        while (!this.at(STATE).equals(uri(STOP)) && this.infiniteFailCounter.get() < MAX_FAILS) {

            // --- Pause check ---
            if (this.at(STATE).equals(uri(PAUSE))) {
                synchronized (this) {
                    while (this.at(STATE).equals(uri(PAUSE))) {
                        try {
                            this.wait();
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                if (this.at(STATE).equals(uri(STOP)))
                    break;
            }

            final PCMonad m = (PCMonad) this.running().take();
            if (null != m) {
                LOG.trace("   {{g}}=>{{/g}} processing monad %s [%s]", m, m.inst().isInitial() ? "initial" : "midway");
                final PCMonad x = this.split(m);
                x.apply().stream().forEach(y -> {
                    final PCMonad n = y.as();
                    LOG.trace(" {{g}}===>{{/g}} post-processing monad %s", n);
                    if (n.obj().isFail())
                        this.infiniteFailCounter.incrementAndGet();
                    if (n.inst().isBatching() && (!n.dead() || n.inst().dom().c().isZeroable())) {
                        if (n.inst().isGather()) {
                            final PCMonad barrier = this.barriers().<LinkedList<PCMonad>>jvmAs().peek();
                            LOG.trace("{{m}}====|{{/m}} appending living obj to barrier %s", n);
                            if (null == barrier)
                                throw MTronException.of("barrier should exist: %s", n.inst());
                            barrier.obj().append(n.obj());
                            Router.global().stats().monadicStats().incrBarrierMonads(1L);
                        } else {
                            this.running().append(n);
                        }
                    } else if (!n.dead()) {
                        if (n.halted()) {
                            LOG.trace("{{y}}====>{{/y}} halting monad %s", n);
                            n.obj().iterator().forEachRemaining(no -> {
                                Router.global().stats().monadicStats().incrHaltedMonads(1L);
                                // this.halted().append(no); // TODO: make configurable (lazy result or aggregate result)
                                this.onHalt.accept(no);
                            });
                        } else {
                            LOG.trace("{{g}}====>{{/g}} propagating monad %s", n);
                            n.obj().iterator().forEachRemaining(no -> {
                                this.running().append(n.obj(no));
                            });
                        }
                    } else if (n.zombie() && n.inst().dom().c().isZeroable()) {
                        LOG.trace("{{c}}====>{{/c}} walking undead zombie monad %s", n);
                        this.running().append(n);
                    } else {
                        Router.global().stats().monadicStats().incrKilledMonads(1L);
                        LOG.trace("{{r}}====>{{/r}} killing monad %s", n);
                    }
                });
            } else if (!this.barriers().isEmpty()) {
                final PCMonad barrier = this.barriers().<LinkedList<PCMonad>>jvmAs().poll();
                if (null != barrier) {
                    LOG.trace("   {{m}}=|{{/m}} processing barrier monad %s", barrier);
                    final Obj result = barrier.inst().apply(barrier.obj());
                    final Inst nextInst = code.nextInst(barrier.inst());
                    if (nextInst.isGather()) {
                        LOG.trace("  {{m}}==|{{/m}} passing barrier obj %s to %s", result, nextInst);
                        final PCMonad nextBarrier = this.barriers().<LinkedList<PCMonad>>jvmAs().peek();
                        if (null == nextBarrier)
                            throw MTronException.of("barrier should exist: %s", nextInst);
                        nextBarrier.obj().append(result);
                    } else if (nextInst.isBatching()) {
                        Router.global().stats().monadicStats().incrBarrierMonads(-1L);
                        this.running().append(pcmonad(result, nextInst, noobjRec(), code));
                        Router.global().stats().monadicStats().incrRunningMonads(1L);
                    } else {
                        LOG.trace("  {{m}}==|{{/m}} scattering barrier obj %s to %s", result, nextInst);
                        result.forEach(o -> {
                            final PCMonad n = pcmonad(o, nextInst, noobjRec(), code);
                            Router.global().stats().monadicStats().incrBarrierMonads(-1L);
                            LOG.trace(" {{m}}===|{{/m}} scattering %s", n);
                            this.running().append(n);
                        });
                    }
                }
            } else {
                LOG.trace("{{b}}monad {{g}}processing completed{{X}}");
                break;
            }
        }

        // Build result (RESULT already points to HALTED via auto-from set in constructor)
        if (this.at(STATE).equals(uri(STOP))) {
            return fail(MTronException.of(Graphitty.sillyPrint("machine interrupted", false, true)));
        } else if (this.infiniteFailCounter.get() >= MAX_FAILS) {
            return fail(MTronException.of(Graphitty.sillyPrint("machine failed", false, true)),
                    fail(MTronException.of("infinite fail-loop detected"),
                            fail("obj/inst coefficients yielding unsolvable monad")));
        } else {
            return objs(this.halted());
        }
    }

    /**
     * Async execution via {@code ThreadExecutor}.  Called after
     * {@link #applyAsync(Obj)} submits this thread.  Wraps
     * {@link #runMonadicLoop()} with error handling and lifecycle management.
     */
    @Override
    public Runnable createTask() {
        return () -> {
            try {
                final Obj result = runMonadicLoop();
                if (null != this.future)
                    this.future.setObj(result);
            } catch (final Exception e) {
                if (null != this.thread && !e.getMessage().contains("nterrupt")) {
                    if (null != this.future)
                        this.future.setObj(fail(e));
                    //this.logger().error("machine execution failed: %s", e.getMessage());
                }
            } finally {
                this.stop();
                synchronized (this) {
                    this.notifyAll();
                }
            }
        };
    }

    // ======================== Monad splitting ========================

    protected PCMonad split(final PCMonad monad) {
        if (monad.obj().unique() && (monad.inst().dom().c().isOne() || monad.inst().dom().c().isAny()))
            return monad;
        if (monad.inst().dom().c().isZero() && !monad.obj().c().isZeroable())
            throw MTronException.of("monad obj coefficient is greater than inst domain coefficient: " +
                    "\n\tobj       => %s" +
                    "\n\t\\_c       => %s" +
                    "\n\tinst     X=> %s" +
                    "\n\t\\_dom_c  X=> %s", monad.obj(), monad.obj().c(), monad.inst(), monad.inst().dom().c());
        final Tuple.Pair<Obj, Obj> pair =
                monad.obj().c().gte(monad.inst().dom().c()) ?
                        monad.obj().take(monad.inst().dom().c().most()) :
                        monad.obj().take(monad.obj().c().most());
        if (!pair.get1().isNoObj())
            this.running().append(monad.obj(pair.get1()));
        LOG.trace("{{g}}=>{{/g}} splitting monad %s / %s (inst: %s)", pair.get0(), pair.get1(), monad.inst());
        return monad.obj(pair.get0());
    }

    // ======================== Cloning ========================

    @Override
    public SwarmMachine clone(final Object jvm, final fURI tid, final fURI vid) {
        // Return a fresh instance via the no-arg constructor and self(),
        // bypassing the full MObj(obj,tid,vid) constructor to avoid
        // objCheckAndSave → type-resolution StackOverflow.
        final SwarmMachine clone = new SwarmMachine();
        final Map<Obj, Obj> map = new LinkedHashMap<>((Map<Obj, Obj>) jvm);
        map.putIfAbsent(uri(RUN), RUNNING_SUPPLIER.get());
        map.putIfAbsent(uri(BARRIER), lst(new LinkedList<>()));
        map.putIfAbsent(uri(HALTED), MObjs.objs0());
        clone.self(map, tid, null == this.vid() ? vid : this.vid());
        // RESULT auto-pointer must point to the clone's own HALTED
        clone.jvm().put(uri(RESULT), auto_from_(uri(clone.vid().extend(HALTED))).tryToInst());
        clone.onHalt = clone.makeOnHalt(); // fresh callback bound to the clone
        clone.infiniteFailCounter.set(0);
        return clone;
    }

    /**
     * No-arg constructor that skips objCheckAndSave (used by clone).
     */
    protected SwarmMachine() {
        // MObj no-arg constructor — sets parent=noobj, does NOT call objCheckAndSave
    }

    @Override
    public SwarmMachine self(final Object jvm, final fURI tid, final fURI vid) {
        final Map<Obj, Obj> map = (Map<Obj, Obj>) jvm;
        // Ensure defaults for machine state fields
        map.putIfAbsent(uri(RUN), RUNNING_SUPPLIER.get());
        map.putIfAbsent(uri(BARRIER), lst(new LinkedList<>()));
        map.putIfAbsent(uri(HALTED), MObjs.objs0());
        return (SwarmMachine) super.self(jvm, tid, null == this.vid() ? vid : this.vid());
    }

    @Override
    public SwarmMachine clone() {
        return this;
    }

}
