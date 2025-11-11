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

package studio.phaseshift.metatron.lang.sys.router;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.db.kv.kvSpace;
import studio.phaseshift.metatron.lang.core.m.obj.NoObj;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MRouterTest extends RouterTest {

    @BeforeAll
    public static void begin() {

    }

    @Test
    public void testCloseSpace() {
        // BootLoader.ROUTER = new MRouter(f("ws://localhost:8866"),f("/sys/router"));
        kvSpace mnt = kvSpace.of(f("/mnt/#"), fURI.NULL).vid(f("/mnt")).as();
        assertFalse(Router.global().hasSpaceFor(f("/test/a")));
        kvSpace test = kvSpace.of(f("/test/#"), fURI.NULL).vid(f("/mnt/test")).as();
        assertTrue(Router.global().hasSpaceFor(f("/test/a")));
        assertTrue(Router.global().hasSpaceFor(f("/test/a")));
        Router.global().write("/test/a", jnt(10));
        assertEquals(jnt(10), Router.global().read("/test/a"));
        assertTrue(Router.global().hasSpaceFor(f("/test/a")));
        Router.global().write("/mnt/test", NoObj.noobj());
        // TODO::: should close on writing to /mnt/test noobj ... something around cloning I suspect
        test.close();
        assertFalse(Router.global().hasSpaceFor(f("/test/a")));
    }
}
