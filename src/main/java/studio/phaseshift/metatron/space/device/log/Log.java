package studio.phaseshift.metatron.space.device.log;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.mtron.MLst;
import studio.phaseshift.metatron.lang.obj.mtron.MRec;
import studio.phaseshift.metatron.lang.obj.mtron.MUri;

import java.util.Map;

public class Log extends MRec {

    private static final fURI LOG_TID = fURI.of("/mtron/device/log");

    public Log(final fURI vid) {
        super(Map.of(MUri.of("level"),MRec.of(
                MUri.of("INFO"),
                MLst.of(),
                MUri.of("DEBUG"),
                MLst.of(),
                MUri.of("ERROR"),
                MLst.of())),LOG_TID,vid);
    }


}


