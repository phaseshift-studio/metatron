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

package studio.phaseshift.metatron.lang.db.grph.mtp3;

import org.apache.commons.configuration2.Configuration;
import org.apache.tinkerpop.gremlin.AbstractGraphProvider;
import org.apache.tinkerpop.gremlin.LoadGraphWith;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.io.IoRegistry;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.grph.tp3.space.tp3Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.lang.db.grph.type.mtp3.*;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;

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
        add(fURI.class);
    }};

    static {
        BootLoader.load(rec());
        // grphInstSet.create();
    }


    @Override
    public void clear(final Graph graph, final Configuration configuration) throws Exception {
        if (graph != null) {
        //    ((mGraph) graph).getBaseGraph().clear();
        } else {
            final Obj g = Router.global().read(f(configuration.getProperty(SPACE).toString()));
            if (!g.isNoObj())
                ((tp3Space) g).close();
        }
    }

    @Override
    public Map<String, Object> getBaseConfiguration(final String graphName, final Class<?> test, final String testMethodName, final LoadGraphWith.GraphData loadGraphWith) {
        Router.global().addSpace(memSpace.of(f("/mnt/#"), f("/sys/router/space/kv")));
        final Map<String, Object> config = new LinkedHashMap<>();
        config.put(Graph.GRAPH, f(mGraph.class.getCanonicalName()));
        config.put(SPACE, f("/mnt/test/mtp3"));
        config.put(PATTERN, f("/test/g/#"));
        config.put(NAME, f(graphName));
        config.put("guice.injector-source", f("studio.phaseshift.metatron.lang.db.grph.mtp3.mGraphFeatureTest$WorldInjectorSource"));
        config.put(IoRegistry.IO_REGISTRY, f(mIoRegistry.class.getCanonicalName()));
        return config;

    }

    @Override
    public Set<Class> getImplementations() {
        return IMPLEMENTATION;
    }
}
