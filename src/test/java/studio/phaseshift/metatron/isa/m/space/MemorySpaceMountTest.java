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

package studio.phaseshift.metatron.isa.m.space;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Memory;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.Analyzer;
import studio.phaseshift.metatron.isa.m.type.Obj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * MEMORY IS THE SOLE OWNER OF ITS SPACES: a space mounted at {@code ~/memory/space/<name>} IS the entry in
 * {@code Memory.spaces()}, so reading the mount address answers the live space and writing below it (appending a
 * q-proc, changing a setting) changes that space — no second copy in a store beside the index.
 */
public class MemorySpaceMountTest extends AbstractMetatronTest {

    /**
     * MEMORY'S Q-PROCS ARE READ WITH CARE, since every access memory routes reads them: a {@code q} that holds no
     * q-procs (the brackets forgotten) is taken for none, and is repaired by writing it again; and a ?analq under
     * /memory is refused, as any space's undeclared query is, while memory declares none.
     */
    @Test
    void testMemorysQProcsCannotBreakItsRouting() {
        Machine.evaluate("memspace::[pattern=>/carea/#]@/memory/space/carea");
        final Obj refused = Machine.evaluate("/memory/space/carea?analq -> analysis::[read=>counter::T]");
        assertTrue(refused.isFail(), "memory declares no analq yet: the request is refused, never silently taken: " + refused);
        Machine.evaluate("/memory/q -> analq::[=>]");
        try {
            Machine.evaluate("/carea/a -> 1");
            assertEquals(1L, Machine.read(f("/carea/a")).intValue(), "a q that holds no q-proc list breaks no access memory routes");
        } finally {
            Machine.evaluate("/memory/q -> noobj");
        }
        assertEquals(1L, Machine.read(f("/carea/a")).intValue(), "and it is repaired by writing it again");
    }

    /**
     * MEMORY IS A SPACE WHOSE ADDRESSES ARE ITS SPACES (Marko, 2026-11-23). With {@code q => [analq::[=>]]} at the memory,
     * a space asks to be measured at its mount address, every access memory routes to it counts there, and the space
     * itself declares nothing. The spaces answer at {@code /memory/space/+?analq}, and memory's totals at
     * {@code /memory/#?analq}.
     */
    @Test
    void testMemoryMeasuresTheSpacesItRoutesTo() {
        Machine.evaluate("memspace::[pattern=>/logs/#]@/memory/space/logs");
        Machine.evaluate("/memory/q -> [analq::[=>]]");
        try {
            final Obj asked = Machine.evaluate("/memory/space/logs?analq -> analysis::[read=>counter::T,write=>counter::T]");
            assertFalse(asked.isFail(), "a space asks at its mount address: " + asked);
            Machine.evaluate("/logs/a -> 1");
            Machine.read(f("/logs/a"));
            Machine.read(f("/logs/b"));
            final Obj logs = Machine.read(f("/memory/space/logs?analq"));
            assertEquals(2L, logs.asRec().at(uri("read")).intValue(), "every read memory routed to the space counts at its address: " + logs);
            assertEquals(1L, logs.asRec().at(uri("write")).intValue(), "and every write: " + logs);
            final Obj spaces = Machine.read(f("/memory/space/+?analq"));
            assertEquals(2L, spaces.asRec().at(uri("logs")).asRec().at(uri("read")).intValue(), "the spaces answer at /memory/space/+: " + spaces);
            assertFalse(Machine.read(f("/memory/#?analq")).asRec().at(uri("read/count")).isNoObj(), "and memory's totals at /memory/#");
            assertInstanceOf(Analyzer.class, Machine.read(f("/memory/analyzer")), "memory's analyzer is read at its address: the one its analq holds");
            assertEquals(2L, Machine.read(f("/memory/analyzer/analysis/addr/memory/space/logs/read")).intValue(),
                    "and walked into, a space's readings at its address");
            Machine.evaluate("/memory/space/logs/q -> [analq::[=>]]");
            final Space mounted = Machine.current().memory().spaces().jvm().get(uri("logs")).as();
            assertSame(mounted.analq(), Machine.read(f("/memory/space/logs/analyzer")),
                    "a space that declares analq has its own analyzer read at its mount address");
            final Analyzer memory = (Analyzer) Machine.read(f("/memory/analyzer"));
            assertTrue(Machine.write(f("/memory/analyzer"), noobj()).isNoObj(), "an analq's analyzer is not switched off at its address");
            assertSame(memory, Machine.read(f("/memory/analyzer")), "it comes and goes with its q-proc");
            Machine.write(f("/memory/analyzer/step"), jnt(100));
            assertEquals(100L, memory.atDirect(uri("step")).intValue(), "a setting written at it lands in the analq's analyzer");
            assertNull(Machine.current().memory().analyzer(), "and installs no analyzer of memory's own beside it");
        } finally {
            Machine.evaluate("/memory/q -> noobj");
        }
    }

    /**
     * The console flow: mount a space under the memory's index, write a q-proc at its {@code q}, then use it. The
     * analysis must answer, which it cannot if the write landed on a copy of the space.
     */
    @Test
    void testAQProcAppendedAtTheMountAddressReachesTheLiveSpace() {
        Machine.evaluate("memspace::[pattern=>/dev/#]@/memory/space/dev");
        final Space dev = Machine.current().memory().spaces().jvm().get(uri("dev")).as();
        assertEquals(f("/dev/#"), dev.pattern(), "the space is indexed by its name below the mount root");
        assertEquals(0, dev.qs().lstValue().size(), "no q-procs yet");

        Machine.evaluate("/memory/space/dev/q -> [analq::[=>]]");
        assertTrue(dev.hasQ(QCollection.ANALQ_PATTERN), "the q-proc written at the mount address is on the LIVE space");

        Machine.evaluate("/dev/x -> 1");
        Machine.read(f("/dev/x"));
        final Obj analysis = Machine.read(f("/dev/x?analq"));
        assertEquals(1L, analysis.asRec().at(uri("read")).asRec().at(uri("count")).intValue(),
                "one read recorded under read/count");
        assertEquals(1L, analysis.asRec().at(uri("write")).asRec().at(uri("count")).intValue(),
                "one write recorded under write/count");
    }

    @Test
    void testAQProcAppendedAtTheMountAddressReachesTheLiveSpaceUpdate() {
        Machine.evaluate("memspace::[pattern=>/dev/#]@/memory/space/dev");
        final Space dev = Machine.current().memory().read("/memory/space/dev").as();
        assertEquals(f("/dev/#"), dev.pattern(), "the space is indexed by its name below the mount root");
        assertEquals(0, dev.qs().lstValue().size(), "no q-procs yet");

        Machine.evaluate("@/memory/space/dev/q >>= +[analq::[=>]]");
        assertTrue(dev.hasQ(QCollection.ANALQ_PATTERN), "the q-proc written at the mount address is on the LIVE space");

        Machine.evaluate("/dev/x -> 1");
        Machine.read(f("/dev/x"));
        final Obj analysis = Machine.read(f("/dev/x?analq"));
        assertEquals(1L, analysis.asRec().at(uri("read")).asRec().at(uri("count")).intValue(),
                "one read recorded under read/count");
        assertEquals(1L, analysis.asRec().at(uri("write")).asRec().at(uri("count")).intValue(),
                "one write recorded under write/count");
    }

    /**
     * One owner, not two: the object at the mount address is the space the memory indexes, so an append read back
     * through either path is the same list.
     */
    @Test
    void testTheMountAddressAnswersTheIndexedSpace() {
        Machine.evaluate("memspace::[pattern=>/dev/#]@/memory/space/dev");
        final Obj indexed = Machine.current().memory().spaces().jvm().get(uri("dev"));
        final Obj atMount = Machine.read(f("/memory/space/dev"));
        assertEquals(indexed.tid().toString(), atMount.tid().toString(), "the mount address answers the space, not a data copy");
        assertEquals(indexed.asRec().at(uri("pattern")).toString(), atMount.asRec().at(uri("pattern")).toString(),
                "the same pattern, read through the index or through the mount address");
    }

    /**
     * The machine-relative spelling ({@code ~/memory/space/<name>}) mounts at the same index entry.
     */
    @Test
    void testTheMachineRelativeSpellingMountsUnderTheMemory() {
        Machine.evaluate("memspace::[pattern=>/dev2/#]@~/memory/space/dev2");
        assertTrue(Machine.current().memory().spaces().jvm().containsKey(uri("dev2")),
                "~/memory/space/dev2 is the index entry, not a separate address");
    }

    /**
     * A NESTED mount address ({@code ~/memory/space/web/http}) registers and routes: the intermediate grouping in the
     * index must not make it unreadable (the boot files mount at nested names).
     */
    @Test
    void testANestedMountIsIndexedAndRoutable() {
        Machine.evaluate("memspace::[pattern=>/web/#]@/memory/space/web/http");
        assertTrue(Memory.spaces(Machine.current().memory().spaces()).stream()
                        .anyMatch(space -> f("/web/#").equals(space.pattern())),
                "the nested mount is in the index");
        Machine.evaluate("/web/x -> 1");
        assertEquals(1, Machine.read(f("/web/x")).intValue(), "the nested space routes its own data");
    }
}
