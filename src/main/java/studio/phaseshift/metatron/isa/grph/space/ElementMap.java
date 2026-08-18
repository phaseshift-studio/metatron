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
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.grph.io.ObjTP3Serializer;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.AbstractMap;
import java.util.Objects;
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
        return IteratorUtil.stream(element.<Object>properties()).map(p -> new SimpleEntry<Obj, Obj>(uri(p.key()), p.key().startsWith("mtron:") ? ObjmtronSerializer.single().read(p.value().toString()) : ObjTP3Serializer.tp3FromValue(p.value()))).collect(Collectors.toSet());
    }

    @Override
    public Obj put(final Obj key, final Obj value) {
        if (Objects.equals("mtron", key.uriValue().scheme()) && value.isPoly()) {
            final String serial = ObjmtronSerializer.single().write(value);
            this.element.property(key.uriValue().toString(), serial);
        } else if (value.isPoly()) {
            this.element.property(key.uriValue().scheme("mtron").toString(), ObjmtronSerializer.single().write(value));
        } else
            this.element.property(key.uriValue().toString(), value.jvm());
        return value;
    }

    @Override
    public Obj get(final Object key) {
        final fURI keyf = ((Uri) key).uriValue();
        if (Objects.equals("mtron", keyf.scheme())) {
            final Obj serial = ObjmtronSerializer.single().read(this.element.property(keyf.toString()).orElse((Object) "noobj").toString());
            return serial;
        } else {
            final Obj object = ObjTP3Serializer.tp3FromValue(this.element.property(keyf.toString()).orElse(null));
            if (null == object) {
                final Obj serial = ObjmtronSerializer.single().read(this.element.property(keyf.scheme("mtron").toString()).orElse((Object) "noobj").toString());
                return serial;
            } else {
                return object;
            }
        }
    }
}
