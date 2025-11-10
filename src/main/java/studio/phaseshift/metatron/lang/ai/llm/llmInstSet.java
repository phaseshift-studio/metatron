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

package studio.phaseshift.metatron.lang.ai.llm;

import dev.langchain4j.model.ollama.OllamaChatModel;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.ai.llm.type.impl.Audio;
import studio.phaseshift.metatron.lang.core.m.type.Inst;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MInstSet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.ai.llm.type.impl.Audio.AUDIO_TID;
import static studio.phaseshift.metatron.lang.ai.llm.type.impl.OLLM.OLLM_TID;
import static studio.phaseshift.metatron.lang.core.m.mtronInstSet.REC_TID;
import static studio.phaseshift.metatron.lang.core.m.mtronInstSet.STR_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class llmInstSet extends MInstSet {

    public static final fURI MLLM_TID = f("/llm");
    public static final fURI INST_TID = MLLM_TID.extend("inst");
    public static final fURI SPACE_TID = MLLM_TID.extend("space");
    public static final fURI OLLAMA_TID = SPACE_TID.extend("ollama");
    public static final fURI TOOL_TID = MLLM_TID.extend("tool");
    public static final fURI MEMORY_TID = MLLM_TID.extend("memory");

    public llmInstSet(final fURI vid) {
        super(MLLM_TID, vid);
    }

    public static llmInstSet create() {
        return new llmInstSet(fURI.NULL);
    }

    @Override
    public Set<Type> types() {
        return Set.of(
                T(OLLM_TID),
                T(TOOL_TID),
                T(MEMORY_TID),
                ollamaSpace.OLLAMA_TYPE,
                Audio.AUDIO_TYPE);
    }

    @Override
    public Set<Inst> insts() {
        return new LinkedHashSet<>(List.of(
                instC(INST_TID.extend("play").dom(REC_TID).rng(AUDIO_TID), lst(), (lhs, inst) -> {
                    new Audio(lhs.jvm(),AUDIO_TID,lhs.vid()).play();
                    return lhs;
                }),
                instC(INST_TID.extend("chat").dom(OLLM_TID).rng(STR_TID.maybeSome()), lst(T(STR_TID)),
                        (lhs, inst) -> str(OllamaChatModel.builder()
                                .baseUrl(lhs.<Rec>as().at(HOST).uriValue().toString())
                                .modelName(lhs.<Rec>as().at(NAME).uriValue().toString())
                                .build()
                                .chat(inst.arg(0).strValue())))));
    }
}
