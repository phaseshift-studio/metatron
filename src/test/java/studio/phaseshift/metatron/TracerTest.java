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
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Obj;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_TRACER_TID;

/**
 * THE TRACER'S STATE IS THE REC AT {@code /sys/tracer}, typed by {@code tracer::T} at {@code /m/mach/component/tracer}:
 * the boot writes it there, and {@link Tracer#enable}/{@link Tracer#disable} keep it in step with the stages they
 * switch.
 */
public class TracerTest extends AbstractMetatronTest {

    @Test
    public void theTracerStateIsTheRecAtSysTracer() {
        final boolean wasOn = Tracer.java_stack.enabled();
        try {
            final Obj type = Machine.read(MACH_TRACER_TID);
            assertTrue(type.isType(), "tracer::T lives at /m/mach/component/tracer");
            Tracer.enable(Tracer.java_stack);
            final Obj on = Machine.read(f("/sys/tracer/stack/java_stack"));
            assertTrue(on.boolValue(), "enable writes the stage into the tracer's rec");
            Tracer.disable(Tracer.java_stack);
            final Obj off = Machine.read(f("/sys/tracer/stack/java_stack"));
            assertFalse(off.boolValue(), "disable writes it back");
        } finally {
            if (wasOn)
                Tracer.enable(Tracer.java_stack);
            else
                Tracer.disable(Tracer.java_stack);
        }
    }
}
