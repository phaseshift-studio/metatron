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

package studio.phaseshift.metatron.lang.mach;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.MetatronTest;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class machInstSetTest extends MetatronTest {

    @Override
    @ParameterizedTest
    @CsvSource(value = {
            "1.drop().project(2)                                                                         % [=>]",
            "1.drop().project(1).in(*drop)                                                               % true",
            "1.drop().project(0)                                                                         % 1",
            "1.drop().inject(2,[loop=>0]).project(2)                                                     % [loop=>0]",
            "1.drop().inject(2,[loop=>0]).inject(2,project(2)==[loop=>plus(1)]).project(2)               % [loop=>1]",
            // dummy without ending comma so it's easier to add more test cases
            "1.plus(1)                                                                                   % 2"
    }, delimiter = '%')
    public void testCode(final String code, final String expected) {
        super.testCode(code, expected);
    }


    @ParameterizedTest
    @CsvSource(value = {
            "1.zero()                                                                                    % 0",
            "1.one()                                                                                     % 1",
            // "a/b/c.zero()                                                                                % < >",
            "a/b/c.one()                                                                                 % <.>",
            "[1=>2,2=>3].zero()                                                                          % [=>]",
            "[1,2,3].zero()                                                                              % [,]",
            "[1=>2,2=>3].zero()                                                                          % [=>]",
            "1.one()                                                                                     % 1",
            "1.23.one()                                                                                  % 1.0",
            "1.23.mult(one())                                                                            % 1.23",
            "{1,1.23,[1=>2,3=>4],[1,2,3]}.zero()                                                         % {0,0.0,[=>],[,]}",
            "{1,1.23,[1=>2,3=>4],[1,2,3]}.plus(zero())                                                   % {1,1.23,[1=>2,3=>4],[1,2,3]}",
           // "{1,1.23,[1=>2,3=>4],[1,2,3]}.mult(zero())                                                   % {1,1.23,[1=>2,3=>4],[1,2,3]}",
            "{1,1.23}.mult(one())                                                                        % {1,1.23}",
            //"{1,1.23,[1=>2,3=>4],[1,2,3]}.</mach/inst/ring/op/plus>(zero())                              % {1,1.23,[1=>2,3=>4],[1,2,3]}",
            //"{1,1.23,a/b/c}.</mach/inst/ring/op/mult>(one())                                             % {1,1.23,a/b/c}",
            //"{1,2,3,4,5}.reduce(|</mach/inst/ring/op/plus>(0))                                       % 15",
            "1.drop().project(0)                                                                         % 1",
            "1.drop().inject(2,[loop=>0]).project(2)                                                     % [loop=>0]",
            "1.drop().inject(2,[loop=>0]).inject(2,project(2)==[loop=>plus(1)]).project(2)               % [loop=>1]",
            // dummy without ending comma so it's easier to add more test cases
            "1.plus(1)                                                                                   % 2"
    }, delimiter = '%')
    public void testRingAlgebra(final String code, final String expected) {
        super.testCode(code, expected);
    }
}
