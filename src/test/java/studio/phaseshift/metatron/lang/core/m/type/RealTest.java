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
import studio.phaseshift.metatron.lang.mObjTest;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RealTest extends mObjTest {

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
        super.testCode(code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "2.0.pow(4.0)                              % 16.0",
            "2.0.pow(4.0).plus(1.0)                    % 17.0",
            "2.0.pow(4.0).plus(1.0).mult(2.0)          % 34.0",
    }, delimiter = '%')
    public void testMath(final String code, final String expected) {
        super.testCode(code, expected);
    }
}
