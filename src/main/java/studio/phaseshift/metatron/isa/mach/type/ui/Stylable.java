/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Stylable<T extends Stylable<T>> {

    default Style<T> style() {
        return new Style<>((T) this);
    }

    T style(final Style<T> style);

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

        public T apply() {
            this.stylable.style(this);
            return this.stylable;
        }
    }
}
