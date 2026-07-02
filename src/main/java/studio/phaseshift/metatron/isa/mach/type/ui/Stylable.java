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

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.INST_CTOR_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Stylable<T extends Stylable<T>> {

    fURI WIDGET_STYLE_TID = f("/m/mach/ui/widget/style");

    Type WIDGET_STYLE_TYPE = Type.Builder.build()
            .tid(REC_TID).vid(WIDGET_STYLE_TID)
            .isaPredicate(rec(
                    uri("border"),        T(URI_TID.maybe()),
                    uri("background"),    T(STR_TID.maybe()),
                    uri("foreground"),    T(STR_TID.maybe()),
                    uri("divider"),       T(STR_TID.maybe()),
                    uri("headerDivider"), T(STR_TID.maybe()),
                    uri("pointer"),       T(STR_TID.maybe()),
                    uri("leftMargin"),    T(STR_TID.maybe()),
                    uri("rightMargin"),   T(STR_TID.maybe()),
                    uri("topMargin"),     T(STR_TID.maybe()),
                    uri("bottomMargin"),  T(STR_TID.maybe())))
            .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(WIDGET_STYLE_TID),
                    lst(T(REC_TID)), (lhs, inst) -> inst.arg(0).as()))
            .create();

    default Style<T> style() {
        return new Style<>((T) this);
    }

    T style(final Style<T> style);

    Style<T> getStyle();

    class Style<T extends Stylable<T>> {
        public T stylable;
        public Border border = Border.none;
        public String background = "";
        public String foreground = "";
        public Widget<?> attachment = null;
        public Widget<?> parent = null;
        public String divider = "";
        public String headerDivider = "";
        public String body = "";
        public int leftMargin = 0;
        public int rightMargin = 0;
        public int topMargin = 0;
        public int bottomMargin = 0;
        public boolean overlapAttachment = false;
        public String pointer = "";
        public int lowRowRange = 0;
        public int highRowRange = Integer.MAX_VALUE;
        public int lowColRange = 0;
        public int highColRange = Integer.MAX_VALUE;
        public String prefix = "";

        protected Style(final T stylable) {
            this.stylable = stylable;
        }

        public static <T extends Stylable<T>> Style<T> empty() {
            return new Style<>(null);
        }

        public Style<T> border(final Border border) {
            this.border = border;
            return this;
        }

        public Style<T> rowRange(final int low, final int high) {
            this.lowRowRange = low;
            this.highRowRange = high;
            return this;
        }

        public Style<T> colRange(final int low, final int high) {
            this.lowColRange = low;
            this.highColRange = high;
            return this;
        }

        public Style<T> pointer(final String pointer) {
            this.pointer = pointer;
            return this;
        }

        public Style<T> background(final String bg) {
            this.background = bg;
            return this;
        }


        public Style<T> foreground(final String fg) {
            this.foreground = fg;
            return this;
        }


        public <R extends Widget<R>> R attachment() {
            return (R) this.attachment;
        }

        public Style<T> attachment(final Widget attachment, final boolean overlap) {
            this.attachment = attachment;
            this.overlapAttachment = overlap;
            return this;
        }

        public Style<T> headerDivider(final String divider) {
            this.headerDivider = divider;
            return this;
        }

        public Style<T> divider(final String divider) {
            this.divider = divider;
            return this;
        }

        public Style<T> textBody(final String body) {
            this.body = body;
            return this;
        }

        public Style<T> margin(final int left, final int right, final int top, final int bottom) {
            this.leftMargin = left;
            this.rightMargin = right;
            this.topMargin = top;
            this.bottomMargin = bottom;
            return this;
        }

        public Style<T> margin(final int left, final int right) {
            this.leftMargin = left;
            this.rightMargin = right;
            return this;
        }

        public Style<T> freePrefix(final String prefix) {
            this.prefix = prefix;
            return this;
        }

        /** Read style fields from a mtron style Rec. */
        public static <T extends Stylable<T>> Style<T> from(final Rec styleRec) {
            if (styleRec == null) return new Style<>(null);
            final Style<T> s = new Style<>(null);
            final java.util.Map<Obj, Obj> j = styleRec.jvm();
            j.forEach((k, v) -> {
                final String key = k.uriValue().toString();
                if ("border".equals(key) && v.isUri()) s.border(Border.parse(v.uriValue().toString()));
                else if ("background".equals(key) && v.isStr()) s.background(v.strValue());
                else if ("foreground".equals(key) && v.isStr()) s.foreground(v.strValue());
                else if ("divider".equals(key) && v.isStr()) s.divider(v.strValue());
                else if ("headerDivider".equals(key) && v.isStr()) s.headerDivider(v.strValue());
                else if ("pointer".equals(key) && v.isStr()) s.pointer(v.strValue());
                else if ("leftMargin".equals(key) && v.isInt()) s.leftMargin = v.intValue().intValue();
                else if ("rightMargin".equals(key) && v.isInt()) s.rightMargin = v.intValue().intValue();
                else if ("topMargin".equals(key) && v.isInt()) s.topMargin = v.intValue().intValue();
                else if ("bottomMargin".equals(key) && v.isInt()) s.bottomMargin = v.intValue().intValue();
            });
            return s;
        }

        public T apply() {
            this.stylable.style(this);
            return this.stylable;
        }
    }
}
