package studio.phaseshift.metatron.lang.db.grph.mtron;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Graph;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.core.m.type.Inst;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Uri;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.util.Translator;

import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.lang.db.grph.grphInstSet.EDGE_TID;
import static studio.phaseshift.metatron.lang.db.grph.grphInstSet.VERTEX_TID;
import static studio.phaseshift.metatron.lang.core.m.mtronInstSet.FROM_TID;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TP3Translator implements Translator<Obj, Graph> {

    public static final String LABEL = "LABEL";
    public static final String PROPS = "PROPS";
    protected final Builder builder;

    public TP3Translator(final Builder builder) {
        this.builder = builder;
    }


    private Rec addProperties(final Rec elementRec, final Element element) {
        final AtomicReference<Rec> props = new AtomicReference<>(elementRec);
        element.properties().forEachRemaining(tpP -> props.set(props.get().put(uri(tpP.key()), MObjFactory.of().create(tpP.value()))));
        return props.get();
    }

    private Inst createPointer(final fURI dom, final fURI rng, final Uri vid) {
        return instB(FROM_TID.dom(dom).rng(rng), lst(vid));
    }

    @Override
    public Obj translate(final Graph graph) {
        graph.vertices().forEachRemaining(tpV -> {
            AtomicReference<Rec> out = new AtomicReference<>(rec());
            tpV.edges(Direction.OUT).forEachRemaining(tpE -> {
                final Rec props = addProperties(rec(), tpE);
                final Rec edge = rec(
                        uri(LABEL), uri(tpE.label()),
                        uri(PROPS), props.isEmpty() ? noobj() : props,
                        uri(Direction.OUT.name()), createPointer(EDGE_TID, VERTEX_TID, uri(this.builder.root.extend("V").extend(tpE.outVertex().id().toString()))),
                        uri(Direction.IN.name()), createPointer(EDGE_TID, VERTEX_TID, uri(this.builder.root.extend("V").extend(tpE.inVertex().id().toString())))).
                        tid(EDGE_TID);
                out.set(out.get().put(uri(tpE.label()), out.get().at(uri(tpE.label())).orElse(objs()).append(edge)));
            });
            final Rec props = addProperties(rec(), tpV);
            final Rec vertex = rec(
                    uri(LABEL), uri(tpV.label()),
                    uri(PROPS), props.isEmpty() ? noobj() : props,
                    uri(Direction.OUT.name()), out.get()).
                    tid(VERTEX_TID);
            Router.writeToSpace(this.builder.root.extend("V").extend(tpV.id().toString()),RVertex.of(vertex));
        });
        return Router.readFromSpace(this.builder.root.extend("+"));
    }

    @Override
    public Graph translate(final Obj obj) {
        throw new UnsupportedOperationException();
    }

    public static class Builder {
        final fURI root;
        boolean pointerToProps = false;
        boolean pointerToAdjacent = true;
        boolean pointerToIncident = false;

        private Builder(final fURI root) {
            this.root = root;
        }

        public static Builder of(final fURI root) {
            return new Builder(root);
        }

        public Builder pointerToProps(final boolean b) {
            this.pointerToProps = b;
            return this;
        }

        public Builder pointerToAdjacent(final boolean b) {
            this.pointerToAdjacent = b;
            return this;
        }

        public Builder pointerToIncident(final boolean b) {
            this.pointerToIncident = b;
            return this;
        }

        public TP3Translator create() {
            return new TP3Translator(this);
        }
    }
}
