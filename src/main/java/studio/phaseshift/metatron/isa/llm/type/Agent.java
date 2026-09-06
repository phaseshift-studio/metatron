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
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.LLMFactory;
import studio.phaseshift.metatron.isa.llm.mToolProvider;
import studio.phaseshift.metatron.isa.llm.type.feature.*;
import studio.phaseshift.metatron.isa.llm.type.feature.Feature;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.console.StatusLine;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_MILLIS_TID;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
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

    private final AtomicReference<Tuple.Pair<fURI, fURI>> currentHook = new AtomicReference<>(null);
    final AtomicBoolean interrupt = new AtomicBoolean(false);
    final AtomicBoolean first = new AtomicBoolean(true);

    /**
     * The current user message — single source of truth, mutable by features.
     */
    private String userMessage;
    protected final GraphittyLogger LOG = Graphitty.log(this);

    /**
     * Cached recursion depth for the current {@link #chat} call.
     * Populated from {@link #depthMap} at the start of {@code chat()};
     * returned by {@link #chatDepth()}.
     */
    private int currentDepth = 0;

    int currentChatId = 0;

    /**
     * The chat_result::T being assembled for the current chat — features mutate it in onCompleteResponse.
     */
    private ChatResult currentResult;

    public void setCurrentChatId(final int chatId) {
        this.currentChatId = chatId;
    }

    /**
     * Per-session recursion depth counter, stored outside any single
     * {@link Agent} instance so that {@link #agent(Rec)} wrappers share
     * the same counter.  Keyed by session VID string.
     */
    private static final ConcurrentHashMap<String, AtomicInteger> depthMap = new ConcurrentHashMap<>();


    public Agent(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(new ConcurrentHashMap<>(jvm), tid, vid);
        this.validateFeatures();
    }

    /**
     * The agent is the integrator of features.  At construction it ensures
     * that every attached feature's hard dependencies
     * ({@link Feature#requires()}) are attached as well — a missing
     * dependency is a composition error (fail fast here), not a "debilitated"
     * feature discovered mid-chat.  Only features actually attached are
     * checked, so the requirement closure is validated without any
     * transitive computation.
     */
    private void validateFeatures() {
        final Set<fURI> attached = new HashSet<>();
        for (final Obj f : this.features().elements().toList())
            if (!f.isNoObj())
                attached.add(f.typeId());
        for (final Obj f : this.features().elements().toList()) {
            if (!(f instanceof Feature feature) || feature.requires().isEmpty())
                continue;
            for (final fURI required : feature.requires())
                if (!attached.contains(required))
                    throw MTronException.of("{{b}}%s{{X}} requires {{b}}%s{{X}} to function properly", f.typeId(), required);
        }
    }

    // ── User message ───────────────────────────────────────────────

    public String userMessage() {
        return this.userMessage;
    }

    public void userMessage(final String msg) {
        this.userMessage = msg;
    }

    /**
     * Returns the current chat recursion depth for this agent instance.
     * Populated from the session-keyed {@link #depthMap} at the start of
     * {@link #chat(String, Rec)}.  0 = idle, 1 = top-level, 2+ = recursive.
     */
    public int chatDepth() {
        return this.currentDepth;
    }

    /**
     * Returns the execution identifier for the current chat call
     * (monotonic counter per session).  Set by {@code SessionFeature.onBeforeChat}.
     */
    public int chatId() {
        return this.currentChatId;
    }

    /**
     * Resolve the session VID from this agent's {@code session_feature} config.
     */
    private fURI resolveSessionVID() {
        if (this.hasFeature(LLM_MESSAGE_FEATURE_TID)) {
            final Obj sessionFeature = this.feature(LLM_MESSAGE_FEATURE_TID);
            if (!sessionFeature.isNoObj() && sessionFeature.isRec()) {
                final Obj sessionField = sessionFeature.asRec().at(uri(SESSION));
                if (sessionField.isUri())
                    return sessionField.uriValue();
            }
        }
        return null;
    }

    // ── Factory ────────────────────────────────────────────────────

    public static Agent agent(final Rec config) {
        return new Agent(config.jvm(), LLM_AGENT_TID, config.vid());
    }

    // ── Mini-task helper ────────────────────────────────────────────

    /**
     * Utility methods for delegating a one-off "mini-task" to a translator agent.
     */
    public static class Helper {
        private Helper() {
            // do nothing
        }

        /**
         * Direct a dedicated translator agent (a single {@link ChatFeature} over the
         * given model) to perform a one-off task and return its {@link ChatResult}.
         * <p>
         * The translator agent is constructed fresh per call and executes synchronously —
         * threading is the caller's concern.  If an async (or fire-and-forget) invocation
         * is desired, wrap this call in a {@code CoreThread} / {@code virtual} as needed.
         *
         * @param model  the {@code model::T} the translator agent should use
         * @param prompt the task instruction sent to the translator agent
         * @return the resulting {@code chat_result::T} as a {@link ChatResult}
         */
        public static ChatResult miniChat(final String agentName, final mModel model, final String prompt) {
            final Agent chatter = new Agent(mutableMap(
                    uri(NAME), str(agentName),
                    uri(FEATURE), lst(new ChatFeature(mutableMap(
                            uri(MODEL), model,
                            uri(RESPONSE), rec(uri(TO), noobj())),
                            LLM_CHAT_FEATURE_TID, null))), LLM_AGENT_TID, null);
            //model.logger().status(DEBUG, "mini-task launched by %s over %s", agentName, model.llm());
            return chatter.chat(prompt);
        }
    }

    // ── Feature query (generic, no feature is privileged) ──────────
    //
    // Identity is the feature's FULL tid (e.g. LLM_CHAT_FEATURE_TID) —
    // verbose on purpose: exact matching, no substring collisions.

    public boolean hasFeature(final fURI featureTid) {
        return this.features().elements().anyMatch(f -> f.typeId().equals(featureTid));
    }

    public Obj feature(final fURI featureTid) {
        return objs(this.features().elements().filter(f -> f.typeId().equals(featureTid)));
    }

    public Lst features() {
        final Lst feats = this.at(FEATURE).orElse(lst());
        // A feature entry that failed to construct (an MFail) would otherwise blow
        // up later as a raw cast (MFail not castable to Rec) with the original
        // cause lost — surface it here while we still have it at hand.
        int entry = 1;
        for (final Obj f : feats.elements().toList()) {
            if (f instanceof Fail failure)
                throw MTronException.of("agent feature entry %d failed to construct: %s", entry, failure.message());
            entry++;
        }
        return feats;
    }

    /**
     * Phase-1 {@code onBeforeChat} dispatch order, enforced regardless of the stored
     * feature-list order. Rank (lower runs first):
     * <ul>
     *   <li>0 — registrants: every other feature (publishers, Tool, Session, …) that
     *       populates the Skill/Tool gateways — they run first so the registry is complete</li>
     *   <li>1 — {@code Skill}: the composer — runs after all registrants have registered
     *       their capability skills, so the skills table it emits is complete and stable</li>
     *   <li>2 — {@code System}: the consumer — runs last, so write-on-change sees the
     *       final composed text and records exactly one ledger row for unchanged chats</li>
     * </ul>
     */
    protected static int chatPhaseRank(final Obj feat) {
        final fURI tid = feat.typeId();
        if (tid.equals(LLM_SYSTEM_FEATURE_TID))
            return 2;   // consumer: last
        if (tid.equals(LLM_SKILL_FEATURE_TID))
            return 1;   // composer: after all registrants
        return 0;       // registrants and neutral features: first
    }

    /**
     * The agent's features in Phase-1 chat order — a stable sort by {@link #chatPhaseRank},
     * so contributors run before the gatekeepers they register onto, Skill before Tool, and
     * consumers last.  Preserves the original relative order within each phase.
     */
    protected List<Obj> orderedFeatures() {
        return this.features().lstValue().stream()
                .sorted(Comparator.comparingInt(Agent::chatPhaseRank))
                .toList();
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
     * Matches {@code <<TYPE:KEY>>...<</TYPE:KEY>>} blocks for LLM-to-feature signaling.
     */
    private static final Pattern MTRON_BLOCK =
            Pattern.compile("<<(\\w+):(\\w+)>>\\s*(.+?)\\s*<</\\1:\\2>>", Pattern.DOTALL);

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
                StatusLine.message(str("current llm stage: [%s][%s]".formatted(feature.tid(), hookKey)));
                final Obj hook = feature.at(uri(hookKey));
                (hook.isInst() ? hook.asInst().args(lst(args)) : hook).apply(this);
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

    public ChatResult chat(final String message) {
        return this.chat(message, noobjRec());
    }

    public ChatResult chat(final String message, final Rec responseFormat) {
        this.interrupt.set(false);
        final fURI sessionVID = resolveSessionVID();
        final String depthKey = sessionVID != null ? sessionVID.toString() : this.tid().toString();
        final AtomicInteger counter = depthMap.computeIfAbsent(depthKey, k -> new AtomicInteger(0));
        this.currentDepth = counter.incrementAndGet();
        final CommonUtil.Spinner waiting =
                Console.isConsoleOwned() && !this.feature(LLM_CHAT_FEATURE_TID).<ChatFeature>as().at(MODEL).asRec().at(LLM).toCleanString().equals("human:latest") ?
                        CommonUtil.spinner("initializing agent...", true) :
                        null;
        try {
            if (this.first.getAndSet(false))
                this.features().elements().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_AGENT_CTOR, this));
            Router.global().stats().ioStats().incrBytesSent(message.getBytes().length);
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicBoolean isTooling = new AtomicBoolean(false);
            final AtomicReference<MTronException> isError = new AtomicReference<>();
            final long startNanos = System.nanoTime();
            try {
                if (message.isBlank())
                    throw MTronException.of("no message provided: %s", this.vid());

                // ── Phase 1: onBeforeChat — features prepare per-chat state ──
                // Dispatch in contributor→Skill→Tool→consumer order regardless of the
                // stored feature-list order, so the gatekeepers compose from a fully
                // registered registry and System's write-on-change sees the final text.
                final List<Obj> features = this.orderedFeatures();
                this.userMessage = message;

                for (final Obj feat : features) {
                    final Obj result = feat instanceof Feature ?
                            ((Feature) feat).onBeforeChat(this) :
                            feat.asPoly().at(uri(ON_BEFORE_CHAT)).apply(this);
                    if (!result.isNoObj()) {
                        LOG.info("feature short-circuited: %s", result);
                        return ChatResult.chatResult().put(CHAT, result);
                    }
                }
                this.feature(LLM_CHAT_FEATURE_TID).ifPresent(chat -> chat.asRec().at(FORMAT, (responseFormat.isNoObj() || responseFormat.asRec().isEmpty()) ? noobj() : responseFormat, MUTABLE));
                // ── Phase 2: Build LC4j service from Agent's own JVM state ──
                final AiServices<AgentServices> service = AiServices.builder(AgentServices.class)
                        //.executeToolsConcurrently(ThreadExecutor.instance())
                        //.maxToolCallingRoundTrips(10)
                        .storeRetrievedContentInChatMemory(true)
                        .toolProvider(this.hasFeature(LLM_TOOL_FEATURE_TID) ? this.feature(LLM_TOOL_FEATURE_TID).<ToolFeature>as().getToolProvider() : new mToolProvider())
                        .toolExecutionErrorHandler((error, context) -> {
                            if (this.has(TOOL) && this.feature(LLM_TOOL_FEATURE_TID).asRec().has(ON_ERROR)) {
                                this.feature(LLM_TOOL_FEATURE_TID).asRec().at(ON_ERROR).asInst().args(lst(this, fail(error)));
                            } else {
                                LOG.error(error);
                            }
                            return new ToolErrorHandlerResult(error.getMessage());
                        });
                //.storeRetrievedContentInChatMemory(true);
                // AgentUtility.buildService(this, service);
                //////////////////////////////////////////////////////////////////////////////////
                // ADD ANOTHER FEATURE HOOK -- onSetup
                final Obj chatFeature = this.feature(LLM_CHAT_FEATURE_TID);
                if (chatFeature.isNoObj())
                    throw MTronException.of("agent has no chat feature: %s", this.vidOrTid());
                final Rec chat = chatFeature.asRec();
                if (this.hasFeature(LLM_MESSAGE_FEATURE_TID))
                    MessageFeature.buildSession(this, service);
                //if (this.hasFeature(SKILL))
                //    SkillFeature.buildSkills(this, service);
                //if (this.hasFeature(TOOL))
                //    ToolFeature.buildTools(this, service);
                // this.at(feat(CONCEPT)).ifPresent(c -> ((ConceptFeature) c).build(this, service));
                //////////////////////////////////////////////////////////////////////////////////
                // The single system-message channel: SystemFeature owns the contributions
                // (features add via agent.feature(SYSTEM).<SystemFeature>as().addSystemMessage
                // during onBeforeChat, Phase 1) and composes the text here at build time.
                // Capture it now — AFTER all onBeforeChat hooks ran — then append to the
                // model's base system prompt.  SystemFeature.clearSystemMessages() runs in
                // the finally below after this chat completes.
                final String systemText = this.hasFeature(LLM_SYSTEM_FEATURE_TID)
                        ? this.feature(LLM_SYSTEM_FEATURE_TID).<SystemFeature>as().systemMessage()
                        : "";
                final AgentServices agent = service
                        .systemMessageTransformer(current -> current + systemText)
                        .streamingChatModel(LLMFactory.createChatInteraction(this,
                                chat.at(uri(MODEL)),
                                chat.at(uri(RESPONSE)),
                                chat.at(uri(FORMAT)))).build();
                // ── Phase 3: Stream — write events to result blackboard, dispatch hooks ──
                LOG.debug("processed message: %s %s", this.userMessage, this.feature(LLM_CHAT_FEATURE_TID).asRec().at(FORMAT).orElse(rec(uri(FORMAT), uri("none"))));
                spinnerMessage(waiting, "waiting for agent response...");
                agent.chat(Str.Helper.stripString(str(this.userMessage)))
                        .onToolExecuted(tool -> {
                            StatusLine.message(str("current llm stage: on_tool_execute"));
                            if (this.interrupt.get()) latch.countDown();
                            isTooling.set(false);
                            final Rec toolRec = rec(
                                    uri(NAME), str(tool.request().name()),
                                    uri(TOOL_ARGUMENTS), str(tool.request().arguments()),
                                    uri(RESULT), str(tool.result() != null ? tool.result() : ""),
                                    uri(CONTENTS), str(tool.request().id()));
                            features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_TOOL_EXECUTED, toolRec));
                        })
                        .onPartialToolCall(partialToolCall -> {
                            StatusLine.message(str("current llm stage: on_partial_tool_call"));
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
                            StatusLine.message(str("current llm stage: on_partial_response"));
                            closeSpinner(waiting);
                            if (this.interrupt.get()) {
                                latch.countDown();
                                return;
                            }
                            Router.global().stats().ioStats().incrBytesRecv(s.getBytes().length);
                            //response.append(s);
                            features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_PARTIAL_RESPONSE, str(s)));
                        })
                        .onPartialThinking(t -> {
                            StatusLine.message(str("current llm stage: on_partial_thinking"));
                            closeSpinner(waiting);
                            if (this.interrupt.get()) {
                                latch.countDown();
                                return;
                            }
                            Router.global().stats().ioStats().incrBytesRecv(t.text().getBytes().length);
                            features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_PARTIAL_THINKING, str(t.text())));
                        })
                        .onError(e -> {
                            closeSpinner(waiting);
                            final fURI currentFeature = this.currentHook.get().get0();
                            final fURI currentStage = this.currentHook.get().get1();
                            final String errorMessage = "[" + currentFeature + "][" + currentStage + "]";
                            LOG.error("%s: %s", errorMessage, e);
                            isError.set(MTronException.of("%s: %s", errorMessage, e));
                            features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_ERROR));
                            latch.countDown();
                        }).onCompleteResponse(c -> {
                            StatusLine.message(str("current llm stage: on_complete_response"));
                            closeSpinner(waiting);
                            if (this.interrupt.get()) {
                                latch.countDown();
                                return;
                            }
                            final String fullText = null == c.aiMessage().text() ? "" : c.aiMessage().text();
                            Router.global().stats().ioStats().incrBytesRecv(fullText.getBytes().length);
                            // Parse response format if requested
                            final boolean formatted = !responseFormat.isNoObj();
                            final Obj chatObj;
                            final Map<Obj, Obj> blocks = new LinkedHashMap<>();
                            if (formatted) {
                                chatObj = ObjJSONSerializer.simple().inputBytes(fullText);
                            } else {
                                // Parse <<TYPE:KEY>>...<</TYPE:KEY>> blocks into the result's
                                // blocks rec (features read them there), strip from chat
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
                                        blocks.put(uri(key), parsed);
                                        // Strip block from visible text
                                        final int start = blockMatcher.start() - stripped;
                                        final int end = blockMatcher.end() - stripped;
                                        cleaned.delete(start, end);
                                        stripped += (end - start);
                                    } catch (final Exception e) {
                                        LOG.warn("failed to parse <<%s:%s>> block: %s", tag, key, e.getMessage());
                                    }
                                }
                                chatObj = str(cleaned.toString().stripTrailing());
                            }
                            // Build the chat_result — monos inline (chat, user, time), the
                            // parsed <<TYPE:KEY>> blocks on a blocks rec; feature outputs are
                            // attached by the features themselves in their onCompleteResponse.
                            final long elapsed = (System.nanoTime() - startNanos) / 1_000_000;
                            final ChatResult result = ChatResult.chatResult()
                                    .put(CHAT, chatObj.apply(this))
                                    .put(USER, str(this.userMessage))
                                    .put(TIME, mathInstSet.normalizeTime(real((double) elapsed, MATH_MILLIS_TID, null)));
                            if (!blocks.isEmpty())
                                result.put(BLOCK, rec(blocks, null, null));
                            this.currentResult = result;
                            this.logger().none("\n");
                            features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_COMPLETE_RESPONSE, result));
                            // Signal the waiting thread after all hooks have mutated the result
                            latch.countDown();
                        }).start();
                latch.await();
                closeSpinner(waiting);
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
                return ChatResult.chatResult().put(STOP, BOOL_TRUE);
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
            // ── Phase 4: persist + return the chat_result ──
            final ChatResult result = this.currentResult;
            this.currentResult = null;
            this.currentHook.set(null);
            if (null != result) {
                this.feature(LLM_CHAT_FEATURE_TID).ifPresent(chat -> {
                    if (chat.asRec() instanceof ChatFeature cf)
                        cf.persist(this, result);
                });
                return result;
            }
            return ChatResult.chatResult();
        } finally {
            closeSpinner(waiting);
            // SystemFeature owns the per-chat system-message state — clear it so the
            // next chat re-surfaces its own system context.
            if (this.hasFeature(LLM_SYSTEM_FEATURE_TID))
                this.feature(LLM_SYSTEM_FEATURE_TID).<SystemFeature>as().clearSystemMessages();
            counter.decrementAndGet();
            this.currentDepth = 0;

        }
    }

    // ── Console spinner helpers ─

    private static void closeSpinner(final CommonUtil.Spinner spinner) {
        if (null != spinner)
            spinner.close();
    }

    private static void spinnerMessage(final CommonUtil.Spinner spinner, final String format, final Object... args) {
        if (null != spinner)
            spinner.setMessage(format, args);
    }

    // ── Embed ──────────────────────────────────────────────────────

    public Lst embed(final String toEmbed) {
        if (this.first.getAndSet(false))
            this.features().elements().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_AGENT_CTOR, this));
        final EmbeddingModel agent = LLMFactory.createEmbeddingInteraction(mModel.model(this.at(MODEL).asRec()));
       /* final Obj costObj = this.at(feat(COST));
        if (!costObj.isNoObj())
            agent.addListener(new CostCalculator(costObj.asRec().at(RATE));*/
        final TextSegment embeddingString = TextSegment.from(toEmbed);
        final Response<Embedding> response = agent.embed(embeddingString);
        if (null != response.tokenUsage())
            this.logger().info("embedding token usage: %s", response.tokenUsage());
        return vec(response.content().vectorAsDoubleArray());
    }

}
