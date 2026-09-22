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

import studio.phaseshift.metatron.isa.m.type.reflect.JInst;
import studio.phaseshift.metatron.isa.mach.type.ui.tmux.Pane;
import studio.phaseshift.metatron.isa.mach.type.ui.tmux.PaneNode;
import studio.phaseshift.metatron.isa.mach.type.ui.tmux.SplitContainer;
import studio.phaseshift.metatron.isa.mach.type.ui.tmux.SplitLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * The console's pane tree: the split layout the console paints in
 * screen mode — where the root is a single {@link Pane} or a
 * {@link SplitContainer} with nested panes — which pane the
 * prompt is in, and the tree operations on top of that
 * (split, close, focus, cycle, resize) plus the pure
 * tree walk that turns a pane into a terminal position.
 * <p>
 * Rendering the tree (and the cursor / layout bookkeeping around
 * it) stays on {@link Console} — this class owns the shape of
 * the tree, not its pixels.
 */
public final class PaneManager {

    // Pane tree: root can be a single Pane or a SplitContainer with nested panes
    private PaneNode paneRoot;
    private Pane activePane;
    private boolean splitMode = false;  // True when we have more than one pane

    public PaneManager() {
    }

    /**
     * Build the initial single-pane tree for the console's own pane.
     */
    public void init(final Console console) {
        this.activePane = new Pane();
        this.activePane.setConsole(console);
        this.paneRoot = this.activePane;
        this.registerPaneListener(console, this.activePane);
    }

    public Pane activePane() {
        return this.activePane;
    }

    public boolean splitMode() {
        return this.splitMode;
    }

    public PaneNode root() {
        return this.paneRoot;
    }

    /**
     * @return every pane in the tree, or an empty list when there is
     * no tree yet
     */
    public List<Pane> getAllPanes() {
        return null == this.paneRoot ? new ArrayList<>() : this.paneRoot.getAllPanes();
    }

    /**
     * Split the active pane in the given direction.
     * Creates a new pane and makes it the sibling of the current active pane.
     *
     * @param console the console the new pane is attached to
     * @param direction VERTICAL (left|right) or HORIZONTAL (top|bottom)
     * @return the newly created pane
     */
    public Pane split(final Console console, final SplitLayout direction) {
        if (direction == SplitLayout.NONE) {
            console.logger().warn("cannot split with direction NONE");
            return this.activePane;
        }

        // Create new pane
        final Pane newPane = new Pane(1000);
        newPane.setConsole(console);
        this.registerPaneListener(console, newPane);

        // Create split container with active pane and new pane
        final SplitContainer container = new SplitContainer(direction, this.activePane, newPane);

        // Replace active pane in tree with the container
        if (this.paneRoot == this.activePane) {
            // Active pane is root - just replace root
            this.paneRoot = container;
        } else {
            // Find and replace in tree
            this.paneRoot.replaceChild(this.activePane, container);
        }

        this.splitMode = true;
        console.logger().info("split pane {{y}}%d{{X}} %s, created pane {{y}}%d{{X}}",
                this.activePane.id(), direction.name().toLowerCase(), newPane.id());

        // Switch focus to new pane
        this.activePane = newPane;
        console.requestRedraw();
        JInst.Helper.processInst(console);
        return newPane;
    }

    /**
     * Close the active pane. If it's the last pane, do nothing.
     *
     * @param console the console the panes belong to
     */
    public void closeActivePane(final Console console) {
        final List<Pane> allPanes = this.getAllPanes();
        if (allPanes.size() <= 1) {
            console.logger().warn("cannot close the last pane");
            return;
        }

        final Pane toClose = this.activePane;

        // Find next pane to focus — use id() comparison to avoid equals()/jvm() issues
        final int currentIndex = indexOfPaneById(allPanes, toClose != null ? toClose.id() : -1);
        final Pane nextPane = allPanes.get((currentIndex + 1) % allPanes.size());

        // Remove from tree — unsubscribe first so no stale space subscriptions linger
        if (toClose != null) toClose.unsubscribe();
        this.paneRoot = this.paneRoot.removePane(toClose);
        if (this.paneRoot == null) {
            // Shouldn't happen, but safety
            this.paneRoot = nextPane;
        }

        this.activePane = nextPane;
        this.splitMode = this.getAllPanes().size() > 1;

        console.logger().info("closed pane {{y}}%d{{X}}, focused pane {{y}}%d{{X}}", toClose.id(), this.activePane.id());
        console.requestRedraw();
    }

    /**
     * Focus a specific pane by ID.
     *
     * @param console the console the panes belong to
     * @param paneId the id of the pane to focus
     */
    public void focusPane(final Console console, final int paneId) {
        final Pane pane = this.paneRoot.findPane(paneId);
        if (pane == null) {
            console.logger().error("pane {{r}}%d{{X}} not found", paneId);
            return;
        }
        this.activePane = pane;
        console.logger().info("focused pane {{y}}%d{{X}}", paneId);
        console.requestRedraw();
    }

    /**
     * Cycle to the next pane.
     *
     * @param console the console the panes belong to
     */
    public void nextPane(final Console console) {
        final List<Pane> allPanes = this.getAllPanes();
        if (allPanes.size() <= 1) return;

        // Use id() comparison to avoid equals()/jvm() issues introduced by JRec changes
        // (JRec.jvm() now creates new instLambda objects on every call, breaking Map.equals())
        final int currentIndex = indexOfPaneById(allPanes, this.activePane != null ? this.activePane.id() : -1);
        this.activePane = allPanes.get((currentIndex + 1) % allPanes.size());
        console.logger().info("focused pane {{y}}%d{{X}}", this.activePane.id());
        console.requestRedraw();
    }

    /**
     * Cycle to the previous pane.
     *
     * @param console the console the panes belong to
     */
    public void prevPane(final Console console) {
        final List<Pane> allPanes = this.getAllPanes();
        if (allPanes.size() <= 1) return;

        // Use id() comparison to avoid equals()/jvm() issues introduced by JRec changes
        final int currentIndex = indexOfPaneById(allPanes, this.activePane != null ? this.activePane.id() : -1);
        this.activePane = allPanes.get((currentIndex - 1 + allPanes.size()) % allPanes.size());
        console.logger().info("focused pane {{y}}%d{{X}}", this.activePane.id());
        console.requestRedraw();
    }

    /**
     * Resize the active pane by adjusting its parent container's split ratio.
     *
     * @param console the console the panes belong to
     * @param delta positive = more space for active pane, negative = less space
     */
    public void resizeActivePane(final Console console, final float delta) {
        if (!this.splitMode || this.activePane == null) return;
        if (this.paneRoot.isLeaf()) return; // Single pane, nothing to resize

        // Find the parent container of the active pane
        final SplitContainer parent = ((SplitContainer) this.paneRoot).findParentOf(this.activePane);
        if (parent == null) {
            // Active pane might be direct child of root
            if (this.paneRoot instanceof SplitContainer root) {
                // Check if active pane is in first or second subtree
                if (root.first() == this.activePane ||
                        (!root.first().isLeaf() && root.first().findPane(this.activePane.id()) != null)) {
                    // Active pane is in first subtree - increase ratio for more space
                    root.adjustRatio(delta);
                } else {
                    // Active pane is in second subtree - decrease ratio for more space
                    root.adjustRatio(-delta);
                }
            }
        } else {
            // Determine if active pane is first or second child
            if (parent.first() == this.activePane) {
                // Active pane is first child - increase ratio for more space
                parent.adjustRatio(delta);
            } else {
                // Active pane is second child - decrease ratio for more space
                parent.adjustRatio(-delta);
            }
        }

        console.requestRedraw();
    }

    /**
     * Wire the output-change listener and space subscriptions onto a pane.
     *
     * @param console the console the pane reports to
     * @param pane the pane to wire
     */
    private void registerPaneListener(final Console console, final Pane pane) {
        pane.setOutputListener(console::onPaneOutputChanged);
        pane.subscribe();
    }

    /**
     * Find the index of a pane by its integer id, avoiding {@link Object#equals} / {@code jvm()}
     * comparisons which are unreliable for JRec subclasses (JRec.jvm() creates new lambda
     * instances on every call, causing Map.equals to return false even for the same object).
     *
     * @return the index, or -1 if not found
     */
    private static int indexOfPaneById(final List<Pane> panes, final int id) {
        for (int i = 0; i < panes.size(); i++) {
            if (panes.get(i).id() == id) return i;
        }
        return -1;
    }

    /**
     * Calculate a pane's position (startRow, startCol, height, width) by traversing the tree.
     * This ensures we always have the correct position regardless of render state.
     *
     * @param console the console whose terminal sizes the walk
     * @param pane the pane to locate
     * @return int[] {startRow, startCol, height, width}
     */
    public int[] calculatePanePosition(final Console console, final Pane pane) {
        final int height = console.getTerminal().getHeight() - 1;  // -1 for status line only
        final int width = console.getTerminal().getWidth();
        return this.calculatePanePositionInNode(pane, this.paneRoot, 1, 1, height, width);
    }

    private int[] calculatePanePositionInNode(final Pane target, final PaneNode node,
                                              final int startRow, final int startCol,
                                              final int height, final int width) {
        if (node == target) {
            return new int[]{startRow, startCol, height, width};
        }
        if (node.isLeaf()) {
            return null; // Not found in this branch
        }
        // It's a SplitContainer
        final SplitContainer container = (SplitContainer) node;
        if (container.direction() == SplitLayout.VERTICAL) {
            // No divider - panes are directly adjacent
            final int firstWidth = (int) (width * container.ratio());
            final int secondWidth = width - firstWidth;

            // Check first (left)
            final int[] result = this.calculatePanePositionInNode(target, container.first(),
                    startRow, startCol, height, firstWidth);
            if (result != null) return result;

            // Check second (right)
            return this.calculatePanePositionInNode(target, container.second(),
                    startRow, startCol + firstWidth, height, secondWidth);
        } else { // HORIZONTAL
            // No divider - panes are directly adjacent
            final int firstHeight = (int) (height * container.ratio());
            final int secondHeight = height - firstHeight;

            // Check first (top)
            final int[] result = this.calculatePanePositionInNode(target, container.first(),
                    startRow, startCol, firstHeight, width);
            if (result != null) return result;

            // Check second (bottom)
            return this.calculatePanePositionInNode(target, container.second(),
                    startRow + firstHeight, startCol, secondHeight, width);
        }
    }
}
