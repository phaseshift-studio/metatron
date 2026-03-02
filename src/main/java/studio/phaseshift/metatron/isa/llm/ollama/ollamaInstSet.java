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

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecutor;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.llm.ollama.space.SpaceChatMemoryStore;
import studio.phaseshift.metatron.isa.llm.ollama.type.OLLM;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.q.DocQ.Doc.docWrap;
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
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
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


    interface Assistant {
        TokenStream chat(@dev.langchain4j.service.UserMessage ChatRequest userMessage);

        TokenStream chat(String userMessage);
    }
    public static Type OLLM_TYPE = docWrap(Type.Builder.build()
            .tid(LLM_TID)
            .vid(OLLAMA_OLLM_TID).
            isaPredicate(rec(
                    uri(NAME), URI_TYPE,
                    uri(HOST), URI_TYPE,
                    uri(THINK).c(cInt::maybe), BOOL_TYPE,
                    uri(SIZE), INT_TYPE,
                    uri(MEMORY).maybe(), URI_TYPE,
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
            .inst(instC(INST_TID.extend("chat").dom(OLLAMA_OLLM_TID).rng(STR_TID), lst(STR_TYPE, T(ALL.maybe())),
                    (lhs, inst) -> {
                        //try {
                        final StringBuilder response = new StringBuilder();
                        try {
                            final GraphittyLogger LOG = Graphitty.log(lhs);
                            final String host = lhs.asRec().at(HOST).uriValue().toString();
                            final boolean toolUse = !lhs.asRec().at(TOOL).isNoObj();
                            final boolean thinking = lhs.asRec().at(THINK).orElse(bool(false)).boolValue();
                            final Uri memory = lhs.asRec().at(MEMORY).orElse(null);
                            final String model = lhs.asRec().at(NAME).uriValue().toString();
                            final Map<ToolSpecification, ToolExecutor> toolSpecs = lhs.asRec().at(TOOL).elements()
                                    .filter(Obj::isInst)
                                    .map(i -> OLLM.mtronInstToolSpecification(i.asInst()))
                                    .collect(Collectors.toMap(Tuple.Pair::get0, Tuple.Pair::get1));
                            /// ///////////////////////////////////////////////////////////////////////////////////////
                            final StreamingChatModel streamingChatModel = OllamaStreamingChatModel.builder()
                                    .baseUrl(host)
                                    .modelName(model)
                                    .think(thinking)
                                    .returnThinking(thinking)
                                    .build();
                            final AiServices<Assistant> service = AiServices.builder(Assistant.class)
                                    .streamingChatModel(streamingChatModel);
                            if (null != memory) {
                                service.chatMemory(MessageWindowChatMemory.builder()
                                        .maxMessages(15)
                                        .id(memory.uriValue())
                                        .chatMemoryStore(SpaceChatMemoryStore.single())
                                        .build());
                            }
                            if (toolUse)
                                service.tools(toolSpecs);
                            /// ////////////////////////////////////////////////////////////////////////////////////////
                            final Assistant assistant = service.build();
                            Router.global().stats().ioStats().incrBytesSent(inst.arg(0).strValue().getBytes().length);
                            final AtomicBoolean isThinking = new AtomicBoolean(false);
                            final AtomicBoolean isResponding = new AtomicBoolean(false);
                            final AtomicBoolean isComplete = new AtomicBoolean(false);
                            final AtomicBoolean isTooling = new AtomicBoolean(false);
                            final AtomicReference<MTronException> isError = new AtomicReference<>();
                            assistant.chat(inst.arg(0).strValue())
                                    .onToolExecuted(tool -> {
                                        LOG.info("tool executed: %s(%s) => %s", tool.request().name(), tool.request().arguments(), tool.result());
                                        isTooling.set(false);
                                    })
                                    .onCompleteResponse(c -> {
                                        isComplete.set(true);
                                        LOG.none("\n");
                                    })
                                    .onPartialToolCall(partialToolCall -> {
                                        if (!isTooling.getAndSet(true)) {
                                            LOG.none(Graphitty.sillyPrint("tooling:\n", true, true));
                                            LOG.none("\t{{y}}partial{{X}}: {{b}}%s{{g}}({{b}}%s{{g}}){{X}}\n", partialToolCall.name(), partialToolCall.partialArguments());
                                        }
                                    })
                                    .onPartialResponse(s -> {
                                        isResponding.getAndSet(true);
                                        Router.global().stats().ioStats().incrBytesRecv(s.getBytes().length);
                                        response.append(s);
                                    })
                                    .onPartialThinking(t -> {
                                        if (!isThinking.getAndSet(true))
                                            LOG.none(Graphitty.sillyPrint("thinking..\n", true, true));
                                        Router.global().stats().ioStats().incrBytesRecv(t.text().getBytes().length);
                                        LOG.none("{{b}}%s{{X}}", t.text());
                                    })
                                    .onError(e -> isError.set(MTronException.of(e))).start();
                            while (!isComplete.get()) {
                                CommonUtil.sleepThread(100);
                            }
                            if (null != isError.get())
                                throw isError.get();
                        } catch (final Exception e) {
                            throw MTronException.of(e);
                        }
                        return str(response.toString());
                    }))
            .create(TYPES, INSTS), "a ollama backed large language model", "the model construction", Map.of(
            uri(NAME), "the name of the model",
            uri(HOST), "the ollama host endpoint",
            uri(THINK).c(cInt::maybe), "whether to think before responding",
            uri(SIZE), "the size of the model",
            uri(MEMORY).maybe(), "a pointer to the llm's memory",
            uri(SKILL).maybe(), "the skills to use",
            uri(TOOL).maybe(), "the tools to use"), "an ollama backed large language model");

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
/*
    final boolean hasStructuredPoly = !inst.arg(1).isNoObj();
                           final ChatModel chatModel = OllamaChatModel.builder()
                                    .baseUrl(host)
                                    .modelName(model)
                                    .think(thinking)
                                    .returnThinking(thinking)
                                    .build();
                            final ChatRequest chatRequest = ChatRequest.builder()
                                    .responseFormat(ResponseFormat.builder()
                                            .type(hasStructuredPoly ? ResponseFormatType.JSON : ResponseFormatType.TEXT)
                                            .jsonSchema(hasStructuredPoly ? JsonSchema.builder()
                                                    .rootElement(OLLM.objToSchema(inst.arg(1).type(), inst.arg(1).as(), "<no description>"))
                                                    .build() : null)
                                            .build())
                                    .toolChoice(ToolChoice.AUTO)
                                    .toolSpecifications(new ArrayList<>(toolSpecs.keySet()))
                                    .messages(new UserMessage(inst.arg(0).strValue())).build();
     
     */
}
