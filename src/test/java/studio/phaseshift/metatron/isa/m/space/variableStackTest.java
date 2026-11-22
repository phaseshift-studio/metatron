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

package studio.phaseshift.metatron.isa.m.space;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * The variable stack (the machine's arg stack) is where a relative write ({@code a -> 14}) must persist
 * its binding so a later relative read ({@code *a}) finds it — the write lands in the stack's persistent
 * root, not in a frame that a subsequent evaluation pops away.
 */
public class variableStackTest extends AbstractMetatronTest {

    @ParameterizedTest
    @TestData(value = {"a -> 14"}, oneTime = true)
    @CsvSource(value = {
            "*a             % 14",
            "a->13; *a      % 13",
            "*a             % 13",
    }, delimiter = '%')
    void testVariablePersistence(final String code, final String expected) {
        checkCodeEvaluate(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            // A BRANCH URI IS A SUM OF ADDRESSES (Marko, 2026-11-10): br/{a,b}/c -> 23 binds br/a/c and br/b/c, and
            // *br/{a,b}/c reads both into one stream
            "br/{a,b}/c -> 23; *br/{a,b}/c                 % {2}23",
            "br/{a,b}/c -> 23; *br/a/c                     % 23",
            "br/{a,b}/c -> 23; *br/b/c                     % 23",
            // an unbound branch adds nothing
            "br/{a,b}/c -> 23; *br/{b,q}/c                 % 23",
            // the branches are separate bindings: rebinding one leaves the other
            "br/{a,b}/c -> 23; br/a/c -> 7; *br/{a,b}/c    % {7,23}",
            // the write answers its obj, as any write does (x -> y is y), however many bindings it made
            "br/{a,b}/c -> 23                              % 23",
    }, delimiter = '%')
    void testBranchBindings(final String code, final String expected) {
        checkCodeEvaluate(LOG, code, expected);
    }

    @Test
    void testConstructingAStackInAChildDoesNotShadowTheParent() {
        final Machine parent = Machine.current();
        final Machine child = parent.push();
        try {
            // The arg stack is the thread's local variables, reached via Memory.argStack(), not a machine space.
            // Constructing one while a child is current must not put a +/# into the child's index — its +/# would
            // match /sys/log and /m/inst and shadow the parent's real spaces.
            new variableStack(f("+/#"));

            final boolean stackSpaceInIndex = child.memory().spaces().jvm().values().stream()
                    .map(o -> (Space) o)
                    .anyMatch(s -> f("+/#").equals(s.pattern()));
            assertFalse(stackSpaceInIndex, "the arg stack must not register +/# into the frame's space index");
        } finally {
            child.pop();
        }
    }
}
