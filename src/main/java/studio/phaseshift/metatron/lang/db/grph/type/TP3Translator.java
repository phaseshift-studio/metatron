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

package studio.phaseshift.metatron.lang.db.grph.type;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Graph;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.util.Translator;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.auto;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.db.grph.inst.grphInstSet.EDGE_TID;
import static studio.phaseshift.metatron.lang.db.grph.inst.grphInstSet.VERTEX_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public record TP3Translator(Builder builder) implements Translator<Obj, Graph> {

    public static final String LABEL = "LABEL";
    public static final String PROPS = "PROPS";


    private Rec addProperties(final Rec elementRec, final Element element) {
        final AtomicReference<Rec> props = new AtomicReference<>(elementRec);
        element.properties().forEachRemaining(tpP -> props.set(props.get().put(uri(tpP.key()), MObjFactory.of().create(tpP.value()))));
        return props.get();
    }

    private Rec createEdge(final Edge tpEdge) {
        final Rec props = addProperties(rec(), tpEdge);
        return rec(Map.of(uri(LABEL), uri(tpEdge.label()),
                        uri(PROPS), props.isEmpty() ? noobj() : props,
                        uri(Direction.OUT.name()), auto(this.builder.root.extend("V").extend(tpEdge.outVertex().id().toString())),
                        uri(Direction.IN.name()), auto(this.builder.root.extend("V").extend(tpEdge.inVertex().id().toString()))),
                EDGE_TID, fURI.fnull);
        //this.builder.root.extend("E").extend(tpEdge.id().toString()));
    }

    @Override
    public Obj translate(final Graph graph) {
        graph.vertices().forEachRemaining(tpV -> {
            final AtomicReference<Rec> out = new AtomicReference<>(rec());
            tpV.edges(Direction.OUT).forEachRemaining(tpE -> out.set(out.get().put(uri(tpE.label()), out.get().at(uri(tpE.label())).append(createEdge(tpE)))));
            final AtomicReference<Rec> in = new AtomicReference<>(rec());
            tpV.edges(Direction.IN).forEachRemaining(tpE -> in.set(in.get().put(uri(tpE.label()), in.get().at(uri(tpE.label())).append(createEdge(tpE)))));
            final Rec props = addProperties(rec(), tpV);
            rec(Map.of(uri(LABEL), uri(tpV.label()),
                            uri(PROPS), props.isEmpty() ? noobj() : props,
                            uri(Direction.OUT.name()), out.get().isEmpty() ? noobj() : out.get(),
                            uri(Direction.IN.name()), in.get().isEmpty() ? noobj() : in.get()),
                    VERTEX_TID,
                    this.builder.root.extend("V").extend(tpV.id().toString()));
        });
        /*
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
         */
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
