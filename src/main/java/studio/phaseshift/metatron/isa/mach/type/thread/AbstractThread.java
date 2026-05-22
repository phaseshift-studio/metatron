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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractThread extends MRec {

    protected Thread thread;

    public AbstractThread(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.thread = null;
    }

    public Uri state() {
        return this.thread == null ? uri(STOPPED) : uri(this.thread.getState().name());
    }

    public boolean isRunning() {
        return this.at(STATE).equals(uri(RUNNING));
    }

    public abstract Obj result();
    
    public abstract Obj result(final long timeout, final TimeUnit unit);
    
    /*
    public boolean isReady() {
        return this.state().equals(thread_state.ready);
    }

    public boolean isRunning() {
        return this.state().equals(thread_state.running);
    }

    public boolean isPaused() {
        return this.state().equals(thread_state.paused);
    }

    public boolean isStopped() {
        return this.state().equals(thread_state.stopped);
    }
  
     */

}
