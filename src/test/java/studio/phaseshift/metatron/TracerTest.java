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

package studio.phaseshift.metatron;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.processor.Tracer;
import studio.phaseshift.metatron.isa.m.sys.ui.console.CommandPalette;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_TRACER_TID;

/**
 * THE TRACER IS THE PROCESSOR'S, IN THE TYPER'S FORM (Marko, 2026-11-10): its state is its rec at
 * {@code ~/processor/tracer/stack}, as the typer's is at {@code ~/compiler/typer/check}. There are two spellings of one
 * nested write, java and mtron; every trace point is off by default; and a pushed machine's processor carries a copy
 * of its parent's tracer, so the child traces as its parent did at the push and a later change stays on its side.
 */
public class TracerTest extends AbstractMetatronTest {

    @Test
    public void theTracerStateIsTheProcessorsStage() {
        // a machine of the test's own: a fork's classes share its root
        final Machine machine = Machine.current().push(f("tracing"));
        try {
            assertTrue(Machine.read(MACH_TRACER_TID).isType(), "tracer::T lives at /m/mach/component/processor/tracer");
            assertFalse(Tracer.Helper.javaStack(), "java_stack is off by default");
            assertFalse(Tracer.Helper.mtronStack(), "and so is mtron_stack");

            // the java spelling: one nested write into the tracer's state
            machine.processor().tracer().traces(Tracer.JAVA_STACK, true);
            assertTrue(Tracer.Helper.javaStack(), "the java write turns a trace point on");

            // the mtron spelling: the same act through the space
            ObjmtronSerializer.parse("@~/processor/tracer/stack >>= [java_stack => false]").apply();
            assertFalse(Tracer.Helper.javaStack(), "the mtron write turns it off");

            Tracer.Helper.set(Tracer.MTRON_STACK, true);
            assertTrue(Machine.read(f("~/processor/tracer/stack/mtron_stack")).boolValue(), "the helper writes what the address reads");

            final Machine child = machine.push(f("child"));
            try {
                assertTrue(Tracer.Helper.mtronStack(), "a pushed machine's processor carries a copy of its parent's tracer");
                Tracer.Helper.set(Tracer.MTRON_STACK, false);
                assertFalse(Tracer.Helper.mtronStack(), "the child's write is the child's");
            } finally {
                child.pop();
            }
            assertTrue(Tracer.Helper.mtronStack(), "and never reaches the parent");
        } finally {
            machine.pop();
        }
    }

    /**
     * THE CONSOLE CYCLES THE TRACER THROUGH ITS FOUR STATES (Marko, 2026-11-10): mtron, java, java/mtron, and
     * nothing, as alt+t cycles the typer's check points.
     */
    @Test
    public void theConsoleCyclesTheTracersFourStates() {
        final Machine machine = Machine.current().push(f("cycling"));
        try {
            final List<Set<fURI>> states = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                CommandPalette.cycleTracer();
                states.add(Tracer.Helper.enabled());
            }
            assertEquals(List.of(
                    Set.of(Tracer.MTRON_STACK),
                    Set.of(Tracer.JAVA_STACK),
                    Set.of(Tracer.MTRON_STACK, Tracer.JAVA_STACK),
                    Set.of()), states, "mtron, java, java/mtron, nothing");
        } finally {
            machine.pop();
        }
    }
}
