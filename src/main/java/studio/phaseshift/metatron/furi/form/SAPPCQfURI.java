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

import java.util.List;
import java.util.Map;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SAPPCQfURI extends SAPXCQfURI {

    protected final List<String> poly;

    public SAPPCQfURI(final String scheme, final String host, final int port, final List<String> path, final List<String> poly, final C<?, ?> coefficient, final Map<String, String> query) {
        super(scheme, host, port, path, coefficient, query);
        this.poly = null == poly ? List.of() : poly;
    }

    @Override
    public List<String> poly() {
        return this.poly;
    }
    
}
