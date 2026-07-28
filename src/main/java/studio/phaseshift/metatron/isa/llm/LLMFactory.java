/*
 * metatron: a distributed virtual machine and language
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
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.localai.LocalAiEmbeddingModel;
import dev.langchain4j.model.localai.LocalAiStreamingChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaModels;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiModelCatalog;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.slf4j.event.Level;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.Model;
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
import java.util.List;
import java.util.Optional;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_MODEL_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.GBYTE_TYPE;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_BYTE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
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

    public static Rec createModel(final Rec preModel) {
        return switch (preModel.at(PROTOCOL).uriValue().toString()) {
            case ANTHROPIC -> {
                final AnthropicModelCatalog models = AnthropicModelCatalog.builder().baseUrl(preModel.at(HOST).uriValue().toString()).build();
                yield models.listModels().stream()
                        .filter(m -> m.name().equals(preModel.at(LLM).uriValue().toString()))
                        .map(m -> {
                            final Rec postModel = rec(mutableMap(
                                            uri(NAME), uri(m.name()),
                                            uri(TYPE), m.type() == null ? noobj() : uri(m.type().name().toLowerCase()),
                                            uri(CREATOR), str(m.provider().name()),
                                            uri(DESC), m.description() == null || m.description().isBlank() ? noobj() : str(m.description())
                                            // uri(PROVIDER), auto_from_(spaceRec.vid()).tryToInst()),
                                    ),
                                    LLM_MODEL_TID, null);
                            postModel.jvm().putAll(preModel.jvm());
                            return postModel;
                        }).findFirst()
                        .orElseThrow(() -> MTronException.of("unknown model: %s", preModel.at(LLM)));
            }
            case OLLAMA -> {
                final OllamaModels models = OllamaModels.builder().baseUrl(preModel.at(HOST).uriValue().toString()).build();
                preModel.logger().debug("connected to ollama server at %s", preModel.at(HOST));
                yield models.availableModels().content().stream()
                        .map(m -> Tuple.Pair.with(m, models.modelCard(m.getName()).content()))
                        .peek(m -> preModel.logger().debug("checking ollama server model %s", m.get0().getName()))
                        .filter(m -> m.get0().getModel().equals(preModel.at(LLM).uriValue().toString()))
                        .peek(m -> preModel.logger().debug("located ollama server model %s", m.get0().getName()))
                        .map(m -> {
                            try {
                                final Rec postModel = rec(mutableMap(
                                                // uri(PROVIDER), auto_from_(spaceRec.vid()).tryToInst(),
                                                //uri(NAME), uri(m.get0().getName()),
                                                //uri(LICENSE), Optional.ofNullable(m.get1().getLicense()).map(MStr::str).map(o -> (Obj) o).orElse(noobj()),
                                                //uri(THINK), m.get1().getCapabilities().contains(THINKING) ? rec() : noobj(),
                                                uri(SKILL), lst(m.get1().getCapabilities().stream().map(MUri::uri)),
                                                uri(SIZE), real(Long.valueOf(m.get0().getSize()).doubleValue(), MATH_BYTE_TID, null).as(GBYTE_TYPE)),
                                        REC_TID, null);
                                postModel.jvm().putAll(preModel.jvm());
                                return postModel;
                            } catch (final Exception e) {
                                throw MTronException.of("unable to to construct model from %s", m);
                            }
                        })
                        .findFirst()
                        .orElseThrow(() -> {
                            preModel.logger().error("unable to locate model %s at %s", preModel.at(LLM).uriValue(), preModel.at(HOST).uriValue());
                            return MTronException.of("unknown model: %s", preModel.at(LLM));
                        });
            }
           /* case LOCALAI -> {
                final LocalAiModelCatalog models = new LocalAiModelCatalog(spaceRec.at(HOST).uriValue().toString());
                final modelCatalogSpace<LocalAiModelCatalog> catalogSpace = modelCatalogSpace.of(spaceRec.jvm(), spaceRec.vid());
                catalogSpace.at(QPROC, catalogSpace.at(QPROC).orElse(lst()).add(QCollection.subq(), MUTABLE), MUTABLE);
                models.listModels().forEach(m -> rec(mutableMap(
                                uri(NAME), uri(m.name()),
                                uri(DESC), Optional.ofNullable(m.description()).filter(d -> !d.isBlank()).map(MStr::str).map(o -> (Obj) o).orElse(noobj()),
                                uri(TYPE), Optional.ofNullable(m.type()).map(t -> uri(t.name().toLowerCase(Locale.ROOT))).map(o -> (Obj) o).orElse(noobj()),
                                uri(PROVIDER), auto_from_(spaceRec.vid()).tryToInst()),
                        MODEL_TID, catalogSpace.pattern().retractPattern().extend(m.name())));
                yield catalogSpace;
            }*/
            case OPENAI -> {
                final OpenAiModelCatalog models = OpenAiModelCatalog.builder()
                        .baseUrl(preModel.at(HOST).uriValue().toString()).apiKey(preModel.at(API_KEY).strValue()).build();
                yield models.listModels().stream()
                        .filter(m -> m.name().equals(preModel.at(LLM).uriValue().toString()))
                        .map(m -> {
                            final Rec postModel = rec(mutableMap(
                                            uri(NAME), uri(m.name()),
                                            uri(DESC), Optional.ofNullable(m.description()).filter(d -> !d.isBlank()).map(MStr::str).map(o -> (Obj) o).orElse(noobj()),
                                            uri(TYPE), Optional.ofNullable(m.type()).map(t -> uri(t.name().toLowerCase())).map(o -> (Obj) o).orElse(noobj())
                                    ),//uri(PROVIDER), auto_from_(spaceRec.vid()).tryToInst()),
                                    LLM_MODEL_TID, null);
                            postModel.jvm().putAll(preModel.jvm());
                            return postModel;
                        })
                        .findFirst()
                        .orElseThrow(() -> MTronException.of("unknown model: %s", preModel.at(LLM)));
            }
            default -> throw new IllegalArgumentException("unsupported llm protocol: " + preModel.at(PROTOCOL));
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
                                .rootElement(JsonSchemaGenerator.objToSchema(REC_TYPE, responseFormat, RESPONSE))
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

    public static StreamingChatModel createChatInteraction(final Agent agent, final Obj modelObj, final Obj responseObj, final Obj fmt) {
        final Rec model = modelObj.isNoObj() ? noobjRec() : modelObj.asRec();
        final Rec responseFormat = fmt.isNoObj() ? rec0().c(cInt::zero).as() : fmt.asRec();
        final fURI provider = model.at(f(PROTOCOL)).uriValue();
        final String host = model.at(HOST).uriValue().toString();
        final boolean thinking = agent.hasFeature(THINK);
        final String modelName = Str.Helper.cleanString(model.at(NAME));
        final Str api_key = model.at(API_KEY).orElse(str0());//model.at(f(PROVIDER)).asRec().at(API_KEY).orElse(str0());
        // final Str organization = model.at(f(PROVIDER)).asRec().at(ORG).orElse(str0());
        final String name = Str.Helper.cleanString(model.at(LLM));
        final Rec responseFormat2 = responseFormat.isNoObj() ? agent.feature(f(RESPONSE).extend(FORMAT).toString()).orElse(noobjRec()) : responseFormat;
        final boolean hasResponseFormat = !responseFormat2.isNoObj() && !responseFormat2.isEmpty();
        return switch (provider.toString().toLowerCase()) {
            case LOCALAI -> LocalAiStreamingChatModel.builder()
                    .baseUrl(host)
                    .modelName(name)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            case OLLAMA -> {
                OllamaStreamingChatModel.OllamaStreamingChatModelBuilder builder =
                        OllamaStreamingChatModel.builder()
                                .baseUrl(host)
                                .modelName(name)
                                .think(thinking)
                                .returnThinking(thinking)
                                .logRequests(true)
                                .logResponses(true)
                                // .listeners(model.cost().isPresent() ? List.of(new CostCalculator(model.cost().get())) : null)
                                .logger(Graphitty.log(OllamaStreamingChatModel.class).logger(Level.WARN));
                if (hasResponseFormat)
                    builder = builder.responseFormat(createResponseFormat(responseFormat2));
                if (!model.at(COST).isNoObj())
                    builder.listeners(List.of(new CostCalculator(model.at(COST).as())));
                yield builder.build();
            }
            case OPENAI -> {
                // don't pass empty organizationId - it causes hangs in some LangChain4j versions
                // final String orgId = organization.strValue().isBlank() ? null : organization.strValue();
                final String baseUrl = (host != null && !host.isBlank() && !host.equals("https://api.openai.com/v1")) ? host : null;
                // Fail early if a response format was requested but the model can't honor it
                if (hasResponseFormat && host != null && host.contains("api.openai.com") && !openAiSupportsJsonObject(modelName))
                    throw MTronException.of("response format not supported by %s — use gpt-4-turbo, gpt-4o, or newer", modelName);
                // Pick the best response_format the model actually supports:
                //   gpt-4o+ / o-series  → json_schema (Structured Outputs)
                //   gpt-4-turbo / gpt-3.5-turbo → json_object
                final ResponseFormat openAiFormat = openAiSupportsStructuredOutputs(modelName) ?
                        createResponseFormat(responseFormat) :
                        createJsonObjectResponseFormat(responseFormat);
                final OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                        .apiKey(api_key.strValue())
                        .baseUrl(baseUrl)
                        .modelName(modelName)
                        .returnThinking(thinking)
                        .sendThinking(thinking, "reasoning_content")
                        //.organizationId(orgId)
                        .logRequests(true)
                        .logResponses(true)
                        .logger(Graphitty.log(OpenAiStreamingChatModel.class).logger(Level.WARN))
                        .timeout(Duration.ofSeconds(60))
                        .responseFormat(openAiFormat);
                if (!model.at(COST).isNoObj())
                    builder.listeners(List.of(new CostCalculator(model.at(COST).as())));
                yield builder.build();
            }
            case ANTHROPIC -> {
                final AnthropicStreamingChatModel.AnthropicStreamingChatModelBuilder builder =
                        AnthropicStreamingChatModel.builder()
                                .apiKey(api_key.strValue())
                                .modelName(modelName)
                                .returnThinking(thinking)
                                .logRequests(true)
                                .logResponses(true)
                                // .listeners(model.cost().isPresent() ? List.of(new CostCalculator(model.cost().get())) : null)
                                .logger(Graphitty.log(AnthropicStreamingChatModel.class).logger(Level.WARN))
                                .responseFormat(createResponseFormat(responseFormat));
                if (!model.at(COST).isNoObj())
                    builder.listeners(List.of(new CostCalculator(model.at(COST).as())));
                yield builder.build();
            }

            default -> throw MTronException.of("llm provider does not support chatting: %s", provider);
        };
    }

    public static EmbeddingModel createEmbeddingInteraction(final Model model) {
        final String modelName = Str.Helper.cleanString(model.llm());
        return switch (model.protocol().uriValue().toString().toLowerCase()) {
            case LOCALAI -> LocalAiEmbeddingModel.builder()
                    .baseUrl(model.host().uriValue().toString())
                    .modelName(modelName)
                    .logRequests(true)
                    .logResponses(true)
                    .logger(Graphitty.log(LocalAiEmbeddingModel.class).logger(Level.WARN))
                    .build();
            case OLLAMA -> OllamaEmbeddingModel.builder()
                    .baseUrl(model.host().uriValue().toString())
                    .modelName(modelName)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            case OPENAI -> OpenAiEmbeddingModel.builder()
                    .apiKey(model.apiKey().strValue())
                    .baseUrl(model.host().uriValue().toString())
                    .modelName(modelName)
                    .logRequests(true)
                    .logResponses(true)
                    .logger(Graphitty.log(OpenAiEmbeddingModel.class).logger(Level.WARN))
                    .build();
            default ->
                    throw MTronException.of("llm provider does not support embedding: %s", model.provider().uriValue());
        };

    }
}
