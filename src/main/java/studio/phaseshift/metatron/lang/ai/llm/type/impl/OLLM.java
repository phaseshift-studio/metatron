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
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.util.Tuple;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.lang.ai.llm.llmInstSet.LLM_TID;
import static studio.phaseshift.metatron.lang.Space.HOST;
import static studio.phaseshift.metatron.lang.Space.NAME;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class OLLM extends MRec implements LLM {

    public static final fURI OLLM_TID = LLM_TID.extend("ollm");

    public OLLM(final Tuple.Pair<OllamaModel, OllamaModelCard> model, final fURI tid, final fURI vid) {
        super(modelToRec(model), tid, vid);
        this.put(uri(HOST), Router.global().getSpace(this.vid).at(HOST));
    }

    private static Map<Obj, Obj> modelToRec(final Tuple.Pair<OllamaModel, OllamaModelCard> model) {
        return new LinkedHashMap<>() {{
            put(uri(NAME), uri(model.get0().getName()));
        }};

    }

    public static OLLM ollm(final Tuple.Pair<OllamaModel, OllamaModelCard> model, final fURI tid, final fURI vid) {
        return new OLLM(model, tid, vid);
    }
    
    public String name() {
        return this.at(NAME).uriValue().toString();
    }

    public OLLM clone() {
        return this;
    }

    public OLLM clone(final Object model, fURI tid, final fURI vid) {
        return (OLLM) super.clone(model, tid, vid);
    }
}
