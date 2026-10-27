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

package studio.phaseshift.metatron.isa.m.type;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE PREFIX TABLE IS THE INSTRUCTION SET'S, because a MOUNT is what gives the name: {@code import(/m/math, math)} is
 * one act, and it produces both the vocabulary and the shorthand. Memory holds no part of it, which is why the table
 * answers at {@code *</instset/prefix>} and nowhere else.
 * <p>
 * The rows below are the live form of the (never-subclassed, therefore never-run) {@code AbstractRouterTest}: a
 * prefixed spelling denotes exactly what its aligned form denotes, and the short name inside the prefix is placed by
 * memory's redirect — {@code math:cos} is {@code /m/math/inst/cos}, not {@code /m/math/cos}.
 */
public class InstSetPrefixTest extends AbstractMetatronTest {

    @TestData(value = {
            "import(/m/math,math)"}, oneTime = true)
    @ParameterizedTest
    @CsvSource(value = {
            "math:pi       | /m/math/pi",         // a const lives directly under /m/math/
            "math:e        | /m/math/e",
            "math:e{3}     | /m/math/e{3}",
            "math:cos      | /m/math/inst/cos",   // an inst lives under /m/math/inst/ — the redirect places it
            "math:inst/cos | /m/math/inst/cos",   // a full path — naive extension under the prefix's root
    }, delimiter = '|')
    void testTheSpellingDenotesTheAlignedAddress(final String small, final String big) {
        assertEquals(Machine.read(f(big)), Machine.read(f(small)), small + " denotes " + big);
    }

    /**
     * BOTH DIRECTIONS OF THE TABLE'S LIFE: a name binds, the binding is addressable where it is kept, and unbinding is
     * the universal close — after it the spelling denotes nothing rather than denoting somewhere else.
     */
    @TestData(value = {
            "import(/m/math,math)"}, oneTime = true)
    @Test
    void testTheTableIsAddressableAndRetractable() {
        final fURI tag = f("tag");
        final InstSet isa = Machine.current().instset();
        isa.registerPrefix(tag, f("/m/math"));
        try {
            assertEquals(Machine.read(f("/m/math/pi")), Machine.read(f("tag:pi")));
            // THE TABLE ANSWERS WHERE IT LIVES — the instset's own rec, not memory's
            final Obj table = Machine.read(f("/instset/prefix"));
            assertTrue(table.isRec(), "the prefix table is the instset's: " + table);
            assertEquals(f("/m/math"), table.asRec().at(uri("tag")).uriValue());
        } finally {
            isa.unregisterPrefix(tag);
        }
        assertTrue(Machine.read(f("tag:pi")).isNoObj(), "an unbound name denotes nothing");
    }
}
