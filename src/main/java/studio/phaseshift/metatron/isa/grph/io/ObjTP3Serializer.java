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

package studio.phaseshift.metatron.isa.grph.io;

import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.grph.space.EdgeRec;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import studio.phaseshift.metatron.isa.grph.space.VertexRec;
import studio.phaseshift.metatron.isa.grph.space.grphSpace;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;

import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import studio.phaseshift.metatron.isa.mach.io.type.AbstractObjSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjByteBufferSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;

import static studio.phaseshift.metatron.isa.web.webInstSet.OBJ_TP3_SERIALIZER_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjTP3Serializer extends AbstractObjSerializer<Element> {

    public static final fURI OBJ_TP3_SERIALIZER_VID = OBJ_TP3_SERIALIZER_TID;
    private static final ObjSerializer<ByteBuffer> BYTES_SERIALIZER = new ObjByteBufferSerializer();

    private static final ObjTP3Serializer INSTANCE = new ObjTP3Serializer();

    public static ObjTP3Serializer single() {
        return INSTANCE;
    }

    public ObjTP3Serializer() {
        super(OBJ_TP3_SERIALIZER_TID, OBJ_TP3_SERIALIZER_VID);
    }

    @Override
    public ByteBuffer outputBytes(final Obj obj) throws MTronException {
        return BYTES_SERIALIZER.outputBytes(obj);
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) throws MTronException {
        return BYTES_SERIALIZER.inputBytes(bytes);
    }

    @Override
    public Obj read(final Element data) throws MTronException {
        return data instanceof Vertex v ?
                new VertexRec(v, grphSpace.from(v))
                : new EdgeRec((Edge) data, grphSpace.from(data));
    }

    @Override
    public fURI vid() {
        return OBJ_TP3_SERIALIZER_VID;
    }

    /**
     * Convert a metatron value to a TinkerPop-serializable Java primitive.
     * URIs are wrapped in {@code <>} so they round-trip as URIs on read-back.
     */
    public static Object tp3Value(final Obj obj) {
        return switch (obj) {
            case NoObj __ -> null;
            case Int i -> i.jvm();
            case Real r -> r.jvm();
            case Bool b -> b.jvm();
            case Str s -> s.jvm();
            case Uri u -> "<" + u.uriValue() + ">";
            default -> obj.toString();
        };
    }

    /**
     * Reverse of {@link #tp3Value}: convert a TinkerPop property value
     * back to a metatron Obj.  Strings wrapped in {@code <>} are
     * recognized as URIs.
     */
    public static Obj tp3FromValue(final Object value) {
        if (value instanceof String s && s.startsWith("<") && s.endsWith(">")) {
            return uri(f(s.substring(1, s.length() - 1)));
        }
        if (value instanceof String s) {
            return str(s);
        }
        return MObjFactory.single().toObj(value);
    }

    /**
     * Map a Java property type to its mtron Type representation.
     * Returns {@code null} for unsupported types.
     */
    public static Type javaTypeToMtronType(final Class<?> javaType) {
        if (javaType == String.class) return T(str("").tid());
        if (javaType == Integer.class || javaType == Long.class) return T(jnt(0).tid());
        if (javaType == Double.class || javaType == Float.class) return T(real(0.0).tid());
        if (javaType == Boolean.class) return T(bool(false).tid());
        return null;
    }

/*
private Map<Obj, Obj> createProperties(final Element element) {
        final Map<Obj, Obj> props = new LinkedHashMap<>();
        element.properties().forEachRemaining(tpP -> props.put(uri(tpP.key()), MObjFactory.of().toObj(tpP.value())));
        return props;
    }

    private Rec createEdge(final Edge tpEdge) {
        final Map<Obj, Obj> props = createProperties(tpEdge);
        return rec(Map.of(uri(LABEL), uri(tpEdge.label()),
                        uri(PROPS), props.isEmpty() ? noobj() : rec(props),
                        uri(Direction.OUT.name()), auto(this.builder.root.extend("V").extend(tpEdge.outVertex().id().toString())),
                        uri(Direction.IN.name()), auto(this.builder.root.extend("V").extend(tpEdge.inVertex().id().toString()))),
                EDGE_TID,null);
        //this.builder.root.extend("E").extend(tpEdge.id().toString()));
    }

    @Override
    public Obj translate(final Graph graph) {
        graph.vertices().forEachRemaining(tpV -> {
            final Map<Obj, Obj> out = mutableMap();
            tpV.edges(Direction.OUT).forEachRemaining(tpE -> out.compute(uri(tpE.label()), (k, v) -> null == v ? createEdge(tpE) : v.append(createEdge(tpE))));
            final Map<Obj, Obj> in = mutableMap();
            tpV.edges(Direction.IN).forEachRemaining(tpE -> in.compute(uri(tpE.label()), (k, v) -> null == v ? createEdge(tpE) : v.append(createEdge(tpE))));
            final Map<Obj, Obj> props = createProperties(tpV);
            Router.writeToSpace(this.builder.root.extend("V").extend(tpV.id().toString()), rec(
                    Map.of(uri(ID), uri(tpV.id().toString()),
                            uri(LABEL), uri(tpV.label()),
                            uri(PROPS), props.isEmpty() ? noobj() : rec(props),
                            uri(Direction.OUT.name()), out.isEmpty() ? noobj() : rec(out),
                            uri(Direction.IN.name()), in.isEmpty() ? noobj() : rec(in)),
                    VRTX_TID,
                   null));
            //this.builder.root.extend("V").extend(tpV.id().toString()));
        });
        
              graph.edges().forEachRemaining(tpE -> {
            Router.writeToSpace(Router.readFromSpace(this.builder.root.extend("V").extend(tpE.outVertex().id().toString()))
                    .stream()
                    .map(v -> v.as(RVertex.class))
                    .map(v -> {
                        v.edge(tpE.label(), this.builder.root.extend("V").extend(tpE.inVertex().id().toString()),
                                IteratorUtil.stream(tpE.properties()).map(p -> rel(uri(p.key()), MObjFactory.of().create(p.value()))).collect(new Common.RecCollector()));
                        return v;
                    }).iterator().next());
        });
         
        return Router.readFromSpace(this.builder.root.extend("+"));
}
 */

}
