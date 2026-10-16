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
