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

import org.jline.reader.LineReader;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The console's screen: the buffer that holds every row the console has
 * written, so the transcript can be repainted, restored and answered —
 * the output funnel that feeds it (the graphitty writer, the captured
 * stdout/stderr streams), the paint pass that turns it into rows on the
 * terminal, the link clicks resolved against it, and the repair of the
 * rows it shares with the floating widgets.
 * <p>
 * Whether the console owns its screen at all is {@link Console#screenMode()}
 * (a terminal capability decision), and the screen's own rows live on
 * {@link ConsoleScreen}.
 */
public final class ScreenView {

    /**
     * The clear-screen code the graphitty {@code {{XX}}} rewrite carries.
     */
    private static final String CLEAR_SCREEN = "\033[2J";

    private final ConsoleScreen screen = new ConsoleScreen();

    /**
     * True while a screen paint is on its way to the render thread, so a burst of
     * writes paints once rather than per line.
     */
    private final AtomicBoolean screenPaintRunning = new AtomicBoolean(false);

    /**
     * True when something changed that a paint would show, and that paint has not
     * happened yet — a request that arrives while a pass is running must not be dropped,
     * or the rows it was about (a widget's erased box) stay unpainted until the next
     * prompt.  The running pass re-runs itself while this is set.
     */
    private final AtomicBoolean screenPaintWanted = new AtomicBoolean(false);

    /**
     * True while the console's output is still being appended to the terminal rather than
     * painted (see {@link #screenAppends()}), so the first painted frame knows it must
     * repaint the whole region instead of trusting what is on screen.
     */
    private volatile boolean screenAppended = false;

    /**
     * The terminal row the console's own output starts on, or -1 while that is unknown.
     *
     * <p>Everything the console paints is positioned against it, and a click is resolved by it
     * (terminal row − this = the console's own row), so it is the one fact the screen cannot
     * work without.  Probing the terminal for it is the only way to learn it, and doing so
     * waits for input on a terminal that does not answer, so it is left unknown (-1) and the
     * console appends its output instead until it owns the screen.
     */
    private volatile int screenStart = declaredStartRow();

    /**
     * The row the console's own output starts on, from the launcher's banner count when it gives
     * one ({@code -Dmetatron.console.bannerRows=N} — the rows it printed before starting the VM),
     * or -1 when it does not, which leaves the console appending below whatever is there.
     */
    private static int declaredStartRow() {
        final int banner = Integer.getInteger("metatron.console.bannerRows", -1);
        return banner >= 0 ? banner + 1 : -1;
    }

    private final Console console;

    public ScreenView(final Console console) {
        this.console = console;
    }

    public ConsoleScreen screen() {
        return this.screen;
    }

    /**
     * The console owns the screen from its first frame, so it starts with a
     * clean one: whatever was printed above (a launcher's banner, a shell
     * prompt) is cleared and the console's own banner begins on the first
     * row.  That row is also what a click is resolved against, which is why
     * the console cannot simply append below it.
     */
    void beginAtTop() {
        this.screenStart = 1;
        final org.jline.terminal.Terminal terminal = this.console.getTerminal();
        terminal.writer().print("\033[2J\033[H");
        terminal.writer().flush();
    }

    /**
     * A new prompt: invalidate the region (jline moved the terminal while it
     * made room for the prompt and its status line, so every row the painter
     * believed it knew has moved) and land on the live end — a reader who
     * wheeled back into history gets the newest rows again as soon as they
     * are given something to type at, which is what a terminal's
     * scroll-to-bottom-on-input does, and without it the command they are
     * about to run would print off-screen.
     */
    void resetForNewPrompt() {
        this.screen.invalidate();
        this.screen.scrollToTail();
    }

    /**
     * The row the prompt sits on: the bottom of the terminal until the transcript is tall
     * enough to fill the screen, and below the transcript until then.
     *
     * <p>Pinning it at the bottom from the first prompt is what leaves a screen of blank
     * space above a short transcript — the banner would sit at the top with twenty empty
     * rows between it and the prompt, and the boot text a reader was still reading is
     * pushed out of view.  Letting the prompt follow the transcript instead means the
     * transcript starts at the top row (which is where the boot text already is), the
     * prompt stays immediately below the newest line, and the moment the transcript fills
     * the screen the prompt arrives at the bottom and stays there.
     *
     * <p>The last row is not available to it: jline's {@code Status} owns the bottom row.
     */
    private int screenPromptRow() {
        final int bottom = Math.max(2, Console.getTerminal().getHeight() - 1);
        final int top = this.screenStart > 0 ? this.screenStart : 1;
        final int transcript = this.screen.size();
        return Math.min(bottom, Math.max(Math.min(2, top), top + transcript));
    }

    /**
     * Point the console's output funnel at the screen, so every row the console
     * writes is a row the console holds a copy of.
     *
     * <p>{@code Graphitty.setTerminalWriter} is the funnel every console write
     * already goes through — results ({@code Console.write}), the {@code print}
     * instruction, log lines, agent replies — because {@code Graphitty.out} hands
     * the resolved text to it rather than to the terminal.  Routing individual call
     * sites instead leaves the rest writing rows the screen never saw, and a row the
     * screen never saw is a row nothing can put back: it scrolls the terminal away
     * and is gone (which is exactly how {@code print} output behaved until the
     * funnel pointed here).
     */
    void installScreenWriter() {
        if (!Console.screenMode()) return;
        Graphitty.setTerminalWriter(this::screenOutput);
    }

    /**
     * True while the console is still filling the terminal, so its output is appended the
     * ordinary way instead of painted over rows.
     *
     * <p>The console does not start on a blank terminal: a launcher prints a banner, the
     * boot logs print theirs, and a shell may have left whatever it liked above that.
     * Those rows are not the console's to paint over — and it cannot ask where they end
     * (the cursor-position report is a blocking read with no timeout, which would hang
     * startup on a terminal that does not answer).  So output is appended, the way any
     * terminal program appends it, until the console has printed more than a screenful:
     * by then its own content has scrolled everything before it away, the rows belong to
     * the console, and painting them is what keeps them restorable.
     */
    public boolean screenAppends() {
        if (!Console.screenMode()) return false;
        // The console's first row is known, so painting from it is safe at any length: a launcher
        // that printed a banner says how many rows it took (-Dmetatron.console.bannerRows), and
        // the console's output then starts on the line after it — the banner stays readable AND
        // its own rows are addressable, which is what a click needs.
        if (this.screenStart > 0) return false;
        // Unknown, and it CANNOT be found out: the only way to ask a terminal where its cursor is
        // is jline's cursor-position report, whose read blocks on a terminal that does not answer
        // (measured — it hangs startup).  So the console appends, the way any terminal program
        // does: the launcher's output is left alone, and the cost is that its own rows are not
        // addressable — a click cannot be resolved to one — until it has filled the screen and
        // taken the rows over.
        return true;
    }

    /**
     * Wipe the transcript — the display and the screen's model of it, together.
     * <p>
     * A raw clear-screen escape is a wipe the screen does not know about: its rows are still
     * recorded, so the painter has nothing to correct, the frame stays empty, and the console is
     * left describing a screen that is no longer there.  A link that is no longer drawn is then a
     * link that no longer answers, which is why clicking stopped working after {@code :clear}.
     * Emptying the screen and invalidating it makes the next frame paint the (now empty)
     * transcript, and the pointer is re-synced to what is actually on screen.
     */
    public void clearTranscript() {
        if (!Console.screenMode()) return;
        Graphitty.out(Console.getTerminal().output(), CLEAR_SCREEN);
        this.screen.clear();
        this.screen.invalidate();
        this.requestScreenPaint();
        this.console.syncWidgetMouseTracking();
    }

    /**
     * Where every console write lands: recorded in the screen, and — while the console is
     * still filling the terminal — also appended to it (see {@link #screenAppends()}).
     *
     * @param ansi the text, already resolved
     */
    void screenOutput(final String ansi) {
        if (null == ansi || ansi.isEmpty()) return;
        Console.linkTrace("out len=%d osc8=%b linkMarkup=%b | %s", ansi.length(), ansi.contains("\033]8;"),
                ansi.contains("{{link}}"), Graphitty.strip(ansi).replace("\n", "\\n"));
        // A clear-screen code ({{XX}} — and so :clear, and any print of it) is an ACTION, not
        // content: the terminal is cleared AND the screen's own rows are dropped with it.
        // Clearing only the terminal left every row in the buffer, so the next frame — or a
        // widget moving over those rows — painted the text straight back, which is what :clear
        // used to do.
        if (ansi.contains(CLEAR_SCREEN)) {
            final String rest = ansi.replace(CLEAR_SCREEN, "");
            this.screen.clear();
            this.screen.invalidate();
            this.screenStart = 1;          // cleared: the console's rows start at the top again
            this.screenAppended = false;
            this.console.getFloatingSurface().writeToTerminal(CLEAR_SCREEN + "\033[H");
            if (rest.isEmpty()) this.requestScreenPaint();
            else this.screenOutput(rest);
            return;
        }
        this.screen.appendAnsi(ansi);
        if (this.screenAppends()) {
            this.screenAppended = true;
            // Plain text, without the hyperlink: the console is not yet handling clicks on
            // these rows (it does not know which terminal rows they are), and a terminal that
            // is handed a hyperlink takes the click for itself — which is how a link ended up
            // opening the desktop's "Unsupported operation" instead of being followed here.
            this.console.getFloatingSurface().writeToTerminal(ScreenPainter.withoutLinks(ansi));
            return;
        }
        if (this.screenAppended) {
            // the console owns the rows now: what it appended has moved (the terminal
            // scrolled), so the region is repainted from the buffer rather than trusting
            // what happens to be on screen
            this.screenAppended = false;
            this.screen.invalidate();
        }
        this.requestScreenPaint();
    }

    /**
     * Take the process's stdout for the screen, so text that never went through a
     * routed writer still lands somewhere restorable.
     *
     * <p>The funnel above catches everything written through {@code Graphitty}, but
     * that is not everything a console prints: {@code print} reaches the terminal
     * through {@code GraphittyLogger.none}'s {@code System.out} fallback, Logback's
     * appender holds the stream it started with, and a library may write to stdout
     * on its own.  An uncaptured row is a row that scrolls the terminal and is gone,
     * which is exactly what happened to {@code print} output before this existed.
     *
     * <p>This must run <em>after</em> the terminal is built: jline takes the real
     * stdout when it is constructed, so replacing {@code System.out} afterwards
     * leaves the terminal's own writes on their original stream — the screen paints
     * through jline, and capturing that would be a loop.
     */
    void installStdoutCapture() {
        if (!Console.screenMode()) return;
        final ScreenOutputStream sink = new ScreenOutputStream(this::screenOutput);
        System.setOut(new java.io.PrintStream(sink, true, java.nio.charset.StandardCharsets.UTF_8));
        // stderr too, and not as an afterthought: logging is configured to write there
        // (conf/logback.xml targets System.err, with the Graphitty layout), so a console
        // that captured only stdout left every log line going to the terminal at the
        // cursor — where the screen's next frame painted over it, and the output looked
        // as if it had stopped.  Logback's console target forwards to System.err on each
        // write, so replacing it here is enough to bring the log into the transcript.
        System.setErr(new java.io.PrintStream(
                new ScreenOutputStream(this::screenOutput), true, java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Paint the screen soon, without blocking the caller.
     *
     * <p>The funnel runs on whatever thread wrote — a job thread streaming output —
     * so it must not wait on the render thread per write.  Requests coalesce: a
     * burst of writes paints on the last frame rather than once per line.
     */
    void requestScreenPaint() {
        if (!Console.screenMode() || this.screenAppends()) return;
        // Never drop a request: note that a paint is wanted, and let whoever is already
        // painting see it.  A dropped request is a row that stays blank until the next
        // prompt — which is exactly how a widget's erased box survived a quick drag.
        this.screenPaintWanted.set(true);
        if (!this.screenPaintRunning.compareAndSet(false, true)) return;
        this.console.getFloatingSurface().writeAndRender(() -> {
            final StringBuilder painted = new StringBuilder();
            try {
                // paint until nothing new arrives mid-pass: the rows are produced here, on
                // the render thread, so every forget that has happened by now is included
                do {
                    this.screenPaintWanted.set(false);
                    painted.append(this.screenFrame());
                } while (this.screenPaintWanted.get());
            } finally {
                this.screenPaintRunning.set(false);
                // a request that arrived between the last check and the flag clearing has
                // no pass to join, so it gets one (this is bounded: the retry either paints
                // or coalesces with a pass that is already running)
                if (this.screenPaintWanted.get()) this.requestScreenPaint();
            }
            return painted.toString();
        });
    }

    /**
     * Lay the region out and return the bytes that bring it up to date (see
     * {@link ConsoleScreen#frame()}), with the cursor handed back to the prompt row.
     */
    String screenFrame() {
        // while the console is still appending there is nothing to paint: the text is
        // already on the terminal, below everything that was there before it
        if (this.screenAppends()) return "";
        final int promptRow = this.screenPromptRow();
        final int top = this.screenStart > 0 ? this.screenStart : 1;
        this.screen.layout(top, promptRow - top, Console.getTerminal().getWidth());
        // The pointer follows the links.  A uri arrives with a job's output — AFTER the prompt was
        // drawn, which is when the arming decision was taken, with nothing on screen to click yet.
        // Re-checking here, on the paint that put the link there, is what lets a freshly painted
        // uri answer a click instead of leaving the mouse with the terminal until the next prompt.
        // Writing the mode inside the pass is safe: it moves no cursor, and the frame is written
        // after it.
        this.console.syncWidgetMouseTracking();
        final String frame = this.screen.frame();
        if (this.console.inReadLine()) {
            // Mid-prompt the cursor is jline's, and jline draws from where it believes the
            // cursor is: moving it here would leave jline's bookkeeping wrong, and the next
            // keystroke would be written at a column that no longer matches what is on
            // screen (characters landing on top of each other at the start of the prompt
            // line).  So the rows are painted with the cursor saved and put back exactly
            // where it was — the same save/restore a widget pass uses.
            return frame.isEmpty() ? "" : "\033[s" + frame + "\033[u";
        }
        // Between prompts the console may place it: the next prompt is drawn from there.
        return frame + "\033[" + promptRow + ";1H";
    }

    /**
     * Paint the screen region from the console's own buffer, then let the floating
     * widgets redraw over it.
     *
     * <p>The frame goes through {@code FloatingSurface.writeToTerminal} —
     * the console's single writer — so a screen frame can never interleave with a
     * widget frame.  Painting the region is also what puts back text a widget
     * covered: the widget erased its old box with blanks, and nothing in the
     * terminal remembers what was there, but the buffer does.
     *
     * <p>The cursor is handed back to the prompt row afterwards, because that row
     * belongs to jline: it draws the prompt there, and moves the cursor as the user
     * types.
     *
     * @param deferIfTyping when true (background output), a repaint is deferred
     *                      while the user is actively typing — the same deferral
     *                      pane rendering uses, and for the same reason: yanking
     *                      the cursor out from under a half-typed line
     */
    void renderScreen(final boolean deferIfTyping) {
        if (!Console.screenMode()) return;
        if (deferIfTyping && this.console.deferNonActivePaneRender()) return;
        // while the console is appending, the prompt the reader is looking at is jline's
        // and it is already exactly where the cursor is: nothing to paint, nothing to
        // move — the widgets are still drawn (they float wherever they were pinned)
        if (this.screenAppends()) {
            this.console.getFloatingSurface().render();
            return;
        }
        // screenFrame() lays the region out and knows whether the console may place the
        // cursor (between prompts) or must put it back where it was (mid-prompt)
        this.console.getFloatingSurface().writeToTerminal(this.screenFrame());
        this.console.getFloatingSurface().render();
    }

    /**
     * A render on the spot: paint the screen region now (see {@link #renderScreen}) — used
     * where the caller needs the paint to have happened, not merely requested.
     */
    public void renderScreen() {
        this.renderScreen(true);
    }

    /**
     * Scroll the console's own transcript by {@code delta} rows (negative goes back into
     * history, positive towards the newest) and repaint it.
     *
     * <p>The wheel over the transcript lands here rather than handing the pointer back to
     * the terminal: the rows are the console's now, so a terminal scrollback is not where
     * this transcript lives, and releasing the pointer cost the widgets the mouse (a click
     * on one then needed {@code alt}+{@code w} to get it back).  A wheel that reaches the
     * tail again resumes following, so scrolling back down needs no separate gesture.
     */
    public void scrollScreen(final int delta) {
        if (!Console.screenMode()) return;
        this.screen.scrollBy(delta);
        // the window moved under the rows, so what is on screen is no longer known
        this.screen.invalidate();
        this.repaintScreen();
    }

    /**
     * Repaint the screen region now, without blocking the caller's interest in the result.
     */
    void repaintScreen() {
        this.console.getFloatingSurface().writeAndRender(this::screenFrame);
    }

    /**
     * A click on a link in the transcript: type the dereference of its uri at the prompt,
     * leaving the reader one &lt;enter&gt; from the resource.
     *
     * <p>The uri is not dereferenced for the reader, and that is the point: in metatron a uri
     * IS an expression, so typing {@code *<uri>} puts the reader in charge of the resource —
     * they can edit it, extend it, or walk somewhere else from it — while still being one
     * keystroke from following the link.  It also means a link cannot run something the
     * reader did not see.
     *
     * @param row 1-based terminal row of the click
     * @param col 1-based terminal column of the click
     * @return true when a link was there and its uri is now in the prompt
     */
    boolean openScreenLink(final int row, final int col, final boolean follow) {
        // Resolving a click needs the console's own rows: while it is appending (opt-in), a
        // terminal row is not a buffer row and the pointer stays the terminal's.
        if (!Console.screenMode() || this.screenAppends() || !this.console.inReadLine()) {
            Console.linkTrace("  refused: screen=%b appends=%b inReadLine=%b",
                    Console.screenMode(), this.screenAppends(), this.console.inReadLine());
            return false;
        }
        // One click, one gesture: type the dereference and let the reader decide, or — with control
        // held — submit it as well.  Nothing is remembered between clicks, so a repaint that moves
        // the row cannot make the next click answer for the last one.
        //
        // The clicked row is the only row that can answer: a terminal row maps onto the screen's
        // rows by their exact difference (row − screen.top()), and the row a uri is painted on is
        // the row it sits on.  Answering a row above or below is how a click once opened a link
        // the reader was looking past, and the row model it was added to absorb has been exact
        // since the screen's top row is declared (a launcher's banner rows) or taken over (a
        // cleared terminal).  A miss on the exact row is refused — the trace says so.
        final String target = this.linkAtRow(row, col);
        if (null == target) {
            Console.linkTrace("  no link at row %d (col %d, top %d, rows %d)",
                    row, col, this.screen.top(), this.screen.rows());
            return false;
        }
        Console.linkTrace("  resolved '%s' at row %d (col %d, follow=%b)", target, row, col, follow);
        final String expression = "*" + target;
        if (follow) {
            // follow it: submit what the click resolved, with no keystroke in between.  The uri is
            // asserted rather than appended — a click may already have typed it, and appending
            // would submit it twice.
            if (!expression.equals(this.console.getReader().getBuffer().toString())) {
                this.console.getReader().getBuffer().clear();
                this.console.getReader().getBuffer().write(expression);
            }
            // through Widgets, the way the console's own bindings submit a line
            this.console.getWidgets().callWidget("accept-line");
            return true;
        }
        this.console.getReader().getBuffer().write(expression);
        // the reader thread owns the line: ask jline to draw what was typed
        this.console.getReader().callWidget(LineReader.REDISPLAY);
        return true;
    }

    /**
     * The uri a link at this terminal row and column resolves to, or null when that row has none.
     */
    private String linkAtRow(final int row, final int col) {
        final int index = row - this.screen.top();
        if (index < 0 || index >= this.screen.rows()) return null;
        final java.util.List<String> window = this.screen.visible();
        return index < window.size() ? ScreenPainter.linkAt(window.get(index), col - 1) : null;
    }

    /**
     * Put back the rows a widget erased, then draw the widgets back over them.
     *
     * <p>The surface reports the rows it blanked — a moved widget's old box, a removed
     * widget's box, a resized box's leftovers — because it cannot know what those rows
     * held.  The screen can: it keeps the content, so the rows are <em>forgotten</em>
     * there, which obliges the next frame to paint them, and a paint is asked for.  No
     * repair is ever lost that way, where queued repair bytes could be coalesced away
     * along with the pass that needed them.
     *
     * @param from 1-based first terminal row erased
     * @param to   1-based last terminal row erased
     */
    void repairRows(final int from, final int to) {
        if (!Console.screenMode()) return;
        this.screen.forget(from, to);
        this.requestScreenPaint();
    }

    /**
     * Make the screen the truth again after something drew outside it.
     * <p>
     * The screen paints rows at absolute positions and keeps its own idea of what is on each one;
     * anything written straight to the terminal — an answer echoed while a job held the console —
     * leaves that idea wrong, and the next paint then fights the write that follows it.
     * <p>
     * Only the forgetting happens here.  The paint pass runs on whoever asks, and this is called
     * from the reader: a frame drawn there waits on the render lock while holding the reader,
     * which is a console that stops responding until the terminal gives up on it.  Marking the
     * rows unknown is enough — the next output or prompt repaints them, and repainting stale rows
     * is exactly what invalidating prevents.
     */
    void resyncScreen() {
        if (!Console.screenMode()) return;
        this.screen.invalidate();
    }
}
