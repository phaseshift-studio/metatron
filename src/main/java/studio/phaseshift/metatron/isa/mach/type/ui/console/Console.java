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
import java.util.function.Supplier;

import static org.jline.keymap.KeyMap.*;
import static studio.phaseshift.metatron.BootLoader.BOOTING;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.INST_TID;
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

    public static final Type CONSOLE_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(CONSOLE_TID)
            .isaPredicate(rec())
            .constructor(instC(INST_TID.dom(ALL.maybe()).rng(CONSOLE_TID), lst(T(REC_TID)), (lhs, inst) -> {
                final Console console = new Console(inst.arg(0).as(), inst.arg(0).vid());
                BootLoader.getExecutor().submit(console);
                LOCAL_INSTANCE = console;
                return console;
            })).create();

    public Console(final Rec options, final fURI vid) {
        super(null, options.jvm(), CONSOLE_TID, vid);
        this.jvm = this;
        try {
            final DefaultParser parser = new DefaultParser()
                    .quoteChars(new char[]{'\'', '"'})
                    .lineCommentDelims(new String[]{"[--", "--]"})
                    .blockCommentDelims(new DefaultParser.BlockCommentDelims("[===", "===]"))
                    .eofOnUnclosedQuote(true)
                    .eofOnUnclosedBracket(DefaultParser.Bracket.CURLY, DefaultParser.Bracket.ROUND, DefaultParser.Bracket.SQUARE);
            Console.terminal = TerminalBuilder.builder().signalHandler(signal -> {
                if (signal == Terminal.Signal.INT) {
                    if (null != this.machine)
                        this.machine.interrupt();
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
        return Graphitty.string("{{m}}mtron{{g}}> ");
    }

    public StatusLine getStatus() {
        return this.status;
    }

    public ConfigurationPath getConfigurations() {
        return Console.configurations;
    }

    protected void printResult(final Obj result) {
        result.stream().forEach(o -> {
            this.write("{{-X-}}{{m}}=={{g}}>{{X}}");
            this.write(o);
            this.write("\n");
        });
    }

    public void redrawBuffer() {
        Graphitty.out(this.terminal.output(), "\n");
        Graphitty.out(this.terminal.output(), this.prompt());
        Graphitty.out(this.terminal.output(), Highlighter.format(this.reader.getBuffer().toString()));
    }

    public void run() {
        while (BOOTING) {
            CommonUtil.sleepThread(10);
        }
        CommonUtil.sleepThread(50);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                final String line = this.reader.readLine(this.prompt()).trim();
                if (line.equals(":header"))
                    this.outputHeader();
                else if (line.equals(":quit"))
                    break;
                else if (line.equals(":clear")) {
                    Graphitty.out(terminal.output(), "{{XX}}");
                    this.status.refresh();
                } else if (line.equals(":help")) {
                    new Panel("{{c}}help menu{{X}}", new Table(
                            List.of("name", "short", "description"))
                            .addRow(List.of("space walk", "<tab>", "explore spaces"))
                            .addRow(List.of("introspect", "<space><tab>", "analyze machine"))
                            .addRow(List.of("header", "random header", "random header")).style().headerDivider("{{[b]}} ").apply().format()).style().border(Border.simple.foreground("{{b}}")).apply().run();
                } else if (line.startsWith(":log")) {
                    LogObj.setSLF4J(line.substring(4));
                } else if (line.startsWith(":check")) {
                    Arrays.stream(line.substring(6).trim().split(" ")).forEach(s -> {
                        if(!s.trim().isEmpty()) {
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
                } else {
                    this.status.startTimer();
                    Arrays.stream(line.split(";")).forEach(l -> {
                        try {
                            final Obj parseResult = mParser.parse(l);
                            if (null != parseResult && !parseResult.isNoObj()) {
                                this.machine = SwarmMachine.of(parseResult.isCall() ? parseResult.as() : start_(parseResult)).onHalt(this::printResult);
                                final Obj computeResult = this.machine.apply();
                                computeResult.stream().forEach(this::printResult);
                            }
                        } catch (final Exception e) {
                            this.printResult(fail(e));
                        }
                    });
                    this.machine = null;
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
            /// CREATE NEW LINE ABOVE CURRENT LOCATION
            getKeyMap().bind((Widget)
                    () -> {
                        reader.getBuffer().up();
                        reader.getBuffer().write("\n");
                        return true;
                    }, alt('w'));
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