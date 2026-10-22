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

package studio.phaseshift.metatron.isa.mach.type.ui.tool;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Attributes;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import org.jline.utils.AttributedString;
import org.jline.utils.InfoCmp;
import studio.phaseshift.metatron.isa.mach.type.ui.Stylable;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.AbstractWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Utilities;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.WidgetCanvas;

import java.io.IOError;
import java.io.InterruptedIOException;
import java.util.Map;

/**
 * A TOOL — an interactive view that owns the terminal's rows while it runs.
 * <p>
 * Five tools had grown the same skeleton by copy: enter raw mode, install the keypad and hide the cursor, build a
 * {@link KeyMap}, read keys in a loop, paint through a {@link WidgetCanvas}, then restore the terminal — with two
 * behavioural divergences hiding in the copies (two tools never called {@code beginToolRun/endToolRun}, so their
 * frames could double on the console's screen; one swallowed ctrl-c's {@link IOError} and four did not). This class
 * is that skeleton, once.
 * <p>
 * <b>The lifecycle is {@code final}; the content is not.</b> A tool answers three questions — which keys mean what
 * ({@link #keyMap()}), what one frame looks like ({@link #redraw()}), and what to do with a key
 * ({@link #handle(Object)}) — and inherits the rest: raw mode, the loop, the console's tool-run bracket, the ctrl-c
 * path, and the terminal restore, which runs in a {@code finally} so an exception cannot leave a terminal in raw
 * mode.
 * <p>
 * <b>A tool is modal by default, and may instead present a panel.</b> {@link #modal()} returning false means "do
 * not take the keyboard": {@link #present()} runs and returns (a live panel that the console keeps usable, floating
 * if its style carries an anchor). {@link ModalTool} is modal even when it floats (it waits for a dismiss key);
 * {@code NetworkPanelTool} is a panel when anchored and a scroller when not.
 *
 * @param <W> the tool's own type (the fluent return of the widget contract)
 * @param <A> the tool's action vocabulary — its own enum, bound in {@link #keyMap()}
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractTool<W extends AbstractTool<W, A>, A> extends AbstractWidget<W> {

    /**
     * the bytes a terminal sends for ctrl-d, the universal "leave this tool" key
     */
    protected static final String CTRL_D = "\u0004";

    private Attributes savedAttributes;

    protected AbstractTool() {
        super();
    }

    protected AbstractTool(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }
    private BindingReader bindingReader;
    private int totalHeightUsed = 0;
    private boolean running = false;

    // ── what a tool answers ────────────────────────────────────────

    /**
     * The keys this tool understands. Bind the tool's own action constants; use
     * {@link #bindArrows(KeyMap, Object, Object, Object, Object)} for the arrow/enter vocabulary every tool shares.
     */
    protected abstract KeyMap<A> keyMap();

    /**
     * One frame. Draw through {@link #canvas()} and close the pass with {@link #settle(WidgetCanvas)} — the canvas
     * owns the absolute-vs-relative rendering and the stale-line bookkeeping, so the tool never does.
     */
    protected abstract void redraw();

    /**
     * Act on a key.
     *
     * @param action the action the pressed key maps to
     * @return true to keep the tool running, false to leave it
     */
    protected abstract boolean handle(A action);

    // ── what a tool may override ───────────────────────────────────

    /**
     * Whether the tool takes the keyboard. False means {@link #present()} is called instead of the key loop.
     */
    protected boolean modal() {
        return true;
    }

    /**
     * Present the tool passively — no raw mode, no loop — when {@link #modal()} is false, when there is no console,
     * or when the terminal cannot be a keyboard (see {@link #interactive(org.jline.terminal.Terminal)}). Default:
     * print the widget's format once (a script, a pipe, a headless run).
     */
    protected void present() {
        final org.jline.terminal.Terminal terminal = Console.getTerminal();
        if (null != terminal)
            Graphitty.out(terminal.output(), this.format() + "\n");
    }

    /**
     * Called once after raw mode is installed and before the first frame (bind the mouse here).
     */
    protected void onEnter() {
    }

    /**
     * Called once on the way out, before the terminal is restored — {@link #onEnter()} must undo itself here.
     */
    protected void onLeave() {
    }

    /**
     * The tool's last frame, settled into the console's transcript on exit as the screen hands the rows back.
     * Null leaves the transcript as it was.
     */
    protected String finalFrame() {
        return null;
    }

    /**
     * Whether the tool still wants to run; a tool whose stop condition lives in its rec can answer from there, so
     * mtron can stop it from outside.
     */
    protected boolean running() {
        return this.running;
    }

    /**
     * Leave the loop after the current key.
     */
    protected void stop() {
        this.running = false;
    }

    // ── the shared plumbing ────────────────────────────────────────

    /**
     * The reader the loop is using — a tool that must read a key's payload back (jline delivers a mouse sequence
     * as a macro, and the event is polled from the reader) takes it from here.
     */
    protected BindingReader bindingReader() {
        return this.bindingReader;
    }

    protected WidgetCanvas canvas() {
        return this.beginRedraw(this.totalHeightUsed);
    }

    protected void settle(final WidgetCanvas canvas) {
        this.totalHeightUsed = canvas.finish();
    }

    /**
     * Bind the vocabulary every interactive tool shares: up/down (and optionally left/right) and enter. A null
     * action skips that key, so a tool that does not navigate horizontally passes nulls.
     */
    protected void bindArrows(final KeyMap<A> keyMap, final A up, final A down, final A left, final A right,
                              final A select) {
        if (null != up)
            keyMap.bind(up, "\033[A");
        if (null != down)
            keyMap.bind(down, "\033[B");
        if (null != right)
            keyMap.bind(right, "\033[C");
        if (null != left)
            keyMap.bind(left, "\033[D");
        if (null != select)
            keyMap.bind(select, Utilities.enter_key);
    }

    // ── the lifecycle, once ────────────────────────────────────────

    @Override
    public final void run() {
        final org.jline.terminal.Terminal terminal = this.terminal;
        if (!this.modal() || null == Console.LOCAL_INSTANCE || !interactive(terminal)) {
            this.present();
            return;
        }
        this.savedAttributes = terminal.enterRawMode();
        terminal.puts(InfoCmp.Capability.keypad_xmit);
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.writer().flush();
        this.running = true;
        try {
            // the console stops treating this tool's frames' cursor math as transcript content, which is what keeps
            // an in-place redraw in place instead of doubling the frame on every key
            this.beginToolRun();
            this.onEnter();
            this.bindingReader = new BindingReader(terminal.reader());
            final KeyMap<A> keyMap = this.keyMap();
            while (this.running) {
                this.redraw();
                final A action = this.bindingReader.readBinding(keyMap);
                // jline answers null only at the end of input (a closed stdin, a drained pipe): no key will ever
                // come, and asking again would redraw as fast as the CPU allows — the input is over, and so is the tool
                if (null == action || !this.handle(action))
                    this.running = false;
            }
        } catch (final IOError e) {
            // ctrl-c surfaces as an IOError with an interrupted cause; that is a user hanging up, not a bug, and it
            // must leave through the finally below like any other exit
            if (!(e.getCause() instanceof InterruptedIOException))
                throw e;
        } finally {
            this.endToolRun(this.finalFrame());
            this.onLeave();
            this.restoreTerminal();
        }
    }

    /**
     * Whether a terminal can be a tool's keyboard. jline falls back to a DUMB terminal when no stream is a tty — a
     * pipe, a CI log, an IDE's run window, a test JVM — and a dumb terminal has no raw mode and no cursor addressing:
     * the frames cannot redraw in place, and the keys arrive (if ever) a line at a time. A tool presents there
     * instead, which is also what keeps a headless boot from parking on a stdin that nobody types into. Both of
     * jline's dumb types count: it picks the colored one whenever {@code TERM} is set or an IDE is the parent.
     */
    private static boolean interactive(final org.jline.terminal.Terminal terminal) {
        if (null == terminal)
            return false;
        final String type = terminal.getType();
        return !org.jline.terminal.Terminal.TYPE_DUMB.equals(type)
                && !org.jline.terminal.Terminal.TYPE_DUMB_COLOR.equals(type);
    }

    /**
     * Hand the terminal back: cursor visible, attributes as they were, keypad off. Idempotent, so a tool's
     * {@code close()} may call it again.
     */
    protected void restoreTerminal() {
        final org.jline.terminal.Terminal terminal = this.terminal;
        terminal.puts(InfoCmp.Capability.cursor_visible);
        if (null != this.savedAttributes)
            terminal.setAttributes(this.savedAttributes);
        terminal.puts(InfoCmp.Capability.keypad_local);
        terminal.writer().flush();
        this.savedAttributes = null;
    }

    /**
     * A tool restores the terminal and then closes like any widget (which unfloats it). A tool that presents a
     * PERSISTENT panel overrides this and does nothing, because {@code .display()} closes the instance it just ran
     * and the default close would erase the panel the display was asked to pin.
     */
    @Override
    public void close() {
        this.restoreTerminal();
        super.close();
    }

    /**
     * The style, for tools that want to read it without repeating the cast.
     */
    protected Stylable.Style<W> toolStyle() {
        return this.getStyle();
    }

    /**
     * A one-line footer of key hints, drawn by tools that own the screen.
     */
    protected void hint(final WidgetCanvas canvas, final String hints) {
        canvas.statusLine(Graphitty.string(hints));
    }

    /**
     * not used by the loop itself; kept so a tool can render an attributed line without importing jline
     */
    protected static AttributedString line(final String markup) {
        return AttributedString.fromAnsi(Graphitty.string(markup));
    }
}
