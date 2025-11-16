package studio.phaseshift.metatron.lang.sys.router;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.sys.router.impl.MConnection;

import java.util.Map;
import java.util.function.BiPredicate;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Cluster {

    Map<fURI, MConnection> nodes();

    fURI host();
    
    default void send(final fURI furi, final Obj obj) {
        this.send((f, conn) -> conn.remoteHost().bimatches(furi), furi, obj);
    }

    default Obj sendRecv(final fURI furi, final Obj obj) {
        return this.sendRecv((f, conn) -> conn.remoteHost().bimatches(furi), furi, obj);
    }

    default void send(final BiPredicate<fURI, MConnection> match, final fURI furi, final Obj obj) {
        this.nodes().values().stream().filter(conn -> match.test(furi, conn)).forEach(conn -> {
            conn.sendObj(obj);
        });
    }

    default Obj sendRecv(final BiPredicate<fURI, MConnection> match, final fURI furi, final Obj obj) {
        return objs(this.nodes().values().stream().filter(mConnection -> match.test(furi, mConnection)).map(mConnection -> mConnection.sendRecvObj(obj)));
    }
}
