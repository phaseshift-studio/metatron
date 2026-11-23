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
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.DoubleSupplier;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_ANALYZER_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The concrete {@link Analyzer}, as {@code BasicTracer} is the concrete tracer: a rec whose {@code analysis} field is
 * built from lock-free meters each time it is read, one per component.
 * <p>
 * THE METERS ARE NOT THE REC. A recording touches only its meter's cells — a {@link LongAdder}, an atomic min and max —
 * which is what lets the hottest paths in the VM count (a socket frame; a monad step under a profile): a rec write
 * copies the map, type-checks it and stores it, per event. The rec is what a READER sees, built from the cells as they
 * stand, so the meters cost nothing until someone looks. A copy of the rec (a read may stamp one) shares the cells.
 * <p>
 * THE PUBLICATION IS A STEP, NOT AN ECHO. The first change of a quiet analyzer schedules one publication a {@code step}
 * later and every change until then rides it, so a subscriber hears at most one analysis per step however hot the probe,
 * and a quiet analyzer publishes nothing. The subscriptions are found where the analysis is read — its address's
 * {@code ?subq}, which the machine's space answers for every component alike, so {@code ~/+/analyzer/analysis?subq}
 * hears them all — and each is handed {@code pub::[<address>, analysis::T]}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BasicAnalyzer extends MRec implements Analyzer {

    /**
     * the milliseconds a publication waits to gather the changes behind it, when the analyzer states no step
     */
    public static final long DEFAULT_STEP = 250L;

    /**
     * ONE METER'S CELLS. A counter adds to {@code count}; a timer counts its observations there and sums their nanos in
     * {@code total}, keeping the least and the greatest; a gauge holds its reading's bits, or is SAMPLED from
     * {@code sample} as it is read. The kind is fixed at birth.
     */
    private record Meter(fURI kind, LongAdder count, LongAdder total, AtomicLong min, AtomicLong max, AtomicLong gauge,
                         DoubleSupplier sample) {

        private static Meter of(final fURI kind) {
            return new Meter(kind, new LongAdder(), new LongAdder(), new AtomicLong(Long.MAX_VALUE),
                    new AtomicLong(Long.MIN_VALUE), new AtomicLong(), null);
        }

        // cells of its own, so a stray recording at a sampled probe is harmless: the sample is what is read
        private static Meter sampled(final DoubleSupplier sample) {
            return new Meter(Analysis.GAUGE_TID, new LongAdder(), new LongAdder(), new AtomicLong(Long.MAX_VALUE),
                    new AtomicLong(Long.MIN_VALUE), new AtomicLong(), sample);
        }

        /**
         * the meter's reading, or null for a sampled gauge whose sample failed: a statistic that can break the read it
         * answers is worse than none
         */
        private Obj read() {
            if (null != this.sample) {
                try {
                    return BasicAnalysis.gauge(this.sample.getAsDouble());
                } catch (final RuntimeException e) {
                    return null;
                }
            }
            if (Analysis.COUNTER_TID.equals(this.kind))
                return BasicAnalysis.counter(this.count.sum());
            if (Analysis.GAUGE_TID.equals(this.kind))
                return BasicAnalysis.gauge(Double.longBitsToDouble(this.gauge.get()));
            // a timer read between its birth and its first observation has no extremes yet, not Long's
            final long count = this.count.sum();
            return BasicAnalysis.timer(count, this.total.sum(), 0L == count ? 0L : this.min.get(), 0L == count ? 0L : this.max.get());
        }
    }

    /**
     * probe => meter: one lookup and no allocation once a probe exists
     */
    private final Map<fURI, Meter> meters = new ConcurrentHashMap<>();
    /**
     * a publication is scheduled, and every change until it runs rides it
     */
    private final AtomicBoolean pending = new AtomicBoolean();
    /**
     * the machine slot whose statistics these are (network, processor) — it names where they are published; null for an
     * analyzer that only profiles
     */
    private final String component;

    /**
     * An analyzer that publishes nothing: what {@code profile()} times its runs into.
     */
    public BasicAnalyzer() {
        this(mutableMap(), MACH_ANALYZER_TID, null, null);
    }

    /**
     * The analyzer of the machine slot {@code component}, published at {@code ~/<component>/analyzer/analysis}.
     */
    public BasicAnalyzer(final String component) {
        this(mutableMap(), MACH_ANALYZER_TID, null, component);
    }

    public BasicAnalyzer(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        this(jvm, tid, vid, null);
    }

    /**
     * The analyzer of the machine slot {@code component}, under the settings its rec states — which may be a shallow
     * copy of another analyzer's, as a pushed machine's processor is handed its parent's.
     */
    public BasicAnalyzer(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid, final String component) {
        super(jvm, tid, vid);
        this.component = component;
        // THE ANALYSIS IS DERIVED AS IT IS READ, closing over THIS analyzer's meters: a constructed analyzer is new
        // meters whatever rec it is built from, and only its settings come from the rec. (A copy of the analyzer -- a
        // read may stamp one -- is a java clone, which runs no constructor and shares the cells.)
        this.jvm().put(uri(ANALYSIS), auto_(instLambda(lhs -> this.analysis())).tryToInst());
    }

    // ======================== recording ========================

    @Override
    public Analyzer incr(final fURI probe, final long delta) {
        this.meter(probe, Analysis.COUNTER_TID).count().add(delta);
        return this.changed();
    }

    @Override
    public Analyzer record(final fURI probe, final long nanos) {
        final Meter meter = this.meter(probe, Analysis.TIMER_TID);
        meter.count().increment();
        meter.total().add(nanos);
        meter.min().accumulateAndGet(nanos, Math::min);
        meter.max().accumulateAndGet(nanos, Math::max);
        return this.changed();
    }

    @Override
    public Analyzer set(final fURI probe, final double value) {
        this.meter(probe, Analysis.GAUGE_TID).gauge().set(Double.doubleToRawLongBits(value));
        return this.changed();
    }

    @Override
    public Analyzer gauge(final fURI probe, final DoubleSupplier sample) {
        this.meters.put(probe, Meter.sampled(sample));
        return this;
    }

    private Meter meter(final fURI probe, final fURI kind) {
        return this.meters.computeIfAbsent(probe, _ -> Meter.of(kind));
    }

    // ======================== reading ========================

    @Override
    public Analysis analysis() {
        // in path order, so two reads of the same meters print alike
        final Map<fURI, Meter> paths = new TreeMap<>(Comparator.comparing(fURI::toString));
        paths.putAll(this.meters);
        final Map<Obj, Obj> analysis = new LinkedHashMap<>();
        paths.forEach((path, meter) -> {
            final Obj reading = meter.read();
            if (null == reading)
                return;
            // A PROBE NESTS AS DEEP AS ITS PATH: per_inst/0/time is per_inst => [0 => [time => timer]], which is what a
            // read walks (*…/analysis/per_inst/0/time) — a flat key holding slashes would be a key no path can reach
            Map<Obj, Obj> level = analysis;
            final List<String> segments = path.segments();
            for (final String segment : segments.subList(0, segments.size() - 1))
                level = level.computeIfAbsent(uri(segment), _ -> rec()).asRec().jvm();
            level.put(uri(segments.getLast()), reading);
        });
        return new BasicAnalysis(analysis);
    }

    // ======================== publishing ========================

    /**
     * A CHANGE PUBLISHES A STEP LATER, on a thread of its own, in the machine the change was recorded in — an analyzer is
     * reached through {@code Machine.current()}, so that machine's space holds its subscriptions, and the executor
     * starts the thread where its caller stands. The pending flag is cleared before the analysis is read, so a change
     * landing during the publication schedules the next one: the last change of a burst is never left unpublished.
     * <p>
     * The thread is a metatron one, listed under {@code /sys/thread/executor} while it waits out its step. Making one
     * writes it at its vid, here, on the thread that recorded the change: if that fails, the change goes unpublished
     * and the recording goes on, and the next change schedules again.
     */
    private Analyzer changed() {
        if (null == this.component || !Machine.loaded() || !this.pending.compareAndSet(false, true))
            return this;
        final Machine machine = Machine.current();
        if (null == machine.vid()) {
            this.pending.set(false);
            return this;
        }
        final fURI address = machine.vid().resolve().extend(this.component).extend(ANALYZER).extend(ANALYSIS);
        try {
            QCollection.docWrap(VirtualThread.virtual(instLambda(_ -> {
                try {
                    // the step is any int the type admits: a negative one is no wait, not a sleep that throws
                    CommonUtil.sleepThread(Math.max(0L, this.at(uri(STEP)).orElse(jnt(DEFAULT_STEP)).intValue()));
                } finally {
                    this.pending.set(false);
                }
                try {
                    final Obj subscriptions = Machine.read(address.addQ(SUBQ));
                    if (subscriptions.isLst() && !subscriptions.asLst().isEmpty())
                        QCollection.publish(subscriptions.asLst().elements(), address, this.analysis());
                } catch (final Exception e) {
                    // a statistic that can break what it measures is worse than none
                    this.logger().debug("unable to publish %s: %s", address, e.getMessage());
                }
                return noobj();
            })), "metatron-analyzer-" + this.component).applyAsync();
        } catch (final Exception e) {
            // ... and so is one that breaks the recording, or stops every publication after it
            this.pending.set(false);
            this.logger().debug("unable to schedule the publication of %s: %s", address, e.getMessage());
        }
        return this;
    }
}
