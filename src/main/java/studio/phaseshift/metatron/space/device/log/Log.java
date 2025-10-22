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
 */

package studio.phaseshift.metatron.space.device.log;

import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.core.Appender;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Lst;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Rec;
import studio.phaseshift.metatron.lang.obj.Uri;
import studio.phaseshift.metatron.lang.obj.mtron.MLst;
import studio.phaseshift.metatron.lang.obj.mtron.MRec;
import studio.phaseshift.metatron.lang.obj.mtron.MUri;
import studio.phaseshift.metatron.ui.GraphittyObjLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.lang.reflect.Field;
import java.util.Map;

import static studio.phaseshift.metatron.lang.obj.mtron.MUri.uri;

public class Log extends MRec {

    private static final fURI LOG_TID = fURI.of("/usr/log");

    public Log(final Obj log) {
        super(log.recValue());
    }

    protected Log(final fURI vid) {
        super(Map.of(uri("level"), MRec.ofUriKeyed(
                "INFO", MLst.of(),
                "DEBUG", MLst.of(),
                "WARN", MLst.of(),
                "TRACE", MLst.of(uri("#")),
                "ERROR", MLst.of())), LOG_TID, vid);
    }

    public static Log from(final Rec log) {
        return new Log(log);
    }

    public static Log of(final fURI vid) {
        GraphittyObjLogger.setLogger(vid);
        return new Log(vid);
    }

    public static Uri setSLF4J(final String level) {
        final ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        if (null == level || level.isEmpty()) {
            return uri(root.getAppender("STDOUT").getCopyOfAttachedFiltersList()
                            .stream()
                            .filter(x -> x instanceof ThresholdFilter)
                            .map(x -> {
                                try {
                                    final Field field = x.getClass().getDeclaredField("level");
                                    field.trySetAccessible();
                                    return field.get(x).toString();
                                } catch (final Exception e) {
                                    throw MTronException.of(e);
                                }
                            }).findFirst().get()
                    , fURI.dotPath(root.getClass().getCanonicalName()));
        } else {
            final Appender<?> appender = root.getAppender("STDOUT");
            if (null != appender)
                appender.clearAllFilters();
            else
                root.setLevel(ch.qos.logback.classic.Level.valueOf(level));
            ThresholdFilter filter = new ThresholdFilter();
            filter.setLevel(level.replace(":log", "").trim());
            filter.start();
            if(appender != null)
                root.getAppender("STDOUT").addFilter(filter);
            return uri(level);
        }
    }

    public boolean check(final Level level, final fURI pattern) {
        return ((Rec) this.jvm().get(uri("level"))).jvm()
                .entrySet()
                .stream()
                .filter(kv -> Level.valueOf(kv.getKey().uriValue().toString()).compareTo(level) >= 0)
                .flatMap(kv -> kv.getValue().<Lst>as().jvm().stream())
                .anyMatch(v -> pattern.matches(v.uriValue()));
    }
}


