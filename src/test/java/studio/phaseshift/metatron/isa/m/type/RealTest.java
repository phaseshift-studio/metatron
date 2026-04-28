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

package studio.phaseshift.metatron.isa.m.type;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.algebra.AbstractAlgebraTest;

import java.util.Set;

import static studio.phaseshift.metatron.algebra.Form.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RealTest extends AbstractAlgebraTest<Real> {

    public RealTest() {
        super(real(23.5), Set.of(PLUS_MONOID, MULT_MONOID, PLUS_GROUP, MULT_GROUP, RING, RIG));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "-2.0.as(int::T)                          % -2",
            "-102.1.as(int::T)                        % -102",
            "2.0.as(int::T)                           % 2",
            "2.1.as(int::T)                           % 2",
            "2.9.as(int::T)                           % 2",
            "3.9.as(int::T)                           % 3",
    }, delimiter = '%')
    public void testAs(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "2.0.pow(4.0)                              % 16.0",
            "2.0.pow(4.0).plus(1.0)                    % 17.0",
            "2.0.pow(4.0).plus(1.0).mult(2.0)          % 34.0",
            "10.5.plus(5.5)                            % 16.0",
            "10.0.mult(2.5)                            % 25.0",
            "10.0.minus(3.5)                           % 6.5",
            "0.0.plus(0.0)                             % 0.0",
            "1.0.mult(0.0)                             % 0.0",
            "-5.5.plus(10.5)                           % 5.0",
            "-2.5.mult(-2.0)                           % 5.0",
    }, delimiter = '%')
    public void testMath(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{1.0,2.0,3.0}.mean()                                             % 2.0",
            "{1.0,0.0}.mean()                                                 % 0.5",
            "{1.1,2.2,3.3}.mean().?>2.1.lt(2.3)                               % true", // java double too wiggly
            "{1.1,2.2,3.3}.mean().and(gt(2.1),lt(2.3))                        % true", // java double too wiggly
            "{1.1,2.2,3.3}.mean().and(gt(22.1),lt(42.3))                      % false", // java double too wiggly
            "{1.1,2.2,3.3}.mean().map(and(gt(22.1),lt(42.3)))                 % false", // java double too wiggly
            "{1.1,2.2,3.3}.mean().map(and(gt(2.1),lt(2.3)))                   % true", // java double too wiggly
            //"{1.1,2.2,3.3}.mean().map(gt(2.1) & lt(2.3))                      % true", // java double too wiggly
            //"{1.1,2.2,3.3}.mean().map(gt(442.1) & lt(233.3))                  % false", // java double too wiggly
            "{1.1,2.2,3.3}.mean().and(gt(32.1),lt(52.3))                      % false", // java double too wiggly
            //"{1.1,2.2,3.3}.mean().is(gt(32.1) & lt(52.3))                     % noobj",
            "1.1.mean()                                                       % 1.1",
            "{}.mean()                                                        % 0.0",
            "[1.0,2.0,3.0]>-.mean()                                           % 2.0",
            "[1.0,2.0,3.0]-<[>-.mean(),>-.sum?real<=(),>-.prod?real<=()]      % [2.0,6.0,6.0]"
    }, delimiter = '%')
    public void testMean(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{1.1,2.2,3.3,4.4}                   %order()       %[1.1,2.2,3.3,4.4]",
            "{2.2,3.3,4.4,1.1}                   %order()       %[1.1,2.2,3.3,4.4]",
            "{4.4,2.2,3.3,1.1}                   %order()       %[1.1,2.2,3.3,4.4]",
            "{2.2,4.4,1.1,3.3}                   %order()       %[1.1,2.2,3.3,4.4]",
            "{3.3,1.1,2.2,4.4}                   %order()       %[1.1,2.2,3.3,4.4]",
            "{1.1,1.1,2.2,2.2,3.3,4.4}           %order()       %[real{2}::1.1,real{2}::2.2,3.3,4.4]",
            "{1.1,2.2,3.3,4.4,4.4,4.4,4.4}       %order()       %[1.1,2.2,3.3,real{4}::4.4]",
            "{2.2,1.1,2.2,3.3,2.2,3.3,4.4,2.2}   %order()       %[1.1,real{4}::2.2,real{2}::3.3,4.4]",
            "1.1                                 %order()       %[1.1]",
            "1.1                                 %order()       %[real{1}::1.1]",
            "real{5}::1.1                        %order()       %[real{5}::1.1]",
            "{-1.5,0.0,1.5}                      %order()       %[-1.5,0.0,1.5]",
            "{-5.5,-2.2,-10.1}                   %order()       %[-10.1,-5.5,-2.2]",
    }, delimiter = '%')
    public void testOrder(final String input, final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, input, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "5.5.gt(3.3)                         % true",
            "5.5.gt(5.5)                         % false",
            "5.5.gt(7.7)                         % false",
            "5.5.lt(3.3)                         % false",
            "5.5.lt(5.5)                         % false",
            "5.5.lt(7.7)                         % true",
            "5.5.eq(5.5)                         % true",
            "5.5.eq(3.3)                         % false",
            "0.0.eq(0.0)                         % true",
            "-5.5.lt(0.0)                        % true",
            "-5.5.gt(-10.1)                      % true",
    }, delimiter = '%')
    public void testComparison(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{1.1,2.2,3.3}.sum()                 % 6.6",
            "{10.5,20.5,30.0}.sum()              % 61.0",
            "{-5.5,5.5}.sum()                    % 0.0",
            "{1.5}.sum()                         % 1.5",
    }, delimiter = '%')
    public void testSum(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }
}
