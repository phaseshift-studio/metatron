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
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.reflect.JRec;
import studio.phaseshift.metatron.isa.m.type.reflect.ObjFieldReflection;
import studio.phaseshift.metatron.isa.mach.type.LogObj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.machine.SwarmMachine;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.*;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.jline.keymap.KeyMap.*;
import static studio.phaseshift.metatron.BootLoader.BOOTING;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.start_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;

public class Console extends JRec implements Closeable, Runnable {

    public static final fURI CONSOLE_TID = MACH_ISA_TID.extend("console");
    @ObjFieldReflection(tid = "/m/uri")
    public static final String METATRON_VERSION = "0.1-alpha";
    @ObjFieldReflection(tid = "/m/uri")
    public static final String MTRON = "mtron";
    // @ObjFieldReflection(tid = FILE_TID_STRING)
    public static final String MTRON_NANORC = "mtron.nanorc";
    //@ObjFieldReflection(tid = FILE_TID_STRING)
    public static String HEADER_FILE = "./conf/ansi_headers.txt";
    //@ObjFieldReflection(tid = FILE_TID_STRING)
    public static Path HISTORY_FILE = Paths.get(".metatron.history");

    private final GraphittyLogger LOG = Graphitty.log(this);
    public static String HEADER_SEPARATOR = "####################";
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
                LOCAL_INSTANCE = console;
                return console;
            })).create();

    public Console(final Rec options, final fURI vid) {
        super(null, options.jvm(), CONSOLE_TID, vid);
        this.jvm = this;
        try {
            // Initialize pane system with a single pane
            this.activePane = new Pane();
            this.activePane.setConsole(this);
            this.paneRoot = this.activePane;

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
            this.outputHeader();
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
        return Graphitty.string(this.currentLanguage.prompt);
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
        } else {
            // Re-enable AUTO_FRESH_LINE in normal mode
            this.reader.setOpt(LineReader.Option.AUTO_FRESH_LINE);
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

    public List<Pane> getAllPanes() {
        return this.paneRoot.getAllPanes();
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

        // Find next pane to focus
        final int currentIndex = allPanes.indexOf(toClose);
        final Pane nextPane = allPanes.get((currentIndex + 1) % allPanes.size());

        // Remove from tree
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

        final int currentIndex = allPanes.indexOf(this.activePane);
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

        final int currentIndex = allPanes.indexOf(this.activePane);
        this.activePane = allPanes.get((currentIndex - 1 + allPanes.size()) % allPanes.size());
        LOG.info("focused pane {{y}}%d{{X}}", this.activePane.id());
        this.requestRedraw();
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
     * Render all panes to the terminal. Called when in split mode.
     */
    public void renderPanes() {
        if (!this.splitMode) return;

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
        CommonUtil.splitOnNonQuotedSequence(line, ';', false).forEach(l -> {
            try {
                final Obj parseResult = mParser.parse(l);
                if (null != parseResult && !parseResult.isNoObj()) {
                    final Machine mach = SwarmMachine.of(parseResult.isCall() ? parseResult.as() : start_(parseResult)).onHalt(this::printResult);

                    // Track machine in both places for interruption
                    this.machine = mach;
                    if (this.activePane != null) {
                        this.activePane.machine(mach);
                    }

                    final Obj computeResult = mach.apply();
                    computeResult.stream().forEach(this::printResult);
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
                final String line = this.reader.readLine(this.prompt()).trim();
                if (line.equals(":header"))
                    this.outputHeader();
                else if (line.equals(":quit"))
                    break;
                else if (line.equals(":clear")) {
                    Graphitty.out(terminal.output(), "{{XX}}");
                    this.status.refresh();
                } else if (line.equals(":help")) {
                    Graphitty.out(terminal.output(), new Panel("{{c}}help menu{{X}}", new Table(
                            List.of("name", "short", "description"))
                            .addRow(List.of("explain", "<tab>", "a tabular view of the current code"))
                            .addRow(List.of("header", ":header", "print random metatron header"))
                            .addRow(List.of("{{[g]&w}}panes", "{{[g]&w}}", "{{[g]&w}}"))
                            .addRow(List.of("split", ":split [v|h]", "split pane vertical/horizontal"))
                            .addRow(List.of("focus", ":focus [id]", "focus pane by id"))
                            .addRow(List.of("panes", ":panes", "list all panes"))
                            .addRow(List.of("close", ":close", "close active pane"))
                            .addRow(List.of("next pane", "ctrl+w", "cycle to next pane"))
                            .addRow(List.of("prev pane", "alt+w", "cycle to previous pane"))
                            .addRow(List.of("shrink pane", "alt+<", "make active pane smaller"))
                            .addRow(List.of("grow pane", "alt+>", "make active pane larger")).style().headerDivider("{{[b]&w}}|").margin(0,0,0,0).apply().format()).style().margin(0,0,0,0).border(Border.simple.foreground("{{b}}")).apply().format());
                } else if (line.startsWith(":log")) {
                    LogObj.setSLF4J(line.substring(4));
                } else if (line.startsWith(":check")) {
                    Arrays.stream(line.substring(6).trim().split(" ")).forEach(s -> {
                        if (!s.trim().isEmpty()) {
                            if (s.startsWith("-"))
                                TypeCheck.disable(TypeCheck.valueOf(s.substring(1).toUpperCase()));
                            else
                                TypeCheck.enable(TypeCheck.valueOf(s.toUpperCase()));
                        }
                    });
                    LOG.info("type checking %s", TypeCheck.getEnabled());
                } else if (line.startsWith(":card")) {
                    final List<studio.phaseshift.metatron.isa.mach.type.ui.Widget<?>> widgets = new ArrayList<>();
                    Router.global().spaces().elements().map(Rel::second).forEach(s ->
                            widgets.add(new Card(s.vidOrTid().name() + ": " + s.tid().name(), s.asRec().at("native", noobj(), IMMUTABLE).toString())
                                    .style()
                                    .border(Border.simple.foreground("{{b}}"))
                                    .background("{{[g]}}")
                                    .foreground("{{y}}")
                                    .margin(1, 1)
                                    .apply()));
                    new Grid(widgets, 3).run();
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

    protected void outputHeader() {
        try {
            final Map<String, String> headers = new HashMap<>();
            StringBuilder current = new StringBuilder();
            final BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(HEADER_FILE)));
            String headerTitle = null;
            while (input.ready()) {
                final String line = input.readLine().stripTrailing();
                if (line.startsWith(HEADER_SEPARATOR) && line.endsWith(HEADER_SEPARATOR)) {
                    if (null != headerTitle && !current.isEmpty()) {
                        headers.put(headerTitle, current.toString());
                    }
                    current = new StringBuilder();
                    headerTitle = line.replace(HEADER_SEPARATOR, "").trim();
                } else {
                    current.append(line).append("\n");
                }
            }
            if (!current.isEmpty())
                headers.put(headerTitle, current.toString());
            final String randomHeaderTitle = new ArrayList<>(headers.keySet()).get(new Random().nextInt(headers.size()));
            final String randomHeader = headers.get(randomHeaderTitle);
            if (null == randomHeader)
                throw new IllegalArgumentException("<unknown header: " + randomHeaderTitle + ">");
            terminal.writer().print(Graphitty.string(randomHeader));
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
            /// CYCLE TO NEXT PANE (Ctrl+W)
            getKeyMap().bind((Widget)
                    () -> {
                        if (Console.this.splitMode) {
                            Console.this.nextPane();
                            Console.this.renderPanes();
                            // Force JLine to redraw prompt and buffer at new cursor position
                            Console.this.redrawBuffer();
                        }
                        return true;
                    }, ctrl('w'));
            /// CYCLE TO PREVIOUS PANE (Ctrl+Shift+W = Alt+W in some terminals)
            getKeyMap().bind((Widget)
                    () -> {
                        if (Console.this.splitMode) {
                            Console.this.prevPane();
                            Console.this.renderPanes();
                            // Force JLine to redraw prompt and buffer at new cursor position
                            Console.this.redrawBuffer();
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
                            Console.this.renderPanes();
                            Console.this.redrawBuffer();
                        }
                        return true;
                    }, "\033<");  // Alt+<
            /// RESIZE PANE LARGER (Ctrl+Shift+> = Alt+> in most terminals)
            getKeyMap().bind((Widget)
                    () -> {
                        if (Console.this.splitMode) {
                            Console.this.resizeActivePane(0.05f);
                            Console.this.renderPanes();
                            Console.this.redrawBuffer();
                        }
                        return true;
                    }, "\033>");  // Alt+>
            /// SPLIT PANE VERTICALLY OR HORIZONTALLY
            getKeyMap().bind((Widget)
                    () -> {
                      Console.this.split(SplitLayout.VERTICAL);
                        Console.this.renderPanes();
                        Console.this.redrawBuffer();
                        return true;
                    }, "\033[1;5C");  // Ctrl+<right>
            getKeyMap().bind((Widget)
                    () -> {
                        Console.this.split(SplitLayout.HORIZONTAL);
                        Console.this.renderPanes();
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
            /// EXPLAIN BUFFER CODE (IF IS CODE)
            getKeyMap().bind((Widget) () -> {
                try {
                    final Obj code = mParser.parse(this.reader.getBuffer().toString());
                    if (code.isCode()) {
                        terminal.writer().write("\n");
                        final Explain explain = new Explain(code.as());
                        Utilities.runCursorLessWidget(explain, true);
                        redrawBuffer();
                    }
                } catch (final Exception e) {
                    // do nothing
                }
                return true;
            }, key(Console.terminal, InfoCmp.Capability.tab));
        }
    }
}