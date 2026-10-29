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

package studio.phaseshift.metatron.isa.m.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractSerializerTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class ObjmtronSerializerTest extends AbstractSerializerTest<String> {
    public ObjmtronSerializerTest() {
        super(ObjmtronSerializer.single(), null, null);
    }

    /**
     * AN ADDRESSED URI SURVIVES THE WIRE. The inherited corpus is TEXT IN, TEXT OUT, so it can only contain spellings
     * the parser already accepts — and a bare uri carrying a vid was not one of them: {@code @} was a legal bare-uri
     * character, so {@code /wire/noobj@<a3f91c2b>} read back as ONE uri whose value ended in {@code @}, the address
     * swallowed into the value. That is a wire-wide hole (any uri read from an address and re-sent lost its vid) that
     * only a JAVA-BUILT case can express, which is why this row could not live in {@link #testSerializeDeserializeObj}'s
     * corpus and why the round trip's absent-answer mark depended on it.
     */
    @Test
    void testAnAddressedUriKeepsItsAddress() {
        final Obj addressed = uri(f("/mtron/test")).selfVID(f("a3f91c2b"));
        final String text = ObjmtronSerializer.single().write(addressed);
        LOG.debug("an addressed uri on the wire: %s", text);
        checkEquality(LOG, addressed, ObjmtronSerializer.single().read(text), true);
    }

    /**
     * THE TWO POSITIONS ARE ONE SPELLING: a value that carries an {@code @} is written wrapped, and the vid postfix
     * goes OUTSIDE the brackets — {@code <user@mail>@abc}. The wrapped form closes at {@code >}, so the postfix can
     * never be read as part of the value: no {@code @} fighting, and user-info comes back without the bare-uri
     * charset having to allow it.
     * <p>
     * THE VALUE HERE HAS NO DOT ON PURPOSE. A dotted value would be bracketed for the dot's sake alone, and this
     * test would then pass even if the {@code @} rule were deleted; {@code user@mail} can only be bracketed because
     * of its {@code @}, which is what makes the assertion a guard rather than a description.
     */
    @Test
    void testAUserInfoUriCarriesAVidOutsideItsBrackets() {
        final Obj addressed = uri(f("user@mail")).selfVID(f("abc"));
        final String text = ObjmtronSerializer.single().write(addressed);
        LOG.debug("an addressed user-info uri on the wire: %s", text);
        assertEquals("<user@mail>@abc", text, "the value is wrapped and the vid sits outside the brackets");
        final Obj read = ObjmtronSerializer.single().read(text);
        assertEquals(f("abc"), read.vid(), "the vid comes back outside the brackets");
        checkEquality(LOG, addressed, read, true);
    }

    /**
     * A URI THAT REALLY CARRIES AN {@code @} IS WRITTEN WRAPPED — the other half of the same rule: {@code @} is not a
     * bare-uri character (it is the vid separator), so the writer has to wrap such a value, and the wrapped form still
     * parses. User-info in an authority is the case that will need this for real.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "/mtron/test                       % /mtron/test",
            // no dot in either value: the brackets can only come from the '@' (a dotted value is bracketed for the
            // dot's sake, which would hide a deleted '@' rule)
            "user@mail                         % <user@mail>",
            "ws://user@host:8555/mtron         % <ws://user@host:8555/mtron>",
            "user@domain.com                   % <user@domain.com>",
            "ws://user@domain.com:8555/mtron   % <ws://user@domain.com:8555/mtron>",
    }, delimiter = '%')
    void testAUriIsWrittenWithAnUnambiguousSpelling(final String value, final String expected) {
        final Obj written = ObjmtronSerializer.parse(expected).apply();
        checkEquality(LOG, uri(f(value)), written, true);
        checkEquality(LOG, uri(f(value)), ObjmtronSerializer.single().read(ObjmtronSerializer.single().write(uri(f(value)))), true);
    }
}
