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

package studio.phaseshift.metatron.isa;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * The {@code ?asq} read: an {@code as} instruction is a property-graph edge — label {@code as}, outV the
 * instruction's dom, inV its rng — and the read names that edge by its endpoints, whether or not a like-named
 * {@code as} instruction is registered, handing it back carrying its property map
 * ({@link studio.phaseshift.metatron.furi.q.AsQ#asEdgeKinds}).
 */
public class AsQTest extends AbstractObjTest {

    @BeforeAll
    public static void importAllInstSets() {
        InstSet.importInstSet(f("#"));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*/m/inst/as?int<=int&asq            % /m/inst/as?rng=/m/int&asq=[incomparable,retract]&dom=/m/int % a self-loop is the identity, an idempotent, so it retracts itself",
            "*/m/inst/as?str<=int&asq            % /m/inst/as?rng=/m/str&asq=[incomparable,coupling]&dom=/m/int % int and str cast into each other, and int's other casts are disjoint from str",
            "*/m/inst/as?str<=int&asq=[coupling] % /m/inst/as?rng=/m/str&asq=[coupling]&dom=/m/int % the query narrows the property map, it never unions it",
    }, delimiter = '%')
    void testAsqReadsAnEdgeByItsEndpoints(final String read, final String expected, final String why) {
        checkCodeParseApply(LOG, read.trim(), expected.trim());
    }

    @Test
    public void testAnEdgeWithNoneOfTheAskedKindsIsNothing() {
        // the read yields no obj at all, rather than the un-augmented instruction the address would have matched
        assertEquals(noobj(), ObjmtronSerializer.parse("*/m/inst/as?int<=int&asq=[duplicate]").apply(),
                "an edge whose property map holds none of the asked kinds has no such property to read");
    }
}
