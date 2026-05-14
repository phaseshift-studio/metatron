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

package studio.phaseshift.metatron.isa.mach.type.ui.console;

import org.jline.builtins.Commands;
import org.jline.builtins.ConfigurationPath;
import org.jline.builtins.TTop;
import org.jline.console.SystemRegistry;
import org.jline.console.impl.Builtins;
import org.jline.console.impl.SystemRegistryImpl;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import org.jline.widget.Widgets;
import org.slf4j.event.Level;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MInst;
import studio.phaseshift.metatron.isa.m.type.reflect.JInst;
import studio.phaseshift.metatron.isa.m.type.reflect.JRec;
import studio.phaseshift.metatron.isa.m.type.reflect.JRecElement;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.LogObj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.machine.SwarmMachine;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.*;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.jline.keymap.KeyMap.*;
import static studio.phaseshift.metatron.BootLoader.BOOTING;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instA;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.HEADER_FILE;

public class Console extends JRec<Console> implements Closeable, Runnable {

    public static final fURI CONSOLE_TID = MACH_ISA_TID.extend("console");
    @JRecElement(key = "metatron_version", rng = "/m/uri")
    public static final String METATRON_VERSION = "0.1-alpha";
    @JRecElement(key = "mtron", rng = "/m/uri")
    public static final String MTRON = "mtron";
    // @JRecElement(tid = FILE_TID_STRING)
    public static final String MTRON_NANORC = "mtron.nanorc";
    //@JRecElement(tid = FILE_TID_STRING)
    //@JRecElement(tid = FILE_TID_STRING)
    public static Path HISTORY_FILE = Paths.get(".metatron.history");
    @JRecElement(key = "history", rng = "/m/inst")
    public Inst history = instA(f("dummy"));
    @JRecElement(key = "input", rng = "/m/uri")
    public Uri input = uri("");
    @JRecElement(key = "prefix", rng = "/m/str")
    public String prefix = "";
    @JRecElement(key = "postfix", rng = "/m/str")
    public String postfix = "";
    private final GraphittyLogger LOG = Graphitty.log(this);
    private static Terminal terminal;
    private final LineReader reader;
    private final StatusLine status;
    private final static ConfigurationPath configurations = new ConfigurationPath(
            Paths.get("conf"),                                     // application-wide settings
            Paths.get(System.getProperty("user.home"), ".metatron") // user-specific settings
    );
    public static Console LOCAL_INSTANCE = null;
    public Machine machine = null;

    // ========== Split Pane Support ==========
    // Pane tree: root can be a single Pane or a SplitContainer with nested panes
    private PaneNode paneRoot;
    private Pane activePane;
    private final AtomicBoolean needsRedraw = new AtomicBoolean(false);
    private boolean splitMode = false;  // True when we have more than one pane

    /**
     * Minimum ms between content-only live redraws triggered by pane output.
     */
    private static final long PANE_RENDER_THROTTLE_MS = 80;
    private volatile long lastPaneRenderMs = 0;

    /** While true, non-active-pane rendering is deferred until the keyboard has been
     * idle for {@link #INPUT_IDLE_THRESHOLD_MS}.  Set at the top of the REPL loop,
     * cleared when readLine() returns. */
    private volatile boolean inReadLine = false;
    /** Timestamp of the last detected keystroke (buffer-length change). */
    private volatile long lastKeyActivityMs = 0;
    /** Snapshot of buffer length used to detect keystrokes via polling. */
    private volatile int lastBufferLength = 0;
    private volatile boolean pendingPaneFlush = false;
    /** Milliseconds of keyboard inactivity after which non-active panes may render. */
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

    public static final Type CONSOLE_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(CONSOLE_TID)
            .isaPredicate(rec())
            .constructor(instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(CONSOLE_TID), lst(T(REC_TID)), (lhs, inst) -> {
                final Console console = new Console(inst.arg(0).as(), inst.arg(0).vid());
                BootLoader.getExecutor().submit(console);
                return console;
            })).create();

    public Console(final Rec options, final fURI vid) {
        super(options.jvm(), CONSOLE_TID, vid);
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
                        this.activePane.machine().interrupt();
                    } else if (null != this.machine) {
                        this.machine.interrupt();
                    }
                }
            }).encoding(StandardCharsets.UTF_8).system(true).build();
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
            this.reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .appName("metatron")
                    .history(new DefaultHistory())
                    .highlighter(new Highlighter(new ObjConsoleSerializer()))
                    .parser(parser)
                    .variable(LineReader.HISTORY_FILE, HISTORY_FILE)
                    .option(LineReader.Option.AUTO_FRESH_LINE, true)
                    .option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .variable(LineReader.SECONDARY_PROMPT_PATTERN, Graphitty.string("{{-X&v1&^1&m}}     {{g}}| {{X}}"))
                    .variable(LineReader.INDENTATION, 0)
                    //  .completer(new MCompleter(this))
                    .build();
            new CustomWidgets(this.reader);
            this.status = new StatusLine(this);
            BootLoader.getExecutor().submit(this.status);
            this.history = auto_(instC(f("history").dom(ALL).rng(REC_TID.maybeSome()), lst(T(ALL)),
                    (lhs, inst) -> objs(IteratorUtil.stream(this.reader.getHistory().reverseIterator())
                            .sorted(Comparator.comparing(History.Entry::time))
                            .map(s -> rec(uri(TIME), str(s.time().toString()), uri(ENTRY), str(s.line())))
                    ))).tryToInst().as();
            this.input = null == this.vid() ? uri("") : uri(this.vid().extend("in"));
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
        terminal.writer().write(((Highlighter) this.reader.getHighlighter()).write(object));
    }

    public static Terminal getTerminal() {
        return Console.terminal;
    }

    public LineReader getReader() {
        return this.reader;
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
            this.reader.setVariable(LineReader.SECONDARY_PROMPT_PATTERN,
                    Graphitty.string("{{-X&v1&^1&m}}     {{g}}| {{X}}"));
        }
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

    @JRecElement(key = "pane", rng = "/m/lst[/m/mach/console/pane{*}]", mimic = JRecElement.Mimic.FIELD)
    //@JInst(tid = "pane", dom = "#{?}", rng = "/m/lst", attach = JInst.Attach.OBJ)
    public List<Pane> getAllPanes() {
        return null == this.paneRoot ? new ArrayList<>() : this.paneRoot.getAllPanes();
    }

    public boolean isSplitMode() {
        return this.splitMode;
    }

    /**
     * Request a redraw of the pane layout. Thread-safe.
     * Called by panes when their output buffer changes.
     */
    public void requestRedraw() {
        this.needsRedraw.set(true);
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

        // Clear screen area for panes (not status line)
        terminal.writer().print("\u001b[1;1H"); // Move to top-left
        for (int row = 1; row <= height; row++) {
            terminal.writer().print("\u001b[" + row + ";1H");
            terminal.writer().print(" ".repeat(width));
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
            // In split mode, output to active pane's buffer
            this.activePane.appendResult(result);
        } else {
            // Normal mode - direct output
            result.stream().forEach(o -> {
                this.write("{{-X-}}{{m}}=={{g}}>{{X}}");
                this.write(o);
                this.write("\n");
            });
        }
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
        /// ///////////////////////////////////////////////////
        ////////////////// PANEL SUBSCRIPTIONS ////////////////
        /// ///////////////////////////////////////////////////
        if (line.trim().startsWith(":" + SUBQ)) {
            final String strip = line.substring(5).trim();
            final fURI subURI = strip.isEmpty() ?
                    this.vid().extend("pane").extend(this.activePane.id() + "") :
                    f(strip);
            final Pane pane = this.getAllPanes()
                    .stream()
                    .filter(p -> p.id() == this.activePane.id())
                    .findFirst()
                    .orElse(null);
            if (pane == null) {
                LOG.error("unable to find active pane: %d", this.activePane.id());
            } else {
                pane.unsubscribe();
                pane.vid(subURI);
                pane.subscribe();
            }
            return;
        }
        /// /////////////////////////////////////////////////////
        CommonUtil.splitOnNonQuotedSequence(this.prefix + line + this.postfix, ';', false).forEach(l -> {
            try {
                final Obj parseResult = ObjmtronSerializer.parse(l);
                final Level startLevel = this.status.getState();
                if (null != parseResult && !parseResult.isNoObj()) {
                    final Obj resolvedResult = parseResult.isCall() ? Call.Helper.resolveInspection(parseResult.asCall(), unresolved -> {
                        if (TypeCheck.code_resolve.enabled()) {
                            throw MTronException.of("unable to fully resolve code. execution will require dynamic inst resolution for:\n\t%s", unresolved.stream().map(Obj::tid).toList());
                        } else {
                            this.status.setState(Level.WARN);
                            LOG.warn("unable to fully resolve code. execution will require dynamic inst resolution for:\n\t%s", unresolved.stream().map(Obj::tid).toList());
                        }
                    }) : parseResult;
                    final Machine mach = SwarmMachine.of(resolvedResult.isCall() ? resolvedResult.as() : start_(resolvedResult)).onHalt(this::printResult);
                    // Track machine in both places for interruption
                    this.machine = mach;
                    if (this.activePane != null) {
                        this.activePane.machine(mach);
                    }

                    final Obj computeResult = mach.apply();
                    computeResult.stream().forEach(this::printResult);
                    this.status.setState(startLevel);
                }
            } catch (final Exception e) {
                this.printResult(fail(e));
            } finally {
                if (this.activePane != null) {
                    this.activePane.clearMachine();
                }
            }
        });
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
                final String line = this.reader.readLine(this.prompt()).trim();
                this.inReadLine = false;
                // Drain any agent output that was deferred while the user was typing.
                if (this.pendingPaneFlush) {
                    this.pendingPaneFlush = false;
                    if (this.splitMode) this.renderPanes();
                }
                // An empty line can result from pane-switch (Ctrl+W clears the buffer and
                // calls accept-line to break out of readLine so the next iteration can
                // start fresh in the new pane).  Skip evaluation entirely.
                if (line.isEmpty()) continue;
                if (line.equals(":connect")) {
                    Router.writeToSpace("abc", block_(MInst.instLambda((lhs, inst) -> {
                        LOG.info("HERE %s", lhs);
                        reader.getBuffer().write(lhs.asLst().at(1).strValue());
                        return lhs;
                    })));
                } else if (line.equals(":header"))
                    this.outputHeader(line.substring(7).trim());
                else if (line.equals(":quit"))
                    break;
                else if (line.equals(":clear")) {
                    Graphitty.out(terminal.output(), "{{XX}}");
                    this.status.refresh();
                } else if (line.equals(":help")) {
                    final String helpText = new Panel("{{c}}metatron console help{{X}}", new Table(
                            List.of("name", "short", "description"))
                            /// ///////////////////////////////////////////////////////////////////////////////////////
                            .addRow(List.of("{{[g]&w}}mtron", "{{[g]&w}}", "{{[g]&w}}"))
                            .addRow(List.of("explain", "<tab>", "a tabular view of the current code"))
                            .addRow(List.of("type check", ":check [-| ] [ |type_ctor|obj_write|inst_rng|inst_dom|code_resolve]", "show or enable/disable type checking stages"))
                            .addRow(List.of("cycle type check", "<ctrl>+t", "cycle type check activations"))
                            /// ///////////////////////////////////////////////////////////////////////////////////////
                            .addRow(List.of("{{[g]&w}}console", "{{[g]&w}}", "{{[g]&w}}"))
                            .addRow(List.of("quit", ":quit | <ctrl>+q", "exit the console"))
                            .addRow(List.of("clear", ":clear", "clear the console"))
                            .addRow(List.of("header", ":header [ |<name>]", "print random or named metatron header"))
                            .addRow(List.of("log", ":log [ |trace|debug|info|warn|error] [ |int]", "show or set log level (and target a output to a pane)"))
                            .addRow(List.of("word jump", "<shift>+<left/right>", "jump to start/end of a word"))
                            .addRow(List.of("word delete", "<ctrl>+<backspace>", "delete previous word"))
                            .addRow(List.of("prefix", ":prefix \"<text>\"", "prefix input with text"))
                            .addRow(List.of("postfix", ":postfix \"<text>\"", "postfix input with text"))
                            .addRow(List.of("back erase", "<alt>+k <char>", "erase buffer back to first occurrence of char"))
                            .addRow(List.of("format buffer", "<ctrl>+f", "pretty-print current buffer (legal syntax only)"))
                            /// ///////////////////////////////////////////////////////////////////////////////////////
                            .addRow(List.of("{{[g]&w}}panes", "{{[g]&w}}", "{{[g]&w}}"))
                            .addRow(List.of("split horizontal", ":split v | <ctrl>+<up>", "split current pane horizontally"))
                            .addRow(List.of("split vertical", ":split h | <ctrl>+<right>", "split current pane vertically"))
                            .addRow(List.of("focus", ":focus <id>", "focus pane by id"))
                            .addRow(List.of("panes", ":panes", "list all panes"))
                            .addRow(List.of("close", ":close", "close active pane"))
                            .addRow(List.of("next pane", "<ctrl>+w", "cycle to next pane"))
                            .addRow(List.of("prev pane", "<alt>+w", "cycle to previous pane"))
                            .addRow(List.of("shrink pane", "<alt>+<", "make active pane smaller"))
                            .addRow(List.of("grow pane", "<alt>+>", "make active pane larger"))
                            .style().headerDivider("{{[b]&w}}|").margin(0, 0, 0, 0).apply().format()).style().margin(0, 0, 0, 0).border(Border.simple.foreground("{{b}}")).apply().format();
                    if (this.splitMode && this.activePane != null) {
                        this.activePane.appendOutput(helpText);
                    } else {
                        Graphitty.out(terminal.output(), helpText);
                    }
                } else if (line.startsWith(":log")) {
                    if (!line.substring(4).trim().isBlank()) {
                        //LOG.none("updating log level: %s\n",  line.substring(4).trim());
                        final String[] args = line.substring(4).trim().split(" ");
                        LogObj.setSLF4J(args[0]);
                        if (args.length > 1)
                            GraphittyLogger.setDefaultTargetPane(Integer.parseInt(args[1]));
                    }
                    LOG.none("log level: %s [target pane: %s]\n", LogObj.getSLF4J().toString().toLowerCase(), GraphittyLogger.getDefaultTargetPane());
                } else if (line.startsWith(":check")) {
                    Arrays.stream(line.substring(6).trim().split(" ")).forEach(s -> {
                        if (!s.trim().isEmpty()) {
                            if (s.startsWith("-"))
                                TypeCheck.disable(TypeCheck.valueOf(s.substring(1).toLowerCase()));
                            else
                                TypeCheck.enable(TypeCheck.valueOf(s.toLowerCase()));
                        }
                    });
                    LOG.info("type check stages {{%s}}%s{{X}}", TypeCheck.colorLevel(), TypeCheck.getEnabled());
                } else if (line.startsWith(":top")) {
                    TTop.ttop(terminal, new PrintStream(terminal.output()), System.err, new String[0]);
                } else if (line.startsWith(":less")) {
                    Commands.less(terminal, terminal.input(), new PrintStream(terminal.output()), System.err, Paths.get(""), new String[0]);
                } else if (line.startsWith(":subs")) {
                    final SubsWidget selector = new SubsWidget(this);
                    selector.run();
                    selector.close();
                } else if (line.startsWith(":justify")) {
                    final boolean leftJustify = line.substring(8).trim().equalsIgnoreCase("left");
                    ((Highlighter) this.reader.getHighlighter()).justify(leftJustify);
                    LOG.info("%s justifying nested polys", leftJustify ? "{{y}}left{{X}}" : "{{y}}right{{X}}");
                } else if (line.startsWith(":state")) {
                    this.status.setState(Level.valueOf(line.substring(6).trim().toUpperCase()));
                } else if (line.startsWith(":lang")) {
                    final String langName = line.substring(5).trim().toLowerCase();
                    try {
                        final Language newLang = Language.valueOf(langName.toUpperCase());
                        this.setLanguage(newLang);
                    } catch (IllegalArgumentException e) {
                        LOG.error("unknown language: {{r}}%s{{X}}. Available: mtron, gremlin, sql", langName);
                    }
                } else if (line.startsWith(":prefix")) {
                    this.prefix = line.substring(7).trim();
                    if (this.prefix.startsWith("\""))
                        this.prefix = this.prefix.substring(1);
                    if (this.prefix.endsWith("\""))
                        this.prefix = this.prefix.substring(0, this.prefix.length() - 1);
                } else if (line.startsWith(":postfix")) {
                    this.postfix = line.substring(8).trim();
                    if (this.postfix.startsWith("\""))
                        this.postfix = this.postfix.substring(1);
                    if (this.postfix.endsWith("\""))
                        this.postfix = this.postfix.substring(0, this.postfix.length() - 1);
                }
                // ========== Split Pane Commands ==========
                else if (line.startsWith(":split")) {
                    final String arg = line.substring(6).trim();
                    try {
                        final SplitLayout direction = arg.isEmpty()
                                ? SplitLayout.VERTICAL  // Default to vertical
                                : SplitLayout.parse(arg);
                        this.split(direction);
                        this.renderPanes();
                    } catch (IllegalArgumentException e) {
                        LOG.error(e.getMessage());
                    }
                } else if (line.equals(":unsplit") || line.equals(":close")) {
                    this.closeActivePane();
                    if (this.splitMode) {
                        this.renderPanes();
                    } else {
                        Graphitty.out(terminal.output(), "{{XX}}"); // Clear screen
                    }
                } else if (line.startsWith(":focus")) {
                    final String arg = line.substring(6).trim();
                    if (arg.isEmpty()) {
                        // List all panes
                        LOG.info("panes: %s, active: {{y}}%d{{X}}",
                                getAllPanes().stream().map(p -> String.valueOf(p.id())).toList(),
                                this.activePane.id());
                    } else {
                        try {
                            final int paneId = Integer.parseInt(arg);
                            this.focusPane(paneId);
                            if (this.splitMode) this.renderPanes();
                        } catch (NumberFormatException e) {
                            LOG.error("invalid pane id: {{r}}%s{{X}}", arg);
                        }
                    }
                } else if (line.equals(":panes")) {
                    // Show all panes
                    final List<Pane> panes = getAllPanes();
                    LOG.info("{{y}}%d{{X}} pane(s):", panes.size());
                    for (final Pane p : panes) {
                        final String active = (p == this.activePane) ? " {{g}}[active]{{X}}" : "";
                        LOG.info("  [{{y}}%d{{X}}] %s, %d lines%s",
                                p.id(), p.language().name, p.outputBuffer().size(), active);
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
                    this.machine.interrupt();
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
                final String stackTrace = this.reader.readLine(Highlighter.format("{{y}}display stack trace {{g}}[y/N]{{y}}?{{X}} "));
                if (stackTrace.trim().equalsIgnoreCase("y")) {
                    e.printStackTrace();
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

    protected void outputHeader(final String name) {
        try {
            terminal.writer().print(CommonUtil.getHeader(HEADER_FILE, name, true));
            terminal.writer().flush();
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

    class CustomWidgets extends Widgets {
        private CustomWidgets(final LineReader reader) {
            super(reader);
            getKeyMap().bind((Widget) () -> {
                final String current = this.reader.getBuffer().toString();
                try {
                    final String formatted = ObjmtronSerializer.parse(current).toString();
                    // Replace the buffer and let JLine's own display engine erase the old
                    // content and draw the new.  The old manual erase loop counted actual
                    // '\n' characters, which broke when COLUMNS < terminal width caused
                    // the input to visually wrap across more rows than the loop knew about.
                    this.reader.getBuffer().clear();
                    this.reader.getBuffer().write(formatted);
                    // Returning true signals JLine to redraw the line; no manual
                    // erase needed since JLine tracks exact visual row count.
                } catch (final Exception e) {
                    // do nothing (most likely unparsable buffer)
                }
                return true;
            }, ctrl('f'));
            /// CYCLE TO NEXT PANE (Ctrl+W)
            getKeyMap().bind((Widget)
                    () -> {
                        if (Console.this.splitMode) {
                            Console.this.nextPane();
                            // renderPanes() would move the cursor via absolute ANSI escapes,
                            // but JLine's internal Display.cursorPos stays stale.  When JLine
                            // redraws after the widget it moves RELATIVE to that stale position
                            // and draws in the wrong pane.  The only reliable fix is to
                            // terminate the current readLine() via accept-line; the REPL loop
                            // then calls prepareForInput() → renderPanes() and starts a fresh
                            // readLine() correctly anchored in the new active pane.
                            this.reader.getBuffer().clear(); // don't accidentally submit partial input
                            callWidget("accept-line");
                        }
                        return true;
                    }, ctrl('w'));
            /// CYCLE TO PREVIOUS PANE (Ctrl+Shift+W = Alt+W in some terminals)
            getKeyMap().bind((Widget)
                    () -> {
                        if (Console.this.splitMode) {
                            Console.this.prevPane();
                            this.reader.getBuffer().clear();
                            callWidget("accept-line");
                        } else {
                            // Original behavior when not in split mode
                            reader.getBuffer().up();
                            reader.getBuffer().write("\n");
                        }
                        return true;
                    }, alt('w'));
            /// RESIZE PANE SMALLER (Ctrl+Shift+< = Alt+< in most terminals)
            getKeyMap().bind((Widget)
                    () -> {
                        if (Console.this.splitMode) {
                            Console.this.resizeActivePane(-0.05f);
                            Console.this.renderPanes(false); // user-initiated — force render
                            Console.this.redrawBuffer();
                        }
                        return true;
                    }, "\033<");  // Alt+<
            /// RESIZE PANE LARGER (Ctrl+Shift+> = Alt+> in most terminals)
            getKeyMap().bind((Widget)
                    () -> {
                        if (Console.this.splitMode) {
                            Console.this.resizeActivePane(0.05f);
                            Console.this.renderPanes(false); // user-initiated — force render
                            Console.this.redrawBuffer();
                        }
                        return true;
                    }, "\033>");  // Alt+>
            /// SPLIT PANE VERTICALLY OR HORIZONTALLY
            getKeyMap().bind((Widget)
                    () -> {
                        Console.this.split(SplitLayout.VERTICAL);
                        Console.this.renderPanes(false); // user-initiated — force render
                        Console.this.redrawBuffer();
                        return true;
                    }, "\033[1;5C");  // Ctrl+<right>
            getKeyMap().bind((Widget)
                    () -> {
                        Console.this.split(SplitLayout.HORIZONTAL);
                        Console.this.renderPanes(false); // user-initiated — force render
                        Console.this.redrawBuffer();
                        return true;
                    }, "\033[1;5A");  // Ctrl+<up>
            /// TURN ON/OFF TYPE CHECKING
            getKeyMap().bind((Widget)
                    () -> {
                        if (TypeCheck.level() == 0)
                            TypeCheck.enable(TypeCheck.values());
                        else
                            TypeCheck.disable(TypeCheck.getEnabled().stream().toList().getFirst());
                        return true;
                    }, ctrl('t'));
            /// FAST NAVIGATION: JUMP BY WORD (Shift+Left/Right)
            getKeyMap().bind((Widget)
                    () -> {
                        callWidget("backward-word");
                        return true;
                    }, "\033[1;2D");  // Shift+<left>
            getKeyMap().bind((Widget)
                    () -> {
                        callWidget("forward-word");
                        return true;
                    }, "\033[1;2C");  // Shift+<right>
            /// FAST DELETION: DELETE WORD BACKWARDS (Ctrl+Backspace)
            // CSI u / kitty keyboard protocol format (kitty, ghostty, iTerm2, xterm-modifyOtherKeys)
            getKeyMap().bind((Widget)
                    () -> {
                        callWidget("backward-kill-word");
                        return true;
                    }, "\033[127;5u");
            getKeyMap().bind((Widget)
                    () -> {
                        callWidget("backward-kill-word");
                        return true;
                    }, "\033[8;5u");
            // Fallback: terminals without extended key reporting often send plain BS (0x08)
            getKeyMap().bind((Widget)
                    () -> {
                        callWidget("backward-kill-word");
                        return true;
                    }, "\b");
            /// CREATE NEW LINE BELOW CURRENT LOCATION
            getKeyMap().bind((Widget)
                    () -> {
                        reader.getBuffer().write("\n");
                        return true;
                    }, alt('s'));
            /// PUT CURRENT BUFFER IN FULL SCREEN EDITOR
            getKeyMap().bind((Widget)
                    () -> Editor.of(Console.this, reader.getBuffer().toString()), ctrl('y'));
            /// QUIT METATRON (CLOSE EVERYTHING)
            getKeyMap().bind((Widget)
                    () -> {
                        Console.this.close();
                        System.exit(0);
                        return true;
                    }, ctrl('q'));
            /// EXPLAIN BUFFER CODE (IF IS CODE) OR DOT-COMPLETION FOR INSTRUCTIONS
            getKeyMap().bind((Widget) () -> {
                try {
                    final String bufferText = this.reader.getBuffer().toString();
                    // Check if buffer ends with '.' for instruction completion
                    if (bufferText.trim().endsWith(".")) {
                        final Obj parsed = ObjmtronSerializer.parse(bufferText.trim().substring(0, bufferText.trim().length() - 1));
                        if (parsed.isCode()) {
                            terminal.writer().write("\n");
                            final InstSelector selector = new InstSelector(parsed.resolve(noobj()).as(), bufferText);
                            if (selector.hasInstructions()) {
                                // Constrain the selector to the active pane when in split mode
                                if (Console.this.splitMode && Console.this.activePane != null) {
                                    final int[] pos = Console.this.calculatePanePosition(Console.this.activePane);
                                    if (pos != null) selector.setPaneBounds(pos[0], pos[1], pos[2], pos[3]);
                                }
                                Utilities.runCursorLessWidget(selector, true);
                                // Restore the full pane layout after the widget closes
                                if (Console.this.splitMode) {
                                    Console.this.renderPanes(false); // restore after fullscreen widget
                                }
                            }
                        }
                    } else {
                        // Original behavior: show explain widget for code
                        final Obj code = ObjmtronSerializer.parse(bufferText);
                        if (code.isCode()) {
                            terminal.writer().write("\n");
                            final Explain explain = new Explain(code.as());
                            // Constrain the widget to the active pane when in split mode
                            if (Console.this.splitMode && Console.this.activePane != null) {
                                final int[] pos = Console.this.calculatePanePosition(Console.this.activePane);
                                if (pos != null) explain.setPaneBounds(pos[0], pos[1], pos[2], pos[3]);
                            }
                            Utilities.runCursorLessWidget(explain, true);
                            // Restore the full pane layout after the widget closes
                            if (Console.this.splitMode) {
                                Console.this.renderPanes(false); // restore after fullscreen widget
                            }
                            redrawBuffer();
                        }
                    }
                } catch (final Exception e) {
                    LOG.error(e);
                }
                return true;
            }, key(Console.terminal, InfoCmp.Capability.tab));
            /// ERASE BUFFER BACK TO FIRST OCCURRENCE OF CHARACTER (Alt+K then <char>)
            // Bind all printable characters with Alt+K prefix
            for (char c = 32; c <= 126; c++) {
                final char targetChar = c;
                // Alt+K followed by character: ESC + 'k' + character
                final String altKSequence = "\033k" + c;
                getKeyMap().bind((Widget) () -> {
                    eraseBackToChar(targetChar);
                    return true;
                }, altKSequence);
            }
        }

        /**
         * Erase the buffer back to (and including) the first occurrence of the target character.
         * Searches from the current cursor position backwards.
         */
        private void eraseBackToChar(char targetChar) {
            final Buffer buffer = reader.getBuffer();
            final String currentText = buffer.toString();
            final int cursorPos = buffer.cursor();

            if (cursorPos == 0) {
                return; // Nothing to erase
            }

            // Search backwards from cursor position for the target character
            int targetPos = -1;
            for (int i = cursorPos - 1; i >= 0; i--) {
                if (currentText.charAt(i) == targetChar) {
                    targetPos = i;
                    break;
                }
            }

            if (targetPos == -1) {
                // Character not found, do nothing
                return;
            }

            // Delete from targetPos to cursor position
            buffer.cursor(targetPos);
            buffer.delete(cursorPos - targetPos);
        }
    }
}