/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.isa.mach.type;

import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.filter.Filter;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyObjLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class LogObj extends MRec {

    public static final fURI LOG_TID = MACH_ISA_TID.extend("log");

    public LogObj(final Obj log) {
        super(new LinkedHashMap<>(log.recValue()), LOG_TID,null);
    }

    protected LogObj(final Rec levels, final fURI vid) {
        super(mutableMap(uri(Tokens.LEVEL), levels), LOG_TID, vid);
    }

    public static LogObj from(final Rec log) {
        return new LogObj(log);
    }

    public static LogObj of(final Rec levels, final fURI vid) {
        GraphittyObjLogger.setLogger(vid);
        return new LogObj(levels, vid);
    }

    public static Level getSLF4J() {
        final ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        return org.slf4j.event.Level.valueOf(root.getLevel().toString());
    }

    public static Uri setSLF4J(final String level) {
        final ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        if (null == level || level.isEmpty()) {
            return uri(root.getAppender("STDOUT").getCopyOfAttachedFiltersList()
                    .stream()
                    .filter(x -> x instanceof ThresholdFilter)
                    .map(x -> {
                        try {
                            final Field field = x.getClass().getDeclaredField(Tokens.LEVEL);
                            field.trySetAccessible();
                            return field.get(x).toString();
                        } catch (final Exception e) {
                            throw MTronException.of(e);
                        }
                    }).findFirst().orElse("TRACE"), f(root.getClass().getCanonicalName().replace(".", "/")));
        } else {
            final Appender<?> appender = root.getAppender("STDOUT");
            if (null != appender)
                appender.clearAllFilters();
            root.setLevel(ch.qos.logback.classic.Level.valueOf(level));
            ThresholdFilter filter = new ThresholdFilter();
            filter.setLevel(level);
            filter.start();
            if (appender != null)
                appender.addFilter((Filter)filter);
            return uri(level);
        }
    }

    public boolean check(final Level level, final fURI pattern) {
        return ((Rec) this.jvm().get(uri("level"))).jvm()
                .entrySet()
                .stream()
                .filter(kv -> Level.valueOf(kv.getKey().uriValue().toString().toUpperCase()).compareTo(level) >= 0)
                .flatMap(kv -> kv.getValue().<Lst>as().jvm().stream())
                .anyMatch(v -> pattern.test(v.uriValue()));
    }
}


