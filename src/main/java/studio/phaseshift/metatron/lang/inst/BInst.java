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
import studio.phaseshift.metatron.lang.obj.BObj.Obj;
import studio.phaseshift.metatron.lang.obj.SObj;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.lang.obj.BObj.Inst;
import static studio.phaseshift.metatron.lang.obj.BObj.InstF;

public interface BInst {
    class SymbolTable {
        private static final Map<fURI, InstF> TABLE = new HashMap<>();

        public static void load(final fURI type, InstF instF) {
            TABLE.put(type, instF);
        }

        public static Inst resolve(final Obj source, final Inst inst) {
            if (null != inst.function())
                return inst;
            System.out.println(inst.type() + "+++" + TABLE.keySet());
            final InstF resolvedFunction = TABLE.get(inst.type());
            if (null == resolvedFunction)
                throw new IllegalArgumentException("unable to resolve %s".formatted(inst));
            final List<Obj> resolvedArgs = new ArrayList<>();
            // System.out.println(source + "=>" + inst.args());
            for (Obj arg : inst.args()) {
                resolvedArgs.add(arg.apply(source));
            }
            return inst.clone(new Triplet<>(new SObj.Lst(resolvedArgs),
                    resolvedFunction,
                    inst.value().getValue2()));
        }

    }

    public interface Contract {
        boolean isInitial();

        boolean isTerminal();

        boolean isGather();

        boolean isScatter();

        InstF function();

        Obj seed();
    }

    public interface Initial extends Inst {
    }

    public interface Terminal extends Inst {
    }

    public interface Barrier extends Inst {
    }


    public interface Gather extends Barrier {
    }

    public interface Scatter extends Barrier {
    }


    /// ///////////////////////////////////////////

}