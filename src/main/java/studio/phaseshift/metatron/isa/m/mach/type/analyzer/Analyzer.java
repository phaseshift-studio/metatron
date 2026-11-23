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

import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import static studio.phaseshift.metatron.Tokens.ANALYZER;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * analyzer::T — the INSTRUMENT that measures, in the form of the compiler's typer and the processor's tracer (Marko,
 * 2026-11-13). Its STATE is the {@link Analysis} it has measured, derived as it is read, and its SETTINGS sit beside
 * that state:
 * <pre>
 * analyzer::[analysis => analysis::T, step => int::T, profile => bool::T]
 * </pre>
 * <ul>
 *   <li><b>recording</b> is the producer's, in Java, where the thing it counts happens, through the verbs of
 *   {@link Helper} (the per-step probes of the processor and {@code repeat()} still record directly, until their
 *   per-step cost is measured). It never writes a rec: the meters are lock-free cells, so a socket frame or a monad step
 *   pays an atomic add — never a map copy, a type check or a store write;</li>
 *   <li><b>reading</b> is anyone's, in mtron: {@code *~/network/analyzer/analysis/wire/sent};</li>
 *   <li><b>subscribing</b> is the space's own pubsub: {@code ~/+/analyzer/analysis?subq -> sub::[code=>…]} receives
 *   {@code pub::[<address>, analysis::T]} at most once per {@code step} (millis), and only after a change;</li>
 *   <li><b>{@code profile}</b> switches on a component's costly instrumentation (the processor's per-evaluation
 *   profiles, each at its computer's {@code ~/processor/computer/<n>/analyzer}), which is off by default;</li>
 *   <li><b>OFF IS NOOBJ</b> (Marko, 2026-11-23): {@code ~/<component>/analyzer -> noobj} removes the analyzer, so every
 *   verb is handed {@code null} and records nothing, and a rec written there later installs a new one;
 *   {@code ~/<component>/analyzer/analysis/<probe> -> noobj} mutes the one probe, and its subtree (see {@link #mute}).</li>
 * </ul>
 * <p>
 * A PROBE IS A PATH: {@code wire/recv}, {@code resolve/inst_resolve}, {@code per_inst/0/time}. The analysis nests a
 * probe as deep as its path, so a read walks it ({@code *~/network/analyzer/analysis/wire}).
 * <p>
 * An analyzer sits at its owner's address (Marko, 2026-11-22/23): a component's at {@code ~/<component>/analyzer}, in
 * the machine's space; a profiled evaluation's at its computer's, {@code ~/processor/computer/<n>/analyzer} (the
 * computer that {@link #claim}s it); a server's in the {@code ?analq} q-proc its socket carries. A DATA SPACE records
 * through the {@code ?analq} q-proc it declares.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Analyzer extends Rec {

    /**
     * the key a component holds its analyzer under — a constant, so a producer's lookup is one map read
     */
    Obj KEY = uri(ANALYZER);

    /**
     * the key its muted probes are read under, {@code *~/processor/analyzer/mute}: derived as it is read, as the analysis
     * is, and written only through the analysis ({@link #mute})
     */
    Obj MUTE = uri("mute");

    /**
     * THE ANALYZER THIS THREAD'S RUN IS PROFILED INTO, or {@code null} when nothing profiles it. {@code profile()} sets
     * one of its own for the runs it times, and a processor whose analyzer has {@code profile} on sets a new one for each
     * evaluation it mints; a computer minted under a profile carries it to whichever thread runs it. Handed to the
     * verbs, it is the run's gate: a run nobody profiles pays one thread-local read per probe and measures nothing.
     */
    ThreadLocal<Analyzer> PROFILE = new ThreadLocal<>();

    /**
     * THE WORK AN OBSERVATION CAUSES (Marko, 2026-11-23): on while an analyzer publishes, and carried into every thread
     * that work starts ({@code ThreadExecutor} carries it beside the frame of reference), so a subscriber's delivery and
     * everything it runs are marked. What a marked thread records is COUNTED — an observer's evaluations are the
     * machine's — and ANNOUNCES NOTHING: no publication can include the cost of its own delivery, which exists only
     * after it, so announcing that cost would publish forever. The cost rides the next change something else makes, and
     * a pull reads it now. A reply to a round trip the observer made arrives on a thread no executor started, and is
     * read under {@link #REPLY}.
     */
    ThreadLocal<Boolean> OBSERVING = ThreadLocal.withInitial(() -> false);

    /**
     * ON WHILE A SOCKET READS A FRAME DURING AN OBSERVER'S ROUND TRIP ({@code WebSocketObj.observed}): the frame may be
     * the trip's reply, which is the observer's work, and which frame it is is known only once it is read. What this
     * thread records while it is on is counted and announces nothing, as under {@link #OBSERVING}; unlike that mark it
     * is never carried, so a frame that proves to be no reply only waits for the next change to be announced, and
     * whatever it starts runs unmarked. A request another machine sends is that machine's work.
     */
    ThreadLocal<Boolean> REPLY = ThreadLocal.withInitial(() -> false);

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
     * records it, so it can never miss an event; a mute of its probe hides it rather than dropping it, because nothing
     * would register it again — and nothing announces it either: a sampled gauge changes without scheduling a
     * publication, and a subscriber sees it with the next change.
     */
    Analyzer gauge(fURI probe, DoubleSupplier sample);

    /**
     * @return the analysis the meters hold as they stand — a value built for this read, which the reader may keep
     */
    Analysis analysis();

    /**
     * MUTE {@code probe} — the analysis key written {@code noobj}, {@code ~/<component>/analyzer/analysis/<probe> -> noobj}
     * — or lift its mute ({@code muted => false}: any other value written there). A muted probe, and every probe below
     * it, records nothing and leaves the analysis; what it had counted is dropped, so a probe written back counts again
     * from zero. A sampled gauge is only hidden: nothing records it, and it is registered once. The muted probes are
     * read at {@link #MUTE}, and an analyzer built from another's rec (a pushed machine's) mutes what that one did.
     */
    Analyzer mute(fURI probe, boolean muted);

    /**
     * THE FIRST CLAIM HOLDS IT: whether {@code owner} is the one that holds this analyzer at its own address. Only the
     * first owner to claim it is, and that one again: a profiled evaluation's nested computers record into their run's
     * analyzer, which only the outermost — the first minted under it — holds at {@code ~/processor/computer/<n>/analyzer}.
     */
    boolean claim(fURI owner);

    /**
     * AGGREGATOR — the OWNER role: a component whose analyzer is where its producers record, held at
     * {@code ~/<component>/analyzer}. The analyzer is the meta-construct in the machine's space; the aggregator's
     * {@code analysis::T} is the union of everything recorded into it.
     */
    interface Aggregator extends Rec {

        /**
         * THIS COMPONENT'S STATISTICS — its {@code analyzer} entry, read at {@code ~/<component>/analyzer} — or
         * {@code null} when it keeps none: it measures nothing, or its analyzer was written {@code noobj} (switched off).
         * A DIRECT MAP READ that builds nothing, because a producer asks it on every event, where the thing it counts
         * happens, and hands the answer to a verb, which records nothing into {@code null}.
         */
        default Analyzer analyzer() {
            return this.jvm().get(KEY) instanceof Analyzer analyzer ? analyzer : null;
        }

        /**
         * install {@code analyzer} as this component's statistics, or remove them ({@code null}) — what a write at
         * {@code ~/<component>/analyzer} does. A component whose analyzer samples a gauge registers it here, so a
         * re-installed analyzer samples it too.
         */
        default Aggregator analyzer(final Analyzer analyzer) {
            if (null == analyzer)
                this.jvm().remove(KEY);
            else
                this.jvm().put(KEY, analyzer);
            return this;
        }
    }

    /**
     * THE RECORDING VERBS — how a producer records, so no statistics code is interleaved with the work it
     * measures (Marko, 2026-11-23). Each verb is handed the analyzer to record into and GATES ITSELF: an absent analyzer
     * ({@code null}: none kept, or switched off) records nothing, and the work a verb wraps runs bare (a muted probe is
     * the analyzer's own to skip). Which analyzer is the gate's choice, never the verb's:
     * <ul>
     *   <li>the OWNER's, for the cheap meters that are always on: {@code network.analyzer()}, the processor's
     *   evaluations, a server's {@code analyzer()};</li>
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
            if (null != analyzer)
                analyzer.incr(probe, delta);
        }

        /**
         * the gauge at {@code probe} is {@code value} now
         */
        public static void set(final Analyzer analyzer, final fURI probe, final double value) {
            if (null != analyzer)
                analyzer.set(probe, value);
        }

        /**
         * one more observation of {@code nanos} at {@code probe} — a duration measured elsewhere
         */
        public static void record(final Analyzer analyzer, final fURI probe, final long nanos) {
            if (null != analyzer)
                analyzer.record(probe, nanos);
        }

        /**
         * run {@code work}, recording its elapsed nanos at {@code probe}, and answer what it answers. Unmeasured, the
         * clock is never read and {@code work} runs bare.
         */
        public static <R> R timed(final Analyzer analyzer, final fURI probe, final Supplier<R> work) {
            if (null == analyzer)
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
            if (null != analyzer)
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

        /**
         * THE HOLDER HOLDS {@code analyzer} if it is the first to {@link Analyzer#claim} it — what a computer does with
         * the profile it is minted under
         */
        public static void claim(final Aggregator holder, final Analyzer analyzer) {
            if (null != analyzer && analyzer.claim(holder.vid()))
                holder.analyzer(analyzer);
        }

        /**
         * THE ONE READING VERB: what {@code analyzer} has measured, or an empty analysis when there is no analyzer — a
         * reader that draws the meters (the status bar, the network panel) draws "nothing measured", and a probe read off
         * it is absent ({@code noobj}), never a fabricated zero
         */
        public static Analysis analysis(final Analyzer analyzer) {
            return null == analyzer ? new BasicAnalysis() : analyzer.analysis();
        }
    }
}
