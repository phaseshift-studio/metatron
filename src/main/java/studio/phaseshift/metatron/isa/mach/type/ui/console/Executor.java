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

package studio.phaseshift.metatron.isa.mach.type.ui.console;

import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.machine.SwarmMachine;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static studio.phaseshift.metatron.Tokens.DEBUG;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

import org.slf4j.event.Level;

/**
 * The console's execution engine: takes one prompt line, parses its
 * segments at {@code end()} boundaries, runs each through a
 * {@link SwarmMachine} (foreground, backgrounded on {@code alt}+b, or
 * through the console's input instruction), and streams the results back
 * through the console.
 * <p>
 * The decision table for what happens when the console's own keys arrive
 * while a job runs lives on {@link ForegroundJobs} (see
 * {@link Console#foregroundStep}), and the prompt loop that feeds lines
 * here and reads {@code :human} replies is the console's read-side (see
 * {@link HumanBroker}).
 */
public final class Executor {

    private final Console console;

    public Executor(final Console console) {
        this.console = console;
    }

    /**
     * Execute one prompt line: parse it, split it into independently
     * executable segments at {@code end()} boundaries, and run each —
     * chaining the result of one into the input of the next — until the
     * line is exhausted, a job detaches to the background, or an error
     * ends the turn.
     *
     * @param line the prompt line, already resolved to plain text
     */
    public void execute(final String line) {
        /// /////////////////////////////////////////////////////
        final AtomicReference<Obj> running = new AtomicReference<>(noobj());
        final String fullInput = this.console.prefix + line + this.console.postfix;
        if (fullInput.isBlank()) return;

        // 1. Parse the full input — the parser natively handles ; via end() sugar
        final Obj parsed = ObjmtronSerializer.parseMulti(fullInput);
        if (null == parsed || parsed.isNoObj()) return;

        // 2. Split into independently executable segments at end() boundaries
        final List<Code> segments;
        if (parsed.isCode()) {
            segments = ObjmtronSerializer.splitCodeAtEnd(parsed.asCode());
        } else {
            // Single expression (bare value or single instruction) — wrap as a one-instruction code
            segments = List.of(MCode.of(List.of(
                    parsed.isInst() ? parsed.as() : instB(START_INST_TID, lst(parsed)))));
        }
        if (segments.isEmpty()) return;

        // 3. Execute each segment with its own SwarmMachine, chaining running state
        boolean backgrounded = false;
        for (final Code segment : segments) {
            try {
                final Level startLevel = this.console.getStatus().getState();
                final Obj resolvedResult = Call.Helper.resolveInspection(running.get(), segment, unresolved -> {
                    if (TypeCheck.code_resolve.enabled()) {
                        throw MTronException.of("unable to fully resolve code. execution will require dynamic inst resolution for:\n\t%s", unresolved.stream().map(Obj::tid).toList());
                    } else {
                        this.console.getStatus().setState(Level.WARN);
                        segment.logger().status(DEBUG, "{{y}}dynamic resolution{{X}}: %s", unresolved.stream().map(i -> "{{b}}" + i.tid() + "{{y}}@" + i.vid() + "{{X}}").reduce("", (a, b) -> a + "," + b).substring(1));
                    }
                });
                final AtomicReference<Obj> computeResult = new AtomicReference<>(noobj());
                if (this.console.input.isNoObj()) {
                    final Machine mach = SwarmMachine.of(resolvedResult.as());
                    final Consumer<Obj> defaultOnHalt = mach.onHalt(); // accumulate into HALTED
                    mach.onHalt(o -> {
                        defaultOnHalt.accept(o);  // persist in HALTED collection
                        //this.printResult(o);      // display
                    });
                    // Track machine in both places for interruption
                    this.console.machine = mach;
                    if (this.console.getActivePane() != null) {
                        this.console.getActivePane().machine(mach);
                    }
                    final FutureObj<Obj> future = mach.applyAsync();
                    if (this.console.awaitForeground(mach, future, line)) {
                        // <alt>+b — the machine keeps its own thread and keeps
                        // running (widgets stay live); this turn is over.
                        backgrounded = true;
                    } else {
                        computeResult.set(future.get());
                    }
                } else {
                    computeResult.set(this.console.input.apply(resolvedResult));
                }
                running.set(computeResult.get());
                computeResult.get().stream().forEach(this.console::printResult);
                this.console.promptTraceForFails(computeResult.get());
                this.console.getStatus().setState(startLevel);
            } catch (final Exception e) {
                final Obj failResult = fail(e);
                this.console.printResult(failResult);
                this.console.promptTraceForFails(failResult);
            } finally {
                Console.userMode.set(false);
                if (this.console.getActivePane() != null)
                    this.console.getActivePane().clearMachine();

            }
            // a detached job took the rest of this input to the background
            if (backgrounded) break;
        }
    }
}
