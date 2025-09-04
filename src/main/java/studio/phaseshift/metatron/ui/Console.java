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
import org.jline.reader.impl.DefaultHighlighter;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.lang.monoid.SMonoid.Monoid;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.Palette;
import studio.phaseshift.metatron.lang.parse.ObjParser;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.*;

import static org.jline.jansi.Ansi.ansi;

public class Console {
    private static final Logger LOG = LoggerFactory.getLogger(Console.class);
    public static String HEADER_FILE = "/ansi_headers.txt";
    public static String HEADER_SEPARATOR = "####################";
    public static Palette PALETTE = Palette.STANDARD;
    private final Terminal terminal;
    private final LineReader reader;

    public Console() throws IOException {
        this.terminal = TerminalBuilder.builder().system(true).build();
        Highlighter highlighter = new DefaultHighlighter() {
            @Override
            public AttributedString highlight(final LineReader reader, final String buffer) {
                // Create a builder for the highlighted text
                AttributedStringBuilder builder = new AttributedStringBuilder();

                // Apply different styles based on content
                if (buffer.contains("error")) {
                    // Highlight "error" in red
                    int index = buffer.indexOf("error");
                    builder.append(buffer.substring(0, index));
                    builder.styled(
                            AttributedStyle.BOLD.foreground(AttributedStyle.RED), buffer.substring(index, index + 5));
                    builder.append(buffer.substring(index + 5));
                } else if (buffer.contains("m:")) {
                    // Highlight "error" in red
                    int index = buffer.indexOf("m:");
                    builder.append(buffer.substring(0, index));
                    builder.styled(
                            AttributedStyle.BOLD.foreground(AttributedStyle.MAGENTA), buffer.substring(index, index + 2));
                    builder.append(buffer.substring(index + 2));
                } /*else if (buffer.contains("(")) {
                    // Highlight "error" in red
                    int index = buffer.lastIndexOf("(");
                    int dotIndex = buffer.lastIndexOf(".");
                    if(dotIndex < index) {
                        builder.append(buffer.substring(0, dotIndex));
                        builder.styled(
                                AttributedStyle.BOLD.foreground(AttributedStyle.BLUE), buffer.substring(dotIndex, index));
                        //builder.append(buffer.substring(dotIndex,index));
                    } else {
                        builder.append(buffer);
                    }
                } */ else if (buffer.contains("warning")) {
                    // Highlight "warning" in yellow
                    int index = buffer.indexOf("warning");
                    builder.append(buffer.substring(0, index));
                    builder.styled(
                            AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW),
                            buffer.substring(index, index + 7));
                    builder.append(buffer.substring(index + 7));
                } else if (buffer.startsWith("#")) {
                    builder.styled(AttributedStyle.BOLD_OFF.foreground(AttributedStyle.WHITE), buffer);
                } else {
                    builder.append(buffer);
                }
                return builder.toAttributedString();
            }
        };
        final History history = new DefaultHistory();
        this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("metatron")
                .history(history)
                .highlighter(highlighter)
                .variable(LineReader.HISTORY_FILE, Paths.get(".metatron.history"))
                .option(LineReader.Option.AUTO_FRESH_LINE, true)
                .build();
    }

    public void run() throws IOException {
        // AnsiConsole.systemInstall();
        this.outputHeader();
        BootLoader.load();
        String line = "";
        while (true) {
            try {
                line = this.reader.readLine(ansi().fg(PALETTE.form2C()).a("mtron").fg(PALETTE.formC()).a("> ").reset().toString());
                if (line.trim().equals(":quit"))
                    break;
                else {
                    final Object result = ObjParser.parse(line);
                    if (result instanceof Monoid) {
                        IteratorUtil.iterate(IteratorUtil.consume(
                                ((Monoid) result).iterator(),
                                n -> this.terminal.writer().println(ansi().fg(PALETTE.form2C()).a("==").fg(PALETTE.formC()).a(">").reset().a(n).toString())));
                    } else if (!(result instanceof BObj.NoObj)) {
                        this.terminal.writer().println(ansi().fg(PALETTE.form2C()).a("==").fg(PALETTE.formC()).a(">").reset().a(result).toString());
                    }
                }
            } catch (final UserInterruptException e) {
                this.terminal.writer().println("process interrupted");
            } catch (final EndOfFileException e) {
                this.terminal.writer().println("shutting down");
                break;
            } catch (final Exception e) {
                e.printStackTrace();
                LOG.error(ansi().a(Graphitty.global().parse(e.getMessage())).reset().toString());
                final String stackTrace = this.reader.readLine(ansi().fg(PALETTE.warnC()).a("display stack trace ").fg(PALETTE.formC()).a("[y/N]").fg(PALETTE.warnC()).a("? ").reset().toString());
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
            final BufferedReader input = new BufferedReader(new InputStreamReader(Console.class.getResourceAsStream(HEADER_FILE)));
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
            this.terminal.writer().print(randomHeader);
        } catch (final Exception e) {
            this.terminal.writer().println("...an exception has occurred.");
            this.terminal.writer().println("      ...this doesn't bode well for your time in the meTaRon: " + e);
            this.terminal.writer().println(" __  __  ____  ____   __   ____  ____  _____  _  _ \n" +
                    "(  \\/  )( ___)(_  _) /__\\ (_  _)(  _ \\(  _  )( \\( )\n" +
                    " )    (  )__)   )(  /(__)\\  )(   )   / )(_)(  )  ( \n" +
                    "(_/\\/\\_)(____) (__)(__)(__)(__) (_)\\_)(_____)(_)\\_)");
            this.terminal.writer().printf("\t\t\tby PhaseShift Studio (%s)\n", Calendar.getInstance().get(Calendar.YEAR));
        }
    }

    public static void main(final String[] args) throws Exception {
        new Console().run();
    }
}