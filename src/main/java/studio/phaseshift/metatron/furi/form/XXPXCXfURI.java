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

package studio.phaseshift.metatron.furi.form;

import studio.phaseshift.metatron.furi.C;
import studio.phaseshift.metatron.furi.c.cInt;

import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class XXPXCXfURI extends XXPXXXfURI {

    final cInt coefficient;

    public XXPXCXfURI(final List<String> path, final C<?, ?> coefficient) {
        super(path);
        this.coefficient = null == coefficient ? cInt.ONE() : (cInt) coefficient;
    }

    @Override
    public cInt c() {
        return this.coefficient;
    }
}
