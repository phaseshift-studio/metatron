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
import studio.phaseshift.metatron.util.MTronException;

import java.util.function.DoubleSupplier;
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
 *   <li><b>recording</b> is the producer's, in Java, where the thing it counts happens, and always through the verbs
 *   of {@link Helper}. It never writes a rec: the meters are lock-free cells, so a socket frame or a monad step pays an
 *   atomic add — never a map copy, a type check or a store write;</li>
 *   <li><b>reading</b> is anyone's, in mtron: {@code *~/network/analyzer/analysis/wire/sent};</li>
 *   <li><b>subscribing</b> is the space's own pubsub: {@code ~/+/analyzer/analysis?subq -> sub::[code=>…]} receives
 *   {@code pub::[<address>, analysis::T]} at most once per {@code step} (millis), and only after a change;</li>
 *   <li><b>{@code profile}</b> switches on a component's costly instrumentation (the processor's per-monad timings),
 *   which is off by default; <b>{@code enabled}</b> switches recording itself off, so a verb's one {@link #enabled()}
 *   read elides the meter work entirely.</li>
 * </ul>
 * <p>
 * A PROBE IS A PATH: {@code wire/recv}, {@code resolve/inst_resolve}, {@code per_inst/0/time}. The analysis nests a
 * probe as deep as its path, so a read walks it ({@code *~/network/analyzer/analysis/wire}).
 * <p>
 * An analyzer sits at its owner's address (Marko, 2026-11-22/23): a component's at {@code ~/<component>/analyzer}, in
 * the machine's space; a server's in the {@code ?analq} q-proc its socket carries; a profiled run's in
 * {@link #PROFILE}. A DATA SPACE records through the {@code ?analq} q-proc it declares.
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
     * one; a computer minted under a profile carries it to whichever thread runs it. Handed to the verbs, it is the
     * run's gate: a run nobody profiles pays one thread-local read per probe and measures nothing.
     */
    ThreadLocal<Analyzer> PROFILE = new ThreadLocal<>();

    /**
     * count {@code delta} more at {@code probe} — a {@code counter}, the monotonic meter.
     */
    Analyzer incr(fURI probe, long delta);

    /**
     * one more observation of {@code nanos} at {@code probe} — a {@code timer}: count, total, min, max.
     */
    Analyzer record(fURI probe, long nanos);

    /**
     * the value at {@code probe} now — a {@code gauge}, the point-in-time meter (the one kind that is no monoid).
     */
    Analyzer set(fURI probe, double value);

    /**
     * a {@code gauge} at {@code probe} whose value is {@code sample}'s, taken each time the analysis is built. Nothing
     * records it, so it can never miss an event — and nothing announces it either: a sampled gauge changes without
     * scheduling a publication, and a subscriber sees it with the next change.
     */
    Analyzer gauge(fURI probe, DoubleSupplier sample);

    /**
     * @return the analysis the meters hold as they stand — a value built for this read, which the reader may keep
     */
    Analysis analysis();

    /**
     * the key the {@code enabled} setting is read under — a constant, like {@link #KEY}
     */
    Obj ENABLED_KEY = uri(ENABLED);

    /**
     * whether recording is on — a setting switched off at {@code ~/<component>/analyzer/enabled}. On by default; a
     * verb reads it once per event so that, muted, the producer pays that one read and nothing else. A DIRECT MAP
     * READ that builds nothing: a verb asks on every event, inside the type graph's own lookup too, where building an
     * obj (or an {@code at()} that resolves one) looks a type up again and recurses without end.
     */
    default boolean enabled() {
        return !(this.jvm().get(ENABLED_KEY) instanceof Obj enabled) || !enabled.isBool() || enabled.boolValue();
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
     * THE RECORDING VERBS — the one way a producer records, so no statistics code is interleaved with the work it
     * measures (Marko, 2026-11-23). Each verb is handed the analyzer to record into and GATES ITSELF: an absent analyzer
     * ({@code null}) or a muted one ({@code enabled => false}) records nothing, and the work a verb wraps runs bare. Which
     * analyzer is the gate's choice, never the verb's:
     * <ul>
     *   <li>the OWNER's, for the cheap meters that are always on: {@code network.analyzer()}, a server's
     *   {@code analyzer()};</li>
     *   <li>the RUN's, {@code Analyzer.PROFILE.get()}, for the costly ones a profile switches on.</li>
     * </ul>
     * A probe is a {@code static final} path built from the tokens, so a probe nobody measures costs no {@code f()}.
     * {@link #timed} wraps a block; {@link #start}/{@link #stop} time a region with no lambda, for a hot path or one with
     * early exits.
     */
    final class Helper {
        private Helper() {
        }

        /**
         * count {@code delta} more at {@code probe}
         */
        public static void incr(final Analyzer analyzer, final fURI probe, final long delta) {
            if (null != analyzer && analyzer.enabled())
                analyzer.incr(probe, delta);
        }

        /**
         * the gauge at {@code probe} is {@code value} now
         */
        public static void set(final Analyzer analyzer, final fURI probe, final double value) {
            if (null != analyzer && analyzer.enabled())
                analyzer.set(probe, value);
        }

        /**
         * one more observation of {@code nanos} at {@code probe} — a duration measured elsewhere
         */
        public static void record(final Analyzer analyzer, final fURI probe, final long nanos) {
            if (null != analyzer && analyzer.enabled())
                analyzer.record(probe, nanos);
        }

        /**
         * run {@code work}, recording its elapsed nanos at {@code probe}, and answer what it answers. Unmeasured, the
         * clock is never read and {@code work} runs bare.
         */
        public static <R> R timed(final Analyzer analyzer, final fURI probe, final Supplier<R> work) {
            if (null == analyzer || !analyzer.enabled())
                return work.get();
            final long t0 = System.nanoTime();
            try {
                return work.get();
            } finally {
                analyzer.record(probe, System.nanoTime() - t0);
            }
        }

        /**
         * the clock a region starts at, for {@link #stop}: {@code 0} when nothing measures it, so an unmeasured region
         * never reads the clock
         */
        public static long start(final Analyzer analyzer) {
            return null == analyzer ? 0L : System.nanoTime();
        }

        /**
         * one more observation at {@code probe}: the nanos since {@code t0}, the clock {@link #start} answered
         */
        public static void stop(final Analyzer analyzer, final fURI probe, final long t0) {
            if (null != analyzer && analyzer.enabled())
                analyzer.record(probe, System.nanoTime() - t0);
        }

        /**
         * a gauge at {@code probe} sampled from {@code sample} each time the analysis is read — registered once, where
         * the thing it samples is built
         */
        public static void gauge(final Analyzer analyzer, final fURI probe, final DoubleSupplier sample) {
            if (null != analyzer)
                analyzer.gauge(probe, sample);
        }
    }
}
