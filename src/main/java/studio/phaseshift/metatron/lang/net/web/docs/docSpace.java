package studio.phaseshift.metatron.lang.net.web.docs;

import org.java_websocket.WebSocket;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.MSpace;
import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.lang.core.m.type.Fail;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.sys.router.impl.MServer;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class docSpace extends MSpace<MServer> {

    public docSpace(final MServer sjvm, final Map<Obj, Obj> jvm, final fURI pattern, final fURI vid) {
        super(sjvm, jvm, pattern, f("/web/space/doc"), vid);
    }

    public static docSpace of(final fURI host) {
        try {
            MServer server = new MServer(host) {
                public void onObj(final WebSocket conn, final Obj obj) {
                    try {
                        LOG.info("processing doc request: %s", obj);
                        final String source = obj.strValue();
                        final Obj eval = mParser.eval(source);
                        String result = eval.isObjs() ?
                                eval.elements()
                                        .map(Obj::toString)
                                        .reduce((a, b) -> a + "%%%" + b)
                                        .orElse("") :
                                eval.toString();
                        result = Graphitty.strip(result);
                        this.sendObj(conn, str(result));
                        if (eval.isFail())
                            this.onError(conn, eval.<Fail>as().jvmAs());
                    } catch (final Exception e) {
                        this.sendObj(conn, fail(e));
                        this.onError(conn, e);
                    }
                }
            };
            final docSpace space = new docSpace(server, Map.of(uri(PATTERN), uri("/web/docs/#")), f("/web/docs/#"), fURI.NULL);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(space::close));
            return space;
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    public synchronized void close() {
        this.sjvm().stop();
    }

    @Override
    public Obj read(fURI vid) {
        return noobj();
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        LOG.info("processing doc request: %s => %s", vid, obj);
        return noobj();
    }
}
