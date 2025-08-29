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

import org.javatuples.Triplet;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.BObj.Inst;
import studio.phaseshift.metatron.lang.obj.BObj.Obj;
import studio.phaseshift.metatron.lang.obj.SObj;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class SInst {

    public static final fURI PLUS_URI = fURI.create("plus");
    public static final fURI START_URI = fURI.create("start");

    public static Map<fURI, Function<Obj, Inst>> SYMBOL_TABLE = new HashMap<>() {{
        put(START_URI, StartInst::new);
        put(PLUS_URI, PlusInst::new);
    }};

    public static class PlusInst extends SObj.Inst implements BInst.PlusInst {

        public PlusInst(final BObj.Obj arg) {
            super(new Triplet<>(SObj.Lst.single(arg), (lhs, args) -> {
                if (lhs.isInt() && args.value().get(0).isInt())
                    return SObj.Int.of(lhs.intValue() + args.value().get(0).intValue());
                else
                    throw new IllegalStateException("the operands do not support plus");

            }, BObj.NoObj.of()), PLUS_URI);
        }
    }

    public static class StartInst extends SObj.Inst implements BInst.StartInst {

        public StartInst(final BObj.Obj arg) {
            super(new Triplet<>(SObj.Lst.single(arg), (lhs, args) -> args.value().get(0), BObj.NoObj.of()), START_URI);
        }
    }
}
