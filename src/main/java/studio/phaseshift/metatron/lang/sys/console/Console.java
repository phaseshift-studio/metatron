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

package studio.phaseshift.metatron.lang.sys.console;

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
import org.jline.widget.Widgets;
import org.slf4j.event.Level;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObj;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObjs;
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;
import studio.phaseshift.metatron.lang.core.m.type.reflect.ReflectRec;
import studio.phaseshift.metatron.lang.core.mach.type.impl.MMachine;
import studio.phaseshift.metatron.lang.sys.fs.fileSpace;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.util.LogObj;
import studio.phaseshift.metatron.ui.Border;
import studio.phaseshift.metatron.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.ui.widget.Panel;
import studio.phaseshift.metatron.ui.widget.Table;
import studio.phaseshift.metatron.util.Common;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Threadable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;

import static org.jline.keymap.KeyMap.alt;
import static org.jline.keymap.KeyMap.ctrl;
import static studio.phaseshift.metatron.BootLoader.BOOTING;
import static studio.phaseshift.metatron.furi.fURI.*;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.INST_TID;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.REC_TID;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.sys.sysInstSet.SYS_TYPE_TID;

public class Console extends MObj implements Threadable, Runnable, ReflectRec {

    public static final fURI CONSOLE_TID = SYS_TYPE_TID.extend("console");
    public static final String METATRON_VERSION = "0.1-alpha";
    public static final String MTRON = "mtron";
    public static final String MTRON_NANORC = "mtron.nanorc";
    private final GraphittyLogger LOG = Graphitty.log(this);
    public static String HEADER_FILE = "./conf/ansi_headers.txt";
    public static String HEADER_SEPARATOR = "####################";
    public static Path HISTORY_FILE = Paths.get(".metatron.history");
    private final Terminal terminal;
    private final LineReader reader;
    private final StatusLine status;
    private final static ConfigurationPath configurations = new ConfigurationPath(
            Paths.get("conf"),                                     // application-wide settings
            Paths.get(System.getProperty("user.home"), ".metatron") // user-specific settings
    );
    private final Highlighter highlighter;
    private final Thread thread;
    public static Console LOCAL_INSTANCE = null;

    public static final Type CONSOLE_TYPE = T(CONSOLE_TID, isa_(rec()), instC(INST_TID.dom(ALL.maybe()).rng(CONSOLE_TID), lst(T(REC_TID)), (lhs, inst) -> {
        final Console console = new Console(inst.arg(0).as());
        console.start();
        LOCAL_INSTANCE = console;
        Router.global().write(f("/sys/obj/console"),console);
        return console;
    }));

    @Override
    public Rec self(Object jvm, fURI tid, fURI vid) {
        return this;
    }

    @Override
    public Rec clone(Object jvm, fURI tid, fURI vid) {
        return this;
    }

    @Override
    public fURI tid() {
        return CONSOLE_TID;
    }
    
    

    @Override
    public Map<Obj,Obj> jvm() {
        return Map.of();
    }
    
    public Console(final Rec options) {
        super(options.jvm(), CONSOLE_TID, f("/sys/obj/console"));
        try {
            final DefaultParser parser = new DefaultParser()
                    .quoteChars(new char[]{'\'', '"'})
                    .lineCommentDelims(new String[]{"[--", "--]"})
                    .blockCommentDelims(new DefaultParser.BlockCommentDelims("[===", "===]"))
                    .eofOnUnclosedQuote(true)
                    .eofOnUnclosedBracket(DefaultParser.Bracket.CURLY, DefaultParser.Bracket.ROUND, DefaultParser.Bracket.SQUARE);
            this.terminal = TerminalBuilder.builder().encoding(StandardCharsets.UTF_8).system(true).build();
            this.outputHeader();
            final Supplier<Path> currentDir = () -> Paths.get("");
            final Builtins builtins = new Builtins(currentDir, Console.configurations, null);
            SystemRegistry systemRegistry = new SystemRegistryImpl(parser, terminal, currentDir, Console.configurations);
            systemRegistry.setCommandRegistries(builtins);
            this.highlighter = Highlighter.single();
            this.reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .appName("metatron")
                    .history(new DefaultHistory())
                    .highlighter(this.highlighter) // TODO: command/args/lang
                    .parser(parser)
                    .variable(LineReader.HISTORY_FILE, HISTORY_FILE)
                    .option(LineReader.Option.AUTO_FRESH_LINE, true)
                    .option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .variable(LineReader.SECONDARY_PROMPT_PATTERN, Graphitty.string("{{-X&v1&^1&m}}     {{g}}| {{X}}"))
                    .variable(LineReader.INDENTATION, 0)
                    .completer(new MCompleter(this))
                    .build();
            new CustomWidgets(this.reader);
            this.status = new StatusLine(this, "{{b}}loading...{{X}}");
            this.thread = new Thread(this);
            /// ///////////////////////////////////////////////////////
            this.put(uri("history"), fileSpace.makeFile(Path.of(HISTORY_FILE.toString())), MUTABLE);
            this.put(uri("header"), fileSpace.makeFile(Path.of(HEADER_FILE)), MUTABLE);
            this.put(uri("output"), instC(INST_TID.dom(ALL).rng(NOOBJ), lst(T(ALL)), (lhs, inst) -> {
                this.reader.printAbove(Highlighter.format(inst.arg(0)));
                return noobj();
            }));
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    public static Console of(final Rec options) {
        return new Console(options);
    }

    @Override
    public void close() {
        try {
            this.status.close();
            this.reader.getBuffer().clear();
            this.terminal.close();
            Threadable.super.close();
        } catch (final IOException e) {
            LOG.error(e);
        }
    }

    public void write(final Object object) {
        this.terminal.writer().write(Highlighter.format(object));
    }

    public Terminal getTerminal() {
        return this.terminal;
    }

    public LineReader getReader() {
        return this.reader;
    }

    public StatusLine getStatus() {
        return this.status;
    }

    public ConfigurationPath getConfigurations() {
        return Console.configurations;
    }

    public void run() {
        while (BOOTING) {
            Common.sleepThread(10);
        }
        this.status.start();
        Common.sleepThread(50);
        String line = "";
        while (!this.thread.isInterrupted()) {
            try {
                Obj result = null;
                //Graphitty.out(this.terminal.output(), "%s{{v%d&^%d&Xv}}","\n");
                line = this.reader.readLine(Graphitty.string("{{m}}mtron{{g}}> ")).trim();
                if (line.equals(":header"))
                    this.outputHeader();
                else if (line.equals(":quit"))
                    break;
                else if (line.equals(":clear")) {
                    Graphitty.out(this.terminal.output(), "{{XX&@}}");
                    this.status.refresh();
                } else if (line.equals(":help")) {
                    Graphitty.out(this.terminal.output(), new Panel("{{c}}help menu{{X}}", new Table(
                            List.of("name", "short", "description"))
                            .addRow(List.of("space walk", "<tab>", "explore spaces"))
                            .addRow(List.of("introspect", "<space><tab>", "analyze machine"))
                            .addRow(List.of("header", "random header", "random header")).toString(), Border.simple).toString());
                } else if (line.startsWith(":log")) {
                    LogObj.setSLF4J(line.substring(4));
                } else if (line.startsWith(":top")) {
                    TTop.ttop(terminal, new PrintStream(terminal.output()), System.err, new String[0]);
                } else if (line.startsWith(":less")) {
                    Commands.less(terminal, terminal.input(), new PrintStream(terminal.output()), System.err, Paths.get(""), new String[0]);
                } else if (line.startsWith(":select")) {
                    final Subscriptions selector = new Subscriptions(this);
                    final String selected = selector.select();
                    LOG.info("space selected: %s", selected);
                } else if (line.startsWith(":state")) {
                    this.status.setState(Level.valueOf(line.substring(6).trim().toUpperCase()));
                } else {
                    long startTime = System.currentTimeMillis();
                    result = mParser.parse(line);
                    if (null != result) {
                        (result.isNoObj() ?
                                MObjs.empty() :
                                result.isObjCall() ? MMachine.of(result.as()).apply() : result).stream()
                                .forEach(o -> {
                                    this.write("{{-X-}}{{m}}=={{g}}>{{X}}");
                                    this.write(o);
                                    this.write("\n");
                                });
                    }
                    this.status.setLastExecutionTime(System.currentTimeMillis() - startTime);
                }
                
                /*if (null != result && !result.isNoObj()) {
                    fURI key = f("xxx");
                    for (Obj o : (result.isObjCall() ? MMachine.of(result.as()).apply() : result).stream().toList()) {
                        if (o.isRel() && o.<Rel>as().jvm().get0().isUri()) {
                            Rel rel = o.<Rel>as();
                            if (!rel.jvm().get0().uriValue().retract().equals(key)) {
                                key = rel.jvm().get0().uriValue().retract();
                                this.highlighter.highlightToOut(this.terminal.output(), "{{_&y}}%s{{X}}".formatted(rel.jvm().get0().uriValue().retract()));
                                this.highlighter.highlightToOut(this.terminal.output(), "\n");
                            }
                            this.highlighter.highlightToOut(this.terminal.output(), "{{-X-}}{{m}}=={{g}}>{{X}}");
                            this.highlighter.highlightToOut(this.terminal.output(), rel.jvm().get0().uriValue().name());
                            this.highlighter.highlightToOut(this.terminal.output(), "{{g}}=>{{X}}");
                            this.highlighter.highlightToOut(this.terminal.output(), rel.jvm().get1());
                        } else {
                            this.highlighter.highlightToOut(this.terminal.output(), "{{-X-}}{{m}}=={{g}}>{{X}}");
                            this.highlighter.highlightToOut(this.terminal.output(), o);
                        }
                        this.terminal.output().write("\n".getBytes(StandardCharsets.UTF_8));
                    }
                }*/
            } catch (final UserInterruptException e) {
                LOG.warn(Graphitty.sillyPrint("process interrupted", true, true));
            } catch (final EndOfFileException e) {
                System.exit(0);
            } catch (final Exception e) {
                Throwable x = e;
                int y = 0;
                while (null != x) {
                    LOG.error("\n%s%s", ((0 == y++) ? "" : (" ".repeat(y) + "\\_")), x.getMessage());
                    x = x.getCause();
                }
                final String stackTrace = this.reader.readLine(Graphitty.string("{{y}}display stack trace {{g}}[y/N]{{y}}?{{X}} "));
                if (stackTrace.trim().equalsIgnoreCase("y")) {
                    e.printStackTrace();
                }

            }
        }
        try {
            this.terminal.close();
        } catch (final IOException e) {
            LOG.error(e);
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
            if (null == randomHeader) throw new IllegalArgumentException("<unknown header: " + randomHeaderTitle + ">");
            this.terminal.writer().print(Graphitty.string(randomHeader));
            this.terminal.writer().flush();
        } catch (final Exception e) {
            this.terminal.writer().println("...a fundamental boot exception has occurred.");
            this.terminal.writer().println("      ...this does not bode well for your time in the meTaRon: " + e);
            this.terminal.writer().println(" __  __  ____  ____   __   ____  ____  _____  _  _ \n" +
                    "(  \\/  )( ___)(_  _) /__\\ (_  _)(  _ \\(  _  )( \\( )\n" +
                    " )    (  )__)   )(  /(__)\\  )(   )   / )(_)(  )  ( \n" +
                    "(_/\\/\\_)(____) (__)(__)(__)(__) (_)\\_)(_____)(_)\\_)");
            this.terminal.writer().printf("\t\t\tby PhaseShift Studio (%s)\n", Calendar.getInstance().get(Calendar.YEAR));
            this.terminal.flush();
        }
        LOG.none("\t{{b}}ve{{y}}rs{{m}}ion {{y}}%s{{X}}\n", METATRON_VERSION);
        Graphitty.out(this.terminal.output(), "   {{m}}:help{{X}} for console features\n\n");
    }

    @Override
    public Thread getThread() {
        return this.thread;
    }

    class CustomWidgets extends Widgets {

        private CustomWidgets(final LineReader reader) {
            super(reader);
            /*getKeyMap().bind((Widget) () -> {
                new MCompleter(Console.this).complete(reader, null, new ArrayList<>());
                return true;
            },"\t");*/
            getKeyMap().bind((Widget)
                    () -> Editor.of(Console.this, reader.getBuffer().toString()), ctrl('y'));
            getKeyMap().bind((Widget)
                    () -> {
                        Console.this.reader.getBuffer().write("\n");
                        return true;
                    }, alt('\n'));
            getKeyMap().bind((Widget)
                    () -> {
                        Console.this.close();
                        System.exit(0);
                        return true;
                    }, ctrl('q'));
        }
    }
}