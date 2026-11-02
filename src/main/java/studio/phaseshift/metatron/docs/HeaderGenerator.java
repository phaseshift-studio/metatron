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

import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.GraphittyLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regenerates the "instruction sets" and "agent skills" dropdowns in the website
 * navigation header from the committed site artifacts.
 *
 * <p>Instruction sets are discovered from the generated pages under
 * {@code docs/website/instset} (one {@code m_&lt;name&gt;.html} per instruction
 * set); the menu icon is derived by convention as
 * {@code images/icons/space/&lt;leaf&gt;-icon.svg} (with the root {@code /m} mapping
 * to {@code mtron}). Skills are discovered from {@code docs/website/skills}, one
 * directory per skill ({@code SKILL.md} plus {@code references/*.md}, {@code scripts/},
 * and {@code assets/}); each listing drops the payload files {@link #isListed} names —
 * {@code png}/{@code svg} images, {@code .sqlite} dumps, and a folder's
 * {@code README.md}.</p>
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

    /**
     * Extensions a skill's dropdown never lists (see {@link #isListed}): images and
     * SQLite dumps are payload, not pages.
     */
    private static final Set<String> SKIPPED_EXTENSIONS = Set.of("png", "svg", "sqlite");

    /**
     * Lower-cased file names a skill's dropdown never lists: a folder's own note, which
     * is not a skill document.
     */
    private static final Set<String> SKIPPED_NAMES = Set.of("readme.md");

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

        final Path referencesDir = skillsDir.resolve("mtron/references");
        final List<InstSet> instsets = scanInstSets(instsetDir, iconsDir, referencesDir);
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
     * filename, icon name, and (when present) a reference to its
     * {@code skills/mtron/references/<leaf>-instset-mtron.html} doc. A set is
     * included only when its icon file exists ({@code &lt;leaf&gt;-icon.svg}), which
     * drops sets that lack a published icon.
     */
    private static List<InstSet> scanInstSets(final Path instsetDir, final Path iconsDir, final Path referencesDir) throws IOException {
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
                final String leaf = DocsUtil.leafName(vid);
                final String icon = iconName(leaf);
                if (!Files.isRegularFile(iconsDir.resolve(icon + "-icon.svg"))) continue;
                final String doc = docLink(leaf, referencesDir);
                instsets.add(new InstSet(vid, name, icon, doc));
            }
        }
        return instsets;
    }

    /**
     * The relative link to a leaf's instruction-set reference doc, or {@code null}
     * when no {@code <leaf>-instset-mtron.html} file exists under the references
     * directory.
     */
    private static String docLink(final String leaf, final Path referencesDir) {
        final String docName = leaf + "-instset-mtron.html";
        return Files.isRegularFile(referencesDir.resolve(docName))
                ? "./skills/mtron/references/" + docName
                : null;
    }

    /**
     * Walk the skill directory and collect each skill — a subdirectory holding a
     * {@code SKILL.md} plus optional {@code references/}, {@code scripts/}, and
     * {@code assets/} directories. Each folder's listing drops the payload files
     * {@link #isListed} names, so images, SQLite dumps and folder READMEs never reach
     * the dropdown. Package-private for {@code HeaderGeneratorTest}.
     */
    static List<Skill> scanSkills(final Path skillsDir) throws IOException {
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
                    .filter(HeaderGenerator::isListed)
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
                    .filter(HeaderGenerator::isListed)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }
    }

    /**
     * Whether a skill's dropdown lists {@code file}, in any of its resource folders
     * ({@code references/}, {@code scripts/}, {@code assets/}).
     *
     * <p>What a dropdown must not offer: payload rather than document. Images
     * ({@code png}/{@code svg}) and SQLite dumps are data a reader cannot use as a
     * page, and a folder's {@code README.md} is that folder's own note — a skill's
     * document is its {@code SKILL.md}, which the menu links separately. Extensionless
     * resources ({@code scripts/wsplus}) stay listed.
     *
     * <p>A filename test, not {@link Path#endsWith}: that compares whole path
     * <em>elements</em>, so {@code Path.of("t.sqlite").endsWith(".sqlite")} is false and
     * a data dump slips through (which is how {@code drstynx.sqlite} reached the menu).
     */
    static boolean isListed(final Path file) {
        final String name = file.getFileName().toString();
        if (SKIPPED_NAMES.contains(name.toLowerCase())) return false;
        final int dot = name.lastIndexOf('.');
        return dot < 0 || !SKIPPED_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase());
    }

    /**
     * Render the instruction-sets dropdown as an indented tree: an "overview" entry,
     * a divider, then the {@code /m} namespace as a nested tree. A node that names an
     * instruction set links to its generated page; a node that is only an intermediate
     * namespace (e.g. {@code /m/db}) is a non-linking folder header, and every level is
     * indented one step under its parent. The root {@code /m} sits at the top of the
     * tree, and its immediate children are the top-level namespaces.
     */
    static String renderInstSetMenu(final List<InstSet> instsets) {
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
        final Node root = buildTree(instsets);
        if (root != null) {
            renderNode(sb, root, 0);
        }
        sb.append("                </ul>\n");
        sb.append("            </li>");
        return sb.toString();
    }

    /**
     * Build the instruction-set tree from a flat vid list. Every vid is split on
     * {@code /} and each segment chain becomes a node; a node owns the {@link InstSet}
     * whose vid equals the node's full path, or none when it is only an intermediate
     * namespace. Children are ordered by their leaf name so sibling folders and leaves
     * read naturally. The root is always {@code /m}.
     */
    private static Node buildTree(final List<InstSet> instsets) {
        final Map<String, Node> byPath = new HashMap<>();
        final Map<String, InstSet> byVid = new HashMap<>();
        for (final InstSet is : instsets) {
            byVid.put(is.vid(), is);
        }
        for (final InstSet is : instsets) {
            final String[] segments = is.vid().split("/");
            final StringBuilder path = new StringBuilder();
            for (int i = 1; i < segments.length; i++) {
                path.append('/').append(segments[i]);
                byPath.computeIfAbsent(path.toString(), Node::new);
            }
        }
        for (final Map.Entry<String, Node> entry : byPath.entrySet()) {
            final Node node = entry.getValue();
            node.instSet = byVid.get(entry.getKey());
            final String parentPath = parentPath(entry.getKey());
            if (parentPath != null) {
                final Node parent = byPath.get(parentPath);
                if (parent != null) {
                    parent.children.add(node);
                }
            }
        }
        final Node root = byPath.get("/m");
        if (root != null) {
            sortTree(root);
        }
        return root;
    }

    /**
     * The parent namespace of {@code vid} (e.g. {@code /m/db} for {@code /m/db/tble}),
     * or {@code null} for the root {@code /m}.
     */
    private static String parentPath(final String vid) {
        final int slash = vid.lastIndexOf('/');
        return slash <= 0 ? null : vid.substring(0, slash);
    }

    /**
     * Order each node's children by leaf name (the final path segment), recursively.
     */
    private static void sortTree(final Node node) {
        node.children.sort(Comparator.comparing(Node::leafName));
        for (final Node child : node.children) {
            sortTree(child);
        }
    }

    /**
     * Emit one tree level. A node with an instruction set is a link (icon + full vid);
     * a folder node is a muted, non-linking header. Children follow, indented one more
     * step via a {@code depth-N} class.
     */
    private static void renderNode(final StringBuilder sb, final Node node, final int depth) {
        final String indentClass = "isa-depth-" + depth;
        if (node.instSet != null) {
            final InstSet is = node.instSet;
            if (is.doc() != null) {
                sb.append("                    <li class=\"article-row\">\n");
                sb.append("                        <a class=\"code dropdown-item article-link d-flex align-items-center ").append(indentClass)
                        .append("\" href=\"./instset/").append(is.filename()).append("\"><img\n");
                sb.append("                                src=\"./images/icons/space/").append(is.icon())
                        .append("-icon.svg\" class=\"isa-row icon-color me-2\"\n");
                sb.append("                                alt=\"").append(is.icon()).append("\"/>").append(is.vid()).append("</a>\n");
                sb.append("                        <span class=\"article-formats\"><a class=\"article-format\" href=\"")
                        .append(is.doc()).append("\">doc</a></span>\n");
                sb.append("                    </li>\n");
            } else {
                sb.append("                    <li><a class=\"code dropdown-item d-flex align-items-center ").append(indentClass)
                        .append("\" href=\"./instset/").append(is.filename()).append("\"><img\n");
                sb.append("                            src=\"./images/icons/space/").append(is.icon())
                        .append("-icon.svg\" class=\"isa-row icon-color me-2\"\n");
                sb.append("                            alt=\"").append(is.icon()).append("\"/>").append(is.vid()).append("</a></li>\n");
            }
        } else {
            sb.append("                    <li class=\"isa-folder d-flex align-items-center ").append(indentClass).append("\">")
                    .append("<i class=\"bi bi-folder2-open me-2\"></i>").append(node.path).append("</li>\n");
        }
        for (final Node child : node.children) {
            renderNode(sb, child, depth + 1);
        }
    }

    /**
     * Render the skills dropdown as an accordion. Every skill links to its
     * {@code SKILL.html}; skills with resources (references/scripts/assets) get a
     * chevron that expands them in place, one open at a time via
     * {@code data-bs-parent}.
     */
    static String renderSkillsMenu(final List<Skill> skills) {
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
     * Map a leaf name to its icon filename (without extension): the root
     * {@code m} uses {@code mtron}.
     */
    private static String iconName(final String leaf) {
        return "m".equals(leaf) ? "mtron" : leaf;
    }

    /**
     * An instruction set's vid (label), generated page filename, icon name, and the
     * relative link to its {@code skills/mtron/references/<leaf>-instset-mtron.html}
     * reference (or {@code null} when the set has no such reference).
     */
    record InstSet(String vid, String filename, String icon, String doc) {
    }

    /**
     * A node in the instruction-set tree: its full namespace path, the instruction
     * set whose vid equals that path (or {@code null} when the node is only an
     * intermediate namespace), and its children.
     */
    private static final class Node {
        private final String path;
        private InstSet instSet;
        private final List<Node> children = new ArrayList<>();

        Node(final String path) {
            this.path = path;
        }

        String leafName() {
            final int slash = this.path.lastIndexOf('/');
            return this.path.substring(slash + 1);
        }
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
