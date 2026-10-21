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

package studio.phaseshift.metatron.isa.mach.type;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The published perspective: the mtron instructions that move a thread are what makes a move visible to a reader
 * that never moves (the status bar). Machine.push()/pop() themselves deliberately do NOT publish — a frame minted by
 * the machine's own machinery is not the user changing where they stand.
 */
public class MachinePerspectiveTest extends AbstractMetatronTest {

    @Test
    void shouldPublishWhereMoveTakesTheThread() {
        Machine.evaluate("move()");   // up from the root saturates at the root, but it is still a move
        final MachinePerspective.Step step = MachinePerspective.last();
        assertNotNull(step, "move() publishes the perspective");
        assertEquals(Machine.root(), step.machine(), "the machine the thread moved to");
        assertFalse(step.thread().isEmpty(), "and it names the thread that moved, for a reader that cares");
        assertEquals(Machine.root().vid(), MachinePerspective.vid(), "the vid a status bar would draw");
    }

    @Test
    void shouldTimestampEveryMove() {
        Machine.evaluate("move()");
        final long first = MachinePerspective.last().at();
        Machine.evaluate("move()");
        assertTrue(MachinePerspective.last().at() >= first, "each move carries its own time, so a reader can age it");
    }

    /**
     * A push and its pop are moves too, and they are the case a prompt-line expression cannot show on its own.
     */
    @Test
    void shouldFollowAPushAndItsPop() {
        final Obj pushed = Machine.evaluate("push()");
        assertTrue(pushed.isMachine(), "push() minted and entered a machine: " + pushed);
        final Machine child = pushed.asMachine();
        assertEquals(child, MachinePerspective.last().machine(), "the push publishes the frame it entered");
        assertEquals(child.vid(), MachinePerspective.vid(), "and the vid the bar would draw");
        // the prompt would evaluate the next line from where the push left the thread
        Machine.current(child);
        Machine.evaluate("pop()");
        assertNotNull(MachinePerspective.last(), "a pop publishes too");
        assertEquals(Machine.root(), MachinePerspective.last().machine(),
                "and the frame it landed on is what the bar shows again");
    }
}
