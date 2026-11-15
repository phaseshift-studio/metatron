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
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.function.Supplier;

/**
 * ANALYZABLE — a thing that carries an {@link Analyzer} and may therefore be measured, in the form the compiler's
 * typer and the processor's tracer take. An analyzable is the OWNER of an analyzer, never an analyzer itself: the
 * analyzer records (in Java, where the thing it counts happens), the analyzable is the thing counted, and its {@code
 * group} is the name it records under inside a shared analyzer.
 * <p>
 * Recording goes through {@link Helper#incr}/{@link Helper#set}/{@link Helper#timed}, which gate it: a producer pays
 * one {@code null} read and one {@link Analyzer#enabled()} read and nothing else when its analyzer is absent or muted.
 * {@link Helper#locateBase} resolves an {@code analyzer => path} binding into the analyzer that path names and the
 * group that path leaves below it — the {@code locateBasePoly} walk, for analyzers.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Analyzable extends Rec {

    /**
     * the analyzer this thing records into, or {@code null} when it carries none (recording is a noop)
     */
    Analyzer analyzer();

    /**
     * the group this thing records under inside its analyzer, or {@code null} when it carries none
     */
    fURI group();

    /**
     * whether recording is on — a reader switches it off at the analyzer's {@code enabled} setting
     */
    default boolean enabled() {
        final Analyzer analyzer = this.analyzer();
        return null != analyzer && analyzer.enabled();
    }

    /**
     * the recording verbs, gated — one algorithm, one place: a producer calls these and never considers whether its
     * analyzer is absent or muted, nor where the group lives.
     */
    final class Helper {
        private Helper() {
        }

        /**
         * count {@code delta} more at {@code <group>/<probe>} — the one obvious line for a counter.
         */
        public static void incr(final Analyzable analyzable, final fURI probe, final long delta) {
            final Analyzer analyzer = analyzable.analyzer();
            final fURI group = analyzable.group();
            if (null == analyzer || null == group || !analyzer.enabled())
                return;
            analyzer.incr(group, probe, delta);
        }

        /**
         * the value at {@code <group>/<probe>} now — the one obvious line for a gauge.
         */
        public static void set(final Analyzable analyzable, final fURI probe, final double value) {
            final Analyzer analyzer = analyzable.analyzer();
            final fURI group = analyzable.group();
            if (null == analyzer || null == group || !analyzer.enabled())
                return;
            analyzer.set(group, probe, value);
        }

        /**
         * one more observation of {@code nanos} at {@code <group>/<probe>} — the raw timer, when the elapsed time was
         * measured outside {@link #timed}.
         */
        public static void record(final Analyzable analyzable, final fURI probe, final long nanos) {
            final Analyzer analyzer = analyzable.analyzer();
            final fURI group = analyzable.group();
            if (null == analyzer || null == group || !analyzer.enabled())
                return;
            analyzer.record(group, probe, nanos);
        }

        /**
         * time {@code work}, recording the elapsed nanos at {@code <group>/<probe>}. Muted, the clock is never read and
         * {@code work} runs bare — the t0/record/nanoTime-skip idiom is here, not at the call site.
         */
        public static <R> R timed(final Analyzable analyzable, final fURI probe, final Supplier<R> work) {
            final Analyzer analyzer = analyzable.analyzer();
            final fURI group = analyzable.group();
            if (null == analyzer || null == group || !analyzer.enabled())
                return work.get();
            final long t0 = System.nanoTime();
            try {
                return work.get();
            } finally {
                analyzer.record(group, probe, System.nanoTime() - t0);
            }
        }

        /**
         * THE BASE ANALYZER A PATH NAMES, and the group it leaves below it — the {@code locateBasePoly} walk, for
         * analyzers: retract up from {@code path} until an {@link Analyzer} is read; the retracted tail is the group.
         * {@code ~/network/analyzer/web_analyzer/http} resolves to the network's analyzer with group
         * {@code web_analyzer/http}. Answers {@code null} when the path names no analyzer.
         */
        public static Located locateBase(final fURI path) {
            fURI candidate = path;
            while (!candidate.segments().isEmpty()) {
                final Obj read = Machine.read(candidate);
                if (read instanceof Analyzer analyzer)
                    return new Located(analyzer, path.removePrefix(candidate));
                candidate = candidate.retract(1);
            }
            return null;
        }

        /**
         * an analyzer and the group within it a path resolves to
         */
        public record Located(Analyzer analyzer, fURI group) {
        }
    }
}
