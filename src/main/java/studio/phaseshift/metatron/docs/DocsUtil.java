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
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.ui.uiInstSet;
import studio.phaseshift.metatron.isa.rdf.rdfInstSet;
import studio.phaseshift.metatron.isa.tble.tbleInstSet;
import studio.phaseshift.metatron.isa.vec.vecInstSet;
import studio.phaseshift.metatron.isa.web.webInstSet;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static studio.phaseshift.metatron.Tokens.BOOT;
import static studio.phaseshift.metatron.Tokens.INFO;
import static studio.phaseshift.metatron.Tokens.LOGG;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Shared helpers for the docs pipeline — the small cross-runner primitives that
 * {@link MarkdownRunner} (skills), {@link AsciiDocRunner} (adoc book), and
 * {@link InstSetDocGenerator} (instset reference) each used to carry as private
 * near-duplicates: the VM boot/instset registration, the frontmatter parser,
 * and the {@code depth}/{@code esc}/{@code leafName}/{@code elapsedMs} string and
 * path utilities. Consolidated here so a new docs species (or a unified content
 * model) reaches for one home instead of re-implementing them.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class DocsUtil {

    private DocsUtil() {
    }

    /**
     * Boot the metatron VM and register the instruction sets used by the docs
     * (the union of every domain the three runners touch). This is the per-file
     * cost that {@code --single-boot} amortizes to once; a fresh {@code InstSet}
     * instance is created per call so reused boots never share state.
     */
    public static void bootVM(final String boot) {
        BootLoader.BOOTING = true;
        BootLoader.TESTING = true;
        BootLoader.load(MRec.rec(uri(LOGG), uri(INFO), uri(BOOT), uri(boot)));
        for (final InstSet is : new InstSet[]{
                new mathInstSet(), new webInstSet(), new iotInstSet(),
                new grphInstSet(), new llmInstSet(), new tbleInstSet(),
                new dcmntInstSet(), new rdfInstSet(), new dckrInstSet(),
                new uiInstSet(), new vecInstSet()
        }) {
            Machine.root().addSpace(is);
            Machine.writeToSpace(is);
            is.setup();
        }
        // hardcode type checker in support of runtime inst resolution
        TypeCheck.enable(TypeCheck.values());
        TypeCheck.disable(TypeCheck.code_resolve);
    }

    /**
     * Elapsed milliseconds since {@code startNanos}.
     */
    public static long elapsedMs(final long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * HTML-escape a string for safe interpolation into generated markup.
     */
    public static String esc(final String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /**
     * Relative path ({@code ../..}) from the output file's directory up to the
     * website root, so the shared header/footer assets resolve at any depth.
     */
    public static String depth(final Path websiteRoot, final Path htmlFile) {
        final Path rel = websiteRoot.relativize(htmlFile);
        final int segments = rel.getParent() == null ? 0 : rel.getParent().getNameCount();
        return String.join("/", Collections.nCopies(segments, ".."));
    }

    /**
     * Leaf name from a URI path: {@code /m/mach/console → console} (before {@code ?}
     * or {@code &}).
     */
    public static String leafName(final String uri) {
        final String leaf = uri.substring(uri.lastIndexOf('/') + 1);
        final int q = leaf.indexOf('?');
        final int a = leaf.indexOf('&');
        final int cut = (q >= 0 && a >= 0) ? Math.min(q, a) : q >= 0 ? q : a;
        return cut >= 0 ? leaf.substring(0, cut) : leaf;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Frontmatter parsing (moved from MarkdownRunner)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * A parsed skill document: name, description, and the frontmatter-free body.
     */
    public record FrontMatter(String name, String description, String body) {
    }

    /**
     * Split the YAML frontmatter from the markdown body and surface the
     * {@code name} and {@code description} fields for the page chrome.
     */
    public static FrontMatter split(final String md) {
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
     * Best-effort extraction of one frontmatter field, honoring every form the
     * skill docs actually use. A value owns the lines indented deeper than its
     * key, so a wrapped <b>plain scalar</b> ({@code description:} on its own line,
     * prose indented under it) folds those lines with spaces, while the block
     * styles keep their own shape: {@code key: |} is literal — line breaks are
     * kept — and {@code key: >} folds them, one break becoming a space and a blank
     * line a break. Both block styles tolerate the {@code -} / {@code +} chomping
     * and explicit-indent indicators, and trailing breaks are dropped because
     * every consumer here renders the value as prose.
     *
     * <p>A key matches only when its colon ends the name ({@code name:} never
     * matches {@code namespace:}).
     */
    public static String extract(final String front, final String key) {
        final String[] lines = front.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            final String raw = lines[i];
            final String line = raw.strip();
            final String prefix = key + ":";
            if (!line.startsWith(prefix)) continue;
            if (line.length() > prefix.length() && !Character.isWhitespace(line.charAt(prefix.length()))) continue;
            final String header = line.substring(prefix.length()).strip();
            final int indent = indentOf(raw);
            return header.startsWith("|") || header.startsWith(">")
                    ? block(lines, i, indent, header)
                    : plain(lines, i, indent, header);
        }
        return "";
    }

    /**
     * A plain scalar: its header text plus any deeper-indented lines it wraps
     * onto, folded back into one line.
     */
    private static String plain(final String[] lines, final int keyLine, final int indent, final String header) {
        final List<String> parts = new ArrayList<>();
        if (!header.isEmpty()) parts.add(header);
        for (int j = keyLine + 1; j < lines.length; j++) {
            final String line = lines[j].strip();
            if (line.isEmpty() || indentOf(lines[j]) <= indent) break;
            parts.add(line);
        }
        return String.join(" ", parts);
    }

    /**
     * A {@code |} (literal) or {@code >} (folded) block scalar, ending at the first
     * line indented no deeper than the key.
     */
    private static String block(final String[] lines, final int keyLine, final int indent, final String header) {
        final int explicit = header.chars().filter(Character::isDigit).findFirst().orElse('0') - '0';
        final List<String> body = new ArrayList<>();
        int base = explicit > 0 ? indent + explicit : -1;
        for (int j = keyLine + 1; j < lines.length; j++) {
            final String raw = lines[j];
            if (raw.isBlank()) {
                body.add("");
                continue;
            }
            if (indentOf(raw) <= indent) break;
            final String stripped = raw.stripTrailing();
            if (base < 0) base = indentOf(raw);
            body.add(stripped.substring(Math.min(base, stripped.length())));
        }
        while (!body.isEmpty() && body.getLast().isBlank()) body.removeLast();
        return header.startsWith("|") ? String.join("\n", body) : fold(body);
    }

    /**
     * YAML folded style: the break between two lines becomes a space, and each
     * blank line becomes a break.
     */
    private static String fold(final List<String> body) {
        final StringBuilder folded = new StringBuilder();
        int breaks = 0;
        for (final String line : body) {
            if (line.isEmpty()) {
                breaks++;
                continue;
            }
            if (!folded.isEmpty()) folded.append(breaks == 0 ? " " : "\n".repeat(breaks));
            folded.append(line);
            breaks = 0;
        }
        return folded.toString();
    }

    /**
     * Column of the first non-whitespace character. A blank line answers
     * {@link Integer#MAX_VALUE} so it never ends a block.
     */
    private static int indentOf(final String line) {
        if (line.isBlank()) return Integer.MAX_VALUE;
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
        return i;
    }
}
