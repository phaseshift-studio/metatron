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

package studio.phaseshift.metatron.isa.grph.space.tp3;

import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.NoObj;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.MInst;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.sys.type.Router;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.ELMT_TID;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.VRTX_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.AUTO_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ElementMap extends AbstractMap<Uri, Obj> {

    protected Element base;

    public ElementMap(final Element base) {
        this.base = base;
    }

    public Element getBase() {
        return this.base;
    }

    @Override
    public Obj get(final Object key) {
        final Property<?> property = this.base.property(((Uri)key).uriValue().toString());
        return property.isPresent() ? MObjFactory.of().create(property.value()) : noobj();
    }
    
    @Override
    public Obj put(final Uri key, final Obj value) {
        Router.global().logger().info("adding property %s to element %s",key,value);
        final Property<?> property = this.base.property(key.uriValue().toString());
        this.base.property(key.uriValue().toString(), value.jvm());
        return property.isPresent() ? MObjFactory.of().create(property.value()) : value;
    }

    @Override
    public Set<Entry<Uri, Obj>> entrySet() {
        return IteratorUtil.stream(this.base.properties()).map(p -> (Property<Object>) p).map(p -> new SimpleEntry<>(uri(p.key()), MObjFactory.of().create(p.value()))).collect(Collectors.toSet());
    }

    @Override
    public boolean equals(final Object o) {
        return ElementHelper.areEqual(this.base, o);

    }

    public Rec selfRec() {
        return rec((Map) this, ELMT_TID,null).self(this, ELMT_TID, null);
    }
    
    @Override
    public int hashCode() {
        return ElementHelper.hashCode(this.base);
    }

    public Rec asRec() {
        return rec((Map) this, ELMT_TID, null);
    }

    public static class LazyAutoInst extends MInst {

        private final ElementMap map;

        public LazyAutoInst(final ElementMap map) {
            super(Tuple.Triplet.with(lst(List.of()), null, noobj()), INST_TID, fURI.fnull);
            this.map = map;
        }

        public fURI tid() {
            return AUTO_INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome());
        }

        public Element getBase() {
            return this.map.getBase();
        }

        public Obj apply(final Obj rhs) {
            return this.map.selfRec();
        }

        public boolean equals(final Object o) {
            if (o instanceof Element)
                return this.map.getBase().equals(o);
            if (o instanceof ElementMap)
                return this.map.getBase().equals(((ElementMap) o).getBase());
            if (o instanceof LazyAutoInst)
                return this.map.getBase().equals(((LazyAutoInst) o).map.getBase());
            return false;
        }

        public int hashCode() {
            return this.getBase().hashCode();
        }
        
    }
}
