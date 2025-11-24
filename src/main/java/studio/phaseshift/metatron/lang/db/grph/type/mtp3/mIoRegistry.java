package studio.phaseshift.metatron.lang.db.grph.type.mtp3;

import org.apache.tinkerpop.gremlin.structure.io.AbstractIoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.gryo.GryoIo;
import studio.phaseshift.metatron.furi.fURI;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mIoRegistry extends AbstractIoRegistry {

    private static final mIoRegistry INSTANCE = new mIoRegistry();

    private mIoRegistry() {
        //try {
        super.register(GryoIo.class, fURI.class, null);
        // } catch (final ClassNotFoundException e) {
        //    throw new IllegalStateException(e);
        //}
    }

    public static mIoRegistry instance() {
        return INSTANCE;
    }
}
