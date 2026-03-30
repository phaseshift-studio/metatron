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

/**
 * Split layout modes for Console panes.
 *
 * <pre>
 * NONE:        Single pane (default)
 * ┌─────────────────────────────────┐
 * │                                 │
 * │           Pane 0                │
 * │                                 │
 * └─────────────────────────────────┘
 *
 * VERTICAL:   Side-by-side panes (left | right)
 * ┌───────────────┬─────────────────┐
 * │               │                 │
 * │    Pane 0     │     Pane 1      │
 * │               │                 │
 * └───────────────┴─────────────────┘
 *
 * HORIZONTAL: Stacked panes (top / bottom)
 * ┌─────────────────────────────────┐
 * │            Pane 0               │
 * ├─────────────────────────────────┤
 * │            Pane 1               │
 * └─────────────────────────────────┘
 * </pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public enum SplitLayout {
    /** No split - single pane takes full screen */
    NONE,

    /** Vertical split - panes side by side (left | right) */
    VERTICAL,

    /** Horizontal split - panes stacked (top / bottom) */
    HORIZONTAL;

    /**
     * Parse from command argument.
     * Accepts: "vertical", "v", "horizontal", "h", "none", "unsplit"
     */
    public static SplitLayout parse(final String arg) {
        if (arg == null || arg.isBlank()) return NONE;
        final String lower = arg.trim().toLowerCase();
        return switch (lower) {
            case "vertical", "v", "vert" -> VERTICAL;
            case "horizontal", "h", "horiz" -> HORIZONTAL;
            case "none", "unsplit", "close" -> NONE;
            default -> throw new IllegalArgumentException("Unknown split layout: " + arg +
                    ". Use: vertical (v), horizontal (h), or none");
        };
    }
}
