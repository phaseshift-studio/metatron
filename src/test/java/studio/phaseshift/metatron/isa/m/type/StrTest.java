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
import studio.phaseshift.metatron.isa.AbstractObjTest;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class StrTest extends AbstractObjTest {

    @ParameterizedTest
    @CsvSource(value = {
            "'true'.as(bool::T)                                                             % true",
            "'false'.as(bool::T)                                                            % false",
            "'true'.as(bool::T).as(str::T)                                                  % \"true\"",
            "'sadf'.as(bool::T)                                                             % false",
            "'123'.as(int::T)                                                               % 123",
            "'123.122'.as(real::T)                                                          % 123.122",
            "'abcd'.as(uri::T)                                                              % abcd",
            "'abc'.as(bytes::T)                                                             % 0x616263",
            "'abc'.as(bytes::T).as(str::T)                                                  % \"abc\""

    }, delimiter = '%', quoteCharacter = '~')
    public void testAsInst(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "'123'.regex('\\d')                                                             % ['1','2','3']",
            "'abcd'.regex('[a-z]{2}')                                                       % ['ab','cd']",
            "'ab3cd'.regex('([a-z]+)(\\d?)([a-z]?)')                                        % ['ab3c','d']",
            "'ab3cd'.regex('(?<a>[a-z]+)(?<b>\\d?)(?<c>[a-z]?)')                            % ['ab3c','d']",
            "'ab3cd'.regex('\\d*')                                                          % ['','','3','','','']",
            "'ab3cd'.regex('\\d+')                                                          % ['3']",
            "'ab3cd'.regex('\\d{2}')                                                        % [,]",
    }, delimiter = '%', quoteCharacter = '~')
    public void testRegexInst(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{'a','b','c','123'}.sum()                                                      % \"abc123\"",
            "{'a','b','c','123'}.sum('22')                                                  % \"22abc123\"",
            "{}.sum('22')                                                                   % \"22\"",
            "{}.sum?str<=str{*}('')                                                         % \"\"",
            //"{}.sum?str<=str{*}()                                                           % \"\"",  // TODO
    }, delimiter = '%', quoteCharacter = '~')
    public void testSum(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "\"abc\".ucase()                                                      % \"ABC\"",
            "\"abc\".lcase()                                                      % \"abc\"",
            "\"AbC-DeF\".lcase()                                                  % \"abc-def\"",
            "\"AbC-DeF\".ucase()                                                  % \"ABC-DEF\"",
            "\"AbC23-4eF\".lcase()                                                % \"abc23-4ef\"",
            "\"AbC23-4eF#\".ucase()                                               % \"ABC23-4EF#\"",
            "\"123\".ucase()                                                      % \"123\"",
            "\"123\".lcase()                                                      % \"123\"",
    }, delimiter = '%')
    public void testUCaseAndLCase(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "'ab3cd'.has('ab.*')                                                               % \"ab3cd\"",
            "'ab3cd'.has('bb')                                                                 % noobj",
            "{'abc3d','aaa'}.has('a\\.')                                                       %  noobj",
            "{'abc3d','aaa'}.has('a.*')                                                        % {\"abc3d\",\"aaa\"}",
            "{'abc3d','aaa'}.has('a(b)?(a|c).?')                                               % {\"abc3d\",\"aaa\"}",
            "{'abc3d','aaa'}.has('b.*')                                                        % {\"abc3d\"}",
            "{'abc3d','aaa'}.has('c.*')                                                        % {\"abc3d\"}",
            "{'abc3d','aaa'}.has('d.*')                                                        % \"abc3d\"",
            "{'abc3d','aaa'}.has('d.?')                                                        % \"abc3d\"",
            "{'abc3d','aaa'}.has('e.*')                                                        % noobj"
            // "{'abc3d','aaa'}.where(not(has('e.')))                                          % {\"abc3d\",\"aaa\"}",
            // "{'abc3d','aaa'}.where(has('e.'))                                               % noobj",
    }, delimiter = '%', quoteCharacter = '~')
    public void testHasInst(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }


    /*@ParameterizedTest
    @CsvSource(value = {
            "'a.b.c'-<'.'                                                                   % ['a','b','c']",
            "'a.b.c'-<re::'.'                                                               % [,]",
            "'a-b-c'-<'-'                                                                   % ['a','b','c']",
            "'a-b-c'-<re::'[a-z]'                                                           % ['','-','-']",
            "'a-b-c'-<'-'>-                                                                 % {'a','b','c'}",
            "'a-b-c'-<'-'>-'_'                                                              % \"a_b_c\"",
            "'a:b-b:c-c:d'-<'-'                                                             % ['a:b','b:c','c:d']",
            "'a:b-b:c-c:d'-<'-'_/>-.-<':'\\_                                                % [['a','b'],['b','c'],['c','d']]",
            "'a:b-b:c-c:d'-<'-'_/>-.-<':'\\_.>-                                             % {['a','b'],['b','c'],['c','d']}",
            "'a:b-b:c-c:d'-<'-'_/>-.-<':'\\_.>-.>-':'                                       % {'a:b','b:c','c:d'}",
            "'a:b-b:c-c:d'-<'-'_/>-.-<':'\\_.>-.>-':'.>-?<=str{*}('-')                      % \"a:b-b:c-c:d\"",
    }, delimiter = '%')
    public void testSplitMerge(final String code, final String expected) {
        mTest.testCode(LOG,code,expected);
    }*/

    @ParameterizedTest
    @CsvSource(value = {
            "'a'.plus('a')                                                                  % \"aa\"",
            "'ab3cd' + 'ab.'                                                                % \"ab3cdab.\"",
    }, delimiter = '%', quoteCharacter = '~')
    public void testPlusInst(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "\"abc\".reverse()                % \"cba\"",
            "\"12bac2545_245\".reverse()      % \"542_5452cab21\"",
           // "\"abc\".reverse().reverse()      % \"abc\"", TODO instructions are being seen as the same
            "\"\".reverse().map(_).reverse()         % \"\"",
            "\"\".reverse()                  % \"\"",
            "\"a\".reverse()                 % \"a\"",
            "\"hello world\".reverse()       % \"dlrow olleh\"",
    }, delimiter = '%')
    public void testReverse(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "\"abc\".eq(\"abc\")               % true",
            "\"abc\".eq(\"def\")               % false",
            "\"abc\".eq(\"\")                  % false",
            "\"\".eq(\"\")                     % true",
            "\"ABC\".eq(\"abc\")               % false",
    }, delimiter = '%')
    public void testEquality(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "\"abc\".gt(\"aaa\")               % true",
            "\"abc\".gt(\"abc\")               % false",
            "\"abc\".gt(\"def\")               % false",
            "\"abc\".lt(\"aaa\")               % false",
            "\"abc\".lt(\"abc\")               % false",
            "\"abc\".lt(\"def\")               % true",
            "\"abc\".eq(\"abc\")               % true",
            "\"abc\".eq(\"def\")               % false",
            "\"abc\".gte(\"abc\")              % true",
            "\"abc\".gte(\"aaa\")              % true",
            "\"abc\".gte(\"def\")              % false",
            "\"abc\".lte(\"abc\")              % true",
            "\"abc\".lte(\"def\")              % true",
            "\"abc\".lte(\"aaa\")              % false",
            "\"abc\".neq(\"abc\")              % false",
            "\"abc\".neq(\"def\")              % true",
            "\"\".eq(\"\")                     % true",
            "\"\".lt(\"a\")                    % true",
            "\"z\".gt(\"a\")                   % true",
            "\"ABC\".lt(\"abc\")               % true",
    }, delimiter = '%')
    public void testComparison(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "\"hello\".plus(\" world\")       % \"hello world\"",
            "\"a\".plus(\"b\").plus(\"c\")    % \"abc\"",
            "\"\".plus(\"test\")              % \"test\"",
            "\"test\".plus(\"\")              % \"test\"",
    }, delimiter = '%')
    public void testConcat(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

}
