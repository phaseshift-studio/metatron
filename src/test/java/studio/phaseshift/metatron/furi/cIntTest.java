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

package studio.phaseshift.metatron.furi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.util.MTronException;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class cIntTest extends AbstractMetatronTest {

    @Test
    public void testTokens() {
        assertEquals(cInt.of("10"), cInt.of(10, 10));
        assertEquals(cInt.of("10,13"), cInt.of(10, 13));
        assertEquals(cInt.of("?"), cInt.of(0, 1));
        assertEquals(cInt.of("+"), cInt.of(1, null));
        assertEquals(cInt.of("*"), cInt.of(0, null));
        assertEquals(cInt.of("**"), cInt.of((Long) null, null));
        assertEquals(cInt.of("??"), cInt.of(-1, 1));
        assertEquals(cInt.of("-?"), cInt.of(-1, 0));
        assertEquals(cInt.of("-"), cInt.of(null, -1));
        assertEquals(cInt.of("-*"), cInt.of(null, 0));
        assertEquals(cInt.of("-12"), cInt.of(-12, -12));
        assertEquals(cInt.of("-12,-5"), cInt.of(-12, -5));
        assertEquals(cInt.of("-12,16"), cInt.of(-12, 16));
        assertThrows(MTronException.class, () -> cInt.of("-12,-16"));
    }

    @Test
    public void testInverse() {
        assertEquals(cInt.of(-10, -10), cInt.of(10, 10).inverse());
        assertEquals(cInt.of(-13, -10), cInt.of(10, 13).inverse());
        assertEquals(cInt.of(-1, 0), cInt.of(0, 1).inverse());
        assertEquals(cInt.of(null, -1), cInt.of(1, null).inverse());
        assertEquals(cInt.of(null, 0), cInt.of(0, null).inverse());
        assertEquals(cInt.of((Long) null, null), cInt.of((Long) null, null).inverse());
        assertEquals(cInt.of(-1, 1), cInt.of(-1, 1).inverse());
        assertEquals(cInt.of(0, 1), cInt.of(-1, 0).inverse());
        assertEquals(cInt.of(1, null), cInt.of(null, -1).inverse());
        assertEquals(cInt.of(0, null), cInt.of(null, 0).inverse());
        assertEquals(cInt.of(12), cInt.of(-12, -12).inverse());
        assertEquals(cInt.of(5, 12), cInt.of(-12, -5).inverse());
        assertEquals(cInt.of(-16, 12), cInt.of(-12, 16).inverse());
        assertEquals(cInt.of("-?"), cInt.of("?").inverse());
        assertEquals(cInt.of("?"), cInt.of("-?").inverse());
        assertEquals(cInt.of("-"), cInt.of("+").inverse());
        assertEquals(cInt.of("+"), cInt.of("-").inverse());
        assertEquals(cInt.of("**"), cInt.of("**").inverse());
        assertEquals(cInt.of("??"), cInt.of("??").inverse());
        assertEquals(cInt.of("-*"), cInt.of("*").inverse());
        assertEquals(cInt.of("*"), cInt.of("-*").inverse());
        assertThrows(MTronException.class, () -> cInt.of("12,-16"));
    }

    @Test
    public void testMirror() {
        assertEquals(cInt.of("??"), cInt.of("??").mirror());
        assertEquals(cInt.of("**"), cInt.of("**").mirror());
        assertEquals(cInt.of("??"), cInt.of("?").mirror());
        assertEquals(cInt.of("??"), cInt.of("-?").mirror());
        assertEquals(cInt.of("**"), cInt.of("-*").mirror());
        assertEquals(cInt.of("**"), cInt.of("*").mirror());
        assertEquals(cInt.of(-10, 10), cInt.of(0, 10).mirror());
        assertEquals(cInt.of(-10, 10), cInt.of(-2, 10).mirror());
        assertEquals(cInt.of(-20, 20), cInt.of(-20, -10).mirror());
        assertEquals(cInt.of(-20, 20), cInt.of(10, 20).mirror());
    }


    @Test
    public void testPredicateOps() {
        assertTrue(cInt.ZERO().isZeroable());
        assertTrue(cInt.of(-1, 1).isZeroable());
        assertFalse(cInt.of(-1, 1).within(cInt.ZERO()));
        assertFalse(cInt.of(1, 2).isZeroable());
        assertTrue(cInt.ZERO().within(cInt.of(-1, 1)));
        assertTrue(cInt.of(-1, 1).contains(cInt.ZERO()));
        assertFalse(cInt.of(1, 2).contains(cInt.ZERO()));
        assertFalse(cInt.of(-2, -1).contains(cInt.ZERO()));
        //    assertTrue(cInt.of((Long)null,(Long)null).contains(cInt.ZERO()));
        assertTrue(cInt.of(Long.MIN_VALUE, Long.MAX_VALUE).contains(cInt.ZERO()));
        //  assertTrue(cInt.MAYBE().contains(cInt.ZERO()));
        //   assertTrue(cInt.MAYBESOME().contains(cInt.ZERO()));
        assertFalse(cInt.SOME().contains(cInt.ZERO()));
        assertFalse(cInt.ZERO().contains(cInt.MAYBE()));
        assertFalse(cInt.ZERO().contains(cInt.MAYBESOME()));
        assertFalse(cInt.ZERO().contains(cInt.SOME()));
        assertTrue(cInt.ZERO().within(cInt.of(Long.MIN_VALUE, Long.MAX_VALUE)));
        assertTrue(cInt.ZERO().within(cInt.MAYBE()));
        assertTrue(cInt.of(1, 1).within(cInt.MAYBE()));
        assertTrue(cInt.of(0, 0).within(cInt.MAYBE()));
        assertFalse(cInt.of(1, 2).within(cInt.MAYBE()));
        assertFalse(cInt.of(-1, 0).within(cInt.MAYBE()));
        assertTrue(cInt.ZERO().within(cInt.MAYBESOME()));
        assertFalse(cInt.ZERO().within(cInt.SOME()));
        assertEquals(cInt.ONE(), cInt.ZERO().plus(cInt.ONE()));
        assertEquals(cInt.of(100), cInt.ZERO().plus(cInt.of(100)));
    }

    @Test
    public void testExactOps() {
        assertEquals(cInt.ZERO(), cInt.ZERO().plus(cInt.ZERO()));
        assertEquals(cInt.ONE(), cInt.ZERO().plus(cInt.ONE()));
        assertEquals(cInt.of(100), cInt.ZERO().plus(cInt.of(100)));
        assertEquals(cInt.of(100), cInt.of(44).plus(cInt.of(56)));
        assertEquals(cInt.of(2), cInt.ONE().plus(cInt.ONE()));
    }

    @Test
    public void testRangeOps() {
        assertEquals(cInt.of(22, 44), cInt.of(11, 22).plus(cInt.of(11, 22)));
        assertEquals(cInt.of(22, 81), cInt.of(21, 24).plus(cInt.of(1, 57)));
        assertEquals(cInt.of(-20, 81), cInt.of(-21, 24).plus(cInt.of(1, 57)));
        assertThrows(MTronException.class, () -> cInt.of(-24, -46));
        assertThrows(MTronException.class, () -> cInt.of(2, 0).plus(cInt.of(0, 2)));
    }

    @Test
    public void testComparators() {
        assertFalse(cInt.of(11, 22).gt(cInt.of(11, 22)));
        assertTrue(cInt.of(11, 22).gte(cInt.of(11, 22)));
        assertTrue(cInt.of(11, 22).equals(cInt.of(11, 22)));
        assertFalse(cInt.of(11, 22).lt(cInt.of(11, 22)));
        assertTrue(cInt.of(11, 22).lte(cInt.of(11, 22)));
        /// //
        assertTrue(cInt.of(22, 81).signeq(cInt.of(21, 24)));
        assertFalse(cInt.of(-81, -22).signeq(cInt.of(21, 24)));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{1}true                % true                 % true",
            "{1,23}true             % bool{1,23}::true     % true",
            "{+}true                % bool{1,}::true     % true",
            "{0}false               % noobj                % true",
            "{1}1                   % 1                    % true",
            "{1}1.0                 % 1.0                  % true",
            "{-2}1.02               % real{-2}::1.02       % true",
            "{-2,6}1.02             % real{-2,6}::1.02     % true",
            "{*}1.02                % real{*}::1.02        % true",
            "{-1}1                  % int{-1}::1           % true",
            "{-1}1                  % 1                    % false",
            "{-1,1}1                % int{??}::1           % true",
            "{-1}\"abc\"            % str{-1}::\"abc\"     % true",
            "{12}\"abc\"            % str{12}::\"abc\"     % true",
            "{12}a/b/c              % uri{12}::a/b/c       % true",
            "{12}a/b/c              % str{12}::\"a/b/c\"   % false",
            "{??}[a,b,c]            % lst{??}::[a,b,c]     % true",
            "{?}[a,b,c]             % lst{??}::[a,b,c]     % false",
            "{**}[a=>1,b=>2]        % rec{**}::[a=>1,b=>2] % true",
            "{??}[a=>1,b=>2]        % rec{-1,1}::[a=>1,b=>2] % true",
            "{**}[a=>1,b=>2]        % rec{,}::[a=>1,b=>2]    % true",
            "{??}[a=>1,b=>2]        % rec{??}::[a=>1,b=>2]   % true"
    }, delimiter = '%')
    public void testRewrites(final String code, final String expected, final boolean matches) throws Exception {
        AbstractMetatronTest.testEquals(LOG, mParser.eval(code), mParser.eval(expected), matches);
    }


}
