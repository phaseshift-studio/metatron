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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Component;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Processor;
import studio.phaseshift.metatron.isa.m.mach.type.processor.BasicSwarmComputer;
import studio.phaseshift.metatron.isa.m.mach.type.thread.AbstractThread;
import studio.phaseshift.metatron.isa.m.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mach.type.analyzer.Analysis.TIMER_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * analyzer::T — A COMPONENT'S STATISTICS, in the typer's and the tracer's form (Marko, 2026-11-13). The meters are
 * lock-free cells that lose no count under contention; the analysis built from them is a VALUE, nested as deep as each
 * probe's path, and merges as a monoid; every component's analyzer is answered by the machine's space — read live,
 * written in place, subscribed to through one {@code ?subq} registry — and publishes at most once per step after a
 * change; and a profile measures exactly the runs it scopes.
 * <p>
 * Each test that touches a machine's analyzers pushes a machine of its own: a pushed machine's components carry fresh
 * meters, and a fork's classes share its root.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class AnalyzerTest extends AbstractMetatronTest {

    /**
     * how long to wait for a publication: several steps, so a scheduled one has surely landed
     */
    private static final long PUBLISHED_MS = 4 * BasicAnalyzer.DEFAULT_STEP;

    private static Analysis measured(final long sent, final long... splits) {
        final Analyzer analyzer = new BasicAnalyzer();
        analyzer.incr(f(WIRE).extend(f(SENT)), sent);
        for (final long split : splits)
            analyzer.record(f(APPLY).extend(f(SPLIT)), split);
        return analyzer.analysis();
    }

    private static long read(final Obj analysis, final String path) {
        return analysis.asRec().at(uri(path)).orElse(jnt(0)).intValue();
    }

    /**
     * the probes a processor that profiles nothing keeps: the machine's evaluations, counted and timed
     */
    private static final Set<Obj> EVALUATED = Set.of(uri(Processor.EVAL_PROBE), uri(Processor.TIME_PROBE));

    /**
     * the computers of {@code machine}'s processor that hold a profile, in the order they were minted — what
     * {@code ~/processor/computer/+/analyzer} reads
     */
    private static List<BasicSwarmComputer> profiled(final Machine machine) {
        return machine.processor().computers().jvm().values().stream()
                .filter(obj -> obj instanceof BasicSwarmComputer computer && computer.jvm().get(Analyzer.KEY) instanceof Analyzer)
                .map(obj -> (BasicSwarmComputer) obj)
                .toList();
    }

    // ======================== the types ========================

    @ParameterizedTest
    @CsvSource(value = {
            // a meter's tid is its kind
            "counter::5                                              % counter::5",
            "gauge::0.5                                              % gauge::0.5",
            "timer::[count=>2,total=>30,min=>10,max=>20]>>total      % 30",
            "timer::[count=>2]                                       % <ERROR>",
            "timer::[count=>2,total=>'long',min=>10,max=>20]         % <ERROR>",
            // an analyzer's settings are typed
            "analyzer::[step=>500]>>step                             % 500",
            "analyzer::[profile=>true]>>profile                      % true",
            "analyzer::[step=>'fast']                                % <ERROR>",
            "analyzer::[profile=>3]                                  % <ERROR>",
    }, delimiter = '%')
    void testMeterAndAnalyzerTypes(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    // ======================== the meters ========================

    /**
     * A PROBE NESTS AS DEEP AS ITS PATH: per_inst/0/time is per_inst => [0 => [time => timer]], which is what a read
     * walks — a flat key holding slashes would be a key no path reaches.
     */
    @Test
    void shouldNestAProbeAsDeepAsItsPath() {
        final Analyzer analyzer = new BasicAnalyzer();
        analyzer.incr(f(PER_INST).extend(f("0").extend(MONAD_IN)), 3L);
        analyzer.record(f(PER_INST).extend(f("0").extend(TIME)), 40L);
        analyzer.record(f(PER_INST).extend(f("0").extend(TIME)), 10L);
        analyzer.record(f(PER_INST).extend(f("1").extend(TIME)), 7L);
        final Analysis analysis = analyzer.analysis();
        assertEquals(3L, read(analysis, "per_inst/0/monad_in"), "the counter sits at its path: " + analysis);
        assertEquals(TIMER_TID, analysis.at(uri("per_inst/0/time")).tid().basePath(), "a timer is typed by its kind");
        assertEquals(2L, read(analysis, "per_inst/0/time/count"), "two observations");
        assertEquals(50L, read(analysis, "per_inst/0/time/total"), "their sum");
        assertEquals(10L, read(analysis, "per_inst/0/time/min"), "the least");
        assertEquals(40L, read(analysis, "per_inst/0/time/max"), "the greatest");
        assertEquals(7L, read(analysis, "per_inst/1/time/max"), "and a sibling probe is its own");
    }

    /**
     * A gauge is a point in time: the last reading stands.
     */
    @Test
    void shouldKeepTheLastReadingOfAGauge() {
        final Analyzer analyzer = new BasicAnalyzer();
        analyzer.set(f(WIRE).extend(f(OPEN)), 3.5d);
        analyzer.set(f(WIRE).extend(f(OPEN)), 1.25d);
        assertEquals(1.25d, analyzer.analysis().at(uri("wire/open")).realValue(), 0.0d);
    }

    /**
     * THE METERS LOSE NOTHING UNDER CONTENTION: eight threads hammer one counter and one timer, and every increment
     * and every observation is in the analysis — which a read-modify-write rec could not promise.
     */
    @Test
    void shouldLoseNoCountUnderContention() throws Exception {
        final Analyzer analyzer = new BasicAnalyzer();
        final int threads = 8;
        final int each = 25_000;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            final List<Future<?>> running = IntStream.range(0, threads).<Future<?>>mapToObj(t -> pool.submit(() -> {
                for (int i = 1; i <= each; i++) {
                    analyzer.incr(f(WIRE).extend(f(SENT)), 1L);
                    analyzer.record(f(APPLY).extend(f(SPLIT)), i);
                }
            })).toList();
            for (final Future<?> run : running)
                run.get(60, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        final Analysis analysis = analyzer.analysis();
        assertEquals((long) threads * each, read(analysis, "wire/sent"), "every increment counted");
        assertEquals((long) threads * each, read(analysis, "apply/split/count"), "every observation counted");
        assertEquals((long) threads * each * (each + 1) / 2, read(analysis, "apply/split/total"), "and summed");
        assertEquals(1L, read(analysis, "apply/split/min"), "the least kept");
        assertEquals(each, read(analysis, "apply/split/max"), "the greatest kept");
    }

    /**
     * AN ANALYSIS IS A VALUE: a read is the meters as they stood, the next read is fresh, and nothing a reader does
     * to the value it holds reaches the meters.
     */
    @Test
    void shouldHandAReaderAValue() {
        final Analyzer analyzer = new BasicAnalyzer();
        analyzer.incr(f(WIRE).extend(f(SENT)), 1L);
        final Analysis before = analyzer.analysis();
        analyzer.incr(f(WIRE).extend(f(SENT)), 1L);
        assertEquals(1L, read(before, "wire/sent"), "a read is the meters as they stood");
        assertEquals(2L, read(analyzer.analysis(), "wire/sent"), "and the next read is fresh");
        before.at(uri(WIRE)).asRec().jvm().put(uri(SENT), BasicAnalysis.counter(99L));
        assertEquals(2L, read(analyzer.analysis(), "wire/sent"), "a reader's write never reaches the meters");
    }

    // ======================== the monoid ========================

    @Test
    void shouldMergeAsAMonoid() {
        final Analysis a = measured(1L, 10L);
        final Analysis b = measured(2L, 30L, 5L);
        final Analysis c = measured(4L, 20L);
        final Analysis ab = Analysis.Helper.merge(a, b);
        assertEquals(3L, read(ab, "wire/sent"), "counters add");
        assertEquals(3L, read(ab, "apply/split/count"), "timers combine their counts");
        assertEquals(45L, read(ab, "apply/split/total"), "and their totals");
        assertEquals(5L, read(ab, "apply/split/min"), "the least of the leasts");
        assertEquals(30L, read(ab, "apply/split/max"), "the greatest of the greatests");
        assertEquals(Analysis.Helper.merge(ab, c), Analysis.Helper.merge(a, Analysis.Helper.merge(b, c)), "associative");
        assertEquals(ab, Analysis.Helper.merge(b, a), "commutative: counters and timers are order-free");
        final Analysis empty = new BasicAnalyzer().analysis();
        assertEquals(a, Analysis.Helper.merge(a, empty), "the empty analysis is the identity on the right");
        assertEquals(a, Analysis.Helper.merge(empty, a), "and on the left");
        // disjoint probes are a union
        final Analyzer flow = new BasicAnalyzer();
        flow.incr(f(FLOW).extend(f(MONADS)), 6L);
        final Analysis union = Analysis.Helper.merge(a, flow.analysis());
        assertEquals(1L, read(union, "wire/sent"), "a's probes survive");
        assertEquals(6L, read(union, "flow/monads"), "and so do the other's");
    }

    @Test
    void shouldKeepTheLaterGaugeWhenMerging() {
        final Analyzer x = new BasicAnalyzer();
        x.set(f(WIRE).extend(f(OPEN)), 2.0d);
        final Analyzer y = new BasicAnalyzer();
        y.set(f(WIRE).extend(f(OPEN)), 5.0d);
        assertEquals(5.0d, Analysis.Helper.merge(x.analysis(), y.analysis()).at(uri("wire/open")).realValue(), 0.0d,
                "a gauge is no monoid: the later reading stands");
    }

    // ======================== the address ========================

    /**
     * EVERY COMPONENT'S ANALYZER IS READ AT ITS ADDRESS, the network's included though everything else below the
     * network is the network's: the machine's space answers it, live.
     */
    @ParameterizedTest
    @CsvSource(value = {"network", "processor"}, delimiter = '%')
    void shouldReadAnAnalyzerLiveAtItsAddress(final String component) {
        final Machine machine = Machine.current().push(f("analyzer_read"));
        try {
            final Analyzer analyzer = machine.<Component>at(uri(component)).analyzer();
            final fURI at = f("~").extend(component).extend(ANALYZER);
            assertInstanceOf(Analyzer.class, Machine.read(at), "the analyzer is read at its address");
            analyzer.incr(f(FLOW).extend(f(MONADS)), 7L);
            assertEquals(7L, Machine.read(at.extend(ANALYSIS).extend(FLOW).extend(MONADS)).intValue(),
                    "and its meters through it, as they stand");
            analyzer.incr(f(FLOW).extend(f(MONADS)), 1L);
            assertEquals(8L, Machine.read(at.extend(ANALYSIS).extend(FLOW).extend(MONADS)).intValue(), "live");
        } finally {
            machine.pop();
        }
    }

    /**
     * A COMPONENT'S ANALYZER IS WRITTEN IN PLACE, NEVER REPLACED: a field write sets the field, a write at the analyzer
     * declares its settings (the analyzer and its meters stay the component's), and its analysis takes no write.
     */
    @ParameterizedTest
    @CsvSource(value = {"network", "processor"}, delimiter = '%')
    void shouldWriteAnAnalyzersSettingsInPlace(final String component) {
        final Machine machine = Machine.current().push(f("analyzer_write"));
        try {
            final Analyzer analyzer = machine.<Component>at(uri(component)).analyzer();
            analyzer.incr(f(WIRE).extend(f(SENT)), 2L);
            final fURI at = f("~").extend(component).extend(ANALYZER);
            Machine.write(at.extend(STEP), jnt(400));
            assertEquals(400L, analyzer.atDirect(uri(STEP)).intValue(), "a field write lands in the analyzer itself");
            assertEquals(400L, Machine.read(at.extend(STEP)).intValue(), "and is read back at its address");
            Machine.write(at, rec(uri(STEP), jnt(300), uri(PROFILE), bool(false)));
            assertSame(analyzer, machine.<Component>at(uri(component)).analyzer(), "a write at the analyzer never replaces it");
            assertEquals(300L, analyzer.atDirect(uri(STEP)).intValue(), "it declares the settings");
            assertEquals(2L, read(analyzer.analysis(), "wire/sent"), "and the meters behind it are kept");
            Machine.write(at.extend(ANALYSIS), jnt(5));
            assertEquals(2L, Machine.read(at.extend(ANALYSIS).extend(WIRE).extend(SENT)).intValue(),
                    "an analysis is derived from its meters, and takes no write");
            // the mtron spelling of the same declaration: an update of the analyzer read where it lives
            ObjmtronSerializer.parse("@~/" + component + "/analyzer >>= [step => 200]").apply();
            assertEquals(200L, machine.<Component>at(uri(component)).analyzer().atDirect(uri(STEP)).intValue(),
                    "an update declares a setting");
            assertEquals(2L, read(machine.<Component>at(uri(component)).analyzer().analysis(), "wire/sent"),
                    "and keeps the meters");
        } finally {
            machine.pop();
        }
    }

    // ======================== the publication ========================

    /**
     * THE PUBLICATION IS A STEP, NOT AN ECHO: a burst of changes rides one publication, which carries every change
     * before it, and a quiet analyzer publishes nothing.
     */
    @Test
    void shouldPublishOneAnalysisPerStepAfterAChange() {
        final Machine machine = Machine.current().push(f("analyzer_publish"));
        try {
            final AtomicInteger heard = new AtomicInteger();
            final AtomicReference<Obj> last = new AtomicReference<>(noobj());
            Machine.write(f("~/network/analyzer/analysis").addQ(SUBQ), instLambda((pub, inst) -> {
                heard.incrementAndGet();
                last.set(pub.asLst().lstValue().get(1));
                return noobj();
            }));
            final Analyzer network = machine.network().analyzer();
            for (int i = 0; i < 200; i++)
                network.incr(f(WIRE).extend(f(SENT)), 1L);
            CommonUtil.sleepThread(PUBLISHED_MS);
            assertEquals(1, heard.get(), "a burst rides one publication, not one per change");
            assertEquals(200L, read(last.get(), "wire/sent"), "and the publication carries every change before it");
            CommonUtil.sleepThread(PUBLISHED_MS);
            assertEquals(1, heard.get(), "a quiet analyzer publishes nothing");
            network.incr(f(WIRE).extend(f(SENT)), 1L);
            CommonUtil.sleepThread(PUBLISHED_MS);
            assertEquals(2, heard.get(), "the next change is the next publication");
            assertEquals(201L, read(last.get(), "wire/sent"), "carrying the meters as they stand");
            Machine.write(f("~/network/analyzer/analysis").addQ(SUBQ), noobj());
            network.incr(f(WIRE).extend(f(SENT)), 1L);
            CommonUtil.sleepThread(PUBLISHED_MS);
            assertEquals(2, heard.get(), "an unsubscribed analyzer reaches no one");
        } finally {
            machine.pop();
        }
    }

    /**
     * THE STEP IS A SETTING, and any int the type admits is a step: a shorter one publishes sooner, and one that cannot
     * be waited (negative) publishes at once rather than losing the publication.
     */
    @ParameterizedTest
    @CsvSource(value = {"50", "0", "-5"}, delimiter = '%')
    void shouldPublishAtTheStepTheAnalyzerStates(final long step) {
        final Machine machine = Machine.current().push(f("analyzer_step"));
        try {
            final AtomicInteger heard = new AtomicInteger();
            Machine.write(f("~/processor/analyzer/step"), jnt(step));
            Machine.write(f("~/processor/analyzer/analysis").addQ(SUBQ), instLambda((pub, inst) -> {
                heard.incrementAndGet();
                return noobj();
            }));
            machine.processor().analyzer().incr(f(FLOW).extend(f(MONADS)), 1L);
            CommonUtil.sleepThread(PUBLISHED_MS);
            assertEquals(1, heard.get(), "a step of " + step + " publishes the change");
        } finally {
            machine.pop();
        }
    }

    /**
     * THE PUBLICATION IS A METATRON THREAD: a subscriber hears it on a thread whose source is a virtual thread (it mostly
     * sleeps), which the executor listed as running the moment the change scheduled it and as ended once it had
     * published.
     */
    @Test
    void shouldPublishFromAThreadTheExecutorLists() {
        final Machine machine = Machine.current().push(f("analyzer_thread"));
        try {
            final AtomicReference<AbstractThread> heardOn = new AtomicReference<>();
            Machine.write(f("~/network/analyzer/analysis").addQ(SUBQ), instLambda((pub, inst) -> {
                heardOn.compareAndSet(null, BootLoader.CURRENT_THREAD.get());
                return noobj();
            }));
            final List<fURI> running = listedThreads(RUN);
            machine.network().analyzer().incr(f(WIRE).extend(f(SENT)), 1L);
            final List<fURI> scheduled = listedThreads(RUN).stream().filter(thread -> !running.contains(thread)).toList();
            CommonUtil.sleepThread(PUBLISHED_MS);
            assertNotNull(heardOn.get(), "the subscriber hears the change");
            final fURI publication = heardOn.get().sourceVid().orElseThrow();
            assertTrue(scheduled.contains(publication), "the change lists its publication at once: " + scheduled);
            assertInstanceOf(VirtualThread.class, Machine.read(publication), "a virtual thread");
            assertTrue(awaitsStopped(publication, PUBLISHED_MS),
                    () -> "listed as ended once it has published: " + listedThreads(STOP));
        } finally {
            machine.pop();
        }
    }

    /**
     * A PUBLICATION THAT CANNOT BE MADE HOLDS BACK NONE AFTER IT. Making a publication's thread writes it at its vid;
     * with that write refused, the change is still counted and goes unpublished, and the next change, once threads can
     * be made again, publishes both.
     */
    @Test
    void shouldPublishAfterAPublicationThatCouldNotBeMade() {
        final Machine machine = Machine.current().push(f("analyzer_refused"));
        try {
            final AtomicInteger heard = new AtomicInteger();
            final AtomicReference<Obj> last = new AtomicReference<>(noobj());
            Machine.write(f("~/network/analyzer/analysis").addQ(SUBQ), instLambda((pub, inst) -> {
                heard.incrementAndGet();
                last.set(pub.asLst().lstValue().get(1));
                return noobj();
            }));
            final AtomicBoolean refusing = new AtomicBoolean(true);
            refuseThreads(machine, refusing);
            final Analyzer network = machine.network().analyzer();
            network.incr(f(WIRE).extend(f(SENT)), 1L);
            CommonUtil.sleepThread(PUBLISHED_MS);
            assertEquals(0, heard.get(), "a change whose publication cannot be made goes unpublished");
            refusing.set(false);
            network.incr(f(WIRE).extend(f(SENT)), 1L);
            CommonUtil.sleepThread(PUBLISHED_MS);
            assertEquals(1, heard.get(), "and the next change publishes");
            assertEquals(2L, read(last.get(), "wire/sent"), "carrying the change that went unpublished");
        } finally {
            machine.pop();
        }
    }

    /**
     * ONE REGISTRY HEARS EVERY ANALYZER: the machine's space answers each component's analyzer, so a single wildcard
     * subscription is the machine's metric bus — the network's analyzer included.
     */
    @Test
    void shouldHearEveryComponentOnTheWildcardBus() {
        final Machine machine = Machine.current().push(f("analyzer_bus"));
        try {
            final Set<String> heard = ConcurrentHashMap.newKeySet();
            Machine.write(f("~/+/analyzer/analysis").addQ(SUBQ), instLambda((pub, inst) -> {
                heard.add(pub.asLst().lstValue().getFirst().uriValue().toString());
                return noobj();
            }));
            machine.network().analyzer().incr(f(WIRE).extend(f(SENT)), 1L);
            machine.processor().analyzer().incr(f(FLOW).extend(f(MONADS)), 1L);
            CommonUtil.sleepThread(PUBLISHED_MS);
            final fURI root = machine.vid().resolve();
            assertTrue(heard.contains(root.extend(NETWORK).extend(ANALYZER).extend(ANALYSIS).toString()),
                    "the network's analyzer is heard on the bus: " + heard);
            assertTrue(heard.contains(root.extend(PROCESSOR).extend(ANALYZER).extend(ANALYSIS).toString()),
                    "and the processor's: " + heard);
        } finally {
            machine.pop();
        }
    }

    // ======================== the verbs ========================

    /**
     * EVERY VERB GATES ITSELF, so no producer tests for an analyzer before it records: an absent analyzer (none kept, or
     * switched off) records nothing and the work a verb wraps still runs, and a live one keeps each verb's meter. A
     * block that throws is timed too, and its throw passes through.
     */
    @Test
    void shouldGateEveryVerbOnTheAnalyzerItIsHanded() {
        final fURI counted = f("verb/counted");
        final fURI gauged = f("verb/gauged");
        final fURI timed = f("verb/timed");
        final fURI region = f("verb/region");
        final fURI recorded = f("verb/recorded");
        // absent: nothing to record into, and the work is still the work
        Analyzer.Helper.incr(null, counted, 1L);
        Analyzer.Helper.set(null, gauged, 1.0d);
        Analyzer.Helper.record(null, recorded, 5L);
        assertEquals("done", Analyzer.Helper.timed(null, timed, () -> "done"), "an unmeasured block runs bare");
        assertEquals(0L, Analyzer.Helper.start(null), "an unmeasured region never reads the clock");
        Analyzer.Helper.stop(null, region, 0L);
        assertTrue(Analyzer.Helper.analysis(null).isEmpty(), "and an absent analyzer reads as nothing measured");
        // live: each verb keeps its meter
        final Analyzer live = new BasicAnalyzer();
        Analyzer.Helper.incr(live, counted, 2L);
        Analyzer.Helper.set(live, gauged, 0.5d);
        Analyzer.Helper.record(live, recorded, 5L);
        assertEquals("done", Analyzer.Helper.timed(live, timed, () -> "done"), "a measured block answers what it answers");
        Analyzer.Helper.stop(live, region, Analyzer.Helper.start(live));
        assertThrows(MTronException.class, () -> Analyzer.Helper.timed(live, timed, () -> {
            throw MTronException.of("the block failed");
        }));
        final Analysis analysis = live.analysis();
        assertEquals(2L, read(analysis, "verb/counted"), "the counter: " + analysis);
        assertEquals(0.5d, analysis.at(uri("verb/gauged")).realValue(), 0.0d, "the gauge: " + analysis);
        assertEquals(2L, read(analysis, "verb/timed/count"), "both blocks timed, the one that threw included: " + analysis);
        assertEquals(1L, read(analysis, "verb/region/count"), "the region: " + analysis);
        assertEquals(5L, read(analysis, "verb/recorded/total"), "the duration measured elsewhere: " + analysis);
    }

    /**
     * A MUTED PROBE IS AN ANALYSIS KEY WRITTEN NOOBJ: it records nothing, and neither does any probe below it, while its
     * siblings record on (a probe is muted by its path, never by a prefix of its name); what it counted is dropped, so
     * written back it counts from zero; and a sampled gauge it covers is hidden, never dropped, because nothing would
     * register it again.
     */
    @Test
    void shouldMuteAProbeAndItsSubtreeUntilItIsWrittenBack() {
        final Analyzer analyzer = new BasicAnalyzer();
        final AtomicLong held = new AtomicLong(3L);
        Analyzer.Helper.incr(analyzer, f("verb/counted"), 2L);
        Analyzer.Helper.gauge(analyzer, f("verb/sampled"), held::get);
        analyzer.mute(f("verb"), true);
        Analyzer.Helper.incr(analyzer, f("verb/counted"), 1L);
        Analyzer.Helper.set(analyzer, f("verb/gauged"), 1.0d);
        Analyzer.Helper.record(analyzer, f("verb/recorded"), 5L);
        Analyzer.Helper.stop(analyzer, f("verb/region"), Analyzer.Helper.start(analyzer));
        Analyzer.Helper.incr(analyzer, f("verbose/counted"), 1L);
        final Analysis muted = analyzer.analysis();
        assertTrue(muted.at(uri("verb")).isNoObj(), "a muted probe and its subtree leave the analysis: " + muted);
        assertEquals(1L, read(muted, "verbose/counted"), "a sibling its name prefixes records on: " + muted);
        analyzer.mute(f("verb/counted"), false);
        Analyzer.Helper.incr(analyzer, f("verb/counted"), 1L);
        assertTrue(analyzer.analysis().at(uri("verb")).isNoObj(),
                "a probe below a muted one stays muted: " + analyzer.analysis());
        analyzer.mute(f("verb"), false);
        Analyzer.Helper.incr(analyzer, f("verb/counted"), 1L);
        final Analysis lifted = analyzer.analysis();
        assertEquals(1L, read(lifted, "verb/counted"), "written back, a probe counts from zero: " + lifted);
        assertEquals(3.0d, lifted.at(uri("verb/sampled")).realValue(), 0.0d, "and a gauge it hid is sampled again: " + lifted);
    }

    /**
     * A SAMPLED GAUGE IS READ, NEVER RECORDED: its value is the sample's as the analysis is built, it follows what it
     * samples with no event, a stray recording at its probe changes nothing, and a sample that fails drops the probe
     * from that read rather than breaking it.
     */
    @Test
    void shouldSampleAGaugeAsTheAnalysisIsRead() {
        final Analyzer analyzer = new BasicAnalyzer();
        final AtomicLong held = new AtomicLong(2L);
        Analyzer.Helper.gauge(analyzer, f("level/peer"), held::get);
        final Obj first = analyzer.analysis().at(uri("level/peer"));
        assertEquals(Analysis.GAUGE_TID, first.tid().basePath(), "a sampled meter is a gauge: " + first);
        assertEquals(2.0d, first.realValue(), 0.0d, "read as the analysis is built");
        held.set(5L);
        assertEquals(5.0d, analyzer.analysis().at(uri("level/peer")).realValue(), 0.0d, "and it follows what it samples");
        Analyzer.Helper.incr(analyzer, f("level/peer"), 1L);
        assertEquals(5.0d, analyzer.analysis().at(uri("level/peer")).realValue(), 0.0d,
                "a stray recording at a sampled probe changes nothing");
        Analyzer.Helper.gauge(analyzer, f("level/broken"), () -> {
            throw MTronException.of("what it sampled is gone");
        });
        final Analysis analysis = analyzer.analysis();
        assertTrue(analysis.at(uri("level/broken")).isNoObj(), "a sample that fails drops its probe: " + analysis);
        assertEquals(5.0d, analysis.at(uri("level/peer")).realValue(), 0.0d, "and the rest of the read stands");
        Analyzer.Helper.gauge(null, f("level/peer"), held::get);
    }

    // ======================== the profile ========================

    /**
     * A PROFILE MEASURES EXACTLY THE RUNS IT SCOPES: a processor whose profile is off profiles nothing (it counts the
     * evaluation, and that is all), a scoped run measures its stages and its flow into the scope's analyzer alone, and
     * nothing of it leaks into the processor's.
     */
    @Test
    void shouldMeasureOnlyTheRunsAProfileScopes() {
        final Machine machine = Machine.current().push(f("analyzer_scope"));
        try {
            final Analyzer processor = machine.processor().analyzer();
            Machine.parse("{1,2,3}.plus(1).sum()").apply(noobj());
            assertEquals(EVALUATED, processor.analysis().jvm().keySet(),
                    "a processor whose profile is off counts the evaluation and profiles nothing: " + processor.analysis());
            final Analyzer profile = new BasicAnalyzer();
            Analyzer.PROFILE.set(profile);
            try {
                Machine.parse("{1,2,3}.plus(1).sum()").apply(noobj());
            } finally {
                Analyzer.PROFILE.remove();
            }
            final Analysis measured = profile.analysis();
            assertTrue(read(measured, "flow/monads") > 0, "the run's monads were counted: " + measured);
            assertTrue(read(measured, "flow/processors") > 0, "and its computers: " + measured);
            assertTrue(read(measured, "apply/apply/count") > 0, "its applies timed: " + measured);
            assertTrue(read(measured, "resolve/inst_resolve/count") > 0, "its resolution timed: " + measured);
            assertTrue(read(measured, "cache/hits") + read(measured, "cache/misses") > 0,
                    "its type lookups counted: " + measured);
            assertFalse(measured.at(uri(PER_INST)).isNoObj(), "and each instruction's flow kept: " + measured);
            assertEquals(EVALUATED, processor.analysis().jvm().keySet(),
                    "nothing of the profile leaked into the processor's own analyzer: " + processor.analysis());
            final long monads = read(measured, "flow/monads");
            Machine.parse("{1,2,3}.plus(1).sum()").apply(noobj());
            assertEquals(monads, read(profile.analysis(), "flow/monads"), "and a run outside the scope is not the scope's");
        } finally {
            machine.pop();
        }
    }

    /**
     * A PROCESSOR THAT PROFILES gives each evaluation a profile of its own, held by the computer that ran it at
     * {@code ~/processor/computer/<n>/analyzer}, while its own analyzer keeps only the machine's totals — until it stops; a
     * pushed machine profiles as its parent did at the push, into computers of its own.
     */
    @Test
    void shouldProfileEachEvaluationAtTheComputerThatRanIt() {
        final Machine machine = Machine.current().push(f("analyzer_profile"));
        try {
            final Analyzer processor = machine.processor().analyzer();
            Machine.write(f("~/processor/analyzer/profile"), bool(true));
            Machine.parse("{1,2,3}.plus(1)").apply(noobj());
            final List<BasicSwarmComputer> first = profiled(machine);
            assertEquals(1, first.size(), "the evaluation's computer holds its profile: " + first);
            final fURI at = first.getFirst().vid().extend(ANALYZER).extend(ANALYSIS);
            final long during = Machine.read(at.extend(FLOW).extend(MONADS)).intValue();
            assertTrue(during > 0, "read at the computer's address, " + at);
            final Analysis profile = Machine.read(at).as();
            assertTrue(read(profile, "cache/hits") + read(profile, "cache/misses") > 0,
                    "with the type lookups its computers made: " + profile);
            assertEquals(EVALUATED, processor.analysis().jvm().keySet(),
                    "while the processor's own analyzer keeps the totals alone: " + processor.analysis());
            Machine.parse("{1,2,3}.plus(1)").apply(noobj());
            final List<BasicSwarmComputer> second = profiled(machine);
            assertEquals(2, second.size(), "the next evaluation is profiled at its own computer: " + second);
            assertNotSame(second.get(0).jvm().get(Analyzer.KEY), second.get(1).jvm().get(Analyzer.KEY),
                    "into an analyzer of its own");
            assertEquals(during, Machine.read(at.extend(FLOW).extend(MONADS)).intValue(),
                    "and the first evaluation's profile is left as it was");
            final Machine child = machine.push(f("child"));
            try {
                assertTrue(child.processor().analyzer().atDirect(uri(PROFILE)).boolValue(),
                        "a pushed machine profiles under the settings it was pushed with");
                Machine.parse("{1,2,3}.plus(1)").apply(noobj());
                assertEquals(1, profiled(child).size(), "so the child's evaluation is profiled at the child's computer");
                assertEquals(2, profiled(machine).size(), "and never at the parent's");
            } finally {
                child.pop();
            }
            Machine.write(f("~/processor/analyzer/profile"), bool(false));
            Machine.parse("{1,2,3}.plus(1)").apply(noobj());
            assertEquals(2, profiled(machine).size(), "nothing more is profiled once the profile is off");
        } finally {
            machine.pop();
        }
    }

    /**
     * A PROFILE()'S ANALYZER IS HELD BY THE FIRST COMPUTER ITS TIMED RUNS MINT, the first claim being the one that holds:
     * the report is read off that analyzer, which stays at the computer's address after the report is made, and the
     * computers its other runs mint record into it without holding it.
     */
    @Test
    void shouldHoldAProfileAtTheFirstComputerItsRunsMint() {
        final Analyzer claimed = new BasicAnalyzer();
        assertTrue(claimed.claim(f("~/processor/computer/1")), "the first claim holds an analyzer");
        assertTrue(claimed.claim(f("~/processor/computer/1")), "and holds it again");
        assertFalse(claimed.claim(f("~/processor/computer/2")), "and no later one does");
        final Machine machine = Machine.current().push(f("analyzer_claim"));
        try {
            final Obj flow = ObjmtronSerializer.eval("{1,2,3}.plus(1).profile()>>flow");
            final List<BasicSwarmComputer> held = profiled(machine);
            assertEquals(1, held.size(), "one computer holds the profile: " + held);
            final long processors = flow.asRec().at(uri(PROCESSORS)).intValue();
            assertTrue(processors > 1, "the timed runs minted several computers: " + flow);
            assertEquals(processors,
                    Machine.read(held.getFirst().vid().extend(ANALYZER).extend(ANALYSIS).extend(FLOW).extend(PROCESSORS)).intValue(),
                    "and the one that holds the profile holds what the report was read from");
        } finally {
            machine.pop();
        }
    }

    // ======================== the processor's evaluations ========================

    /**
     * THE PROCESSOR COUNTS THE MACHINE'S EVALUATIONS, profile or not: each run counted as it starts and timed to its end,
     * and each fail counted where it is filed — once, though the evaluation answers it on. The counts are the
     * processor's, so they outlive the computers that ran them.
     */
    @Test
    void shouldCountEveryEvaluationAtTheProcessor() {
        final Machine machine = Machine.current().push(f("analyzer_evaluations"));
        try {
            final Analyzer processor = machine.processor().analyzer();
            final long before = read(processor.analysis(), "eval");
            Machine.parse("{1,2,3}.plus(1)").apply(noobj());
            final long one = read(processor.analysis(), "eval") - before;
            assertTrue(one > 0, "an evaluation is counted: " + processor.analysis());
            Machine.parse("{1,2,3}.plus(1)").apply(noobj());
            Machine.parse("{1,2,3}.plus(1)").apply(noobj());
            final Analysis three = processor.analysis();
            assertEquals(before + 3 * one, read(three, "eval"), "each one: " + three);
            assertEquals(read(three, "eval"), read(three, "time/count"), "each run timed to its end: " + three);
            assertTrue(three.at(uri("fail")).isNoObj(), "and no fail counted where none was filed: " + three);
            Machine.parse("1.plus(b)").apply(noobj());
            assertEquals(1L, read(processor.analysis(), "fail"), "an evaluation that fails counts its fail once: " + processor.analysis());
            final long evaluations = read(processor.analysis(), "eval");
            machine.processor().close();
            assertTrue(machine.processor().computers().isEmpty(), "with the computers that ran them reaped");
            assertEquals(evaluations, read(processor.analysis(), "eval"), "the counts stand");
        } finally {
            machine.pop();
        }
    }

    /**
     * OFF IS NOOBJ: a processor whose analyzer is written noobj keeps none — its address reads noobj, and it counts no
     * evaluation and profiles none, profile on or not — and a field written while it is off is refused. A rec written at
     * the analyzer installs a new one, which counts from zero under the settings the rec states.
     */
    @Test
    void shouldSwitchAnAnalyzerOffAndOnByWritingIt() {
        final Machine machine = Machine.current().push(f("analyzer_off"));
        try {
            final Analyzer original = machine.processor().analyzer();
            Machine.write(f("~/processor/analyzer/profile"), bool(true));
            final long evals = read(original.analysis(), "eval");
            final int profiles = profiled(machine).size();
            Machine.parse("{1,2,3}.plus(1)").apply(noobj());
            final long one = read(original.analysis(), "eval") - evals;
            final int profiledOne = profiled(machine).size() - profiles;
            assertTrue(one > 0 && profiledOne > 0, "an evaluation is counted and profiled while the analyzer is on");
            Machine.write(f("~/processor/analyzer"), noobj());
            assertNull(machine.processor().analyzer(), "noobj removes the analyzer");
            assertTrue(Machine.read(f("~/processor/analyzer")).isNoObj(), "so its address reads noobj");
            final long counted = read(original.analysis(), "eval");
            Machine.parse("{1,2,3}.plus(1)").apply(noobj());
            Machine.parse("1.plus(b)").apply(noobj());
            assertEquals(counted, read(original.analysis(), "eval"), "an evaluation is counted nowhere");
            assertTrue(original.analysis().at(uri("fail")).isNoObj(), "nor a fail");
            assertEquals(profiles + profiledOne, profiled(machine).size(), "and profiled nowhere, though the profile was on");
            assertTrue(Machine.write(f("~/processor/analyzer/step"), jnt(100)).isNoObj(), "a field written while it is off is refused");
            assertNull(machine.processor().analyzer(), "and installs nothing");
            Machine.write(f("~/processor/analyzer"), rec(uri(PROFILE), bool(true)));
            final Analyzer installed = machine.processor().analyzer();
            assertNotNull(installed, "a rec written at the analyzer installs one");
            assertNotSame(original, installed, "a new one");
            assertTrue(installed.atDirect(uri(PROFILE)).boolValue(), "under the settings the rec states");
            Machine.parse("{1,2,3}.plus(1)").apply(noobj());
            assertEquals(one, read(installed.analysis(), "eval"), "counting from zero: " + installed.analysis());
            assertEquals(profiles + 2 * profiledOne, profiled(machine).size(), "and profiling again");
        } finally {
            machine.pop();
        }
    }

    /**
     * A PROBE IS MUTED AT ITS ADDRESS: noobj written at {@code analysis/<probe>} stops it while its siblings record on,
     * and any other value written there lifts the mute, the probe counting again from zero; the analysis as a whole
     * still takes no write.
     */
    @Test
    void shouldMuteAProbeWrittenNoobjAtItsAddress() {
        final Machine machine = Machine.current().push(f("analyzer_mute"));
        try {
            final Analyzer processor = machine.processor().analyzer();
            Machine.parse("{1,2,3}.plus(1)").apply(noobj());
            assertFalse(Machine.read(f("~/processor/analyzer/analysis/time")).isNoObj(), "an evaluation is timed");
            Machine.write(f("~/processor/analyzer/analysis/time"), noobj());
            assertTrue(Machine.read(f("~/processor/analyzer/analysis/time")).isNoObj(), "until its probe is written noobj");
            final long evals = read(processor.analysis(), "eval");
            Machine.parse("{1,2,3}.plus(1)").apply(noobj());
            assertTrue(Machine.read(f("~/processor/analyzer/analysis/time")).isNoObj(), "then it times nothing: " + processor.analysis());
            assertTrue(read(processor.analysis(), "eval") > evals, "while its sibling counts on: " + processor.analysis());
            Machine.write(f("~/processor/analyzer/analysis/time"), bool(true));
            final long lifted = read(processor.analysis(), "eval");
            Machine.parse("{1,2,3}.plus(1)").apply(noobj());
            assertEquals(read(processor.analysis(), "eval") - lifted, read(processor.analysis(), "time/count"),
                    "written back, it times again, from zero: " + processor.analysis());
            assertTrue(Machine.write(f("~/processor/analyzer/analysis"), noobj()).isNoObj(), "the analysis as a whole takes no write");
            assertFalse(processor.analysis().isEmpty(), "so nothing is muted by one");
        } finally {
            machine.pop();
        }
    }

    /**
     * A NETWORK SWITCHED BACK ON GAUGES ITS PEERS AGAIN: the peer gauge is registered with every analyzer the network
     * is given, so one written at {@code ~/network/analyzer} after a noobj samples them as the first did.
     */
    @Test
    void shouldGaugePeersThroughAReinstalledNetworkAnalyzer() {
        final Machine machine = Machine.current().push(f("analyzer_network_on"));
        try {
            assertEquals(0.0d, Machine.read(f("~/network/analyzer/analysis/peer")).realValue(), 0.0d, "a level with no peer gauges none");
            Machine.write(f("~/network/analyzer"), noobj());
            assertNull(machine.network().analyzer(), "noobj removes the network's analyzer");
            assertTrue(Machine.read(f("~/network/analyzer/analysis/peer")).isNoObj(), "and its gauge with it");
            Machine.write(f("~/network/analyzer"), rec());
            assertNotNull(machine.network().analyzer(), "an empty rec installs one");
            final Obj peer = Machine.read(f("~/network/analyzer/analysis/peer"));
            assertEquals(Analysis.GAUGE_TID, peer.tid().basePath(), "which gauges the peers again: " + peer);
        } finally {
            machine.pop();
        }
    }

    // ======================== derived fields ========================

    /**
     * A DERIVED FIELD IS A CUSTOM ANALYSIS OVER THE ANALYSIS: a metric no producer records can be attached to the
     * analyzer as an auto field, computed on read over the live analysis — a sibling of the analysis, never part of its
     * mergeable core.
     */
    @Test
    void shouldComputeADerivedFieldOverTheAnalysis() {
        final Machine machine = Machine.current().push(f("analyzer_derived"));
        try {
            // a pushed machine's network is a level of its own, with meters of its own: they start at zero
            final Analyzer analyzer = machine.network().analyzer();
            analyzer.incr(f(WIRE).extend(f(SENT)), 100L);
            analyzer.incr(f(WIRE).extend(f(RECV)), 50L);
            // total = wire/sent + wire/recv, attached to the analyzer as a derived (auto) field
            Machine.write(f("~/network/analyzer/total"),
                    auto_(instLambda(lhs -> {
                        final Analysis analysis = analyzer.analysis();
                        return jnt(analysis.at(uri("wire/sent")).intValue() + analysis.at(uri("wire/recv")).intValue());
                    })).tryToInst());
            assertEquals(150L, Machine.read(f("~/network/analyzer/total")).intValue(),
                    "a derived field computes over the live analysis");
            analyzer.incr(f(WIRE).extend(f(SENT)), 5L);
            assertEquals(155L, Machine.read(f("~/network/analyzer/total")).intValue(),
                    "and re-derives as the meters move");
            assertTrue(Machine.read(f("~/network/analyzer/analysis")).asRec().at(uri("total")).isNoObj(),
                    "a derived field is a sibling of the analysis, never inside it");
        } finally {
            machine.pop();
        }
    }
}
