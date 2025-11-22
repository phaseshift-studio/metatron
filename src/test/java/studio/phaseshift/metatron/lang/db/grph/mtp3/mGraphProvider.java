package studio.phaseshift.metatron.lang.db.grph.mtp3;

import org.apache.commons.configuration2.Configuration;
import org.apache.tinkerpop.gremlin.AbstractGraphProvider;
import org.apache.tinkerpop.gremlin.GraphProvider;
import org.apache.tinkerpop.gremlin.LoadGraphWith;
import org.apache.tinkerpop.gremlin.structure.Graph;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.db.grph.grphSpace;
import studio.phaseshift.metatron.lang.db.grph.type.mtp3.*;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.SPACE;
import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mGraphProvider extends AbstractGraphProvider {

    protected static final GraphittyLogger LOG = Graphitty.log(mGraphProvider.class);

    private static final Set<Class> IMPLEMENTATION = new HashSet<>() {{
        add(mEdge.class);
        add(mElement.class);
        add(mGraph.class);
        add(mVariables.class);
        add(mProperty.class);
        add(mVertex.class);
        add(mVertexProperty.class);
    }};

    static {
        BootLoader.load(rec());
    }


    @Override
    public void clear(final Graph graph, final Configuration configuration) throws Exception {
        if (graph != null) {
            ((mGraph) graph).getBaseGraph().clear();
        } else {
            final Obj g = Router.global().read(f(configuration.getProperty(SPACE).toString()));
            if (!g.isNoObj())
                ((grphSpace) g).clear();
        }
    }

    @Override
    public Map<String, Object> getBaseConfiguration(final String graphName, final Class<?> test, final String testMethodName, final LoadGraphWith.GraphData loadGraphWith) {
        final Map<String, Object> config = new LinkedHashMap<>();
        config.put("gremlin.graph", f(mGraph.class.getCanonicalName()));
        config.put(SPACE, f("/mnt/test/mtp3"));
        config.put(PATTERN, f("/test/g/#"));
        config.put(NAME, f(graphName));
        return config;

    }

    @Override
    public Set<Class> getImplementations() {
        return IMPLEMENTATION;
    }
}
