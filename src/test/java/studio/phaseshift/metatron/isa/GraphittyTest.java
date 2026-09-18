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

package studio.phaseshift.metatron.isa;

import org.jline.jansi.Ansi;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.EmojiTable;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.jline.jansi.Ansi.ansi;
import static org.junit.jupiter.api.Assertions.*;

public class GraphittyTest extends AbstractMetatronTest {

    @Test
    public void testRewrites() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Graphitty g = new Graphitty(Map.of("abc", "hello"), out);
        /*g.print("{{abc}} here");
        assertEquals("hello here", out.toString());
        out.reset();
        g.print("{{r}}red{{/r}}");
        assertEquals(ansi().fg(Ansi.Color.RED).a("red").reset().toString(), out.toString());
        out.reset();*/
        g.print("{{r}}red{{g}}green{{/g}}back to{{/r}}");
        assertEquals(ansi().fg(Ansi.Color.RED).a("red").fg(Ansi.Color.GREEN).a("green").reset().fg(Ansi.Color.RED).a("back to").reset().toString(), out.toString());
    }

    // ── Emoji shortcodes ({{:name:}}) ───────────────────────────────

    @ParameterizedTest
    @CsvSource(value = {
            "{{:beer:}}                 % 🍺",
            "{{:beer:}} cheers          % 🍺 cheers",
            "{{:us:}}                   % 🇺🇸",
            "{{:rocket:}}               % 🚀",
            "raw 🐿 emoji               % raw 🐿 emoji",
            "{{:definitely_not_an_emoji:}} % :definitely_not_an_emoji:",
    }, delimiter = '%')
    void testEmojiShortcode(final String code, final String expected) {
        assertEquals(expected, Graphitty.string(code));
    }

    @Test
    public void testEmojiComposesWithColor() {
        final String s = Graphitty.string("{{:beer:&b}}");
        assertTrue(s.contains("🍺"), "emoji should render alongside the color rule: " + s);
        assertTrue(s.contains("\033[34m"), "{{:beer:&b}} should also emit the blue color rule: " + s);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "beer   % 🍺",
            "rocket % 🚀",
    }, delimiter = '%')
    void testEmojiTableLookup(final String name, final String expected) {
        assertEquals(expected, EmojiTable.get(name));
    }

    // ── Syntax blocks ({{syntax:lang}} … {{/syntax:lang}}) ──────────

    /**
     * Everything between the tags belongs to the language named in the tags and is
     * colorized from a conf/nanorc file.  The tags themselves never reach the
     * terminal, and the block measures exactly as it renders — the
     * strip/viewLength property every widget width calculation rides on.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "int x = 42;              % java       % a capitalized declaration",
            "def f(self): return None % python     % a lowercase declaration",
            "key: value               % yaml       % a lowercase declaration",
            "SELECT * FROM space      % sql        % another capitalized declaration",
            "a -> b?                  % mtron      % the metatron syntax",
            "const x = 42;            % js         % an alias of the javascript file",
    }, delimiter = '%')
    void testSyntaxBlockIsColorizedAndMeasuresVerbatim(final String code, final String language, final String desc) {
        final String rendered = Graphitty.string("{{syntax:%s}}%s{{/syntax:%s}}".formatted(language, code, language));
        assertEquals(code, Graphitty.strip(rendered), "a syntax block measures as it renders: " + desc);
        assertEquals(code.length(), Graphitty.viewLength(rendered), "color adds no visual length: " + desc);
        assertTrue(rendered.contains("\u001B["), "a syntax block is colorized (" + Graphitty.strip(rendered) + "): " + desc);
    }

    /**
     * A block is NOT a verbatim region — the markup of the document around it keeps
     * working inside it, which is what lets a widget draw its border and its colours
     * THROUGH the lines of a block it renders.  Code that needs to show a tag uses the
     * DSL escape, {@code \{\{ … \}\}}.
     */
    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', delimiter = '|', value = {
            "{{r}}red{{X}}       | red          | an inner colour rule still applies",
            "Map.of(a, {{b}})    | Map.of(a, )  | a tag inside code is markup, as anywhere else",
            "printf('%d of %s'); | printf('%d of %s'); | percent tokens survive the formatter"})
    void testMarkupInsideABlockStillApplies(final String content, final String visible, final String desc) {
        final String tagged = "{{syntax:txt}}" + content + "{{/syntax:txt}}";
        assertEquals(visible, Graphitty.strip(tagged), "the block shows this text: " + desc);
        assertEquals(Graphitty.strip(tagged), Graphitty.strip(Graphitty.string(tagged)),
                "and it measures as it renders: " + desc);
    }

    /**
     * The escape produces text that LOOKS like a tag, so it is checked on its own: the
     * agreement assertion above cannot hold for it (measuring an already-rendered string
     * strips what looks like markup — the same is true of the escaped text anywhere).
     */
    @Test
    void testEscapedTagInsideABlockStaysLiteral() {
        assertEquals("{{b}}", Graphitty.strip("{{syntax:txt}}\\{\\{b\\}\\}{{/syntax:txt}}"),
                "the DSL escape shows a tag as text");
    }

    /**
     * A single leading backslash renders a tag literally: the escape consumes the
     * first brace, leaving a lone brace that cannot open a tag, so the tag text and
     * its closing braces fall through untouched.  One backslash is enough — the
     * fully-escaped form is the same result with more noise.  The escape is per-tag,
     * not a line-wide verbatim mode: a following real tag still fires.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "\\{{g}}            % {{g}}            % a color rule",
            "\\{{blah}}         % {{blah}}         % a rule the parser does not know",
            "\\{{syntax:java}}  % {{syntax:java}}  % a syntax open tag",
            "\\{{/syntax:java}} % {{/syntax:java}} % a syntax end tag",
            "\\{{[r]&y}}        % {{[r]&y}}        % a chained background+foreground rule",
    }, delimiter = '%')
    void testSingleBackslashShowsTagVerbatim(final String code, final String expected, final String desc) {
        assertEquals(expected, Graphitty.string(code), desc);
    }

    /**
     * A widget draws its border and colours THROUGH the lines of a block it renders, so a
     * block's markup is interleaved with its code: the decoration must still apply (it is
     * markup like any other) while the code is colorized as code.  This is the shape a
     * live accordion produced — the reason a block cannot be a verbatim region.
     */
    @Test
    void testMarkupInterleavedThroughABlockStillApplies() {
        final String composed = "{{X}}|{{X}} {{syntax:java}}class A {" + "  " + "{{X}}|{{X}}\n"
                + "{{X}}|{{X}} int x = 42;" + "  " + "{{X}}|{{X}}\n"
                + "{{X}}|{{X}} {{/syntax:java}}{{X}}|{{X}}\n";
        final String rendered = Graphitty.string(composed);
        assertEquals(-1, rendered.indexOf("{{"), "no tag text leaks into the border (" + rendered.replace("\u001B", "\\e") + ")");
        assertTrue(rendered.contains("\u001B[32mint"), "the code inside the border is colorized (" + rendered.replace("\u001B", "\\e") + ")");
        assertEquals(Graphitty.strip(composed), Graphitty.strip(rendered), "and the block measures as it renders");
    }

    /**
     * A block is colorized in ONE pass, so a comment opened on its first line stays
     * a comment on the next (the property that rules out caching a block per line),
     * and the block still measures as it renders.
     */
    @Test
    void testSyntaxBlockSpansLines() {
        final String code = "class A {\n  /** doc\n      line */ int x;\n}";
        final String rendered = Graphitty.string("{{syntax:java}}" + code + "{{/syntax:java}}");
        assertEquals(code, Graphitty.strip(rendered), "a multi-line block measures as it renders");
        assertTrue(rendered.contains("\u001B[94m/** doc"), "the javadoc comment is colorized (" + rendered.replace("\u001B", "\\e") + ")");
        assertTrue(rendered.contains("\u001B[94m      line */"), "the comment carries to its close (" + rendered.replace("\u001B", "\\e") + ")");
    }

    /**
     * An unterminated block renders what it captured instead of throwing: the flush
     * belongs to the outermost parse, so a tag stream that never closed still shows
     * its text.
     */
    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', delimiter = '|', value = {
            "dangling                     | java | a block that was never closed",
            "IDENTIFICATION DIVISION.     | cobol | a language with no conf/nanorc file renders verbatim"})
    void testUnterminatedOrUnknownSyntaxBlockStillRenders(final String code, final String language, final String desc) {
        final String rendered = Graphitty.string("{{syntax:%s}}%s".formatted(language, code));
        assertEquals(code, Graphitty.strip(rendered), "an open block still renders its text: " + desc);
    }

    /**
     * The end tag must name the language the open tag named — a long source file
     * makes the block obvious, so a mismatch is a producer bug, reported the way
     * every other unmatched rule wrap is.
     */
    @Test
    @Disabled("now lenient so console doesn't get corrupted on widget resizing")
    void testMismatchedSyntaxEndTagIsRejected() {
        final MTronException e = assertThrows(MTronException.class,
                () -> Graphitty.string("{{syntax:java}}int x = 42;{{/syntax:sql}}"));
        assertTrue(e.getMessage().contains("unmatched syntax wrap"), "the mismatch names both rules: " + e.getMessage());
    }

    /**
     * Measuring runs the same block capture the render does (tags dropped, code kept), so
     * a widget's width for a line of a block is the width of what is drawn on it.
     */
    @ParameterizedTest
    @CsvSource(value = {"txt", "java", "mtron"}, delimiter = '%')
    void testSyntaxBlockMeasuresAsItRenders(final String language) {
        final String code = "Map.of(answer, value)";
        final String tagged = "{{syntax:%s}}%s{{/syntax:%s}}".formatted(language, code, language);
        assertEquals(code, Graphitty.strip(tagged), "the block measures as its own text: " + language);
        assertEquals(code.length(), Highlighter.visualLength(tagged), "a widget measures the block as it shows it: " + language);
    }

    /**
     * A widget measures its body ONE LINE AT A TIME, so a block's tags arrive without
     * each other: a line holds only the open tag, another only the end tag.  Measuring
     * a lone tag is harmless — it is dropped, and a rule that merely CONTAINS the
     * prefix (the end tag, a rule named something-syntax:java) names no language.
     */
    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', delimiter = '|', value = {
            "{{/syntax:java}}            | <none>      | an end tag alone measures as nothing",
            "{{syntax:java}}             | <none>      | an open tag alone measures as nothing",
            "int x = 42;{{/syntax:java}} | int x = 42; | an end tag on the last body line",
            "{{not_syntax:java}}code     | code        | a rule merely containing the prefix is not a language"})
    void testSyntaxTagWithoutItsBlockStillMeasures(final String line, final String expected, final String desc) {
        assertEquals("<none>".equals(expected) ? "" : expected, Graphitty.strip(line),
                "measuring a lone block tag is harmless: " + desc);
    }

    /**
     * A block is a wrap like any other: what encloses it resumes after it, and a
     * block at the top level closes with the plain reset.
     */
    @Test
    void testEnclosingRuleResumesAfterSyntaxBlock() {
        final String rendered = Graphitty.string("{{c}}before {{syntax:txt}}code{{/syntax:txt}} after{{/c}}");
        assertEquals("before code after", Graphitty.strip(rendered), "the block is part of the surrounding text");
        assertTrue(rendered.startsWith("\u001B[36m"), "cyan opens the line (" + rendered.replace("\u001B", "\\e") + ")");
        assertTrue(rendered.contains("\u001B[0;36m after"), "cyan resumes after the block (" + rendered.replace("\u001B", "\\e") + ")");
    }

    // ── Markdown code fences (```lang … ```) ──────────────────────

    /**
     * A markdown fence is the same block as {@code {{syntax:lang}}}: the markers are
     * dropped but their lines remain (each becomes an empty line), the code between
     * them is colorized from the language's conf/nanorc file, and the block measures
     * exactly as it renders.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "int x = 42;              % java   % a capitalized declaration",
            "def f(self): return None % python % a lowercase declaration",
            "key: value               % yaml   % a lowercase declaration",
            "SELECT * FROM space      % sql    % another capitalized declaration",
            "a -> b?                  % mtron  % the metatron syntax",
    }, delimiter = '%')
    void testMarkdownFenceIsColorizedAndMeasuresVerbatim(final String code, final String language, final String desc) {
        final String rendered = Graphitty.string("```%s\n%s\n```".formatted(language, code));
        assertEquals("\n" + code + "\n", Graphitty.strip(rendered), "a fence measures as its code: " + desc);
        assertEquals(("\n" + code + "\n").length(), Graphitty.viewLength(rendered), "color adds no visual length: " + desc);
        assertTrue(rendered.contains("\u001B["), "a fence is colorized (" + Graphitty.strip(rendered) + "): " + desc);
    }

    /**
     * A fence is colorized in ONE pass, so a comment opened on its first line stays a
     * comment on the next, and it still measures as it renders.
     */
    @Test
    void testMarkdownFenceSpansLines() {
        final String code = "class A {\n  /** doc\n      line */ int x;\n}";
        final String rendered = Graphitty.string("```java\n" + code + "\n```");
        assertEquals("\n" + code + "\n", Graphitty.strip(rendered), "a multi-line fence measures as its code");
        assertTrue(rendered.contains("\u001B[94m/** doc"), "the javadoc comment is colorized (" + rendered.replace("\u001B", "\\e") + ")");
        assertTrue(rendered.contains("\u001B[94m      line */"), "the comment carries to its close (" + rendered.replace("\u001B", "\\e") + ")");
    }

    /**
     * An unterminated fence renders what it captured instead of throwing, like a
     * {@code {{syntax:…}}} block left open.
     */
    @Test
    void testUnterminatedFenceStillRenders() {
        assertEquals("\nint x = 42;", Graphitty.strip("```java\nint x = 42;"), "an open fence still renders its text");
    }

    /**
     * Measuring a lone fence line is harmless: a fence names no language without its
     * block, so it measures as nothing.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "```java % an open fence alone",
            "```      % a close fence alone",
    }, delimiter = '%')
    void testLoneFenceLineMeasuresAsNothing(final String line, final String desc) {
        assertEquals("", Graphitty.strip(line), "measuring a lone fence is harmless: " + desc);
    }

    /**
     * A fence inside a {@code {{syntax:…}}} block is just code: the block's own
     * delimiter is the tag, so the fence arrives verbatim, not as a nested block.
     */
    @Test
    void testFenceInsideASyntaxBlockIsCode() {
        final String tagged = "{{syntax:txt}}```java\nint x = 42;\n```{{/syntax:txt}}";
        assertEquals("```java\nint x = 42;\n```", Graphitty.strip(tagged), "a fence inside a block is code");
    }

    /**
     * A fence is a wrap like any other, so markup inside it still applies — the code
     * is captured around the color rules, and the block measures as its own text.
     */
    @Test
    void testMarkupInsideAFenceStillApplies() {
        final String tagged = "```txt\n{{b}}bold{{X}}\n```";
        assertEquals("\nbold\n", Graphitty.strip(tagged), "markup inside a fence still applies");
    }

    // ── Fences are tokens, not line starts ─────────────────────────

    /**
     * A fence is a token like {@code {{…}}}, found anywhere in the stream: a widget's
     * border markup can precede the open fence, and the code inside the border is still
     * colorized with no fence text leaking through.
     */
    @Test
    void testFenceAfterBorderMarkupStillOpens() {
        final String composed = "{{X}}|{{X}} ```java\n{{X}}|{{X}} int x = 42;\n{{X}}|{{X}} ```\n";
        final String rendered = Graphitty.string(composed);
        assertEquals(-1, rendered.indexOf("```"), "no fence text leaks into the border (" + rendered.replace("\u001B", "\\e") + ")");
        assertTrue(rendered.contains("\u001B[32mint"), "the code inside the border is colorized (" + rendered.replace("\u001B", "\\e") + ")");
        assertEquals(Graphitty.strip(composed), Graphitty.strip(rendered), "the block measures as it renders");
    }

    /**
     * The close fence need not sit on its own line: code followed by {@code ```} on the
     * same line is a complete block.
     */
    @Test
    void testClosingFenceOnTheSameLineAsCode() {
        final String rendered = Graphitty.string("```java\nint x = 42;```");
        assertEquals("\nint x = 42;", Graphitty.strip(rendered), "a close on the code's line measures as its code");
        assertTrue(rendered.contains("\u001B["), "the same-line block is still colorized (" + Graphitty.strip(rendered) + ")");
    }

    /**
     * A fence keeps its delimiter newlines — the open fence's newline is the blank line
     * before the code and the close fence's newline the blank line after, so the line
     * count is preserved.  A close sharing a line with code contributes no blank line of
     * its own; an own-line close does.
     */
    @Test
    void testFenceKeepsItsDelimiterNewlines() {
        assertEquals("\nint x = 42;\nnext", Graphitty.strip("```java\nint x = 42;```\nnext"),
                "a same-line close leaves the code's own newline");
        assertEquals("\nint x = 42;\n\nnext", Graphitty.strip("```java\nint x = 42;\n```\nnext"),
                "an own-line close adds a blank line before what follows");
    }

    // ── Escaping a fence ───────────────────────────────────────────

    /**
     * A backslash before a backtick shows a literal backtick, so {@code \```java}
     * renders as text rather than opening a fence — the fence's counterpart of the
     * single backslash that makes {@code \{{syntax:java}}} literal.
     */
    @Test
    void testBackslashEscapesAFence() {
        assertEquals("```java", Graphitty.string("\\```java"), "a backslash before the first backtick shows the fence as text");
        assertEquals("```java", Graphitty.strip("\\```java"), "and it measures as the literal text");
        assertEquals(-1, Graphitty.string("\\```java").indexOf("\u001B["), "no color — it is not a block");
    }

    /**
     * Escaping BOTH fences shows the whole snippet as text: neither marker opens or
     * closes a block, so the code between them is plain prose, not colorized.
     */
    @Test
    void testEscapedFenceRendersTheWholeBlockAsText() {
        final String escaped = "\\```java\npublic static void method() { }\n\\```";
        final String rendered = Graphitty.string(escaped);
        assertEquals("```java\npublic static void method() { }\n```", rendered,
                "both fences escaped render literally");
        assertEquals(-1, rendered.indexOf("\u001B["), "no block is opened, so no color");
    }

    // ── A fence is bounded to its language token ──────────────────

    /**
     * The fence open consumes only its language token, never "the rest of the line".
     * A floating widget composes its lines with ANSI cursor codes, not newlines, so a
     * fence that ran to the next {@code \n} would swallow the whole render buffer —
     * including the {@code \033[u} restore-cursor that un-traps the terminal (the
     * "cursor stuck in the widget" bug).  The restore-cursor must survive.
     */
    @Test
    void testFenceDoesNotSwallowACursorCodeBuffer() {
        final String composed = "\033[s ```java \033[6;1H int x = 42; ``` \033[u";
        final String rendered = Graphitty.string(composed);
        assertTrue(rendered.endsWith("\033[u"), "the restore-cursor survives (" + rendered.replace("\u001B", "\\e") + ")");
        assertEquals(-1, rendered.indexOf("```"), "the fence markers are consumed, not the buffer");
    }

    // ── Fences are line-preserving ─────────────────────────────────

    /**
     * A fence keeps its delimiter newlines, so the markers become empty lines and the
     * rendered text has the same line count as the input — the property a floating
     * widget (or the terminal buffer) relies on, and the reason {@code string()} is the
     * uniform algorithm for all Graphitty text.
     */
    @Test
    void testStringIsLinePreservingForFences() {
        final String rendered = Graphitty.string("before\n```java\nint x = 42;\n```\nafter");
        assertEquals("before\n\nint x = 42;\n\nafter", Graphitty.strip(rendered),
                "the fence markers become empty lines");
        assertEquals(5, rendered.split("\n", -1).length, "the line count is preserved");
        assertTrue(rendered.contains("\u001B[32mint"), "the code is still colorized (" + rendered.replace("\u001B", "\\e") + ")");
    }

}
