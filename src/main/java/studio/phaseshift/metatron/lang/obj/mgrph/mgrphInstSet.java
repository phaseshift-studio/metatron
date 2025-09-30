package studio.phaseshift.metatron.lang.obj.mgrph;

import org.apache.tinkerpop.gremlin.structure.Direction;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.*;
import studio.phaseshift.metatron.lang.obj.mtron.MInst;
import studio.phaseshift.metatron.lang.obj.MInstSet;
import studio.phaseshift.metatron.lang.obj.mtron.MLst;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.*;

import static studio.phaseshift.metatron.lang.obj.mtron.MInst.instC;
import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;
import static studio.phaseshift.metatron.lang.obj.mtron.MObjs.objs;
import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.ID_TID;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.URI_TID;

public class mgrphInstSet extends MInstSet {

    public static final fURI MGRPH_TID = fURI.of("/mgrph");
    public static final fURI GRAPH_TID = MGRPH_TID.extend("graph");
    public static final fURI ELEMENT_TID = MGRPH_TID.extend("element");
    public static final fURI VERTEX_TID = MGRPH_TID.extend("vertex");
    public static final fURI EDGE_TID = MGRPH_TID.extend("edge");
    public static final fURI PROPERTY_TID = MGRPH_TID.extend("property");

    public static final fURI INST_TID = MGRPH_TID.extend("inst");
    public static final fURI G_TID = INST_TID.extend("g");
    public static final fURI V_TID = INST_TID.extend("V");
    public static final fURI E_TID = INST_TID.extend("E");
    public static final fURI OUT_TID = INST_TID.extend("out");
    public static final fURI OUTE_TID = INST_TID.extend("outE");
    public static final fURI IN_TID = INST_TID.extend("in");
    public static final fURI INE_TID = INST_TID.extend("inE");
    public static final fURI VALUES_TID = INST_TID.extend("values");

    private static final Lst NO_ARGS__ = MLst.of();
    private static final Map<fURI, Map<fURI, Set<Inst>>> SYMBOL_TABLE = new LinkedHashMap<>();
    private final Map<fURI, Obj> OBJ_TABLE = new LinkedHashMap<>();
    private static final Inst ID__ = MInst.instB(ID_TID, MLst.of());
    public static final fURI ANY_TID = fURI.of("#");

    public mgrphInstSet(final fURI vid) {
        super(MGRPH_TID, vid);
        this.load();
    }

    @Override
    public Objs types() {
        return objs(T(GRAPH_TID), T(ELEMENT_TID), T(VERTEX_TID), T(EDGE_TID), T(PROPERTY_TID));
    }

    public void load() {
        this.write(
                V_TID, instC(V_TID.dom(GRAPH_TID).rng(VERTEX_TID.any()), lst(T(URI_TID.any())), (lhs, inst) -> objs(IteratorUtil.list(MVertex.makeVertices(lhs.<MGraph>as().vertices())))),
                E_TID, instC(V_TID.dom(MGRPH_TID).rng(VERTEX_TID.any()), lst(T(URI_TID.any())), (lhs, inst) -> objs(() -> MEdge.makeEdges(lhs.<MGraph>as().edges()))),
                OUT_TID, instC(OUT_TID.dom(VERTEX_TID).rng(VERTEX_TID.any()), lst(T(URI_TID.any())), (lhs, inst) ->
                        inst.args().has(0) ?
                                objs(IteratorUtil.list((Iterator) lhs.<MVertex>as().vertices(Direction.OUT, (String[]) inst.args().stream().map(Object::toString).toArray()))) :
                                objs(IteratorUtil.list((Iterator) lhs.<MVertex>as().vertices(Direction.OUT)))),
                OUTE_TID, instC(OUT_TID.dom(VERTEX_TID).rng(VERTEX_TID.any()), lst(T(URI_TID.any())), (lhs, inst) -> objs(() -> MEdge.makeEdges(lhs.<MVertex>as().edges(Direction.OUT, (String[]) inst.args().stream().map(Object::toString).toArray()))))
        );
    }

    @Override
    public mgrphInstSet clone(final Object value, final fURI tid, final fURI vid) {
        return this;
    }
}
