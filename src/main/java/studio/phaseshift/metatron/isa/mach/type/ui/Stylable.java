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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.FloatingSurface;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
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

    class Style<T extends Stylable<T>> extends MRec {
        public T stylable;
        public Border border = null;
        public FloatingSurface.Anchor floatAnchor = null;
        public int floatWidth = 0;

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
            this.jvm().put(uri("lowRowRange"), studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt(low));
            this.jvm().put(uri("highRowRange"), studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt(high));
            return this;
        }

        public int lowColRange() {
            return this.at("lowColRange").isInt() ? this.at("lowColRange").asInt().intValue().intValue() : 0;
        }

        public int highColRange() {
            return this.at("highColRange").isInt() ? this.at("highColRange").asInt().intValue().intValue() : Integer.MAX_VALUE;
        }

        public Style<T> colRange(final int low, final int high) {
            this.jvm().put(uri("lowColRange"), studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt(low));
            this.jvm().put(uri("highColRange"), studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt(high));
            return this;
        }

        public String pointer() {
            return this.at("pointer").orElse(studio.phaseshift.metatron.isa.m.type.impl.MStr.str("")).strValue();
        }

        public Style<T> pointer(final String pointer) {
            this.jvm().put(uri("pointer"), studio.phaseshift.metatron.isa.m.type.impl.MStr.str(pointer));
            return this;
        }

        public String background() {
            return this.at("background").orElse(studio.phaseshift.metatron.isa.m.type.impl.MStr.str("")).strValue();
        }

        public Style<T> background(final String bg) {
            this.jvm().put(uri("background"), studio.phaseshift.metatron.isa.m.type.impl.MStr.str(bg));
            return this;
        }

        public String foreground() {
            return this.at("foreground").orElse(studio.phaseshift.metatron.isa.m.type.impl.MStr.str("")).strValue();
        }

        public Style<T> foreground(final String fg) {
            this.jvm().put(uri("foreground"), studio.phaseshift.metatron.isa.m.type.impl.MStr.str(fg));
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
            return this.at("headerDivider").orElse(studio.phaseshift.metatron.isa.m.type.impl.MStr.str("")).strValue();
        }

        public Style<T> headerDivider(final String divider) {
            this.jvm().put(uri("headerDivider"), studio.phaseshift.metatron.isa.m.type.impl.MStr.str(divider));
            return this;
        }

        public String divider() {
            return this.at("divider").orElse(studio.phaseshift.metatron.isa.m.type.impl.MStr.str("")).strValue();
        }

        public Style<T> divider(final String divider) {
            this.jvm().put(uri("divider"), studio.phaseshift.metatron.isa.m.type.impl.MStr.str(divider));
            return this;
        }

        public String textBody() {
            return this.at("body").orElse(studio.phaseshift.metatron.isa.m.type.impl.MStr.str("")).strValue();
        }

        public Style<T> textBody(final String body) {
            this.jvm().put(uri("body"), studio.phaseshift.metatron.isa.m.type.impl.MStr.str(body));
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
            this.jvm().put(uri("leftMargin"), studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt(left));
            this.jvm().put(uri("rightMargin"), studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt(right));
            this.jvm().put(uri("topMargin"), studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt(top));
            this.jvm().put(uri("bottomMargin"), studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt(bottom));
            return this;
        }

        public Style<T> margin(final int left, final int right) {
            this.jvm().put(uri("leftMargin"), studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt(left));
            this.jvm().put(uri("rightMargin"), studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt(right));
            return this;
        }

        public String prefix() {
            return this.at("prefix").orElse(studio.phaseshift.metatron.isa.m.type.impl.MStr.str("")).strValue();
        }

        public Style<T> freePrefix(final String prefix) {
            this.jvm().put(uri("prefix"), studio.phaseshift.metatron.isa.m.type.impl.MStr.str(prefix));
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

        public Style<T> floatAt(final FloatingSurface.Anchor anchor, final int width) {
            this.jvm().put(uri("floatAnchor"), uri(anchor.name().toLowerCase()));
            this.jvm().put(uri("floatWidth"), jnt(width));
            this.floatAnchor = anchor;
            this.floatWidth = width;
            return this;
        }

        public Style<T> unfloat() {
            this.jvm().remove(uri("floatAnchor"));
            this.jvm().remove(uri("floatWidth"));
            this.floatAnchor = null;
            this.floatWidth = 0;
            return this;
        }

        public boolean hasFloat() {
            return this.floatAnchor != null || this.at("floatAnchor").isUri();
        }

        public FloatingSurface.Anchor floatAnchor() {
            if (this.floatAnchor != null) return this.floatAnchor;
            if (this.at("floatAnchor").isUri())
                return FloatingSurface.Anchor.parse(this.at("floatAnchor").uriValue().toString());
            return null;
        }

        public int floatWidth() {
            if (this.floatWidth > 0) return this.floatWidth;
            if (this.at("floatWidth").isInt())
                return this.at("floatWidth").asInt().intValue().intValue();
            return 0; // 0 = "use widget's natural width"
        }

        public T applyStyle() {
            this.stylable.style(this);
            return this.stylable;
        }
    }
}
