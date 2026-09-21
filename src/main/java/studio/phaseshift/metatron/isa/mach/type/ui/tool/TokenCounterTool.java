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

package studio.phaseshift.metatron.isa.mach.type.ui.tool;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.llmInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.StackBarWidget;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A single-line token usage bar — the tailor-made caller of
 * {@link StackBarWidget}: given the usage, it draws the distribution and
 * nothing else, one line, render-only.
 *
 * <pre>
 *   [sys  usr  ai  tools  4%                                  ] 100k
 * </pre>
 * <p>
 * The bar IS the model's context window.  The data it reads:
 *
 * <pre>
 *   [in => 4413, out => 66, max => 100000,
 *    est => [system => 622, ai => 170, user => 409, tool => 334]]
 * </pre>  Each {@code est} category — the estimated composition of what went
 * into the window — is a colored section sized as its share of {@code max} (the
 * window's size), smallest first, largest last, an empty category skipped.
 * The percent is {@code in} over {@code max}, painted where the used part
 * ends; the blank tail is the window's unused part; the trailing size
 * ({@code 100k}) is the window itself.  The colors and labels
 * ({@code sys}, {@code ai}, {@code usr}, {@code tool}) are the tool's own —
 * the general bar's configurability does not reach in here.
 */
public class TokenCounterTool extends StackBarWidget {

    /**
     * The bar in columns when no style width says otherwise.
     */
    public static final int BAR_WIDTH = 40;

    private static final Obj K_IN = uri(IN);
    private static final Obj K_EST = uri(EST);
    private static final Obj K_MAX = uri(MAX);

    /**
     * The percent painted, the style of every other widget.
     */
    private static final String PCT_STYLE = "{{W}}";

    /**
     * The bar's unused tail.
     */
    private static final String UNUSED_BACKGROUND = "{{[k]}}";

    /**
     * One est category: its label, its color, and the estimated token count it
     * carries.
     */
    private record Section(String label, String color, long value) {
    }

    // ── constructors ─────────────────────────────────────────────────

    public TokenCounterTool(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * Flat data — no {@code token} wrapper needed; the tid is this tool's own.
     */
    public TokenCounterTool(final Map<Obj, Obj> data) {
        this(new LinkedHashMap<>(data), llmInstSet.LLM_TOKEN_COUNTER_TOOL_TID, null);
    }

    // ── the widget contract (the style's home is inherited) ──────────
    // getStyle() / style(…) / cursor(…) / close() / render…() are the
    // StackBarWidget's; this tool paints its own line in format().

    // ── data ─────────────────────────────────────────────────────────

    /**
     * The window to measure against: the rec's {@code max} — the context
     * window's size — through {@code this.get}, so a space round-trip's renamed
     * key still answers.
     */
    private long maxOf(final Map<Obj, Obj> data) {
        return TokenCounterTool.valueOf(this.get(data, K_MAX));
    }

    /**
     * A positive int under {@code key}, or 0 when the key is absent or not an
     * int.
     */
    private long value(final Map<Obj, Obj> fields, final Obj key) {
        if (null == fields || null == key) return 0L;
        return TokenCounterTool.valueOf(this.get(fields, key));
    }

    /**
     * The count an int-typed value carries, or 0 when it is not one.
     */
    private static long valueOf(final Obj o) {
        return (null != o && o.isInt()) ? (long) o.intValue().intValue() : 0L;
    }

    /**
     * The est categories of the rec, in rec order: positive only — an empty
     * category is skipped — and named the way the bar reads.
     */
    private List<Section> segments(final Map<Obj, Obj> data) {
        final Obj est = this.get(data, K_EST);
        if (null == est || !est.isRec()) return List.of();
        final List<Section> out = new ArrayList<>();
        for (final Map.Entry<Obj, Obj> entry : est.asRec().jvm().entrySet()) {
            if (null == entry.getKey() || !entry.getKey().isUri()) continue;
            final String kind = entry.getKey().uriValue().name();
            final long v = TokenCounterTool.valueOf(entry.getValue());
            if (v <= 0) continue;   // an empty category is skipped
            out.add(new Section(labelFor(kind), colorFor(kind), v));
        }
        return out;
    }

    // ── rendering ────────────────────────────────────────────────────

    @Override
    public String format() {
        final Map<Obj, Obj> data = this.read();
        final long max = this.maxOf(data);
        final long in = this.value(data, K_IN);

        // the canvas must fit the widget's whole box: style.width is the box
        // (the float surface re-applies it on every update, and a line longer
        // than its box is stripped of color to fit) — so the canvas is the box
        // less the chrome: the brackets, and the trailing " size" label
        final int styled = this.getStyle().width();
        final boolean windowed0 = this.maxOf(data) > 0;
        final String size0 = windowed0 ? " " + sizeLabel(this.maxOf(data)) : "";
        final int canvas = styled > 0
                ? Math.max(1, styled - 2 - size0.length())
                : BAR_WIDTH;

        // the est categories, smallest first, largest last (the bar draws the
        // composition, not the ledger's order)
        final List<Section> segments = this.segments(data).stream()
                .sorted(Comparator.comparingLong(Section::value))
                .toList();
        final long estSum = segments.stream().mapToLong(Section::value).sum();

        // the window to measure against: max when the rec says so — the
        // sections are sized against it, the pct is in over it, the tail and
        // the size label come from it
        final boolean windowed = max > 0;
        final long denominator = windowed ? max : estSum;
        final String pct = windowed
                ? (int) Math.clamp(Math.round(in * 100.0 / max), 0, 100) + "%"
                : null;
        final int pctLen = null == pct ? 0 : pct.length();

        // each section is its share of the whole: value/denominator * canvas
        // columns, never below the room its label needs
        final List<Section> painted = segments.stream()
                .map(s -> new Section(s.label(), s.color(),
                        Math.max(s.label().length() + 1, (int) Math.round(s.value() * (double) canvas / denominator))))
                .toList();
        final int used = painted.stream().mapToInt(s -> (int) s.value()).sum();
        final int budget = canvas - pctLen;
        final List<Section> fitted = used > budget   // near-full window: shrink the sections, never the pct
                ? painted.stream().map(s -> new Section(s.label(), s.color(), Math.max(1L, s.value() * budget / used))).toList()
                : painted;
        final int usedW = fitted.stream().mapToInt(s -> (int) Math.max(1L, s.value())).sum();

        final StringBuilder sb = new StringBuilder();
        sb.append("│");
        for (final Section s : fitted) {
            final int sw = (int) Math.max(1L, s.value());
            sb.append("{{X}}").append(s.color()).append(s.label())
                    .repeat(" ", Math.max(0, sw - s.label().length()));
        }
        if (null != pct)
            sb.append("{{X}}").append(PCT_STYLE).append(pct);
        final int tail = windowed ? Math.max(0, canvas - usedW - pctLen) : 0;
        if (tail > 0)
            sb.append("{{X}}").append(UNUSED_BACKGROUND).repeat(" ", tail);
        sb.append("{{X}}│");
        if (windowed)
            sb.append(" ").append(sizeLabel(max));
        return sb.toString();
    }

    /**
     * The section's label, the way the bar reads: system is sys, tool_request
     * is tool, and ai and usr stand as they are.
     */
    private static String labelFor(final String kind) {
        return switch (kind) {
            case SYSTEM -> "sys";
            case AI -> "ai";
            case USER -> "usr";
            case TOOL -> "tool";
            default -> kind;
        };
    }

    /**
     * The section's background: one color per category — the bar's legend is
     * this tool's own.
     */
    private static String colorFor(final String kind) {
        return switch (kind) {
            case SYSTEM -> "{{[c]}}";    // cyan
            case AI -> "{{[g]}}";        // green
            case USER -> "{{[m]}}";      // magenta
            case TOOL -> "{{[y]}}";   // yellow
            default -> "{{[b]}}";        // blue: an est kind the bar has not named
        };
    }

    /**
     * The window's size label: 262144 reads as {@code 256k}, 8388608 as {@code 8m};
     * a size with no clean power is its own number.
     */
    private static String sizeLabel(final long size) {
        // the models speak both powers: 262144 reads as 256k, 100000 as 100k
        if (size >= 1024 && size % 1024 == 0) {
            final long k = size / 1024;
            return (k >= 1024 && k % 1024 == 0) ? (k / 1024) + "m" : k + "k";
        }
        if (size >= 1000 && size % 1000 == 0) {
            final long k = size / 1000;
            return (k >= 1000 && k % 1000 == 0) ? (k / 1000) + "m" : k + "k";
        }
        return Long.toString(size);
    }
}
