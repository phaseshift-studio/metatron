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
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.*;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;

import static org.jline.keymap.KeyMap.key;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_PANEL_TID;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_TREE_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * TreeSelect — interactive tree browser with code-driven selection and
 * per-branch expansion.
 * <p>
 * Arrow keys navigate the tree rows.  {@code Enter} passes the selected
 * node's {@code rel::T} (URI → value) to the user-configurable
 * {@code on_select} field.  {@code Right} expands the current branch one level
 * deeper (or increases {@code max} for leaf nodes).  {@code Ctrl-D} pops
 * the current level (or exits at the root).
 * <p>
 * Architecture mirrors {@link ExplainTool}: a stack of levels, each with
 * its own offset and selection state, rendered via {@link WidgetCanvas}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TreeSelectTool extends AbstractWidget<TreeSelectTool> {

    private static final Style<?> DEFAULT_STYLE =
            Style.from(rec(uri("mini"), instLambda((lhs, inst) -> str(lhs.asRel().second().tid().name()))))
                    .border(Border.continuous)
                    .pointer("{{r}}>{{/r}}")
                    .foreground("{{y}}");

    private enum Action {
        QUIT, DOWN_ROW, UP_ROW, SELECT, RIGHT, LEFT
    }

    // ==================================================================
    // Level types
    // ==================================================================

    /**
     * A navigable tree level showing the URI-subtree rooted at a given URI.
     */
    private static class TreeLevel {
        final fURI root;
        final TreeWidget tree;
        final int offsetX;
        final int offsetY;
        int selectedRow;
        final int spawnRow;
        final int spawnCol;

        TreeLevel(final fURI root, final int maxDepth,
                  final Set<fURI> forceExpand,
                  final Call label,
                  final int offsetX, final int offsetY,
                  final int spawnRow, final int spawnCol,
                  final Rec parentStyle) {
            this.root = root;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.selectedRow = 0;
            this.spawnRow = spawnRow;
            this.spawnCol = spawnCol;

            final Map<Obj, Obj> jvm = mutableMap(
                    uri(ROOT), root.toUri(),
                    uri(MAX), jnt(maxDepth),
                    uri(CODE), label);
            if (parentStyle != null) {
                jvm.put(uri(STYLE), parentStyle);
            }
            this.tree = new TreeWidget(jvm, UI_TREE_TID, null);
            if (!forceExpand.isEmpty())
                this.tree.forceExpand(forceExpand);
        }

        int rowCount() {
            return tree.rowCount();
        }

        List<String> rowStrings() {
            return tree.rowStrings();
        }

        CommonUtil.TreeEntry getEntry(final int row) {
            return tree.entries().get(row);
        }
    }

    /**
     * A read-only detail level showing a formatted object inside a
     * {@link PanelWidget} so border drawing and layout are delegated.
     */
    private static class DetailLevel {
        final fURI objUri;
        final PanelWidget panel;
        final int offsetX;
        final int offsetY;
        final int spawnRow;
        final int spawnCol;

        DetailLevel(final Obj obj, final fURI objUri,
                    final int offsetX, final int offsetY,
                    final int spawnRow, final int spawnCol,
                    final Rec parentStyle) {
            this.objUri = objUri;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.spawnRow = spawnRow;
            this.spawnCol = spawnCol;

            final String formatted = ObjmtronSerializer.single().write(obj);
            final Map<Obj, Obj> jvm = mutableMap(
                    uri(TITLE), str(" " + objUri + " "),
                    uri(BODY), str(formatted));
            if (parentStyle != null) {
                jvm.put(uri(STYLE), parentStyle);
            }
            this.panel = new PanelWidget(jvm, UI_PANEL_TID, null);
        }

        int rowCount() {
            return panel.height();
        }

        List<String> rowStrings() {
            return List.of(panel.format().split("\n"));
        }
    }

    // ==================================================================
    // State
    // ==================================================================

    private final Deque<Object> stack = new ArrayDeque<>();
    private final Set<fURI> expandedNodes = new HashSet<>();
    private Attributes savedAttributes;
    private boolean running = false;
    private int totalHeightUsed = 0;

    public TreeSelectTool(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        readStyle(this.jvm());
    }

    private fURI rootUri() {
        return this.at(ROOT).orThrow(MTronException.of("no root uri provided")).uriValue();
    }

    private int maxDepth() {
        return this.at(uri(MAX)).orElse(jnt(3)).intValue().intValue();
    }

    private void maxDepth(final int value) {
        jvmWrite(uri(MAX), jnt(value));
    }

    private Call onSelect() {
        return this.at(uri(ON_SELECT)).orElse(instLambda((lhs, inst) -> {
            this.pushDetailLevel(lhs.asRel().second(), lhs.asRel().first().uriValue(), 0, 0, 0, 0);
            return noobj();
        }));
    }

    private Call label() {
        return this.at(uri(LABEL));
    }

    private void readStyle(final Map<Obj, Obj> jvm) {
        final Obj s = jvm.getOrDefault(uri(STYLE), DEFAULT_STYLE);
        if (s != null && s.isRec()) {
            final Style<TreeSelectTool> st = Style.from(s.as());
            st.stylable = this;
            this.style = st;
            this.at(STYLE, st);
        }
    }

    // ==================================================================
    // Lifecycle
    // ==================================================================

    @Override
    public void run() {
        Console.userMode.set(true);
        // Enter raw mode for arrow-key reading
        savedAttributes = terminal.enterRawMode();
        terminal.puts(InfoCmp.Capability.keypad_xmit);
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.writer().flush();

        pushTreeLevel(rootUri(), maxDepth(), expandedNodes, 0, 0, -1, -1);

        this.running = true;
        final BindingReader bindingReader = new BindingReader(terminal.reader());
        final KeyMap<Action> keyMap = buildKeyMap();

        while (running && !stack.isEmpty()) {
            redrawStack();
            final Action action = bindingReader.readBinding(keyMap);
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

    // ==================================================================
    // Key bindings
    // ==================================================================

    private KeyMap<Action> buildKeyMap() {
        final KeyMap<Action> keyMap = new KeyMap<>();
        keyMap.bind(Action.DOWN_ROW, key(terminal, InfoCmp.Capability.key_down));
        keyMap.bind(Action.UP_ROW, key(terminal, InfoCmp.Capability.key_up));
        keyMap.bind(Action.QUIT, "");              // Ctrl-D
        keyMap.bind(Action.SELECT, Utilities.enter_key);   // Enter
        keyMap.bind(Action.RIGHT, key(terminal, InfoCmp.Capability.key_right)); // Right arrow
        keyMap.bind(Action.LEFT, key(terminal, InfoCmp.Capability.key_left));   // Left arrow
        return keyMap;
    }

    // ==================================================================
    // Action dispatch
    // ==================================================================

    private void handleAction(final Action action) {
        final Object top = stack.peek();
        if (top == null) return;

        switch (action) {
            case DOWN_ROW -> {
                if (top instanceof TreeLevel t) {
                    if (t.rowCount() > 0)
                        t.selectedRow = Math.min(t.selectedRow + 1, t.rowCount() - 1);
                }
            }
            case UP_ROW -> {
                if (top instanceof TreeLevel t) {
                    t.selectedRow = Math.max(t.selectedRow - 1, 0);
                }
            }
            case SELECT -> {
                if (top instanceof TreeLevel t) {
                    handleSelect(t);
                }
            }
            case RIGHT -> {
                if (top instanceof TreeLevel t) {
                    handleRight(t);
                }
            }
            case LEFT -> {
                if (top instanceof TreeLevel t) {
                    handleLeft(t);
                }
            }
            case QUIT -> {
                popLevel();
                if (stack.isEmpty()) {
                    running = false;
                }
            }
        }
    }

    /**
     * Enter key: pass the selected node's {@code rel::T} (URI → value)
     * to the user-configured {@link #onSelect} instruction.
     */
    private void handleSelect(final TreeLevel level) {
        if (level.rowCount() == 0) return;
        final CommonUtil.TreeEntry entry = level.getEntry(level.selectedRow);
        if (entry == null) return;

        // Build rel::T for the selected node: URI → value
        final Rel nodeRel = rel(uri(entry.uri()), entry.obj());
        onSelect().apply(nodeRel);
    }

    /**
     * Right arrow: expand the selected branch one level deeper.
     * Always increments the global {@code max} depth so expansion is
     * immediately visible.  Additionally, tracks the node for per-branch
     * expansion so that subsequent right-arrow presses on the same
     * branch deepen it further (depth-first) without broadening every
     * other branch at the same pace.
     */
    private void handleRight(final TreeLevel level) {
        if (level.rowCount() == 0) return;
        final CommonUtil.TreeEntry entry = level.getEntry(level.selectedRow);
        if (entry == null) return;

        // Per-branch tracking: force this subtree one level deeper.
        expandedNodes.add(entry.uri());
        // Global expansion: always bump max so the change is visible.
        maxDepth(maxDepth() + 1);
        rebuildTreeLevel(level);
    }

    /**
     * Left arrow: contract the selected branch one level.
     * Removes the node from per-branch expansion tracking and decrements
     * the global {@code max} depth, with a floor of 1 so the tree never
     * collapses completely.
     */
    private void handleLeft(final TreeLevel level) {
        if (level.rowCount() == 0) return;
        final CommonUtil.TreeEntry entry = level.getEntry(level.selectedRow);
        if (entry == null) return;

        expandedNodes.remove(entry.uri());
        if (maxDepth() > 1) {
            maxDepth(maxDepth() - 1);
        }
        rebuildTreeLevel(level);
    }

    /**
     * Pop and re-push the current tree level with updated
     * {@link #maxDepth} and {@link #expandedNodes}, preserving the
     * selected node by URI so the cursor stays on the expanded node
     * even after new rows shift the indices.
     */
    private void rebuildTreeLevel(final TreeLevel current) {
        final fURI selectedUri = (current.selectedRow >= 0 && current.selectedRow < current.tree.entries().size())
                ? current.getEntry(current.selectedRow).uri()
                : null;
        stack.pop();
        pushTreeLevel(current.root, maxDepth(), expandedNodes,
                current.offsetX, current.offsetY,
                current.spawnRow, current.spawnCol);
        if (selectedUri != null) {
            final Object top = stack.peek();
            if (top instanceof TreeLevel t) {
                final List<CommonUtil.TreeEntry> entries = t.tree.entries();
                for (int i = 0; i < entries.size(); i++) {
                    if (entries.get(i).uri().equals(selectedUri)) {
                        t.selectedRow = i;
                        return;
                    }
                }
                // URI not found (shouldn't happen) — keep row 0
            }
        }
    }

    // ==================================================================
    // Stack manipulation
    // ==================================================================

    private void pushTreeLevel(final fURI root, final int maxDepth,
                               final Set<fURI> forceExpand,
                               final int offsetX, final int offsetY,
                               final int spawnRow, final int spawnCol) {
        stack.push(new TreeLevel(root, maxDepth, forceExpand, label(),
                offsetX, offsetY, spawnRow, spawnCol,
                this.getStyle()));
    }

    private void pushDetailLevel(final Obj obj, final fURI objUri,
                                 final int offsetX, final int offsetY,
                                 final int spawnRow, final int spawnCol) {
        stack.push(new DetailLevel(obj, objUri, offsetX, offsetY, spawnRow, spawnCol,
                this.getStyle()));
    }

    private void popLevel() {
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    // ==================================================================
    // Rendering
    // ==================================================================

    /**
     * Redraw all levels from bottom (oldest) to top (active), dimming
     * non-active levels so the user can see the stack depth.
     * <p>
     * All colors, the pointer character, and border-drawing glyphs are
     * read from {@link #getStyle()} so that a mtron {@code style::[...]}
     * expression is honored.
     */
    private void redrawStack() {
        final List<Object> levels = new ArrayList<>(stack);
        Collections.reverse(levels); // draw bottom → top

        final Style<?> style = this.getStyle();
        final String fg = style.foreground();                   // e.g. "{{y}}"
        final String pointer = style.at("pointer").orElse(str("{{r}}>")).strValue();
        final String dimColor = "{{w}}";

        final WidgetCanvas canvas = beginRedraw(totalHeightUsed);

        for (int levelIdx = 0; levelIdx < levels.size(); levelIdx++) {
            final Object level = levels.get(levelIdx);
            final boolean isTop = (levelIdx == levels.size() - 1);
            final Object child = (levelIdx + 1 < levels.size()) ? levels.get(levelIdx + 1) : null;

            if (level instanceof TreeLevel t) {
                final boolean hasChild = child instanceof DetailLevel d && d.spawnRow >= 0;
                final String indent = " ".repeat(t.offsetX);
                final List<String> rows = t.rowStrings();

                // Header bar
                //final String activeColor = isTop ? fg : dimColor;
                //canvas.line(Graphitty.string(activeColor + indent
                //        + " Tree: " + t.root + " {{X}}"));

                for (int lineIdx = 0; lineIdx < rows.size(); lineIdx++) {
                    final boolean isSelected = isTop && lineIdx == t.selectedRow;
                    final boolean isSpawn = !isTop && hasChild && lineIdx == childSpawnRow(child);
                    final String raw = rows.get(lineIdx);

                    if (isSelected) {
                        canvas.line(Graphitty.string(indent + pointer + "{{X}} " + raw));
                    } else if (isSpawn) {
                        canvas.line(Graphitty.string("{{[R]}}" + indent + "  " + raw + "{{X}}"));
                    } else if (false) {
                        canvas.line(Graphitty.string(dimColor + indent + "  " + raw));
                    } else {
                        canvas.line(Graphitty.string(indent + "  " + raw));
                    }
                }

                if (hasChild) canvas.blankLine();

            } else if (level instanceof DetailLevel d) {
                final String indent = " ".repeat(d.offsetX);
                //final String activeColor = isTop ? fg : dimColor;
                for (final String line : d.rowStrings()) {
                    canvas.line(Graphitty.string(/*activeColor + */indent + line));
                }
                canvas.blankLine();
            }
        }

        // Status bar
        final Object top = stack.peek();
        if (top instanceof TreeLevel t) {
            canvas.statusLine("{{w}}ctrl-d{{g}}:back {{w}}<^v>{{g}}:nav {{w}}enter{{g}}:select {{w}}<left|right>{{g}}:expand {{X}}  {{y}}["
                    + (t.selectedRow + 1) + "/" + t.rowCount() + "]{{X}}");
        } else if (top instanceof DetailLevel) {
            canvas.statusLine("{{w}}ctrl-d{{g}}:back {{X}}");
        }

        totalHeightUsed = canvas.finish();
    }

    private static int childSpawnRow(final Object child) {
        if (child instanceof DetailLevel d) return d.spawnRow;
        if (child instanceof TreeSelectTool.TreeLevel t) return t.spawnRow;
        return -1;
    }

    @Override
    public String format() {
        return ""; // Rendering is handled by run() via WidgetCanvas
    }
}
