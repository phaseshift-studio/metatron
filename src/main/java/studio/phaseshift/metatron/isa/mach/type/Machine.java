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
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.router.BasicRouter;
import studio.phaseshift.metatron.util.CommonUtil;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.InstSet.instset0;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;

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

    interface Component extends Rec {
        default Machine machine() {
            final Obj mach = this.at(MACHINE);
            return mach instanceof Machine ? mach.as() : mach0();
        }
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

    /**
     * @return the machine's compiler, or {@code null} when none is bound
     */
    default Compiler compiler() {
        return this.at(COMPILER).orElse(null);
    }

    /**
     * @return this machine with the given compiler bound
     */
    default Machine compiler(final Compiler compiler) {
        return this.at(uri(COMPILER), compiler, MUTABLE).as();
    }

    /**
     * @return the machine's processor, or {@code null} when none is bound
     */
    default Processor processor() {
        return this.at(PROCESSOR).orElse(null);
    }

    /**
     * @return this machine with the given processor bound
     */
    default Machine processor(final Processor processor) {
        return this.at(uri(PROCESSOR), processor, MUTABLE).as();
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
