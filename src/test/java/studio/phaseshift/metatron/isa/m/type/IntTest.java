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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.algebra.AbstractAlgebraTest;

import java.util.Set;

import static studio.phaseshift.metatron.algebra.Form.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

@Disabled
public class IntTest extends AbstractAlgebraTest<Int> {

    public IntTest() {
        super(jnt(23), Set.of(PLUS_MONOID, MULT_MONOID, PLUS_GROUP, RING, RIG));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "1.as(str::T)                                                                   % \"1\"",
            "2.as(str::T)                                                                   % \"2\"",
            "1.as(real::T)                                                                  % 1.0",
            "2.as(real::T)                                                                  % 2.0"
    }, delimiter = '%')
    public void testAsInst(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            // a                                 % b                            % matches
            "1                                   % 1                            % true",
            "1                                   % int::T[]                     % true",
            "1                                   % str::T[]                     % false",
            "1                                   % int{0,4}::T[]                % true",
            "int{5}::1                           % int{0,4}::T[]                % false",
            "int{5}::1                           % int{*}::T[]                  % true",
            "int{0}::1                           % noobj[0}::T[]                % true",
            "int{**}::1                          % int{**}::T[]                 % true",
            "int{-1}::1                          % int{**}::T[]                 % true",
            "int{-1}::1                          % int{0,1}::T[]                % false",
            "int{-1}::1                          % int{-1,1}::T[]               % true"
    }, delimiter = '%')
    public void testMatches(final String lhs, final String rhs, final boolean matches) {
        AbstractMetatronTest.checkMatches(LOG, lhs, rhs, matches);
    }

    @ParameterizedTest
    @CsvSource(value = {
            // a                                 % b                            % matches
            "1                                   % plus(2)                      % 3",
            "1                                   % plus(mult(10))               % 11",
            "1                                   % gt(0)                        % true",
            "1                                   % is(gt(0))                    % 1",
            "1                                   % matches(int::T[])            % true",
            "1                                   % is(matches(int::T[]))        % 1",
            "1                                   % =~(str::T[])                 % false",
            "1                                   % ?=~str::T[]                  % noobj",
            "int{-1}::1                          % is(matches(int{**}::T[]))    % int{-1}::1",
            "int{-1}::1                          % ?=~int{,}::T[]               % int{-1}::1"
    }, delimiter = '%')
    public void testCode(final String lhs, final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, lhs, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "2.pow(4)                          % 16",
            "2.pow(4).plus(1)                  % 17",
            "2.pow(4).plus(1).mult(2)          % 34",
            "10.plus(5)                        % 15",
            "10.mult(5)                        % 50",
            "10.minus(3)                       % 7",
            "0.plus(0)                         % 0",
            "1.mult(0)                         % 0",
            "0.mult(100)                       % 0",
            "-5.plus(10)                       % 5",
            "-5.mult(-2)                       % 10",
    }, delimiter = '%')
    public void testMath(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "bool::1                                                      % <ERROR>",
            "real::1                                                      % <ERROR>",
            "str::1                                                       % <ERROR>",
            "uri::1                                                       % <ERROR>",
            "lst::1                                                       % <ERROR>",
            "rec::1                                                       % <ERROR>",
            "inst::1                                                      % <ERROR>",
            // "code::1                                                    % <ERROR>",
            "3.plus(mult(2))                                              % 9",
            "{2,3}>-.plus(mult(2))                                        % {6,9}",
            "{2,3}.plus(mult(2))                                          % {6,9}",
            //"{2,3}.is?int{*}<=int{*}(in?bool{+}<=int{*}(int{2}::T[]))   % {2,3}",
            "{1,2,3}.plus(1).plus(2)                                      % {4,5,6}",
            "{1,2,3}.plus(1).plus(2).mult(2)                              % {8,10,12}",
            "{1,2,3}.plus(1).plus(2).mult(2).isa(int::T)                  % {8,10,12}",
            "{1,2,3}.plus(1).plus(2).mult(2).?=~str::T                    % noobj",
            "{int{-1}::1,int::1}                                          % noobj",
            // "start?int{-1,1}<=int{0}(int{-1}::1)>-{int::1}             % noobj"
    }, delimiter = '%')
    public void testBasic(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "int{10}::1         %int{5}       %int{5}::1          %int{5}::1",
            "int{10}::1         %int{3}       %int{3}::1          %int{7}::1",
            "int{4,10}::1       %int{3}       %int{3}::1          %int{1,7}::1",
            "int{10}::1         %int{10}      %int{10}::1         %int{0}::1",
            "int{10}::1         %int{10}      %int{10}::1         %noobj",
            "int{10}::1         %int{11}      %int{11}::1         %int{-1}::1",
            "int{0}::1          %int{0}       %int{0}::1          %int{0}::1",
            "int{0}::1          %int{10}      %int{10}::1         %int{-10}::1",
            "int{0}::1          %int{-10}      %int{-10}::1         %int{10}::1",
            "int{10}::1         %int{0}       %int{0}::1          %int{10}::1",
            "int{10}::1         %int{-5}      %int{-5}::1         %int{15}::1",
            "int{-10}::1        %int{-5}      %int{-5}::1         %int{-5}::1",
            "int{10,}::1        %int{10,}     %int{10,}::1        %int{0}::1",
            "int{10,}::1        %int{1,}      %int{1,}::1         %noobj",
            "noobj,             %int{10}      %noobj              %noobj",
            "int{,10}::1        %int{,10}     %int{,10}::1        %noobj",
            "int{,10}::1        %int{1}       %int::1             %int{,9}::1",
            "int{1,10}::1       %int{1}       %int::1             %int{0,9}::1",
            "int{-10,-1}::1     %int{1}       %int::1             %int{-11,-2}::1",
            "int{-10,-1}::1     %int{-1}      %int{-1}::1         %int{-9,0}::1",
            "int{-10,-10}::1    %int{10}      %int{10}::1         %int{-20,-20}::1",
            "int{-10,-8}::1     %int{-5}      %int{-5}::1         %int{-5,-3}::1",
            "int{-10,8}::1      %int{-5}      %int{-5}::1         %int{-5,13}::1",
            "int{-10}::1        %int{-10}     %int{-10}::1        %noobj",
    }, delimiter = '%')
    public void testTake(final String current, final String remove, final String retrieved, final String remaining) {
        super.testTake(current, remove, retrieved, remaining);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4}         %order()       %[1,2,3,4]",
            "{2,3,4,1}         %order()       %[1,2,3,4]",
            "{4,2,3,1}         %order()       %[1,2,3,4]",
            "{2,4,1,3}         %order()       %[1,2,3,4]",
            "{3,1,2,4}         %order()       %[1,2,3,4]",
            "{1,1,2,2,3,4}     %order()       %[int{2}::1,int{2}::2,3,4]",
            "{1,2,3,4,4,4,4}   %order()       %[1,2,3,int{4}::4]",
            "{2,1,2,3,2,3,4,2} %order()       %[1,int{4}::2,int{2}::3,4]",
            "1                 %order()       %[1]",
            "int{5}::1         %order()       %[int{5}::1]",
            "{-1,0,1}          %order()       %[-1,0,1]",
            "{-5,-2,-10}       %order()       %[-10,-5,-2]",
    }, delimiter = '%')
    public void testOrder(final String input, final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, input, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "5.gt(3)                           % true",
            "5.gt(5)                           % false",
            "5.gt(7)                           % false",
            "5.lt(3)                           % false",
            "5.lt(5)                           % false",
            "5.lt(7)                           % true",
            "5.eq(5)                           % true",
            "5.eq(3)                           % false",
            "5.gte(5)                          % true",
            "5.gte(3)                          % true",
            "5.gte(7)                          % false",
            "5.lte(5)                          % true",
            "5.lte(7)                          % true",
            "5.lte(3)                          % false",
            "0.eq(0)                           % true",
            "-5.lt(0)                          % true",
            "-5.gt(-10)                        % true",
    }, delimiter = '%')
    public void testComparison(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3}.sum()                     % 6",
            "{10,20,30}.sum()                  % 60",
            "{-5,5}.sum()                      % 0",
            "{1}.sum()                         % 1",
            "{}.sum?int<=int{*}(0)             % 0",
    }, delimiter = '%')
    public void testSum(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }
}
