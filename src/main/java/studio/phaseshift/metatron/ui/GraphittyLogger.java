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

package studio.phaseshift.metatron.ui;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.HashMap;
import java.util.Map;

public class GraphittyLogger extends LayoutBase<ILoggingEvent> {
    private static final Map<String, String> COLORS = new HashMap<>() {{
        put("INFO", "g");
        put("WARN", "y");
        put("ERROR", "r");
        put("DEBUG", "m");
        put("TRACE", "c");
    }};

    private enum OtherLevel {NONE, EXCEPT}

    protected final Object source;

    public GraphittyLogger() {
        this.source = null;
    }

    public GraphittyLogger(final Object source) {
        this.source = source;
    }

    private static String toStringOrNull(final Object o) {
        return null == o ? "null" : o.toString();
    }

    private String toSourceString() {
        return this.source instanceof Obj ? ((Obj) this.source).vidOrTid().toString() : (this.source instanceof Class ? ((Class<?>) this.source).getSimpleName() : this.source.getClass().getSimpleName());
    }

    private String makeMessage(final boolean metadata, final Object f, final Object... args) {
        return metadata ?
                Graphitty.string("[{{b}}%s{{/b}}] %s".formatted(toSourceString(), args.length == 0 ? toStringOrNull(f) : toStringOrNull(f).formatted(args))) :
                Graphitty.string(args.length == 0 ? toStringOrNull(f) : toStringOrNull(f).formatted(args));
    }

    protected GraphittyLogger logLevel(final Level level, final Object f, final Object... args) {
        try {
            this.logger().makeLoggingEventBuilder(level).log(() -> this.makeMessage(true, f, args));
        } catch (Exception e) {
            System.out.println(e);
        }
        return this;
    }

    private GraphittyLogger otherLevel(final OtherLevel level, final Object f, final Object... args) {
        if (OtherLevel.NONE == level)
            System.out.print(this.makeMessage(false, f, args));
        else if (OtherLevel.EXCEPT == level) {
            throw MTronException.of(f, args);
        }
        return this;
    }

    public GraphittyLogger info(final Object f, final Object... args) {
        return this.logLevel(Level.INFO, f, args);
    }

    public GraphittyLogger debug(final Object f, final Object... args) {
        return this.logLevel(Level.DEBUG, f, args);
    }

    public GraphittyLogger warn(final Object f, final Object... args) {
        return this.logLevel(Level.WARN, f, args);
    }

    public GraphittyLogger trace(final Object f, final Object... args) {
        return this.logLevel(Level.TRACE, f, args);
    }

    public GraphittyLogger error(final Object f, final Object... args) {
        return this.logLevel(Level.ERROR, f, args);
    }

    /// ///////////////////////////////

    public GraphittyLogger none(final Object f, final Object... args) {
        return this.otherLevel(OtherLevel.NONE, f, args);
    }

    public MTronException except(final Object f, final Object... args) throws MTronException {
        this.otherLevel(OtherLevel.EXCEPT, f, args);
        return MTronException.of("dummy");
    }


    private Logger logger() {
        if (null == this.source)
            return LoggerFactory.getLogger(GraphittyLogger.class);
        else if (!(this.source instanceof Logger))
            return LoggerFactory.getLogger(this.source.getClass());
        return (Logger) this.source;
    }

    public String doLayout(final ILoggingEvent event) {
        try {
            return Graphitty.string("{{w}}[{{%s}}%s%s{{w}}]{{X}} %s\n".formatted(COLORS.get(event.getLevel().levelStr),
                    event.getLevel(),
                    event.getLevel().toString().length() == 4 ? " " : "",
                    event.getFormattedMessage()));
        } catch (final Exception e) {
            return "[ERROR] error in logger: " + e.getMessage();
        }
    }
}
