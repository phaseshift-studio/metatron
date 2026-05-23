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

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Bool;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class ThreadPerformanceBenchmark extends AbstractMetatronTest {

    public ThreadPerformanceBenchmark() {
        InstSet.importInstSet(f("/m/mach"));
        //TypeCheck.disable(TypeCheck.inst_dom,TypeCheck.obj_write,TypeCheck.type_ctor);
    }

    @Test
    public void testVirtualThreadConcurrency() throws InterruptedException {
        // Adjust this number to stress test harder (e.g., 5000, 10000)
        int threadCount = 2000;
        LOG.info("Starting benchmark with %d virtual threads", threadCount);
        LOG.info(ObjmtronSerializer.parse("*virtual").apply());

        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failCount = new AtomicInteger(0);

        long start = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            // Spawn a Java virtual thread that runs Metatron code
            Thread t = Thread.ofVirtual().name("mtron-bench-" + i).start(() -> {
                try {
                    //Parse and execute Metatron code: repeat plus(1) until > 100, starting at 1 -> returns 101
                    //VirtualThread mtronThread = VirtualThread.of(rec(uri(CODE),code(List.of(plus_(jnt(2)).tryToInst().as(),repeat_(rec(uri(CODE),plus_(jnt(1)), uri(UNTIL), is_(gt_(jnt(100))))).tryToInst().as()))).vid(f("thread_" + threadId)));
                    VirtualThread mtronThread = ObjmtronSerializer.parse("virtual::[code=>_.repeat(code=>plus(1),until=>is(gt(100)))]@thread_" + threadId).as();
                    mtronThread.apply(jnt(1));
                    //LOG.info(mtronThread);
                    if (!mtronThread.isFail() && mtronThread.result(60, TimeUnit.SECONDS).equals(jnt(101))) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                        LOG.error("Thread %d failed: %s", threadId, mtronThread);
                    }
                    //LOG.info(mtronThread);
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    LOG.error("Thread %d exception: %s", threadId, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all threads to finish
        boolean completed = latch.await(120, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - start;

        LOG.info("========================================");
        LOG.info("Benchmark completed in %d ms", duration);
        LOG.info("Threads: %d", threadCount);
        LOG.info("Successes: %d", successCount.get());
        LOG.info("Failures: %d", failCount.get());
        LOG.info("Throughput: %.2f threads/sec", (threadCount * 1000.0) / duration);
        LOG.info("Avg time per thread: %.2f ms", (double) duration / threadCount);
        LOG.info("========================================");

        assertTrue(completed, "Benchmark timed out!");
        assertEquals(0, failCount.get(), "Some threads failed!");
    }
}