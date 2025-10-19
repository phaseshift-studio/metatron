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

package studio.phaseshift.metatron.lang.obj.mgrph;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.space.Router;

import static studio.phaseshift.metatron.lang.obj.mtron.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mgrphInstSetTest extends MetatronTest {

    @BeforeAll
    public static void begin() {
        MetatronTest.begin();
        Router.global().write("g", uri("/mnt/tp"));
    }


    @Override
    @ParameterizedTest
    @CsvSource(value = {
            "*(*g).V().count()                                                         % 6",
            "*(*g).E().count()                                                         % 6",
            "*(*g).V().values(name)                                                    % {'marko','josh','peter','vadas','lop','ripple'}",
            "*(*g).V().values(age).count()                                             % 4",
            // dummy without ending comma so it's easier to add more test cases
            "1.plus(1)                                                                  % 2"
    }, delimiter = '%')
    public void testCode(final String code, final String expected) {
        super.testCode(code, expected);
    }
}
