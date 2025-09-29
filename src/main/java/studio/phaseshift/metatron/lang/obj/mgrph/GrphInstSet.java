package studio.phaseshift.metatron.lang.obj.mgrph;

import org.apache.tinkerpop.gremlin.structure.Direction;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.*;
import studio.phaseshift.metatron.lang.obj.mtron.MInst;
import studio.phaseshift.metatron.lang.obj.mtron.MLst;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.mem.MSpace;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.*;

import static studio.phaseshift.metatron.lang.obj.mtron.MInst.instC;
import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.ID_TID;
import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.URI_TID;
import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;
import static studio.phaseshift.metatron.lang.obj.mtron.MObjs.objs;
import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;

public class GrphInstSet extends MSpace implements InstSet {

    public static final fURI GRPH_TID = fURI.of("/grph");
    public static final fURI GRAPH_TID = GRPH_TID.extend("graph");
    public static final fURI ELEMENT_TID = GRPH_TID.extend("element");
    public static final fURI VERTEX_TID = GRPH_TID.extend("vertex");
    public static final fURI EDGE_TID = GRPH_TID.extend("edge");
    public static final fURI PROPERTY_TID = GRPH_TID.extend("property");

    public static final fURI INST_TID = GRPH_TID.extend("inst");
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

    public GrphInstSet(final fURI pattern, final fURI vid) {
        super(pattern, GRPH_TID, vid);
    }

    @Override
    public Objs types() {
        return objs(T(GRAPH_TID), T(ELEMENT_TID), T(VERTEX_TID), T(EDGE_TID), T(PROPERTY_TID));
    }

    public void load() {
        //types().forEach(t -> this.write(t.tid(), t));
        this.write(
                V_TID, instC(V_TID.dom(GRAPH_TID).rng(VERTEX_TID.any()), lst(T(URI_TID.any())), (lhs, inst) -> objs(IteratorUtil.list(MVertex.makeVertices(lhs.<MGraph>as().vertices())))),
                E_TID, instC(V_TID.dom(GRPH_TID).rng(VERTEX_TID.any()), lst(T(URI_TID.any())), (lhs, inst) -> objs(() -> MEdge.makeEdges(lhs.<MGraph>as().edges()))),
                OUT_TID, instC(OUT_TID.dom(VERTEX_TID).rng(VERTEX_TID.any()), lst(T(URI_TID.maybe())), (lhs, inst) ->
                        inst.args().has(0) ?
                                objs(IteratorUtil.list((Iterator) lhs.<MVertex>as().vertices(Direction.OUT, (String[]) inst.args().stream().map(Object::toString).toArray()))) :
                                objs(IteratorUtil.list((Iterator) lhs.<MVertex>as().vertices(Direction.OUT)))),
                OUTE_TID, instC(OUT_TID.dom(VERTEX_TID).rng(VERTEX_TID.any()), lst(T(URI_TID.any())), (lhs, inst) -> objs(() -> MEdge.makeEdges(lhs.<MVertex>as().edges(Direction.OUT, (String[]) inst.args().stream().map(Object::toString).toArray()))))
        );
        //this.types().forEach(t -> OBJ_TABLE.put(t.tid(), t));
    }

    @Override
    public Obj read(final fURI vid) {
        final fURI bigvid = vid.big();
        final Obj result = ObjUtil.oneNoneOrAll(SYMBOL_TABLE.entrySet()
                .stream()
                .filter(kv -> kv.getKey().matches(bigvid.basePath()))
                .flatMap(kv -> kv.getValue().entrySet().stream())
                .filter(kv2 -> !bigvid.hasDom() || kv2.getKey().bimatches(bigvid.dom()))
                .map(Map.Entry::getValue)
                .flatMap(Set::stream)
                .filter(i -> !bigvid.hasRng() || i.rng().tid().bimatches(bigvid.rng()))
                .map(i -> (Obj) i));
        return result.isNoObj() ?
                ObjUtil.oneNoneOrAll(OBJ_TABLE.entrySet().stream().filter(kv -> kv.getKey().matches(bigvid)).map(Map.Entry::getValue).iterator()) :
                result;
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        if (obj.isInst()) {
            Router.global().registerRewrite(fURI.of(vid.name()), vid);
            final Inst inst = obj.as();
            SYMBOL_TABLE
                    .computeIfAbsent(inst.tid().basePath(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(inst.tid().dom(), k -> new LinkedHashSet<>()).add(inst);
        } else {
            OBJ_TABLE.put(vid, obj);
        }
        return obj;
    }

    @Override
    public Map<fURI, Map<fURI, Set<Inst>>> value() {
        return SYMBOL_TABLE;
    }


}
