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
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.*;

import java.util.List;
import java.util.Map;

import static org.jline.keymap.KeyMap.key;
import static studio.phaseshift.metatron.Tokens.OBJ;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

/**
 * SwipePanelWidgetTool - A left-right swipe panel for browsing a stream of objs.
 * <p>
 * Displays each obj via {@link CardUtil#explorerCard}.  When the card is a
 * {@link Selector} (Type predicates, Rec/Lst values), the arrow keys navigate
 * rows within the table and Enter drills into the selected field.  Popups
 * follow the ExplainTool overlay pattern — rendered inline in the same
 * event loop, no nested raw-mode sessions.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SwipePanelWidgetTool extends AbstractWidget<SwipePanelWidgetTool> {

    private int currentIndex = 0;
    private int totalHeightUsed = 0;
    private Attributes savedAttributes;
    private boolean running = false;

    // ── Selector / popup state (ExplainTool pattern) ─────────────────
    private int selectorItemIndex = -1;  // which item the table was built for
    private TableWidget selectorTable;
    private int selectorRow = 0;
    private boolean selectorFocused = false; // true after first Enter
    private PanelWidget drillPopup;

    private enum Action {
        QUIT, NEXT, PREV, PAGE_DOWN, PAGE_UP, UP_ROW, DOWN_ROW, SELECT
    }

    /**
     * Java API constructor.
     */
    public SwipePanelWidgetTool(final Lst items) {
        this.at(OBJ, items);
    }

    /**
     * JRec constructor — mtron construction via {@code swipe_panel::[obj=>[...]]}.
     */
    public SwipePanelWidgetTool(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    // =====================================================================
    // Lifecycle
    // =====================================================================

    @Override
    public void run() {
        if (!this.has(OBJ)) return;
        Console.userMode.set(true);
        terminal.writer().print("\n");
        savedAttributes = terminal.enterRawMode();
        terminal.puts(InfoCmp.Capability.keypad_xmit);
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.writer().flush();

        this.running = true;
        BindingReader bindingReader = new BindingReader(terminal.reader());
        KeyMap<Action> keyMap = buildKeyMap();

        while (running) {
            redraw();
            Action action = bindingReader.readBinding(keyMap);
            handleAction(action);
        }
    }

    @Override
    public void close() {
        terminal.puts(InfoCmp.Capability.cursor_visible);
        if (savedAttributes != null) {
            terminal.setAttributes(savedAttributes);
        }
        terminal.puts(InfoCmp.Capability.keypad_local);
        terminal.writer().flush();
        super.close();
    }

    // =====================================================================
    // Input
    // =====================================================================

    private KeyMap<Action> buildKeyMap() {
        KeyMap<Action> keyMap = new KeyMap<>();
        keyMap.bind(Action.NEXT, key(terminal, InfoCmp.Capability.key_right));
        keyMap.bind(Action.PREV, key(terminal, InfoCmp.Capability.key_left));
        keyMap.bind(Action.PAGE_DOWN, key(terminal, InfoCmp.Capability.key_npage));
        keyMap.bind(Action.PAGE_UP, key(terminal, InfoCmp.Capability.key_ppage));
        keyMap.bind(Action.UP_ROW, key(terminal, InfoCmp.Capability.key_up));
        keyMap.bind(Action.DOWN_ROW, key(terminal, InfoCmp.Capability.key_down));
        keyMap.bind(Action.SELECT, Utilities.enter_key);
        keyMap.bind(Action.QUIT, ""); // Ctrl-D
        return keyMap;
    }

    private void handleAction(Action action) {
        if (!this.has(OBJ)) return;
        final int size = (int) this.at(OBJ).asLst().count();

        // ── Popup overlay mode ───────────────────────────────────────
        if (drillPopup != null) {
            if (action == Action.SELECT || action == Action.QUIT) {
                drillPopup = null;
            }
            return;
        }

        // ── Selector table focused (second Enter already pressed) ────
        if (selectorFocused) {
            switch (action) {
                case UP_ROW:
                    selectorRow = Math.max(0, selectorRow - 1);
                    return;
                case DOWN_ROW:
                    selectorRow = Math.min(selectorRow + 1,
                            selectorTable.rows().size() - 1);
                    return;
                case SELECT: {
                    final Obj drillObj = CardUtil.predicateValueAt(selectorTable, selectorRow);
                    if (drillObj != null) {
                        drillPopup = CardUtil.popup(drillObj);
                    }
                    return;
                }
                case QUIT:
                    selectorFocused = false;
                    return;
                // ← → pgup/pgdn stay in focus — no-op
                case NEXT, PREV, PAGE_DOWN, PAGE_UP:
                    return;
                default:
                    return;
            }
        }

        // ── Selector table present but not yet focused ───────────────
        if (selectorTable != null) {
            switch (action) {
                case SELECT:
                    selectorFocused = true;
                    return;
                case NEXT, PREV, PAGE_DOWN, PAGE_UP:
                    // Swipe navigation — clear selector state
                    selectorTable = null;
                    selectorRow = 0;
                    break;
                case QUIT:
                    running = false;
                    return;
                default:
                    return;
            }
        }

        // ── Normal swipe navigation ──────────────────────────────────
        switch (action) {
            case NEXT -> currentIndex = Math.floorMod(currentIndex + 1, size);
            case PREV -> currentIndex = Math.floorMod(currentIndex - 1, size);
            case PAGE_DOWN -> currentIndex = Math.floorMod(currentIndex + 5, size);
            case PAGE_UP -> currentIndex = Math.floorMod(currentIndex - 5, size);
            case QUIT -> running = false;
        }
        // Changing items clears selector state
        selectorTable = null;
        selectorRow = 0;
        selectorFocused = false;
    }

    // =====================================================================
    // Rendering  (ExplainTool: redrawStack with inline popups)
    // =====================================================================

    private void redraw() {
        final WidgetCanvas canvas = beginRedraw(totalHeightUsed);

        if (this.has(OBJ)) {
            final Obj current = this.at(OBJ).asLst().at(jnt(currentIndex));
            final Widget<?> card = CardUtil.explorerCard(current);

            // Detect Selector → extract table.  Only rebuild when the
            // item changes so that selectorRow survives between redraws.
            if (currentIndex != selectorItemIndex) {
                selectorItemIndex = currentIndex;
                selectorTable = null;
                selectorRow = 0;
                selectorFocused = false;
                if (card instanceof Selector s) {
                    final Widget<?> att = s.getStyle().attachment();
                    if (att instanceof TableWidget t) {
                        selectorTable = t;
                    }
                }
            }

            if (selectorTable != null) {
                renderSelectorTable(canvas);
            } else if (card instanceof PanelWidget p) {
                applyStyleToPanel(p);
                for (String line : p.format().split("\n", -1)) {
                    canvas.line(line);
                }
            }

            // Drill-down popup overlay
            if (drillPopup != null && selectorTable != null) {
                for (String line : drillPopup.format().split("\n", -1)) {
                    canvas.line("  " + line);
                }
            }
        }

        // Status / hint bar
        final String hint;
        if (drillPopup != null) {
            hint = "{{w}}enter/ctrl-d{{g}}:dismiss{{X}}";
        } else if (selectorFocused) {
            hint = String.format(
                    "{{w}}[%d/%d] {{g}}↕{{g}}:field {{w}}enter{{g}}:drill {{w}}ctrl-d{{g}}:back{{X}}",
                    currentIndex + 1, (int) this.at(OBJ).asLst().count());
        } else if (selectorTable != null) {
            hint = String.format(
                    "{{w}}[%d/%d] {{g}}↔{{g}}:nav {{w}}pgup/pgdn{{g}}:±5 {{w}}enter{{g}}:focus {{w}}ctrl-d{{g}}:quit{{X}}",
                    currentIndex + 1, (int) this.at(OBJ).asLst().count());
        } else {
            hint = String.format(
                    "{{w}}[%d/%d] {{g}}↔{{g}}:nav {{w}}pgup/pgdn{{g}}:±5 {{w}}ctrl-d{{g}}:quit{{X}}",
                    currentIndex + 1, (int) this.at(OBJ).asLst().count());
        }
        canvas.line(hint);

        totalHeightUsed = canvas.finish();
    }

    /**
     * Render the selector table with pointer highlighting on the active row.
     * Follows ExplainTool's {@code renderArgTablePopupLines} pattern.
     */
    /**
     * Render the selector table via {@link TableWidget#rowStrings()} so
     * borders, alignment, and padding are handled by the table's own style.
     * The pointer is post-processed onto the selected data row by replacing
     * the leading divider with {@code {{r}}>}.
     */
    private void renderSelectorTable(final WidgetCanvas canvas) {
        final List<String> lines = selectorTable.rowStrings();
        int dataRowCount = 0;

        for (String line : lines) {
            // Data rows are the lines containing the divider ({{g}}│).
            // Count them to map selectorRow without guessing header offsets.
            if (line.contains("{{g}}│")) {
                if (selectorFocused && dataRowCount == selectorRow) {
                    line = line.replaceFirst("\\{\\{g\\}\\}│", "{{r}}>");
                }
                dataRowCount++;
            }
            canvas.line(line);
        }
    }

    /**
     * Transfer style from this widget to a PanelWidget card.
     */
    private void applyStyleToPanel(final PanelWidget panel) {
        if (this.style == null) return;
        final var ps = panel.getStyle();
        if (ps == null) return;

        final String bg = this.style.background();
        if (bg != null && !bg.isEmpty()) ps.background(bg);
        final String fg = this.style.foreground();
        if (fg != null && !fg.isEmpty()) ps.foreground(fg);
        final Border border = this.style.border();
        if (border != null && border != Border.none) ps.border(border);

        ps.applyStyle();
    }

    // =====================================================================
    // Widget contract
    // =====================================================================

    @Override
    public String format() {
        if (!this.has(OBJ)) return "";
        final Obj current = this.at(OBJ).asLst().at(jnt(currentIndex));
        final Widget<?> display = CardUtil.explorerCard(current);
        if (display instanceof PanelWidget p) applyStyleToPanel(p);
        return display.format();
    }
}
