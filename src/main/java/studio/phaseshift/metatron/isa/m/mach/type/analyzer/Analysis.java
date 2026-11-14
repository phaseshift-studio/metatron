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
 * analysis::T — what a component has measured: a {@code Rec} whose paths are probes and whose leaves are meters,
 * {@code group => [probe => meter]}, a probe nesting as deep as its path ({@code per_inst/0/time}).
 * <p>
 * A meter's tid IS its kind — {@code counter} (a monotonic int), {@code gauge} (a point-in-time real), or
 * {@code timer} (a {@code rec[count, total, min, max]} distribution of nanoseconds). Derived metrics — rates, ratios,
 * means, percentages — are computed on read, never stored, so the stored core is the two mergeable kinds (counter +
 * timer) and {@link Helper#merge} is the monoid that folds one analysis into another: a machine's components, a
 * cluster's machines. An analysis is a VALUE — the {@link Analyzer} that measures it builds a fresh one each time it
 * is read, so a reader can never disturb the meters.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Analysis extends Rec {

    fURI ANALYSIS_TID = MACH_ISA_TID.extend("analysis");
    // a meter's tid is its kind
    fURI COUNTER_TID = ANALYSIS_TID.extend("counter");
    fURI GAUGE_TID = ANALYSIS_TID.extend("gauge");
    fURI TIMER_TID = ANALYSIS_TID.extend("timer");

    final class Helper {
        private Helper() {
        }

        /**
         * the monoid — merge two analyses pointwise: union the paths, combine the meters per kind (counter → sum,
         * timer → count/total/min/max, gauge → last-write).
         */
        public static Analysis merge(final Analysis a, final Analysis b) {
            return BasicAnalysis.merge(a, b);
        }
    }
}
