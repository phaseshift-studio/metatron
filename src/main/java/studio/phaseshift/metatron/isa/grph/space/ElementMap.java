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

package studio.phaseshift.metatron.isa.grph.space;

import org.apache.tinkerpop.gremlin.structure.Element;
import org.jspecify.annotations.NonNull;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ElementMap<T extends Element> extends AbstractMap<Obj, Obj> {

    protected final T element;

    public ElementMap(final T element) {
        this.element = element;
    }

    @Override
    public @NonNull Set<Entry<Obj, Obj>> entrySet() {
        return IteratorUtil.stream(element.<Object>properties()).map(p -> new SimpleEntry<Obj,Obj>(uri(p.key()), MObjFactory.single().toObj(p.value()))).collect(Collectors.toSet());
    }
    
    @Override
    public Obj put(final Obj key, final Obj value) {
        this.element.property(key.uriValue().toString(),value.jvm());
        return value;
    }

    @Override
    public Obj get(final Object key) {
       return MObjFactory.single().toObj(this.element.property(((Uri)key).uriValue().toString()).orElse(null));
    }
}
