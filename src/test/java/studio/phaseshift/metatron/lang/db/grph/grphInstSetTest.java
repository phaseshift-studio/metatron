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

package studio.phaseshift.metatron.lang.db.grph;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.lang.db.grph.inst.grphInstSet;
import studio.phaseshift.metatron.isa.SpaceTest;
import studio.phaseshift.metatron.mTest;

import static studio.phaseshift.metatron.furi.fURI.f;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Disabled
public class grphInstSetTest extends SpaceTest {

    public grphInstSetTest() {
        super(() -> mParser.parse("""
                 grph::[pattern => /g/#,
                        store   => kv::[pattern => /g/#],
                        :load   => modern]
                """));
        grphInstSet.create();
    }

    @BeforeAll
    @Disabled
    public static void begin() {
        //     MetatronTest.begin();
      
        //  MGraph.of(TinkerFactory.createModern(), f("/tp/#"), f("/sys/router/space/tp"));
    }

    
    @Disabled
    @ParameterizedTest
    @CsvSource(value = {
            //"/g -> */sys/router/space/tp                                                 % /sys/router/space/tp",
            "/g.V().count()                                                         % 6",
            "/g.E().count()                                                         % 6",
            "/g.V().outE().count()                                                  % 6",
            "/g.V().out().count()                                                   % 6",
            "/g.V().in().count()                                                    % 6",
            "/g.V().out().out().count()                                             % 2",
            "/g.V().out().out().out().count()                                       % 0",
            "/g.V().values(name)                                                    % {'marko','josh','peter','vadas','lop','ripple'}",
            "/g.V().values(age).count()                                             % 4",
            "/g.V().values(age).sum?int<=int{*}()                                   % 123",
            // dummy without ending comma so it's easier to add more test cases
            "1.plus(1)                                                                  % 2"
    }, delimiter = '%')
    public void testCode(final String code, final String expected) {
   mTest.testCode(LOG,code,expected);
    }
}
