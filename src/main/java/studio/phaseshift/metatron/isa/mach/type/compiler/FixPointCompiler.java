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

package studio.phaseshift.metatron.isa.mach.type.compiler;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.Tokens.LOOP;
import static studio.phaseshift.metatron.Tokens.REWRITE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_FIXPOINT_COMPILER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * FixPointCompiler — the concrete {@code fixpoint_compiler::T}: the default schedule
 * ({@code rewrite → resolve → bind → type}) over the machine's rewrite rules. It carries no logic
 * of its own — the stages and the schedule live on {@code Compiler}; the {@code loop} convergence
 * window is its {@code fixpoint_compiler::T} refinement.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FixPointCompiler extends AbstractCompiler {

    public FixPointCompiler() {
        this(mutableMap(), MACH_FIXPOINT_COMPILER_TID, null);
    }

    public FixPointCompiler(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Code rewrite(final Code code) {
        final AtomicReference<Code> rewritten = new AtomicReference<>(code);
        int hash = code.hashCode();
        int done = this.at(LOOP).orElse(jnt(2)).intValue().intValue();
        while (done != 0) {
            this.machine().instset().at(REWRITE).elements()
                    .forEach(r -> rewritten.set(this.applyRewrite(r, rewritten.get())));
            if (hash == (hash = rewritten.get().hashCode()))
                done--;
        }
        return rewritten.get();
    }
}
