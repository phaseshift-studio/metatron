/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.isa.mach.type.ui.widget;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Terminal;
import org.jline.utils.Display;
import org.jline.utils.InfoCmp;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.*;
import java.util.function.BiConsumer;

import static org.jline.keymap.KeyMap.key;

/**
 * A window manager that handles overlapping widgets with proper z-order.
 *
 * Usage:
 * <pre>
 *   WindowStack stack = new WindowStack();
 *   stack.push(myWidget, 0, 0);           // Push widget at position
 *   stack.push(nestedWidget, 4, 2);       // Push another on top, offset
 *   stack.pop();                          // Remove top, restore what was underneath
 * </pre>
 *
 * The stack handles:
 * - Saving screen state before drawing
 * - Restoring screen state when closing
 * - Keyboard event routing to the top window
 * - Relative positioning of nested windows
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class WindowStack {

    public enum Action {
        QUIT,           // ESC - close current window
        DOWN_ROW,
        UP_ROW,
        RIGHT_COL,
        LEFT_COL,
        SELECT,         // Enter - activate/drill into selection
        BACK            // Backspace - same as quit but semantic
    }

    /**
     * Represents a window in the stack with its position and state.
     */
    public static class Window {
        public final Widget<?> widget;
        public final int offsetX;           // Horizontal offset from parent
        public final int offsetY;           // Vertical offset from parent
        public final List<String> savedLines;  // Lines that were overwritten
        public int selectedRow = 0;
        public int selectedCol = 0;
        public BiConsumer<Window, Action> onAction;

        public Window(Widget<?> widget, int offsetX, int offsetY) {
            this.widget = widget;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.savedLines = new ArrayList<>();
        }

        public int absoluteX(WindowStack stack) {
            int x = this.offsetX;
            // Sum up all parent offsets
            for (Window w : stack.windows) {
                if (w == this) break;
                x += w.offsetX;
            }
            return x;
        }

        public int absoluteY(WindowStack stack) {
            int y = this.offsetY;
            for (Window w : stack.windows) {
                if (w == this) break;
                y += w.offsetY;
            }
            return y;
        }
    }

    private final Deque<Window> windows = new ArrayDeque<>();
    private final Terminal terminal;
    private final Display display;
    private boolean running = false;

    public WindowStack() {
        this.terminal = Console.getTerminal();
        this.display = new Display(terminal, false);
        this.display.resize(terminal.getHeight(), terminal.getWidth());
    }

    /**
     * Push a new window onto the stack at the given offset from the current top.
     */
    public Window push(Widget<?> widget, int offsetX, int offsetY) {
        Window window = new Window(widget, offsetX, offsetY);

        // Save what's currently on screen at this position (for later restore)
        // This is a simplified version - in practice you'd capture the actual screen buffer
        window.savedLines.addAll(getCurrentScreenLines(
                window.absoluteX(this),
                window.absoluteY(this),
                widget.width(),
                widget.height()
        ));

        windows.push(window);
        redraw();
        return window;
    }

    /**
     * Pop the top window and restore what was underneath.
     */
    public Window pop() {
        if (windows.isEmpty()) return null;

        Window removed = windows.pop();
        redraw();
        return removed;
    }

    /**
     * Get the currently focused (top) window.
     */
    public Window top() {
        return windows.peek();
    }

    /**
     * Check if stack is empty.
     */
    public boolean isEmpty() {
        return windows.isEmpty();
    }

    /**
     * Number of windows in stack.
     */
    public int depth() {
        return windows.size();
    }

    /**
     * Redraw all windows from bottom to top.
     */
    public void redraw() {
        // Clear the area
        StringBuilder sb = new StringBuilder();

        // Draw windows from bottom to top (reverse iteration)
        List<Window> windowList = new ArrayList<>(windows);
        Collections.reverse(windowList);

        for (Window window : windowList) {
            int absX = window.absoluteX(this);
            int absY = window.absoluteY(this);

            List<String> lines = window.widget.rowStrings();
            for (int i = 0; i < lines.size(); i++) {
                // Position cursor and draw each line
                sb.append(Graphitty.string("{{@&v%d&|%d}}", absY + i, absX + 1));
                sb.append(lines.get(i));
            }
        }

        Graphitty.out(terminal.output(), sb.toString());
        terminal.flush();
    }

    /**
     * Run the window stack event loop.
     * Routes keyboard events to the top window.
     */
    public void run() {
        if (windows.isEmpty()) return;

        this.running = true;
        BindingReader bindingReader = new BindingReader(terminal.reader());

        KeyMap<Action> keyMap = new KeyMap<>();
        keyMap.bind(Action.DOWN_ROW, key(terminal, InfoCmp.Capability.key_down));
        keyMap.bind(Action.UP_ROW, key(terminal, InfoCmp.Capability.key_up));
        keyMap.bind(Action.RIGHT_COL, key(terminal, InfoCmp.Capability.key_right));
        keyMap.bind(Action.LEFT_COL, key(terminal, InfoCmp.Capability.key_left));
        keyMap.bind(Action.QUIT, Utilities.esc_key);
        keyMap.bind(Action.SELECT, Utilities.enter_key);
        keyMap.bind(Action.BACK, "\u007f"); // Backspace

        while (running && !windows.isEmpty()) {
            Window current = top();
            redrawWithSelection(current);

            Action action = bindingReader.readBinding(keyMap);

            if (action == Action.QUIT || action == Action.BACK) {
                pop();
                if (windows.isEmpty()) {
                    running = false;
                }
            } else if (current.onAction != null) {
                // Let the window handle navigation and selection
                current.onAction.accept(current, action);
            } else {
                // Default navigation behavior
                handleDefaultNavigation(current, action);
            }
        }
    }

    /**
     * Stop the event loop.
     */
    public void stop() {
        this.running = false;
    }

    /**
     * Redraw with selection highlight for the top window.
     */
    private void redrawWithSelection(Window window) {
        int absX = window.absoluteX(this);
        int absY = window.absoluteY(this);

        List<String> lines = window.widget.rowStrings();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            sb.append(Graphitty.string("{{@&v%d&|%d}}", absY + i, absX + 1));

            if (i == window.selectedRow) {
                // Highlight selected row with pointer
                sb.append(Graphitty.string("{{r}}>{{X}}"));
                sb.append(lines.get(i).substring(1)); // Skip first char, replace with pointer
            } else {
                sb.append(lines.get(i));
            }
        }

        Graphitty.out(terminal.output(), sb.toString());
        terminal.flush();
    }

    /**
     * Default navigation handler.
     */
    private void handleDefaultNavigation(Window window, Action action) {
        int maxRows = window.widget.height();

        switch (action) {
            case DOWN_ROW:
                window.selectedRow = Math.min(window.selectedRow + 1, maxRows - 1);
                break;
            case UP_ROW:
                window.selectedRow = Math.max(window.selectedRow - 1, 0);
                break;
            case RIGHT_COL:
                window.selectedCol++;
                break;
            case LEFT_COL:
                window.selectedCol = Math.max(window.selectedCol - 1, 0);
                break;
            case SELECT:
                // Override with onAction for custom behavior
                break;
        }
    }

    /**
     * Capture current screen lines at a position (placeholder implementation).
     * In a full implementation, this would read from the terminal's buffer.
     */
    private List<String> getCurrentScreenLines(int x, int y, int width, int height) {
        // For now, return empty lines - the redraw will repaint from bottom up
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < height; i++) {
            lines.add(" ".repeat(width));
        }
        return lines;
    }

    /**
     * Clear the stack and screen area.
     */
    public void clear() {
        while (!windows.isEmpty()) {
            windows.pop();
        }
        // Clear screen area
        Graphitty.out(terminal.output(), "{{XX}}");
    }
}
