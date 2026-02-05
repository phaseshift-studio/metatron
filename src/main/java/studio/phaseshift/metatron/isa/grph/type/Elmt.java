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

package studio.phaseshift.metatron.isa.grph.type;

import studio.phaseshift.metatron.isa.m.type.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Elmt extends Rec {

    public static class ElmtType {

        public static final Type ELMT_TYPE = Type.Builder.build()
                .tid(REC_TID)
                .vid(ELMT_TID).create();

        private static BiFunction<Obj, Inst, Obj> PROPERTY_FUNCTION(final Uri key) {
            return (lhs, inst) -> lhs.asRec().at(key);
        }

        public static Set<Inst> insts() {

            return new LinkedHashSet<>(List.of(
                    instC(LABEL_INST_TID.dom(ELMT_TID).rng(URI_TID), lst(), (lhs, inst) -> lhs.asRec().at(LABEL).orElse(uri(lhs.tid()))),
                    instC(VALUES_INST_TID.dom(ELMT_TID).rng(ALL.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> lhs.asRec().at(inst.arg(0).isNoObj() ? uri("+") : inst.arg(0).asUri()))
                    // instC(VALUES_INST_TID.dom(ELMT_TID).rng(ALL.maybeSome()), lst(T(URI_TID.maybeSome())), PROPERTY_FUNCTION(uri("+")))
            ));
        }
    }
}
