/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.impl.MUri;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.web.webInstSet;
import studio.phaseshift.metatron.isa.iot.iotInstSet;
import studio.phaseshift.metatron.isa.grph.grphInstSet;
import studio.phaseshift.metatron.isa.llm.llmInstSet;
import studio.phaseshift.metatron.isa.tble.tbleInstSet;
import studio.phaseshift.metatron.isa.dcmnt.dcmntInstSet;
import studio.phaseshift.metatron.isa.rdf.rdfInstSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.BOOT;
import static studio.phaseshift.metatron.Tokens.LOGG;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * Generates HTML documentation for metatron instruction sets by querying the
 * mtron VM directly (in-process) via the InstSet Java API.
 *
 * <h3>Usage</h3>
 * <pre>
 * java ...InstSetDocGenerator /m/mach -o docs/website/instset --website-template
 * java ...InstSetDocGenerator /m /m/mach /m/tble -o target/instsets
 * </pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class InstSetDocGenerator {

    private static final GraphittyLogger LOG = Graphitty.log(InstSetDocGenerator.class);
    private static ObjmtronSerializer SER;

    private static final Path INCLUDES_PATH = Path.of("docs/website/includes");
    private static final Path CSS_PATH = Path.of("docs/website/css/instset_doc.css");

    private static final Pattern SHORTHAND_PAT = Pattern.compile("\\?(?:rng=([^&]+))?(?:&?dom=([^&]+))?");
    private static final Pattern DOC_LINK_PAT = Pattern.compile("(href|src)=\"(?:\\./)?(images|css|lib|highlight|js)/");

    // ========================================================================
    // Lightweight instset metadata (replaces InstSetInfo model record)
    // ========================================================================

    private record Meta(String vid, String name, String desc, String parent,
                        List<String> children, String full) {}

    // ========================================================================
    // MAIN
    // ========================================================================

    public static void main(final String[] args) throws IOException {
        String outputDir = "docs/website/instset";
        String bootFile = "boot/docs.mtron";
        String host = null;
        boolean websiteTemplate = true;
        boolean verbose = true;
        int buildNumber = 0;
        String relativeDepth = "..";
        final List<String> instsetVids = new ArrayList<>(List.of(
                "/m", "/m/mach", "/m/math", "/m/web", "/m/iot",
                "/m/llm", "/m/tble", "/m/dcmnt", "/m/grph", "/m/rdf"
        ));

        int i = 0;
        while (i < args.length) {
            switch (args[i]) {
                case "-o", "--output" -> outputDir = args[++i];
                case "-b", "--boot" -> bootFile = args[++i];
                case "--host" -> host = args[++i];
                case "--website-template" -> websiteTemplate = true;
                case "--no-website-template" -> websiteTemplate = false;
                case "--relative-depth" -> relativeDepth = args[++i];
                case "--build" -> buildNumber = Integer.parseInt(args[++i]);
                case "-v", "--verbose" -> verbose = true;
                case "-q", "--quiet" -> verbose = false;
                default -> {
                    if (!args[i].startsWith("-")) instsetVids.add(args[i]);
                }
            }
            i++;
        }

        if (instsetVids.isEmpty()) {
            LOG.info("Usage: InstSetDocGenerator <vid>... [-o <dir>] [--website-template] [--verbose]");
            LOG.info("Example: InstSetDocGenerator /m/mach -o docs/website/instset --website-template");
            System.exit(1);
        }

        final Path outputPath = Path.of(outputDir).toAbsolutePath().normalize();
        Files.createDirectories(outputPath);

        LOG.info("Booting metatron VM ...");
        boot(bootFile);

        SER = ObjmtronSerializer.single();
        final String depth = relativeDepth;
        try {
            final List<Meta> metas = new ArrayList<>();
            final List<Set<Type>> allTypes = new ArrayList<>();
            final List<Set<Inst>> allInsts = new ArrayList<>();
            final List<Set<Obj>> allConsts = new ArrayList<>();
            final List<Set<Inst>> allRewrites = new ArrayList<>();
            final List<List<SpaceEntry>> allSpaces = new ArrayList<>();

            for (final String vid : instsetVids) {
                try {
                    if (!vid.equals("/m"))
                        InstSet.importInstSet(f(vid));
                    LOG.info("Fetching: " + vid);
                    final InstSet is = (InstSet) Router.readFromSpace(f(vid));
                    LOG.info("  read " + vid + " type=" + is.getClass().getSimpleName() + " noObj=" + is.isNoObj());

                    final Meta meta = extractMeta(is, vid);

                    final Set<Type> types = is.types();
                    final Set<Inst> insts = is.insts();
                    final Set<Inst> rewrites = is.rewrites();
                    final Set<Obj> consts = is.consts();
                    final List<SpaceEntry> spaces = fetchSpaces(is, vid);

                    LOG.info("  " + vid + ": " + types.size() + " types, " + insts.size() + " insts, "
                            + rewrites.size() + " rewrites, " + spaces.size() + " spaces, " + consts.size() + " consts");

                    final String html = generateHtml(meta, types, insts, rewrites, spaces, consts,
                            websiteTemplate, depth, buildNumber);
                    final String filename = vidToFilename(vid);
                    Files.writeString(outputPath.resolve(filename), html);
                    LOG.info("  -> " + outputPath.resolve(filename));

                    metas.add(meta);
                    allTypes.add(types);
                    allInsts.add(insts);
                    allConsts.add(consts);
                    allRewrites.add(rewrites);
                    allSpaces.add(spaces);
                } catch (final Exception e) {
                    LOG.info("Failed to process " + vid + ": " + e.getMessage());
                    if (verbose) e.printStackTrace();
                }
            }

            if (metas.size() > 1) {
                final String indexHtml = generateIndexHtml(metas, allTypes, allInsts, allSpaces,
                        websiteTemplate, depth, buildNumber);
                Files.writeString(outputPath.resolve("index.html"), indexHtml);
                LOG.info("  -> " + outputPath.resolve("index.html"));
            }

            LOG.info("Done. " + metas.size() + " instset(s) generated.");
        } finally {
            BootLoader.close();
            System.exit(0);
        }
    }

    private static void boot(final String bootFile) {
        BootLoader.BOOTING = true;
        BootLoader.TESTING = true;
        BootLoader.load(MRec.rec(MUri.uri(LOGG), MUri.uri("info"), MUri.uri(BOOT), MUri.uri(bootFile)));
        for (final InstSet is : new InstSet[] {
                new mathInstSet(), new webInstSet(), new iotInstSet(),
                new grphInstSet(), new llmInstSet(), new tbleInstSet(),
                new dcmntInstSet(), new rdfInstSet()
        }) {
            Router.global().addSpace(is);
            Router.writeToSpace(is);
            is.setup();
        }
        TypeCheck.disable(TypeCheck.code_resolve);
    }

    // ========================================================================
    // DATA EXTRACTION
    // ========================================================================

    private static Meta extractMeta(final InstSet is, final String vid) {
        final String name = vid.substring(vid.lastIndexOf('/') + 1);
        final String desc = fieldStr(is, "desc");
        final String full = SER.write(is);

        // Parent / children from space metadata
        String parent = null;
        final List<String> children = new ArrayList<>();
        final Obj spaceMeta = is.at("space");
        if (spaceMeta instanceof Rec spaceRec) {
            final Obj superRaw = spaceRec.at("super");
            if (superRaw instanceof Rec r) {
                String p = fieldStr(r, "pattern");
                if (p != null) {
                    p = p.replaceAll("/[#*]$", "");
                    if (!p.isEmpty()) parent = p;
                }
            } else if (superRaw != null && !superRaw.isNoObj()) {
                final String ref = resolveSpaceRef(SER.write(superRaw));
                if (!ref.isEmpty()) parent = ref;
            }

            final Obj sub = spaceRec.at("sub");
            if (sub instanceof Lst lst) {
                for (final Obj s : lst.jvm())
                    children.add(resolveSpaceRef(SER.write(s)));
            } else if (sub != null && !sub.isNoObj()) {
                children.add(resolveSpaceRef(SER.write(sub)));
            }
        }

        return new Meta(vid, name, desc, parent, children, full);
    }

    /** A child space entry: URI, name, and its type object. */
    private record SpaceEntry(String vid, String name, Obj obj, String typeSpec) {}

    private static List<SpaceEntry> fetchSpaces(final InstSet is, final String instsetVid) {
        final List<SpaceEntry> spaces = new ArrayList<>();
        try {
            final Obj result = is.read(f("space/+"));
            if (result instanceof Lst lst) {
                for (final Obj item : lst.jvm()) {
                    final String uri = item instanceof Str s ? s.jvm() : SER.write(item);
                    final String name = leafName(uri);
                    final Obj typeObj = Router.readFromSpace(f(uri));
                    spaces.add(new SpaceEntry(uri, name, typeObj, SER.write(typeObj)));
                }
            }
        } catch (final Exception e) {
            LOG.info("_fetchSpaces(" + instsetVid + "): " + e.getMessage());
        }
        return spaces;
    }

    // ── Documentation ──────────────────────────────────────────────────

    /** Fetch the doc Rec for an object via {@code uri.addQ("docq")}. */
    private static Rec fetchDoc(final fURI uri) {
        if (uri == null) return null;
        try {
            final Obj docObj = Router.readFromSpace(uri.addQ("docq"));
            if (docObj == null || docObj.isNoObj() || !(docObj instanceof Rec r)) return null;
            final String desc = fieldStr(r, "desc");
            if (desc == null || desc.isEmpty() || "no documentation available".equals(desc))
                return null;
            return r;
        } catch (final Exception e) {
            return null;
        }
    }

    /** Fetch doc via the object's vid (works for types and consts). */
    private static Rec fetchDocByVid(final Obj obj) {
        return obj == null || obj.vid() == null ? null : fetchDoc(obj.vid());
    }

    /** Fetch doc via the object's tid (works for insts and rewrites). */
    private static Rec fetchDocByTid(final Obj obj) {
        return obj == null || obj.tid() == null ? null : fetchDoc(obj.tid());
    }

    // ========================================================================
    // HTML GENERATION
    // ========================================================================

    private static String generateHtml(final Meta meta,
                                       final Set<Type> types, final Set<Inst> insts,
                                       final Set<Inst> rewrites, final List<SpaceEntry> spaces,
                                       final Set<Obj> consts,
                                       final boolean websiteTemplate, final String depth,
                                       final int buildNumber) {
        if (websiteTemplate) {
            final String header = loadWebsiteHeader(depth);
            final String footer = loadWebsiteFooter(depth);
            if (!header.isEmpty() && !footer.isEmpty()) {
                final String h = header.replace("</head>",
                                "    <link rel=\"stylesheet\" href=\"" + depth + "/css/instset_doc.css\">\n</head>")
                        .replaceAll("<title>.*?</title>",
                                "<title>" + esc(meta.name()) + " - metatron Instruction Set</title>");
                return h + bodyContent(meta, types, insts, rewrites, spaces, consts, buildNumber) + footer;
            }
        }
        return """
               <!DOCTYPE html>
               <html lang="en">
               <head>
                   <meta charset="UTF-8">
                   <meta name="viewport" content="width=device-width, initial-scale=1.0">
                   <title>%s - metatron instruction set reference</title>
                   <link rel="stylesheet" href="%s/css/metatron.css">
                   <link rel="stylesheet" href="%s/css/instset_doc.css">
               </head>
               <body>
                   <div class="container">
                       %s
                   </div>
                   <script src="%s/highlight/highlight.min.js"></script>
                   <script src="%s/highlight/languages/mtron.min.js"></script>
                   <script>hljs.highlightAll();</script>
               </body>
               </html>
               """.formatted(esc(meta.name()), depth, depth,
                bodyContent(meta, types, insts, rewrites, spaces, consts, buildNumber), depth, depth);
    }

    private static String bodyContent(final Meta meta,
                                      final Set<Type> types, final Set<Inst> insts,
                                      final Set<Inst> rewrites, final List<SpaceEntry> spaces,
                                      final Set<Obj> consts,
                                      final int buildNumber) {
        final StringBuilder sb = new StringBuilder();
        sb.append(sectionHeader(meta));
        sb.append(sectionNav(consts.size(), types.size(), insts.size(), spaces.size(), rewrites.size()));
        sb.append(sectionToc(meta.vid(), meta.name(), types, insts, rewrites, spaces, consts));
        sb.append(sectionConsts(meta.vid(), consts));
        sb.append(sectionTypes(meta.vid(), types));
        sb.append(sectionInsts(meta.vid(), insts));
        sb.append(sectionSpaces(spaces));
        sb.append(sectionRewrites(meta.vid(), rewrites));
        sb.append(sectionFooter(buildNumber));
        return sb.toString();
    }

    // ── Section: Header ────────────────────────────────────────────────

    private static String sectionHeader(final Meta meta) {
        final String parentPath = meta.vid().substring(0, meta.vid().lastIndexOf('/'));
        final StringBuilder descHtml = new StringBuilder();
        if (meta.desc() != null && !meta.desc().isEmpty() && !"null".equals(meta.desc())) {
            descHtml.append("""
                            <p class="text-light mt-3 mb-0" style="line-height:2.5em;max-width:1000px;margin:0 auto;">
                            %s</p>
                            """.formatted(esc(meta.desc().replace("\n", ""))));
        }

        return """
               <div class="container-xxl py-4">
                   <div class="text-center mb-2">
                       <h1 class="text-primary glow-text mb-1">
                           <span class="text-light">%1$s/</span>%2$s
                       </h1>
                       <p style="margin-top:0;margin-bottom:0;" class="subtitle text-light">instruction set reference</p>
                   </div>
                   <div class="text-light">%3$s</div>
                   <div class="instset-accordion-wrapper">
                       <div class="accordion accordion-flush" id="accordianInstSet">
                           <div class="accordion-item">
                               <h2 class="accordion-header" id="headingOne">
                                   <button class="accordion-button collapsed" type="button"
                                       data-bs-toggle="collapse" data-bs-target="#flush-collapseOne"
                                       aria-expanded="false" aria-controls="flush-collapseOne">
                                       <i class="bi bi-code-slash me-2"></i>%1$s/%2$s instset obj
                                   </button>
                               </h2>
                               <div id="flush-collapseOne" class="accordion-collapse collapse"
                                   aria-labelledby="flush-headingOne" data-bs-parent="#accordianInstSet">
                                   <div class="accordion-body">
                                       <pre><code>%4$s</code></pre>
                                   </div>
                               </div>
                           </div>
                       </div>
                   </div>
               </div>"""
                .formatted(esc(parentPath), esc(meta.name()), descHtml.toString(), esc(meta.full()));
    }

    // ── Section: Navigation pills ──────────────────────────────────────

    private static String sectionNav(final int consts, final int types, final int insts,
                                     final int spaces, final int rewrites) {
        return """
               <div class="container-xxl mb-4">
                   <div class="d-flex justify-content-center gap-2 flex-wrap">
                       %s
                   </div>
               </div>""".formatted(
                navBtn("consts", "Consts", consts, "bg-secondary") +
                        navBtn("types", "Types", types, "bg-primary") +
                        navBtn("instructions", "Insts", insts, "bg-success") +
                        navBtn("spaces", "Spaces", spaces, "bg-info") +
                        navBtn("rewrites", "Rewrites", rewrites, "bg-warning"));
    }

    private static String navBtn(final String href, final String label, final int count, final String badgeClass) {
        if (count > 0) {
            return "<a href=\"#" + href + "\" class=\"btn btn-outline-primary\">"
                    + label + " <span class=\"pill-label badge " + badgeClass + "\">" + count + "</span></a>";
        }
        return "<button class=\"btn btn-outline-secondary\" disabled>"
                + label + " <span class=\"pill-label badge bg-dark\">0</span></button>";
    }

    // ── Section: Table of Contents ─────────────────────────────────────

    private static String sectionToc(final String instsetVid, final String instsetName,
                                     final Set<Type> types, final Set<Inst> insts,
                                     final Set<Inst> rewrites, final List<SpaceEntry> spaces,
                                     final Set<Obj> consts) {
        final StringBuilder cols = new StringBuilder();

        // Types (grouped by branch)
        cols.append(tocTypesSection(instsetVid, instsetName, types));

        // Constants
        tocFlatGroup(cols, "Constants", "bg-secondary", "C",
                consts.stream()
                        .filter(c -> c.vid() != null)
                        .map(c -> tocPill("const-" + c.vid().name(), c.vid().name(), "bg-secondary", "C"))
                        .sorted()
                        .collect(Collectors.joining()));

        // Instructions
        tocFlatGroup(cols, "Instructions", "bg-success", "I",
                insts.stream()
                        .filter(inst -> inst.tid() != null)
                        .map(inst -> inst.tid().name())
                        .distinct().sorted()
                        .map(n -> tocPill("inst-" + n, n, "bg-success", "I"))
                        .collect(Collectors.joining()));

        // Spaces
        tocFlatGroup(cols, "Spaces", "bg-info text-dark", "S",
                spaces.stream().sorted((a, b) -> a.name().compareTo(b.name()))
                        .map(s -> tocPill("space-" + s.name(), s.name(), "bg-info text-dark", "S"))
                        .collect(Collectors.joining()));

        // Rewrites
        tocFlatGroup(cols, "Rewrites", "bg-warning text-dark", "R",
                rewrites.stream()
                        .filter(r -> r.tid() != null)
                        .map(r -> r.tid().name())
                        .distinct().sorted()
                        .map(n -> tocPill("rewrite-" + n, n, "bg-warning text-dark", "R"))
                        .collect(Collectors.joining()));

        if (cols.isEmpty()) return "";

        return """
               <div class="container-xxl mb-4">
                   <div class="card">
                       <div class="card-header"><h5 class="mb-0 text-primary">Index</h5></div>
                       <div class="card-body">%s</div>
                   </div>
               </div>""".formatted(cols.toString());
    }

    private static String tocPill(final String href, final String name, final String badgeClass, final String letter) {
        return """
               <a href="#%s" class="d-inline-flex justify-content-between align-items-center \
               bg-dark text-light border border-secondary text-decoration-none px-2 py-1 me-1 mb-1" \
               style="min-width:180px;font-size:0.8rem;">
                   <span class="code" style="font-size:1.2rem;">%s</span>
                   <span class="pill-label badge %s ms-2">%s</span>
               </a>""".formatted(href, esc(name), badgeClass, letter);
    }

    private static void tocFlatGroup(final StringBuilder sb, final String title, final String badgeClass,
                                     final String letter, final String pills) {
        if (pills.isEmpty()) return;
        sb.append("""
                  <div class="mb-3">
                      <h6 class="text-primary mb-2">%s</h6>
                      <div class="d-flex flex-wrap">%s</div>
                  </div>""".formatted(title, pills));
    }

    private static String tocTypesSection(final String instsetVid, final String instsetName,
                                          final Set<Type> types) {
        if (types.isEmpty()) return "";

        final Map<String, List<Type>> branches = new TreeMap<>(
                (a, b) -> {
                    int da = a.split("/").length, db = b.split("/").length;
                    return da != db ? Integer.compare(da, db) : a.compareTo(b);
                });

        final int instsetSegs = instsetVid.isEmpty() || instsetVid.equals("/")
                ? 0 : (int) instsetVid.chars().filter(c -> c == '/').count();

        for (final Type t : types.stream()
                .filter(t -> t.vid() != null)
                .sorted((a, b) -> a.vid().name().compareTo(b.vid().name()))
                .toList()) {
            final List<String> allSegs = t.vid().segments();
            if (allSegs.size() <= instsetSegs) continue;
            // Remainder = segments under this instset
            final List<String> rem = allSegs.subList(instsetSegs, allSegs.size() - 1);
            final String branch = rem.isEmpty() ? "" : String.join("/", rem);
            branches.computeIfAbsent(branch, _k -> new ArrayList<>()).add(t);
        }

        final StringBuilder rows = new StringBuilder();
        final List<Type> rootTypes = branches.remove("");

        if (rootTypes != null && !rootTypes.isEmpty()) {
            final String pills = rootTypes.stream()
                    .map(t -> tocPill("type-" + (t.vid() != null ? t.vid().name() : ""),
                            t.vid() != null ? t.vid().name() : "", "bg-primary", "T"))
                    .collect(Collectors.joining());
            if (!branches.isEmpty()) {
                rows.append("<div class=\"d-flex align-items-start mb-1\">")
                        .append(branchLabel(instsetName))
                        .append("<div class=\"d-flex flex-wrap\">").append(pills).append("</div></div>");
            } else {
                rows.append("<div class=\"d-flex flex-wrap\">").append(pills).append("</div>");
            }
        }

        for (final var entry : branches.entrySet()) {
            final String pills = entry.getValue().stream()
                    .map(t -> tocPill("type-" + (t.vid() != null ? t.vid().name() : ""),
                            t.vid() != null ? t.vid().name() : "", "bg-primary", "T"))
                    .collect(Collectors.joining());
            rows.append("<div class=\"d-flex align-items-start mb-1\">")
                    .append(branchLabel(entry.getKey()))
                    .append("<div class=\"d-flex flex-wrap\">").append(pills).append("</div></div>");
        }

        return """
               <div class="mb-3">
                   <h6 class="text-primary mb-2">Types</h6>
                   %s
               </div>""".formatted(rows.toString());
    }

    private static String branchLabel(final String branch) {
        final String[] parts = branch.split("/");
        final String colStyle = "style=\"min-width:6rem;max-width:6rem;text-align:right;" +
                "padding-right:0.6rem;flex-shrink:0;\"";
        if (parts.length <= 1 || (parts.length == 1 && parts[0].isEmpty())) {
            return "<div " + colStyle + "><small class=\"micro-label fw-bold text-secondary\" " +
                    "style=\"font-family:monospace;\">" + esc(branch) + "</small></div>";
        }
        final String parent = String.join("/", java.util.Arrays.copyOf(parts, parts.length - 1));
        final String terminal = esc(parts[parts.length - 1]);
        return "<div " + colStyle + " style=\"min-width:6rem;max-width:6rem;text-align:right;" +
                "padding-right:0.6rem;flex-shrink:0;line-height:1.25;\" class=\"micro-label\">" +
                "<div style=\"font-size:0.6rem;opacity:0.45;font-family:monospace;white-space:nowrap;\">" +
                esc(parent) + "&thinsp;/</div>" +
                "<div><small class=\"mini-label fw-bold text-secondary\" style=\"font-family:monospace;\">" +
                terminal + "</small></div></div>";
    }

    // ── Section: Constants ─────────────────────────────────────────────

    private static String sectionConsts(final String instsetVid, final Set<Obj> consts) {
        if (consts.isEmpty()) return "";
        final StringBuilder cards = new StringBuilder();
        for (final Obj c : consts.stream()
                .filter(c -> c.vid() != null)
                .sorted((a, b) -> a.vid().name().compareTo(b.vid().name()))
                .toList()) {
            final String name = c.vid() != null ? c.vid().name() : "";
            final String uri = c.vid() != null ? c.vid().toString() : "";
            final String gid = "const-" + esc(name);
            final String defn = SER.write(c);
            final String defnBlock = !defn.isEmpty()
                    ? "<div class=\"card-body p-2\"><pre class=\"mb-0\"><code class=\"language-mtron\">"
                    + esc(defn) + "</code></pre></div>" : "";
            final Rec doc = fetchDocByVid(c);
            cards.append("""
                         <div class="card mb-3" id="%s">
                             <div class="card-header d-flex justify-content-between align-items-center py-2">
                                 <span class="code text-secondary fw-bold">%s</span>
                                 <small class="text-muted code">%s</small>
                             </div>
                             %s
                             %s
                         </div>""".formatted(gid, esc(name), esc(uri), defnBlock, renderDoc(doc, gid)));
        }
        return """
               <div class="container-xxl mb-4" id="consts">
                   <h3 class="text-primary mb-3">Constants <span class="pill-label badge bg-secondary">%d</span></h3>
                   %s
               </div>""".formatted(consts.size(), cards.toString());
    }

    // ── Section: Types ─────────────────────────────────────────────────

    private static String sectionTypes(final String instsetVid, final Set<Type> types) {
        if (types.isEmpty()) return "";
        final StringBuilder cards = new StringBuilder();
        for (final Type t : types.stream()
                .filter(t -> t.vid() != null)
                .sorted((a, b) -> a.vid().name().compareTo(b.vid().name()))
                .toList()) {
            final String name = t.vid() != null ? t.vid().name() : "";
            final String uri = t.vid() != null ? t.vid().toString() : "";
            final String gid = "type-" + esc(name);
            final String defn = SER.write(t);
            final String defnBlock = !defn.isEmpty()
                    ? "<div class=\"card-body p-2\"><pre class=\"mb-0\"><code class=\"language-mtron\">"
                    + esc(defn) + "</code></pre></div>" : "";
            final String refines = superTypeRefines(t, instsetVid);
            final Rec doc = fetchDocByVid(t);
            cards.append("""
                         <div class="card mb-3" id="%s">
                             <div class="card-header d-flex justify-content-between align-items-center py-2">
                                 <span>
                                     <span class="code text-primary fw-bold">%s::T</span>
                                     %s
                                 </span>
                                 <small class="text-muted code">%s</small>
                             </div>
                             %s
                             %s
                         </div>""".formatted(gid, esc(name), refines, esc(uri), defnBlock, renderDoc(doc, gid)));
        }
        return """
               <div class="container-xxl mb-4" id="types">
                   <h3 class="text-primary mb-3">Types <span class="pill-label badge bg-primary">%d</span></h3>
                   %s
               </div>""".formatted(types.size(), cards.toString());
    }

    private static String superTypeRefines(final Type t, final String instsetVid) {
        // Use parentType() to get the super type; skip root types
        if (t == null || t.isRootType()) return "";
        final Type parent = t.parentType();
        if (parent == null || parent.isRootType()) return "";

        final fURI superTid = parent.vid() != null ? parent.vid() : parent.tid();
        if (superTid == null) return "";

        final String superName = superTid.name();
        final String superShort = superTid.toString();
        final String superInstset = extractInstset(superShort);

        if (superInstset != null && !superInstset.isEmpty() && !superInstset.equals(instsetVid)) {
            final String target = vidToFilename(superInstset) + "#type-" + esc(superName);
            return "<span class=\"ms-2 text-muted instset-doc-small-code\">refines " +
                    "<a href=\"" + target + "\" class=\"instset-doc-small-code text-info code\">" +
                    esc(superShort) + "::T</a></span>";
        }
        return "<span class=\"ms-2 text-muted instset-doc-small-code\">refines " +
                "<span class=\"code instset-doc-small-code text-info\">" + esc(superShort) + "::T</span></span>";
    }

    // ── Section: Instructions ──────────────────────────────────────────

    private static String sectionInsts(final String instsetVid, final Set<Inst> insts) {
        if (insts.isEmpty()) return "";

        // Group by name
        final Map<String, List<Inst>> groups = new LinkedHashMap<>();
        for (final Inst inst : insts) {
            if (inst.tid() == null) continue;
            final String name = inst.tid().name();
            groups.computeIfAbsent(name, _k -> new ArrayList<>()).add(inst);
        }

        final StringBuilder cards = new StringBuilder();
        for (final String name : groups.keySet().stream().sorted().toList()) {
            final List<Inst> group = groups.get(name);
            final String gid = "inst-" + esc(name);

            // Collect unique docs across polymorphic variants
            final List<Rec> uniqueDocs = new ArrayList<>();
            final Map<String, Integer> seenRaw = new HashMap<>();
            final Map<String, Integer> variantTab = new HashMap<>();
            for (final Inst inst : group) {
                final Rec doc = fetchDocByTid(inst);
                if (doc != null) {
                    final String raw = SER.write(doc);
                    if (!seenRaw.containsKey(raw)) {
                        seenRaw.put(raw, uniqueDocs.size());
                        uniqueDocs.add(doc);
                    }
                    variantTab.put(inst.tid() != null ? inst.tid().toString() : "", seenRaw.get(raw));
                }
            }

            // Render signatures
            final StringBuilder sigs = new StringBuilder();
            for (final Inst inst : group) {
                final String sig = convertShorthand(SER.write(inst));
                final int tabIdx = variantTab.getOrDefault(inst.tid() != null ? inst.tid().toString() : "", 0);
                final String tabId = gid + "-doc-" + tabIdx;
                sigs.append("""
                            <pre class="mb-1 clickable-signature" style="font-size:0.85rem;cursor:pointer;" \
                            onclick="document.getElementById('%s-tab').click();\
                            document.getElementById('%s').scrollIntoView({behavior:'smooth',block:'center'});">\
                            <code class="language-mtron">%s</code></pre>
                            """.formatted(tabId, tabId, esc(sig)));
            }

            final String typeSig = typeSignatureHtml(instsetVid, group.get(0));
            final String vidStr = group.get(0).tid() != null ? group.get(0).tid().toString() : "";
            cards.append("""
                         <div class="card mb-3" id="%s">
                             <div class="card-header d-flex justify-content-between align-items-center py-2 \
                             text-light border-bottom border-secondary">
                                 <span>
                                     <span class="code text-primary fw-bold">%s</span>
                                     %s
                                 </span>
                                 <small class="text-muted code">%s</small>
                             </div>
                             <div class="card-body p-2">%s</div>
                             %s
                         </div>""".formatted(gid, esc(name), typeSig, esc(vidStr),
                    sigs.toString(), renderMultiDoc(uniqueDocs, gid)));
        }

        return """
               <div class="container-xxl mb-4" id="instructions">
                   <h3 class="text-primary mb-3">Instructions <span class="pill-label badge bg-success">%d</span></h3>
                   %s
               </div>""".formatted(insts.size(), cards.toString());
    }

    // ── Section: Rewrites ──────────────────────────────────────────────

    private static String sectionRewrites(final String instsetVid, final Set<Inst> rewrites) {
        if (rewrites.isEmpty()) return "";
        final StringBuilder cards = new StringBuilder();
        for (final Inst rw : rewrites.stream()
                .sorted(Comparator.comparing(a -> a.tid().name()))
                .toList()) {
            final String name = rw.tid().name();
            final String uri = rw.tid().toString();
            final String gid = "rewrite-" + esc(name);
            final String sig = convertShorthand(SER.write(rw));
            final String sigBlock = !sig.isEmpty()
                    ? "<div class=\"card-body p-2\"><pre class=\"mb-0\"><code class=\"language-mtron\">"
                    + esc(sig) + "</code></pre></div>" : "";
            final String typeSig = typeSignatureHtml(instsetVid, rw);
            final Rec doc = fetchDocByTid(rw);
            cards.append("""
                         <div class="card mb-3" id="%s">
                             <div class="card-header d-flex justify-content-between align-items-center py-2">
                                 <span>
                                     <span class="code text-warning fw-bold">%s</span>
                                     %s
                                 </span>
                                 <small class="text-muted code">%s</small>
                             </div>
                             %s
                             %s
                         </div>""".formatted(gid, esc(name), typeSig, esc(uri), sigBlock, renderDoc(doc, gid)));
        }
        return """
               <div class="container-xxl mb-4" id="rewrites">
                   <h3 class="text-primary mb-3">Rewrites <span class="pill-label badge bg-warning text-dark">%d</span></h3>
                   %s
               </div>""".formatted(rewrites.size(), cards.toString());
    }

    // ── Section: Spaces ────────────────────────────────────────────────

    private static String sectionSpaces(final List<SpaceEntry> spaces) {
        if (spaces.isEmpty()) return "";
        final StringBuilder cards = new StringBuilder();
        for (final SpaceEntry sp : spaces.stream().sorted((a, b) -> a.name().compareTo(b.name())).toList()) {
            final String gid = "space-" + esc(sp.name());
            final String spec = sp.typeSpec() != null && !sp.typeSpec().isEmpty()
                    ? "<div class=\"mt-2\"><pre class=\"mb-0\"><code class=\"language-mtron\">"
                    + esc(sp.typeSpec()) + "</code></pre></div>" : "";
            final Rec doc = fetchDocByVid(sp.obj());
            cards.append("""
                         <div class="card mb-3" id="%s">
                             <div class="card-header d-flex justify-content-between align-items-center py-2">
                                 <a href="%s" class="code text-primary fw-bold text-decoration-none">%s</a>
                                 <small class="text-muted code">%s</small>
                             </div>
                             %s
                             %s
                         </div>""".formatted(gid, vidToFilename(sp.vid()), esc(sp.name()), esc(sp.vid()),
                    spec, renderDoc(doc, gid)));
        }
        return """
               <div class="container-xxl mb-4" id="spaces">
                   <h3 class="text-primary mb-3">Spaces <span class="pill-label badge bg-info">%d</span></h3>
                   %s
               </div>""".formatted(spaces.size(), cards.toString());
    }

    // ── Section: Footer ────────────────────────────────────────────────

    private static String sectionFooter(final int buildNumber) {
        final String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return """
               <div class="container-xxl py-3 text-center">
                   <hr class="border-secondary">
                   <small class="text-muted">
                       generated by metatron instset doc generator on build %d-%s<br>
                       (c) PhaseShift Studio, LLC
                   </small>
               </div>""".formatted(0,"0.1-SNAPSHOT");//buildNumber, ts);
    }

    // ── Documentation rendering ────────────────────────────────────────

    private static String renderMultiDoc(final List<Rec> docs, final String gid) {
        if (docs == null || docs.isEmpty()) return "";
        if (docs.size() == 1) return renderSingleDoc(docs.get(0));

        final StringBuilder pills = new StringBuilder();
        final StringBuilder contents = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            final String tabId = gid + "-doc-" + i;
            final String active = i == 0 ? "active" : "";
            final String show = i == 0 ? "show active" : "";
            pills.append("""
                         <li class="nav-item" role="presentation">
                             <button class="nav-link py-1 px-2 %s text-start h-100"
                                 id="%s-tab" data-bs-toggle="pill" data-bs-target="#%s"
                                 type="button" role="tab" aria-selected="%s"
                                 style="font-size:0.75rem;max-width:60px;">
                                 <i class="fas fa-info-circle me-1"></i> %d
                             </button>
                         </li>""".formatted(active, tabId, tabId, i == 0 ? "true" : "false", i + 1));
            contents.append("""
                            <div class="tab-pane fade %s" id="%s" role="tabpanel" aria-labelledby="%s-tab">
                                %s
                            </div>""".formatted(show, tabId, tabId, renderSingleDoc(docs.get(i))));
        }

        return """
               <div class="card-body border-top p-0">
                   <ul class="nav nav-pills p-2 bg-dark" id="%s-pills" role="tablist">
                       <li class="nav-item disabled me-2">
                           <span class="nav-link disabled py-1 px-0 text-muted" style="font-size:0.75rem;">polymorph:</span>
                       </li>
                       %s
                   </ul>
                   <div class="tab-content" id="%s-content">
                       %s
                   </div>
               </div>""".formatted(gid, pills.toString(), gid, contents.toString());
    }

    /** Render a single doc Rec, or nothing. */
    private static String renderDoc(final Rec doc, final String gid) {
        if (doc == null) return "";
        return renderSingleDoc(doc);
    }

    private static String renderSingleDoc(final Rec doc) {
        if (doc == null) return "";
        final StringBuilder parts = new StringBuilder();

        // Description
        final String desc = fieldStr(doc, "desc");
        if (desc != null && !desc.isEmpty()) {
            parts.append("""
                         <div class="card-body border-top py-2">
                             <p class="mb-0 text-light">%s</p>
                         </div>""".formatted(esc(desc)));
        }

        // Signature (dom/rng) + args
        final String dom = fieldStr(doc, "dom");
        final String rng = fieldStr(doc, "rng");
        final boolean hasSig = (dom != null && !dom.isEmpty()) || (rng != null && !rng.isEmpty());
        final Obj argsObj = doc.at("args");
        final boolean hasArgs = argsObj != null && !argsObj.isNoObj() && argsObj instanceof Rec;

        if (hasSig || hasArgs) {
            final StringBuilder inner = new StringBuilder();
            if (hasSig) {
                final String domTxt = esc(dom != null && !dom.isEmpty() ? dom : "?");
                final String rngTxt = esc(rng != null && !rng.isEmpty() ? rng : "?");
                final String sigLabel = hasArgs ? "<small class=\"text-muted fw-bold\">sig:</small>\n    " : "";
                inner.append(sigLabel)
                        .append("<pre class=\"mb-0 text-bright\" style=\"font-family:monospace;font-size:0.8em;\">")
                        .append(domTxt).append(" <span class=\"text-light\">=&gt;</span> ")
                        .append(rngTxt).append("</pre>");
            }
            if (hasArgs) {
                final StringBuilder rows = new StringBuilder();
                for (final Map.Entry<Obj, Obj> e : ((Rec) argsObj).jvm().entrySet()) {
                    rows.append("  ").append(esc(SER.write(e.getKey())))
                            .append(" <span class=\"text-light\">=&gt;</span> <span class=\"text-info\">")
                            .append(esc(SER.write(e.getValue()))).append("</span>\n");
                }
                final String mtClass = hasSig ? " mt-2 d-block" : "";
                inner.append("<small class=\"text-muted fw-bold").append(mtClass).append("\">args:</small>\n")
                        .append("    <pre class=\"mb-0 text-bright\" style=\"font-family:monospace;font-size:0.8em;\">")
                        .append(rows.toString()).append("</pre>");
            }
            parts.append("<div class=\"card-body py-2\">\n    ").append(inner.toString()).append("\n</div>");
        }

        // Examples
        final Obj exObj = doc.at("example");
        if (exObj instanceof Lst lst && !lst.jvm().isEmpty()) {
            final String examples = lst.jvm().stream()
                    .map(e -> esc(e instanceof Str s ? s.jvm() : SER.write(e)))
                    .collect(Collectors.joining("\n"));
            parts.append("""
                         <div class="card-body border-top py-2">
                             <small class="text-muted fw-bold">examples:</small>
                             <pre><code class="language-mtron" style="padding:0 0.75rem 0 !important">%s</code></pre>
                         </div>""".formatted(examples));
        }

        return parts.toString();
    }

    // ── Type signature HTML ────────────────────────────────────────────

    private static String typeSignatureHtml(final String instsetVid, final Inst inst) {
        final Type domain = inst.dom();
        final Type range = inst.rng();
        if (domain == null && range == null) return "";

        final StringBuilder sb = new StringBuilder("<span class=\"ms-1\">");
        if (domain != null) {
            final String domStr = domain.vid() != null ? domain.vid().toString() : domain.tid().toString();
            sb.append("<span class=\"instset-doc-small-code\">")
                    .append(typeLink(domStr, "text-info", "domain", instsetVid))
                    .append("</span>");
        }
        sb.append("<span class=\"text-muted mx-1\">=&gt;</span>");
        if (range != null) {
            final String rngStr = range.vid() != null ? range.vid().toString() : range.tid().toString();
            sb.append("<span class=\"instset-doc-small-code\">")
                    .append(typeLink(rngStr, "text-success", "range", instsetVid))
                    .append("</span>");
        }
        return sb.append("</span>").toString();
    }

    private static String typeLink(final String full, final String cssClass,
                                   final String tooltip, final String instsetVid) {
        if (full == null || full.isEmpty()) {
            return "<span class=\"code " + cssClass + "\">?</span>";
        }

        String shortName = full.substring(full.lastIndexOf('/') + 1);
        String typeName = shortName;
        final String qlessName = typeName.contains("?") ? typeName.substring(0, typeName.indexOf('?')) : typeName;
        final String clessName = qlessName.contains("{") ? qlessName.substring(0, qlessName.indexOf('{')) : qlessName;

        String cardinality = "";
        if (typeName.contains("{")) {
            final String card = typeName.substring(typeName.indexOf('{') + 1, typeName.indexOf('}'));
            cardinality = switch (card) {
                case "*" -> "maybe some ";
                case "?" -> "maybe ";
                case "+" -> "some ";
                case "0" -> "noobj ";
                default -> "";
            };
        }

        if (clessName.equals(clessName.toUpperCase())) {
            return "<a href=\"#\" data-bs-toggle=\"tooltip\" title=\"" + cardinality + "generic " + tooltip
                    + "\" class=\"code " + cssClass + "\">" + esc(shortName) + "</a>";
        }

        final String typeInstset = extractInstset(full);
        final String target;
        if (typeInstset != null && !typeInstset.isEmpty() && !typeInstset.equals(instsetVid)) {
            target = vidToFilename(typeInstset) + "#type-" + esc(clessName);
        } else {
            target = "#type-" + esc(clessName);
        }

        return "<a href=\"" + target + "\" data-bs-toggle=\"tooltip\" title=\""
                + cardinality + tooltip + "\" class=\"code " + cssClass + "\">" + esc(shortName) + "</a>";
    }

    // ========================================================================
    // INDEX PAGE
    // ========================================================================

    private static String generateIndexHtml(final List<Meta> metas,
                                            final List<Set<Type>> allTypes,
                                            final List<Set<Inst>> allInsts,
                                            final List<List<SpaceEntry>> allSpaces,
                                            final boolean websiteTemplate, final String depth,
                                            final int buildNumber) {
        final StringBuilder cards = new StringBuilder();
        for (int i = 0; i < metas.size(); i++) {
            final Meta meta = metas.get(i);
            final String filename = vidToFilename(meta.vid());
            cards.append("""
                         <div class="col-md-6 col-lg-4">
                             <a href="%s" class="text-decoration-none">
                                 <div class="card h-100">
                                     <div class="card-body">
                                         <h5 class="card-title code text-primary">%s</h5>
                                         <div class="d-flex gap-2 flex-wrap">
                                             <span class="pill-label badge bg-primary">%d types</span>
                                             <span class="pill-label badge bg-success">%d instructions</span>
                                             <span class="pill-label badge bg-info text-dark">%d spaces</span>
                                         </div>
                                     </div>
                                 </div>
                             </a>
                         </div>""".formatted(filename, esc(meta.vid()),
                    allTypes.get(i).size(), allInsts.get(i).size(), allSpaces.get(i).size()));
        }

        final String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        final String content = """
                               <div class="container-xxl py-4">
                                   <div class="text-center mb-5">
                                       <h1 class="text-primary glow-text">metatron</h1>
                                       <p class="subtitle text-light">instruction set documentation</p>
                                   </div>
                                   <div class="row g-3">%s</div>
                                   <div class="py-3 text-center mt-5">
                                       <hr class="border-secondary">
                                       <small class="text-muted">
                                           metatron instset doc generator on build %d-%s<br>
                                           (c) PhaseShift Studio, LLC
                                       </small>
                                   </div>
                               </div>""".formatted(cards.toString(), buildNumber, ts);

        if (websiteTemplate) {
            final String header = loadWebsiteHeader(depth);
            final String footer = loadWebsiteFooter(depth);
            if (!header.isEmpty() && !footer.isEmpty()) {
                final String h = header.replace("</head>",
                                "    <link rel=\"stylesheet\" href=\"" + depth + "/css/instset_doc.css\">\n</head>")
                        .replaceAll("<title>.*?</title>", "<title>metatron instruction sets</title>");
                return h + content + footer;
            }
        }

        return """
               <!DOCTYPE html>
               <html lang="en">
               <head>
                   <meta charset="UTF-8">
                   <meta name="viewport" content="width=device-width, initial-scale=1.0">
                   <title>metatron instruction set reference</title>
                   <link rel="stylesheet" href="%s/css/metatron.css">
                   <link rel="stylesheet" href="%s/css/instset_doc.css">
               </head>
               <body>
                   <div class="container">%s</div>
               </body>
               </html>""".formatted(depth, depth, content);
    }

    // ========================================================================
    // SHARED UTILITIES
    // ========================================================================

    /** Get the string value of a record field, or null if missing. */
    private static String fieldStr(final Obj rec, final String field) {
        if (!(rec instanceof Rec r)) return null;
        final Obj val = r.at(field);
        if (val == null || val.isNoObj()) return null;
        return val instanceof Str s ? s.jvm() : SER.write(val);
    }

    /**
     * Convert ?rng=X&dom=Y to the ?X<=Y shorthand form.
     */
    static String convertShorthand(final String text) {
        if (!text.contains("?")) return text;
        final Matcher m = Pattern.compile("\\?[^()\\[]*").matcher(text);
        final StringBuilder sb = new StringBuilder();
        while (m.find()) {
            final String query = m.group().substring(1);
            final Matcher qm = SHORTHAND_PAT.matcher(query);
            String replacement;
            if (qm.find()) {
                final String rng = qm.group(1);
                final String dom = qm.group(2);
                if (rng != null && dom != null) replacement = "?" + rng + "<=" + dom;
                else if (rng != null) replacement = "?" + rng;
                else if (dom != null) replacement = "?<=" + dom;
                else replacement = m.group();
            } else {
                replacement = m.group();
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Extract parent instset from a type URI: /m/tble/lrow → /m/tble.
     */
    static String extractInstset(final String uri) {
        if (uri == null || uri.isEmpty()) return "";
        final String[] parts = uri.replaceAll("^/+", "").split("/");
        if (parts.length <= 1) return parts.length == 1 ? "/" + parts[0] : "";
        return "/" + String.join("/", java.util.Arrays.copyOf(parts, parts.length - 1));
    }

    /**
     * Strip !* or * prefixes from space reference strings.
     */
    static String resolveSpaceRef(final String ref) {
        if (ref == null) return "";
        String r = ref.strip();
        while (!r.isEmpty() && (r.charAt(0) == '!' || r.charAt(0) == '*'))
            r = r.substring(1);
        return r;
    }

    /**
     * Leaf name from a URI path: /m/mach/console → console (before ? or &).
     */
    private static String leafName(final String uri) {
        final String leaf = uri.substring(uri.lastIndexOf('/') + 1);
        final int q = leaf.indexOf('?');
        final int a = leaf.indexOf('&');
        final int cut = (q >= 0 && a >= 0) ? Math.min(q, a) : q >= 0 ? q : a;
        return cut >= 0 ? leaf.substring(0, cut) : leaf;
    }

    /**
     * Convert a VID to a filename: /m/mach → _m_mach.html
     */
    static String vidToFilename(final String vid) {
        return vid.replace("/", "_").replaceFirst("^_", "") + ".html";
    }

    private static String esc(final String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    // ========================================================================
    // TEMPLATE LOADING
    // ========================================================================

    private static String loadWebsiteHeader(final String depth) {
        final Path p = INCLUDES_PATH.resolve("header.html");
        if (!Files.exists(p)) return "";
        try {
            String content = Files.readString(p);
            content = DOC_LINK_PAT.matcher(content).replaceAll("$1=\"" + depth + "/$2/");
            content = content.replace("href=\"index.html\"", "href=\"" + depth + "/index.html\"")
                    .replace("href=\"tractatus.html\"", "href=\"" + depth + "/tractatus.html\"")
                    .replace("location.href='./articles/", "location.href='" + depth + "/articles/")
                    .replace("location.href='tractatus.html'", "location.href='" + depth + "/tractatus.html'")
                    .replace("location.href='index.html'", "location.href='" + depth + "/index.html'")
                    .replace("location.href='./instset/", "location.href='" + depth + "/instset/");
            return content;
        } catch (final IOException e) {
            return "";
        }
    }

    private static String loadWebsiteFooter(final String depth) {
        final Path p = INCLUDES_PATH.resolve("footer.html");
        if (!Files.exists(p)) return "";
        try {
            String content = Files.readString(p);
            content = DOC_LINK_PAT.matcher(content).replaceAll("$1=\"" + depth + "/$2/");
            return content;
        } catch (final IOException e) {
            return "";
        }
    }
}
