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

package studio.phaseshift.metatron.isa.m.mach;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.mach.type.Component;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;

/**
 * ONE CONTRACT, EVERY PREFIXED COMPONENT. {@link AbstractMetatronTest#testParentalChildPrefix(Component)} is
 * the seam matrix for the parental prefix algorithm: inheritance through the view (never a copy), shadowing, giving
 * the shadow back, per-level membership. A component joins this file with one line.
 */
public class PrefixParentalTest extends AbstractMetatronTest {

    @Test
    void theNetworkHonoursTheParentalPrefixContract() {
        AbstractMetatronTest.testParentalChildPrefix(Machine.current().network());
    }

    @Test
    void theInstSetHonoursTheParentalPrefixContract() {
        AbstractMetatronTest.testParentalChildPrefix(Machine.current().instset());
    }

    @Test
    void theMemoryHonoursTheParentalPrefixContract() {
        AbstractMetatronTest.testParentalChildPrefix(Machine.current().memory());
    }
}
