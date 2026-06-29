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

package studio.phaseshift.metatron.isa.llm.type;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.llm.CostCalculator;
import studio.phaseshift.metatron.isa.llm.LLMFactory;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatSessionStore;
import studio.phaseshift.metatron.isa.llm.type.mod.*;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MReal;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.vec.type.MVec;
import studio.phaseshift.metatron.util.MTronException;

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
import static studio.phaseshift.metatron.isa.llm.llmInstSet.MODEL_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.SYSTEM_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.llm.type.mTool.LLM_TOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.str0;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread.virtual;
import static studio.phaseshift.metatron.isa.vec.type.MVec.vec;
import static studio.phaseshift.metatron.isa.vec.vecInstSet.VEC_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.MCP_CLIENT_TYPE;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class mModel extends MRec {
    public record Provider(String name, fURI host, String apiKey) {
    }

    // User-added system messages (via addSystemMessage).  Merged with
    // capability-generated messages during agent construction.
    private final List<String> customSystemMessages = new ArrayList<>();
    protected final GraphittyLogger LOG = Graphitty.log(this);

    public mModel(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * Add a system message that will be included in every chat turn.
     * Mirrored to the typed table when the agent is built.
     */
    public void addSystemMessage(final String text) {
        this.customSystemMessages.add(text);
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
        // Update the last message's attributes (messages are inline typed Recs in the mem Lst)
        final Obj memObj = this.session().at("mem");
        if (responseFormatted && memObj.isLst() && !memObj.asLst().isEmpty()) {
            final Obj last = memObj.asLst().lstValue().getLast();
            if (last.isRec()) {
                last.asRec().recValue().put(uri(ATTRIBUTES), rec(uri(FORMAT), result));
                memObj.save();
            }
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

    public Rec session() {
        return this.at(feat(SESSION)).orElse(noobjRec());
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
        final AiServices<mAgent> service = AiServices.builder(mAgent.class);
        new PromptMod().apply(this, service);
        new SessionMod().apply(this, service);
        new SkillMod().apply(this, service);
        new ToolMod().apply(this, service);
        new SystemMod().apply(this, service);
        return service;
    }

  /*  private Capability ragCapability() {
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
    }*/

    public List<String> getSystemMessages() {
        return this.customSystemMessages;
    }

    protected String onMessageUpdate(final String message, final String messageType) {
        final Obj onMessage = this.at(f(FEATURE).extend(SESSION).extend(messageType));
        return onMessage.isNoObj() ? message : Str.Helper.cleanString(onMessage.apply(str(message)));
    }

    public Obj chat(final String message) {
        return this.chat(message, noobjRec());
    }

    public Obj chat(final String message, final Rec responseFormat) {
        final StringBuilder response = new StringBuilder();
        final String processedMessage = this.onMessageUpdate(message, "on_user");
        Router.global().stats().ioStats().incrBytesSent(processedMessage.getBytes().length);
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean isThinking = new AtomicBoolean(false);
        final AtomicBoolean isResponding = new AtomicBoolean(false);
        final AtomicBoolean isTooling = new AtomicBoolean(false);
        final AtomicReference<MTronException> isError = new AtomicReference<>();

        try {
            final mAgent agent = (this.has(DESC) && !this.at(DESC).strValue().isBlank() ?
                    this.agentBuilder().systemMessageTransformer((current, content) -> (this.at(DESC).orElse(str0()).strValue() + "\n\n" + (current != null ? current : "")).trim()) :
                    this.agentBuilder())
                    .streamingChatModel(LLMFactory.createChatInteraction(this, this.model(), responseFormat)).build();
            final AtomicReference<String> STAGE = new AtomicReference<>("START");
            if (processedMessage.isBlank())
                throw MTronException.of("no message provided: %s", this.vid());
            LOG.info("processed message: %s", processedMessage);
            agent.chat(processedMessage)
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
        final TextSegment embeddingString = TextSegment.from(Str.Helper.cleanString(toEmbed));
        final Response<Embedding> response = agent.embed(embeddingString);
        if (null != response.tokenUsage())
            this.logger().info("embedding token usage: %s", response.tokenUsage());

        return vec(response.content().vectorAsDoubleArray());
    }

}