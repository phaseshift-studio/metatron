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

package studio.phaseshift.metatron.lang.parse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.lang.obj.Call;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.mtron.MInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.lang.parse.ObjParser.eval;

public class InstParseTest {

    @BeforeAll
    public static void begin() {
        BootLoader.load();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "|(/usr/abc?int<=int(){ map(6) }).to(/usr/abc)                       % 2./usr/abc()                   % 6",
            "|(/usr/abc?int<=int(){ plus(_) }).to(/usr/abc)                      % 2./usr/abc()                   % 4",
            "|(/usr/abc?int<=int(a=>int::T[]){ mult(*a) }).to(/usr/abc)          % 2./usr/abc(a=>4)               % 8",
            "|(/usr/abc?int<=int(a=>int::T[]){ mult(*a) }).to(/usr/abc)          % 2./usr/abc(a=>/usr/abc(4))     % 16",
            "|(/usr/abc?int<=int(a=>isa(int::T[])){ mult(*a) }).to(/usr/abc)     % 2./usr/abc(a=>4)               % 8",
            "|(/usr/abc?int<=int(a=>else(10)){ mult(*a) }).to(/usr/abc)          % 2./usr/abc()                   % 20",
            "|(/usr/abc?int<=int(a=>else(10)){ mult(*a) }).to(/usr/abc)          % 2./usr/abc(a=>noobj)           % 20",
            "|(/usr/abc?int<=int(a=>int::T[]){ mult(*a) }).to(/usr/abc)          % 2./usr/abc(4)                  % 8",
            "|(/usr/abc?int<=int(a=>int::T[]){ mult(*a) }).to(/usr/abc)          % 2./usr/abc(plus(10))           % 24",
            "|(/usr/abc?int<=int(int::T[]){ mult(*a0) }).to(/usr/abc)            % 2./usr/abc(10)                 % 20",
            "|(/usr/abc?int<=int(int::T[]){ mult(*a0) }).to(/usr/abc)            % 2./usr/abc(10)                 % 20",
            "|(/usr/abc?int<=int(int::T[]){ mult(*a0) }).to(/usr/abc)            % 2./usr/abc(plus(10))           % 24",
            //"/mtron/code[plus(1).plus(2)].plus([d,e,f])% [a,b,c,d,e,f]" (requires union())
    }, delimiter = '%')
    void testInstDefinitions(final String definition, final String usage, final String expected) {
        Call def = ObjParser.<Call>eval(definition).next();
        Obj use = ObjParser.eval(usage).next();
        Obj exp = ObjParser.m_obj().parse(expected).get();
        assertEquals(exp, use);
        //assertEquals(inst, ObjParser.eval(expression).next());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "true.plus(false)% true",
            "false.plus(false)% false",
            "0.plus(0)% 0",
            "1.plus(2)% 3",
            "3.plus(-3)% 0",
            "{1,2,3}.plus(10).sum()% 36",
           // "{1,2,3}.plus(mult(10)).sum()% 55",
            "int[4]::10.plus(20)% int[4]::30",
           // "int[4]::10.plus(mult(20))% int[4]::210",
            "int[4]::10.plus(mult?int[+]<=int[+](20))% int[4]::210",
            "\"abc\".plus(\"def\")% \"abcdef\"",
            "abc[0,2].plus(abc[23])% abc[23,25]",
            "[a,b,c].plus([d,e,f])% [a,b,c,d,e,f]",
            //"/mtron/code[plus(1).plus(2)].plus([d,e,f])% [a,b,c,d,e,f]" (requires union())
    }, delimiter = '%')
    void testPlusInst(final String expression, final String expectedResult) {
        assertEquals(ObjParser.m_obj().parse(expectedResult).get(), ObjParser.eval(expression).next());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3}.count()                                % 3",
            "{1,int[10]::2,3}.count()                       % 12",
            "5.count()                                      % 1",
            "5.plus(count())                                % 6",
            "{5,-5}.count()                                 % 2",
            "{int[10]::1}.count()                           % 10",
            //"{1,2,3}.plus(sum())-|id()                   % {2,4,6}"
    }, delimiter = '%')
    public void testCountInst(final String expression, final String expectedResult) {
        assertEquals(ObjParser.m_obj().parse(expectedResult).get(), ObjParser.eval(expression).next());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3}.sum()                                % 6",
            "{1,int[10]::2,3}.sum()                       % 24",
            "5.sum()                                      % 5",
            "5.plus(sum())                                % 10",
            "{5,-5}.sum()                                 % 0",
            "{int[10]::1}.sum()                           % 10",
            //"{1,2,3}.plus(sum())-|id()                  % {2,4,6}"
            /// ////////////////////////////////////////////////
            "{1.0,2.2,3.3}.sum()                          % 6.5",
            "{1.1,real[10]::2.1,3.5}.sum()                % 25.6",
            "5.75.sum()                                   % 5.75",
            "5.2.plus(sum())                              % 10.4",
            "{5.1,-5.1}.sum()                             % 0.0",
            "{real[10]::1.1}.sum()                        % 11.0",
            /// ////////////////////////////////////////////////
            "{[,],[,],[,]}.sum()                          % [,]",
            "{[,],[,],[1]}.sum()                          % [1]",
            "{[1],[2],[3]}.sum()                          % [1,2,3]",
            "{[1,2],[2,4],[3,2,2]}.sum()                  % [1,2,2,4,3,2,2]",
            "[1,2,3]_/sum?int<=int[*]()\\_                % [6]",
    }, delimiter = '%')
    public void testSumInst(final String expression, final String expectedResult) {
        assertEquals(ObjParser.m_obj().parse(expectedResult).get(), ObjParser.eval(expression).next());
    }

}
