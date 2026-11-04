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

package studio.phaseshift.metatron.isa.m.sys.ui.tool;

import org.jline.keymap.KeyMap;
import org.jline.utils.InfoCmp;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.sys.ui.Border;
import studio.phaseshift.metatron.isa.m.sys.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.*;
import studio.phaseshift.metatron.isa.m.type.*;

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
public class ExplainTool extends AbstractTool<ExplainTool, ExplainTool.Action> {

    private static final GraphittyLogger LOG = Graphitty.log(ExplainTool.class);

    enum Action {
        QUIT, DOWN_ROW, UP_ROW, RIGHT_COL, LEFT_COL, SELECT, SPACE, STEP
    }

    /** Column index of the {@code args} cell — the one with the inline selector. */
    private static final int ARGS_COL = 3;

    /**
     * One frame in a poly drill-down: a selection table over a rec/lst's
     * elements, each of which may itself be code (→ nested ExplainLevel), a
     * poly (→ another frame), or a scalar (→ popup).
     */
    private static class ArgFrame {
        final TableWidget table;
        final List<Obj> entries;
        int selectedRow;

        ArgFrame(final TableWidget table, final List<Obj> entries) {
            this.table = table;
            this.entries = entries;
        }
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
        int selectedArg;         // -1 = column mode; >= 0 = the arg under the inline selector
        final List<Integer> argPath = new ArrayList<>(); // descent path within the selected arg (empty = arg level)
        int windowedRow;         // the row whose args cell currently holds the inline selector (-1 = none)
        final List<String> savedScreen; // What was underneath this level
        final String pointer;      // The pointer character with color (e.g., "{{r}}>")
        final String divider;      // The divider string with color (e.g., "{{r}}|")
        final String rawDivider;   // The divider without color codes (e.g., "|")
        // Track which cell in the PARENT spawned this level (for visual connector)
        final int spawnRow;        // Row in parent that spawned this (-1 if root)
        final int spawnCol;        // Column in parent that spawned this (-1 if root)
        final int spawnArg;        // Arg index in the parent's args cell that spawned this (-1 if not an arg)

        // ── Popup overlay state ──────────────────────────────────────
        PanelWidget panelPopup;                              // Info panel popup (docs, type, form desc, coeff)
        final Deque<ArgFrame> argFrames = new ArrayDeque<>(); // Poly drill-down stack (args / collection navigation)

        ExplainLevel(final Code code, final int offsetX, final int offsetY, final int spawnRow, final int spawnCol, final int spawnArg) {
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
            this.selectedArg = -1; // column mode until the args column is entered
            this.windowedRow = -1; // no inline selector rendered yet
            this.savedScreen = new ArrayList<>();
            // Get divider and pointer from table style
            this.divider = this.table.getStyle().divider();
            this.rawDivider = Graphitty.strip(this.divider);
            this.pointer = this.table.getStyle().pointer().isEmpty() ? "{{r}}>" : this.table.getStyle().pointer();
            this.spawnRow = spawnRow;
            this.spawnCol = spawnCol;
            this.spawnArg = spawnArg;
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
    private String lastFrameMarkup = "";  // The last full frame, settled into the transcript on exit

    // ── Single-step rewrite state (ctrl+space) ─────────────────────
    private Map<String, List<Inst>> rewritesByCategory;  // category → ordered rewrites (lazily built)
    private List<String> categoryNames;                  // ordered categories
    private int categoryIndex = 0;                       // focused category
    private boolean categoryMode = false;                // category list open
    private int stepIndex = 0;                           // next rewrite in the focused category
    private String stepNote = "";                        // "[i/n] name" shown in the status bar

    public ExplainTool(final Code code) {
        this.rootCode = code;
    }

    @Override
    protected void onEnter() {
        this.terminal.writer().print("\n");
        this.pushLevel(this.rootCode, 0, 0, -1, -1, -1);   // the root has no parent spawn position
    }

    @Override
    protected KeyMap<Action> keyMap() {
        return this.buildKeyMap();
    }

    @Override
    protected boolean handle(final Action action) {
        this.handleAction(action);
        return this.running() && !this.stack.isEmpty();
    }

    @Override
    protected void redraw() {
        this.redrawStack();
    }

    @Override
    protected String finalFrame() {
        return this.lastFrameMarkup;
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
        keyMap.bind(Action.STEP, KeyMap.ctrl(' ')); // ctrl+space
        return keyMap;
    }

    private void handleAction(Action action) {
        ExplainLevel current = stack.peek();
        if (current == null) return;

        // ── Popup-mode dispatch ──────────────────────────────────────
        if (current.panelPopup != null) {
            // SELECT, QUIT, or UP_ROW dismisses a panel popup, returning to whatever
            // was underneath (the arg-frame stack, if any)
            if (action == Action.SELECT || action == Action.QUIT || action == Action.UP_ROW) {
                current.panelPopup = null;
            }
            return;
        }
        if (this.categoryMode) {
            handleCategoryAction(current, action);
            return;
        }
        if (!current.argFrames.isEmpty()) {
            handleArgTableAction(current, action);
            return;
        }

        // ── Normal table navigation ──────────────────────────────────
        switch (action) {
            case DOWN_ROW:
                current.selectedRow = Math.min(current.selectedRow + 1, current.dataRowCount() - 1);
                current.selectedArg = -1;   // a row change exits the inline arg selector
                current.argPath.clear();
                break;

            case UP_ROW:
                if (current.selectedRow == 0 && stack.size() > 1) {
                    popLevel();   // top row of a nested window: back out like ctrl-d
                } else {
                    current.selectedRow = Math.max(current.selectedRow - 1, 0);
                    current.selectedArg = -1;
                    current.argPath.clear();
                }
                break;

            case RIGHT_COL:
                moveColumn(current, +1);
                break;

            case LEFT_COL:
                moveColumn(current, -1);
                break;

            case SELECT:
                handleSelect(current);
                break;

            case SPACE:
                handleCompile(current);
                break;

            case STEP:
                handleStep(current);
                break;

            case QUIT:
                popLevel();
                if (stack.isEmpty()) {
                    this.stop();
                }
                break;
        }
    }

    /**
     * Move one column left ({@code dir = -1}) or right ({@code dir = +1}). The args
     * column is an in-order cursor over the args tree: {@code rng → divider → arg0 →
     * elem0 → … → arg n-1 → f}. The divider ({@code selectedArg == -1}) is the args
     * Poly as a whole; RIGHT descends into a non-empty poly's first element, LEFT
     * ascends back out. Entering from the left lands on the divider, from the right
     * on the last arg.
     */
    private void moveColumn(final ExplainLevel current, final int dir) {
        final int lastCol = current.table.rows().get(0).size() - 1;
        if (current.selectedCol == ARGS_COL) {
            final Poly<?, ?> args = getMetadataArgs(current, current.selectedRow);
            if (dir > 0) moveArgNext(current, args, lastCol);
            else moveArgPrev(current, args);
            return;
        }
        current.selectedCol = dir > 0
                ? Math.min(current.selectedCol + 1, lastCol)
                : Math.max(current.selectedCol - 1, 0);
        if (current.selectedCol == ARGS_COL) {
            final int n = argCount(current, current.selectedRow);
            current.selectedArg = dir > 0 ? -1 : (n >= 1 ? n - 1 : -1);
            current.argPath.clear();
        }
    }

    private void moveArgNext(final ExplainLevel current, final Poly<?, ?> args, final int lastCol) {
        final int n = args == null ? 0 : (int) args.count();
        if (current.selectedArg < 0) {
            if (n == 0) current.selectedCol = Math.min(current.selectedCol + 1, lastCol);
            else { current.selectedArg = 0; current.argPath.clear(); }
            return;
        }
        final Obj node = atPath(args, current.selectedArg, current.argPath);
        if (node.isPoly() && !node.<Poly<?, ?>>as().isEmpty()) {
            current.argPath.add(0);   // descend into the first element
            return;
        }
        if (nextSibling(current, args)) return;
        current.selectedArg = -1;      // walked off the args → f
        current.argPath.clear();
        current.selectedCol = Math.min(current.selectedCol + 1, lastCol);
    }

    private boolean nextSibling(final ExplainLevel current, final Poly<?, ?> args) {
        while (!current.argPath.isEmpty()) {
            final int lastIdx = current.argPath.size() - 1;
            final int last = current.argPath.get(lastIdx);
            final Obj parent = atPath(args, current.selectedArg, current.argPath.subList(0, lastIdx));
            if (last < (int) parent.<Poly<?, ?>>as().count() - 1) { current.argPath.set(lastIdx, last + 1); return true; }
            current.argPath.remove(lastIdx);
        }
        if (current.selectedArg < (int) args.count() - 1) { current.selectedArg++; return true; }
        return false;
    }

    private void moveArgPrev(final ExplainLevel current, final Poly<?, ?> args) {
        final int n = args == null ? 0 : (int) args.count();
        if (current.selectedArg < 0) {
            current.selectedCol = Math.max(current.selectedCol - 1, 0);   // divider → rng
            return;
        }
        if (!current.argPath.isEmpty()) {
            final int lastIdx = current.argPath.size() - 1;
            final int last = current.argPath.get(lastIdx);
            if (last > 0) current.argPath.set(lastIdx, last - 1);
            else current.argPath.remove(lastIdx);
            return;
        }
        if (current.selectedArg > 0) current.selectedArg--;
        else current.selectedArg = -1;
    }

    private int argCount(final ExplainLevel level, final int row) {
        final Poly<?, ?> args = getMetadataArgs(level, row);
        return args == null ? 0 : (int) args.count();
    }

    private static Obj argAt(final Poly<?, ?> args, final int idx) {
        if (args.isLst()) {
            final List<Obj> list = args.lstValue();
            return idx >= 0 && idx < list.size() ? list.get(idx) : noobj();
        }
        final List<Obj> values = new ArrayList<>(args.recValue().values());
        return idx >= 0 && idx < values.size() ? values.get(idx) : noobj();
    }

    private static Obj atPath(final Poly<?, ?> args, final int argIdx, final List<Integer> path) {
        Obj node = argAt(args, argIdx);
        for (final int idx : path) {
            if (!node.isPoly()) return noobj();
            node = argAt(node.as(), idx);
        }
        return node;
    }

    /**
     * Handle key actions when an arg-table popup is active.
     * Up/Down navigate rows, Enter drills into the selected arg, Esc/QUIT dismisses.
     */
    private void handleArgTableAction(ExplainLevel current, Action action) {
        final ArgFrame frame = current.argFrames.peek();
        if (frame == null) return;
        switch (action) {
            case DOWN_ROW:
                if (!frame.entries.isEmpty())
                    frame.selectedRow = Math.min(frame.selectedRow + 1, frame.entries.size() - 1);
                break;
            case UP_ROW:
                frame.selectedRow = Math.max(frame.selectedRow - 1, 0);
                break;
            case SELECT:
                drillIntoSelectedArg(current, frame);
                break;
            case QUIT:
                // Pop this frame, return to the outer frame or cell navigation
                current.argFrames.pop();
                break;
            default:
                // Other keys ignored in arg-table mode
                break;
        }
    }

    /**
     * Drill into the currently selected arg in an arg-frame.
     */
    private void drillIntoSelectedArg(ExplainLevel current, ArgFrame frame) {
        if (frame.entries == null || frame.selectedRow >= frame.entries.size()) return;
        final Obj selectedArg = frame.entries.get(frame.selectedRow);
        // Keep the frame on the stack: a pushed code level or a scalar popup
        // must return to this frame on back / dismiss.
        drillIntoObj(current, selectedArg);
    }

    /**
     * What a drill-down argument is, so the routing is one switch and the
     * classification is unit-testable without a terminal.
     */
    enum DrillKind {CODE, POLY, EMPTY_POLY, SCALAR}

    static DrillKind drillKind(final Obj obj) {
        if (obj.isObjCall())
            return DrillKind.CODE;
        if (obj.isPoly()) {
            final Poly<?, ?> poly = obj.as();
            return poly.isEmpty() ? DrillKind.EMPTY_POLY : DrillKind.POLY;
        }
        return DrillKind.SCALAR;
    }

    /**
     * Recursively drill into an argument value. A code value pushes a full
     * {@link ExplainLevel}; a non-empty poly pushes an arg-selection frame (so
     * its elements — which may themselves be code — are one Enter away); an
     * empty poly or a scalar (an empty code is a noobj, which is scalar) shows
     * a popup.
     */
    private void drillIntoObj(final ExplainLevel current, final Obj obj) {
        switch (drillKind(obj)) {
            case CODE -> {
                final int newOffsetX = current.offsetX + 4;
                final int newOffsetY = current.offsetY + current.selectedRow + 3;
                final int spawnArg = current.selectedCol == ARGS_COL ? current.selectedArg : -1;
                pushLevel(obj.asCode(), newOffsetX, newOffsetY, current.selectedRow, current.selectedCol, spawnArg);
            }
            case POLY -> showArgTablePopup(current, obj.as());
            case EMPTY_POLY ->
                    current.panelPopup = CardUtil.popup((obj.isRec() ? "rec" : "lst") + " arguments", "{{y}}empty{{X}}");
            case SCALAR -> current.panelPopup = CardUtil.popup(obj);
        }
    }

    private void handleCompile(ExplainLevel current) {
        this.categoryMode = false;
        this.stepNote = "";
        this.stepIndex = 0;
        this.popLevel();
        this.pushLevel(current.code.resolve(noobj()).rewrite(), current.offsetX, current.offsetY, current.spawnRow, current.spawnCol, current.spawnArg);
    }

    /**
     * ctrl+space in normal mode: open the rewrite category list (rules grouped by
     * {@code tid().retract(1)}), from which space runs a whole category and
     * ctrl+space steps through it one rule at a time.
     */
    private void handleStep(ExplainLevel current) {
        this.buildRewriteCategories();
        if (this.categoryNames.isEmpty()) {
            current.panelPopup = CardUtil.popup("rewrite", "{{y}}no rewrite rules registered{{X}}");
            return;
        }
        this.categoryMode = true;
        this.categoryIndex = 0;
        this.stepIndex = 0;
        this.stepNote = "";
    }

    /**
     * Lazily group the rewriter's rules by their URI category ({@code tid().retract(1)}).
     */
    private void buildRewriteCategories() {
        if (this.categoryNames != null) return;
        this.rewritesByCategory = new TreeMap<>();
        for (final Inst r : Machine.current().compiler().rewriter().rewrites()) {
            this.rewritesByCategory.computeIfAbsent(categoryOf(r), k -> new ArrayList<>()).add(r);
        }
        this.rewritesByCategory.values().forEach(l -> l.sort(Comparator.comparing(r -> r.tid().name())));
        this.categoryNames = new ArrayList<>(this.rewritesByCategory.keySet());
        this.categoryIndex = 0;
    }

    private static String categoryOf(final Inst rewrite) {
        return rewrite.tid().retract(1).basePath().toString();
    }

    /**
     * Category-mode key dispatch: navigate, run a category, or step through one.
     */
    private void handleCategoryAction(final ExplainLevel current, final Action action) {
        switch (action) {
            case DOWN_ROW -> this.categoryIndex = Math.min(this.categoryIndex + 1, this.categoryNames.size() - 1);
            case UP_ROW -> this.categoryIndex = Math.max(this.categoryIndex - 1, 0);
            case SPACE -> runFocusedCategory(current);
            case STEP -> stepFocusedCategory(current);
            case QUIT -> {
                this.categoryMode = false;
                this.stepNote = "";
            }
            default -> {
            }
        }
    }

    /**
     * ctrl+space in category mode: apply the next rule in the focused category.
     */
    private void stepFocusedCategory(final ExplainLevel current) {
        final List<Inst> rewrites = this.rewritesByCategory.get(this.categoryNames.get(this.categoryIndex));
        if (rewrites == null || rewrites.isEmpty()) return;
        if (this.stepIndex >= rewrites.size()) this.stepIndex = 0;
        final Inst rewrite = rewrites.get(this.stepIndex);
        final String name = rewrite.tid().name();
        final Obj out = rewrite.apply(current.code.resolve(noobj()));
        if (out.isCode()) {
            this.popLevel();
            this.pushLevel(out.asCode(), current.offsetX, current.offsetY, current.spawnRow, current.spawnCol, current.spawnArg);
        }
        this.stepIndex++;
        this.stepNote = "  {{y}}[" + this.stepIndex + "/" + rewrites.size() + "] " + name + "{{X}}";
    }

    /**
     * space in category mode: run the focused category's rules to a fixpoint.
     */
    private void runFocusedCategory(final ExplainLevel current) {
        final List<Inst> rewrites = this.rewritesByCategory.get(this.categoryNames.get(this.categoryIndex));
        final Code rewritten = applyFixpoint(current.code.resolve(noobj()), rewrites);
        this.popLevel();
        this.pushLevel(rewritten, current.offsetX, current.offsetY, current.spawnRow, current.spawnCol, current.spawnArg);
        this.categoryMode = false;
        this.stepNote = "";
    }

    /**
     * The full rewrite's fixpoint loop, scoped to one category's rules (top-level only, no branch descent).
     */
    private static Code applyFixpoint(final Code code, final List<Inst> rewrites) {
        Code current = code;
        int stable = 0;
        while (stable < 2) {
            final Code before = current;
            for (final Inst r : rewrites) {
                final Obj out = r.apply(current);
                if (out.isCode()) current = out.asCode();
            }
            if (current.hashCode() == before.hashCode()) stable++;
            else stable = 0;
        }
        return current;
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
        String title = Graphitty.string("{{b}}%s{{/b}}{{m}}::T{{/m}} refines {{b}}/m/inst{{/b}}{{m}}::T{{/m}}",
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
            title = Graphitty.string("{{b}}%s{{/b}}{{m}}::T{{/m}}%s", type.vid(), suffix);
        } else {
            title = Graphitty.string("{{b}}%s{{/b}}{{m}}::T{{/m}} refines {{b}}%s{{/b}}{{m}}::T{{/m}}%s",
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
        if (current.selectedArg < 0) {
            current.panelPopup = CardUtil.popup(args);   // the args Poly as a whole
            return;
        }
        final Obj node = atPath(args, current.selectedArg, current.argPath);
        if (node.isPoly()) {
            current.panelPopup = CardUtil.popup(node);   // a poly node: Enter shows it as a whole
        } else {
            drillIntoObj(current, node);
        }
    }

    private void showArgTablePopup(ExplainLevel current, Poly<?, ?> args) {
        final List<Obj> entries = new ArrayList<>();
        final List<String> labels = new ArrayList<>();

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

        // Build a small TableWidget for arg selection
        final TableWidget tbl = new TableWidget(List.of("arg"));
        for (String label : labels) {
            tbl.addRow(List.of(Highlighter.format(label)));
        }
        tbl.style()
                .border(Border.continuous.foreground("{{b}}"))
                .divider("{{b}}|")
                .pointer("{{r}}>")
                .applyStyle();
        current.argFrames.push(new ArgFrame(tbl, entries));
    }

    private static String summarizeArg(Obj arg) {
        if (arg.isCall()) {
            return arg.asCall().insts().stream()
                    .map(i -> i.tid().name())
                    .reduce((a, b) -> a + "." + b).orElse("call");
        }
        if (arg.isRec()) {
            return "{…" + arg.asRec().count() + " rels}";
        }
        if (arg.isLst()) {
            return "[…" + arg.asLst().count() + " objs]";
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
            Obj doc = Machine.read(basePath.addQ(QCollection.DOCQ));
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
            // <m> — mtron code → push a full ExplainLevel (or drill into a poly function obj)
            Obj mtronObj = inst.getMtronFunctionObj();
            if (mtronObj.isNoObj()) {
                current.panelPopup = CardUtil.popup("function",
                        "{{y}}mtron function object is empty.{{X}}");
            } else {
                drillIntoObj(current, mtronObj);
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
        cInt c = cInt.of(current.table.rowMetadata(row).get(metaIdx).toString());
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

    /**
     * Before rendering the top level, overwrite the selected row's args cell: the
     * windowed inline selector when the args column is armed, otherwise the plain
     * fixed-width summary. Any previously windowed row is restored first, so a
     * selector is never left behind when the selection moves to another row.
     */
    private void applyArgsCell(final ExplainLevel level) {
        final int row = level.selectedRow;
        if (level.windowedRow >= 0 && level.windowedRow != row) {
            restoreArgsCell(level, level.windowedRow);
            level.windowedRow = -1;
        }
        final Poly<?, ?> args = getMetadataArgs(level, row);
        if (args == null) return;
        final String base = args.elements().allMatch(x -> x.isResolved(true)) ? "{{g}}" : "{{y}}";
        final boolean window = level.selectedCol == ARGS_COL && level.selectedArg >= 0;
        final String body;
        if (window && !level.argPath.isEmpty()) {
            body = argsCellDescended(args, level.selectedArg, level.argPath, ProfileTool.ARGS_WIDTH, base, level.pointer);
        } else if (window) {
            body = argsCell(ProfileTool.argTokens(args), level.selectedArg, ProfileTool.ARGS_WIDTH, base, level.pointer);
        } else {
            body = ProfileTool.argsSummary(args);
        }
        level.table.setCell(row, ARGS_COL, base + body);
        level.windowedRow = window ? row : -1;
    }

    private void restoreArgsCell(final ExplainLevel level, final int row) {
        final Poly<?, ?> args = getMetadataArgs(level, row);
        if (args == null) return;
        final String base = args.elements().allMatch(x -> x.isResolved(true)) ? "{{g}}" : "{{y}}";
        level.table.setCell(row, ARGS_COL, base + ProfileTool.argsSummary(args));
    }

    /** Underline the arg that spawned a child window, marking the parent's spawn point. */
    private void applySpawnUnderline(final ExplainLevel level, final int row, final int arg) {
        final Poly<?, ?> args = getMetadataArgs(level, row);
        if (args == null) return;
        final String base = args.elements().allMatch(x -> x.isResolved(true)) ? "{{g}}" : "{{y}}";
        level.table.setCell(row, ARGS_COL, base + argsCellSpawn(ProfileTool.argTokens(args), arg, ProfileTool.ARGS_WIDTH));
    }

    /**
     * The args cell as a fixed-width window. The selector (color + character from the
     * style's pointer) replaces the leading slot ({@code sel == 0}) or the space after
     * the comma before token {@code sel} ({@code sel > 0}), so no token ever shifts
     * column. An ellipsis marks arguments clipped off each side, and the window slides
     * so the selected argument (plus as much of the right side as fits) stays visible.
     * Returns the cell body WITHOUT the base-color prefix (the caller prepends it);
     * width is measured with {@link Highlighter#visualLength}, so markup adds no columns.
     */
    static String argsCell(final List<String> tokens, final int sel, final int width,
                           final String base, final String selector) {
        final int n = tokens.size();
        if (n == 0) return " ".repeat(width);
        final String ptr = selector + base;   // the selector, then back to the base color
        int left = sel, right = sel;
        while (right + 1 < n && windowCost(tokens, left, right + 1) <= width) right++;
        while (left - 1 >= 0 && windowCost(tokens, left - 1, right) <= width) left--;

        final StringBuilder sb = new StringBuilder();
        sb.append(sel == left ? ptr : " ");   // slot: the selector points at the first shown token
        if (left > 0) sb.append('…');          // leading ellipsis (1 column, like the divider's clip)
        for (int i = left; i <= right; i++) {
            if (i > left) {
                sb.append(',');
                sb.append(sel == i ? ptr : " ");   // ", " or ",selector"
            }
            sb.append(tokens.get(i));
        }
        if (right < n - 1) sb.append('…');     // trailing ellipsis (1 column)
        final String result = sb.toString();
        final int len = Highlighter.visualLength(result);
        if (len > width) return clipVisual(result, width);   // a single over-long token: never reflow
        if (len < width) return result + " ".repeat(width - len);
        return result;
    }

    /**
     * The args cell when the selector is descended into a poly argument: the selected
     * arg is rendered via {@link #renderPoly} (brackets + commas with the selector
     * inside), every other arg as its summary. No selector at the slot. Width is
     * measured with {@link Highlighter#visualLength}.
     */
    static String argsCellDescended(final Poly<?, ?> args, final int argIdx, final List<Integer> path,
                                    final int width, final String base, final String selector) {
        final List<String> tokens = ProfileTool.argTokens(args);
        final StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append(", ");
            if (i == argIdx) sb.append(renderPoly(argAt(args, i).as(), path, base, selector));
            else sb.append(tokens.get(i));
        }
        final String result = sb.toString();
        final int len = Highlighter.visualLength(result);
        if (len > width) return clipVisual(result, width);
        if (len < width) return result + " ".repeat(width - len);
        return result;
    }

    /**
     * Render a poly (lst/rec) with the selector descended into it along {@code path}.
     * At the leaf of the path the selector replaces the opening bracket (element 0) or
     * a comma (later elements); deeper levels recurse without a selector, so exactly one
     * {@code >} is drawn, at the selected element.
     */
    static String renderPoly(final Poly<?, ?> poly, final List<Integer> path, final String base, final String selector) {
        final String ptr = selector + base;
        final String open = poly.isLst() ? "[" : "{";
        final String close = poly.isLst() ? "]" : "}";
        final int sel = path.get(0);
        final boolean leaf = path.size() == 1;
        final List<String> tokens = ProfileTool.argTokens(poly);
        final StringBuilder sb = new StringBuilder();
        sb.append(leaf && sel == 0 ? ptr : open);
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append(leaf && sel == i ? ptr : ",");
            if (i == sel && !leaf && argAt(poly, i).isPoly()) {
                sb.append(renderPoly(argAt(poly, i).as(), path.subList(1, path.size()), base, selector));
            } else {
                sb.append(tokens.get(i));
            }
        }
        sb.append(close);
        return sb.toString();
    }

    /** Clip to {@code width} visual columns, carrying {@code {{…}}} markup spans through verbatim. */
    private static String clipVisual(final String s, final int width) {
        final StringBuilder sb = new StringBuilder();
        int visible = 0;
        int i = 0;
        while (i < s.length() && visible < width) {
            if (i + 1 < s.length() && s.charAt(i) == '{' && s.charAt(i + 1) == '{') {
                final int end = s.indexOf("}}", i);
                if (end >= 0) {
                    sb.append(s, i, end + 2);
                    i = end + 2;
                    continue;
                }
            }
            sb.append(s.charAt(i));
            visible++;
            i++;
        }
        return sb.toString();
    }

    /**
     * The args cell for a parent window whose arg was drilled into: the selected arg is
     * wrapped in {@code {{_}}…{{/_}}} (underline) instead of a selector, so the spawn
     * point is marked without a red background. No selector; the leading slot is a space.
     * Width is measured with {@link Highlighter#visualLength}, so the markup adds no columns.
     */
    static String argsCellSpawn(final List<String> tokens, final int spawnArg, final int width) {
        final int n = tokens.size();
        if (n == 0) return " ".repeat(width);
        int left = spawnArg, right = spawnArg;
        while (right + 1 < n && windowCost(tokens, left, right + 1) <= width) right++;
        while (left - 1 >= 0 && windowCost(tokens, left - 1, right) <= width) left--;

        final StringBuilder sb = new StringBuilder();
        sb.append(' ');   // leading slot (no selector in the spawn state)
        if (left > 0) sb.append('…');
        for (int i = left; i <= right; i++) {
            if (i > left) sb.append(", ");
            if (i == spawnArg) {
                sb.append("{{_}}").append(tokens.get(i)).append("{{/_}}");
            } else {
                sb.append(tokens.get(i));
            }
        }
        if (right < n - 1) sb.append('…');
        final String result = sb.toString();
        final int len = Highlighter.visualLength(result);
        if (len > width) return clipVisual(result, width);
        if (len < width) return result + " ".repeat(width - len);
        return result;
    }

    private static int windowCost(final List<String> tokens, final int left, final int right) {
        int cost = 1;   // the leading pointer slot
        for (int i = left; i <= right; i++) {
            cost += Highlighter.visualLength(tokens.get(i));
            if (i < right) cost += 2;    // ", "
        }
        if (left > 0) cost += 1;                 // "…"
        if (right < tokens.size() - 1) cost += 1; // "…"
        return cost;
    }

    private void pushLevel(Code code, int offsetX, int offsetY, int spawnRow, int spawnCol, int spawnArg) {
        ExplainLevel level = new ExplainLevel(code, offsetX, offsetY, spawnRow, spawnCol, spawnArg);
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

        final WidgetCanvas canvas = this.canvas();

        for (int levelIdx = 0; levelIdx < levels.size(); levelIdx++) {
            final ExplainLevel level = levels.get(levelIdx);
            final boolean isTop = (levelIdx == levels.size() - 1);
            final ExplainLevel child = (levelIdx + 1 < levels.size()) ? levels.get(levelIdx + 1) : null;
            final boolean hasChild = child != null && child.spawnRow >= 0;
            if (isTop) {
                applyArgsCell(level);
            } else if (hasChild && child.spawnCol == ARGS_COL && child.spawnArg >= 0) {
                applySpawnUnderline(level, child.spawnRow, child.spawnArg);
            }
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
                    if (child.spawnCol == ARGS_COL && child.spawnArg >= 0) {
                        content = Graphitty.string(dimColor) + indent + line;   // the underline is already in the args cell
                    } else {
                        content = Graphitty.string(dimColor) + indent + highlightSpawnCell(line, child.spawnCol, level);
                    }
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

            if (isTop && this.categoryMode) {
                renderCategoryList(canvas, indent);
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
            if (this.categoryMode) {
                hint = mark + " {{w}}<^v>{{g}}:category {{w}}space{{g}}:run-category {{w}}ctrl+space{{g}}:step {{w}}ctrl-d{{g}}:back {{X}}" + this.stepNote;
            } else if (top.panelPopup != null) {
                hint = mark + " {{w}}enter/ctrl-d{{g}}:dismiss {{X}}";
            } else if (!top.argFrames.isEmpty()) {
                hint = mark + " {{w}}<^v>{{g}}:nav-arg {{w}}enter{{g}}:drill {{w}}ctrl-d{{g}}:back {{X}}";
            } else {
                hint = mark + " {{w}}ctrl-d{{g}}:back {{w}}<^v>{{g}}:nav {{w}}enter{{g}}:inspect {{w}}space{{g}}:rewrite {{w}}ctrl+space{{g}}:step {{X}}" + this.stepNote;
            }
            canvas.statusLine(hint);
        }

        this.settle(canvas);
        this.lastFrameMarkup = canvas.frameText();
    }

    /**
     * Replace the divider before the selected column with the pointer indicator.
     * This gives a smooth visual effect as the selector moves across columns.
     * Uses the divider and pointer from the level's style configuration.
     */
    private String highlightSelectedColumn(String line, int selectedCol, ExplainLevel level) {
        // The args column's pointer lives inside the cell (the inline arg selector),
        // not at the divider — so an armed selector keeps the cell boundary intact.
        if (selectedCol == ARGS_COL && level.selectedArg >= 0) {
            return line;
        }
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
                    result.append("{{[r]}}");
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
        if (level.panelPopup != null) {
            renderPanelPopupLines(level, canvas, indent);
        } else if (!level.argFrames.isEmpty()) {
            renderArgTablePopupLines(level, canvas, indent);
        }
    }

    /**
     * Render the rewrite-category list (ctrl+space), with the focused category
     * marked by a red pointer and each category's rule count beside it.
     */
    private void renderCategoryList(WidgetCanvas canvas, String indent) {
        final String popupIndent = indent + "  ";
        canvas.line(Graphitty.string(popupIndent + "{{w}}rewrite categories:{{X}}"));
        for (int i = 0; i < this.categoryNames.size(); i++) {
            final String category = this.categoryNames.get(i);
            final int count = this.rewritesByCategory.get(category).size();
            final String marker = (i == this.categoryIndex) ? "{{r}}>" : " ";
            canvas.line(Graphitty.string(popupIndent + marker + " {{b}}" + category + "{{X}} {{y}}(" + count + "){{X}}"));
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
        final ArgFrame frame = level.argFrames.peek();
        if (frame == null) return;
        TableWidget tbl = frame.table;
        String popupIndent = indent + "  ";
        List<String> headers = tbl.headers();
        List<List<Object>> rows = tbl.rows();

        if (headers.isEmpty() && rows.isEmpty()) return;

        // Build formatted lines with selection highlighting
        List<String> popupLines = new ArrayList<>();
        final String divider = "{{b}}|";
        final String pointer = "{{r}}>";
        final int selRow = frame.selectedRow;

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
        return level != null && (level.panelPopup != null || !level.argFrames.isEmpty());
    }

    @Override
    public String toString() {
        return "Explain[code=" + rootCode + "]";
    }
}
