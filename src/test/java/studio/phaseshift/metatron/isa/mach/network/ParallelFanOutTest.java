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
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * PARALLEL FAN-OUT (Thm 5, entry D). The shard's fan-out submits every round trip at once — one virtual thread per
 * route — and the gather ({@code >>rhs}) awaits them all, so latency is the slowest shard, not the sum. A loop peer
 * whose {@code eval} sleeps before it answers makes the serial-vs-parallel difference measurable without the wire: two
 * {@code delayMs} shards dispatched together start within microseconds of each other, while a serial fan-out would
 * separate their start times by a full delay. The fan-out is measured through the emitted layout
 * ({@code start · select(virtual routes) · select(>>rhs) · merge · combiner}) and through the gap between the two
 * {@code eval} bodies running.
 */
//@Isolated
//@Execution(ExecutionMode.SAME_THREAD)
public class ParallelFanOutTest extends AbstractMetatronTest {

    private static final fURI SLOW_A = f("loop://slow-a");
    private static final fURI SLOW_B = f("loop://slow-b");

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
        Machine.current().network().disconnect(SLOW_A);
        Machine.current().network().disconnect(SLOW_B);
    }

    /**
     * Two slow peers, two shards. The layout fans out through a virtual-threaded select and gathers through a second
     * select of {@code >>rhs}; the two {@code eval} bodies start together (their gap is a scheduling epsilon, far under
     * one delay), never one delay apart. The recombined value is the value the undistributed fold answers.
     */
    @Test
    void testFanOutDispatchesConcurrently() {
        final long delayMs = 500;
        final List<Long> starts = Collections.synchronizedList(new ArrayList<>());
        Network.Helper.index(slowPeer(SLOW_A, delayMs, starts));
        Network.Helper.index(slowPeer(SLOW_B, delayMs, starts));

        final Code compiled = Machine.current().compiler().rewriter()
                .apply(ObjmtronSerializer.parse("{1,2,3,4,5,6}.plus(1).sum()").asCode()).asCode();
        // the fan-out is a virtual-threaded select, then a gather select of >>rhs, then merge + combiner
        final DistributedSweep.Layout layout = DistributedSweep.layout(compiled);
        assertEquals(List.of(START_INST_TID, SELECT_INST_TID, SELECT_INST_TID, MERGE_INST_TID, SUM_INST_TID),
                layout.ops(), "the master fans out then gathers: " + compiled);
        assertEquals(List.of(SLOW_A, SLOW_B), layout.routes(),
                "one route per peer, peeled off the virtual threads: " + compiled);

        checkEquality(LOG, jnt(27),
                TransportRewriteHarness.single(TransportRewriteHarness.evaluate(compiled)).clone().selfVID(null), true);

        assertEquals(2, starts.size(), "two peers, two shards");
        final List<Long> ordered = new ArrayList<>(starts);
        Collections.sort(ordered);
        final long gapMs = (ordered.get(1) - ordered.get(0)) / 1_000_000L;
        assertTrue(gapMs < delayMs / 2,
                "the two shards were dispatched together (gap=" + gapMs + "ms < " + (delayMs / 2) + "ms), not serially: " + starts);
    }

    /**
     * A loop-style peer whose {@code eval} sleeps {@code delayMs} then evaluates the shipped code locally, recording
     * when its override body ran — the observable that separates parallel dispatch (starts together) from serial
     * (each start a delay after the last).
     */
    private static Obj slowPeer(final fURI host, final long delayMs, final List<Long> starts) {
        final Rec overrides = rec();
        overrides.jvm().put(uri(EVAL_INST_TID), Network.Helper.override(EVAL_INST_TID, (lhs, inst) -> {
            starts.add(System.nanoTime());
            CommonUtil.sleepThread(delayMs);
            return lhs.apply(noobj());
        }));
        return rec(uri(HOST), uri(host), uri(INST), overrides, uri(STATUS), Network.Helper.statusOf(() -> true));
    }
}
