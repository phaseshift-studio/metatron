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

package studio.phaseshift.metatron.isa.m.mach.type.processor.tracer;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.processor.Tracer;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_TRACER_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The concrete {@link Tracer}, as {@code BasicTyper} is the concrete typer: a rec whose {@code stack} field is the
 * state, one per processor.
 */
public class BasicTracer extends MRec implements Tracer {

    /**
     * A tracer whose {@code stack} state is {@code stack} — what the boot installs into the root's processor.
     */
    public static BasicTracer of(final Rec stack) {
        final BasicTracer tracer = new BasicTracer(mutableMap(), MACH_TRACER_TID, null);
        tracer.jvm().put(uri(Tracer.STACK), stack);
        return tracer;
    }

    /**
     * A tracer carrying the boot default state.
     */
    public BasicTracer() {
        this(mutableMap(), MACH_TRACER_TID, null);
    }

    public BasicTracer(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        // a given stack is kept (a point it leaves out reads the boot default); only a tracer without one is seeded
        this.jvm().putIfAbsent(uri(Tracer.STACK), rec(new LinkedHashMap<>(Tracer.defaults().jvm())));
    }
}
