/*
 * metatron: a distributed virtual machine and language
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

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
        assertEquals(match, ObjmtronSerializer.parse(code).apply().boolValue());
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
        Obj result = ObjmtronSerializer.parse(code);
        Obj expected = ObjmtronSerializer.parse(expectedType);
        LOG.debug("result [%s] expected [%s] [should match: %b]", result, expected, shouldMatch);
        assertEquals(shouldMatch, result.test(expected));
    }

    @ParameterizedTest
    @CsvSource(value = {
            // raw real to time unit (no conversion — re-tagging only)
            "30000.0.as(minute::T)                                                               % minute::30000.0",

            // millis conversions (identity and upward)
            "millis::1000.0.as(millis::T)                                                        % millis::1000.0",
            "millis::1000.0.as(second::T)                                                        % second::1.0",
            "millis::60000.0.as(minute::T)                                                       % minute::1.0",
            "millis::3600000.0.as(hour::T)                                                       % hour::1.0",

            // second conversions (downward, identity, and upward)
            "second::1.0.as(millis::T)                                                           % millis::1000.0",
            "second::60.0.as(second::T)                                                          % second::60.0",
            "second::60.0.as(minute::T)                                                          % minute::1.0",
            "second::3600.0.as(hour::T)                                                          % hour::1.0",

            // minute conversions (downward, identity, and upward)
            "minute::1.0.as(millis::T)                                                           % millis::1000.0.mult(60.0)",
            "minute::1.0.as(second::T)                                                           % second::60.0",
            "minute::1.0.as(minute::T)                                                           % minute::1.0",
            "minute::60.0.as(hour::T)                                                            % hour::1.0",

            // hour conversions (downward and identity)
            "hour::1.0.as(millis::T)                                                             % millis::1000.0.mult(60.0).mult(60.0)",
            "hour::1.0.as(second::T)                                                             % second::60.0.mult(60.0)",
            "hour::1.0.as(minute::T)                                                             % minute::60.0",
            "hour::1.0.as(hour::T)                                                               % hour::1.0",

            // Multi-step conversions (skip levels)
            "millis::3600000.0.as(hour::T)                                                       % hour::1.0",
            "second::3600.0.as(hour::T)                                                          % hour::1.0",

            // Larger / fractional values
            "millis::1800000.0.as(minute::T)                                                     % minute::30.0",
            "millis::7200000.0.as(hour::T)                                                       % hour::2.0",
            "second::90.0.as(minute::T)                                                          % minute::1.5",
            "second::5400.0.as(hour::T)                                                          % hour::1.5",
            "minute::90.0.as(hour::T)                                                            % hour::1.5",
            "hour::2.5.as(minute::T)                                                             % minute::150.0",
            "hour::0.5.as(second::T)                                                             % second::1800.0",
    }, delimiter = '%', quoteCharacter = '~')
    public void testTimeConversions(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            // eq() - Equality tests with exact conversions
            "millis::1000.0.eq(second::1.0)                                                       % true",
            "second::60.0.eq(minute::1.0)                                                         % true",
            "minute::60.0.eq(hour::1.0)                                                           % true",
            "millis::60000.0.eq(minute::1.0)                                                      % true",
            "millis::3600000.0.eq(hour::1.0)                                                      % true",
            "second::3600.0.eq(hour::1.0)                                                         % true",

            // neq() - Not equal tests
            "millis::1000.0.neq(second::1.0)                                                      % false",
            "second::60.0.neq(minute::1.0)                                                        % false",

            // lt() and gt() - Basic comparison tests
            "millis::500.0.lt(second::1.0)                                                        % true",
            "second::1.0.gt(millis::500.0)                                                        % true",
            "millis::1000.0.lt(second::1.0)                                                       % false",
            "millis::1000.0.gt(second::1.0)                                                       % false",

            // lte() and gte() - Less/greater than or equal tests
            "millis::1000.0.lte(second::1.0)                                                      % true",
            "millis::1000.0.gte(second::1.0)                                                      % true",
            "minute::60.0.lte(hour::1.0)                                                          % true",
            "minute::60.0.gte(hour::1.0)                                                          % true",
    }, delimiter = '%', quoteCharacter = '~')
    public void testTimeConversionRelations(final String code, final boolean match) {
        assertEquals(match, ObjmtronSerializer.parse(code).apply().boolValue());
    }

    @ParameterizedTest
    @CsvSource(value = {
            // Time unit as() conversions - verifies resolver picks correct as?X<=Y instruction
            "millis::1000.0.as(second::T)   | *second   | true",
            "second::60.0.as(minute::T)     | *minute   | true",
            "minute::60.0.as(hour::T)       | *hour     | true",
            // Downward conversions
            "second::1.0.as(millis::T)       | *millis   | true",
            "minute::1.0.as(second::T)       | *second   | true",
            "hour::1.0.as(minute::T)         | *minute   | true",
    }, delimiter = '|')
    public void testTimeAs(String code, String expectedType, boolean shouldMatch) {
        Obj result = ObjmtronSerializer.parse(code);
        Obj expected = ObjmtronSerializer.parse(expectedType);
        LOG.debug("result [%s] expected [%s] [should match: %b]", result, expected, shouldMatch);
        assertEquals(shouldMatch, result.test(expected));
    }

    @Test
    public void testConstants() {
        Router.global().addSpace(memSpace.of(rec(uri(PATTERN), uri("/abc/#"), uri(Tokens.QPROC), lst(QCollection.constQ())), f("abc")));
        assertEquals(jnt(34), ObjmtronSerializer.parse("/abc/xyz -> 34").apply());
        assertEquals(jnt(34), ObjmtronSerializer.parse("*/abc/xyz").apply());
        assertEquals(jnt(99), ObjmtronSerializer.parse("/abc/xyz -> 99").apply());
        assertEquals(jnt(99), ObjmtronSerializer.parse("*/abc/xyz").apply());
        assertFalse(ObjmtronSerializer.parse("*/abc/xyz?constq").apply().boolValue());
        assertEquals(noobj(), ObjmtronSerializer.parse("/abc/xyz?constq -> noobj").apply());
        assertEquals(jnt(989), ObjmtronSerializer.parse("/abc/xyz?constq -> 989").apply());
        assertTrue(ObjmtronSerializer.parse("*/abc/xyz?constq").apply().boolValue());
        //assertEquals(jnt(989), mParser.eval("*/abc/xyz"));
        assertTrue(ObjmtronSerializer.parse("/abc/xyz -> 100").apply().isFail());
        assertEquals(jnt(88), ObjmtronSerializer.parse("/abc/xyz?constq -> 88").apply());
        //assertEquals(jnt(88), mParser.eval("*/abc/xyz"));
    }
}
