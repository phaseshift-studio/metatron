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

package studio.phaseshift.metatron.isa.mach;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.machine.SwarmMachine;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Verifies that SwarmMachine's monadic state (running, barriers, halted)
 * lives in the jvm map, survives pause/resume, and is cloneable.
 */
public class SwarmMachineTest extends AbstractMetatronTest {

    // ======================== Sync execution ========================

    @Test
    public void testSyncApplyReturnsExpectedResult() {
        final Code code = ObjmtronSerializer.parse("1.plus(2)").as();
        final SwarmMachine mach = SwarmMachine.of(code);

        final Obj result = mach.apply(noobj());
        System.out.println("result: " + result);

        assertFalse(result.isNoObj(), "expected non-noobj result from 1+2");
        assertTrue(result.toString().contains("3"), "expected result to contain 3");
    }

    // ======================== Monadic queues in jvm ========================

    @Test
    public void testMachineStateIsInJvm() {
        final Code code = ObjmtronSerializer.parse("1.plus(2)").as();
        final SwarmMachine mach = SwarmMachine.of(code);

        // Before execution: defaults exist
        final Map<Obj, Obj> jvmBefore = mach.jvm();
        assertTrue(jvmBefore.containsKey(uri(CODE)), "jvm should contain /code");
        assertTrue(jvmBefore.containsKey(uri(RUN)), "jvm should contain /run (default)");
        assertTrue(jvmBefore.containsKey(uri(BARRIER)), "jvm should contain /barrier (default)");
        assertTrue(jvmBefore.containsKey(uri(HALTED)), "jvm should contain /halted (default)");

        // Execute synchronously
        mach.apply(noobj());

        // After execution: halted should have results
        final Map<Obj, Obj> jvmAfter = mach.jvm();
        final Obj halted = jvmAfter.get(uri(HALTED));
        assertNotNull(halted, "halted should exist after execution");
        assertFalse(halted.isNoObj(), "halted should not be noobj after execution");
    }

    // ======================== Clone preserves state ========================

    @Test
    public void testClonePreservesMachineState() {
        final Code code = ObjmtronSerializer.parse("1.plus(2)").as();
        final SwarmMachine mach = SwarmMachine.of(code);
        mach.apply(noobj());

        // Clone with the same jvm
        final SwarmMachine clone = mach.clone(mach.jvm(), mach.tid(), mach.vid());

        // Clone should have same halted result
        assertFalse(clone.halted().isNoObj(), "clone should preserve halted");
        assertEquals(mach.halted().toString(), clone.halted().toString(), "clone halted should match original");

        // Clone should have its own running queue (not shared reference for new clones)
        assertNotNull(clone.running(), "clone should have a running queue");

        // Clone should be independently executable with new code
        final Code newCode = ObjmtronSerializer.parse("3.plus(4)").as();
        final SwarmMachine reclone = clone.code(newCode);
        final Obj result = reclone.apply(noobj());
        assertFalse(result.isNoObj(), "reclone with new code should execute");
    }

    // ======================== Pause / resume lifecycle ========================

    @Test
    public void testPauseAndResumePreservesState() {
        final Code code = ObjmtronSerializer.parse("1.plus(2)").as();
        final SwarmMachine mach = SwarmMachine.of(code);

        // Pause before execution sets STATE=PAUSE in jvm
        mach.pause();
        assertEquals(uri(PAUSE), mach.jvm().get(uri(STATE)), "state should be PAUSE after pause()");

        // Resume sets STATE=RUN
        mach.resume();
        assertEquals(uri(RUN), mach.jvm().get(uri(STATE)), "state should be RUN after resume()");

        // Execute synchronously — should complete regardless of prior pause/resume
        final Obj result = mach.apply(noobj());
        assertFalse(result.isNoObj(), "should produce result after pause/resume cycle");
    }

    @Test
    public void testPauseMidExecutionPreservesQueues() throws Exception {
        final Code code = ObjmtronSerializer.parse("{1,2,3,4,5,6,7,8,9}.repeat(code=>+1,until=>?>200).count()").as();
        final SwarmMachine mach = SwarmMachine.of(code);

        record Snap(String phase, long running, long barriers, long halted, String state, String result) {}
        final var snaps = new java.util.ArrayList<Snap>();

        // --- before ---
        snaps.add(new Snap("before",
                mach.running().elements().count(),
                mach.barriers().elements().count(),
                mach.halted().elements().count(), "-", "-"));

        // --- start async, then pause ---
        mach.applyAsync();
        Thread.sleep(50);
        mach.pause();

        // --- paused ---
        final String statePaused = mach.jvm().get(uri(STATE)).toString();
        snaps.add(new Snap("paused",
                mach.running().elements().count(),
                mach.barriers().elements().count(),
                mach.halted().elements().count(), statePaused, "-"));
        assertTrue(statePaused.contains("pause"), "state should be PAUSE");

        // --- resume ---
        mach.resume();
        final Obj result = mach.result();
        final String stateDone = mach.jvm().get(uri(STATE)).toString();

        // --- done ---
        snaps.add(new Snap("done",
                mach.running().elements().count(),
                mach.barriers().elements().count(),
                mach.halted().elements().count(), stateDone, result.toString()));

        // --- after ---
        snaps.add(new Snap("after",
                mach.running().elements().count(),
                mach.barriers().elements().count(),
                mach.halted().elements().count(), stateDone, "-"));

        // --- display ---
        final String fmt = "%-8s %-8s %-9s %-8s %-6s %s%n";
        System.out.printf("%n" + fmt, "phase", "running", "barriers", "halted", "state", "result");
        System.out.println("-------- -------- --------- -------- ------ ------");
        snaps.forEach(s -> System.out.printf(fmt, s.phase, s.running, s.barriers, s.halted, s.state, s.result));

        // --- verify ---
        assertTrue(snaps.get(2).halted > 0, "should have halted output after completion");
        assertFalse(result.isNoObj(), "should produce non-noobj result");
    }

    @Test
    public void testStopSetsStateToStop() {
        final Code code = ObjmtronSerializer.parse("1.plus(2)").as();
        final SwarmMachine mach = SwarmMachine.of(code);

        mach.stop();
        assertEquals(uri(STOP), mach.jvm().get(uri(STATE)), "state should be STOP after stop()");
    }

    @Test
    public void testPausedMachineCanBeCloned() {
        final Code code = ObjmtronSerializer.parse("1.plus(2)").as();
        final SwarmMachine mach = SwarmMachine.of(code);

        // Execute to populate halted, then pause
        mach.apply(noobj());
        mach.jvm().put(uri(STATE), uri(PAUSE)); // simulate paused state

        // Count what's in the queues
        final long haltedCount = mach.halted().stream().count();
        System.out.printf("paused clone: halted=%d%n", haltedCount);

        // Clone the paused machine
        final SwarmMachine clone = mach.clone(mach.jvm(), mach.tid(), mach.vid());
        assertEquals(uri(PAUSE), clone.jvm().get(uri(STATE)),
                "clone should preserve PAUSE state");

        // Verify queues survived the clone — counts preserved
        assertEquals(haltedCount, clone.halted().stream().count(),
                "clone should preserve halted count");
    }

    // ======================== Machine IS-A Thread ========================

    @Test
    public void testMachineHasThreadLifecycle() {
        final Code code = ObjmtronSerializer.parse("1.plus(2)").as();
        final SwarmMachine mach = SwarmMachine.of(code);

        // Machine has thread lifecycle operations
        assertNotNull(mach.state(), "machine should have thread state");
        assertNotNull(mach.future(), "machine should have a future");
        assertNotNull(mach.source(), "machine should have a source");
    }

    @Test
    public void testAsyncExecutionProducesResult() throws Exception {
        final Code code = ObjmtronSerializer.parse("1.plus(2)").as();
        final SwarmMachine mach = SwarmMachine.of(code);

        // Async execution
        mach.applyAsync(noobj());

        // Block for result
        final Obj result = mach.result();
        assertFalse(result.isNoObj(), "async execution should produce non-noobj result");
    }
}
