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

package studio.phaseshift.metatron.lang.obj.mtron;

/*
@author Marko A. Rodriguez (http://markorodriguez.com)
*/

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.MetatronTest;

public class mtronInstSetTest extends MetatronTest {

    @Override
    @ParameterizedTest
    @CsvSource(value = {
            "1.plus(2)                                                              % 3      ",
            "{1,2,3}._                                                              % {1,2,3}",
            "{1,2,3}.plus(2)                                                        % {3,4,5}",
           // "{1,2,3}.plus(id?int<=int())                                            % {2,4,6}"
            // MERGE ///
            "{1,2,3}>-{,}                                                           % {1,2,3}",
            "{1,1,2,2,2,3}>-{,}                                                     % {1,1,2,2,2,3}",
            "{1,2,3}>-[,]                                                           % [1,2,3]",
            "[1=>2,2=>3,3=>4]>-.>-[=>]                                              % [1=>2,2=>3,3=>4]",
            "{1,2,3}>-noobj                                                         % {1,2,3}",
            "{1,2,3}>-[noobj]                                                       % [noobj,1,2,3]",
            "[1=>2,2=>3,3=>4]>-.>-[noobj=>noobj]                                    % [noobj=>noobj,1=>2,2=>3,3=>4]",
            "{3,4}>-[1,2]                                                           % [1,2,3,4]",
            "{1,2,3,4}>-{,}                                                         % {1,2,3,4}",
            "{1,2,2,2,3,3,4}>-{,}                                                   % {1,2,2,2,3,3,4}",
            "{3,4}>-{1,2}                                                           % {1,2,3,4}",
            "{2,3,4}>-{1,2,2}                                                       % {1,2,2,2,3,4}",
            "{1,2,3}>-1                                                             % {1,1,2,3}",
            "[a=>1,b=>2]>-.>-[=>]                                                   % [a=>1,b=>2]",
            "[b=>2]>-.>-[a=>1]                                                      % [a=>1,b=>2]",
            // SPLIT //
            "{1,2,3}-<{,}                                                           % noobj",
            "{1,2,3}-<[,]                                                           % [,]",
            "{1,2,3}-<[=>]                                                          % [=>]",
            "{1,2,3}-<noobj                                                         % noobj",
            "{1,2,3}-<[noobj]                                                       % [noobj]",
            "{1,2,3}-<[noobj=>noobj]                                                % [=>]", // TODO: should be noobj=>noobj ?
            "{1,2,3}-<1                                                             % 1",
            "{1,2,3}-<[is(gt(1))=>_, is(gt(2))=>_]                                  % [is(gt(1))=>{2,3},is(gt(2))=>3]",
            "{1,2,3}-<{is(gt(1)), is(gt(2))}                                        % {2,3}", // TODO: hmmmm
            "{1,2,3}.>-{3,3,2}                                                      % {3,3,2,3,2,1}"
    }, delimiter = '%')
    public void testCode(final String code, final String expected) {
        super.testCode(code, expected);
    }
}
