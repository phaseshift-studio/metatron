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

package studio.phaseshift.metatron.lang.parse;

import org.jline.reader.*;
import org.jline.reader.impl.*;
import org.jline.reader.impl.history.*;
import org.jline.terminal.*;
import org.jline.utils.*;
import org.slf4j.*;
import studio.phaseshift.metatron.lang.obj.*;

import java.io.*;
import java.nio.file.*;

public class Console {
    private static final Logger LOG = LoggerFactory.getLogger(Console.class);

    public void run() throws IOException {
        final Terminal terminal = TerminalBuilder.builder().system(true).build();

        Highlighter highlighter = new DefaultHighlighter() {
            @Override
            public AttributedString highlight(LineReader reader, String buffer) {
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
                } else if (buffer.contains("warning")) {
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
        final LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("metatron")
                .history(history)
                .highlighter(highlighter)
                .variable(LineReader.HISTORY_FILE, Paths.get(".metatron.history"))
                .option(LineReader.Option.AUTO_FRESH_LINE, true)
                .build();
        boolean done = false;

        String line = "";
        while (true) {
            try {
                line = reader.readLine("mtron> ");
                if (line.trim().equals(":quit"))
                    break;
                else {
                    final BObj.Obj result = MParser.parse(line);
                    if (!result.isNoObj())
                        System.out.println("==>" + result);
                }
            } catch (UserInterruptException e) {
                terminal.writer().println("process interrupted");
            } catch (EndOfFileException e) {
                terminal.writer().println("shutting down");
                break;
            } catch (final Exception e) {
                LOG.error(e.getMessage());
            }
        }
        terminal.close();
        System.exit(0);
    }

    public static void main(final String[] args) throws Exception {
        new Console().run();
    }
}