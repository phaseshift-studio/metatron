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

package studio.phaseshift.metatron.isa.mach.type.ui.console;

import org.jline.terminal.Terminal;

import java.util.List;

/**
 * A node in the pane tree. Can be either:
 * - A {@link Pane} (leaf node with output buffer and input)
 * - A {@link SplitContainer} (branch node with two children)
 *
 * <pre>
 * Example tree:
 *            Root
 *           /    \
 *       Pane0   SplitContainer
 *              /             \
 *          Pane1           Pane2
 * </pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface PaneNode {

    /**
     * Render this node within the given screen region.
     *
     * @param terminal the terminal to render to
     * @param startRow top row of the region (1-indexed for ANSI)
     * @param startCol left column of the region (1-indexed for ANSI)
     * @param height   number of rows
     * @param width    number of columns
     * @param activePane the currently active pane (for highlighting)
     */
    void render(Terminal terminal, int startRow, int startCol, int height, int width, Pane activePane);

    /**
     * @return true if this is a leaf Pane, false if it's a SplitContainer
     */
    boolean isLeaf();

    /**
     * @return all leaf Panes under this node
     */
    List<Pane> getAllPanes();

    /**
     * Find a pane by its ID.
     * @return the pane, or null if not found
     */
    Pane findPane(int id);

    /**
     * Replace a child pane with a new node (used when splitting).
     * @param oldPane the pane to replace
     * @param newNode the new node (typically a SplitContainer containing oldPane + newPane)
     * @return true if replacement was successful
     */
    boolean replaceChild(Pane oldPane, PaneNode newNode);

    /**
     * Remove a pane from the tree, collapsing its parent if needed.
     * @param pane the pane to remove
     * @return the node that should replace this subtree (may be null, sibling, or self)
     */
    PaneNode removePane(Pane pane);
}
