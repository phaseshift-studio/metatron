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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * The "agent skills" dropdown is generated from the published skills tree, so what it
 * lists is decided by HeaderGenerator's folder scans. A skill folder holds payload
 * beside its documents — diagram png/svg, SQLite dumps, a folder README — and none of
 * those is a page a reader can open, so the scan must drop them while keeping the
 * resources that ARE documents (a .py script, a .jsonl dataset, an extensionless
 * binary like `scripts/wsplus`).
 */
public class HeaderGeneratorTest {

    @Test
    public void testSkillsMenuDropsPayloadButKeepsResources(@TempDir final Path tmp) throws IOException {
        final Path skill = tmp.resolve("mtron");
        Files.createDirectories(skill.resolve("references"));
        Files.createDirectories(skill.resolve("scripts"));
        Files.createDirectories(skill.resolve("assets"));
        Files.writeString(skill.resolve("SKILL.md"), "---\nname: mtron\n---\n");
        // kept: documents and raw resources
        Files.writeString(skill.resolve("references/type-system-mtron.md"), "---\nname: t\n---\n");
        Files.writeString(skill.resolve("references/README.md"), "a folder note, not a reference\n");
        Files.writeString(skill.resolve("scripts/wsplus"), "#!/usr/bin/env python3\n");
        Files.writeString(skill.resolve("scripts/unsloth_studio.py"), "print('x')\n");
        Files.writeString(skill.resolve("assets/mtron_training_dataset.jsonl"), "{}\n");
        // dropped: payload
        Files.writeString(skill.resolve("assets/README.md"), "the assets folder's own note\n");
        Files.writeString(skill.resolve("assets/cat-lift.png"), "\u0089PNG\n");
        Files.writeString(skill.resolve("assets/mach-stages.png"), "\u0089PNG\n");
        Files.writeString(skill.resolve("assets/diagram.svg"), "<svg/>\n");
        Files.writeString(skill.resolve("assets/drstynx.sqlite"), "SQLite format 3\n");

        final String menu = HeaderGenerator.renderSkillsMenu(HeaderGenerator.scanSkills(tmp));
        for (final String kept : List.of("references/type-system-mtron.html", "scripts/wsplus",
                "scripts/unsloth_studio.py", "assets/mtron_training_dataset.jsonl")) {
            assertTrue(menu.contains(kept), "the menu must still list " + kept + ": " + menu);
        }
        for (final String dropped : List.of("README", "cat-lift", "mach-stages", "diagram.svg", ".sqlite")) {
            assertFalse(menu.contains(dropped), "the menu must not list " + dropped + ": " + menu);
        }
    }

    @Test
    public void testInstSetMenuRendersATree() {
        final List<HeaderGenerator.InstSet> instsets = List.of(
                new HeaderGenerator.InstSet("/m", "m.html", "mtron", null),
                new HeaderGenerator.InstSet("/m/db/tble", "m_db_tble.html", "tble", "./skills/mtron/references/tble-instset-mtron.html"),
                new HeaderGenerator.InstSet("/m/db/vec", "m_db_vec.html", "vec", null),
                new HeaderGenerator.InstSet("/m/ai/llm", "m_ai_llm.html", "llm", "./skills/mtron/references/llm-instset-mtron.html"),
                new HeaderGenerator.InstSet("/m/dev/ide", "m_dev_ide.html", "ide", null),
                new HeaderGenerator.InstSet("/m/math/cat", "m_math_cat.html", "cat", "./skills/mtron/references/cat-instset-mtron.html"),
                new HeaderGenerator.InstSet("/m/sys/ui", "m_sys_ui.html", "ui", "./skills/mtron/references/ui-instset-mtron.html"),
                new HeaderGenerator.InstSet("/m/web/rdf", "m_web_rdf.html", "rdf", null));

        final String menu = HeaderGenerator.renderInstSetMenu(instsets);

        // leaves that name an instruction set are links to their generated page
        assertTrue(menu.contains("./instset/m_db_tble.html"), "tble leaf links: " + menu);
        assertTrue(menu.contains("./instset/m_ai_llm.html"), "llm leaf links");
        // the root /m is itself an instruction set, so it is a link, not a folder
        assertTrue(menu.contains("./instset/m.html"), "/m links to its own page");
        assertTrue(menu.contains("alt=\"mtron\"/>") && menu.contains("/m</a>"), "/m label present");
        // intermediate namespaces (/m/db, /m/dev, /m/ai) are folders, not links
        assertTrue(menu.contains("isa-folder"), "intermediate namespaces render as folders: " + menu);
        assertTrue(menu.contains("/m/db</li>"), "db folder header present");
        assertTrue(menu.contains("/m/dev</li>"), "dev folder header present");
        assertTrue(menu.contains("/m/ai</li>"), "ai folder header present");
        // a folder never links (no href for a pure namespace)
        assertFalse(menu.contains("href=\"./instset/m_db.html\""), "db is a folder, not a page");
        // depth classes indent children under their parent
        assertTrue(menu.contains("isa-depth-1"), "depth-1 indent present");
        assertTrue(menu.contains("isa-depth-2"), "depth-2 indent present");
        // children follow their parent in document order (pre-order traversal)
        final int dbFolder = menu.indexOf("/m/db</li>");
        final int tbleLeaf = menu.indexOf("./instset/m_db_tble.html");
        final int vecLeaf = menu.indexOf("./instset/m_db_vec.html");
        assertTrue(dbFolder < tbleLeaf && tbleLeaf < vecLeaf,
                "db children render under the db folder, in order: " + menu);
        // a set with a reference doc gets a [doc] pill next to its link
        assertTrue(menu.contains("./skills/mtron/references/tble-instset-mtron.html\">doc</a>"),
                "tble carries a doc pill: " + menu);
        assertTrue(menu.contains("class=\"article-row\""), "doc-bearing sets render as article rows");
        assertTrue(menu.contains("class=\"article-format\""), "the doc pill reuses the article-format box");
    }

    @Test
    public void testUnlistedFilesAreDecidedByTheFilename(@TempDir final Path tmp) {
        // extensionless resources are listed; the extension is matched case-insensitively
        assertTrue(HeaderGenerator.isListed(tmp.resolve("wsplus")), "an extensionless resource is listed");
        assertTrue(HeaderGenerator.isListed(tmp.resolve("dataset.jsonl")));
        assertTrue(HeaderGenerator.isListed(tmp.resolve("SKILL.md")));
        assertTrue(HeaderGenerator.isListed(tmp.resolve("notes.txt")));
        assertFalse(HeaderGenerator.isListed(tmp.resolve("cat-lift.png")));
        assertFalse(HeaderGenerator.isListed(tmp.resolve("CAT-LIFT.PNG")), "the extension is case-insensitive");
        assertFalse(HeaderGenerator.isListed(tmp.resolve("diagram.svg")));
        assertFalse(HeaderGenerator.isListed(tmp.resolve("drstynx.sqlite")));
        // Path.endsWith compares path elements, not name suffixes: the old filter let
        // every .sqlite dump through
        assertFalse(tmp.resolve("drstynx.sqlite").endsWith(".sqlite"));
        // a folder's own note, whatever its case
        assertFalse(HeaderGenerator.isListed(tmp.resolve("README.md")));
        assertFalse(HeaderGenerator.isListed(tmp.resolve("readme.md")));
    }
}
