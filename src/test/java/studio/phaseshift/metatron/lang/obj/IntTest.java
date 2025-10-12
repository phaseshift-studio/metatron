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

package studio.phaseshift.metatron.lang.obj;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.parse.ObjParser;
import studio.phaseshift.metatron.util.Tuple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.lang.fURI.f;

public class IntTest extends MetatronTest {
    @Override
    @ParameterizedTest
    @CsvSource(value = {
            // a                                 | b                            | matches
            "1                                   | 1                            | true",
            "1                                   | int::T[]                     | true",
            "1                                   | str::T[]                     | false",
            "1                                   | int{0,4}::T[]                | true",
            "int{5}::1                           | int{0,4}::T[]                | false",
            "int{5}::1                           | int{*}::T[]                  | true",
            "int{0}::1                           | noobj[0}::T[]                | true"
    }, delimiter = '|')
    public void testMatches(final String lhs, final String rhs, final boolean matches) {
        super.testMatches(lhs, rhs, matches);
    }

    @Override
    @ParameterizedTest
    @CsvSource(value = {
            // a                                 | b                            | matches
            "1                                   | plus(2)                      | 3",
            "1                                   | plus(mult(10))               | 11",
            "1                                   | gt(0)                        | true",
            "1                                   | is(gt(0))                    | 1",
            "1                                   | in(int::T[])                 | true",
            "1                                   | is(in(int::T[]))             | 1",
            "1                                   | in(str::T[])                 | false",
            "1                                   | is(in(str::T[]))             | noobj"
    }, delimiter = '|')
    public void testCode(final String lhs, final String code, final String expected) {
        super.testCode(lhs, code, expected);
    }

    @Override
    @ParameterizedTest
    @CsvSource(value = {
            "bool::1                                                      | <ERROR>",
            "real::1                                                      | <ERROR>",
            "str::1                                                       | <ERROR>",
            "uri::1                                                       | <ERROR>",
            "lst::1                                                       | <ERROR>",
            "rec::1                                                       | <ERROR>",
            "inst::1                                                      | <ERROR>",
            "code::1                                                      | <ERROR>",
            "3.plus(mult(2))                                              | 9",
            "{2,3}>-.plus(mult(2))                                        | {6,9}",
            "{2,3}.plus(mult(2))                                          | {6,9}",
            // "{2,3}.is?int{*]<=int{*](in?bool[+]<=int{*](int{2}::T[]))   | {2,3}",
            "{1,2,3}.plus(1).plus(2)                                      | {4,5,6}",
            "{1,2,3}.plus(1).plus(2).mult(2)                              | {8,10,12}",
            "{1,2,3}.plus(1).plus(2).mult(2).is(in(int::T[]))             | {8,10,12}",
            "{1,2,3}.plus(1).plus(2).mult(2).is(in(str::T[]))             | noobj"
    }, delimiter = '|')
    public void testCode(final String code, final String expected) {
        super.testCode(code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "int{10}::1         |int{5}       |int{5}::1          |int{5}::1",
            "int{10}::1         |int{3}       |int{3}::1          |int{7}::1",
            "int{4,10}::1       |int{3}       |int{3}::1          |int{1,7}::1",
            "int{10}::1         |int{10}      |int{10}::1         |int{0}::1",
            "int{10}::1         |int{10}      |int{10}::1         |noobj",
            "int{10}::1         |int{11}      |int{0}::1          |int{10}::1",
            "int{0}::1          |int{0}       |int{0}::1          |int{0}::1",
            "int{10}::1         |int{0}       |int{0}::1          |int{10}::1",
            "int{10}::1         |int{-5}      |int{-5}::1         |int{15}::1",
            "int{10,}::1        |int{10,}     |int{10,}::1        |int{0}::1",
            "int{10,}::1        |int{1,}      |int{1,}::1         |noobj",
            "noobj,             |int{10}      |noobj              |noobj",
            "int{,10}::1        |int{,10}     |int{,10}::1        |noobj",
            "int{,10}::1        |int{1}       |int::1             |int{,9}::1",
            "int{1,10}::1       |int{1}       |int::1             |int{0,9}::1",
    }, delimiter = '|')
    public void testRemove(final String current, final String remove, final String retrieved, final String remaining) {
        final Obj currentF = ObjParser.m_obj().parse(current).get();
        final fURI removeF = f(remove);
        final Obj retrievedF = ObjParser.m_obj().parse(retrieved).get();
        final Obj remainingF = ObjParser.m_obj().parse(remaining).get();
        assertEquals(Tuple.Pair.with(retrievedF, remainingF), currentF.take(removeF.cV()));


    }
}
