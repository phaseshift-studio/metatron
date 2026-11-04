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
import studio.phaseshift.metatron.isa.m.type.Rec;

import static studio.phaseshift.metatron.Tokens.MACH_ISA_TID;

/**
 * analysis::T — a component's statistics: a mergeable {@code Rec} keyed {@code group => [probe => meter]}.
 * <p>
 * A measurement's tid IS its kind — {@code counter} (a monotonic int), {@code gauge} (a point-in-time real), or
 * {@code timer} (a {@code rec[count, total, min, max]} duration distribution). Derived metrics — rates, ratios,
 * averages, percentages — are computed on read, never stored, so the stored core is exactly the two mergeable kinds
 * (counter + timer), and the merge-up is the monoid that folds a component's children into it
 * ({@code computer ⊎ computer → processor ⊎ … → machine}), the same {@code XXXUnion} walk as the InstSet union.
 * The {@code profile()} rec is this type's prototype; a {@code GrafanaSerializer} walks it and emits
 * {@code name{labels} value} with timers fanned into {@code _count/_sum/_min/_max}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Analysis extends Rec {

    fURI ANALYSIS_TID = MACH_ISA_TID.extend("analysis");
    // a measurement's tid is its kind
    fURI COUNTER_TID = ANALYSIS_TID.extend("counter");
    fURI GAUGE_TID = ANALYSIS_TID.extend("gauge");
    fURI TIMER_TID = ANALYSIS_TID.extend("timer");

    /**
     * increment a {@code counter} at {@code group/probe} by {@code delta} — the monotonic meter.
     */
    Analysis incr(fURI group, fURI probe, long delta);

    /**
     * record one {@code timer} observation (nanos) at {@code group/probe} — updates count/total/min/max.
     */
    Analysis record(fURI group, fURI probe, long nanos);

    /**
     * set a {@code gauge} at {@code group/probe} — the point-in-time meter (the one non-monoid kind).
     */
    Analysis set(fURI group, fURI probe, double value);

    final class Helper {
        private Helper() {
        }

        /**
         * the monoid — merge two analyses pointwise: union the groups, union the probes, combine the measurements per
         * kind (counter → sum, timer → count/total/min/max, gauge → last-write).
         */
        public static Analysis merge(final Analysis a, final Analysis b) {
            return BasicAnalysis.merge(a, b);
        }
    }
}
