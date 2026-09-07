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
 * Fidelity guarantee for the skill-doc HTML pipeline: the multi-character mtron
 * operators (=>, -<, >=, <=, >>=, ...) are ASCII in the source and must stay that
 * way in the rendered HTML. SkillHtmlRenderer (flexmark) passes them through
 * verbatim — it never typographically "compresses" them into single Unicode
 * look-alikes such as ⇒, ≥ or ≤. If they still *appear* as one glyph when the
 * page is viewed, that is the code font's ligatures at display time, which the
 * site CSS disables via font-variant-ligatures: none on .markdown-body code.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SkillHtmlRendererTest {

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
        final String html = SkillHtmlRenderer.renderBody(markdown);
        assertFalse(containsAny(html, LOOKALIKES),
                "html must not contain a compressed single-character look-alike: " + html);
        assertTrue(decode(html).contains(operator),
                "decoded html must keep the " + operator.length() + "-char operator " + operator + ": " + html);
    }

    @Test
    public void testRenderedFileKeepsOperators(@TempDir final Path tmp) throws IOException {
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

        assertTrue(SkillHtmlRenderer.renderAll(skillsDir) >= 1, "a new page must be written");
        final String html = Files.readString(references.resolve("ops.html"));

        assertFalse(containsAny(html, LOOKALIKES),
                "rendered html file must not contain a compressed single-character look-alike");
        final String text = decode(html);
        for (final String operator : new String[]{"=>", "-<", ">=", "<=", ">>="}) {
            assertTrue(text.contains(operator),
                    "rendered html file must keep the operator " + operator + " verbatim");
        }
    }

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
