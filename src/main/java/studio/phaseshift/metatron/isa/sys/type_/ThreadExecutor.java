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

package studio.phaseshift.metatron.isa.sys.type_;

import org.jspecify.annotations.NonNull;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.mach.machInstSet;
import studio.phaseshift.metatron.isa.mach.type.thread.AbstractThread;
import studio.phaseshift.metatron.isa.mach.type.thread.CoreThread;
import studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread;

import java.util.*;
import java.util.concurrent.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst0;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * ThreadExecutor — single funnel for all metatron threading.
 *
 * Exposes threads as mtron-queryable Rec fields rather than hidden Java maps:
 *
 *   /sys/thread           ThreadExecutor rec
 *   /sys/thread/active    Lst of currently running threads
 *   /sys/thread/inactive  Lst of completed threads
 *
 * Standard mtron URI queries work naturally:
 *   * /sys/thread/active/+               all active threads
 *   * /sys/thread/inactive/+=?[source=x] completed threads spawned by x
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ThreadExecutor extends AbstractExecutorService implements Rec {

    /**
     * ThreadExecutor type TID — registered in sysInstSet.
     */
    public static final fURI THREAD_EXECUTOR_TID = REC_TID;

    private fURI vid;
    private final ExecutorService service;
    private boolean running = true;

    /**
     * The jvm map — every entry is mtron-accessible via the Rec interface.
     * {@code run} and {@code stop} are thread-safe Lsts of currently-executing
     * and completed threads, respectively.
     */
    public final Map<Obj, Obj> map;

    public ThreadExecutor(final ExecutorService service, final fURI vid) {
        this.service = service;
        this.vid = vid;
        this.map = new ConcurrentHashMap<>();
        this.map.put(uri(RUN), lst(new CopyOnWriteArrayList<>()));
        this.map.put(uri(STOP), lst(new CopyOnWriteArrayList<>()));
    }

    // ======================== Rec interface ========================

    @Override
    public fURI vid() {
        return this.vid;
    }

    @Override
    public fURI tid() {
        return THREAD_EXECUTOR_TID;
    }

    @Override
    public Rec clone(final Object jvm, final fURI tid, final fURI vid) {
        return this;
    }

    @Override
    public Map<Obj, Obj> jvm() {
        return this.map;
    }

    @Override
    public Rec self(final Object jvm, final fURI tid, final fURI vid) {
        return this;
    }

    // ======================== ExecutorService delegation ========================

    @Override
    public void shutdown() {
        //this.jvm().getOrDefault(uri(RUN),lst0()).elements().map(x->(AbstractThread)x).forEach(AbstractThread::stop);
        this.running = false;
        this.service.shutdown();
    }

    @Override
    public @NonNull List<Runnable> shutdownNow() {
        this.running = false;
        return this.service.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return !this.running;
    }

    @Override
    public boolean isTerminated() {
        return !this.running;
    }

    @Override
    public boolean awaitTermination(long timeout, @NonNull TimeUnit unit) throws InterruptedException {
        return false;
    }

    /**
     * Standard {@code Runnable} execution — delegates directly to the underlying service.
     */
    @Override
    public void execute(@NonNull Runnable command) {
        this.service.execute(command);
    }

    private final Set<fURI> executedVids = Collections.synchronizedSet(new HashSet<>());

    // ======================== Metatron-aware thread execution ========================

    /**
     * Execute a metatron {@code AbstractThread} through this executor.
     * Idempotent: if the thread has already been executed (tracked by vid),
     * this call is a no-op.  On first execution the thread is added to the
     * {@code active} list; on completion it moves to {@code inactive}.
     * Both lists are exposed via the Rec jvm for mtron URI queries.
     */
    public void execute(final AbstractThread thread) {
        final Lst run = this.at(uri(RUN)).asLst();
        if (false && run.lstValue()
                .stream()
                .map(Obj.Helper::getAutoPointer)
                .filter(Optional::isPresent)
                .anyMatch(t -> Objects.equals(t.get(), thread.vid())))
            return;
        thread.jvm().put(uri(STATE), uri(RUN));
        final Inst pointer = auto_from_(thread.vid()).tryToInst().as();
        run.lstValue().add(pointer);
        final Runnable task = thread.createTask();
        final Runnable wrapped = () -> {
            final AbstractThread previous = BootLoader.CURRENT_THREAD.get();
            BootLoader.CURRENT_THREAD.set(thread);
            try {
                task.run();
            } finally {
                BootLoader.CURRENT_THREAD.set(previous);
                run.lstValue().remove(pointer);
                this.at(uri(STOP)).lstValue().add(pointer);
            }
        };

        if (thread instanceof VirtualThread) {
            final Thread javaThread = Thread.ofVirtual()
                    .name(null != thread.vid() ? thread.vid().toString() : "metatron-virtual")
                    .unstarted(wrapped);
            thread.setJavaThread(javaThread);
            javaThread.start();
        } else {
            this.service.execute(() -> {
                thread.setJavaThread(Thread.currentThread());
                wrapped.run();
            });
        }
    }

    // ======================== Builder ========================

    @Override
    public Obj clone() {
        return this;
    }

    public static class Builder {

        protected Code code = null;
        protected Obj until = noobj();
        protected Real loop = real(0.0d).zero();
        protected fURI vid = null;
        protected fURI source = null;

        public static Builder build() {
            return new Builder();
        }

        public Builder code(final Code code) {
            this.code = code;
            return this;
        }

        public Builder loop(final Real timeUnit) {
            this.loop = timeUnit;
            return this;
        }

        public Builder vid(final fURI vid) {
            this.vid = vid;
            return this;
        }

        /**
         * Set the source object (the Obj that spawned this thread).
         */
        public Builder source(final fURI sourceVid) {
            this.source = sourceVid;
            return this;
        }

        public VirtualThread spawnVirtualThread() {
            final Map<Obj, Obj> jvm = mutableMap(uri(LOOP), this.loop);
            if (null != this.code)
                jvm.put(uri(CODE), this.code);
            if (null != this.source)
                jvm.put(uri(SOURCE), auto_from_(this.source).tryToInst());
            return new VirtualThread(jvm, machInstSet.MACH_VIRTUAL_THREAD_TID, this.vid);
        }

        public CoreThread spawnCoreThread() {
            final Map<Obj, Obj> jvm = mutableMap(uri(LOOP), this.loop);
            if (null != this.code)
                jvm.put(uri(CODE), this.code);
            if (null != this.source)
                jvm.put(uri(SOURCE), auto_from_(this.source).tryToInst());
            return new CoreThread(jvm, machInstSet.MACH_CORE_THREAD_TID, this.vid);
        }
    }
}
