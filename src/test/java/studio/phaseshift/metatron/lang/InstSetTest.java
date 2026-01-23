/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.lang;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.mTest;
import studio.phaseshift.metatron.lang.core.m.type.InstSet;
import studio.phaseshift.metatron.lang.core.m.type.Rel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class InstSetTest extends mTest {

    protected final InstSet space;

    public InstSetTest(final InstSet space) {
        this.space = space;
    }

    @Test
    public void testReadResult() {
        // TODO: I have no idea what this test is testing...??!
        this.space.read(space.pattern() + "/").forEach(o -> {
            assertTrue(o.isRel());
            assertTrue(o.<Rel>as().first().uriValue().matches(this.space.pattern()));
            if (o.<Rel>as().second().isInst()) {
                assertEquals(o.<Rel>as().jvm().get0().uriValue(), o.<Rel>as().jvm().get1().tid());
            }
        });
        assertTrue(this.space.read(this.space.pattern()).stream().anyMatch(o -> !o.isInst()));
    }
}
