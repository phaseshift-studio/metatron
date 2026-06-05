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
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractDataPathTest extends AbstractSpaceTest {

    public AbstractDataPathTest(final fURI baseURI, final Supplier<Space> spaceSupplier) {
        super(baseURI, spaceSupplier);
    }

    /**
     * Verifies the collection-URI-is-schema-instset contract: collection-level
     * DataPath segments resolve to instset-encoded {@link studio.phaseshift.metatron.isa.m.type.Type}
     * objects, while entry-level segments resolve to data instances (Recs, etc.)
     * that are valid refinements of their collection's Type.
     *
     * <p>{@code $$} is replaced by the subclass's base URI via {@link #make(String)}.
     * {@code +} is the single-level wildcard.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "*<$$/+>            % collection",    // wildcard collection → every result is a Type
            // "*<$$/+>.take(1) % collection",    // TODO: .take(1) on collection wildcard returns entry, not Type — investigate parser/space interaction
            "*<$$/+/+>.take(1)  % entry",         // wildcard collection + wildcard entry → first is instance
            "*<$$/+/+>.take(2)  % entry",         // second entry also an instance (not just first)
    }, delimiter = '%')
    public void testDataPathSegmentTypes(final String code, final String segmentType) {
        final Obj result = ObjmtronSerializer.parse(make(code)).apply();
        assertFalse(result.isNoObj(), "test data for " + segmentType + " should not be noobj: " + code);
        if (segmentType.equals("collection")) {
            result.stream().forEach(o ->
                    assertTrue(o.isType(), "collection objs should be the type schema of the collection elements: " + o));
        } else if (segmentType.equals("entry")) {
            result.stream().forEach(entry -> {
                assertFalse(entry.isType(), "entry objs should not be types but instances of their collection schema: " + entry);
                // Read the collection Type(s) for this entry via its VID
                // retract(1) strips the entry segment (e.g., /g/E/7 → /g/E)
                final Obj collectionTypes = Router.readFromSpace(entry.vid().retract(1));
                assertFalse(collectionTypes.isNoObj(),
                        "collection type(s) should exist for entry: " + entry.vid() + " → " + entry.vid().retract(1));
                // Stream in case the collection prefix resolves to multiple Types
                // (e.g., /g/E returns both knows::T and created::T)
                final boolean isValidInstance = collectionTypes.stream()
                        .anyMatch(ct -> entry.test(ct));
                assertTrue(isValidInstance,
                        "entry should be a valid instance of at least one collection type: "
                                + entry + " ∉ " + collectionTypes);
                final boolean isMatchingType = collectionTypes.stream()
                        .anyMatch(ct -> entry.type().test(ct));
                assertTrue(isMatchingType,
                        "entry type must be equal to or a refinement of at least one collection type: "
                                + entry.type() + " ≮: " + collectionTypes);
            });
        } else {
            fail("bad test definition as segment type is unknown: " + segmentType);
        }
    }
}
