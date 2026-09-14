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

package studio.phaseshift.metatron.docs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * Site-html pass of the docs pipeline (MarkdownRunner --html), which moved in
 * from the deleted docs/SkillHtmlRenderer: body conversion is delegated to
 * HTMLMarkdownSerializer; the chrome — frontmatter h1 title, .md → .html href
 * rewriting, heading shift, header/footer — is assembled by
 * MarkdownRunner.renderSiteHtml. The multi-character mtron operators (=>, -<,
 * >=, <=, >>=) are ASCII in the source and must stay that way in the rendered
 * html — never typographically "compressed" into single Unicode look-alikes
 * such as ⇒, ≥ or ≤ (the flexmark engine HTMLMarkdownSerializer shares with the
 * old renderer passes them through verbatim).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MarkdownRunnerTest {

    // single-codepoint look-alikes that ligature fonts draw for the ASCII runs
    private static final String LOOKALIKES = "\u21D2\u21D0\u2265\u2264\u27F9\u27F8\u27FE\u2919\u291A"; // ⇒ ⇐ ≥ ≤ ⟹ ⟸ ⟾ ⤙ ⤚

    @Test
    public void testSkillsContainerClimbsToWebsiteSkillsDir(@TempDir final Path tmp) throws IOException {
        // ws/skills/mtron/references — the canonical container is the ancestor named "skills"
        final Path container = tmp.resolve("ws").resolve("skills");
        final Path references = container.resolve("mtron").resolve("references");
        Files.createDirectories(references.resolve("sub"));

        assertEquals(container, MarkdownRunner.skillsContainer(references));
        assertEquals(container, MarkdownRunner.skillsContainer(references.resolve("sub")));
        // a tree with no "skills" ancestor is not a website skills container (probe outputs)
        final Path plain = tmp.resolve("probe-output");
        Files.createDirectories(plain);
        assertNull(MarkdownRunner.skillsContainer(plain));
    }

    @Test
    public void testRenderedSiteFileKeepsOperators(@TempDir final Path tmp) throws IOException {
        // A minimal website skills container: <tmp>/skills/mtron/references/ops.md
        final Path references = tmp.resolve("skills").resolve("mtron").resolve("references");
        Files.createDirectories(references);
        final Path md = references.resolve("ops.md");
        Files.writeString(md, """
                ---
                name: ops
                description: operator fidelity probe
                ---

                # Operator fidelity

                Use `a=>b`, `x-<y`, `n>=0`, `n<=1` and `p>>=q` in code; `a=>b` again in prose.
                """);
        final Path skillsDir = tmp.resolve("skills");

        assertTrue(MarkdownRunner.renderSiteHtml(skillsDir) >= 1, "a new page must be written");
        assertFalse(MarkdownRunner.renderSiteHtml(skillsDir) >= 1, "a second pass must write nothing (idempotent)");
        final String html = Files.readString(references.resolve("ops.html"));

        // page chrome: the frontmatter owns the single h1 title, the description the line beneath it
        assertTrue(html.contains("class=\"skill-title"), "page must carry the frontmatter h1 chrome: " + html);
        assertTrue(html.contains("<h1 class=\"skill-title mb-1\">ops</h1>"),
                "the h1 must carry the frontmatter name: " + html);
        assertTrue(html.contains("<small>operator fidelity probe</small>"),
                "the description must sit under the h1: " + html);

        assertFalse(containsAny(html, LOOKALIKES),
                "rendered html file must not contain a compressed single-character look-alike");
        final String text = decode(html);
        for (final String operator : new String[]{"=>", "-<", ">=", "<=", ">>="}) {
            assertTrue(text.contains(operator),
                    "rendered html file must keep the operator " + operator + " verbatim");
        }
    }

    /**
     * The frontmatter value forms the skill docs actually write: a plain scalar on
     * one line, a plain scalar wrapped onto indented lines (which folds back to one
     * line), and the {@code |} / {@code >} block scalars with their chomping
     * indicators. A value owns every line indented deeper than its key — and
     * nothing else — so a following key at the key's own indentation closes it.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "description: operator fidelity%description%operator fidelity%a one-line plain scalar",
            "'description:\n  The /m/tble instruction set:\n    a JDBC database as a space.'%description%The /m/tble instruction set: a JDBC database as a space.%a plain scalar wrapped onto deeper lines folds to one line",
            "'description: |\n  one\n  two'%description%'one\ntwo'%a literal block keeps its line breaks",
            "'description: |-\n  one\n  two'%description%'one\ntwo'%literal with strip chomping",
            "'description: >\n  one\n  two'%description%one two%a folded block folds its breaks to spaces",
            "'description: >-\n  one\n  two'%description%one two%folded with strip chomping",
            "'description: >\n  one\n\n  two'%description%'one\ntwo'%a blank line in a folded block is a break",
            "'description: |\n  one\n    two'%description%'one\n  two'%deeper indentation survives in a literal block",
            "'description: |\n  one\n  two\nname: ops'%description%'one\ntwo'%a following key at the same indentation ends the block",
            "'description: |\nlist: x'%description%''%an empty block scalar",
            "'  description: |\n    one\n    two'%description%'one\ntwo'%an indented key owns its own block",
            "name: ops%description%''%a missing key yields the empty value",
            "namespace: x%name%''%a longer key must not match",
            "'namespace: x\ndescription: yes'%description%yes%a sibling key still matches",
    }, delimiter = '%')
    public void testFrontmatterScalarForms(final String front, final String key, final String expected, final String desc) {
        assertEquals(expected, MarkdownRunner.extract(front, key), desc);
    }

    /**
     * The bug this guards: a block-scalar (or wrapped) description used to reach
     * the page chrome truncated to its first line, because the extractor returned
     * the first non-blank line of the block and stopped there.
     */
    @Test
    public void testRenderedPageCarriesTheWholeDescription(@TempDir final Path tmp) throws IOException {
        final Path references = tmp.resolve("skills").resolve("mtron").resolve("references");
        Files.createDirectories(references);
        Files.writeString(references.resolve("tble.md"), """
                ---
                name: tble instruction set
                description:
                  The /m/tble instruction set and the tblespace::T it belongs to:
                    a JDBC database mounted as a metatron space.
                  TRIGGER: when a read may be pushed down into SQL.
                ---

                # tble

                body
                """);

        assertTrue(MarkdownRunner.renderSiteHtml(tmp.resolve("skills")) >= 1, "a new page must be written");
        final String html = Files.readString(references.resolve("tble.html"));
        assertTrue(html.contains("<small>The /m/tble instruction set and the tblespace::T it belongs to: "
                        + "a JDBC database mounted as a metatron space. TRIGGER: when a read may be pushed down into SQL.</small>"),
                "the whole description must reach the page chrome, not its first line: " + html);
    }

    @Test
    public void testSiteHtmlPassSkipsStrayMarkdownInContainer(@TempDir final Path tmp) throws IOException {
        // The container holds a real skill (SKILL.md + references/) and a stray .md
        // (mtron/assets/README.md). The site-html pass must render the skill docs and
        // skip the stray file — not crash climbing its parents up to the filesystem
        // root, where Path.getFileName() is null (docs/website/skills carries such a
        // stray: mtron/assets/README.md).
        final Path skillsDir = tmp.resolve("skills");
        final Path skillRoot = skillsDir.resolve("mtron");
        Files.createDirectories(skillRoot.resolve("references"));
        Files.createDirectories(skillRoot.resolve("assets"));
        Files.writeString(skillRoot.resolve("SKILL.md"), """
                ---
                name: mtron
                description: stray-file probe skill
                ---

                # The skill

                body here
                """);
        Files.writeString(skillRoot.resolve("references").resolve("ops.md"), """
                ---
                name: ops
                description: reference doc
                ---

                # Ops

                reference body
                """);
        Files.writeString(skillRoot.resolve("assets").resolve("README.md"), """
                # asset notes (not a skill doc — must neither crash nor render)
                """);

        assertTrue(MarkdownRunner.renderSiteHtml(skillsDir) >= 1, "the skill docs must be rendered");
        assertTrue(Files.exists(skillRoot.resolve("SKILL.html")), "SKILL.md must get a sibling html");
        assertTrue(Files.exists(skillRoot.resolve("references").resolve("ops.html")),
                "references/*.md must get a sibling html");
        assertFalse(Files.exists(skillRoot.resolve("assets").resolve("README.html")),
                "a stray non-skill .md must not be rendered to html");
        // page chrome must link the website stylesheet (depth-rewritten by loadWebsiteHeader)
        final String html = Files.readString(skillRoot.resolve("SKILL.html"));
        assertTrue(html.contains("css/metatron.css"), "page chrome must link the website stylesheet: " + html);
        assertEquals(0, MarkdownRunner.renderSiteHtml(skillsDir), "a second pass must write nothing (idempotent)");
    }

    /**
     * The freshness test the markdown pass now gates its write on. A doc whose blocks
     * all render as written — or that has no {@code mtron_pre} block at all — still has
     * to reach the website tree with its own prose and frontmatter, so the gate is the
     * <em>copy</em> being current, not evaluation having changed the text. {@code MISSING}
     * stands for "no copy on disk yet" and {@code +NL} for a line break (so the CSV
     * stays one row per case).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "body with no evaluated blocks%MISSING%false%a missing copy must be written",
            "body with no evaluated blocks%body with no evaluated blocks%true%an identical copy must not be rewritten",
            "body with no evaluated blocks+NL%body with no evaluated blocks%true%the writer strips trailing whitespace, so the source's trailing blank line is not a change",
            "body with no evaluated blocks%body with no evaluated blocks+NL%false%a copy still carrying a trailing blank line is stale (it is rewritten once, then stays current)",
            "edited body%[-- stale copy --]%false%a stale copy must be rewritten",
    }, delimiter = '%')
    public void testProcessedCopyFreshness(final String processed, final String existing, final boolean current,
                                          final String desc) throws IOException {
        final Path target = Files.createTempDirectory("processed-copy").resolve("doc.md");
        if (!"MISSING".equals(existing)) Files.writeString(target, existing.replace("+NL", "\n"));
        assertEquals(current, MarkdownRunner.isCurrent(target, processed.replace("+NL", "\n")), desc);
    }

    private static boolean containsAny(final String s, final String chars) {
        for (int i = 0; i < chars.length(); i++) {
            if (s.indexOf(chars.charAt(i)) >= 0) return true;
        }
        return false;
    }

    /// Flexmark-escape reversal (the html may legitimately carry the operators as &gt; / &lt;).
    private static String decode(final String html) {
        return html.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'");
    }
}
