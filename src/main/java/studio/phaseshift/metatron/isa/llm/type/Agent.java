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
import studio.phaseshift.metatron.isa.llm.type.feature.*;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_AGENT_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_CHAT_RESULT_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_MILLIS_TID;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.str0;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.vec.type.MVec.vec;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Agent extends MRec {

    private final List<String> systemMessages = new ArrayList<>();
    private final AtomicReference<Tuple.Pair<fURI, fURI>> currentHook = new AtomicReference<>(null);
    final AtomicBoolean interrupt = new AtomicBoolean(false);
    final AtomicBoolean first = new AtomicBoolean(true);
    /**
     * The current user message — single source of truth, mutable by features.
     */
    private String userMessage;
    protected final GraphittyLogger LOG = Graphitty.log(this);


    public Agent(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(new ConcurrentHashMap<>(jvm), tid, vid);
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

    public static Agent agent(final Rec config) {
        return new Agent(config.jvm(), LLM_AGENT_TID, config.vid());
    }

    // ── Feature query (generic, no feature is privileged) ──────────

    public boolean hasFeature(final String feature) {
        return this.features().elements().anyMatch(f -> f.typeId().name().toLowerCase().contains(feature));
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
            if (hookKey.equals(ON_AGENT_CTOR) || feature.at(ACTIVE).orElse(BOOL_TRUE).boolValue()) {
                if (!hookKey.equals(ON_ERROR))
                    this.currentHook.set(Tuple.Pair.with(feature.tid(), f(hookKey)));
                feature.at(uri(hookKey)).asInst().args(lst(args)).apply(this);
            } else {
                LOG.debug("skipping inactive feature: [%s][%s]", feature.tid(), hookKey);
            }
        } catch (final Exception e) {
            LOG.error(e);
            if (feature.has(ON_ERROR))
                feature.at(ON_ERROR).apply(fail(e).caught());
        }
    }

    public void interrupt() {
        this.interrupt.set(true);
    }

    // ── Chat ───────────────────────────────────────────────────────

    public Obj chat(final String message) {
        return this.chat(message, noobjRec());
    }

    public Obj chat(final String message, final Rec responseFormat) {
        // final StringBuilder response = new StringBuilder();
        this.interrupt.set(false);
        if (this.first.getAndSet(false))
            this.features().elements().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_AGENT_CTOR, this));
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

            for (final Obj feat : features) {
                final Obj result = feat instanceof Feature ?
                        ((Feature) feat).onBeforeChat(this) :
                        feat.asPoly().at(uri(ON_BEFORE_CHAT)).apply(this);
                if (!result.isNoObj()) {
                    LOG.info("feature short-circuited: %s", result);
                    return result;
                }
            }
            this.feature(CHAT).ifPresent(chat -> chat.asRec().at(FORMAT, (responseFormat.isNoObj() || responseFormat.asRec().isEmpty()) ? noobj() : responseFormat, MUTABLE));
            // ── Phase 2: Build LC4j service from Agent's own JVM state ──
            final AiServices<AgentServices> service = AiServices.builder(AgentServices.class);
            // AgentUtility.buildService(this, service);
            //////////////////////////////////////////////////////////////////////////////////
            // ADD ANOTHER FEATURE HOOK -- onSetup
            final Obj chatFeature = this.feature(CHAT);
            if (chatFeature.isNoObj())
                throw MTronException.of("agent has no chat feature: %s", this.vidOrTid());
            final Rec chat = chatFeature.asRec();
            if (this.hasFeature(SESSION))
                SessionFeature.buildSession(this, service);
            if (this.hasFeature(SKILL))
                SkillFeature.buildSkills(this, service);
            if (this.hasFeature(TOOL))
                ToolFeature.buildTools(this, service);
            if (this.hasFeature(SYSTEM))
                SystemFeature.buildSystemMessage(this, service);
            //////////////////////////////////////////////////////////////////////////////////
            final AgentServices agent = (this.has(DESC) && !this.at(DESC).strValue().isBlank() ?
                    service.systemMessageTransformer((current, content) ->
                            (this.at(DESC).orElse(str0()).strValue() + "\n\n" +
                                    (current != null ? current : "")).trim()) : service)
                    .streamingChatModel(LLMFactory.createChatInteraction(this,
                            chat.at(uri(MODEL)),
                            chat.at(uri(RESPONSE)),
                            chat.at(uri(FORMAT)))).build();
            // ── Phase 3: Stream — write events to result blackboard, dispatch hooks ──
            LOG.info("processed message: %s %s", this.userMessage, this.feature(CHAT).asRec().at(FORMAT).orElse(rec(uri(FORMAT), uri("none"))));
            agent.chat(this.userMessage)
                    .onToolExecuted(tool -> {
                        if (this.interrupt.get()) latch.countDown();
                        isTooling.set(false);
                        final Rec toolRec = rec(
                                uri(NAME), str(tool.request().name()),
                                uri(TOOL_ARGUMENTS), str(tool.request().arguments()),
                                uri(RESULT), str(tool.result()));
                        this.at(res("tool_executed"), toolRec, MUTABLE);
                        features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_TOOL_EXECUTED, toolRec));
                    })
                    .onPartialToolCall(partialToolCall -> {
                        if (this.interrupt.get()) {
                            latch.countDown();
                            return;
                        }
                        if (!isTooling.getAndSet(true)) {
                            this.logger().none(Graphitty.sillyPrint("tooling...\n", true, true));
                            this.logger().none("\t{{y}}partial{{X}}: {{b}}%s{{g}}({{b}}%s{{g}}){{X}}\n",
                                    partialToolCall.name(), partialToolCall.partialArguments());
                        }
                        features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_PARTIAL_TOOL_CALL));
                    })
                    .onPartialResponse(s -> {
                        if (this.interrupt.get()) {
                            latch.countDown();
                            return;
                        }
                        Router.global().stats().ioStats().incrBytesRecv(s.getBytes().length);
                        //response.append(s);
                        features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_PARTIAL_RESPONSE, str(s)));
                    })
                    .onPartialThinking(t -> {
                        if (this.interrupt.get()) {
                            latch.countDown();
                            return;
                        }
                        Router.global().stats().ioStats().incrBytesRecv(t.text().getBytes().length);
                        features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_PARTIAL_THINKING, str(t.text())));
                    })
                    .onError(e -> {
                        final fURI currentFeature = this.currentHook.get().get0();
                        final fURI currentStage = this.currentHook.get().get1();
                        final String errorMessage = "[" + currentFeature + "][" + currentStage + "]";
                        LOG.error("%s: %s", errorMessage, e);
                        isError.set(MTronException.of("%s: %s", errorMessage, e));
                        this.at(res(ERROR), str(e.getMessage()), MUTABLE);
                        features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_ERROR));
                        latch.countDown();
                    }).onCompleteResponse(c -> {
                        if (this.interrupt.get()) {
                            latch.countDown();
                            return;
                        }
                        final String fullText = null == c.aiMessage().text() ? "" : c.aiMessage().text();
                        Router.global().stats().ioStats().incrBytesRecv(fullText.getBytes().length);

                        // Parse response format if requested
                        final boolean formatted = !responseFormat.isNoObj();
                        final Obj chatResult;
                        if (formatted) {
                            chatResult = ObjJSONSerializer.simple().inputBytes(fullText);
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
                        // Elapsed time — written before hook dispatch so features can read it
                        final long elapsed = (System.nanoTime() - startNanos) / 1_000_000;
                        this.at(res(TIME), mathInstSet.normalizeTime(real((double) elapsed, MATH_MILLIS_TID, null)), MUTABLE);
                        this.logger().none("\n");
                        features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_COMPLETE_RESPONSE, chatResult));
                        // Signal main thread AFTER all blackboard writes complete
                        latch.countDown();
                    }).start();
            latch.await();
            if (this.interrupt.get()) {
                final fURI currentFeature = this.currentHook.get().get0();
                final fURI currentStage = this.currentHook.get().get1();
                final String warnMessage = "[" + currentFeature + "][" + currentStage + "]";
                LOG.warn("%s: agent interrupted", warnMessage);
                throw new InterruptedException();
            }
            if (null != isError.get())
                throw isError.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return rec(mutableMap(uri(STOP), BOOL_TRUE), LLM_CHAT_RESULT_TID, null);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }

        // ── Phase 4: Assemble result Rec from blackboard ──
        final Map<Obj, Obj> resultMap = new LinkedHashMap<>();
        final Obj chatResult = this.at(res(CHAT));
        resultMap.put(uri(CHAT), chatResult);
        resultMap.put(uri(TIME), this.at(res(TIME)));
        // if (!this.at(res(COST)).isNoObj()) resultMap.put(uri(COST), this.at(res(COST)));
        if (!this.at(res("stages")).isNoObj()) resultMap.put(uri("stages"), this.at(res("stages")));
        resultMap.put(uri(ERROR), this.at(res(ERROR)));
        if (!this.at(res(AUDIT)).isNoObj()) resultMap.put(uri(AUDIT), this.at(res(AUDIT)));
        if (!this.at(res("loop_results")).isNoObj()) resultMap.put(uri("loop_results"), this.at(res("loop_results")));
        this.currentHook.set(null);
        return rec(resultMap, LLM_CHAT_RESULT_TID, null);
    }

    // ── Embed ──────────────────────────────────────────────────────

    public Lst embed(final Obj toEmbed) {
        if (this.first.getAndSet(false))
            this.features().elements().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_AGENT_CTOR, this));
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
