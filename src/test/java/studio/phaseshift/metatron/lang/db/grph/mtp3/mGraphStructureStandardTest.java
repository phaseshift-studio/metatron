package studio.phaseshift.metatron.lang.db.grph.mtp3;

import org.apache.tinkerpop.gremlin.GraphProviderClass;
import org.apache.tinkerpop.gremlin.structure.StructureStandardSuite;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import studio.phaseshift.metatron.lang.db.grph.type.mtp3.mGraph;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Ignore
@RunWith(StructureStandardSuite.class)
@GraphProviderClass(provider = mGraphProvider.class, graph = mGraph.class)
public class mGraphStructureStandardTest {
    

}
