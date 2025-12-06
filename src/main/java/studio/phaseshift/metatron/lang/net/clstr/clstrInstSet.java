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

package studio.phaseshift.metatron.lang.net.clstr;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MInstSet;

import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.f;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class clstrInstSet extends MInstSet {

    public static final fURI MCLSTR_TID = f("/clstr");

    public clstrInstSet(final fURI vid) {
        super(MCLSTR_TID, vid);
    }

    public static clstrInstSet create() {
        return new clstrInstSet(fURI.fnull);
    }

    @Override
    public Set<Type> types() {
        return Set.of(clstrSpace.CLSTR_TYPE);
    }
}