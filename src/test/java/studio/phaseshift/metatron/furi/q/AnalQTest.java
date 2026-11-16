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

package studio.phaseshift.metatron.furi.q;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.CommonUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * Contract for spaces with a metric-recording {@code analq} QProc — every read/write through the space lands in a
 * held {@code analyzer::T}, read back at {@code ?analq}.
 */
public interface AnalQTest extends QProcTest {

    /**
     * Base URI for analq test traffic (e.g. {@code /t/analq}).
     */
    fURI analQBaseURI();

    /**
     * The space with {@code analq} attached, adding it when the backend did not.
     */
    default Space analQSpace() {
        final Space space = this.getSpace();
        if (space.qs().lstValue().stream().noneMatch(q -> ((QProc) q).pattern().equals(ANALQ_PATTERN)))
            space.addQ(QCollection.analQ());
        return space;
    }

    @Test
    default void testAnalQRecordsReadsAndWrites() {
        final Space space = this.analQSpace();
        final fURI base = this.analQBaseURI();
        space.write(base, jnt(1));
        space.read(base);
        final Obj analysis = space.read(base.addQ(ANALQ));
        assertEquals(1L, metric(analysis, "read", "count"), "one read recorded under read/count");
        assertEquals(1L, metric(analysis, "write", "count"), "one write recorded under write/count");
    }

    /**
     * A write of {@code noobj} is a delete: it bumps {@code write/count} and the {@code write/delete/count} subset.
     */
    @Test
    default void testAnalQCountsDeletesUnderTheWriteSubset() {
        final Space space = this.analQSpace();
        final fURI base = this.analQBaseURI();
        space.write(base, noobj());
        final Obj analysis = space.read(base.addQ(ANALQ));
        assertEquals(1L, metric(analysis, "write", "count"), "a delete is still a write");
        assertEquals(1L, metric(analysis, "write", "delete", "count"), "the delete subset counts it too");
    }

    /**
     * Reading the analysis ({@code ?analq}) is not itself a data read — it does not advance {@code read/count}.
     */
    @Test
    default void testAnalQReadIsNotSelfReferential() {
        final Space space = this.analQSpace();
        final fURI base = this.analQBaseURI();
        space.read(base.addQ(ANALQ));
        final Obj analysis = space.read(base.addQ(ANALQ));
        assertEquals(0L, metric(analysis, "read", "count"), "pulling the analysis does not count as a read");
    }

    /**
     * EVERY read and write is TIMED, not sampled: a timer's {@code count} tracks its counter's, and the delete subset
     * carries a timer of its own. One observation has {@code total == min == max}, so a single reading is not lost.
     */
    @Test
    default void testAnalQLatencyTimesEveryReadAndWrite() {
        final Space space = this.analQSpace();
        final fURI base = this.analQBaseURI();
        space.write(base, jnt(1));
        space.write(base, noobj());            // a delete is a write too
        space.read(base);
        final Obj analysis = space.read(base.addQ(ANALQ));
        assertEquals(1L, metric(analysis, "read", "latency", "count"), "the one read was timed");
        assertEquals(metric(analysis, "read", "count"), metric(analysis, "read", "latency", "count"),
                "every read through the source is timed, not a sample of them");
        assertEquals(2L, metric(analysis, "write", "latency", "count"), "both writes were timed, the delete included");
        assertEquals(metric(analysis, "write", "count"), metric(analysis, "write", "latency", "count"),
                "timed as often as counted");
        assertEquals(1L, metric(analysis, "write", "delete", "latency", "count"), "the delete subset was timed");
        final long total = metric(analysis, "read", "latency", "total");
        assertTrue(total > 0L, "a real read took real nanos, not a placeholder zero");
        assertEquals(total, metric(analysis, "read", "latency", "min"), "one observation: its total is its min");
        assertEquals(total, metric(analysis, "read", "latency", "max"), "one observation: its total is its max");
    }

    /**
     * THE MEASURED INTERVAL IS THE SOURCE'S OWN ACCESS, not the pipeline around it: a source that takes 5 millis to
     * answer is timed at 5 millis, so moving the clock outward (before the pre hooks) would fail this case.
     */
    @Test
    default void testAnalQLatencyMeasuresTheAccessItself() {
        final Space space = this.analQSpace();
        final fURI base = this.analQBaseURI();
        QProc.Layer.read(space, base, () -> {
            CommonUtil.sleepThread(5);
            return jnt(1);
        });
        final Obj analysis = space.read(base.addQ(ANALQ));
        assertTrue(metric(analysis, "read", "latency", "max") >= 5_000_000L,
                "the access's own 5ms — not the hooks around it — is what the timer holds");
    }

    /**
     * Walk a nested analysis path ({@code read/count}, {@code write/delete/count}) to its counter value.
     */
    private static long metric(final Obj analysis, final String... path) {
        Obj node = analysis;
        for (final String segment : path)
            node = node.asRec().at(uri(segment));
        return node.intValue();
    }
}
