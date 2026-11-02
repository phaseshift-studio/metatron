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

package studio.phaseshift.metatron.isa.m.mach.network;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import studio.phaseshift.metatron.isa.m.mach.type.Network;

/**
 * CONCURRENT MOVES INTO ONE FRAME. The loop peer is this VM, so threads that move through it share the machine tree,
 * its memory and its spaces: {@code Network.Helper.boundary} scopes THREAD state (perspective, arg frames, thread
 * object), never data. This stress asks the DATA half the question the seam matrix leaves open — do concurrent shipped
 * programs land their writes, and does one thread's move stay out of every other thread's perspective?
 * <p>
 * The two assertions are deliberately different in kind:
 * <ul>
 *   <li><b>no leak</b> — after each move, the moving thread's own machine is where it stood (a ThreadLocal cannot leak
 *   across threads, but the boundary also restores WITHIN the thread, and a shared perspective would show here);</li>
 *   <li><b>no loss</b> — every shipped write is readable afterwards. The writes go to distinct keys under ONE space
 *   ({@code /sys/concurrent}), so a lost write means the shared tree's write path is not concurrency-safe, which no
 *   single-threaded test can see. (A machine's own rec refuses stray keys — measured — so the shared space is the
 *   place a raw shipped write can actually land.)</li>
 * </ul>
 * A failure here is a finding, not a flake to retry: the loop peer's data is shared by construction, so if the write
 * path races, a real deployment sees it only when two machines share a backend.
 */
public class MoveConcurrencyStressTest extends AbstractMetatronTest {

    private static final int THREADS = 8;
    private static final int ROUNDS = 10;
    private static final fURI SELF = f("loop://self");

    @Test
    void concurrentMovesIntoOneFrameLoseNothingAndLeakNothing() throws Exception {
        final Machine root = Machine.current();
        Network.Helper.connect(root.network(), SELF);
        root.push(f("hot"));
        Machine.current(root);
        final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            final List<Future<?>> moving = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                final int id = t;
                moving.add(pool.submit(() -> {
                    for (int r = 0; r < ROUNDS; r++) {
                        final int round = r;
                        final fURI stood = Machine.current().vid();
                        final Obj answer = Machine.evaluate(
                                "5.move(<loop://self/hot>).to(</sys/concurrent/w" + id + "_" + round + ">)");
                        assertFalse(answer.isFail(), "thread " + id + " round " + round + ": " + answer);
                        assertEquals(stood, Machine.current().vid(),
                                "thread " + id + " round " + round + " was re-homed by its own move");
                    }
                    return null;
                }));
            }
            for (final Future<?> one : moving)
                one.get(120, TimeUnit.SECONDS);
            for (int t = 0; t < THREADS; t++)
                for (int r = 0; r < ROUNDS; r++)
                    assertFalse(Machine.read(f("/sys/concurrent/w" + t + "_" + r)).isNoObj(),
                            "the write shipped by thread " + t + " round " + r + " survived the concurrent moves");
        } finally {
            pool.shutdownNow();
            Machine.Helper.evalAt(f("/hot"), () -> Machine.current().pop());
            Machine.current(root);
            root.network().drop(SELF);
        }
    }
}
