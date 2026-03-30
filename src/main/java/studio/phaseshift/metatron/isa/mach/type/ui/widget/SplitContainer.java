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

package studio.phaseshift.metatron.isa.mach.type.ui.widget;

import org.jline.terminal.Terminal;

import java.util.ArrayList;
import java.util.List;

/**
 * A SplitContainer is a branch node in the pane tree - holds two children
 * split either vertically or horizontally.
 *
 * <pre>
 * VERTICAL split (left | right):
 * ┌───────────────┬─────────────────┐
 * │               │                 │
 * │    first      │     second      │
 * │   (left)      │    (right)      │
 * │               │                 │
 * └───────────────┴─────────────────┘
 *
 * HORIZONTAL split (top / bottom):
 * ┌─────────────────────────────────┐
 * │             first               │
 * │             (top)               │
 * ├─────────────────────────────────┤
 * │            second               │
 * │           (bottom)              │
 * └─────────────────────────────────┘
 * </pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SplitContainer implements PaneNode {

    private final SplitLayout direction;
    private PaneNode first;   // left or top
    private PaneNode second;  // right or bottom
    private float ratio;      // 0.0-1.0, portion allocated to first child

    public SplitContainer(final SplitLayout direction, final PaneNode first, final PaneNode second) {
        this(direction, first, second, 0.5f);
    }

    public SplitContainer(final SplitLayout direction, final PaneNode first, final PaneNode second, final float ratio) {
        if (direction == SplitLayout.NONE) {
            throw new IllegalArgumentException("SplitContainer requires VERTICAL or HORIZONTAL direction");
        }
        this.direction = direction;
        this.first = first;
        this.second = second;
        this.ratio = Math.max(0.1f, Math.min(0.9f, ratio)); // Clamp to reasonable range
    }

    public SplitLayout direction() {
        return this.direction;
    }

    public PaneNode first() {
        return this.first;
    }

    public PaneNode second() {
        return this.second;
    }

    public float ratio() {
        return this.ratio;
    }

    public void setRatio(final float ratio) {
        this.ratio = Math.max(0.1f, Math.min(0.9f, ratio));
    }

    /**
     * Adjust the split ratio by a delta.
     * Positive = more space to first, negative = more space to second.
     */
    public void adjustRatio(final float delta) {
        setRatio(this.ratio + delta);
    }

    // ========== PaneNode interface ==========

    @Override
    public void render(final Terminal terminal, final int startRow, final int startCol,
                       final int height, final int width, final Pane activePane) {
        if (this.direction == SplitLayout.VERTICAL) {
            // Split left | right - no separate divider, panes' borders are adjacent
            final int firstWidth = (int) (width * this.ratio);
            final int secondWidth = width - firstWidth;

            // Render first (left)
            this.first.render(terminal, startRow, startCol, height, firstWidth, activePane);

            // Render second (right) - starts immediately after first
            this.second.render(terminal, startRow, startCol + firstWidth, height, secondWidth, activePane);

        } else { // HORIZONTAL
            // Split top / bottom - no separate divider, panes' borders are adjacent
            final int firstHeight = (int) (height * this.ratio);
            final int secondHeight = height - firstHeight;

            // Render first (top)
            this.first.render(terminal, startRow, startCol, firstHeight, width, activePane);

            // Render second (bottom) - starts immediately after first
            this.second.render(terminal, startRow + firstHeight, startCol, secondHeight, width, activePane);
        }
    }

    @Override
    public boolean isLeaf() {
        return false;
    }

    @Override
    public List<Pane> getAllPanes() {
        final List<Pane> panes = new ArrayList<>();
        panes.addAll(this.first.getAllPanes());
        panes.addAll(this.second.getAllPanes());
        return panes;
    }

    @Override
    public Pane findPane(final int id) {
        Pane found = this.first.findPane(id);
        if (found != null) return found;
        return this.second.findPane(id);
    }

    @Override
    public boolean replaceChild(final Pane oldPane, final PaneNode newNode) {
        // Check if first child is the pane to replace
        if (this.first == oldPane) {
            this.first = newNode;
            return true;
        }
        // Check if second child is the pane to replace
        if (this.second == oldPane) {
            this.second = newNode;
            return true;
        }
        // Recursively check children
        if (this.first.replaceChild(oldPane, newNode)) return true;
        return this.second.replaceChild(oldPane, newNode);
    }

    @Override
    public PaneNode removePane(final Pane pane) {
        // Check if first child is/contains the pane
        if (this.first == pane) {
            // First is the pane to remove, return second as replacement
            return this.second;
        }
        if (this.second == pane) {
            // Second is the pane to remove, return first as replacement
            return this.first;
        }

        // Recursively try to remove from children
        PaneNode newFirst = this.first.removePane(pane);
        if (newFirst != this.first) {
            if (newFirst == null) {
                // First subtree collapsed to nothing, return second
                return this.second;
            }
            this.first = newFirst;
            return this;
        }

        PaneNode newSecond = this.second.removePane(pane);
        if (newSecond != this.second) {
            if (newSecond == null) {
                // Second subtree collapsed to nothing, return first
                return this.first;
            }
            this.second = newSecond;
            return this;
        }

        // Pane not found in this subtree
        return this;
    }

    /**
     * Find the SplitContainer that directly contains the given pane.
     * @return the parent container, or null if not found or pane is at root
     */
    public SplitContainer findParentOf(final Pane pane) {
        if (this.first == pane || this.second == pane) {
            return this;
        }
        if (!this.first.isLeaf()) {
            SplitContainer found = ((SplitContainer) this.first).findParentOf(pane);
            if (found != null) return found;
        }
        if (!this.second.isLeaf()) {
            return ((SplitContainer) this.second).findParentOf(pane);
        }
        return null;
    }

    /**
     * Get the sibling of a pane (the other child in the same container).
     * @return the sibling node, or null if pane is not a direct child
     */
    public PaneNode getSibling(final Pane pane) {
        if (this.first == pane) return this.second;
        if (this.second == pane) return this.first;
        return null;
    }

    @Override
    public String toString() {
        return "Split[%s, ratio=%.2f, first=%s, second=%s]".formatted(
                this.direction, this.ratio, this.first, this.second);
    }
}
