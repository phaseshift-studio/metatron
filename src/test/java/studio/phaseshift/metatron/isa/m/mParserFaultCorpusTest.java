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

package studio.phaseshift.metatron.isa.m;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.parser.mParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fault corpus: a fixed grid of pathological and ordinary mtron snippets, asserting what
 * the parser must keep doing to them.
 *
 * <p>Its purpose is to make the parser's error work safe. Adding an expectation label
 * ({@code mParser.labeled}) must never change <em>acceptance</em> — what parses today keeps
 * parsing — and must never move a diagnostic out of the document it belongs to. Those two
 * properties are asserted here, so the message wording itself is free to be rewritten: that is
 * the point of the exercise, and pinning prose is what has kept the wording bad.</p>
 *
 * <p>What is deliberately NOT asserted: the message text. Existing message fragments are pinned
 * in {@code mParserTest}, which will churn when the labels land; this corpus is the part that
 * must not.</p>
 */
public class mParserFaultCorpusTest extends AbstractMetatronTest {

    /**
     * Snippets that must parse. If a row starts failing, the grammar lost a case.
     */
    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(value = {
            // ── values ────────────────────────────────────────────────
            "1                                    % int",
            "-3.5                                 % negative real",
            "0xff                                 % bytes",
            "true                                 % bool",
            "noobj                                % noobj",
            "\"a string\"                         % double-quoted string",
            "'a string'                           % single-quoted string",
            // ── structure ────────────────────────────────────────────
            "rec[a=>1,b=>2]                       % record literal",
            "lst[1,2,3]                           % list literal",
            "[a=>1,b=>[c=>2]]                     % nested literal",
            "plus(1,2)                            % instruction call",
            "1.plus(2)                            % fluent call",
            "x -> 555                             % arrow sugar",
            "is?int(5)                            % prefix ? sugar",
            // ── uris and types ───────────────────────────────────────
            "*/usr/share                          % space path",
            "<http://example.com>                 % angle-wrapped uri",
            "int::29@/usr/marko/age               % typed, located int",
            "int::T                               % type reference",
            // ── comments ─────────────────────────────────────────────
            "1 [-- a note --]                     % trailing line comment",
            "1 + 2 [== a block ==]                % trailing block comment",
            "[-- nothing but a note --]           % comment-only document",
    }, delimiter = '%', quoteCharacter = '~')
    void testMustParse(final String code, final String desc) {
        final mParser.ParseDiagnostic diag = mParser.parseDiagnose(code);
        assertTrue(diag.success(),
                () -> desc + ": must parse, got: " + diag.formatted());
    }

    /**
     * Snippets that must fail, and must fail <em>legibly</em>: the diagnostic locates itself in
     * the document, and the explanation is prose rather than a PetitParser combinator dump.
     */
    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(value = {
            // ── unclosed delimiters ──────────────────────────────────
            "lst[1,2,3                           % unclosed list",
            "plus(1,2                            % unclosed call",
            "rec[a=>1                            % unclosed record",
            "<http://example.com                 % unclosed angle",
            // ── unbalanced closers ───────────────────────────────────
            "1]                                   % stray list closer",
            "1)                                   % stray paren closer",
            // ── dangling operators and terms ─────────────────────────
            "1 +                                  % dangling binary operator",
            "12ab                                 % two adjacent terms",
            "1 2                                  % two adjacent ints",
            ".5                                   % leading-dot real",
            "a.b                                  % dot used as a name separator",
            "[a b]                                % two terms in a list slot",
            "=>                                   % operator with no left side",
            "::                                   % type marker with no name",
            // ── quotes ───────────────────────────────────────────────
            "\"unterminated                       % unclosed double quote",
            "'unterminated                        % unclosed single quote",
    }, delimiter = '%', quoteCharacter = '~')
    void testMustFailLegibly(final String code, final String desc) {
        final mParser.ParseDiagnostic diag = mParser.parseDiagnose(code);
        assertFalse(diag.success(), () -> desc + ": must fail");

        // the diagnostic describes the document the caller sent, not a preprocessed buffer
        assertEquals(code, diag.buffer(), () -> desc + ": buffer must be the document as sent");
        assertTrue(diag.position() >= 0 && diag.position() <= code.length(),
                () -> desc + ": position " + diag.position() + " must be inside the document");

        // line and column are derived from that position, in that document
        final int lineStart = code.lastIndexOf('\n', diag.position() - 1) + 1;
        assertEquals(diag.position() - lineStart + 1, diag.column(),
                () -> desc + ": column must be the document column of the position");
        final long newlines = code.substring(0, diag.position()).chars().filter(c -> c == '\n').count();
        assertEquals((int) newlines + 1, diag.line(),
                () -> desc + ": line must be the document line of the position");

        assertLegible(diag.message(), desc);
        assertLegible(diag.formatted(), desc);
    }

    /**
     * The quality bar the message work is measured against, independent of wording: a person can
     * read it, and it does not leak the parser's combinator names or a Java stack trace.
     */
    private static void assertLegible(final String text, final String desc) {
        assertNotNull(text, () -> desc + ": expected an explanation, got null");
        assertFalse(text.isBlank(), () -> desc + ": expected an explanation, got blank");
        assertFalse(text.contains("Parser"),
                () -> desc + ": combinator names are not an explanation: " + text);
        assertFalse(text.contains("\tat "),
                () -> desc + ": a stack trace is not an explanation: " + text);
        assertFalse(text.contains("null"),
                () -> desc + ": 'null' leaked into the explanation: " + text);
        assertTrue(text.length() < 400,
                () -> desc + ": explanation is " + text.length() + " chars, too long to read: " + text);
    }

    /**
     * A document is not one expression: a broken statement must not silence the rest. This pins
     * the current contract of the multi-statement path while M4 is unbuilt — it reports what it
     * could read rather than throwing.
     */
    @Test
    void testMultiStatementStillYieldsWhatItCan() {
        assertDoesNotThrow(() -> mParser.parseMulti("a -> 1; b -> 2"),
                "two well-formed statements must parse");
    }
}
