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
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.MThread;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.STATE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_CORE_THREAD_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractThread extends MRec implements MThread {

    public AbstractThread(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.at(STATE, auto_(() -> this.at(STATE).orElse(uri("stopped"))));
    }

    public thread_state state() {
        return this.at(STATE).isNoObj() ? thread_state.ready : thread_state.valueOf(this.at(STATE).orElse(uri("stopped")).uriValue().toString());
    }

    public static <T extends AbstractThread> T of(final Rec thread) {
        if (thread instanceof AbstractThread)
            return (T) thread;
        return (T) (thread.tid().equals(MACH_CORE_THREAD_TID) ?
                new CoreThread(thread.jvm(), thread.tid(), thread.vid()) :
                new VirtualThread(thread.jvm(), thread.tid(), thread.vid()));
    }

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

}
