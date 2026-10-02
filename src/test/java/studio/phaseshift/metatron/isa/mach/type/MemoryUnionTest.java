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

package studio.phaseshift.metatron.isa.mach.type;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicMemory;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.mInstSet.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The frame's memory: a Rec whose relative bindings read through to the one it inherited, and which {@code >>}
 * navigates across the boundary because {@code Rec.Helper.rshiftRec} is written against {@code at}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MemoryUnionTest extends AbstractMetatronTest {

    private static Obj eval(final String code) {
        return ObjmtronSerializer.parse(code).apply();
    }

    /** one level of memory, from plain bindings — memory is a Rec, so a level is bindings plus an index */
    private static Memory mem(final Rec bindings) {
        return new BasicMemory(bindings.jvm(), null);
    }

    /**
     * navigation reads through to the inherited subtree — a path only the previous level holds resolves via the
     * frame.
     */
    @Test
    public void testNavigationFallsThroughToThePrevious() {
        final MemoryUnion memory = new MemoryUnion(
                mem(rec(uri("a"), rec(uri("b"), rec(uri("c"), jnt(1))))),
                mem(rec()));
        assertEquals(1, memory.at(uri("a/b/c")).intValue(),
                "a path only the previous level supplies must resolve through the frame");
    }

    /**
     * a path only the frame holds resolves in the frame.
     */
    @Test
    public void testNavigationResolvesInTheFrame() {
        final MemoryUnion memory = new MemoryUnion(
                mem(rec(uri("a"), rec(uri("b"), rec(uri("c"), jnt(1))))),
                mem(rec(uri("b"), rec(uri("d"), jnt(2)))));
        assertEquals(2, memory.at(uri("b/d")).intValue(),
                "a path only the frame supplies must resolve");
    }

    /**
     * <b>The union composes at the top level, not per segment.</b>
     * <p>
     * {@code at} receives a whole path, so it is asked <em>once</em> with {@code a/b/d}: the frame does not hold
     * {@code a} and the previous level does not hold {@code d}, and neither side is consulted mid-path — so a path
     * that stitches a previous prefix to a frame suffix does not resolve. Asserted so the boundary is explicit
     * rather than discovered later.
     * <p>
     * Making it compose would mean {@link MemoryUnion#at} splitting the path and walking segment by segment
     * through itself, alternating sides. That is the upgrade path if a frame ever needs to graft a branch under a
     * path the previous level owns; nothing needs it yet.
     */
    @Test
    public void testAMixedPathDoesNotComposeAcrossTheBoundary() {
        final MemoryUnion memory = new MemoryUnion(
                mem(rec(uri("a"), rec(uri("b"), rec(uri("c"), jnt(1))))),
                mem(rec(uri("b"), rec(uri("d"), jnt(2)))));

        assertTrue(memory.at(uri("a/b/d")).isNoObj(),
                "the previous level's prefix and the frame's suffix are not stitched together");
    }

    /**
     * an exact key is <b>shadowed</b>: the frame's binding wins outright
     */
    @Test
    public void testExactKeyIsShadowedByTheFrame() {
        final MemoryUnion memory = new MemoryUnion(mem(rec(uri("a"), jnt(1))), mem(rec(uri("a"), jnt(9))));
        assertEquals(9, memory.at(uri("a")).intValue(), "the frame's binding shadows the previous level's");
    }

    /**
     * A <b>pattern</b> is not shadowed — it is a question, and both sides know part of the answer. Answering from
     * one side only would silently lose half the result, so the union merges: the previous level holds
     * {@code a/x=1} and the frame holds {@code a/y=2}, and {@code a/+} answers with both values.
     */
    @Test
    public void testPatternMergesBothSidesRatherThanShadowing() {
        final MemoryUnion memory = new MemoryUnion(
                mem(rec(uri("a"), rec(uri("x"), jnt(1)))),
                mem(rec(uri("a"), rec(uri("y"), jnt(2)))));
        final Obj matches = memory.at(uri("a/+"));
        assertFalse(matches.isNoObj(), "a pattern both sides match must answer with something");
        assertEquals(2, matches.stream().count(), "both sides' matches belong to the answer: " + matches);
        assertTrue(matches.stream().anyMatch(o -> o.intValue() == 1), "the previous match survives: " + matches);
        assertTrue(matches.stream().anyMatch(o -> o.intValue() == 2), "and so does the frame's: " + matches);
    }

    /**
     * A pattern queries the <b>entire chain</b>, not just the top two frames — and it does so for free, because
     * {@code previous()} may itself be a union, so the pattern branch recurses by virtual dispatch. Contrast an
     * exact key, which stops at the first frame that has it.
     */
    @Test
    public void testAPatternQueriesTheEntireChain() {
        final Rec base = rec(uri("a"), rec(uri("x"), jnt(1)));
        final Rec mid = rec(uri("a"), rec(uri("y"), jnt(2)));
        final Rec top = rec(uri("a"), rec(uri("z"), jnt(3)));
        // three frames deep: top wraps mid wraps base
        final MemoryUnion memory = new MemoryUnion(new MemoryUnion(mem(base), mem(mid)), mem(top));

        final Obj matches = memory.at(uri("a/+"));
        assertEquals(3, matches.stream().count(),
                "every frame in the chain that matches must contribute: " + matches);
        for (final int value : new int[]{1, 2, 3})
            assertTrue(matches.stream().anyMatch(o -> o.intValue() == value),
                    "the match from each frame must survive, missing " + value + ": " + matches);
    }

    /**
     * a frame writes only what it introduced
     */
    @Test
    public void testWriteLandsInTheFrameOnly() {
        final Memory previous = mem(rec());
        final Memory current = mem(rec());
        final MemoryUnion memory = new MemoryUnion(previous, current);
        memory.at(uri("n"), jnt(7), MUTABLE);

        assertEquals(7, memory.at(uri("n")).intValue(), "the frame's own write is visible to it");
        assertTrue(previous.at(uri("n")).isNoObj(), "and it must not have reached the previous level");
    }

    /**
     * the property everything depends on: a frame releases what it opened and leaves what it inherited alone.
     * Uses a plainly-closable memory stand-in so the reach of {@code close()} is observable.
     */
    @Test
    public void testCloseReleasesOnlyWhatTheFrameOwns() {
        final Tracked previous = new Tracked();
        final Tracked current = new Tracked();
        final MemoryUnion memory = new MemoryUnion(previous, current);

        memory.close();

        assertTrue(current.closed, "the frame's own memory must be released");
        assertFalse(previous.closed, "an inherited memory must never be released by the frame that borrowed it");
    }

    /** a memory that records that it was closed */
    private static final class Tracked extends BasicMemory implements AutoCloseable {
        private boolean closed = false;

        private Tracked() {
            super();
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }
}
