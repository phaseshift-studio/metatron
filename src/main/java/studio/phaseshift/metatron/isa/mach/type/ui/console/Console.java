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
import studio.phaseshift.metatron.isa.sys.mSystem;
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
    /**
     * The serializer every result goes through.  It is the console's own (see
     * {@code ObjConsoleSerializer}) because that is where a uri becomes a {@code {{link}}} — a
     * plain serializer writes the uri and nothing downstream can tell it apart from text.
     */
    public ObjSerializer<String> serializer = new ObjConsoleSerializer();
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

    private final ConsoleScreen screen = new ConsoleScreen();
    /**
     * True while a screen paint is on its way to the render thread, so a burst of
     * writes paints once rather than per line.
     */
    private final AtomicBoolean screenPaintRunning = new AtomicBoolean(false);

    /**
     * True when something changed that a paint would show, and that paint has not
     * happened yet — a request that arrives while a pass is running must not be dropped,
     * or the rows it was about (a widget's erased box) stay unpainted until the next
     * prompt.  The running pass re-runs itself while this is set.
     */
    private final AtomicBoolean screenPaintWanted = new AtomicBoolean(false);

    /**
     * True while the console's output is still being appended to the terminal rather than
     * painted (see {@link #screenAppends()}), so the first painted frame knows it must
     * repaint the whole region instead of trusting what is on screen.
     */
    private volatile boolean screenAppended = false;

    /**
     * The row the prompt sits on: the bottom of the terminal until the transcript is tall
     * enough to fill the screen, and below the transcript until then.
     *
     * <p>Pinning it at the bottom from the first prompt is what leaves a screen of blank
     * space above a short transcript — the banner would sit at the top with twenty empty
     * rows between it and the prompt, and the boot text a reader was still reading is
     * pushed out of view.  Letting the prompt follow the transcript instead means the
     * transcript starts at the top row (which is where the boot text already is), the
     * prompt stays immediately below the newest line, and the moment the transcript fills
     * the screen the prompt arrives at the bottom and stays there.
     *
     * <p>The last row is not available to it: jline's {@code Status} owns the bottom row.
     */
    private int screenPromptRow() {
        final int bottom = Math.max(2, terminal.getHeight() - 1);
        final int top = this.screenStart > 0 ? this.screenStart : 1;
        final int transcript = this.screen.size();
        return Math.min(bottom, Math.max(Math.min(2, top), top + transcript));
    }

    /**
     * The shared {@link FloatingSurface} for this console session.
     * Widgets pinned here are redrawn automatically at every prompt cycle.
     */
    public FloatingSurface getFloatingSurface() {
        if (this.floatingSurface == null) {
            this.floatingSurface = new FloatingSurface(terminal);
            // The surface claims the output funnel in its constructor; on a console
            // that owns its screen, the screen takes it instead (see installScreenWriter).
            this.installScreenWriter();
            // The screen also takes the rows the widgets draw over: the surface reports
            // what it erased and stops blanking rows it does not own (see repairRowedRows).
            if (screenMode())
                this.floatingSurface.setDamageListener(band -> this.repairRows(band[0], band[1]));
        }
        return this.floatingSurface;
    }

    /**
     * Point the console's output funnel at the screen, so every row the console
     * writes is a row the console holds a copy of.
     *
     * <p>{@code Graphitty.setTerminalWriter} is the funnel every console write
     * already goes through — results ({@code Console.write}), the {@code print}
     * instruction, log lines, agent replies — because {@code Graphitty.out} hands
     * the resolved text to it rather than to the terminal.  Routing individual call
     * sites instead leaves the rest writing rows the screen never saw, and a row the
     * screen never saw is a row nothing can put back: it scrolls the terminal away
     * and is gone (which is exactly how {@code print} output behaved until the
     * funnel pointed here).
     */
    private void installScreenWriter() {
        if (!screenMode()) return;
        Graphitty.setTerminalWriter(this::screenOutput);
    }

    /**
     * The terminal row the console's own output starts on, or -1 while that is unknown.
     *
     * <p>Everything the console paints is positioned against it, and a click is resolved by it
     * (terminal row − this = the console's own row), so it is the one fact the screen cannot
     * work without.  Probing the terminal for it is the only way to learn it, and doing so
     * waits for input on a terminal that does not answer, so it is left unknown (-1) and the
     * console appends its output instead until it owns the screen.
     */
    /**
     * The clear-screen code the graphitty {@code {{XX}}} rewrite carries.
     */
    private static final String CLEAR_SCREEN = "\033[2J";

    private volatile int screenStart = declaredStartRow();

    /**
     * The row the console's own output starts on, from the launcher's banner count when it gives
     * one ({@code -Dmetatron.console.bannerRows=N} — the rows it printed before starting the VM),
     * or -1 when it does not, which leaves the console appending below whatever is there.
     */
    private static int declaredStartRow() {
        final int banner = Integer.getInteger("metatron.console.bannerRows", -1);
        return banner >= 0 ? banner + 1 : -1;
    }

    /**
     * Where and when the last link click landed.  A terminal does not report a double click —
     * the same press simply arrives twice — so it is the cell and the timing that say so.
     */

    /**
     * Ask the terminal where its cursor is, once, with a bounded read.
     *
     * <p>Non-destructive by construction: the reply is <em>peeked</em> for (a peek consumes
     * nothing) and only an escape — the start of a cursor-position report — is actually read,
     * so a key the user happened to press in this window is left for the reader.  A terminal
     * that does not answer costs one short wait and leaves {@link #screenStart} unknown, in
     * which case the console appends its output instead of painting it.
     *
     * @return the 1-based row the cursor is on, or -1 when the terminal did not say
     */
    /**
     * True while the console is still filling the terminal, so its output is appended the
     * ordinary way instead of painted over rows.
     *
     * <p>The console does not start on a blank terminal: a launcher prints a banner, the
     * boot logs print theirs, and a shell may have left whatever it liked above that.
     * Those rows are not the console's to paint over — and it cannot ask where they end
     * (the cursor-position report is a blocking read with no timeout, which would hang
     * startup on a terminal that does not answer).  So output is appended, the way any
     * terminal program appends it, until the console has printed more than a screenful:
     * by then its own content has scrolled everything before it away, the rows belong to
     * the console, and painting them is what keeps them restorable.
     */
    private boolean screenAppends() {
        if (!screenMode()) return false;
        // The console's first row is known, so painting from it is safe at any length: a launcher
        // that printed a banner says how many rows it took (-Dmetatron.console.bannerRows), and
        // the console's output then starts on the line after it — the banner stays readable AND
        // its own rows are addressable, which is what a click needs.
        if (this.screenStart > 0) return false;
        // Unknown, and it CANNOT be found out: the only way to ask a terminal where its cursor is
        // is jline's cursor-position report, whose read blocks on a terminal that does not answer
        // (measured — it hangs startup).  So the console appends, the way any terminal program
        // does: the launcher's output is left alone, and the cost is that its own rows are not
        // addressable — a click cannot be resolved to one — until it has filled the screen and
        // taken the rows over.
        return true;
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
     * A line the console is asked for by someone other than its own read loop (see {@link #readHumanLine}).
     */
    private record HumanRead(String prompt, java.util.concurrent.CompletableFuture<String> answer) {
    }

    /**
     * Lines waiting to be taken by the console's read loop, oldest first.
     */
    private final java.util.concurrent.BlockingQueue<HumanRead> humanReads =
            new java.util.concurrent.LinkedBlockingQueue<>();

    /**
     * Ask the human for a line of input, through the console's own reader.
     * <p>
     * A job that wants a line cannot read the terminal itself: the console owns it — it holds the
     * tty for keys and mouse, and while a prompt is up its reader consumes whatever is typed, so a
     * second reader on {@code System.in} sees nothing and what the human types is taken as a repl
     * command instead of an answer.  So the request is queued and answered on the console's side:
     * by the read loop when the console is at a prompt, and by the watcher while a foreground job
     * holds it (that thread is the only reader of the terminal in that window, see
     * {@link #watchTerminal}).  One reader, one terminal.
     * <p>
     * Falls back to {@code System.in} where there is no console at all (headless boots, tests).
     *
     * @param prompt what the human is being asked for (markup is resolved)
     * @return the line they typed, without its newline
     */
    private static String readHumanLine(final String prompt) {
        final Console console = Console.LOCAL_INSTANCE;
        if (null == console || null == console.reader || BootLoader.TESTING)
            return new java.util.Scanner(System.in).nextLine();
        final HumanRead request = new HumanRead(prompt, new java.util.concurrent.CompletableFuture<>());
        console.humanReads.add(request);
        if (console.inReadLine)
            // break the console out of the prompt it is showing so its reader thread comes back
            // around and takes this request: accept-line is the widget the console's own bindings
            // submit with
            try {
                console.widgets.callWidget("accept-line");
            } catch (final Exception ignore) {
                // no prompt to break out of: the loop takes the request at its next pass
            }
        // While a foreground job holds the console the watcher is the only reader of the terminal:
        // reading here as well would put two readers on one stream — each taking a share of the
        // bytes, so the job would get a stray character while the person's typing went to the
        // type-ahead buffer, and <enter> would be classified as no key at all.
        try {
            return request.answer().get();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    /**
     * The console's own input, as the stream {@link mSystem#in()} hands out.
     * <p>
     * Each read is one line, asked of the console's reader (see {@link #readHumanLine}), which is
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
                    final String line = readHumanLine(null);
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
                this.screen.linkRows(),
                this.widgetMouseTracking ? "held by the console" : "held by the terminal",
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
        if (!screenMode()) return;
        Graphitty.out(terminal.output(), CLEAR_SCREEN);
        this.screen.clear();
        this.screen.invalidate();
        this.requestScreenPaint();
        this.syncWidgetMouseTracking();
    }

    /**
     * Where every console write lands: recorded in the screen, and — while the console is
     * still filling the terminal — also appended to it (see {@link #screenAppends()}).
     *
     * @param ansi the text, already resolved
     */
    private void screenOutput(final String ansi) {
        if (null == ansi || ansi.isEmpty()) return;
        linkTrace("out len=%d osc8=%b linkMarkup=%b | %s", ansi.length(), ansi.contains("\033]8;"),
                ansi.contains("{{link}}"), Graphitty.strip(ansi).replace("\n", "\\n"));
        // A clear-screen code ({{XX}} — and so :clear, and any print of it) is an ACTION, not
        // content: the terminal is cleared AND the screen's own rows are dropped with it.
        // Clearing only the terminal left every row in the buffer, so the next frame — or a
        // widget moving over those rows — painted the text straight back, which is what :clear
        // used to do.
        if (ansi.contains(CLEAR_SCREEN)) {
            final String rest = ansi.replace(CLEAR_SCREEN, "");
            this.screen.clear();
            this.screen.invalidate();
            this.screenStart = 1;          // cleared: the console's rows start at the top again
            this.screenAppended = false;
            getFloatingSurface().writeToTerminal(CLEAR_SCREEN + "\033[H");
            if (rest.isEmpty()) this.requestScreenPaint();
            else this.screenOutput(rest);
            return;
        }
        this.screen.appendAnsi(ansi);
        if (this.screenAppends()) {
            this.screenAppended = true;
            // Plain text, without the hyperlink: the console is not yet handling clicks on
            // these rows (it does not know which terminal rows they are), and a terminal that
            // is handed a hyperlink takes the click for itself — which is how a link ended up
            // opening the desktop's "Unsupported operation" instead of being followed here.
            getFloatingSurface().writeToTerminal(
                    studio.phaseshift.metatron.isa.mach.type.ui.console.ScreenPainter.withoutLinks(ansi));
            return;
        }
        if (this.screenAppended) {
            // the console owns the rows now: what it appended has moved (the terminal
            // scrolled), so the region is repainted from the buffer rather than trusting
            // what happens to be on screen
            this.screenAppended = false;
            this.screen.invalidate();
        }
        this.requestScreenPaint();
    }

    /**
     * Take the process's stdout for the screen, so text that never went through a
     * routed writer still lands somewhere restorable.
     *
     * <p>The funnel above catches everything written through {@code Graphitty}, but
     * that is not everything a console prints: {@code print} reaches the terminal
     * through {@code GraphittyLogger.none}'s {@code System.out} fallback, Logback's
     * appender holds the stream it started with, and a library may write to stdout
     * on its own.  An uncaptured row is a row that scrolls the terminal and is gone,
     * which is exactly what happened to {@code print} output before this existed.
     *
     * <p>This must run <em>after</em> the terminal is built: jline takes the real
     * stdout when it is constructed, so replacing {@code System.out} afterwards
     * leaves the terminal's own writes on their original stream — the screen paints
     * through jline, and capturing that would be a loop.
     */
    private void installStdoutCapture() {
        if (!screenMode()) return;
        final ScreenOutputStream sink = new ScreenOutputStream(this::screenOutput);
        System.setOut(new java.io.PrintStream(sink, true, java.nio.charset.StandardCharsets.UTF_8));
        // stderr too, and not as an afterthought: logging is configured to write there
        // (conf/logback.xml targets System.err, with the Graphitty layout), so a console
        // that captured only stdout left every log line going to the terminal at the
        // cursor — where the screen's next frame painted over it, and the output looked
        // as if it had stopped.  Logback's console target forwards to System.err on each
        // write, so replacing it here is enough to bring the log into the transcript.
        System.setErr(new java.io.PrintStream(
                new ScreenOutputStream(this::screenOutput), true, java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Paint the screen soon, without blocking the caller.
     *
     * <p>The funnel runs on whatever thread wrote — a job thread streaming output —
     * so it must not wait on the render thread per write.  Requests coalesce: a
     * burst of writes paints on the last frame rather than once per line.
     */
    private void requestScreenPaint() {
        if (!screenMode() || this.screenAppends()) return;
        // Never drop a request: note that a paint is wanted, and let whoever is already
        // painting see it.  A dropped request is a row that stays blank until the next
        // prompt — which is exactly how a widget's erased box survived a quick drag.
        this.screenPaintWanted.set(true);
        if (!this.screenPaintRunning.compareAndSet(false, true)) return;
        getFloatingSurface().writeAndRender(() -> {
            final StringBuilder painted = new StringBuilder();
            try {
                // paint until nothing new arrives mid-pass: the rows are produced here, on
                // the render thread, so every forget that has happened by now is included
                do {
                    this.screenPaintWanted.set(false);
                    painted.append(this.screenFrame());
                } while (this.screenPaintWanted.get());
            } finally {
                this.screenPaintRunning.set(false);
                // a request that arrived between the last check and the flag clearing has
                // no pass to join, so it gets one (this is bounded: the retry either paints
                // or coalesces with a pass that is already running)
                if (this.screenPaintWanted.get()) this.requestScreenPaint();
            }
            return painted.toString();
        });
    }

    /**
     * Lay the region out and return the bytes that bring it up to date (see
     * {@link ConsoleScreen#frame()}), with the cursor handed back to the prompt row.
     */
    private String screenFrame() {
        // while the console is still appending there is nothing to paint: the text is
        // already on the terminal, below everything that was there before it
        if (this.screenAppends()) return "";
        final int promptRow = this.screenPromptRow();
        final int top = this.screenStart > 0 ? this.screenStart : 1;
        this.screen.layout(top, promptRow - top, terminal.getWidth());
        // The pointer follows the links.  A uri arrives with a job's output — AFTER the prompt was
        // drawn, which is when the arming decision was taken, with nothing on screen to click yet.
        // Re-checking here, on the paint that put the link there, is what lets a freshly painted
        // uri answer a click instead of leaving the mouse with the terminal until the next prompt.
        // Writing the mode inside the pass is safe: it moves no cursor, and the frame is written
        // after it.
        this.syncWidgetMouseTracking();
        final String frame = this.screen.frame();
        if (this.inReadLine) {
            // Mid-prompt the cursor is jline's, and jline draws from where it believes the
            // cursor is: moving it here would leave jline's bookkeeping wrong, and the next
            // keystroke would be written at a column that no longer matches what is on
            // screen (characters landing on top of each other at the start of the prompt
            // line).  So the rows are painted with the cursor saved and put back exactly
            // where it was — the same save/restore a widget pass uses.
            return frame.isEmpty() ? "" : "\033[s" + frame + "\033[u";
        }
        // Between prompts the console may place it: the next prompt is drawn from there.
        return frame + "\033[" + promptRow + ";1H";
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
            // ...and take the process's own stdout too, so text that never went
            // through Graphitty still lands in the screen (see the method).
            this.installStdoutCapture();
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
                this.screenStart = 1;
                terminal.writer().print("\033[2J\033[H");
                terminal.writer().flush();
            }
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
            // Hand mouse tracking back to the terminal if widget scrolling
            // owns it — the shell we return to must not inherit it.
            if (this.widgetMouseTracking && terminal.hasMouseSupport()) {
                terminal.writer().print(MOUSE_OFF);
                this.widgetMouseTracking = false;
            }
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
        // The string is Graphitty markup (a prompt, a serialized result) — resolve it
        // with Graphitty directly.  Routing it through the reader's highlighter first
        // (which runs the mtron syntax with graphitty disabled) would interleave ANSI
        // into a ``` fence or {{…}} and stop Graphitty from seeing it.
        final String markup = object instanceof Obj ? this.serializer.write((Obj) object) : object.toString();
        if (screenMode()) {
            // recorded in the screen, and shown either the way any terminal program shows
            // it or by the screen's own painting (see screenOutput)
            this.screenOutput(studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty.string(markup));
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
        // The widget set may have changed since the last prompt, and jline
        // releases mouse tracking at the end of every readLine — so the mode is
        // re-asserted here, once per prompt (the watcher tick only reacts to a
        // change, so nothing chatters in between).  A new prompt also re-arms
        // the pointer if a wheel released it to the terminal.
        this.pointerReleased = false;
        this.syncWidgetMouseTracking(true);
        if (screenMode() && !this.splitMode) {
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
            this.screen.invalidate();
            // And a new prompt is where the live end is: a reader who wheeled back into
            // history gets the newest rows again as soon as they are given something to
            // type at — what a terminal's scroll-to-bottom-on-input does, and without it
            // the command they are about to run would print off-screen.
            this.screen.scrollToTail();
            this.renderScreen(false);
        } else if (this.splitMode && this.activePane != null) {
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
     * Whether terminal mouse tracking is currently owned by widget scrolling
     * (see {@link #syncWidgetMouseTracking()}).
     */
    private volatile boolean widgetMouseTracking = false;

    /**
     * When true the pointer has been handed back to the terminal (mouse tracking
     * disabled) so the wheel scrolls the terminal's own scrollback.  Re-armed on
     * the next prompt or when a widget is focused via {@code alt}+{@code w}.
     */
    private volatile boolean pointerReleased = false;

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
        } else {
            this.activeWidgetKey = FloatingSurface.widgetKey(widget);
            getFloatingSurface().setFocusKey(this.activeWidgetKey);
            // re-arming a widget re-arms the pointer, so a wheel released to
            // the terminal is handed back to the widgets
            this.pointerReleased = false;
        }
        // fire-and-forget: a click must never stall the console thread on a
        // render, and the pointer decision only needs the focus itself
        getFloatingSurface().render();
        this.syncWidgetMouseTracking();
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
            LOG.debug("focus key {{y}}%s{{X}} not found among {{y}}%d{{X}} floating widgets; cycling restarts at the first — see {{m}}:widgets{{X}}",
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
        getFloatingSurface().nudge(active, widthDelta, heightDelta);
    }

    // ========== Floating Widget Scrolling ==========
    // A pinned widget draws through a viewport (its style height, else the
    // terminal), so content that does not fit is not discarded — it is simply
    // off the viewport, still alive in the widget's body.  These methods move
    // that viewport, which is how the text scrolled out of sight is read again.

    /**
     * Scroll the focused floating widget's viewport by {@code (dx, dy)} cells.
     * Positive {@code dy} moves toward newer content, positive {@code dx}
     * toward later columns.
     *
     * @return true when the focused widget accepted the scroll
     */
    public boolean scrollActiveWidget(final int dx, final int dy) {
        final Widget<?> active = getActiveWidget();
        // nothing focused is not an error — a scroll key is allowed to fall
        // through to the binding that owned it before
        if (null == active) return false;
        if (!getFloatingSurface().scroll(active, dx, dy)) {
            LOG.warn("focused floating widget {{y}}%s{{X}} does not scroll", this.activeWidgetKey);
            return false;
        }
        this.reportWidgetScroll(active);
        return true;
    }

    /**
     * Scroll the focused floating widget by one viewport page
     * ({@code direction} &lt; 0 = back in the text, &gt; 0 = forward).
     */
    public boolean pageActiveWidget(final int direction) {
        final Widget<?> active = getActiveWidget();
        // nothing focused is not an error — a scroll key is allowed to fall
        // through to the binding that owned it before
        if (null == active) return false;
        final int page = getFloatingSurface().pageRows(active) * (direction < 0 ? -1 : 1);
        if (!getFloatingSurface().scroll(active, 0, page)) {
            LOG.warn("focused floating widget {{y}}%s{{X}} does not scroll", this.activeWidgetKey);
            return false;
        }
        this.reportWidgetScroll(active);
        return true;
    }

    /**
     * Put the focused floating widget's viewport back on its newest content —
     * the state it starts in, and the one it returns to as content arrives.
     */
    public boolean tailActiveWidget() {
        final Widget<?> active = getActiveWidget();
        // nothing focused is not an error — a scroll key is allowed to fall
        // through to the binding that owned it before
        if (null == active) return false;
        getFloatingSurface().scrollToTail(active);
        this.reportWidgetScroll(active);
        return true;
    }

    /**
     * Jump the focused floating widget's viewport to an absolute body row
     * (clamped to the content).
     */
    public boolean scrollActiveWidgetTo(final int row) {
        final Widget<?> active = getActiveWidget();
        // nothing focused is not an error — a scroll key is allowed to fall
        // through to the binding that owned it before
        if (null == active) return false;
        getFloatingSurface().scrollTo(active, row);
        this.reportWidgetScroll(active);
        return true;
    }

    /**
     * @return true when the focused floating widget has content off its
     * viewport that a scroll would reveal
     */
    public boolean activeWidgetScrolls() {
        final Widget<?> active = getActiveWidget();
        return null != active && getFloatingSurface().canScroll(active);
    }

    /**
     * A one-line description of the focused widget's viewport, e.g.
     * {@code rows 12-31/240} (empty when it is not scrollable).
     */
    public String activeWidgetScrollInfo() {
        final Widget<?> active = getActiveWidget();
        if (null == active) return "";
        final String info = getFloatingSurface().scrollInfo(active);
        return info.isEmpty() ? "" : getFloatingSurface().resolveKey(this.activeWidgetKey) + " " + info;
    }

    /**
     * Echo a widget's viewport position to the status line, so a scroll (which
     * changes nothing a cursor can point at) is still visibly acknowledged.
     */
    private void reportWidgetScroll(final Widget<?> widget) {
        final String key = getFloatingSurface().resolveKey(this.activeWidgetKey);
        final String info = getFloatingSurface().scrollInfo(widget);
        StatusLine.message(str(info.isEmpty()
                ? "{{y}}%s{{X}} {{w}}has nothing off its viewport{{X}}".formatted(key)
                : "{{y}}%s{{X}} %s".formatted(key, info)));
    }

    /**
     * A pointer click at a terminal cell.
     *
     * <p>The gesture vocabulary is the one a pointer implies: the widget under
     * the pointer is focused, its own affordances get first refusal (an
     * accordion's {@code [-]} / {@code [+]} toggle, for example — see
     * {@link Widget#onClick(int, int)}), and a click on terminal that has no
     * widget under it means no widget owns the user's attention, so the focus
     * is cleared.
     *
     * @param row 1-based terminal row of the click
     * @param col 1-based terminal column of the click
     * @return true when a widget consumed the click for itself
     */
    public boolean clickAt(final int row, final int col) {
        return this.clickAt(row, col, false);
    }

    /**
     * As {@link #clickAt(int, int)} with a held control key (see {@link #mousePressed(int, int, boolean)}).
     */
    public boolean clickAt(final int row, final int col, final boolean follow) {
        final Widget<?> hit = getFloatingSurface().widgetAt(row, col);
        if (Boolean.getBoolean("metatron.render.trace"))
            rawErr().println("[click] row=" + row + " col=" + col + " widget=" + (null != hit)
                    + " inRead=" + this.inReadLine + " appends=" + this.screenAppends()
                    + " rows=" + this.screen.rows() + " top=" + this.screen.top());
        if (null == hit) {
            // A link in the transcript is the screen's own affordance, and it answers before
            // the focus is touched: it types rather than focuses.
            if (this.openScreenLink(row, col, follow)) return true;
            // Empty terminal — including the prompt area: nothing owns the
            // user's attention, so the focus is dropped.  The pointer stays
            // armed, though — while a widget is on screen the next click can
            // still focus one (no alt+w re-entry).  Use Shift+drag for native
            // terminal text selection while the widgets hold the mouse.
            if (null != getActiveWidget()) focusWidget(null);
            else syncWidgetMouseTracking();
            return false;
        }
        final boolean focused = getActiveWidget() == hit;
        if (!focused) focusWidget(hit);
        // The affordance gets its own render: a click that both focuses a widget
        // and toggles it must show the toggled state (focusWidget's render ran
        // before the widget acted).  surface.render() coalesces, so a click
        // never costs more than one pass.
        final boolean consumed = getFloatingSurface().click(hit, row, col, false);
        if (consumed) {
            getFloatingSurface().render();
        }
        return consumed;
    }

    // ── the drag gesture ──────────────────────────────────────────────
    //
    // Where a widget sits and how big it is WHILE the pointer works on it is VIEW state,
    // so it lives here and on the widget's slot — never in the widget's rec.  Only the
    // release writes the rec (style top/left after a move, width/height after a resize),
    // because that is what has to outlive the session: every .display() re-hydrates the
    // widget into a fresh instance, and geometry that only lived in this console would be
    // gone the next time the widget's content changed.

    /**
     * The widget the pointer is working on, or null when no drag is in flight.
     */
    private volatile Widget<?> dragWidget = null;
    /**
     * Which handle was taken hold of: the chevron (move) or the corner marker (resize).
     */
    private FloatingSurface.Handle dragHandle = null;
    /**
     * Where the pointer took hold — the handle's own cell.
     */
    private int dragRow = 0;
    private int dragCol = 0;
    /**
     * The box at press time: a resize is a delta from it, not an accumulation of events.
     */
    private int dragWidth = 0;
    private int dragHeight = 0;
    /**
     * True once the pointer actually moved, so a press-and-release stays a click.
     */
    private boolean dragMoved = false;

    /**
     * A pointer press: taking hold of one of the focused widget's handles starts a
     * gesture, and anything else keeps the click semantics (focus the widget under the
     * pointer, let it work its affordance, or clear the focus on empty terminal).
     *
     * <p>The handles are the two cells a widget can be worked from, and both are
     * painted in the widget's own box — so the cell the user grabs IS the geometry the
     * gesture changes: the chevron ({@code ▶}) in the top-left cell is the widget's
     * origin, which makes a move a translation, and the marker ({@code ◢}) in the
     * bottom-right cell is the far corner of its box, which makes a resize a
     * width/height delta.  No offset arithmetic, and targets that small stay easy to
     * hit on purpose: nobody clicks a corner by accident, and the body of the widget
     * stays free for selection, scrolling and affordances.
     *
     * <p>An affordance under the press still wins: the click runs first, and only a
     * press the widget did not consume becomes a grab.  A widget whose own top-left
     * cell acts on a click (a selector's first row, say) keeps that click and simply
     * cannot be dragged by its chevron.
     *
     * @param row 1-based terminal row of the press
     * @param col 1-based terminal column of the press
     * @return true when the press was consumed — by the widget's affordance or by a grab
     */
    public boolean mousePressed(final int row, final int col) {
        return this.mousePressed(row, col, false);
    }

    /**
     * As {@link #mousePressed(int, int)} with a held control key: on a link it means follow the
     * uri (type it AND submit it) rather than only type it.
     */
    public boolean mousePressed(final int row, final int col, final boolean follow) {
        linkTrace("press row=%d col=%d follow=%b | %s", row, col, follow, this.linkReport());
        // whose handles these are: the ALREADY focused widget's.  A press on the corner
        // of an unfocused widget is how you focus it (no handle is drawn there yet, so
        // the user did not aim at one), and a second press then grabs.
        final Widget<?> focused = getActiveWidget();
        final FloatingSurface.Handle handle = null == focused
                ? null : getFloatingSurface().handleAt(focused, row, col);
        // the click runs first so a widget's own affordance keeps its cell
        final boolean consumed = this.clickAt(row, col, follow);
        if (null == handle || consumed) return consumed;
        final FloatingSurface.Placement placement = getFloatingSurface().placement(focused);
        this.dragWidget = focused;
        this.dragHandle = handle;
        this.dragRow = row;
        this.dragCol = col;
        this.dragWidth = null == placement ? 0 : placement.width();
        this.dragHeight = null == placement ? 0 : placement.height();
        this.dragMoved = false;
        // hold the pointer for the whole gesture: a mid-drag hand-back to the
        // terminal would drop the release event and leave the widget in flight
        this.syncWidgetMouseTracking(true);
        return true;
    }

    /**
     * A pointer motion with a button held: the grabbed handle follows the pointer — the
     * chevron moves the widget to where the pointer is, the corner marker sizes the box
     * by how far the pointer has come from where it took hold.
     *
     * @return true when a gesture is in flight
     */
    public boolean mouseDragged(final int row, final int col) {
        final Widget<?> widget = this.dragWidget;
        if (null == widget) return false;
        if (row == this.dragRow && col == this.dragCol) return true;   // still on the handle
        this.dragMoved = true;
        this.applyDrag(widget, this.dragHandle, row, col);
        return true;
    }

    /**
     * Put the gesture's current pointer cell to work on the widget: measured from the
     * press cell, so a drag is a delta and not an accumulation of motion events.
     */
    private void applyDrag(final Widget<?> widget, final FloatingSurface.Handle handle,
                           final int row, final int col) {
        // what the box covers now is what the move is about to leave behind: the
        // screen has to put those rows back (see repairRows), and asking here — on the
        // console thread, once per motion event — keeps a fast drag from outrunning the
        // damage the render pass reports
        final int[] before = getFloatingSurface().lastRows(widget);
        if (FloatingSurface.Handle.RESIZE == handle)
            getFloatingSurface().resizeTo(widget,
                    this.dragWidth + (col - this.dragCol),
                    this.dragHeight + (row - this.dragRow));
        else
            // the corner lands where the pointer is — placeAt clamps so the handle itself
            // can never be dragged off screen and out of reach
            getFloatingSurface().placeAt(widget, row, col);
        if (null != before) this.repairRows(before[0], before[1]);
    }

    /**
     * A pointer release: park the widget — write what the gesture changed into its style
     * so it survives re-hydration — and end the gesture.  A press and release that never
     * moved is just a click on a handle, and the press already focused the widget.
     *
     * @return true when a gesture was in flight
     */
    public boolean mouseReleased(final int row, final int col) {
        final Widget<?> widget = this.dragWidget;
        if (null == widget) return false;
        final FloatingSurface.Handle handle = this.dragHandle;
        if (this.dragMoved) {
            this.dragMoved = false;
            // the release cell is the final word: a terminal need not send a motion
            // event for the last cell the pointer crossed
            this.applyDrag(widget, handle, row, col);
            this.parkDrag(widget, handle);
            // where the gesture ends, repair for certain: the per-motion repair is
            // coalesced, so the last one may have been skipped
            if (screenMode()) this.repairRows(row, row);
        }
        this.dragWidget = null;
        this.dragHandle = null;
        this.syncWidgetMouseTracking(true);
        return true;
    }

    /**
     * Write what the gesture changed into the widget's style — the rec is where a
     * widget's geometry lives, so both survive the re-hydration every update performs
     * and a later session.  A move parks {@code top}/{@code left}; a resize parks
     * {@code width}/{@code height} (and, harmlessly, the position it was resized from,
     * because the anchor's free edge moves as the box grows).  The rest of the style is
     * untouched: this reads it, changes a couple of keys, and puts it back.
     */
    private void parkDrag(final Widget<?> widget, final FloatingSurface.Handle handle) {
        final FloatingSurface.Placement placement = getFloatingSurface().placement(widget);
        if (null == placement) return;
        try {
            final var style = widget.style().top(placement.top()).left(placement.left());
            if (FloatingSurface.Handle.RESIZE == handle)
                style.width(placement.width()).height(placement.height());
            style.applyStyle();
        } catch (final Exception e) {
            LOG.warn("pointer: could not park the widget's geometry: {{r}}%s{{X}}", e.getMessage());
        }
    }

    /**
     * True while the pointer is moving a widget.
     */
    public boolean dragging() {
        return null != this.dragWidget;
    }

    /**
     * Terminal mouse modes this console turns on: button events (1000), button-event
     * tracking (1002) — motion while a button is held, which is what a drag is — and
     * the SGR encoding (1006), and nothing else.
     *
     * <p>Deliberately NOT jline's {@code MouseSupport.trackMouse(Normal)}, which
     * also enables {@code ?1005h} — the legacy UTF-8 coordinate encoding.  With
     * 1005 and 1006 enabled together a terminal may report the pointer in either
     * encoding, and a mis-decoded column is exactly what makes a small target
     * (an accordion's {@code [-]} cell) impossible to hit while a large one (the
     * widget body) still works.
     */
    private static final String MOUSE_ON = "\033[?1000h\033[?1002h\033[?1006h";
    /**
     * Every mode jline may have enabled, off — a full hand-back to the terminal.
     */
    private static final String MOUSE_OFF =
            "\033[?1000l\033[?1002l\033[?1003l\033[?1005l\033[?1006l\033[?1015l\033[?1016l";

    /**
     * Turn terminal mouse tracking on or off to match what is on screen.
     *
     * <p>The pointer belongs to the widgets while any of them is on screen
     * (or one is focused): that is what lets a cold click focus a widget,
     * and a click after unfocusing re-focus one.  Only with nothing pinned
     * does the terminal get its own mouse back (wheel, drag-selection).
     *
     * <p>Called only when the prompt owns the terminal (the console's watcher
     * thread, and the console thread itself): while a job holds the console the
     * mouse stays off, so its bytes can never be mistaken for typed input.
     */
    public void syncWidgetMouseTracking() {
        syncWidgetMouseTracking(false);
    }

    /**
     * @param force re-assert the mode even when it has not changed — used once
     *              per prompt, because jline releases mouse tracking at the end
     *              of every readLine, so the flag alone would leave the pointer
     *              dead from the second prompt on
     */
    public void syncWidgetMouseTracking(final boolean force) {
        final Terminal term = getTerminal();
        if (null == term || !term.hasMouseSupport()) return;
        final int widgets = getFloatingSurface().drawnWidgetCount();
        // a drag holds the pointer whatever else changes — the gesture is not over
        // until the button comes up
        final boolean wanted = this.dragging()
                || pointerWanted(null != getActiveWidget(), this.pointerReleased, widgets)
                // ...and while the transcript holds a link: clicking one is the screen's own
                // affordance, and a click is only ours while we hold the pointer.  A release the
                // reader asked for (alt+s) outranks it: the pointer goes back to the terminal so
                // shift-drag selection works, and stays there until the next prompt re-arms it —
                // re-arming on the next paint would take it back before they could select anything
                || (screenMode() && !this.pointerReleased && this.screen.hasLinks());
        if (wanted == this.widgetMouseTracking && !force) return;
        linkTrace("mouse %s (wanted=%b force=%b dragging=%b focused=%b released=%b widgets=%d links=%d rows=%d)",
                wanted ? "ON" : "OFF", wanted, force, this.dragging(), null != getActiveWidget(),
                this.pointerReleased, widgets, this.screen.linkRows(), this.screen.rows());
        try {
            // only the enable is worth re-asserting (jline turns it off again at
            // the end of every readLine); a disable is written once, on the way out
            if (wanted) {
                term.writer().print(MOUSE_ON);
            } else if (this.widgetMouseTracking) {
                term.writer().print(MOUSE_OFF);
            }
            term.writer().flush();
            this.widgetMouseTracking = wanted;
        } catch (final Exception e) {
            LOG.warn("could not %s mouse tracking: {{r}}%s{{X}}", wanted ? "enable" : "disable", e.getMessage());
        }
    }

    /**
     * Whether terminal mouse tracking is currently owned by widget scrolling.
     */
    public boolean widgetMouseTracking() {
        return this.widgetMouseTracking;
    }

    /**
     * Hand the pointer back to the terminal (disable mouse tracking) so the
     * wheel scrolls the terminal's own scrollback.  Triggered by a wheel over
     * empty terminal with nothing focused; re-armed on the next prompt or when
     * a widget is focused via {@code alt}+{@code w}.
     */
    public void releasePointer() {
        this.pointerReleased = true;
        // drop the focus too, so the widgets read as fully detached from the
        // pointer and the focus marker disappears with the re-render
        this.focusWidget(null);
    }

    /**
     * Make the screen the truth again after something drew outside it.
     * <p>
     * The screen paints rows at absolute positions and keeps its own idea of what is on each one;
     * anything written straight to the terminal — an answer echoed while a job held the console —
     * leaves that idea wrong, and the next paint then fights the write that follows it.
     * <p>
     * Only the forgetting happens here.  {@link #requestScreenPaint()} runs the paint pass on
     * whatever thread asks, and this is called from the reader: a frame drawn there waits on the
     * render lock while holding the reader, which is a console that stops responding until the
     * terminal gives up on it.  Marking the rows unknown is enough — the next output or prompt
     * repaints them, and repainting stale rows is exactly what invalidating prevents.
     */
    private void resyncScreen() {
        if (!screenMode()) return;
        this.screen.invalidate();
    }

    /**
     * Hand the terminal back on the way out.
     * <p>
     * Mouse tracking is a terminal mode, not a console flag: the terminal keeps reporting presses,
     * drags and the wheel until something turns it off.  Exiting with it armed leaves the shell
     * receiving mouse bytes for every drag, so nothing outside metatron can be selected — and the
     * sequences type themselves into the next command.  The console writes the mode itself (jline
     * does not know about it), so it is also the console's to write off, on every exit path:
     * quit, ctrl-c, or the launcher restarting the VM.
     */
    private void releaseTerminalOnExit() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                final Terminal term = getTerminal();
                if (null == term) return;
                term.writer().print(MOUSE_OFF);
                term.writer().flush();
            } catch (final Exception ignore) {
                // the process is going away: nothing here is worth an error
            }
        }, "metatron-console-teardown"));
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
        if (!screenMode()) return;
        if (deferIfTyping && deferNonActivePaneRender()) return;
        // while the console is appending, the prompt the reader is looking at is jline's
        // and it is already exactly where the cursor is: nothing to paint, nothing to
        // move — the widgets are still drawn (they float wherever they were pinned)
        if (this.screenAppends()) {
            getFloatingSurface().render();
            return;
        }
        // screenFrame() lays the region out and knows whether the console may place the
        // cursor (between prompts) or must put it back where it was (mid-prompt)
        getFloatingSurface().writeToTerminal(this.screenFrame());
        getFloatingSurface().render();
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
        if (!screenMode()) return;
        this.screen.scrollBy(delta);
        // the window moved under the rows, so what is on screen is no longer known
        this.screen.invalidate();
        this.repaintScreen();
    }

    /**
     * Repaint the screen region now, without blocking the caller's interest in the result.
     */
    private void repaintScreen() {
        getFloatingSurface().writeAndRender(this::screenFrame);
    }

    /**
     * A click on a link in the transcript: type the dereference of its uri at the prompt,
     * leaving the reader one &lt;enter&gt; from the resource.
     *
     * <p>The uri is not dereferenced for the reader, and that is the point: in metatron a uri
     * IS an expression, so typing {@code *<uri>} puts the reader in charge of the resource —
     * they can edit it, extend it, or walk somewhere else from it — while still being one
     * keystroke from following the link.  It also means a link cannot run something the
     * reader did not see.
     *
     * @param row 1-based terminal row of the click
     * @param col 1-based terminal column of the click
     * @return true when a link was there and its uri is now in the prompt
     */
    /**
     * How far from the clicked row the console will look for the link it was aimed at, nearest
     * first.  0 is the row itself; the rest absorb the row-origin difference between the screen's
     * model and the terminal's display, which put a uri the reader could see one or two rows away
     * from where they had to click.
     */
    private static final int[] LINK_ROW_PROBE = {0, 1, -1, 2, -2};

    /**
     * The uri a link at this terminal row and column resolves to, or null when that row has none.
     */
    private String linkAtRow(final int row, final int col) {
        final int index = row - this.screen.top();
        if (index < 0 || index >= this.screen.rows()) return null;
        final java.util.List<String> window = this.screen.visible();
        return index < window.size() ? ScreenPainter.linkAt(window.get(index), col - 1) : null;
    }

    private boolean openScreenLink(final int row, final int col, final boolean follow) {
        // Resolving a click needs the console's own rows: while it is appending (opt-in), a
        // terminal row is not a buffer row and the pointer stays the terminal's.
        if (!screenMode() || this.screenAppends() || !this.inReadLine) {
            linkTrace("  refused: screen=%b appends=%b inReadLine=%b",
                    screenMode(), this.screenAppends(), this.inReadLine);
            return false;
        }
        // One click, one gesture: type the dereference and let the reader decide, or — with control
        // held — submit it as well.  Nothing is remembered between clicks, so a repaint that moves
        // the row cannot make the next click answer for the last one.
        String target = null;
        int landedOn = row;
        // The row the reader points at and the row the console believes it painted can differ by a
        // row or two (the terminal's origin against the screen's), and a uri they can plainly see
        // should not have to be clicked "about here": the exact row answers first, then the nearest
        // rows do, nearest first.
        for (final int offset : LINK_ROW_PROBE) {
            final String found = this.linkAtRow(row + offset, col);
            linkTrace("  probe %+d row %d -> %s", offset, row + offset, found);
            if (null != found) {
                target = found;
                landedOn = row + offset;
                break;
            }
        }
        if (null == target) return false;
        linkTrace("  resolved '%s' from row %d (clicked row %d, top %d, rows %d, follow=%b)",
                target, landedOn, row, this.screen.top(), this.screen.rows(), follow);
        final String expression = "*" + target;
        if (follow) {
            // follow it: submit what the click resolved, with no keystroke in between.  The uri is
            // asserted rather than appended — a click may already have typed it, and appending
            // would submit it twice.
            if (!expression.equals(this.reader.getBuffer().toString())) {
                this.reader.getBuffer().clear();
                this.reader.getBuffer().write(expression);
            }
            // through Widgets, the way the console's own bindings submit a line
            this.widgets.callWidget("accept-line");
            return true;
        }
        this.reader.getBuffer().write(expression);
        // the reader thread owns the line: ask jline to draw what was typed
        this.reader.callWidget(LineReader.REDISPLAY);
        return true;
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
    private void repairRows(final int from, final int to) {
        if (!screenMode()) return;
        this.screen.forget(from, to);
        this.requestScreenPaint();
    }

    /**
     * The console's screen (see {@link ConsoleScreen}) — for commands that report on it.
     */
    public ConsoleScreen getScreen() {
        return this.screen;
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
                // The prompt owns the terminal: keep the pointer alive for
                // whatever widgets are on screen (a widget floated while the
                // user sat at the prompt turns the mouse on within a tick).
                if (!this.watching) this.syncWidgetMouseTracking();
                CommonUtil.sleepThread(WATCHER_IDLE_MS);
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
                // a job asking the human takes the keystrokes first: this thread is the only reader
                // while the console is busy, so the answer has to be served from here (see readHumanLine)
                if (this.answerHumanRead(c)) continue;
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
     * reader racing jline for them — and also when a widget is in user mode:
     * the widget (e.g. a selector or modal) reads the terminal itself with its
     * own {@code BindingReader}, so the watcher must not race it for keystrokes.
     */
    private boolean beginWatch() {
        synchronized (this.watchGate) {
            //if (Console.userMode.get())
            //    return false;  // a widget owns the terminal; never race its reader
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
    /**
     * Delete/backspace, as the answer loop edits with (see {@link #answerHumanRead}).
     */
    private static final int DEL = 0x7f;

    /**
     * The line being typed as an answer to a job's question (see {@link #answerHumanRead}).
     */
    private final StringBuilder humanAnswer = new StringBuilder();

    /**
     * Take one keystroke as the human's answer to the job that is asking, when one is.
     * <p>
     * Served here because this thread is the only reader of the terminal while a foreground job
     * holds the console: the answer is echoed and edited like a line, and {@code <enter>} releases
     * the job waiting on it.  Keys are consumed only while a question is outstanding — otherwise
     * this is a plain keystroke and the hotkeys decide what it means.
     *
     * @return true when the keystroke was consumed as part of an answer
     */
    private boolean answerHumanRead(final int c) {
        final HumanRead asked = this.humanReads.peek();
        if (null == asked) return false;
        if ('\r' == c || '\n' == c) {
            final String answer = this.humanAnswer.toString();
            this.humanAnswer.setLength(0);
            this.humanReads.poll();
            this.echoTypedChar('\n');
            asked.answer().complete(answer);
            // The answer was echoed straight to the terminal (echoTypedChar), not into the screen,
            // and the job's own output kept painting absolute rows in the meantime: the two do not
            // agree about where the cursor is or which rows are current.  Reconcile before anything
            // else draws, or every later paint corrects a layout the next write undoes — which reads
            // as a transcript that creeps up a line and back down.
            this.resyncScreen();
            return true;
        }
        if (DEL == c || 0x08 == c) {
            if (this.humanAnswer.length() > 0) {
                this.humanAnswer.setLength(this.humanAnswer.length() - 1);
                try {
                    terminal.writer().print("\b \b");
                    terminal.writer().flush();
                } catch (final Exception ignore) {
                    // echo is a courtesy
                }
            }
            return true;
        }
        if (c >= 32) {
            this.humanAnswer.append((char) c);
            this.echoTypedChar(c);
        }
        return true;
    }

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
        final Obj result;
        try {
            result = future.get();
        } catch (final Exception e) {
            this.backgroundJobs.remove(mach);
            LOG.none("{{-X-&|0}}");
            this.printResult(fail(e));
            return;
        }
        // the job has halted — only now drop it from the tracking list, so
        // :bg lists it for the whole time it is still running
        this.backgroundJobs.remove(mach);
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
        Graphitty.out(terminal.output(), this.redrawLine());
        terminal.flush();
    }

    /**
     * The live input line as the user sees it while typing: syntax color and nothing else.
     * <p>
     * This text is the user's OWN, so graphitty markup must stay literal -- resolving it here
     * rewrites the line under the cursor mid-word (a name in braces is swallowed as a rule, a
     * tag they have not closed yet changes what they already typed, and {{XX}} clears the
     * screen out from under them).  `redrawBuffer` runs whenever a widget or a trace renders
     * mid-line, which is why ordinary typing feels like it is being rewritten.
     * <p>
     * The reader's highlighter is built with {@code ignoreGraphitty}, so jline's own redraw of
     * the same line and this one agree.  The markup still applies to what they SUBMIT: the
     * echo and the results go through {@link Highlighter#format(Object)}.
     */
    public String redrawLine() {
        final String line = this.reader.getBuffer().toString();
        final String ansi = Highlighter.line(line);
        if (Boolean.getBoolean("metatron.render.trace"))
            rawErr().println("[line] in=<" + line + "> out=<" + ansi + ">");
        return ansi;
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
                final HumanRead asked = this.humanReads.poll();
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
                final String typedAhead = this.hotkeys.takePendingText();
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
                    if (this.splitMode) this.renderPanes();
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
                    if (screenMode() && !screenAppends()) this.screenOutput(this.prompt() + "\n");
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
                    if (this.splitMode && this.activePane != null) {
                        // the line is the user's own: colored, never resolved (see Highlighter.line)
                        this.activePane.appendOutput(Graphitty.string(this.currentLanguage.prompt)
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
                        this.screen.appendAnsi(Graphitty.string(this.currentLanguage.prompt)
                                + Highlighter.line(line) + "\n");
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
            if (screenMode()) {
                // the banner is history like anything else: through the same funnel as every
                // other write, so it is recorded in the screen AND shown — appended below
                // whatever the terminal already had while the console is still filling it,
                // painted once the console owns the rows
                final String banner = this.at(HEADER).isNoObj()
                        ? CommonUtil.getHeader(HEADER_FILE, name, true)
                        : Graphitty.string(Str.Helper.cleanString(this.at(HEADER)));
                this.screenOutput(banner);
                // screenOutput takes resolved text: the prompt prefix is markup
                this.screenOutput(Graphitty.string("\t{{b}}ve{{y}}rs{{m}}ion {{y}}" + METATRON_VERSION + "{{X}}\n"));
                this.screenOutput(Graphitty.string("   {{m}}:help{{X}} for console features\n\n"));
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