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

package studio.phaseshift.metatron.isa.m.type.impl;

import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static studio.phaseshift.metatron.Tokens.REC_TID;

/**
 * FIFORec — a rec that keeps a bounded history (Marko, 2026-11-25): a write past the cap drops the eldest entries whose
 * value is not live, so the rec holds every live entry and the newest {@code cap} of the rest, in the order they
 * arrived. Keyed, so an entry keeps its name while it is kept ({@code ~/processor/computer/<n>}). The trim runs on the
 * map a write builds, before the write installs it, so no reader ever walks a map the trim is changing.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FIFORec extends MRec {

    private final int cap;
    private final Predicate<Obj> live;

    /**
     * @param live which values are never dropped while they are live (a running computer)
     */
    public FIFORec(final int cap, final Predicate<Obj> live) {
        super(new LinkedHashMap<>(), REC_TID, null);
        this.cap = cap;
        this.live = live;
    }

    @Override
    public Rec at(final Obj key, final Obj value, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
        // the operation is handed down to the entries a nested write passes through, so only this rec's own map is trimmed
        return super.at(key, value, (poly, jvm) -> {
            if (poly == this) {
                final Map<Obj, Obj> map = (Map<Obj, Obj>) jvm;
                long ended = map.values().stream().filter(this.live.negate()).count();
                for (final Iterator<Obj> values = map.values().iterator(); ended > this.cap && values.hasNext(); ) {
                    if (!this.live.test(values.next())) {
                        values.remove();
                        ended--;
                    }
                }
            }
            return operation.apply(poly, jvm);
        });
    }
}
