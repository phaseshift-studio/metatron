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

package studio.phaseshift.metatron.lang.core.m.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.impl.MStr;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.q.DocQ.Doc.docWrap;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

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
        private final static Map<String, Pattern> REGEX_CACHE = new HashMap<>();

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(AS_INST_TID.dom(STR_TID).rng(A), lst(T(A)), (lhs, inst) -> {
                        if (inst.arg(0).matches(T(URI_TID)))
                            return uri(lhs.strValue());
                        else if (inst.arg(0).matches(T(INT_TID)))
                            return jnt(Long.parseLong(lhs.strValue()));
                        else if (inst.arg(0).matches(T(REAL_TID)))
                            return real(Double.parseDouble(lhs.strValue()));
                        else if (inst.arg(0).matches(T(STR_TID)))
                            return lhs;
                        else throw MTronException.of("no defined conversion for %s => %s", lhs.tid(), inst.arg(0).tid());
                    }),
                    docWrap(instC(SPLIT_INST_TID.dom(STR_TID).rng(STR_TID.some()), lst(T(STR_TID)), (lhs, inst) -> objs(Arrays.stream(lhs.strValue().split(inst.arg(0).strValue())).map(MStr::str))),
                            "a str to split", "the components of the split lhs str", Map.of(jnt(0), "a token to split on"), "split the lhs string according to the token arg and emit a stream of splits"),
                    docWrap(instC(SPLIT_INST_TID.dom(STR_TID).rng(LST_TID), lst(T(STR_TID)), (lhs, inst) -> lst((List) Arrays.stream(lhs.strValue().split(inst.arg(0).strValue())).map(MStr::str).toList())),
                            "a str to split", "lst encoded components of the split lhs str", Map.of(jnt(0), "a token to split on"), "split the lhs string according to the token arg and insert components into an ordered lst"),
                    docWrap(instC(MERGE_INST_TID.dom(STR_TID.maybeSome()).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> str(lhs.stream().map(Obj::<String>jvmAs).reduce((a, b) -> a + inst.arg(0).strValue() + b).orElse(""))),
                            "an str barrier", "the join of the str barrier", Map.of(jnt(0), "the join token"), "join the barrier given the str arg"),
                    instC(REGEX_INST_TID.dom(STR_TID).rng(LST_TID), lst(T(STR_TID)), (lhs, inst) ->
                            lst(REGEX_CACHE.compute(inst.arg(0).strValue(), (k, v) -> null == v ? Pattern.compile(k) : v).matcher(lhs.strValue()).results().map(MatchResult::group).map(MStr::str).map(Obj::<Obj>as).toList())),
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