/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.util;

import studio.phaseshift.metatron.furi.fURI;

import java.util.stream.Collectors;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DatabasePath {

    // /<db>/<ag>/<el>/<field>/<value/....>
    // appdb/people/1/address/street/number
    
    private final String databaseName;
    private final String aggregateName;
    private final String elementName;
    private final String fieldName;
    private final fURI valuePath;

    public DatabasePath(final fURI uri) {
        final int size = uri.path().size();
        this.databaseName = size > 0 ? uri.path().get(0) : null;
        this.aggregateName = size > 1 ? uri.path().get(1) : null;
        this.elementName = size > 2 ? uri.path().get(2) : null;
        this.fieldName = size > 3 ? uri.path().get(3) : null;
        this.valuePath = size > 4 ? f("/" + String.join("/", uri.path().subList(4, size))) : null;
    }

    public String getDatabaseName() {
        return this.databaseName;
    }

    public String getAggregateName() {
        return this.aggregateName;
    }

    public String getElementName() {
        return this.elementName;
    }

    public String getFieldName() {
        return this.fieldName;
    }

    public fURI getValuePath() {
        return this.valuePath;
    }
}
