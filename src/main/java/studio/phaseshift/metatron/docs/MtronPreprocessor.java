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

import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Pre-processes text containing mtron code blocks, evaluating each block's
 * expressions and replacing the block content with the mtron input/output
 * listing (mtron&gt; ... / ==&gt; ... lines).
 *
 * <p>The block format is configured at construction time by a block header
 * {@link Pattern} (see {@link #ADOC_HEADER} and {@link #MARKDOWN_HEADER}). The
 * pattern's capture groups define the contract: the LAST group is the block
 * body and, for adoc, the group before it is the block's {@code role}. A
 * two-group header produces adoc output ({@code [source,mtron]}); a one-group
 * header produces markdown output ({@code ```mtron} fenced).</p>
 *
 * <p>For display-only mtron code (no evaluation), use {@code [source,mtron]}
 * (adoc) or a plain {@code ```mtron} fence (markdown) — these are not matched
 * by either header pattern.</p>
 *
 * <h3>Inline directives (within block content)</h3>
 * <ul>
 *   <li>{@code [HIDDEN]} — evaluate, suppress from output</li>
 *   <li>{@code [NO_HEADER]} — suppress listing wrapper</li>
 *   <li>{@code [HEADER] text} — add header line above the block output</li>
 *   <li>{@code [ERROR]} — expect evaluation errors</li>
 *   <li>{@code [NO_OUTPUT]} — suppress output (show {@code ...})</li>
 *   <li>{@code [NO_PROMPT]} — suppress the {@code mtron>} input prompt</li>
 *   <li>{@code [MAXOUTPUT N]} — cap output at N lines</li>
 *   <li>{@code /} at end-of-line — continue expression on next line</li>
 *   <li>{@code [-- <N> --]} — callout number marker (stripped, preserved in adoc colist)</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class MtronPreprocessor {

    // ── Block header patterns ─────────────────────────────────────

    /**
     * adoc {@code [mtron]----...----} blocks. group(1)=role (unused in practice),
     * group(2)=body.
     */
    public static final Pattern ADOC_HEADER = Pattern.compile(
            "\\[mtron](?:,role=\"([^\"]+)\")?\\R----\\R(.*?)\\R----",
            Pattern.DOTALL);

    /**
     * markdown {@code ```mtron_pre ... ```} fenced blocks. group(1)=body.
     */
    public static final Pattern MARKDOWN_HEADER = Pattern.compile(
            "(?m)^```mtron_pre[ \\t]*\\R(.*?)^```[ \\t]*(?:\\R|$)",
            Pattern.DOTALL);

    private final Pattern header;

    /**
     * @param header block header pattern; the last capture group is the block
     *               body and an optional group before it is the adoc role —
     *               the capture layout also selects the output format
     */
    public MtronPreprocessor(final Pattern header) {
        this.header = header;
    }

    // ── Inline directive patterns ───────────────────────────────────

    private static final Pattern CALLOUT = Pattern.compile("\\[--\\s*<([0-9]+)>\\s*--]\\s*$");
    private static final Pattern HIDDEN = Pattern.compile("\\[HIDDEN]");
    private static final Pattern HEADER = Pattern.compile("\\[HEADER]\\s*(.*)");
    private static final Pattern NOHDR = Pattern.compile("\\[NO_HEADER]");
    private static final Pattern ERROR = Pattern.compile("\\[ERROR]");
    private static final Pattern NOOUT = Pattern.compile("\\[NO_OUTPUT]");
    private static final Pattern NOPROMPT = Pattern.compile("\\[NO_PROMPT]");
    private static final Pattern MAXOUTPUT = Pattern.compile("\\[MAXOUTPUT (\\d+)]");

    private static final ObjmtronSerializer SER;

    static {
        SER = new ObjmtronSerializer();
        SER.at(uri("clip"), rec("str", jnt(35), "rec", jnt(7), "lst", jnt(7)), MUTABLE);
    }

    private static final GraphittyLogger LOG = Graphitty.log(MtronPreprocessor.class);

    // ── Process entry point ─────────────────────────────────────────

    /**
     * Process text, evaluating all mtron blocks matched by the configured
     * header. Each block is replaced with the same wrapper but with evaluated
     * input/output content (mtron&gt; ... / ==&gt; ... lines).
     *
     * @param text raw adoc or markdown source text
     * @return processed text with evaluated mtron blocks
     */
    public String process(final String text) {
        final Matcher m = this.header.matcher(text);
        // Two capture groups → adoc (role+body); one group → markdown (body).
        final boolean adoc = m.groupCount() >= 2;
        final StringBuilder sb = new StringBuilder();
        while (m.find()) {
            final String role = adoc ? m.group(1) : null;
            final String body = m.group(m.groupCount());
            final EvalResult result = evaluateBlock(body);

            final String replacement;
            if (result.noHeader) {
                // [NO_HEADER] → output just the evaluated lines, no wrapper
                replacement = String.join("\n", result.lines);
            } else {
                replacement = adoc ? wrapAdoc(role, result.lines) : wrapMarkdown(result.lines);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);

        // Rainbow [metatron] replacement (adoc only)
        final String out = sb.toString();
        return adoc && out.contains("[metatron]")
                ? out.replace("[metatron]", Graphitty.sillyPrint("metatron", true, true))
                : out;
    }

    /**
     * adoc output wrapper: {@code [source,mtron,role="..."]} listing block.
     */
    private static String wrapAdoc(final String role, final List<String> lines) {
        final StringBuilder block = new StringBuilder();
        block.append("[source,mtron");
        if (role != null)
            block.append(",role=\"").append(role).append("\"");
        block.append("]\n----\n");
        for (final String line : lines)
            block.append(line).append('\n');
        block.append("----");
        return block.toString();
    }

    /**
     * markdown output wrapper: {@code ```mtron} fenced listing block.
     */
    private static String wrapMarkdown(final List<String> lines) {
        final StringBuilder block = new StringBuilder();
        block.append("```mtron\n");
        for (final String line : lines)
            block.append(line).append('\n');
        block.append("```");
        return block.toString();
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
     * Evaluate a single block's body content.
     * Returns the evaluated input/output lines.
     */
    private EvalResult evaluateBlock(final String body) {
        final var headers = new ArrayList<String>();
        final var lines = new ArrayList<String>();
        boolean noHeader = false;
        final StringBuilder acc = new StringBuilder();

        boolean noPrompt = false;
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
            int maxOutput = 100;
            if (NOHDR.matcher(raw).find()) noHeader = true;
            if (ERROR.matcher(raw).find()) error = true;
            if (NOOUT.matcher(raw).find()) noOutput = true;
            if (NOPROMPT.matcher(raw).find()) noPrompt = true;
            final Matcher maxm = MAXOUTPUT.matcher(raw);
            if (maxm.find()) maxOutput = Integer.parseInt(maxm.group(1));
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
            expr = NOPROMPT.matcher(expr).replaceAll("");
            expr = MAXOUTPUT.matcher(expr).replaceAll("");
            acc.setLength(0);
            if (expr.isEmpty()) continue;

            // ── Build input line ──
            if (!hidden) {
                String prefix = (noPrompt ? "" : "mtron> ") + expr;
                if (calloutNumber != null)
                    prefix += " ".repeat(5) + "​" + calloutNumber + "​";
                lines.add(prefix);
            }

            // ── Evaluate ──
            try {
                TypeCheck.disable(TypeCheck.code_resolve, TypeCheck.inst_rng);
                final Obj input = ObjmtronSerializer.singleNoClip().read(expr);
                final Obj result = ObjmtronSerializer.eval(expr);
                if (result.isFail() && !error) {
                    LOG.error("no [ERROR] modifier in code block (docs are buggy): %s\n\t[{{r}}bad expression{{X}}]: %s\n", result, expr);
                    //System.exit(1);
                }
                if (!hidden && !noOutput && !result.isNoObj()) {
                    result.stream().forEach(o -> lines.add("==>" + SER.write(o).replace("\n", "\n   ")));
                } else if (noOutput) {
                    lines.add("...");
                } else if (!result.isNoObj() && input.isType()) {
                    lines.add("==>" + SER.write(input).replace("\n", "\n   ")); // replacement so second+ lines are indented past the result prompt
                }
                // Clear fail stack so errors don't leak across blocks
                if (!hidden) ObjmtronSerializer.singleNoClip().inputBytes("/sys/fail/+ -> noobj").apply();
            } catch (final Exception e) {
                if (!hidden) lines.add("==>ERROR: " + e.getMessage());
            }
            if (lines.size() > maxOutput) {
                final List<String> shortList = new ArrayList<>(lines.subList(0, maxOutput));
                lines.clear();
                lines.addAll(shortList);
                lines.add("   ...");
            }
            final int maxOutputFinal = maxOutput;
            final List<String> newShort = new ArrayList<>(lines.stream().map(l -> new ArrayList<>(List.of(l.split("\n")))).map(l -> {
                if (l.size() > maxOutputFinal) {
                    final List<String> shortList = new ArrayList<>(l.subList(0, maxOutputFinal));
                    l.clear();
                    l.addAll(shortList);
                    l.add("   ...");
                }
                return l.stream().reduce("", (a, b) -> a + "\n" + b).trim();
            }).toList());
            lines.clear();
            lines.addAll(newShort);
            noPrompt = false;
        }

        // ── Prepend headers ──
        if (!headers.isEmpty()) {
            final var all = new ArrayList<>(headers);
            all.addAll(lines);
            return new EvalResult(all, noHeader);
        }
        return new EvalResult(lines, noHeader);
    }
}
