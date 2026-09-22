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
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

/**
 * The console's jline scaffolding: the mtron parser dialect, terminal and
 * line reader construction, and the redraw helpers widgets and traces call
 * back into.
 * <p>
 * The console facade keeps the {@code terminal}, {@code reader} and
 * {@code widgets} fields — half the codebase reaches for them directly — but
 * the jline-facing knowledge (dialect, options, prompt machinery) lives here,
 * so the facade only has to wire the pieces together in its constructor.
 */
public final class ReaderSetup {

    private ReaderSetup() {
    }

    /**
     * The mtron parser dialect: quote chars, line and block comment delims,
     * and the EOF-on-unclosed-quote/bracket rules the repl needs — a
     * half-typed line must not parse.  The same instance feeds both the
     * reader and the builtins registry.
     */
    public static DefaultParser parser() {
        return new DefaultParser()
                .quoteChars(new char[]{'\'', '"'})
                .lineCommentDelims(new String[]{"[--", "--]"})
                .blockCommentDelims(new DefaultParser.BlockCommentDelims("[===", "===]"))
                .eofOnUnclosedQuote(true)
                .eofOnUnclosedBracket(DefaultParser.Bracket.CURLY, DefaultParser.Bracket.ROUND, DefaultParser.Bracket.SQUARE);
    }

    /**
     * The console's terminal.  What ctrl-c does is a console concern (which
     * machine to interrupt), so the interrupt action is handed in.
     */
    public static Terminal buildTerminal(final Runnable interrupt) throws IOException {
        return TerminalBuilder.builder().signalHandler(signal -> {
            if (signal == Terminal.Signal.INT) {
                interrupt.run();
            }
        }).encoding(StandardCharsets.UTF_8).system(true).build();
    }

    /**
     * jline's builtin command registry: the console's line editor gets
     * ":command" support, bound to the shared configuration path.
     */
    public static void installSystemRegistry(final DefaultParser parser, final Terminal terminal, final ConfigurationPath configurations) {
        final Supplier<Path> currentDir = () -> Paths.get("");
        final Builtins builtins = new Builtins(currentDir, configurations, null);
        final SystemRegistry systemRegistry = new SystemRegistryImpl(parser, terminal, currentDir, configurations);
        systemRegistry.setCommandRegistries(builtins);
    }

    /**
     * The line reader the console reads at: the mtron parser dialect, shared
     * history, the serializer-aware highlighter, and the console's completer.
     */
    public static LineReader buildReader(final DefaultParser parser, final Terminal terminal, final Console console) {
        final Highlighter highlighter = new Highlighter(new ObjConsoleSerializer(), true);
        highlighter.setTerminal(terminal);
        Highlighter.single().setTerminal(terminal);
        return LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("metatron")
                .history(new DefaultHistory())
                .highlighter(highlighter)
                .parser(parser)
                .variable(LineReader.HISTORY_FILE, Console.HISTORY_FILE)
                .option(LineReader.Option.AUTO_FRESH_LINE, true)
                .option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .option(LineReader.Option.MOUSE, false)
                .variable(LineReader.SECONDARY_PROMPT_PATTERN, Graphitty.string("{{-X&v1&^1&m}}     {{g}}| {{X}}"))
                .variable(LineReader.INDENTATION, 0)
                .completer(new mCompleter(console))
                .build();
    }

    /**
     * Repaint the prompt and the live input line in one frame — widgets and
     * traces call this whenever they render mid-line, so the line the user is
     * typing is not left stranded where it was.
     */
    public static void redrawBuffer(final Console console) {
        // In split mode, skip the newline - prompt() includes cursor positioning
        if (!console.isSplitMode()) {
            Graphitty.out(Console.getTerminal().output(), "\n");
        }
        Graphitty.out(Console.getTerminal().output(), console.prompt());
        Graphitty.out(Console.getTerminal().output(), redrawLine(console));
        Console.getTerminal().flush();
    }

    /**
     * The live input line as the user sees it while typing: syntax color and
     * nothing else.
     * <p>
     * This text is the user's OWN, so graphitty markup must stay literal --
     * resolving it here rewrites the line under the cursor mid-word (a name in
     * braces is swallowed as a rule, a tag they have not closed yet changes
     * what they already typed, and {{XX}} clears the screen out from under
     * them).  `redrawBuffer` runs whenever a widget or a trace renders
     * mid-line, which is why ordinary typing feels like it is being rewritten.
     * <p>
     * The reader's highlighter is built with {@code ignoreGraphitty}, so
     * jline's own redraw of the same line and this one agree.  The markup
     * still applies to what they SUBMIT: the echo and the results go through
     * {@link Highlighter#format(Object)}.
     */
    public static String redrawLine(final Console console) {
        final String line = console.getReader().getBuffer().toString();
        final String ansi = Highlighter.line(line);
        if (Boolean.getBoolean("metatron.render.trace"))
            Console.rawErr().println("[line] in=<" + line + "> out=<" + ansi + ">");
        return ansi;
    }
}
