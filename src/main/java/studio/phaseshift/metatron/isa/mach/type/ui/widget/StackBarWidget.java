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

package studio.phaseshift.metatron.isa.mach.type.ui.widget;

import org.jline.terminal.Cursor;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.reflect.SpaceRec;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.ui.uiInstSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A single-line stacked bar — a general purpose widget, not a tool for one use:
 * the token usage readout is one caller of it ({@code TokenCounterTool} stays
 * that tailor-made caller).  Everything a render needs is in the rec; the class
 * carries no Java state, so one rec is the one source of truth a space round-trip
 * must survive.
 *
 * <pre>
 *   head [ user    system      tools        ai     5%                          ] 262144
 * </pre>
 *
 * <h2>Data (the rec)</h2>
 *
 * <ul>
 * <li>{@code data => [key => int]} — the sections, each labelled by its key,
 * drawn smallest first, largest last, non-positive values dropped.</li>
 * <li>{@code context => int} — with one, the bar is the window and the percent
 * ({@code total} over {@code context}, default the sum of the data) is painted at
 * the used edge, the remaining columns being the unused section.  Without one the
 * data IS the bar: a composition, no percent, and no unused section either.</li>
 * <li>{@code total => int} — the numerator of that percent (default the sum of
 * the data).</li>
 * <li>{@code pre => "…"} / {@code post => …} — the content painted before the
 * opening and after the closing bracket; a value resolves the same as anything
 * else does,
 * </ul>
 *
 * <h2>Style (the one style rec is the bar's presentation)</h2>
 *
 * <ul>
 * <li>{@code width} — the bar's width (the reserved {@code Style} width slot, so
 * a bar with a float anchor reuses the same field as every other floating
 * widget).  It is a floor: a section cannot shrink below the room its label
 * needs, so a tight bar grows rather than overlapping.</li>
 * <li>{@code section => [name => style::[ … ], …]} — the per-section
 * styles: one {@code style} rec per section, matched by the rec key (the
 * section's name, which is the label painted).  The entry under the reserved
 * key {@code unused} styles the unused section (the empty tail).  Each entry's
 * {@code foreground}/
 * {@code background} are the fragments painted around the label (background
 * first, then foreground, like every other widget), and an entry may declare a
 * {@code width} of its own.
 *
 * <pre>{@code
 *   stack_bar_widget::[pre => "head",
 *                      post => usr/dr/context_window,
 *                      data => [ai => 345, user => 3234, sys => 2434],
 *                      style => [
 *                        width   => 80,
 *                        section => [
 *                                ai     => style::[foreground => "{{g}}", width => 123],
 *                                user   => style::[background => "{{[m]}}"],
 *                                unused => style::[background => "{{[k]}}"]]
 * }</pre>
 * <p>
 * A section that declares a width takes it outright; the rest split what is
 * left, each by its share of the data.  A section's label is its style's
 * {@code body} when it has one (any computed text mtron resolved), else its
 * data key; unstyled sections paint plain, and the window's unused tail keeps
 * its dark background unless the reserved {@code unused} key styles it.
 * </ul>
 *
 * <p>Only the data is specified in the type — everything else rides on the
 * rec's open world.
 */
public class StackBarWidget extends SpaceRec<StackBarWidget> implements Widget<StackBarWidget> {

    /**
     * The bar's width in columns when the style carries none.
     */
    public static final int DEFAULT_WIDTH = 40;

    // the bar's non-section painting: the percent, and the unused section's fallback background
    private static final String PCT_STYLE = "{{W}}";
    private static final String UNUSED_BACKGROUND = "{{[k]}}";

    // the style-rec slots
    private static final String SECTIONS = "section";
    private static final String BODY = "body";
    private static final String FOREGROUND = "foreground";
    private static final String BACKGROUND = "background";
    private static final String WIDTH_SLOT = "width";

    private static final Obj K_DATA = uri(DATA);
    private static final Obj K_CONTEXT = uri(CONTEXT);
    private static final Obj K_TOTAL = uri(TOTAL);
    private static final Obj K_PRE = uri(PRE);
    private static final Obj K_POST = uri(POST);

    /**
     * One section of the data: its key, the label the key carries, and the count.
     */
    private record Section(Obj key, String name, long value) {
    }

    // ── constructors ─────────────────────────────────────────────────

    public StackBarWidget(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        // the map must be mutable: the write path (style) installs into it
        super(new LinkedHashMap<>(jvm), tid, vid);
        this.readStyle();
    }

    /**
     * Flat data — the widget's own tid, ready to render.
     */
    public StackBarWidget(final Map<Obj, Obj> data) {
        this(data, uiInstSet.UI_STACK_BAR_TID, null);
    }

    // ── style (the one style rec is the bar's presentation) ──────────

    @Override
    public Style<StackBarWidget> getStyle() {
        return Style.from(this.get(this.read(), STYLE_KEY));
    }

    @Override
    public StackBarWidget style(final Style<StackBarWidget> s) {
        final Style<StackBarWidget> st = null == s ? Style.empty() : s;
        st.stylable = this;
        this.put(STYLE_KEY, st);
        return this;
    }

    /**
     * Materialise the style into the rec when it carries none — the same first
     * touch {@code AccordionWidget.readStyle()} performs, so a store-backed
     * widget adopts what the space holds rather than a construction snapshot.
     */
    private void readStyle() {
        final Obj s = this.get(this.read(), STYLE_KEY);
        if (Style.isStyle(s)) {
            this.style(Style.from(s));
            return;
        }
        final Style<StackBarWidget> fresh = Style.empty();
        fresh.stylable = this;
        this.put(STYLE_KEY, fresh);
    }

    // ── the widget contract ──────────────────────────────────────────

    @Override
    public StackBarWidget cursor(final Cursor c) {
        return this;   // a layout hint for a parent widget; nothing reads it back
    }

    /**
     * A display-only widget must NOT take the default {@link Widget#close()}: that
     * unfloats the widget, and {@code .display()} closes what it just ran — which
     * would erase it immediately.
     */
    @Override
    public void close() {
    }

    @Override
    public String renderInPlace() {
        return this.format() + "\n";
    }

    @Override
    public String renderFresh() {
        return this.format() + "\n";
    }

    // ── data ─────────────────────────────────────────────────────────

    /**
     * The int under {@code key}, through the anchored read (a renamed key from a space round-trip still answers).
     */
    private long num(final Map<Obj, Obj> fields, final Obj key) {
        if (null == fields || null == key) return 0L;
        final Obj o = this.get(fields, key);
        return (null != o && o.isInt()) ? (long) o.intValue().intValue() : 0L;
    }

    /**
     * The content under {@code key} as text — a str (a literal) or an int — or
     * null when there is nothing to paint.
     */
    private String text(final Map<Obj, Obj> fields, final Obj key) {
        if (null == fields || null == key) return null;
        final Obj o = this.get(fields, key);
        if (null == o) return null;
        if (o.isStr()) return o.strValue();
        return o.isInt() ? String.valueOf((long) o.intValue().intValue()) : null;
    }

    /**
     * The style rec under a section key, matched by name.  A <>(empty) key is
     * its own key in a parsed rec, so neither a canonical at("") nor the data's
     * key object is guaranteed to answer it; name equality is.
     */
    private static Rec recUnder(final Rec sections, final String name) {
        if (null == sections || null == name) return null;
        for (final Map.Entry<Obj, Obj> entry : sections.jvm().entrySet()) {
            final Obj k = entry.getKey();
            if (null != k && k.isUri() && name.equals(k.uriValue().name()))
                return (entry.getValue() instanceof Rec r) ? r : null;
        }
        return null;
    }

    /**
     * The text a label cell carries: a str (cleaned for painting), an int, or a
     * real — or null when the cell is not yet a value; then the data key name
     * stands as the label.
     */
    private static String labelOf(final Obj o) {
        if (null == o) return null;
        final Obj v = o.isCode() ? o.asCode().apply() : o;
        if (null == v) return null;
        if (v.isStr()) return v.toCleanString();
        if (v.isInt()) return String.valueOf((long) v.intValue().intValue());
        if (v.isReal()) return v.realValue().toString();
        return null;
    }

    // ── rendering ────────────────────────────────────────────────────

    @Override
    public String format() {
        final Map<Obj, Obj> fields = this.read();    // the anchored read, once
        final Obj dataObj = this.get(fields, K_DATA);
        final Rec data = (null != dataObj && dataObj.isRec()) ? dataObj.asRec() : null;

        final long context = this.num(fields, K_CONTEXT);
        final long total = this.num(fields, K_TOTAL);
        final String pre = this.text(fields, K_PRE);
        final String post = this.text(fields, K_POST);

        // the style rec, when there is one: the bar's width and the sections' styles
        final Rec style = (null != fields && fields.get(STYLE_KEY) instanceof Rec r) ? r : null;
        final Rec sections = (null != style && style.at(uri(SECTIONS)) instanceof Rec s) ? s : null;
        final int styleWidth = (null != style && style.at(uri(WIDTH_SLOT)) instanceof Obj w && w.isInt()) ? w.intValue().intValue() : 0;
        final int canvas = styleWidth > 0 ? styleWidth : DEFAULT_WIDTH;

        // the data's sections, smallest first, largest last — and the reserved
        // what's-left section (the empty-URI key, <>), pinned last
        final List<Section> drawn = new ArrayList<>();
        if (null != data)
            data.jvm().forEach((key, value) -> {
                final String name = null != key && key.isUri() ? key.uriValue().name() : (null != key && key.isStr() ? key.strValue() : "");
                if (null == value) return;
                final long v = value.isInt() ? (long) value.intValue().intValue() : 0L;
                if (v > 0) drawn.add(new Section(key, name, v));
            });
        drawn.sort((a, b) -> {
            final boolean al = a.name().isEmpty(), bl = b.name().isEmpty();
            if (al != bl) return al ? 1 : -1;
            return Long.compare(a.value(), b.value());
        });
        final long sum = drawn.stream().mapToLong(Section::value).sum();

        // with a context the bar is the window and the percent is the share of it;
        // without one the data IS the bar (no percent, and no unused section either)
        final boolean windowed = context > 0;
        final long numerator = total > 0 ? total : sum;
        final String pct = windowed
                ? (int) Math.clamp(Math.round(numerator * 100.0 / context), 0, 100) + "%"
                : null;
        final int pctLen = null == pct ? 0 : pct.length();

        // a section's width is its share of the whole: the value over the
        // denominator — the context when the bar is the window, the data's sum
        // when the data IS the bar — never below the room its label needs
        final long denominator = windowed ? context : sum;

        // the window tail, when the bar is the window: styled by the reserved
        // key "unused", dark by default
        final Rec unusedStyle = recUnder(sections, "unused");
        final String tailBackground = (null != unusedStyle && unusedStyle.at(uri(BACKGROUND)) instanceof Obj b && b.isStr()) ? b.strValue() : UNUSED_BACKGROUND;
        final String tailForeground = (null != unusedStyle && unusedStyle.at(uri(FOREGROUND)) instanceof Obj f && f.isStr()) ? f.strValue() : "";
        final int unusedW = (null != unusedStyle && unusedStyle.at(uri(WIDTH_SLOT)) instanceof Obj u && u.isInt()) ? u.intValue().intValue() : 0;

        final StringBuilder sb = new StringBuilder();
        if (null != pre && !pre.isEmpty())
            sb.append(pre).append(" ");
        sb.append("[");
        int used = 0;
        for (final Section s : drawn) {
            // one rec in, one line out: background, foreground, label — the
            // label is the section's body when it has one, else its data key.
            // the what's-left key (<> ) assumes the reserved "unused" style when
            // its own key carries none
            final Rec sty = recUnder(sections, s.name()) != null ? recUnder(sections, s.name()) : (s.name().isEmpty() ? recUnder(sections, "unused") : null);
            String label = s.name();
            int explicit = 0;
            if (null != sty) {
                final String computed = labelOf(sty.at(uri(BODY)));
                if (null != computed) label = computed;
                if (sty.at(uri(WIDTH_SLOT)) instanceof Obj x && x.isInt() && x.intValue().intValue() > 0)
                    explicit = x.intValue().intValue();
            }
            final int min = Math.max(1, label.length() + 1);
            final int w;
            if (explicit > 0)
                w = explicit;
            else if (denominator <= 0)
                w = min;
            else
                w = Math.max(min, (int) Math.round(s.value() * (double) canvas / denominator));
            used += w;
            final String background = (null != sty && sty.at(uri(BACKGROUND)) instanceof Obj bg && bg.isStr()) ? bg.strValue() : "";
            final String foreground = (null != sty && sty.at(uri(FOREGROUND)) instanceof Obj fg && fg.isStr()) ? fg.strValue() : "";
            sb.append("{{X}}").append(background).append(foreground).append(label)
                    .repeat(" ", Math.max(0, w - label.length()));
        }
        if (null != pct)
            sb.append("{{X}}").append(PCT_STYLE).append(pct);
        final int room = Math.max(canvas, used + pctLen + (windowed ? unusedW : 0));
        final int tail = windowed
                ? (unusedW > 0 ? Math.clamp(room - used - pctLen, 0, unusedW) : Math.max(0, room - used - pctLen))
                : 0;
        if (tail > 0)
            sb.append("{{X}}").append(tailBackground).append(tailForeground).repeat(" ", tail);
        sb.append("{{X}}]");
        if (null != post && !post.isEmpty())
            sb.append(" ").append(post);
        return sb.toString();
    }
}

