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

package studio.phaseshift.metatron.isa.grph.tp3;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.tinkerpop.gremlin.process.computer.GraphComputer;
import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.gremlin.structure.io.Io;
import org.apache.tinkerpop.gremlin.structure.io.gryo.GryoMapper;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedGraph;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.grph.space.graphSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.SPACE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Graph.OptIn(Graph.OptIn.SUITE_STRUCTURE_STANDARD)
//@Graph.OptIn(Graph.OptIn.SUITE_STRUCTURE_INTEGRATE)
@Graph.OptIn(Graph.OptIn.SUITE_PROCESS_EMBEDDED_STANDARD)
@Graph.OptIn(Graph.OptIn.SUITE_PROCESS_STANDARD)
@Graph.OptIn(Graph.OptIn.SUITE_PROCESS_LIMITED_STANDARD)
@Graph.OptOut(
        test = "org.apache.tinkerpop.gremlin.process.traversal.step.map.MatchTest",
        method = "*",
        reason = "avoiding grateful dead tests for now")
@Graph.OptOut(
        test = "org.apache.tinkerpop.gremlin.process.traversal.step.map.ProfileTest",
        method = "*",
        reason = "avoiding grateful dead tests for now")
@Graph.OptOut(
        test = "org.apache.tinkerpop.gremlin.process.traversal.step.filter.WhereTest",
        method = "g_VX1X_repeatXbothEXcreatedX_whereXwithoutXeXX_aggregateXeX_otherVX_emit_path",
        reason = "avoiding grateful dead tests for now")
public class mGraph implements Graph, WrappedGraph<graphSpace> {

    protected final graphSpace space;
    protected final mVariables variables;
    protected long counter;
    protected final fURI baseURI;
    protected final fURI baseVertexURI;
    protected final fURI baseEdgeURI;

    protected Map<Obj, Obj> configurationToMap(final Configuration configuration) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        configuration.getKeys().forEachRemaining(key -> {
            map.put(uri(key), MObjFactory.of().createOrFail(configuration.getProperty(key)));
        });
        return map;
    }


    public static mGraph open(final Configuration configuration) {
        return new mGraph(configuration);
    }

    public mGraph(final Configuration configuration) {
        //grphInstSet.create();
        if (!configuration.containsKey(SPACE) || !configuration.containsKey(PATTERN))
            throw MTronException.of("configuration requires space and pattern properties");
        final fURI spacevid = f(configuration.getProperty(SPACE).toString());
        final fURI pattern = f(configuration.getProperty(PATTERN).toString());
        final Obj s = Router.global().read(spacevid);
        if (s.isNoObj()) {
            this.space = graphSpace.of(rec(configurationToMap(configuration)).plus(rec(uri(PATTERN), uri(pattern))), spacevid);
        } else if (s instanceof graphSpace) {
            this.space = (graphSpace) s;
        } else {
            throw MTronException.of("obj is not a grph space: %s", s);
        }
        //Router.global().addSpace(this.space);
        this.variables = new mVariables(this.space);
        this.baseURI = this.getBaseGraph().pattern().retractPattern();
        this.baseVertexURI = this.baseURI.extend("V");
        this.baseEdgeURI = this.baseURI.extend("E");
    }

    @Override
    public <I extends Io> I io(final Io.Builder<I> builder) {
        GryoMapper.build().addRegistry(mIoRegistry.instance()).create();
        return (I) builder.graph(this).onMapper(mapper -> mapper.addRegistry(mIoRegistry.instance())).create();

    }

    protected final fURI makeVertexID(final Object id) {
        if (id instanceof Vertex)
            return f(((Vertex) id).id().toString());
        final fURI temp = id instanceof fURI ? (fURI) id : (id instanceof Uri ? ((Uri) id).uriValue() : f(Highlighter.unformat(id.toString())));
        return temp.hasPrefix(this.baseVertexURI.toString()) ? temp : this.baseVertexURI.extend(temp);
    }

    protected final fURI makeEdgeID(final Object id) {
        if (id instanceof Edge)
            return f(((Edge) id).id().toString());
        final fURI temp = id instanceof fURI ? (fURI) id : (id instanceof Uri ? ((Uri) id).uriValue() : f(Highlighter.unformat(id.toString())));
        return temp.hasPrefix(this.baseEdgeURI.toString()) ? temp : this.baseEdgeURI.extend(temp);
    }

    @Override
    public Vertex addVertex(final Object... keyValues) {
        ElementHelper.legalPropertyKeyValueArray(keyValues);
        final fURI vid = ElementHelper.getIdValue(keyValues).map(this::makeVertexID).orElseGet(() -> makeVertexID("" + counter++));
        final fURI tid = f(ElementHelper.getLabelValue(keyValues).orElse(Vertex.DEFAULT_LABEL));
        final Map<Obj, Obj> props = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i = i + 2) {
            if (keyValues[i] != T.id && keyValues[i] != T.label) {
                final Uri key = uri(keyValues[i].toString());
                final Obj value = MObjFactory.of().toObj(keyValues[i + 1]);
                props.put(key, value);
            }
        }
        return mVertex.of(this, rec(props, tid, vid));
    }

    @Override
    public <C extends GraphComputer> C compute(final Class<C> graphComputerClass) throws IllegalArgumentException {
        throw new UnsupportedOperationException();
    }

    @Override
    public GraphComputer compute() throws IllegalArgumentException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Vertex> vertices(final Object... vertexIds) {
        return this.getBaseGraph().read("V/+").stream().map(v -> (Vertex) mVertex.of(this, v.asRec())).iterator();
    }

    @Override
    public Iterator<Edge> edges(final Object... edgeIds) {
        return this.getBaseGraph().read("E/+").stream().map(e -> (Edge) mEdge.of(this, e.asRec())).iterator();
    }

    @Override
    public Transaction tx() {
        return Transaction.NO_OP;
    }

    @Override
    public void close() throws Exception {
        this.getBaseGraph().close();
    }

    @Override
    public Variables variables() {
        return this.variables;
    }

    @Override
    public Configuration configuration() {
        final BaseConfiguration configuration = new BaseConfiguration();
        this.getBaseGraph().jvm().forEach((key, value) -> configuration.setProperty(key.uriValue().toString(), value));
        return configuration;
    }

    @Override
    public Features features() {
        return new mFeatures(this);
    }

    @Override
    public graphSpace getBaseGraph() {
        return this.space;
    }

}
