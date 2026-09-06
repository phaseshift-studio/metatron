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
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.*;

import java.util.*;

import static org.jline.keymap.KeyMap.key;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * Explain - A code explanation widget with proper nested window support.
 * <p>
 * Features:
 * - Navigate cells with arrow keys
 * - Press Enter on 'args' column to drill into nested code
 * - Press ESC to go back up / close
 * - Nested tables appear offset and overlay the parent
 * - Proper z-order: closing a nested table restores the view
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ExplainTool extends AbstractWidget<ExplainTool> {

    private static final GraphittyLogger LOG = Graphitty.log(ExplainTool.class);

    private enum Action {
        QUIT, DOWN_ROW, UP_ROW, RIGHT_COL, LEFT_COL, SELECT, SPACE
    }

    /**
     * Represents one level in the explain stack (one code block being viewed).
     */
    private static class ExplainLevel {
        final Code code;
        final ProfileTool profile;
        final boolean rewritten;
        final TableWidget table;
        final int offsetX;
        final int offsetY;
        int selectedRow;
        int selectedCol;
        final List<String> savedScreen; // What was underneath this level
        final String pointer;      // The pointer character with color (e.g., "{{r}}>")
        final String divider;      // The divider string with color (e.g., "{{r}}|")
        final String rawDivider;   // The divider without color codes (e.g., "|")
        // Track which cell in the PARENT spawned this level (for visual connector)
        final int spawnRow;        // Row in parent that spawned this (-1 if root)
        final int spawnCol;        // Column in parent that spawned this (-1 if root)

        // ── Popup overlay state ──────────────────────────────────────
        PanelWidget panelPopup;         // Info panel popup (docs, type, form desc, coeff)
        TableWidget argTablePopup;      // Multi-arg selection table
        List<Obj> argEntries;           // Arg objects for drill-down from arg table
        int argTableSelectedRow = 0;    // Selected row in arg table popup

        ExplainLevel(final Code code, final int offsetX, final int offsetY, final int spawnRow, final int spawnCol) {
            this.code = code;//.resolve(noobj());
            this.rewritten = code.isResolved(false);// false;//code.insts().stream().map(Inst::tid).toList().equals(code.clone().asCode().rewrite().insts().stream().map(Inst::tid).toList());
            this.profile = new ProfileTool(this.code);
            final Border border = Border.continuous.foreground("{{b}}");
            this.profile.instTable.style()
                    .headerDivider("{{[b]}} ")
                    .border(border)
                    .divider("{{b}}" + border.leftSide())
                    .pointer("{{r}}>")  // Configure pointer style
                    .applyStyle();
            this.table = this.profile.instTable;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.selectedRow = 0;  // Start at first data row (after header)
            this.selectedCol = 0;
            this.savedScreen = new ArrayList<>();
            // Get divider and pointer from table style
            this.divider = this.table.getStyle().divider();
            this.rawDivider = Graphitty.strip(this.divider);
            this.pointer = this.table.getStyle().pointer().isEmpty() ? "{{r}}>" : this.table.getStyle().pointer();
            this.spawnRow = spawnRow;
            this.spawnCol = spawnCol;
        }

        int dataRowCount() {
            return this.code.codeValue().size();
        }

        Inst getInst(int row) {
            if (row >= 0 && row < code.codeValue().size()) {
                return code.codeValue().get(row);
            }
            return null;
        }
    }

    private final Deque<ExplainLevel> stack = new ArrayDeque<>();
    private final Code rootCode;
    private Attributes savedAttributes;
    private boolean running = false;
    private int totalHeightUsed = 0;  // Track how many lines we've used

    public ExplainTool(final Code code) {
        this.rootCode = code;
    }

    @Override
    public void run() {
        // Enter raw mode
        terminal.writer().print("\n");
        savedAttributes = terminal.enterRawMode();
        terminal.puts(InfoCmp.Capability.keypad_xmit);
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.writer().flush();

        // Don't clear screen - draw below current position
        // Get current cursor position as the base
        // The row where we start drawing (below prompt)
        //int baseRow = 0;  // We'll draw relative to current position

        pushLevel(rootCode, 0, 0, -1, -1);  // Root has no parent spawn position

        // Main event loop
        this.running = true;
        BindingReader bindingReader = new BindingReader(this.terminal.reader());
        KeyMap<Action> keyMap = buildKeyMap();
        while (this.running && !this.stack.isEmpty()) {
            redrawStack();
            Action action = bindingReader.readBinding(keyMap);
            handleAction(action);
        }
    }

    @Override
    public void close() {
        // Restore terminal state
        terminal.puts(InfoCmp.Capability.cursor_visible);
        if (savedAttributes != null) {
            terminal.setAttributes(savedAttributes);
        }
        terminal.puts(InfoCmp.Capability.keypad_local);
        terminal.writer().flush();
        super.close();
    }

    private KeyMap<Action> buildKeyMap() {
        KeyMap<Action> keyMap = new KeyMap<>();
        keyMap.bind(Action.DOWN_ROW, key(terminal, InfoCmp.Capability.key_down));
        keyMap.bind(Action.UP_ROW, key(terminal, InfoCmp.Capability.key_up));
        keyMap.bind(Action.RIGHT_COL, key(terminal, InfoCmp.Capability.key_right));
        keyMap.bind(Action.LEFT_COL, key(terminal, InfoCmp.Capability.key_left));
        keyMap.bind(Action.QUIT, "\u0004"); // Ctrl-D
        keyMap.bind(Action.SELECT, Utilities.enter_key);
        keyMap.bind(Action.SPACE, " ");
        return keyMap;
    }

    private void handleAction(Action action) {
        ExplainLevel current = stack.peek();
        if (current == null) return;

        // ── Popup-mode dispatch ──────────────────────────────────────
        if (current.argTablePopup != null) {
            handleArgTableAction(current, action);
            return;
        }
        if (current.panelPopup != null) {
            // SELECT or QUIT dismisses a panel popup
            if (action == Action.SELECT || action == Action.QUIT) {
                current.panelPopup = null;
            }
            return;
        }

        // ── Normal table navigation ──────────────────────────────────
        switch (action) {
            case DOWN_ROW:
                current.selectedRow = Math.min(current.selectedRow + 1, current.dataRowCount() - 1);
                break;

            case UP_ROW:
                current.selectedRow = Math.max(current.selectedRow - 1, 0);
                break;

            case RIGHT_COL:
                current.selectedCol = Math.min(current.selectedCol + 1, current.table.rows().get(0).size() - 1);
                break;

            case LEFT_COL:
                current.selectedCol = Math.max(current.selectedCol - 1, 0);
                break;

            case SELECT:
                handleSelect(current);
                break;

            case SPACE:
                handleCompile(current);
                break;

            case QUIT:
                popLevel();
                if (stack.isEmpty()) {
                    running = false;
                }
                break;
        }
    }

    /**
     * Handle key actions when an arg-table popup is active.
     * Up/Down navigate rows, Enter drills into the selected arg, Esc/QUIT dismisses.
     */
    private void handleArgTableAction(ExplainLevel current, Action action) {
        switch (action) {
            case DOWN_ROW:
                if (current.argEntries != null && !current.argEntries.isEmpty())
                    current.argTableSelectedRow = Math.min(current.argTableSelectedRow + 1, current.argEntries.size() - 1);
                break;
            case UP_ROW:
                current.argTableSelectedRow = Math.max(current.argTableSelectedRow - 1, 0);
                break;
            case SELECT:
                drillIntoSelectedArg(current);
                break;
            case QUIT:
                // Dismiss arg table popup, return to cell navigation
                current.argTablePopup = null;
                current.argEntries = null;
                current.argTableSelectedRow = 0;
                break;
            default:
                // Other keys ignored in arg-table mode
                break;
        }
    }

    /**
     * Drill into the currently selected arg in the arg-table popup.
     */
    private void drillIntoSelectedArg(ExplainLevel current) {
        if (current.argEntries == null || current.argTableSelectedRow >= current.argEntries.size()) return;
        Obj selectedArg = current.argEntries.get(current.argTableSelectedRow);
        current.argTablePopup = null;
        current.argEntries = null;
        current.argTableSelectedRow = 0;

        if (selectedArg.isCode()) {
            // Code arg → push new ExplainLevel
            int newOffsetX = current.offsetX + 4;
            int newOffsetY = current.offsetY + current.selectedRow + 3;
            pushLevel(selectedArg.asCode(), newOffsetX, newOffsetY, current.selectedRow, current.selectedCol);
        } else {
            // Non-code arg → show in panel popup
            current.panelPopup = CardUtil.popup(selectedArg);
        }
    }

    private void handleCompile(ExplainLevel current) {
        this.popLevel();
        this.pushLevel(current.code.resolve(noobj()).rewrite(), current.offsetX, current.offsetY, current.spawnRow, current.spawnCol);

    }

    // =====================================================================
    // Column-specific select handlers
    // =====================================================================

    private void handleSelect(ExplainLevel current) {
        String header = current.table.header(current.selectedCol);
        int row = current.selectedRow;

        switch (header) {
            case "op" -> handleOpSelect(current, row);
            case "dom" -> handleDomRngSelect(current, row, true);
            case "rng" -> handleDomRngSelect(current, row, false);
            case "args" -> handleArgsSelect(current, row);
            case "f" -> handleFSelect(current, row);
            case "desc" -> handleDescSelect(current, row);
            case "c_dom" -> handleCoefSelect(current, row, true);
            case "c_rng" -> handleCoefSelect(current, row, false);
            default -> { /* unhandled column */ }
        }
    }

    // ── op ───────────────────────────────────────────────────────────

    private void handleOpSelect(ExplainLevel current, int row) {
        Inst inst = getMetadataInst(current, row);
        if (inst == null) return;
        String title = Graphitty.string("{{b}}%s{{\\b}}{{m}}::T{{\\m}} refines {{b}}/m/inst{{\\b}}{{m}}::T{{\\m}}",
                inst.tid().basePath());
        current.panelPopup = CardUtil.popup(title, CardUtil.bodyOf(inst));
    }

    // ── dom / rng ─────────────────────────────────────────────────────

    private void handleDomRngSelect(ExplainLevel current, int row, boolean isDom) {
        int metaIdx = isDom ? 1 : 2;
        Type type = (Type) current.table.rowMetadata(row).get(metaIdx);
        if (type == null) return;
        String suffix = isDom ? " domain" : " range";
        String title;
        if (type.isBaseType()) {
            title = Graphitty.string("{{b}}%s{{\\b}}{{m}}::T{{\\m}}%s", type.vid(), suffix);
        } else {
            title = Graphitty.string("{{b}}%s{{\\b}}{{m}}::T{{\\m}} refines {{b}}%s{{\\b}}{{m}}::T{{\\m}}%s",
                    type.vid(), type.tid(), suffix);
        }
        current.panelPopup = CardUtil.popup(title, CardUtil.bodyOf(type));
    }

    // ── args ──────────────────────────────────────────────────────────

    private void handleArgsSelect(ExplainLevel current, int row) {
        Poly<?, ?> args = getMetadataArgs(current, row);
        if (args == null || args.isEmpty()) {
            String kind = (args != null && args.isRec()) ? "rec" : "lst";
            current.panelPopup = CardUtil.popup(kind + " arguments", "{{y}}no arguments{{X}}");
            return;
        }

        long count = args.count();
        if (count == 1) {
            // Single arg — show directly
            Obj singleArg = args.isLst()
                    ? args.lstValue().getFirst()
                    : args.recValue().values().iterator().next();
            if (singleArg.isCode()) {
                int newOffsetX = current.offsetX + 4;
                int newOffsetY = current.offsetY + current.selectedRow + 3;
                pushLevel(singleArg.asCode(), newOffsetX, newOffsetY, current.selectedRow, current.selectedCol);
            } else {
                current.panelPopup = CardUtil.popup(singleArg);
            }
        } else {
            // Multiple args — show selection table
            showArgTablePopup(current, args);
        }
    }

    private void showArgTablePopup(ExplainLevel current, Poly<?, ?> args) {
        List<Obj> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        if (args.isLst()) {
            int idx = 0;
            for (Obj arg : args.lstValue()) {
                entries.add(arg);
                labels.add("[" + idx++ + "] " + summarizeArg(arg));
            }
        } else {
            for (Map.Entry<Obj, Obj> kv : args.recValue().entrySet()) {
                entries.add(kv.getValue());
                labels.add(kv.getKey().uriValue().toString() + " => " + summarizeArg(kv.getValue()));
            }
        }

        current.argEntries = entries;
        current.argTableSelectedRow = 0;

        // Build a small TableWidget for arg selection
        TableWidget tbl = new TableWidget(List.of("arg"));
        for (String label : labels) {
            tbl.addRow(List.of(Highlighter.format(label)));
        }
        tbl.style()
                .border(Border.continuous.foreground("{{b}}"))
                .divider("{{b}}|")
                .pointer("{{r}}>")
                .applyStyle();
        current.argTablePopup = tbl;
    }

    private static String summarizeArg(Obj arg) {
        if (arg.isCall()) {
            return arg.asCall().insts().stream()
                    .map(i -> i.tid().name())
                    .reduce((a, b) -> a + "." + b).orElse("call");
        }
        return arg.toShortString().replace("\n", " ").trim();
    }

    // ── f ─────────────────────────────────────────────────────────────

    private void handleFSelect(ExplainLevel current, int row) {
        Inst inst = getMetadataInst(current, row);
        if (inst == null) return;

        if (!inst.hasf()) {
            current.panelPopup = CardUtil.popup("function",
                    "{{y}}No function registered for this instruction.{{X}}\n" +
                            "The instruction may be resolved at runtime or defined externally.");
            return;
        }

        if (inst.isJavaFunction()) {
            // <j> — Java lambda
            String className = inst.functionClassName();
            fURI basePath = inst.tid().basePath();
            Obj doc = Router.readFromSpace(basePath.addQ(QCollection.DOCQ));
            String docBody = (doc.isRec() && !QCollection.isNoDocs(doc))
                    ? new QCollection.Docs(doc.asRec()).description()
                    : "{{y}}no documentation available{{X}}";

            String body = Graphitty.string(
                    "{{m}}java implementation{{X}}\n" +
                            "{{w}}class:{{X}} %s\n" +
                            "{{w}}documentation:{{X}}\n%s",
                    className, docBody);
            current.panelPopup = CardUtil.popup(inst.tid().name() + " <j>", body);
        } else {
            // <m> — mtron code → push new ExplainLevel
            Obj mtronObj = inst.getMtronFunctionObj();
            if (mtronObj.isCode()) {
                int newOffsetX = current.offsetX + 4;
                int newOffsetY = current.offsetY + current.selectedRow + 3;
                pushLevel(mtronObj.asCode(), newOffsetX, newOffsetY, current.selectedRow, current.selectedCol);
            } else if (mtronObj.isNoObj()) {
                current.panelPopup = CardUtil.popup("function",
                        "{{y}}mtron function object is empty.{{X}}");
            } else {
                current.panelPopup = CardUtil.popup("function <m>",
                        mtronObj.toCleanString());
            }
        }
    }

    // ── desc ──────────────────────────────────────────────────────────

    private void handleDescSelect(ExplainLevel current, int row) {
        Inst inst = getMetadataInst(current, row);
        if (inst == null) return;
        Inst.Form form = Inst.Form.of(inst);
        current.panelPopup = CardUtil.popup(
                form.name() + " instruction",
                "{{w}}" + form.description + "{{X}}");
    }

    // ── c_dom / c_rng ─────────────────────────────────────────────────

    private void handleCoefSelect(ExplainLevel current, int row, boolean isDom) {
        int metaIdx = isDom ? 6 : 7;
        cInt c = (cInt) current.table.rowMetadata(row).get(metaIdx);
        if (c == null) return;
        String label = (isDom ? "domain coefficient" : "range coefficient");

        String sugar = c.toString();
        String desugar = "{" + (c.min() == null ? "" : c.min()) + "," + (c.max() == null ? "" : c.max()) + "}";
        String body = Graphitty.string(
                "{{w}}sugar:{{X}}  %s\n{{w}}range:{{X}} %s",
                Highlighter.format(sugar), Highlighter.format(desugar));
        current.panelPopup = CardUtil.popup(label, body);
    }

    // =====================================================================
    // Metadata helpers
    // =====================================================================

    /**
     * Get the Inst from metadata slot 0 (or 5, both store the Inst).
     */
    private Inst getMetadataInst(ExplainLevel level, int row) {
        Object meta = level.table.rowMetadata(row).get(0);
        return meta instanceof Inst i ? i : null;
    }

    /**
     * Get the args Poly from metadata slot 3.
     */
    private Poly<?, ?> getMetadataArgs(ExplainLevel level, int row) {
        Object meta = level.table.rowMetadata(row).get(3);
        return meta instanceof Poly<?, ?> p ? p : null;
    }

    private void pushLevel(Code code, int offsetX, int offsetY, int spawnRow, int spawnCol) {
        ExplainLevel level = new ExplainLevel(code, offsetX, offsetY, spawnRow, spawnCol);
        stack.push(level);
    }

    private void popLevel() {
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    /**
     * Redraw all levels from bottom to top with selection highlighting.
     * <p>
     * Rendering mechanics (absolute pane-bounded vs relative terminal-wide) are
     * handled transparently by the {@link WidgetCanvas} returned from
     * {@link #beginRedraw}; this method never inspects pane bounds directly.
     */
    private void redrawStack() {
        final List<ExplainLevel> levels = new ArrayList<>(stack);
        Collections.reverse(levels); // Draw bottom → top

        final WidgetCanvas canvas = beginRedraw(totalHeightUsed);

        for (int levelIdx = 0; levelIdx < levels.size(); levelIdx++) {
            final ExplainLevel level = levels.get(levelIdx);
            final boolean isTop = (levelIdx == levels.size() - 1);
            final ExplainLevel child = (levelIdx + 1 < levels.size()) ? levels.get(levelIdx + 1) : null;
            final boolean hasChild = child != null && child.spawnRow >= 0;
            final List<String> lines = level.profile.rowStrings();
            final String dimColor = isTop ? "" : "{{w}}";
            final String indent = " ".repeat(level.offsetX);

            for (int lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
                final String line = lines.get(lineIdx);
                final int dataLineIdx = lineIdx - 2;
                final boolean isDataRow = dataLineIdx >= 0 && dataLineIdx < level.dataRowCount();
                final boolean isSelected = isTop && isDataRow && dataLineIdx == level.selectedRow;
                final boolean isSpawn = !isTop && hasChild && isDataRow && dataLineIdx == child.spawnRow;

                final String content;
                if (isSelected) {
                    content = indent + highlightSelectedColumn(line, level.selectedCol, level);
                } else if (isSpawn) {
                    content = Graphitty.string(dimColor) + indent + highlightSpawnCell(line, child.spawnCol, level);
                } else if (isTop && isDataRow) {
                    content = indent + line;
                } else {
                    content = Graphitty.string(dimColor) + indent + line;
                }
                canvas.line(content);

                // Render popup overlay after the selected row (top level only)
                if (isTop && isSelected && hasPopup(level)) {
                    renderPopupLines(level, canvas, indent);
                }
            }

            if (hasChild) canvas.blankLine();
            if (isTop && stack.size() > 1) {
                canvas.line(Graphitty.string(indent + "  {{y}}[depth: %d]{{X}}", stack.size()));
            }
        }

        // Status / key-hint bar
        final ExplainLevel top = stack.peek();
        if (top != null) {
            final String mark = top.rewritten ? "{{[g]&w}} R {{X}}" : "{{[y]&w}} R {{X}}";
            final String hint;
            if (top.argTablePopup != null) {
                hint = mark + " {{w}}<^v>{{g}}:nav-arg {{w}}enter{{g}}:drill {{w}}ctrl-d{{g}}:back {{X}}";
            } else if (top.panelPopup != null) {
                hint = mark + " {{w}}enter/ctrl-d{{g}}:dismiss {{X}}";
            } else {
                hint = mark + " {{w}}ctrl-d{{g}}:back {{w}}<^v>{{g}}:nav {{w}}enter{{g}}:inspect {{w}}space{{g}}:rewrite {{X}}";
            }
            canvas.statusLine(hint);
        }

        totalHeightUsed = canvas.finish();
    }

    /**
     * Replace the divider before the selected column with the pointer indicator.
     * This gives a smooth visual effect as the selector moves across columns.
     * Uses the divider and pointer from the level's style configuration.
     */
    private String highlightSelectedColumn(String line, int selectedCol, ExplainLevel level) {
        // The table row format is: |col0|col1|col2|... (where | is the divider)
        // Replace the divider before the selected column with pointer

        String rawDivider = level.rawDivider;
        int dividerLen = rawDivider.length();

        if (dividerLen == 0) {
            return line;  // No divider configured
        }

        StringBuilder result = new StringBuilder();
        int dividerCount = 0;
        int i = 0;

        while (i < line.length()) {
            // Check if we're at a divider (match the raw divider characters)
            boolean atDivider = false;
            if (i + dividerLen <= line.length()) {
                String segment = line.substring(i, i + dividerLen);
                if (segment.equals(rawDivider)) {
                    atDivider = true;
                }
            }

            if (atDivider) {
                if (dividerCount == selectedCol) {
                    // Replace this divider with pointer
                    result.append(Graphitty.string(level.pointer));
                    // Skip any additional divider chars if divider is multi-char
                    if (dividerLen > 1) {
                        result.append(rawDivider.substring(1));  // Keep chars after first
                    }
                } else {
                    // Keep the original divider
                    result.append(rawDivider);
                }
                i += dividerLen;
                dividerCount++;
            } else {
                result.append(line.charAt(i));
                i++;
            }
        }

        return result.toString();
    }

    /**
     * Highlight a specific cell with background color (for showing spawn point in parent table).
     */
    private String highlightSpawnCell(String line, int colIndex, ExplainLevel level) {
        String rawDivider = level.rawDivider;
        int dividerLen = rawDivider.length();

        if (dividerLen == 0) {
            return line;
        }

        StringBuilder result = new StringBuilder();
        int dividerCount = 0;
        int i = 0;
        boolean inTargetCell = false;

        while (i < line.length()) {
            // Check if we're at a divider
            boolean atDivider = false;
            if (i + dividerLen <= line.length()) {
                String segment = line.substring(i, i + dividerLen);
                if (segment.equals(rawDivider)) {
                    atDivider = true;
                }
            }

            if (atDivider) {
                if (inTargetCell) {
                    // End of target cell - close highlight
                    result.append("{{X}}");
                    inTargetCell = false;
                }
                result.append(rawDivider);
                i += dividerLen;
                dividerCount++;

                // Start highlight after the divider before target cell
                if (dividerCount == colIndex + 1) {
                    result.append("{{[R]}}");
                    inTargetCell = true;
                }
            } else {
                result.append(line.charAt(i));
                i++;
            }
        }

        // Close highlight if we ended inside the cell
        if (inTargetCell) {
            result.append("{{X}}");
        }

        return Graphitty.string(result.toString());
    }

    @Override
    public String format() {
        return ""; // Rendering is handled by run()
    }

    // =====================================================================
    // Popup rendering helpers
    // =====================================================================

    /**
     * Render all active popup lines for a level (panel or arg-table),
     * indented relative to the level's offset.
     */
    private void renderPopupLines(ExplainLevel level, WidgetCanvas canvas, String indent) {
        if (level.argTablePopup != null) {
            renderArgTablePopupLines(level, canvas, indent);
        } else if (level.panelPopup != null) {
            renderPanelPopupLines(level, canvas, indent);
        }
    }

    private void renderPanelPopupLines(ExplainLevel level, WidgetCanvas canvas, String indent) {
        // Compute available width: terminal minus indent, popup padding, border, and margin
        int avail = terminal.getWidth() - level.offsetX - 6;
        if (avail > 20) level.panelPopup.maxWidth(avail);
        String formatted = level.panelPopup.format();
        String[] lines = formatted.split("\n", -1);
        String popupIndent = indent + "  ";
        for (String line : lines) {
            canvas.line(popupIndent + line);
        }
    }

    private void renderArgTablePopupLines(ExplainLevel level, WidgetCanvas canvas, String indent) {
        TableWidget tbl = level.argTablePopup;
        String popupIndent = indent + "  ";
        List<String> headers = tbl.headers;
        List<List<Object>> rows = tbl.rows();

        if (headers.isEmpty() && rows.isEmpty()) return;

        // Build formatted lines with selection highlighting
        List<String> popupLines = new ArrayList<>();
        final String divider = "{{b}}|";
        final String pointer = "{{r}}>";
        final int selRow = level.argTableSelectedRow;

        // Header line
        if (!headers.isEmpty()) {
            StringBuilder hdr = new StringBuilder(divider);
            for (String h : headers) {
                hdr.append(h).append("  ").append(divider);
            }
            popupLines.add(Graphitty.string(hdr.toString()));
            // Separator
            popupLines.add(Graphitty.string(divider + "─".repeat(Math.max(1, headers.get(0).length() + 1)) + divider));
        }

        // Data rows
        for (int r = 0; r < rows.size(); r++) {
            List<Object> row = rows.get(r);
            StringBuilder rb = new StringBuilder();
            if (r == selRow) {
                rb.append(pointer); // selection pointer
            } else {
                rb.append(divider);
            }
            for (Object cell : row) {
                rb.append(cell.toString());
                if (r == selRow) {
                    rb.append("  ").append(divider);
                } else {
                    rb.append("  ").append(divider);
                }
            }
            popupLines.add(Graphitty.string(rb.toString()));
        }

        // Hint line
        popupLines.add(Graphitty.string("{{w}}enter:select  esc:back{{X}}"));

        for (String line : popupLines) {
            canvas.line(popupIndent + line);
        }
    }

    /**
     * Returns true if the given level has any active popup overlay.
     */
    private boolean hasPopup(ExplainLevel level) {
        return level != null && (level.panelPopup != null || level.argTablePopup != null);
    }

    @Override
    public String toString() {
        return "Explain[code=" + rootCode + "]";
    }
}
