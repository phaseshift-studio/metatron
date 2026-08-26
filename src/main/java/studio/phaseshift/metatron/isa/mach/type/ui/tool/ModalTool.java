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
import org.jline.utils.InfoCmp;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Stylable;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.AbstractWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.PanelWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Utilities;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.WidgetCanvas;

import java.util.Map;

/**
 * ModalTool — a modal popup panel that blocks input until dismissed.
 * <p>
 * Renders a {@link PanelWidget} (title + body) and runs its own input loop:
 * {@code Space}, {@code Enter}, or {@code Ctrl-D} dismisses the modal and
 * returns.  The loop is the modal aspect — while it is up, no other input
 * reaches the console, and the terminal is restored on {@link #close()}.
 * <p>
 * Usage:
 * <pre>{@code
 *   final ModalTool modal = new ModalTool("confirmation", "Do you want to proceed?");
 *   modal.run();
 *   modal.close();
 * }</pre>
 * {@link #format()} returns the underlying panel, so the modal also works as a
 * plain display widget (e.g. in render tests).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ModalTool extends AbstractWidget<ModalTool> {

    private enum Action {
        DISMISS
    }

    private final PanelWidget panel;
    private Attributes savedAttributes;
    private boolean running = false;
    private int totalHeightUsed = 0;

    public ModalTool(final String title, final String body) {
        this(new PanelWidget(title, body));
    }

    public ModalTool(final PanelWidget panel) {
        super();
        this.panel = panel;
        this.applyDefaultBorder();
        this.syncStyle();
    }

    /**
     * JRec constructor — mtron construction via
     * {@code modal::[title=>'...',body=>'...']}.
     */
    public ModalTool(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.panel = new PanelWidget(jvm, PanelWidget.UI_PANEL_TID, null);
        this.applyDefaultBorder();
        this.syncStyle();
    }

    private void applyDefaultBorder() {
        // A borderless panel renders as plain text (Border.none glyphs are
        // spaces) — give unstyled panels a visible popup box.  Panels styled
        // by the caller (e.g. CardUtil.popup) keep their border.
        if (this.panel.getStyle().border() == Border.none) {
            this.panel.style().border(Border.continuous).applyStyle();
        }
    }

    /**
     * Mirror the inner panel's style onto the modal's own style.  The
     * {@code FloatingSurface} z-order sorts pinned widgets by the widget's
     * own {@link Style#zIndex()}, not the panel's — without this, a styled
     * modal would always sort as z=0 and could be painted under other floats.
     */
    private void syncStyle() {
        final Style<PanelWidget> panelStyle = this.panel.getStyle();
        if (panelStyle != null) {
            final Style<ModalTool> own = this.style();
            own.jvm().putAll(panelStyle.jvm());
            this.style(own);
        }
    }

    @Override
    public void run() {
        Console.userMode.set(true);
        this.terminal.writer().print("\n");
        this.savedAttributes = this.terminal.enterRawMode();
        this.terminal.puts(InfoCmp.Capability.keypad_xmit);
        this.terminal.puts(InfoCmp.Capability.cursor_invisible);
        this.terminal.writer().flush();

        // A styled modal floats at its anchor (e.g. anchor=>middle centers it
        // on screen); otherwise it renders in-place below the prompt.  When
        // floating, the console's FloatingSurface owns the pixels, so the key
        // loop below only waits for a dismiss key (no per-iteration redraw).
        final Stylable.Style<PanelWidget> style = this.panel.getStyle();
        final boolean floats = Console.LOCAL_INSTANCE != null
                && style != null && style.hasFloat();
        if (floats) {
            // Constrain the panel to the terminal width before floating.  The
            // surface clips lines wider than the terminal via Graphitty.strip(),
            // which would strip the background/foreground color codes out of a
            // wide body line.
            final int avail = this.terminal.getWidth() - 6;
            if (avail > 20) this.panel.maxWidth(avail);
            final int floatW = style.width() > 0 ? style.width() : this.panel.width();
            this.floatAt(Console.LOCAL_INSTANCE.getFloatingSurface(), style.anchor(),
                    floatW, style.top(), style.left());
            Console.LOCAL_INSTANCE.getFloatingSurface().render();
        }

        this.running = true;
        final BindingReader bindingReader = new BindingReader(this.terminal.reader());
        final KeyMap<Action> keyMap = buildKeyMap();
        while (this.running) {
            if (!floats) redraw();
            final Action action = bindingReader.readBinding(keyMap);
            if (action == Action.DISMISS) {
                this.running = false;
            }
        }
    }

    @Override
    public void close() {
        this.terminal.puts(InfoCmp.Capability.cursor_visible);
        if (null != this.savedAttributes) {
            this.terminal.setAttributes(this.savedAttributes);
        }
        this.terminal.puts(InfoCmp.Capability.keypad_local);
        this.terminal.writer().flush();
        super.close();
    }

    private KeyMap<Action> buildKeyMap() {
        final KeyMap<Action> keyMap = new KeyMap<>();
        keyMap.bind(Action.DISMISS, "");             // Ctrl-D
        keyMap.bind(Action.DISMISS, Utilities.enter_key);  // Enter
        keyMap.bind(Action.DISMISS, " ");                  // Space
        return keyMap;
    }

    /**
     * Redraw the panel and its dismiss hint, wrapping the body to the
     * terminal width so long bodies stay on screen.
     */
    private void redraw() {
        final WidgetCanvas canvas = beginRedraw(this.totalHeightUsed);
        final int avail = this.terminal.getWidth() - 6;
        if (avail > 20) this.panel.maxWidth(avail);
        for (final String line : this.format().split("\n", -1)) {
            canvas.line("  " + line);
        }
        canvas.statusLine("{{w}}space/enter/ctrl-d{{g}}:dismiss {{X}}");
        this.totalHeightUsed = canvas.finish();
    }

    @Override
    public String format() {
        return this.panel.format();
    }
}
