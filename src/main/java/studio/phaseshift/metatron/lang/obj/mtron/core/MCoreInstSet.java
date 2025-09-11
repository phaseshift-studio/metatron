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

package studio.phaseshift.metatron.lang.obj.mtron.core;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.base.Inst;
import studio.phaseshift.metatron.lang.obj.base.InstSet;
import studio.phaseshift.metatron.lang.obj.base.Int;
import studio.phaseshift.metatron.lang.obj.mtron.MInst;
import studio.phaseshift.metatron.lang.obj.mtron.MInt;
import studio.phaseshift.metatron.lang.obj.mtron.MLst;
import studio.phaseshift.metatron.lang.obj.mtron.MObj;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class MCoreInstSet extends MObj implements InstSet {

    public static final fURI TID = fURI.of("/mtron/core");
    public static final fURI ID_TID = fURI.of("id");
    public static final fURI START_TID = fURI.of("start");
    public static final fURI MULT_TID = fURI.of("mult");
    public static final fURI PLUS_TID = fURI.of("plus");

    private static final Map<fURI, Map<fURI, Set<Inst>>> SYMBOL_TABLE = new LinkedHashMap<>() {{
        put(ID_TID, Map.of(fURI.of("#"), Set.of(MInst.instC(ID_TID, MLst.of(), (lhs, inst) -> lhs))));
        put(START_TID,
                Map.of(
                        fURI.of("#"),
                        Set.of(MInst.instC(START_TID, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> inst.arg(0)))));
        put(PLUS_TID,
                Map.of(
                        fURI.of("#"),
                        Set.of(MInst.instC(PLUS_TID, MLst.of(MInst.instA(ID_TID)),
                                (lhs, inst) -> MInt.of(lhs.<Int>as().value() + inst.arg(0).<Int>as().value())))));
    }};


    public MCoreInstSet() {
        super(SYMBOL_TABLE, TID, fURI.NONE);
    }

    @Override
    public InstSet clone(final Object value, final fURI tid, final fURI vid) {
        return this;
    }

    @Override
    public Map<fURI, Map<fURI, Set<Inst>>> value() {
        return (Map<fURI, Map<fURI, Set<Inst>>>) this.value;
    }
}
