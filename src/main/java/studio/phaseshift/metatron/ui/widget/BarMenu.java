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

package studio.phaseshift.metatron.ui.widget;

import studio.phaseshift.metatron.lang.sys.console.Highlighter;
import studio.phaseshift.metatron.ui.Widget;
import studio.phaseshift.metatron.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.ui.Stylable;
import studio.phaseshift.metatron.util.Tuple;

import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BarMenu extends AbstractWidget<BarMenu> {

    private final List<Tuple.Pair<String, Runnable>> options;
    private int currentActive = 0;

    public BarMenu(final List<Tuple.Pair<String, Runnable>> options) {
        this.options = options;
    }

    @Override
    public String toString() {
        String top = this.options.stream().map(Tuple.Pair::get0).reduce(this.style.background + this.style.border.leftSide(), (a, b) -> a + (this.style.background + " " + this.style.background + b + " " + this.style.divider));
        top = top.substring(0, top.length() - this.style.divider.length());
        top = top + (this.style.background + this.style.divider + "{{X}}");
        final int topLength = Highlighter.visualLength(top);
        top = top + (this.style.background + " ".repeat(this.style.attachment.width() - topLength - Highlighter.visualLength(this.style.border.rightSide()))) + this.style.border.rightSide() + "{{X}}";
        if (null == this.style.attachment)
            return top;
        final String attachmentString = this.style.attachment.toString();
        if (this.style.overlapAttachment) {
            final List<String> attachmentList = List.of(attachmentString.split("\n"));
            return top + "\n" + attachmentList.subList(1,attachmentList.size()).stream().reduce("",(a,b) -> a + b + "\n");
        }
        else
            return top + "\n" + attachmentString;

    }


    @Override
    public int width() {
        return this.style.attachment.width();
    }

    @Override
    public String rowString(int i) {
        return i == 0 ? this.toString() : "";
    }
}
