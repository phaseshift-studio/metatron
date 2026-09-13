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
import org.jline.console.SystemRegistry;
import org.jline.console.impl.Builtins;
import org.jline.console.impl.SystemRegistryImpl;
import org.jline.keymap.KeyMap;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;
import org.jline.widget.Widgets;
import org.slf4j.event.Level;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.Tracer;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.m.type.reflect.JInst;
import studio.phaseshift.metatron.isa.m.type.reflect.JRec;
import studio.phaseshift.metatron.isa.m.type.reflect.JRecElement;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.machine.SwarmMachine;
import studio.phaseshift.metatron.isa.mach.type.thread.AbstractThread;
import studio.phaseshift.metatron.isa.mach.type.thread.CoreThread;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.mach.type.ui.tmux.Pane;
import studio.phaseshift.metatron.isa.mach.type.ui.tmux.PaneNode;
import studio.phaseshift.metatron.isa.mach.type.ui.tmux.SplitContainer;
import studio.phaseshift.metatron.isa.mach.type.ui.tmux.SplitLayout;
import studio.phaseshift.metatron.isa.mach.type.ui.tool.TraceTool;
import studio.phaseshift.metatron.isa.mach.type.ui.tool.TypeDiffTool;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.FloatingSurface;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Utilities;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static studio.phaseshift.metatron.BootLoader.BOOTING;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread.virtual;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_CONSOLE_TID;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.SYS;
import static studio.phaseshift.metatron.util.CommonUtil.HEADER_FILE;

public class Console extends JRec<Console> implements Closeable, Runnable {

    @JRecElement(key = "metatron_version", rng = "/m/uri")
    public static final String METATRON_VERSION = "0.1-alpha";
    //@JRecElement(key = "mtron", rng = "/m/uri")
    public static final String MTRON = "mtron";
    public static final String MTRON_NANORC = "mtron.nanorc";
    public static Path HISTORY_FILE = Paths.get(".metatron.history");
    @JRecElement(key = "history", rng = "/m/inst")
    public Inst history = instA(f("dummy"));
    @JRecElement(key = "redirect/input", rng = "/m/inst")
    public Inst input = noobj();
    @JRecElement(key = "redirect/output", rng = "/m/inst")
    public Inst output = noobj();
    @JRecElement(key = "prefix", rng = "/m/str")
    public String prefix = "";
    @JRecElement(key = "postfix", rng = "/m/str")
    public String postfix = "";
    @JRecElement(key = "serializer", rng = "/m/rec")
    public ObjSerializer<String> serializer = new ObjmtronSerializer();
    @JRecElement(key = "status", rng = "/m/inst")
    public Inst statusLine = instLambda((lhs, inst) -> {
        StatusLine.message(inst.arg(0));
        return noobj();
    });
    private final GraphittyLogger LOG = Graphitty.log(this);
    private static Terminal terminal;
    private final LineReader reader;
    private final Widgets widgets;
    /**
     * Built-in key bindings (sequence -&gt; handler object as bound) that must
     * keep working across prompts.  A later binder on the same sequence (a
     * menu line key, a tool, anything) shadows a builtin until the next
     * prompt, when {@link #reassertBuiltinKeys} reclaims it.
     */
    private final java.util.Map<String, Object> builtinBindings = new java.util.LinkedHashMap<>();
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
     * Keystrokes typed while a foreground job runs.  The repl polls the job's
     * future instead of blocking on it, so {@code <alt>+b} can detach the job
     * and give the keyboard back; the classifier turns those keystrokes into a
     * detach, a cancel, or text for the next prompt.
     */
    private final Hotkeys hotkeys = new Hotkeys();

    /**
     * True while a foreground job holds the console — the window in which the
     * repl thread is not inside {@code readLine()} and the {@link #watchTerminal}
     * thread owns the terminal's keystrokes.
     */
    private volatile boolean watching = false;

    /**
     * set by the watcher when {@code <alt>+b} is pressed while a job runs
     */
    private final AtomicBoolean detachRequested = new AtomicBoolean(false);

    /**
     * set by the watcher when ctrl-c arrives as a byte — sigint is a no-op off the prompt
     */
    private final AtomicBoolean interruptRequested = new AtomicBoolean(false);

    /**
     * set by the watcher when the cancel key answers an armed cancel offer
     */
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    /**
     * stops the watcher when the console closes
     */
    private volatile boolean watcherClosed = false;

    /**
     * Orders the terminal handoff between the watcher's read and the prompt:
     * {@code watching} and {@code watchingRead} are only touched under it.
     */
    private final Object watchGate = new Object();

    /**
     * true while the watcher sits inside a terminal read
     */
    private boolean watchingRead = false;

    /**
     * Jobs detached with {@code <alt>+b} that are still running.  A detached
     * job is untouched — its own thread keeps updating widgets — this list only
     * tracks it so its result can be printed when it halts.
     */
    private final List<Machine> backgroundJobs = java.util.Collections.synchronizedList(new ArrayList<>());

    /**
     * cadence of the foreground poll — small enough that {@code <alt>+b} feels instant
     */
    private static final long FOREGROUND_POLL_MS = 120;
    /**
     * how long a job may hold the console before it offers to be cancelled
     */
    private static final long CANCEL_OFFER_MS = 10_000;
    /**
     * how often the idle watcher re-checks whether a job took the console
     */
    private static final long WATCHER_IDLE_MS = 25;
    /**
     * how long the prompt waits for the watcher to leave its read before taking the terminal back
     */
    private static final long WATCH_HANDOFF_MS = 400;
    /**
     * how long one watcher read waits for a keystroke.  It must be finite: with
     * timeout 0 a raw-mode read never expires, so the watcher would still be
     * parked inside a read when the job ends, the handoff would time out, and
     * restoring the cooked attributes would turn that read into a blocking one
     * that swallows the user's next keystroke — the half-eaten arrow key, where
     * the escape goes to the watcher and the rest lands in the line as text.
     */
    private static final long WATCH_READ_MS = 100;
    /**
     * how much of the console line a detached-job banner echoes
     */
    private static final int DETACH_PREVIEW_CHARS = 48;

    // ========== Split Pane Support ==========
    // Pane tree: root can be a single Pane or a SplitContainer with nested panes
    private PaneNode paneRoot;
    private Pane activePane;
    private final AtomicBoolean needsRedraw = new AtomicBoolean(false);
    private boolean splitMode = false;  // True when we have more than one pane
    private boolean traceEnabled = false; // True when :trace toggled on — dumps Java stack on fail
    private FloatingSurface floatingSurface;

    /**
     * The shared {@link FloatingSurface} for this console session.
     * Widgets pinned here are redrawn automatically at every prompt cycle.
     */
    public FloatingSurface getFloatingSurface() {
        if (this.floatingSurface == null) {
            this.floatingSurface = new FloatingSurface(terminal);
        }
        return this.floatingSurface;
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
    @JRecElement(key = "exp", rng = "/m/lst")
    public Lst expressionStack = lst();

    /**
     * Milliseconds of keyboard inactivity after which non-active panes may render.
     */
    private static final long INPUT_IDLE_THRESHOLD_MS = 500;

    // Language mode for multi-language support
    public enum Language {
        MTRON("mtron", "{{m}}mtron{{g}}> "),
        GREMLIN("gremlin", "{{y}}gremlin{{g}}> "),
        SQL("sql", "{{c}}sql{{g}}> ");

        public final String name;
        public final String prompt;

        Language(String name, String prompt) {
            this.name = name;
            this.prompt = prompt;
        }
    }

    private Language currentLanguage = Language.MTRON;

    public Console(final Rec options, final fURI vid) {
        super(options.jvm(), UI_CONSOLE_TID, vid);
        BootLoader.ONE_SHOT = false;
        Console.LOCAL_INSTANCE = this;
        try {
            // Initialize pane system with a single pane
            this.activePane = new Pane();
            this.activePane.setConsole(this);
            this.paneRoot = this.activePane;
            registerPaneListener(this.activePane);

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

            final DefaultParser parser = new DefaultParser()
                    .quoteChars(new char[]{'\'', '"'})
                    .lineCommentDelims(new String[]{"[--", "--]"})
                    .blockCommentDelims(new DefaultParser.BlockCommentDelims("[===", "===]"))
                    .eofOnUnclosedQuote(true)
                    .eofOnUnclosedBracket(DefaultParser.Bracket.CURLY, DefaultParser.Bracket.ROUND, DefaultParser.Bracket.SQUARE);
            Console.terminal = TerminalBuilder.builder().signalHandler(signal -> {
                if (signal == Terminal.Signal.INT) {
                    // Interrupt active pane's machine
                    if (this.activePane != null && this.activePane.machine() != null) {
                        this.activePane.machine().stop();
                    } else if (null != this.machine) {
                        this.machine.stop();
                    }
                }
            }).encoding(StandardCharsets.UTF_8).system(true).build();
            // Register terminal with Graphitty so widgets can write without
            // depending on Console/Terminal directly.
            Graphitty.init(terminal.output());
            // Request extended key reporting so terminals that support it (kitty, ghostty,
            // xterm with modifyOtherKeys, iTerm2, etc.) will send distinguishable
            // sequences for Shift+Backspace and other modified keys.
            // Backward-compatible: terminals that don't understand these sequences ignore them.
            terminal.writer().print("\033[>1u");   // kitty progressive enhancement 1 (disambiguate)
            terminal.writer().print("\033[>4;2m"); // xterm modifyOtherKeys level 2
            terminal.writer().flush();
            this.outputHeader("");
            final Supplier<Path> currentDir = () -> Paths.get("");
            final Builtins builtins = new Builtins(currentDir, Console.configurations, null);
            SystemRegistry systemRegistry = new SystemRegistryImpl(parser, terminal, currentDir, Console.configurations);
            systemRegistry.setCommandRegistries(builtins);
            final Highlighter highlighter = new Highlighter(new ObjConsoleSerializer(), true);
            highlighter.setTerminal(terminal);
            Highlighter.single().setTerminal(terminal);
            this.reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .appName("metatron")
                    .history(new DefaultHistory())
                    .highlighter(highlighter)
                    .parser(parser)
                    .variable(LineReader.HISTORY_FILE, HISTORY_FILE)
                    .option(LineReader.Option.AUTO_FRESH_LINE, true)
                    .option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .option(LineReader.Option.MOUSE, false)
                    .variable(LineReader.SECONDARY_PROMPT_PATTERN, Graphitty.string("{{-X&v1&^1&m}}     {{g}}| {{X}}"))
                    .variable(LineReader.INDENTATION, 0)
                    .completer(new MCompleter(this))
                    .build();
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
            this.watcherClosed = true;
            this.watching = false;
            this.reader.getBuffer().clear();
            // Disable extended key reporting before exit so we don't leave the
            // terminal in a state that confuses subsequent applications.
            terminal.writer().print("\033[<u");    // kitty: pop keyboard enhancement
            terminal.writer().print("\033[>4m");  // xterm: reset modifyOtherKeys
            terminal.writer().flush();
            terminal.close();
        } catch (final IOException e) {
            LOG.error(e);
        }
    }

    public void write(final Object object) {
        Graphitty.out(terminal.output(), object instanceof Obj ? this.serializer.write((Obj) object) : ((Highlighter) this.reader.getHighlighter()).write(object));
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

    public String prompt() {
        if (this.splitMode && this.activePane != null) {
            return this.activePane.prompt();
        }
        return Graphitty.string(this.currentLanguage.prompt + this.prefix);
    }

    /**
     * Prepare for readLine() - position cursor and clear prompt area in split mode.
     * Always re-renders panes to ensure correct layout before input.
     */
    private void prepareForInput() {
        // Built-in shortcut keys are reasserted before every read, so a
        // shadow binding (menu line key, tool) can never outlive this turn.
        this.reassertBuiltinKeys();
        if (this.splitMode && this.activePane != null) {
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
            final int[] pos = calculatePanePosition(this.activePane);
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

    public Language getCurrentLanguage() {
        if (this.activePane != null) {
            return this.activePane.language();
        }
        return this.currentLanguage;
    }

    public void setLanguage(Language language) {
        if (this.activePane != null) {
            this.activePane.language(language);
        }
        this.currentLanguage = language;
        LOG.info("switched to {{y}}%s{{X}} mode", language.name);
    }

    // ========== Pane Management ==========

    public Pane getActivePane() {
        return this.activePane;
    }

    @JRecElement(key = "pane", rng = "/m/lst[rec{*}]", mimic = JRecElement.Mimic.FIELD)
    //@JInst(tid = "pane", dom = "#{?}", rng = "/m/lst", attach = JInst.Attach.OBJ)
    public List<Pane> getAllPanes() {
        return null == this.paneRoot ? new ArrayList<>() : this.paneRoot.getAllPanes();
    }

    public boolean isSplitMode() {
        return this.splitMode;
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
     * Creates a new pane and makes it the sibling of the current active pane.
     *
     * @param direction VERTICAL (left|right) or HORIZONTAL (top|bottom)
     * @return the newly created pane
     */
    public Pane split(final SplitLayout direction) {
        if (direction == SplitLayout.NONE) {
            LOG.warn("cannot split with direction NONE");
            return this.activePane;
        }

        // Create new pane
        final Pane newPane = new Pane(this.activePane.language(), 1000);
        newPane.setConsole(this);
        registerPaneListener(newPane);

        // Create split container with active pane and new pane
        final SplitContainer container = new SplitContainer(direction, this.activePane, newPane);

        // Replace active pane in tree with the container
        if (this.paneRoot == this.activePane) {
            // Active pane is root - just replace root
            this.paneRoot = container;
        } else {
            // Find and replace in tree
            this.paneRoot.replaceChild(this.activePane, container);
        }

        this.splitMode = true;
        LOG.info("split pane {{y}}%d{{X}} %s, created pane {{y}}%d{{X}}",
                this.activePane.id(), direction.name().toLowerCase(), newPane.id());

        // Switch focus to new pane
        this.activePane = newPane;
        this.requestRedraw();
        JInst.Helper.processInst(this);
        return newPane;
    }

    /**
     * Close the active pane. If it's the last pane, do nothing.
     */
    public void closeActivePane() {
        final List<Pane> allPanes = getAllPanes();
        if (allPanes.size() <= 1) {
            LOG.warn("cannot close the last pane");
            return;
        }

        final Pane toClose = this.activePane;

        // Find next pane to focus — use id() comparison to avoid equals()/jvm() issues
        final int currentIndex = indexOfPaneById(allPanes, toClose != null ? toClose.id() : -1);
        final Pane nextPane = allPanes.get((currentIndex + 1) % allPanes.size());

        // Remove from tree — unsubscribe first so no stale space subscriptions linger
        if (toClose != null) toClose.unsubscribe();
        this.paneRoot = this.paneRoot.removePane(toClose);
        if (this.paneRoot == null) {
            // Shouldn't happen, but safety
            this.paneRoot = nextPane;
        }

        this.activePane = nextPane;
        this.splitMode = getAllPanes().size() > 1;

        LOG.info("closed pane {{y}}%d{{X}}, focused pane {{y}}%d{{X}}", toClose.id(), this.activePane.id());
        this.requestRedraw();
    }

    /**
     * Focus a specific pane by ID.
     */
    public void focusPane(final int paneId) {
        final Pane pane = this.paneRoot.findPane(paneId);
        if (pane == null) {
            LOG.error("pane {{r}}%d{{X}} not found", paneId);
            return;
        }
        this.activePane = pane;
        LOG.info("focused pane {{y}}%d{{X}}", paneId);
        this.requestRedraw();
    }

    /**
     * Cycle to the next pane.
     */
    public void nextPane() {
        final List<Pane> allPanes = getAllPanes();
        if (allPanes.size() <= 1) return;

        // Use id() comparison to avoid equals()/jvm() issues introduced by JRec changes
        // (JRec.jvm() now creates new instLambda objects on every call, breaking Map.equals())
        final int currentIndex = indexOfPaneById(allPanes, this.activePane != null ? this.activePane.id() : -1);
        this.activePane = allPanes.get((currentIndex + 1) % allPanes.size());
        LOG.info("focused pane {{y}}%d{{X}}", this.activePane.id());
        this.requestRedraw();
    }

    /**
     * Cycle to the previous pane.
     */
    public void prevPane() {
        final List<Pane> allPanes = getAllPanes();
        if (allPanes.size() <= 1) return;

        // Use id() comparison to avoid equals()/jvm() issues introduced by JRec changes
        final int currentIndex = indexOfPaneById(allPanes, this.activePane != null ? this.activePane.id() : -1);
        this.activePane = allPanes.get((currentIndex - 1 + allPanes.size()) % allPanes.size());
        LOG.info("focused pane {{y}}%d{{X}}", this.activePane.id());
        this.requestRedraw();
    }

    /**
     * Wire the output-change listener and space subscriptions onto a pane.
     */
    private void registerPaneListener(final Pane pane) {
        pane.setOutputListener(this::onPaneOutputChanged);
        pane.subscribe();
    }

    /**
     * Find the index of a pane by its integer id, avoiding {@link Object#equals} / {@code jvm()}
     * comparisons which are unreliable for JRec subclasses (JRec.jvm() creates new lambda
     * instances on every call, causing Map.equals to return false even for the same object).
     *
     * @return the index, or -1 if not found
     */
    private static int indexOfPaneById(final List<Pane> panes, final int id) {
        for (int i = 0; i < panes.size(); i++) {
            if (panes.get(i).id() == id) return i;
        }
        return -1;
    }

    /**
     * Resize the active pane by adjusting its parent container's split ratio.
     *
     * @param delta positive = more space for active pane, negative = less space
     */
    public void resizeActivePane(final float delta) {
        if (!this.splitMode || this.activePane == null) return;
        if (this.paneRoot.isLeaf()) return; // Single pane, nothing to resize

        // Find the parent container of the active pane
        final SplitContainer parent = ((SplitContainer) this.paneRoot).findParentOf(this.activePane);
        if (parent == null) {
            // Active pane might be direct child of root
            if (this.paneRoot instanceof SplitContainer root) {
                // Check if active pane is in first or second subtree
                if (root.first() == this.activePane ||
                        (!root.first().isLeaf() && root.first().findPane(this.activePane.id()) != null)) {
                    // Active pane is in first subtree - increase ratio for more space
                    root.adjustRatio(delta);
                } else {
                    // Active pane is in second subtree - decrease ratio for more space
                    root.adjustRatio(-delta);
                }
            }
        } else {
            // Determine if active pane is first or second child
            if (parent.first() == this.activePane) {
                // Active pane is first child - increase ratio for more space
                parent.adjustRatio(delta);
            } else {
                // Active pane is second child - decrease ratio for more space
                parent.adjustRatio(-delta);
            }
        }

        this.requestRedraw();
    }

    // ========== Floating Widget Focus (mirrors the pane focus above) ==========

    /**
     * Per-keystroke resize step for floating widgets (the pane analog
     * resizes by ±0.05 ratio per keystroke).
     */
    private static final int WIDGET_WIDTH_STEP = 4;
    private static final int WIDGET_HEIGHT_STEP = 1;

    /**
     * The stable key of the focused floating widget
     * ({@link FloatingSurface#widgetKey} — its vid when it has one).  A
     * floating widget is re-hydrated into a fresh instance on every
     * {@code .display()} update, so the key — not the instance — is tracked,
     * and the current instance is resolved lazily.  null = no focus.
     */
    private volatile String activeWidgetKey = null;

    /**
     * @return true when the console's floating surface has at least one pinned widget
     */
    public boolean hasFloatingWidgets() {
        return !getFloatingSurface().widgets().isEmpty();
    }

    /**
     * Register a built-in key binding (handler identity remembered) so that
     * {@link #reassertBuiltinKeys} can reclaim the sequence if a later binder
     * shadows it (a menu line key, a tool, anything bound after startup).
     */
    public void registerBuiltinKey(final String sequence, final Object handler) {
        this.builtinBindings.put(sequence, handler);
    }

    /**
     * Reclaim built-in key bindings that a later binder has overridden on the
     * shared "main" keymap.  Called before every prompt, so a builtin key can
     * never stay shadowed beyond a single session turn — the shadow only
     * lasts until the next read.
     */
    public void reassertBuiltinKeys() {
        if (this.builtinBindings.isEmpty())
            return;
        reassertBuiltin(this.widgets.getKeyMap(), this.builtinBindings);
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
        return this.widgets.getKeyMap().getBound(sequence);
    }

    /**
     * @return the handler the console registered for the builtin (may be null)
     */
    public Object builtinKeyHandler(final String sequence) {
        return this.builtinBindings.get(sequence);
    }

    /**
     * @return the sequences the console keeps as reasserted builtins
     */
    public java.util.Set<String> builtinKeySequences() {
        return this.builtinBindings.keySet();
    }

    /**
     * @return the pinned floating widgets in deterministic focus-cycling order
     * (see {@link FloatingSurface#widgets()})
     */
    public List<Widget<?>> getFloatingWidgets() {
        return getFloatingSurface().widgets();
    }

    /**
     * @return the currently focused floating widget, or null when nothing
     * is focused (or the focused widget was removed / re-floated into
     * an unknown key since)
     */
    public Widget<?> getActiveWidget() {
        final String key = getFloatingSurface().resolveKey(this.activeWidgetKey);
        if (null == key) return null;
        for (final Widget<?> w : getFloatingWidgets()) {
            if (key.equals(FloatingSurface.widgetKey(w)))
                return w;
        }
        return null;
    }

    /**
     * Focus the given floating widget (pass null to clear the focus).
     * Triggers a re-render so the focus marker lands immediately.
     */
    public void focusWidget(final Widget<?> widget) {
        if (null == widget) {
            this.activeWidgetKey = null;
            getFloatingSurface().setFocusKey(null);
            LOG.info("cleared floating widget focus");
        } else {
            this.activeWidgetKey = FloatingSurface.widgetKey(widget);
            getFloatingSurface().setFocusKey(this.activeWidgetKey);
            LOG.info("focused floating widget {{y}}%s{{X}}", this.activeWidgetKey);
        }
        getFloatingSurface().render();
    }

    /**
     * Cycle the focus to the next floating widget (wrapping).  A no-op when
     * no widgets are pinned.
     */
    public void nextWidget() {
        cycleWidgetFocus(1);
    }

    /**
     * Cycle the focus back to the previous floating widget (wrapping).
     */
    public void prevWidget() {
        cycleWidgetFocus(-1);
    }

    private void cycleWidgetFocus(final int direction) {
        final List<Widget<?>> widgets = getFloatingWidgets();
        if (widgets.isEmpty()) {
            LOG.warn("no floating widgets to focus");
            return;
        }
        final String activeKey = getFloatingSurface().resolveKey(this.activeWidgetKey);
        int index = -1;
        for (int i = 0; i < widgets.size(); i++) {
            if (null != activeKey && activeKey.equals(FloatingSurface.widgetKey(widgets.get(i)))) {
                index = i;
                break;
            }
        }
        if (index < 0 && widgets.size() > 1) {
            // lost the position — say so, so a live session can see it
            // (single-widget cycling back to the same widget is normal)
            LOG.warn("focus key {{y}}%s{{X}} not found among {{y}}%d{{X}} floating widgets; cycling restarts at the first — see {{m}}:widgets{{X}}",
                    this.activeWidgetKey, widgets.size());
        }
        final Widget<?> next = widgets.get(((index + 1 + direction) % widgets.size() + widgets.size()) % widgets.size());
        focusWidget(next);
    }

    /**
     * Grow the focused floating widget's width by one step
     * ({@value WIDGET_WIDTH_STEP} columns).  Which edge moves is decided by
     * the widget's anchor: a left-anchored widget extends its right edge,
     * a right-anchored widget pulls in its left edge.
     */
    public void growActiveWidgetWidth() {
        nudgeActiveWidget(WIDGET_WIDTH_STEP, 0);
    }

    /**
     * Shrink the focused floating widget's width by one step.
     */
    public void shrinkActiveWidgetWidth() {
        nudgeActiveWidget(-WIDGET_WIDTH_STEP, 0);
    }

    /**
     * Grow the focused floating widget's height by one step
     * ({@value WIDGET_HEIGHT_STEP} rows).  Which edge moves is decided by
     * the widget's anchor: a bottom-anchored widget pushes its top edge
     * up, a top-anchored widget pushes its bottom edge down.
     */
    public void growActiveWidgetHeight() {
        nudgeActiveWidget(0, WIDGET_HEIGHT_STEP);
    }

    /**
     * Shrink the focused floating widget's height by one step.
     */
    public void shrinkActiveWidgetHeight() {
        nudgeActiveWidget(0, -WIDGET_HEIGHT_STEP);
    }

    private void nudgeActiveWidget(final int widthDelta, final int heightDelta) {
        final Widget<?> active = getActiveWidget();
        if (null == active) {
            LOG.warn("no floating widget in focus");
            return;
        }
        if (getFloatingSurface().nudge(active, widthDelta, heightDelta)) {
            LOG.info("resized floating widget {{y}}%s{{X}} (width %+d, height %+d)",
                    this.activeWidgetKey, widthDelta, heightDelta);
        }
    }

    /**
     * Position the cursor at the active pane's prompt location.
     * Called after operations that move the cursor (like status refresh).
     */
    public void positionCursorInActivePane() {
        if (this.activePane == null) return;
        final int[] pos = calculatePanePosition(this.activePane);
        if (pos == null) return; // activePane not in tree yet (e.g. during split transition)
        final int promptRow = pos[0] + pos[2] - 2; // startRow + height - 2
        final int promptCol = pos[1] + 1; // startCol + 1 to skip left border
        terminal.writer().print("\u001b[" + promptRow + ";" + promptCol + "H");
        terminal.writer().flush();
    }

    /**
     * Calculate a pane's position (startRow, startCol, height, width) by traversing the tree.
     * This ensures we always have the correct position regardless of render state.
     *
     * @return int[] {startRow, startCol, height, width}
     */
    public int[] calculatePanePosition(final Pane pane) {
        final int height = terminal.getHeight() - 1;  // -1 for status line only
        final int width = terminal.getWidth();
        return calculatePanePositionInNode(pane, this.paneRoot, 1, 1, height, width);
    }

    private int[] calculatePanePositionInNode(final Pane target, final PaneNode node,
                                              final int startRow, final int startCol,
                                              final int height, final int width) {
        if (node == target) {
            return new int[]{startRow, startCol, height, width};
        }
        if (node.isLeaf()) {
            return null; // Not found in this branch
        }
        // It's a SplitContainer
        final SplitContainer container = (SplitContainer) node;
        if (container.direction() == SplitLayout.VERTICAL) {
            // No divider - panes are directly adjacent
            final int firstWidth = (int) (width * container.ratio());
            final int secondWidth = width - firstWidth;

            // Check first (left)
            int[] result = calculatePanePositionInNode(target, container.first(),
                    startRow, startCol, height, firstWidth);
            if (result != null) return result;

            // Check second (right)
            return calculatePanePositionInNode(target, container.second(),
                    startRow, startCol + firstWidth, height, secondWidth);
        } else { // HORIZONTAL
            // No divider - panes are directly adjacent
            final int firstHeight = (int) (height * container.ratio());
            final int secondHeight = height - firstHeight;

            // Check first (top)
            int[] result = calculatePanePositionInNode(target, container.first(),
                    startRow, startCol, firstHeight, width);
            if (result != null) return result;

            // Check second (bottom)
            return calculatePanePositionInNode(target, container.second(),
                    startRow + firstHeight, startCol, secondHeight, width);
        }
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
        if (!this.splitMode || pane == null) return;
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
    private void onPaneOutputChanged(final Pane pane) {
        if (!this.splitMode) return;

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
    private boolean deferNonActivePaneRender() {
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
        if (!this.splitMode) return;

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
        this.paneRoot.render(terminal, 1, 1, height, width, this.activePane);

        // Position cursor at active pane's prompt location (use dynamic calculation)
        final int[] pos = calculatePanePosition(this.activePane);
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

    public StatusLine getStatus() {
        return this.status;
    }

    public ConfigurationPath getConfigurations() {
        return Console.configurations;
    }

    protected void printResult(final Obj result) {
        if (this.splitMode && this.activePane != null) {
            this.activePane.appendResult(result);
        } else {
            result.stream().takeWhile(o -> !this.interruptRequested.get())
                    .forEach(o -> {
                        this.write("{{-X-}}{{m}}=={{g}}>{{X}}");
                        this.write(this.serializer.write(o));
                        this.write("\n");
                    });
            terminal.flush();
        }
    }

    private void renderTrace(final Obj failObj) {
        if (this.splitMode && this.activePane != null)
            this.activePane.appendOutput(formatTraceAnsi(failObj), false);
        else
            this.write(formatTraceMarkup(failObj));
    }

    /**
     * If {@link Tracer#java_stack} is enabled, prompt the user to launch the
     * {@link TraceTool} for each fail object.  Falls back to
     * {@link #renderTrace} when the user declines.
     */
    private void promptTraceForFails(final Obj result) {
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
                    if (Console.this.splitMode && Console.this.activePane != null) {
                        final int[] pos = Console.this.calculatePanePosition(Console.this.activePane);
                        if (pos != null) traceTool.setPaneBounds(pos[0], pos[1], pos[2], pos[3]);
                    }
                    Utilities.runCursorLessWidget(traceTool, true);
                    if (Console.this.splitMode) {
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

    protected void executeInCurrentLanguage(final String line) {
        final Language lang = this.getCurrentLanguage();
        switch (lang) {
            case MTRON -> this.executeMtron(line);
            case GREMLIN -> this.executeGremlin(line);
            case SQL -> this.executeSql(line);
        }
    }

    protected void executeMtron(final String line) {
        /// /////////////////////////////////////////////////////
        AtomicReference<Obj> running = new AtomicReference<>(noobj());
        final String fullInput = this.prefix + line + this.postfix;
        if (fullInput.isBlank()) return;

        // 1. Parse the full input — the parser natively handles ; via end() sugar
        final Obj parsed = ObjmtronSerializer.parseMulti(fullInput);
        if (null == parsed || parsed.isNoObj()) return;

        // 2. Split into independently executable segments at end() boundaries
        final List<Code> segments;
        if (parsed.isCode()) {
            segments = ObjmtronSerializer.splitCodeAtEnd(parsed.asCode());
        } else {
            // Single expression (bare value or single instruction) — wrap as a one-instruction code
            segments = List.of(MCode.of(List.of(
                    parsed.isInst() ? parsed.as() : instB(START_INST_TID, lst(parsed)))));
        }
        if (segments.isEmpty()) return;

        // 3. Execute each segment with its own SwarmMachine, chaining running state
        boolean backgrounded = false;
        for (final Code segment : segments) {
            try {
                final Level startLevel = this.status.getState();
                final Obj resolvedResult = Call.Helper.resolveInspection(running.get(), segment, unresolved -> {
                    if (TypeCheck.code_resolve.enabled()) {
                        throw MTronException.of("unable to fully resolve code. execution will require dynamic inst resolution for:\n\t%s", unresolved.stream().map(Obj::tid).toList());
                    } else {
                        this.status.setState(Level.WARN);
                        segment.logger().status(DEBUG, "{{y}}dynamic resolution{{X}}: %s", unresolved.stream().map(i -> "{{b}}" + i.tid() + "{{y}}@" + i.vid() + "{{X}}").reduce("", (a, b) -> a + "," + b).substring(1));
                    }
                });
                final AtomicReference<Obj> computeResult = new AtomicReference<>(noobj());
                if (this.input.isNoObj()) {
                    final Machine mach = SwarmMachine.of(resolvedResult.as());
                    final Consumer<Obj> defaultOnHalt = mach.onHalt(); // accumulate into HALTED
                    mach.onHalt(o -> {
                        defaultOnHalt.accept(o);  // persist in HALTED collection
                        //this.printResult(o);      // display
                    });
                    // Track machine in both places for interruption
                    this.machine = mach;
                    if (this.activePane != null) {
                        this.activePane.machine(mach);
                    }
                    final FutureObj<Obj> future = mach.applyAsync();
                    if (this.awaitForeground(mach, future, line)) {
                        // <alt>+b — the machine keeps its own thread and keeps
                        // running (widgets stay live); this turn is over.
                        backgrounded = true;
                    } else {
                        computeResult.set(future.get());
                    }
                } else {
                    computeResult.set(this.input.apply(resolvedResult));
                }
                running.set(computeResult.get());
                computeResult.get().stream().forEach(this::printResult);
                this.promptTraceForFails(computeResult.get());
                this.status.setState(startLevel);
            } catch (final Exception e) {
                final Obj failResult = fail(e);
                this.printResult(failResult);
                this.promptTraceForFails(failResult);
            } finally {
                userMode.set(false);
                if (this.activePane != null)
                    this.activePane.clearMachine();

            }
            // a detached job took the rest of this input to the background
            if (backgrounded) break;
        }
    }

    /**
     * Poll a foreground job's future until it completes, watching the terminal
     * while we wait.  The repl thread holds the reader here — jline is not
     * reading — so keystrokes typed during the job belong to the console:
     * {@code <alt>+b} detaches the job, {@code [q]} cancels it once the cancel
     * offer has been printed, and anything else is kept for the next prompt.
     *
     * @param mach   the machine running the foreground job
     * @param future the job's future
     * @param line   the console line that started the job
     * @return true when the job was detached with {@code <alt>+b} (it is still
     * running — the console returns to the prompt)
     */
    private boolean awaitForeground(final Machine mach, final FutureObj<Obj> future, final String line) {
        long offerAtMs = System.currentTimeMillis() + CANCEL_OFFER_MS;
        this.detachRequested.set(false);
        this.cancelRequested.set(false);
        this.interruptRequested.set(false);
        // jline restores the terminal's cooked attributes when readLine()
        // returns, so outside the prompt keystrokes are line-buffered: alt+b
        // would not arrive until Enter.  Raw mode for the life of the job makes
        // every keypress a byte the watcher can classify.  jline saves and
        // restores its own attributes around each readLine, so handing the
        // cooked attributes back before returning is exactly what it expects.
        final Attributes cooked = terminal.enterRawMode();
        // the watcher owns the terminal from here until this job is over: the
        // repl thread is parked on the future below, so jline is not reading
        synchronized (this.watchGate) {
            this.watching = true;
            this.watchGate.notifyAll();
        }
        try {
            while (!future.isDone()) {
                try {
                    future.get(FOREGROUND_POLL_MS);
                } catch (final Exception e) {
                    // a poll window ending is the normal path — the job is still running
                }
                if (future.isDone())
                    break;
                final boolean detach = this.detachRequested.getAndSet(false);
                final boolean interrupt = this.interruptRequested.getAndSet(false);
                final boolean cancel = this.cancelRequested.getAndSet(false);
                switch (foregroundStep(detach, interrupt, cancel, userMode.get(),
                        System.currentTimeMillis(), offerAtMs)) {
                    case DETACH -> {
                        this.detachForegroundJob(mach, future, line);
                        return true;
                    }
                    case INTERRUPT -> mach.stop();
                    case CANCEL -> {
                        this.hotkeys.disarmCancel();
                        this.write(Graphitty.string("{{-X-&|0}}"));
                        future.cancel(true);
                    }
                    case OFFER_CANCEL -> {
                        this.hotkeys.armCancel();
                        offerAtMs = System.currentTimeMillis() + CANCEL_OFFER_MS;
                        terminal.writer().write(Highlighter.format(
                                "{{-X-}}{{k}}cancel stream with [q] or background with <" + Hotkeys.DETACH_COMBO + "> {{X}}\r"));
                        terminal.writer().flush();
                    }
                    case WAIT -> {
                    }
                }
            }
            return false;
        } finally {
            this.releaseTerminal(cooked);
        }
    }

    /**
     * Read the terminal while a foreground job holds the console, one keystroke
     * at a time, and classify each one through {@link Hotkeys}.  This is the
     * only reader in that window — jline is parked outside {@code readLine()} —
     * and a bounded read is exactly right here: the reading happens on this
     * thread, never on the repl thread, so the console's poll loop stays live
     * and the read can still be released when the job ends.
     *
     * <p>A terminal read cannot be interrupted, so a read that started while the
     * job ran may complete after it ended.  A keystroke taken that way is handed
     * back to the line being typed ({@link #injectTypedText()}) — the prompt is
     * live by then and jline never saw it.
     */
    private void watchTerminal() {
        while (!this.watcherClosed) {
            if (!this.beginWatch()) {
                CommonUtil.sleepThread(WATCHER_IDLE_MS);  // the prompt owns the terminal
                continue;
            }
            try {
                final int c = terminal.reader().read(WATCH_READ_MS);
                if (NonBlockingReader.READ_EXPIRED == c) {
                    // nothing typed: a half-seen escape sequence ends here, so a
                    // stale escape can never swallow the next keystroke
                    this.hotkeys.reset();
                    continue;
                }
                if (c < 0)
                    continue;  // eof — nothing to classify
                final boolean onWatch = this.watching;
                switch (this.hotkeys.accept(c)) {
                    case DETACH -> this.detachRequested.set(true);
                    case INTERRUPT -> this.interruptRequested.set(true);
                    case CANCEL -> this.cancelRequested.set(true);
                    case TEXT -> {
                        if (onWatch)
                            this.echoTypedChar(c);
                        else
                            this.injectTypedText();
                    }
                    case NONE -> {
                    }
                }
            } catch (final Exception e) {
                // a failed read must never kill the watcher — the repl still works
                CommonUtil.sleepThread(WATCHER_IDLE_MS);
            } finally {
                this.endWatch();
            }
        }
    }

    /**
     * Take the terminal for one read.  False when no job holds the console —
     * the prompt owns the keystrokes then, and the watcher must not be a second
     * reader racing jline for them.
     */
    private boolean beginWatch() {
        synchronized (this.watchGate) {
            if (!this.watching && !this.hotkeys.inSequence())
                return false;
            this.watchingRead = true;
            return true;
        }
    }

    /**
     * give the terminal back, waking anyone waiting on the handoff
     */
    private void endWatch() {
        synchronized (this.watchGate) {
            this.watchingRead = false;
            this.watchGate.notifyAll();
        }
    }

    /**
     * Hand the terminal back to the prompt.  The order matters: restoring the
     * cooked attributes while a raw-mode read is still pending turns that read
     * into a blocking one, which then swallows the user's next keystroke.  So
     * the watcher is told to stop first and given a moment to leave its read —
     * in raw mode a read with no input expires in about 100ms — and only then do
     * the cooked attributes go back.
     */
    private void releaseTerminal(final Attributes cooked) {
        synchronized (this.watchGate) {
            this.watching = false;
            this.watchGate.notifyAll();
            final long end = System.currentTimeMillis() + WATCH_HANDOFF_MS;
            while (this.watchingRead && System.currentTimeMillis() < end) {
                try {
                    this.watchGate.wait(WATCH_HANDOFF_MS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        terminal.setAttributes(cooked);
    }

    /**
     * Echo a keystroke the watcher took while the job ran.  Raw mode is on for
     * the life of the job, so the terminal does not echo for us — without this,
     * typing ahead of a long job would be invisible.
     */
    private void echoTypedChar(final int c) {
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
    private void injectTypedText() {
        final String text = this.hotkeys.takePendingText();
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
     * Hand a running foreground job to the background — {@code <alt>+b}.  The
     * job is not touched: it keeps its own thread, keeps updating widgets, and
     * stays addressable at {@code /sys/thread/<vid>}.  The console only stops
     * waiting on it and stops treating it as foreground work, so ctrl-c at the
     * returning prompt cannot kill it.  A collector thread prints its result
     * when it halts.
     */
    private void detachForegroundJob(final Machine mach, final FutureObj<Obj> future, final String line) {
        final fURI vid = vidOf(mach);
        this.backgroundJobs.add(mach);
        // the turn no longer owns this machine: clear both places the interrupt
        // paths read (the ctrl-c signal handler and UserInterrupt handling)
        this.machine = null;
        if (this.activePane != null)
            this.activePane.clearMachine();
        docWrap(virtual(instLambda((lhs, inst) -> {
            this.printBackgroundResult(mach, future);
            return noobj();
        }), SYS.extend("thread/console_background")), "background result collector").applyAsync();
        LOG.none("{{-X-&|0}}");
        LOG.info("<%s> => background %s (:bg to list)", Hotkeys.DETACH_COMBO, preview(line));
        terminal.flush();
    }

    /**
     * Print the result of a detached job, once its future resolves.  Runs on
     * the job's collector thread.
     */
    private void printBackgroundResult(final Machine mach, final FutureObj<Obj> future) {
        this.backgroundJobs.remove(mach);
        final Obj result;
        try {
            result = future.get();
        } catch (final Exception e) {
            LOG.none("{{-X-&|0}}");
            this.printResult(fail(e));
            return;
        }
        LOG.none("{{-X-&|0}}\r[{{g}}result start{{/g}}:%s]\n", null == vidOf(mach) ? "?" : vidOf(mach).toString());
        this.printResult(result);
        LOG.none("[{{g}}result end{{/g}}:%s]\n", null == vidOf(mach) ? "?" : vidOf(mach).toString());
    }

    /**
     * a machine's thread vid, when it has one — every machine is a rec at run-time
     */
    private static fURI vidOf(final Machine mach) {
        return (mach instanceof Obj obj) ? obj.vid() : null;
    }

    /**
     * @return the jobs detached with {@code <alt>+b} that have not yet halted,
     * in detach order
     */
    public List<Machine> backgroundJobs() {
        synchronized (this.backgroundJobs) {
            return List.copyOf(this.backgroundJobs);
        }
    }

    /**
     * @return the thread vids of the jobs detached with {@code <alt>+b} — each
     * one is addressable at {@code /sys/thread/<vid>}
     */
    public List<fURI> backgroundJobVids() {
        final List<fURI> vids = new ArrayList<>();
        synchronized (this.backgroundJobs) {
            for (final Machine job : this.backgroundJobs) {
                final fURI vid = vidOf(job);
                if (null != vid)
                    vids.add(vid);
            }
        }
        return vids;
    }

    /**
     * Stop every detached job.  Returns the number of jobs stopped.
     */
    public int stopBackgroundJobs() {
        final List<Machine> jobs = this.backgroundJobs();
        jobs.forEach(Machine::stop);
        this.backgroundJobs.clear();
        return jobs.size();
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

    protected void executeGremlin(final String line) {
        try {
            // TODO: Implement Gremlin execution using GremlinScriptEngine
            // The result should be converted to Metatron objects
            LOG.warn("Gremlin execution not yet implemented");
            this.printResult(fail(new UnsupportedOperationException("Gremlin mode not yet implemented")));
        } catch (final Exception e) {
            this.printResult(fail(e));
        }
    }

    protected void executeSql(final String line) {
        try {
            // TODO: Implement SQL execution
            // The result should be converted to Metatron objects (similar to tbleSpace.sql())
            LOG.warn("SQL execution not yet implemented");
            this.printResult(fail(new UnsupportedOperationException("SQL mode not yet implemented")));
        } catch (final Exception e) {
            this.printResult(fail(e));
        }
    }

    public void redrawBuffer() {
        // In split mode, skip the newline - prompt() includes cursor positioning
        if (!this.splitMode) {
            Graphitty.out(terminal.output(), "\n");
        }
        Graphitty.out(terminal.output(), this.prompt());
        Graphitty.out(terminal.output(), Highlighter.format(this.reader.getBuffer().toString()));
        terminal.flush();
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
                this.inReadLine = true;
                this.lastKeyActivityMs = System.currentTimeMillis();
                this.lastBufferLength = 0; // fresh buffer for new readLine
                // keys typed while a foreground job held the console seed this
                // prompt, so typing ahead of a long job is never lost
                final String typedAhead = this.hotkeys.takePendingText();
                if (!typedAhead.isEmpty())
                    this.seedBuffer = null == this.seedBuffer ? typedAhead : this.seedBuffer + typedAhead;
                final String line = (null != this.seedBuffer
                        ? this.reader.readLine(this.prompt(), null, (MaskingCallback) null, this.seedBuffer)
                        : this.reader.readLine(this.prompt())).trim();
                this.seedBuffer = null;
                this.inReadLine = false;
                // Drain any agent output that was deferred while the user was typing.
                if (this.pendingPaneFlush) {
                    this.pendingPaneFlush = false;
                    if (this.splitMode) this.renderPanes();
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
                            if (this.splitMode && this.activePane != null) {
                                this.activePane.appendOutput(
                                        Graphitty.string("{{c}}  \\_{{X}} ") + Highlighter.format(chatPart));
                            }
                            this.executeInCurrentLanguage(chatPart);
                            this.machine = null;
                            if (this.splitMode) {
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
                        final int promptWidth = Highlighter.visualLength(this.getCurrentLanguage().prompt);
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
                if (line.isEmpty()) continue;
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
                    if (this.splitMode && this.activePane != null) {
                        this.activePane.appendOutput(Graphitty.string(this.currentLanguage.prompt) + Highlighter.format(line));
                    }
                    this.executeInCurrentLanguage(line);
                    this.machine = null;
                    // Redraw panes after command execution to show output
                    if (this.splitMode) {
                        this.renderPanes();
                    }
                }
            } catch (final UserInterruptException e) {
                if (null != this.machine)
                    this.machine.stop();
                LOG.warn(Graphitty.sillyPrint("machine interrupted", true, true));
            } catch (final EndOfFileException e) {
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
                    if (Console.this.splitMode && Console.this.activePane != null) {
                        final int[] pos = Console.this.calculatePanePosition(Console.this.activePane);
                        if (pos != null) widget.setPaneBounds(pos[0], pos[1], pos[2], pos[3]);
                    }
                    Utilities.runCursorLessWidget(widget, true);
                    if (Console.this.splitMode) {
                        Console.this.renderPanes(false);
                    }
                    redrawBuffer();
                }
            } finally {
                this.status.stopTimer();
                this.status.refresh();
                // Reposition cursor to active pane after status refresh (which moves cursor to bottom)
                if (this.splitMode && this.activePane != null) {
                    this.positionCursorInActivePane();
                }
            }
        }
        this.close();
        System.exit(0);
    }

    public void outputHeader(final String name) {
        try {
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