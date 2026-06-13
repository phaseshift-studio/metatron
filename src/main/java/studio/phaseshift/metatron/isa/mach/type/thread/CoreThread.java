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

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_CORE_THREAD_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * CoreThread — a metatron thread backed by a platform (OS) thread from
 * the main executor pool.
 *
 * Execution ({@code apply} / {@code applyAsync}) and lifecycle
 * ({@code stop} / {@code pause} / {@code resume}) are inherited from
 * {@code AbstractThread}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CoreThread extends AbstractThread {

    public CoreThread(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static CoreThread core(final Obj code, final fURI vid) {
        return new CoreThread(mutableMap(uri(CODE), code), MACH_CORE_THREAD_TID, vid);
    }

    public static CoreThread core(final Obj code) {
        return new CoreThread(mutableMap(uri(CODE), code), MACH_CORE_THREAD_TID, null);
    }

}
