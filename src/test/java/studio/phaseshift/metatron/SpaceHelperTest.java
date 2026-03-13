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

package studio.phaseshift.metatron;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;

import static org.junit.Assert.*;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SpaceHelperTest extends AbstractMetatronTest {
    
    @Test
    public void testLocateBasePoly() {
        // a -> [b => [c => d]]
        memSpace space = memSpace.of(rec(uri(PATTERN), uri("/test")), f("/sys/space/test"));
        space.directWriter().apply(f("/test/a"), rec(uri("b"),rec(uri("c"),str("d"))));
        Space.IdObj base = Space.Helper.locateBasePoly(space,f("/test/a/b/c/d"));
        assertNotNull(base);
        assertEquals(f("/test/a/b"), base.furi());
        assertEquals(rec(uri("c"),str("d")), base.obj());
        /// //
        base = Space.Helper.locateBasePoly(space,f("/test/a/b/c"));
        assertNotNull(base);
        assertEquals(f("/test/a/b"), base.furi());
        assertEquals(rec(uri("c"),str("d")), base.obj());
        /// //
        base = Space.Helper.locateBasePoly(space,f("/test/a/b/c/"));
        assertNotNull(base);
        assertEquals(f("/test/a/b"), base.furi());
        assertEquals(rec(uri("c"),str("d")), base.obj());
        /// //
        base = Space.Helper.locateBasePoly(space,f("/test/a/b"));
        assertNotNull(base);
        assertEquals(f("/test/a"), base.furi());
        assertEquals(rec(uri("b"),rec(uri("c"),str("d"))), base.obj());
        /// //
        base = Space.Helper.locateBasePoly(space,f("/test/a"));
        assertNull(base);
    }
    
}
