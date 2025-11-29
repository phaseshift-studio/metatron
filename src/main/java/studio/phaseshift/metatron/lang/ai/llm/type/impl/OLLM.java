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

import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModelCard;
import dev.langchain4j.model.ollama.OllamaModels;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.ai.llm.type.LLM;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;
import studio.phaseshift.metatron.lang.core.m.type.impl.MUri;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.lang.ai.llm.llmInstSet.INST_TID;
import static studio.phaseshift.metatron.lang.ai.llm.llmInstSet.LLM_TID;
import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.*;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.*;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class OLLM extends MRec implements LLM {

    public static final fURI OLLM_TID = LLM_TID.extend("ollm");
    public static final String SKILL = "skill";
    public static final String THINK = "think";
    public static final String TOOL = "tool";
    public static final String THINKING = "thinking";

    public static Type OLLM_TYPE = T(OLLM_TID, isa_(rec(uri(NAME),
            T(URI_TID),
            uri(SKILL),
            T(LST_TID),
            uri(THINK).c(cInt::maybe),
            T(BOOL_TID))), instC(INST_TID.dom(ALL_STAR).rng(OLLM_TID), lst(),
            (lhs, inst) -> {
                final String modelName = inst.arg(0).<Rec>as().at(uri(NAME)).uriValue().toString();
                final OllamaModels models = OllamaModels.builder().baseUrl(inst.arg(0).<Rec>as().at(HOST).uriValue().toString()).build();
                final OllamaModelCard card = models.modelCard(modelName).content();
                final OllamaModel model = models.availableModels().content().stream().filter(m -> m.getName().equals(modelName)).findFirst().orElse(null);
                if (null == model)
                    throw MTronException.of("unknown card");
                return new OLLM(Tuple.Pair.with(model, card), OLLM_TID, inst.arg(0).vid());
            }));

    public OLLM(final Tuple.Pair<OllamaModel, OllamaModelCard> model, final fURI tid, final fURI vid) {
        super(modelToRec(model), tid, vid);
        this.put(uri(HOST), Router.global().getSpace(this.vid).at(HOST));
    }

    private static Map<Obj, Obj> modelToRec(final Tuple.Pair<OllamaModel, OllamaModelCard> model) {
        return new LinkedHashMap<>() {{
            put(uri(NAME), uri(model.get0().getName()));
            put(uri(THINK), bool(model.get1().getCapabilities().contains(THINKING)));
            put(uri(SKILL), lst(model.get1().getCapabilities().stream().map(MUri::uri)));
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
