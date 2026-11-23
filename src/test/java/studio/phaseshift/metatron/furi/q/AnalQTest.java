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
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.Analysis;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.CommonUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
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

    /**
     * AN ADDRESS THAT ASKS IS MEASURED ON ITS OWN, AS IT ASKED (Marko, 2026-11-23). A request is the type of its readings
     * ({@code read=>counter::T} reads back as a count), an empty request asks for everything the cap allows (here every
     * event, as a timer), the children answer at {@code <addr>/+?analq}, an address that never asked answers the space's
     * whole analysis, and noobj withdraws a request along with what it measured.
     */
    @Test
    default void testAnalQMeasuresAnAddressThatAsks() {
        final Space space = this.analQSpace();
        final fURI base = this.analQBaseURI();
        final fURI other = base.retract(1).extend("analq_other");
        final fURI silent = base.retract(1).extend("analq_silent");
        assertFalse(space.write(base.addQ(ANALQ), rec(uri("read"), T(Analysis.COUNTER_TID))).isFail(), "a request within the cap is taken");
        space.write(base, jnt(1));
        space.read(base);
        space.read(base);
        final Obj own = space.read(base.addQ(ANALQ));
        assertEquals(2L, own.asRec().at(uri("read")).intValue(), "it counts the reads it asked for: " + own);
        assertTrue(own.asRec().at(uri("write")).isNoObj(), "and nothing it did not ask for: " + own);
        space.write(other.addQ(ANALQ), rec());
        space.write(other, jnt(2));
        assertEquals(1L, space.read(other.addQ(ANALQ)).asRec().at(uri("write/count")).intValue(),
                "an empty request takes the whole cap: its write is timed, and a timer counts too");
        final Obj children = space.read(base.retract(1).extend("+").addQ(ANALQ));
        assertEquals(2L, children.asRec().at(uri(base.name())).asRec().at(uri("read")).intValue(),
                "the children answer at +, each with its own readings: " + children);
        space.read(silent);
        assertFalse(space.read(silent.addQ(ANALQ)).asRec().at(uri("read/count")).isNoObj(),
                "an address that never asked answers the space's whole analysis");
        space.write(base.addQ(ANALQ), noobj());
        assertTrue(space.read(base.retract(1).extend("#").addQ(ANALQ)).asRec().at(uri(f("addr").extend(base.asRelative()))).isNoObj(),
                "a withdrawn request takes what it measured with it");
        space.write(other.addQ(ANALQ), noobj());
    }

    /**
     * A REQUEST FITS ITS SPACE'S CAP OR IS REFUSED: an event the cap does not name, or one asked for more finely than the
     * cap measures it (a timer under a counter), fails the write, and so does an address past the cap's {@code max}.
     */
    @Test
    default void testAnalQRefusesARequestBeyondItsCap() {
        final Space space = this.analQSpace();
        final fURI base = this.analQBaseURI();
        final fURI other = base.retract(1).extend("analq_other");
        space.qs().lstValue().removeIf(q -> ((QProc) q).pattern().equals(ANALQ_PATTERN));
        space.addQ(QCollection.analQ(rec(uri("read"), T(Analysis.COUNTER_TID), uri("max"), jnt(1))));
        try {
            assertTrue(space.write(base.addQ(ANALQ), rec(uri("read"), T(Analysis.TIMER_TID))).isFail(),
                    "a read asked to be timed under a cap that only counts it is refused");
            assertTrue(space.write(base.addQ(ANALQ), rec(uri("write"), T(Analysis.COUNTER_TID))).isFail(),
                    "an event the cap does not name is refused");
            assertTrue(space.write(base.addQ(ANALQ), rec(uri("read"), T(Analysis.GAUGE_TID))).isFail(),
                    "a gauge is never asked of an address: a request names counters and timers");
            assertTrue(space.write(base.retract(1).extend("read").addQ(ANALQ), rec(uri("read"), T(Analysis.COUNTER_TID))).isFail(),
                    "an address with a segment named after an event cannot be measured on its own");
            assertFalse(space.write(base.addQ(ANALQ), rec(uri("read"), T(Analysis.COUNTER_TID))).isFail(),
                    "what the cap allows is taken");
            assertTrue(space.write(other.addQ(ANALQ), rec(uri("read"), T(Analysis.COUNTER_TID))).isFail(),
                    "a second address is past the cap's max of one, the first holding its slot from the request on");
            space.qs().lstValue().removeIf(q -> ((QProc) q).pattern().equals(ANALQ_PATTERN));
            space.addQ(QCollection.analQ(rec(uri("max"), jnt(5))));
            assertFalse(space.write(base.addQ(ANALQ), rec(uri("write"), T(Analysis.TIMER_TID))).isFail(),
                    "a cap that states only max states no event, and allows every one as a timer");
            space.write(base.addQ(ANALQ), noobj());
        } finally {
            space.qs().lstValue().removeIf(q -> ((QProc) q).pattern().equals(ANALQ_PATTERN));
            space.addQ(QCollection.analQ());
        }
    }

    /**
     * A REQUEST'S LIFE, AS A REVIEW TRIED TO BREAK IT (2026-11-23).
     * <ul>
     *   <li>An address that asked answers an empty analysis before its first reading, never its data.</li>
     *   <li>A pattern measures each address it covers, misses and deletes included when asked; an address's own request
     *   is more specific than the pattern, and a wildcard access names no address.</li>
     *   <li>A withdrawal is handled where it is written (the data untouched) and keeps what a standing request covers.</li>
     *   <li>A request asked again is measured afresh, and a wildcard in mid-path withdraws as any pattern does.</li>
     * </ul>
     */
    @Test
    default void testAnalQRequestsLiveAndAreWithdrawn() {
        final Space space = this.analQSpace();
        final fURI tree = this.analQBaseURI().retract(1).extend("analq_tree");
        final fURI a = tree.extend("a");
        final fURI b = tree.extend("b");
        final fURI pattern = tree.extend("#");
        try {
            space.write(a, jnt(42));
            space.write(a.addQ(ANALQ), rec(uri("read"), T(Analysis.COUNTER_TID)));
            final Obj before = space.read(a.addQ(ANALQ));
            assertTrue(before.isRec() && before.asRec().isEmpty(), "asked and not yet read, an address answers an empty analysis, never its data: " + before);
            space.write(pattern.addQ(ANALQ), rec(uri("read"), T(Analysis.COUNTER_TID), uri("miss"), T(Analysis.COUNTER_TID), uri("delete"), T(Analysis.COUNTER_TID)));
            space.read(a);
            space.read(b);
            space.write(b, noobj());
            space.read(pattern);
            final Obj children = space.read(tree.extend("+").addQ(ANALQ));
            assertEquals(1L, children.asRec().at(uri("a")).asRec().at(uri("read")).intValue(), "a counts its read under its own request: " + children);
            assertTrue(children.asRec().at(uri("a")).asRec().at(uri("miss")).isNoObj(), "which is more specific than the pattern: " + children);
            assertEquals(1L, children.asRec().at(uri("b")).asRec().at(uri("miss")).intValue(), "the pattern measures b's miss: " + children);
            assertEquals(1L, children.asRec().at(uri("b")).asRec().at(uri("delete")).intValue(), "and its delete: " + children);
            assertEquals(2, children.asRec().jvm().size(), "and a wildcard access is no address: " + children);
            assertFalse(space.write(pattern.addQ(ANALQ), noobj()).isNoObj(), "a withdrawal is handled where it is written");
            assertEquals(1L, space.read(a.addQ(ANALQ)).asRec().at(uri("read")).intValue(), "a standing request keeps its readings");
            assertEquals(42L, space.read(a).intValue(), "and the data under the withdrawal is untouched");
            assertTrue(space.read(tree.extend("+").addQ(ANALQ)).asRec().at(uri("b")).isNoObj(), "what the pattern alone measured goes with it");
            space.write(a.addQ(ANALQ), rec(uri("read"), T(Analysis.TIMER_TID)));
            space.read(a);
            assertEquals(1L, space.read(a.addQ(ANALQ)).asRec().at(uri("read/count")).intValue(), "asked again, a request is measured afresh, as a timer now");
            space.write(tree.extend("+").extend("status").addQ(ANALQ), rec(uri("read"), T(Analysis.COUNTER_TID)));
            space.write(tree.extend("+").extend("status").addQ(ANALQ), noobj());
            space.read(a);
            assertEquals(2L, space.read(a.addQ(ANALQ)).asRec().at(uri("read/count")).intValue(),
                    "a wildcard in mid-path withdraws as any pattern does, muting nothing it never measured");
        } finally {
            space.write(a.addQ(ANALQ), noobj());
            space.write(a, noobj());
        }
    }
}
