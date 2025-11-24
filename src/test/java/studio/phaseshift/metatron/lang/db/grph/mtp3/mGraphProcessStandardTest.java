package studio.phaseshift.metatron.lang.db.grph.mtp3;

import org.apache.tinkerpop.gremlin.GraphProviderClass;
import org.apache.tinkerpop.gremlin.process.ProcessEmbeddedStandardSuite;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.ProfileTest;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import studio.phaseshift.metatron.lang.db.grph.type.mtp3.mGraph;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Ignore
@RunWith(ProcessEmbeddedStandardSuite.class)
@GraphProviderClass(provider = mGraphProvider.class, graph = mGraph.class)
public class mGraphProcessStandardTest {
    
}
