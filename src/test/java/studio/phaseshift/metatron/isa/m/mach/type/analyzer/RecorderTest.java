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
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_ANALYZER_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * analyzable — the owner side of {@link Analyzer}: the {@link Analyzer.Recorder.Helper} verbs record through a gated,
 * group-scoped path, and {@link Analyzer.Recorder.Helper#locateBaseAnalyzer} resolves an {@code analyzer => path} binding into the
 * analyzer that path names and the group it leaves below it (the {@code locateBasePoly} walk, for analyzers).
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class RecorderTest extends AbstractMetatronTest {

    /**
     * a minimal analyzable: a rec that carries an analyzer and a group, and nothing else
     */
    private static final class TestRecorder extends MRec implements Analyzer.Recorder {
        private final Analyzer analyzer;
        private final fURI group;

        TestRecorder(final Analyzer analyzer, final fURI group) {
            super(new LinkedHashMap<>(), MACH_ANALYZER_TID, null);
            this.analyzer = analyzer;
            this.group = group;
        }

        @Override
        public Analyzer analyzer() {
            return this.analyzer;
        }

        @Override
        public fURI group() {
            return this.group;
        }
    }

    private static long read(final Obj analysis, final String path) {
        return analysis.asRec().at(uri(path)).orElse(jnt(0)).intValue();
    }

    /**
     * AN ANALYZER => PATH RESOLVES TO THE BASE ANALYZER THE PATH NAMES, and the retracted tail is the group: a server's
     * {@code ~/network/analyzer/web_analyzer/http} binds to the network's analyzer under group {@code web_analyzer/http}.
     */
    @Test
    void shouldLocateTheBaseAnalyzerAndRetractTheGroup() {
        final Machine machine = Machine.current().push(f("analyzable_locate"));
        try {
            final Analyzer.Recorder.Helper.Located located = Analyzer.Recorder.Helper.locateBaseAnalyzer(f("~/network/analyzer/web_analyzer/http"));
            assertNotNull(located, "the path names an analyzer");
            assertInstanceOf(Analyzer.class, located.analyzer(), "the base is the network's analyzer");
            assertNotNull(located.group(), "the retracted tail is the group");
            assertTrue(located.group().toString().contains("http"), "the group carries the path's tail: " + located.group());
        } finally {
            machine.pop();
        }
    }

    /**
     * THE HELPER RECORDS UNDER THE GROUP: a counter, a gauge and a timer land beneath the group's path in the one
     * shared analysis, and {@code timed} returns the work's own result.
     */
    @Test
    void shouldRecordThroughTheHelperUnderTheGroup() {
        final Machine machine = Machine.current().push(f("analyzable_helper"));
        try {
            final Analyzer.Recorder.Helper.Located located = Analyzer.Recorder.Helper.locateBaseAnalyzer(f("~/network/analyzer/web_analyzer/http"));
            final Analyzer.Recorder analyzable = new TestRecorder(located.analyzer(), located.group());
            Analyzer.Recorder.Helper.incr(analyzable, f("requests"), 3L);
            Analyzer.Recorder.Helper.set(analyzable, f("connections"), 4.0d);
            final String worked = Analyzer.Recorder.Helper.timed(analyzable, f("latency"), () -> "done");
            assertEquals("done", worked, "timed returns the work's result");
            final Analysis analysis = located.analyzer().analysis();
            assertEquals(3L, read(analysis, "web_analyzer/http/requests"), "the counter nests under the group: " + analysis);
            assertEquals(4.0d, analysis.at(uri("web_analyzer/http/connections")).realValue(), 0.0d, "and the gauge: " + analysis);
            assertTrue(read(analysis, "web_analyzer/http/latency/count") >= 1L, "and the timer observed: " + analysis);
        } finally {
            machine.pop();
        }
    }

    /**
     * AN UNBOUND ANALYZABLE RECORDS NOTHING: every verb is a noop, and {@code timed} runs its work bare and hands back
     * its result.
     */
    @Test
    void shouldNoopWhenUnbound() {
        final Analyzer.Recorder unbound = new TestRecorder(null, null);
        Analyzer.Recorder.Helper.incr(unbound, f("requests"), 1L);
        Analyzer.Recorder.Helper.set(unbound, f("connections"), 2.0d);
        Analyzer.Recorder.Helper.record(unbound, f("latency"), 5L);
        assertEquals("done", Analyzer.Recorder.Helper.timed(unbound, f("latency"), () -> "done"),
                "a muted analyzable runs the work bare and records nothing");
    }

    /**
     * A MULTI-SEGMENT PROBE NESTS UNDER THE GROUP — the $SYS-mirroring shape: a broker recording {@code messages/sent}
     * under group {@code web_analyzer/mqtt} reads back at {@code web_analyzer/mqtt/messages/sent}, so a $SYS leaf can
     * dereference the one analysis.
     */
    @Test
    void shouldNestAMultiSegmentProbeUnderTheGroup() {
        final Machine machine = Machine.current().push(f("analyzable_probe"));
        try {
            final Analyzer.Recorder.Helper.Located located = Analyzer.Recorder.Helper.locateBaseAnalyzer(f("~/network/analyzer/web_analyzer/mqtt"));
            final Analyzer.Recorder analyzable = new TestRecorder(located.analyzer(), located.group());
            Analyzer.Recorder.Helper.incr(analyzable, f("messages/sent"), 9L);
            Analyzer.Recorder.Helper.incr(analyzable, f("packets/sent"), 4L);
            final Analysis analysis = located.analyzer().analysis();
            assertEquals(9L, read(analysis, "web_analyzer/mqtt/messages/sent"), "the probe nests under the group: " + analysis);
            assertEquals(4L, read(analysis, "web_analyzer/mqtt/packets/sent"), "and its sibling: " + analysis);
        } finally {
            machine.pop();
        }
    }
}
