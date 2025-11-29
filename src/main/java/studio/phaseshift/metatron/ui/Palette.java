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

public class Palette {

    public static final Palette STANDARD = new Palette();
    public static final Palette NO_COLOR = new Palette(false);

    public static Palette GLOBAL = STANDARD;

    boolean inColor;

    public Palette() {
        this(true);
    }

    public Palette(final boolean inColor) {
        this.inColor = inColor;
    }

    public String typeC() {
        return inColor ? "{{b}}" : "";
    }

    public String valueC() {
        return inColor ? "{{y}}" : "";
    }

    public String formC() {
        return inColor ? "{{g}}" : "";
    }

    public String form2C() {
        return inColor ? "{{m}}" : "";
    }

    public String form3C() {
        return inColor ? "{{c}}" : "";
    }

    public String warnC() {
        return inColor ? "{{y}}" : "";
    }

    public String infoC() {
        return inColor ? "{{g}}" : "";
    }

    public String debugC() {
        return inColor ? "{{m}}" : "";
    }

    public String errorC() {
        return inColor ? "{{r}}" : "";
    }
}
