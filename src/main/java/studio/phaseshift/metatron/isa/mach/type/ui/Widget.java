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

import org.jline.terminal.Cursor;
import studio.phaseshift.metatron.isa.mach.type.console.Highlighter;

import java.util.Arrays;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Widget<W extends Widget<W>> extends Stylable<W>, AutoCloseable, Runnable {

    public static final String X = "{{X}}";
    
    @Override
    default void close() {

    }

     W cursor(final Cursor cursor);
    
    void display();

    default int height() {
        return this.format().split("\n").length;
    }

    default int width() {
        return Arrays.stream(this.format().split("\n")).map(Highlighter::visualLength).max(Integer::compareTo).orElse(0);
    }

    default int rowCount() {
        return this.height();
    }

    default int columnCount() {
        return this.width();
    }

    default String rowString(int i) {
        return this.format().split("\n")[i];
    }

    default String format() {
        return Highlighter.format(this.toString());
    }
}
