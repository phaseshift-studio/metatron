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

package studio.phaseshift.metatron.isa.m.mach.type.processor;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.mach.type.Computer;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Processor;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Selector;
import studio.phaseshift.metatron.isa.m.mach.type.processor.monad.ListMonad;
import studio.phaseshift.metatron.isa.m.mach.type.processor.monad.StatefulMonad;
import studio.phaseshift.metatron.isa.m.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.isa.m.mach.type.thread.mThread;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.m.type.impl.MFail;
import studio.phaseshift.metatron.isa.m.type.impl.MInst;
import studio.phaseshift.metatron.isa.m.type.impl.MObjs;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_SWARM_COMPUTER_TID;
import static studio.phaseshift.metatron.isa.m.mach.type.processor.monad.BasicStatefulMonad.statefulMonad;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs0;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * BasicSwarmComputer — a monadic execution engine that IS a VirtualThread.
 *
 * A BasicSwarmComputer processes code through a monadic step-loop with barrier
 * synchronization and halted-object collection.  It extends {@code VirtualThread}
 * so execution runs on a cheap virtual thread; lifecycle control (stop, pause,
 * resume) is inherited from {@code AbstractThread}.
 *
 * Machine state lives in the jvm map:
 *   {@code /code}     — the Code being executed
 *   {@code /start}    — the input object (set by apply())
 *   {@code /result}   — the final result (set when processing completes)
 *   {@code /on_halted} - callback for when a monad halts
 *   {@code /cache/run}      — the active monad queue (ListMonad)
 *   {@code /cache/barrier}  — the barrier monad queue (LinkedList)
 *   {@code /cache/halted}   — collected halted objects
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BasicSwarmComputer extends VirtualThread implements Computer {

    public static final int MAX_FAILS = 10;


    protected final GraphittyLogger LOG = Graphitty.log(this);
    private static final Supplier<Obj> RUNNING_SUPPLIER = ListMonad::of;
    private static final AtomicLong PROCESSOR_COUNTER = new AtomicLong(0);
    // number of processor instances spawned during evaluation (read by the profile() instruction)
    public static final AtomicLong PROCESSORS = new AtomicLong(0);

    public static void resetTimings() {
        PROCESSORS.set(0);
    }

    private final AtomicInteger infiniteFailCounter = new AtomicInteger(0);

    // ======================== Constructors & factories ========================

    public BasicSwarmComputer(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        // ~/processor: machine-agnostic — resolved to the current machine's vid on write (and selfVID-stamped).
        super(jvm, tid, null == vid ? f("/processor/computer").extend(String.valueOf(PROCESSOR_COUNTER.incrementAndGet())) : vid);
        PROCESSORS.incrementAndGet();
        // Ensure machine state fields exist with defaults
        final Map<Obj, Obj> cache = new LinkedHashMap<>(this.jvm().getOrDefault(uri(CACHE), rec()).recValue());
        cache.putIfAbsent(uri(RUN), RUNNING_SUPPLIER.get());
        cache.putIfAbsent(uri(BARRIER), lst(new LinkedList<>()));
        // RESULT is an auto-pointer to HALTED — always live, never stale
        this.jvm().put(uri(RESULT), auto_from_(uri(this.vid().extend(HALTED))).tryToInst());
        this.jvm().putIfAbsent(uri(ON_HALT), makeOnHalt());
        this.jvm().put(uri(CACHE), rec(cache));
        // HALTED is objs0() — an appendable noobj (isNoObj() true while empty) — so rec(cache)'s cleanMap
        // strips it. Seed it after the rec is built so the empty halt pool survives the clean.
        this.jvm().get(uri(CACHE)).recValue().putIfAbsent(uri(HALTED), MObjs.objs0());
    }

    public static BasicSwarmComputer of(final Call code) {
        return new BasicSwarmComputer(
                mutableMap(Map.of(uri(CODE), code.isCode() ? code.as() : new MCode(code.insts(), CODE_TID, null))),
                MACH_SWARM_COMPUTER_TID, f("/processor/computer").extend(String.valueOf(PROCESSOR_COUNTER.incrementAndGet())));
    }

    public static BasicSwarmComputer of(final Obj start, final Code code) {
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
            return new BasicSwarmComputer(
                    mutableMap(uri(CODE), MCode.of(prepended)), MACH_SWARM_COMPUTER_TID, null);
        } else {
            return BasicSwarmComputer.of(code);
        }
    }

    // ======================== Machine state accessors ========================

    @Override
    public Map<Obj, Obj> jvm() {
        return super.jvm();
    }

    public Code code() {
        return this.jvm().getOrDefault(uri(CODE), noobj()).asCode();
    }

    public BasicSwarmComputer code(final Code code) {
        return new BasicSwarmComputer(mutableMap(uri(CODE), code), this.tid(), this.vid());
    }

    public Obj halted() {
        return this.jvm().getOrDefault(uri(CACHE), rec()).recValue().getOrDefault(uri(HALTED), MObjs.objs0());
    }

    public Lst barriers() {
        return this.jvm().getOrDefault(uri(CACHE), rec()).recValue().getOrDefault(uri(BARRIER), lst(new LinkedList<>())).as();
    }

    public Obj running() {
        return this.jvm().getOrDefault(uri(CACHE), rec()).recValue().computeIfAbsent(uri(RUN), k -> RUNNING_SUPPLIER.get());
    }

    @Override
    public Rec cache() {
        return this.jvm().getOrDefault(uri(CACHE), rec()).asRec();
    }

    @Override
    public Computer add(final mThread worker) {
        final Lst pool = this.at(THREAD).orElse(lst());
        pool.lstValue().add(worker);
        this.at(THREAD, pool, MUTABLE);
        return this;
    }

    // ======================== Halt callback ========================

    /**
     * Create a fresh onHalt callback bound to this machine instance.
     * Uses direct jvm access (not Rec.at()) to guarantee HALTED is always
     * found and mutated in-place.
     */
    private Inst makeOnHalt() {
        return MInst.instC(mInstSet.CATCH_INST_TID.dom(ALL_STAR.any()).rng(NOOBJ_TID), lst(), o -> {
            if (o.isFail()) {
                MFail.incrStackWrap(o.asFail(), MFail.failStackPattern(this.vid()));
            }
            final Obj h = this.jvm().getOrDefault(uri(CACHE), rec()).recValue().get(uri(HALTED));
            if (null != h && h.isObjs())
                ((List<Obj>) h.asObjs().jvm()).add(o);
            else if (null != h)
                h.append(o);
            return noobj();
        });
    }

    public Computer onHalt(final Inst onHalt) {
        this.at(ON_HALT, onHalt, MUTABLE);
        return this;
    }

    public Inst onHalt() {
        return this.atDirect(ON_HALT);
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
            return this.atDirect(RESULT); // auto-resolves !*<self>/halted
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
    public BasicSwarmComputer resolve(final Obj lhs) {
        final Obj resolveLhs = lhs.isMonad() ? lhs.asMonad().obj() : lhs;
        final Code resolvedCode = this.code().resolve(resolveLhs);
        // boolean-switch: if any inst reads the walked path (?monad_in=state/path), tag the code
        // with ?path so the monads it mints accrue the path (looked up via the shared code, not
        // copied into each monad's state).
        final Code code = computesPath(resolvedCode) ? resolvedCode.tid(resolvedCode.tid().addQ(PATH)) : resolvedCode;
        final BasicSwarmComputer mach = this.code(code);
        for (final Inst inst : mach.code().jvm()) {
            if (inst.isInitial()) {
                LOG.trace("  {{g}}==>{{/g}} creating {{y}}initial{{/y}} monad at %s", inst);
                this.running().append(statefulMonad(noobj(), inst, lhs.isMonad() ? lhs.asMonad().state() : rec0(), code));
            } else if (inst.isGather()) {
                LOG.trace("  {{m}}==|{{/m}} creating {{y}}barrier{{/y}} monad at %s", inst);
                final StatefulMonad m = statefulMonad(objs0(), inst, lhs.isMonad() ? lhs.asMonad().state() : rec0(), code);
                this.barriers().<LinkedList<Obj>>jvmAs().add(m);
            }
        }
        return mach;
    }

    /**
     * true when any instruction in {@code code} reads the walked path ({@code monad_in=state/path}).
     */
    private static boolean computesPath(final Code code) {
        return code.insts().stream().anyMatch(i -> i.tid().hasQ(MONAD_IN) && PATH.equals(f(i.tid().q(MONAD_IN)).name()));
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
            this.jvm().put(uri(LHS), other);
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
        final Code submitted = this.code();
        final List<Inst> submittedInsts = submitted.insts();
        if (submittedInsts.size() < 2) {
            // profile()/explain() collapse to a single *_compute inst by design — a 1-inst
            // sequence here is the point, not a smell. suppress the warning for those.
            final String name = submittedInsts.isEmpty() ? "" : submittedInsts.getFirst().tid().name();
            if (null != this.parent && !this.parent.isNoObj() && !"profile".equals(name) && !"explain".equals(name)
                    && !"profile_compute".equals(name) && !"explain_compute".equals(name))
                LOG.debug("processor %s evaluating a %d-instruction code sequence: %s", this.vid(), submittedInsts.size(), submitted);
        }
        Machine.current().memory().stats().monadicStats().resetMonads();
        final Code code = this.resolve(this.at(LHS)).code();
        if (this.running().c().isZero()) {
            final Obj start = this.at(LHS);
            this.running().append(statefulMonad(start.isMonad() ? start.asMonad().obj() : start, code.insts().getFirst(), start.isMonad() ? start.asMonad().state() : rec0(), code));
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

            final StatefulMonad m = (StatefulMonad) this.running().take();
            if (null != m) {
                m.countPropagation();
                LOG.trace("   {{g}}=>{{/g}} processing monad %s [%s]", m, m.inst().isInitial() ? "initial" : "midway");
                final StatefulMonad x = split(this, m);
                x.apply().stream().forEach(y -> {
                    final StatefulMonad n = y.as();
                    LOG.trace(" {{g}}===>{{/g}} post-processing monad %s", n);
                    if (n.obj().isFail())
                        this.infiniteFailCounter.incrementAndGet();
                    if (n.inst().isBatching() && (!n.dead() || n.inst().dom().c().isZeroable())) {
                        if (n.inst().isGather()) {
                            final StatefulMonad barrier = this.barriers().<LinkedList<StatefulMonad>>jvmAs().peek();
                            LOG.trace("{{m}}====|{{/m}} appending living obj to barrier %s", n);
                            if (null == barrier)
                                throw MTronException.of("barrier should exist: %s", n.inst());
                            barrier.obj().append(n.obj());
                            Machine.current().memory().stats().monadicStats().incrBarrierMonads(1L);
                        } else {
                            this.running().append(n);
                        }
                    } else if (!n.dead()) {
                        if (n.halted()) {
                            LOG.trace("{{y}}====>{{/y}} halting monad %s", n);
                            n.obj().iterator().forEachRemaining(no -> {
                                Machine.current().memory().stats().monadicStats().incrHaltedMonads(1L);
                                // this.halted().append(no); // TODO: make configurable (lazy result or aggregate result)
                                if (no.isUncaughtFail())
                                    MFail.incrStackWrap(no.asFail(), MFail.failStackPattern(this.vid()));
                                // THE HALT CALLBACK TAKES THE RESULT AS IT IS: invoke, not apply. apply() fans a
                                // fibered rec out to its bundle and fibers (the callback's domain is any obj), so a
                                // halted person[ninja] halted its ninja beside it
                                Processor.Helper.invoke(no, Selector.Helper.resolve(no, this.onHalt()), this.onHalt());
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
                        Machine.current().memory().stats().monadicStats().incrKilledMonads(1L);
                        LOG.trace("{{r}}====>{{/r}} killing monad %s", n);
                    }
                });
            } else if (!this.barriers().isEmpty()) {
                final StatefulMonad barrier = this.barriers().<LinkedList<StatefulMonad>>jvmAs().poll();
                if (null != barrier) {
                    barrier.countPropagation();
                    LOG.trace("   {{m}}=|{{/m}} processing barrier monad %s", barrier);
                    final Obj result = Processor.Helper.apply(barrier.obj(), barrier.inst());
                    // a gather emits one result monad carrying its coefficient — record its
                    // out-flow here (the barrier path bypasses StatefulMonad.next()).
                    final fURI barVid = barrier.inst().vid();
                    if (null != barVid && !result.isNoObj()) {
                        StatefulMonad.INST_MONAD_OUT.computeIfAbsent(barVid, k -> new AtomicLong()).incrementAndGet();
                        StatefulMonad.INST_COEFF_OUT.computeIfAbsent(barVid, k -> new AtomicLong()).addAndGet(StatefulMonad.coeffMagnitude(result));
                    }
                    final Inst nextInst = code.nextInst(barrier.inst());
                    if (nextInst.isGather()) {
                        LOG.trace("  {{m}}==|{{/m}} passing barrier obj %s to %s", result, nextInst);
                        final StatefulMonad nextBarrier = this.barriers().<LinkedList<StatefulMonad>>jvmAs().peek();
                        if (null == nextBarrier)
                            throw MTronException.of("barrier should exist: %s", nextInst);
                        nextBarrier.obj().append(result);
                    } else if (nextInst.isBatching()) {
                        Machine.current().memory().stats().monadicStats().incrBarrierMonads(-1L);
                        // a monad born past a barrier starts with an empty state, like every fresh monad above
                        this.running().append(statefulMonad(result, nextInst, rec0(), code));
                        Machine.current().memory().stats().monadicStats().incrRunningMonads(1L);
                    } else {
                        LOG.trace("  {{m}}==|{{/m}} scattering barrier obj %s to %s", result, nextInst);
                        result.forEach(o -> {
                            final StatefulMonad n = statefulMonad(o, nextInst, rec0(), code);
                            Machine.current().memory().stats().monadicStats().incrBarrierMonads(-1L);
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
                this.complete(runMonadicLoop());
            } catch (final Exception e) {
                if (null != this.thread && null != e.getMessage() && !e.getMessage().contains("nterrupt")) {
                    this.complete(fail(e));
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

    static StatefulMonad split(final BasicSwarmComputer computer, final StatefulMonad monad) {
        if (monad.obj().unique() && (monad.inst().dom().c().isOne() || monad.inst().dom().c().isAny()))
            return monad;
        if (monad.inst().dom().c().isZero() && !monad.obj().c().isZeroable())
            throw MTronException.of("monad obj coefficient is greater than inst dom coefficient:" +
                    "\n\t%s [{%s} X=> {%s}] %s", monad.obj(), monad.obj().c(), monad.inst().dom().c(), monad.inst());
        final long t0 = System.nanoTime();
        final Tuple.Pair<Obj, Obj> pair =
                monad.obj().c().gte(monad.inst().dom().c()) ?
                        monad.obj().take(monad.inst().dom().c().most()) :
                        monad.obj().take(monad.obj().c().most());
        if (!pair.get1().isNoObj())
            computer.running().append(monad.obj(pair.get1()));
        computer.logger().trace("{{g}}=>{{/g}} splitting monad %s / %s (inst: %s)", pair.get0(), pair.get1(), monad.inst());
        StatefulMonad.T_SPLIT.addAndGet(System.nanoTime() - t0);
        return monad.obj(pair.get0());
    }


    // ======================== Cloning ========================

    @Override
    public BasicSwarmComputer clone(final Object jvm, final fURI tid, final fURI vid) {
        // Return a fresh instance via the no-arg constructor and self(),
        // bypassing the full MObj(obj,tid,vid) constructor to avoid
        // objCheckAndSave → type-resolution StackOverflow.
        final BasicSwarmComputer clone = new BasicSwarmComputer();
        final Map<Obj, Obj> map = new LinkedHashMap<>((Map<Obj, Obj>) jvm);
        map.putIfAbsent(uri(RUN), this.running());
        map.putIfAbsent(uri(BARRIER), this.barriers());
        map.putIfAbsent(uri(HALTED), this.halted());
        ((Map<Obj, Obj>) jvm).put(uri(CACHE), rec(map));
        clone.self(jvm, tid, null == this.vid() ? vid : this.vid());
        // RESULT auto-pointer must point to the clone's own HALTED
        clone.at(uri(f(CACHE).extend(RESULT)), auto_from_(uri(clone.vid().extend(HALTED))).tryToInst(), MUTABLE);
        clone.at(uri(f(CACHE).extend(ON_HALT)), clone.makeOnHalt(), MUTABLE); // fresh callback bound to the clone
        clone.infiniteFailCounter.set(0);
        return clone;
    }

    /**
     * No-arg constructor that skips objCheckAndSave (used by clone).
     */
    protected BasicSwarmComputer() {
        // MObj no-arg constructor — sets parent=noobj, does NOT call objCheckAndSave
        PROCESSORS.incrementAndGet();
    }

    @Override
    public BasicSwarmComputer self(final Object jvm, final fURI tid, final fURI vid) {
        final Map<Obj, Obj> map = (Map<Obj, Obj>) jvm;
        // Ensure defaults for machine state fields
        final Map<Obj, Obj> cache = map.getOrDefault(uri(CACHE), rec()).recValue();
        map.putIfAbsent(uri(RUN), RUNNING_SUPPLIER.get());
        map.putIfAbsent(uri(BARRIER), lst(new LinkedList<>()));
        map.putIfAbsent(uri(HALTED), MObjs.objs0());
        return (BasicSwarmComputer) super.self(jvm, tid, null == this.vid() ? vid : this.vid());
    }

    @Override
    public BasicSwarmComputer clone() {
        return this;
    }

}
