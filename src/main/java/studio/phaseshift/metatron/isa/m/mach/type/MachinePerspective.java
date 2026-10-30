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

package studio.phaseshift.metatron.isa.m.mach.type;

import studio.phaseshift.metatron.furi.fURI;

/**
 * WHERE THE VM LAST STOOD — the machine a reader on a thread that never moves can see.
 * <p>
 * {@link Machine#current()} is a THREAD-LOCAL, and that is right for code: a frame is a place one thread evaluates
 * in. But an observer is not evaluating anything — a status bar's render thread, a dashboard, a logger — and asking
 * {@code Machine.current()} there answers with the root forever. That is why the console's machine widget used to
 * read {@code ./} while an evaluation stood in a peer's frame.
 * <p>
 * So a move PUBLISHES itself here, and observers read the publication. The writers are the mtron instructions that
 * move a thread deliberately — {@code move}, {@code push}, {@code pop} — and not {@link Machine#push()} itself: a
 * frame minted by the machine's own machinery (bootstrap, a peer fork's internal pushes, a worker's scope) is not
 * the user changing where they stand, and publishing it would make the bar flicker through frames nobody asked for.
 * <p>
 * The address is always LOCAL. A frame that stands in a peer is registered at a local path
 * ({@code /network/peer/ws/host/port/ns}), never at the wire address — so there is no {@code ws://} to draw here.
 * <p>
 * It is deliberately LAST-WRITER-WINS: several threads may stand in frames at once, and a glanceable bar reports the
 * most recent move rather than pretending to aggregate them. The thread and the time are carried so a reader can
 * prefer one thread, or age a stale entry, without the writer changing.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class MachinePerspective {

    /**
     * one published move: the machine the thread moved to, which thread moved it, and when.
     */
    public record Step(Machine machine, String thread, long at) {
    }

    private static volatile Step last = null;

    private MachinePerspective() {
        // static only
    }

    /**
     * Publish where a thread now stands. Called by the frame instructions ({@code move}, {@code push}, {@code pop})
     * with what they returned.
     *
     * @param machine the machine that is now current for the moving thread
     */
    public static void publish(final Machine machine) {
        if (null == machine)
            return;
        last = new Step(machine, Thread.currentThread().getName(), System.currentTimeMillis());
    }

    /**
     * @return the last published move, or null when none has happened yet
     */
    public static Step last() {
        return last;
    }

    /**
     * The vid an observer should draw: the published machine's, else the root's.
     */
    public static fURI vid() {
        final Step step = last;
        return null == step ? Machine.root().vid() : step.machine().vid();
    }
}
