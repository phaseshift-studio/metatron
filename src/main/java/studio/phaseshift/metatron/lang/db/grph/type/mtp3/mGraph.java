package studio.phaseshift.metatron.lang.db.grph.type.mtp3;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.tinkerpop.gremlin.process.computer.GraphComputer;
import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedGraph;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.lang.db.grph.grphSpace;
import studio.phaseshift.metatron.lang.db.grph.inst.grphInstSet;
import studio.phaseshift.metatron.lang.db.grph.type.RVertex;
import studio.phaseshift.metatron.lang.db.kv.kvSpace;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.SPACE;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.db.grph.type.TP3Translator.LABEL;
import static studio.phaseshift.metatron.lang.db.grph.type.TP3Translator.PROPS;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Graph.OptIn(Graph.OptIn.SUITE_STRUCTURE_STANDARD)
//@Graph.OptIn(Graph.OptIn.SUITE_STRUCTURE_INTEGRATE)
@Graph.OptIn(Graph.OptIn.SUITE_PROCESS_EMBEDDED_STANDARD)
@Graph.OptIn(Graph.OptIn.SUITE_PROCESS_STANDARD)
@Graph.OptIn(Graph.OptIn.SUITE_PROCESS_LIMITED_STANDARD)
public class mGraph implements Graph, WrappedGraph<grphSpace> {

    protected final grphSpace space;
    protected final mVariables variables;
    protected long counter;

    protected Map<Obj, Obj> configurationToMap(final Configuration configuration) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        configuration.getKeys().forEachRemaining(key -> {
            map.put(uri(key), MObjFactory.of().create(configuration.getProperty(key)));
        });
        return map;
    }


    public static mGraph open(final Configuration configuration) {
        return new mGraph(configuration);
    }

    public mGraph(final Configuration configuration) {
        grphInstSet.create();
        final fURI spacevid = f(configuration.getProperty(SPACE).toString());
        final fURI pattern = f(configuration.getProperty(PATTERN).toString());
        final Obj s = Router.global().read(spacevid);
        if (s.isNoObj()) {
            this.space = new grphSpace(kvSpace.of(pattern, fURI.NULL), configurationToMap(configuration), pattern, spacevid);
        } else if (s instanceof grphSpace) {
            this.space = (grphSpace) s;
        } else {
            throw MTronException.of("obj is not a grph space: %s", s);
        }
        this.variables = new mVariables(this.space);
    }

    @Override
    public Vertex addVertex(final Object... keyValues) {
        ElementHelper.legalPropertyKeyValueArray(keyValues);
        final fURI vid = (fURI) ElementHelper.getIdValue(keyValues).orElse(f("tp" + counter++));
        final fURI label = f(ElementHelper.getLabelValue(keyValues).orElse(Vertex.DEFAULT_LABEL));
        final Rec props = rec();
        for (int i = 0; i < keyValues.length; i = i + 2) {
            if (keyValues[i] != T.id && keyValues[i] != T.label)
                props.jvm().put(uri(keyValues[i].toString()), MObjFactory.of().create(keyValues[i + 1]));
        }
        return this.space.write(vid, (Obj) mVertex.of(RVertex.of(rec(uri(PROPS), props, uri(LABEL), uri(label)).vid(vid)))).as();
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
        return 0 == vertexIds.length ?
                Router.global().read(this.space.pattern().retractPattern().extend("V/+")).stream().flatMap(RVertex::of).map(mVertex::of).map(m -> (Vertex) m).iterator() :
                Arrays.stream(vertexIds).map(vertexId -> Router.global().read(this.space.pattern().retractPattern().extend("V").extend(vertexId.toString()))).flatMap(RVertex::of).map(mVertex::of).map(m -> (Vertex) m).iterator();
    }

    @Override
    public Iterator<Edge> edges(final Object... edgeIds) {
        return IteratorUtil.of();
    }

    @Override
    public Transaction tx() {
        return null;
    }

    @Override
    public void close() throws Exception {
        this.space.close();
    }

    @Override
    public Variables variables() {
        return this.variables;
    }

    @Override
    public Configuration configuration() {
        final BaseConfiguration configuration = new BaseConfiguration();
        this.space.jvm().forEach((key, value) -> configuration.setProperty(key.uriValue().toString(), value));
        return configuration;
    }

    @Override
    public grphSpace getBaseGraph() {
        return this.space;
    }

}
