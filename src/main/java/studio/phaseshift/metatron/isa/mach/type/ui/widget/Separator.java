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

package studio.phaseshift.metatron.isa.mach.type.ui.widget;

import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Separator extends AbstractWidget<Separator> {
    
    private final String sepToken;
    private final Widget coupledWidth;

    public Separator(final String sepToken, final Widget coupledWidth) {
        this.sepToken = sepToken;
        this.coupledWidth = coupledWidth;
    }

    @Override
    public int height() {
        return 1;
    }

    @Override
    public int width() {
        return this.coupledWidth.width();
    }

    @Override
    public String toString() {
        int tokenWidth = Highlighter.visualLength(this.sepToken);
        return this.sepToken.repeat((int) ((float) this.coupledWidth.width() / (float) tokenWidth)) + "{{X}}";
    }

    @Override
    public Separator style(final Style<Separator> style) {
        this.style = style;
        return new Separator(style.foreground + this.sepToken + "{{X}}", this.coupledWidth);
    }
}
