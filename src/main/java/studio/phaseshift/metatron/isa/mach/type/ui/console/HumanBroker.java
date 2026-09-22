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

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.util.MTronException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * The console's one way of being asked for a line by anyone other than its own
 * read loop: a job that wants a line cannot read the terminal itself (the
 * console owns it), so it queues a request and blocks — and the request is
 * answered either by the read loop at the prompt or by the watcher while a
 * foreground job holds the terminal.  One reader, one terminal.
 */
public final class HumanBroker {

    /**
     * A line the console is asked for by someone other than its own read loop
     * (see {@link #readHumanLine}).
     */
    public record HumanRead(String prompt, CompletableFuture<String> answer) {
    }

    private static final int DEL = 0x7f;

    private final Console console;

    /**
     * Lines waiting to be taken by the console's read loop, oldest first.
     */
    private final BlockingQueue<HumanRead> humanReads = new LinkedBlockingQueue<>();

    /**
     * The line being typed as an answer to a job's question (see {@link #answer(int)}).
     */
    private final StringBuilder humanAnswer = new StringBuilder();

    public HumanBroker(final Console console) {
        this.console = console;
    }

    /**
     * Ask the human for a line of input, through the console's own reader.
     * <p>
     * A job that wants a line cannot read the terminal itself: the console owns it — it holds the
     * tty for keys and mouse, and while a prompt is up its reader consumes whatever is typed, so a
     * second reader on {@code System.in} sees nothing and what the human types is taken as a repl
     * command instead of an answer.  So the request is queued and answered on the console's side:
     * by the read loop when the console is at a prompt, and by the watcher while a foreground job
     * holds it (that thread is the only reader of the terminal in that window, see
     * {@link ForegroundJobs#watchTerminal()}).  One reader, one terminal.
     * <p>
     * Falls back to {@code System.in} where there is no console at all (headless boots, tests).
     *
     * @param prompt what the human is being asked for (markup is resolved)
     * @return the line they typed, without its newline
     */
    public static String readHumanLine(final String prompt) {
        final Console console = Console.LOCAL_INSTANCE;
        if (null == console || null == console.getReader() || BootLoader.TESTING)
            return new java.util.Scanner(System.in).nextLine();
        final HumanRead request = console.human().post(prompt);
        if (console.inReadLine())
            // break the console out of the prompt it is showing so its reader thread comes back
            // around and takes this request: accept-line is the widget the console's own bindings
            // submit with
            try {
                console.getWidgets().callWidget("accept-line");
            } catch (final Exception ignore) {
                // no prompt to break out of: the loop takes the request at its next pass
            }
        // While a foreground job holds the console the watcher is the only reader of the terminal:
        // reading here as well would put two readers on one stream — each taking a share of the
        // bytes, so the job would get a stray character while the person's typing went to the
        // type-ahead buffer, and <enter> would be classified as no key at all.
        try {
            return request.answer().get();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    /**
     * Queue a new request and hand it back — the ask side of the protocol
     * (the console's read loop answers it via {@link #poll()}).
     */
    public HumanRead post(final String prompt) {
        final HumanRead request = new HumanRead(prompt, new CompletableFuture<>());
        this.humanReads.add(request);
        return request;
    }

    /**
     * Take the oldest outstanding request — the read loop's way of seeing
     * that a job is asking for a line.
     */
    public HumanRead poll() {
        return this.humanReads.poll();
    }

    /**
     * Take one keystroke as the human's answer to the job that is asking, when one is.
     * <p>
     * Served here because the watcher thread is the only reader of the terminal while a
     * foreground job holds the console: the answer is echoed and edited like a line, and
     * {@code <enter>} releases the job waiting on it.  Keys are consumed only while a
     * question is outstanding — otherwise this is a plain keystroke and the hotkeys decide
     * what it means.
     *
     * @return true when the keystroke was consumed as part of an answer
     */
    public boolean answer(final int c) {
        final HumanRead asked = this.humanReads.peek();
        if (null == asked) return false;
        if ('\r' == c || '\n' == c) {
            final String answer = this.humanAnswer.toString();
            this.humanAnswer.setLength(0);
            this.humanReads.poll();
            this.console.echoTypedChar('\n');
            asked.answer().complete(answer);
            // The answer was echoed straight to the terminal (echoTypedChar), not into the screen,
            // and the job's own output kept painting absolute rows in the meantime: the two do not
            // agree about where the cursor is or which rows are current.  Reconcile before anything
            // else draws, or every later paint corrects a layout the next write undoes — which reads
            // as a transcript that creeps up a line and back down.
            this.console.resyncScreen();
            return true;
        }
        if (DEL == c || 0x08 == c) {
            if (this.humanAnswer.length() > 0) {
                this.humanAnswer.setLength(this.humanAnswer.length() - 1);
                try {
                    this.console.getTerminal().writer().print("\b \b");
                    this.console.getTerminal().writer().flush();
                } catch (final Exception ignore) {
                    // echo is a courtesy
                }
            }
            return true;
        }
        if (c >= 32) {
            this.humanAnswer.append((char) c);
            this.console.echoTypedChar(c);
        }
        return true;
    }

}
