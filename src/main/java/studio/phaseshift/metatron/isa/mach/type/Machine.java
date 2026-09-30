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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.router.BasicRouter;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.InstSet.instset0;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.SYS;

/**
 * Machine — the container that binds an ISA to its lowering and execution axes. A Machine IS-A
 * {@code Router}: it holds spaces, so a machine is a memory hierarchy — its own address space plus
 * the nested spaces of its instset, compiler and processor. The three members are held as rec
 * entries ({@code instset}, {@code compiler}, {@code processor}), which is exactly the shape of the
 * {@code machine::T} structural type.
 * <p>
 * Execution is <em>not</em> on this axis: {@link Processor} (a thread) runs code and
 * {@link Compiler} (a rec) lowers it — they are siblings of {@code Machine}, not refinements. A
 * machine <em>contains</em> them.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Machine extends Router {

    static Machine mach0() {
        return Helper.Machine0.single();
    }

    /**
     * The default machine — the {@code /sys/mach} constant bootstrapped by {@code machInstSet.setup()}.
     * Falls back to {@link #mach0()} before the constant is loaded.
     */
    static Machine defaultMachine() {
        final Obj machine = Router.readFromSpace(SYS.extend(MACH));
        return machine.isNoObj() ? mach0() : machine.as();
    }

    interface Component extends Rec {
        default Machine machine() {
            Obj mach = this.parent();
            while (!mach.isNoObj() && !(mach instanceof Machine))
                mach = mach.parent();
            return mach instanceof Machine ? mach.as() : mach0();
        }
    }

    @Override
    default Obj apply(final Obj call) {
        return this.apply(call.asCode(), noobj());
    }

    /**
     * The machine's single source of truth for code execution: compile once (rewrite → resolve →
     * type) then run the compiled code against {@code start} on this machine's processor. The
     * processor does not re-resolve — the compiled code short-circuits via its
     * {@code isResolved(true)} gate, and runtime (element-type-dependent) resolution is memoized by
     * the compiler's resolver.
     */
    default Obj apply(final Code code, final Obj start) {
        return this.processor().code(this.compiler().apply(code).asCode()).apply(start);
    }

    /**
     * @return the machine's instruction set (ISA), or {@code null} when none is bound
     */
    default InstSet instset() {
        return this.at(uri(INSTSET)).orElse(instset0());
    }

    /**
     * @return this machine with the given instruction set bound
     */
    default Machine instset(final InstSet instset) {
        CommonUtil.close(this.atDirect(uri(INSTSET)));
        return this.at(uri(INSTSET), instset, MUTABLE).as();
    }

    default Machine instset(final Call instsetReference) {
        CommonUtil.close(this.atDirect(uri(INSTSET)));
        return this.at(uri(INSTSET), instsetReference, MUTABLE).as();
    }

    /**
     * @return the machine's compiler, or {@code null} when none is bound
     */
    default Compiler compiler() {
        final Obj protoCompiler = this.atDirect(COMPILER).orThrow(MTronException.of("machine has no compiler: %s", this.type().vid()));
        if (protoCompiler.isCall())
            return protoCompiler.apply().as();
        return protoCompiler.as();
    }

    /**
     * @return this machine with the given compiler bound
     */
    default Machine compiler(final Compiler compiler) {
        return this.at(uri(COMPILER), compiler, MUTABLE).as();
    }

    default Machine compiler(final Call templateCompiler) {
        return this.at(uri(COMPILER), templateCompiler, MUTABLE).as();

    }


    /**
     * @return the machine's processor, or {@code null} when none is bound
     */
    default Processor processor() {
        final Obj protoProcessor = this.atDirect(PROCESSOR).orThrow(MTronException.of("machine has no processor: %s", this.type().vid()));
        if (protoProcessor.isCall())
            return protoProcessor.apply().as();
        return protoProcessor.clone().as();
    }

    /**
     * @return this machine with the given processor bound
     */
    default Machine processor(final Processor processor) {
        return this.at(uri(PROCESSOR), processor, MUTABLE).as();
    }

    default Machine processor(final Call templateProcessor) {
        return this.at(uri(PROCESSOR), templateProcessor, MUTABLE).as();
    }

    class Helper {

        public static final class Machine0 extends BasicRouter implements Machine {
            private static final Machine0 INSTANCE = new Machine0();

            public static Machine0 single() {
                return INSTANCE;
            }

            private Machine0() {
                super(null);
            }

            public fURI tid() {
                return MACH_MACHINE_TID.zero();
            }

            public fURI vid() {
                return null;
            }

        }

    }
}
