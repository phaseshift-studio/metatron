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

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.WILD_ONE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * Contract for spaces with write-capable {@code incrQ} QProc.
 * Uses direct {@code space.write()}/{@code space.read()} — no mtron.
 */
public interface IncrQTest {

    Space getSpace();

    /**
     * Optional hook for backend-specific setup.
     */
    default void setupIncrQ() {
    }

    /**
     * Base URI for incrQ test writes (e.g. {@code sqlite:incrq}).
     */
    fURI incrQBaseURI();

    @TestCategory.Crud
    @Test
    default void testIncrQ() {
        final Space space = getSpace();
        if (space.qs().lstValue().stream().noneMatch(q -> ((QProc) q).pattern().equals(INCRQ_PATTERN)))
            space.addQ(QCollection.incrQ());
        setupIncrQ();
        final fURI base = incrQBaseURI();
        // Write 1: value 12
        space.write(base.extend(INCRQ_INCR_PATTERN).addQ("incrq"), jnt(12));
        final Obj all1 = space.read(base.extend(WILD_ONE).asBranch());
        assertFalse(all1.isNoObj(), "read returned noobj after write 1");
        final long count1 = all1.stream().count();
        assertEquals(1, count1, "should have 1 entry after first write");
        // Write 2: value 13
        space.write(base.extend(INCRQ_INCR_PATTERN).addQ("incrq"), jnt(13));
        final Obj all2 = space.read(base.extend(WILD_ONE).asBranch());
        assertFalse(all2.isNoObj(), "read returned noobj after write 2");
        final long count2 = all2.stream().count();
        assertEquals(2, count2, "should have 2 entries after second write");
    }
}
