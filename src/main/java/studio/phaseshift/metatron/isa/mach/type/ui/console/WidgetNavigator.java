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

import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.FloatingSurface;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/**
 * The console's navigation over its pinned floating widgets: which one
 * is focused (tracked by the widget's stable key, since a widget is
 * re-hydrated into a fresh instance on every {@code .display()} update),
 * cycling the focus, resizing the focused widget, and scrolling its
 * viewport — plus the reasserted built-in key bindings that a later
 * binder (a menu line key, a tool, anything) may shadow.
 * <p>
 * The surface's own geometry, rendering, and the pointer handoff
 * (mouse tracking, pointer release) stay on {@link Console}.
 */
public final class WidgetNavigator {

    /**
     * Per-keystroke resize step for floating widgets (the pane analog
     * resizes by ±0.05 ratio per keystroke).
     */
    private static final int WIDGET_WIDTH_STEP = 4;
    private static final int WIDGET_HEIGHT_STEP = 1;

    /**
     * The stable key of the focused floating widget
     * ({@link FloatingSurface#widgetKey} — its vid when it has one).  A
     * floating widget is re-hydrated into a fresh instance on every
     * {@code .display()} update, so the key — not the instance — is tracked,
     * and the current instance is resolved lazily.  null = no focus.
     */
    private volatile String activeWidgetKey = null;

    /**
     * The built-in key bindings this console registers, by sequence, in
     * registration order — the identity of each handler is what
     * {@link #reassertBuiltinKeys()} compares against when it reclaims a
     * shadowed binding.
     */
    private final Map<String, Object> builtinBindings = new LinkedHashMap<>();

    private final Console console;

    public WidgetNavigator(final Console console) {
        this.console = console;
    }

    /**
     * @return true when the console's floating surface has at least one pinned widget
     */
    public boolean hasFloatingWidgets() {
        return !this.console.getFloatingSurface().widgets().isEmpty();
    }

    /**
     * Register a built-in key binding (handler identity remembered) so that
     * {@link #reassertBuiltinKeys} can reclaim the sequence if a later binder
     * shadows it (a menu line key, a tool, anything bound after startup).
     */
    public void registerBuiltinKey(final String sequence, final Object handler) {
        this.builtinBindings.put(sequence, handler);
    }

    /**
     * Reclaim the built-in key bindings that a later binder has overridden on
     * the shared "main" keymap — called before every prompt by the console.
     * The reclamation itself is {@link Console#reassertBuiltin} (static, so it
     * can be tested without a console).
     */
    public void reassertBuiltinKeys() {
        if (this.builtinBindings.isEmpty())
            return;
        Console.reassertBuiltin(this.console.getWidgets().getKeyMap(), this.builtinBindings);
    }

    /**
     * @return the currently bound handler for the given key sequence (may be null)
     */
    public Object boundKeyHandler(final String sequence) {
        return this.console.getWidgets().getKeyMap().getBound(sequence);
    }

    /**
     * @return the handler the console registered for the builtin (may be null)
     */
    public Object builtinKeyHandler(final String sequence) {
        return this.builtinBindings.get(sequence);
    }

    /**
     * @return the sequences the console keeps as reasserted builtins
     */
    public java.util.Set<String> builtinKeySequences() {
        return this.builtinBindings.keySet();
    }

    /**
     * @return the pinned floating widgets in deterministic focus-cycling order
     * (see {@link FloatingSurface#widgets()})
     */
    public List<Widget<?>> getFloatingWidgets() {
        return this.console.getFloatingSurface().widgets();
    }

    /**
     * @return the currently focused floating widget, or null when nothing
     * is focused (or the focused widget was removed / re-floated into
     * an unknown key since)
     */
    public Widget<?> getActiveWidget() {
        final String key = this.console.getFloatingSurface().resolveKey(this.activeWidgetKey);
        if (null == key) return null;
        for (final Widget<?> w : this.getFloatingWidgets()) {
            if (key.equals(FloatingSurface.widgetKey(w)))
                return w;
        }
        return null;
    }

    /**
     * Focus the given floating widget (pass null to clear the focus).
     * Triggers a re-render so the focus marker lands immediately.
     */
    public void focusWidget(final Widget<?> widget) {
        if (null == widget) {
            this.activeWidgetKey = null;
            this.console.getFloatingSurface().setFocusKey(null);
        } else {
            this.activeWidgetKey = FloatingSurface.widgetKey(widget);
            this.console.getFloatingSurface().setFocusKey(this.activeWidgetKey);
            // re-arming a widget re-arms the pointer, so a wheel released to
            // the terminal is handed back to the widgets
            this.console.rearmPointer();
        }
        // fire-and-forget: a click must never stall the console thread on a
        // render, and the pointer decision only needs the focus itself
        this.console.getFloatingSurface().render();
        this.console.syncWidgetMouseTracking();
    }

    /**
     * Cycle the focus to the next floating widget (wrapping).  A no-op when
     * no widgets are pinned.
     */
    public void nextWidget() {
        this.cycleWidgetFocus(1);
    }

    /**
     * Cycle the focus back to the previous floating widget (wrapping).
     */
    public void prevWidget() {
        this.cycleWidgetFocus(-1);
    }

    private void cycleWidgetFocus(final int direction) {
        final List<Widget<?>> widgets = this.getFloatingWidgets();
        if (widgets.isEmpty()) {
            this.console.logger().warn("no floating widgets to focus");
            return;
        }
        final String activeKey = this.console.getFloatingSurface().resolveKey(this.activeWidgetKey);
        int index = -1;
        for (int i = 0; i < widgets.size(); i++) {
            if (null != activeKey && activeKey.equals(FloatingSurface.widgetKey(widgets.get(i)))) {
                index = i;
                break;
            }
        }
        if (index < 0 && widgets.size() > 1) {
            // lost the position — say so, so a live session can see it
            // (single-widget cycling back to the same widget is normal)
            this.console.logger().debug("focus key {{y}}%s{{X}} not found among {{y}}%d{{X}} floating widgets; cycling restarts at the first — see {{m}}:widgets{{X}}",
                    this.activeWidgetKey, widgets.size());
        }
        final Widget<?> next = widgets.get(((index + 1 + direction) % widgets.size() + widgets.size()) % widgets.size());
        this.focusWidget(next);
    }

    /**
     * Grow the focused floating widget's width by one step
     * ({@value WIDGET_WIDTH_STEP} columns).  Which edge moves is decided by
     * the widget's anchor: a left-anchored widget extends its right edge,
     * a right-anchored widget pulls in its left edge.
     */
    public void growActiveWidgetWidth() {
        this.nudgeActiveWidget(WIDGET_WIDTH_STEP, 0);
    }

    /**
     * Shrink the focused floating widget's width by one step.
     */
    public void shrinkActiveWidgetWidth() {
        this.nudgeActiveWidget(-WIDGET_WIDTH_STEP, 0);
    }

    /**
     * Grow the focused floating widget's height by one step
     * ({@value WIDGET_HEIGHT_STEP} rows).  Which edge moves is decided by
     * the widget's anchor: a bottom-anchored widget pushes its top edge
     * up, a top-anchored widget pushes its bottom edge down.
     */
    public void growActiveWidgetHeight() {
        this.nudgeActiveWidget(0, WIDGET_HEIGHT_STEP);
    }

    /**
     * Shrink the focused floating widget's height by one step.
     */
    public void shrinkActiveWidgetHeight() {
        this.nudgeActiveWidget(0, -WIDGET_HEIGHT_STEP);
    }

    private void nudgeActiveWidget(final int widthDelta, final int heightDelta) {
        final Widget<?> active = this.getActiveWidget();
        if (null == active) {
            this.console.logger().warn("no floating widget in focus");
            return;
        }
        this.console.getFloatingSurface().nudge(active, widthDelta, heightDelta);
    }

    // ========== Floating Widget Scrolling ==========
    // A pinned widget draws through a viewport (its style height, else the
    // terminal), so content that does not fit is not discarded — it is simply
    // off the viewport, still alive in the widget's body.  These methods move
    // that viewport, which is how the text scrolled out of sight is read again.

    /**
     * Scroll the focused floating widget's viewport by {@code (dx, dy)} cells.
     * Positive {@code dy} moves toward newer content, positive {@code dx}
     * toward later columns.
     *
     * @return true when the focused widget accepted the scroll
     */
    public boolean scrollActiveWidget(final int dx, final int dy) {
        final Widget<?> active = this.getActiveWidget();
        // nothing focused is not an error — a scroll key is allowed to fall
        // through to the binding that owned it before
        if (null == active) return false;
        if (!this.console.getFloatingSurface().scroll(active, dx, dy)) {
            this.console.logger().warn("focused floating widget {{y}}%s{{X}} does not scroll", this.activeWidgetKey);
            return false;
        }
        this.reportWidgetScroll(active);
        return true;
    }

    /**
     * Scroll the focused floating widget by one viewport page
     * ({@code direction} &lt; 0 = back in the text, &gt; 0 = forward).
     */
    public boolean pageActiveWidget(final int direction) {
        final Widget<?> active = this.getActiveWidget();
        // nothing focused is not an error — a scroll key is allowed to fall
        // through to the binding that owned it before
        if (null == active) return false;
        final int page = this.console.getFloatingSurface().pageRows(active) * (direction < 0 ? -1 : 1);
        if (!this.console.getFloatingSurface().scroll(active, 0, page)) {
            this.console.logger().warn("focused floating widget {{y}}%s{{X}} does not scroll", this.activeWidgetKey);
            return false;
        }
        this.reportWidgetScroll(active);
        return true;
    }

    /**
     * Put the focused floating widget's viewport back on its newest content —
     * the state it starts in, and the one it returns to as content arrives.
     */
    public boolean tailActiveWidget() {
        final Widget<?> active = this.getActiveWidget();
        // nothing focused is not an error — a scroll key is allowed to fall
        // through to the binding that owned it before
        if (null == active) return false;
        this.console.getFloatingSurface().scrollToTail(active);
        this.reportWidgetScroll(active);
        return true;
    }

    /**
     * Jump the focused floating widget's viewport to an absolute body row
     * (clamped to the content).
     */
    public boolean scrollActiveWidgetTo(final int row) {
        final Widget<?> active = this.getActiveWidget();
        // nothing focused is not an error — a scroll key is allowed to fall
        // through to the binding that owned it before
        if (null == active) return false;
        this.console.getFloatingSurface().scrollTo(active, row);
        this.reportWidgetScroll(active);
        return true;
    }

    /**
     * @return true when the focused floating widget has content off its
     * viewport that a scroll would reveal
     */
    public boolean activeWidgetScrolls() {
        final Widget<?> active = this.getActiveWidget();
        return null != active && this.console.getFloatingSurface().canScroll(active);
    }

    /**
     * A one-line description of the focused widget's viewport, e.g.
     * {@code rows 12-31/240} (empty when it is not scrollable).
     */
    public String activeWidgetScrollInfo() {
        final Widget<?> active = this.getActiveWidget();
        if (null == active) return "";
        final String info = this.console.getFloatingSurface().scrollInfo(active);
        return info.isEmpty() ? "" : this.console.getFloatingSurface().resolveKey(this.activeWidgetKey) + " " + info;
    }

    /**
     * Echo a widget's viewport position to the status line, so a scroll (which
     * changes nothing a cursor can point at) is still visibly acknowledged.
     */
    private void reportWidgetScroll(final Widget<?> widget) {
        final String key = this.console.getFloatingSurface().resolveKey(this.activeWidgetKey);
        final String info = this.console.getFloatingSurface().scrollInfo(widget);
        StatusLine.message(str(info.isEmpty()
                ? "{{y}}%s{{X}} {{w}}has nothing off its viewport{{X}}".formatted(key)
                : "{{y}}%s{{X}} %s".formatted(key, info)));
    }
}
