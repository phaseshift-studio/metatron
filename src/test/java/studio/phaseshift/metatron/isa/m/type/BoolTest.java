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
import studio.phaseshift.metatron.lang.mObjTest;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BoolTest extends mObjTest {

    @ParameterizedTest
    @CsvSource(value = {
            "true.as(str::T)                                                               % \"true\"",
            "false.as(str::T)                                                              % \"false\"",
            "true.as(int::T)                                                               % 1",
            "false.as(int::T)                                                              % 0",
            "true.as(real::T)                                                              % 1.0",
            "false.as(real::T)                                                             % 0.0"
    }, delimiter = '%')
    public void testAsInst(final String code, final String expected) {
        super.testCode(code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "true.plus(false)                                                                  % true",
            "false.plus(true)                                                                  % true",
            "true.plus(true)                                                                   % true",
            "false.plus(false)                                                                 % false"
    }, delimiter = '%')
    public void testPlusInst(final String code, final String expected) {
        super.testCode(code, expected);
    }

}
