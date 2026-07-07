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
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.llm.CostCalculator;
import studio.phaseshift.metatron.isa.llm.LLMFactory;
import studio.phaseshift.metatron.isa.llm.type.feature.*;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.Tokens.API_KEY;
import static studio.phaseshift.metatron.Tokens.ATTRIBUTES;
import static studio.phaseshift.metatron.Tokens.COST;
import static studio.phaseshift.metatron.Tokens.DESC;
import static studio.phaseshift.metatron.Tokens.FEATURE;
import static studio.phaseshift.metatron.Tokens.FORMAT;
import static studio.phaseshift.metatron.Tokens.NOTE;
import static studio.phaseshift.metatron.Tokens.PROMPT;
import static studio.phaseshift.metatron.Tokens.RAG;
import static studio.phaseshift.metatron.Tokens.RESPONSE;
import static studio.phaseshift.metatron.Tokens.SESSION;
import static studio.phaseshift.metatron.Tokens.SKILL;
import static studio.phaseshift.metatron.Tokens.THINK;
import static studio.phaseshift.metatron.Tokens.TO;
import static studio.phaseshift.metatron.Tokens.TOOL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import studio.phaseshift.metatron.isa.llm.type.feature.ChatFeature;
import static studio.phaseshift.metatron.isa.llm.type.feature.ChatFeature.chatFeature;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.noobjRec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.Str.str0;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.vec.type.MVec.vec;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Agent extends MRec {
    public record Provider(String name, fURI host, String apiKey) {
    }

    /**
     * Pipeline stage — each phase the agent passes through is recorded as
     * a Stage carrying the data in flight at that moment.  Available via
     * {@link #stages()} when {@code onError} fires.
     */
    public sealed interface Stage
            permits Stage.BeforeChat,
            Stage.Chat,
            Stage.PartialResponse,
            Stage.PartialThinking,
            Stage.PartialToolCall,
            Stage.BeforeToolExecution,
            Stage.ToolExecuted,
            Stage.CompleteResponse {
        record BeforeChat(String userMessage) implements Stage {
        }

        record Chat(String userMessage) implements Stage {
        }

        record PartialResponse(String text) implements Stage {
        }

        record PartialThinking(String text) implements Stage {
        }

        record PartialToolCall(String toolName) implements Stage {
        }

        record BeforeToolExecution(String toolName, String arguments) implements Stage {
        }

        record ToolExecuted(fURI toolId, String result) implements Stage {
        }

        record CompleteResponse(String text) implements Stage {
        }
    }

    // User-added system messages (via addSystemMessage).  Merged with
    // capability-generated messages during agent construction.
    private final List<String> customSystemMessages = new ArrayList<>();

    /**
     * Pipeline stage trail — accumulated as each phase completes.
     */
    private final List<Stage> stages = new ArrayList<>();

    /**
     * The current user message — single source of truth, mutable by features.
     */
    private String userMessage;
    protected final GraphittyLogger LOG = Graphitty.log(this);

    public Agent(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * The accumulated pipeline stage trail.
     */
    public List<Stage> stages() {
        return Collections.unmodifiableList(this.stages);
    }

    /**
     * The most recent stage, or null if no pipeline has run.
     */
    public Stage stage() {
        return this.stages.isEmpty() ? null : this.stages.getLast();
    }

    /**
     * Add a system message that will be included in every chat turn.
     * Mirrored to the typed table when the agent is built.
     */
    public void addSystemMessage(final String text) {
        this.customSystemMessages.add(text);
    }

    /**
     * The current user message, mutable by features in {@code onBeforeUserMessage}.
     */
    public String userMessage() {
        return this.userMessage;
    }

    public void userMessage(final String msg) {
        this.userMessage = msg;
    }

    public static Agent agent(final Rec agent) {
        return new Agent(agent.jvm(), LLM_AGENT_TID, agent.vid());
    }

    public String model() {
        return this.at(MODEL).asRec().at(NAME).uriValue().toString();
    }

    public Provider provider() {
        return new Provider(
                this.at(model(PROTOCOL)).strValue(),
                this.at(model(HOST)).uriValue(),
                this.at(model(API_KEY)).orElse(str("")).strValue());
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
        final Lst features = this.features();
        if (features == null || features.isEmpty()) return Optional.empty();
        for (final Obj f : features.lstValue()) {
            if (f instanceof Rec rec && rec.has(uri(feature))) return Optional.of((T) rec.at(uri(feature)).autoResolve(this));
            if (f instanceof Feature feat && feat.tid().toString().contains(feature)) return Optional.of((T) f);
        }
        return Optional.empty();
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

    public Lst features() {
        return this.at(feat()).orElse(lst());
    }

    /**
     * Resolve a feature Rec through the Type system.  The Rec's TID is the
     * feature type's VID.  The ISA's Type constructor creates the Feature.
     */
    private Feature resolveFeature(final Rec rec) {
        if (rec.tid() == null) return null;
        try {
            final Obj isaObj = Router.readFromSpace(rec.tid().retract(2));
            if (isaObj instanceof Space isa) {
                final Lst types = isa.at(uri(TYPE)).asLst();
                for (final Obj t : types.elements().toList()) {
                    if (t.vid() != null && t.vid().equals(rec.tid()) && t.isType()) {
                        final Obj instance = t.asType().constructor().apply(rec);
                        if (instance instanceof Feature f)
                            return f;
                    }
                }
            }
        } catch (final Exception e) {
            LOG.debug("feature resolution failed for %s: %s", rec.tid(), e.getMessage());
        }
        return null;
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

    static fURI model(final String... segments) {
        fURI path = f(MODEL);
        for (final String segment : segments)
            path = path.extend(segment);
        return path;
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
            // Collect features from the Lst — each is already constructed
            // by the Type system.
            final List<Feature> features = new ArrayList<>();
            final List<Feature> chatFeatures = new ArrayList<>();
            final Lst featureList = this.features();
            LOG.debug("feature list: %s entries", featureList.lstValue().size());
            for (final Obj entry : featureList.lstValue()) {
                LOG.debug("feature entry: %s [tid=%s, isFeature=%s, isRec=%s]",
                        entry.getClass().getSimpleName(), entry.tid(),
                        entry instanceof Feature, entry.isRec());
                if (entry instanceof Feature feat) {
                    features.add(feat);
                    final boolean isChat = entry.tid().equals(LLM_CHAT_FEATURE_TID);
                    LOG.debug("  -> Feature: %s, tid=%s, LLM_CHAT_FEATURE_TID=%s, match=%s",
                            feat.getClass().getSimpleName(), entry.tid(),
                            LLM_CHAT_FEATURE_TID, isChat);
                    if (isChat) {
                        chatFeatures.add(feat);
                        LOG.debug("  -> added to chatFeatures (size=%d)", chatFeatures.size());
                    }
                } else if (entry.isRec()) {
                    final Feature f = resolveFeature(entry.asRec());
                    if (f != null) features.add(f);
                }
            }
            LOG.debug("features=%d chatFeatures=%d", features.size(), chatFeatures.size());
            if (!chatFeatures.isEmpty()) {
                final Feature chatFeat = chatFeatures.getFirst();
                LOG.debug("chatFeature keys: %s", chatFeat.jvm().keySet());
                LOG.debug("chatFeature JVM has MODEL=%s", chatFeat.jvm().containsKey(uri(MODEL)));
            }

            if (message.isBlank())
                throw MTronException.of("no message provided: %s", this.vid());

            // Fire onBeforeChat FIRST — features may prepare state (tools, etc.)
            // that the service builder reads.
            this.userMessage = message;
            this.stages.clear();
            Obj shortCircuit = noobj();
            for (final Feature f : features) {
                final Obj hook_l = f.at(uri("onBeforeChat"));
                if (hook_l.isNoObj())
                    shortCircuit = f.onBeforeChat(this);          // Java default
                else
                    shortCircuit = hook_l.apply(this);            // JVM instLambda — Agent is lhs
                if (!shortCircuit.isNoObj()) {
                    this.stages.add(new Stage.BeforeChat(this.userMessage));
                    LOG.info("feature %s short-circuited: %s", f.getClass().getSimpleName(), shortCircuit);
                    return shortCircuit;
                }
            }
            this.stages.add(new Stage.BeforeChat(this.userMessage));

            // Now build the service — features have registered their wiring
            final AiServices<AgentServices> service = AiServices.builder(AgentServices.class);
            for (final Feature f : features) {
                if (f instanceof ToolFeature tf) {
                    if (tf.toolSpecs() != null && !tf.toolSpecs().isEmpty())
                        service.tools(tf.toolSpecs());
                    if (tf.mcp() != null)
                        service.toolProvider(tf.mcp());
                }
                if (f instanceof SessionFeature sf && sf.chatMemory() != null)
                    service.chatMemory(sf.chatMemory())
                            .storeRetrievedContentInChatMemory(true);
                if (f instanceof SkillFeature sf && sf.toolProvider() != null)
                    service.toolProvider(sf.toolProvider());
                if (f instanceof SystemFeature sf && sf.systemMessage() != null)
                    service.systemMessage(sf.systemMessage());
            }

            final ChatFeature cf = chatFeature(chatFeatures.getFirst());
            LOG.debug("chatFeature wrapper: jvm=%s, at(MODEL)=%s, at(\"model\")=%s",
                    cf.jvm().keySet(), cf.at(MODEL), cf.at("model"));
            final AgentServices agent = (this.has(DESC) && !this.at(DESC).strValue().isBlank() ?
                    service.systemMessageTransformer((current, content) -> (this.at(DESC).orElse(str0()).strValue() + "\n\n" + (current != null ? current : "")).trim()) :
                    service).streamingChatModel(LLMFactory.createChatInteraction(this, cf)).build();

            LOG.info("processed message: %s", this.userMessage);
            this.stages.add(new Stage.Chat(this.userMessage));
            agent.chat(this.userMessage)
                    .onToolExecuted(tool -> {
                        this.stages.add(new Stage.ToolExecuted(f(tool.request().name()), tool.result()));
                        isTooling.set(false);
                        final Rec toolRec = rec(uri(NAME), str(tool.request().name()),
                                uri(TOOL_ARGUMENTS), str(tool.request().arguments()),
                                uri(RESULT), str(tool.result()));
                        features.forEach(f -> f.onToolExecuted(this, toolRec));
                    })
                    .onCompleteResponse(c -> {
                        this.stages.add(new Stage.CompleteResponse(c.aiMessage().text()));
                        latch.countDown();
                        isResponding.set(false);
                        Router.global().stats().ioStats().incrBytesRecv(c.aiMessage().text().getBytes().length);
                        this.recordResponse(str(c.aiMessage().text()), !responseFormat.isNoObj() && !responseFormat.isEmpty());
                        this.logger().none("\n");
                        features.forEach(f -> f.onCompleteResponse(this, str(c.aiMessage().text())));
                    })
                    .onPartialToolCall(partialToolCall -> {
                        this.stages.add(new Stage.PartialToolCall(partialToolCall.name()));
                        if (!isTooling.getAndSet(true)) {
                            this.logger().none(Graphitty.sillyPrint("tooling...\n", true, true));
                            this.logger().none("\t{{y}}partial{{X}}: {{b}}%s{{g}}({{b}}%s{{g}}){{X}}\n", partialToolCall.name(), partialToolCall.partialArguments());
                        }
                        features.forEach(f -> f.onPartialToolCall(this, noobj()));
                    })
                    .onPartialResponse(s -> {
                        this.stages.add(new Stage.PartialResponse(s));
                        if (!isResponding.getAndSet(true))
                            this.logger().none(Graphitty.sillyPrint("\nresponding...", true, true));
                        Router.global().stats().ioStats().incrBytesRecv(s.getBytes().length);
                        response.append(s);
                        features.forEach(f -> f.onPartialResponse(this, str(s)));
                    })
                    .onPartialThinking(t -> {
                        this.stages.add(new Stage.PartialThinking(t.text()));
                        if (this.has(feat(THINK)) && !isThinking.getAndSet(true))
                            this.logger().none(Graphitty.sillyPrint("thinking...\n", true, true));
                        this.applyThoughtInstruction(str(t.text()));
                        Router.global().stats().ioStats().incrBytesRecv(t.text().getBytes().length);
                        features.forEach(f -> f.onPartialThinking(this, str(t.text())));
                    })
                    .onError(e -> {
                        isError.set(MTronException.of("error at %s: %s", this.stage(), e));
                        features.forEach(f -> f.onError(this, null));
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
        final EmbeddingModel agent = LLMFactory.createEmbeddingInteraction(Model.model(this.at(MODEL).asRec()));
        if (this.cost().isPresent())
            agent.addListener(new CostCalculator(this.cost().get()));
        final TextSegment embeddingString = TextSegment.from(Str.Helper.cleanString(toEmbed));
        final Response<Embedding> response = agent.embed(embeddingString);
        if (null != response.tokenUsage())
            this.logger().info("embedding token usage: %s", response.tokenUsage());

        return vec(response.content().vectorAsDoubleArray());
    }

}