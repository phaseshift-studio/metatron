/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron;

import studio.phaseshift.metatron.lang.monoid.BMonoid.Monoid;
import studio.phaseshift.metatron.lang.obj.BObj.Obj;
import studio.phaseshift.metatron.lang.obj.SObj;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public final class MetatronTest {

    private MetatronTest() {

    }

    public static void assertMEquals(final Object a, final Object b) {
        final Iterator<Obj> aa = a instanceof Monoid ? ((Monoid) a).iterator() : SObj.Obj.of(a).iterator();
        final Iterator<Obj> bb = b instanceof Monoid ? ((Monoid) b).iterator() : SObj.Obj.of(b).iterator();
        while (aa.hasNext()) {
            if (!bb.hasNext())
                fail("%s has fewer objs than %s".formatted(b, a));
            assertEquals(aa.next(), bb.next());
        }
        if (bb.hasNext()) {
            fail("%s has more objs than %s".formatted(b, a));
        }
    }
}
