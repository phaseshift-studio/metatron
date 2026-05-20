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
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.json.*;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.skills.Skills;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.llm.CostCalculator;
import studio.phaseshift.metatron.isa.llm.LLMFactory;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatMemoryStore;
import studio.phaseshift.metatron.isa.llm.space.SpaceContentRetriever;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MReal;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.vec.type.MVec;
import studio.phaseshift.metatron.isa.vec.type.Vec;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.llm.type.mMcpClient.MCP_CLIENT_TYPE;
import static studio.phaseshift.metatron.isa.llm.type.mTool.LLM_TOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Rel.REL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.str0;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.vec.vecInstSet.VEC_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class mModel extends MRec {
    public record Provider(String name, fURI host, String apiKey) {
    }

    public mModel(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static mModel model(final Rec model) {
        return new mModel(model.jvm(), MODEL_TID, model.vid());
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
        return this.at(f(FEATURE).extend(THINK)).apply(thought);
    }

    public Obj processResponse(final Str response, final boolean responseFormatted) {
        final Obj result = responseFormatted ?
                ObjSimpleJSONSerializer.single().inputBytes(ByteBuffer.wrap(response.strValue().getBytes(StandardCharsets.UTF_8))) :
                response;
        final Obj memObj = this.memory().at("mem");
        if (responseFormatted && memObj.isLst() && !memObj.asLst().isEmpty() && memObj.asLst().lstValue().getLast().isRec()) {
            memObj.asLst().lstValue().getLast().asRec().recValue().put(uri("attributes"), rec(uri(FORMAT), result));
            memObj.save();
        }
        this.asRec().at(f(FEATURE).extend(RESPONSE).extend(TO)).apply(result);
        return result;
    }

    public <T extends Obj> Optional<T> feature(final String feature) {
        return Optional.<Obj>ofNullable(this.at(f(FEATURE).extend(feature)).orElse(null)).map(o -> o.autoResolve(this)).map(o -> (T) o);
    }

    public Optional<Lst> tools() {
        return this.feature(TOOL);
    }

    public Optional<Rec> cost() {
        return Optional.<Rec>ofNullable(this.at(COST).orElse(null)).map(o -> o.autoResolve(this)).map(Obj::asRec);
    }

    public Optional<Lst> skills() {
        return this.feature(SKILL);
    }

    public Optional<Lst> notes() {
        return this.feature(NOTE);
    }

    public Optional<Obj> prompt() {
        return this.feature(PROMPT);
    }

    public Rec features() {
        return this.at(f(FEATURE)).orElse(noobjRec());
    }

    public void addNote(final Obj note) {
        this.feature(NOTE).orElse(lst()).ifPresent(l -> l.asLst().add(note, MUTABLE));
    }

    public Optional<Rec> responseFormat() {
        return this.feature(f(RESPONSE).extend(FORMAT).toString());
    }

    public Rec memory() {
        return this.at(f(FEATURE).extend(MEMORY)).orElse(noobjRec());
    }

    public Optional<Rec> lastResponse() {
        return Optional.<Obj>ofNullable(this.at(f(FEATURE).extend(RESPONSE)).orElse(null)).map(o -> o.autoResolve(this)).map(Obj::asRec);
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
        return this.feature(RAG);
    }

    public AiServices<mAgent> agent() {
        final List<String> systemMessage = new ArrayList<>();
        final AiServices<mAgent> service = AiServices.builder(mAgent.class);
        //////////////////////////////////////////
        /////////////// PROMPT ///////////////////
        //////////////////////////////////////////
        this.prompt().ifPresent(p -> {
            try {
                service.userMessage(p.isStr() ? p.strValue() : p.toString());
            } catch(Exception e) {
                throw MTronException.of("unable to setup prompt: %s", e);
            }
        });
        //////////////////////////////////////////
        /////////////// MEMORY ///////////////////
        //////////////////////////////////////////
        if (!this.memory().isNoObj()) {
            try {
                final fURI memoryVID = this.memory().at("mem").vid();
                if (memoryVID == null)
                    this.logger().warn("llm memory has no vid (ignoring): %s", this.memory());
                else {
                    service.chatMemory(MessageWindowChatMemory.builder()
                                    //.maxMessages(Router.readFromSpace(this.fetchMemory().uriValue().extend(MAX)).orElse(jnt(15)).intValue().intValue())
                                    .maxMessages(this.memory().at(MAX).intValue().intValue())
                                    .id(memoryVID)
                                    .chatMemoryStore(SpaceChatMemoryStore.single())
                                    .build())
                            .storeRetrievedContentInChatMemory(true);
                }
            } catch (Exception e) {
                throw MTronException.of("unable to setup memory: %s", e);
            }
        }
        //////////////////////////////////////////
        ///////////////  RESPONSE  ///////////////
        //////////////////////////////////////////
        // HANDLED IN LLMFACTORY
        //////////////////////////////////////////
        ///////////////   SKILLS /////////////////
        //////////////////////////////////////////
        if (this.skills().isPresent() && !this.skills().get().elements().allMatch(Obj::isUri)) {
            try {
                final Skills skills = new Skills.Builder().skills(
                        this.skills().get()
                                .elements()
                                .filter(s -> !s.isUri())
                                .map(s -> mSkill.of(s.apply().asRec()).toSkill())
                                .toList()).build();
                service.toolProvider(skills.toolProvider());
                systemMessage.add("You have access to the following skills:\n" + skills.formatAvailableSkills()
                        + "\nWhen the user's request relates to one of these skills, activate it first using the `activate_skill` tool before proceeding.");
            } catch (Exception e) {
                throw MTronException.of("unable to setup skills: %s", e);
            }
        }

        //////////////////////////////////////////
        ///////////////  TOOLS  //////////////////
        //////////////////////////////////////////
        service.hallucinatedToolNameStrategy(tool -> new ToolExecutionResultMessage(ToolExecutionResultMessage.builder().toolName(tool.name()).text("unknown or inaccessible tool")));
        if (this.tools().isPresent()) {
            try {
                final Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();
                this.tools().get()
                        .elements()
                        .flatMap(e -> e.isObjs() ? e.elements() : Stream.of(e))
                        .map(e -> e.autoResolve(this))
                        .filter(t -> !t.isNoObj())
                        .forEach(t -> {
                            try {
                                if (t.isRec() && t.test(MCP_CLIENT_TYPE)) {
                                    service.toolProvider(McpToolProvider.builder().mcpClients(Rec.wrap(t.as(), mMcpClient.class).client()).build()).executeToolsConcurrently(BootLoader.getExecutor());
                                } else if (t.isObjInst()) {
                                    if (QCollection.isNoDocs(Router.global().read(t.tid().addQ(DOCQ))))
                                        t.logger().warn("ignoring inst as it has no associated ?docq: %s", t);
                                    else {
                                        final Tuple.Pair<ToolSpecification, ToolExecutor> pair = mTool.mtronInstToolSpecification(mTool.mtronInstToTool(t.asInst()));
                                        tools.put(pair.get0(), pair.get1());
                                    }
                                } else if (t.isRec() && t.test(LLM_TOOL_TYPE)) {
                                    final Tuple.Pair<ToolSpecification, ToolExecutor> pair = mTool.mtronInstToolSpecification(t.asRec());
                                    tools.put(pair.get0(), pair.get1());
                                }
                            } catch(final Exception e) {
                                this.logger().error("unable to set up tool: %s [%s]",t,e);
                            }
                        });
                if (!tools.isEmpty())
                    service.tools(tools).executeToolsConcurrently(BootLoader.getExecutor());
            } catch (Exception e) {
                throw MTronException.of("unable to setup tools: %s", e);
            }
        }
        /////////////////////////////////////////////
        ///////////////   NOTES   //////////////////
        ////////////////////////////////////////////
        if (this.notes().isPresent()) {
            try {
                if (null == this.vid())
                    this.logger().warn("llm has no vid (ignoring): %s", this.notes());
                else
                    systemMessage.add("""
                                      ### IMPORTANT ###
                                      Always check for any notes the user has provided you.
                                      Do this before, during, and after completing your task.
                                      The contents of the notes should be deemed of crucial importance.
                                      To check for notes, use your provided mtron `eval` tool with the following argument:
                                        `@<%s/feature/note>.remove(0)`
                                      A result of `noobj` means "no note" at this time, but do check again periodically.
                                      """.formatted(this.vid()));
            } catch (Exception e) {
                throw MTronException.of("unable to setup notes: %s", e);
            }
        }
        //////////////////////////////////////////
        ///////////////   RAG   //////////////////
        //////////////////////////////////////////
        // RAG = Retrieval Augmented Generation
        // Before sending to LLM, search Space for relevant context and inject it into the prompt
        if (this.rag().isPresent()) {
            try {
                final Rec ragConfig = this.rag().get();
                final fURI pattern = ragConfig.at(PATTERN).uriValue();
                final int maxResults = ragConfig.at(MAX).orElse(jnt(10)).intValue().intValue();
                this.logger().info("rag enabled: pattern=%s, max=%d", pattern, maxResults);
                service.contentRetriever(new SpaceContentRetriever(pattern, maxResults));
            } catch (Exception e) {
                throw MTronException.of("unable to setup rag: %s", e);
            }
        }
        // merge all system messages into a single system message
        try {
            service.systemMessage(String.join("\n", systemMessage));
        } catch(Exception e) {
            throw MTronException.of("unable to setup system message: %s", e);
        }
        /// ////////////////////////////////////////////////////////////////////////////////////////
        return service;
    }
    
  /*  public Rec query(final Rec query) {
        this.agent()
    }*/

    public mModel chat(final String message, final Inst onResponse) {
        BootLoader.getExecutor().submit(() -> {
            onResponse.apply(this.chat(message));
        });
        return this;
    }

    public Obj chat(final String message) {
        return this.chat(message, noobjRec());
    }

    public Obj chat(final String message, final Rec responseFormat) {
        final StringBuilder response = new StringBuilder();
        Router.global().stats().ioStats().incrBytesSent(message.getBytes().length);
        final AtomicBoolean isThinking = new AtomicBoolean(false);
        final AtomicBoolean isResponding = new AtomicBoolean(false);
        final AtomicBoolean isComplete = new AtomicBoolean(false);
        final AtomicBoolean isTooling = new AtomicBoolean(false);
        final AtomicReference<MTronException> isError = new AtomicReference<>();

        try {
            final mAgent agent = this.agent()
                    .systemMessageTransformer((current, content) -> this.at(DESC).orElse(str0()).strValue() + "\n\n" + current)
                    .streamingChatModel(LLMFactory.createChatInteraction(this, this.model(), responseFormat)).build();
            final AtomicReference<String> STAGE = new AtomicReference<>("START");
            agent.chat(message)
                    .onToolExecuted(tool -> {
                        STAGE.set("TOOLING");
                        this.logger().info("tool executed: %s(%s) => %s", tool.request().name(), tool.request().arguments(), tool.result());
                        isTooling.set(false);
                    })
                    .onCompleteResponse(c -> {
                        STAGE.set("COMPLETE");
                        isComplete.set(true);
                        isResponding.set(false);
                        Router.global().stats().ioStats().incrBytesRecv(c.aiMessage().text().getBytes().length);
                        this.processResponse(str(c.aiMessage().text()), !responseFormat.isNoObj() && !responseFormat.isEmpty());
                        this.logger().none("\n");
                    })
                    .onPartialToolCall(partialToolCall -> {
                        if (!isTooling.getAndSet(true)) {
                            STAGE.set("TOOLING (" + partialToolCall.name() + ")");
                            this.logger().none(Graphitty.sillyPrint("tooling...\n", true, true));
                            this.logger().none("\t{{y}}partial{{X}}: {{b}}%s{{g}}({{b}}%s{{g}}){{X}}\n", partialToolCall.name(), partialToolCall.partialArguments());
                        }
                    })
                    .onPartialResponse(s -> {
                        STAGE.set("RESPONDING");
                        if (!isResponding.getAndSet(true))
                            this.logger().none(Graphitty.sillyPrint("responding...", true, true));
                        Router.global().stats().ioStats().incrBytesRecv(s.getBytes().length);
                        response.append(s);
                    })
                    .onPartialThinking(t -> {
                        STAGE.set("THINKING");
                        if (this.has(f(FEATURE).extend(THINK)) && !isThinking.getAndSet(true))
                            this.logger().none(Graphitty.sillyPrint("thinking...\n", true, true));
                        this.processThought(str(t.text()));
                        Router.global().stats().ioStats().incrBytesRecv(t.text().getBytes().length);
                    })
                    .onError(e -> {
                        isError.set(MTronException.of("error during %s: %s", STAGE.get(), e));
                        isComplete.set(true);
                    }).start();
            while (!isComplete.get()) {
                CommonUtil.sleepThread(250);
                if (isResponding.get())
                    this.logger().none(Graphitty.sillyPrint(".", true, false));
            }
            if (null != isError.get())
                throw isError.get();
        } catch (final Exception e) {
            //e.printStackTrace();
            throw MTronException.of(e);
        }
        return this.processResponse(str(response.toString()), !responseFormat.isNoObj() && !responseFormat.isEmpty());
    }

    public Lst embed(final Obj toEmbed) {
        final EmbeddingModel agent = LLMFactory.createEmbeddingInteraction(this, this.model());
        if (this.cost().isPresent())
            agent.addListener(new CostCalculator(this.cost().get()));
        final TextSegment embeddingString = TextSegment.from(toEmbed.toString());
        if (toEmbed.vid() != null)
            embeddingString.metadata().put("vid", toEmbed.vid().toString());
        embeddingString.metadata().put("tid", toEmbed.tid().toString());
        final Response<Embedding> response = agent.embed(embeddingString);
        if (null != response.tokenUsage())
            this.logger().info("embedding token usage: %s", response.tokenUsage());
        return lst((List) new MVec<>(new Vector<>(response.content().vectorAsList().stream().map(MReal::real).toList()), VEC_TID, null).jvm().stream().toList());
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
                return lstToSchema(null == depth ? lst() : depth.asLst(), description);
            else if (type.test(REC_TYPE))
                return recToSchema(null == depth ? rec() : depth.asRec(), description);
            else if (type.test(REL_TYPE))
                return recToSchema(rec(depth.asRel().first().type(), depth.asRel().second()), description);
            else
                return new JsonStringSchema.Builder().description(description).build();
            //throw MTronException.of("unsupported obj type for schema: %s", type);
        }

        public static JsonArraySchema lstToSchema(final Lst l, final String description) {
            final JsonArraySchema.Builder schema = JsonArraySchema.builder();
            l.elements().forEach(e -> schema.items(objToSchema(e.type(), null, description)));
            // OpenAI structured outputs require a defined items schema; fall back to string for untyped lists
            if (l.isEmpty())
                schema.items(new JsonStringSchema.Builder().description(description).build());
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
    }
}

 
    

/*
  public static Tools.mTool mtronInstTool(final Inst inst) {
        final Docs doc = Router.readFromSpace(inst.tid().q("doc", null))
                .orSupply(() -> Docs.doc(inst,
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

        return Tools.mTool.builder()
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

    public static Tools.mTool mtronEvalToolSpecification() {
        return Tools.mTool.builder()
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

