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
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
