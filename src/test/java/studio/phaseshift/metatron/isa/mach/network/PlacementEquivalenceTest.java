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

package studio.phaseshift.metatron.isa.mach.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

/**
 * The emitter's equivalence obligation (§12.5): a {@code law_shard} lowered over a loop peer — the local transport,
 * no forked process — produces the same tree layout the forked-peer suite asserts, and evaluates to the value the
 * undistributed fold answers. The loop transport applies the shipped shard against the local machine, so this is the
 * fast, deterministic guard for the {@code Env}-driven emitter.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
//@Isolated
//@Execution(ExecutionMode.SAME_THREAD)
public class PlacementEquivalenceTest extends AbstractMetatronTest {

    @BeforeEach
    void setup() {
        // typing is not solved across distributed executions yet — run type-check-free, as the forked-peer suite does
        TypeCheck.disable(TypeCheck.values());
        InstSet.importInstSet(f("/m/math"));
        InstSet.importInstSet(f("/m/math/cat"));
        Machine.current().compiler(CatTheoryCompiler.single());
    }

    @AfterEach
    void teardown() {
        Machine.current().network().disconnect(f("loop://localhost:8555"));
    }

    /**
     * One loop peer fans the shard out through the local transport, so the distributed layout recombines to the
     * undistributed fold's value. Each row: the fold % its value.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4,5,6}.plus(1).sum() % 27",
            "{1,2,3,4,5,6}.count()     % 6",
    }, delimiter = '%')
    void testShardEquivalentToLocalFold(final String source, final int expected) {
        Machine.current().network().connect(f("loop://localhost:8555"));
        final Code compiled = Machine.current().compiler().rewriter()
                .apply(ObjmtronSerializer.parse(source).asCode()).asCode();
        // the emitter fired the tree layout: the master leads start·select·select·merge·sum
        assertEquals(List.of(START_INST_TID, SELECT_INST_TID, SELECT_INST_TID, MERGE_INST_TID, SUM_INST_TID),
                TransportRewriteHarness.ops(compiled), "the master recombines through the combiner: " + compiled);
        checkEquality(LOG, jnt(expected),
                TransportRewriteHarness.single(TransportRewriteHarness.evaluate(compiled)).clone().selfVID(null), true);
    }
}
