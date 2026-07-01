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

package studio.phaseshift.metatron.isa.mach.type.thread;

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Real;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.Closeable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MILLIS_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractThread extends MRec implements mThread, Closeable {

    protected static Uri threadState(final Thread thread) {
        if (null == thread)
            return uri(STOP);
        final Thread.State state = thread.getState();
        if (state.equals(Thread.State.NEW) ||
                state.equals(Thread.State.RUNNABLE) ||
                state.equals(Thread.State.TERMINATED))
            return uri(STOP);
        if (state.equals(Thread.State.BLOCKED) ||
                state.equals(Thread.State.WAITING) ||
                state.equals(Thread.State.TIMED_WAITING))
            return uri(RUN);
        throw MTronException.of("unknown thread state: %s", state.name());
    }

    protected Thread thread;
    protected final FutureObj<Obj> future = new FutureObj<>(UUID.randomUUID());

    // ======================== Execution ========================

    /**
     * Non-blocking apply — submits to {@code ThreadExecutor} and returns
     * this thread immediately.  Use {@link #result()} or
     * {@link #applyAsync(Obj)}.{@code get()} to block for the result.
     *
     * <p>Subclasses with truly synchronous execution (e.g.
     * {@code SwarmMachine}) override this to run inline and return the
     * result directly.</p>
     */
    @Override
    public Obj apply(final Obj other) {
        this.applyAsync(other);
        return this;
    }

    /**
     * Asynchronous apply — submits to {@code ThreadExecutor} and returns
     * the future immediately.
     */
    @Override
    public FutureObj<Obj> applyAsync(final Obj other) {
        synchronized (this) {
            if (null != this.thread && this.thread.getState() != Thread.State.NEW) {
                this.logger().warn("thread currently running, ignoring %s", other);
                return this.future;
            }
            this.jvm().put(uri(START), other);
            BootLoader.getExecutor().execute(this);
        }
        return this.future;
    }

    public AbstractThread(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, null == vid ? CommonUtil.mintShortUUID(f("/sys/thread"), true) : vid);
        this.thread = null;
        if (!jvm.containsKey(uri(SOURCE))) {
            final AbstractThread parent = BootLoader.CURRENT_THREAD.get();
            if (null != parent && null != parent.vid())
                jvm.put(uri(SOURCE), auto_from_(parent.vid()).tryToInst());
        }
    }

    protected AbstractThread() {
        // no-arg for clone (bypasses objCheckAndSave)
    }

    // ======================== Cloning ========================

    /**
     * Threads are not cloneable — the 3-arg clone returns {@code this}.
     * Subclasses that need a real clone (e.g. {@code SwarmMachine})
     * override this.
     */
    @Override
    public AbstractThread clone(final Object jvm, final fURI tid, final fURI vid) {
        return this;
    }

    @Override
    public AbstractThread clone() {
        return this;
    }

    /**
     * Set by {@code ThreadExecutor} before the task begins executing.
     */
    public void setJavaThread(final Thread thread) {
        this.thread = thread;
    }

    /**
     * @return the URI vid of the Obj that spawned this thread, or empty uri if unset
     */
    public Obj source() {
        return this.jvm().getOrDefault(uri(SOURCE), noobj());
    }

    public Uri state() {
        return threadState(this.thread);
    }

    /**
     * @return the FutureObj for this thread's result
     */
    public FutureObj<Obj> future() {
        return this.future;
    }

    // ======================== Task (shared do-while loop) ========================

    /**
     * The Runnable executed by ThreadExecutor.  Evaluates {@code CODE} against
     * {@code START}, respecting the optional {@code LOOP} interval.  Subclasses
     * do not need to override this — the loop semantics are identical for
     * virtual and core threads.
     */
    public Runnable createTask() {
        return () -> {
            try {
                if (this.jvm().containsKey(uri(LOOP)) && this.at(LOOP).realValue() < 0.0d)
                    this.jvm().remove(uri(LOOP));
                do {
                    final Obj result = this.jvm().getOrDefault(uri(CODE), noobj()).apply(this.at(START));
                    this.jvm().put(uri(RESULT), result);
                    if (null != this.future)
                        this.future.setObj(result);
                    if (this.thread.isInterrupted() || this.at(STATE).equals(uri(STOP))) {
                        if (!this.thread.isInterrupted())
                            this.thread.interrupt();
                        this.logger().warn("thread {{y}}interrupted{{X}} at %s",
                                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                        break;
                    }
                    if (this.at(STATE).equals(uri(PAUSE))) {
                        final Obj yieldTo = this.at(YIELD, noobj(), MUTABLE);
                        if (!yieldTo.isNoObj()) {
                            try {
                                final AbstractThread partner = resolveYield(yieldTo);
                                if (null != partner)
                                    partner.resume();
                            } catch (final Exception e) {
                                this.logger().warn("unable to resume yield target: %s", e.getMessage());
                            }
                        }
                        synchronized (AbstractThread.this) {
                            while (this.at(STATE).equals(uri(PAUSE))) {
                                try {
                                    AbstractThread.this.wait();
                                } catch (final InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        }
                    }
                    if (this.jvm().containsKey(uri(LOOP))) {
                        CommonUtil.sleepThread(this.at(LOOP).as(MILLIS_TYPE).realValue().intValue());
                    }
                } while (this.jvm().containsKey(uri(LOOP)));
            } catch (final Exception e) {
                if (null != this.thread && (null == e.getMessage() || !e.getMessage().contains("nterrupt"))) {
                    this.jvm().put(uri(RESULT), fail(e));
                    this.logger().error("thread execution failed: %s", e.getMessage());
                }
            } finally {
                this.stop();
                synchronized (AbstractThread.this) {
                    AbstractThread.this.notifyAll();
                }
            }
        };
    }

    // ======================== Result access ========================

    public Obj result() {
        return this.at(RESULT);
    }

    public Obj result(final long timeout, final TimeUnit unit) {
        synchronized (this) {
            final long endTime = System.currentTimeMillis() + unit.toMillis(timeout);
            while (System.currentTimeMillis() < endTime) {
                if (this.jvm().getOrDefault(uri(STATE), noobj()).equals(uri(STOP))) {
                    return this.jvm().getOrDefault(uri(RESULT), noobj());
                }
                try {
                    final long waitTime = Math.min(10, endTime - System.currentTimeMillis());
                    if (waitTime > 0) {
                        this.wait(waitTime);
                    }
                } catch (final InterruptedException e) {
                    if (null != this.thread)
                        this.thread.interrupt();
                    throw MTronException.of("interrupted while waiting for result");
                }
            }
        }
        throw MTronException.of("result wait timeout for thread %s", this.vid());
    }

    // ======================== Lifecycle ========================

    @Override
    public void close() {
        boolean running = this.state().equals(uri(RUN));
        this.stop();
        if (!running)
            this.logger().info("closing at %s", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    public void stop() {
        synchronized (this) {
            this.jvm().put(uri(STATE), uri(STOP));
            if (null != this.thread && !this.thread.isInterrupted())
                this.thread.interrupt();
            this.notifyAll();
        }
    }

    /**
     * Pauses this thread after the current cycle completes.
     * No-op if state is already PAUSE or STOP.
     * Only effective for looping threads.
     */
    public void pause() {
        synchronized (this) {
            if (this.at(STATE).equals(uri(STOP))) {
                this.logger().warn("cannot pause a stopped thread");
                return;
            }
            if (!this.jvm().containsKey(uri(LOOP)) || this.at(LOOP).realValue() < 0.0d) {
                this.logger().warn("only looping threads can be paused");
                return;
            }
            this.jvm().put(uri(STATE), uri(PAUSE));
            this.notifyAll();
        }
    }

    /**
     * Resumes a paused thread.  No-op if state is RUN, warn if STOP.
     */
    public void resume() {
        synchronized (this) {
            if (this.at(STATE).equals(uri(RUN)))
                return;
            if (this.at(STATE).equals(uri(STOP))) {
                this.logger().warn("cannot resume a stopped thread");
                return;
            }
            this.jvm().put(uri(STATE), uri(RUN));
            this.notifyAll();
        }
    }

    /**
     * Resolves a yield pointer (auto-from or plain URI) to the target AbstractThread.
     */
    private static AbstractThread resolveYield(final Obj yieldObj) {
        if (yieldObj.isUri()) {
            final Obj resolved = studio.phaseshift.metatron.isa.mach.type.Router.global().read(yieldObj.uriValue().qLess());
            if (resolved instanceof AbstractThread t)
                return t;
        } else if (yieldObj.isInst()) {
            // auto-from pointer — apply it to resolve
            final Obj resolved = yieldObj.asInst().apply(noobj());
            if (resolved instanceof AbstractThread t)
                return t;
        }
        return null;
    }
}
