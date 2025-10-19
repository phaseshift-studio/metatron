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

package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.translate.ObjParser;
import studio.phaseshift.metatron.util.Tuple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.lang.fURI.f;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class MetatronObjTest extends MetatronTest {

    public void testTake(final String current, final String remove, final String retrieved, final String remaining) {
        final Obj currentF = ObjParser.m_obj().parse(current).get();
        final fURI removeF = f(remove);
        final Obj retrievedF = ObjParser.m_obj().parse(retrieved).get();
        final Obj remainingF = ObjParser.m_obj().parse(remaining).get();
        assertEquals(Tuple.Pair.with(retrievedF, remainingF), currentF.take(removeF.cV()));
    }
}
