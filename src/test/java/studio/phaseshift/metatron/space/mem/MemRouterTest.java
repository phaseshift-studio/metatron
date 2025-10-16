/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.space.mem;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.space.RouterTest;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mtron.MInt.jnt;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MemRouterTest extends RouterTest {

    @BeforeAll
    public static void setup() {

    }

    @Test
    public void testCloseSpace() {
        MemSpace mnt = new MemSpace(f("/mnt/#"), f("/mnt"));
        MemRouter router = new MemRouter(f("/mnt/sys/router"));
        router.addSpace(mnt);
        assertFalse(router.hasSpaceFor(f("/test/a")));
        MemSpace test = new MemSpace(f("/test/#"), f("/mnt/test"));
        assertFalse(router.hasSpaceFor(f("/test/a")));
        router.addSpace(test);
        assertTrue(router.hasSpaceFor(f("/test/a")));
        router.write("/test/a", jnt(10));
        assertEquals(jnt(10), router.read("/test/a"));
        assertTrue(router.hasSpaceFor(f("/test/a")));
        router.write("/mnt/test", NoObj.single());
        assertFalse(router.hasSpaceFor(f("/test/a")));
    }
}
