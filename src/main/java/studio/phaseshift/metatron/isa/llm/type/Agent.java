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
import studio.phaseshift.metatron.isa.llm.CostCalculator;
import studio.phaseshift.metatron.isa.llm.LLMFactory;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_MILLIS_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MILLIS_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.str0;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst0;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.vec.type.MVec.vec;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Agent extends MRec {

    private final List<String> systemMessages = new ArrayList<>();

    /**
     * The current user message — single source of truth, mutable by features.
     */
    private String userMessage;
    protected final GraphittyLogger LOG = Graphitty.log(this);

    public Agent(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    // ── System messages ────────────────────────────────────────────

    public void addSystemMessage(final String text) {
        this.systemMessages.add(text);
    }

    public List<String> getSystemMessages() {
        return this.systemMessages;
    }

    // ── User message ───────────────────────────────────────────────

    public String userMessage() {
        return this.userMessage;
    }

    public void userMessage(final String msg) {
        this.userMessage = msg;
    }

    // ── Factory ────────────────────────────────────────────────────

    public static Agent agent(final Rec agent) {
        return new Agent(agent.jvm(), LLM_AGENT_TID, agent.vid());
    }

    // ── Feature query (generic, no feature is privileged) ──────────

    public boolean hasFeature(final fURI feature) {
        return this.at(FEATURE).orElse(lst0()).stream().anyMatch(f -> f.typeId().test(feature));
    }

    public Obj feature(final String feature) {
        return objs(this.features().elements().filter(f -> f.typeId().name().toLowerCase().contains(feature)));
    }

    public Lst features() {
        return this.at(feat()).orElse(lst());
    }

    // ── Path builders ──────────────────────────────────────────────

    /**
     * Feature configuration namespace: {@code /feature/...}
     */
    public static fURI feat(final String... segments) {
        fURI path = f(FEATURE);
        for (final String segment : segments)
            path = path.extend(segment);
        return path;
    }

    /**
     * Result blackboard namespace: {@code /result/...}
     */
    public static fURI res(final String... segments) {
        fURI path = f(RESULT);
        for (final String segment : segments)
            path = path.extend(segment);
        return path;
    }

    /**
     * Matches {@code <<TYPE:KEY>>...<</TYPE:KEY>>} blocks for LLM-to-blackboard signaling.
     */
    private static final Pattern MTRON_BLOCK =
            Pattern.compile("<<(\\w+):(\\w+)>>\\s*(.+?)\\s*<</\\1:\\2>>\\s*$", Pattern.DOTALL);

    /**
     * Maps block tag names to MIME types for deserialization.
     */
    private static MIME.MIMEType mimeForTag(final String tag) {
        return switch (tag) {
            case "mtron" -> MIME.MIMEType.APPLICATION_MTRON;
            case "json" -> MIME.MIMEType.APPLICATION_JSON;
            case "html" -> MIME.MIMEType.TEXT_HTML;
            case "md" -> MIME.MIMEType.TEXT_MARKDOWN;
            case "xml" -> MIME.MIMEType.APPLICATION_XML;
            case "txt", "plain" -> MIME.MIMEType.TEXT_PLAIN;
            case "bson" -> MIME.MIMEType.APPLICATION_BSON;
            default -> MIME.MIMEType.APPLICATION_MTRON;
        };
    }

    // ── Hook dispatch ──────────────────────────────────────────────

    /**
     * Dispatch a hook by JVM key to a feature.  If the feature has an inst
     * at {@code hookKey}, it is evaluated with {@code args} bound and the
     * Agent as lhs.  If the feature lacks the hook, the noobj chain is a
     * silent no-op ({@code noobj().args(...).apply(this) → noobj}).
     */
    private void dispatchHook(final Rec feature, final String hookKey, final Obj... args) {
        try {
            feature.at(uri(hookKey)).asInst().args(lst(args)).apply(this);
        } catch (final Exception e) {
            LOG.error(e);
            if (feature instanceof studio.phaseshift.metatron.isa.llm.type.feature.Feature) {
                ((studio.phaseshift.metatron.isa.llm.type.feature.Feature) feature).onError(this, fail(e));
            } else if (feature.has("onError")) {
                feature.at("onError").apply(fail(e).caught());
            }
        }
    }

    // ── Chat ───────────────────────────────────────────────────────

    public Obj chat(final String message) {
        return this.chat(message, noobjRec());
    }

    public Obj chat(final String message, final Rec responseFormat) {
        final StringBuilder response = new StringBuilder();
        Router.global().stats().ioStats().incrBytesSent(message.getBytes().length);
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean isTooling = new AtomicBoolean(false);
        final AtomicReference<MTronException> isError = new AtomicReference<>();
        final long startNanos = System.nanoTime();

        try {
            final List<Obj> features = this.features().lstValue();
            if (message.isBlank())
                throw MTronException.of("no message provided: %s", this.vid());

            // ── Phase 1: onBeforeChat — features push state to Agent blackboard ──
            this.userMessage = message;
            // Clear result blackboard for this execution
            this.at(res(CHAT), noobj(), MUTABLE);
            this.at(res(COST), noobj(), MUTABLE);
            this.at(res("stages"), noobj(), MUTABLE);
            this.at(res(ERROR), noobj(), MUTABLE);

            for (final Obj f : features) {
                final Obj result = f instanceof studio.phaseshift.metatron.isa.llm.type.feature.Feature ?
                        ((studio.phaseshift.metatron.isa.llm.type.feature.Feature) f).onBeforeChat(this) :
                        ((Poly) f).at(uri(ON_BEFORE_CHAT)).apply(this);
                if (!result.isNoObj()) {
                    LOG.info("feature short-circuited: %s", result);
                    return result;
                }
            }
            this.feature(CHAT).ifPresent(chat -> chat.asRec().at(FORMAT, (responseFormat.isNoObj() || responseFormat.asRec().isEmpty()) ? noobj() : responseFormat, MUTABLE));
            // ── Phase 2: Build LC4j service from Agent's own JVM state ──
            final AiServices<AgentServices> service = AiServices.builder(AgentServices.class);
            AgentUtility.buildService(this, service);
            final AgentServices agent = (this.has(DESC) && !this.at(DESC).strValue().isBlank() ?
                    service.systemMessageTransformer((current, content) ->
                            (this.at(DESC).orElse(str0()).strValue() + "\n\n" +
                             (current != null ? current : "")).trim()) :
                    service).streamingChatModel(AgentUtility.createChatModel(this)).build();

            // ── Phase 3: Stream — write events to result blackboard, dispatch hooks ──
            LOG.info("processed message: %s %s", this.userMessage, this.feature(CHAT).asRec().at(FORMAT).orElse(rec(uri(FORMAT), uri("none"))));
            agent.chat(this.userMessage)
                    .onToolExecuted(tool -> {
                        isTooling.set(false);
                        final Rec toolRec = rec(
                                uri(NAME), str(tool.request().name()),
                                uri(TOOL_ARGUMENTS), str(tool.request().arguments()),
                                uri(RESULT), str(tool.result()));
                        this.at(res("tool_executed"), toolRec, MUTABLE);
                        features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f.asRec(), ON_TOOL_EXECUTED, toolRec));
                    })
                    .onPartialToolCall(partialToolCall -> {
                        if (!isTooling.getAndSet(true)) {
                            this.logger().none(Graphitty.sillyPrint("tooling...\n", true, true));
                            this.logger().none("\t{{y}}partial{{X}}: {{b}}%s{{g}}({{b}}%s{{g}}){{X}}\n",
                                    partialToolCall.name(), partialToolCall.partialArguments());
                        }
                        features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_PARTIAL_TOOL_CALL));
                    })
                    .onPartialResponse(s -> {
                        Router.global().stats().ioStats().incrBytesRecv(s.getBytes().length);
                        response.append(s);
                        features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_PARTIAL_RESPONSE, str(s)));
                    })
                    .onPartialThinking(t -> {
                        Router.global().stats().ioStats().incrBytesRecv(t.text().getBytes().length);
                        features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_PARTIAL_THINKING, str(t.text())));
                    })
                    .onError(e -> {
                        LOG.error(e);
                        isError.set(MTronException.of("error during chat: %s", e));
                        this.at(res(ERROR), str(e.getMessage()), MUTABLE);
                        features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, "onError"));
                        latch.countDown();
                    }).onCompleteResponse(c -> {
                        final String fullText = null == c.aiMessage().text() ? "" : c.aiMessage().text();
                        Router.global().stats().ioStats().incrBytesRecv(fullText.getBytes().length);

                        // Parse response format if requested
                        final boolean formatted = !responseFormat.isNoObj();
                        final Obj chatResult;
                        if (formatted) {
                            chatResult = ObjSimpleJSONSerializer.single().inputBytes(fullText);
                        } else {
                            // Parse <<TYPE:KEY>>...<</TYPE:KEY>> blocks into res(KEY), strip from chat
                            final Matcher blockMatcher = MTRON_BLOCK.matcher(fullText);
                            final StringBuilder cleaned = new StringBuilder(fullText);
                            int stripped = 0;
                            while (blockMatcher.find()) {
                                final String tag = blockMatcher.group(1);
                                final String key = blockMatcher.group(2);
                                final String body = blockMatcher.group(3);
                                try {
                                    final MIME.MIMEType mime = mimeForTag(tag);
                                    final Obj parsed = mime.fromBytes(body.getBytes(StandardCharsets.UTF_8));
                                    this.at(res(key), parsed, MUTABLE);
                                    // Strip block from visible text
                                    final int start = blockMatcher.start() - stripped;
                                    final int end = blockMatcher.end() - stripped;
                                    cleaned.delete(start, end);
                                    stripped += (end - start);
                                } catch (final Exception e) {
                                    LOG.warn("failed to parse <<%s:%s>> block: %s", tag, key, e.getMessage());
                                }
                            }
                            final String cleanText = cleaned.toString().stripTrailing();
                            chatResult = str(cleanText);
                        }
                        //if (!cleanText.equals(fullText))
                        this.at(res(CHAT), chatResult);
                        //this.at(res(CHAT), cleanText.isBlank() ? chatResult : str(cleanText), MUTABLE);
                        // Elapsed time — written before hook dispatch so features can read it
                        final long elapsed = (System.nanoTime() - startNanos) / 1_000_000;
                        this.at(res(TIME), mathInstSet.normalizeTime(real((double) elapsed, MATH_MILLIS_TID, null)), MUTABLE);
                        this.logger().none("\n");
                        features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_COMPLETE_RESPONSE, chatResult));
                        // Signal main thread AFTER all blackboard writes complete
                        latch.countDown();
                    }).start();
            latch.await();
            if (null != isError.get())
                throw isError.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw MTronException.of(e);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }

        // ── Phase 4: Assemble result Rec from blackboard ──
        final Map<Obj, Obj> resultMap = new LinkedHashMap<>();
        final Obj chatResult = this.at(res(CHAT));
        resultMap.put(uri(CHAT), chatResult.isNoObj() ? str(response.toString()) : chatResult);
        resultMap.put(uri(TIME), this.at(res(TIME)));
        // if (!this.at(res(COST)).isNoObj()) resultMap.put(uri(COST), this.at(res(COST)));
        if (!this.at(res("stages")).isNoObj()) resultMap.put(uri("stages"), this.at(res("stages")));
        resultMap.put(uri(ERROR), this.at(res(ERROR)));
        if (!this.at(res(AUDIT)).isNoObj()) resultMap.put(uri(AUDIT), this.at(res(AUDIT)));
        if (!this.at(res("loop_results")).isNoObj()) resultMap.put(uri("loop_results"), this.at(res("loop_results")));
        return rec(resultMap, LLM_CHAT_RESULT_TID, null);
    }

    // ── Embed ──────────────────────────────────────────────────────

    public Lst embed(final Obj toEmbed) {
        final EmbeddingModel agent = LLMFactory.createEmbeddingInteraction(Model.model(this.at(MODEL).asRec()));
        final Obj costObj = this.at(feat(COST));
        if (!costObj.isNoObj())
            agent.addListener(new CostCalculator(costObj.autoResolve(this).asRec()));
        final TextSegment embeddingString = TextSegment.from(Str.Helper.cleanString(toEmbed));
        final Response<Embedding> response = agent.embed(embeddingString);
        if (null != response.tokenUsage())
            this.logger().info("embedding token usage: %s", response.tokenUsage());

        return vec(response.content().vectorAsDoubleArray());
    }

}
