package studio.phaseshift.metatron.space.device.log;

import org.slf4j.event.Level;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Lst;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Rec;
import studio.phaseshift.metatron.lang.obj.mtron.MLst;
import studio.phaseshift.metatron.lang.obj.mtron.MRec;
import studio.phaseshift.metatron.lang.obj.mtron.MUri;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyObjLogger;

import java.util.Map;

public class Log extends MRec {

    private static final fURI LOG_TID = fURI.of("/usr/log");

    public Log(final Obj log) {
        super(log.recValue());
    }

    protected Log(final fURI vid) {
        super(Map.of(MUri.of("level"), MRec.ofUriKeyed(
                "INFO", MLst.of(),
                "DEBUG", MLst.of(),
                "WARN", MLst.of(),
                "TRACE", MLst.of(MUri.of("#")),
                "ERROR", MLst.of())), LOG_TID, vid);
    }

    public static Log from(final Rec log) {
        return new Log(log);
    }

    public boolean check(final Level level, final fURI pattern) {
       return this.value().getOrDefault(fURI.of(level.name().toUpperCase()).toUri(),MLst.of()).<Lst>as().value().stream().anyMatch(v -> pattern.matches(v.uriValue()));
    }

    public static Log of(final fURI vid) {
        GraphittyObjLogger.setLogger(vid);
        return new Log(vid);
    }

}


