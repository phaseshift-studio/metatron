/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.isa.m.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;

import java.util.Set;
import java.util.function.Function;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.CODE_TID;
import static studio.phaseshift.metatron.isa.m.type.Code.CODE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

public interface InstSet extends Space {


    fURI A = f("A");
    fURI B = f("B");
    fURI C = f("C");
    fURI D = f("D");
    fURI E = f("E");
    fURI F = f("F");
    fURI G = f("G");

    @Override
    fURI pattern();

    Set<Obj> consts();

    Set<Type> types();

    Set<Inst> insts();

    Set<Inst> rewrites();

    public static class Helper {

        public static Inst rewriter(final fURI tid, Function<Code, Code> rewrite) {
            return instC(tid.dom(ALL.maybe()).rng(CODE_TID.maybe()), lst(CODE_TYPE), (lhs, inst) -> rewrite.apply(inst.arg(0).asCode()).as());
        }
    }
}
