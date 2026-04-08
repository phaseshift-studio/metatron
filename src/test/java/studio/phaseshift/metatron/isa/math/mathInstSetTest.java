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

package studio.phaseshift.metatron.isa.math;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mathInstSetTest extends AbstractInstSetTest {

    public mathInstSetTest() {
        super(mathInstSet::new);
    }

    @ParameterizedTest
    @CsvSource(value = {
            // int to byte units
            "1024.0.as(kB::T)                                                                    % kB::1024.0",

            // bB conversions (identity and upward)
            "bB::1024.0.as(bB::T)                                                                % bB::1024.0",
            "bB::1024.0.as(kB::T)                                                                % kB::1.0",
            "bB::1048576.0.as(mB::T)                                                             % mB::1.0",
            "bB::1073741824.0.as(gB::T)                                                          % gB::1.0",

            // kB conversions (downward, identity, and upward)
            "kB::1.0.as(bB::T)                                                                   % bB::1024.0",
            "kB::1024.0.as(kB::T)                                                                % kB::1024.0",
            "kB::1024.0.as(mB::T)                                                                % mB::1.0",
            "kB::1048576.0.as(gB::T)                                                             % gB::1.0",
            "kB::1073741824.0.as(tB::T)                                                          % tB::1.0",

            // mB conversions (downward, identity, and upward)
            "mB::1.0.as(bB::T)                                                                   % bB::1024.0.mult(1024.0)",
            "mB::1.0.as(kB::T)                                                                   % kB::1024.0",
            "mB::1.0.as(mB::T)                                                                   % mB::1.0",
            "mB::1024.0.as(gB::T)                                                                % gB::1.0",
            "mB::1048576.0.as(tB::T)                                                             % tB::1.0",
            "mB::1073741824.0.as(pB::T)                                                          % pB::1.0",

            // gB conversions (downward, identity, and upward)
            "gB::1.0.as(bB::T)                                                                   % bB::1024.0.mult(1024.0).mult(1024.0)",
            "gB::1.0.as(kB::T)                                                                   % kB::1024.0.mult(1024.0)",
            "gB::1.0.as(mB::T)                                                                   % mB::1024.0",
            "gB::1.0.as(gB::T)                                                                   % gB::1.0",
            "gB::1024.0.as(tB::T)                                                                % tB::1.0",
            "gB::1048576.0.as(pB::T)                                                             % pB::1.0",

            // tB conversions (downward, identity, and upward)
            "tB::1.0.as(bB::T)                                                                   % bB::1024.0.mult(1024.0).mult(1024.0).mult(1024.0)",
            "tB::1.0.as(kB::T)                                                                   % kB::1024.0.mult(1024.0).mult(1024.0)",
            "tB::1.0.as(mB::T)                                                                   % mB::1024.0.mult(1024.0)",
            "tB::1.0.as(gB::T)                                                                   % gB::1024.0",
            "tB::1.0.as(tB::T)                                                                   % tB::1.0",
            "tB::1024.0.as(pB::T)                                                                % pB::1.0",

            // pB conversions (downward and identity)
            "pB::1.0.as(bB::T)                                                                   % bB::1024.0.mult(1024.0).mult(1024.0).mult(1024.0).mult(1024.0)",
            "pB::1.0.as(kB::T)                                                                   % kB::1024.0.mult(1024.0).mult(1024.0).mult(1024.0)",
            "pB::1.0.as(mB::T)                                                                   % mB::1024.0.mult(1024.0).mult(1024.0)",
            "pB::1.0.as(gB::T)                                                                   % gB::1024.0.mult(1024.0)",
            "pB::1.0.as(tB::T)                                                                   % tB::1024.0",
            "pB::1.0.as(pB::T)                                                                   % pB::1.0",

            // Multi-step conversions (skip levels)
            "bB::1099511627776.0.as(tB::T)                                                       % tB::1.0",
            "bB::1125899906842624.0.as(pB::T)                                                    % pB::1.0",
            "kB::1099511627776.0.as(pB::T)                                                       % pB::1.0",

            // Larger values
            "kB::2048.0.as(mB::T)                                                                % mB::2.0",
            "mB::2048.0.as(gB::T)                                                                % gB::2.0",
            "gB::2048.0.as(tB::T)                                                                % tB::2.0",
            "tB::2048.0.as(pB::T)                                                                % pB::2.0",
            "pB::2.0.as(tB::T)                                                                   % tB::2048.0",
            "tB::2.0.as(gB::T)                                                                   % gB::2048.0",
            "gB::2.0.as(mB::T)                                                                   % mB::2048.0",
            "mB::2.0.as(kB::T)                                                                   % kB::2048.0",
            "kB::2.0.as(bB::T)                                                                   % bB::2048.0",
    }, delimiter = '%', quoteCharacter = '~')
    public void testConversions(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            // eq() - Equality tests with exact conversions (smaller to larger unit)
            "kB::1024.0.eq(mB::1.0)                                                               % true",
            "mB::1024.0.eq(gB::1.0)                                                               % true",
            "gB::1024.0.eq(tB::1.0)                                                               % true",
            "tB::1024.0.eq(pB::1.0)                                                               % true",
            "bB::1024.0.eq(kB::1.0)                                                               % true",

            // eq() - Long-range equality tests
            "bB::1048576.0.eq(mB::1.0)                                                            % true",
            "bB::1073741824.0.eq(gB::1.0)                                                         % true",
            "kB::1048576.0.eq(gB::1.0)                                                            % true",
            "mB::1048576.0.eq(tB::1.0)                                                            % true",

            // neq() - Not equal tests
            "kB::1024.0.neq(mB::1.0)                                                              % false",
            "mB::1024.0.neq(gB::1.0)                                                              % false",

            // lt() and gt() - Basic comparison tests
            "kB::1024.0.lt(mB::1.0)                                                               % false",
            "kB::1024.0.gt(mB::1.0)                                                               % false",

            // lte() and gte() - Less/greater than or equal tests
            "kB::1024.0.lte(mB::1.0)                                                              % true",
            "kB::1024.0.gte(mB::1.0)                                                              % true",

            // NOTE: Comparison operations with non-exact conversions fail due to implementation bugs
            // TODO: Byte unit types need to support real values for accurate bidirectional conversions
            // TODO: Fix comparison logic to handle non-exact unit conversions correctly
    }, delimiter = '%', quoteCharacter = '~')
    public void testConversionRelations(final String code, final boolean match) {
        assertEquals(match, mParser.eval(code).boolValue());
    }

    @ParameterizedTest
    @CsvSource(value = {
            // Byte unit as() conversions - verifies resolver picks correct as?X<=Y instruction
            "bB::1024.0.as(kB::T)         | *kB   | true",
            "kB::1024.0.as(mB::T)         | *mB   | true",
            "mB::1024.0.as(gB::T)         | *gB   | true",
            "gB::1024.0.as(tB::T)         | *tB   | true",
            "tB::1024.0.as(pB::T)         | *pB   | true",
            // Downward conversions
            "kB::1.0.as(bB::T)            | *bB   | true",
            "mB::1.0.as(kB::T)            | *kB   | true",
            "gB::1.0.as(mB::T)            | *mB   | true",
            "tB::1.0.as(gB::T)            | *gB   | true",
            "pB::1.0.as(tB::T)            | *tB   | true",
    }, delimiter = '|')
    public void testAs(String code, String expectedType, boolean shouldMatch) {
        Obj result = mParser.eval(code);
        Obj expected = mParser.eval(expectedType);
        LOG.debug("result [%s] expected [%s] [should match: %b]", result, expected, shouldMatch);
        assertEquals(shouldMatch, result.test(expected));
    }
}
