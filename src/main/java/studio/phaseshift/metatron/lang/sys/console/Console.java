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
import org.jline.builtins.TTop;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.widget.Widgets;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.inst.mInstSet;
import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObjs;
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;
import studio.phaseshift.metatron.lang.core.mach.type.impl.MMachine;
import studio.phaseshift.metatron.lang.util.logObj;
import studio.phaseshift.metatron.lang.util.serial.ObjStringSerializer;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.ui.Mode;
import studio.phaseshift.metatron.util.MTronException;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiConsumer;

import static org.jline.keymap.KeyMap.ctrl;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.INST_TID;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.REC_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.sys.sysInstSet.SYS_TID;

public class Console extends MRec implements Mode {

    public static final fURI CONSOLE_TID = SYS_TID.extend("console");

    private static final String METATRON_VERSION = "0.1-alpha";

    private final GraphittyLogger LOG = Graphitty.log(this);
    public static String HEADER_FILE = "./conf/ansi_headers.txt";
    public static String HEADER_SEPARATOR = "####################";
    protected boolean RESOLVE_MODE = false;
    private final Terminal terminal;
    private final LineReader reader;
    private Thread mainThread;

    public static final Type CONSOLE_TYPE = T(CONSOLE_TID, isa_(rec()), instC(INST_TID.dom(ALL.maybe()).rng(CONSOLE_TID), lst(T(REC_TID)), (lhs, inst) -> {
        final Console console = new Console(inst.arg(0).as());
        console.start();
        return console;
    }));

    public Console(final Rec options) {
        super(options.jvm(), CONSOLE_TID, f("/sys/obj/console"));
        try {
            final DefaultParser parser = new DefaultParser()
                    .quoteChars(new char[]{'\'', '"'})
                    .lineCommentDelims(new String[]{"[--", "--]"})
                    .blockCommentDelims(new DefaultParser.BlockCommentDelims("[===", "===]"))
                    .eofOnUnclosedQuote(true)
                    .eofOnUnclosedBracket(DefaultParser.Bracket.CURLY, DefaultParser.Bracket.ROUND, DefaultParser.Bracket.SQUARE);
            this.terminal = TerminalBuilder.builder().encoding(StandardCharsets.UTF_8).system(true)/*.signalHandler(Terminal.SignalHandler.SIG_IGN)*/.build();
            this.outputHeader();
            // this.terminal.handle(Terminal.Signal.WINCH) // TODO: signal handling on some CNTRL-?? to resolve (not evaluate) current expression
            final History history = new DefaultHistory();
            this.reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .appName("metatron")
                    .history(history)
                    .highlighter(CustomHighlighters.of(this.terminal))
                    .parser(parser)
                    .variable(LineReader.HISTORY_FILE, Paths.get(".metatron.history"))
                    .option(LineReader.Option.AUTO_FRESH_LINE, true)
                    .option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .variable(LineReader.SECONDARY_PROMPT_PATTERN, Graphitty.string("{{-X&v1&^1&m}}     {{g}}| {{X}}"))
                    .variable(LineReader.INDENTATION, 0)
                    .completer(new MCompleter(this))
                    .build();
            //reader.unsetOpt(LineReader.Option.INSERT_TAB);


        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    public static Console of(final Rec options) {
        return new Console(options);
    }

    public void stop() {
        try {
            this.reader.getBuffer().clear();
            this.terminal.close();
            this.mainThread.interrupt();
        } catch (final IOException e) {
            LOG.error(e);
        }
    }

    public void start() {
        final Runnable console = () -> {
            try {
                this.run();
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        };
        this.mainThread = new Thread(console);
        this.mainThread.start();
    }

    public Terminal getTerminal() {
        return this.terminal;
    }

    public LineReader getReader() {
        return this.reader;
    }

    @Override
    public Optional<Thread> mainThread() {
        return Optional.of(this.mainThread);
    }

    public void run() throws Exception {
        Mode.waitForBoot();
        new CustomWidgets(this.reader);
        String line = "";
        while (true) {
            try {
                Obj result = null;
                Graphitty.out(this.terminal.output(), "%s{{v%d&^%d&Xv}}",
                        RESOLVE_MODE ? "\n".repeat(3) : "\n".repeat(1),
                        RESOLVE_MODE ? 3 : 1,
                        RESOLVE_MODE ? 3 : 1);
                RESOLVE_MODE = false;
                line = this.reader.readLine(Graphitty.string("{{m}}mtron{{g}}> ")).trim();
                if (line.equals(":header"))
                    this.outputHeader();
                else if (line.equals(":quit"))
                    break;
                else if (line.equals(":clear")) {
                    Graphitty.out(this.terminal.output(), "{{XX&@}}");
                } else if (line.startsWith(":log")) {
                    logObj.setSLF4J(line.substring(4));
                } else if (line.startsWith(":top")) {
                    TTop.ttop(terminal, System.out, System.err, new String[0]);
                } else if (line.startsWith(":box")) {
                    LOG.none(Box.wrap(line.substring(4).trim(),List.of("|","|","-","-")));
                } else if (line.startsWith(":less")) {
                    Commands.less(terminal, System.in, System.out, System.err, Paths.get(""), new String[0]);
                } else if (line.startsWith(":select")) {
                    final Subscriptions selector = new Subscriptions(this);
                    final String selected = selector.select();
                    LOG.info("space selected: %s", selected);
                } else
                    result = mParser.parse(line);

                if (null != result) {
                    (result.isNoObj() ?
                            MObjs.empty() :
                            result.isObjCall() ?
                                    MMachine.of(result.as()).apply() :
                                    result).stream().forEach(
                            o -> Graphitty.out(this.terminal.output(), "{{-X-}}{{m}}=={{g}}>{{X}}%s\n".formatted(o.toString())));
                }
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
        this.terminal.close();
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
            this.terminal.writer().println("...an exception has occurred.");
            this.terminal.writer().println("      ...this doesn't bode well for your time in the meTaRon: " + e);
            this.terminal.writer().println(" __  __  ____  ____   __   ____  ____  _____  _  _ \n" +
                    "(  \\/  )( ___)(_  _) /__\\ (_  _)(  _ \\(  _  )( \\( )\n" +
                    " )    (  )__)   )(  /(__)\\  )(   )   / )(_)(  )  ( \n" +
                    "(_/\\/\\_)(____) (__)(__)(__)(__) (_)\\_)(_____)(_)\\_)");
            this.terminal.writer().printf("\t\t\tby PhaseShift Studio (%s)\n", Calendar.getInstance().get(Calendar.YEAR));
            this.terminal.flush();
        }
        LOG.none("\t{{b}}ve{{y}}rs{{m}}ion {{y}}%s{{X}}\n\n", METATRON_VERSION);
        Graphitty.out(this.terminal.output(), """
                . {{y&_}}r{{X&y}}esolve {{m}}[{{y}}ctrl-r{{m}}]{{X}}: automatic expression resolution
                . {{y&_}}h{{X&y}}ide    {{m}}[{{y}}ctrl-h{{m}}]{{X}}: hide base type prefixes
                . t{{y&_}}y{{X&y}}ping  {{m}}[{{y}}ctrl-y{{m}}]{{X}}: typing checking
                . {{y&_}}q{{X&y}}uit    {{m}}[{{y}}ctrl-t{{m}}]{{X}}: leave the metatron
                
                """);
    }

    static class CustomHighlighters implements Highlighter {
        private final Terminal terminal;
        private final List<BiConsumer<AttributedStringBuilder, String>> highlighters = new ArrayList<>();

        private CustomHighlighters(final Terminal terminal) {
            this.terminal = terminal;
            this.highlighters.add((builder, buffer) -> {
                builder.append(buffer);
            });
        }

        public static Highlighter of(final Terminal terminal) {
            return new CustomHighlighters(terminal);
        }

        @Override
        public AttributedString highlight(final LineReader reader, final String buffer) {
            final AttributedStringBuilder builder = new AttributedStringBuilder();
            this.highlighters.forEach(highlighter -> highlighter.accept(builder, buffer));
            return builder.toAttributedString();
        }
    }

    class CustomWidgets extends Widgets {

        private CustomWidgets(final LineReader reader) {
            super(reader);
            this.addWidget("quit-widget", () -> {
                System.exit(0);
                return true;
            });
            this.addWidget("resolve-widget", () -> RESOLVE_MODE = !RESOLVE_MODE);
            this.addWidget("editor-widget", () -> Editor.of(Console.this, reader.getBuffer().toString()));
            this.addWidget("hide-widget", this::hideWidget);
            this.addWidget("typing-widget", this::typingWidget);
            /// ///////////////////////////////////////////////////////////////////////////////////////////
            getKeyMap().bind(new Reference("quit-widget"), ctrl('q'));
            getKeyMap().bind(new Reference("resolve-widget"), ctrl('r'));
            getKeyMap().bind(new Reference("hide-widget"), ctrl('h'));
            getKeyMap().bind(new Reference("typing-widget"), ctrl('y'));
            getKeyMap().bind(new Reference("editor-widget"), ctrl('e'));
            //   getKeyMap().bind(new Reference("detach-widget"), alt(key_down.name()));
        }


        private boolean typingWidget() {
            BootLoader.TYPE_CHECK = !BootLoader.TYPE_CHECK;
            final int xLocation = terminal.getCursorPosition(System.out::print).getX() + 1;
            Graphitty.out(terminal.output(), "\n{{-X-}}{{%s}}%s{{/%s}}{{X}} base type prefixes{{^1&|%d}}{{X}}", !BootLoader.TYPE_CHECK ? "y" : "g", !BootLoader.TYPE_CHECK ? "no type checking" : "typing checking", !BootLoader.TYPE_CHECK ? "y" : "g", xLocation);

            return true;
        }

        private boolean hideWidget() {
            boolean hiding = ObjStringSerializer.HIDE_TIDS.isEmpty();
            if (hiding)
                ObjStringSerializer.HIDE_TIDS.addAll(mInstSet.BASE_TYPES);
            else
                ObjStringSerializer.HIDE_TIDS.clear();
            final int xLocation = terminal.getCursorPosition(System.out::print).getX() + 1;
            Graphitty.out(terminal.output(), "\n{{-X-}}{{%s}}%s{{/%s}}{{X}} base type prefixes{{^1&|%d}}{{X}}", hiding ? "y" : "g", hiding ? "hiding" : "showing", hiding ? "y" : "g", xLocation);
            return true;
        }
    }
}