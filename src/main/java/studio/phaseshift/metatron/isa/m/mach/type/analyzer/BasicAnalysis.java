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

package studio.phaseshift.metatron.isa.m.mach.type.analyzer;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * BasicAnalysis — the {@code analysis::T} value: a Rec of probes whose leaves are meters (counter / gauge / timer). An
 * {@link Analyzer} builds one each time it is read; {@link #merge(Analysis, Analysis)} is the monoid that folds one
 * analysis into another.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BasicAnalysis extends MRec implements Analysis {

    public BasicAnalysis() {
        this(new LinkedHashMap<>());
    }

    public BasicAnalysis(final Map<Obj, Obj> probes) {
        super(probes, ANALYSIS_TID, null);
    }

    // ======================== the meters ========================

    public static Obj counter(final long value) {
        return jnt(value).tid(COUNTER_TID);
    }

    public static Obj gauge(final double value) {
        return real(value).tid(GAUGE_TID);
    }

    public static Rec timer(final long count, final long total, final long min, final long max) {
        return rec(uri(COUNT), jnt(count), uri(TOTAL), jnt(total), uri(MIN), jnt(min), uri(MAX), jnt(max))
                .tid(TIMER_TID);
    }

    // ======================== the monoid ========================

    public static Analysis merge(final Analysis a, final Analysis b) {
        return new BasicAnalysis(merge(a.jvm(), b.jvm()));
    }

    /**
     * One level of the merge: the union of the two levels' keys, and where both hold one, the two combined — two meters
     * by their kind, two probe levels by descending into them.
     */
    private static Map<Obj, Obj> merge(final Map<Obj, Obj> a, final Map<Obj, Obj> b) {
        final Map<Obj, Obj> merged = new LinkedHashMap<>(a);
        b.forEach((key, y) -> merged.merge(key, y, (x, _) -> {
            final fURI kind = x.tid().basePath();
            if (COUNTER_TID.equals(kind))
                return counter(x.intValue() + y.intValue());
            if (TIMER_TID.equals(kind))
                return timer(x.asRec().at(uri(COUNT)).intValue() + y.asRec().at(uri(COUNT)).intValue(),
                        x.asRec().at(uri(TOTAL)).intValue() + y.asRec().at(uri(TOTAL)).intValue(),
                        Math.min(x.asRec().at(uri(MIN)).intValue(), y.asRec().at(uri(MIN)).intValue()),
                        Math.max(x.asRec().at(uri(MAX)).intValue(), y.asRec().at(uri(MAX)).intValue()));
            // a gauge is a point in time, the one kind that is no monoid: the later reading stands
            if (GAUGE_TID.equals(kind) || !x.isRec() || !y.isRec())
                return y;
            return rec(merge(x.asRec().jvm(), y.asRec().jvm()));
        }));
        return merged;
    }
}
