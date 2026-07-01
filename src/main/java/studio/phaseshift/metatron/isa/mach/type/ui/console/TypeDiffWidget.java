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

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Attributes;
import org.jline.utils.InfoCmp;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.AbstractWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Table;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Utilities;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.WidgetCanvas;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import static org.jline.keymap.KeyMap.key;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * TypeDiffWidget — cloned from Explain.  Interactive table display that walks
 * an instance-vs-type mismatch and lets the user drill into nested failures.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TypeDiffWidget extends AbstractWidget<TypeDiffWidget> {

    /* ---- status markers ---- */
    private static final String GOOD = "{{g}}O{{X}}";
    private static final String FAIL = "{{r}}X{{X}}";
    private static final String MISSING = "noobj";

    /* ---- key map (exactly Explain's Action set) ---- */
    private enum Action {
        QUIT, DOWN_ROW, UP_ROW, SELECT
    }

    /* ---- one level in the drill-down stack (mirrors Explain.ExplainLevel) ---- */
    private static class DiffLevel {
        final String prefix;          // path that this level roots at ("" = top)
        final List<DiffRow> rows;     // rows visible at this level
        final Table table;
        final int offsetX;
        final int offsetY;
        int selectedRow;
        final int spawnRow;
        final int spawnCol;

        DiffLevel(final String prefix, final List<DiffRow> allRows,
                  final int offsetX, final int offsetY,
                  final int spawnRow, final int spawnCol) {
            this.prefix = prefix;
            this.rows = filterVisible(prefix, allRows);
            this.table = buildTable(this.rows);
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.selectedRow = 0;
            this.spawnRow = spawnRow;
            this.spawnCol = spawnCol;
        }

        int dataRowCount() {
            return this.rows.size();
        }
    }

    /* ---- row record ---- */
    private record DiffRow(String path, String actual, String status, String expected) {
    }

    /* ---- static helpers (mirrors Explain's filterVisible / hasChildren) ---- */

    private static List<DiffRow> filterVisible(final String prefix, final List<DiffRow> all) {
        if (prefix.isEmpty()) {
            return all.stream()
                    .filter(r -> {
                        final int lastSlash = r.path().lastIndexOf('/');
                        if (lastSlash < 0) return true;
                        final String parent = r.path().substring(0, lastSlash);
                        return all.stream().noneMatch(p -> p.path().equals(parent));
                    })
                    .toList();
        }
        final String childPrefix = prefix + "/";
        return all.stream()
                .filter(r -> r.path().startsWith(childPrefix)
                        && r.path().indexOf('/', childPrefix.length()) < 0)
                .toList();
    }

    private static boolean hasChildren(final String rowPath, final List<DiffRow> all) {
        return all.stream().anyMatch(r -> r.path().startsWith(rowPath + "/"));
    }

    private static Table buildTable(final List<DiffRow> rows) {
        final Table table = new Table(List.of("path", "status", "actual", "expected"))
                .style()
                .headerDivider("{{[y]}} ")
                .border(Border.continuous.foreground("{{y}}"))
                .divider("{{y}}|")
                .pointer("{{r}}>")
                .apply();
        for (final DiffRow row : rows) {
            table.addRow(List.of(row.path(), row.status(), row.actual(), row.expected()));
        }
        return table;
    }

    /* ---- instance state (mirrors Explain) ---- */

    private final Obj instance;
    private final Type type;
    private final List<DiffRow> allRows = new ArrayList<>();
    private final Deque<DiffLevel> stack = new ArrayDeque<>();

    private Attributes savedAttributes;
    private boolean running = false;
    private int totalHeightUsed = 0;

    public TypeDiffWidget(final Obj instance, final Type type) {
        this.instance = instance;
        this.type = type;
        walkComparison(this.instance, this.type, "");
    }

    /* ================================================================
     * Row construction  (unchanged walk logic)
     * ================================================================ */

    private void walkComparison(final Obj inst, final Type typ, final String path) {
        if (inst.isNoObj()) {
            this.allRows.add(new DiffRow(path, FAIL, MISSING, typ.toShortString()));
            return;
        }
        if (inst.test(typ)) return;

        final Obj typePred = Type.Helper.typePredicateObj(typ);
        if (typePred != null && typePred.isRec() && inst.isRec()) {
            walkRecFields(inst, typePred.asRec(), path);
        } else if (typePred != null && typePred.isLst() && inst.isLst()) {
            walkLstElements(inst, typePred.asLst(), path);
        } else {
            this.allRows.add(new DiffRow(path, FAIL, inst.toShortString(), typ.toShortString()));
        }
    }

    private void walkRecFields(final Obj inst, final Rec typePredRec, final String path) {
        for (final Rel field : typePredRec.elements().toList()) {
            final Obj key = field.first();
            final Obj fieldTypeObj = field.second();
            final Type fieldType = fieldTypeObj.isType() ? fieldTypeObj.asType() : null;
            final Obj instValue = inst.asRec().at(key);
            final String childPath = path.isEmpty()
                    ? key.toShortString() : path + "/" + key.toShortString();

            if (instValue.isNoObj()) {
                if (!key.c().isZeroable())
                    this.allRows.add(new DiffRow(childPath, FAIL, MISSING, describe(fieldType, fieldTypeObj)));
                continue;
            }
            if (fieldType != null && instValue.test(fieldType)) {
                this.allRows.add(new DiffRow(childPath, GOOD, instValue.toShortString(), fieldType.toShortString()));
                continue;
            }
            if (instValue.isRec() && fieldTypeObj.isRec()) {
                final int before = this.allRows.size();
                walkRecFields(instValue, fieldTypeObj.asRec(), childPath);
                if (this.allRows.size() == before)
                    this.allRows.add(new DiffRow(childPath, FAIL, instValue.toShortString(), describe(fieldType, fieldTypeObj)));
                continue;
            }
            if (instValue.isLst() && fieldTypeObj.isLst()) {
                final int before = this.allRows.size();
                walkLstElements(instValue, fieldTypeObj.asLst(), childPath);
                if (this.allRows.size() == before)
                    this.allRows.add(new DiffRow(childPath, FAIL, instValue.toShortString(), describe(fieldType, fieldTypeObj)));
                continue;
            }
            if (fieldType != null && (instValue.isRec() || instValue.isLst())) {
                final int before = this.allRows.size();
                walkComparison(instValue, fieldType, childPath);
                if (this.allRows.size() == before)
                    this.allRows.add(new DiffRow(childPath, FAIL, instValue.toShortString(), fieldType.toShortString()));
                continue;
            }
            this.allRows.add(new DiffRow(childPath, FAIL, instValue.toShortString(), describe(fieldType, fieldTypeObj)));
        }
    }

    private void walkLstElements(final Obj inst, final Lst typePredLst, final String path) {
        final List<Obj> typeElems = typePredLst.lstValue();
        final int max = Math.max(typeElems.size(), inst.asLst().lstValue().size());
        for (int i = 0; i < max; i++) {
            final Obj typeElem = i < typeElems.size() ? typeElems.get(i) : null;
            final Type elemType = typeElem != null && typeElem.isType() ? typeElem.asType() : null;
            final Obj instElem = i < inst.asLst().lstValue().size() ? inst.asLst().lstValue().get(i) : noobj();
            final String childPath = path + "/[" + i + "]";

            if (instElem.isNoObj()) {
                this.allRows.add(new DiffRow(childPath, FAIL, MISSING, describe(elemType, typeElem)));
                continue;
            }
            if (elemType != null && instElem.test(elemType)) {
                this.allRows.add(new DiffRow(childPath, GOOD, instElem.toShortString(), elemType.toShortString()));
                continue;
            }
            if (typeElem != null && instElem.isRec() && typeElem.isRec()) {
                final int before = this.allRows.size();
                walkRecFields(instElem, typeElem.asRec(), childPath);
                if (this.allRows.size() == before)
                    this.allRows.add(new DiffRow(childPath, FAIL, instElem.toShortString(), typeElem.toShortString()));
                continue;
            }
            if (typeElem != null && instElem.isLst() && typeElem.isLst()) {
                final int before = this.allRows.size();
                walkLstElements(instElem, typeElem.asLst(), childPath);
                if (this.allRows.size() == before)
                    this.allRows.add(new DiffRow(childPath, FAIL, instElem.toShortString(), typeElem.toShortString()));
                continue;
            }
            if (elemType != null && (instElem.isRec() || instElem.isLst())) {
                final int before = this.allRows.size();
                walkComparison(instElem, elemType, childPath);
                if (this.allRows.size() == before)
                    this.allRows.add(new DiffRow(childPath, FAIL, instElem.toShortString(), elemType.toShortString()));
                continue;
            }
            this.allRows.add(new DiffRow(childPath, FAIL, instElem.toShortString(), describe(elemType, typeElem)));
        }
    }

    private static String describe(final Type fieldType, final Obj fallback) {
        return fieldType != null ? fieldType.toShortString()
                : (fallback != null ? fallback.toShortString() : "?");
    }

    /* ================================================================
     * Lifecycle  (cloned verbatim from Explain)
     * ================================================================ */

    @Override
    public void run() {
        savedAttributes = terminal.enterRawMode();
        terminal.puts(InfoCmp.Capability.keypad_xmit);
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.writer().flush();

        pushLevel("", 0, 0, -1, -1);

        this.running = true;
        BindingReader bindingReader = new BindingReader(terminal.reader());
        KeyMap<Action> keyMap = buildKeyMap();

        while (running && !stack.isEmpty()) {
            redrawStack();
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

    /* ================================================================
     * Input  (cloned verbatim from Explain)
     * ================================================================ */

    private KeyMap<Action> buildKeyMap() {
        KeyMap<Action> keyMap = new KeyMap<>();
        keyMap.bind(Action.DOWN_ROW, key(terminal, InfoCmp.Capability.key_down));
        keyMap.bind(Action.UP_ROW, key(terminal, InfoCmp.Capability.key_up));
        keyMap.bind(Action.QUIT, Utilities.esc_key);
        keyMap.bind(Action.SELECT, Utilities.enter_key);
        return keyMap;
    }

    private void handleAction(Action action) {
        DiffLevel current = stack.peek();
        if (current == null) return;

        switch (action) {
            case DOWN_ROW:
                current.selectedRow = Math.min(current.selectedRow + 1, current.dataRowCount() - 1);
                break;
            case UP_ROW:
                current.selectedRow = Math.max(current.selectedRow - 1, 0);
                break;
            case SELECT:
                handleSelect(current);
                break;
            case QUIT:
                popLevel();
                if (stack.isEmpty()) running = false;
                break;
        }
    }

    /* ---- stack helpers (mirrors Explain) ---- */

    private void pushLevel(final String prefix, final int offsetX, final int offsetY,
                           final int spawnRow, final int spawnCol) {
        stack.push(new DiffLevel(prefix, this.allRows, offsetX, offsetY, spawnRow, spawnCol));
    }

    private void popLevel() {
        if (!stack.isEmpty()) stack.pop();
    }

    private void handleSelect(DiffLevel current) {
        if (current.dataRowCount() == 0) return;
        DiffRow selected = current.rows.get(current.selectedRow);
        if (!hasChildren(selected.path(), this.allRows)) return;

        int newOffsetX = current.offsetX + 4;
        int newOffsetY = current.offsetY + current.selectedRow + 3;
        pushLevel(selected.path(), newOffsetX, newOffsetY, current.selectedRow, current.spawnCol);
    }

    /* ================================================================
     * Rendering  (cloned verbatim from Explain.redrawStack)
     * ================================================================ */

    private void redrawStack() {
        final List<DiffLevel> levels = new ArrayList<>(stack);
        Collections.reverse(levels);

        final WidgetCanvas canvas = beginRedraw(totalHeightUsed);

        for (int levelIdx = 0; levelIdx < levels.size(); levelIdx++) {
            final DiffLevel level = levels.get(levelIdx);
            final boolean isTop = (levelIdx == levels.size() - 1);
            final DiffLevel child = (levelIdx + 1 < levels.size()) ? levels.get(levelIdx + 1) : null;
            final boolean hasChild = child != null && child.spawnRow >= 0;
            final List<String> lines = level.table.rowStrings();
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
                    content = indent + highlightPointer(line);
                } else if (isSpawn) {
                    content = Graphitty.string(dimColor) + indent + line;
                } else if (isTop && isDataRow) {
                    content = indent + line;
                } else {
                    content = Graphitty.string(dimColor) + indent + line;
                }
                canvas.line(content);
            }

            if (hasChild) canvas.blankLine();
            if (isTop && stack.size() > 1) {
                canvas.line(Graphitty.string(indent + "  {{y}}[depth: %d]{{X}}", stack.size()));
            }
        }

        // Status / key-hint bar
        final DiffLevel top = stack.peek();
        if (top != null) {
            final int totalFail = (int) allRows.stream().filter(r -> FAIL.equals(r.status())).count();
            final String expandHint = (top.dataRowCount() > 0
                    && hasChildren(top.rows.get(top.selectedRow).path(), this.allRows))
                    ? " {{w}}enter{{g}}:expand"
                    : "";
            final String backHint = stack.size() > 1 ? " {{w}}esc{{g}}:back" : " {{w}}esc{{g}}:close";
            canvas.statusLine(Graphitty.string(
                    "{{w}}{{[b]}}%s {{w}}^v{{g}}:navigate%s {{w}}%d row%s {{w}}(%d {{r}}X{{w}}){{X}}",
                    backHint.trim(), expandHint,
                    top.dataRowCount(),
                    top.dataRowCount() == 1 ? "" : "s",
                    totalFail));
        }

        totalHeightUsed = canvas.finish();
    }

    /**
     * Replace the first | divider with the red > pointer.
     */
    private String highlightPointer(final String line) {
        final int firstDiv = line.indexOf('|');
        if (firstDiv >= 0) {
            return "{{r}}>{{X}}" + line.substring(firstDiv + 1);
        }
        return "{{r}}>{{X}}" + line;
    }

    /* ================================================================
     * Contract
     * ================================================================ */

    @Override
    public String format() {
        return "";
    }

    @Override
    public String toString() {
        return "TypeDiffWidget[rows=" + allRows.size() + "]";
    }
}
