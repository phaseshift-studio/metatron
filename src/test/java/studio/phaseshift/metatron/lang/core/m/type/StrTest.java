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

package studio.phaseshift.metatron.lang.core.m.type;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.lang.MetatronObjTest;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class StrTest extends MetatronObjTest {

    @ParameterizedTest
    @CsvSource(value = {
            "'true'.as(bool::T)                                                             % true",
            "'false'.as(bool::T)                                                            % false",
            "'true'.as(bool::T).as(str::T)                                                  % \"true\"",
            "'sadf'.as(bool::T)                                                             % false",
            "'123'.as(int::T)                                                               % 123",
            "'123.122'.as(real::T)                                                          % 123.122",
            "'abcd'.as(uri::T)                                                              % abcd",

    }, delimiter = '%')
    public void testAsInst(final String code, final String expected) {
        super.testCode(code, expected);
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
    }, delimiter = '%')
    public void testRegexInst(final String code, final String expected) {
        super.testCode(code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{'a','b','c','123'}.sum()                                                      % \"abc123\"",
            "{'a','b','c','123'}.sum('22')                                                  % \"22abc123\"",
            "{}.sum('22')                                                                   % \"22\"",
            // "{}.sum?str<=str{*}()                                                           % \"\"",  // TODO
    }, delimiter = '%')
    public void testSum(final String code, final String expected) {
        super.testCode(code, expected);
    }


    @ParameterizedTest
    @CsvSource(value = {
            "'ab3cd'.has('ab.')                                                             % true",
            "'ab3cd'.has('bb')                                                              % false",
            "{'abc3d','aaa'}.has('a\\.')                                                    % bool{2}::false",
            "{'abc3d','aaa'}.has('a.')                                                      % bool{2}::true",
            "{'abc3d','aaa'}.has('a(b)?(a|c).?')                                            % bool{2}::true",
            "{'abc3d','aaa'}.has('b.')                                                      % {true,false}",
            "{'abc3d','aaa'}.has('c.')                                                      % {true,false}",
            "{'abc3d','aaa'}.has('d.')                                                      % bool{2}::false",
            "{'abc3d','aaa'}.has('d.?')                                                     % {true,false}",
            "{'abc3d','aaa'}.has('e.')                                                      % bool{2}::false",
            // "{'abc3d','aaa'}.where(not(has('e.')))                                          % {\"abc3d\",\"aaa\"}",
            // "{'abc3d','aaa'}.where(has('e.'))                                               % noobj",
    }, delimiter = '%')
    public void testHasInst(final String code, final String expected) {
        super.testCode(code, expected);
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
        super.testCode(code, expected);
    }*/

    @ParameterizedTest
    @CsvSource(value = {
            "'a'.plus('a')                                                                  % \"aa\"",
            "'ab3cd' + 'ab.'                                                                % \"ab3cdab.\"",
    }, delimiter = '%')
    public void testPlusInst(final String code, final String expected) {
        super.testCode(code, expected);
    }

}
