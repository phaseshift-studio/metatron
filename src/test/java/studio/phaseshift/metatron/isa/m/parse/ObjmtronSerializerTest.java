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
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
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

    /**
     * STRINGS ON THE WIRE: every quoting shape a str can be written in has to survive {@code write()}→{@code read()}.
     * <p>
     * A str is carried by QUOTING, not by escaping, so the whole risk is a value that can spell its own closing
     * delimiter: {@code """} in the content must not end the render early, and a quote at the very end must not leave
     * it unbalanced. The corpus is the mtron LITERAL itself — empty, one character, blank space at either end, an
     * apostrophe, a double quote, both at once, and the wire's own triple-quote delimiter inside the value — because
     * a row the reader cannot express would be testing the corpus, not the wire.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "''",                                     // empty
            "'a'",                                    // one character
            "' '",                                    // one blank
            "'  x  '",                                // blank space at both ends
            "'a b c'",                                // spaces inside
            "\"\"\"abc\"\"\"",                        // triple quoted
            "\"it's\"",                               // an apostrophe, double quoted
            "'say \"hi\"'",                           // double quotes, single quoted
            "\"\"\"it's a \"quoted\" word\"\"\"",     // both quote kinds at once
            "'\"\"\"'",                               // the value IS the triple-quote delimiter
            "'a\"\"\"b'",                             // the delimiter inside the value
            "'ends in a quote\"'",                    // one quote at the very end
            "'\\\\'",                                 // a backslash
            "'\\n'",                                  // backslash-n: two characters, as a regex or a path carries
            "'it\\'s'",                               // the delimiter escaped, as only the fallback form needs
            "\"say \\\"hi\\\"\"",                     // a double quote escaped
            "'a\\\\b'",                               // an escaped escape character
            "'\\\\s'",                                // an unknown escape is NOT decoded: it stays backslash-s
            "'trailing\\\\'",                         // a backslash at the very end
            "'{}[](),%#><@!?'",                       // punctuation that doubles as syntax everywhere else
    })
    void testAStringKeepsEveryQuotingShapeAcrossTheWire(final String literal) {
        final Obj parsed = ObjmtronSerializer.parse(literal).apply();
        assertTrue(parsed.isStr(), () -> "not a str literal: " + literal);
        final String wire = ObjmtronSerializer.single().write(parsed);
        LOG.debug("testing {{b}}%s{{/b}} serialized to {{b}}%s{{/b}}", literal, wire);
        assertEquals(parsed, ObjmtronSerializer.single().read(wire),
                () -> "wire form '" + wire + "' did not read back as the value it wrote");
    }

    /**
     * THE CASE NO LITERAL CAN EXPRESS: a value carrying an apostrophe, a double quote, AND the triple-quote delimiter
     * — {@code it's """ here}. No single-, double- or triple-quoted literal spells it (that is why it cannot be a CSV
     * row: the corpus is literals, and this is the value Python's <code>'''</code>/<code>"""</code> problem leaves to
     * escaping). The writer must still render a value the reader gets back unchanged; if it cannot, this is the row
     * that says so.
     */
    @Test
    void testAStringWithBothQuoteKindsAndTheWireDelimiter() {
        final Obj value = str("it's \"\"\" here");
        final String wire = ObjmtronSerializer.single().write(value);
        LOG.debug("a mixed-quote string on the wire: %s", wire);
        assertEquals(value, ObjmtronSerializer.single().read(wire),
                () -> "wire form '" + wire + "' did not read back as the value it wrote");
    }

    /**
     * A SPANNED INSTRUCTION KEEPS ITS SPAN ACROSS THE WIRE. The span is the instruction's LEADING coefficient
     * ({@code {m,n}op}) and the parser reads it back into the tid's span; {@code basePath()} carries no coefficient, so
     * the writer dropped it — a branch lane's narrowed share printed (and could be re-read) as the whole run. The round
     * trip is the assertion: the text must carry the span AND read back as the code it wrote.
     */
    @Test
    void testASpannedInstructionKeepsItsSpanAcrossTheWire() {
        final Obj code = ObjmtronSerializer.parse("start(0).{0,4}plus(1).sum()").asCode();
        final String wire = ObjmtronSerializer.single().write(code);
        LOG.debug("a spanned chain on the wire: %s", wire);
        assertTrue(wire.contains("{0,4}"), () -> "the span is written: " + wire);
        // READ IS THE WIRE'S OWN READER: a code expression is applied, so the round trip is the parser's code (the
        // text is a program, and reading it as a value would run it).
        checkEquality(LOG, code, ObjmtronSerializer.parse(wire).asCode(), true);
    }
}
