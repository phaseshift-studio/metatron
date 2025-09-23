package studio.phaseshift.metatron.ui;

import org.slf4j.event.Level;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.device.log.Log;
import studio.phaseshift.metatron.util.MTronException;

import static org.slf4j.event.Level.*;


public class GraphittyObjLogger extends GraphittyLogger {

    private static fURI LOG_VID = fURI.NONE;

    public GraphittyObjLogger(final Obj source) {
        super(source);
    }

    public static void setLogger(final fURI logvid) {
        LOG_VID = logvid;
    }


    private boolean doLog(final Level level) {
        final Router router = Router.global();
        if (null == router)
            return true;
        else {
            final Obj o = router.read(LOG_VID);
            if (o.isNoObj())
                throw MTronException.of("no logger in space");
            return Log.from(router.read(LOG_VID).as()).check(level, ((Obj) this.source).vidOrTid());
        }
        //   } catch(final Exception e) {
        //return false;
        // }
    }

    @Override
    public GraphittyObjLogger info(final Object f, final Object... args) {
        if (doLog(INFO)) super.logLevel(INFO, f, args);
        return this;
    }

    @Override
    public GraphittyObjLogger error(final Object f, final Object... args) {
        if (doLog(ERROR)) super.logLevel(ERROR, f, args);
        return this;
    }


    @Override
    public GraphittyObjLogger warn(final Object f, final Object... args) {
        if (doLog(WARN)) super.logLevel(WARN, f, args);
        return this;
    }


    @Override
    public GraphittyObjLogger debug(final Object f, final Object... args) {
        if (doLog(DEBUG)) super.logLevel(DEBUG, f, args);
        return this;
    }

    @Override
    public GraphittyObjLogger trace(final Object f, final Object... args) {
        if (doLog(TRACE)) super.logLevel(TRACE, f, args);
        return this;
    }


}