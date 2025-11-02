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

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mllm.mollamaSpace.MOLLAMA_TID;
import static studio.phaseshift.metatron.lang.mllm.type.impl.OLLM.OLLM_TID;
import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.STR_TID;
import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.URI_TID;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mllmInstSet extends MInstSet {

    public static final fURI MLLM_TID = f("/mllm");
    public static final fURI INST_TID = MLLM_TID.extend("inst");

    public static final fURI TOOL_TID = MLLM_TID.extend("tool");
    public static final fURI MEMORY_TID = MLLM_TID.extend("memory");

    public mllmInstSet(final fURI vid) {
        super(MLLM_TID, vid);
    }

    public static mllmInstSet create() {
        return new mllmInstSet(fURI.NULL);
    }

    @Override
    public Set<Type> types() {
        return Set.of(T(OLLM_TID), T(TOOL_TID), T(MEMORY_TID));
    }

    @Override
    public Set<Inst> insts() {
        return new LinkedHashSet<>(List.of(
                instC(INST_TID.extend("mollama").dom(ALL.maybe()).rng(MOLLAMA_TID),
                        rec(uri("host"), T(URI_TID), uri("pattern"), T(URI_TID)),
                        (lhs, inst) -> mollamaSpace.of(inst.arg("host").uriValue(), inst.arg("pattern").uriValue())),
                instC(INST_TID.extend("chat").dom(OLLM_TID).rng(STR_TID.maybeSome()), lst(T(STR_TID)),
                        (lhs, inst) -> str(OllamaChatModel.builder()
                                .baseUrl(lhs.<Rec>as().at("host").uriValue().toString())
                                .modelName(lhs.<Rec>as().at("name").uriValue().toString())
                                .build()
                                .chat(inst.arg(0).strValue())))));
    }
}
