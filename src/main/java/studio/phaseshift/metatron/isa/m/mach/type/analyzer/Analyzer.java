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
import studio.phaseshift.metatron.util.MTronException;

import java.util.function.Supplier;

import static studio.phaseshift.metatron.Tokens.ANALYZER;
import static studio.phaseshift.metatron.Tokens.ENABLED;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * analyzer::T — the INSTRUMENT that measures, in the form of the compiler's typer and the processor's tracer (Marko,
 * 2026-11-13). Its STATE is the {@link Analysis} it has measured, derived as it is read, and its SETTINGS sit beside
 * that state:
 * <pre>
 * analyzer::[analysis => analysis::T, step => int::T, profile => bool::T, enabled => bool::T]
 * </pre>
 * <ul>
 *   <li><b>recording</b> ({@link #incr}, {@link #record}, {@link #set}) is the producer's, in Java, where the thing it
 *   counts happens. It never writes a rec: the meters are lock-free cells, so a socket frame or a monad step pays an
 *   atomic add — never a map copy, a type check or a store write;</li>
 *   <li><b>reading</b> is anyone's, in mtron: {@code *~/network/analyzer/analysis/wire/sent};</li>
 *   <li><b>subscribing</b> is the space's own pubsub: {@code ~/+/analyzer/analysis?subq -> sub::[code=>…]} receives
 *   {@code pub::[<address>, analysis::T]} at most once per {@code step} (millis), and only after a change;</li>
 *   <li><b>{@code profile}</b> switches on a component's costly instrumentation (the processor's per-monad timings),
 *   which is off by default; <b>{@code enabled}</b> switches recording itself off, so a producer's one {@link
 *   #enabled()} read elides the meter work entirely.</li>
 * </ul>
 * <p>
 * An analyzer is used two ways, named by the two sub-interfaces below: an {@link Aggregator} OWNS one (a component, at
 * {@code ~/<component>/analyzer} in the machine's space), and a {@link Recorder} RECORDS into one (a server). Analyzers
 * live only in the machine's component space — the meta-construct, never sprinkled through a data address. A DATA SPACE
 * records through the {@code ?analq} q-proc instead, and so does a SERVER: a {@code listener::T} is an addressable
 * source carrying its own {@code ?analq}, so the analyzer it records into is the one its q-proc holds rather than a
 * resolved {@code analyzer => path} reference into a shared component's meters.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Analyzer extends Rec {

    /**
     * the key a component holds its analyzer under — a constant, so a producer's lookup is one map read
     */
    Obj KEY = uri(ANALYZER);

    /**
     * THE ANALYZER THIS THREAD'S RUN IS PROFILED INTO, or {@code null} when nothing profiles it. {@code profile()} sets
     * one of its own for the runs it times, and a computer whose processor's analyzer has {@code profile} on sets that
     * one; a computer minted under a profile carries it to whichever thread runs it. The costly instrumentation reads
     * it once and measures only when it is set, so a run nobody profiles pays one thread-local read per probe.
     */
    ThreadLocal<Analyzer> PROFILE = new ThreadLocal<>();

    /**
     * count {@code delta} more at {@code group/probe} — a {@code counter}, the monotonic meter.
     */
    Analyzer incr(fURI group, fURI probe, long delta);

    /**
     * one more observation of {@code nanos} at {@code group/probe} — a {@code timer}: count, total, min, max.
     */
    Analyzer record(fURI group, fURI probe, long nanos);

    /**
     * the value at {@code group/probe} now — a {@code gauge}, the point-in-time meter (the one kind that is no monoid).
     */
    Analyzer set(fURI group, fURI probe, double value);

    /**
     * @return the analysis the meters hold as they stand — a value built for this read, which the reader may keep
     */
    Analysis analysis();

    /**
     * whether recording is on — a setting switched off at {@code ~/<component>/analyzer/enabled}. On by default; a
     * producer reads it once per event so that, muted, it pays that one read and nothing else.
     */
    default boolean enabled() {
        final Obj enabled = this.at(uri(ENABLED));
        return !enabled.isBool() || enabled.boolValue();
    }

    /**
     * AGGREGATOR — the OWNER role: a component whose analyzer is the aggregation point its recorders send their meters
     * into, held at {@code ~/<component>/analyzer}. The analyzer is the meta-construct in the machine's space; the
     * aggregator's {@code analysis::T} is the union of everything recorded into it.
     */
    interface Aggregator extends Rec {

        /**
         * THIS COMPONENT'S STATISTICS — its {@code analyzer} entry, read at {@code ~/<component>/analyzer}. A direct
         * map read, because a producer asks it where the thing it counts happens. A component that measures nothing
         * carries none, and asking it for one is a mistake, not an empty answer.
         */
        default Analyzer analyzer() {
            if (this.jvm().get(KEY) instanceof Analyzer analyzer)
                return analyzer;
            throw MTronException.of("%s carries no analyzer", this.tid());
        }
    }

    /**
     * RECORDER — the RECORDING role: a thing (a server) that records into an analyzer. Its {@link #analyzer()} is the
     * analyzer it carries (null when it carries none, and recording is then a noop) and its {@link #group()} is the
     * group its probes read under inside it — a server's is the empty group, since its analyzer is its own and its
     * probes sit flat at the analysis root.
     * <p>
     * Recording goes through {@link Helper#incr}/{@link Helper#set}/{@link Helper#record}/{@link Helper#timed}, which
     * gate it: a producer pays one {@code null} read and one {@link Analyzer#enabled()} read and nothing else when its
     * analyzer is absent or muted. {@link Helper#locateBaseAnalyzer} resolves an {@code analyzer => path} binding into
     * the analyzer that path names and the group that path leaves below it — the {@code locateBasePoly} walk, for
     * analyzers.
     */
    interface Recorder extends Rec {

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
            public static void incr(final Recorder recorder, final fURI probe, final long delta) {
                final Analyzer analyzer = recorder.analyzer();
                final fURI group = recorder.group();
                if (null == analyzer || null == group || !analyzer.enabled())
                    return;
                analyzer.incr(group, probe, delta);
            }

            /**
             * the value at {@code <group>/<probe>} now — the one obvious line for a gauge.
             */
            public static void set(final Recorder recorder, final fURI probe, final double value) {
                final Analyzer analyzer = recorder.analyzer();
                final fURI group = recorder.group();
                if (null == analyzer || null == group || !analyzer.enabled())
                    return;
                analyzer.set(group, probe, value);
            }

            /**
             * one more observation of {@code nanos} at {@code <group>/<probe>} — the raw timer, when the elapsed time
             * was measured outside {@link #timed}.
             */
            public static void record(final Recorder recorder, final fURI probe, final long nanos) {
                final Analyzer analyzer = recorder.analyzer();
                final fURI group = recorder.group();
                if (null == analyzer || null == group || !analyzer.enabled())
                    return;
                analyzer.record(group, probe, nanos);
            }

            /**
             * time {@code work}, recording the elapsed nanos at {@code <group>/<probe>}. Muted, the clock is never read
             * and {@code work} runs bare — the t0/record/nanoTime-skip idiom is here, not at the call site.
             */
            public static <R> R timed(final Recorder recorder, final fURI probe, final Supplier<R> work) {
                final Analyzer analyzer = recorder.analyzer();
                final fURI group = recorder.group();
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
             * analyzers: retract up from {@code path} until an {@link Analyzer} is read at a component's {@code
             * analyzer} door ({@code ~/<component>/analyzer}); the retracted tail is the group. {@code
             * ~/network/analyzer/web_analyzer/http} resolves to the network's analyzer with group
             * {@code web_analyzer/http}. Answers {@code null} when the path names no component's analyzer.
             */
            public static Located locateBaseAnalyzer(final fURI path) {
                fURI candidate = path;
                while (!candidate.segments().isEmpty()) {
                    final Obj read = Machine.read(candidate);
                    if (read instanceof Analyzer analyzer && ANALYZER.equals(candidate.segments().getLast()))
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
}
