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

/*
 * Keystrokes that arrive while the console is busy.
 *
 * While a foreground job holds the console, the repl thread is parked on the
 * job's future — not inside {@code readLine()} — so jline is not reading and
 * nothing is consuming the terminal.  The console's watcher thread reads the
 * keystrokes instead and hands each one here, one character at a time, to be
 * classified:
 *
 *   alt+b   detach — hand the running job to the background
 *   [q]     cancel — the cancel-stream key, honored only once the offer is armed
 *   text    a printable character, kept as the next prompt's seed so typing
 *           ahead of a long job is never lost
 *
 * Classification is a pure character state machine — no reader, no terminal —
 * because reading a terminal with a timeout is not portable: a pty-backed
 * jline reader blocks in its native read whatever timeout it is handed.  The
 * state therefore spans keystrokes: ESC may arrive in its own read.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Hotkeys {

    /**
     * The detach combo — {@code alt+b} (ESC b), one keypress on every tty.
     * jline's emacs keymap owns {@code \eb} (backward-word) while the prompt is
     * live, so this combo is deliberately a busy-state key: it is classified
     * here, never bound on a keymap.
     */
    public static final String DETACH_COMBO = "alt+b";

    /** the cancel-stream key offered while a long-running job holds the console */
    public static final char CANCEL_KEY = 'q';

    private static final int ESC = 0x1b;
    private static final int DEL = 0x7f;
    private static final int BACKSPACE = 0x08;
    private static final int ETX = 0x03;

    /** what one keystroke means to a console that is busy running a job */
    public enum Verdict {NONE, TEXT, DETACH, INTERRUPT, CANCEL}

    private final StringBuilder pending = new StringBuilder();
    private State state = State.NORMAL;
    private boolean cancelArmed = false;

    private enum State {NORMAL, ESC, CSI}

    /** arm the cancel key — the console prints the cancel offer first */
    public synchronized void armCancel() {
        this.cancelArmed = true;
    }

    /** disarm the cancel key — the offer was answered or retracted */
    public synchronized void disarmCancel() {
        this.cancelArmed = false;
    }

    /** @return true when the cancel offer is outstanding */
    public synchronized boolean cancelArmed() {
        return this.cancelArmed;
    }

    /**
     * @return true while an escape sequence has only been half read — the
     * watcher finishes such a sequence even after the job ends, so its tail can
     * never reach the next line as literal text
     */
    public synchronized boolean inSequence() {
        return State.NORMAL != this.state;
    }

    /**
     * Drop a half-read escape sequence: its partner never arrived.  A dropped
     * escape beats a stale one, which would swallow the next keystroke.
     */
    public synchronized void reset() {
        this.state = State.NORMAL;
    }

    /** @return the text typed while the console was busy, and clear it */
    public synchronized String takePendingText() {
        final String text = this.pending.toString();
        this.pending.setLength(0);
        return text;
    }

    /**
     * Classify one keystroke.  Escape sequences other than {@code alt+b}
     * (arrows, alt+letter, function keys) resolve to {@link Verdict#NONE}: their
     * bytes must leave the terminal so they cannot corrupt the next line, and
     * there is nothing to keep from them.
     *
     * @param c the character read from the terminal
     * @return what the keystroke means to the busy console
     */
    public synchronized Verdict accept(final int c) {
        switch (this.state) {
            case ESC -> {
                this.state = State.NORMAL;
                if (c == 'b')
                    return Verdict.DETACH;
                if (c == '[' || c == 'O')
                    this.state = State.CSI;  // arrow / function key / ss3
                return Verdict.NONE;         // any other alt-combo
            }
            case CSI -> {
                if (c >= 0x40 && c <= 0x7e)
                    this.state = State.NORMAL;  // sequence complete
                return Verdict.NONE;
            }
            default -> {
                if (c == ESC) {
                    this.state = State.ESC;
                    return Verdict.NONE;
                }
                if (c == CANCEL_KEY && this.cancelArmed) {
                    this.cancelArmed = false;
                    return Verdict.CANCEL;
                }
                if (c == ETX)
                    return Verdict.INTERRUPT;  // ctrl-c: stop the job, as sigint would
                if (c == DEL || c == BACKSPACE) {
                    if (!this.pending.isEmpty())
                        this.pending.deleteCharAt(this.pending.length() - 1);
                    return Verdict.NONE;
                }
                if (c < 0x20)
                    return Verdict.NONE;  // enter, tab, ctrl-keys: dropped, never queued
                this.pending.append((char) c);
                return Verdict.TEXT;
            }
        }
    }
}
