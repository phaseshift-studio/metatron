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
import studio.phaseshift.metatron.isa.m.type.NotDetachable;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_VIRTUAL_THREAD_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class VirtualThread extends AbstractThread implements NotDetachable {

    public VirtualThread(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(mutableMap(jvm), tid, null == vid ? CommonUtil.mintShortUUID(f("/sys/thread"), true) : vid);
    }

    @Override
    public Obj apply(final Obj other) {
        synchronized (this) {
            if (null != this.thread && this.thread.getState() != Thread.State.NEW) {
                this.logger().warn("thread currently running, ignoring %s", other);
                return this;
            }
            this.jvm().put(uri(START), other);
            BootLoader.getExecutor().execute(this);
        }
        return this;
    }

    public static VirtualThread virtual(final Obj code, final fURI vid) {
        return new VirtualThread(mutableMap(uri(CODE), code), MACH_VIRTUAL_THREAD_TID, vid);
    }

    public static VirtualThread virtual(final Obj code) {
        return new VirtualThread(mutableMap(uri(CODE), code), MACH_VIRTUAL_THREAD_TID, null);
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
