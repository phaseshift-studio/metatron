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
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.reflect.SpaceRec;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.INST_CTOR_TID;
import static studio.phaseshift.metatron.Tokens.REC_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PanelWidget extends SpaceRec<PanelWidget> implements Widget<PanelWidget> {

    public static final fURI UI_PANEL_TID = f("/m/mach/ui/widget/panel");

    public static final Type UI_PANEL_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(UI_PANEL_TID)
            .isaPredicate(rec())
            .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(UI_PANEL_TID),
                    lst(T(REC_TID)), (lhs, inst) ->
                            new PanelWidget(inst.arg(0).as().jvm(), UI_PANEL_TID, inst.arg(0).vid())))
            .create();

    private static final Obj K_TITLE = uri("title");
    private static final Obj K_BODY = uri("body");

    // ── constructor ────────────────────────────────────────────────

    public PanelWidget(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        readStyle();
    }

    /**
     * Adopt the style the rec carries, materialising this widget's defaults into
     * the rec when it carries none.  Read through {@link #read()} so an anchored
     * panel adopts what the space holds, not a construction-time snapshot.
     */
    private void readStyle() {
        final Obj s = this.get(this.read(), STYLE_KEY);
        if (Style.isStyle(s)) {
            this.style(Style.from(s));
            return;
        }
        final Style<PanelWidget> fresh = Style.empty();
        fresh.stylable = this;
        fresh.border(Border.continuous);
        this.put(STYLE_KEY, fresh);
    }

    // ── convenience constructors ───────────────────────────────────

    public PanelWidget() {
        this(new LinkedHashMap<>(), UI_PANEL_TID, null);
    }

    public PanelWidget(final String body) {
        this(null, body);
    }

    public PanelWidget(final String title, final String body) {
        this(new LinkedHashMap<>(), UI_PANEL_TID, null);
        if (null != title) this.put(K_TITLE, str(title));
        if (null != body) this.put(K_BODY, str(body));
    }

    // ── composition ────────────────────────────────────────────────

    public PanelWidget bottom(final Widget<?> dims) {
        return new PanelWidget(null, this + "\n" + dims.toString());
    }

    public PanelWidget right(final Widget<?> dims) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.max(this.height(), dims.height()); i++) {
            if (i < this.height()) sb.append(this.rowString(i));
            else sb.append(" ".repeat(this.width()));
            if (i < dims.height()) sb.append(" ").append(dims.rowString(i));
            sb.append("\n");
        }
        sb.deleteCharAt(sb.length() - 1);
        return new PanelWidget(sb.toString()).style().border(this.getStyle().border()).applyStyle();
    }

    public PanelWidget setTitle(final String title) {
        this.put(K_TITLE, str(null != title ? title : ""));
        return this;
    }

    /**
     * Body-line wrap width — the style's {@code width} in the rec, which is also
     * what the surface uses to place and clip the panel.  0 = no wrapping.
     */
    public PanelWidget maxWidth(final int w) {
        this.style().width(w).applyStyle();
        return this;
    }

    public int maxWidth() {
        return this.getStyle().width();
    }

    // ── Widget contract ────────────────────────────────────────────

    /**
     * A layout hint for a parent widget; nothing reads it back (see Widget#cursor).
     */
    @Override
    public PanelWidget cursor(final Cursor cursor) {
        return this;
    }

    @Override
    public Style<PanelWidget> getStyle() {
        return Style.from(this.get(this.read(), STYLE_KEY));
    }

    @Override
    public PanelWidget style(final Style<PanelWidget> style) {
        final Style<PanelWidget> st = null == style ? Style.empty() : style;
        st.stylable = this;
        if (st.border() == Border.none) st.border(Border.continuous);
        this.put(STYLE_KEY, st);
        return this;
    }

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

    // ── rendering ──────────────────────────────────────────────────

    @Override
    public String format() {
        // ONE anchored read for the whole pass (fields and style both come from it)
        final Map<Obj, Obj> fields = this.read();
        final Style<PanelWidget> style = Style.from(this.get(fields, STYLE_KEY));
        final String title = this.getStr(fields, K_TITLE);
        // a body is a str or a list of lines — both are lines here
        final List<String> rawLines = this.getLines(fields, K_BODY).stream()
                .map(l -> l.endsWith("\r") ? l.substring(0, l.length() - 1) : l)
                .toList();

        // Word-wrap when the style carries a width (see maxWidth())
        final int maxWidth = style.width();
        final List<String> lines;
        if (maxWidth > 0) {
            lines = new ArrayList<>();
            for (final String raw : rawLines) {
                lines.addAll(wrapLine(raw, maxWidth));
            }
        } else {
            lines = rawLines;
        }

        final int maxLen = Stream.concat(Stream.of(title), lines.stream())
                .map(Highlighter::visualLength)
                .max(Integer::compareTo).orElse(0);

        // A leading color code in the body (e.g. "{{y}}") is the body's own
        // text color, distinct from the style foreground that colors the
        // border.  It is re-applied on every line because each body line ends
        // with a {{X}} reset that would otherwise drop it after the first line.
        final String bodyLead = lines.isEmpty() ? "" : leadingCodes(lines.get(0));

        final StringBuilder sb = new StringBuilder();
        // Style background/foreground are re-applied on every line because each
        // body line ends with a {{X}} reset (background+foreground, TableWidget
        // order, so a foreground code wins when both are fg codes).
        final String color = style.background() + style.foreground();
        sb.append(style.prefix()).append(color);
        final String top = "%s%s".formatted(
                        title,
                        style.border().topSide().repeat(
                                title.isEmpty() ? maxLen : maxLen - Highlighter.visualLength(title)))
                .stripTrailing();
        if (!top.isEmpty())
            sb.append(style.border().topLeftCorner()).append(top)
                    .append(style.border().topRightCorner()).append("{{X}}\n");
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            // Line 0 already carries its own leading codes; re-apply them to
            // the subsequent lines so a multi-line body keeps one text color.
            final String lead = i == 0 ? "" : bodyLead;
            sb.append(color)
                    .append(style.border().leftSide())
                    .append(lead).append(line)
                    .append(" ".repeat(maxLen - Highlighter.visualLength(line)))
                    .append(color)  // re-assert so the right border matches the style color, not the body's
                    .append(style.border().rightSide()).append("{{X}}\n");
        }
        final String bottom = style.border().bottomSide().repeat(maxLen).stripTrailing();
        if (!bottom.isEmpty())
            sb.append(color)
                    .append(style.border().bottomLeftCorner()).append(bottom)
                    .append(style.border().bottomRightCorner()).append("{{X}}\n");
        // The final {{X}} above is load-bearing: a colored panel must not leave
        // the terminal with background/foreground active, or the FloatingSurface's
        // erase/buffer-zone spaces in the next render pass get painted with the
        // leaked color — showing up as a stray colored blank line above widgets.
        return sb.toString();
    }

    private static List<String> wrapLine(final String line, final int maxW) {
        return Utilities.wordWrap(line, maxW);
    }

    /**
     * Regex to capture leading Graphitty codes (e.g. "{{y}}{{b}}") from a line.
     */
    private static final Pattern LEADING_CODES = Pattern.compile("^(\\{\\{[^}]*}})*");

    private static String leadingCodes(final String line) {
        final Matcher m = LEADING_CODES.matcher(line);
        return m.find() ? m.group() : "";
    }

    @Override
    public String toString() {
        return this.format();
    }
}
