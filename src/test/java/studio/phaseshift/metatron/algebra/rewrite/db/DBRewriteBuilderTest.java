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

package studio.phaseshift.metatron.algebra.rewrite.db;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.db.dcmnt.dcmntInstSet;
import studio.phaseshift.metatron.isa.m.db.grph.grphInstSet;
import studio.phaseshift.metatron.isa.m.db.tble.tbleInstSet;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.db.dcmnt.dcmntInstSet.DCMNT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.db.grph.grphInstSet.GRPH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.db.tble.tbleInstSet.TBLE_ISA_TID;

/**
 * A DATABASE REWRITE IS THE IDENTITY OFF ITS SPACE (Marko, 2026-11-12): a rewrite touches only a chain that reads a
 * space of its type, the space whose native instruction it calls (a tble's sql(), a grph's gremlin(), a dcmnt's mql).
 * Anywhere else (a seed such as start({1,2,3}), a filter before the fold, a read of a space of another type) it hands
 * the code back unchanged, never a fail. Before, sql_count and gremlin_count, whose first match position is a
 * wildcard, read start({1,2,3})'s bag as a uri and answered a fail.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DBRewriteBuilderTest extends AbstractMetatronTest {

    @BeforeAll
    public static void importDatabases() {
        InstSet.importInstSet(TBLE_ISA_TID);
        InstSet.importInstSet(GRPH_ISA_TID);
        InstSet.importInstSet(DCMNT_ISA_TID);
    }

    /**
     * Each row: a chain no database space sources, which every database rewrite of the pool leaves as it is.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3}.count()",
            "{1,2,3}.is(gt(1)).count()",
            "{1,2,3}.neg().count()",
            "{1,2,3}.sum()",
            "{1,2,3}.take(2)",
            "{1,2,3}.skip(1).take(1)",
            "{1,2,3}.order()",
            "{1,2,3}.dedup()",
            "*</rewrite/elsewhere>.count()",
    }, delimiter = '%')
    void testADatabaseRewriteLeavesAChainOffItsSpaceAsItIs(final String chain) {
        final Obj code = ObjmtronSerializer.parse(chain);
        assertTrue(code.isCode(), () -> "the chain does not parse: " + code);
        final List<Inst> rewrites = Machine.current().instset().members().stream()
                .filter(isa -> isa instanceof tbleInstSet || isa instanceof grphInstSet || isa instanceof dcmntInstSet)
                .flatMap(isa -> isa.rewrites().stream())
                .toList();
        assertFalse(rewrites.isEmpty(), "the database rewrites are in the pool");
        rewrites.forEach(rewrite -> assertEquals(code, rewrite.apply(code), () -> rewrite.tid().name() + " leaves " + chain + " as it is"));
    }
}
