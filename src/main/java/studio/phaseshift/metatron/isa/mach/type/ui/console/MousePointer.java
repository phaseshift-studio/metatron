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

import org.jline.terminal.Terminal;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.FloatingSurface;

/**
 * The console's pointer: the click and drag gestures on the pinned
 * floating widgets, the terminal mouse-tracking mode the console holds
 * while the pointer is in play, and the hand-back of the pointer to the
 * terminal (a wheel released over empty terminal, the close of the
 * console).
 * <p>
 * What the pointer touches stays elsewhere: the widget set and its
 * geometry on {@link FloatingSurface}, transcript links and screen
 * repair on the console itself, and the pure
 * {@link Console#pointerWanted} decision.
 */
public final class MousePointer {

    /**
     * Terminal mouse modes this console turns on: button events (1000), button-event
     * tracking (1002) — motion while a button is held, which is what a drag is — and
     * the SGR encoding (1006), and nothing else.
     *
     * <p>Deliberately NOT jline's {@code MouseSupport.trackMouse(Normal)}, which
     * also enables {@code ?1005h} — the legacy UTF-8 coordinate encoding.  With
     * 1005 and 1006 enabled together a terminal may report the pointer in either
     * encoding, and a mis-decoded column is exactly what makes a small target
     * (an accordion's {@code [-]} cell) impossible to hit while a large one (the
     * widget body) still works.
     */
    private static final String MOUSE_ON = "\033[?1000h\033[?1002h\033[?1006h";
    /**
     * Every mode jline may have enabled, off — a full hand-back to the terminal.
     */
    private static final String MOUSE_OFF =
            "\033[?1000l\033[?1002l\033[?1003l\033[?1005l\033[?1006l\033[?1015l\033[?1016l";

    /**
     * The widget the pointer is working on, or null when no drag is in flight.
     */
    private volatile Widget<?> dragWidget = null;
    /**
     * Which handle was taken hold of: the chevron (move) or the corner marker (resize).
     */
    private FloatingSurface.Handle dragHandle = null;
    /**
     * Where the pointer took hold — the handle's own cell.
     */
    private int dragRow = 0;
    private int dragCol = 0;
    /**
     * The box at press time: a resize is a delta from it, not an accumulation of events.
     */
    private int dragWidth = 0;
    private int dragHeight = 0;
    /**
     * True once the pointer actually moved, so a press-and-release stays a click.
     */
    private boolean dragMoved = false;

    /**
     * Whether terminal mouse tracking is currently owned by widget scrolling
     * (see {@link #syncWidgetMouseTracking()}).
     */
    private volatile boolean widgetMouseTracking = false;

    /**
     * When true the pointer has been handed back to the terminal (mouse tracking
     * disabled) so the wheel scrolls the terminal's own scrollback.  Re-armed on
     * the next prompt or when a widget is focused via {@code alt}+{@code w}.
     */
    private volatile boolean pointerReleased = false;

    private final Console console;

    public MousePointer(final Console console) {
        this.console = console;
    }

    // ── the click gesture ─────────────────────────────────────────────

    /**
     * A pointer click at a terminal cell.
     *
     * <p>The gesture vocabulary is the one a pointer implies: the widget under
     * the pointer is focused, its own affordances get first refusal (an
     * accordion's {@code [-]} / {@code [+]} toggle, for example — see
     * {@link Widget#onClick(int, int)}), and a click on terminal that has no
     * widget under it means no widget owns the user's attention, so the focus
     * is cleared.
     *
     * @param row 1-based terminal row of the click
     * @param col 1-based terminal column of the click
     * @return true when a widget consumed the click for itself
     */
    public boolean clickAt(final int row, final int col) {
        return this.clickAt(row, col, false);
    }

    /**
     * As {@link #clickAt(int, int)} with a held control key.
     */
    public boolean clickAt(final int row, final int col, final boolean follow) {
        final Widget<?> hit = this.console.getFloatingSurface().widgetAt(row, col);
        if (Boolean.getBoolean("metatron.render.trace"))
            Console.rawErr().println("[click] row=" + row + " col=" + col + " widget=" + (null != hit)
                    + " inRead=" + this.console.inReadLine() + " appends=" + this.console.screenAppends()
                    + " rows=" + this.console.getScreen().rows() + " top=" + this.console.getScreen().top());
        if (null == hit) {
            // A link in the transcript is the screen's own affordance, and it answers before
            // the focus is touched: it types rather than focuses.
            if (this.console.openScreenLink(row, col, follow)) return true;
            // Empty terminal — including the prompt area: nothing owns the
            // user's attention, so the focus is dropped.  The pointer stays
            // armed, though — while a widget is on screen the next click can
            // still focus one (no alt+w re-entry).  Use Shift+drag for native
            // terminal text selection while the widgets hold the mouse.
            if (null != this.console.getActiveWidget()) this.console.focusWidget(null);
            else this.syncWidgetMouseTracking();
            return false;
        }
        final boolean focused = this.console.getActiveWidget() == hit;
        if (!focused) this.console.focusWidget(hit);
        // The affordance gets its own render: a click that both focuses a widget
        // and toggles it must show the toggled state (focusWidget's render ran
        // before the widget acted).  surface.render() coalesces, so a click
        // never costs more than one pass.
        final boolean consumed = this.console.getFloatingSurface().click(hit, row, col, false);
        if (consumed) {
            this.console.getFloatingSurface().render();
        }
        return consumed;
    }

    // ── the drag gesture ──────────────────────────────────────────────
    //
    // Where a widget sits and how big it is WHILE the pointer works on it is VIEW state,
    // so it lives here and on the widget's slot — never in the widget's rec.  Only the
    // release writes the rec (style top/left after a move, width/height after a resize),
    // because that is what has to outlive the session: every .display() re-hydrates the
    // widget into a fresh instance, and geometry that only lived in this console would be
    // gone the next time the widget's content changed.

    /**
     * A pointer press: taking hold of one of the focused widget's handles starts a
     * gesture, and anything else keeps the click semantics (focus the widget under the
     * pointer, let it work its affordance, or clear the focus on empty terminal).
     *
     * <p>The handles are the two cells a widget can be worked from, and both are
     * painted in the widget's own box — so the cell the user grabs IS the geometry the
     * gesture changes: the chevron ({@code ▶}) in the top-left cell is the widget's
     * origin, which makes a move a translation, and the marker ({@code ◢}) in the
     * bottom-right cell is the far corner of its box, which makes a resize a
     * width/height delta.  No offset arithmetic, and targets that small stay easy to
     * hit on purpose: nobody clicks a corner by accident, and the body of the widget
     * stays free for selection, scrolling and affordances.
     *
     * <p>An affordance under the press still wins: the click runs first, and only a
     * press the widget did not consume becomes a grab.  A widget whose own top-left
     * cell acts on a click (a selector's first row, say) keeps that click and simply
     * cannot be dragged by its chevron.
     *
     * @param row 1-based terminal row of the press
     * @param col 1-based terminal column of the press
     * @return true when the press was consumed — by the widget's affordance or by a grab
     */
    public boolean mousePressed(final int row, final int col) {
        return this.mousePressed(row, col, false);
    }

    /**
     * As {@link #mousePressed(int, int)} with a held control key: on a link it means follow the
     * uri (type it AND submit it) rather than only type it.
     */
    public boolean mousePressed(final int row, final int col, final boolean follow) {
        Console.linkTrace("press row=%d col=%d follow=%b | %s", row, col, follow, this.console.linkReport());
        // whose handles these are: the ALREADY focused widget's.  A press on the corner
        // of an unfocused widget is how you focus it (no handle is drawn there yet, so
        // the user did not aim at one), and a second press then grabs.
        final Widget<?> focused = this.console.getActiveWidget();
        final FloatingSurface.Handle handle = null == focused
                ? null : this.console.getFloatingSurface().handleAt(focused, row, col);
        // the click runs first so a widget's own affordance keeps its cell
        final boolean consumed = this.clickAt(row, col, follow);
        if (null == handle || consumed) return consumed;
        final FloatingSurface.Placement placement = this.console.getFloatingSurface().placement(focused);
        this.dragWidget = focused;
        this.dragHandle = handle;
        this.dragRow = row;
        this.dragCol = col;
        this.dragWidth = null == placement ? 0 : placement.width();
        this.dragHeight = null == placement ? 0 : placement.height();
        this.dragMoved = false;
        // hold the pointer for the whole gesture: a mid-drag hand-back to the
        // terminal would drop the release event and leave the widget in flight
        this.syncWidgetMouseTracking(true);
        return true;
    }

    /**
     * A pointer motion with a button held: the grabbed handle follows the pointer — the
     * chevron moves the widget to where the pointer is, the corner marker sizes the box
     * by how far the pointer has come from where it took hold.
     *
     * @return true when a gesture is in flight
     */
    public boolean mouseDragged(final int row, final int col) {
        final Widget<?> widget = this.dragWidget;
        if (null == widget) return false;
        if (row == this.dragRow && col == this.dragCol) return true;   // still on the handle
        this.dragMoved = true;
        this.applyDrag(widget, this.dragHandle, row, col);
        return true;
    }

    private void applyDrag(final Widget<?> widget, final FloatingSurface.Handle handle,
                           final int row, final int col) {
        // what the box covers now is what the move is about to leave behind: the
        // screen has to put those rows back (see repairRows), and asking here — on the
        // console thread, once per motion event — keeps a fast drag from outrunning the
        // damage the render pass reports
        final int[] before = this.console.getFloatingSurface().lastRows(widget);
        if (FloatingSurface.Handle.RESIZE == handle)
            this.console.getFloatingSurface().resizeTo(widget,
                    this.dragWidth + (col - this.dragCol),
                    this.dragHeight + (row - this.dragRow));
        else
            // the corner lands where the pointer is — placeAt clamps so the handle itself
            // can never be dragged off screen and out of reach
            this.console.getFloatingSurface().placeAt(widget, row, col);
        if (null != before) this.console.repairRows(before[0], before[1]);
    }

    /**
     * A pointer release: park the widget — write what the gesture changed into its style
     * so it survives re-hydration — and end the gesture.  A press and release that never
     * moved is just a click on a handle, and the press already focused the widget.
     *
     * @return true when a gesture was in flight
     */
    public boolean mouseReleased(final int row, final int col) {
        final Widget<?> widget = this.dragWidget;
        if (null == widget) return false;
        final FloatingSurface.Handle handle = this.dragHandle;
        if (this.dragMoved) {
            this.dragMoved = false;
            // the release cell is the final word: a terminal need not send a motion
            // event for the last cell the pointer crossed
            this.applyDrag(widget, handle, row, col);
            this.parkDrag(widget, handle);
            // where the gesture ends, repair for certain: the per-motion repair is
            // coalesced, so the last one may have been skipped
            if (Console.screenMode()) this.console.repairRows(row, row);
        }
        this.dragWidget = null;
        this.dragHandle = null;
        this.syncWidgetMouseTracking(true);
        return true;
    }

    private void parkDrag(final Widget<?> widget, final FloatingSurface.Handle handle) {
        final FloatingSurface.Placement placement = this.console.getFloatingSurface().placement(widget);
        if (null == placement) return;
        try {
            final var style = widget.style().top(placement.top()).left(placement.left());
            if (FloatingSurface.Handle.RESIZE == handle)
                style.width(placement.width()).height(placement.height());
            style.applyStyle();
        } catch (final Exception e) {
            this.console.logger().warn("pointer: could not park the widget's geometry: {{r}}%s{{X}}", e.getMessage());
        }
    }

    /**
     * True while the pointer is moving a widget.
     */
    public boolean dragging() {
        return null != this.dragWidget;
    }

    // ── the mouse-tracking mode ───────────────────────────────────────

    /**
     * Turn terminal mouse tracking on or off to match what is on screen.
     *
     * <p>The pointer belongs to the widgets while any of them is on screen
     * (or one is focused): that is what lets a cold click focus a widget,
     * and a click after unfocusing re-focus one.  Only with nothing pinned
     * does the terminal get its own mouse back (wheel, drag-selection).
     *
     * <p>Called only when the prompt owns the terminal (the console's watcher
     * thread, and the console thread itself): while a job holds the console the
     * mouse stays off, so its bytes can never be mistaken for typed input.
     */
    public void syncWidgetMouseTracking() {
        this.syncWidgetMouseTracking(false);
    }

    /**
     * @param force re-assert the mode even when it has not changed — used once
     *              per prompt, because jline releases mouse tracking at the end
     *              of every readLine, so the flag alone would leave the pointer
     *              dead from the second prompt on
     */
    public void syncWidgetMouseTracking(final boolean force) {
        final Terminal term = this.console.getTerminal();
        if (null == term || !term.hasMouseSupport()) return;
        final int widgets = this.console.getFloatingSurface().drawnWidgetCount();
        // a drag holds the pointer whatever else changes — the gesture is not over
        // until the button comes up
        final boolean wanted = this.dragging()
                || Console.pointerWanted(null != this.console.getActiveWidget(), this.pointerReleased, widgets)
                // ...and while the transcript holds a link: clicking one is the screen's own
                // affordance, and a click is only ours while we hold the pointer.  A release the
                // reader asked for (alt+s) outranks it: the pointer goes back to the terminal so
                // shift-drag selection works, and stays there until the next prompt re-arms it —
                // re-arming on the next paint would take it back before they could select anything
                || (Console.screenMode() && !this.pointerReleased && this.console.getScreen().hasLinks());
        if (wanted == this.widgetMouseTracking && !force) return;
        Console.linkTrace("mouse %s (wanted=%b force=%b dragging=%b focused=%b released=%b widgets=%d links=%d rows=%d)",
                wanted ? "ON" : "OFF", wanted, force, this.dragging(), null != this.console.getActiveWidget(),
                this.pointerReleased, widgets, this.console.getScreen().linkRows(), this.console.getScreen().rows());
        try {
            // only the enable is worth re-asserting (jline turns it off again at
            // the end of every readLine); a disable is written once, on the way out
            if (wanted) {
                term.writer().print(MOUSE_ON);
            } else if (this.widgetMouseTracking) {
                term.writer().print(MOUSE_OFF);
            }
            term.writer().flush();
            this.widgetMouseTracking = wanted;
        } catch (final Exception e) {
            this.console.logger().warn("could not %s mouse tracking: {{r}}%s{{X}}", wanted ? "enable" : "disable", e.getMessage());
        }
    }

    /**
     * Whether terminal mouse tracking is currently owned by widget scrolling.
     */
    public boolean widgetMouseTracking() {
        return this.widgetMouseTracking;
    }

    /**
     * Re-arm the pointer after a widget (re)gains focus — a wheel released
     * to the terminal is handed back to the widgets.
     */
    public void rearmPointer() {
        this.pointerReleased = false;
    }

    /**
     * Hand the pointer back to the terminal (disable mouse tracking) so the
     * wheel scrolls the terminal's own scrollback.  Triggered by a wheel over
     * empty terminal with nothing focused; re-armed on the next prompt or when
     * a widget is focused via {@code alt}+{@code w}.
     */
    public void releasePointer() {
        this.pointerReleased = true;
        // drop the focus too, so the widgets read as fully detached from the
        // pointer and the focus marker disappears with the re-render
        this.console.focusWidget(null);
    }

    /**
     * Hand mouse tracking back to the terminal on the way out — the shell the
     * console returns to must not inherit it.
     */
    public void handBackMouseTracking() {
        final Terminal terminal = this.console.getTerminal();
        if (this.widgetMouseTracking && null != terminal && terminal.hasMouseSupport()) {
            terminal.writer().print(MOUSE_OFF);
            this.widgetMouseTracking = false;
        }
    }

    /**
     * On every exit path (quit, ctrl-c, the launcher restarting the VM),
     * turn the mouse modes off outright: they are terminal modes, and the
     * process going away is the moment the shell would otherwise inherit
     * them.  Written unconditionally — jline may have enabled modes the
     * console never did.
     */
    public void disableMouseModesForExit() {
        final Terminal terminal = this.console.getTerminal();
        if (null == terminal) return;
        try {
            terminal.writer().print(MOUSE_OFF);
            terminal.writer().flush();
        } catch (final Exception ignore) {
            // the process is going away: nothing here is worth an error
        }
    }
}
