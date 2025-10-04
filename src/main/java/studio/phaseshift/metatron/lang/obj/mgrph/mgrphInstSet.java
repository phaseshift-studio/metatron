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
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

package studio.phaseshift.metatron.lang.obj.mgrph;

import org.apache.tinkerpop.gremlin.structure.Direction;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.MInstSet;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Type;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Iterator;
import java.util.Set;

import static studio.phaseshift.metatron.lang.obj.mtron.MInst.instC;
import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;
import static studio.phaseshift.metatron.lang.obj.mtron.MObjs.objs;
import static studio.phaseshift.metatron.lang.obj.mtron.MStr.str;
import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.STR_TID;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.URI_TID;

public class mgrphInstSet extends MInstSet {

    public static final fURI MGRPH_TID = fURI.of("/mgrph");
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

    public mgrphInstSet(final fURI vid) {
        super(MGRPH_TID, vid);
        this.load();
    }

    @Override
    public Set<Type> types() {
        return Set.of(T(GRAPH_TID), T(ELEMENT_TID), T(VERTEX_TID), T(EDGE_TID), T(PROPERTY_TID));
    }


    private static String[] labelsAsUri(final Inst inst) {
        return inst.args().isEmpty() ?
                EMPTY_STRING_ARRAY :
                IteratorUtil.stream(inst.args().elements())
                        .flatMap(o -> IteratorUtil.stream(o.iterator()))
                        .map(Obj::uriValue)
                        .map(Object::toString)
                        .toArray(String[]::new);

    }

    private static Object[] idsAsUri(final Inst inst) {
        return inst.args().isEmpty() ?
                EMPTY_STRING_ARRAY :
                IteratorUtil.stream(inst.args().elements())
                        .flatMap(o -> IteratorUtil.stream(o.iterator()))
                        .map(Obj::value)
                        .map(o -> o instanceof fURI ? ((fURI) o).name() : o)
                        .toArray(Object[]::new);

    }

    public void load() {
        this.write(
                G_TID, instC(G_TID.dom(fURI.NONE.zero()).rng(GRAPH_TID), lst(T(URI_TID)), (lhs, inst) -> Router.global().read(inst.arg(0).uriValue())),
                V_TID, instC(V_TID.dom(GRAPH_TID).rng(VERTEX_TID.maybeSome()), lst(T(fURI.ALL.maybeSome())), (lhs, inst) -> objs(IteratorUtil.list((Iterator) lhs.<MGraph>as().vertices(idsAsUri(inst))))),
                E_TID, instC(E_TID.dom(GRAPH_TID).rng(EDGE_TID.maybeSome()), lst(T(fURI.ALL.maybeSome())), (lhs, inst) -> objs(IteratorUtil.list((Iterator) lhs.<MGraph>as().edges(idsAsUri(inst))))),
                OUTE_TID, instC(OUTE_TID.dom(VERTEX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) ->
                        objs(IteratorUtil.list((Iterator) lhs.<MVertex>as().edges(Direction.OUT, labelsAsUri(inst))))),
                OUT_TID, instC(OUT_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) ->
                        objs(IteratorUtil.list((Iterator) lhs.<MVertex>as().vertices(Direction.OUT, labelsAsUri(inst))))),
                OUT_TID, instC(OUT_TID.dom(VERTEX_TID.maybeSome()).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) ->
                        objs((Iterable) lhs.stream().flatMap(v -> IteratorUtil.stream(v.<MVertex>as().vertices(Direction.OUT, labelsAsUri(inst)))).toList())),
                INE_TID, instC(OUTE_TID.dom(VERTEX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) ->
                        objs(IteratorUtil.list((Iterator) lhs.<MVertex>as().edges(Direction.IN, labelsAsUri(inst))))),
                IN_TID, instC(OUT_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) ->
                        objs(IteratorUtil.list((Iterator) lhs.<MVertex>as().vertices(Direction.IN, labelsAsUri(inst))))),
                BOTHE_TID, instC(BOTHE_TID.dom(VERTEX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) ->
                        objs(IteratorUtil.list((Iterator) lhs.<MVertex>as().edges(Direction.BOTH, labelsAsUri(inst))))),
                BOTH_TID, instC(BOTH_TID.dom(VERTEX_TID).rng(VERTEX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) ->
                        objs(IteratorUtil.list((Iterator) lhs.<MVertex>as().vertices(Direction.BOTH, labelsAsUri(inst))))),
                OUTV_TID, instC(OUTV_TID.dom(EDGE_TID).rng(VERTEX_TID), lst(), (lhs, inst) -> (MVertex) lhs.<MEdge>as().vertices(Direction.OUT).next()),
                INV_TID, instC(INV_TID.dom(EDGE_TID).rng(VERTEX_TID), lst(), (lhs, inst) -> (MVertex) lhs.<MEdge>as().vertices(Direction.IN).next()),
                BOTHV_TID, instC(BOTHV_TID.dom(EDGE_TID).rng(VERTEX_TID.c("2")), lst(), (lhs, inst) -> objs(IteratorUtil.list((Iterator) lhs.<MEdge>as().vertices(Direction.BOTH)))),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                LABEL_TID, instC(LABEL_TID.dom(MGRPH_TID.extend(fURI.SINGLE)).rng(STR_TID), lst(), (lhs, inst) -> str(lhs.<MElement>as().label())),
                VALUES_TID, instC(VALUES_TID.dom(MGRPH_TID.extend(fURI.SINGLE)).rng(ANY_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(IteratorUtil.list((Iterator) lhs.<MElement>as().values(labelsAsUri(inst))))),
                PROPERTIES_TID, instC(PROPERTIES_TID.dom(MGRPH_TID.extend(fURI.SINGLE)).rng(ANY_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(IteratorUtil.list((Iterator) lhs.<MElement>as().properties(labelsAsUri(inst)))))
        );
    }

    @Override
    public mgrphInstSet clone(final Object value, final fURI tid, final fURI vid) {
        return this;
    }

}
