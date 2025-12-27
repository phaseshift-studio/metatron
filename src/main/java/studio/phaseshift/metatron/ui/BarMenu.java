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

import studio.phaseshift.metatron.util.Tuple;

import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BarMenu implements Dimensions, Stylable<BarMenu> {

    private final List<Tuple.Pair<String, Runnable>> options;
    private int currentActive = 0;
    private Style style = Style.build();
    
    public BarMenu(final List<Tuple.Pair<String, Runnable>> options) {
        this.options = options;
    }

    @Override
    public String toString() {
        String top = this.options.stream().map(Tuple.Pair::get0).reduce(this.style.background + this.style.border.leftSide(), (a, b) -> a + (this.style.background + " " + this.style.background + b + " " + this.style.divider));
        top = top.substring(0, top.length() - this.style.divider.length());
        top =  top + (this.style.background + this.style.divider + "{{X}}");
        final int topLength = Graphitty.strip(top).length();
        top = top + (this.style.background + " ".repeat(this.style.attachment.width() - topLength - Graphitty.strip(this.style.border.rightSide()).length())) + this.style.border.rightSide() + "{{X}}";
        return null == this.style.attachment ? top : (top + "\n" + this.style.attachment);
    }


    @Override
    public int width() {
        return this.style.attachment.width();
    }

    @Override
    public int height() {
        return Dimensions.super.height();
    }

    @Override
    public String rowString(int i) {
        return i == 0 ? this.toString() : "";
    }


    @Override
    public Style style() {
        return this.style;
    }

    @Override
    public BarMenu style(final Style style) {
        this.style = style;
        return this;
    }
}
