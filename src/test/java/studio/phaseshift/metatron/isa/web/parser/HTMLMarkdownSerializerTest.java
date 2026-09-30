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

package studio.phaseshift.metatron.isa.web.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/*
 * Fidelity + coverage for the generalized markdown ⇄ html serializer that
 * replaced the docs SkillHtmlRenderer (deleted): the multi-character mtron
 * operators (=>, -<, >=, <=, >>=, ...) are ASCII in markdown and must stay that
 * way in the rendered html. HTMLMarkdownSerializer (flexmark, same engine and
 * extension set the website renderer used) passes them through verbatim — it
 * never typographically "compresses" them into single Unicode look-alikes such
 * as ⇒, ≥ or ≤. If they still *appear* as one glyph when a page is viewed, that
 * is the code font's ligatures at display time, not the html.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class HTMLMarkdownSerializerTest {

    // single-codepoint look-alikes that ligature fonts draw for the ASCII runs
    // (and that a "compressing" markdown pass might mistakenly emit)
    private static final String LOOKALIKES = "\u21D2\u21D0\u2265\u2264\u27F9\u27F8\u27FE\u2919\u291A"; // ⇒ ⇐ ≥ ≤ ⟹ ⟸ ⟾ ⤙ ⤚

    @ParameterizedTest
    @CsvSource(value = {
            "spaceless `a=>b` is a relation.                  % =>",
            "spaceless `x-<y` stays ascii.                    % -<",
            "`n>=0` is a lower bound.                         % >=",
            "`n<=1` is an upper bound.                        % <=",
            "`p>>=q` is a stream push.                        % >>=",
            "plain text a=>b, x-<y, n>=0, n<=1, p>>=q here.   % =>",
    }, delimiter = '%')
    public void testOperatorsPassThroughVerbatim(final String markdown, final String operator) {
        final String html = HTMLMarkdownSerializer.toHTML(markdown);
        assertFalse(containsAny(html, LOOKALIKES),
                "html must not contain a compressed single-character look-alike: " + html);
        assertTrue(decode(html).contains(operator),
                "decoded html must keep the " + operator.length() + "-char operator " + operator + ": " + html);
    }

    // markdown → html: structural coverage of the extension set the old
    // website renderer enabled (tables, strikethrough, task lists, autolink).
    @ParameterizedTest
    @CsvSource(value = {
            "# Title                    % <h1>Title</h1>",
            "## Sub                     % <h2>Sub</h2>",
            "### Sub3                   % <h3>Sub3</h3>",
            "a paragraph here.          % <p>a paragraph here.</p>",
            "use `code` inline.         % <code>code</code>",
            "*em* and **strong** text.  % <em>em</em>",
            "*em* and **strong** text.  % <strong>strong</strong>",
            "[link](https://x.dev)      % href=\"https://x.dev\"",
            "![alt](/img.png)           % src=\"/img.png\"",
            "~~gone~~                   % <del>gone</del>",
            "> quoted line              % <blockquote>",
    }, delimiter = '%')
    public void testMarkdownToHtmlStructure(final String markdown, final String expectedFragment) {
        final String html = HTMLMarkdownSerializer.toHTML(markdown);
        assertTrue(html.contains(expectedFragment),
                "rendered html must contain " + expectedFragment + ": " + html);
    }

    @Test
    public void testTable() {
        final String html = HTMLMarkdownSerializer.toHTML("""
                | a | b |
                |---|---|
                | 1 | 2 |
                """);
        assertTrue(html.contains("<table>"), "tables extension must render a table: " + html);
        assertTrue(html.contains("<th>a</th>"), "header cell must render: " + html);
        assertTrue(html.contains("<td>2</td>"), "body cell must render: " + html);
    }

    @Test
    public void testFencedCodeLanguage() {
        final String html = HTMLMarkdownSerializer.toHTML("```java\nint x = 1;\n```");
        assertTrue(html.contains("<pre>"), "fenced code must render a pre: " + html);
        assertTrue(html.contains("language-java"), "language must become a class: " + html);
        assertTrue(html.contains("int x = 1;"), "code body must be preserved: " + html);
    }

    @Test
    public void testTaskListAndAutolink() {
        final String html = HTMLMarkdownSerializer.toHTML("""
                - [ ] open task
                - [x] done task

                visit https://x.dev
                """);
        assertTrue(html.contains("checkbox"), "task list must render checkboxes: " + html);
        assertTrue(html.contains("checked"), "a done task must be checked: " + html);
        assertTrue(html.contains("href=\"https://x.dev\""), "bare url must autolink: " + html);
    }

    // ===================================================================
    //  html → markdown (FlexmarkHtmlConverter, house style: ATX + '-' + '---')
    // ===================================================================

    @Test
    public void testFrontMatterIsConsumedNotRendered() {
        // a leading YAML front-matter block (the skill docs' ---name/description---)
        // is consumed by the jekyll-front-matter extension, never rendered
        final String html = HTMLMarkdownSerializer.toHTML("""
                ---
                name: ops
                description: operator fidelity probe
                ---

                # Title
                """);
        assertFalse(html.contains("name:"), "front matter must not leak into html: " + html);
        assertFalse(html.contains("description:"), "front matter must not leak into html: " + html);
        assertTrue(html.contains("<h1>Title</h1>"), "the body must render: " + html);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "<h1>Title</h1>                              % # Title",
            "<h2>Sub</h2>                                % ## Sub",
            "<p>use <code>code</code> inline</p>         % `code`",
            "<p>a <strong>bold</strong> bit</p>          % **bold**",
            "<p>an <em>em</em> bit</p>                   % *em*",
            "<p>a <a href=\"https://x.dev\">link</a></p> % [link](https://x.dev)",
            "<ul><li>one</li><li>two</li></ul>           % - one",
            "<ol><li>first</li><li>second</li></ol>      % 1. first",
            "<blockquote><p>quoted</p></blockquote>      % > quoted",
            "<hr>                                        % ---",
    }, delimiter = '%')
    public void testHtmlToMarkdownStructure(final String html, final String expectedFragment) {
        final String markdown = HTMLMarkdownSerializer.toMarkdown(html);
        assertTrue(markdown.contains(expectedFragment),
                "converted markdown must contain " + expectedFragment + ": " + markdown);
    }

    @Test
    public void testHtmlToMarkdownTableAndCode() {
        final String markdown = HTMLMarkdownSerializer.toMarkdown("""
                <table><thead><tr><th>a</th><th>b</th></tr></thead>
                <tbody><tr><td>1</td><td>2</td></tr></tbody></table>
                <pre><code class="language-java">int x = 1;
                System.out.println(x);</code></pre>
                """);
        assertTrue(markdown.contains("| a | b |"), "table header must convert: " + markdown);
        assertTrue(markdown.contains("| 1 | 2 |"), "table body must convert: " + markdown);
        assertTrue(markdown.contains("```java"), "code fence with language must convert: " + markdown);
        assertTrue(markdown.contains("int x = 1;"), "code body must convert: " + markdown);
    }

    @Test
    public void testMarkdownHtmlMarkdownSmoke() {
        // a doc written in the house style survives the round trip's content:
        // md → html → md must keep its headings, bullets, and inline styles.
        final String markdown = "# Operator docs\n\n- one\n- two\n\nuse `a=>b` inline and **bold**.";
        final String back = HTMLMarkdownSerializer.toMarkdown(HTMLMarkdownSerializer.toHTML(markdown));
        assertTrue(back.contains("# Operator docs"), "heading must survive: " + back);
        assertTrue(back.contains("- one"), "bullet must survive: " + back);
        assertTrue(back.contains("`a=>b`"), "inline code with operator must survive: " + back);
        assertTrue(back.contains("**bold**"), "strong must survive: " + back);
    }

    // ===================================================================
    //  LaTeX math — the one place typography IS the point. Authored $…$ /
    //  $$…$$ (and MathJax's own \(…\) / \[…\]) is lifted out of the markdown
    //  before the parse and restored as \(…\) / \[…\], so the engine the site
    //  already loads sees delimiters it owns AND markdown's backslash escapes
    //  cannot eat the LaTeX (\{ → {, \, → ,, \\ → \ — the array row separator).
    // ===================================================================

    @ParameterizedTest
    @MethodSource("mathSpans")
    public void testAuthoredMathReachesThePageAsMathJaxDelimiters(final String markdown, final String expected) {
        final String html = HTMLMarkdownSerializer.toHTML(markdown);
        assertTrue(html.contains(expected), "html must carry " + expected + ": " + html);
        assertFalse(html.contains(String.valueOf('\uE000')), "no math placeholder may leak: " + html);
    }

    static Stream<Arguments> mathSpans() {
        return Stream.of(
                // the skill-doc intro paragraph that motivated the pass
                arguments("two vertex sets: $V = U \\cup O$. The set $O$ holds objs.", "\\(V = U \\cup O\\)"),
                arguments("two vertex sets: $V = U \\cup O$. The set $O$ holds objs.", "\\(O\\)"),
                // markdown backslash escapes would otherwise eat the LaTeX
                arguments("$\\{0\\} \\to \\{1\\}$", "\\(\\{0\\} \\to \\{1\\}\\)"),
                arguments("$a\\_1 + b_1$", "\\(a\\_1 + b_1\\)"),
                arguments("$\\int_0^1 x\\,dx$", "\\(\\int_0^1 x\\,dx\\)"),
                arguments("$\\begin{array}{c} 1 \\\\ 2 \\end{array}$", "1 \\\\ 2"),
                // display math
                arguments("$$\\int_0^1 x\\,dx$$", "\\[\\int_0^1 x\\,dx\\]"),
                arguments("\\[E = mc^2\\]", "\\[E = mc^2\\]"),
                // MathJax's own inline spelling is protected as well
                arguments("\\(\\mathrm{obj} = X\\)", "\\(\\mathrm{obj} = X\\)"),
                // the TeX is html-escaped, so it cannot break the page
                arguments("$a < b > c$", "\\(a &lt; b &gt; c\\)"),
                // every inline context flexmark offers
                arguments("## the set $\\mathbb{N}$", "<h2>the set \\(\\mathbb{N}\\)</h2>"),
                arguments("**bold $\\alpha$**", "<strong>bold \\(\\alpha\\)</strong>"),
                arguments("| $a_1$ | b |\n|---|---|\n| c | $d$ |", "<th>\\(a_1\\)</th>"),
                arguments("| $a_1$ | b |\n|---|---|\n| c | $d$ |", "<td>\\(d\\)</td>"),
                arguments("[link $\\beta$](https://x.dev)", ">link \\(\\beta\\)</a>")
        );
    }

    @ParameterizedTest
    @MethodSource("proseDollars")
    public void testProseDollarSignsAreNotMath(final String markdown, final String expected) {
        final String html = HTMLMarkdownSerializer.toHTML(markdown);
        assertTrue(html.contains(expected), "html must carry " + expected + " verbatim: " + html);
        assertFalse(html.contains("\\(") || html.contains("\\["),
                "metatron's dollar vocabulary must not become math: " + html);
        assertFalse(html.contains(String.valueOf('\uE000')), "no math placeholder may leak: " + html);
    }

    static Stream<Arguments> proseDollars() {
        return Stream.of(
                // a currency pair: pandoc's rules ($ need a non-space neighbour, no digit after)
                arguments("price is $5 and $6 a piece", "$5 and $6"),
                // metatron's ${…} interpolation splice, never math
                arguments("the pull (`src/.../${class}()`) returns", "<code>src/.../${class}()</code>"),
                arguments("prose ${_} binds the current one", "${_}"),
                // shell substitution inside a code span
                arguments("`--user $(id -u):$(id -g)`", "<code>--user $(id -u):$(id -g)</code>"),
                // sentinels and java class names
                arguments("`$DBRef` nested edges", "<code>$DBRef</code>"),
                arguments("a `Tuple$Pair` cast", "<code>Tuple$Pair</code>"),
                // an escaped dollar is a literal dollar
                arguments("cost \\$5 or \\$6 here", "$5 or $6"),
                // a fence is copied verbatim: were it scanned, this would become \(…\)
                arguments("```mtron_pre\necho \"$V = U \\cup O$\"\n```", "$V = U \\cup O$")
        );
    }

    @Test
    public void testFencedCodeKeepsBackslashParensVerbatim() {
        // the real doc line (sys-instset-mtron.md): a sed regexp in a mtron fence
        final String html = HTMLMarkdownSerializer.toHTML(
                "```mtron_pre\nbash('ls')==[_ => bash('stat ${_} | sed -n \"s/\\([0-9]*\\)/\\1/p\"')>>0]\n```");
        assertTrue(html.contains("\\([0-9]*\\)"),
                "a fenced regexp must survive the math pass untouched: " + html);
        assertFalse(html.contains(String.valueOf('\uE000')), "no math placeholder may leak: " + html);
    }

    /// Flexmark-escape reversal (the html may legitimately carry the operators as &gt; / &lt;).
    private static String decode(final String html) {
        return html.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'");
    }

    private static boolean containsAny(final String s, final String chars) {
        for (int i = 0; i < chars.length(); i++) {
            if (s.indexOf(chars.charAt(i)) >= 0) return true;
        }
        return false;
    }
}
