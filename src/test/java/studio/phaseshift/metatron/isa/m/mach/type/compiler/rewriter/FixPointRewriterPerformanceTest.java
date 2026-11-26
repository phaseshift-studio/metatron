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

package studio.phaseshift.metatron.isa.m.mach.type.compiler.rewriter;

import jdk.jfr.Recording;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.Analyzer;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.BasicAnalyzer;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Parser;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static studio.phaseshift.metatron.Tokens.COUNT;
import static studio.phaseshift.metatron.Tokens.PEER;
import static studio.phaseshift.metatron.Tokens.REWRITE;
import static studio.phaseshift.metatron.Tokens.TIME;
import static studio.phaseshift.metatron.Tokens.TOTAL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Performance and validation coverage for the rewrite stage, {@link FixPointRewriter}: the pool of rewrite rules a
 * chain is taken through, to a fixpoint, before it is resolved.
 * <p>
 * <b>What is measured.</b> Each row is a program, compiled from one parse and rewritten from a fresh copy of it every
 * run (some rules rewrite their code in place): the rewrite alone, the whole compile (rewrite, resolve, type) and the
 * end-to-end evaluation of the source text, each the best and the median of the measured runs after the warm-up runs.
 * A profiled pass then times every rule ({@code rewrite/<rule>/time}, the probes {@code profile()} reads). Every row is
 * measured under two peer maps: {@link Peers#none}, nothing dialed, and {@link Peers#gateway}, the two web gateways
 * that four of the stock boots construct, boot.mtron among them ({@code http_client::[host=><http://#>]} and its https
 * twin), peers no lane can be dealt to.
 * {@code law_shard} declines either way, but only a lone machine declines before it plans.
 * <p>
 * The numbers go to {@code target/rewriter-performance.txt} (in a docker run, {@code .docker-target/}), one line per
 * row and peer map, then a table of the rules, slowest first. {@code -Dmetatron.rewriter.perf.report=<path>} moves the
 * report, {@code -Dmetatron.rewriter.perf.runs=<n>} scales the measured runs, and
 * {@code -Dmetatron.rewriter.perf.jfr=<path>} samples the whole class into a flight recording, for the methods the time
 * goes to ({@code jfr view hot-methods <path>}).
 * <p>
 * <b>What is asserted.</b> Correctness only, on every run: each row's rewrite and value ({@code checkCodeRewrite}), and
 * the same rewrite under both peer maps. Timing is reported, never asserted: a stopwatch flakes on a loaded box. A new
 * cost center joins as a row.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class FixPointRewriterPerformanceTest extends AbstractMetatronTest {

    private static final int WARMUP = 2;
    private static final int RUNS = Math.max(1, Integer.getInteger("metatron.rewriter.perf.runs", 5));
    private static final String REPORT_PATH = System.getProperty("metatron.rewriter.perf.report",
            System.getProperty("user.dir") + "/target/rewriter-performance.txt");

    /**
     * Consumed by every measured body, so the JIT cannot drop work whose answer nothing reads.
     */
    private static volatile int SINK = 0;

    /**
     * Per peer map: every rule's applications and nanoseconds over the profiled passes, and the sums of the rows' best
     * rewrite, compile and evaluation.
     */
    private static final Map<Peers, Map<String, long[]>> RULES = new EnumMap<>(Peers.class);
    private static final Map<Peers, double[]> TOTALS = new EnumMap<>(Peers.class);

    /**
     * The peer maps a row is measured under: what the machine has dialed when the chain is rewritten.
     */
    private enum Peers {
        none(List.of(), List.of()),
        gateway(List.of("http_client::[host=><http://#>]", "http_client::[host=><https://#>]"), List.of(f("http://#"), f("https://#")));

        private final List<String> clients;
        private final List<fURI> hosts;

        Peers(final List<String> clients, final List<fURI> hosts) {
            this.clients = clients;
            this.hosts = hosts;
        }

        private void dial() {
            this.clients.forEach(Machine::evaluate);
            if (!this.clients.isEmpty())
                assertFalse(Machine.current().network().peers().isEmpty(), "the gateways should be peers");
        }

        private void drop() {
            this.hosts.forEach(host -> Machine.current().network().drop(host));
        }
    }

    /**
     * A flight recording of the whole class, sampling where the time goes, when {@code -Dmetatron.rewriter.perf.jfr=<path>}
     * names the file it is dumped to ({@code jfr view hot-methods <path>} reads it).
     */
    private static Recording RECORDING = null;

    @BeforeAll
    public static void startRecording() {
        if (null == System.getProperty("metatron.rewriter.perf.jfr"))
            return;
        RECORDING = new Recording();
        RECORDING.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
        RECORDING.start();
    }

    @BeforeEach
    public void clearPeers() {
        // a dial another class left in the fork's root would make the lone machine plan
        Machine.current().network().write(f(PEER), noobj());
    }

    @ParameterizedTest
    @CsvSource(value = {
            // a fold over a run of mappers: law_shard's anchor, and plus and mult heads for derivation_contraction to scan
            "{1,2,3,4,5,6,7,8,9,10}.plus(2).mult(3).sum()                              % {1,2,3,4,5,6,7,8,9,10}.plus(2).mult(3).sum()           % 225",
            // the run nested in a map's argument
            "{1,2,3}.map(plus(2).mult(3)).sum()                                        % {1,2,3}.map(plus(2).mult(3)).sum()                     % 36",
            // three members, each rewritten to its own fixpoint
            "5.branch(plus(1).mult(2),plus(3).mult(4),neg().plus(5))                   % 5.branch(plus(1).mult(2),plus(3).mult(4),neg().plus(5)) % {12,32,0}",
            // a long chain
            "{1,2,3,4,5}.plus(1).mult(2).plus(3).mult(4).minus(1).neg().neg().count()  % {1,2,3,4,5}.plus(1).mult(2).plus(3).mult(4).minus(1).count() % 5",
            // rules that fire: the involution, the units, the inverse, a derivation, the poset, the monoid, the dead else
            "5.neg().neg().plus(0).mult(1)                                             % start(5)                                               % 5",
            "5.plus(3).minus(3)                                                        % start(5)                                               % 5",
            "7.plus(map(2).neg())                                                      % 7.minus(2)                                             % 5",
            "{1,2,3,4}.is(gt(1)).is(gt(2)).count()                                     % {1,2,3,4}.is(gt(2)).count()                            % 2",
            "{1,2,3}.sum().sum()                                                       % {1,2,3}.sum()                                          % 6",
            "{1,2,3}.count().else(0)                                                   % {1,2,3}.count()                                        % 3",
            // the code ring: like terms, a shared head, a split's shared prefix
            "5.branch(plus(2),plus(2),mult(3))                                         % 5.branch(plus{2}(2),mult(3))                           % {{2}7,15}",
            "5.branch(plus(1).mult(2),plus(1).mult(3))                                 % 5.plus(1).branch(mult(2),mult(3))                      % {12,18}",
            "1-<[plus(2).mult(3), plus(2).mult(4)]>-                                   % 1.plus(2).-<[mult(3),mult(4)]>-                        % {9,12}",
    }, delimiter = '%')
    public void testRewriteCost(final String program, final String rewrite, final String value) {
        final List<Inst> insts = Parser.Helper.toCode(ObjmtronSerializer.parse(program)).insts();
        final Map<Peers, Obj> rewritten = new EnumMap<>(Peers.class);
        for (final Peers peers : Peers.values()) {
            peers.dial();
            try {
                rewritten.put(peers, measure(peers, program, insts));
            } finally {
                peers.drop();
            }
        }
        checkCodeRewrite(LOG, program, rewrite, value, false);
        // a peer no lane can be dealt to must not change the rewrite
        checkEquality(LOG, rewritten.get(Peers.none), rewritten.get(Peers.gateway), true);
    }

    @AfterAll
    public static void reportRules() throws Exception {
        if (null != RECORDING) {
            RECORDING.stop();
            RECORDING.dump(Path.of(System.getProperty("metatron.rewriter.perf.jfr")));
            RECORDING.close();
        }
        for (final Peers peers : Peers.values()) {
            final double[] totals = TOTALS.getOrDefault(peers, new double[3]);
            report("%-8s totals (sum of the rows' best): rewrite %.3f ms, compile %.3f ms, evaluate %.3f ms",
                    peers, totals[0], totals[1], totals[2]);
            report("%-8s %-28s %10s %12s %12s", peers, "rule", "calls", "total ms", "us/call");
            RULES.getOrDefault(peers, Map.of()).entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
                    .forEach(rule -> report("%-8s %-28s %10d %12.3f %12.1f", peers, rule.getKey(), rule.getValue()[0],
                            rule.getValue()[1] / 1e6, 0 == rule.getValue()[0] ? 0.0 : rule.getValue()[1] / 1e3 / rule.getValue()[0]));
        }
    }

    /**
     * One row under one peer map: the rewrite, compile and evaluation timings, then the profiled pass; the code the
     * rewrite left, for the caller to compare across peer maps.
     */
    private static Obj measure(final Peers peers, final String program, final List<Inst> insts) {
        final Machine machine = Machine.current();
        final Supplier<Code> fresh = () -> MCode.of(new ArrayList<>(insts));
        final Code rewritten = machine.compiler().rewriter().apply(fresh.get());
        final Timing rewrite = time(() -> machine.compiler().rewriter().apply(fresh.get()));
        final Timing compile = time(() -> machine.compiler().apply(fresh.get()));
        final Timing evaluate = time(() -> Machine.evaluate(program));
        final Map<String, long[]> rules = profile(() -> machine.compiler().rewriter().apply(fresh.get()));
        final double[] totals = TOTALS.computeIfAbsent(peers, key -> new double[3]);
        totals[0] += rewrite.best();
        totals[1] += compile.best();
        totals[2] += evaluate.best();
        final Map<String, long[]> all = RULES.computeIfAbsent(peers, key -> new TreeMap<>());
        rules.forEach((rule, meter) -> {
            final long[] sum = all.computeIfAbsent(rule, key -> new long[2]);
            sum[0] += meter[0];
            sum[1] += meter[1];
        });
        final List<String> top = rules.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
                .limit(3)
                .map(rule -> "%s %.3f".formatted(rule.getKey(), rule.getValue()[1] / 1e6 / RUNS))
                .toList();
        report("%-8s rewrite %s  compile %s  evaluate %s  top/rewrite %s  :: %s", peers, rewrite, compile, evaluate, top, program);
        report("%-8s rewrites to %s and answers %s", peers, ObjmtronSerializer.single().write(rewritten),
                ObjmtronSerializer.single().write(Machine.evaluate(program)));
        return rewritten;
    }

    /**
     * The best and the median of {@link #RUNS} runs of {@code body}, in milliseconds, after {@link #WARMUP} runs.
     */
    private static Timing time(final Supplier<?> body) {
        for (int i = 0; i < WARMUP; i++)
            SINK = System.identityHashCode(body.get());
        final double[] millis = new double[RUNS];
        for (int i = 0; i < RUNS; i++) {
            final long start = System.nanoTime();
            final Object answer = body.get();
            millis[i] = (System.nanoTime() - start) / 1e6;
            SINK = System.identityHashCode(answer);
        }
        Arrays.sort(millis);
        return new Timing(millis[0], millis[RUNS / 2]);
    }

    /**
     * Every rule's applications and nanoseconds over {@link #RUNS} runs of {@code body}, read off a profile of its own.
     */
    private static Map<String, long[]> profile(final Supplier<?> body) {
        final Analyzer outer = Analyzer.PROFILE.get();
        final Analyzer profile = new BasicAnalyzer();
        Analyzer.PROFILE.set(profile);
        try {
            for (int i = 0; i < RUNS; i++)
                SINK = System.identityHashCode(body.get());
        } finally {
            Analyzer.PROFILE.set(outer);
        }
        final Map<Obj, Obj> table = profile.analysis().at(uri(REWRITE)).orElse(rec()).asRec().jvm();
        final Map<String, long[]> rules = new TreeMap<>();
        table.forEach((rule, meters) -> {
            final long calls = meters.asRec().at(uri(f(TIME).extend(COUNT))).orElse(jnt(0)).intValue();
            final long nanos = meters.asRec().at(uri(f(TIME).extend(TOTAL))).orElse(jnt(0)).intValue();
            rules.put(rule.toString(), new long[]{calls, nanos});
        });
        return rules;
    }

    private static void report(final String format, final Object... args) {
        final String line = "%s %s%n".formatted(Instant.now(), format.formatted(args));
        STATIC_LOG.info("%s", line.strip());
        try {
            Files.writeString(Path.of(REPORT_PATH), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (final Exception e) {
            // a report that can break the measurement it reports on is worse than no report
        }
    }

    private record Timing(double best, double median) {
        @Override
        public String toString() {
            return "%8.3f ms (median %8.3f)".formatted(this.best, this.median);
        }
    }
}
