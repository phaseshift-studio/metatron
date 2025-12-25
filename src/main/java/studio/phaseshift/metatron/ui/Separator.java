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
public class Separator implements Dimensions {

    private final String sepToken;
    private final Dimensions coupledWidth;

    public Separator(final String sepToken, final Dimensions coupledWidth) {
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
        int tokenWidth = Graphitty.strip(this.sepToken).length();
        return this.sepToken.repeat((int) ((float) this.coupledWidth.width() / (float) tokenWidth)) + "{{X}}";
    }
}
