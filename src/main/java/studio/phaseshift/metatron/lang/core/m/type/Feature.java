package studio.phaseshift.metatron.lang.core.m.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Feature {

    interface HasLogger {

        default GraphittyLogger logger() {
            return Graphitty.log(this);
        }
    }
    
    interface SelfClone extends HasLogger {

        default <O extends Obj> O clone(final Object jvm, final fURI tid, final fURI vid) {
            this.logger().warn("this obj doesn't support pure cloning");
            ((Obj) this).self(jvm, tid, vid);
            return (O) this;
        }

        default Obj clone() {
            this.logger().warn("this obj doesn't support pure cloning");
            return (Obj) this;
        }
    }
}
