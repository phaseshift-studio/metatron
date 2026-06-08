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

import dev.langchain4j.service.V;
import org.jspecify.annotations.NonNull;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Real;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.mach.machInstSet;
import studio.phaseshift.metatron.isa.mach.type.thread.AbstractThread;
import studio.phaseshift.metatron.isa.mach.type.thread.CoreThread;
import studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static studio.phaseshift.metatron.Tokens.CODE;
import static studio.phaseshift.metatron.Tokens.LOOP;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread.virtual;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ThreadExecutor extends AbstractExecutorService implements Rec {

    public final Map<Obj, Obj> map = new LinkedHashMap<>();
    private fURI vid;
    private final ExecutorService service;
    private boolean running = true;

    public ThreadExecutor(final ExecutorService service, final fURI vid) {
        this.service = service;
        this.vid = vid;
    }

    @Override
    public fURI vid() {
        return this.vid;
    }

    @Override
    public fURI tid() {
        return REC_TID;
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

    @Override
    public void shutdown() {
        this.service.shutdown();
    }

    @Override
    public @NonNull List<Runnable> shutdownNow() {
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

    @Override
    public void execute(@NonNull Runnable command) {
        this.service.execute(command);
    }

    public void execute(final AbstractThread thread) {

    }

    @Override
    public Obj clone() {
        return this;
    }


    public static class Builder {

        protected Code code = MCode.code(List.of()).zero().as();
        protected Obj until = noobj();
        protected Real loop = real(0.0d).zero();
        protected fURI vid = null;


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

        public VirtualThread spawnVirtualThread() {
            final VirtualThread thread = new VirtualThread(mutableMap(uri(CODE), this.code, uri(LOOP), this.loop), machInstSet.MACH_VIRTUAL_THREAD_TID, this.vid);
            return thread;
        }

        public CoreThread spawnCoreThread() {
            final CoreThread thread = new CoreThread(mutableMap(uri(CODE), this.code, uri(LOOP), this.loop), machInstSet.MACH_CORE_THREAD_TID, this.vid);
            return thread;
        }
    }


}
