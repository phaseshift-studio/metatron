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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

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
        final Obj pushed = Machine.evaluate("push().*<~>");
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

    @ParameterizedTest
    @CsvSource(value = {
            // the value flows through every move spelling: move is a re-homing identity
            "5.move(<~>)            % 5",
            "5.move()               % 5",
            "5.move(<..>)           % 5",
            "5.move(<.>)            % 5",
            "push(abc)..34.to(~/sys/temp).move(</.>).abc.move(_).from(~/sys/temp)           % 34",
            "push(abc)..34.to(~/sys/temp).move(</.>).from(~/sys/temp)                       % noobj",
            // and an expression continues on the same data after the move
            "5.move(<~>).plus(1)    % 6",
            "5.move().mult(2)       % 10",
            "5.move(<..>).plus(3)   % 8",
            // the value flows through regardless of its type
            "[1,2,3].move(<~>)      % [1,2,3]",
            "[1,2,3].move().>-.sum()   % 6",
            "{1,2,3}.move(<~>).sum()% 6",
    }, delimiter = '%')
    void testMoveFlowsData(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * move() is data-transparent: it re-homes the machine as a side effect, and the value flows through so an
     * expression continues on the same data in the new machine.
     */
    @Test
    void shouldFlowDataThroughMove() {
        final Obj result = Machine.evaluate("5.move().plus(1)");
        assertEquals(jnt(6), result, "move re-homes the machine; the value flows through so plus(1) sees 5");
    }
}
