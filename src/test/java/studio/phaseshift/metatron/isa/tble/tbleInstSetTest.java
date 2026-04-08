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

package studio.phaseshift.metatron.isa.tble;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class tbleInstSetTest extends AbstractInstSetTest {

    public tbleInstSetTest() {
        super(tbleInstSet::new);
    }

    // TODO: Add testAs() once table types are better understood
    // Row type conversions: lst_row to rec_row and vice versa
    // @ParameterizedTest
    // @CsvSource(value = {
    //         "lst_row_expression.as(rec_row::T) | *rec_row | true",
    // }, delimiter = '|')
    // public void testAs(String code, String expectedType, boolean shouldMatch) {
    //     Obj result = mParser.eval(code);
    //     Obj expected = mParser.eval(expectedType);
    //     LOG.debug("result [%s] expected [%s] [should match: %b]", result, expected, shouldMatch);
    //     assertEquals(shouldMatch, result.test(expected));
    // }
}

