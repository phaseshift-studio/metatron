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

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
/*
 "/t -> [a,[b,[c,d],e],f]                               % */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.lang.mObjTest;
import studio.phaseshift.metatron.mTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class LstTest extends mObjTest {

    @ParameterizedTest
    @CsvSource(value = {
            // lst                                 | key                  | value
            "[a,[b,[c,d],e],f]                     | <>                   | noobj",
            "[a,[b,[c,d],e],f]                     | <0>                  | a",
            "[a,[b,[c,d],e],f]                     | <1/0>                | b",
            "[a,[b,[c,d],e],f]                     | <1/1/0>              | c",
            "[a,[b,[c,d],e],f]                     | <1/1/1>              | d",
            "[a,[b,[c,d],e],f]                     | <1/1/+>              | {c,d}",
            "[a,[b,[c,d],e],f]                     | <1/+/+>              | {c,d}",
            "[a,[b,[c,d],[e,f]],g]                 | <1/+/+>              | {c,d,e,f}",
            "[a,[b,[c,d],[e,[f,g]]],h]             | <1/+/+>              | {c,d,e,[f,g]}",
            "[a,[b,[c,d],e],f]                     | <1/+>                | {b,[c,d],e}",
            "[a,[b,[c,d],e],f]                     | <1/+>                | {b,[c,d],e}",
            "[a,[b,[c,d],e],f]                     | <#>                  | {a,[b,[c,d],e],f}" // TODO: should this be unrolled?
    }, delimiter = '|')
    public void testKeyValue(final String lst, final String key, final String value) {
        Lst r = mParser.m_obj().parse(lst).get();
        Obj k = mParser.m_obj().parse(key).get();
        Obj v = mParser.m_obj().parse(value).get();
        Obj actual = r.at(k);
        LOG.debug("testing %s at %s is %s [expected:%s]", k, r, actual, v);
        assertTrue(r.isLst());
        assertEquals(v, actual);
    }

    
    @ParameterizedTest
    @CsvSource(value = {
            "[a,[b,[c,d],e],f]                                                                       % [a,[b,[c,d],e],f]",
            "[a,[b,[c,d],e],f].get(<0>)                                                              % a",
            "[a,[b,[c,d],e],f].get(<1/0>)                                                            % b",
            "[a,[b,[c,d],e],f].get(<1/1/0>)                                                          % c",
            "[a,[b,[c,d],e],f].get(<1/1/1>)                                                          % d",
            "[a,[b,[c,d],e],f].get(<1/1/+>)                                                          % {c,d}",
            "[a,[b,[c,d],e],f].get(<1/+/+>)                                                          % {c,d}",
            "[a,[b,[c,d],[e,f]],g].get(<1/+/+>)                                                      % {c,d,e,f}",
            "[a,[b,[c,d],[e,[f,g]]],h].get(<1/+/+>)                                                  % {c,d,e,[f,g]}",
            "[a,[b,[c,d],e],f].get(<1/+>)                                                            % {b,[c,d],e}",
            "[a,[b,[c,d],e],f].get(<1/+>)                                                            % {b,[c,d],e}",
            "[a,[b,[c,d],e],f].get(<#>)                                                              % {a,[b,[c,d],e],f}" // TODO: should this be unrolled?

    }, delimiter = '%')
    public void testCode(final String code, final String expected) {
        mTest.testCode(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "lst{10}::[1,2,3]                                                                        % lst{10}::[1,2,3]",
            "lst{10}::[1,2,3]>-                                                                      % {int{10}::1,int{10}::2,int{10}::3}",
            "lst{10}::[1,2,3]._/sum()\\_                                                             % lst{10}::[6]",
            // "lst{10}::[1,2,3]._/sum()\\_._/sum()\\_                                                  % lst{10}::[6]",
            "lst{10}::[1,2,3]._/sum()\\_.>-.-<[_]._/sum()\\_                                         % [60]",
            "lst{10}::[1,2,3]._/sum()\\_-<[_]                                                        % [lst{10}::[6]]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]]                                                    % [[lst{10}::[6]]]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]]>-                                                  % [lst{10}::[6]]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-]                                                  % [lst{10}::[6]]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-]>-                                                % lst{10}::[6]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]]>-.>-                                               % lst{10}::[6]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]]>-.>-.>-                                            % int{10}::6",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-]>-.>-                                             % int{10}::6",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-.>-].>-                                            % int{10}::6",
            //"lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-.>-.>-]                                            % [int{10}::6]",
            "lst{10}::[1,2,3]>-.sum?int<=int{*}()                                                    % 60",
            //"lst{10}::[1,2,3]>-.sum().sum()                                                          % 60",
            //"lst{10}::[1,2,3]>-.sum()                                                                % 60",

    }, delimiter = '%')
    public void testCoefficients(final String code, final String expected) {
        mTest.testCode(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[1,2,3].as(rec::T)                                                % [0=>1,1=>2,2=>3]",
            "lst{10}::[1,2,3].as(rec::T)                                       % rec{10}::[0=>1,1=>2,2=>3]",
            "lst{10}::[1,2,3].as(rec::T).merge?rel{*}<=rec()                   % {rel{10}::(0=>1),rel{10}::(1=>2),rel{10}::(2=>3)}",
            "[1,2,3].as(rec::T).>-.isa(rel::T).count()                         % 3",
            "[1,2,3].as(rec::T).>-.isa(rel::T).count()                         % 3",
            "[1,2,3].as(rec::T).>-.>>.isa(int::T).count()                      % 3",
            "[1,2,3].as(rec::T).>-.>>.sum()                                    % 6",
            "[1,2,3].as(rec::T).as(lst::T)                                     % [(0=>(0=>1)),(1=>(1=>2)),(2=>(2=>3))]",
            "[1,2,3].as(rec::T).as(rec::T)                                     % [0=>1,1=>2,2=>3]",
            // "[1,2,3].as(rec::T).as(lst::T).as(rec::T)               % [0=>(0=>(0=>1)),1=>(1=>(1=>2)),2=>(2=>(2=>3))]", // TODO: infinite loop?! why?

    }, delimiter = '%')
    public void testAs(final String code, final String expected) {
        mTest.testCode(LOG, code, expected);
    }
}
