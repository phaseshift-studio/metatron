/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.isa.mach.type.ui.graphitty;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.util.MTronException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class GraphittyLogger extends LayoutBase<ILoggingEvent> {
    private static final Map<String, String> COLORS = new HashMap<>() {{
        put("INFO", "g");
        put("WARN", "y");
        put("ERROR", "r");
        put("DEBUG", "m");
        put("TRACE", "c");
    }};

    public enum OtherLevel {NONE, EXCEPT}

    protected final Object source;

    public GraphittyLogger() {
        this.source = null;
    }

    public GraphittyLogger(final Object source) {
        this.source = source;
    }

    private static String toStringOrNull(final Object o) {
        if (o instanceof Obj)
            return Highlighter.format(o);
        return null == o ? "null" : o.toString();
    }

    private String toSourceString() {
        return this.source instanceof Obj ? ((Obj) this.source).vidOrTid().basePath().toString() : (this.source instanceof Class ? ((Class<?>) this.source).getSimpleName() : this.source.getClass().getSimpleName());
    }


    public static boolean isLambda(Object obj) {
        return null != obj && obj.getClass().toString().contains("$$Lambda$");
    }

    private String makeMessage(final boolean metadata, final Object f, final Object... args) {
        final Object[] args2 = args.length == 0 ? new Object[0] :
                Stream.of(args)
                        .map(x -> isLambda(x) ? ((Supplier<?>) x).get() : x)
                        .map(x -> x instanceof Obj || x instanceof String ? Highlighter.format(x) : x)
                        .toArray();
        return metadata ?
                Graphitty.string("[{{b}}%s{{/b}}] %s".formatted(toSourceString(), args.length == 0 ? toStringOrNull(f) : toStringOrNull(f).formatted(args2))) :
                Graphitty.string(args.length == 0 ? toStringOrNull(f) : toStringOrNull(f).formatted(args2));
    }

    protected GraphittyLogger logLevel(final Level level, final Object f, final Object... args) {
        try {
            this.logger().makeLoggingEventBuilder(level).log(() -> this.makeMessage(true, f, args));
        } catch (final Exception e) {
            System.err.println(e);
        }
        return this;
    }

    protected String localLog(final Level level, final Object f, final Object... args) {
        return this.makeMessage(true, f, args);
    }

    private GraphittyLogger otherLevel(final OtherLevel level, final Object f, final Object... args) {
        if (OtherLevel.NONE == level)
            System.out.print(this.makeMessage(false, f, args));
        else if (OtherLevel.EXCEPT == level) {
            throw MTronException.of(f, args);
        }
        return this;
    }

    public Optional<String> localInfo(final Object f, final Object... args) {
        return Optional.ofNullable(this.logger().isEnabledForLevel(Level.INFO) ? this.localLog(Level.INFO, f, args) : null);
    }

    public Optional<String> localError(final Object f, final Object... args) {
        return Optional.ofNullable(this.logger().isEnabledForLevel(Level.ERROR) ? this.localLog(Level.ERROR, f, args) : null);
    }

    public GraphittyLogger info(final Object f, final Object... args) {
        return this.logger().isEnabledForLevel(Level.INFO) ? this.logLevel(Level.INFO, f, args) : this;
    }

    public GraphittyLogger debug(final Object f, final Object... args) {
        return this.logger().isEnabledForLevel(Level.DEBUG) ? this.logLevel(Level.DEBUG, f, args) : this;
    }

    public GraphittyLogger warn(final Object f, final Object... args) {
        return this.logger().isEnabledForLevel(Level.WARN) ? this.logLevel(Level.WARN, f, args) : this;
    }

    public GraphittyLogger trace(final Object f, final Object... args) {
        return this.logger().isEnabledForLevel(Level.TRACE) ? this.logLevel(Level.TRACE, f, args) : this;
    }

    public GraphittyLogger error(final Object f, final Object... args) {
        return this.logger().isEnabledForLevel(Level.ERROR) ? this.logLevel(Level.ERROR, f, args) : this;
    }

    /// ///////////////////////////////

    public GraphittyLogger none(final Object f, final Object... args) {
        return this.otherLevel(OtherLevel.NONE, f, args);
    }

    public MTronException except(final Object f, final Object... args) throws MTronException {
        this.otherLevel(OtherLevel.EXCEPT, f, args);
        return MTronException.of("dummy");
    }


    public Logger logger() {
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
