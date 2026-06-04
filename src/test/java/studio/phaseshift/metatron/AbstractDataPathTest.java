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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractDataPathTest extends AbstractSpaceTest {

    public AbstractDataPathTest(final fURI baseURI, final Supplier<Space> spaceSupplier) {
        super(baseURI, spaceSupplier);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*$$/+              % collection",
            "*$$/+/+.take(1)    % entry"
    }, delimiter = '%')
    public void testDataPathSegmentTypes(final String code, final String segmentType) {
        final Obj result = ObjmtronSerializer.parse(make(code)).apply();
        assertFalse(result.isNoObj(), "test data for " + segmentType + " should not be noobj");
        result.stream().forEach(o -> {
            if (segmentType.equals("collection")) {
                assertTrue(o.isType(), "collection objs should be the type schema of the collection elements:" + o);
            } else if (segmentType.equals("entry")) {
                assertFalse(o.isType(), "entry objs should not be types but instances of their collection schema: " + o);
            } else {
                fail("bad test definition as segment type is unknown: " + segmentType);
            }
        });

    }
}
