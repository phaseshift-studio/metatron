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
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.util.MTronException;

import java.io.Closeable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractThread extends MRec implements Closeable {

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

    public AbstractThread(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.thread = null;
    }

    public Uri state() {
        return threadState(this.thread);
    }

    public abstract Obj result();

    public abstract Obj result(final long timeout, final TimeUnit unit);

    @Override
    public void close() {
        boolean running = this.state().equals(uri(RUN));
        this.stop();
        this.thread.interrupt();
        if(!running)
            this.logger().info("closing at %s", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
    }

    public void stop() {
        this.jvm().put(uri(STATE), uri(STOP));
    }
}
