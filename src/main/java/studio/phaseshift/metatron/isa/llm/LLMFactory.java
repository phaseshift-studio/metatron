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

package studio.phaseshift.metatron.isa.llm;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaModels;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiModelCatalog;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.llm.space.modelCatalogSpace;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.m.type.impl.MUri;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.MODEL_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.GBYTE_TYPE;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_BYTE_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public final class LLMFactory {

    private LLMFactory() {
        // do nothing
    }

    public static Space createModelCatalog(final Rec spaceRec) {
        return switch (spaceRec.at(PROVIDER).uriValue().toString()) {
            case "ollama" -> {
                final OllamaModels models = OllamaModels.builder().baseUrl(spaceRec.at(HOST).uriValue().toString()).build();
                final modelCatalogSpace<?> catalogSpace = modelCatalogSpace.of(models, spaceRec.jvm(), spaceRec.vid());
                models.availableModels().content().stream()
                        .map(m -> Tuple.Pair.with(m, models.modelCard(m.getName()).content()))
                        .forEach(m -> rec(
                                Map.of(uri(PROVIDER), auto_from_(spaceRec.vid()).tryToInst(),
                                        uri(HOST), auto_from_(spaceRec.vid().extend(HOST)).tryToInst(),
                                        uri(NAME), uri(m.get0().getName()),
                                        uri(THINK), bool(m.get1().getCapabilities().contains(THINKING)),
                                        uri(SKILL), lst(m.get1().getCapabilities().stream().map(MUri::uri)),
                                        uri(SIZE), real(Long.valueOf(m.get0().getSize()).doubleValue(), MATH_BYTE_TID, null).as(GBYTE_TYPE)),
                                MODEL_TID, catalogSpace.pattern().retractPattern().extend(m.get0().getName())));
                yield catalogSpace;
            }
            case "openai" -> {
                final OpenAiModelCatalog models = OpenAiModelCatalog.builder().apiKey(spaceRec.at(API_KEY).strValue()).build();
                final modelCatalogSpace<?> catalogSpace = modelCatalogSpace.of(models, spaceRec.jvm(), spaceRec.vid());
                models.listModels().forEach(m -> rec(Map.of(
                                uri(NAME), uri(m.name()),
                                uri(CREATOR), str(m.provider().name()),
                                uri(DESC), str(m.description()),
                                uri(PROVIDER), auto_from_(spaceRec.vid()).tryToInst()),
                        MODEL_TID, catalogSpace.pattern().retractPattern().extend(m.name())));
                yield catalogSpace;
            }
            default -> throw new IllegalArgumentException("unsupported LLM provider: " + spaceRec.at(PROVIDER));
        };
    }

    public static StreamingChatModel createModel(final Rec llm, String modelName) {
        final fURI provider = llm.at(f(PROVIDER)).asRec().at(PROVIDER).uriValue();
        final String host = llm.at(f(PROVIDER)).asRec().at(HOST).uriValue().toString();
        //   final boolean toolUse = !llm.at(TOOL).isNoObj();
        final boolean thinking = llm.at(THINK).orElse(bool(false)).boolValue();
        // final Uri memory = llm.at(MEMORY).orElse(null);
        final Str api_key = llm.at(API_KEY).orElse(null);
        final String model = llm.at(NAME).uriValue().toString();
        return switch (provider.toString().toLowerCase()) {
            case "ollama" -> OllamaStreamingChatModel.builder()
                    .baseUrl(host)
                    .modelName(model)
                    .think(thinking)
                    .returnThinking(thinking)
                    .build();

            case "openai" -> OpenAiStreamingChatModel.builder()
                    .apiKey(api_key.strValue())
                    .modelName(modelName)
                    .returnThinking(thinking)
                    .build();

            default -> throw MTronException.of("unsupported LLM provider: %s", provider);
        };
    }
}
