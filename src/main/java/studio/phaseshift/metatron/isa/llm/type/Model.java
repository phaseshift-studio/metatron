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

package studio.phaseshift.metatron.isa.llm.type;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.json.*;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.skills.ActivateSkillToolConfig;
import dev.langchain4j.skills.Skills;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.llm.LLMFactory;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatMemoryStore;
import studio.phaseshift.metatron.isa.llm.space.SpaceContentRetriever;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.langchain4j.internal.Json.fromJson;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.MCP_SERVER_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.MODEL_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Rel.REL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class Model extends MRec {
    public record Provider(String name, fURI host, String apiKey) {
    }

    public Model(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static Model model(final Rec model) {
        return new Model(model.jvm(), MODEL_TID, model.vid());
    }

    public String model() {
        return this.at(NAME).uriValue().toString();
    }

    public Provider provider() {
        return new Provider(
                this.at(PROVIDER).asRec().at(NAME).strValue(),
                this.at(PROVIDER).asRec().at(HOST).uriValue(),
                this.at(API_KEY).orElse(str("")).strValue());
    }

    public Obj processThought(final Str thought) {
        return this.at(THINK).orElse(rec0()).at(TO).apply(thought);
    }

    public Obj processResponse(final Str response) {
        final Obj result = this.at(RESPONSE).orElse(rec0()).has(FORMAT) ?
                ObjSimpleJSONSerializer.single().inputBytes(ByteBuffer.wrap(response.strValue().getBytes(StandardCharsets.UTF_8))) :
                response;
        return this.at(RESPONSE).orElse(rec0()).at(TO).apply(result);
    }

    public Obj fetchMemory() {
        return this.at(MEMORY).orElse(rec0()).at(FROM);
    }

    public Obj processMemory(final ChatMessage message) {
        return noobj();
        // return this.at(MEMORY).orElse(rec0()).at(TO).apply(message);
    }

    public Optional<Lst> tools() {
        return Optional.<Obj>ofNullable(this.at(TOOL).orElse(null)).map(o -> o.autoResolve(this)).map(Obj::asLst);
    }

    public Optional<Lst> skills() {
        return Optional.<Obj>ofNullable(this.at(SKILL).orElse(null)).map(o -> o.autoResolve(this)).map(Obj::asLst);
    }

    /**
     * RAG (Retrieval Augmented Generation) configuration.
     *
     * <p>Example mtron config:
     * <pre>
     * ollama:qwen3:32b[
     *   rag => [pattern => &lt;/sys/docs/#&gt;, max => 5]
     * ].chat("How do I use map?")
     * </pre>
     *
     * @return Optional rec with 'pattern' (fURI) and optional 'max' (int, default 10)
     */
    public Optional<Rec> rag() {
        return Optional.<Obj>ofNullable(this.at("rag").orElse(null)).map(o -> o.autoResolve(this)).map(Obj::asRec);
    }

    public AiServices<Agent> agent() {
        final AiServices<Agent> service = AiServices.builder(Agent.class);
        //////////////////////////////////////////
        /////////////// MEMORY ///////////////////
        //////////////////////////////////////////
        if (!this.fetchMemory().isNoObj())
            service.chatMemory(MessageWindowChatMemory.builder()
                    .maxMessages(Router.readFromSpace(this.fetchMemory().uriValue().extend(MAX)).orElse(jnt(15)).intValue().intValue())
                    .id(this.fetchMemory().uriValue())
                    .chatMemoryStore(SpaceChatMemoryStore.single())
                    .build());

        //////////////////////////////////////////
        ///////////////  RESPONSE  ///////////////
        //////////////////////////////////////////
        // HANDLED IN LLMFACTORY
        //////////////////////////////////////////
        ///////////////   SKILLS /////////////////
        //////////////////////////////////////////
        if (this.skills().isPresent()) {
            final Skills skills = new Skills.Builder().skills(this.skills().get().elements().map(s -> mSkill.of(s.asRec())).toList()).build();
            service.toolProvider(skills.toolProvider());
            service.systemMessage("You have access to the following skills:\n" + skills.formatAvailableSkills()
                    + "\nWhen the user's request relates to one of these skills, activate it first using the `activate_skill` tool before proceeding.");
        }
        //////////////////////////////////////////
        ///////////////  TOOLS  //////////////////
        //////////////////////////////////////////
        if (this.tools().isPresent()) {
            final Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();
            this.tools().get()
                    .elements()
                    .flatMap(e -> e.isObjs() ? e.elements() : Stream.of(e))
                    .forEach(t -> {
                        if (t.tid().equals(MCP_SERVER_TID)) {
                            service.toolProvider(McpToolProvider.builder().mcpClients(((MCPServer) t).client()).build()).executeToolsConcurrently(BootLoader.getExecutor());
                        } else if (t.isInst()) {
                            if (!Router.global().read(t.tid().q(DOCQ, null)).isNoObj()) {
                                final Tuple.Pair<ToolSpecification, ToolExecutor> pair = Model.Helper.mtronInstToolSpecification(t.asInst());
                                tools.put(pair.get0(), pair.get1());
                            } else {
                                t.logger().warn("ignoring inst as it has no associated ?doc: %s", t);
                            }
                        }
                    });
            if (!tools.isEmpty())
                service.tools(tools).executeToolsConcurrently(BootLoader.getExecutor());
        }
        //////////////////////////////////////////
        ///////////////   RAG   //////////////////
        //////////////////////////////////////////
        // RAG = Retrieval Augmented Generation
        // Before sending to LLM, search Space for relevant context and inject it into the prompt
        if (this.rag().isPresent()) {
            final Rec ragConfig = this.rag().get();
            final fURI pattern = ragConfig.at(PATTERN).uriValue();
            final int maxResults = ragConfig.at(MAX).orElse(jnt(10)).intValue().intValue();
            this.logger().info("RAG enabled: pattern=%s, max=%d", pattern, maxResults);
            service.contentRetriever(new SpaceContentRetriever(pattern, maxResults));
        }

        /// ////////////////////////////////////////////////////////////////////////////////////////
        return service;

    }
    
  /*  public Rec query(final Rec query) {
        this.agent()
    }*/

    public Model chat(final String message, final Inst onResponse) {
        BootLoader.getExecutor().submit(() -> {
            onResponse.apply(this.chat(message));
        });
        return this;
    }

    public Obj chat(final String message) {
        final StringBuilder response = new StringBuilder();
        Router.global().stats().ioStats().incrBytesSent(message.getBytes().length);
        final AtomicBoolean isThinking = new AtomicBoolean(false);
        final AtomicBoolean isResponding = new AtomicBoolean(false);
        final AtomicBoolean isComplete = new AtomicBoolean(false);
        final AtomicBoolean isTooling = new AtomicBoolean(false);
        final AtomicReference<MTronException> isError = new AtomicReference<>();
        try {
            final Agent agent = this.agent().streamingChatModel(LLMFactory.createChatInteraction(this, this.model())).build();
            agent.chat(message)
                    .onToolExecuted(tool -> {
                        this.logger().info("tool executed: %s(%s) => %s", tool.request().name(), tool.request().arguments(), tool.result());
                        isTooling.set(false);
                    })
                    .onCompleteResponse(c -> {
                        isComplete.set(true);
                        isResponding.set(false);
                        Router.global().stats().ioStats().incrBytesRecv(c.aiMessage().text().getBytes().length);
                        this.processResponse(str(c.aiMessage().text()));
                        this.logger().none("\n");
                    })
                    .onPartialToolCall(partialToolCall -> {
                        if (!isTooling.getAndSet(true)) {
                            this.logger().none(Graphitty.sillyPrint("tooling:\n", true, true));
                            this.logger().none("\t{{y}}partial{{X}}: {{b}}%s{{g}}({{b}}%s{{g}}){{X}}\n", partialToolCall.name(), partialToolCall.partialArguments());
                        }
                    })
                    .onPartialResponse(s -> {
                        if (!isResponding.getAndSet(true))
                            this.logger().none(Graphitty.sillyPrint("responding", true, true));
                        Router.global().stats().ioStats().incrBytesRecv(s.getBytes().length);
                        response.append(s);
                    })
                    .onPartialThinking(t -> {
                        if (this.has(THINK) && !isThinking.getAndSet(true))
                            this.logger().none(Graphitty.sillyPrint("thinking...\n", true, true));
                        this.processThought(str(t.text()));
                        Router.global().stats().ioStats().incrBytesRecv(t.text().getBytes().length);
                    })
                    .onError(e -> isError.set(MTronException.of(e))).start();
            while (!isComplete.get()) {
                CommonUtil.sleepThread(250);
                if (isResponding.get())
                    this.logger().none(Graphitty.sillyPrint(".", true, false));
            }
            if (null != isError.get())
                throw isError.get();
        } catch (final Exception e) {
            e.printStackTrace();
            throw MTronException.of(e);
        }
        return this.processResponse(str(response.toString()));
    }

    public static class Helper {
        public static JsonSchemaElement objToSchema(final Type type, final Poly<?, ?> depth, final String description) {
            if (type.test(BOOL_TYPE))
                return new JsonBooleanSchema.Builder().description(description).build();
            else if (type.test(INT_TYPE))
                return new JsonIntegerSchema.Builder().description(description).build();
            else if (type.test(REAL_TYPE))
                return new JsonNumberSchema.Builder().description(description).build();
            else if (type.test(URI_TYPE))
                return new JsonStringSchema.Builder().description(description).build();
            else if (type.test(STR_TYPE))
                return new JsonStringSchema.Builder().description(description).build();
            else if (type.test(LST_TYPE))
                return lstToSchema(depth.asLst(), description);
            else if (type.test(REC_TYPE))
                return recToSchema(depth.asRec(), description);
            else if (type.test(REL_TYPE))
                return recToSchema(rec(depth.asRel().first().type(), depth.asRel().second()), description);
            else
                return new JsonStringSchema.Builder().description(description).build();
            //throw MTronException.of("unsupported obj type for schema: %s", type);
        }

        public static JsonArraySchema lstToSchema(final Lst l, final String description) {
            final JsonArraySchema.Builder schema = JsonArraySchema.builder();
            l.elements().forEach(e -> schema.items(objToSchema(e.type(), null, description)));
            return schema.description(description).build();
        }

        public static JsonObjectSchema recToSchema(final Rec r, final String description) {
            final JsonObjectSchema.Builder schema = JsonObjectSchema.builder();
            final List<String> required = new ArrayList<>();
            r.elements().forEach(e -> {
                schema.addProperty(e.first().uriValue().toString(), objToSchema(e.second().type(), null, description));
                if (!e.first().c().isZeroable())
                    required.add(e.first().uriValue().toString());
            });

            schema.required(required);
            return schema.build();
        }

        public static Tuple.Pair<ToolSpecification, ToolExecutor> mtronInstToolSpecification(final Inst inst) {
            final QCollection.Doc doc = Router.readFromSpace(inst.tid().q(DOCQ, null))
                    .orSupply(() -> QCollection.Doc.doc(inst,
                            inst.dom().tid().toString(),
                            inst.rng().tid().toString(),
                            instB(AS_INST_TID, lst(REC_TYPE)).apply(inst.args().orElse(rec0())).asRec().elements().collect(Collectors.toMap(
                                    Rel::first,
                                    e -> e.second().tid().toString()
                            )),
                            "<no description>"));
            inst.logger().info("building ai compliant tool from mtron inst: %s => %s", inst.tid(), doc);
            JsonObjectSchema.Builder parameters = new JsonObjectSchema.Builder();
            List<String> required = new ArrayList<>();
            parameters.addProperty(LHS, objToSchema(inst.dom(), Type.Helper.polyTypePredicateObj(inst.dom()), doc.at(DOM).orElse(str("<no description>")).strValue()));
            if (!inst.tid().dom().c().isZeroable())
                required.add(LHS);
            final boolean recArgs = doc.args().isRec();
            final AtomicInteger counter = new AtomicInteger(0);
            doc.args().elements().forEach(e -> {
                final Rel kv = recArgs ? e.asRel() : rel(uri(ARG + counter.getAndIncrement()), e);
                parameters.addProperty(
                        kv.first().toString(),
                        objToSchema(kv.second().type(), Type.Helper.polyTypePredicateObj(kv.second().type()), kv.second().orElse(str("<no description>")).strValue()));
                if (!kv.second().c().isZeroable())
                    required.add(kv.first().toString());
            });
            parameters.required(required);
            ToolSpecification.Builder toolSpecBuilder = ToolSpecification.builder()
                    .name(inst.tid().basePath().toString())
                    .description(doc.description())
                    .parameters(parameters.build());

            ToolExecutor toolExecutor = (toolExecutionRequest, memoryId) -> {
                Map<String, Object> arguments = fromJson(toolExecutionRequest.arguments(), Map.class);
                final Poly<?, ?> args = inst.args().isNoObj() ? lst() : (inst.args().isLst() ?
                        lst(arguments.entrySet().stream().filter(e -> !e.getKey().equals(LHS)).map(e -> MObjFactory.single().toObjFromString(e.getValue().toString())).collect(Collectors.toList())) :
                        rec(arguments.entrySet().stream().filter(e -> !e.getKey().equals(LHS)).collect(Collectors.toMap(e -> uri(e.getKey()), e -> MObjFactory.single().toObjFromString(e.getValue().toString())))));
                final Object result = inst
                        .args(args)
                        .apply(arguments.containsKey(LHS) ? MObjFactory.single().toObjFromString(arguments.get(LHS).toString()) : noobj());
                inst.logger().info("evaluating mtron_inst tool: %s => %s => %s", arguments.get(LHS), inst, result);
                return result.toString();
            };
            return Tuple.Pair.with(toolSpecBuilder.build(), toolExecutor);
        }

    }
}
    

/*
  public static Tools.Tool mtronInstTool(final Inst inst) {
        final Doc doc = Router.readFromSpace(inst.tid().q("doc", null))
                .orSupply(() -> Doc.doc(inst,
                        inst.dom().tid().toString(),
                        inst.rng().tid().toString(),
                        instB(AS_INST_TID, lst(REC_TYPE)).apply(inst.args().orElse(rec0())).asRec().elements().collect(Collectors.toMap(
                                Rel::first,
                                e -> e.second().tid().toString()
                        )),
                        "<no description>"));
        LOG.info("building ollama compliant tool from mtron inst: %s => %s", inst.tid(), doc);
        Map<String, Tools.Property> instProperties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        instProperties.put("lhs", Tools.Property.builder().description(doc.at(DOM).toString()).required(!inst.dom().c().isZeroable()).type(inst.tid().dom().toString()).build());
        instProperties.putAll(inst.args().isLst() ?
                inst.args().asLst().indexedStream().collect(Collectors.toMap(
                        e -> e.first().intValue().toString(),
                        e -> Tools.Property.builder()
                                .required(!e.second().c().isZeroable())
                                .type(e.second().tid().toString())
                                .description(doc.args().at(e.first()).toString()).build())) :
                inst.args().asRec().elements().collect(Collectors.toMap(
                        e -> e.first().uriValue().toString(),
                        e -> Tools.Property.builder()
                                .required(!e.second().c().isZeroable())
                                .type(e.second().tid().toString())
                                .description(doc.args().at(e.first()).toString()).build()
                )));
        instProperties.values().forEach(p -> required.add(p.isRequired() ? "true" : "false"));

        return Tools.Tool.builder()
                .toolSpec(Tools.ToolSpec.builder()
                        .name(inst.tid().name())
                        .description(doc.description())
                        .parameters(new Tools.Parameters(instProperties, required)).build())
                .toolFunction(arguments -> {
                    final Poly<?, ?> args = inst.args().isNoObj() ? lst() : (inst.args().isLst() ?
                            lst(arguments.entrySet().stream().filter(e -> !e.getKey().equals("lhs")).map(e -> MObjFactory.single().toObjFromString(e.getValue().toString())).collect(Collectors.toList())) :
                            rec(arguments.entrySet().stream().filter(e -> !e.getKey().equals("lhs")).collect(Collectors.toMap(e -> uri(e.getKey()), e -> MObjFactory.single().toObjFromString(e.getValue().toString())))));
                    final Object result = inst
                            .args(args)
                            .apply(MObjFactory.single().toObjFromString(arguments.get("lhs").toString()));
                    LOG.info("evaluating mtron_inst tool: %s => %s => %s", arguments.get("lhs"), inst, result);
                    return result;
                })
                .isMCPTool(false)
                .type(inst.rng().tid().toString())
                .build();
    }

    public static Tools.Tool mtronEvalToolSpecification() {
        return Tools.Tool.builder()
                .toolSpec(Tools.ToolSpec.builder()
                        .name("mtron_eval")
                        .description("evaluate mtron source code and get back an obj result")
                        .parameters(new Tools.Parameters(Map.of(
                                "code", Tools.Property.builder().required(true).type("string").description("mtron sourcecode to evaluate").build()),
                                List.of("true")))
                        .build())
                .toolFunction(new ToolFunction() {
                    @Override
                    public Object apply(final Map<String, Object> arguments) {
                        LOG.info("evaluating mtron_eval tool: %s", arguments.get("code"));
                        return mParser.eval((String) arguments.get("code"));
                    }
                }).isMCPTool(false).type("obj").build();
    }
 */

