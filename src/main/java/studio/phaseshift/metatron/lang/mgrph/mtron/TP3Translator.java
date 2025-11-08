package studio.phaseshift.metatron.lang.mgrph.mtron;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Graph;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.msys.Router;
import studio.phaseshift.metatron.lang.mtron.type.Inst;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.Rec;
import studio.phaseshift.metatron.lang.mtron.type.Uri;
import studio.phaseshift.metatron.lang.mtron.type.impl.MObjFactory;
import studio.phaseshift.metatron.util.Translator;

import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.lang.mgrph.grphInstSet.EDGE_TID;
import static studio.phaseshift.metatron.lang.mgrph.grphInstSet.VERTEX_TID;
import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.FROM_TID;
import static studio.phaseshift.metatron.lang.mtron.type.NoObj.noobj;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MInst.instB;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;

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
        element.properties().forEachRemaining(vp -> props.set(props.get().put(uri(vp.key()), MObjFactory.of().create(vp.value()))));
        return props.get();
    }

    private Inst createPointer(final fURI dom, final fURI rng, final Uri vid) {
        return instB(FROM_TID.dom(dom).rng(rng), lst(vid));
    }

    @Override
    public Obj translate(final Graph graph) {
        graph.vertices().forEachRemaining(v -> {
            AtomicReference<Rec> out = new AtomicReference<>(rec());
            v.edges(Direction.OUT).forEachRemaining(e -> {
                final Rec props = addProperties(rec(), e);
                final Rec edge = rec(
                        uri(LABEL), uri(e.label()),
                        uri(PROPS), props.isEmpty() ? noobj() : props,
                        uri(Direction.OUT.name()), createPointer(EDGE_TID, VERTEX_TID, uri(this.builder.root.extend("v" + e.outVertex().id()))),
                        uri(Direction.IN.name()), createPointer(EDGE_TID, VERTEX_TID, uri(this.builder.root.extend("v" + e.inVertex().id())))).
                        tid(EDGE_TID);
                out.set(out.get().put(uri(e.label()), out.get().at(uri(e.label())).orElse(objs()).append(edge)));
            });
            final Rec props = addProperties(rec(), v);
            final Rec vertex = rec(
                    uri(LABEL), uri(v.label()),
                    uri(PROPS), props.isEmpty() ? noobj() : props,
                    uri(Direction.OUT.name()), out.get()).
                    tid(VERTEX_TID);
            Router.writeToSpace(this.builder.root.extend("v" + v.id()),RVertex.of(vertex));
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
