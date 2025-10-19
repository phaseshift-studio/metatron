/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.ui;

import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.widget.Widgets;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.translate.ObjParser;
import studio.phaseshift.metatron.space.device.log.Log;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.StringUtil;
import studio.phaseshift.metatron.vm.MMachine;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiConsumer;

import static org.jline.keymap.KeyMap.ctrl;

public class Console {
    private static final String METATRON_VERSION = "0.1-alpha";

    private static final GraphittyLogger LOG = Graphitty.log(Console.class);
    public static String HEADER_FILE = "./conf/ansi_headers.txt";
    public static String HEADER_SEPARATOR = "####################";
    private static boolean RESOLVE_MODE = false;
    private final Terminal terminal;
    private final LineReader reader;

    public Console(final Map<String, String> terminalArgs) throws IOException {
        final DefaultParser parser = new DefaultParser()
                .quoteChars(new char[]{'\'', '"'})
                .lineCommentDelims(new String[]{"---"})
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
                .variable(LineReader.SECONDARY_PROMPT_PATTERN, Graphitty.string("{{-X&v1&^1&FORM2}}    {{FORM1}}> {{X}}"))
                .variable(LineReader.INDENTATION, 0)
                .build();
        terminalArgs.forEach((k, v) -> {
            if (k.equals("log"))
                Log.setSLF4J(v);
        });
        // final AutosuggestionWidgets autosuggestionWidgets = new AutosuggestionWidgets(this.reader);
        // autosuggestionWidgets.enable();
    }

    public static void main(final String[] args) throws IOException {
        final Map<String, String> params = new HashMap<>();
        for (final String arg : args) {
            final String[] kv = arg.split("=");
            params.put(kv[0].replace("--", ""), kv[1]);
        }
        boolean reload = true;
        while (reload) {
            reload = false;
            final Console console = new Console(params);
            try {
                console.run();
            } catch (final Exception e) {
                LOG.error("a %s error occurred. reloading the console.\n", Graphitty.sillyPrint("catastrophic", true, true));
                final String stackTrace = console.reader.readLine(Graphitty.string("{{WARN}}display stack trace {{FORM1}}[y/N]{{WARN}}?{{X}} "));
                if (stackTrace.trim().equalsIgnoreCase("y"))
                    e.printStackTrace();
                reload = true;
            }
            console.stop();
        }
    }

    public void stop() {
        try {
            this.terminal.close();
        } catch (IOException e) {
            LOG.error(e);
        }
    }

    public void run() throws IOException {
        new CustomWidgets(this.reader);
        BootLoader.load();
        String line = "";
        while (true) {
            try {
                Obj result = null;
                Graphitty.out(this.terminal.output(), "\n{{v1&^1}}");
                line = this.reader.readLine(Graphitty.string("{{FORM2}}mton{{FORM1}}> ")).trim();
                if (line.equals(":header"))
                    this.outputHeader();
                else if (line.equals(":quit"))
                    break;
                else if (line.startsWith(":log")) {
                    Log.setSLF4J(line.substring(4));
                } else
                    result = ObjParser.parse(line);

                if (null != result) {
                    IteratorUtil.stream(result.isNoObj() ?
                            List.of() :
                            result.isCode() ?
                                    MMachine.of(result.as()).apply() :
                                    result).forEach(
                            o -> Graphitty.out(this.terminal.output(), "{{-X-}}{{FORM2}}=={{FORM1}}>{{X}}%s\n".formatted(o)));
                }
            } catch (final UserInterruptException e) {
                LOG.warn(Graphitty.sillyPrint("process interrupted", true, true));
            } catch (final EndOfFileException e) {
                System.exit(0);
            } catch (final Exception e) {
                Throwable x = e;
                int y = 0;
                while (null != x) {
                    LOG.error("%s%s", ((0 == y++) ? "" : (" ".repeat(y) + "\\_")), x.getMessage());
                    x = x.getCause();
                }
                final String stackTrace = this.reader.readLine(Graphitty.string("{{WARN}}display stack trace {{FORM1}}[y/N]{{WARN}}?{{X}} "));
                if (stackTrace.trim().equalsIgnoreCase("y")) {
                    e.printStackTrace();
                }
            }
        }
        this.terminal.close();
        BootLoader.close();
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
                . {{y}}{{[r]}}r{{[d]}}esolve {{m}}[{{y}}ctrl-r{{m}}]{{X}}: automatic expression resolution
                . {{y}}hi{{[r]}}d{{[d]}}e    {{m}}[{{y}}ctrl-h{{m}}]{{X}}: hide base type prefixes
                . {{y}}{{[r]}}q{{[d]}}uit    {{m}}[{{y}}ctrl-q{{m}}]{{X}}: leave the metatron
                
                """);
    }

    static class CustomHighlighters implements Highlighter {
        private final Terminal terminal;
        private final List<BiConsumer<AttributedStringBuilder, String>> highlighters = new ArrayList<>();

        private CustomHighlighters(final Terminal terminal) {
            this.terminal = terminal;
            // auto compilation
            /*this.highlighters.add((builder,buffer) -> {
               if(!buffer.isEmpty())
                  builder.append(buffer.replaceAll("\\(",Graphitty.string("{{g}}({{/g}}")).replaceAll("\\)",Graphitty.string("{{g}}){{/g}}")));
            })*/
            ;
            this.highlighters.add((builder, buffer) -> {
                if (RESOLVE_MODE) {
                    try {
                        if (buffer.isEmpty()) {
                            //builder.append(buffer);
                            //Graphitty.out(this.terminal.output(), "{{v1&-X&^1&|%d}}".formatted(9));
                        } else {
                            final Obj o = ObjParser.parse(buffer);
                            final int xLocation = this.terminal.getCursorPosition(System.out::print).getX() + 1;
                            // final int promptLength = 8; //"mtron> ".length() + 1;
                            builder.append(buffer);
                            final String oString = o.toString();
                            final int yDistance = StringUtil.countLines(oString);
                            Graphitty.out(this.terminal.output(), "{{v1&-X-&Xv&|%d}}%s".formatted(8, oString));
                            Graphitty.out(this.terminal.output(), "{{^%d&|%d}}".formatted(yDistance, xLocation));
                        }

                    } catch (final Exception e) {
                        // console expression doesn't compile yet
                        builder.append(buffer);
                    }
                } else {
                    builder.append(buffer);
                }
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
            this.addWidget("quit-widget", this::quitWidget);
            this.addWidget("resolve-widget", this::resolveWidget);
            this.addWidget("define-widget", this::defineWidget);
            this.addWidget("hide-widget", this::hideWidget);
            getKeyMap().bind(new Reference("quit-widget"), ctrl('q'));
            getKeyMap().bind(new Reference("resolve-widget"), ctrl('r'));
            getKeyMap().bind(new Reference("define-widget"), ctrl('e'));
            getKeyMap().bind(new Reference("hide-widget"), ctrl('i'));
            //   getKeyMap().bind(new Reference("detach-widget"), alt(key_down.name()));
        }

        private boolean quitWidget() {
            BootLoader.close();
            System.exit(0);
            return true;
        }

        private boolean resolveWidget() {
            RESOLVE_MODE = !RESOLVE_MODE;
            //LOG.none("{{@&v1&-X}}switched %s auto-resolution mode{{^1&/@}}", RESOLVE_MODE ? "{{g}}on{{/g}}" : "{{y}}off{{/y}}");
            return true;
        }

        private boolean defineWidget() {
            String sourceCode = reader.getBuffer().toString();
            reader.getBuffer().clear();
            String sourceName = "stuff"; // reader.readLine(/*Graphitty.string("{{-X-}}\r{{m}}name{{g}}:{{X}} "*/);
            String sourceKey = "B"; //reader.readLine(Graphitty.string("{{-X-}}\r{{m}}hotkey{{g}}:{{X}} "));
            getKeyMap().bind(new Reference(sourceName), ctrl(sourceKey.charAt(0)));
            this.addWidget(sourceName, () -> {
                ObjParser.eval(sourceCode).forEachRemaining(System.out::println);
                return true;
            });
            return true;
        }

        private boolean hideWidget() {
            boolean hiding = ObjStringSerializer.HIDE_TIDS.isEmpty();
            if (hiding)
                ObjStringSerializer.HIDE_TIDS.addAll(ObjStringSerializer.BASE_TIDS);
            else
                ObjStringSerializer.HIDE_TIDS.clear();
            final int xLocation = terminal.getCursorPosition(System.out::print).getX() + 1;
            Graphitty.out(terminal.output(), "\n{{-X-}}{{%s}}%s{{/%s}}{{X}} base type prefixes{{^1&|%d}}{{X}}", hiding ? "y" : "g", hiding ? "hiding" : "showing", hiding ? "y" : "g", xLocation);
            return true;
        }
    }
}