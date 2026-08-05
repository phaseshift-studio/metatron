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

package studio.phaseshift.metatron.isa.mach.type.ui;

import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.FloatingSurface;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_STYLE_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Stylable<T extends Stylable<T>> {

    default Style<T> style() {
        return new Style<>((T) this);
    }

    T style(final Style<T> style);

    Style<T> getStyle();

    /**
     * Number of structural chrome lines at the top of the widget that
     * should be preserved when a {@link Style#height height cap} is
     * applied.  Computed from the style's border — if a border is
     * configured, the top border row counts as chrome.  Widgets with
     * additional structural elements (column headers, status bars)
     * override this to add their own.
     */
    default int chromeLines() {
        final Style<T> s = this.getStyle();
        if (s != null) {
            final Border b = s.border();
            if (b != null && b != Border.none) return 1;
        }
        return 0;
    }

    class Style<T extends Stylable<T>> extends MRec {
        public T stylable;
        public Border border = null;
        public FloatingSurface.Anchor anchor = null;

        protected Style(final T stylable) {
            super(new LinkedHashMap<>(), UI_STYLE_TID, null);
            this.stylable = stylable;
        }

        public static <T extends Stylable<T>> Style<T> empty() {
            return new Style<>(null);
        }

        public Border border() {
            return null != this.border ? this.border : (this.at("border").isUri() ? Border.parse(this.at("border").uriValue().toString()) : Border.none);
        }

        public Style<T> border(final Border border) {
            this.jvm().put(uri("border"), uri(border.toString()));
            this.border = border;
            return this;
        }

        public int lowRowRange() {
            return this.at("lowRowRange").isInt() ? this.at("lowRowRange").asInt().intValue().intValue() : 0;
        }

        public int highRowRange() {
            return this.at("highRowRange").isInt() ? this.at("highRowRange").asInt().intValue().intValue() : Integer.MAX_VALUE;
        }

        public Style<T> rowRange(final int low, final int high) {
            this.jvm().put(uri("lowRowRange"), jnt(low));
            this.jvm().put(uri("highRowRange"), jnt(high));
            return this;
        }

        public int lowColRange() {
            return this.at("lowColRange").isInt() ? this.at("lowColRange").asInt().intValue().intValue() : 0;
        }

        public int highColRange() {
            return this.at("highColRange").isInt() ? this.at("highColRange").asInt().intValue().intValue() : Integer.MAX_VALUE;
        }

        public Style<T> colRange(final int low, final int high) {
            this.jvm().put(uri("lowColRange"), jnt(low));
            this.jvm().put(uri("highColRange"), jnt(high));
            return this;
        }

        public String pointer() {
            return this.at("pointer").orElse(str("")).strValue();
        }

        public Style<T> pointer(final String pointer) {
            this.jvm().put(uri("pointer"), str(pointer));
            return this;
        }

        public String background() {
            return this.at("background").orElse(str("")).strValue();
        }

        public Style<T> background(final String bg) {
            this.jvm().put(uri("background"), str(bg));
            return this;
        }

        public String foreground() {
            return this.at("foreground").orElse(str("")).strValue();
        }

        public Style<T> foreground(final String fg) {
            this.jvm().put(uri("foreground"), str(fg));
            return this;
        }

        public <R extends Widget<R>> R attachment() {
            return this.at("attachment").isNoObj() ? null : (R) this.at("attachment");
        }

        public boolean overlapAttachment() {
            return this.at("overlapAttachment").isBool() ? this.at("overlapAttachment").asBool().jvm() : false;
        }

        public <R extends Widget<R>> R styleParent() {
            return this.at("parent").isNoObj() ? null : (R) this.at("parent");
        }

        public Style<T> styleParent(final Widget parent) {
            this.jvm().put(uri("parent"), (Obj) parent);
            return this;
        }

        public Style<T> attachment(final Widget attachment, final boolean overlap) {
            this.jvm().put(uri("attachment"), (Obj) attachment);
            this.jvm().put(uri("overlapAttachment"), studio.phaseshift.metatron.isa.m.type.impl.MBool.bool(overlap));
            return this;
        }

        public String headerDivider() {
            return this.at("headerDivider").orElse(str("")).strValue();
        }

        public Style<T> headerDivider(final String divider) {
            this.jvm().put(uri("headerDivider"), str(divider));
            return this;
        }

        public String divider() {
            return this.at("divider").orElse(str("")).strValue();
        }

        public Style<T> divider(final String divider) {
            this.jvm().put(uri("divider"), str(divider));
            return this;
        }

        public String textBody() {
            return this.at("body").orElse(str("")).strValue();
        }

        public Style<T> textBody(final String body) {
            this.jvm().put(uri("body"), str(body));
            return this;
        }

        public int leftMargin() {
            return this.at("leftMargin").isInt() ? this.at("leftMargin").asInt().intValue().intValue() : 0;
        }

        public int rightMargin() {
            return this.at("rightMargin").isInt() ? this.at("rightMargin").asInt().intValue().intValue() : 0;
        }

        public int topMargin() {
            return this.at("topMargin").isInt() ? this.at("topMargin").asInt().intValue().intValue() : 0;
        }

        public int bottomMargin() {
            return this.at("bottomMargin").isInt() ? this.at("bottomMargin").asInt().intValue().intValue() : 0;
        }

        public Style<T> margin(final int left, final int right, final int top, final int bottom) {
            this.jvm().put(uri("leftMargin"), jnt(left));
            this.jvm().put(uri("rightMargin"), jnt(right));
            this.jvm().put(uri("topMargin"), jnt(top));
            this.jvm().put(uri("bottomMargin"), jnt(bottom));
            return this;
        }

        public Style<T> margin(final int left, final int right) {
            this.jvm().put(uri("leftMargin"), jnt(left));
            this.jvm().put(uri("rightMargin"), jnt(right));
            return this;
        }

        public String prefix() {
            return this.at("prefix").orElse(str("")).strValue();
        }

        public Style<T> freePrefix(final String prefix) {
            this.jvm().put(uri("prefix"), str(prefix));
            return this;
        }

        /**
         * Read style fields from a mtron style Rec.
         */
        public static <T extends Stylable<T>> Style<T> from(final Rec styleRec) {
            if (styleRec == null) return new Style<>(null);
            if (styleRec instanceof Style) return (Style<T>) styleRec;
            final Style<T> s = new Style<>(null);
            s.jvm().putAll(styleRec.jvm());
            return s;
        }

        /**
         * Read style fields from a mtron style Rec.
         */
        public static <T extends Stylable<T>> T from(final Rec styleRec, final T stylable) {
            final Style<T> s = new Style<>(stylable);
            s.jvm().putAll(styleRec.jvm());
            return s.applyStyle();
        }

        public Style<T> floatAt(final FloatingSurface.Anchor anchor, final int width,
                                final int top, final int left) {
            this.jvm().put(uri("anchor"), uri(anchor.name().toLowerCase()));
            this.jvm().put(uri("width"), jnt(width));
            this.jvm().put(uri("top"), jnt(top));
            this.jvm().put(uri("left"), jnt(left));
            this.anchor = anchor;
            return this;
        }

        public Style<T> unfloat() {
            this.jvm().remove(uri("anchor"));
            this.jvm().remove(uri("width"));
            this.jvm().remove(uri("top"));
            this.jvm().remove(uri("left"));
            this.anchor = null;
            return this;
        }

        public boolean hasFloat() {
            return this.anchor != null || this.at("anchor").isUri();
        }

        public FloatingSurface.Anchor anchor() {
            if (this.anchor != null) return this.anchor;
            if (this.at("anchor").isUri())
                return FloatingSurface.Anchor.parse(this.at("anchor").uriValue().toString());
            return null;
        }

        /**
         * Display width override.  0 = use the widget's natural width.
         */
        public int width() {
            return this.at("width").orElse(jnt(0)).asInt().intValue().intValue();
        }

        public Style<T> width(final int w) {
            this.jvm().put(uri("width"), jnt(w));
            return this;
        }

        /**
         * Display height override.  0 = use the widget's natural height
         * (grow unbounded).  When set and content exceeds this many rows,
         * the top lines are discarded so the newest content stays visible
         * at the bottom.
         */
        public int height() {
            return this.at("height").orElse(jnt(0)).asInt().intValue().intValue();
        }

        public Style<T> height(final int h) {
            this.jvm().put(uri("height"), jnt(h));
            return this;
        }

        /**
         * Row offset from the anchor edge (CSS {@code top}).
         */
        public int top() {
            return this.at("top").orElse(jnt(0)).asInt().intValue().intValue();
        }

        public Style<T> top(final int t) {
            this.jvm().put(uri("top"), jnt(t));
            return this;
        }

        /**
         * Column offset from the anchor edge (CSS {@code left}).
         */
        public int left() {
            return this.at("left").orElse(jnt(0)).asInt().intValue().intValue();
        }

        public Style<T> left(final int l) {
            this.jvm().put(uri("left"), jnt(l));
            return this;
        }

        /**
         * Word-wrap a list of text lines to fit within {@code maxWidth}
         * visual characters, breaking at word boundaries.  Lines that fit
         * are passed through unchanged; overlong lines are split.
         */
        public static List<String> wrapLines(final List<String> lines, final int maxWidth) {
            if (maxWidth <= 0 || lines == null) return List.of();
            final List<String> out = new ArrayList<>();
            for (final String line : lines) {
                if (line.length() <= maxWidth) {
                    out.add(line);
                } else {
                    wrapLine(line, maxWidth, out);
                }
            }
            return out;
        }

        private static void wrapLine(final String text, final int maxWidth, final List<String> out) {
            String remaining = text;
            while (remaining.length() > maxWidth) {
                int breakAt = maxWidth;
                for (int i = Math.min(maxWidth, remaining.length() - 1); i > 0; i--) {
                    if (remaining.charAt(i) == ' ') {
                        breakAt = i;
                        break;
                    }
                }
                out.add(remaining.substring(0, breakAt).stripTrailing());
                remaining = remaining.substring(breakAt).stripLeading();
            }
            if (!remaining.isEmpty()) out.add(remaining);
        }

        public T applyStyle() {
            this.stylable.style(this);
            return this.stylable;
        }
    }
}
