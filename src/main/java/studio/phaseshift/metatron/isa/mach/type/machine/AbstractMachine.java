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

package studio.phaseshift.metatron.isa.mach.type.machine;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.router.BasicRouter;

import java.util.Map;

/**
 * AbstractMachine — the thin wrapper around a {@code machine::T} router. It holds no fields of its
 * own; the machine's members (instset, compiler, processor) and its nested spaces live in the
 * router's jvm map, and space addressing is {@code Router}'s read/write/addSpace/removeSpace.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractMachine extends BasicRouter implements Machine {

    public AbstractMachine(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * A machine is a callable obj — {@code machine.apply(code)} is the single source of truth for
     * code execution (compile then run). {@link BasicRouter} stubs {@code apply(Obj)} to {@code null};
     * this re-pins it to the {@link Machine} contract.
     */
    @Override
    public Obj apply(final Obj call) {
        return Machine.super.apply(call);
    }

}
