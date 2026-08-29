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

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Renders the processed skill markdown under {@code docs/website/skills} into
 * styled, standalone HTML pages, one {@code .html} beside each {@code .md}.
 *
 * <p>The markdown is the source of truth (committed); the HTML is derived and
 * regenerated on every site build. Each page reuses the shared website header and
 * footer, depth-rewritten for its location, and rewrites relative {@code .md}
 * links to their {@code .html} counterparts so cross-references resolve.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 * java studio.phaseshift.metatron.docs.SkillHtmlRenderer [-s &lt;skills-dir&gt;]
 * </pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SkillHtmlRenderer {

    private static final GraphittyLogger LOG = Graphitty.log(SkillHtmlRenderer.class);

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;

    static {
        final MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create(),
                AutolinkExtension.create()));
        PARSER = Parser.builder(options).build();
        RENDERER = HtmlRenderer.builder(options).build();
    }

    // Rewrites href="<relative>.md[#anchor]" → href="<relative>.html[#anchor]",
    // leaving scheme-qualified links (http:, mailto:, …) untouched.
    private static final Pattern MD_HREF = Pattern.compile("href=\"((?![a-z]+:)[^\"]*)\\.md(#[^\"]*)?\"");

    // Shift the markdown body's headings down one level (h1→h2, … h5→h6) because
    // the frontmatter description now owns the page's single <h1>.
    private static final Pattern OPEN_HEADING = Pattern.compile("<h([1-5])([ >])");
    private static final Pattern CLOSE_HEADING = Pattern.compile("</h([1-5])>");

    public static void main(final String[] args) throws IOException {
        Path skillsDir = Path.of("docs/website/skills");
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-s", "--skills" -> skillsDir = Path.of(args[++i]);
                default -> LOG.error("[skill-html] ignoring unknown arg: %s", args[i]);
            }
        }

        final Path websiteRoot = skillsDir.getParent();
        final List<Path> files = new ArrayList<>();
        try (final var stream = Files.walk(skillsDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .filter(SkillHtmlRenderer::isSkillDoc)
                    .sorted()
                    .forEach(files::add);
        }

        int written = 0;
        for (final Path file : files) {
            if (render(file, websiteRoot)) written++;
        }
        LOG.info("[skill-html] rendered %d of %d markdown files into %s", written, files.size(), skillsDir);
    }

    /**
     * A markdown file belongs to the skill doc set when it is a {@code SKILL.md}
     * or lives under a {@code references/} directory (mirrors {@code MarkdownRunner}
     * and the nav generator, so assets like {@code assets/README.md} are skipped).
     */
    private static boolean isSkillDoc(final Path file) {
        final Path name = file.getFileName();
        if (name != null && "SKILL.md".equals(name.toString())) return true;
        for (Path parent = file.getParent(); parent != null && parent.getFileName() != null; parent = parent.getParent()) {
            if ("references".equals(parent.getFileName().toString())) return true;
        }
        return false;
    }

    /**
     * Render a single markdown file to a sibling {@code .html}. Returns true only
     * when the output changed (idempotent, so a clean build writes nothing).
     */
    private static boolean render(final Path mdFile, final Path websiteRoot) throws IOException {
        final FrontMatter fm = split(Files.readString(mdFile));

        final String mdName = mdFile.getFileName().toString();
        final Path htmlFile = mdFile.resolveSibling(mdName.substring(0, mdName.length() - ".md".length()) + ".html");

        final String depth = depth(websiteRoot, htmlFile);
        final String header = InstSetDocGenerator.loadWebsiteHeader(depth)
                .replace("<title>PhaseShift Studio</title>",
                        "<title>" + fm.name() + " · PhaseShift Studio</title>");
        final String footer = InstSetDocGenerator.loadWebsiteFooter(depth);

        final String body = RENDERER.render(PARSER.parse(fm.body()));
        final String bodyLinks = MD_HREF.matcher(body).replaceAll(mr ->
                "href=\"" + mr.group(1) + ".html" + (mr.group(2) == null ? "" : mr.group(2)) + "\"");
        final String bodyShifted = shiftHeadingsDown(bodyLinks);

        final StringBuilder page = new StringBuilder();
        page.append(header);
        page.append("    <div class=\"skill-doc mb-4\">\n");
        if (!fm.description().isBlank()) {
            final String title = fm.name().isBlank() ? fm.description() : fm.name() + ": " + fm.description();
            page.append("        <h1 class=\"skill-title mb-1\">").append(title).append("</h1>\n");
        }
        page.append("        <a href=\"").append(mdName).append("\" class=\"text-decoration-none text-light small\"><i class=\"bi bi-file-earmark-code me-1\"></i>").append(mdName).append("</a>\n");
        page.append("    </div>\n");
        page.append("    <div class=\"markdown-body\">\n");
        page.append(bodyShifted);
        page.append("\n    </div>\n");
        page.append(footer);

        final String html = page.toString();
        if (Files.exists(htmlFile) && html.equals(Files.readString(htmlFile))) {
            return false;
        }
        Files.writeString(htmlFile, html);
        LOG.info("[skill-html] wrote %s", htmlFile);
        return true;
    }

    /**
     * Shift the rendered body's headings down one level ({@code h1}→{@code h2},
     * … {@code h5}→{@code h6}) so they sit under the page's {@code h1} title.
     */
    private static String shiftHeadingsDown(final String html) {
        final String opened = OPEN_HEADING.matcher(html).replaceAll(mr ->
                "<h" + (Integer.parseInt(mr.group(1)) + 1) + mr.group(2));
        return CLOSE_HEADING.matcher(opened).replaceAll(mr ->
                "</h" + (Integer.parseInt(mr.group(1)) + 1) + ">");
    }

    /**
     * Split the YAML frontmatter from the markdown body and surface the
     * {@code name} and {@code description} fields for the page chrome.
     */
    private static FrontMatter split(final String md) {
        if (!md.startsWith("---")) {
            return new FrontMatter("", "", md);
        }
        final int close = md.indexOf("\n---", 3);
        if (close < 0) {
            return new FrontMatter("", "", md);
        }
        final String front = md.substring(3, close);
        final String body = md.substring(close + 4).stripLeading();
        return new FrontMatter(extract(front, "name"), extract(front, "description"), body);
    }

    /**
     * Best-effort extraction of a single frontmatter field, tolerating YAML
     * block scalars ({@code key: |} / {@code key: >}) by taking their first line.
     */
    private static String extract(final String front, final String key) {
        final String[] lines = front.split("\n");
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i].trim();
            if (!line.startsWith(key + ":")) continue;
            final String value = line.substring(key.length() + 1).trim();
            if ("|".equals(value) || ">".equals(value)) {
                for (int j = i + 1; j < lines.length; j++) {
                    final String next = lines[j].trim();
                    if (!next.isEmpty()) return next;
                }
                return "";
            }
            return value;
        }
        return "";
    }

    /**
     * Relative path ({@code ../..}) from the output file's directory up to the
     * website root, so the shared header/footer assets resolve at any depth.
     */
    private static String depth(final Path websiteRoot, final Path htmlFile) {
        final Path rel = websiteRoot.relativize(htmlFile);
        final int segments = rel.getParent() == null ? 0 : rel.getParent().getNameCount();
        return String.join("/", Collections.nCopies(segments, ".."));
    }

    /**
     * A parsed skill document: name, description, and the frontmatter-free body.
     */
    private record FrontMatter(String name, String description, String body) {
    }
}
