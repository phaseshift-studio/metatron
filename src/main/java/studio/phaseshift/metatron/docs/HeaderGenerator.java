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

import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Regenerates the "instruction sets" and "agent skills" dropdowns in the website
 * navigation header from the committed site artifacts.
 *
 * <p>Instruction sets are discovered from the generated pages under
 * {@code docs/website/instset} (one {@code m_&lt;name&gt;.html} per instruction
 * set); the menu icon is derived by convention as
 * {@code images/icons/space/&lt;leaf&gt;-icon.svg} (with the root {@code /m} mapping
 * to {@code mtron}). Skills are discovered from {@code docs/website/skills}, one
 * directory per skill ({@code SKILL.md} plus {@code references/*.md}).</p>
 *
 * <p>Both dropdowns are delimited by HTML comment markers in
 * {@code docs/website/includes/header.html}: {@code <!-- INSTSET-MENU:START/END -->}
 * and {@code <!-- SKILLS-MENU:START/END -->}.</p>
 *
 * <p>Idempotent: the header is only written when a regenerated dropdown differs
 * from what is already present, so a normal site build leaves no spurious diff.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 * java studio.phaseshift.metatron.docs.HeaderGenerator
 *      [-i &lt;instset-dir&gt;] [-c &lt;icons-dir&gt;] [-s &lt;skills-dir&gt;] [-h &lt;header-file&gt;]
 * </pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class HeaderGenerator {

    private static final GraphittyLogger LOG = Graphitty.log(HeaderGenerator.class);

    private static final String INSTSET_START = "<!-- INSTSET-MENU:START -->";
    private static final String INSTSET_END = "<!-- INSTSET-MENU:END -->";
    private static final String SKILLS_START = "<!-- SKILLS-MENU:START -->";
    private static final String SKILLS_END = "<!-- SKILLS-MENU:END -->";

    public static void main(final String[] args) throws IOException {
        Path instsetDir = Path.of("docs/website/instset");
        Path iconsDir = Path.of("docs/website/images/icons/space");
        Path skillsDir = Path.of("docs/website/skills");
        Path headerFile = Path.of("docs/website/includes/header.html");
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-i", "--instsets" -> instsetDir = Path.of(args[++i]);
                case "-c", "--icons" -> iconsDir = Path.of(args[++i]);
                case "-s", "--skills" -> skillsDir = Path.of(args[++i]);
                case "-h", "--header" -> headerFile = Path.of(args[++i]);
                default -> LOG.error("[header] ignoring unknown arg: %s", args[i]);
            }
        }

        final List<InstSet> instsets = scanInstSets(instsetDir, iconsDir);
        final List<Skill> skills = scanSkills(skillsDir);

        final String header = Files.readString(headerFile);
        String updated = replaceRegion(header, INSTSET_START, INSTSET_END, renderInstSetMenu(instsets));
        updated = replaceRegion(updated, SKILLS_START, SKILLS_END, renderSkillsMenu(skills));

        if (updated.equals(header)) {
            LOG.info("[header] unchanged (%d instruction sets, %d skills)", instsets.size(), skills.size());
        } else {
            Files.writeString(headerFile, updated);
            LOG.info("[header] wrote %d instruction sets and %d skills into %s", instsets.size(), skills.size(), headerFile);
        }
    }

    /**
     * Replace the content between {@code startMarker} and {@code endMarker} with
     * {@code menu}, preserving the markers and their line indentation. Returns the
     * header unchanged when either marker is absent.
     */
    private static String replaceRegion(final String header, final String startMarker, final String endMarker,
                                        final String menu) {
        final int start = header.indexOf(startMarker);
        final int end = start < 0 ? -1 : header.indexOf(endMarker, start + startMarker.length());
        if (start < 0 || end < 0) {
            LOG.error("[header] markers %s / %s not found — skipping", startMarker, endMarker);
            return header;
        }
        final int lineStart = header.lastIndexOf('\n', start - 1) + 1;
        final String indent = header.substring(lineStart, start);
        final String replacement = startMarker + "\n" + menu + "\n" + indent + endMarker;
        return header.substring(0, start) + replacement + header.substring(end + endMarker.length());
    }

    /**
     * Walk the generated instruction-set pages, deriving each set's vid, page
     * filename, and icon name. A set is included only when its icon file exists
     * ({@code &lt;leaf&gt;-icon.svg}), which drops sets that lack a published icon.
     */
    private static List<InstSet> scanInstSets(final Path instsetDir, final Path iconsDir) throws IOException {
        if (!Files.isDirectory(instsetDir)) {
            LOG.error("[header] not a directory: %s", instsetDir);
            return List.of();
        }
        final List<InstSet> instsets = new ArrayList<>();
        try (final var files = Files.list(instsetDir)) {
            final List<String> names = files.filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(n -> n.endsWith(".html") && !"index.html".equals(n))
                    .sorted()
                    .toList();
            for (final String name : names) {
                final String vid = "/" + name.replaceFirst("\\.html$", "").replace("_", "/");
                final String icon = iconName(leafName(vid));
                if (!Files.isRegularFile(iconsDir.resolve(icon + "-icon.svg"))) continue;
                instsets.add(new InstSet(vid, name, icon));
            }
        }
        return instsets;
    }

    /**
     * Walk the skill directory and collect each skill — a subdirectory holding a
     * {@code SKILL.md} plus optional {@code references/}, {@code scripts/}, and
     * {@code assets/} directories.
     */
    private static List<Skill> scanSkills(final Path skillsDir) throws IOException {
        if (!Files.isDirectory(skillsDir)) {
            LOG.error("[header] not a directory: %s", skillsDir);
            return List.of();
        }
        final List<Skill> skills = new ArrayList<>();
        try (final var dirs = Files.list(skillsDir)) {
            for (final Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                if (!Files.isRegularFile(dir.resolve("SKILL.md"))) continue;
                skills.add(new Skill(dir.getFileName().toString(),
                        listReferences(dir.resolve("references")),
                        listDirectFiles(dir.resolve("scripts")),
                        listDirectFiles(dir.resolve("assets"))));
            }
        }
        return skills;
    }

    /**
     * The (sorted) {@code .md} basenames under a skill's {@code references/}
     * directory, without their extension.
     */
    private static List<String> listReferences(final Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (final var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(n -> n.endsWith(".md"))
                    .map(n -> n.substring(0, n.length() - ".md".length()))
                    .sorted()
                    .toList();
        }
    }

    /**
     * The (sorted) direct file names under a directory — used for {@code scripts/}
     * and {@code assets/}, which are raw resources.
     */
    private static List<String> listDirectFiles(final Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (final var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }
    }

    /**
     * Render the instruction-sets dropdown: an "overview" entry, a divider, then
     * one item per instruction set (icon + vid, linking to its generated page).
     */
    private static String renderInstSetMenu(final List<InstSet> instsets) {
        final StringBuilder sb = new StringBuilder();
        sb.append("            <li class=\"nav-item dropdown\">\n");
        sb.append("                <a id=\"isa_dropdown\" class=\"nav-link dropdown-toggle\" href=\"#\" role=\"button\" data-bs-toggle=\"dropdown\"\n");
        sb.append("                   aria-expanded=\"false\">\n");
        sb.append("                    instruction sets\n");
        sb.append("                </a>\n");
        sb.append("                <ul class=\"dropdown-menu dropdown-menu-dark\" aria-labelledby=\"isa_dropdown\">\n");
        sb.append("                    <li><a class=\"code dropdown-item d-flex align-items-center\" href=\"./instset/index.html\">\n");
        sb.append("                        <i class=\"bi bi-grid-3x3-gap-fill me-2\"></i>overview</a></li>\n");
        sb.append("                    <li><hr class=\"dropdown-divider\"></li>\n");
        for (final InstSet is : instsets) {
            sb.append("                    <li><a class=\"code dropdown-item d-flex align-items-center\" href=\"./instset/").append(is.filename()).append("\"><img\n");
            sb.append("                            src=\"./images/icons/space/").append(is.icon())
                    .append("-icon.svg\" class=\"isa-row icon-color me-2\"\n");
            sb.append("                            alt=\"").append(is.icon()).append("\"/>").append(is.vid()).append("</a></li>\n");
        }
        sb.append("                </ul>\n");
        sb.append("            </li>");
        return sb.toString();
    }

    /**
     * Render the skills dropdown as an accordion. Every skill links to its
     * {@code SKILL.html}; skills with resources (references/scripts/assets) get a
     * chevron that expands them in place, one open at a time via
     * {@code data-bs-parent}.
     */
    private static String renderSkillsMenu(final List<Skill> skills) {
        final StringBuilder sb = new StringBuilder();
        sb.append("            <li class=\"nav-item dropdown\">\n");
        sb.append("                <a id=\"skills_dropdown\" class=\"nav-link dropdown-toggle\" href=\"#\" role=\"button\"\n");
        sb.append("                   data-bs-toggle=\"dropdown\" data-bs-auto-close=\"outside\" aria-expanded=\"false\">\n");
        sb.append("                    agent skills\n");
        sb.append("                </a>\n");
        sb.append("                <ul class=\"dropdown-menu dropdown-menu-dark\" id=\"skills-accordion\" aria-labelledby=\"skills_dropdown\">\n");
        for (final Skill skill : skills) {
            if (!skill.hasResources()) {
                sb.append("                    <li><a class=\"code dropdown-item d-flex align-items-center\" href=\"./skills/")
                        .append(skill.name()).append("/SKILL.html\"><i class=\"bi bi-robot icon-color me-2 isa-row\"></i>")
                        .append(skill.name()).append("</a></li>\n");
            } else {
                sb.append("                    <li class=\"skill-group\">\n");
                sb.append("                        <div class=\"d-flex align-items-center\">\n");
                sb.append("                            <a class=\"code dropdown-item skill-link d-flex align-items-center flex-grow-1\" href=\"./skills/")
                        .append(skill.name()).append("/SKILL.html\"><i class=\"bi bi-robot icon-color me-2 isa-row\"></i>")
                        .append(skill.name()).append("</a>\n");
                sb.append("                            <button class=\"skill-chevron\" type=\"button\" data-bs-toggle=\"collapse\" data-bs-target=\"#skill-")
                        .append(skill.name()).append("\" aria-expanded=\"false\" aria-label=\"").append(skill.name())
                        .append(" resources\"><i class=\"bi bi-chevron-down\"></i></button>\n");
                sb.append("                        </div>\n");
                sb.append("                        <div id=\"skill-").append(skill.name())
                        .append("\" class=\"collapse\" data-bs-parent=\"#skills-accordion\">\n");
                sb.append("                            <div class=\"skill-resources\">\n");
                appendResourceGroup(sb, skill.name(), "references", skill.references(), "/references/", ".html");
                appendResourceGroup(sb, skill.name(), "scripts", skill.scripts(), "/scripts/", "");
                appendResourceGroup(sb, skill.name(), "assets", skill.assets(), "/assets/", "");
                sb.append("                            </div>\n");
                sb.append("                        </div>\n");
                sb.append("                    </li>\n");
            }
        }
        sb.append("                </ul>\n");
        sb.append("            </li>");
        return sb.toString();
    }

    /**
     * Append one resource group: a label plus one link per file. {@code linkSuffix}
     * is {@code ".html"} for rendered references and empty for raw scripts/assets.
     */
    private static void appendResourceGroup(final StringBuilder sb, final String skillName, final String label,
                                            final List<String> files, final String subdir, final String linkSuffix) {
        if (files.isEmpty()) return;
        sb.append("                                <div class=\"skill-resource-group\">").append(label).append("</div>\n");
        for (final String file : files) {
            sb.append("                                <a class=\"skill-resource dropdown-item\" href=\"./skills/")
                    .append(skillName).append(subdir).append(file).append(linkSuffix).append("\">")
                    .append(file).append("</a>\n");
        }
    }

    /**
     * Leaf name from a vid path: {@code /m/web} → {@code web}.
     */
    private static String leafName(final String vid) {
        return vid.substring(vid.lastIndexOf('/') + 1);
    }

    /**
     * Map a leaf name to its icon filename (without extension): the root
     * {@code m} uses {@code mtron}.
     */
    private static String iconName(final String leaf) {
        return "m".equals(leaf) ? "mtron" : leaf;
    }

    /**
     * An instruction set's vid (label), generated page filename, and icon name.
     */
    private record InstSet(String vid, String filename, String icon) {
    }

    /**
     * A skill's name and the (sorted) files under its optional resources:
     * {@code references/*.md} (rendered), {@code scripts/*}, and {@code assets/*}
     * (raw). A skill is defined by its {@code SKILL.md} plus these optional dirs.
     */
    private record Skill(String name, List<String> references, List<String> scripts, List<String> assets) {
        boolean hasResources() {
            return !references().isEmpty() || !scripts().isEmpty() || !assets().isEmpty();
        }
    }
}
