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
import studio.phaseshift.metatron.TestCategory;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.WILD_ONE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * Contract for spaces with write-capable {@code incrQ} QProc — {@code _} in the address is the counter's slot, so each write lands at the next value rather than at the pattern.
 */
public interface IncrQTest extends QProcTest {

    /**
     * Base URI for incrQ test writes (e.g. {@code sqlite:incrq}).
     */
    fURI incrQBaseURI();

    /**
     * Optional hook for backend-specific setup.
     */
    default void setupIncrQ() {
    }

    /**
     * The space with {@code incrQ} attached, adding it when the backend did not.
     */
    default Space incrQSpace() {
        final Space space = this.getSpace();
        if (space.qs().lstValue().stream().noneMatch(q -> ((QProc) q).pattern().equals(INCRQ_PATTERN)))
            space.addQ(QCollection.incrQ());
        this.setupIncrQ();
        return space;
    }

    @TestCategory.Write
    @TestCategory.Read
    @Test
    default void testIncrQ() {
        final Space space = this.incrQSpace();
        final fURI base = this.incrQBaseURI();
        // a backend may hand the collection over already populated (a tble table outlives a row cleanup), so every
        // count is measured against the collection as this test found it
        final long initial = count(space, base);
        // Write poly (Rec) values — collections take polys, not monos
        space.write(base.extend(INCRQ_INCR_PATTERN).addQ(INCRQ),
                rec(mutableMap(uri("a"), jnt(12))));
        assertEquals(initial + 1, count(space, base), "each write adds exactly one entry");
        // Write 2
        space.write(base.extend(INCRQ_INCR_PATTERN).addQ(INCRQ),
                rec(mutableMap(uri("a"), jnt(13))));
        assertEquals(initial + 2, count(space, base), "and one more, at the next counter value");
    }

    /**
     * How many entries this collection holds — {@code noobj} counts as none.
     */
    private static long count(final Space space, final fURI base) {
        final Obj all = space.read(base.extend(WILD_ONE).asBranch());
        return all.isNoObj() ? 0 : all.stream().count();
    }

    /**
     * The {@code _} segment is REPLACED by the counter, and the write answers with the address it actually landed at.
     */
    @TestCategory.Write
    @Test
    default void testIncrQReplacesTheUnderscoreSegment() {
        final Space space = this.incrQSpace();
        final fURI base = this.incrQBaseURI();
        final List<fURI> vids = new ArrayList<>();
        for (int i = 1; i <= 3; i++)
            vids.add(space.write(base.extend(INCRQ_INCR_PATTERN).addQ(INCRQ),
                    rec(mutableMap(uri("n"), jnt(i)))).vid());
        // a native counter belongs to the backend (tble is SQL AUTO INCREMENT) and can outlive a row cleanup, so
        // the claim is that the three writes are CONSECUTIVE — parsing the tail also proves the `_` was consumed
        final List<Integer> counters = vids.stream().map(vid -> Integer.parseInt(vid.name())).toList();
        assertEquals(List.of(counters.get(0), counters.get(0) + 1, counters.get(0) + 2), counters,
                "each write must land at the next counter value, with `_` consumed rather than part of the address");
    }

    /**
     * A SECOND collection, distinct from {@link #incrQBaseURI()}. A backend whose incrQ is per-table — tble's is
     * SQL AUTO INCREMENT — overrides this with a sibling table, because a path BENEATH a collection is a row there.
     */
    default fURI incrQSecondCollectionURI() {
        return this.incrQBaseURI().extend("b");
    }

    /**
     * Counters are per collection: an interleaved write into another collection does not advance this one's.
     */
    @TestCategory.Write
    @Test
    default void testIncrQCountersArePerCollection() {
        final Space space = this.incrQSpace();
        final fURI a = this.incrQBaseURI().extend(INCRQ_INCR_PATTERN).addQ(INCRQ);
        final fURI b = this.incrQSecondCollectionURI().extend(INCRQ_INCR_PATTERN).addQ(INCRQ);
        // the starting value belongs to the backend (tble is SQL AUTO INCREMENT and can outlive a row cleanup),
        // so the claim is that a's own writes are CONSECUTIVE across an interleaved write into b — if the two
        // shared a counter, b's write would consume a's next value and the sequence would show a gap
        final List<Integer> aCounters = new ArrayList<>();
        aCounters.add(Integer.parseInt(space.write(a, rec(mutableMap(uri("n"), jnt(1)))).vid().name()));
        aCounters.add(Integer.parseInt(space.write(a, rec(mutableMap(uri("n"), jnt(2)))).vid().name()));
        final fURI bVID = space.write(b, rec(mutableMap(uri("n"), jnt(1)))).vid();
        aCounters.add(Integer.parseInt(space.write(a, rec(mutableMap(uri("n"), jnt(3)))).vid().name()));
        assertEquals(this.incrQSecondCollectionURI(), bVID.retract(1), "b's write lands in b's own collection");
        assertEquals(List.of(aCounters.get(0), aCounters.get(0) + 1, aCounters.get(0) + 2), aCounters,
                "an interleaved write into another collection does not advance this one's counter");
    }

    /**
     * The mtron write path reaches the same collection as the direct one.
     */
    @TestCategory.Write
    @TestCategory.Read
    @Test
    default void testIncrQThroughMtron() {
        final Space space = this.incrQSpace();
        final fURI base = this.incrQBaseURI();
        // create the collection the way the direct test does, so a schema-fixed backend (tble) has the table the
        // mtron write lands in — and count from there, since the backend owns the collection's starting state
        space.write(base.extend(INCRQ_INCR_PATTERN).addQ(INCRQ), rec(mutableMap(uri("n"), jnt(0))));
        final long before = count(space, base);
        for (int i = 1; i <= 2; i++)
            ObjmtronSerializer.parse(base + "/_?incrq -> [n=>" + i + "]").apply();
        assertEquals(before + 2, count(space, base),
                "two mtron incrq writes must land at two addresses");
    }
}
