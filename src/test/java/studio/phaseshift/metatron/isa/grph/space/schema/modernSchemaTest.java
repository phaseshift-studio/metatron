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

package studio.phaseshift.metatron.isa.grph.space.schema;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.GRPH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

/**
 * The modern schema's types reach EVERY machine that sets up grph, not only the first machine the JVM boots.
 * <p>
 * They used to be registered only as a side effect of {@link modernSchema}'s static initializer, which runs once per
 * JVM. Surefire reuses a fork's JVM across test classes and boots a machine per class, so whichever class touched the
 * schema first kept the types: TinkerGrphSpaceTest lost {@code person}'s rec parent and its schema reads whenever an
 * {@code import #} (webRouteTableTest) or another grph test had run before it in its fork. Each row imports in the
 * machine it starts in (initializing the schema there, if nothing did before), then reboots the VM the way the next
 * class in a fork does, so its checks always run in a machine created after the schema class initialized.
 */
class modernSchemaTest extends AbstractMetatronTest {

    @ParameterizedTest
    @CsvSource(value = {
            "#       % /m/grph/schema/modern/person   % /m/rec",
            "/m/grph % /m/grph/schema/modern/software % /m/rec",
            "/m/grph % /m/grph/schema/modern/knows    % /m/rec",
            "#       % /m/grph/schema/modern/created  % /m/rec",
    }, delimiter = '%')
    void testTypesReachEveryMachine(final String imported, final String vid, final String parent) {
        InstSet.importInstSet(f(imported));
        end();   // the class before closes its machine...
        begin(); // ...and the next class in the fork boots its own, in the same JVM
        InstSet.importInstSet(GRPH_ISA_TID);
        final Obj registered = Machine.read(f(vid));
        assertTrue(registered.isType(), () -> vid + " is not registered in the rebooted machine: " + registered);
        assertEquals(f(parent), registered.tid());
        final Type type = T(f(vid));
        assertFalse(type.isEphemeral(), () -> vid + " resolved to an ephemeral label: " + type);
        assertTrue(type.isRefinementOf(T(f(parent))), () -> vid + " does not refine " + parent);
    }
}
