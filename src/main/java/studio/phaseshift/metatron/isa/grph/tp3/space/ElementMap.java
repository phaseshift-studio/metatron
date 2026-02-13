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

package studio.phaseshift.metatron.isa.grph.tp3.space;

import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.MInst;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.mach.io.type.ObjCleanStringSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.util.AbstractMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.isa.grph.tp3.space.tp3Space.FACTORY;
import static studio.phaseshift.metatron.isa.m.mInstSet.AUTO_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class ElementMap extends AbstractMap<Uri, Obj> {

    protected static final GraphittyLogger LOG = Graphitty.log(ElementMap.class);

    protected static final ObjSerializer<String> SERIALIZER = new ObjCleanStringSerializer();
    protected Element base;
    transient public final tp3Space space;

    public ElementMap(final Element base, final tp3Space space) {
        this.base = base;
        this.space = space;
    }

    public Element getBase() {
        return this.base;
    }

    @Override
    public Obj get(final Object key) {
        Property<?> property = this.base.property(((Uri) key).uriValue().toString());
        if (property.isPresent()) {
            return MObjFactory.of().toObj(property.value());
        } else {
            property = this.base.property(":" + ((Uri) key).uriValue().toString());
            return property.isPresent() ? mParser.m_obj().parse(property.value().toString()).get() : noobj();
        }
    }

    @Override
    public Obj put(final Uri key, final Obj value) {
        final Property<?> property = this.base.property(key.uriValue().toString());
        if (value.isMono()) {
            LOG.info("adding mono property %s to element %s", key, value);
            this.base.property(key.uriValue().toString(), value.jvm());
        } else {
            LOG.info("adding mtron-native property %s to element %s", key, value);
            String keyString = key.uriValue().toString();
            while (keyString.startsWith(":"))
                keyString = keyString.substring(1);
            this.base.property(":" + keyString, SERIALIZER.write(value));
        }
        return property.isPresent() ? FACTORY.toObj(property.value()) : null;
    }

    @Override
    public Set<Entry<Uri, Obj>> entrySet() {
        return IteratorUtil.stream(this.base.properties())
                .map(p -> (Property<Object>) p)
                .map(p -> {
                    final Uri key = uri(p.key());
                    final Obj value = key.toString().startsWith(":") ? MObjFactory.of().toObj(SERIALIZER.read(p.value().toString())) : MObjFactory.of().toObj(p.value());
                    return new SimpleEntry<>(key, value);
                })
                .collect(Collectors.toSet());
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof ElementMap && ElementHelper.areEqual(this.base, ((ElementMap) o).getBase());
    }

    public abstract Rec selfRec();

    @Override
    public int hashCode() {
        return ElementHelper.hashCode(this.base);
    }

    public abstract Rec asRec();

    public static class LazyAutoInst<E extends ElementMap> extends MInst {

        private final E map;

        public LazyAutoInst(final E map) {
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
