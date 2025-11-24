package studio.phaseshift.metatron.lang.db.grph.mtp3;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import io.cucumber.guice.CucumberModules;
import io.cucumber.guice.GuiceFactory;
import io.cucumber.guice.InjectorSource;
import io.cucumber.java.Scenario;
import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.apache.commons.configuration2.MapConfiguration;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.tinkerpop.gremlin.LoadGraphWith;
import org.apache.tinkerpop.gremlin.TestHelper;
import org.apache.tinkerpop.gremlin.features.World;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.io.IoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.gryo.GryoResourceAccess;
import org.junit.jupiter.api.Disabled;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.lang.db.grph.type.mtp3.mGraph;
import studio.phaseshift.metatron.lang.db.grph.type.mtp3.mIoRegistry;
import studio.phaseshift.metatron.lang.db.kv.kvSpace;
import studio.phaseshift.metatron.lang.sys.router.Router;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.f;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Disabled
//@RunWith(Cucumber.class)
@CucumberOptions(
        tags = "not @GraphComputerOnly and not @AllowNullPropertyValues",
        glue = {"org.apache.tinkerpop.gremlin.features"},
        objectFactory = GuiceFactory.class,
        features = {"classpath:/org/apache/tinkerpop/gremlin/test/features"},
        plugin = {"progress", "junit:target/cucumber.xml"})
public class mGraphFeatureTest {
    private static final Logger logger = LoggerFactory.getLogger(mGraphFeatureTest.class);

    public static final class mGraphServiceModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(World.class).to(mGraphWorld.class);
        }
    }

    public static final class mGraphWorldInjectorSource implements InjectorSource {
        @Override
        public Injector getInjector() {
            return Guice.createInjector(Stage.PRODUCTION, CucumberModules.createScenarioModule(), new mGraphServiceModule());
        }
    }

    public static final class mGraphWorld implements World {

        private static final mGraph modern = mGraph.open(new MapConfiguration(getBaseConfiguration(LoadGraphWith.GraphData.MODERN)));
        private static final mGraph classic = mGraph.open(new MapConfiguration(getBaseConfiguration(LoadGraphWith.GraphData.CLASSIC)));
        private static final mGraph sink = mGraph.open(new MapConfiguration(getBaseConfiguration(LoadGraphWith.GraphData.SINK)));
        private static final mGraph grateful = mGraph.open(new MapConfiguration(getBaseConfiguration(LoadGraphWith.GraphData.GRATEFUL)));
        private static final mGraph empty = mGraph.open(new MapConfiguration(getBaseConfiguration(null)));

        /*static {
            createIndices();
            readIntoGraph(modern, GraphData.MODERN);
            readIntoGraph(classic, GraphData.CLASSIC);
            readIntoGraph(sink, GraphData.SINK);
            readIntoGraph(grateful, GraphData.GRATEFUL);
        }*/

        @Override
        public GraphTraversalSource getGraphTraversalSource(final LoadGraphWith.GraphData graphData) {
            if (null == graphData)
                return empty.traversal();
            else if (graphData == LoadGraphWith.GraphData.CLASSIC)
                return classic.traversal();
            else if (graphData == LoadGraphWith.GraphData.CREW)
                throw new UnsupportedOperationException("The Crew dataset is not supported by mGraph because it doesn't support mult/meta-properties");
            else if (graphData == LoadGraphWith.GraphData.MODERN)
                return modern.traversal();
            else if (graphData == LoadGraphWith.GraphData.SINK)
                return sink.traversal();
            else if (graphData == LoadGraphWith.GraphData.GRATEFUL)
                return grateful.traversal();
            else
                throw new UnsupportedOperationException("GraphData not supported: " + graphData.name());
        }

        @Override
        public void beforeEachScenario(final Scenario scenario) {
            cleanEmpty();
        }

        @Override
        public String changePathToDataFile(final String pathToFileFromGremlin) {
            return ".." + File.separator + pathToFileFromGremlin;
        }

        private void cleanEmpty() {
            final GraphTraversalSource g = empty.traversal();
            g.V().drop().iterate();
        }

        private static void readIntoGraph(final Graph graph, final LoadGraphWith.GraphData graphData) {
            try {
                final String dataFile = TestHelper.generateTempFileFromResource(graph.getClass(),
                        GryoResourceAccess.class, graphData.location().substring(graphData.location().lastIndexOf(File.separator) + 1), "", false).getAbsolutePath();
                graph.traversal().io(dataFile).read().iterate();
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }
        }

        private static Map<String, Object> getBaseConfiguration(final LoadGraphWith.GraphData graphData) {
            Router.global().addSpace(kvSpace.of(f("/mnt/#"), f("/sys/router/space/kv")));
            final Map<String, Object> config = new LinkedHashMap<>();
            config.put(Graph.GRAPH, f(mGraph.class.getCanonicalName()));
            config.put(SPACE, f("/mnt/test/mtp3"));
            config.put(PATTERN, f("/test/g/#"));
            config.put(NAME, f(graphData.name()));
            config.put(IoRegistry.IO_REGISTRY, f(mIoRegistry.class.getCanonicalName()));
            return config;
        }
    }
}