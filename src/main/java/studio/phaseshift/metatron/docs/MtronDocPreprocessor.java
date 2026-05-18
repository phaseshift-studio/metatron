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

import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pre-processes adoc text, evaluating {@code [mtron]----...----} blocks
 * and replacing each block's content with the mtron input/output listing.
 *
 * <p>Use {@code [source,mtron]} for display-only mtron code (no evaluation).</p>
 *
 * <h3>Inline directives (within block content)</h3>
 * <ul>
 *   <li>{@code [HIDDEN]} — evaluate, suppress from output</li>
 *   <li>{@code [NO_HEADER]} — suppress listing wrapper</li>
 *   <li>{@code [HEADER] text} — add header line above the block output</li>
 *   <li>{@code [ERROR]} — expect evaluation errors</li>
 *   <li>{@code [NO_OUTPUT]} — suppress output (show {@code ...})</li>
 *   <li>{@code /} at end-of-line — continue expression on next line</li>
 *   <li>{@code [-- <N> --]} — callout number marker (stripped, preserved in adoc colist)</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class MtronDocPreprocessor {

    // ── Mtron block regex ───────────────────────────────────────────

    /**
     * Matches {@code [mtron]----...----} blocks. Group(1)=role, Group(2)=body.
     */
    private static final Pattern MTRON_BLOCK = Pattern.compile(
            "\\[mtron](?:,role=\"([^\"]+)\")?\\R----\\R(.*?)\\R----",
            Pattern.DOTALL);

    // ── Inline directive patterns ───────────────────────────────────

    private static final Pattern CALLOUT = Pattern.compile("\\[--\\s*<([0-9]+)>\\s*--]\\s*$");
    private static final Pattern HIDDEN = Pattern.compile("\\[HIDDEN]");
    private static final Pattern HEADER = Pattern.compile("\\[HEADER]\\s*(.*)");
    private static final Pattern NOHDR = Pattern.compile("\\[NO_HEADER]");
    private static final Pattern ERROR = Pattern.compile("\\[ERROR]");
    private static final Pattern NOOUT = Pattern.compile("\\[NO_OUTPUT]");

    private static final ObjmtronSerializer SER = ObjmtronSerializer.single();

    // ── Process entry point ─────────────────────────────────────────

    /**
     * Process adoc text, evaluating all {@code [mtron]} blocks.
     * Each block is replaced with the same {@code [mtron]} wrapper but with
     * evaluated input/output content (mtron&gt; ... / ==&gt; ... lines).
     *
     * @param adocText raw adoc source text
     * @return processed adoc text with evaluated mtron blocks
     */
    public String process(final String adocText) {
        final Matcher m = MTRON_BLOCK.matcher(adocText);
        final StringBuilder sb = new StringBuilder();
        while (m.find()) {
            final String role = m.group(1);
            final String body = m.group(2);
            final EvalResult result = evaluateBlock(body);

            final String replacement;
            if (result.noHeader) {
                // [NO_HEADER] → output just the evaluated lines, no wrapper
                replacement = String.join("\n", result.lines);
            } else {
                // Build a [source,mtron] block with evaluated content so
                // AsciidoctorJ produces highlight.js-compatible markup
                final StringBuilder block = new StringBuilder();
                block.append("[source,mtron");
                if (role != null)
                    block.append(",role=\"").append(role).append("\"");
                block.append("]\n----\n");
                for (final String line : result.lines) {
                    block.append(line).append('\n');
                }
                block.append("----");
                replacement = block.toString();
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);

        // Rainbow [metatron] replacement
        final String result = sb.toString().contains("[metatron]")
                ? sb.toString().replace("[metatron]", Graphitty.sillyPrint("metatron", true, true))
                : sb.toString();
        return result;
    }

    // ── Block evaluation ────────────────────────────────────────────

    private static class EvalResult {
        final List<String> lines;
        final boolean noHeader;

        EvalResult(final List<String> lines, final boolean noHeader) {
            this.lines = lines;
            this.noHeader = noHeader;
        }
    }

    /**
     * Evaluate a single {@code [mtron]} block's body content.
     * Returns the evaluated input/output lines.
     */
    private EvalResult evaluateBlock(final String body) {
        final var headers = new ArrayList<String>();
        final var lines = new ArrayList<String>();
        boolean noHeader = false;
        final StringBuilder acc = new StringBuilder();

        for (String raw : body.split("\n")) {
            raw = raw.stripTrailing();
            // ── Callout number: [-- <N> --] ──
            final Matcher cm = CALLOUT.matcher(raw);
            String calloutNumber = null;
            if (cm.find()) {
                calloutNumber = cm.group(1);
                raw = cm.replaceAll("").stripTrailing();
            }

            // ── Directives ──
            final Matcher hm = HEADER.matcher(raw);
            if (hm.matches()) {
                headers.add(hm.group(1));
                continue;
            }
            boolean error = false;
            boolean noOutput = false;
            if (NOHDR.matcher(raw).find()) noHeader = true;
            if (ERROR.matcher(raw).find()) error = true;
            if (NOOUT.matcher(raw).find()) noOutput = true;
            final boolean hidden = HIDDEN.matcher(raw).find();

            // ── Line continuation ──
            if (raw.endsWith("/")) {
                acc.append(raw.substring(0, raw.length() - 1).stripTrailing()).append("\n       ");
                continue;
            }

            acc.append(raw);
            String expr = HIDDEN.matcher(acc).replaceAll("").replace("%", "").strip();
            expr = ERROR.matcher(expr).replaceAll("");
            expr = NOHDR.matcher(expr).replaceAll("");
            expr = NOOUT.matcher(expr).replaceAll("");
            expr = CALLOUT.matcher(expr).replaceAll("");
            acc.setLength(0);
            if (expr.isEmpty()) continue;

            // ── Build input line ──
            if (!hidden) {
                String prefix = "mtron> " + expr;
                if (calloutNumber != null)
                    prefix += " ".repeat(5) + "<" + calloutNumber + ">";
                lines.add(prefix);
            }

            // ── Evaluate ──
            try {
                final Obj result = ObjmtronSerializer.parse(expr).apply();
                if (result.isFail() && !error) {
                    System.err.printf("ERROR in docs with no [ERROR] specifier (docs are buggy): %s\nfor expression:\n%s\n", result, expr);
                    //System.exit(1);
                }
                if (!hidden && !noOutput && !result.isNoObj()) {
                    result.stream().forEach(o -> lines.add("==>" + SER.write(o)));
                } else if (noOutput) {
                    lines.add(Graphitty.sillyPrint("...", true, true));
                }
                // Clear fail stack so errors don't leak across blocks
                if (!hidden) ObjmtronSerializer.parse("/sys/fail/+ -> noobj").apply();
            } catch (final Exception e) {
                if (!hidden) lines.add("==>ERROR: " + e.getMessage());
            }
        }

        // ── Prepend headers ──
        if (!headers.isEmpty()) {
            final var all = new ArrayList<String>(headers);
            all.addAll(lines);
            return new EvalResult(all, noHeader);
        }
        return new EvalResult(lines, noHeader);
    }

    // ── Public helpers (used by DocRunner) ──────────────────────────

    /**
     * Evaluate prefix expressions (piggy-format blocks) for side-effects.
     */
    public static void evalPrefixBlocks(final List<String> prefixLines) {
        if (prefixLines == null || prefixLines.isEmpty()) return;
        final Matcher m = PIggy.matcher(String.join("\n", prefixLines));
        while (m.find()) {
            for (String raw : m.group(2).split("\n")) {
                final String expr = HIDDEN.matcher(raw).replaceAll("").replace("%", "").strip();
                if (!expr.isEmpty()) {
                    try {
                        ObjmtronSerializer.parse(expr).apply();
                    } catch (final Exception ignored) {
                    }
                }
            }
        }
    }

    // ── Piggy-block pattern (prefix only) ───────────────────────

    private static final Pattern PIggy = Pattern.compile(
            "\\+\\+\\+\\+\\h*\\R" +
                    "\\h*<!-- \\uD83D\\uDC16(?:\\h+([^\\r\\n]+))?\\R" +
                    "(.*?)" +
                    "\\R\\h*-->\\R\\h*\\+\\+\\+\\+",
            Pattern.DOTALL);
}
