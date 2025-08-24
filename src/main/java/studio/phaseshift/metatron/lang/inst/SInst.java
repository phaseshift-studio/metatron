/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.inst;

import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.SObj;

public class SInst {
    public static class PlusInst extends SObj.Inst implements BInst.PlusInst {

        PlusInst(final BObj.Obj arg) {
            super(SObj.Uri.of("plus"), SObj.Lst.single(arg), (lhs, args) -> {
                if (lhs.isInt() && args.value().get(0).isInt())
                    return SObj.Int.of(lhs.intValue() + args.value().get(0).intValue());
                else
                    throw new IllegalStateException("the operands do not support plus");

            }, BObj.NoObj.of());
        }
    }
}
