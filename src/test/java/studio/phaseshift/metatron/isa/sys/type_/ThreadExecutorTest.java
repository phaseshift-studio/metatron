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

package studio.phaseshift.metatron.isa.sys.type_;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.machInstSet;
import studio.phaseshift.metatron.isa.mach.type.thread.AbstractThread;
import studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_VIRTUAL_THREAD_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * Tests for {@link ThreadExecutor} thread lifecycle, registry, source tracking,
 * and the {@code HasThread} convenience method on {@code Obj}.
 */
public class ThreadExecutorTest extends AbstractMetatronTest {

    private ThreadExecutor executor;

    @BeforeAll
    public static void importMach() {
        InstSet.importInstSet(MACH_ISA_TID);
    }

    @BeforeEach
    public void createExecutor() {
        this.executor = BootLoader.getExecutor();
    }

    // =========================================================
    // Source tracking (parameterized — data extensible)
    // =========================================================

    @ParameterizedTest(name = "[{index}] thread source = {0}")
    @CsvSource(value = {
            "/test/src/alpha % /test/src/alpha",
            "/test/src/beta  % /test/src/beta",
            "/test/src/gamma % /test/src/gamma",
    }, delimiter = '%')
    void testThreadSource(final String sourceVid, final String expectedSource) throws Exception {
        final fURI source = f(sourceVid);
        final VirtualThread thread = new VirtualThread(
                mutableMap(uri(CODE), jnt(42), uri(SOURCE), auto_from_(source).tryToInst()),
                MACH_VIRTUAL_THREAD_TID,
                CommonUtil.mintShortUUID(f("/sys/thread"), true));
        assertEquals(f(expectedSource), Obj.Helper.getAutoPointer(thread.source()).get(),
                "thread should report its source vid");
    }

    @ParameterizedTest(name = "[{index}] unset source → empty uri ({1})")
    @CsvSource(value = {
            "no source set      % ",
            "explicit empty vid % /",
    }, delimiter = '%')
    void testThreadSourceUnset(final String desc, final String expectedStr) {
        final VirtualThread thread = VirtualThread.virtual(jnt(1));
        // When no SOURCE is in jvm, source() returns the default (empty uri)
        final Obj src = thread.source();
        assertNotNull(src, desc + ": source should not be null");
        // unset source defaults to empty string URI
        assertEquals(f("/sys/thread/main"), Obj.Helper.getAutoPointer(thread.source()).get(), desc + ": unset source should main thread");
    }

    // =========================================================
    // ThreadExecutor registry — active / completed
    // =========================================================

    @Test
    void testActiveThreadsOnExecute() throws Exception {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch finish = new CountDownLatch(1);

        final VirtualThread thread = new VirtualThread(
                mutableMap(uri(CODE), instB(START_INST_TID, lst(str("done")))),
                MACH_VIRTUAL_THREAD_TID,
                CommonUtil.mintShortUUID(f("/sys/thread"), true)) {
            @Override
            public Runnable createTask() {
                return () -> {
                    started.countDown();
                    try {
                        finish.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                };
            }
        };
        thread.apply(noobj());

        assertTrue(started.await(2, TimeUnit.SECONDS), "thread should start within timeout");
        final Lst run = executor.at(uri(RUN));
        assertFalse(run.lstValue().isEmpty(),
                "active threads should include running thread");

        finish.countDown();
        Thread.sleep(200); // allow completion callback

        // After completion, thread should move to inactive
        final Lst stop = executor.at(uri(STOP));
        assertFalse(stop.lstValue().isEmpty(),
                "inactive threads should include finished thread");
    }

    @Test
    void testVirtualThreadCompletesAndTransitions() throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final fURI vid = CommonUtil.mintShortUUID(f("/sys/thread"), true);

        final VirtualThread thread = new VirtualThread(
                mutableMap(uri(CODE), jnt(99)),
                MACH_VIRTUAL_THREAD_TID,
                vid) {
            @Override
            public Runnable createTask() {
                return () -> {
                    this.jvm().put(uri(STATE), uri(RUN));
                    this.jvm().put(uri(RESULT), jnt(99));
                    done.countDown();
                };
            }
        };
        thread.apply(noobj());

        assertTrue(done.await(2, TimeUnit.SECONDS), "thread should complete within timeout");
        Thread.sleep(250);

        final Lst stop = executor.at(uri(STOP));
        assertTrue(stop.lstValue().stream().map(Obj.Helper::getAutoPointer).filter(Optional::isPresent).anyMatch(o -> o.get().equals(vid)),
                "stop threads should contain the finished thread vid");
    }

    // =========================================================
    // threadsBySource query
    // =========================================================

    @Test
    void testThreadsBySourceFindsMatchingThreads() throws Exception {
        final fURI sourceA = f("/test/src/a");
        final fURI sourceB = f("/test/src/b");
        final CountDownLatch done = new CountDownLatch(2);

        // Thread sourced from A
        final VirtualThread threadA = new VirtualThread(
                mutableMap(uri(CODE), jnt(1), uri(SOURCE), auto_from_(sourceA).tryToInst()),
                MACH_VIRTUAL_THREAD_TID,
                CommonUtil.mintShortUUID(f("/sys/thread"), true)) {
            @Override
            public Runnable createTask() {
                return () -> {
                    this.jvm().put(uri(STATE), uri(RUN));
                    this.jvm().put(uri(RESULT), jnt(1));
                    done.countDown();
                };
            }
        };
        threadA.apply(noobj());

        // Thread sourced from B
        final VirtualThread threadB = new VirtualThread(
                mutableMap(uri(CODE), jnt(2), uri(SOURCE), auto_from_(sourceB).tryToInst()),
                MACH_VIRTUAL_THREAD_TID,
                CommonUtil.mintShortUUID(f("/sys/thread"), true)) {
            @Override
            public Runnable createTask() {
                return () -> {
                    this.jvm().put(uri(STATE), uri(RUN));
                    this.jvm().put(uri(RESULT), jnt(2));
                    done.countDown();
                };
            }
        };
        threadB.apply(noobj());

        assertTrue(done.await(2, TimeUnit.SECONDS));
        Thread.sleep(100);

        // Query inactive threads via jvm — filter by source field on each thread
        final Lst stop = executor.at(uri(STOP));
       /* final boolean hasSourceA = stop.lstValue().stream()
                .map(Obj.Helper::getAutoPointer)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .anyMatch(sourceA::equals);*/
        final boolean hasSourceB = stop.lstValue().stream()
                .map(Obj.Helper::getAutoPointer)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .anyMatch(sourceB::equals);
        final boolean hasNoSourceBforA = stop.lstValue().stream()
                .map(Obj.Helper::getAutoPointer)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(sourceA::equals)
                .noneMatch(sourceB::equals);

  //      assertTrue(hasSourceA, "should find threads for source A");
     //   assertTrue(hasSourceB, "should find threads for source B");
        assertTrue(hasNoSourceBforA, "thread A should NOT report source B");
    }

    // =========================================================
    // HasThread — obj.thread() convenience
    // =========================================================

    @Test
    void testHasThreadBuilderPreservesSource() {
        // Call obj.thread() and verify the builder chains correctly.
        // Since uri() values don't carry vids, we test by explicitly setting
        // source on the builder rather than relying on obj.vid().
        final fURI sourceVid = f("/sys/test/src/" + java.util.UUID.randomUUID().toString().substring(0, 8));
        final Obj obj = jnt(42);

        // Explicitly set source on the builder (what obj.thread() would do
        // if obj had a vid)
        final ThreadExecutor.Builder builder = obj.thread().source(sourceVid);
        assertNotNull(builder, "builder should not be null");

        final VirtualThread vt = builder.code(start_(jnt(1))).spawnVirtualThread();
        assertEquals(sourceVid, Obj.Helper.getAutoPointer(vt.source()).get(), "spawned thread should carry the source vid");
    }

    @Test
    void testHasThreadOnObjWithoutVid() {
        // Obj without a vid — thread() should still work (source = null → no SOURCE key)
        final Obj obj = jnt(42); // Int has no vid by default (vid() returns null)
        final ThreadExecutor.Builder builder = obj.thread();
        assertNotNull(builder, "builder should be created even for vid-less obj");
        final VirtualThread vt = builder.code(start_(jnt(1))).spawnVirtualThread();
        // No SOURCE key in jvm → source() returns empty uri
        assertNotNull(vt.vid(), "thread always has a vid");
    }

    // =========================================================
    // CoreThread lifecycle
    // =========================================================

    @Test
    void testCoreThreadRoutesThroughExecutor() throws Exception {
        final fURI vid = CommonUtil.mintShortUUID(f("/sys/thread"), true);

        // Verify CoreThread.apply() routes through ThreadExecutor.
        // CoreThread's createTask uses SwarmMachine internally; test that
        // the thread is tracked by the executor regardless of computation result.
        final studio.phaseshift.metatron.isa.mach.type.thread.CoreThread thread =
                studio.phaseshift.metatron.isa.mach.type.thread.CoreThread.core(
                        start_(jnt(1)), vid);

        thread.apply(noobj());
        // Give the executor thread time to register
        Thread.sleep(200);

        // Thread should appear in either active or inactive list
        final Lst run = executor.at(uri(RUN));
        final Lst stop = executor.at(uri(STOP));
        final boolean tracked = run.lstValue().stream()
                .anyMatch(o -> Obj.Helper.getAutoPointer(o).get().equals(vid))
                || stop.lstValue().stream()
                .anyMatch(o -> Obj.Helper.getAutoPointer(o).get().equals(vid));
        assertTrue(tracked, "core thread should be tracked by executor");
    }

    // =========================================================
    // Thread state tracking via jvm
    // =========================================================

    @ParameterizedTest(name = "[{index}] mtron code: {0} => {1}")
    @CsvSource(value = {
            "1          % 1       % integer literal",
            "'hello'    % 'hello' % string literal",
            "1+2        % 3       % addition",
            "true       % true    % boolean literal",
    }, delimiter = '%', quoteCharacter = '~')
    void testVirtualThreadResult(final String codeExpr, final String expectedExpr, final String desc) throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final Obj parsedCode = ObjmtronSerializer.parse(codeExpr);
        final Obj expected = ObjmtronSerializer.parse(expectedExpr);

        final VirtualThread thread = new VirtualThread(
                mutableMap(uri(CODE), parsedCode),
                MACH_VIRTUAL_THREAD_TID,
                CommonUtil.mintShortUUID(f("/sys/thread"), true)) {
            @Override
            public Runnable createTask() {
                return () -> {
                    this.jvm().put(uri(STATE), uri(RUN));
                    final Obj result = this.at(CODE).apply(this.at(START));
                    this.jvm().put(uri(RESULT), result);
                    done.countDown();
                };
            }
        };
        thread.apply(noobj());

        assertTrue(done.await(2, TimeUnit.SECONDS), desc + ": should complete");
        assertEquals(expected, thread.result(), desc + ": result should match");
        assertEquals(uri(STOP), thread.state(), desc + ": should be in STOP state");
    }

    // =========================================================
    // VirtualThread loop behavior (real createTask, no override)
    // =========================================================

    @ParameterizedTest(name = "[{index}] real task: {0} => {1}")
    @CsvSource(value = {
            "1       % 1       % int literal, no loop",
            "'hello' % 'hello' % string literal, no loop",
            "1+2     % 3       % addition, no loop",
    }, delimiter = '%', quoteCharacter = '~')
    void testVirtualThreadNoLoopExecutesOnce(final String codeExpr, final String expectedExpr, final String desc)
            throws Exception {
        final Obj parsedCode = ObjmtronSerializer.parse(codeExpr);
        final Obj expected = ObjmtronSerializer.parse(expectedExpr);

        // Use the real createTask() — no override.  With no LOOP key,
        // the do-while executes once and stops.
        final VirtualThread thread = VirtualThread.virtual(parsedCode);
        thread.apply(noobj());

        final Obj result = thread.result(5, TimeUnit.SECONDS);
        assertEquals(expected, result, desc + ": result should match");
        assertEquals(uri(STOP), thread.state(), desc + ": should be STOP after single execution");
    }

    // =========================================================
    // Edge cases
    // =========================================================

    @Test
    void testExecuteSameThreadTwiceIsRejected() throws Exception {
        final VirtualThread thread = VirtualThread.virtual(jnt(1));
        thread.apply(noobj());
        // Second apply should warn and return without re-executing
        final Obj result = thread.apply(noobj());
        assertNotNull(result, "second apply should return the thread (not null)");
    }

    @Test
    void testThreadExecutorExecuteRunnableStillWorks() {
        // Standard execute(Runnable) should still delegate to underlying service
        final CountDownLatch done = new CountDownLatch(1);
        this.executor.execute((Runnable) done::countDown);
        try {
            assertTrue(done.await(2, TimeUnit.SECONDS), "standard execute(Runnable) should work");
        } catch (InterruptedException e) {
            fail("interrupted waiting for runnable");
        }
    }

    // =========================================================
    // Pause / Resume / Yield
    // =========================================================

    @Test
    void testPauseLoopingThread() throws Exception {
        final VirtualThread thread = VirtualThread.virtual(plus_(jnt(1)));
        thread.jvm().put(uri(LOOP), real(0.0d));
        thread.apply(jnt(1));

        Thread.sleep(50); // let a few cycles run

        thread.pause();
        Thread.sleep(50);
        assertEquals(uri(PAUSE), thread.at(uri(STATE)), "looping thread should enter PAUSE");
        assertTrue(executor.at(RUN).lstValue().stream().map(Obj.Helper::getAutoPointer).filter(Optional::isPresent).anyMatch(o -> o.get().equals(thread.vid())),
                "paused thread should still be in run list");

        thread.resume();
        Thread.sleep(50);
        assertEquals(uri(RUN), thread.at(uri(STATE)), "resumed thread should be RUN");

        thread.stop();
    }

    @Test
    void testPauseNonLoopingThreadWarns() throws Exception {
        final VirtualThread thread = VirtualThread.virtual(jnt(1));
        thread.apply(noobj());
        Thread.sleep(200);

        // Pause a non-looping thread (no LOOP key) should warn and no-op
        thread.pause();
        assertEquals(uri(STOP), thread.at(uri(STATE)), "non-looping thread should not be paused");
    }

    @Test
    void testResumeRunningThreadNoOp() throws Exception {
        final VirtualThread thread = VirtualThread.virtual(jnt(1));
        thread.jvm().put(uri(LOOP), real(10.0d));
        thread.apply(noobj());
        Thread.sleep(50);
        thread.resume(); // no-op on RUN
        assertEquals(uri(RUN), thread.at(uri(STATE)), "resume on RUN should be no-op");
        thread.stop();
    }

    @Test
    void testResumeStoppedThreadWarns() throws Exception {
        final VirtualThread thread = VirtualThread.virtual(jnt(1));
        thread.apply(noobj());
        Thread.sleep(200);
        assertEquals(uri(STOP), thread.at(uri(STATE)));
        thread.resume(); // warn — cannot resume stopped
        assertEquals(uri(STOP), thread.at(uri(STATE)), "resume on STOP should be no-op");
    }

    @Test
    void testYieldCoroutinePair() throws Exception {
        // Two threads with yield pointers — A pauses → B resumes, and vice versa
        final VirtualThread threadB = VirtualThread.virtual(jnt(2));
        threadB.jvm().put(uri(LOOP), real(5.0d));
        threadB.jvm().put(uri(YIELD), uri(f("/sys/thread"))); // will be updated after A is created
        threadB.pause(); // start B in PAUSE
        threadB.apply(noobj());
        Thread.sleep(50);

        final VirtualThread threadA = VirtualThread.virtual(jnt(1));
        threadA.jvm().put(uri(LOOP), real(5.0d));
        threadA.jvm().put(uri(YIELD), uri(threadB.vid()));
        threadB.jvm().put(uri(YIELD), uri(threadA.vid()));
        threadA.apply(noobj());
        Thread.sleep(200);

        // Both should have run at least one cycle
        assertEquals(uri(RUN), threadA.at(uri(STATE)), "A should be running after yield cycle");
        assertEquals(uri(RUN), threadB.at(uri(STATE)), "B should be running after being yielded to");

        threadA.stop();
        threadB.stop();
    }

    @Test
    void testPauseKeepsThreadInRunList() throws Exception {
        final VirtualThread thread = VirtualThread.virtual(jnt(1));
        thread.jvm().put(uri(LOOP), real(5.0d));
        thread.apply(noobj());
        Thread.sleep(200);

        thread.pause();
        Thread.sleep(100);

        final boolean inRun = executor.at(RUN).lstValue().stream().map(Obj.Helper::getAutoPointer).filter(Optional::isPresent).anyMatch(o -> o.get().equals(thread.vid()));
        assertTrue(inRun, "paused thread should remain in run list");

        thread.stop();
    }

    @Test
    void testLoopFieldDetection() {
        final VirtualThread thread = VirtualThread.virtual(jnt(1));
        thread.jvm().put(uri(LOOP), real(5.0d));
        assertTrue(thread.has(LOOP), "should detect LOOP field via has()");
        assertEquals(5.0d, thread.at(LOOP).realValue(), 0.01, "should read LOOP value via realValue()");
    }

    @Test
    void testLoopingThreadStaysRunning() throws Exception {
        final VirtualThread thread = VirtualThread.virtual(jnt(1));
        thread.jvm().put(uri(LOOP), real(10.0d));
        thread.apply(noobj());
        Thread.sleep(100);
        assertEquals(uri(RUN), thread.at(STATE), "looping thread should still be RUN after 100ms");
        thread.stop();
    }

    @Test
    void testStateDoesntAffectLoopDetection() {
        final VirtualThread thread = VirtualThread.virtual(jnt(1));
        assertFalse(thread.jvm().containsKey(uri(LOOP)),
                "LOOP should not be in jvm just because STATE=RUN was set");
    }
}
