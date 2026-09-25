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

package studio.phaseshift.metatron.isa.mach.type.ui.console;

import org.jline.builtins.ConfigurationPath;
import org.jline.keymap.KeyMap;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.widget.Widgets;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.Tracer;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronUISerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.thread.AbstractThread;
import studio.phaseshift.metatron.isa.mach.type.thread.CoreThread;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.mach.type.ui.tmux.Pane;
import studio.phaseshift.metatron.isa.mach.type.ui.tmux.SplitLayout;
import studio.phaseshift.metatron.isa.mach.type.ui.tool.TraceTool;
import studio.phaseshift.metatron.isa.mach.type.ui.tool.TypeDiffTool;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.FloatingSurface;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Utilities;
import studio.phaseshift.metatron.isa.sys.mSystem;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static studio.phaseshift.metatron.BootLoader.BOOTING;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread.virtual;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_CONSOLE_TID;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.SYS;
import static studio.phaseshift.metatron.util.CommonUtil.HEADER_FILE;

public class Console extends MRec implements Closeable, Runnable {

    public static final String METATRON_VERSION = "0.1-alpha";
    public static final String MTRON = "mtron";
    /**
     * The one prompt the console shows — mtron is the only language it runs.
     */
    public static final String PROMPT = "{{m}}mtron{{g}}> ";
    public static final String MTRON_NANORC = "mtron.nanorc";
    public static Path HISTORY_FILE = Paths.get(".metatron.history");
    public Inst history = instA(f("dummy"));
    public Inst input = noobj();
    public Inst output = noobj();
    public String prefix = "";
    public String postfix = "";

    /**
     * The serializer every result goes through.  It is the console's own (see
     * {@code ObjConsoleSerializer}) because that is where a uri becomes a {@code {{link}}} — a
     * plain serializer writes the uri and nothing downstream can tell it apart from text.
     */
    public ObjSerializer<String> serializer = new ObjmtronUISerializer();
    public Inst statusLine = instLambda((lhs, inst) -> {
        StatusLine.message(inst.arg(0));
        return noobj();
    });
    private final GraphittyLogger LOG = Graphitty.log(this);
    private static Terminal terminal;
    private final LineReader reader;
    private final Widgets widgets;
    private final StatusLine status;
    private final static ConfigurationPath configurations = new ConfigurationPath(
            Paths.get("conf"),                                     // application-wide settings
            Paths.get(System.getProperty("user.home"), ".metatron") // user-specific settings
    );
    public static Console LOCAL_INSTANCE = null;
    /**
     * The vid of the console's repl VirtualThread — the root of console-owned
     * evaluation.  Set when the console binds its repl thread.
     */
    public static volatile fURI CONSOLE_THREAD_VID = null;
    public Machine machine = null;
    public static AtomicBoolean userMode = new AtomicBoolean(false);

    /**
     * The foreground-job contract: the watch loop, the detach/cancel/interrupt
     * protocol, the terminal handoff, and the detached-jobs list.  The
     * {@code machine} beside it stays here — the repl and the interrupt paths
     * both read it directly.
     */
    private final ForegroundJobs jobs = new ForegroundJobs(this);

    /**
     * how much of the console line a detached-job banner echoes
     */
    private static final int DETACH_PREVIEW_CHARS = 48;

    // ========== Split Pane Support ==========
    /**
     * The pane tree — its shape, which pane is active, and the split /
     * close / focus / cycle / resize operations (see {@link PaneManager}).
     */
    private final PaneManager panes = new PaneManager();

    private final AtomicBoolean needsRedraw = new AtomicBoolean(false);
    private boolean traceEnabled = false; // True when :trace toggled on — dumps Java stack on fail
    private FloatingSurface floatingSurface;

    /**
     * The console's own screen: when on, console output is drawn from a buffer the
     * console owns (see {@link ConsoleScreen}) instead of being written into the
     * terminal and scrolled away.
     *
     * <p>Opt-in while it beds in — {@code -Dmetatron.console.screen=true}.  The
     * reason to own the screen is that a terminal cannot be asked what it holds:
     * a floating widget that draws over a row and then moves away leaves a hole,
     * because nothing can restore text the terminal owns.  A buffer the console
     * owns can be repainted from the content (see {@link #renderScreen()}), which
     * is also what makes a row's links clickable later on.
     */
    private static final boolean SCREEN_REQUESTED =
            Boolean.parseBoolean(System.getProperty("metatron.console.screen", "true"));

    /**
     * Whether the console is drawing its own screen.
     *
     * <p><b>On by default</b> — this is the console's behaviour, not an experiment,
     * and the reason is not cosmetic: a terminal's rows cannot be read back, so a
     * widget that draws over text and moves leaves a hole nothing can fill.  Opt out
     * with {@code -Dmetatron.console.screen=false} (or {@code METATRON_JAVA_OPTS} via
     * {@code bin/metatron}).
     *
     * <p>Never on in-process under test: the mode takes the process's stdout, and a
     * test JVM's stdout belongs to the test runner.
     */
    /**
     * The process's stderr as it was before this console took it, for diagnostics that
     * must not become transcript: a trace line printed into the screen would trigger
     * another paint, whose trace would trigger another — the loop being the point.
     */
    private static final java.io.PrintStream RAW_ERR = System.err;

    /**
     * The stderr to trace on (see {@link #RAW_ERR}).
     */
    public static java.io.PrintStream rawErr() {
        return RAW_ERR;
    }

    public static boolean screenMode() {
        // A console that owns its screen assumes it can address rows and be repainted; a
        // dumb terminal (no cursor addressing — a captured stream, a CI log) cannot, and
        // asking it to would both lose the output and leave the caller's own writes
        // nowhere.  It is also the guard that keeps the mode out of test JVMs, whose
        // terminals are dumb by construction.
        final org.jline.terminal.Terminal t = Console.terminal;
        if (null != t && org.jline.terminal.Terminal.TYPE_DUMB.equals(t.getType())) return false;
        return SCREEN_REQUESTED && !studio.phaseshift.metatron.BootLoader.TESTING;
    }

    /**
     * The console's screen — the transcript buffer, the output funnel that fills
     * it, the paint pass, the link clicks it answers, and the repair of the rows
     * it shares with the floating widgets (see {@link ScreenView}).
     */
    private final ScreenView screenView = new ScreenView(this);


    /**
     * The shared {@link FloatingSurface} for this console session.
     * Widgets pinned here are redrawn automatically at every prompt cycle.
     */
    public FloatingSurface getFloatingSurface() {
        if (this.floatingSurface == null) {
            this.floatingSurface = new FloatingSurface(terminal);
            // The surface claims the output funnel in its constructor; on a console
            // that owns its screen, the screen takes it instead (see installScreenWriter).
            this.screenView.installScreenWriter();
            // The screen also takes the rows the widgets draw over: the surface reports
            // what it erased and stops blanking rows it does not own (see repairRowedRows).
            if (screenMode())
                this.floatingSurface.setDamageListener(band -> this.repairRows(band[0], band[1]));
        }
        return this.floatingSurface;
    }


    boolean screenAppends() {
        return this.screenView.screenAppends();
    }


    /**
     * Where the link trace goes, when it is asked for (see {@link #linkTrace}) — a file, never the
     * transcript.  Unset means no tracing at all: it is a debugging instrument, enabled for a
     * session with {@code -Dmetatron.links.trace=<path>}.
     */
    private static final String LINK_TRACE = System.getProperty("metatron.links.trace");

    /**
     * Append one line to the link trace.
     * <p>
     * Deliberately a file and not the transcript: anything the console prints at the prompt scrolls
     * the rows under the pointer, which is enough to break the very gesture being examined, so a
     * diagnostic that writes to the screen cannot diagnose a click.  A trace never throws — it must
     * not be able to break the console it is watching.
     */
    static void linkTrace(final String format, final Object... args) {
        if (null == LINK_TRACE) return;
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(LINK_TRACE),
                    String.format(format, args) + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (final Exception ignore) {
            // a trace is never worth an error
        }
    }

    /**
     * The ask/answer protocol for lines the console is asked for by anyone
     * other than its own read loop — the queue and the answer side live on
     * {@link HumanBroker}.
     */
    private final HumanBroker human = new HumanBroker(this);

    public HumanBroker human() {
        return this.human;
    }

    public boolean inReadLine() {
        return this.inReadLine;
    }

    /**
     * The console's own input, as the stream {@link mSystem#in()} hands out.
     * <p>
     * Each read is one line, asked of the console's reader, which is
     * what keeps the console one way of using metatron rather than a special case: whoever wants a
     * line — {@code sys:stdin}, a human chat model, any instruction — reads the terminal the console
     * owns, and none of them mentions the console.
     */
    private java.io.InputStream consoleInput() {
        return new java.io.InputStream() {
            private byte[] pending = new byte[0];
            private int at = 0;

            @Override
            public int read() {
                if (this.at >= this.pending.length) {
                    final String line = HumanBroker.readHumanLine(null);
                    if (null == line) return -1;    // end of input, not a blank line
                    this.pending = (line + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    this.at = 0;
                }
                return this.pending[this.at++] & 0xff;
            }
        };
    }

    /**
     * One line of what the console believes about links, for the {@code :link} report.
     * <p>
     * A uri that is drawn but does not answer a click has exactly two causes, and this
     * distinguishes them: the console is still appending to the terminal rather than painting its
     * own rows (a click can only be resolved against a row the console painted), or the reader is
     * not at the prompt.  The pointer line matters too — while it is held by the console the
     * terminal's own selection is unavailable, which is why {@code alt}+{@code s} hands it back.
     */
    public String linkReport() {
        final Terminal term = getTerminal();
        return String.format("clickable %s, underline %s, screen %s, %s, %d row(s) with links, pointer %s, "
                        + "terminal mouse %s, at prompt %s",
                Graphitty.linkClickable() ? "on" : "off",
                Graphitty.linkUnderline() ? "on" : "off",
                screenMode() ? "on" : "off",
                this.screenAppends()
                        ? "appending to the terminal (a click cannot be resolved yet)"
                        : "painting its own rows",
                this.screenView.screen().linkRows(),
                this.pointer.widgetMouseTracking() ? "held by the console" : "held by the terminal",
                null == term ? "no terminal" : term.hasMouseSupport() ? "supported" : "not supported",
                this.inReadLine ? "yes" : "no");
    }

    /**
     * Wipe the transcript — the display and the screen's model of it, together.
     * <p>
     * A raw clear-screen escape is a wipe the screen does not know about: its rows are still
     * recorded, so the painter has nothing to correct, the frame stays empty, and the console is
     * left describing a screen that is no longer there.  A link that is no longer drawn is then a
     * link that no longer answers, which is why clicking stopped working after {@code :clear}.
     * Emptying the screen and invalidating it makes the next frame paint the (now empty)
     * transcript, and the pointer is re-synced to what is actually on screen.
     */
    public void clearTranscript() {
        this.screenView.clearTranscript();
    }


    /**
     * Minimum ms between content-only live redraws triggered by pane output.
     */
    private static final long PANE_RENDER_THROTTLE_MS = 80;
    private volatile long lastPaneRenderMs = 0;

    /**
     * While true, non-active-pane rendering is deferred until the keyboard has been
     * idle for {@link #INPUT_IDLE_THRESHOLD_MS}.  Set at the top of the REPL loop,
     * cleared when readLine() returns.
     */
    private volatile boolean inReadLine = false;
    /**
     * Timestamp of the last detected keystroke (buffer-length change).
     */
    private volatile long lastKeyActivityMs = 0;
    /**
     * Snapshot of buffer length used to detect keystrokes via polling.
     */
    private volatile int lastBufferLength = 0;
    private volatile boolean pendingPaneFlush = false;
    /**
     * When non-null, the next {@code readLine()} call will pre-fill the JLine
     * editing buffer with this text.  Set by the chat-overlay detection path
     * to restore the mtron&gt; prefix after a chat submission, and cleared
     * immediately after the next {@code readLine()} consumes it.
     */
    private volatile String seedBuffer = null;
    /**
     * Metatron-addressable stack of {@code \_ } expression lines, ordered
     * outermost→innermost.  The deepest (last) element is evaluated on
     * ENTER and popped.  Accessible from mtron via {@code *exp>>0} etc.
     * when the console is stored at {@code /usr/console}.
     */
    public Lst expressionStack = lst();

    /**
     * Milliseconds of keyboard inactivity after which non-active panes may render.
     */
    private static final long INPUT_IDLE_THRESHOLD_MS = 500;

    public Console(final Rec options, final fURI vid) {
        super(options.jvm(), UI_CONSOLE_TID, vid);
        BootLoader.ONE_SHOT = false;
        Console.LOCAL_INSTANCE = this;
        try {
            // Initialize pane system with a single pane
            this.panes.init(this);

            // Register the pane writer so GraphittyLogger.targetPane(id) works.
            // GraphittyLogger lives in the graphitty sub-package and cannot import Console
            // directly, so we supply a lambda here to bridge the two.
            GraphittyLogger.registerPaneWriter((paneId, message) ->
                    this.getAllPanes().stream()
                            .filter(p -> p.id() == paneId)
                            .findFirst()
                            .ifPresent(p -> p.appendOutput(message)));
            // Append-without-newline writer — mirrors System.out.print() for
            // logger.none() (e.g. waiting dots in mModel.chat()).  Without this,
            // each . would be its own buffer line, producing vertical dots instead
            // of horizontal accumulation.
            GraphittyLogger.registerAppendPaneWriter((paneId, message) ->
                    this.getAllPanes().stream()
                            .filter(p -> p.id() == paneId)
                            .findFirst()
                            .ifPresent(p -> p.appendOutput(message, false)));

            final DefaultParser parser = ReaderSetup.parser();
            Console.terminal = ReaderSetup.buildTerminal(this.jobs::interruptActive);
            // Register terminal with Graphitty so widgets can write without
            // depending on Console/Terminal directly.
            Graphitty.init(terminal.output());
            // ...and take the process's own stdout too, so text that never went
            // through Graphitty still lands in the screen (see the method).
            this.screenView.installStdoutCapture();
            // and its input, which the console owns while it runs: mSystem hands out a stream over
            // this console's reader, so sys:stdin and a human chat model read what is typed at the
            // prompt instead of a second reader on System.in that would see nothing
            if (screenMode()) mSystem.in(this.consoleInput());
            // ...and give the terminal back when this process goes away.  Mouse tracking is a mode
            // the terminal keeps until something turns it off: quitting with it armed leaves the
            // shell reporting every drag as mouse bytes, so nothing outside metatron can be selected
            // and the escape sequences land in whatever is typed next.
            this.releaseTerminalOnExit();
            // The console owns the screen from its first frame, so it starts with a clean one:
            // whatever was printed above (a launcher's banner, a shell prompt) is cleared and the
            // console's own banner begins on the first row.  That row is also what a click is
            // resolved against, which is why the console cannot simply append below it.
            if (screenMode()) {
                this.screenView.beginAtTop();
            }
            // Request extended key reporting so terminals that support it (kitty, ghostty,
            // xterm with modifyOtherKeys, iTerm2, etc.) will send distinguishable
            // sequences for Shift+Backspace and other modified keys.
            // Backward-compatible: terminals that don't understand these sequences ignore them.
            terminal.writer().print("\033[>1u");   // kitty progressive enhancement 1 (disambiguate)
            terminal.writer().print("\033[>4;2m"); // xterm modifyOtherKeys level 2
            terminal.writer().flush();
            this.outputHeader("");
            ReaderSetup.installSystemRegistry(parser, terminal, configurations);
            this.reader = ReaderSetup.buildReader(parser, terminal, this);
            this.widgets = new Widgets(this.reader) { /* keys bound by CommandPalette.bindKeys */
            };
            this.status = new StatusLine(this);
            docWrap(virtual(instLambda((lhs, inst2) -> {
                Console.this.status.run();
                return jnt(0);
            }), SYS.extend("thread/console_statusline")), "console statusline").apply();
            // The hotkey watcher reads the terminal whenever a foreground job
            // holds the console (see awaitForeground).  It is a platform thread
            // on purpose: it parks inside a blocking terminal read, which is
            // exactly the work the repl thread must not be doing.
            docWrap(CoreThread.core(instLambda((lhs, inst2) -> {
                Console.this.watchTerminal();
                return noobj();
            }), SYS.extend("thread/console_hotkey")), "console hotkey watcher").applyAsync();
            this.history = auto_(instC(f("history").dom(ALL).rng(REC_TID.maybeSome()), lst(T(ALL)),
                    (lhs, inst) -> objs(IteratorUtil.list(this.reader.getHistory().reverseIterator())
                            .stream()
                            .sorted(Comparator.comparing(History.Entry::time))
                            .map(s -> rec(uri(TIME), str(s.time().toString()), uri(ENTRY), str(s.line())))
                    ))).tryToInst().as();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    public static Console of(final Rec options) {
        return new Console(options, null);
    }

    @Override
    public void close() {
        try {
            this.jobs.close();
            this.reader.getBuffer().clear();
            // Everything that has been asked for is in the terminal before the terminal
            // is given back: the render thread is a daemon and exiting will not wait for
            // it, so without this drain its queued lines — the shutdown log among them —
            // would race the shell's prompt for the same tty
            this.getFloatingSurface().drain();
            // The terminal's modes — mouse tracking and the extended key reporting —
            // outlive the process until something writes them off, and they may have
            // been armed by jline itself (a modal tool's trackMouse) as well as by
            // this console, so the release is unconditional and covers both (see
            // MousePointer.releaseTerminalModes)
            this.pointer.releaseTerminalModes();
            try {
                // jline's own tracking state goes with it, so nothing later believes
                // a tracked mode is still on
                terminal.trackMouse(Terminal.MouseTracking.Off);
            } catch (final Exception ignored) {
                // the release above already wrote the terminal's bytes
            }
            terminal.close();
        } catch (final IOException e) {
            LOG.error(e);
        }
    }

    public void write(final Object object) {
        // The string is Graphitty markup (a prompt, a serialized result) — resolve it
        // with Graphitty directly.  Routing it through the reader's highlighter first
        // (which runs the mtron syntax with graphitty disabled) would interleave ANSI
        // into a ``` fence or {{…}} and stop Graphitty from seeing it.
        final String markup = object instanceof Obj ? this.serializer.write((Obj) object) : object.toString();
        if (screenMode()) {
            // recorded in the screen, and shown either the way any terminal program shows
            // it or by the screen's own painting (see screenOutput)
            this.screenView.screenOutput(studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty.string(markup));
            return;
        }
        Graphitty.out(terminal.output(), markup);
    }

    public static Terminal getTerminal() {
        return Console.terminal;
    }

    /**
     * @return true when the current thread is the console's repl thread or a
     * thread it directly spawned (e.g. the per-line SwarmMachine evaluating
     * an inline {@code @agent.chat(...)}).  False for detached threads such
     * as {@code virtual::[code=>...]} forks, so background chats don't
     * animate console spinners.
     */
    public static boolean isConsoleOwned() {
        final AbstractThread current = BootLoader.CURRENT_THREAD.get();
        if (null == current)
            return true;
        final fURI consoleVid = CONSOLE_THREAD_VID;
        if (null == consoleVid)
            return true;
        if (consoleVid.equals(current.vid()))
            return true;
        return current.sourceVid().map(consoleVid::equals).orElse(true);
    }

    public LineReader getReader() {
        return this.reader;
    }

    public Widgets getWidgets() {
        return this.widgets;
    }

    /**
     * A caller's prompt as it should be drawn: markup resolved, and plain text left alone.
     */
    private String promptFrom(final String prompt) {
        return null == prompt || prompt.isEmpty() ? this.prompt() : Graphitty.string(prompt);
    }

    public String prompt() {
        if (this.panes.splitMode() && this.panes.activePane() != null) {
            return this.panes.activePane().prompt();
        }
        return Graphitty.string(PROMPT + this.prefix);
    }

    /**
     * Prepare for readLine() - position cursor and clear prompt area in split mode.
     * Always re-renders panes to ensure correct layout before input.
     */
    private void prepareForInput() {
        // Built-in shortcut keys are reasserted before every read, so a
        // shadow binding (menu line key, tool) can never outlive this turn.
        this.reassertBuiltinKeys();
        // The widget set may have changed since the last prompt, and jline
        // releases mouse tracking at the end of every readLine — so the mode is
        // re-asserted here, once per prompt (the watcher tick only reacts to a
        // change, so nothing chatters in between).  A new prompt also re-arms
        // the pointer if a wheel released it to the terminal.
        this.pointer.rearmPointer();
        this.syncWidgetMouseTracking(true);
        if (screenMode() && !this.panes.splitMode()) {
            // The screen owns the rows above the prompt, and the prompt is pinned to
            // a fixed row — so jline must not print its ~ marker (or a fresh line)
            // when the cursor is not where it expects to be.  Pane mode learned this
            // the same way; see the split branch below.
            this.reader.unsetOpt(LineReader.Option.AUTO_FRESH_LINE);
            this.reader.setVariable("COLUMNS", terminal.getWidth());
            this.reader.setVariable(LineReader.SECONDARY_PROMPT_PATTERN,
                    Graphitty.string("{{-X&v1&^1&m}}     {{g}}| {{X}}"));
            // Repaint in full: a prompt begins after Enter has run jline's cursor down
            // a row, and jline's status line moves the terminal when it makes room for
            // itself — the screen has scrolled, so every row the painter believed it
            // knew has moved.  Measured cost of the full region is under a millisecond.
            this.screenView.resetForNewPrompt();
            this.renderScreen(false);
        } else if (this.panes.splitMode() && this.panes.activePane() != null) {
            // Disable AUTO_FRESH_LINE in split mode - it interferes with cursor positioning
            // by outputting a ~ marker when cursor isn't at column 1
            this.reader.unsetOpt(LineReader.Option.AUTO_FRESH_LINE);

            // Always render panes fresh to ensure correct layout (handles terminal resize, etc.)
            // renderPanes() also positions cursor at the prompt location
            this.renderPanes();

            // ----------------------------------------------------------------
            // Constrain JLine's input echo to the active pane's width.
            //
            // Without this, JLine thinks the terminal is full-width and typed
            // characters overflow horizontally into adjacent panes.
            //
            // JLine wraps when the tracked column reaches COLUMNS, so we set
            // it to the pane's right content boundary (1-based terminal col):
            //   paneStartCol + paneAvailWidth - 2
            // e.g. pane at cols 1-50 (content 2-49) → COLUMNS = 49
            //      pane at cols 51-100 (content 52-99) → COLUMNS = 99
            // ----------------------------------------------------------------
            final int[] pos = panes.calculatePanePosition(this, this.panes.activePane());
            if (pos == null) return; // activePane not in tree (stale ref) – skip constraints
            final int paneStartCol = pos[1];   // 1-based left col of pane box
            final int paneAvailWidth = pos[3];
            final int contentStartCol = paneStartCol + 1;             // inside left border
            final int rightBoundary = paneStartCol + paneAvailWidth - 2;
            this.reader.setVariable("COLUMNS", rightBoundary);

            // Secondary prompt: pad to the pane's left content column so
            // continuation lines also stay within the pane borders.
            final String secondaryPrompt =
                    " ".repeat(Math.max(0, contentStartCol - 1))
                            + Graphitty.string("{{g}}|{{X}} ");
            this.reader.setVariable(LineReader.SECONDARY_PROMPT_PATTERN, secondaryPrompt);

        } else {
            // Re-enable AUTO_FRESH_LINE in normal mode
            this.reader.setOpt(LineReader.Option.AUTO_FRESH_LINE);

            // Restore full terminal width / secondary prompt.
            this.reader.setVariable("COLUMNS", terminal.getWidth());
            // Use a clean secondary prompt when the seed buffer contains \_ lines
            // so the JLine "|" decoration doesn't appear before the \_ marker.
            final boolean hasChatLines = null != this.seedBuffer && this.seedBuffer.contains("\\_ ");
            this.reader.setVariable(LineReader.SECONDARY_PROMPT_PATTERN,
                    hasChatLines
                            ? Graphitty.string("{{-X-}}{{v1&^1&m}}")
                            : Graphitty.string("{{-X&v1&^1&m}}     {{g}}| {{X}}"));
        }

        // Redraw floating widgets before every prompt so they stay pinned
        // as console output scrolls underneath them.
        getFloatingSurface().render();
    }

    // ========== Pane Management ==========

    public Pane getActivePane() {
        return this.panes.activePane();
    }

    public List<Pane> getAllPanes() {
        return this.panes.getAllPanes();
    }

    public boolean isSplitMode() {
        return this.panes.splitMode();
    }

    public boolean isTraceEnabled() {
        return this.traceEnabled;
    }

    public void setTraceEnabled(final boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
    }

    /**
     * Request a redraw of pane layout and floating widgets. Thread-safe.
     * Called by panes when their output buffer changes and by floating
     * widgets when their content updates.  The actual render is submitted
     * to the FloatingSurface render thread and returns immediately.
     */
    public void requestRedraw() {
        this.needsRedraw.set(true);
        final FloatingSurface surface = getFloatingSurface();
        if (!surface.isEmpty()) {
            surface.render();
        }
    }

    /**
     * Split the active pane in the given direction.
     *
     * @param direction VERTICAL (left|right) or HORIZONTAL (top|bottom)
     * @return the newly created pane
     */
    public Pane split(final SplitLayout direction) {
        return this.panes.split(this, direction);
    }

    /**
     * Close the active pane. If it's the last pane, do nothing.
     */
    public void closeActivePane() {
        this.panes.closeActivePane(this);
    }

    /**
     * Focus a specific pane by ID.
     */
    public void focusPane(final int paneId) {
        this.panes.focusPane(this, paneId);
    }

    /**
     * Cycle to the next pane.
     */
    public void nextPane() {
        this.panes.nextPane(this);
    }

    /**
     * Cycle to the previous pane.
     */
    public void prevPane() {
        this.panes.prevPane(this);
    }

    /**
     * Resize the active pane by adjusting its parent container's split ratio.
     *
     * @param delta positive = more space for active pane, negative = less space
     */
    public void resizeActivePane(final float delta) {
        this.panes.resizeActivePane(this, delta);
    }

    // ========== Floating Widget Focus (mirrors the pane focus above) ==========

    /**
     * The console's navigation over its pinned floating widgets — focus,
     * cycling, resizing, viewport scrolling, and the reasserted built-in
     * key bindings (see {@link WidgetNavigator}).
     */
    private final WidgetNavigator widgetsNav = new WidgetNavigator(this);

    /**
     * The console's pointer — the click/drag gestures on the pinned widgets,
     * the terminal mouse-tracking mode, and the hand-back to the terminal
     * (see {@link MousePointer}).
     */
    private final MousePointer pointer = new MousePointer(this);

    /**
     * The console's execution engine: one prompt line in, its segments
     * parsed, each run through a machine, and the results streamed back
     * to the console (see {@link Executor}).
     */
    private final Executor executor = new Executor(this);

    /**
     * Re-arm the pointer after a widget (re)gains focus — a wheel released
     * to the terminal is handed back to the widgets.
     */
    void rearmPointer() {
        this.pointer.rearmPointer();
    }

    /**
     * @return true when the console's floating surface has at least one pinned widget
     */
    public boolean hasFloatingWidgets() {
        return this.widgetsNav.hasFloatingWidgets();
    }

    /**
     * Register a built-in key binding (handler identity remembered) so that
     * {@link #reassertBuiltinKeys} can reclaim the sequence if a later binder
     * shadows it (a menu line key, a tool, anything bound after startup).
     */
    public void registerBuiltinKey(final String sequence, final Object handler) {
        this.widgetsNav.registerBuiltinKey(sequence, handler);
    }

    /**
     * Reclaim built-in key bindings that a later binder has overridden on the
     * shared "main" keymap.  Called before every prompt, so a builtin key can
     * never stay shadowed beyond a single session turn — the shadow only
     * lasts until the next read.
     */
    public void reassertBuiltinKeys() {
        this.widgetsNav.reassertBuiltinKeys();
    }

    /**
     * Reclaim each registered sequence for the handler it was bound with,
     * when a later binder (menu line key, tool, anything) has overwritten it
     * on the shared "main" keymap.  Identity comparison of the handler
     * objects — no reflection, no logging, O(sequence count).  Idempotent:
     * a pass that finds every builtin intact changes nothing.
     */
    static void reassertBuiltin(final KeyMap<Binding> keyMap, final java.util.Map<String, Object> registered) {
        for (final java.util.Map.Entry<String, Object> entry : registered.entrySet()) {
            if (keyMap.getBound(entry.getKey()) != entry.getValue()) {
                keyMap.bind((Binding) entry.getValue(), entry.getKey());
            }
        }
    }

    /**
     * @return the currently bound handler for the given key sequence (may be null)
     */
    public Object boundKeyHandler(final String sequence) {
        return this.widgetsNav.boundKeyHandler(sequence);
    }

    /**
     * @return the handler the console registered for the builtin (may be null)
     */
    public Object builtinKeyHandler(final String sequence) {
        return this.widgetsNav.builtinKeyHandler(sequence);
    }

    /**
     * @return the sequences the console keeps as reasserted builtins
     */
    public java.util.Set<String> builtinKeySequences() {
        return this.widgetsNav.builtinKeySequences();
    }

    /**
     * @return the pinned floating widgets in deterministic focus-cycling order
     * (see {@link FloatingSurface#widgets()})
     */
    public List<Widget<?>> getFloatingWidgets() {
        return this.widgetsNav.getFloatingWidgets();
    }

    /**
     * @return the currently focused floating widget, or null when nothing
     * is focused (or the focused widget was removed / re-floated into
     * an unknown key since)
     */
    public Widget<?> getActiveWidget() {
        return this.widgetsNav.getActiveWidget();
    }

    /**
     * Focus the given floating widget (pass null to clear the focus).
     * Triggers a re-render so the focus marker lands immediately.
     */
    public void focusWidget(final Widget<?> widget) {
        this.widgetsNav.focusWidget(widget);
    }

    /**
     * Cycle the focus to the next floating widget (wrapping).  A no-op when
     * no widgets are pinned.
     */
    public void nextWidget() {
        this.widgetsNav.nextWidget();
    }

    /**
     * Cycle the focus back to the previous floating widget (wrapping).
     */
    public void prevWidget() {
        this.widgetsNav.prevWidget();
    }

    /**
     * Grow the focused floating widget's width by one step.  Which edge
     * moves is decided by the widget's anchor: a left-anchored widget
     * extends its right edge, a right-anchored widget pulls in its left.
     */
    public void growActiveWidgetWidth() {
        this.widgetsNav.growActiveWidgetWidth();
    }

    /**
     * Shrink the focused floating widget's width by one step.
     */
    public void shrinkActiveWidgetWidth() {
        this.widgetsNav.shrinkActiveWidgetWidth();
    }

    /**
     * Grow the focused floating widget's height by one step.  Which edge
     * moves is decided by the widget's anchor: a bottom-anchored widget
     * pushes its top edge up, a top-anchored widget pushes its bottom down.
     */
    public void growActiveWidgetHeight() {
        this.widgetsNav.growActiveWidgetHeight();
    }

    /**
     * Shrink the focused floating widget's height by one step.
     */
    public void shrinkActiveWidgetHeight() {
        this.widgetsNav.shrinkActiveWidgetHeight();
    }

    /**
     * Scroll the focused floating widget's viewport by {@code (dx, dy)} cells.
     *
     * @return true when the focused widget accepted the scroll
     */
    public boolean scrollActiveWidget(final int dx, final int dy) {
        return this.widgetsNav.scrollActiveWidget(dx, dy);
    }

    /**
     * Scroll the focused floating widget by one viewport page.
     */
    public boolean pageActiveWidget(final int direction) {
        return this.widgetsNav.pageActiveWidget(direction);
    }

    /**
     * Put the focused floating widget's viewport back on its newest content.
     */
    public boolean tailActiveWidget() {
        return this.widgetsNav.tailActiveWidget();
    }

    /**
     * Jump the focused floating widget's viewport to an absolute body row
     * (clamped to the content).
     */
    public boolean scrollActiveWidgetTo(final int row) {
        return this.widgetsNav.scrollActiveWidgetTo(row);
    }

    /**
     * @return true when the focused floating widget has content off its
     * viewport that a scroll would reveal
     */
    public boolean activeWidgetScrolls() {
        return this.widgetsNav.activeWidgetScrolls();
    }

    /**
     * A one-line description of the focused widget's viewport, e.g.
     * {@code rows 12-31/240} (empty when it is not scrollable).
     */
    public String activeWidgetScrollInfo() {
        return this.widgetsNav.activeWidgetScrollInfo();
    }

    /**
     * A pointer click at a terminal cell: the widget under the pointer is
     * focused, its affordances get first refusal, and a click on empty
     * terminal clears the focus.
     *
     * @param row 1-based terminal row of the click
     * @param col 1-based terminal column of the click
     * @return true when a widget consumed the click for itself
     */
    public boolean clickAt(final int row, final int col) {
        return this.pointer.clickAt(row, col);
    }

    /**
     * As {@link #clickAt(int, int)} with a held control key: on a link it means
     * follow the uri (type it AND submit it) rather than only type it.
     *
     * @return true when a widget consumed the click for itself
     */
    public boolean clickAt(final int row, final int col, final boolean follow) {
        return this.pointer.clickAt(row, col, follow);
    }

    /**
     * A pointer press: taking hold of one of the focused widget's handles
     * starts a drag gesture; anything else keeps the click semantics.
     *
     * @param row 1-based terminal row of the press
     * @param col 1-based terminal column of the press
     * @return true when the press was consumed — by the widget's affordance or by a grab
     */
    public boolean mousePressed(final int row, final int col) {
        return this.pointer.mousePressed(row, col);
    }

    /**
     * As {@link #mousePressed(int, int)} with a held control key: on a link it
     * means follow the uri (type it AND submit it) rather than only type it.
     */
    public boolean mousePressed(final int row, final int col, final boolean follow) {
        return this.pointer.mousePressed(row, col, follow);
    }

    /**
     * A pointer motion with a button held: the grabbed handle follows the pointer — the
     * chevron moves the widget to where the pointer is, the corner marker sizes the box
     * by how far the pointer has come from where it took hold.
     *
     * @return true when a gesture is in flight
     */
    public boolean mouseDragged(final int row, final int col) {
        return this.pointer.mouseDragged(row, col);
    }

    /**
     * A pointer release: park the widget — write what the gesture changed into
     * its style so it survives re-hydration — and end the gesture.
     *
     * @return true when a gesture was in flight
     */
    public boolean mouseReleased(final int row, final int col) {
        return this.pointer.mouseReleased(row, col);
    }

    /**
     * True while the pointer is moving a widget.
     */
    public boolean dragging() {
        return this.pointer.dragging();
    }

    /**
     * Turn terminal mouse tracking on or off to match what is on screen — the
     * pointer belongs to the widgets while any of them is on screen (or one
     * is focused), and to the terminal only with nothing pinned.
     */
    public void syncWidgetMouseTracking() {
        this.pointer.syncWidgetMouseTracking();
    }

    /**
     * As {@link #syncWidgetMouseTracking()}, re-asserting the mode even when
     * it has not changed — used once per prompt, because jline releases mouse
     * tracking at the end of every readLine.
     *
     * @param force re-assert the mode even when it has not changed
     */
    public void syncWidgetMouseTracking(final boolean force) {
        this.pointer.syncWidgetMouseTracking(force);
    }

    /**
     * Whether terminal mouse tracking is currently owned by widget scrolling.
     */
    public boolean widgetMouseTracking() {
        return this.pointer.widgetMouseTracking();
    }

    /**
     * Hand the pointer back to the terminal (disable mouse tracking) so the
     * wheel scrolls the terminal's own scrollback.  Re-armed on the next
     * prompt or when a widget is focused via {@code alt}+{@code w}.
     */
    public void releasePointer() {
        this.pointer.releasePointer();
    }

    /**
     * Make the screen the truth again after something drew outside it.
     * <p>
     * The screen paints rows at absolute positions and keeps its own idea of what is on each one;
     * anything written straight to the terminal — an answer echoed while a job held the console —
     * leaves that idea wrong, and the next paint then fights the write that follows it.
     * <p>
     * Only the forgetting happens here.  requestScreenPaint() runs the paint pass on
     * whatever thread asks, and this is called from the reader: a frame drawn there waits on the
     * render lock while holding the reader, which is a console that stops responding until the
     * terminal gives up on it.  Marking the rows unknown is enough — the next output or prompt
     * repaints them, and repainting stale rows is exactly what invalidating prevents.
     */
    void resyncScreen() {
        this.screenView.resyncScreen();
    }

    /**
     * Hand the terminal back on the way out.
     * <p>
     * Mouse tracking is a terminal mode, not a console flag: the terminal keeps reporting presses,
     * drags and the wheel until something turns it off.  Exiting with it armed leaves the shell
     * receiving mouse bytes for every drag, so nothing outside metatron can be selected — and the
     * sequences type themselves into the next command.  The mode may be armed by the console
     * itself or by jline (a modal tool), so the release is unconditional no matter which, and it
     * has to happen on every exit path: quit, ctrl-c, or the launcher restarting the VM.
     */
    private void releaseTerminalOnExit() {
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                this.pointer.releaseTerminalModesForExit(), "metatron-console-teardown"));
    }

    /**
     * Who owns the pointer: the widgets while one of them is focused, or while
     * any widget is on screen and the pointer has not been released.  A wheel
     * over empty terminal releases the pointer ({@link #releasePointer()}), so
     * the terminal keeps its own mouse (wheel scrollback, drag-selection) until
     * the next prompt or a widget is re-focused via {@code alt}+{@code w}.  A
     * pinned widget otherwise keeps the pointer armed so a click can always
     * focus (or re-focus) one.  Native text selection is still available via
     * Shift+drag.
     *
     * @param focused  a widget currently holds the focus
     * @param released the pointer was handed back to the terminal
     * @param widgets  widgets with a drawn region on screen
     */
    static boolean pointerWanted(final boolean focused, final boolean released, final int widgets) {
        return !released && (focused || widgets > 0);
    }

    /**
     * Position the cursor at the active pane's prompt location.
     * Called after operations that move the cursor (like status refresh).
     */
    public void positionCursorInActivePane() {
        if (this.panes.activePane() == null) return;
        final int[] pos = panes.calculatePanePosition(this, this.panes.activePane());
        if (pos == null) return; // activePane not in tree yet (e.g. during split transition)
        final int promptRow = pos[0] + pos[2] - 2; // startRow + height - 2
        final int promptCol = pos[1] + 1; // startCol + 1 to skip left border
        terminal.writer().print("\u001b[" + promptRow + ";" + promptCol + "H");
        terminal.writer().flush();
    }

    /**
     * Calculate a pane's position (startRow, startCol, height, width) by
     * traversing the tree — this ensures we always have the correct position
     * regardless of render state.
     *
     * @param pane the pane to locate
     * @return int[] {startRow, startCol, height, width}
     */
    public int[] calculatePanePosition(final Pane pane) {
        return this.panes.calculatePanePosition(this, pane);
    }

    /**
     * Render only the content rows of a single pane, in-place, with no screen clear.
     * <p>
     * Uses ANSI save/restore cursor ({@code \033[s} / {@code \033[u}) to preserve the
     * exact cursor position the user was typing at, including mid-buffer column offset.
     * Without this, repositioning to the start of the prompt line would cause JLine to
     * echo subsequent keystrokes at column 1 instead of where the user left off.
     */
    public void renderSinglePaneContent(final Pane pane) {
        if (!this.panes.splitMode() || pane == null) return;
        final int[] pos = calculatePanePosition(pane);
        if (pos == null) return;
        // Save cursor — preserves exact row/col (including buffer offset)
        terminal.writer().print("\033[s");
        pane.renderContentOnly(terminal, pos[0], pos[1], pos[2], pos[3]);
        // Restore cursor to where the user was mid-input
        terminal.writer().print("\033[u");
        terminal.writer().flush();
    }

    /**
     * Output-change listener registered on every pane via {@link Pane#setOutputListener}.
     * <p>
     * Pane calls this after every {@code appendOutput}. This method owns all
     * throttling/rendering logic — Pane itself stays free of rendering concerns.
     */
    void onPaneOutputChanged(final Pane pane) {
        if (!this.panes.splitMode()) return;

        // Defer rendering while the user is actively typing so the cursor doesn't
        // jump to a different pane mid-input.  After the idle threshold expires,
        // the next output event will render; when readLine() finally returns the
        // REPL loop flushes everything.
        if (deferNonActivePaneRender()) return;

        final long now = System.currentTimeMillis();
        if (now - this.lastPaneRenderMs >= PANE_RENDER_THROTTLE_MS) {
            this.lastPaneRenderMs = now;
            renderSinglePaneContent(pane);
        }
        // Within the throttle window: visibleOutput() always tails the buffer, so
        // the next unthrottled call automatically shows all lines written since.
    }

    /**
     * Returns {@code true} when the user is actively typing and we should defer
     * non-active-pane rendering.  As a side effect sets {@link #pendingPaneFlush}
     * so the REPL loop can drain accumulated output once input completes.
     *
     * <p>Keystroke detection works by polling {@code reader.getBuffer().length()}
     * on every call (which fires whenever a background pane has output to render).
     * If the buffer length changed since the last poll the user typed something,
     * so we reset the idle timer.  Once the keyboard has been idle at least
     * {@link #INPUT_IDLE_THRESHOLD_MS} the method returns {@code false} and
     * rendering proceeds — the user is reading or thinking, cursor detours are safe.
     */
    boolean deferNonActivePaneRender() {
        if (!this.inReadLine) return false;
        // Poll JLine's buffer — length changes mean the user typed.
        // getBuffer() is safe to read cross-thread (returns live Buffer ref).
        final int currentLen = this.reader.getBuffer().length();
        if (currentLen != this.lastBufferLength) {
            this.lastBufferLength = currentLen;
            this.lastKeyActivityMs = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - this.lastKeyActivityMs >= INPUT_IDLE_THRESHOLD_MS) {
            return false; // keyboard idle long enough — allow render
        }
        this.pendingPaneFlush = true;
        return true;
    }

    /**
     * Render all panes to the terminal. Called when in split mode.
     * Deferral is enabled by default — background subscription callbacks will
     * not steal the cursor while the user is mid-input.
     */
    public void renderPanes() {
        renderPanes(true);
    }

    /**
     * Force immediate pane render — never defers for typing.
     */
    public void renderPanesNow() {
        renderPanes(false);
    }

    /**
     * @param deferIfTyping when true (background output), rendering is deferred while
     *                      the user is actively typing.  Pass false for user-initiated
     *                      widget actions (split, resize) that must render immediately.
     */
    private void renderPanes(final boolean deferIfTyping) {
        if (!this.panes.splitMode()) return;

        // Defer non-essential rendering while the user is actively typing.
        if (deferIfTyping && deferNonActivePaneRender()) return;

        // Get terminal dimensions (leave room for status line)
        final int height = terminal.getHeight() - 1;  // -1 for status line only
        final int width = terminal.getWidth();

        // Clear pane area row-by-row with \033[K (erase-to-end-of-line).
        // Each row is a single atomic ANSI operation — far faster than writing
        // N×width spaces (which caused visible flicker), and unlike \033[J it
        // never touches the status line row.
        for (int row = 1; row <= height; row++) {
            terminal.writer().print("\u001b[" + row + ";1H\u001b[K");
        }

        // Render pane tree (this updates each pane's region tracking)
        this.panes.root().render(terminal, 1, 1, height, width, this.panes.activePane());

        // Position cursor at active pane's prompt location (use dynamic calculation)
        final int[] pos = panes.calculatePanePosition(this, this.panes.activePane());
        if (pos == null) {
            // activePane not found in tree (race or stale ref) – flush and bail
            terminal.writer().flush();
            return;
        }
        final int promptRow = pos[0] + pos[2] - 2; // startRow + height - 2
        final int promptCol = pos[1] + 1; // startCol + 1 to skip left border
        final int paneWidth = pos[3] - 2; // width - 2 for left and right borders

        // Clear the line ABOVE the prompt (where JLine's ~ marker might appear)
        final int lineAbove = promptRow - 1;
        if (lineAbove >= pos[0]) { // Only if within pane bounds
            terminal.writer().print("\u001b[" + lineAbove + ";" + promptCol + "H");
            terminal.writer().print(" ".repeat(Math.max(0, paneWidth)));
        }

        // Clear the prompt line within the pane
        terminal.writer().print("\u001b[" + promptRow + ";" + promptCol + "H");
        terminal.writer().print(" ".repeat(Math.max(0, paneWidth)));
        terminal.writer().print("\u001b[" + promptRow + ";" + promptCol + "H");

        terminal.writer().flush();

        // Redraw floating widgets on top of pane content
        getFloatingSurface().render();

        this.needsRedraw.set(false);
    }

    // ── the console's own screen ─────────────────────────────────────

    /**
     * {@link #renderScreen(boolean)} with background-output deferral.
     */
    public void renderScreen() {
        this.renderScreen(true);
    }

    /**
     * Paint the screen region from the console's own buffer, then let the floating
     * widgets redraw over it.
     *
     * <p>The frame goes through {@link FloatingSurface#writeToTerminal(String)} —
     * the console's single writer — so a screen frame can never interleave with a
     * widget frame.  Painting the region is also what puts back text a widget
     * covered: the widget erased its old box with blanks, and nothing in the
     * terminal remembers what was there, but the buffer does.
     *
     * <p>The cursor is handed back to the prompt row afterwards, because that row
     * belongs to jline: it draws the prompt there, and moves the cursor as the user
     * types.
     *
     * @param deferIfTyping when true (background output), a repaint is deferred
     *                      while the user is actively typing — the same deferral
     *                      pane rendering uses, and for the same reason: yanking
     *                      the cursor out from under a half-typed line
     */
    private void renderScreen(final boolean deferIfTyping) {
        this.screenView.renderScreen(deferIfTyping);
    }

    /**
     * Scroll the console's own transcript by {@code delta} rows (negative goes back into
     * history, positive towards the newest) and repaint it.
     *
     * <p>The wheel over the transcript lands here rather than handing the pointer back to
     * the terminal: the rows are the console's now, so a terminal scrollback is not where
     * this transcript lives, and releasing the pointer cost the widgets the mouse (a click
     * on one then needed {@code alt}+{@code w} to get it back).  A wheel that reaches the
     * tail again resumes following, so scrolling back down needs no separate gesture.
     */
    public void scrollScreen(final int delta) {
        this.screenView.scrollScreen(delta);
    }

    /**
     * Repaint the screen region now, without blocking the caller's interest in the result.
     */
    private void repaintScreen() {
        this.screenView.repaintScreen();
    }

    boolean openScreenLink(final int row, final int col, final boolean follow) {
        return this.screenView.openScreenLink(row, col, follow);
    }


    /**
     * Put back the rows a widget erased, then draw the widgets back over them.
     *
     * <p>The surface reports the rows it blanked — a moved widget's old box, a removed
     * widget's box, a resized box's leftovers — because it cannot know what those rows
     * held.  The screen can: it keeps the content, so the rows are <em>forgotten</em>
     * there, which obliges the next frame to paint them, and a paint is asked for.  No
     * repair is ever lost that way, where queued repair bytes could be coalesced away
     * along with the pass that needed them.
     *
     * @param from 1-based first terminal row erased
     * @param to   1-based last terminal row erased
     */
    void repairRows(final int from, final int to) {
        this.screenView.repairRows(from, to);
    }

    /**
     * An interactive tool (explain, tree select, …) takes over the terminal's rows.
     * While it is up, the screen neither records its frames as transcript content for
     * the painter nor repaints the region under them — the tool redrew in place with
     * its own cursor math, and a paint in between moves the cursor it redraws against,
     * which is how a table doubled itself on every key.  No-op outside screen mode.
     */
    public void beginInteractiveTool() {
        this.screenView.enterTool();
    }

    /**
     * The tool handed the terminal back: its last frame settles into the transcript
     * (what the tool displayed is what the reader scrolls back to), and the region
     * repaints from that content.
     *
     * @param finalFrame the tool's last frame, or null when it leaves no content
     */
    public void endInteractiveTool(final String finalFrame) {
        this.screenView.exitTool(finalFrame);
    }

    /**
     * The console's screen (see {@link ConsoleScreen}) — for commands that report on it.
     */
    public ConsoleScreen getScreen() {
        return this.screenView.screen();
    }

    public StatusLine getStatus() {
        return this.status;
    }

    public ConfigurationPath getConfigurations() {
        return Console.configurations;
    }

    protected void printResult(final Obj result) {
        if (this.panes.splitMode() && this.panes.activePane() != null) {
            this.panes.activePane().appendResult(result);
        } else {
            result.stream().takeWhile(o -> !this.jobs.interruptRequested())
                    .forEach(o -> {
                        this.write("{{-X-}}{{m}}=={{g}}>{{X}}");
                        this.write(Highlighter.format(this.serializer.write(o)));
                        this.write("\n");
                    });
            terminal.flush();
            // the results went into the screen's buffer (screen mode) rather than the
            // terminal, so this is where they reach the screen
            this.renderScreen(false);
        }
    }

    private void renderTrace(final Obj failObj) {
        if (this.panes.splitMode() && this.panes.activePane() != null)
            this.panes.activePane().appendOutput(formatTraceAnsi(failObj), false);
        else
            this.write(formatTraceMarkup(failObj));
    }

    /**
     * If {@link Tracer#java_stack} is enabled, prompt the user to launch the
     * {@link TraceTool} for each fail object.  Falls back to
     * {@link #renderTrace} when the user declines.
     */
    void promptTraceForFails(final Obj result) {
        if (!Tracer.java_stack.enabled()) return;
        result.stream().filter(Obj::isFail).forEach(failObj -> {
            terminal.writer().write(Graphitty.string("{{y}}display trace tool? {{g}}[y/N]{{y}} {{X}}"));
            terminal.writer().flush();
            // Enter raw mode so Ctrl-C / Ctrl-D arrive as bytes (0x03/0x04) rather
            // than a swallowed SIGINT/EOF that would block the read and lock the
            // REPL.  Restore the prior mode and drain any leftover input after.
            final org.jline.terminal.Attributes saved = terminal.enterRawMode();
            try {
                final int ch = terminal.reader().read();
                if (ch == 'y' || ch == 'Y') {
                    terminal.writer().write("\n");
                    final TraceTool traceTool = new TraceTool(failObj.asFail());
                    if (Console.this.panes.splitMode() && Console.this.panes.activePane() != null) {
                        final int[] pos = Console.this.panes.calculatePanePosition(Console.this, Console.this.panes.activePane());
                        if (pos != null) traceTool.setPaneBounds(pos[0], pos[1], pos[2], pos[3]);
                    }
                    Utilities.runCursorLessWidget(traceTool, true);
                    if (Console.this.panes.splitMode()) {
                        Console.this.renderPanes(false);
                    }
                    redrawBuffer();
                } else {
                    terminal.writer().write("\n");
                    renderTrace(failObj);
                }
            } catch (final Exception e) {
                renderTrace(failObj);
            } finally {
                this.drainTerminalInput();
                terminal.setAttributes(saved);
            }
        });
    }

    /**
     * Consume any input left in the terminal buffer (e.g. the remainder of an
     * arrow-key escape sequence after a single-byte read) so it doesn't leak
     * into the next {@code readLine()} as garbage.  Best-effort only.
     */
    private void drainTerminalInput() {
        try {
            while (terminal.reader().ready()) {
                if (terminal.reader().read() < 0) break;
            }
        } catch (final Exception ignored) {
            // never let draining break the REPL
        }
    }

    /**
     * Walk the fail's Throwable cause chain and render as Graphitty markup.
     * The mtron cause chain is threaded through {@link Throwable#getCause()}.
     */
    private static String formatTraceMarkup(final Obj failObj) {
        final StringBuilder sb = new StringBuilder();
        // Collect the full cause chain first so we know which entries have siblings
        final java.util.List<Throwable> chain = new java.util.ArrayList<>();
        Throwable throwable = failObj.asFail().jvm();
        while (null != throwable) {
            chain.add(throwable);
            throwable = throwable.getCause();
        }

        for (int i = 0; i < chain.size(); i++) {
            final boolean last = (i == chain.size() - 1);
            final Throwable current = chain.get(i);

            // Build tree prefixes
            final StringBuilder depthBar = new StringBuilder();
            for (int d = 0; d < i; d++)
                depthBar.append("{{y}}│{{X}}  ");
            // Cause line: depth bars + branch connector
            sb.append(depthBar).append(last ? "{{y}}└─{{X}}" : "{{y}}├─{{X}}")
                    .append("{{y}}").append(current.toString()).append("{{X}}\n");
            // Frame prefix: depth bars + continuation under this cause
            final StringBuilder framePrefix = new StringBuilder(depthBar);
            framePrefix.append(last ? "   " : "{{y}}│{{X}}  ");
            for (final StackTraceElement frame : current.getStackTrace()) {
                sb.append("{{k}}").append(framePrefix).append("at ").append(frame.toString()).append("{{X}}\n");
            }
        }
        return sb.toString();
    }

    /**
     * Walk the fail's cause chain and render as ANSI (for pane appendOutput).
     */
    private static String formatTraceAnsi(final Obj failObj) {
        return Highlighter.format(formatTraceMarkup(failObj));
    }

    /**
     * Print to a specific pane (for background threads).
     */
    public void printResultToPane(final Pane pane, final Obj result) {
        pane.appendResult(result);
    }

    protected void execute(final String line) {
        this.executor.execute(line);
    }


    /**
     * Poll a foreground job's future until it completes, watching the terminal
     * while we wait — {@code <alt>+b} detaches, ctrl-c stops, {@code [q]}
     * cancels once the offer has been printed, other keystrokes are kept for
     * the next prompt.  The protocol itself lives on
     * {@link ForegroundJobs#awaitForeground(Machine, FutureObj, String)}.
     *
     * @return true when the job was detached with {@code <alt>+b} (it is still
     * running — the console returns to the prompt)
     */
    boolean awaitForeground(final Machine mach, final FutureObj<Obj> future, final String line) {
        return this.jobs.awaitForeground(mach, future, line);
    }

    /**
     * Read the terminal while a foreground job holds the console and classify
     * each keystroke through {@link Hotkeys} — served on a dedicated thread so
     * the repl's poll loop stays live.  The watcher loop lives on
     * {@link ForegroundJobs#watchTerminal()}; it hands keystrokes that are not
     * detach/interrupt/cancel back to the line being typed
     * ({@link #injectTypedText()}).
     */
    private void watchTerminal() {
        this.jobs.watchTerminal();
    }

    /**
     * Echo a keystroke the watcher took while the job ran.  Raw mode is on for
     * the life of the job, so the terminal does not echo for us — without this,
     * typing ahead of a long job would be invisible.
     */
    void echoTypedChar(final int c) {
        try {
            terminal.writer().print((char) c);
            terminal.writer().flush();
        } catch (final Exception e) {
            // echo is a courtesy — never let it break the repl
        }
    }

    /**
     * Hand a keystroke the watcher took as the foreground job ended back to the
     * line the user is typing (or to the next prompt when it missed that window).
     */
    void injectTypedText() {
        final String text = this.jobs.hotkeys().takePendingText();
        if (text.isEmpty())
            return;
        if (this.inReadLine) {
            try {
                // the prompt is live: the keystroke belongs on the line being
                // typed.  jline redraws the line for us so the display keeps its
                // own bookkeeping (a raw terminal write could confound it).
                this.reader.getBuffer().write(text);
                this.reader.callWidget(LineReader.REDRAW_LINE);
                return;
            } catch (final Exception e) {
                // the text stays in the buffer — the next keystroke draws it
                return;
            }
        }
        this.seedBuffer = null == this.seedBuffer ? text : this.seedBuffer + text;
    }

    /**
     * what the foreground poll loop does with the keys it just read
     */
    enum ForegroundStep {WAIT, DETACH, INTERRUPT, CANCEL, OFFER_CANCEL}

    /**
     * Decide the next step of the foreground poll loop from the keys typed
     * while the job runs.  Kept static and terminal-free so the decision table
     * — detach beats cancel, ctrl-c still stops the job, a widget on screen
     * suppresses the cancel offer, the offer waits out its interval — is
     * testable on its own.
     *
     * @param detach    {@code <alt>+b} was pressed
     * @param interrupt ctrl-c arrived as a byte (raw mode keeps sigint alive, so
     *                  this is the fallback when the terminal disables it)
     * @param cancel    {@code [q]} was pressed while the cancel offer stood
     * @param userMode  a widget owns the console (no cancel offer, as before)
     * @param nowMs     the current time
     * @param offerAtMs when the next cancel offer is due
     */
    static ForegroundStep foregroundStep(final boolean detach, final boolean interrupt, final boolean cancel,
                                         final boolean userMode, final long nowMs, final long offerAtMs) {
        if (detach) return ForegroundStep.DETACH;
        if (interrupt) return ForegroundStep.INTERRUPT;
        if (cancel) return ForegroundStep.CANCEL;
        if (!userMode && nowMs >= offerAtMs) return ForegroundStep.OFFER_CANCEL;
        return ForegroundStep.WAIT;
    }

    /**
     * @return the jobs detached with {@code <alt>+b} that have not yet halted,
     * in detach order
     */
    public List<Machine> backgroundJobs() {
        return this.jobs.backgroundJobs();
    }

    /**
     * @return the thread vids of the jobs detached with {@code <alt>+b} — each
     * one is addressable at {@code /sys/thread/<vid>}
     */
    public List<fURI> backgroundJobVids() {
        return this.jobs.backgroundJobVids();
    }

    /**
     * Stop every detached job.  Returns the number of jobs stopped.
     */
    public int stopBackgroundJobs() {
        return this.jobs.stopBackgroundJobs();
    }

    /**
     * @return a single-line, clipped echo of the console line that started a job
     */
    static String preview(final String line) {
        if (null == line) return "";
        final String flat = line.replace('\n', ' ').trim();
        return flat.length() <= DETACH_PREVIEW_CHARS
                ? flat
                : flat.substring(0, DETACH_PREVIEW_CHARS - 3) + "...";
    }

    public void redrawBuffer() {
        ReaderSetup.redrawBuffer(this);
    }

    /**
     * The live input line as the user sees it while typing: syntax color and
     * nothing else.  The rules for keeping the user's markup literal in it
     * live on {@link ReaderSetup#redrawLine(Console)}.
     */
    public String redrawLine() {
        return ReaderSetup.redrawLine(this);
    }

    public void run() {
        while (BOOTING) {
            CommonUtil.sleepThread(10);
        }
        CommonUtil.sleepThread(50);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Position cursor at active pane before reading input
                this.prepareForInput();
                // someone else's line first: a job asking the human (see readHumanLine).  It is
                // answered here rather than executed as a command -- the human was answering a
                // question, not typing mtron.
                final HumanBroker.HumanRead asked = this.human.poll();
                if (null != asked) {
                    this.inReadLine = true;
                    // readLine answers null at end of input (ctrl-d, a closed stream): that is not a
                    // line and must not be trimmed -- it is the end of the answers, which the caller
                    // sees as mSystem.readLine() returning null
                    final String read = this.reader.readLine(this.promptFrom(asked.prompt()));
                    this.inReadLine = false;
                    asked.answer().complete(null == read ? null : read.trim());
                    // the reader drew the answer where the screen did not expect it (see the watcher's
                    // answer path), so settle the layout before the next prompt is drawn on top of it
                    this.resyncScreen();
                    continue;
                }
                this.inReadLine = true;
                this.lastKeyActivityMs = System.currentTimeMillis();
                this.lastBufferLength = 0; // fresh buffer for new readLine
                // keys typed while a foreground job held the console seed this
                // prompt, so typing ahead of a long job is never lost
                final String typedAhead = this.jobs.hotkeys().takePendingText();
                if (!typedAhead.isEmpty())
                    this.seedBuffer = null == this.seedBuffer ? typedAhead : this.seedBuffer + typedAhead;
                final String read = null != this.seedBuffer
                        ? this.reader.readLine(this.prompt(), null, (MaskingCallback) null, this.seedBuffer)
                        : this.reader.readLine(this.prompt());
                // null is end of input, not a line to trim
                final String line = null == read ? "" : read.trim();
                this.seedBuffer = null;
                this.inReadLine = false;
                // Drain any agent output that was deferred while the user was typing.
                if (this.pendingPaneFlush) {
                    this.pendingPaneFlush = false;
                    if (this.panes.splitMode()) this.renderPanes();
                    else this.renderScreen(false);
                }
                // --- Expression overlay (\_) detection ---
                // Each \_ line is an expression stored in the metatron-addressable
                // expressionStack Lst (outermost→innermost).  ENTER evaluates the
                // deepest (last), pops it, and rebuilds the seed buffer from the
                // remaining lines.
                final int firstUscore = line.indexOf("\\_ ");
                if (firstUscore >= 0 && line.lastIndexOf('\n', firstUscore) >= 0) {
                    final String[] rawLines = line.split("\n");
                    final String mtronPart = rawLines[0].trim();
                    // Reset and refill expressionStack: >>0 = mtron> line,
                    // >>1 = outermost \_, ..., >> -1 = deepest (top of stack).
                    this.expressionStack = lst();
                    this.expressionStack.add(str(mtronPart), Poly.MUTABLE);
                    for (int i = 1; i < rawLines.length; i++) {
                        final int uScore = rawLines[i].indexOf("\\_ ");
                        if (uScore >= 0) {
                            this.expressionStack.add(
                                    str(rawLines[i].substring(uScore + 3).trim()),
                                    Poly.MUTABLE);
                        }
                    }
                    // Evaluate the deepest expression (>> -1, top of stack)
                    if (this.expressionStack.count() > 1) {
                        final String chatPart = this.expressionStack.at(jnt(-1)).strValue();
                        if (!chatPart.isEmpty()) {
                            this.status.startTimer();
                            if (this.panes.splitMode() && this.panes.activePane() != null) {
                                this.panes.activePane().appendOutput(
                                        Graphitty.string("{{c}}  \\_{{X}} ") + Highlighter.format(chatPart));
                            }
                            this.execute(chatPart);
                            this.machine = null;
                            if (this.panes.splitMode()) {
                                this.renderPanes();
                            }
                        }
                        // Pop the evaluated expression (>> -1)
                        this.expressionStack
                                .at(jnt(-1), noobj(), Poly.MUTABLE);
                    }
                    // Rebuild seed buffer: >>0 (mtron>), then remaining \_ lines
                    // in display order (mtron> part first, then \_ lines).
                    final Obj[] remaining = this.expressionStack.elements().toArray(Obj[]::new);
                    if (remaining.length > 1) {
                        final int promptWidth = Highlighter.visualLength(PROMPT);
                        final StringBuilder sb = new StringBuilder(remaining[0].strValue());
                        for (int i = 1; i < remaining.length; i++) {
                            sb.append('\n');
                            sb.append(" ".repeat(promptWidth - 2 + (i - 1)));
                            sb.append("\\_ ").append(remaining[i].strValue());
                        }
                        this.seedBuffer = sb.toString();
                    } else {
                        this.seedBuffer = remaining.length == 1
                                ? remaining[0].strValue()
                                : "";
                        if (this.seedBuffer.isBlank()) this.seedBuffer = "";
                    }
                    continue;
                }
                // --- End expression overlay detection ---
                // An empty line can result from pane-switch (Ctrl+W clears the buffer and
                // calls accept-line to break out of readLine so the next iteration can
                // start fresh in the new pane).  Skip evaluation entirely.
                if (line.isEmpty()) {
                    // A bare Enter is still a turn: the screen's transcript answers it the
                    // way a terminal does — the prompt scrolls up as a row of its own and a
                    // fresh one takes its place — so pressing Enter twice reads as two
                    // prompts rather than as nothing happening.  In split mode an empty line
                    // is a pane switch and not a turn, so it stays invisible there.
                    //
                    // Only once the console owns the rows, though: while it is still
                    // appending, the terminal is showing jline's own prompt and jline's own
                    // Enter already ends that line — echoing here as well put a second
                    // newline on it, which is the stray blank line between the first
                    // prompts and none after.
                    if (screenMode() && !screenAppends()) this.screenView.screenOutput(this.prompt() + "\n");
                    continue;
                }
                if (line.startsWith(COLON)) {
                    final String cmd = line.substring(1).trim();
                    final int spaceIdx = cmd.indexOf(' ');
                    final String cmdName = spaceIdx > 0 ? cmd.substring(0, spaceIdx) : cmd;
                    final String cmdArgs = spaceIdx > 0 ? cmd.substring(spaceIdx + 1).trim() : "";
                    if (!this.at("menu" + "/" + cmdName).isNoObj()) {
                        this.at("menu" + "/" + cmdName).apply(cmdArgs.isEmpty() ? noobj() : str(cmdArgs));
                    }
                } else {
                    this.status.startTimer();
                    // Echo the input line to the pane's output (with prompt, syntax highlighted)
                    if (this.panes.splitMode() && this.panes.activePane() != null) {
                        // the line is the user's own: colored, never resolved (see Highlighter.line)
                        this.panes.activePane().appendOutput(Graphitty.string(PROMPT)
                                + studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter.line(line));
                    } else if (screenMode()) {
                        // The screen's transcript has to read like the conversation it is:
                        // the prompt and what was typed at it are history too — they scroll
                        // up and stay readable.  The prompt at the bottom of the screen is
                        // only the NEXT one; without this echo the reader sees answers with
                        // no questions.
                        //
                        // appendAnsi, not write: the typed line is not markup.  Resolving
                        // it through Graphitty would interpret what the reader typed — an
                        // argument carrying "\n" would break the echo across rows instead of
                        // showing the one line that was typed, and a brace would vanish as a
                        // rule.  The prompt is markup and is resolved here; the line is
                        // syntax-highlighted and appended verbatim.  The newline is the Enter
                        // itself, ending the echoed line so the result lands on the next row.
                        this.screenView.screen().appendAnsi(Graphitty.string(PROMPT)
                                + Highlighter.line(line) + "\n");
                    }
                    this.execute(line);
                    this.machine = null;
                    // Redraw panes after command execution to show output
                    if (this.panes.splitMode()) {
                        this.renderPanes();
                    }
                }
            } catch (final UserInterruptException e) {
                if (null != this.machine)
                    this.machine.stop();
                LOG.none(Graphitty.sillyPrint("\n\rmachine interrupted\n\r", true, true));
            } catch (final EndOfFileException e) {
                // eof ends the console the way quit does: the close drains the last
                // writes and hands the terminal back before the process goes
                this.close();
                System.exit(0);
            } catch (final Exception e) {
                Throwable x = e;
                int y = 0;
                while (null != x) {
                    LOG.error("\n%s%s", ((0 == y++) ? "" : (" ".repeat(y) + "\\_")), x.getMessage());
                    x = x.getCause();
                }
                final boolean isTypeMismatch = e instanceof TypeMismatchException;
                final String prompt = isTypeMismatch
                        ? "{{y}}display stack trace / type table {{g}}[y/N/t]{{y}}?{{X}} "
                        : "{{y}}display stack trace {{g}}[y/N]{{y}}?{{X}} ";
                String response = null;
                try {
                    response = this.reader.readLine(Highlighter.format(prompt));
                } catch (final UserInterruptException | EndOfFileException ignored) {
                    // Ctrl-C / Ctrl-D at the prompt = "no".  This readLine runs
                    // inside the catch block — letting these escape would bypass
                    // the loop's UserInterrupt/EndOfFile handlers, kill the REPL
                    // thread, and leave the terminal locked.
                }
                // null → Ctrl-C / Ctrl-D / EOF at the prompt; treat as "no"
                if (null != response && response.trim().equalsIgnoreCase("y")) {
                    e.printStackTrace();
                } else if (null != response && isTypeMismatch && response.trim().equalsIgnoreCase("t")) {
                    final TypeMismatchException tme = (TypeMismatchException) e;
                    terminal.writer().write("\n");
                    final TypeDiffTool widget = new TypeDiffTool(tme.instance(), tme.type());
                    if (Console.this.panes.splitMode() && Console.this.panes.activePane() != null) {
                        final int[] pos = Console.this.panes.calculatePanePosition(Console.this, Console.this.panes.activePane());
                        if (pos != null) widget.setPaneBounds(pos[0], pos[1], pos[2], pos[3]);
                    }
                    Utilities.runCursorLessWidget(widget, true);
                    if (Console.this.panes.splitMode()) {
                        Console.this.renderPanes(false);
                    }
                    redrawBuffer();
                }
            } finally {
                this.status.stopTimer();
                this.status.refresh();
                // Reposition cursor to active pane after status refresh (which moves cursor to bottom)
                if (this.panes.splitMode() && this.panes.activePane() != null) {
                    this.positionCursorInActivePane();
                }
            }
        }
        this.close();
        System.exit(0);
    }

    public void outputHeader(final String name) {
        try {
            if (screenMode()) {
                // the banner is history like anything else: through the same funnel as every
                // other write, so it is recorded in the screen AND shown — appended below
                // whatever the terminal already had while the console is still filling it,
                // painted once the console owns the rows
                final String banner = this.at(HEADER).isNoObj()
                        ? CommonUtil.getHeader(HEADER_FILE, name, true)
                        : Graphitty.string(Str.Helper.cleanString(this.at(HEADER)));
                this.screenView.screenOutput(banner);
                // screenOutput takes resolved text: the prompt prefix is markup
                this.screenView.screenOutput(Graphitty.string("\t{{b}}ve{{y}}rs{{m}}ion {{y}}" + METATRON_VERSION + "{{X}}\n"));
                this.screenView.screenOutput(Graphitty.string("   {{m}}:help{{X}} for console features\n\n"));
                this.renderScreen(false);
                return;
            }
            if (this.at(HEADER).isNoObj()) {
                terminal.writer().print(CommonUtil.getHeader(HEADER_FILE, name, true));
                terminal.writer().flush();
            } else {
                terminal.writer().print(Graphitty.string(Str.Helper.cleanString(this.at(HEADER))));
                terminal.writer().flush();
            }
        } catch (final Exception e) {
            terminal.writer().println("...a fundamental boot exception has occurred.");
            terminal.writer().println("      ...this does not bode well for your time in the meTaRon: " + e);
            terminal.writer().println(" __  __  ____  ____   __   ____  ____  _____  _  _ \n" +
                    "(  \\/  )( ___)(_  _) /__\\ (_  _)(  _ \\(  _  )( \\( )\n" +
                    " )    (  )__)   )(  /(__)\\  )(   )   / )(_)(  )  ( \n" +
                    "(_/\\/\\_)(____) (__)(__)(__)(__) (_)\\_)(_____)(_)\\_)");
            terminal.writer().printf("\t\t\tby PhaseShift Studio (%s)\n", Calendar.getInstance().get(Calendar.YEAR));
            terminal.flush();
        }
        LOG.none("\t{{b}}ve{{y}}rs{{m}}ion {{y}}%s{{X}}\n", METATRON_VERSION);
        Graphitty.out(terminal.output(), "   {{m}}:help{{X}} for console features\n\n");
    }

}