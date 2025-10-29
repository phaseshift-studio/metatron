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

package studio.phaseshift.metatron.lang.mllm;

import dev.langchain4j.model.ollama.OllamaChatModel;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.Inst;
import studio.phaseshift.metatron.lang.mtron.type.Rec;
import studio.phaseshift.metatron.lang.mtron.type.Type;
import studio.phaseshift.metatron.lang.mtron.type.impl.MInstSet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mllm.type.impl.OLLM.OLLM_TID;
import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.STR_TID;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mllmInstSet extends MInstSet {

    public static final fURI MLLM_TID = f("/mllm");
    public static final fURI MLLM_LANG_TID = MLLM_TID.extend("lang");

    public mllmInstSet(final fURI vid) {
        super(MLLM_TID, vid);
    }

    public static mllmInstSet of(final fURI vid) {
        return new mllmInstSet(vid);
    }

    @Override
    public Set<Type> types() {
        return Stream.of(T(OLLM_TID)).collect(Collectors.toSet());
    }

    @Override
    public Set<Inst> insts() {
        return new LinkedHashSet<>(List.of(instC(f("/mllm/inst/chat").dom(OLLM_TID).rng(STR_TID.maybeSome()), lst(T(STR_TID)),
                (lhs, inst) -> str(OllamaChatModel.builder()
                        .baseUrl(lhs.<Rec>as().at("host").uriValue().toString())
                        .modelName(lhs.<Rec>as().at("name").uriValue().toString())
                        .build()
                        .chat(inst.arg(0).strValue())))));
    }
}
