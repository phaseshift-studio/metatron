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
import studio.phaseshift.metatron.isa.m.sys.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.m.sys.type.ThreadExecutor;
import studio.phaseshift.metatron.isa.m.web.parser.HTMLMarkdownSerializer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * <p>The theory <em>articles</em> are compiled the way the skills are: their
 * sources live in {@code docs/articles}, where every {@code .md} is an article, and
 * {@code -o docs/website/articles} receives the processed copies. The same pass then
 * renders the {@code articles} container {@code -o} resolves into: every {@code .md}
 * carrying YAML frontmatter becomes a sibling {@code .html} — frontmatter title and
 * subtitle, the theory stylesheet, and image sources left relative so the
 * {@code images/} tree beside it resolves.</p>
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
        // cast-only sources (docs/asciinema/**.md): evaluate the blocks, emit the
        // recordings, and render NO page — the file is a screencast definition, not
        // a document. --cast-out names the served directory directly.
        Path castOutDir = null;
        boolean pages = true;

        int i = 0;
        while (i < args.length) {
            switch (args[i]) {
                case "-b", "--boot" -> boot = args[++i];
                case "-o", "--out" -> outDir = Path.of(args[++i]);
                case "--cast-out" -> castOutDir = Path.of(args[++i]);
                case "--no-page" -> pages = false;
                case "--single-boot" -> singleBoot = true;
                case "--reverse" -> reverse = true;
                case "--html" -> html = true;
                default -> input = args[i];
            }
            i++;
        }
        final Path castOut = castOutDir == null ? null : castOutDir.toAbsolutePath().normalize();
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

        // ── Collect: a single probe .md, a skill dir's SKILL.md + references/*.md, or an
        //    articles tree's every .md
        final List<Path> files = castOut == null
                ? collectMarkdownFiles(skillDir) : collectAnyMarkdown(skillDir);
        if (files.isEmpty()) {
            LOG.info("no skill markdown files found in " + skillDir);
            // An --html run with nothing to process still renders the pages already in
            // the container the output resolves into. That pass needs no VM, so
            // returning here skips boot *and* teardown.
            if (html && pages) {
                renderHtmlPass(out);
            }
            return;
        }
        if (reverse) Collections.reverse(files);
        LOG.info("processing " + files.size() + " files (singleBoot=" + singleBoot
                + ", reverse=" + reverse + ", html=" + html + ")");
        if (singleBoot) DocsUtil.bootVM(boot);
        final long buildStart = System.nanoTime();

        ////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////

        // one tag, one recording: a cast's URI is a pure function of its tag, so
        // the same tag cannot be claimed twice (in a doc or across the tree)
        final java.util.Map<String, String> castTags = new java.util.HashMap<>();

        for (final Path file : files) {
            // ── Bootstrap metatron VM ────────────────────────────────────
            // Fresh VM per file by default; with --single-boot the VM was booted once above.
            if (!singleBoot) DocsUtil.bootVM(boot);

            final Path rel = rel(skillDir, file);
            LOG.info(Graphitty.sillyPrint("\n\nprocessing " + rel + "...\n\n", true, true));
            final long t0 = System.nanoTime();
            final String content = Files.readString(file);
            final MtronPreprocessor.Result compiled =
                    new MtronPreprocessor(MtronPreprocessor.MARKDOWN_HEADER, false).compile(content);
            final String processed = compiled.text();

            if (pages) {
                final Path target = out.resolve(rel);
                // Written when the *copy* is out of date, not when evaluation changed the
                // text: a doc whose blocks all render as written (or that has no mtron_pre
                // block at all) still has to pick up its own frontmatter and prose edits,
                // and a new such doc still has to reach the website tree.
                if (isCurrent(target, processed)) {
                    LOG.info("  unchanged " + rel + " (" + DocsUtil.elapsedMs(t0) + "ms)");
                } else {
                    Files.createDirectories(target.getParent());
                    Files.writeString(target, processed.stripTrailing());
                    LOG.info("  processed " + rel + " (" + DocsUtil.elapsedMs(t0) + "ms)");
                }
            } else {
                LOG.info("  casts-only " + rel + " (" + DocsUtil.elapsedMs(t0) + "ms)");
            }
            // The blocks that opted into a recording are emitted beside the website
            // (even when the markdown copy did not change: the cast is a separate
            // artifact with its own freshness rule).
            writeCasts(compiled, out, rel, castTags, castOut);
            ThreadExecutor.instance().shutdownNow();
        }

        ////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////

        // ── Optional site-html pass over the skills container (was SkillHtmlRenderer) ──
        // cast-only sources have no page to render
        if (html && pages) {
            renderHtmlPass(out);
        }

        LOG.info("done — " + files.size() + " files, " + DocsUtil.elapsedMs(buildStart) + "ms total (singleBoot="
                + singleBoot + ", reverse=" + reverse + ", html=" + html + ")");
        BootLoader.close();
        System.exit(0);
    }

    /**
     * The markdown files the pass processes: the single probe file, every {@code .md}
     * of an articles tree ({@code docs/articles}, where each one is an article), or
     * every skill doc under a directory ({@code SKILL.md}, or anything under a
     * {@code references/} directory).
     */
    static List<Path> collectMarkdownFiles(final Path skillDir) throws IOException {
        if (articlesContainer(skillDir) != null) return collectAnyMarkdown(skillDir);
        if (Files.isRegularFile(skillDir)) return List.of(skillDir);
        final List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(skillDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .filter(MarkdownRunner::isSkillDoc)
                    .sorted()
                    .forEach(files::add);
        }
        return files;
    }

    /**
     * Every {@code .md} under the tree — the CAST-ONLY sources ({@code docs/asciinema/})
     * are cast definitions, not skill docs, so none of the skill-doc rules apply.
     */
    static List<Path> collectAnyMarkdown(final Path dir) throws IOException {
        if (Files.isRegularFile(dir)) return List.of(dir);
        final List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .forEach(files::add);
        }
        return files;
    }

    /**
     * The site-html pass: render whichever canonical website container {@code out}
     * resolves into — the skills tree, else the articles tree. Both passes write their
     * pages into that container (symlinks followed), so {@code -o docs/website/articles}
     * renders next to the article markdown.
     */
    private static void renderHtmlPass(final Path out) throws IOException {
        final long t1 = System.nanoTime();
        final Path skillsDir = skillsContainer(out);
        final Path articlesDir = articlesContainer(out);
        if (skillsDir != null) {
            renderSiteHtml(skillsDir);
            LOG.info("html rendered into " + skillsDir + " (" + DocsUtil.elapsedMs(t1) + "ms)");
        } else if (articlesDir != null) {
            renderArticleHtml(articlesDir);
            LOG.info("html rendered into " + articlesDir + " (" + DocsUtil.elapsedMs(t1) + "ms)");
        } else {
            LOG.info("--html skipped: output " + out + " is not under a website skills or articles tree");
        }
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

    /**
     * The canonical website articles container that the markdown output directory
     * {@code out} lives under: the nearest ancestor (or {@code out} itself) whose
     * directory name is {@code articles}, symlinks resolved. Returns null when
     * {@code out} is not under an {@code articles} directory (probe outputs).
     */
    static Path articlesContainer(final Path out) throws IOException {
        Path p = out.toRealPath();
        while (p != null) {
            final Path name = p.getFileName();
            if (name != null && "articles".equals(name.toString())) return p;
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
        final DocsUtil.FrontMatter fm = DocsUtil.split(Files.readString(mdFile, StandardCharsets.UTF_8));

        final String mdName = mdFile.getFileName().toString();
        final Path htmlFile = mdFile.resolveSibling(mdName.substring(0, mdName.length() - ".md".length()) + ".html");

        final String depth = DocsUtil.depth(websiteRoot, htmlFile);
        final String header = SiteChrome.header(depth, fm.name() + " · PhaseShift Studio", "");
        final String footer = SiteChrome.footer(depth);

        final String body = HTMLMarkdownSerializer.toHTML(fm.body());
        final String bodyLinks = MD_HREF.matcher(body).replaceAll(mr ->
                "href=\"" + mr.group(1) + ".html" + (mr.group(2) == null ? "" : mr.group(2)) + "\"");
        final String bodyShifted = shiftHeadingsDown(bodyLinks);
        final String bodyCasts = castEmbeds(bodyShifted, websiteRoot, depth);
        final String pre = depth == null || depth.isEmpty() ? "" : depth + "/";

        final StringBuilder page = new StringBuilder();
        page.append(header);
        page.append("    <div class=\"skill-doc mb-4\">\n");
        page.append("        <h1 class=\"skill-title mb-1\">").append(fm.name()).append("</h1>\n");
        page.append("        <small>").append(fm.description().replace("`", "").replaceAll("\\s+", " ").strip()).append("</small><br/>\n");
        page.append("        <a href=\"").append(mdName).append("\" class=\"text-decoration-none text-light small\"><i class=\"bi bi-file-earmark-code me-1\"></i>").append(mdName).append("</a>\n");
        page.append("    </div>\n");
        page.append("    <div class=\"markdown-body\">\n");
        page.append(bodyCasts);
        page.append("\n    </div>\n");
        // the loader is only fetched by pages that actually carry a recording
        if (bodyCasts.contains("asciinema-cast")) {
            page.append("    <script src=\"").append(pre).append("js/asciinema.js\" defer></script>\n");
        }
        page.append(footer);

        final String html = page.toString();
        if (Files.exists(htmlFile) && html.equals(Files.readString(htmlFile, StandardCharsets.UTF_8))) {
            return false;
        }
        Files.writeString(htmlFile, html, StandardCharsets.UTF_8);
        LOG.info("[site-html] wrote %s", htmlFile);
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Article-html pass (the "theory article" species)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * The theory-article stylesheet, injected through the shared header's
     * {@code {{EXTRA_HEAD}}} token. Its href is
     * depth-rewritten by hand because {@code {{EXTRA_HEAD}}} is substituted after
     * {@link SiteChrome} has already depth-rewritten the header template.
     *
     * <p>Math needs no injection here: authored {@code $…$} is converted by
     * {@link HTMLMarkdownSerializer} into {@code \(…\)} / {@code \[…\]}, the
     * delimiters the site's global MathJax owns, so one engine renders math on
     * every page. The KaTeX auto-render that used to be injected here scoped
     * itself to {@code $…$} precisely to avoid fighting that MathJax; with the
     * conversion in the serializer there is nothing left for it to find.
     */
    private static String articleExtraHead(final String depth) {
        final String pre = depth == null || depth.isEmpty() ? "" : depth + "/";
        return "    <link href=\"" + pre + "css/theory-article.css\" rel=\"stylesheet\">";
    }

    /**
     * Render every {@code .md} file under {@code articlesDir} to a sibling
     * {@code .html} with the theory-article chrome (frontmatter title + subtitle,
     * KaTeX, the article stylesheet), rewriting nothing that is already current.
     *
     * @return the number of html files actually written (changed)
     */
    static boolean isArticleDoc(final Path file) {
        try (final var r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            final String first = r.readLine();
            return first != null && "---".equals(first.strip());
        } catch (final IOException e) {
            return false;
        }
    }

    public static int renderArticleHtml(final Path articlesDir) throws IOException {
        final Path websiteRoot = articlesDir.getParent();
        final List<Path> files = new ArrayList<>();
        try (final var stream = Files.walk(articlesDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .filter(MarkdownRunner::isArticleDoc)
                    .sorted()
                    .forEach(files::add);
        }
        int written = 0;
        for (final Path file : files) {
            if (renderArticleFile(file, websiteRoot)) written++;
        }
        LOG.info("[article-html] rendered %d of %d markdown files into %s", written, files.size(), articlesDir);
        return written;
    }

    /**
     * Render a single article markdown file to a sibling {@code .html}. Returns
     * true only when the output changed (idempotent, like the skills pass).
     */
    private static boolean renderArticleFile(final Path mdFile, final Path websiteRoot) throws IOException {
        final DocsUtil.FrontMatter fm = DocsUtil.split(Files.readString(mdFile, StandardCharsets.UTF_8));

        final String mdName = mdFile.getFileName().toString();
        final Path htmlFile = mdFile.resolveSibling(mdName.substring(0, mdName.length() - ".md".length()) + ".html");

        final String depth = DocsUtil.depth(websiteRoot, htmlFile);
        final String header = SiteChrome.header(depth, fm.name() + " · PhaseShift Studio", articleExtraHead(depth));
        final String footer = SiteChrome.footer(depth);

        final String body = HTMLMarkdownSerializer.toHTML(fm.body());
        final String bodyLinks = MD_HREF.matcher(body).replaceAll(mr ->
                "href=\"" + mr.group(1) + ".html" + (mr.group(2) == null ? "" : mr.group(2)) + "\"");
        final String bodyShifted = shiftHeadingsDown(bodyLinks);

        final StringBuilder page = new StringBuilder();
        page.append(header);
        page.append("    <div class=\"article-doc\">\n");
        page.append("        <h1 class=\"article-title mb-1\">").append(fm.name()).append("</h1>\n");
        page.append("        <small class=\"article-subtitle\">").append(fm.description().replace("`", "").replaceAll("\\s+", " ").strip()).append("</small>\n");
        // byline and citation come from the frontmatter, so an article body needs no
        // repeated title and no ad-hoc byline block of its own
        if (!fm.author().isBlank()) {
            page.append("        <div class=\"article-author\">").append(htmlLines(fm.author())).append("</div>\n");
        }
        if (!fm.citation().isBlank()) {
            page.append("        <div class=\"article-citation\">").append(htmlLines(fm.citation())).append("</div>\n");
        }
        page.append("    </div>\n");
        page.append("    <div class=\"article-body\">\n");
        page.append(bodyShifted);
        page.append("\n    </div>\n");
        page.append(footer);

        final String html = page.toString();
        if (Files.exists(htmlFile) && html.equals(Files.readString(htmlFile, StandardCharsets.UTF_8))) {
            return false;
        }
        Files.writeString(htmlFile, html, StandardCharsets.UTF_8);
        LOG.info("[article-html] wrote %s", htmlFile);
        return true;
    }

    /**
     * Frontmatter prose as html lines: each source line becomes its own line (so a
     * {@code |} literal block keeps its shape — a byline's affiliation stays under
     * the name), blank lines drop. The text is escaped, because a citation may hold
     * an {@code &} or an angle bracket.
     */
    private static String htmlLines(final String value) {
        final StringBuilder out = new StringBuilder();
        for (final String line : value.split("\n", -1)) {
            final String part = line.strip();
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append("<br>");
            out.append(part.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
        }
        return out.toString();
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
     * True when the processed copy at {@code target} already holds {@code processed}
     * (trailing whitespace ignored, as the writer strips it) — so the docs pass is
     * idempotent and a clean build writes nothing.
     */
    static boolean isCurrent(final Path target, final String processed) throws IOException {
        return Files.exists(target) && processed.stripTrailing().equals(Files.readString(target));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Terminal recordings (the blocks that opted in with cast=<name>)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Render every block that opted into a recording into
     * {@code <website>/asciinema/<name>.cast} — the wire format the browser
     * player reads. The statements come from the SAME evaluation that produced
     * the listing, so the page and the recording cannot disagree; the listing is
     * printed once per keystroke by {@link Asciicast}, never by the author.
     *
     * <p>Skipped silently when the output is not under a website skills tree
     * (probe runs), mirroring the html pass's own rule.</p>
     *
     * @return the number of recordings actually written (changed)
     */
    static int writeCasts(final MtronPreprocessor.Result compiled, final Path out,
                          final Path source, final java.util.Map<String, String> tags,
                          final Path castDir) throws IOException {
        if (compiled.blocks().isEmpty()) return 0;
        final Path asciinemaDir;
        if (castDir != null) {
            // cast-only source: the served directory is named explicitly
            asciinemaDir = castDir;
        } else {
            final Path skillsDir = skillsContainer(out);
            if (skillsDir == null) return 0;
            asciinemaDir = skillsDir.getParent().resolve("asciinema");
        }

        int written = 0;
        int blockNumber = 0;
        for (final MtronPreprocessor.Block block : compiled.blocks()) {
            blockNumber++;
            final String name = block.attributes().get("cast");
            if (name == null) continue;
            // a tag names exactly one recording, and the recording's URI is the
            // tag: refuse a collision instead of silently overwriting a cast
            final String where = source + " (block " + blockNumber + ")";
            final String owner = tags.putIfAbsent(name, where);
            if (owner != null) {
                throw new IllegalStateException("cast tag '" + name + "' is used by "
                        + owner + " and by " + where + " — a tag names one recording");
            }
            // the terminal is only as wide as its widest line needs (plus a
            // little air), capped by the width the block declares
            final Asciicast cast = Asciicast.of(contentCols(block, attribute(block, "cols", 100)),
                            attribute(block, "rows", 30))
                    .prompt("mtron> ")
                    .typingDelay(attribute(block, "typing_delay", 0.05));
            for (final String header : block.headers()) {
                cast.marker(header);
            }
            // the pasteable script: the typed statements, one per line, no results
            final StringBuilder script = new StringBuilder();
            for (final MtronPreprocessor.Statement statement : block.statements()) {
                // the annotation is a caption, not a keystroke: it becomes a
                // marker the page turns into the caption bar. A statement that is
                // ONLY an annotation (hidden, no input) is that marker alone.
                if (statement.caption() != null) cast.marker(statement.caption());
                if (statement.hidden()) continue;
                cast.statement(statement.input(), statement.output(), !statement.noPrompt());
                if (!statement.input().isEmpty()) {
                    // one statement per line, terminated: a console that receives
                    // the paste as a single line still parses the ';' separators
                    script.append(statement.input().replaceAll("\\n\\s*", " ").stripTrailing())
                            .append(";\n");
                }
            }
            final Path scriptTarget = asciinemaDir.resolve(name + ".txt");
            if (!script.isEmpty() && (!Files.exists(scriptTarget)
                    || !Files.readString(scriptTarget, StandardCharsets.UTF_8).equals(script.toString()))) {
                Files.createDirectories(scriptTarget.getParent());
                Files.writeString(scriptTarget, script.toString(), StandardCharsets.UTF_8);
                LOG.info("[cast] wrote %s", scriptTarget);
            }
            final Asciicast.Rendered rendered = cast.render(source.toString());
            final Path target = asciinemaDir.resolve(name + ".cast");
            Files.createDirectories(target.getParent());
            if (Files.exists(target)
                    && Files.readString(target, StandardCharsets.UTF_8).equals(rendered.text())) {
                continue;
            }
            Files.writeString(target, rendered.text(), StandardCharsets.UTF_8);
            LOG.info("[cast] wrote %s (%d events, %ss)", target, rendered.events(),
                    String.format(java.util.Locale.ROOT, "%.2f", rendered.seconds()));
            written++;
        }
        return written;
    }

    /**
     * The narrowest terminal that shows every line unwrapped: the widest visual
     * line (prompt+input and each rendered result), plus two columns of air, but
     * never wider than the block asked for. Measured with {@link Highlighter#visualLength}
     * so ANSI/markup adds no columns.
     */
    static int contentCols(final MtronPreprocessor.Block block, final int declared) {
        int widest = 0;
        for (final MtronPreprocessor.Statement statement : block.statements()) {
            if (statement.hidden()) continue;
            widest = Math.max(widest, Highlighter.visualLength("mtron> " + statement.input()));
            for (final String output : statement.output()) {
                // a rendered value may itself be multi-line: measure each line
                for (final String line : output.split("\n")) {
                    widest = Math.max(widest, Highlighter.visualLength(line));
                }
            }
        }
        return widest == 0 ? declared : Math.max(40, Math.min(declared, widest + 2));
    }

    private static int attribute(final MtronPreprocessor.Block block, final String key, final int absent) {
        final String value = block.attributes().get(key);
        return value == null || value.isBlank() ? absent : Integer.parseInt(value);
    }

    private static double attribute(final MtronPreprocessor.Block block, final String key, final double absent) {
        final String value = block.attributes().get(key);
        return value == null || value.isBlank() ? absent : Double.parseDouble(value);
    }

    // The markdown pass plants this marker in front of an opted-in block; the
    // html pass turns it into the player container (which needs the page depth,
    // known only at render time).
    private static final Pattern CAST_MARKER = Pattern.compile(
            "<!-- cast: ([A-Za-z0-9._-]+)((?:[ \\t]+[a-z_]+=\"[^\"]*\")*)[ \\t]*-->");
    private static final Pattern CAST_ATTR = Pattern.compile("([a-z_]+)=\"([^\"]*)\"");

    /**
     * Replace every {@code <!-- cast: tag -->} marker with a launcher and the
     * modal its player lives in. The tag is the recording's URI
     * ({@code asciinema/<tag>.cast}) AND its label, so one file with several
     * tagged blocks yields several distinguishable recordings. The recording's
     * own header supplies cols/rows; the player mounts when the modal opens. The
     * id is also the deep link: {@code #cast-<tag>} opens that recording.
     */
    private static String castEmbeds(final String body, final Path websiteRoot, final String depth) {
        if (!body.contains("<!-- cast: ")) return body;
        final String pre = depth == null || depth.isEmpty() ? "" : depth + "/";
        final Matcher marker = CAST_MARKER.matcher(body);
        final StringBuilder inline = new StringBuilder();   // body with inline pills
        final StringBuilder top = new StringBuilder();      // pills for place=top|both
        final StringBuilder modals = new StringBuilder();   // one hidden modal per cast
        while (marker.find()) {
            final java.util.LinkedHashMap<String, String> cast = new java.util.LinkedHashMap<>();
            cast.put("cast", marker.group(1));
            final Matcher attribute = CAST_ATTR.matcher(marker.group(2) == null ? "" : marker.group(2));
            while (attribute.find()) cast.put(attribute.group(1), attribute.group(2));

            final String name = cast.get("cast");
            final String title = cast.getOrDefault("title", name)
                    .replace("&", "&amp;").replace("<", "&lt;");
            final String id = "cast-" + name;
            final String[] term = termSize(websiteRoot.resolve("asciinema").resolve(name + ".cast"));
            final String place = cast.getOrDefault("place", "top");
            if (!java.util.Set.of("top", "inline", "both").contains(place)) {
                throw new IllegalStateException("cast '" + name + "': place must be top, inline or both (was '" + place + "')");
            }

            final String pill = "<div class=\"asciinema-embed\">"
                    + "<button type=\"button\" class=\"asciinema-launch\" data-bs-toggle=\"modal\""
                    + " data-bs-target=\"#" + id + "\""
                    + " aria-label=\"watch " + title + "\">"
                    + "<i class=\"bi bi-play-fill\" aria-hidden=\"true\"></i>"
                    + "<span>" + title + "</span></button></div>";

            if ("inline".equals(place) || "both".equals(place)) {
                if ("both".equals(place)) top.append(pill);
                marker.appendReplacement(inline, Matcher.quoteReplacement(pill));
            } else {
                top.append(pill);
                marker.appendReplacement(inline, "");
            }

            modals.append("<div class=\"modal fade asciinema-modal\" id=\"").append(id)
                    .append("\" style=\"--cols: ").append(term[0]).append("\"")
                    .append(" tabindex=\"-1\" aria-labelledby=\"").append(id)
                    .append("-title\" aria-hidden=\"true\">")
                    .append("<div class=\"modal-dialog modal-xl modal-dialog-centered\">")
                    .append("<div class=\"modal-content\">")
                    .append("<div class=\"modal-header\">")
                    .append("<h5 class=\"modal-title\" id=\"").append(id).append("-title\">")
                    .append(title).append("</h5>")
                    .append("<button type=\"button\" class=\"btn-close btn-close-white\"")
                    .append(" data-bs-dismiss=\"modal\" aria-label=\"close\"></button>")
                    .append("</div>")
                    .append("<div class=\"modal-body\">")
                    .append("<div class=\"asciinema-cast\" data-cast=\"").append(pre)
                    .append("asciinema/").append(name).append(".cast\"")
                    .append(" data-cols=\"").append(term[0]).append("\" data-rows=\"").append(term[1]).append("\"");
            if (cast.get("speed_rates") != null) {
                modals.append(" data-speed-rates=\"").append(cast.get("speed_rates")).append("\"");
            }
            if (cast.get("speed_default") != null) {
                modals.append(" data-speed-default=\"").append(cast.get("speed_default")).append("\"");
            }
            modals.append(" data-autoplay></div>")
                    .append("<div class=\"asciinema-caption\" aria-live=\"polite\"></div>")
                    .append("</div></div></div></div></div>");
        }
        marker.appendTail(inline);
        return top + inline.toString() + modals;
    }

    /** The recording's term size from its header line; 80x24 when unreadable. */
    private static String[] termSize(final Path cast) {
        try {
            final String header = Files.readString(cast, StandardCharsets.UTF_8).split("\n", 2)[0];
            final Matcher cols = Pattern.compile("\"cols\":(\\d+)").matcher(header);
            final Matcher rows = Pattern.compile("\"rows\":(\\d+)").matcher(header);
            if (cols.find() && rows.find()) return new String[]{cols.group(1), rows.group(1)};
        } catch (final IOException ignored) {
            // a missing recording is the link check's problem, not the renderer's
        }
        return new String[]{"80", "24"};
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
     *
     * <p>Null-safe against the filesystem root: the parent climb stops when a
     * parent has no file name ({@code Path.getFileName()} is null at {@code /}).
     * A stray {@code .md} inside the walked container that is neither a
     * {@code SKILL.md} nor under {@code references/} (e.g. an
     * {@code assets/README.md}) must be skipped — not crash the site-html pass.
     */
    private static boolean isSkillDoc(final Path file) {
        final Path fileName = file.getFileName();
        if (fileName != null && "SKILL.md".equals(fileName.toString())) {
            return true;
        }
        for (Path parent = file.getParent(); parent != null; parent = parent.getParent()) {
            if (parent.getFileName() == null) {
                return false; // climbed past the filesystem root: not a skill doc
            }
            if ("references".equals(parent.getFileName().toString())) {
                return true;
            }
        }
        return false;
    }
}
