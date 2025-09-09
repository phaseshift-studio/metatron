/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
import studio.phaseshift.metatron.lang.monoid.SMonoid.Monoid;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.SObj;
import studio.phaseshift.metatron.lang.parse.ObjParser;
import studio.phaseshift.metatron.util.IteratorUtil;

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
    private final Terminal terminal;
    private final LineReader reader;

    private static boolean RESOLVE_MODE = false;

    static class CustomWidgets extends Widgets {

        private CustomWidgets(final LineReader reader) {
            super(reader);
            this.addWidget("quit-widget", this::quitWidget);
            this.addWidget("resolve-widget", this::resolveWidget);
            getKeyMap().bind(new Reference("quit-widget"), ctrl('q'));
            getKeyMap().bind(new Reference("resolve-widget"), ctrl('r'));
        }

        public static void of(final LineReader reader) {
            new CustomWidgets(reader);
        }

        private boolean quitWidget() {
            LOG.none(Graphitty.sillyPrint("\nshutting down the metatron\n", true, true));
            System.exit(0);
            return true;
        }

        private boolean resolveWidget() {
            RESOLVE_MODE = !RESOLVE_MODE;
            //LOG.none("{{@}}{{v1}}{{-X}}switched %s auto-resolution mode{{^1}}{{/@}}", RESOLVE_MODE ? "{{g}}on{{/g}}" : "{{y}}off{{/y}}");
            return true;
        }
    }

    static class CustomHighlighters implements Highlighter {
        private final Terminal terminal;
        private final List<BiConsumer<AttributedStringBuilder, String>> highlighters = new ArrayList<>();

        private CustomHighlighters(final Terminal terminal) {
            this.terminal = terminal;
            // auto compilation
            this.highlighters.add((builder, buffer) -> {
                try {
                    if (!buffer.isEmpty()) {
                        final BObj.Obj o = ObjParser.parse(buffer);
                        final int xLocation = this.terminal.getCursorPosition(System.out::print).getX() + 1;
                        // final int promptLength = 8; //"mtron> ".length() + 1;
                        builder.append(buffer);
                        if (o.isCode() && Console.RESOLVE_MODE) {
                            final BObj.Code rCode = SObj.Code.of(o.codeValue().stream().map(BObj.Inst::resolve).toList());
                            Graphitty.stdout().print(Graphitty.string("{{v1}}{{|%d}}%s".formatted(8, rCode)));
                            Graphitty.stdout().print(Graphitty.string("{{^1}}{{|%d}}".formatted(xLocation)));
                        } else {
                            Graphitty.stdout().print(Graphitty.string("{{v1}}{{|%d}}%s".formatted(8, o)));
                            Graphitty.stdout().print(Graphitty.string("{{^1}}{{|%d}}".formatted(xLocation)));
                        }
                    }
                } catch (final Exception e) {
                    // console expression doesn't compile yet
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

    public Console() throws IOException {
        final DefaultParser parser = new DefaultParser().quoteChars(new char[]{'\'', '"'}).lineCommentDelims(new String[]{"---"});
        parser.setEofOnUnclosedBracket(DefaultParser.Bracket.CURLY, DefaultParser.Bracket.ROUND, DefaultParser.Bracket.SQUARE);
        this.terminal = TerminalBuilder.builder().encoding(StandardCharsets.UTF_8).system(true)/*.signalHandler(Terminal.SignalHandler.SIG_IGN)*/.build();
        this.outputHeader();
        LOG.none("\t{{b}}ve{{y}}rs{{m}}ion {{y}}%s{{X}}\n\n", METATRON_VERSION);
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
                .variable(LineReader.SECONDARY_PROMPT_PATTERN, Graphitty.string("\n{{-X}}{{v1}}{{^1}}{{FORM1}}%P >{{X}}"))
                .variable(LineReader.INDENTATION, 2)
                .build();
        CustomWidgets.of(this.reader);
    }

    public void run() throws IOException {
        BootLoader.load();
        String line = "";
        while (true) {
            try {
                this.terminal.writer().print(Graphitty.string("\n{{v1}}{{^1}}"));
                line = this.reader.readLine(Graphitty.string("{{FORM2}}mtron{{FORM1}}>{{X}} "));
                if (line.trim().equals(":header"))
                    this.outputHeader();
                else if (line.trim().equals(":quit"))
                    break;
                else {
                    Graphitty.out(this.terminal.output(), "{{-X}}");
                    final BObj.Obj result = ObjParser.parse(line);
                    IteratorUtil.iterate(IteratorUtil.consume(result.isNoObj() ?
                                    Collections.emptyIterator() :
                                    result.isCode() ?
                                            new Monoid(result).iterator() :
                                            result.iterator(),
                            o -> Graphitty.out(this.terminal.output(), "{{FORM2}}=={{FORM1}}>{{X}}%s\n".formatted(o))));
                }
            } catch (final UserInterruptException e) {
                LOG.warn(Graphitty.sillyPrint("process interrupted", true, true));
            } catch (final EndOfFileException e) {
                try {
                    LOG.none(Graphitty.sillyPrint("the metatron is now offline\n", true, true));
                    break;
                } catch (final Exception e1) {
                    System.exit(0);
                }
            } catch (final Exception e) {
                LOG.error(e.getMessage());
                final String stackTrace = this.reader.readLine(Graphitty.string("{{WARN}}display stack trace {{FORM1}}[y/N]{{WARN}}?{{X}} "));
                if (stackTrace.trim().equalsIgnoreCase("y"))
                    e.printStackTrace();
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
    }

    public static void main(final String[] args) throws Exception {
        new Console().run();
    }
}