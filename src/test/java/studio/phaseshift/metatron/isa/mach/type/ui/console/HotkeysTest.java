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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Keys typed while a foreground job holds the console.  The repl thread is
 * parked on the job's future — jline is not reading — so the console's watcher
 * thread reads the terminal and classifies every keystroke: <alt>+b backgrounds
 * the job, [q] cancels it once the cancel offer stands, and printable text is
 * kept for the next prompt so typing ahead of a long job is never lost.
 *
 * The classifier is a pure character state machine — a terminal read with a
 * timeout blocks on a pty-backed jline reader, so the state spans keystrokes
 * and the test feeds characters instead of a terminal.
 *
 * Escape sequences are written into the CSV as readable escapes
 * (\e = ESC, \d = DEL, \b = backspace) and decoded by the test.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class HotkeysTest extends AbstractMetatronTest {

    /*
     * one row per keystroke stream: what the classifier must make of it
     */
    @ParameterizedTest
    @Timeout(60)
    @CsvSource(value = {
            "\\eb      % false % DETACH % ''     % alt+b backgrounds whatever holds the console",
            "abc\\eb  % false % DETACH % abc    % text typed before the combo is kept for the next prompt",
            "\\eb      % true  % DETACH % ''     % detach beats an armed cancel key",
            "\\e[A     % false % NONE   % ''     % the up-arrow sequence is swallowed, not typed onto the next line",
            "\\e      % false % NONE   % ''     % a bare ESC is held as the start of a combo across reads",
            "\\ex      % false % NONE   % ''     % an unsupported alt-combo is swallowed",
            "hello    % false % TEXT   % hello  % plain typing is kept",
            "ab\\d    % false % NONE   % a      % DEL edits the kept text",
            "ab\\b    % false % NONE   % a      % backspace edits the kept text",
            "q        % false % TEXT   % q      % q is a plain character while no cancel offer stands",
            "q        % true  % CANCEL % ''     % q cancels once the cancel offer stands",
            "a\\r\\t    % false % NONE   % a      % enter and tab are dropped, never queued",
            "\\c        % false % INTERRUPT % ''     % ctrl-c still stops the job when it arrives as a byte",
    }, delimiter = '%')
    void keystrokesAreClassifiedWhileTheConsoleIsBusy(final String input, final boolean armed,
                                                      final String verdict, final String pending,
                                                      final String desc) {
        final Hotkeys hotkeys = new Hotkeys();
        if (armed)
            hotkeys.armCancel();

        Hotkeys.Verdict last = Hotkeys.Verdict.NONE;
        for (final char c : decode(input).toCharArray())
            last = hotkeys.accept(c);

        assertEquals(Hotkeys.Verdict.valueOf(verdict), last, "last keystroke verdict — " + desc);
        assertEquals(pending, hotkeys.takePendingText(), "kept text — " + desc);
    }

    /*
     * an escape sequence must be swallowed whole: a half-eaten one would land
     * in the next line as literal text (the classic "OA" left in the prompt)
     */
    @ParameterizedTest
    @Timeout(60)
    @CsvSource(value = {
            "\\eOA % NONE % ''  % false % an ss3 arrow (application cursor keys) is swallowed whole",
            "\\e[A % NONE % ''  % false % a csi arrow is swallowed whole",
            "\\e   % NONE % ''  % true  % a bare esc leaves the scanner mid-sequence",
            "\\ex  % NONE % ''  % false % an unsupported alt-combo closes the sequence",
            "\\eOp % NONE % ''  % false % an ss3 function key is swallowed whole",
    }, delimiter = '%')
    void escapeSequencesAreSwallowedWhole(final String input, final String verdict, final String pending,
                                          final boolean inSequence, final String desc) {
        final Hotkeys hotkeys = new Hotkeys();
        Hotkeys.Verdict last = Hotkeys.Verdict.NONE;
        for (final char c : decode(input).toCharArray())
            last = hotkeys.accept(c);

        assertEquals(Hotkeys.Verdict.valueOf(verdict), last, "last keystroke verdict — " + desc);
        assertEquals(pending, hotkeys.takePendingText(), "kept text — " + desc);
        assertEquals(inSequence, hotkeys.inSequence(), "mid-sequence — " + desc);
    }

    /*
     * a read that expires (nothing typed) ends a half-seen sequence: a stale
     * escape must never swallow the next keystroke as its partner
     */
    @Test
    @Timeout(60)
    void expiredReadDropsAHalfSeenSequence() {
        final Hotkeys hotkeys = new Hotkeys();
        assertEquals(Hotkeys.Verdict.NONE, hotkeys.accept(0x1b), "esc alone opens a sequence");
        assertEquals(true, hotkeys.inSequence(), "the scanner waits for the partner");

        hotkeys.reset();  // what the watcher does when a read expires

        assertEquals(false, hotkeys.inSequence(), "an expired read closes the sequence");
        assertEquals(Hotkeys.Verdict.TEXT, hotkeys.accept('b'),
                "the next keystroke is plain text, not the second half of alt+b");
        assertEquals("b", hotkeys.takePendingText(), "and it is kept for the prompt");
    }

    /*
     * keystrokes survive as text only until the prompt claims them
     */
    @Test
    @Timeout(60)
    void pendingTextIsHandedToThePromptOnce() {
        final Hotkeys hotkeys = new Hotkeys();
        hotkeys.accept('n');
        hotkeys.accept('e');
        hotkeys.accept('x');
        hotkeys.accept('t');

        assertEquals("next", hotkeys.takePendingText());
        assertEquals("", hotkeys.takePendingText());
    }

    /*
     * the poll loop's decision table: detach beats cancel, a widget on screen
     * suppresses the cancel offer, the offer waits out its interval
     */
    @ParameterizedTest
    @Timeout(60)
    @CsvSource(value = {
            "true  % false % false % false % 0     % 0     % DETACH        % alt+b detaches immediately",
            "true  % true  % true  % false % 0     % 0     % DETACH        % detach wins over everything",
            "false % true  % false % false % 0     % 0     % INTERRUPT     % ctrl-c stops the job",
            "false % true  % true  % false % 0     % 0     % INTERRUPT     % ctrl-c beats an armed cancel key",
            "false % false % true  % false % 0     % 0     % CANCEL        % an armed cancel key cancels",
            "false % false % false % false % 0     % 0     % OFFER_CANCEL  % a long job is offered a cancel",
            "false % false % false % false % 9999  % 10000 % WAIT          % no offer before the interval is up",
            "false % false % false % false % 10000 % 10000 % OFFER_CANCEL  % the offer lands exactly on the interval",
            "false % false % false % true  % 10000 % 10000 % WAIT          % a widget owning the console suppresses the offer",
            "false % false % false % true  % 99999 % 0     % WAIT          % a widget keeps suppressing the offer",
    }, delimiter = '%')
    void foregroundStepTable(final boolean detach, final boolean interrupt, final boolean cancel,
                             final boolean userMode, final long nowMs, final long offerAtMs,
                             final String expected, final String desc) {
        assertEquals(Console.ForegroundStep.valueOf(expected),
                Console.foregroundStep(detach, interrupt, cancel, userMode, nowMs, offerAtMs),
                "next foreground step — " + desc);
    }

    /*
     * the detach banner echoes the line that started the job, clipped to one line
     */
    @Test
    @Timeout(60)
    void detachBannerEchoesOneClippedLine() {
        assertEquals("short line", Console.preview("short line"));
        assertEquals("one two", Console.preview("one\ntwo"));
        assertEquals("x".repeat(45) + "...", Console.preview("x".repeat(64)));
        assertEquals(48, Console.preview("x".repeat(200)).length());
    }

    /** readable escapes in the CSV stand in for the terminal's control bytes */
    private static String decode(final String escaped) {
        final StringBuilder decoded = new StringBuilder();
        for (int i = 0; i < escaped.length(); i++) {
            final char c = escaped.charAt(i);
            if (c != '\\' || i + 1 >= escaped.length()) {
                decoded.append(c);
                continue;
            }
            final char next = escaped.charAt(++i);
            decoded.append(switch (next) {
                case 'e' -> (char) 0x1b;
                case 'c' -> (char) 0x03;
                case 'd' -> (char) 0x7f;
                case 'b' -> (char) 0x08;
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                default -> next;
            });
        }
        return decoded.toString();
    }
}
