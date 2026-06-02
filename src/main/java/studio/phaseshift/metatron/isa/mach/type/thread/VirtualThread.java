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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.NotDetachable;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Real;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MILLIS_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_VIRTUAL_THREAD_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class VirtualThread extends AbstractThread implements NotDetachable {

    //private FutureObj<Obj> future;

    public VirtualThread(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(mutableMap(jvm), tid, vid);
    }

    private void createUnstartedThread() {
        if (null != this.thread)
            return;
        this.thread = Thread.ofVirtual()
                .name(this.vid() == null ? "metatron-virtual-thread" : this.vid().toString())
                .unstarted(() -> {
                    try {
                        final Real loopInterval = this.has(LOOP) ? this.at(LOOP).as(MILLIS_TYPE).as() : real(-1.0d);
                        boolean loop = true;
                        this.jvm().put(uri(STATE), uri(RUN));
                        while (loop) {
                            loop = loopInterval.realValue() != -1.0d;
                            final Obj result = this.jvm().getOrDefault(uri(CODE), noobj()).apply(this.at(START));
                            this.jvm().put(uri(RESULT), result);
                            if (this.thread.isInterrupted() || this.at(uri(STATE)).equals(uri(STOP))) {
                                loop = false;
                                if (!this.thread.isInterrupted())
                                    this.thread.interrupt();
                                this.logger().warn("virtual thread {{y}}interrupted{{X}} at %s", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                            }
                            if (loop) {
                                CommonUtil.sleepThread(loopInterval.realValue().intValue());
                            }
                        }
                    } catch (final Exception e) {
                        if (null != this.thread && !(e.getMessage().contains("nterrupt"))) {
                            this.jvm().put(uri(RESULT), fail(e));
                            this.logger().error("thread execution failed: %s", e.getMessage());
                        }
                    } finally {
                        this.stop();
                        synchronized (VirtualThread.this) {
                            VirtualThread.this.notifyAll();
                        }
                    }
                });
    }


   /* @Override
    public Fail stop() {
        try {
            this.thread.interrupt();
            this.jvm().put(uri(STATE), uri(HALTED));
            return fail("interrupted").asFail();
        } catch (final Exception e) {
            return fail(e);
        }
    }*/

   /* @Override
    public Obj at(final Obj key) {
        if (!key.equals(uri(RESULT)))
            return super.at(key);
        else {
            try {
                return this;
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        }
    }*/

    /*@Override
    public NoObj pause() {
        return noobj(); // use semaphone that wraps the run task
    }*/

    @Override
    public Obj result() {
        return this.at(RESULT);
    }

    @Override
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
                    this.thread.interrupt();
                    throw MTronException.of("interrupted while waiting for result");
                }
            }
        }
        throw MTronException.of("result wait timeout for thread %s", this.vid());
    }

    public static VirtualThread virtual(final Obj code, final fURI vid) {
        return new VirtualThread(mutableMap(uri(CODE), code), MACH_VIRTUAL_THREAD_TID, vid);
    }

    public static VirtualThread virtual(final Obj code) {
        return new VirtualThread(mutableMap(uri(CODE), code), MACH_VIRTUAL_THREAD_TID, null);
    }


    @Override
    public Obj apply(final Obj other) {
        synchronized (this) {
            if (null != this.thread && this.thread.getState() != Thread.State.NEW) {
                this.logger().warn("thread currently running, ignoring %s", other);
                return this;
            }
            if (this.at(START).isNoObj())
                this.jvm().put(uri(START), other);
            this.createUnstartedThread();
            this.thread.start();
        }
        return this;
    }

    @Override
    public VirtualThread clone(final Object jvm, final fURI tid, final fURI vid) {
        return (VirtualThread) super.clone(jvm, tid, null == this.vid() ? vid : this.vid());
    }

    @Override
    public VirtualThread self(final Object jvm, final fURI tid, final fURI vid) {
        return (VirtualThread) super.self(jvm, tid, null == this.vid() ? vid : this.vid());
    }

    @Override
    public VirtualThread clone() {
        return this;
    }

}
