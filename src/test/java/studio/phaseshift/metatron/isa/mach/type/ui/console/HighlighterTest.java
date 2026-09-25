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

package studio.phaseshift.metatron.isa.mach.type.ui.console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The language parameter of {@link Highlighter} names a file in {@code conf/nanorc},
 * not a jline syntax name: jline resolves a syntax by EXACT equality, so {@code java}
 * — the name of the file — is not what {@code conf/nanorc/java.nanorc} declares
 * ({@code Java}), and passing it through falls to a same-named system nanorc or, on a
 * host without {@code /usr/share/nano}, to no highlighting at all.  The token is
 * therefore resolved through the file ({@link Highlighter#syntaxName(String)}), and a
 * block of source is colorized in one pass ({@link Highlighter#highlightBlock}).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class HighlighterTest extends AbstractMetatronTest {

    /**
     * The token names the file; the file declares the name jline needs.  A token with
     * no nanorc of its own — including a language this machine happens to have a
     * system nanorc for — means plain text, never someone else's rules.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "java       % Java",
            "Java       % Java",
            "JAVA       % Java",
            "json       % JSON",
            "javascript % JavaScript",
            "js         % JavaScript",
            "py         % python",
            "yml        % yaml",
            "md         % Markdown",
            "mtron      % mtron",
            "sql        % SQL",
            "html       % HTML",
            "txt        % null",
            "text       % null",
            "plain      % null",
            "cobol      % null",
            "nope       % null",
    }, delimiter = '%')
    void testTokenResolvesThroughTheNanorcFile(final String token, final String expected) {
        assertEquals("null".equals(expected) ? null : expected, Highlighter.syntaxName(token),
                token + " names a conf/nanorc file (or means plain text)");
    }

    @Test
    void testNoTokenMeansPlainText() {
        assertNull(Highlighter.syntaxName(null), "a null token is plain text");
        assertNull(Highlighter.syntaxName(""), "an empty token is plain text");
        assertNull(Highlighter.syntaxName("  "), "a blank token is plain text");
    }

    /**
     * A block is colorized in ONE pass: jline carries its start/end rule state across
     * the block's lines, so a javadoc comment opened on line two is still a comment on
     * line three — the property that rules out colorizing a block line by line.
     */
    @Test
    void testHighlightBlockSpansLines() {
        final String code = """
                class A {
                  /** the answer
                      is 42 */
                  int x = 42;
                }""";
        final String highlighted = Highlighter.highlightBlock("java", code);
        assertTrue(highlighted.contains("\u001B[94m/** the answer"),
                "the javadoc comment opens (" + highlighted.replace("\u001B", "\\e") + ")");
        assertTrue(highlighted.contains("\u001B[94m      is 42 */"),
                "the comment carries to its close (" + highlighted.replace("\u001B", "\\e") + ")");
    }

    /**
     * A block never re-enters the DSL, so source carrying braces reaches the terminal
     * intact.  The line-oriented entry point routes such a line through Graphitty and
     * eats them — a block is exactly the case that must not.
     */
    @Test
    void testHighlightBlockKeepsBracesIntact() {
        final String code = "Map<String, String> m = Map.of(\"answer\", \"{{b}}\");";
        assertTrue(Highlighter.highlightBlock("java", code).contains("{{b}}"),
                "the braces of source code survive");
    }

    /**
     * A token with no syntax leaves the text alone: no escapes, no markers, no
     * surprises for a widget measuring it.
     */
    @ParameterizedTest
    @CsvSource(value = {"txt", "plain", "cobol"}, delimiter = '%')
    void testNoSyntaxMeansVerbatim(final String language) {
        assertEquals("int x = 42;", Highlighter.highlightBlock(language, "int x = 42;"),
                language + " adds nothing to the text");
        assertEquals("", Highlighter.highlightBlock(language, ""), "an empty block stays empty");
    }

    /**
     * A widget re-renders its whole body on every scroll, so a block must come from
     * the memo the second time round — a highlight pass per pass is what a wheel
     * notch used to feel like.
     */
    @Test
    void testHighlightBlockIsMemoized() {
        final String code = "int " + UUID.randomUUID().toString().replace("-", "") + " = 42;";
        final int before = Highlighter.highlightedBlockCacheSize();
        final String first = Highlighter.highlightBlock("java", code);
        final String second = Highlighter.highlightBlock("java", code);
        assertSame(first, second, "a repeated block is served from the memo");
        assertEquals(before + 1, Highlighter.highlightedBlockCacheSize(), "the memo holds the one new block");
    }

    /**
     * The same token must not mean a different rule set per machine.  {@code python} is
     * declared by {@code conf/nanorc/python.nanorc} AND by the system nanorcs, so
     * {@code jnanorc} lists metatron's own syntaxes first; the conf rules color
     * {@code def} bright blue where the system copy colors it bright cyan.
     */
    @Test
    void testConfNanorcWinsOverTheSystemSyntax() {
        final String highlighted = Highlighter.highlightBlock("python", "def f(self): return None");
        assertTrue(highlighted.contains("\u001B[94mdef"),
                "conf/nanorc/python.nanorc rules are in force (" + highlighted.replace("\u001B", "\\e") + ")");
    }

    /**
     * A line carrying a block tag is not a line — it is one end of a wrap over several
     * lines, so it is handed back untouched for the pass that renders the whole body.
     * A widget colorizes and measures its body line by line, and only the surface's
     * own pass over the composed string can see a block whole; expanding one end of it
     * here would drop the tag or leave its end tag with nothing to close.
     */
    /**
     * A {@code {{syntax:…}}} block in the shape the serializer's string output takes — a
     * tagged, quoted multi-line value in a markup context — must be colorized by its own
     * nanorc and NOBODY ELSE'S.  The old path colored the code with the line's syntax
     * first and the block's language second, and a second {@code toAnsi()} pass over text
     * that already carries escapes rewrites them lossily: escapes split in the middle and
     * their fragments ({@code 34m}, {@code 32m}, …) print as literal text at the left of
     * the block's lines — the "funny characters" a highlighted HTML page used to start
     * with.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "html % <html lang=\"en\"><head><title>Marko A. Rodriguez</title></head><body>42</body></html>",
            "java % class Answer { int x = 42; }",
    }, delimiter = '%')
    void testSyntaxBlockInAMarkupContextKeepsItsEscapesIntact(final String language, final String code) {
        final String str = "{{syntax:" + language + "}}\n" + code + "\n{{/syntax:" + language + "}}\n";
        final String rendered = Highlighter.format(str);
        assertFalse(rendered.replaceAll("\u001B\\[[0-9;]*[a-zA-Z]", "").contains("\u001B"),
                "no escape may be split or naked: " + rendered.replace("\u001B", "\\e"));
        assertEquals("\n" + code + "\n\n", Highlighter.unformat(rendered),
                "the block's code — and nothing else — is what a reader sees: " + rendered.replace("\u001B", "\\e"));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "txt        % {{b}}bold{{X}}",
            "txt        % {{syntax:java}}",
            "java       % {{syntax:java}}",
            "java       % {{/syntax:java}}",
            "cobol      % {{b}}bold{{X}}",
            "java       % ```java",
            "java       % ```",
    }, delimiter = '%')
    void testLinesThatBelongToALargerPassAreHandedBack(final String language, final String line) {
        assertEquals(line, Highlighter.highlightLine(language, line),
                "the caller's Graphitty pass renders this line: " + language);
    }

    /**
     * Under a real syntax a markup line is expanded rather than colorized: escapes a
     * highlighter injects inside a tag would break it for the pass that renders the
     * body.
     */
    @Test
    void testMarkupLineUnderASyntaxIsExpanded() {
        final String expanded = Highlighter.highlightLine("java", "{{b}}bold{{X}}");
        assertEquals(-1, expanded.indexOf("{{"),
                "the markup is resolved, not passed through (" + expanded.replace("\u001B", "\\e") + ")");
    }

    /**
     * The widget entry point resolves the token too, so a widget asking for
     * {@code java} reaches {@code conf/nanorc/java.nanorc} rather than a system syntax
     * of the same name — the two agree on keywords and differ on literals (conf colors
     * {@code 42} yellow, Ubuntu's copy colors it green).
     */
    @Test
    void testHighlightLineResolvesTheToken() {
        final String line = Highlighter.highlightLine("java", "int x = 42;");
        assertTrue(line.contains("\u001B[33m42"),
                "conf/nanorc/java.nanorc colors the literal (" + line.replace("\u001B", "\\e") + ")");
    }

    /**
     * A markdown fence routes the string through Graphitty — the same pass that renders
     * {@code {{syntax:…}}} blocks — so its code is colorized from the nanorc file rather
     * than left to the mtron highlighter, and it measures as it renders.
     */
    @Test
    void testFormatRoutesAMarkdownFenceThroughGraphitty() {
        final String highlighted = Highlighter.format("```java\nint x = 42;\n```");
        assertTrue(highlighted.contains("\u001B["),
                "the fence block is colorized (" + highlighted.replace("\u001B", "\\e") + ")");
        assertEquals("\nint x = 42;\n", Highlighter.unformat(highlighted), "the fence measures as its code");
    }

    /**
     * A ``` fence serialized as an Obj (the REPL result path) must keep its color —
     * the Obj branch routes Graphitty markup to {@code writeToString} verbatim rather
     * than through the AttributedString round-trip that drops the escapes.
     */
    @Test
    void testFormatObjWithFenceKeepsItsColor() {
        final studio.phaseshift.metatron.isa.m.type.Obj strObj =
                studio.phaseshift.metatron.isa.m.type.impl.MStr.str("\n```java\npublic static void main();\n```\n");
        final String highlighted = Highlighter.format(strObj);
        assertTrue(highlighted.contains("\u001B["),
                "the fence block keeps its color through the Obj path: " + highlighted.replace("\u001B", "\\e"));
        assertTrue(Highlighter.unformat(highlighted).contains("public static void main();"),
                "the code text is still there: " + Highlighter.unformat(highlighted));
    }

    /**
     * The standard console result path (not split mode) writes the serialized result
     * through Graphitty directly, not through the reader's mtron highlighter — a fence
     * must stay colorized after the serialization round-trip.
     */
    @Test
    void testSerializedFenceColorizesThroughGraphitty() {
        final studio.phaseshift.metatron.isa.m.type.Obj strObj =
                studio.phaseshift.metatron.isa.m.type.impl.MStr.str("\n```java\npublic static void main();\n```\n");
        final String serialized = new studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer().write(strObj);
        final String rendered = studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty.string(serialized);
        assertTrue(rendered.contains("\u001B["),
                "serialization + Graphitty keeps the fence color (the standard result path): "
                        + rendered.replace("\u001B", "\\e"));
    }
}
