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

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.*;
import static studio.phaseshift.metatron.isa.grph.tp3.space.EdgeMap.lazyEdgeToRec;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class VertexMap extends ElementMap {

    protected static final GraphittyLogger LOG = Graphitty.log(VertexMap.class);


    public VertexMap(final Vertex base, final tp3Space tp3Space) {
        super(base, tp3Space);
    }

    @Override
    public Vertex getBase() {
        return (Vertex) this.base;
    }

    @Override
    public boolean containsKey(final Object key) {
        return key.equals(OUT) || key.equals(IN) || key.equals(BOTH) || super.containsKey(key);
    }


    @Override
    public Obj get(final Object key) {
        final fURI keyF = ((Uri) key).uriValue();
        final boolean hasPattern = keyF.hasPattern();
        if (!hasPattern) {
            if (key.equals(OUT) || key.equals(IN) || key.equals(BOTH))
                return objs(IteratorUtil.stream(this.getBase().edges(Direction.valueOf(((Uri) key).uriValue().toString()))).map(e -> rel(uri(e.label()), auto_from_(uri(this.space.elementVID(e)), lazyEdgeToRec(e, this.space)).tryToInst())));
            else {
                try {
                    Obj obj = instB(OUT_INST_TID.dom(VRTX_TID).rng(VRTX_TID.maybeSome()), lst((Uri) key)).apply(vertexToRec(this.getBase()));
                    return obj.append(super.get(key));
                } catch (Exception e) {
                    return super.get(key);
                }
            }
        } else {
            Obj obj = noobj();
            if (OUT_FURI.test(keyF) || BOTH_FURI.test(keyF))
                obj = objs(IteratorUtil.stream(this.getBase().edges(Direction.OUT)).map(e -> rel(uri(e.label()), auto_from_(uri(this.space.elementVID(e)), lazyEdgeToRec(e, this.space)).tryToInst())));
            if (IN_FURI.test(keyF) || BOTH_FURI.test(keyF))
                obj = objs(IteratorUtil.stream(this.getBase().edges(Direction.IN)).map(e -> rel(uri(e.label()), auto_from_(uri(this.space.elementVID(e)), lazyEdgeToRec(e, this.space)).tryToInst())));
            obj = obj.append(instB(OUT_INST_TID.dom(VRTX_TID).rng(VRTX_TID.maybeSome()), lst((Uri) key)).apply(vertexToRec(this.getBase())));
            return obj.append(super.get(key));
        }
    }

    /*@Override
    public Set<Entry<Uri, Obj>> entrySet() {
        final Set<Entry<Uri, Obj>> entries = new LinkedHashSet<>(super.entrySet().stream().collect(Collectors.toSet()));
        entries.add(new SimpleEntry<Uri, Obj>(OUT, auto_(() -> this.get(OUT)).tryToInst()));
        entries.add(new SimpleEntry<Uri, Obj>(IN, auto_(() -> this.get(IN)).tryToInst()));
        return entries;
    }*/

    @Override
    public Obj put(final Uri key, final Obj value) {
        if (key.equals(IN)) {
            LOG.info("adding incoming edge %s from vertex %s", key, value);
            final Edge edge = this.getBase().addEdge(value.asRec().jvm().get(LABEL).uriValue().toString(), Router.readFromSpace(value.asRec().jvm().get(IN).asUri().toString()).asRec().<VertexMap>jvmAs().getBase());
            return auto_from_(uri(this.space.schemaVID(edge.id().toString())), lazyEdgeToRec(edge, this.space)).tryToInst();
        } else if (key.equals(OUT)) {
            LOG.info("adding outgoing edge %s to vertex %s", key, value);
            final Edge edge = Router.readFromSpace(value.asRec().jvm().get(OUT).asUri().toString()).asRec().<VertexMap>jvmAs().getBase().addEdge(value.asRec().jvm().get(LABEL).uriValue().toString(), this.getBase());
            return auto_from_(uri(this.space.schemaVID(edge.id().toString())), lazyEdgeToRec(edge, this.space)).tryToInst();
        } else if (value.isLst()) {
            LOG.info("adding list %s to vertex %s", key, value);
            this.getBase().property(VertexProperty.Cardinality.list, key.uriValue().toString(), value.lstValue());
            return value;
        }
        return super.put(key, value);
    }

    @Override
    public Rec asRec() {
        return rec((Map) this, f(this.getBase().label()), this.space.elementVID(this.base));
    }

    @Override
    public Rec selfRec() {
        return rec().self(this, f(this.getBase().label()), this.space.elementVID(this.base)).asRec();
    }

    public static Rec vertexToRec(final Vertex vertex, final Rec lhs) {
        return rec().self(new VertexMap(vertex, lhs.<ElementMap>jvmAs().space), f(vertex.label()).c(lhs.c()), lhs.<ElementMap>jvmAs().space.elementVID(vertex)).parent(lhs);
    }

    public static Rec vertexToRec(final Vertex vertex) {
        return new VertexMap(vertex, tp3Space.from(vertex)).selfRec();
        //   return VertexMap.vertexToRec(vertex, tp3Space.from(vertex));
    }

    public static Rec vertexToRec(final Vertex vertex, final tp3Space space) {
        return new VertexMap(vertex, space).selfRec();
        //   return VertexMap.vertexToRec(vertex, tp3Space.from(vertex));
    }

    public static Inst lazyVertexToRec(final Vertex base, final tp3Space lhs) {
        return new LazyAutoElmnt(new VertexMap(base, lhs));
    }


    public static Vertex recToVertex(final Rec rec) {
        return rec.<VertexMap>jvmAs().getBase();
    }


}
