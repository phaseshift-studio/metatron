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

package studio.phaseshift.metatron.lang.ai.llm;

import dev.langchain4j.model.ollama.OllamaModels;
import io.github.ollama4j.Ollama;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.models.chat.OllamaChatStreamObserver;
import io.github.ollama4j.models.generate.OllamaGenerateTokenHandler;
import io.github.ollama4j.models.request.ThinkMode;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.ai.llm.type.impl.Audio;
import studio.phaseshift.metatron.lang.ai.llm.type.impl.GGUF;
import studio.phaseshift.metatron.lang.ai.llm.type.impl.OLLM;
import studio.phaseshift.metatron.lang.core.m.type.Inst;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MInstSet;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.furi.fURI.fnull;
import static studio.phaseshift.metatron.lang.ai.llm.type.impl.Audio.AUDIO_TID;
import static studio.phaseshift.metatron.lang.ai.llm.type.impl.OLLM.*;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class llmInstSet extends MInstSet {

    public static final fURI LLM_TID = f("/llm");
    public static final fURI INST_TID = LLM_TID.extend("inst");
    public static final fURI SPACE_TID = LLM_TID.extend("space");
    public static final fURI OLLAMA_TID = SPACE_TID.extend("ollama");
    public static final fURI TOOL_TID = LLM_TID.extend("tool");
    public static final fURI MEMORY_TID = LLM_TID.extend("memory");
    public static final String MODEL = "model";
    public static final fURI LOAD_INST_TID = OLLAMA_TID.extend("inst/load");

    public llmInstSet(final fURI vid) {
        super(LLM_TID, vid);
    }

    public static llmInstSet create() {
        return new llmInstSet(fnull);
    }

    @Override
    public Set<Type> types() {
        return Set.of(
                T(TOOL_TID),
                T(MEMORY_TID),
                GGUF.GGUF_TYPE,
                GGUF.TENSOR_REF_TYPE,
                OLLM.OLLM_TYPE,
                T(OLLAMA_TID, isa_(rec(uri(HOST), T(URI_TID), uri(MODEL).maybe(), isa_(lst(T(OLLM_TID))).else_(lst())))));
    }

    @Override
    public Set<Inst> insts() {
        final Set<Inst> set = new LinkedHashSet<>(List.of(
                instC(INST_TID.extend("play").dom(REC_TID).rng(AUDIO_TID), lst(), (lhs, inst) -> {
                    new Audio(lhs.jvm(), AUDIO_TID, lhs.vid()).play();
                    return lhs;
                }),
                instC(LOAD_INST_TID.dom(OLLAMA_TID).rng(OLLAMA_TID), lst(), (lhs, inst) -> {
                    try {

                        OllamaModels models = new OllamaModels.OllamaModelsBuilder().baseUrl(lhs.<Rec>as().at(HOST).uriValue().toString()).build();
                        if (inst.arg(0).isNoObj()) {
                            lhs.<Rec>as().put(uri(MODEL), lst((List) models.availableModels().content().stream().map(m -> ollm(lhs.<Rec>as().at(HOST).uriValue(), Tuple.Pair.with(m, models.modelCard(m.getName()).content()), OLLM_TID, fnull)).toList()), MUTABLE);
                        } else {
                            final List<fURI> names = inst.arg(0).stream().map(Obj::uriValue).toList();
                            lhs.<Rec>as().put(uri(MODEL), lst((List) models.availableModels().content().stream().filter(x -> names.contains(x.getName())).map(m -> ollm(lhs.<Rec>as().at(HOST).uriValue(), Tuple.Pair.with(m, models.modelCard(m.getName()).content()), OLLM_TID, fnull)).toList()), MUTABLE);
                        }

                    } catch (final Exception e) {
                        e.printStackTrace();
                    }
                    return lhs;
                }),
                instC(INST_TID.extend("chat").dom(OLLM_TID).rng(STR_TID.maybeSome()), lst(T(STR_TID)),
                        (lhs, inst) -> {
                          /*  final OllamaChatModel model = OllamaChatModel.builder()
                                    .baseUrl(lhs.<Rec>as().at(Tokens.HOST).uriValue().toString())
                                    .modelName(lhs.<Rec>as().at(Tokens.NAME).uriValue().toString())
                                    .think(lhs.<Rec>as().at(THINK).orElse(bool(false)).boolValue())
                                    .returnThinking(lhs.<Rec>as().at(THINK).orElse(bool(false)).boolValue())
                                    .build();*/
                            try {
                                final String host = lhs.<Rec>as().at(Tokens.HOST).uriValue().toString();
                                final boolean toolUse = lhs.<Rec>as().at(TOOL).orElse(bool(false)).boolValue();
                                final boolean thinking = lhs.<Rec>as().at(THINK).orElse(bool(false)).boolValue();
                                final String model = lhs.<Rec>as().at(Tokens.NAME).uriValue().toString();

                                final OllamaChatRequest chatRequest =
                                        OllamaChatRequest.builder()
                                                .withModel(model)
                                                .withThinking(thinking ? ThinkMode.ENABLED : ThinkMode.DISABLED)
                                                .withMessage(OllamaChatMessageRole.USER, inst.arg(0).strValue())
                                                .withUseTools(toolUse)
                                                .build();
                                final StringBuilder response = new StringBuilder();

                                final OllamaGenerateTokenHandler thinkingStreamHandler =
                                        (s) -> LOG.none("{{m}}%s{{X}}", s);

                                final OllamaGenerateTokenHandler responseStreamHandler =
                                        (s) -> {
                                            LOG.none("{{y}}%s{{X}}", s);
                                            response.append(s);
                                        };

                                if (thinking)
                                    LOG.none(Graphitty.sillyPrint("thinking...\n", true, true));
                                final OllamaChatResult result =
                                        new Ollama(host).chat(chatRequest, new OllamaChatStreamObserver(thinking ? thinkingStreamHandler : null, responseStreamHandler));
                                while (!result.getResponseModel().isDone()) {
                                    Thread.sleep(100);
                                }
                                if (thinking)
                                    LOG.none("\n");
                                final Rec last = rec(
                                        "request", inst.arg(0),
                                        "response", rec(
                                                "text", str(response.toString()),
                                                "count", jnt(result.getResponseModel().getEvalCount())),
                                        "time", rec(
                                                "load", jnt(result.getResponseModel().getLoadDuration()),
                                                "eval", jnt(result.getResponseModel().getEvalDuration()),
                                                "total", jnt(result.getResponseModel().getTotalDuration())));
                                if (lhs.vid() == null)
                                    lhs.<Rec>as().put(uri("history"), lhs.<Rec>as().at("history").orElse(lst()).add(last, MUTABLE), MUTABLE);
                                else
                                    Router.writeToSpace(lhs.vid().extend("history"), Router.readFromSpace(lhs.vid().extend("history")).orElse(lst()).add(last, MUTABLE));
                                return str(response.toString());
                            } catch (final Exception e) {
                                throw MTronException.of(e);
                            }
                        })));
        return set;
    }
}