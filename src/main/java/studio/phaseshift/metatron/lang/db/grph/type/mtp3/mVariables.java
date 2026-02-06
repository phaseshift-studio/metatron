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

package studio.phaseshift.metatron.lang.db.grph.type.mtp3;

import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedVariables;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mVariables implements Graph.Variables, WrappedVariables<Rec> {

    protected final Rec variables;

    public mVariables(final Rec variables) {
        this.variables = variables;
    }

    @Override
    public Set<String> keys() {
        return this.variables.elements().map(r -> r.first().uriValue().toString()).collect(Collectors.toSet());
    }

    @Override
    public <R> Optional<R> get(final String key) {
        return Optional.ofNullable(this.variables.at(key).orElse(null));
    }

    @Override
    public void set(final String key, final Object value) {
        this.variables.jvm().put(uri(key), MObjFactory.of().toObj(value));
    }

    @Override
    public void remove(final String key) {
        this.variables.jvm().remove(uri(key));
    }

    @Override
    public Rec getBaseVariables() {
        return this.variables;
    }
}
