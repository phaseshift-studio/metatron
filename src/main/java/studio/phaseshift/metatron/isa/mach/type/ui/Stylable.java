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

import studio.phaseshift.metatron.isa.m.type.NoObj;
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

    /**
     * Axis mask: scroll horizontally.
     */
    int SCROLL_X = 1;
    /**
     * Axis mask: scroll vertically.
     */
    int SCROLL_Y = 2;
    /**
     * Axis mask: scroll on both axes (the default when {@code scroll} is unset).
     */
    int SCROLL_XY = SCROLL_X | SCROLL_Y;
    /**
     * Axis mask: scrolling disabled.
     */
    int SCROLL_NONE = 0;

    /**
     * The style of this stylable, read from its own rec ({@code style} key).
     *
     * <p>The returned Style is a <em>view over the rec</em>, not a copy: a
     * setter on it writes into the rec the widget renders from, and
     * {@link Style#applyStyle()} hands it back to the widget, which stores it
     * in the rec (write-through, so a store-backed widget's style survives a
     * re-hydration).  Reading instead of inventing an empty style also means a
     * {@code widget.style().border(x).applyStyle()} chain starts from the
     * settings the widget already had, instead of silently dropping them.
     */
    @SuppressWarnings("unchecked")
    default Style<T> style() {
        final Obj styleObj = this instanceof Rec owner ? (Obj) owner.at(STYLE_KEY) : NoObj.noobj();
        final Style<T> s = Style.from(styleObj);
        s.stylable = (T) this;
        return s;
    }

    /** The rec key a stylable's style lives under. */
    Obj STYLE_KEY = uri("style");

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

        protected Style(final T stylable) {
            super(new LinkedHashMap<>(), UI_STYLE_TID, null);
            this.stylable = stylable;
        }

        public static <T extends Stylable<T>> Style<T> empty() {
            return new Style<>(null);
        }

        /**
         * A Style that IS the given rec — the same map instance, the same vid,
         * no copy.  Writes through it are writes to that rec.
         */
        @SuppressWarnings("unchecked")
        public static <T extends Stylable<T>> Style<T> of(final T stylable, final Rec styleRec) {
            final Style<T> s = new Style<>(stylable);
            if (null != styleRec)
                s.self(styleRec.jvm(),
                        null == styleRec.tid() ? UI_STYLE_TID : styleRec.tid(),
                        styleRec.vid());
            return s;
        }

        /**
         * Read a style out of whatever a rec read hands back: a Style is
         * returned as-is, a style-shaped rec (or an {@code Objs} whose member
         * is one — a rec read can answer a key multi-valued) is wrapped in
         * place, and anything else yields an empty style.  Typing is decided
         * here, once, so no widget has to guess what {@code at(style)} returned.
         */
        @SuppressWarnings("unchecked")
        public static <T extends Stylable<T>> Style<T> from(final Obj styleObj) {
            if (styleObj instanceof Style<?> s)
                return (Style<T>) s;
            final Rec styleRec = asStyleRec(styleObj);
            return null == styleRec ? empty() : of(null, styleRec);
        }

        /** True when a rec read handed back something that IS a style (or holds one). */
        public static boolean isStyle(final Obj styleObj) {
            return null != asStyleRec(styleObj);
        }

        /** The rec a style read landed on, or null when there is none. */
        private static Rec asStyleRec(final Obj styleObj) {
            if (null == styleObj || styleObj.isNoObj()) return null;
            if (styleObj instanceof Rec rec) return rec;
            return styleObj.stream().filter(Obj::isRec).findFirst().map(Obj::asRec).orElse(null);
        }

        public Border border() {
            return this.at("border").isUri() ? Border.parse(this.at("border").uriValue().toString()) : Border.none;
        }

        public Style<T> border(final Border border) {
            this.jvm().put(uri("border"), uri(border.toString()));
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

        public Style<T> highlight(final String language) {
            this.jvm().put(uri("highlight"), str(language));
            return this;
        }

        public String highlight() {
            return this.at("highlight").orElse(str("txt")).strValue();
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
        /**
         * Bind a style read to a stylable and apply it (the rec stays the state).
         *
         * <p>Takes {@link Obj}, deliberately: an overload taking {@link Rec} made
         * a generic {@code at(key)} result infer as {@code Rec} and inserted a
         * cast that threw on a {@code noobj} or an absent style.
         */
        public static <T extends Stylable<T>> T from(final Obj styleObj, final T stylable) {
            final Style<T> s = from(styleObj);
            s.stylable = stylable;
            return s.applyStyle();
        }

        public Style<T> floatAt(final FloatingSurface.Anchor anchor, final int width,
                                final int top, final int left) {
            this.jvm().put(uri("anchor"), uri(anchor.name().toLowerCase()));
            this.jvm().put(uri("width"), jnt(width));
            this.jvm().put(uri("top"), jnt(top));
            this.jvm().put(uri("left"), jnt(left));
            return this;
        }

        public Style<T> unfloat() {
            this.jvm().remove(uri("anchor"));
            this.jvm().remove(uri("width"));
            this.jvm().remove(uri("top"));
            this.jvm().remove(uri("left"));
            return this;
        }

        public boolean hasFloat() {
            return this.at("anchor").isUri();
        }

        public FloatingSurface.Anchor anchor() {
            return this.at("anchor").isUri()
                    ? FloatingSurface.Anchor.parse(this.at("anchor").uriValue().toString())
                    : null;
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
         * Initial {@code x} (column) scroll offset — the column of the widget's
         * content shown in the leftmost visible cell.  0 = content flush left.
         *
         * <p>For a widget pinned to a {@link FloatingSurface} this is only the
         * <em>seed</em>: the surface's slot owns the live offset (exactly like
         * {@link #width()} seeds the slot's target width), so the offset the
         * user scrolls to survives the widget being re-hydrated into a fresh
         * instance by the next {@code .display()} update.
         */
        public int scrollX() {
            return this.at("scrollX").orElse(jnt(0)).asInt().intValue().intValue();
        }

        /**
         * Initial {@code y} (row) scroll offset — the body row shown at the top
         * of the widget's viewport.  See {@link #scrollX()} for the seed
         * semantics.
         */
        public int scrollY() {
            return this.at("scrollY").orElse(jnt(0)).asInt().intValue().intValue();
        }

        public Style<T> scrollTo(final int x, final int y) {
            this.jvm().put(uri("scrollX"), jnt(x));
            this.jvm().put(uri("scrollY"), jnt(y));
            return this;
        }

        public Style<T> scrollBy(final int dx, final int dy) {
            return this.scrollTo(this.scrollX() + dx, this.scrollY() + dy);
        }

        /**
         * Which axes this widget accepts scrolling on, declared as
         * {@code style=>[scroll=>union(x,y)]}.  Accepted forms — all of them
         * are the same declaration in different clothes:
         *
         * <pre>{@code
         *   scroll=>union(x,y)          // both axes (also: [x=>true,y=>true], xy, both)
         *   scroll=>union(x)            // horizontal only   (also: x, union(x,none))
         *   scroll=>union(y)            // vertical only     (also: y, union(y,none))
         *   scroll=>none                // no scrolling      (also: scroll=>false)
         * }</pre>
         *
         * <p>An <em>unset</em> {@code scroll} means {@link #SCROLL_XY}: scrolling
         * is available on any axis whose content actually overflows the
         * viewport.  Nothing scrolls until there is something off-screen, so
         * the default is free.
         */
        public int scrollAxes() {
            return parseAxes(this.at("scroll"));
        }

        public boolean scrollableX() {
            return (this.scrollAxes() & SCROLL_X) != 0;
        }

        public boolean scrollableY() {
            return (this.scrollAxes() & SCROLL_Y) != 0;
        }

        /**
         * Declare the scroll axes (see {@link #scrollAxes()}), writing the
         * canonical uri form ({@code x}, {@code y}, {@code xy}, {@code none}).
         */
        public Style<T> scrollAxes(final int axes) {
            this.jvm().put(uri("scroll"), uri(axesName(axes)));
            return this;
        }

        public static String axesName(final int axes) {
            return switch (axes & SCROLL_XY) {
                case SCROLL_X -> "x";
                case SCROLL_Y -> "y";
                case SCROLL_XY -> "xy";
                default -> "none";
            };
        }

        /**
         * Parse a {@code scroll} declaration into an {@link #SCROLL_XY}-style
         * axis mask.  Deliberately tolerant — the declaration arrives as a
         * quoted value ({@code union(x,y)} is kept unevaluated by {@code =>}),
         * a uri ({@code y}), a bool, a lst, or a rec, and every one of them
         * means the same thing: which axes are permitted to scroll.
         */
        public static int parseAxes(final Obj scroll) {
            if (null == scroll || scroll.isNoObj()) return SCROLL_XY;
            if (scroll.isBool()) return scroll.boolValue() ? SCROLL_XY : SCROLL_NONE;
            if (scroll.isUri()) return axesOf(scroll.uriValue().toString());
            if (scroll.isRec()) {
                final Rec axesRec = scroll.asRec();
                int axes = SCROLL_NONE;
                final Obj x = axesRec.at(uri("x"));
                final Obj y = axesRec.at(uri("y"));
                if (null != x && x.boolValue()) axes |= SCROLL_X;
                if (null != y && y.boolValue()) axes |= SCROLL_Y;
                return axes == SCROLL_NONE ? SCROLL_XY : axes;
            }
            if (scroll.isLst())
                return scroll.lstValue().stream().map(Style::parseAxes)
                        .reduce(SCROLL_NONE, (a, b) -> a | b);
            return axesOf(scroll.toString());
        }

        /**
         * Textual form of the axis declaration: {@code union(x,y)} (the quoted
         * mtron form), {@code xy}, {@code y}, {@code none}, ...
         */
        private static int axesOf(final String declaration) {
            if (null == declaration) return SCROLL_XY;
            final String text = declaration.toLowerCase().replace(" ", "");
            if (text.contains("none") || text.contains("off") || text.equals("false") || text.equals("0"))
                return SCROLL_NONE;
            int axes = SCROLL_NONE;
            if (text.contains("x")) axes |= SCROLL_X;
            if (text.contains("y")) axes |= SCROLL_Y;
            return axes == SCROLL_NONE ? SCROLL_XY : axes;
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
         * Render order for floating widgets: higher z-index widgets are drawn
         * later — i.e. on top of — lower ones.  Default 0.  Persistent chrome
         * like menu bars uses {@link Integer#MAX_VALUE} so it always stays
         * visible above overlapping widgets.
         */
        public int zIndex() {
            return this.at("zIndex").orElse(jnt(0)).asInt().intValue().intValue();
        }

        public Style<T> zIndex(final int z) {
            this.jvm().put(uri("zIndex"), jnt(z));
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
