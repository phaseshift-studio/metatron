package studio.phaseshift.metatron.lang.mgrph.mtron;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Graph;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.msys.Router;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.Rec;
import studio.phaseshift.metatron.util.Translator;

import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mgrph.mgrphInstSet.EDGE_TID;
import static studio.phaseshift.metatron.lang.mgrph.mgrphInstSet.VERTEX_TID;
import static studio.phaseshift.metatron.lang.mtron.mtronFluent.StartLess.from_;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TP3Translator implements Translator<Obj, Graph> {

    private final fURI root;

    public TP3Translator(final fURI root) {
        this.root = root;
    }

    @Override
    public Obj translate(final Graph graph) {
        graph.vertices().forEachRemaining(v -> {
            AtomicReference<Rec> out = new AtomicReference<>(rec());
            v.edges(Direction.OUT).forEachRemaining(e -> {
                Rec edge = rec(uri("label"), uri(e.label()),uri("OUT"), uri(this.root.extend("v" + e.outVertex().id())),uri("IN"), from_(uri(this.root.extend("v" + e.inVertex().id())))).tid(EDGE_TID);;
                out.set(out.get().put(uri(e.label()),out.get().at(uri(e.label())).orElse(objs()).append(edge)));
            });
            Rec vertex = rec(uri("label"), uri(v.label()), uri("OUT"), out.get()).vid(this.root.extend("v" + v.id())).tid(VERTEX_TID);
            Router.writeToSpace(RVertex.of(vertex));
        });
        return Router.readFromSpace(this.root.extend("+"));
    }

    @Override
    public Graph translate(Obj obj) {
        return null;
    }
}
