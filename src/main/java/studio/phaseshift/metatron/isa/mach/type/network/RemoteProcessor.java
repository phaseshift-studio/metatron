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

package studio.phaseshift.metatron.isa.mach.type.network;

import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Processor;
import studio.phaseshift.metatron.isa.mach.type.processor.AbstractProcessor;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static studio.phaseshift.metatron.Tokens.TRANSPORT;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_REMOTE_PROCESSOR_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The processor of a frame that executes elsewhere: applying code here does not RUN it — it ships the code to the
 * peer and answers with what the peer computed.
 * <p>
 * This is where "execute there" belongs, and why nothing else had to change. The console already evaluates by
 * asking the machine for its processor and handing it the compiled code
 * ({@code machine.processor().code(compiled).applyAsync()}), so a frame whose processor ships needs no console case,
 * no rewrite rule, and no compile-time wrapper — and the code is handed over already compiled and resolved, as a
 * value, which is what keeps the resolver out of it. (Wrapping the code as an INST ARG was tried and is wrong:
 * resolving an inst resolves its args, so a nested code as an argument recursed through
 * {@code Inst.resolve → resolveCode → Compiler.resolve → resolveArgs → Inst.resolve} until the stack died.)
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RemoteProcessor extends AbstractProcessor {
    private Consumer<Obj> onHalt;

    private RemoteProcessor(final Map<Obj, Obj> jvm) {
        super(jvm, MACH_REMOTE_PROCESSOR_TID, null);
    }

    /**
     * a shipping processor carrying the reach it ships through — the frame's transport, in the rec
     */
    public static RemoteProcessor of(final Obj transport) {
        return new RemoteProcessor(mutableMap(uri(TRANSPORT), transport));
    }

    /**
     * Shipping is for COMPUTATIONS. A frame instruction is not one: {@code move}, {@code push} and {@code pop}
     * choose where the NEXT code runs, so they execute in the frame doing the choosing — here — and never on the
     * peer, where the address they name means nothing. Shipping {@code move} asked the peer to move, and the round
     * trip that answered it had no halt to wait on.
     * <p>
     * The test is the vocabulary's own, not a list of names: those instructions ANSWER A MACHINE ({@code rng} is
     * {@code machine::T}), whereas everything shippable answers a value. So "answers a machine ⇒ run it where the
     * frame is being chosen" needs no maintenance as instructions are added.
     */
    private static boolean frameInstruction(final Code code) {
        final List<Inst> insts = code.insts();
        if (insts.isEmpty())
            return false;
        // BY NAME, not by Type.test: test() runs the type's PREDICATE (is this obj a machine rec?), which is a
        // different question from "is this rng the machine type" — the latter is an identity, and the type's name is
        // that identity. (`rng=machine` in a compiled form is this vid's short name.)
        final Type rng = insts.getFirst().rng();
        return null != rng.vid() && MACH_MACHINE_TID.name().equals(rng.vid().name());
    }

    /**
     * Run the code where it was submitted — on the enclosing frame's processor — rather than shipping it. Note it
     * must NOT go back through {@link Code#apply}, which would route to {@code Machine.current().applyCode} and land
     * on this processor again.
     */
    private Obj applyLocally(final Code code, final Obj input) {
        final Obj parent = Machine.current().parent();
        final Processor local = parent instanceof Machine machine ? machine.processor() : Machine.root().processor();
        return local.code(code).apply(input);
    }

    /**
     * The one certain site: past here a COMPUTATION is handed to the peer, unconditionally — which is what makes the
     * logged line evidence rather than a guess.
     */
    @Override
    public Obj apply(final Obj input) {
        final Code code = this.code();
        final Obj result;
        if (frameInstruction(code)) {
            result = this.applyLocally(code, input);
        } else {
            final Obj transport = this.at(uri(TRANSPORT));
            if (!transport.isObjInst())
                return fail("remote processor %s carries no transport to ship on", this.vid());
            this.logger().info("evaluating %s at %s", code, transport);
            try {
                // the lhs is threaded in as start(...), the same shape a local processor would run
                result = transport.apply(Code.wrapStart(input, code));
            } catch (final Exception e) {
                return fail(e);
            }
        }
        // THE CONSOLE AWAITS THIS FUTURE, not the return value: applyAsync() submits the thread and
        // awaitForeground polls the future. A synchronous apply that publishes nothing is a job that never ends.
        this.future.setObj(result);
        return result;
    }

    @Override
    public Processor onHalt(Consumer<Obj> halted) {
        this.onHalt = halted;
        return this;
    }

    @Override
    public Consumer<Obj> onHalt() {
        return this.onHalt;
    }
}
