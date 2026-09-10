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

package studio.phaseshift.metatron.isa.mach.type.ui.graphitty;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.console.StatusLine;

import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

public class GraphittyLogger extends LayoutBase<ILoggingEvent> {
    private static final Map<String, String> COLORS = new HashMap<>() {{
        put("INFO", "g");
        put("WARN", "y");
        put("ERROR", "r");
        put("DEBUG", "m");
        put("TRACE", "c");
    }};

    // -----------------------------------------------------------------------
    // Static pane-writer hook – set by Console on startup.
    // BiConsumer<paneId, formattedMessage>
    // Kept as a plain functional field so GraphittyLogger has zero imports of
    // Console or Pane (avoiding a circular package dependency).
    // -----------------------------------------------------------------------

    private static BiConsumer<Integer, String> paneWriter = (id, msg) -> {
    }; // no-op until Console registers
    /**
     * Writer for messages — mirrors {@code System.out.print()}
     * semantics (append without a trailing line break).
     */
    private static BiConsumer<Integer, String> appendPaneWriter = (id, msg) -> {
    };

    /**
     * Register the pane writer.  Called once by {@code Console} during
     * construction so that pane-targeted loggers can route their output into
     * the correct pane buffer.
     *
     * @param writer {@code BiConsumer<paneId, formattedMessage>} – receives the
     *               target pane ID and the fully-formatted (Graphitty-resolved)
     *               log line.
     */
    public static void registerPaneWriter(final BiConsumer<Integer, String> writer) {
        paneWriter = writer;
    }

    /**
     * Register the append-without-newline pane writer.  Called once by
     * {@code Console} during construction.  Messages routed through
     * {@link #none(Object, Object...)} use this writer so that print()-style
     * output (e.g. waiting dots {@code .}) accumulates horizontally on one
     * line instead of each call creating a new buffer line.
     */
    public static void registerAppendPaneWriter(final BiConsumer<Integer, String> writer) {
        appendPaneWriter = writer;
    }

    // -----------------------------------------------------------------------
    // Global default pane target
    // -----------------------------------------------------------------------

    /**
     * Global fallback pane ID used by every logger that has no per-instance
     * target set.  {@code -1} means "no global default" (normal Logback output).
     */
    private static int defaultTargetPaneId = -1;

    /**
     * Set a global default target pane.  Every {@link GraphittyLogger} that has
     * not been given an explicit {@link #targetPane(int)} will route its output
     * to this pane.
     *
     * <p>Pass {@code -1} to clear the global default and restore normal Logback
     * output for un-targeted loggers.
     */
    public static void setDefaultTargetPane(final int paneId) {
        defaultTargetPaneId = paneId;
    }

    /**
     * Returns the global default target pane ID, or {@code -1} if none is set.
     */
    public static int getDefaultTargetPane() {
        return defaultTargetPaneId;
    }

    // -----------------------------------------------------------------------
    // Per-instance pane targeting
    // -----------------------------------------------------------------------

    /**
     * Per-instance pane ID override.
     * {@code -1} = defer to {@link #defaultTargetPaneId} (and then to Logback if that is also -1).
     */
    private int targetPaneId = -1;

    /**
     * Route log output from this logger instance to the pane with the given ID,
     * overriding the global default for this logger only.
     * The pane must exist in the Console's pane tree at the time each message is
     * emitted; if it cannot be found the message is silently dropped.
     *
     * <p>Pass {@code -1} to clear the per-instance override and fall back to the
     * global default (or normal Logback output if no global default is set).
     *
     * <p>Example – always log to pane 2, regardless of global default:
     * <pre>{@code
     *   private static final GraphittyLogger LOG = Graphitty.log(MyClass.class).targetPane(2);
     * }</pre>
     */
    public GraphittyLogger targetPane(final int paneId) {
        this.targetPaneId = paneId;
        return this;
    }

    /**
     * Returns the per-instance target pane ID, or {@code -1} when none is set.
     */
    public int targetPane() {
        return this.targetPaneId;
    }

    /**
     * Returns the effective pane ID for this logger: the per-instance override
     * if set, otherwise the global default, otherwise {@code -1}.
     */
    private int effectivePaneId() {
        if (this.targetPaneId >= 0) return this.targetPaneId;
        return defaultTargetPaneId;
    }

    private boolean hasTargetPane() {
        return effectivePaneId() >= 0;
    }

    /**
     * Format a message with level-coloured prefix for pane output.
     */
    private String formatPaneMessage(final Level level, final Object f, final Object... args) {
        final String msg = this.makeMessage(true, f, args);
        final String color = COLORS.getOrDefault(level.name(), "w");
        return Graphitty.string("{{w}}[{{%s}}%s%s{{w}}]{{X}} %s".formatted(
                color,
                level.name(),
                level.name().length() == 4 ? " " : "",
                msg));
    }

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
        if (o instanceof Throwable t) {
            final StackTraceElement[] trace = t.getStackTrace();
            if (trace != null && trace.length > 0) {
                final StackTraceElement top = trace[0];
                return t + " at " + top.getClassName() + "." + top.getMethodName()
                        + "(" + top.getFileName() + ":" + top.getLineNumber() + ")";
            }
        }
        return null == o ? "null" : o.toString();
    }

    private String toSourceString() {
        return this.source instanceof Obj ? ((Obj) this.source).vidOrTid().basePath().toString() : (this.source instanceof Class ? ((Class<?>) this.source).getSimpleName() : this.source.getClass().getSimpleName());
    }


    public static boolean isLambda(Object obj) {
        return null != obj && obj.getClass().toString().contains("$$Lambda$");
    }

    private String makeMessage(final boolean metadata, final Object f, final Object... args) {
        // arguments are values, not format strings — an argument holding "%s" or
        // "%d" is payload text and must reach the line verbatim
        final Object[] args2 = args.length == 0 ? new Object[0] :
                Stream.of(args)
                        .map(x -> isLambda(x) ? ((Supplier<?>) x).get() : x)
                        .map(x -> x instanceof Obj || x instanceof String ? Highlighter.format(x) : x)
                        .toArray();
        final String message = safeFormat(toStringOrNull(f), args2);
        return metadata ?
                Graphitty.string("[{{b}}%s{{/b}}] %s".formatted(toSourceString(), message)) :
                Graphitty.string(message);
    }

    /**
     * Format a log line against its arguments — never throwing.
     * <p>
     * A message acts as a {@code %}-template only while arguments accompany it. With
     * no arguments it is literal text that may legitimately contain a percent sign
     * (an MCP payload, an exception message, markdown), and formatting such text
     * raises {@link IllegalFormatException} — for instance
     * {@code MissingFormatArgumentException: Format specifier '%s'} for a payload
     * containing a percent token, or {@code UnknownFormatConversionException} when
     * the percent is followed by a character that is not a conversion. An escaping
     * formatter exception is reported by the transport as a bogus protocol error
     * rather than as the logging bug it is.
     * <p>
     * A malformed template — a literal percent sitting next to a specifier, or an
     * argument-count mismatch — falls back to the unformatted text with the
     * arguments appended: logging must never break its caller.
     */
    private static String safeFormat(final String template, final Object... args) {
        if (args.length == 0)
            return template;
        try {
            return template.formatted(args);
        } catch (final IllegalFormatException e) {
            return template + " " + Stream.of(args).map(String::valueOf).collect(Collectors.joining(" "));
        }
    }

    protected GraphittyLogger logLevel(final Level level, final Object f, final Object... args) {
        try {
            if (hasTargetPane()) {
                paneWriter.accept(effectivePaneId(), formatPaneMessage(level, f, args));
            } else {
                this.logger().makeLoggingEventBuilder(level).log(() -> this.makeMessage(true, f, args));
            }
        } catch (final Exception e) {
            System.err.println(e);
        }
        return this;
    }

    protected String localLog(final Level level, final Object f, final Object... args) {
        return this.makeMessage(true, f, args);
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

    public GraphittyLogger log(final String level, final Object f, final Object... args) {
        // the message may already be fully formatted (status() routes through here
        // with no args) — re-formatting it treats its percent signs as specifiers
        final String message = safeFormat(f.toString(), args);
        return switch (level) {
            case TRACE -> this.trace(message);
            case DEBUG -> this.debug(message);
            case INFO -> this.info(message);
            case WARN -> this.warn(message);
            case ERROR -> this.error(message);
            default -> this;
        };
    }

    public GraphittyLogger status(final String level, final Object f, final Object... args) {
        final String message = safeFormat(f.toString(), args);
        try {
            StatusLine.message(str(message));
        } catch (final Exception e) {
            // do nothing
        }
        return log(level, message);
    }

    /// ///////////////////////////////

    public GraphittyLogger none(final Object f, final Object... args) {
        final String msg = this.makeMessage(false, f, args);
        if (hasTargetPane()) {
            appendPaneWriter.accept(effectivePaneId(), msg);
        } else {
            System.out.print(msg);
        }
        return this;
    }

    public Logger logger(final Level level) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        final Logger log = (null == this.source)
                ? lc.getLogger(GraphittyLogger.class)
                : (this.source instanceof Logger)
                ? (Logger) this.source
                : lc.getLogger(this.source.getClass());
        final ch.qos.logback.classic.Logger current = (ch.qos.logback.classic.Logger) log;
        current.setLevel(ch.qos.logback.classic.Level.valueOf(level.name()));
        return log;
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
