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
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronUISerializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * {@link Pattern} (see {@link #ADOC_HEADER} and {@link #MARKDOWN_HEADER}) plus
 * whether that pattern is the adoc species. The pattern's SECOND capture group
 * is the block body; the first is the adoc {@code role} or, for markdown, the
 * fence attributes (see below).</p>
 *
 * <h3>Fence attributes (markdown)</h3>
 * <p>A markdown fence may carry attributes after {@code mtron_pre}:</p>
 * <pre>
 * ```mtron_pre cast=repl-basics cols=100 rows=30 typing_delay=0.05
 * 1 + 1
 * ```
 * </pre>
 * <p>{@code cast=<name>} makes the block a terminal demo: {@link #compile}
 * returns its {@link Block}, and the caller (the markdown pass) renders the
 * block to {@code <name>.cast} while the block itself keeps rendering as the
 * listing. The listing and the recording are two renderings of ONE evaluation,
 * so they cannot disagree. {@code cols}, {@code rows} and {@code typing_delay}
 * shape the recording only.</p>
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
 *   <li>{@code [-- text --]} — a trailing annotation: kept in the listing, shown
 *       as the recording's caption bar, never typed</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class MtronPreprocessor {

    // ── Block header patterns ─────────────────────────────────────

    /**
     * adoc {@code [mtron]----...----} blocks. group(1)=role, group(2)=body.
     */
    public static final Pattern ADOC_HEADER = Pattern.compile(
            "\\[mtron](?:,role=\"([^\"]+)\")?\\R----\\R(.*?)\\R----",
            Pattern.DOTALL);

    /**
     * markdown {@code ```mtron_pre ... ```} fenced blocks. group(1)=fence
     * attributes (may be null), group(2)=body.
     */
    public static final Pattern MARKDOWN_HEADER = Pattern.compile(
            "(?m)^```mtron_pre(?:[ \\t]+([^\\r\\n]*))?[ \\t]*\\R(.*?)^```[ \\t]*(?:\\R|$)",
            Pattern.DOTALL);

    /** The fence attributes a markdown block may carry. */
    private static final Set<String> FENCE_ATTRIBUTES = Set.of(
            "cast", "cols", "rows", "typing_delay", "speed_rates", "speed_default", "title", "place");

    /**
     * One evaluated statement: what was typed, the exact listing line the page
     * shows for it (prompt + callout), what the console answered with, and
     * whether it is hidden from the reader (evaluated, never shown).
     */
    public record Statement(String input, String listing, List<String> output,
                            boolean hidden, boolean noPrompt, String caption) {
    }

    /**
     * One evaluated block: its statements, the {@code [HEADER]} lines above it,
     * and the fence attributes it declared.
     */
    public record Block(List<Statement> statements, List<String> headers,
                        Map<String, String> attributes) {
    }

    /**
     * A compiled document: the processed text (what the page renders) and the
     * blocks that opted into a terminal recording.
     */
    public record Result(String text, List<Block> blocks) {
    }

    private final Pattern header;
    private final boolean adoc;

    /**
     * @param header block header pattern; its second capture group is the body
     * @param adoc   true for the adoc species (role + adoc listing wrapper),
     *               false for the markdown species (attributes + fence wrapper)
     */
    public MtronPreprocessor(final Pattern header, final boolean adoc) {
        this.header = header;
        this.adoc = adoc;
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
    /** A trailing documentation annotation: shown as a caption, never typed. */
    private static final Pattern ANNOTATION = Pattern.compile("\\[--(.*?)--]\\s*$");

    private static final ObjmtronUISerializer SER;

    static {
        SER = ObjmtronUISerializer.blackWhite();
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
        return this.compile(text).text();
    }

    /**
     * Evaluate every block once and return BOTH renderings' material: the
     * processed text the page uses, and the blocks that asked for a recording.
     *
     * @param text raw adoc or markdown source text
     */
    public Result compile(final String text) {
        final Matcher m = this.header.matcher(text);
        final StringBuilder sb = new StringBuilder();
        // A recording's launcher belongs at the TOP of the page (it opens a
        // modal, so its position in the prose is irrelevant) — collect the
        // markers and prepend them once the body is assembled.
        final List<Block> blocks = new ArrayList<>();
        while (m.find()) {
            final String first = m.group(1);
            final Map<String, String> attributes = this.adoc ? Map.of() : attributes(first);
            final String role = this.adoc ? first : null;
            final EvalResult result = this.evaluateBlock(m.group(2));

            final String listing = result.noHeader
                    ? String.join("\n", lines(result))
                    : (this.adoc ? wrapAdoc(role, lines(result)) : wrapMarkdown(lines(result)));
            final String cast = attributes.get("cast");
            final StringBuilder marker = new StringBuilder();
            if (cast != null) {
                marker.append("<!-- cast: ").append(cast)
                        .append(attributes.containsKey("speed_rates")
                                ? " speed_rates=\"" + attributes.get("speed_rates") + "\"" : "")
                        .append(attributes.containsKey("speed_default")
                                ? " speed_default=\"" + attributes.get("speed_default") + "\"" : "")
                        .append(attributes.containsKey("title")
                                ? " title=\"" + attributes.get("title") + "\"" : "")
                        .append(attributes.containsKey("place")
                                ? " place=\"" + attributes.get("place") + "\"" : "")
                        .append(" -->\n");
            }
            // in place: the html pass decides whether this pill stays here (inline),
            // moves to the top row (top) or appears in both places (both)
            m.appendReplacement(sb, Matcher.quoteReplacement(marker + listing));
            blocks.add(new Block(result.statements, result.headers, attributes));
        }
        m.appendTail(sb);

        // The launchers ride at the top of the page BODY — after any YAML
        // frontmatter, which the site pass reads the page title from (a comment
        // before the opening '---' would hide it). Rainbow [metatron]
        // replacement is adoc-only.
        final String out = sb.toString();
        return new Result(this.adoc && out.contains("[metatron]")
                ? out.replace("[metatron]", Graphitty.sillyPrint("metatron", true, true))
                : out, blocks);
    }

    /**
     * The fence attributes of a markdown block, validated: an unknown key is a
     * typo in a doc, so it fails loudly rather than being silently ignored.
     */
    static Map<String, String> attributes(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        final Map<String, String> out = new LinkedHashMap<>();
        final String text = raw.strip();
        int i = 0;
        while (i < text.length()) {
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
            if (i >= text.length()) break;
            final int eq = text.indexOf('=', i);
            if (eq < 0) {
                throw new IllegalArgumentException("attribute needs a value: '" + text.substring(i) + "'");
            }
            final String key = text.substring(i, eq).strip();
            final String value;
            final int next;
            if (eq + 1 < text.length() && text.charAt(eq + 1) == '"') {
                // quoted: a title may contain spaces and punctuation
                final int close = text.indexOf('"', eq + 2);
                if (close < 0) {
                    throw new IllegalArgumentException("unterminated quoted value for '" + key + "'");
                }
                value = text.substring(eq + 2, close);
                next = close + 1;
            } else {
                int end = eq + 1;
                while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
                value = text.substring(eq + 1, end);
                next = end;
            }
            if (!FENCE_ATTRIBUTES.contains(key)) {
                throw new IllegalArgumentException("unknown mtron_pre attribute '" + key + "'");
            }
            if ("cast".equals(key) && !value.matches("[A-Za-z0-9._-]+")) {
                throw new IllegalArgumentException("cast= needs a name of [A-Za-z0-9._-]+, got '" + value + "'");
            }
            out.put(key, value);
            i = next;
        }
        return out;
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
            block.append(Graphitty.strip(line)).append('\n');
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
            block.append(Graphitty.strip(line)).append('\n');
        block.append("```");
        return block.toString();
    }

    /**
     * The listing's lines: every {@code [HEADER]} line first (as the page has
     * always shown them), then each statement's prompt + output.
     */
    private static List<String> lines(final EvalResult result) {
        final List<String> lines = new ArrayList<>();
        for (final Statement statement : result.statements) {
            if (!statement.hidden())
                lines.add(statement.listing());
            lines.addAll(statement.output());
        }
        if (result.headers.isEmpty())
            return lines;
        final List<String> all = new ArrayList<>(result.headers);
        all.addAll(lines);
        return all;
    }

    // ── Block evaluation ────────────────────────────────────────────

    private static class EvalResult {
        final List<Statement> statements;
        final List<String> headers;
        final boolean noHeader;

        EvalResult(final List<Statement> statements, final List<String> headers,
                   final boolean noHeader) {
            this.statements = statements;
            this.headers = headers;
            this.noHeader = noHeader;
        }
    }

    /**
     * Evaluate a single block's body content.
     * Returns the evaluated statements.
     */
    private EvalResult evaluateBlock(final String body) {
        final var headers = new ArrayList<String>();
        final var statements = new ArrayList<Statement>();
        boolean noHeader = false;
        final StringBuilder acc = new StringBuilder();

        // ── Statement-scoped directive state ──────────────────────────────
        // Directives are read from any physical line of a statement and apply
        // to the whole statement — including a multi-line statement whose
        // physical lines end with "/" and are joined before evaluation.  The
        // state is reset once the statement has been evaluated.
        boolean noPrompt = false;
        boolean noOutput = false;
        boolean error = false;
        boolean hidden = false;
        int maxOutput = 100;

        for (String raw : body.split("\n")) {
            raw = raw.stripTrailing();
            // ── Callout number: [-- <N> --] ──
            final Matcher cm = CALLOUT.matcher(raw);
            String calloutNumber = null;
            if (cm.find()) {
                calloutNumber = cm.group(1);
                raw = cm.replaceAll("").stripTrailing();
            }

            // ── Header line ──
            final Matcher hm = HEADER.matcher(raw);
            if (hm.matches()) {
                headers.add(hm.group(1));
                continue;
            }

            // ── Directives (statement-scoped) ──
            if (NOHDR.matcher(raw).find()) noHeader = true;
            if (ERROR.matcher(raw).find()) error = true;
            if (NOOUT.matcher(raw).find()) noOutput = true;
            if (NOPROMPT.matcher(raw).find()) noPrompt = true;
            if (HIDDEN.matcher(raw).find()) hidden = true;
            final Matcher maxm = MAXOUTPUT.matcher(raw);
            if (maxm.find()) maxOutput = Integer.parseInt(maxm.group(1));

            // ── Line continuation ──
            if (raw.endsWith("/")) {
                acc.append(raw.substring(0, raw.length() - 1).stripTrailing()).append("\n       ");
                continue;
            }

            acc.append(raw);
            // Strip AFTER the directive tokens are removed so that a directive
            // such as "[MAXOUTPUT 5] code" does not leave its trailing space
            // behind and render as a doubled space after the "mtron> " prompt.
            String expr = HIDDEN.matcher(acc).replaceAll("").replace("%", "");
            expr = ERROR.matcher(expr).replaceAll("");
            expr = NOHDR.matcher(expr).replaceAll("");
            expr = NOOUT.matcher(expr).replaceAll("");
            expr = CALLOUT.matcher(expr).replaceAll("");
            expr = NOPROMPT.matcher(expr).replaceAll("");
            expr = MAXOUTPUT.matcher(expr).replaceAll("").strip();
            acc.setLength(0);
            if (expr.isEmpty()) continue;

            // A trailing [-- ... --] is the demo's caption (the page shows it
            // in the listing; the player shows it in the caption bar) — it is
            // documentation, not keystrokes, so the recording never types it.
            final Matcher annotation = ANNOTATION.matcher(expr);
            final String caption = annotation.find() ? annotation.group(1).strip() : null;
            final String typed = annotation.replaceAll("").strip();

            // ── Evaluate: the results accumulate into `outputs` so that the
            //    input line(s) never count toward, nor get clipped by,
            //    [MAXOUTPUT] ──
            final var outputs = new ArrayList<String>();
            try {
                Typer.Helper.set(Typer.CODE_RESOLVE, false);
                Typer.Helper.set(Typer.INST_RNG, false);
                final Obj input = SER.read(expr);
                final Obj result = input.apply();
                if (result.isFail() && !error) {
                    LOG.error("no [ERROR] modifier in code block (docs are buggy): %s\n\t[{{r}}bad expression{{X}}]: %s\n", result, expr);
                    //System.exit(1);
                }
                if (!hidden && !noOutput && !result.isNoObj()) {
                    result.stream().forEach(o -> outputs.add(resultLine(SER.write(o))));
                } else if (noOutput) {
                    outputs.add("...");
                } else if (!result.isNoObj() && input.isType()) {
                    outputs.add(resultLine(SER.write(input)));
                }
                // clear the machine's fail stack so errors don't leak across blocks (a block's own evaluation files
                // its fails under the computer that ran it, ~/processor/computer/<n>/fail)
                if (!hidden) SER.inputBytes("~/processor/fail -> noobj").apply();
            } catch (final Exception e) {
                if (!hidden) outputs.add("==>ERROR: " + e.getMessage());
            }

            // ── [MAXOUTPUT n]: clip the statement's rendered result lines
            //    only — the input line never counts toward the limit ──
            if (!outputs.isEmpty())
                MtronPreprocessor.clipOutputs(outputs, maxOutput);

            // ── An annotation-only line ([-- text --]) is the recording's
            //    status-bar note, not a typed statement: no prompt, no listing
            //    line, just a marker. ([== text ==] stays an inline comment,
            //    because mtron itself treats it as one.) ──
            if (typed.isEmpty() && caption != null) {
                statements.add(new Statement("", "", List.of(), true, true, caption));
                noPrompt = false;
                noOutput = false;
                error = false;
                hidden = false;
                maxOutput = 100;
                continue;
            }

            // ── The statement: the exact line the page shows, and the raw
            //    expression a recording would type ──
            // [NO_PROMPT] drops the prompt but keeps its width as indent, so a
            // section-break comment still lines up with the statements
            final String listing = (noPrompt ? " ".repeat("mtron> ".length()) : "mtron> ") + expr
                    + (calloutNumber == null ? "" : " ".repeat(5) + "\u200B" + calloutNumber + "\u200B");
            statements.add(new Statement(typed, listing, List.copyOf(outputs), hidden, noPrompt,
                    caption == null || caption.isEmpty() ? null : caption));

            // statement complete — reset statement-scoped directive state
            noPrompt = false;
            noOutput = false;
            error = false;
            hidden = false;
            maxOutput = 100;
        }

        return new EvalResult(statements, headers, noHeader);
    }

    /**
     * One result line: the {@code ==>} prompt, then the rendered value with each of its
     * continuation lines shifted past that prompt ({@link Graphitty#RESULT_PROMPT_WIDTH}) so
     * they read under the value rather than under the prompt.
     */
    private static String resultLine(final String written) {
        return "==>" + written.replace("\n", "\n" + " ".repeat(Graphitty.RESULT_PROMPT_WIDTH));
    }

    /**
     * Clip the rendered result entries of a single statement so that their
     * total physical line count (every {@code "\n"}-separated part of each
     * entry) is at most {@code maxOutput}.  When anything is dropped, a
     * trailing {@code "   ..."} marker line is appended.  The statement's
     * input line is never part of {@code outputs}, so it is never clipped
     * nor counted toward the limit.
     */
    private static void clipOutputs(final List<String> outputs, final int maxOutput) {
        int total = 0;
        for (final String output : outputs)
            total += output.split("\n").length;
        if (total <= maxOutput) return;
        final var clipped = new ArrayList<String>();
        int budget = maxOutput;
        for (final String output : outputs) {
            if (budget <= 0) break;                     // drop every remaining entry
            final String[] parts = output.split("\n");
            if (parts.length <= budget) {
                clipped.add(output);
                budget -= parts.length;
            } else {
                clipped.add(String.join("\n", Arrays.copyOf(parts, budget)));   // cut the entry mid-line
                budget = 0;
            }
        }
        outputs.clear();
        outputs.addAll(clipped);
        outputs.add("   ...");
    }
}
