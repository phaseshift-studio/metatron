/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

package studio.phaseshift.metatron.space.device.log;

import org.slf4j.event.Level;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Lst;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Rec;
import studio.phaseshift.metatron.lang.obj.mtron.MLst;
import studio.phaseshift.metatron.lang.obj.mtron.MRec;
import studio.phaseshift.metatron.lang.obj.mtron.MUri;
import studio.phaseshift.metatron.ui.GraphittyObjLogger;

import java.util.Map;

import static studio.phaseshift.metatron.lang.obj.mtron.MUri.uri;

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
        return ((Rec) this.value().get(uri("level"))).value()
                .entrySet()
                .stream()
                .filter(kv -> Level.valueOf(kv.getKey().uriValue().toString()).compareTo(level) >= 0)
                .flatMap(kv -> kv.getValue().<Lst>as().value().stream())
                .anyMatch(v -> pattern.matches(v.uriValue()));
    }

    public static Log of(final fURI vid) {
        GraphittyObjLogger.setLogger(vid);
        return new Log(vid);
    }

}


