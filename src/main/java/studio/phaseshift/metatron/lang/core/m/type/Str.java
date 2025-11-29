/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.lang.core.m.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.impl.MStr;

import java.util.*;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.q.DocQ.Doc.docWrap;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;

public interface Str extends Mono {

    @Override
    Str clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    String jvm();

    default Str jvm(final String jvm) {
        return this.clone(jvm, this.tid(), this.vid());
    }

    default Str tid(final fURI tid) {
        return this.clone(this.jvm(), tid, this.vid());
    }

    default Str vid(final fURI vid) {
        return this.clone(this.jvm(), this.tid(), vid);
    }

    public static class StrType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    docWrap(instC(SPLIT_INST_TID.dom(STR_TID).rng(STR_TID.some()), lst(T(STR_TID)), (lhs, inst) -> objs(Arrays.stream(lhs.strValue().split(inst.arg(0).strValue())).map(MStr::str))),
                            "a str to split", "the components of the split lhs str", Map.of(jnt(0), "a token to split on"), "split the lhs string according to the token arg and emit a stream of splits"),
                    docWrap(instC(MERGE_INST_TID.dom(STR_TID.maybeSome()).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> str(lhs.stream().map(Obj::<String>jvmAs).reduce((a, b) -> a + inst.arg(0).strValue() + b).orElse(""))),
                            "an str barrier", "the join of the str barrier", Map.of(jnt(0), "the join token"), "join the barrier given the str arg"),
                    instC(GT_INST_TID.dom(STR_TID).rng(BOOL_TID), lst(T(STR_TID)), (lhs, inst) -> bool(lhs.intValue().compareTo(inst.arg(0).intValue()) > 0)),
                    instC(GTE_INST_TID.dom(STR_TID).rng(BOOL_TID), lst(T(STR_TID)), (lhs, inst) -> bool(lhs.intValue().compareTo(inst.arg(0).intValue()) >= 0)),
                    instC(LT_INST_TID.dom(STR_TID).rng(BOOL_TID), lst(T(STR_TID)), (lhs, inst) -> bool(lhs.intValue().compareTo(inst.arg(0).intValue()) < 0)),
                    instC(LTE_INST_TID.dom(STR_TID).rng(BOOL_TID), lst(T(STR_TID)), (lhs, inst) -> bool(lhs.intValue().compareTo(inst.arg(0).intValue()) <= 0)),
                    instC(PLUS_INST_TID.dom(STR_TID).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> lhs.jvm(lhs.strValue() + inst.arg(0).strValue())),
                    instC(STR_UPPER_TID.dom(ALL).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> lhs.jvm(lhs.strValue().toUpperCase())),
                    instC(STR_LOWER_TID.dom(ALL).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> lhs.jvm(lhs.strValue().toLowerCase()))
            ));
        }
    }


}