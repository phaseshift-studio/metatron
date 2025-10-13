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
            "1.plus?int{?}<=int(int{0}::1)                                          % noobj",
            "1.plus(_)                                                              % 2",
            "1.plus(2)                                                              % 3",
            "{1,2,3}._                                                              % {1,2,3}",
            "{1,2,3}.plus(2)                                                        % {3,4,5}",
            "{int{10}::1}.plus(_)                                                   % int{10}::2",
            "int{10}::2.plus(_)                                                     % int{10}::4",
            "{1,2,3}>-._.plus(_)                                                    % {2,4,6}",
            "{1,2,3}._.plus(_)                                                      % {2,4,6}",
            "{1,2,3}.plus(id())                                                     % {2,4,6}",
            "{1,2,3}.plus(mult(1))                                                  % {2,4,6}",
            "{1,2,3}.plus(plus(1))                                                  % {3,5,7}",
            "{1,2,3}.plus(plus(map(1)))                                             % {3,5,7}",
            "{1,2,3}.plus(plus(_))                                                  % {3,6,9}",
            "{1,2,3}.plus(plus(mult(1)))                                            % {3,6,9}",
            // MERGE ///
            "{1,2,3}>-                                                              % {1,2,3}",
            "{1,1,2,2,2,3}>-                                                        % {1,1,2,2,2,3}",
            "{1,2,3}>-[,]                                                           % [1,2,3]",
            "[1=>2,2=>3,3=>4]>-[=>]                                                 % [1=>2,2=>3,3=>4]",
            "[1=>2,2=>3,3=>4]>-.>-[=>]                                              % [1=>2,2=>3,3=>4]",
            //"{1,2,3}>-noobj                                                         % {1,2,3}",
            "{1,2,3}>-[noobj]                                                       % [1,2,3,noobj]",
            // "[1=>2,2=>3,3=>4]>-                                                     % {1=>2,2=>3,3=>4}",
            "[1=>2,2=>3,3=>4]>-.>-[noobj=>noobj]                                    % [1=>2,2=>3,3=>4]",
            "[z=>c,2=>3,a=>f]>-.>-[noobj=>noobj]                                    % [z=>c,2=>3,a=>f]",
            "{1,2}>-[3,4]                                                           % [1,2,3,4]",
            //"{1,2,3,4}>-{,}                                                         % {1,2,3,4}",
            //"{1,2,2,2,3,3,4}>-{,}                                                   % {1,2,2,2,3,3,4}",
            "{3,4}>-{1,2}                                                           % {1,2,3,4}",
            "{2,3,4}>-{1,2,2}                                                       % {1,2,2,2,3,4}",
            "{1,2,3}>-1                                                             % {1,1,2,3}",
            "[a=>1,b=>2]>-.>-[=>]                                                   % [a=>1,b=>2]",
            "[a=>1]>-.>-[b=>2]                                                      % [a=>1,b=>2]",
            "[b=>2]>-.>-[a=>1]                                                      % [b=>2,a=>1]",
            // SPLIT //
            "{1,2,3}-<{,}                                                           % noobj",
            "{1,2,3}-<?lst<=int{*}([,])                                             % [,]",
            "{1,2,3}-<?lst{*}<=int([_])                                             % {[1],[2],[3]}",
            "{1,2,3}-<?lst<=int{*}([_])                                             % [{1,2,3}]",
            //"{1,2,3}-<?rec<=int{*}([=>])                                        % [=>]",
            "{1,2,3}-<noobj                                                         % noobj",
            "{1,2,3}-<[noobj]                                                       % [noobj]",
            "{1,2,3}-<[noobj=>noobj]                                                % [=>]",
            //"{1,2,3}-<?int<=int{*}(1)                                             % 1",
            "{1,2,3}-<1                                                             % 1",
            "{1,2,3}-<[is(gt(1))=>_, is(gt(2))=>_]                                % [is(gt(1))=>{2,3},is(gt(2))=>3]",
            "{1,2,3}-<{is(gt(1)), is(gt(2))}                                        % {2,3,3}",
           // "{1,2,3}.split?int{*}<=int({is(gt(1)), is(gt(2))})                        % {2,3,3}",
            //"{1,2,3}-<?int[0,2]<=int([is(gt(1)), is(gt(2))])                      % {{2},[3],[3]}",
            "{1,2,3}.>-{3,3,2}                                                      % {3,3,2,3,2,1}",
            "{1,2,3}.>-{3,3,2}                                                      % {int{1}::1,int{2}::2,int{3}::3}",
            // MULT //
            "{1,2,3}.mult(10)                                                       % {int{1}::10,int{1}::20,int{1}::30}",
            "{int{2}::1,int{3}::2,int{4}::3}.mult(10)                               % {int{2}::10,int{3}::20,int{4}::30}",
            "int{50}::10.mult(10)                                                   % int{50}::100",
            // COUNT/SUM //
            "{1,2,3,4}.id{5}().count()                                              % 20",
            "{1,2,3,4}.id{3}().count()                                              % 12",
            "{1,2,3,4}.is(gt(5)).count()                                            % 0",
            "{1,2,3,4}.count()                                                      % 4",
            "{1,2,3,4}.count{2}().sum()                                             % 8",
            "{1,2,3,4}.sum{2}()                                                     % int{2}::10",
            "{1,2,3,4}.sum{2}().count()                                             % 2",
            "{1,2,3,4}-<{count(),count()}                                           % int{2}::4",
            "{1,2,3,4}.is(gt(2)).count()                                            % 2",
            "int{50}::10.mult(10).count()                                           % int{1}::50",
            "int{50}::10.mult(10).sum()                                             % 5000",
            "int{50}::10-<{mult(10),mult(1)}                                        % {int{50}::100,int{50}::10}",
            "int{50}::10-<{mult(10),mult(1)}.count()                                % 100",
            "int{50}::10-<{mult(10),mult(1)}.sum()                                  % 5500",
            "{int{50}::10}-<{mult(10),mult(1)}                                      % {int{50}::100,int{50}::10}",
            "{int{50}::10}-<{mult(10),mult(1)}.count()                              % 100",
            "{int{50}::10}-<{mult(10),mult(1)}.sum()                                % 5500",
            "{int{50}::10,int{10}::5}-<{mult(10),mult(1)}                           % {int{50}::100,int{10}::50,int{50}::10,int{10}::5}",
            "{int{50}::10,int{10}::5}-<{mult(10),mult(1)}.count()                   % 120",
            "{int{50}::10,int{10}::5}-<{mult(10),mult(1)}.sum()                     % 6050",
            "{int{50}::10,int{10}::5}-<{mult(10),mult(1)}.sum{2}().sum()            % 12100",
            "{10,5}-<{mult{3}(10),mult(1)}.sum{2}().sum()                           % 930",
            "{[1,2],[3,4,5],[6,7,8]}.sum()                                          % [1,2,3,4,5,6,7,8]",
            "{[1,2],[3,4,5],[6,7,8]}.sum().>-                                       % {1,2,3,4,5,6,7,8}",
            "{[1,2],[3,4,5],[6,7,8]}.sum().>-.count()                               % 8",
            //"{[1,2],[3,4,5],[6,7,8]}.sum()._/sum()\\_>-                                % 36",
            "{[1,2],[3,4,5],[6,7,8]}>-[,]                                           % [[1,2],[3,4,5],[6,7,8]]",
            // dummy without ending comma so it's easier to add more test cases
            "1.plus(1)                                                              % 2"
    }, delimiter = '%')
    public void testCode(final String code, final String expected) {
        super.testCode(code, expected);
    }
}
