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

package studio.phaseshift.metatron.ui;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Stylable<T extends Stylable<T>> {

    Style style();

    T style(final Style style);

    class Style {

        public Border border = Border.none;
        public String background = "";
        public String foreground = "";
        public Dimensions attachment = null;
        public String divider = "";

        private Style() {

        }

        public static Style build() {
            return new Style();
        }

        public Style border(final Border border) {
            this.border = border;
            return this;
        }


        public Style background(final String bg) {
            this.background = bg;
            return this;
        }


        public Style foreground(final String fg) {
            this.foreground = fg;
            return this;
        }


        public Style attachment(final Dimensions attachment) {
            this.attachment = attachment;
            return this;
        }
        
        public Style divider(final String divider) {
            this.divider = divider;
            return this;
        }

    }
}
