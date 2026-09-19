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
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecution;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.LLMFactory;
import studio.phaseshift.metatron.isa.llm.WatermarkUtil;
import studio.phaseshift.metatron.isa.llm.mToolProvider;
import studio.phaseshift.metatron.isa.llm.type.feature.*;
import studio.phaseshift.metatron.isa.llm.type.feature.Feature;
import studio.phaseshift.metatron.isa.llm.type.feature.service.FrameService;
import studio.phaseshift.metatron.isa.llm.type.feature.service.MessageService;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.console.StatusLine;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_MILLIS_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.nowDatetime;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
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
    final AtomicBoolean first = new AtomicBoolean(true);
    private static final int MAX_TOOL_CALLS = -1;

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
    private ChatFrame currentResult;

    public void setCurrentChatId(final int chatId) {
        this.currentChatId = chatId;
    }

    /**
     * Per-session recursion depth counter, stored outside any single
     * {@link Agent} instance so that {@link #agent(Rec)} wrappers share
     * the same counter.  Keyed by session VID string.
     */
    private static final ConcurrentHashMap<String, AtomicInteger> depthMap = new ConcurrentHashMap<>();

    public fURI getDataPath(final fURI root) {
        return root.extend(sessionVID().name()).extend(chatId()).extend(currentDepth);
    }

    public Agent(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(new ConcurrentHashMap<>(jvm), tid, vid);
        this.at(INTERRUPT, noobj(), MUTABLE);
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
        // union of service tids offered by every attached feature — a dependency is
        // satisfiable when some provider declares the service, whatever its class.
        final Set<fURI> offered = new HashSet<>();
        for (final Obj f : this.features().elements().toList())
            if (f instanceof Feature feature)
                offered.addAll(feature.offers());
        for (final Obj f : this.features().elements().toList()) {
            if (!(f instanceof Feature feature) || feature.requires().isEmpty())
                continue;
            for (final fURI required : feature.requires())
                if (!offered.contains(required))
                    throw MTronException.of("{{b}}%s{{X}} requires service {{b}}%s{{X}} to function properly", f.typeId(), required);
        }
    }

    // ── User message ───────────────────────────────────────────────

    public String userMessage() {
        // the frame is the source of truth when a provider is attached — the prompt stamped at push
        return null != this.currentResult && null != this.currentResult.prompt()
                ? this.currentResult.prompt()
                : this.userMessage;
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
     * (monotonic counter per session).  Set by {@code advanceChatId} in the pre-chat phase;
     * the pushed frame's {@code chat_id} is the source of truth once it exists.
     */
    public int chatId() {
        if (null != this.currentResult) {
            final int frameChatId = this.currentResult.chatId();
            if (frameChatId > 0)
                return frameChatId;
        }
        return this.currentChatId;
    }

    /**
     * Resolve the session VID from this agent's message service (whatever provider is attached).
     */
    public fURI sessionVID() {
        return this.service(MessageService.class).map(MessageService::sessionVID).orElse(null);
    }

    // ── Factory ────────────────────────────────────────────────────

    public static Agent agent(final Rec config) {
        if (config instanceof Agent)
            return (Agent) config;
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
         * given model) to perform a one-off task and return its {@link ChatFrame}.
         * <p>
         * The translator agent is constructed fresh per call and executes synchronously —
         * threading is the caller's concern.  If an async (or fire-and-forget) invocation
         * is desired, wrap this call in a {@code CoreThread} / {@code virtual} as needed.
         *
         * @param model  the {@code model::T} the translator agent should use
         * @param prompt the task instruction sent to the translator agent
         * @return the resulting {@code chat_result::T} as a {@link ChatFrame}
         */
        public static ChatFrame miniChat(final String agentName, final mModel model, final String prompt) {
            final Agent chatter = new Agent(mutableMap(
                    uri(NAME), str(agentName),
                    uri(ROOT), uri(f("/sys/tmp").extend(agentName)),
                    uri(FEATURE), lst(
                            new ChatFeature(mutableMap(
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

    /**
     * Typed veil over {@link #feature(fURI)}: look the feature up by its class (class -> tid
     * via {@link Feature.Helper#tid}) and wrap the matched rec back into the Java class, so
     * callers never touch a raw {@code .as()} cast.
     */
    public <F extends Feature> Optional<F> feature(final Class<F> type) {
        final Obj found = objs(this.features().elements().filter(f -> f.typeId().equals(Feature.Helper.tid(type))));
        return found.isNoObj() ? Optional.empty() : Optional.of(Rec.wrap(found, type));
    }

    public <F extends Feature> boolean hasFeature(final Class<F> type) {
        return this.features().elements().anyMatch(f -> f.typeId().equals(Feature.Helper.tid(type)));
    }

    public <F extends Feature> F require(final Class<F> type) {
        return this.feature(type).orElseThrow(() -> MTronException.of("agent has no %s feature", type.getSimpleName()));
    }

    /**
     * Service lookup by interface — find any feature providing the given capability,
     * decoupled from the concrete feature class.  {@code instanceof}-based: whichever
     * attached feature implements the service is returned, whatever its class.
     */
    public <S> Optional<S> service(final Class<S> type) {
        return this.features().elements().filter(type::isInstance).map(type::cast).findFirst();
    }

    public <S> S requireService(final Class<S> type) {
        return this.service(type).orElseThrow(() -> MTronException.of("agent has no %s service", type.getSimpleName()));
    }

    /**
     * Service lookup by tid — find any feature offering the given service tid.
     * {@code offers()}-based: whichever attached feature declares the service tid
     * is returned, decoupled from the concrete feature class.  Non-feature providers
     * register the same tids elsewhere in the space, so this is one lookup of many.
     */
    public Obj service(final fURI serviceTid) {
        return objs(this.features().elements().filter(f -> f instanceof Feature feature && feature.offers().contains(serviceTid)));
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

    // Watermark scanning lives in {@link Watermarks} — one owner for the
    // pattern, the tag→codec mapping, the decode policy, and the stripping.

    // ── Hook dispatch ──────────────────────────────────────────────

    /**
     * Dispatch a hook by JVM key to a feature.  If the feature has an inst
     * at {@code hookKey}, it is evaluated with {@code args} bound and the
     * Agent as lhs.  If the feature lacks the hook, the noobj chain is a
     * silent no-op ({@code noobj().args(...).apply(this) → noobj}).
     *
     * @return what the hook evaluated to — dropped by every stage but
     * {@code on_tool_result}, which is the one whose value is the point
     * (see {@link #dispatchToolResult})
     */
    private Obj dispatchHook(final Rec feature, final String hookKey, final Obj... args) {
        try {
            if (hookKey.equals(ON_AGENT_CTOR) || feature.at(ACTIVE).orElse(BOOL_TRUE).boolValue()) {
                if (!hookKey.equals(ON_ERROR))
                    this.currentHook.set(Tuple.Pair.with(feature.tid(), f(hookKey)));
                //StatusLine.message(str("current llm stage: [%s][%s]".formatted(feature.tid(), hookKey)));
                final Obj hook = feature.at(uri(hookKey));
                return (hook.isInst() ? hook.asInst().args(lst(args)) : hook).apply(this);
            } else {
                LOG.debug("skipping inactive feature: [%s][%s]", feature.tid(), hookKey);
            }
        } catch (final Exception e) {
            LOG.error(e);
            if (feature.has(ON_ERROR))
                feature.at(ON_ERROR).apply(fail(e).caught());
        }
        return noobj();
    }

    /**
     * Fold the {@code on_tool_result} stage over every attached feature and return
     * the payload the model should be handed.
     *
     * <p>The one stage dispatched from outside the turn: {@code mToolExecutor} calls
     * this between the tool and the model, so the result of each hook is carried into
     * the next rather than dropped.  A feature that has nothing to add returns the
     * payload it was given, and a feature with no hook at all evaluates to
     * {@code noobj} — which is skipped, so a feature that merely exists cannot erase
     * the tool's output.
     */
    public Obj dispatchToolResult(final Obj result, final String requestId) {
        Obj payload = result;
        for (final Obj feature : this.features().elements().toList()) {
            final Obj folded = this.dispatchHook(feature.asRec(), ON_TOOL_RESULT, payload, str(requestId));
            if (!folded.isNoObj())
                payload = folded;
        }
        return payload;
    }

    public void interrupt() {
        this.pushMidChatMessage(rec(TEXT, str("agent interrupt: please return from thinking")));
        this.at(INTERRUPT, BOOL_TRUE, MUTABLE);
    }

    public boolean isInterrupted() {
        return this.load().asRec().at(INTERRUPT).booleanCheck();
    }

    /**
     * Messages a user sent while this turn was in flight.  The atomic is the
     * source of truth and the {@code message_stack} field mirrors it for
     * visibility: LC4j executes a round's tool calls concurrently, so a
     * read-then-clear on the rec field would let two parallel calls split one
     * user message between them — or deliver the same one twice.
     */
    /**
     * Offer a message to the agent mid-turn.
     *
     * <p>It is queued in the {@code MidChatFeature}'s own subspace
     * ({@code pending_messages}), not in a field here: {@link #agent(Rec)} builds a
     * fresh Agent on <em>every</em> call, so the instance that pushes a message — a
     * console line, a second {@code chat} while the first is streaming — is never the
     * instance running the turn that has to deliver it.  A field here belongs to the
     * pusher alone, and the turn reads nothing.
     */
    public void pushMidChatMessage(final Rec message) {
        if (!this.hasFeature(LLM_MIDCHAT_FEATURE_TID)) {
            LOG.warn("no mid-chat feature attached — a message sent mid-turn was dropped: %s", this.vidOrTid());
            return;
        }
        final Uri now = mathInstSet.nowDatetime();
        this.require(MidChatFeature.class).push(this, message
                .at(TIME, now)
                .at(RUNTIME, ObjmtronSerializer.parse("!math:datetime_now().minus(%s).normalize()".formatted(now))));
    }

    /**
     * The messages waiting to be read, drained.  The MidChatFeature owns them; this is
     * the agent's door onto its subspace.
     */
    public Lst popMidChatMessages() {
        return this.feature(MidChatFeature.class).map(f -> f.drain(this)).orElse(lst());
    }

    /**
     * Close the tool and mid-chat channels, and do it on a thread whose interrupt
     * flag is clear.
     *
     * <p>Both closes <em>write to the ledger</em>, and {@code Router.writeToSpace}
     * goes through a space that may do blocking IO — an interrupted thread can
     * abort that IO partway.  The damage is specific and nasty: the close writes
     * the ai message first and its results after, so an aborted close leaves an ai
     * message with fewer results than {@code tool_requests}, which every provider
     * rejects with "insufficient tool messages following tool_calls message" and
     * which makes the whole history unusable.
     *
     * <p>The ordering fix alone is not enough: the {@code catch} path re-arms the
     * flag and then the {@code finally} calls this again with it already set.  So
     * the flag is cleared for the duration of the work and restored afterwards,
     * which makes the close safe wherever it is called from.
     */
    private void closeChannels(final AtomicReference<Set<String>> orphanToolRequests) {
        final boolean interrupted = Thread.interrupted(); // clears the flag
        try {
            this.feature(ToolFeature.class).ifPresent(f -> f.handleOrphanToolRequests(this, orphanToolRequests.get()));
            this.feature(MidChatFeature.class).ifPresent(f -> f.handleOrphanMidChatMessages(this));
        } finally {
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }


    // ── Chat ───────────────────────────────────────────────────────

    public ChatFrame chat(final String message) {
        return this.chat(message, noobjRec());
    }

    public ChatFrame chat(final String message, final Rec responseFormat) {
        this.load();
        if (this.at(ACTIVE).booleanCheck() && this.hasFeature(LLM_MIDCHAT_FEATURE_TID)) {
            final MidChatFeature midchat = this.require(MidChatFeature.class);
            midchat.push(this, rec(MESSAGE, str(message), METADATA, responseFormat));
            return ChatFrame.chatFrame()
                    .put(CHAT, str("added to message stack"))
                    .put("current_stack", midchat.pendingMessages(this));
        }
        final fURI sessionVid = sessionVID();
        final String depthKey = sessionVid != null ? sessionVid.toString() : this.tid().toString();
        final AtomicInteger counter = depthMap.computeIfAbsent(depthKey, k -> new AtomicInteger(0));
        final AtomicReference<Set<String>> orphanToolRequests = new AtomicReference<>(new HashSet<>());
        final FrameService frameService = this.service(FrameService.class).orElse(null);
        this.currentDepth = counter.incrementAndGet();
        try {
            this.beginTurn(message);
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<MTronException> isError = new AtomicReference<>();
            final long startNanos = System.nanoTime();
            try {
                if (message.isBlank())
                    throw MTronException.of("no message provided: %s", this.vid());

                // ── onBeforeChat — features prepare per-chat state ──
                // Dispatch in contributor→Skill→Tool→consumer order regardless of the
                // stored feature-list order, so the gatekeepers compose from a fully
                // registered registry and System's write-on-change sees the final text.
                final List<Obj> features = this.orderedFeatures();
                this.userMessage = message;

                // ── pre-chat: advance the turn id + push the frame BEFORE onBeforeChat, so the
                //    frame is the activation record features read (prompt/chat_id/depth) ──
                this.service(MessageService.class).ifPresent(ms -> ms.advanceChatId(this));
                if (frameService instanceof AbstractFrameFeature frameFeature) {
                    frameFeature.prepare(this);
                    this.currentResult = (ChatFrame) frameService.push(ChatFrame.chatFrame().prompt(message));
                }

                for (final Obj feat : features) {
                    final Obj result = feat instanceof Feature ?
                            ((Feature) feat).onBeforeChat(this) :
                            feat.asPoly().at(uri(ON_BEFORE_CHAT)).apply(this);
                    if (!result.isNoObj()) {
                        LOG.info("feature short-circuited: %s", result);
                        return ChatFrame.chatFrame().put(CHAT, result);
                    }
                }
                this.feature(LLM_CHAT_FEATURE_TID).ifPresent(chat -> chat.asRec().at(FORMAT, (responseFormat.isNoObj() || responseFormat.asRec().isEmpty()) ? noobj() : responseFormat, MUTABLE));

                final AgentServices agent = this.buildService(features, responseFormat);

                // ── the stream — each callback is a lifecycle stage ──
                agent.chat(Str.Helper.stripString(str(this.userMessage())))
                        .onToolExecuted(tool -> this.onToolExecuted(tool, features, orphanToolRequests, latch))
                        .onPartialToolCall(p -> this.onPartialToolCall(p, features, orphanToolRequests, latch))
                        .onPartialResponse(s -> this.onPartialResponse(s, features, latch))
                        .onPartialThinking(t -> this.onPartialThinking(t, latch))
                        .onError(e -> this.onError(e, features, isError, latch))
                        .onCompleteResponse(c -> this.onCompleteResponse(c, responseFormat, features, startNanos, latch))
                        .start();
                latch.await();
                if (this.isInterrupted()) {
                    final fURI currentFeature = this.currentHook.get().get0();
                    final fURI currentStage = this.currentHook.get().get1();
                    final String warnMessage = "[" + currentFeature + "][" + currentStage + "]";
                    LOG.warn("%s: agent interrupted", warnMessage);
                    throw new InterruptedException();
                }
                if (null != isError.get())
                    throw isError.get();
            } catch (final InterruptedException e) {
                // close the channels before re-arming this thread's interrupt flag
                this.closeChannels(orphanToolRequests);
                Thread.currentThread().interrupt();
                return ChatFrame.chatFrame()
                        .put(STOP, BOOL_TRUE)
                        .put(TIME, nowDatetime())
                        .put(ERROR, fail(e).caught());
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
            // ── onCompleteResponse already assembled the result — persist + return ──
            return this.persistResult(frameService);
        } finally {
            this.closeTurn(orphanToolRequests, counter);
        }
    }

    // ── Lifecycle stages (the chat() run-loop, one method per stage) ──

    /**
     * Open the turn: mark active, clear any pending interrupt, run the one-time
     * {@code onAgentCtor} dispatch, and count the outbound bytes.
     */
    private void beginTurn(final String message) {
        this.at(ACTIVE, BOOL_TRUE, MUTABLE);
        this.at(INTERRUPT, noobj(), MUTABLE);
        if (this.first.getAndSet(false))
            this.features().elements().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_AGENT_CTOR, this));
        Router.global().stats().ioStats().incrBytesSent(message.getBytes().length);
    }

    /**
     * Build the LC4j service from the agent's own state (Phase 2) — the tool/skill/error
     * wiring, the session, the system-message channel, and the streaming chat model.
     */
    private AgentServices buildService(final List<Obj> features, final Rec responseFormat) {
        final AiServices<AgentServices> service = AiServices.builder(AgentServices.class)
                .maxToolCallingRoundTrips(MAX_TOOL_CALLS)
                .storeRetrievedContentInChatMemory(true)
                .toolProvider(this.feature(ToolFeature.class).map(ToolFeature::getToolProvider).orElseGet(mToolProvider::new))
                .toolExecutionErrorHandler((error, context) -> {
                    if (this.has(TOOL) && this.feature(LLM_TOOL_FEATURE_TID).asRec().has(ON_ERROR)) {
                        this.feature(LLM_TOOL_FEATURE_TID).asRec().at(ON_ERROR).asInst().args(lst(this, fail(error)));
                    } else {
                        LOG.error(error);
                    }
                    return new ToolErrorHandlerResult(error.getMessage());
                }).toolArgumentsErrorHandler((error, context) ->
                        new ToolErrorHandlerResult("""
                                                   provided arguments do not match tool schema: %s
                                                   """.formatted(error)))
                .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.toolExecutionResultMessage(
                        toolExecutionRequest,
                        """
                        %s tool does not exist. use list_tools() to see available tools.
                        """.formatted(toolExecutionRequest.name())));
        final Obj chatFeature = this.feature(LLM_CHAT_FEATURE_TID);
        if (chatFeature.isNoObj())
            throw MTronException.of("agent has no chat feature: %s", this.vidOrTid());
        final Rec chat = chatFeature.asRec();
        if (this.service(MessageService.class).isPresent())
            AbstractMessageFeature.buildSession(this, service);
        // The single system-message channel: SystemFeature owns the contributions and composes
        // the text here at build time (after every onBeforeChat hook ran).
        final String systemText = this.feature(SystemFeature.class).map(SystemFeature::systemMessage).orElse("");
        return service
                .systemMessageTransformer(current -> current + systemText)
                .streamingChatModel(LLMFactory.createChatInteraction(this,
                        chat.at(uri(MODEL)),
                        chat.at(uri(RESPONSE)),
                        chat.at(uri(FORMAT)))).build();
    }

    private void onToolExecuted(final ToolExecution tool, final List<Obj> features,
                                final AtomicReference<Set<String>> orphanToolRequests, final CountDownLatch latch) {
        StatusLine.message(str("\uD83D\uDD28 on_tool_execute: %s(%s) => %s".formatted(tool.request().name(), tool.request().arguments(), tool.result())));
        if (this.isInterrupted()) latch.countDown();
        final Rec toolRec = rec(
                uri(NAME), str(tool.request().name()),
                uri(TOOL_ARGUMENTS), str(tool.request().arguments()),
                uri(RESULT), str(tool.result() != null ? tool.result() : ""),
                uri(CONTENTS), str(tool.request().id()));
        features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_TOOL_EXECUTED, toolRec));
        orphanToolRequests.get().remove(tool.request().id());
    }

    private void onPartialToolCall(final PartialToolCall partialToolCall, final List<Obj> features,
                                   final AtomicReference<Set<String>> orphanToolRequests, final CountDownLatch latch) {
        StatusLine.message(str("\uD83E\uDDF0 on_partial_tool_call"));
        orphanToolRequests.get().add(partialToolCall.id());
        if (this.isInterrupted()) {
            latch.countDown();
            return;
        }
        features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_PARTIAL_TOOL_CALL));
    }

    private void onPartialResponse(final String s, final List<Obj> features, final CountDownLatch latch) {
        StatusLine.message(str("\uD83D\uDCAC on_partial_response"));
        if (this.isInterrupted()) {
            latch.countDown();
            return;
        }
        Router.global().stats().ioStats().incrBytesRecv(s.getBytes().length);
        features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_PARTIAL_RESPONSE, str(s)));
    }

    private void onPartialThinking(final PartialThinking t, final CountDownLatch latch) {
        StatusLine.message(str("\uD83D\uDCAD on_partial_thinking"));
        if (this.isInterrupted()) {
            latch.countDown();
            return;
        }
        Router.global().stats().ioStats().incrBytesRecv(t.text().getBytes().length);
        // thinking is the one stage this class does not dispatch: ThinkFeature owns it, seeds the
        // thought with the chunk, applies it, and cascades it through the other features
        this.feature(ThinkFeature.class)
                .ifPresent(f -> f.onPartialThinking(this, str(t.text())));
    }

    private void onError(final Throwable e, final List<Obj> features,
                         final AtomicReference<MTronException> isError, final CountDownLatch latch) {
        final fURI currentFeature = this.currentHook.get().get0();
        final fURI currentStage = this.currentHook.get().get1();
        final String errorMessage = "[" + currentFeature + "][" + currentStage + "]";
        LOG.error("%s: %s", errorMessage, e);
        isError.set(MTronException.of("%s: %s", errorMessage, e));
        features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_ERROR));
        latch.countDown();
    }

    private void onCompleteResponse(final ChatResponse c, final Rec responseFormat, final List<Obj> features,
                                    final long startNanos, final CountDownLatch latch) {
        StatusLine.message(str("\uD83D\uDCE6 on_complete_response"));
        if (this.isInterrupted()) {
            latch.countDown();
            return;
        }
        final String fullText = null == c.aiMessage().text() ? "" : c.aiMessage().text();
        Router.global().stats().ioStats().incrBytesRecv(fullText.getBytes().length);
        // Parse response format if requested
        final boolean formatted = !responseFormat.isNoObj();
        final Obj chatObj;
        // A formatted response is a structured rec end to end — there is no text channel for a
        // watermark to ride in.
        final WatermarkUtil.Scan scan = formatted ? null : WatermarkUtil.scan(fullText);
        if (formatted) {
            chatObj = ObjJSONSerializer.simple().inputBytes(fullText);
        } else {
            // Scan the watermarks out of the response: what the model addressed to a feature lands
            // on the result, and the markup is stripped from what the user sees — and, because the
            // chat is persisted, from what the ledger keeps.
            chatObj = str(scan.visible());
        }
        // Build the chat_result — monos inline (chat, user, time), the watermarks the model
        // emitted as an ordered watermark::T lst; feature outputs are attached by the features
        // themselves in their onCompleteResponse.
        final long elapsed = (System.nanoTime() - startNanos) / 1_000_000;
        // The result is the frame itself when a frame provider is attached (pushed at chat() entry
        // with the prompt); otherwise build a standalone ChatFrame here.  Either way the monos
        // (chat, user, time) and the watermarks are written onto it, and the features attach their
        // outputs to the same rec.
        final ChatFrame result = null != this.currentResult
                ? this.currentResult
                : ChatFrame.chatFrame().prompt(this.userMessage());
        result.put(CHAT, chatObj.apply(this))
                .put(USER, str(this.userMessage()))
                .put(TIME, mathInstSet.normalizeTime(real((double) elapsed, MATH_MILLIS_TID, null)));
        if (null != scan && !scan.isEmpty())
            result.put(WATERMARK, scan.list());
        this.currentResult = result;
        this.logger().none("\n");
        features.stream().map(Obj::asRec).forEach(f -> dispatchHook(f, ON_COMPLETE_RESPONSE, result));
        // Signal the waiting thread after all hooks have mutated the result
        latch.countDown();
    }

    private ChatFrame persistResult(final FrameService frameService) {
        final ChatFrame result = this.currentResult;
        this.currentResult = null;
        this.currentHook.set(null);
        if (null != frameService) {
            // the frame was pushed at chat() entry with only the prompt; write the assembled result
            // back into the frame URI, then pop — pop marks it complete and returns the answered frame
            final fURI frameURI = frameService.current();
            if (null != frameURI && null != result)
                Router.writeToSpace(frameURI, result);
            final Frame popped = frameService.pop();
            return null != popped ? (ChatFrame) popped : (null != result ? result : ChatFrame.chatFrame());
        }
        // no frame provider — persist the legacy chat_result ledger and return it
        return null != result ? Router.writeToSpace(this.at(ROOT).uriValue().extend(LLM_CHAT_RESULT_TID.name()).extend("_").addQ(INCRQ), result).as() : ChatFrame.chatFrame();
    }

    private void closeTurn(final AtomicReference<Set<String>> orphanToolRequests, final AtomicInteger counter) {
        // both channels close with the chat: a tool request the loop never answered gets a lost
        // result and its parked ai message is published as a group, and a mid-chat message still
        // queued is written rather than dropped
        this.closeChannels(orphanToolRequests);
        // SystemFeature owns the per-chat system-message state — clear it so the next chat
        // re-surfaces its own system context.
        this.feature(SystemFeature.class).ifPresent(SystemFeature::clearSystemMessages);
        counter.decrementAndGet();
        this.currentDepth = 0;
        this.at(ACTIVE, BOOL_FALSE, MUTABLE);
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
