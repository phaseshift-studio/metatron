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

import dev.langchain4j.model.anthropic.AnthropicModelCatalog;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.localai.LocalAiStreamingChatModel;
import dev.langchain4j.model.ollama.OllamaModels;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiModelCatalog;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.slf4j.event.Level;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.llm.space.LocalAiModelCatalog;
import studio.phaseshift.metatron.isa.llm.space.modelCatalogSpace;
import studio.phaseshift.metatron.isa.llm.type.mModel;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.m.type.impl.MStr;
import studio.phaseshift.metatron.isa.m.type.impl.MUri;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.MODEL_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.GBYTE_TYPE;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_BYTE_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.Rec.REC_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.str0;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public final class LLMFactory {

    private LLMFactory() {
        // do nothing
    }

    public static Space createModelCatalog(final Rec spaceRec) {
        return switch (spaceRec.at(NAME).uriValue().toString()) {
            case ANTHROPIC -> {
                final AnthropicModelCatalog models = AnthropicModelCatalog.builder().baseUrl(spaceRec.at(HOST).uriValue().toString()).build();
                final modelCatalogSpace<AnthropicModelCatalog> catalogSpace = modelCatalogSpace.of(spaceRec.jvm(), spaceRec.vid());
                catalogSpace.at(QSTRING, catalogSpace.at(QSTRING).orElse(lst()).add(QCollection.subq(), MUTABLE), MUTABLE);
                models.listModels().forEach(m -> rec(mutableMap(
                                uri(NAME), uri(m.name()),
                                uri(TYPE), m.type() == null ? noobj() : uri(m.type().name().toLowerCase(Locale.ROOT)),
                                uri(CREATOR), str(m.provider().name()),
                                uri(DESC), m.description() == null || m.description().isBlank() ? noobj() : str(m.description()),
                                uri(PROVIDER), auto_from_(spaceRec.vid()).tryToInst()),
                        MODEL_TID, catalogSpace.pattern().retractPattern().extend(m.name())));
                yield catalogSpace;
            }
            case OLLAMA -> {
                final OllamaModels models = OllamaModels.builder().baseUrl(spaceRec.at(HOST).uriValue().toString()).build();
                final modelCatalogSpace<OllamaModels> catalogSpace = modelCatalogSpace.of(spaceRec.jvm(), spaceRec.vid());
                catalogSpace.at(QSTRING, catalogSpace.at(QSTRING).orElse(lst()).add(QCollection.subq(), MUTABLE), MUTABLE);
                models.availableModels().content().stream()
                        .map(m -> Tuple.Pair.with(m, models.modelCard(m.getName()).content()))
                        .forEach(m -> {
                            final fURI vid = catalogSpace.pattern().retractPattern().extend(m.get0().getName());
                            rec(mutableMap(uri(PROVIDER), auto_from_(spaceRec.vid()).tryToInst(),
                                            uri(NAME), uri(m.get0().getName()),
                                            //uri(LICENSE), Optional.ofNullable(m.get1().getLicense()).map(MStr::str).map(o -> (Obj) o).orElse(noobj()),
                                            uri(THINK), m.get1().getCapabilities().contains(THINKING) ? rec0() : noobj(),
                                            uri(SKILL), lst(m.get1().getCapabilities().stream().map(MUri::uri)),
                                            uri(SIZE), real(Long.valueOf(m.get0().getSize()).doubleValue(), MATH_BYTE_TID, null).as(GBYTE_TYPE)),
                                    MODEL_TID, vid);
                        });
                yield catalogSpace;
            }
            case LOCALAI -> {
                final LocalAiModelCatalog models = new LocalAiModelCatalog(spaceRec.at(HOST).uriValue().toString());
                final modelCatalogSpace<LocalAiModelCatalog> catalogSpace = modelCatalogSpace.of(spaceRec.jvm(), spaceRec.vid());
                catalogSpace.at(QSTRING, catalogSpace.at(QSTRING).orElse(lst()).add(QCollection.subq(), MUTABLE), MUTABLE);
                models.listModels().forEach(m -> rec(mutableMap(
                                uri(NAME), uri(m.name()),
                                uri(DESC), Optional.ofNullable(m.description()).filter(d -> !d.isBlank()).map(MStr::str).map(o -> (Obj) o).orElse(noobj()),
                                uri(TYPE), Optional.ofNullable(m.type()).map(t -> uri(t.name().toLowerCase(Locale.ROOT))).map(o -> (Obj) o).orElse(noobj()),
                                uri(PROVIDER), auto_from_(spaceRec.vid()).tryToInst()),
                        MODEL_TID, catalogSpace.pattern().retractPattern().extend(m.name())));
                yield catalogSpace;
            }
            case OPENAI -> {
                final OpenAiModelCatalog models = OpenAiModelCatalog.builder().baseUrl(spaceRec.at(HOST).uriValue().toString()).apiKey(spaceRec.at(API_KEY).strValue()).build();
                final modelCatalogSpace<OpenAiModelCatalog> catalogSpace = modelCatalogSpace.of(spaceRec.jvm(), spaceRec.vid());
                models.listModels().forEach(m -> rec(mutableMap(
                                uri(NAME), uri(m.name()),
                                uri(DESC), Optional.ofNullable(m.description()).filter(d -> !d.isBlank()).map(MStr::str).map(o -> (Obj) o).orElse(noobj()),
                                uri(TYPE), Optional.ofNullable(m.type()).map(t -> uri(t.name().toLowerCase(Locale.ROOT))).map(o -> (Obj) o).orElse(noobj()),
                                uri(PROVIDER), auto_from_(spaceRec.vid()).tryToInst()),
                        MODEL_TID, catalogSpace.pattern().retractPattern().extend(m.name())));
                yield catalogSpace;
            }
            default -> throw new IllegalArgumentException("unsupported llm provider: " + spaceRec.at(PROVIDER));
        };
    }

    /**
     * OpenAI Structured Outputs (json_schema) is only supported on gpt-4o and newer models.
     */
    private static boolean openAiSupportsStructuredOutputs(final String modelName) {
        return modelName.contains("gpt-4o") ||
                modelName.startsWith("o1") ||
                modelName.startsWith("o3") ||
                modelName.startsWith("o4") ||
                modelName.startsWith("gpt-4.1") ||
                modelName.startsWith("gpt-4.5");
    }

    /**
     * json_object response format is supported by gpt-4-turbo, gpt-3.5-turbo-1106+, and anything
     * that supports Structured Outputs. Base gpt-4 (0314, 0613) supports neither.
     */
    private static boolean openAiSupportsJsonObject(final String modelName) {
        return modelName.contains("turbo") ||
                modelName.contains("gpt-3.5") ||
                openAiSupportsStructuredOutputs(modelName);
    }

    private static ResponseFormat createResponseFormat(final Poly<?, ?> responseFormat) {
        return !responseFormat.isNoObj() && !responseFormat.isEmpty() ?
                new ResponseFormat.Builder()
                        .jsonSchema(new JsonSchema.Builder()
                                .name(RESPONSE)
                                .rootElement(mModel.Helper.objToSchema(REC_TYPE, responseFormat, RESPONSE))
                                .build())
                        .type(ResponseFormatType.JSON).build() :
                null;
    }

    /**
     * For models that only support json_object (no schema enforcement).
     */
    private static ResponseFormat createJsonObjectResponseFormat(final Poly<?, ?> responseFormat) {
        return !responseFormat.isNoObj() && !responseFormat.isEmpty() ?
                ResponseFormat.builder().type(ResponseFormatType.JSON).build() :
                null;
    }

    public static StreamingChatModel createChatInteraction(final mModel model, String modelName, final Rec responseFormat) {
        final fURI provider = model.at(f(PROVIDER)).asRec().at(NAME).uriValue();
        final String host = model.at(f(PROVIDER)).asRec().at(HOST).uriValue().toString();
        final boolean thinking = model.has(THINK);
        final Str api_key = model.at(f(PROVIDER)).asRec().at(API_KEY).orElse(str0());
        final Str organization = model.at(f(PROVIDER)).asRec().at(ORG).orElse(str0());
        final String name = model.at(NAME).uriValue().toString();
        final Rec responseFormat2 = responseFormat.isNoObj() ? model.responseFormat().orElse(noobjRec()) : responseFormat;
        final boolean hasResponseFormat = !responseFormat2.isNoObj() && !responseFormat2.isEmpty();
        return switch (provider.toString().toLowerCase()) {
            case LOCALAI -> LocalAiStreamingChatModel.builder()
                    .baseUrl(host)
                    .modelName(name)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            case OLLAMA -> OllamaStreamingChatModel.builder()
                    .baseUrl(host)
                    .modelName(name)
                    .think(thinking)
                    .returnThinking(thinking)
                    .logRequests(true)
                    .logResponses(true)
                    .logger(Graphitty.log(OllamaStreamingChatModel.class).logger(Level.WARN))
                    .responseFormat(createResponseFormat(responseFormat2))
                    .build();
            case OPENAI -> {
                // Don't pass empty organizationId - it causes hangs in some LangChain4j versions
                final String orgId = organization.strValue().isBlank() ? null : organization.strValue();
                // Only use custom baseUrl if different from default
                final String baseUrl = (host != null && !host.isBlank() && !host.equals("https://api.openai.com/v1")) ? host : null;
                // Fail early if a response format was requested but the model can't honor it
                if (hasResponseFormat && !openAiSupportsJsonObject(modelName))
                    throw MTronException.of("response format not supported by %s — use gpt-4-turbo, gpt-4o, or newer", modelName);
                // Pick the best response_format the model actually supports:
                //   gpt-4o+ / o-series  → json_schema (Structured Outputs)
                //   gpt-4-turbo / gpt-3.5-turbo → json_object
                final ResponseFormat openAiFormat = openAiSupportsStructuredOutputs(modelName) ?
                        createResponseFormat(responseFormat) :
                        createJsonObjectResponseFormat(responseFormat);
                yield OpenAiStreamingChatModel.builder()
                        .apiKey(api_key.strValue())
                        .baseUrl(baseUrl)
                        .modelName(modelName)
                        .returnThinking(thinking)
                        .organizationId(orgId)
                        .logRequests(true)
                        .logResponses(true)
                        .timeout(Duration.ofSeconds(60))
                        //.logger(Graphitty.log(OpenAiStreamingChatModel.class).logger(Level.WARN))
                        .responseFormat(openAiFormat)
                        .build();
            }
            case ANTHROPIC -> AnthropicStreamingChatModel.builder()
                    .apiKey(api_key.strValue())
                    .modelName(modelName)
                    .returnThinking(thinking)
                    .logRequests(true)
                    .logResponses(true)
                    .logger(Graphitty.log(AnthropicStreamingChatModel.class).logger(Level.WARN))
                    .responseFormat(createResponseFormat(responseFormat))
                    .build();

            default -> throw MTronException.of("unsupported LLM provider: %s", provider);
        };
    }
}
