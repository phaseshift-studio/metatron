/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.lang.mgrph;

import org.apache.tinkerpop.gremlin.structure.Direction;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mgrph.mtron.REdge;
import studio.phaseshift.metatron.lang.mgrph.mtron.RElement;
import studio.phaseshift.metatron.lang.mgrph.mtron.RVertex;
import studio.phaseshift.metatron.lang.msys.Router;
import studio.phaseshift.metatron.lang.mtron.type.*;
import studio.phaseshift.metatron.lang.mtron.type.impl.MInstSet;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mgrph.mtron.TP3Translator.LABEL;
import static studio.phaseshift.metatron.lang.mgrph.mtron.TP3Translator.PROPS;
import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.*;
import static studio.phaseshift.metatron.lang.mtron.type.NoObj.noobj;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MType.T;

public class grphInstSet extends MInstSet {

    public static final fURI MGRPH_TID = f("/grph");
    public static final fURI GRAPH_TID = MGRPH_TID.extend("graph");
    public static final fURI ELEMENT_TID = MGRPH_TID.extend("element");
    public static final fURI VERTEX_TID = MGRPH_TID.extend("vertex");
    public static final fURI VERTEX_PROPERTY_TID = MGRPH_TID.extend("vp");
    public static final fURI PROPERTY_TID = MGRPH_TID.extend("p");
    public static final fURI EDGE_TID = MGRPH_TID.extend("edge");

    public static final fURI INST_TID = MGRPH_TID.extend("inst");
    public static final fURI G_TID = INST_TID.extend("g");
    public static final fURI V_TID = INST_TID.extend("V");
    public static final fURI E_TID = INST_TID.extend("E");
    public static final fURI OUT_TID = INST_TID.extend("out");
    public static final fURI OUTE_TID = INST_TID.extend("outE");
    public static final fURI IN_TID = INST_TID.extend("_in");
    public static final fURI INE_TID = INST_TID.extend("inE");
    public static final fURI BOTH_TID = INST_TID.extend("both");
    public static final fURI BOTHE_TID = INST_TID.extend("bothE");
    public static final fURI OUTV_TID = INST_TID.extend("outV");
    public static final fURI INV_TID = INST_TID.extend("inV");
    public static final fURI BOTHV_TID = INST_TID.extend("bothV");
    public static final fURI VALUES_TID = INST_TID.extend("values");
    public static final fURI PROPERTIES_TID = INST_TID.extend("properties");
    public static final fURI LABEL_TID = INST_TID.extend("label");
    public static final fURI HAS_TID = INST_TID.extend("has");

    public grphInstSet(final fURI vid) {
        super(MGRPH_TID, vid);
        // this.types().forEach(t -> Router.global().registerRewrite(f(t.tid().name()), t.tid()));
    }

    public static grphInstSet create() {
        return new grphInstSet(fURI.NULL);
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
        return Set.of(T(GRAPH_TID), T(ELEMENT_TID), T(VERTEX_TID), T(EDGE_TID), T(PROPERTY_TID));
    }

    @Override
    public Set<Inst> rewrites() {
        return new HashSet<>();
    }

    @Override
    public Set<Inst> insts() {
        return new LinkedHashSet<>(List.of(
                instC(V_TID.dom(NOOBJ_TID.zero()).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(inst.arg(0).stream().flatMap(u -> Router.readFromSpace(u.uriValue()).stream()))),
                instC(BOTH_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.<RVertex>as().vertices(Direction.BOTH, inst.args().as()).map(Obj::as))),
                instC(BOTHE_TID.dom(VERTEX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.<RVertex>as().edges(Direction.BOTH, inst.args().as()).map(Obj::as))),
                instC(OUT_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.<RVertex>as().vertices(Direction.OUT, inst.args().as()).map(Obj::as))),
                instC(OUTE_TID.dom(VERTEX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.<RVertex>as().edges(Direction.OUT, inst.args().as()).map(Obj::as))),
                instC(IN_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.<RVertex>as().vertices(Direction.IN, inst.args().as()).map(Obj::as))),
                instC(INE_TID.dom(VERTEX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.<RVertex>as().edges(Direction.IN, inst.args().as()).map(Obj::as))),
                instC(INV_TID.dom(EDGE_TID).rng(VERTEX_TID), lst(), (lhs, inst) -> objs(lhs.<REdge>as().vertices(Direction.IN).map(Obj::as))),
                instC(OUTV_TID.dom(EDGE_TID).rng(VERTEX_TID), lst(), (lhs, inst) -> objs(lhs.<REdge>as().vertices(Direction.OUT).map(Obj::as))),
                instC(BOTHV_TID.dom(EDGE_TID).rng(VERTEX_TID.c("2")), lst(), (lhs, inst) -> objs(lhs.<REdge>as().vertices(Direction.BOTH).map(Obj::as))),
                instC(LABEL_TID.dom(REC_TID).rng(URI_TID), lst(), (lhs, inst) -> lhs.<RElement>as().at(LABEL)),
                instC(HAS_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybe()), lst(T(URI_TID), T(BOOL_TID)), (lhs, inst) -> inst.arg(1).apply(lhs.<RElement>as().<Rec>at(PROPS).at(inst.arg(0))).<Bool>as().boolValue() ? lhs : noobj()),
                instC(HAS_TID.dom(EDGE_TID).rng(EDGE_TID.maybe()), lst(T(URI_TID), T(BOOL_TID)), (lhs, inst) -> inst.arg(1).apply(lhs.<RElement>as().<Rec>at(PROPS).at(inst.arg(0))).<Bool>as().boolValue() ? lhs : noobj()),
                instC(HAS_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybe()), lst(T(URI_TID)), (lhs, inst) -> lhs.<RElement>as().<Rec>at(PROPS).has(inst.arg(0)) ? lhs : noobj()),
                instC(HAS_TID.dom(EDGE_TID).rng(EDGE_TID.maybe()), lst(T(URI_TID)), (lhs, inst) -> lhs.<RElement>as().<Rec>at(PROPS).has(inst.arg(0)) ? lhs : noobj()),
                // TODO: why does values() and properties() require streaming objs when the doms are unit
                instC(VALUES_TID.dom(REC_TID).rng(ALL.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.stream().flatMap(r -> r.<RElement>as().values(inst.arg(0))).map(Obj::as))),
                instC(PROPERTIES_TID.dom(REC_TID).rng(REL_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.stream().flatMap(r -> r.<RElement>as().properties(inst.arg(0))).map(Obj::as)))));
        //G_TID, instC(G_TID.dom(fURI.NONE.zero()).rng(GRAPH_TID), lst(T(URI_TID)), (lhs, inst) -> Router.global().read(inst.arg(0).uriValue())),
        /*return Stream.of(
                instC(V_TID.dom(GRAPH_TID).rng(VERTEX_TID.maybeSome()), lst(T(ALL.maybeSome())), (lhs, inst) -> objs(IteratorUtil.list((Iterator) lhs.<MGraph>as().vertices(idsAsUri(inst))))),
                instC(E_TID.dom(GRAPH_TID).rng(EDGE_TID.maybeSome()), lst(T(ALL.maybeSome())), (lhs, inst) -> objs(IteratorUtil.list((Iterator) lhs.<MGraph>as().edges(idsAsUri(inst))))),
                instC(OUTE_TID.dom(VERTEX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) ->
                        objs(IteratorUtil.list((Iterator) lhs.<MVertex>as().edges(Direction.OUT, labelsAsUri(inst))))),
                instC(OUT_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) ->
                        objs(lhs.stream().flatMap(v -> IteratorUtil.stream(v.<MVertex>as().vertices(Direction.OUT, labelsAsUri(inst)))).map(o -> (Obj) o))),
                // OUT_TID, instC(OUT_TID.dom(VERTEX_TID.maybeSome()).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) ->
                //        objs((Iterable) lhs.stream().flatMap(v -> IteratorUtil.stream(v.<MVertex>as().vertices(Direction.OUT, labelsAsUri(inst)))).toList())),
                instC(OUTE_TID.dom(VERTEX_TID.maybeSome()).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) ->
                        objs(lhs.stream().flatMap(v -> IteratorUtil.stream(v.<MVertex>as().edges(Direction.IN, labelsAsUri(inst)))).map(o -> (Obj) o))),
                instC(OUT_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) ->
                        objs(IteratorUtil.list((Iterator) lhs.<MVertex>as().vertices(Direction.IN, labelsAsUri(inst))))),
                instC(BOTHE_TID.dom(VERTEX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) ->
                        objs(IteratorUtil.list((Iterator) lhs.<MVertex>as().edges(Direction.BOTH, labelsAsUri(inst))))),
                instC(BOTH_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) ->
                        objs(IteratorUtil.list((Iterator) lhs.<MVertex>as().vertices(Direction.BOTH, labelsAsUri(inst))))),
                instC(BOTH_TID.dom(VERTEX_TID.maybeSome()).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) ->
                        objs(lhs.stream().flatMap(v -> (IteratorUtil.stream(v.<MVertex>as().vertices(Direction.BOTH, labelsAsUri(inst))))).map(o -> (Obj) o).toList())),
                instC(OUTV_TID.dom(EDGE_TID).rng(VERTEX_TID), lst(), (lhs, inst) -> (MVertex) lhs.<MEdge>as().vertices(Direction.OUT).next()),
                instC(INV_TID.dom(EDGE_TID).rng(VERTEX_TID), lst(), (lhs, inst) -> (MVertex) lhs.<MEdge>as().vertices(Direction.IN).next()),
                instC(BOTHV_TID.dom(EDGE_TID).rng(VERTEX_TID.c("2")), lst(), (lhs, inst) -> objs(IteratorUtil.list((Iterator) lhs.<MEdge>as().vertices(Direction.BOTH)))),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                instC(LABEL_TID.dom(MGRPH_TID.extend(fURI.SINGLE)).rng(STR_TID), lst(), (lhs, inst) -> str(lhs.<MElement>as().label())),
                //instC(VALUES_TID.dom(GRPH_TID.extend(fURI.SINGLE)).rng(ALL.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(IteratorUtil.list((Iterator) lhs.<MElement>as().values(labelsAsUri(inst))))),
                instC(VALUES_TID.dom(MGRPH_TID.extend(fURI.SINGLE)).rng(ALL.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(lhs.stream().flatMap(o -> IteratorUtil.stream((Iterator) o.<MElement>as().values(labelsAsUri(inst)))))),
                instC(PROPERTIES_TID.dom(MGRPH_TID.extend(fURI.SINGLE)).rng(ALL.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(IteratorUtil.list((Iterator) lhs.<MElement>as().properties(labelsAsUri(inst)))))
        ).collect(Collectors.toSet());*/
    }
}
