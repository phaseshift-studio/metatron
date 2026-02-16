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
import studio.phaseshift.metatron.mTest;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RelTest extends mTest {

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>b).as(rec::T)                                                     % [a=>b]",
            "(a=>b).as(rec::T).as(lst::T).>>                                       % [(a=>b)]",
            "(a=>b)-<(<<.as(str::T)=>>>)                                           % \"a\"=>b",
    }, delimiter = '%')
    public void testRelAs(final String code, final String expected) {
        mTest.evaluate(LOG, code, expected);
    }
    
    @ParameterizedTest
    @CsvSource(value = {
            "(a=>b).<<                                                     % a",
            "(a=>b).>>                                                     % b",
            "(a=>b)-<(_=>_)                                                % ((a=>b)=>(a=>b))",
            "(a=>b)-<(_=>_)>-                                              % {rel{2}::(a=>b)}",
            "(a=>b)-<(_=>_)>-.>-                                           % {uri{2}::a,uri{2}::b}"
    }, delimiter = '%')
    public void testRelSplitShift(final String code, final String expected) {
        mTest.evaluate(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>b).select((_=>b))                                                     % (a=>b)",
            "(a=>1).select((_=>plus(10)))                                              % (a=>11)",
            "(2=>1).select((mult(4)=>plus(5)))                                         % (8=>6)",
            "(1=>(2=>3)).select((mult(4)=>(_=>plus(10))))                              % (4=>(2=>13))",
            "1=>2=>3.select((mult(4)=>(_=>plus(10)))).where((_=>(_=>14)))              % (4=>(2=>14))",
            "1=>2=>3.select((mult(4)=>(_=>plus(10)))).where((_=>(_=>13)))              % noobj",
    }, delimiter = '%')
    public void testRelSelectWhere(final String code, final String expected) {
        mTest.evaluate(LOG, code, expected);
    }
}
