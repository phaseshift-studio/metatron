/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it/or modify
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

package studio.phaseshift.metatron.isa.sys.type;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * push/pop symmetry of the execution stack, including under the frame cap: a
 * pipeline nested deeper than the cap (a deep {@code {!*}} read of an inst
 * space resolves dozens of insts at once) must unwind cleanly — the frame the
 * cap skips is the frame that must not be popped.
 */
public class ExecutionStackTest {

    private static int descend(final int i, final int depth) {
        return ExecutionStack.frame(ExecutionStack.exec(ExecutionStack.ExState.apply_inst, "frame-" + i),
                () -> i < depth ? descend(i + 1, depth) : 0);
    }

    private static void descendThrows(final int i, final int until) {
        ExecutionStack.frame(ExecutionStack.exec(ExecutionStack.ExState.apply_inst, "frame-" + i), () -> {
            if (i >= until)
                throw new IllegalStateException("boom at depth " + i);
            descendThrows(i + 1, until);
            return null;
        });
    }

    @Test
    public void testDeepNestingUnwindsCleanlyPastTheCap() {
        ExecutionStack.clear();
        assertDoesNotThrow(() -> descend(0, 128),
                "128-deep frame nesting must unwind cleanly past the 64-frame cap");
        assertTrue(ExecutionStack.empty(),
                "stack must be empty after the unwind");
    }

    @Test
    public void testDeepExceptionStillSurfacesPastTheCap() {
        ExecutionStack.clear();
        assertThrows(IllegalStateException.class, () -> descendThrows(0, 80),
                "an exception raised past the cap must still surface — not be replaced by a stack-corruption fault");
        assertTrue(ExecutionStack.empty(),
                "every landed frame must have been popped on the way out");
    }

    @Test
    public void testIdenticalFramesCollapseInRender() {
        ExecutionStack.clear();
        final int repeats = 50;
        for (int i = 0; i < repeats; i++)
            ExecutionStack.push(ExecutionStack.exec(ExecutionStack.ExState.resolve_inst, "read /m/inst/auto_from?rng=#{*}&dom=#{?}"));
        ExecutionStack.push(ExecutionStack.exec(ExecutionStack.ExState.apply_inst, "/m/inst/as?rng=/m/lst&dom=/m/int"));
        final String rendered = ExecutionStack.generateStackTrace();
        final String line = "resolve_inst: read /m/inst/auto_from?rng=#{*}&dom=#{?}";
        final int occurrences = rendered.split(java.util.regex.Pattern.quote(line), -1).length - 1;
        assertEquals(2, occurrences,
                "the repeated frame must render once, plus the collapse marker naming it, not " + repeats + " times; got: " + rendered);
        assertTrue(rendered.contains("… (×49 more — same frame repeated:"),
                "the collapse marker should name the count, got: " + rendered);
    }
}
