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

package studio.phaseshift.metatron.lang.db.grph.inst;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MInstSet;
import studio.phaseshift.metatron.lang.db.grph.grphSpace;
import studio.phaseshift.metatron.lang.db.grph.type.mtron.m1Vertex;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.id_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

public class grphInstSet extends MInstSet {

    public static final fURI GRPH_INSTSET_TID = f("/grph");
    public static final fURI GRAPH_TID = GRPH_INSTSET_TID.extend("graph");
    public static final fURI ELEMENT_TID = GRPH_INSTSET_TID.extend("element");
    public static final fURI VERTEX_TID = GRPH_INSTSET_TID.extend("vertex");
    public static final fURI VERTEX_PROPERTY_TID = GRPH_INSTSET_TID.extend("vproperty");
    public static final fURI PROPERTY_TID = GRPH_INSTSET_TID.extend("property");
    public static final fURI EDGE_TID = GRPH_INSTSET_TID.extend("edge");

    public static final fURI INST_TID = GRPH_INSTSET_TID.extend("inst");
    public static final fURI G_INST_TID = INST_TID.extend("g");
    public static final fURI V_INST_TID = INST_TID.extend("V");
    public static final fURI E_INST_TID = INST_TID.extend("E");
    public static final fURI OUT_INST_TID = INST_TID.extend("out");
    public static final fURI OUTE_INST_TID = INST_TID.extend("outE");
    public static final fURI IN_INST_TID = INST_TID.extend("in");
    public static final fURI INE_INST_TID = INST_TID.extend("inE");
    public static final fURI BOTH_INST_TID = INST_TID.extend("both");
    public static final fURI BOTHE_INST_TID = INST_TID.extend("bothE");
    public static final fURI OUTV_INST_TID = INST_TID.extend("outV");
    public static final fURI INV_INST_TID = INST_TID.extend("inV");
    public static final fURI BOTHV_INST_TID = INST_TID.extend("bothV");
    public static final fURI VALUES_INST_TID = INST_TID.extend("values");
    public static final fURI PROPERTIES_INST_TID = INST_TID.extend("properties");

    public static final fURI LABEL_INST_TID = INST_TID.extend("label");
    public static final fURI HAS_INST_TID = INST_TID.extend("has");

    public grphInstSet(final fURI vid) {
        super(GRPH_INSTSET_TID, vid);
        // this.types().forEach(t -> Router.global().registerRewrite(f(t.tid().name()), t.tid()));
    }

    public static grphInstSet create() {
        return new grphInstSet(fURI.fnull);
    }

    private static String[] labelsAsUri(final Inst inst) {
        return inst.args().isEmpty() ?
                EMPTY_STRING_ARRAY :
                inst.args().elements()
                        .flatMap(Obj::<Obj>stream)
                        .map(Obj::uriValue)
                        .map(Object::toString)
                        .toArray(String[]::new);

    }

    private static Object[] idsAsUri(final Inst inst) {
        return inst.args().isEmpty() ?
                EMPTY_STRING_ARRAY :
                inst.args().elements()
                        .flatMap(Obj::<Obj>stream)
                        .map(Obj::jvm)
                        .map(o -> o instanceof fURI ? ((fURI) o).name() : o)
                        .toArray(Object[]::new);

    }

    @Override
    public Set<Type> types() {
        return Set.of(
                T(GRAPH_TID, isa_(T(VERTEX_TID.maybeSome()))),
                T(ELEMENT_TID, isa_(rec())),
                T(VERTEX_TID),//, null, instC(INST_TID.dom(ALL.maybe()).rng(VERTEX_TID), lst(), (lhs, inst) -> RVertex.of(lhs.as()))),
                T(EDGE_TID),//, null, instC(INST_TID.dom(ALL.maybe()).rng(EDGE_TID), lst(), (lhs, inst) -> REdge.of(lhs.as()))),
                T(PROPERTY_TID, isa_(rec(T(URI_TID), id_()))),
                grphSpace.GRPH_TYPE);
    }

    @Override
    public Set<Inst> rewrites() {
        return new HashSet<>();
    }

    @Override
    public Set<Inst> insts() {
        //  Router.global().write(VERTEX_TID,T(REC_TID));
        //  Router.global().write(EDGE_TID,T(REC_TID));
        final Set<Inst> set = new LinkedHashSet<>();
        set.addAll(m1Vertex.m1VertexType.insts());
        return set;
       /* return new LinkedHashSet<>(List.of(
                instC(V_INST_TID.dom(URI_TID).rng(VERTEX_TID.maybeSome()), lst(), (lhs, inst) -> Router.readFromSpace(f("/" + lhs.uriValue()).extend("V/+"))),
                //instC(V_TID.dom(NOOBJ_TID.zero()).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(inst.arg(0).stream().flatMap(u -> Router.readFromSpace(u.uriValue()).stream()))),
                instC(BOTH_INST_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.stream().map(Obj::<Rec>as).flatMap(x -> RVertex.of(x).vertices(Direction.BOTH, inst.args().as()).map(Obj::as)))),
                instC(BOTHE_INST_TID.dom(VERTEX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.stream().map(Obj::<Rec>as).flatMap(x -> RVertex.of(x).edges(Direction.BOTH, inst.args().as()).map(Obj::as)))),
                instC(OUT_INST_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.stream().map(Obj::<Rec>as).flatMap(x -> RVertex.of(x).vertices(Direction.OUT, inst.args().as()).map(Obj::as)))),
                instC(OUTE_INST_TID.dom(VERTEX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.stream().map(Obj::<Rec>as).flatMap(x -> RVertex.of(x).edges(Direction.OUT, inst.args().as()).map(Obj::as)))),
                instC(IN_INST_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.stream().map(Obj::<Rec>as).flatMap(x -> RVertex.of(x).vertices(Direction.IN, inst.args().as()).map(Obj::as)))),
                instC(INE_INST_TID.dom(VERTEX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.stream().map(Obj::<Rec>as).flatMap(x -> RVertex.of(x).edges(Direction.IN, inst.args().as()).map(Obj::as)))),
                instC(INV_INST_TID.dom(EDGE_TID).rng(VERTEX_TID), lst(), (lhs, inst) -> objs(lhs.stream().map(Obj::<Rec>as).flatMap(x -> REdge.of(x).vertices(Direction.IN)))),
                instC(OUTV_INST_TID.dom(EDGE_TID).rng(VERTEX_TID), lst(), (lhs, inst) -> objs(lhs.stream().map(Obj::<Rec>as).flatMap(x -> REdge.of(x).vertices(Direction.OUT)))),
                instC(BOTHV_INST_TID.dom(EDGE_TID).rng(VERTEX_TID.c("2")), lst(), (lhs, inst) -> objs(lhs.stream().map(Obj::<Rec>as).flatMap(x -> REdge.of(x).vertices(Direction.BOTH)))),
                instC(LABEL_INST_TID.dom(REC_TID).rng(URI_TID), lst(), (lhs, inst) -> lhs.<Rec>as().at(LABEL)),
                instC(HAS_INST_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybe()), lst(T(URI_TID), T(BOOL_TID)), (lhs, inst) -> inst.arg(1).apply(lhs.as(RElement.class).<Rec>at(PROPS).at(inst.arg(0))).<Bool>as().boolValue() ? lhs : noobj()),
                instC(HAS_INST_TID.dom(EDGE_TID).rng(EDGE_TID.maybe()), lst(T(URI_TID), T(BOOL_TID)), (lhs, inst) -> inst.arg(1).apply(lhs.as(RElement.class).<Rec>at(PROPS).at(inst.arg(0))).<Bool>as().boolValue() ? lhs : noobj()),
                instC(HAS_INST_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybe()), lst(T(URI_TID)), (lhs, inst) -> objs(lhs.stream().map(x -> x.as(RElement.class).<Rec>at(PROPS).has(inst.arg(0)) ? lhs : noobj()))),
                instC(HAS_INST_TID.dom(EDGE_TID).rng(EDGE_TID.maybe()), lst(T(URI_TID)), (lhs, inst) -> lhs.as(RElement.class).<Rec>at(PROPS).has(inst.arg(0)) ? lhs : noobj()),
                // TODO: why does values() and properties() require streaming objs when the doms are unit
                instC(VALUES_INST_TID.dom(VERTEX_TID).rng(ALL.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.stream().map(Obj::<Rec>as).flatMap(r -> RVertex.of(r).values(inst.arg(0))).map(Obj::as))),
                instC(VALUES_INST_TID.dom(EDGE_TID).rng(ALL.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.stream().map(Obj::<Rec>as).flatMap(r -> REdge.of(r).values(inst.arg(0))).map(Obj::as))),
                instC(PROPERTIES_INST_TID.dom(VERTEX_TID).rng(REL_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.stream().map(Obj::<Rec>as).flatMap(r -> RVertex.of(r).properties(inst.arg(0))).map(Obj::as))),
                instC(PROPERTIES_INST_TID.dom(EDGE_TID).rng(REL_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.stream().map(Obj::<Rec>as).flatMap(r -> REdge.of(r).properties(inst.arg(0))).map(Obj::as))),
                instC(PROPERTIES_INST_TID.dom(A).rng(A), lst(T(REC_TID)), (lhs, inst) -> objs(lhs.stream().map(Obj::<Rec>as).peek(r -> inst.arg(0).<Rec>as().elements().forEach(kv -> RElement.of(r).property(kv.first().uriValue(), kv.second()))).map(r -> (r instanceof REdge ? REdge.of(r) : RVertex.of(r)))))));

 instC(PROPERTIES_TID.dom(A).rng(A), lst(T(URI_TID), T(REC_TID)), (lhs, inst) -> objs(lhs.stream().map(Obj::<Rec>as).peek(r -> inst.arg(0).<Rec>as().elements().forEach(kv -> RElement.of(r).property(kv.first().uriValue(), kv.second()))).map(r -> (r instanceof REdge ? REdge.of(r) : RVertex.of(r)))))));

 */
    }
}
