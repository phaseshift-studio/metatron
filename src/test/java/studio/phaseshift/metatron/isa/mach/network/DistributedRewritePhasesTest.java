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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.mach.AbstractMultiServerSingleSpaceTest;

import java.util.function.Supplier;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.QPROC;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE DISTRIBUTION REWRITE, decomposed: the same end-to-end run as {@link DistributedRewriteTest}, but driven
 * through {@link DistributedRewriteHarness}'s four first-class phases — compile (the master and per-peer worker
 * forms), execute (each machine demonstrably ran), provenance (each partial pinned to its own seed slice), and result
 * (the folded total) — with the topology declared and cleared around each case.
 *
 * <p>THE ROWS declare their own cluster, one peer per {@code |} in the last column, and the total the home must fold
 * to. The slices, the per-peer partials, and the forms are all DERIVED by the harness from the source and the peer
 * count — the row states only what the user wrote and what it should total:
 *
 * <ul>
 *   <li>one peer: {@code per = ceil(3/2) = 2}, home keeps {10,20} and the peer takes {30}</li>
 *   <li>the SAME code and SAME total over two peers, sliced three ways instead of two</li>
 *   <li>more data than machines, so the slices are uneven and the last machine takes the remainder</li>
 *   <li>a reducer that is NOT last: the trailing work stays on the home and never reaches a worker</li>
 * </ul>
 */
public class DistributedRewritePhasesTest extends AbstractMultiServerSingleSpaceTest {

    public DistributedRewritePhasesTest() {
        super(f("/sys/space/compute"));
    }

    @Override
    protected Supplier<Space> computeSpace() {
        return () -> memSpace.of(rec(
                uri(PATTERN), uri("/usr/compute/#"),
                uri(QPROC), lst(QCollection.subQ())), nextComputeVID());
    }

    @ParameterizedTest
    @CsvSource(value = {
            // one peer, per = ceil(3/2) = 2: home {10,20} -> 40, peer b {30} -> 35, fold 75
            "{10,20,30}.plus(5).sum()             %   75  %  b",
            // the SAME code and the SAME 75 as the row above, over two peers (per = 1, three slices)
            "{10,20,30}.plus(5).sum()             %   75  %  b|c",
            // five values over two peers, uneven: home {5,6} -> 15, b {7,8} -> 19, c {9} -> 11, fold 45
            "{5,6,7,8,9}.plus(2).sum()            %   45  %  b|c",
            // the reducer is not last: home {1} -> 4, b {2} -> 5, c {3} -> 6, fold 15, then +100 stays home
            "{1,2,3}.plus(1).plus(2).sum().plus(100)  %  115  %  b|c",
    }, delimiter = '%')
    public void testFourPhases(final String code, final String result, final String peers) {
        DistributedRewriteHarness.verify(LOG, code, result, peers.split("\\|"));
    }

    /**
     * The four phases, spelled out step by step, so a reader sees the harness's surface directly rather than through
     * the one-shot {@code verify}: declare the cluster, compile and inspect the forms, execute and read the signals,
     * pin the provenance, check the fold, and clear.
     */
    @Test
    public void testFourPhasesExplicitly() {
        DistributedRewriteHarness.declare("b", "c");
        try {
            final DistributedRewriteHarness.Compilation compilation =
                    DistributedRewriteHarness.compile(LOG, "{2,4,6,8,10,12}.plus(3).sum()", "b", "c");
            final DistributedRewriteHarness.Execution execution = DistributedRewriteHarness.execute(LOG, compilation);
            DistributedRewriteHarness.assertProvenance(LOG, compilation, execution);
            DistributedRewriteHarness.assertResult(LOG, execution, "60");
        } finally {
            DistributedRewriteHarness.clear();
        }
    }
}
