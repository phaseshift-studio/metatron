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

package studio.phaseshift.metatron.lang.ai.llm.type.impl;

import io.github.ollama4j.models.response.Model;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.ai.llm.type.LLM;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.lang.ai.llm.llmInstSet.LLM_INSTSET_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class O4JLLM extends MRec implements LLM {

    public static final fURI O4JLLM_TID = LLM_INSTSET_TID.extend("o4jllm");

    public O4JLLM(final Model model, final fURI tid, final fURI vid) {
        super(modelToRec(model), O4JLLM_TID, vid);
    }

    private static Map<Obj, Obj> modelToRec(final Model model) {
        return new LinkedHashMap<>() {{
            put(uri(NAME), uri(model.getModelName()));
            put(uri("size"), jnt(model.getSize()));
            put(uri("quant"), uri(model.getModelMeta().getQuantizationLevel()));
            put(uri("family"), uri(model.getModelMeta().getFamily()));
            //   put(uri("card"), rec(model.get1().getModelInfo(), MObjFactory.of()));
        }};

    }

    public static O4JLLM ollm(final Model model, final fURI tid, final fURI vid) {
        return new O4JLLM(model, tid, vid);
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
