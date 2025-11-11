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

package studio.phaseshift.metatron.lang.ai.llm.type.impl;

import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModelCard;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.ai.llm.type.LLM;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;
import studio.phaseshift.metatron.util.Tuple;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.lang.ai.llm.llmInstSet.MLLM_TID;
import static studio.phaseshift.metatron.lang.Space.HOST;
import static studio.phaseshift.metatron.lang.Space.NAME;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class OLLM extends MRec implements LLM {

    public static final fURI OLLM_TID = MLLM_TID.extend("ollm");

    public OLLM(final Tuple.Triplet<OllamaModel, OllamaModelCard, fURI> model, final fURI tid, final fURI vid) {
        super(modelToRec(model), tid, vid);
    }

    private static Map<Obj, Obj> modelToRec(final Tuple.Triplet<OllamaModel, OllamaModelCard, fURI> model) {
        return new LinkedHashMap<>() {{
            put(uri(NAME), uri(model.get0().getName()));
            put(uri(HOST), uri(model.get2()));
            put(uri("size"), jnt(model.get0().getSize()));
            put(uri("quant"), uri(model.get0().getDetails().getQuantizationLevel()));
            put(uri("family"), uri(model.get0().getDetails().getFormat()));
         //   put(uri("card"), rec(model.get1().getModelInfo(), MObjFactory.of()));
        }};

    }

    public static OLLM ollm(final Tuple.Triplet<OllamaModel, OllamaModelCard, fURI> model, final fURI tid, final fURI vid) {
        return new OLLM(model, tid, vid);
    }

    public String name() {
        return this.at(NAME).strValue();
    }

    public OLLM clone() {
        return (OLLM) super.clone();
    }

    public OLLM clone(final Object model, fURI tid, final fURI vid) {
        return (OLLM) super.clone(model, tid, vid);
    }
}
