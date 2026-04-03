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
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.util.Tuple;

import java.util.List;
import java.util.Map;

/*
 * fURI with Template support - extends the most complete fURI (SAPPCQfURI) and adds template storage.
 * Templates are ${expr} placeholders that get evaluated at the Uri (monadic) level.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SAPPCQTfURI extends SAPPCQfURI {

    protected final List<Tuple.Pair<fURI.Component, String>> templates;

    public SAPPCQTfURI(final String scheme,
                       final String host,
                       final int port,
                       final List<String> path,
                       final List<String> poly,
                       final C<?, ?> coefficient,
                       final Map<String, String> query,
                       final List<Tuple.Pair<fURI.Component, String>> templates) {
        super(scheme, host, port, path, poly, coefficient, query);
        this.templates = null == templates ? List.of() : templates;
    }

    @Override
    public List<Tuple.Pair<fURI.Component, String>> templates() {
        return this.templates;
    }

    @Override
    public boolean hasTemplates() {
        return !this.templates.isEmpty();
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj))
            return false;
        if (obj instanceof SAPPCQTfURI other) {
            return this.templates.equals(other.templates);
        }
        // If other is not template-aware but we have templates, not equal
        return this.templates.isEmpty();
    }

    @Override
    public int hashCode() {
        return super.hashCode() * 31 + this.templates.hashCode();
    }
}
