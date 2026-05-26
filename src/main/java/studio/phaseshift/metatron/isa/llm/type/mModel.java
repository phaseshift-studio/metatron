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
import studio.phaseshift.metatron.isa.llm.Capability;
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
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

    public Obj applyThoughtInstruction(final Str thought) {
        return this.at(feat(THINK)).apply(thought);
    }

    public Obj recordResponse(final Str response, final boolean responseFormatted) {
        final Obj result = responseFormatted ?
                ObjSimpleJSONSerializer.single().inputBytes(ByteBuffer.wrap(response.strValue().getBytes(StandardCharsets.UTF_8))) :
                response;
        final Obj memObj = this.memory().at("mem");
        if (responseFormatted && memObj.isLst() && !memObj.asLst().isEmpty() && memObj.asLst().lstValue().getLast().isRec()) {
            memObj.asLst().lstValue().getLast().asRec().recValue().put(uri("attributes"), rec(uri(FORMAT), result));
            memObj.save();
        }
        this.asRec().at(feat(RESPONSE, TO)).apply(result);
        return result;
    }

    public <T extends Obj> Optional<T> feature(final String feature) {
        return Optional.<Obj>ofNullable(this.at(feat(feature)).orElse(null)).map(o -> o.autoResolve(this)).map(o -> (T) o);
    }

    public Optional<Lst> tools() {
        return this.feature(TOOL);
    }

    public Optional<Rec> cost() {
        return Optional.<Rec>ofNullable(this.at(feat(COST)).orElse(null)).map(o -> o.autoResolve(this)).map(Obj::asRec);
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
        return this.at(feat()).orElse(noobjRec());
    }

    public void addNote(final Obj note) {
        this.feature(NOTE).orElse(lst()).ifPresent(l -> l.asLst().add(note, MUTABLE));
    }

    public Optional<Rec> responseFormat() {
        return this.feature(f(RESPONSE).extend(FORMAT).toString());
    }

    public Rec memory() {
        return this.at(feat(MEMORY)).orElse(noobjRec());
    }

    public Optional<Rec> lastResponse() {
        return Optional.<Obj>ofNullable(this.at(feat(RESPONSE)).orElse(null)).map(o -> o.autoResolve(this)).map(Obj::asRec);
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

    static fURI feat(final String... segments) {
        fURI path = f(FEATURE);
        for (final String segment : segments)
            path = path.extend(segment);
        return path;
    }

    public AiServices<mAgent> agentBuilder() {
        final List<String> systemMessages = new ArrayList<>();
        final AiServices<mAgent> service = AiServices.builder(mAgent.class);

        promptCapability().apply(service, systemMessages);
        memoryCapability().apply(service, systemMessages);
        skillsCapability().apply(service, systemMessages);
        toolsCapability().apply(service, systemMessages);
        notesCapability().apply(service, systemMessages);
        ragCapability().apply(service, systemMessages);
        mergeSystemMessages(service, systemMessages);

        return service;
    }

    private Capability promptCapability() {
        return (service, systemMessages) -> this.prompt().ifPresent(p -> {
            if (p.toString().isBlank())
                return;
            try {
                service.userMessage(p.isStr() ? p.strValue() : p.toString());
            } catch (Exception e) {
                throw MTronException.of("unable to setup prompt: %s", e);
            }
        });
    }

    private Capability memoryCapability() {
        return (service, systemMessages) -> {
            if (!this.memory().isNoObj()) {
                try {
                    final fURI memoryVID = this.memory().at("mem").vid();
                    if (memoryVID == null)
                        this.logger().warn("llm memory has no vid (ignoring): %s", this.memory());
                    else {
                        service.chatMemory(MessageWindowChatMemory.builder()
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
        };
    }

    private Capability skillsCapability() {
        return (service, systemMessages) -> {
            if (this.skills().isPresent()) {
                try {
                    final Skills skills = new Skills.Builder().skills(
                            this.skills().get()
                                    .elements()
                                    .filter(s -> !s.isUri())
                                    .map(s -> mSkill.of(s.apply().asRec()).toSkill())
                                    .toList()).build();
                    service.toolProvider(skills.toolProvider());
                    systemMessages.add("You have access to the following skills:\n" + skills.formatAvailableSkills()
                            + "\nWhen the user's request relates to one of these skills, activate it first using the `activate_skill` tool before proceeding.");
                } catch (Exception e) {
                    throw MTronException.of("unable to setup skills: %s", e);
                }
            }
        };
    }

    private Capability toolsCapability() {
        return (service, systemMessages) -> {
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
                                } catch (final Exception e) {
                                    this.logger().error("unable to set up tool: %s [%s]", t, e);
                                }
                            });
                    if (!tools.isEmpty())
                        service.tools(tools).executeToolsConcurrently(BootLoader.getExecutor());
                } catch (Exception e) {
                    throw MTronException.of("unable to setup tools: %s", e);
                }
            }
        };
    }

    private Capability notesCapability() {
        return (service, systemMessages) -> {
            if (this.notes().isPresent() && this.vid() != null) {
                try {
                    systemMessages.add("""
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
        };
    }

    private Capability ragCapability() {
        return (service, systemMessages) -> {
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
        };
    }

    private void mergeSystemMessages(final AiServices<mAgent> service, final List<String> systemMessages) {
        try {
            final String finalSystemMessage = String.join("\n", systemMessages);
            if (!finalSystemMessage.isBlank())
                service.systemMessage(finalSystemMessage);
        } catch (Exception e) {
            throw MTronException.of("unable to setup system message: %s", e);
        }
    }

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
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean isThinking = new AtomicBoolean(false);
        final AtomicBoolean isResponding = new AtomicBoolean(false);
        final AtomicBoolean isTooling = new AtomicBoolean(false);
        final AtomicReference<MTronException> isError = new AtomicReference<>();

        try {
            final mAgent agent = (this.has(DESC) && !this.at(DESC).strValue().isBlank() ?
                    this.agentBuilder().systemMessageTransformer((current, content) -> this.at(DESC).orElse(str0()).strValue() + "\n\n" + current) :
                    this.agentBuilder())
                    .streamingChatModel(LLMFactory.createChatInteraction(this, this.model(), responseFormat)).build();
            final AtomicReference<String> STAGE = new AtomicReference<>("START");
            if (message.isBlank())
                throw MTronException.of("no message provided: %s", this.vid());
            agent.chat(message)
                    .onToolExecuted(tool -> {
                        STAGE.set("TOOLING");
                        this.logger().info("tool executed: %s(%s) => %s", tool.request().name(), tool.request().arguments(), tool.result());
                        isTooling.set(false);
                    })
                    .onCompleteResponse(c -> {
                        STAGE.set("COMPLETE");
                        latch.countDown();
                        isResponding.set(false);
                        Router.global().stats().ioStats().incrBytesRecv(c.aiMessage().text().getBytes().length);
                        this.recordResponse(str(c.aiMessage().text()), !responseFormat.isNoObj() && !responseFormat.isEmpty());
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
                        if (this.has(feat(THINK)) && !isThinking.getAndSet(true))
                            this.logger().none(Graphitty.sillyPrint("thinking...\n", true, true));
                        this.applyThoughtInstruction(str(t.text()));
                        Router.global().stats().ioStats().incrBytesRecv(t.text().getBytes().length);
                    })
                    .onError(e -> {
                        isError.set(MTronException.of("error during %s: %s", STAGE.get(), e));
                        latch.countDown();
                    }).start();
            while (!latch.await(250, TimeUnit.MILLISECONDS)) {
                if (isResponding.get())
                    this.logger().none(Graphitty.sillyPrint(".", true, false));
            }
            if (null != isError.get())
                throw isError.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw MTronException.of(e);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
        return this.recordResponse(str(response.toString()), !responseFormat.isNoObj() && !responseFormat.isEmpty());
    }

    public Lst embed(final Obj toEmbed) {
        final EmbeddingModel agent = LLMFactory.createEmbeddingInteraction(this, this.model());
        if (this.cost().isPresent())
            agent.addListener(new CostCalculator(this.cost().get()));
        final TextSegment embeddingString = TextSegment.from(toEmbed.toString());
        final Response<Embedding> response = agent.embed(embeddingString);
        if (null != response.tokenUsage())
            this.logger().info("embedding token usage: %s", response.tokenUsage());
        return lst((List) new MVec<>(new Vector<>(response.content().vectorAsList().stream().map(MReal::real).toList()), VEC_TID, null).jvm().stream().toList());
    }

}