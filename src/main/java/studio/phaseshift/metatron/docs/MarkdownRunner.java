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

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.isa.dckr.dckrInstSet;
import studio.phaseshift.metatron.isa.dcmnt.dcmntInstSet;
import studio.phaseshift.metatron.isa.grph.grphInstSet;
import studio.phaseshift.metatron.isa.iot.iotInstSet;
import studio.phaseshift.metatron.isa.llm.llmInstSet;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.mach.ui.uiInstSet;
import studio.phaseshift.metatron.isa.rdf.rdfInstSet;
import studio.phaseshift.metatron.isa.sys.type.ThreadExecutor;
import studio.phaseshift.metatron.isa.tble.tbleInstSet;
import studio.phaseshift.metatron.isa.web.parser.HTMLMarkdownSerializer;
import studio.phaseshift.metatron.isa.web.webInstSet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * CLI tool that pre-processes a skill directory's markdown files, evaluating
 * {@code ```mtron_pre ... ```} blocks via {@link MtronPreprocessor} and
 * replacing them with {@code ```mtron} input/output listings.
 *
 * <p>For a skill root such as {@code docs/skills/mtron}, processes
 * {@code SKILL.md} and every {@code .md} file under {@code references/}; given
 * the parent {@code docs/skills}, every skill root is processed. The processed
 * markdown is written to the output directory ({@code .metatron/skills} by
 * default), mirroring the input layout — the raw source is never modified.</p>
 *
 * <p>A <em>single</em> {@code .md} file may also be given (the iterate-on-one-doc
 * probe mode): it is processed on its own and written to
 * {@code <out>/<file-basename>}, so pair it with an {@code -o} that resolves to
 * the directory the file belongs to.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 * java studio.phaseshift.metatron.docs.MarkdownRunner &lt;input-dir&gt; [-b &lt;boot&gt;] [-o &lt;out&gt;] [--html]
 * java studio.phaseshift.metatron.docs.MarkdownRunner &lt;file.md&gt;  [-b &lt;boot&gt;] [-o &lt;out&gt;] [--html]
 * </pre>
 *
 * <p>{@code --html} (or {@code -Dmtron.html=true}) chains the site-html pass
 * onto the markdown pass: after processing, every skill doc under the canonical
 * website skills container that {@code -o} resolves into (the nearest ancestor
 * directory named {@code skills}, symlinks followed — {@code .metatron/skills}
 * is the repo's symlink to {@code docs/website/skills}) is rendered to a sibling
 * {@code .html}. Body fragments are converted by
 * {@link HTMLMarkdownSerializer} (markdown ⇄ html, the engine that replaced the
 * deleted {@code docs/SkillHtmlRenderer}); the website chrome — frontmatter
 * {@code h1}, shared header/footer, {@code .md} → {@code .html} link rewriting,
 * heading shift — is assembled by {@link #renderSiteHtml(Path)} here.
 * Outputs outside a website skills tree (probe dirs like {@code target/temp})
 * skip the html pass with a note.</p>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MarkdownRunner {

    private static final String VERSION = "0.1-SNAPSHOT";
    private static final GraphittyLogger LOG = Graphitty.log(MarkdownRunner.class);

    public static void main(final String[] args) throws IOException {
        // ── Parse CLI arguments ──────────────────────────────────────────
        String input = "docs/skills";
        String boot = "boot/docs.mtron";
        Path outDir = Path.of(".metatron/skills");
        // --single-boot: boot the VM once for all files instead of once per file
        //   (or -Dmtron.singleBoot=true). Toggle to measure how much state bleeds
        //   between files when the VM is reused instead of rebuilt per file.
        // --reverse: process files in reverse sorted order — an order-dependence
        //   probe for state bleed (or -Dmtron.reverse=true).
        // --html: after the markdown pass, render the site html over the
        //   canonical website skills container the -o output resolves into
        //   (or -Dmtron.html=true).
        boolean singleBoot = Boolean.parseBoolean(System.getProperty("mtron.singleBoot", "false"));
        boolean reverse = Boolean.parseBoolean(System.getProperty("mtron.reverse", "false"));
        boolean html = Boolean.parseBoolean(System.getProperty("mtron.html", "false"));

        int i = 0;
        while (i < args.length) {
            switch (args[i]) {
                case "-b", "--boot" -> boot = args[++i];
                case "-o", "--out" -> outDir = Path.of(args[++i]);
                case "--single-boot" -> singleBoot = true;
                case "--reverse" -> reverse = true;
                case "--html" -> html = true;
                default -> input = args[i];
            }
            i++;
        }
        LOG.info("\n[Markdown Runner v" + VERSION + "]\n\targs: " + String.join(" ", args));

        final Path skillDir = Path.of(input).toAbsolutePath().normalize();
        final Path out = outDir.toAbsolutePath().normalize();
        if (skillDir.equals(out)) {
            throw new IllegalArgumentException("ERROR: output directory must differ from input directory.\n"
                    + "  input:  " + skillDir + "\n"
                    + "  output: " + out);
        }
        final boolean singleFile = Files.isRegularFile(skillDir);
        if (!singleFile && !Files.isDirectory(skillDir)) {
            LOG.error("not a file or directory: " + skillDir);
            return;
        }
        if (singleFile && !skillDir.getFileName().toString().endsWith(".md")) {
            LOG.error("expected a .md file: " + skillDir);
            return;
        }
        if (singleFile && out.resolve(skillDir.getFileName()).equals(skillDir)) {
            throw new IllegalArgumentException("output would overwrite the source file: " + skillDir);
        }
        Files.createDirectories(out);

        // ── Collect: a single probe .md, or a skill dir's SKILL.md + references/*.md
        final List<Path> files = new ArrayList<>();
        if (singleFile) {
            files.add(skillDir);
        } else {
            try (var stream = Files.walk(skillDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".md"))
                        .filter(MarkdownRunner::isSkillDoc)
                        .sorted()
                        .forEach(files::add);
            }
        }
        if (files.isEmpty()) {
            LOG.info("no skill markdown files found in " + skillDir);
            return;
        }
        if (reverse) Collections.reverse(files);
        LOG.info("processing " + files.size() + " files (singleBoot=" + singleBoot
                + ", reverse=" + reverse + ", html=" + html + ")");
        if (singleBoot) bootVM(boot);
        final long buildStart = System.nanoTime();

        ////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////

        for (final Path file : files) {
            // ── Bootstrap metatron VM ────────────────────────────────────
            // Fresh VM per file by default; with --single-boot the VM was booted once above.
            if (!singleBoot) bootVM(boot);

            final Path rel = rel(skillDir, file);
            LOG.info(Graphitty.sillyPrint("\n\nprocessing " + rel + "...\n\n", true, true));
            final long t0 = System.nanoTime();
            final String content = Files.readString(file);
            final String processed = new MtronPreprocessor(MtronPreprocessor.MARKDOWN_HEADER).process(content);

            if (!processed.equals(content)) {
                final Path target = out.resolve(rel);
                Files.createDirectories(target.getParent());
                Files.writeString(target, processed.stripTrailing());
                LOG.info("  processed " + rel + " (" + elapsedMs(t0) + "ms)");
            } else {
                LOG.info("  unchanged " + rel + " (" + elapsedMs(t0) + "ms)");
            }
            ThreadExecutor.instance().shutdownNow();
        }

        ////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////

        // ── Optional site-html pass over the skills container (was SkillHtmlRenderer) ──
        if (html) {
            final long t1 = System.nanoTime();
            final Path skillsDir = skillsContainer(out);
            if (skillsDir == null) {
                LOG.info("--html skipped: output " + out + " is not under a website skills tree");
            } else {
                renderSiteHtml(skillsDir);
                LOG.info("html rendered into " + skillsDir + " (" + elapsedMs(t1) + "ms)");
            }
        }

        LOG.info("done — " + files.size() + " files, " + elapsedMs(buildStart) + "ms total (singleBoot="
                + singleBoot + ", reverse=" + reverse + ", html=" + html + ")");
        BootLoader.close();
        System.exit(0);
    }

    /**
     * The canonical website skills container that the markdown output directory
     * {@code out} lives under: the nearest ancestor (or {@code out} itself) whose
     * directory name is {@code skills}, with symlinks resolved — the repo's
     * {@code .metatron/skills} is a symlink to {@code docs/website/skills}, and a
     * probe written to {@code .metatron/skills/mtron/references} still belongs to
     * that same container. Returns null when {@code out} is not under a
     * {@code skills} directory (probe outputs like {@code target/temp}).
     */
    static Path skillsContainer(final Path out) throws IOException {
        Path p = out.toRealPath();
        while (p != null) {
            final Path name = p.getFileName();
            if (name != null && "skills".equals(name.toString())) return p;
            p = p.getParent();
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Site-html pass (moved in from the deleted docs/SkillHtmlRenderer)
    // ═══════════════════════════════════════════════════════════════════

    // Rewrites href="<relative>.md[#anchor]" → href="<relative>.html[#anchor]",
    // leaving scheme-qualified links (http:, mailto:, …) untouched.
    private static final Pattern MD_HREF = Pattern.compile("href=\"((?![a-z]+:)[^\"]*)\\.md(#[^\"]*)?\"");

    // Shift the markdown body's headings down one level (h1→h2, … h5→h6) because
    // the frontmatter description now owns the page's single <h1>.
    private static final Pattern OPEN_HEADING = Pattern.compile("<h([1-5])([ >])");
    private static final Pattern CLOSE_HEADING = Pattern.compile("</h([1-5])>");

    /**
     * Render every skill markdown doc under {@code skillsDir} to a sibling
     * {@code .html}, rewriting nothing that is already current.
     *
     * <p>The markdown body fragment is converted by
     * {@link HTMLMarkdownSerializer#toHTML(String)}; the website chrome — the
     * frontmatter {@code h1} title, the shared header/footer (depth-rewritten),
     * the {@code .md} → {@code .html} href rewrite, and the heading shift that
     * makes room for the {@code h1} — is assembled here. This is the site-html
     * pass of {@code MarkdownRunner --html}; it lives in this docs-pipeline
     * runner (not in the serializer, which stays a generic format converter).
     *
     * @return the number of html files actually written (changed)
     */
    public static int renderSiteHtml(final Path skillsDir) throws IOException {
        final Path websiteRoot = skillsDir.getParent();
        final List<Path> files = new ArrayList<>();
        try (final var stream = Files.walk(skillsDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .filter(MarkdownRunner::isSkillDoc)
                    .sorted()
                    .forEach(files::add);
        }

        int written = 0;
        for (final Path file : files) {
            if (renderSiteFile(file, websiteRoot)) written++;
        }
        LOG.info("[site-html] rendered %d of %d markdown files into %s", written, files.size(), skillsDir);
        return written;
    }

    /**
     * Render a single markdown file to a sibling {@code .html}. Returns true only
     * when the output changed (idempotent, so a clean build writes nothing).
     */
    private static boolean renderSiteFile(final Path mdFile, final Path websiteRoot) throws IOException {
        final FrontMatter fm = split(Files.readString(mdFile, StandardCharsets.UTF_8));

        final String mdName = mdFile.getFileName().toString();
        final Path htmlFile = mdFile.resolveSibling(mdName.substring(0, mdName.length() - ".md".length()) + ".html");

        final String depth = depth(websiteRoot, htmlFile);
        final String header = InstSetDocGenerator.loadWebsiteHeader(depth)
                .replace("<title>PhaseShift Studio</title>",
                        "<title>" + fm.name() + " · PhaseShift Studio</title>");
        final String footer = InstSetDocGenerator.loadWebsiteFooter(depth);

        final String body = HTMLMarkdownSerializer.toHTML(fm.body());
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
        if (Files.exists(htmlFile) && html.equals(Files.readString(htmlFile, StandardCharsets.UTF_8))) {
            return false;
        }
        Files.writeString(htmlFile, html, StandardCharsets.UTF_8);
        LOG.info("[site-html] wrote %s", htmlFile);
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

    /**
     * Elapsed milliseconds since {@code startNanos}.
     */
    private static long elapsedMs(final long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * Boot the metatron VM and register the instruction sets used by skill docs.
     * This is the per-file cost that {@code --single-boot} amortizes to once.
     */
    private static void bootVM(final String boot) {
        BootLoader.BOOTING = true;
        BootLoader.TESTING = true;
        BootLoader.load(MRec.rec(uri(LOGG), uri(INFO), uri(BOOT), uri(boot)));
        for (final InstSet is : new InstSet[]{
                new mathInstSet(), new webInstSet(), new iotInstSet(),
                new grphInstSet(), new llmInstSet(), new tbleInstSet(),
                new dcmntInstSet(), new rdfInstSet(), new dckrInstSet(),
                new uiInstSet()
        }) {
            Router.global().addSpace(is);
            Router.writeToSpace(is);
            is.setup();
        }
        // hardcode type checker in support of runtime inst resolution
        TypeCheck.enable(TypeCheck.values());
        TypeCheck.disable(TypeCheck.code_resolve);
    }

    /**
     * Path of {@code file} relative to {@code root}; in single-file probe mode
     * {@code root} <em>is</em> the file, and {@code Path.relativize} would give
     * the empty path — return the file's own name instead.
     */
    private static Path rel(final Path root, final Path file) {
        return root.equals(file) ? file.getFileName() : root.relativize(file);
    }

    /**
     * A markdown file belongs to a skill doc set when it is a {@code SKILL.md}
     * or lives under a {@code references/} directory at any depth.
     */
    private static boolean isSkillDoc(final Path file) {
        if (file.getFileName().toString().equals("SKILL.md")) return true;
        for (Path parent = file.getParent(); parent != null; parent = parent.getParent()) {
            if ("references".equals(parent.getFileName().toString())) return true;
        }
        return false;
    }
}
