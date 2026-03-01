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

package studio.phaseshift.metatron.isa.llm.ollama;

import io.github.ollama4j.Ollama;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.models.chat.OllamaChatStreamObserver;
import io.github.ollama4j.models.generate.OllamaGenerateTokenHandler;
import io.github.ollama4j.models.request.ThinkMode;
import io.github.ollama4j.tools.Tools;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.llm.ollama.type.OLLM;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_ISA_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_TID;
import static studio.phaseshift.metatron.isa.llm.ollama.space.ollamaSpace.OLLAMA_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TID;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(tid = "/m/llm/ollama")
public class ollamaInstSet extends AbstractInstSet {
    public static final fURI OLLAMA_TID = LLM_ISA_TID.extend("ollama");
    public static final fURI OLLAMA_OLLM_TID = OLLAMA_TID.extend("ollm");
    public static final fURI OLLAMA_SPACE_TID = OLLAMA_TID.extend("space").extend("ollama");
    //protected final GraphittyLogger LOG = Graphitty.log(this);

    private static final Set<Type> TYPES = new LinkedHashSet<>();
    private static final Set<Inst> INSTS = new LinkedHashSet<>();

    public static Type OLLM_TYPE = Type.Builder.build()
            .tid(LLM_TID)
            .vid(OLLAMA_OLLM_TID).
            isaPredicate(rec(
                    uri(NAME), URI_TYPE,
                    uri(HOST), URI_TYPE,
                    uri(THINK).c(cInt::maybe), BOOL_TYPE,
                    uri(SIZE), INT_TYPE,
                    uri(SKILL).maybe(), LST_TYPE,
                    uri(TOOL).maybe(), LST_TYPE))
            /*.constructor(instC(INST_TID.dom(ALL.maybe()).rng(OLLAMA_OLLM_TID), lst(),
                    (lhs, inst) -> {
                        final String modelName = inst.arg(0).<Rec>as().at(NAME).uriValue().toString();
                        final OllamaModels models = OllamaModels.builder().baseUrl(inst.arg(0).<Rec>as().at(HOST).uriValue().toString()).build();
                        final OllamaModelCard card = models.modelCard(modelName).content();
                        final OllamaModel model = models.availableModels().content().stream().filter(m -> m.getName().equals(modelName)).findFirst().orElse(null);
                        if (null == model)
                            throw MTronException.of("unknown card");
                        return new OLLM(Tuple.Pair.with(model, card), OLLAMA_OLLM_TID, inst.arg(0).vid());
                    }))*/
            .inst(instC(INST_TID.extend("chat").dom(OLLAMA_OLLM_TID).rng(STR_TID), lst(STR_TYPE),
                    (lhs, inst) -> {
                       //try {
                           final GraphittyLogger LOG = Graphitty.log(lhs);
                           final String host = lhs.asRec().at(Tokens.HOST).uriValue().toString();
                           final boolean toolUse = !lhs.asRec().at(TOOL).isNoObj();
                           final boolean thinking = lhs.asRec().at(THINK).orElse(bool(false)).boolValue();
                           final String model = lhs.asRec().at(NAME).uriValue().toString();
                           final List<Tools.Tool> tools = lhs.asRec().at(TOOL).elements().filter(Obj::isInst).map(i -> OLLM.mtronInstTool(i.asInst())).collect(Collectors.toCollection(ArrayList::new));
                           final OllamaChatRequest chatRequest =
                                   OllamaChatRequest.builder()
                                           .withModel(model)
                                           .withThinking(thinking ? ThinkMode.ENABLED : ThinkMode.DISABLED)
                                           .withMessage(OllamaChatMessageRole.USER, inst.arg(0).strValue())
                                           .withUseTools(toolUse)
                                           .withTools(tools)
                                           .build();

                           final StringBuilder response = new StringBuilder();
                           final OllamaGenerateTokenHandler thinkingStreamHandler =
                                   (s) -> {
                                       Router.global().stats().ioStats().incrBytesRecv(s.getBytes().length);
                                       LOG.none("{{m}}%s{{X}}", s);
                                   };

                           final AtomicBoolean start = new AtomicBoolean(thinking);
                           final OllamaGenerateTokenHandler responseStreamHandler =
                                   (s) -> {
                                       if (start.getAndSet(false))
                                           LOG.none("\n");
                                       LOG.none("{{y}}%s{{X}}", s);
                                       Router.global().stats().ioStats().incrBytesRecv(s.getBytes().length);
                                       response.append(s);
                                   };

                           final OllamaChatResult result;
                           try {
                               if (thinking)
                                   LOG.none(Graphitty.sillyPrint("thinking...\n", true, true));
                               Router.global().stats().ioStats().incrBytesSent(inst.arg(0).strValue().getBytes().length);
                               result = new Ollama(host).chat(chatRequest, new OllamaChatStreamObserver(thinking ? thinkingStreamHandler : null, responseStreamHandler));
                               while (!result.getResponseModel().isDone()) {
                                   CommonUtil.sleepThread(100);
                               }
                           } catch (final Exception e) {
                               throw MTronException.of(e);
                           }
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
                               lhs.asRec().at("history", lhs.asRec().at("history").orElse(lst()).add(last, IMMUTABLE), MUTABLE);
                           else
                               Router.readFromSpace(lhs.vid().extend("history")).orElse(lst()).add(last, MUTABLE);

                           return str(response.toString());
                      /* } catch(final Exception e) {
                        e.printStackTrace();
                           throw MTronException.of(e);
                       }*/

                    }))
            .create(TYPES, INSTS);

    public ollamaInstSet() {
        super(OLLAMA_TID, OLLAMA_TID);
    }

    public Set<Type> types() {
        TYPES.add(OLLAMA_SPACE_TYPE);
        return TYPES;
    }
    
    /*
       return new LinkedHashMap<>() {{
            put(uri(NAME), uri(model.getModelName()));
            put(uri("size"), jnt(model.getSize()));
            put(uri("quant"), uri(model.getModelMeta().getQuantizationLevel()));
            put(uri("family"), uri(model.getModelMeta().getFamily()));
            //   put(uri("card"), rec(model.get1().getModelInfo(), MObjFactory.of()));
        }};
     */

    public Set<Inst> insts() {
        return INSTS;
    }
}
