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

package studio.phaseshift.metatron.isa.llm.space;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.LLMFactory;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SPACE_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class modelCatalogSpace<CATALOG> extends memSpace {

    public static final fURI LLM_CATALOG_SPACE_TID = LLM_SPACE_TID.extend("catalog");
    public static final Type LLM_CATALOG_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(LLM_CATALOG_SPACE_TID)
            .isaPredicate(rec(
                    uri(NAME), is_(or_(eq_(uri(ANTHROPIC)), eq_(uri(OPENAI)), eq_(uri(OLLAMA)))),
                    uri(PATTERN), URI_TYPE,
                    uri(HOST).maybe(), URI_TYPE,
                    uri(ROUTE), rec(URI_TYPE, URI_TYPE)))
            .constructor(instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(LLM_CATALOG_SPACE_TID), lst(T(REC_TID)), (x, inst) -> LLMFactory.createModelCatalog(inst.arg(0).asRec()))).create();

    private final CATALOG models;

    public static <CATALOG> modelCatalogSpace<CATALOG> of(final CATALOG models, final Map<Obj, Obj> config, final fURI vid) {
        return new modelCatalogSpace<>(models, config, vid);
    }

    public modelCatalogSpace(final CATALOG models, final Map<Obj, Obj> config, final fURI vid) {
        super(config, LLM_SPACE_TID, vid);
        this.models = models;
        this.refreshModels();
    }

    protected void refreshModels() {
        /*
        final OllamaModels models = this.sjvm;
        models.availableModels().content().stream()
                .map(m -> Tuple.Pair.with(m, models.modelCard(m.getName()).content()))
                .map(m -> rec(
                        Map.of(uri(PROVIDER), auto_from_(this.vid.extend("ollama")).tryToInst(),
                                uri(HOST), this.at(Tokens.HOST),
                                uri(NAME), uri(m.get0().getName()),
                                uri(THINK), bool(m.get1().getCapabilities().contains(THINKING)),
                                uri(SKILL), lst(m.get1().getCapabilities().stream().map(MUri::uri)),
                                uri(SIZE), real(Long.valueOf(m.get0().getSize()).doubleValue(), MATH_BYTE_TID, null).as(GBYTE_TYPE)), OLLAMA_OLLM_TID, null)).forEach(m -> {
                    this.write(this.pattern.retract(1).extend(m.at(NAME).uriValue()), m);
                });
              
         */
    }
}
