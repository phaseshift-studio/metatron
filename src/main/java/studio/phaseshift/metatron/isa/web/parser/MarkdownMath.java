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

package studio.phaseshift.metatron.isa.web.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * LaTeX math for the markdown side of {@link HTMLMarkdownSerializer}: the spans a
 * doc <em>authors</em> are lifted out of the markdown before flexmark parses it,
 * and put back afterwards as the delimiters the site's math engine
 * <em>renders</em>.
 *
 * <p>Two problems make the lift necessary, and both are why a renderer alone can
 * never fix an authored {@code $…$}.
 *
 * <ol>
 * <li><strong>Markdown has no math.</strong> flexmark (and CommonMark) treat a
 * dollar sign as ordinary text, so {@code $V = U \cup O$} reaches the page as
 * literal source. The site's global MathJax — loaded by
 * {@code docs/website/includes/header.html} — owns {@code \(…\)} and
 * {@code \[…\]}/{@code $$…$$} by default and deliberately does <em>not</em>
 * define {@code $…$} (dollar signs are too common in prose). So the authored
 * spelling has to be translated into the rendered one.</li>
 *
 * <li><strong>Markdown actively corrupts LaTeX.</strong> CommonMark's
 * backslash-escape rule applies to punctuation inside a math span before any
 * renderer sees it: {@code \{ } → {@code {}, {@code \_ } → {@code _},
 * {@code \, } → {@code ,} (a thin space silently becomes a comma!), and
 * {@code \\} → {@code \} — the row separator of {@code \begin{array}}/
 * {@code \begin{cases}}. {@code \(x\)}, MathJax's own inline delimiter, is
 * flattened to {@code (x)}. Protecting the span <em>before</em> the parse is the
 * only way to keep the LaTeX intact; {@code docs/articles/geometry-is-the-program.md}
 * shipped this corruption ({@code $\{0\}$} reached the page as {@code ${0}$}).</li>
 * </ol>
 *
 * <p><strong>Authored → rendered.</strong> {@code $…$} and {@code \(…\)} become
 * inline {@code \(…\)}; {@code $$…$$} and {@code \[…\]} become display
 * {@code \[…\]}. Authored math therefore stays in the GitHub-readable dollar
 * spelling while the site typesets it with the engine it already ships — no
 * client-side configuration change and no second renderer.
 *
 * <p><strong>What is not math.</strong> The pass is deliberately conservative,
 * because metatron prose is full of dollar signs that are <em>not</em> math —
 * {@code ${class}} interpolation splices, {@code $(id -u)} shell substitutions,
 * {@code $table} sentinels, {@code Tuple$Pair} class names. A span opens only
 * when it looks like math:
 *
 * <ul>
 * <li>an opening {@code $} not preceded by an odd number of backslashes (so
 * {@code \$} is a literal dollar), not followed by whitespace, by {@code $} or by
 * {@code &#123;} — {@code ${…}} is metatron's interpolation splice and never
 * opens math;</li>
 * <li>a closing {@code $} not preceded by whitespace and not followed by a digit
 * (so {@code … $5 and $6 …} is prose, the pandoc rule);</li>
 * <li>content that is non-empty and not blank;</li>
 * <li>fenced code blocks and inline code spans are copied verbatim, so mtron
 * source — {@code ${*next_event}}, {@code Tuple$Pair}, a {@code sed} regexp —
 * is never touched. A math span must open and close on one line.</li>
 * </ul>
 *
 * <p>The scan is text-level, not a markdown parse: it knows code, fences and
 * escapes, but nothing else. A dollar pair inside a link destination would
 * therefore be taken for math (a visible, harmless-to-diagnose broken link)
 * rather than silently ignored.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class MarkdownMath {

    /**
     * Placeholder fences, a private-use pair flexmark renders as opaque text in
     * every context (inline, emphasis, headings, table cells, link text) — so a
     * protected span travels through the markdown parse untouched and is
     * substituted back once the html exists.
     */
    private static final char PLACEHOLDER_OPEN = '\uE000';
    private static final char PLACEHOLDER_CLOSE = '\uE001';

    private MarkdownMath() {
    }

    /**
     * One authored math span: its TeX source, and whether it was authored as
     * display ({@code $$…$$} / {@code \[…\]}) rather than inline.
     */
    public record Span(String tex, boolean display) {
    }

    /**
     * The markdown safe to hand to the markdown parser, plus the spans lifted out
     * of it — restore them onto the rendered html with {@link #restore}.
     */
    public record Protected(String markdown, List<Span> spans) {
    }

    /**
     * Lift every authored math span out of {@code markdown}, leaving an opaque
     * placeholder in its place. Code (fenced blocks, inline spans) is copied
     * verbatim, so only prose math is lifted.
     *
     * <p>The result is markdown a {@code $}-blind parser cannot damage; the
     * caller parses it and then calls {@link #restore} on the html.
     */
    public static Protected protect(final String markdown) {
        if (null == markdown || markdown.isEmpty()) return new Protected("", List.of());
        // fast path: no candidate delimiter anywhere
        if (!markdown.contains("$") && !markdown.contains("\\(") && !markdown.contains("\\["))
            return new Protected(markdown, List.of());

        final StringBuilder out = new StringBuilder(markdown.length());
        final List<Span> spans = new ArrayList<>();
        final String[] lines = markdown.split("\n", -1);
        Fence fence = null;
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) out.append('\n');
            final String line = lines[i];
            if (null != fence) {
                if (isFenceClose(fence, line)) fence = null;
                out.append(line);
                continue;
            }
            final Fence opened = fenceOpen(line);
            if (null != opened) {
                fence = opened;
                out.append(line);
                continue;
            }
            protectLine(line, out, spans);
        }
        return new Protected(out.toString(), List.copyOf(spans));
    }

    /**
     * Put the authored math back into {@code html} in the spelling the site's math
     * engine renders: inline {@code \(…\)}, display {@code \[…\]}. The TeX is
     * html-escaped ({@code &}, {@code <}, {@code >}) because it is substituted
     * after the markdown parse, which would otherwise have escaped it — MathJax
     * reads the element's text, so the entities are decoded back for it.
     */
    public static String restore(final String html, final List<Span> spans) {
        if (null == html || html.isEmpty() || null == spans || spans.isEmpty()) return html;
        String restored = html;
        for (int i = 0; i < spans.size(); i++) {
            restored = restored.replace(placeholder(i), render(spans.get(i)));
        }
        return restored;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Line scan — prose only: backticks and fenced blocks are copied through
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Lift the math out of one line of prose, copying code spans verbatim. A
     * backtick run that never closes is literal text (CommonMark), so the scan
     * continues through it.
     */
    private static void protectLine(final String line, final StringBuilder out, final List<Span> spans) {
        int i = 0;
        while (i < line.length()) {
            final char c = line.charAt(i);
            if (c == '`') {
                final int run = runLength(line, i, '`');
                final int close = codeSpanClose(line, i + run, run);
                final int end = close < 0 ? i + run : close + run;
                out.append(line, i, end);
                i = end;
                continue;
            }
            if ((c == '$' || c == '\\') && !escaped(line, i)) {
                final int consumed = match(line, i, spans);
                if (consumed > 0) {
                    out.append(placeholder(spans.size() - 1));
                    i += consumed;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
    }

    /**
     * Length of the math span opening at {@code open}, or {@code 0} when nothing
     * there is math. On a match the span is appended to {@code spans}.
     */
    private static int match(final String line, final int open, final List<Span> spans) {
        return line.charAt(open) == '$'
                ? matchDollar(line, open, spans)
                : matchLatex(line, open, spans);
    }

    /**
     * {@code $…$} (inline) and {@code $$…$$} (display) — the pandoc rules, plus
     * one metatron rule: {@code ${…}} is an interpolation splice, never math.
     */
    private static int matchDollar(final String line, final int open, final List<Span> spans) {
        final boolean display = open + 1 < line.length() && line.charAt(open + 1) == '$';
        final int from = open + (display ? 2 : 1);
        if (!display) {
            if (from >= line.length()) return 0;
            final char first = line.charAt(from);
            if (Character.isWhitespace(first) || first == '{' || first == '$') return 0;
        }
        for (int j = from; j < line.length(); j++) {
            if (line.charAt(j) != '$' || escaped(line, j)) continue;
            if (display) {
                if (j + 1 >= line.length() || line.charAt(j + 1) != '$') continue;
            } else if (Character.isWhitespace(line.charAt(j - 1))) {
                return 0; // a closing '$' may not follow a space: "$5 and $6" is prose
            } else if (j + 1 < line.length() && Character.isDigit(line.charAt(j + 1))) {
                return 0; // …and may not precede a digit
            }
            final String tex = line.substring(from, j);
            if (tex.isBlank()) return 0;
            spans.add(new Span(tex, display));
            return (display ? j + 2 : j + 1) - open;
        }
        return 0;
    }

    /**
     * {@code \(…\)} (inline) and {@code \[…\]} (display) — MathJax's own spelling,
     * which markdown would otherwise flatten to {@code (…)}.
     */
    private static int matchLatex(final String line, final int open, final List<Span> spans) {
        if (open + 1 >= line.length()) return 0;
        final char kind = line.charAt(open + 1);
        if (kind != '(' && kind != '[') return 0;
        final char close = kind == '(' ? ')' : ']';
        for (int j = open + 2; j + 1 < line.length(); j++) {
            if (line.charAt(j) != '\\' || escaped(line, j)) continue;
            if (line.charAt(j + 1) != close) continue;
            final String tex = line.substring(open + 2, j);
            if (tex.isBlank()) return 0;
            spans.add(new Span(tex, kind == '['));
            return (j + 2) - open;
        }
        return 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Code fences and code spans
    // ═══════════════════════════════════════════════════════════════════

    /** An open code fence: its character ({@code `} or {@code ~}) and run length. */
    private record Fence(char ch, int len) {
    }

    /**
     * The fence opened by {@code line}, or null. CommonMark: a run of three or
     * more backticks or tildes, indented at most three spaces.
     */
    private static Fence fenceOpen(final String line) {
        final String trimmed = line.stripLeading();
        if (line.length() - trimmed.length() > 3 || trimmed.isEmpty()) return null;
        final char ch = trimmed.charAt(0);
        if (ch != '`' && ch != '~') return null;
        final int len = runLength(trimmed, 0, ch);
        return len >= 3 ? new Fence(ch, len) : null;
    }

    /** True when {@code line} closes {@code fence}: same character, at least as long, nothing else. */
    private static boolean isFenceClose(final Fence fence, final String line) {
        final String trimmed = line.stripLeading();
        if (line.length() - trimmed.length() > 3) return false;
        final int len = runLength(trimmed, 0, fence.ch());
        return len >= fence.len() && trimmed.substring(len).isBlank();
    }

    /**
     * Index of the backtick run of exactly {@code run} backticks that closes the
     * code span starting at {@code from}, or {@code -1} when the span never closes
     * (CommonMark: unmatched backticks are literal text).
     */
    private static int codeSpanClose(final String line, final int from, final int run) {
        for (int j = from; j < line.length(); ) {
            if (line.charAt(j) != '`') {
                j++;
                continue;
            }
            final int len = runLength(line, j, '`');
            if (len == run) return j;
            j += len;
        }
        return -1;
    }

    /** Length of the run of {@code ch} starting at {@code from}. */
    private static int runLength(final String s, final int from, final char ch) {
        int i = from;
        while (i < s.length() && s.charAt(i) == ch) i++;
        return i - from;
    }

    /** True when the character at {@code i} is backslash-escaped. */
    private static boolean escaped(final String s, final int i) {
        int backslashes = 0;
        for (int j = i - 1; j >= 0 && s.charAt(j) == '\\'; j--) backslashes++;
        return (backslashes & 1) == 1;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Rendering
    // ═══════════════════════════════════════════════════════════════════

    private static String placeholder(final int index) {
        return PLACEHOLDER_OPEN + Integer.toString(index) + PLACEHOLDER_CLOSE;
    }

    /** The span as the site's MathJax spelling: {@code \(…\)} inline, {@code \[…\]} display. */
    private static String render(final Span span) {
        final String tex = escapeHTML(span.tex());
        return span.display() ? "\\[" + tex + "\\]" : "\\(" + tex + "\\)";
    }

    /**
     * Escape the entity characters of TeX substituted into the html after the
     * markdown parse. MathJax reads the element's text, so it sees them decoded.
     */
    private static String escapeHTML(final String tex) {
        return tex.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
