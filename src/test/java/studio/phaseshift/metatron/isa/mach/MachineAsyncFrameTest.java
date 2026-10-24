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
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.m.type.impl.MInst;
import studio.phaseshift.metatron.isa.m.type.impl.MLst;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Processor;
import studio.phaseshift.metatron.isa.mach.type.thread.AbstractThread;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * THE CONSOLE'S ASYNC FRAME OF REFERENCE. {@link MachineIsolationScriptTest} drives push/pop through the script
 * harness, which evaluates {@code parse(...).apply()} synchronously on ONE thread — so {@link Machine#current()}
 * (a thread-local) sees every move. The console instead runs each prompt line through
 * {@code SwarmProcessor.applyAsync()}, i.e. on a fresh worker thread, so the frame of reference is lost at the
 * thread boundary unless the worker carries it both ways:
 * {@link studio.phaseshift.metatron.isa.sys.type.ThreadExecutor} seeds the worker with the caller's machine and
 * the worker publishes where it landed ({@link AbstractThread#landedPerspective()}), which the session then
 * adopts. These tests pin that boundary.
 * <p>
 * Each line is driven exactly the way {@code console.Executor.execute} does — parse, resolve, compile, run on a
 * worker, adopt the worker's landing — so a regression in the boundary shows up here as the wrong {@code ~}.
 */
public class MachineAsyncFrameTest extends AbstractMetatronTest {

    /**
     * Run one prompt line the way the console does and adopt where the worker landed, returning the result.
     */
    private Obj executeLine(final String line) throws Exception {
        final Obj parsed = ObjmtronSerializer.parse(line);
        final Code segment = parsed.isCode()
                ? parsed.asCode()
                : MCode.of(List.of(parsed.isInst() ? parsed.as() : MInst.instB(START_INST_TID, MLst.lst(parsed))));
        final Call resolved = Call.Helper.resolveInspection(noobj(), segment, unused -> {
        });
        final Machine machine = Machine.current();
        final Code compiled = machine.compiler().apply(resolved).asCode();
        final Processor mach = machine.processor().code(compiled);
        final Obj result = mach.applyAsync().get();
        // the worker records its landing BEFORE the future completes (AbstractThread.complete), so get() returning
        // guarantees landedPerspective() is already published — a direct read, no polling, no race window.
        if (mach instanceof AbstractThread worker) {
            final Machine landed = worker.landedPerspective();
            if (null != landed)
                Machine.current(landed);
        }
        return result;
    }

    @Test
    void testAsyncPushPushPopPopLandsBackAtTheRoot() throws Exception {
        executeLine("push(async-a)");
        assertEquals(f("/async-a"), Machine.current().vid(), "push(a) must land the session in /async-a");

        executeLine("push(async-b)");
        assertEquals(f("/async-a/async-b"), Machine.current().vid(),
                "push(b) must nest under the frame, not mint a root sibling");

        executeLine("pop()");
        assertEquals(f("/async-a"), Machine.current().vid(),
                "pop() must land the session on the discarded frame's parent");

        executeLine("pop()");
        assertEquals(f("/"), Machine.current().vid(), "pop() must land the session back at the root");
    }
}
