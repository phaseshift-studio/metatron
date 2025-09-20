/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.parse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Call;
import studio.phaseshift.metatron.lang.obj.Code;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.mtron.MInt;
import studio.phaseshift.metatron.lang.obj.mtron.MInstSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static studio.phaseshift.metatron.lang.parse.ObjParser.eval;

public class InstParseTest {

    @BeforeAll
    public static void begin() {
        BootLoader.load();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "abc?int<=int(){ plus(_) }% true",
            //"/mtron/code[plus(1).plus(2)].plus([d,e,f])% [a,b,c,d,e,f]" (requires union())
    }, delimiter = '%')
    void testInstDefinitions(final String expression, final String expectedResult) {
        Call call = ObjParser.m_obj().parse(expression).get();
        assertNotNull(call);
        //assertEquals(inst, ObjParser.eval(expression).next());
    }


    @ParameterizedTest
    @CsvSource(value = {
            "true.plus(false)% true",
            "false.plus(false)% false",
            "0.plus(0)% 0",
            "1.plus(2)% 3",
            "3.plus(-3)% 0",
            "\"abc\".plus(\"def\")% \"abcdef\"",
            "abc.plus(def)% abc/def",
            "[a,b,c].plus([d,e,f])% [a,b,c,d,e,f]",
            //"/mtron/code[plus(1).plus(2)].plus([d,e,f])% [a,b,c,d,e,f]" (requires union())
    }, delimiter = '%')
    void testPlusInst(final String expression, final String expectedResult) {
        assertEquals(ObjParser.m_obj().parse(expectedResult).get(), ObjParser.eval(expression).next());
    }

    @Test
    public void testCountInst() {
        assertEquals(MInt.of(3), eval("{1,2,3}.count()").next());
    }

    @Test
    public void testSumInst() {
        assertEquals(MInt.of(6), eval("{1,2,3}.sum()").next());
    }

}
