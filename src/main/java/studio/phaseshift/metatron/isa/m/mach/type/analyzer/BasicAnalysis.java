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
import java.util.LinkedHashSet;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * BasicAnalysis — the {@code analysis::T} implementation: a Rec of meter groups, where a meter is a baggable,
 * commutatively-mergeable measurement (counter / gauge / timer). It is the statistics accumulator every component
 * publishes into; {@link #merge(Analysis, Analysis)} is the monoid that folds children's analyses up the hierarchy.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BasicAnalysis extends MRec implements Analysis {

    public BasicAnalysis() {
        super(new LinkedHashMap<>(), ANALYSIS_TID, null);
    }

    // ======================== meter construction ========================

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

    // ======================== publish ========================

    @Override
    public Analysis incr(final fURI group, final fURI probe, final long delta) {
        final Rec g = this.at(group).orElse(rec()).asRec();
        final long current = g.at(probe).orElse(counter(0L)).intValue();
        g.at(probe, counter(current + delta), MUTABLE);
        this.at(group, g, MUTABLE);
        return this;
    }

    @Override
    public Analysis record(final fURI group, final fURI probe, final long nanos) {
        final Rec g = this.at(group).orElse(rec()).asRec();
        final Obj existing = g.at(probe);
        final long count = existing.isRec() ? existing.asRec().at(uri(COUNT)).orElse(jnt(0)).intValue() + 1L : 1L;
        final long total = existing.isRec() ? existing.asRec().at(uri(TOTAL)).orElse(jnt(0)).intValue() + nanos : nanos;
        final long min = existing.isRec() ? Math.min(existing.asRec().at(uri(MIN)).orElse(jnt(nanos)).intValue(), nanos) : nanos;
        final long max = existing.isRec() ? Math.max(existing.asRec().at(uri(MAX)).orElse(jnt(nanos)).intValue(), nanos) : nanos;
        g.at(probe, timer(count, total, min, max), MUTABLE);
        this.at(group, g, MUTABLE);
        return this;
    }

    @Override
    public Analysis set(final fURI group, final fURI probe, final double value) {
        final Rec g = this.at(group).orElse(rec()).asRec();
        g.at(probe, gauge(value), MUTABLE);
        this.at(group, g, MUTABLE);
        return this;
    }

    // ======================== the monoid ========================

    public static Analysis merge(final Analysis a, final Analysis b) {
        final BasicAnalysis merged = new BasicAnalysis();
        final Set<fURI> groups = new LinkedHashSet<>();
        a.jvm().keySet().forEach(key -> groups.add(key.uriValue()));
        b.jvm().keySet().forEach(key -> groups.add(key.uriValue()));
        for (final fURI group : groups) {
            final Rec ga = a.at(group).orElse(rec()).asRec();
            final Rec gb = b.at(group).orElse(rec()).asRec();
            final Set<fURI> probes = new LinkedHashSet<>();
            ga.jvm().keySet().forEach(key -> probes.add(key.uriValue()));
            gb.jvm().keySet().forEach(key -> probes.add(key.uriValue()));
            final Rec mergedGroup = rec();
            for (final fURI probe : probes)
                mergedGroup.at(probe, mergeMeasurement(ga.at(probe), gb.at(probe)), MUTABLE);
            merged.at(group, mergedGroup, MUTABLE);
        }
        return merged;
    }

    private static Obj mergeMeasurement(final Obj x, final Obj y) {
        if (x.isNoObj())
            return y;
        if (y.isNoObj())
            return x;
        final fURI tid = x.tid();
        if (COUNTER_TID.equals(tid))
            return counter(x.intValue() + y.intValue());
        if (GAUGE_TID.equals(tid))
            return y; // last-write wins — the one non-monoid kind
        // timer — the commutative aggregate
        final long count = x.asRec().at(uri(COUNT)).intValue() + y.asRec().at(uri(COUNT)).intValue();
        final long total = x.asRec().at(uri(TOTAL)).intValue() + y.asRec().at(uri(TOTAL)).intValue();
        final long min = Math.min(x.asRec().at(uri(MIN)).intValue(), y.asRec().at(uri(MIN)).intValue());
        final long max = Math.max(x.asRec().at(uri(MAX)).intValue(), y.asRec().at(uri(MAX)).intValue());
        return timer(count, total, min, max);
    }
}
