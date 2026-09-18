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

package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.llm.WatermarkUtil;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatSessionStore;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatFrame;
import studio.phaseshift.metatron.isa.llm.type.feature.service.MessageService;
import studio.phaseshift.metatron.isa.llm.type.feature.service.SkillService;
import studio.phaseshift.metatron.isa.llm.type.feature.service.SystemService;
import studio.phaseshift.metatron.isa.llm.type.mModel;
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.thread.CoreThread;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.llm.type.mModel.model;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_MINUTE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CompactionFeature extends AbstractFeature {
    public static final fURI FEATURE_TID = studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_COMPACTION_FEATURE_TID;


    /**
     * The background compaction currently running, if any.  Queued in
     * {@link #onCompleteResponse} when the agent appends a
     * {@code <<mtron:compaction>>} block or the context passes the auto
     * threshold; the sentinel it writes is picked up by the store's
     * {@code stopAt} on the next chat.
     */
    final AtomicReference<FutureObj<Obj>> compactionTask = new AtomicReference<>();

    public CompactionFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Set<fURI> requires() {
        return Set.of(LLM_SKILL_SERVICE_TID, LLM_MESSAGE_SERVICE_TID);
    }

    /**
     * Register this feature's skill with the SkillFeature gateway — the
     * gateway owns the skill channel; this feature is a contributor.  The
     * skill teaches the model to emit a {@code <<mtron:compaction>>} block,
     * whose body is the same argument rec the {@code compact()} instruction
     * takes.
     */
    /**
     * This feature's watermark identity.  Declared on the config rec as
     * {@code watermark => [key=>..., tag=>...]} to override either; these are the
     * defaults, and the single place the server-side lookup and the skill prose
     * both take their marker from.
     */
    static final String WATERMARK_KEY = "compaction";
    static final String WATERMARK_CODEC = "mtron";

    private static final String COMPACTION_INSTRUCTIONS = """
                                                          When the conversation history is getting large, you can append a `<<mtron:compaction>>`
                                                          watermark to your response — a deferred `compact()` call, where the watermark body is
                                                          the same argument rec `compact()` takes (all optional). An empty body means "use your
                                                          defaults":
                                                          
                                                              <<mtron:compaction>><</mtron:compaction>>
                                                          
                                                          The compaction runs in the background — acknowledge that it is queued and respond
                                                          normally. On the next chat, the conversation history is replaced with a resume summary
                                                          plus the most recent messages. Prefer this over continuing with an unwieldy history.
                                                          """;

    public void registerSkill(final Agent agent) {
        if (!agent.hasFeature(LLM_SKILL_FEATURE_TID))
            return;
        final String instructions = WatermarkUtil.instructions(WATERMARK_CODEC,
                WatermarkUtil.key(this, WATERMARK_KEY), COMPACTION_INSTRUCTIONS);
        agent.requireService(SkillService.class).addSkill(mSkill.of(rec(mutableMap(
                uri(NAME), uri(LLM_COMPACTION_FEATURE_TID.name()),
                uri(DESC), str("compact the conversation history into a resume summary when the context grows large"),
                uri(CONTENT), str(instructions)))));
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        this.registerSkill(agent);
        this.surfaceWatermarkRejections(agent);
        this.surfaceResumeSummary(agent);
        return noobj();
    }

    /**
     * Surface the resume summary from the newest compaction sentinel as a
     * system-message contribution — the sentinel bounds the window (stopAt)
     * but is not itself a message, so its summary rides the system channel
     * (LC4j has no compaction message type).
     */
    private void surfaceResumeSummary(final Agent agent) {
        if (agent.service(MessageService.class).isEmpty() || !agent.hasFeature(LLM_SYSTEM_FEATURE_TID))
            return;
        final MessageService messageFeature = agent.requireService(MessageService.class);
        final fURI sessionVID = messageFeature.sessionVID();
        if (null == sessionVID || null == messageFeature.store())
            return;
        final List<Rec> window = messageFeature.store().query(sessionVID).stopAt(COMPACTION_MESSAGE_TID).apply();
        if (window.isEmpty() || !window.get(0).tid().equals(COMPACTION_MESSAGE_TID))
            return;
        final String summary = Str.Helper.cleanString(window.get(0).at(TEXT).orElse(str("")));
        if (summary.isBlank())
            return;
        agent.requireService(SystemService.class).addSystemMessage("""
                                                                   resume summary from a prior compaction:
                                                                   
                                                                   %s
                                                                   """.formatted(summary));
    }

    @Override
    public void onCompleteResponse(final Agent agent, final ChatFrame result) {
        // 1. detect the <<mtron:compaction>> watermark the model emitted
        this.noteWatermarkFailure(result, WATERMARK_CODEC, WATERMARK_KEY);
        final Obj signal = result.watermark(WatermarkUtil.key(this, WATERMARK_KEY));
        Rec block = signal.isNoObj() ? null : signal.asRec();
        if (agent.service(MessageService.class).isEmpty()) {
            if (null != block)
                LOG.warn("compaction requires the session feature");
            return;
        }
        final MessageService messageFeature = agent.requireService(MessageService.class);
        final fURI sessionVID = messageFeature.sessionVID();
        if (null == sessionVID || sessionVID.isEmpty()) {
            if (null != block)
                LOG.warn("compaction requires an anchored session");
            return;
        }
        // 2. auto-trigger when there is no watermark and the context is past threshold
        if (null == block && !this.shouldAutoCompact(messageFeature, sessionVID))
            return;
        // 3. don't queue a second compaction while one is still running
        final FutureObj<Obj> running = this.compactionTask.get();
        if (null != running && !running.isDone())
            return;
        // 4. queue the background compaction — the block rec is a deferred compact() call
        final fURI agentHome = agent.at(ROOT).uriValue();
        final Rec config = this.resolveConfig(agent, null == block ? rec() : block);
        final CoreThread thread = CoreThread.core(instLambda((lhs, inst) -> {
            try {
                final Obj applied = compactSession(agentHome, sessionVID, config);
                if (applied.isFail())
                    LOG.warn("compaction failed: %s", Str.Helper.cleanString(applied));
                return applied;
            } catch (final Exception e) {
                LOG.error("compaction failed: %s", e.getMessage());
                return noobj();
            }
        }));
        this.compactionTask.set(thread.applyAsync());
        LOG.info("compaction queued for session %s", sessionVID);
    }

    /**
     * The argument rec for compact() — the block rec (model/prompt) merged
     * over the feature's model default.  The agent/session are resolved by the
     * caller, so the block is exactly a deferred compact() call.
     */
    Rec resolveConfig(final Agent agent, final Rec block) {
        return rec(uri(MODEL), block.at(uri(MODEL)).orElse(this.at(uri(MODEL))),
                uri(PROMPT), block.at(uri(PROMPT)));
    }

    /**
     * Whether the message payload has reached the compaction threshold — the
     * estimated token count of the sentinel-stopped window divided by the
     * model's context window size.  Disabled when the context size is unknown.
     */
    private boolean shouldAutoCompact(final MessageService messageFeature, final fURI sessionVID) {
        final double threshold = this.at(THRESHOLD).orElse(real(0.8)).realValue();
        final int contextWindow = this.resolveContextWindow();
        if (contextWindow <= 0)
            return false;
        final TokenMessageFeature.DefaultTokenCountEstimator estimator = TokenMessageFeature.DefaultTokenCountEstimator.singleton();
        final int payloadTokens = messageFeature.store().query(sessionVID).stopAt(COMPACTION_MESSAGE_TID).apply()
                .stream().mapToInt(r -> estimator.estimateTokenCountInText(Str.Helper.cleanString(r.at(TEXT).orElse(str(""))))).sum();
        return ((double) payloadTokens / (double) contextWindow) >= threshold;
    }

    /**
     * The model's context window size in tokens: the feature's explicit
     * {@code context}, else the model rec's advertised {@code context} (Ollama
     * populates it), else 0 (unknown — auto-compaction disabled).
     */
    private int resolveContextWindow() {
        final Obj featureContext = this.at(uri(CONTEXT));
        if (featureContext.isInt())
            return featureContext.intValue().intValue();
        final Obj model = this.at(uri(MODEL));
        if (model.isRec()) {
            final Obj context = model.asRec().at(uri(CONTEXT));
            if (context.isInt())
                return context.intValue().intValue();
        }
        return 0;
    }

    /**
     * Distill prompt for {@code compact()}: asks the model to write a
     * continuation summary that replaces the conversation history in a future
     * context window.  The summary becomes the {@code text} of the
     * {@code compaction_message::T} sentinel — the model never sees the raw
     * transcript again, only the resume summary.
     */
    private static final String COMPACT_PROMPT = """
                                                 You have been working on the task described above but have not yet completed it.
                                                 Write a continuation summary that will allow you (or another instance of yourself) to resume work efficiently
                                                 in a future context window where the conversation history will be replaced with this summary.
                                                 
                                                 Your summary should be structured, concise, and actionable. Include:
                                                 1. **Task Overview**: The user's core request, success criteria, and constraints.
                                                 2. **Current State**: What has been completed, current progress, and any pending steps.
                                                 3. **Key Details**: User preferences, domain-specific details, or promises made to the user.
                                                 
                                                 Write in a way that enables immediate resumption of the task.
                                                 
                                                 ## Conversation:
                                                 %s
                                                 """;


    /**
     * Compact a session's message ledger into a single {@code compaction_message::T}
     * sentinel whose {@code text} is a resume summary, stamped with the token
     * compression stats ({@code in}, {@code out}, {@code compression}).  The
     * trailing few messages are re-appended after the sentinel so the immediate
     * context is not lost in the summary.  Shared by the {@code compact} inst and
     * the CompactionFeature's background thread — the config rec has the same
     * vocabulary as the {@code <<mtron:compaction>>} block (agent, model, prompt).
     *
     * @param agentHome  the agent root — the model rec is resolved from
     *                   {@code <agentHome>/model} when the config's model is noobj
     * @param sessionVID the session whose ledger messages are compacted
     * @param config     the argument/block rec — {@code model} and {@code prompt}
     *                   override the summarizer's model and prompt template
     * @return the applied-constraints rec — the resolved [to, compaction=>vid]
     * plus the [in, out, compression] stats; a fail::T on error
     */
    public static Obj compactSession(final fURI agentHome, final fURI sessionVID, final Rec config) {
        final GraphittyLogger LOG = Graphitty.log(CompactionFeature.class);
        final Obj modelArg = config.at(uri(MODEL));
        final Obj promptArg = config.at(uri(PROMPT));
        final Obj output = config.at(uri(TO));
        final fURI outputBase = output.isNoObj() ? agentHome : output.uriValue();
        // 1. collect this session's messages from the ledger, oldest -> newest
        LOG.status(DEBUG, "\uD83D\uDCE9 gathering messages for compaction");
        final fURI messagesLocation = agentHome.extend(MESSAGE).extend("+/");
        final List<Rel> messages = Router.readFromSpace(messagesLocation)
                .stream()
                .map(Obj::asRel)
                .filter(pair -> !pair.second().tid().equals(LLM_TOOL_RESULT_MESSAGE_TYPE.vid()))
                .filter(pair -> {
                    final Obj sessionUri = pair.second().asRec().at(SESSION);
                    return sessionUri.isUri() && sessionUri.uriValue().equals(sessionVID);
                })
                .sorted(Comparator.comparing(pair -> Integer.parseInt(pair.first().uriValue().name())))
                .toList();
        LOG.status(DEBUG, "\uD83D\uDCE9 gathered %d messages for compaction", messages.size());
        if (messages.isEmpty())
            return fail("no messages found for session %s at %s", sessionVID, messagesLocation);
        // 2. build the conversation digest — text only, so the model sees content not vids
        final String digest = messages.stream()
                .map(pair -> Str.Helper.cleanString(pair.second().asRec().at(TEXT).orElse(str(""))))
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("\n-----\n"));
        // 3. the summarizer model (agent home model when not given) and prompt
        final mModel model = modelArg.isNoObj()
                ? mModel.model(Router.readFromSpace(agentHome.extend(MODEL)).asRec())
                : mModel.model(modelArg.asRec());
        final String prompt = promptArg.isNoObj() ? COMPACT_PROMPT : promptArg.strValue();
        // 4. distill via a mini-task
        LOG.status(DEBUG, "\uD83D\uDDDC\uFE0F prompting agent to derive compaction sentinel", messages.size());
        final ChatFrame result = Agent.Helper.miniChat("session_compactor", model(model.at(TIMEOUT, real(5.0, MATH_MINUTE_TID, null))), prompt.formatted(digest));
        final String summary = Str.Helper.cleanString(result.at(CHAT).orElse(str("")));
        // 5. write the sentinel + pair-safe recent-tail
        final Rec sentinel = writeCompaction(agentHome, sessionVID, messages, digest, summary);
        return rec(uri(TO), uri(outputBase),
                uri("compaction"), uri(sentinel.vid()),
                uri(IN), sentinel.at(uri(IN)),
                uri(OUT), sentinel.at(uri(OUT)),
                uri(COMPRESSION), sentinel.at(uri(COMPRESSION)));
    }

    /**
     * Write the compaction sentinel — its {@code text} is the resume summary,
     * stamped with {@code in}/{@code out}/{@code compression} token stats — then
     * re-append the recent-tail after it, pair-safe (a {@code tool_result} is
     * never orphaned from its {@code ai} message).  Extracted from
     * {@link #compactSession} so the write-path is testable without an LLM
     * round-trip.
     *
     * @param agentHome  the agent root — the sentinel/tail write under {@code <agentHome>/message/}
     * @param sessionVID the session the sentinel belongs to
     * @param messages   the session's messages, oldest -> newest, as ledger rels
     * @param digest     the conversation digest (drives the {@code in} token stat)
     * @param summary    the resume summary (the sentinel's {@code text})
     * @return the written sentinel rec (text + in/out/compression + session/depth)
     */
    public static Rec writeCompaction(final fURI agentHome, final fURI sessionVID, final List<Rel> messages, final String digest, final String summary) {
        final TokenMessageFeature.DefaultTokenCountEstimator estimator = TokenMessageFeature.DefaultTokenCountEstimator.singleton();
        final int tokensIn = estimator.estimateTokenCountInText(digest);
        final int tokensOut = estimator.estimateTokenCountInText(summary);
        final double compression = tokensIn == 0 ? 0.0 : 1.0 - ((double) tokensOut / (double) tokensIn);
        final fURI writePath = agentHome.extend(MESSAGE).extend("_").addQ(INCRQ);
        final Rec sentinel = MessageBuilder.build(COMPACTION_MESSAGE_TID)
                .text(summary)
                .time()
                .session(sessionVID)
                .depth(1)
                .put(IN, jnt(tokensIn))
                .put(OUT, jnt(tokensOut))
                .put(COMPRESSION, real(compression))
                .create(writePath);
        final int SPILL_OVER = 5; // recent-tail — keep the immediate context raw, not just in the summary
        // only re-append the conversational kinds — system/thinking/compaction
        // are metatron-world records (SystemFeature re-writes the system message
        // each turn), and a system message in the tail would break the model's
        // "system message must be at the beginning" invariant
        final List<Rel> conversational = messages.stream()
                .filter(pair -> {
                    final fURI tid = pair.second().tid();
                    return tid.equals(USER_MESSAGE_TID) || tid.equals(AI_MESSAGE_TID) || tid.equals(TOOL_RESULT_MESSAGE_TID);
                })
                .toList();
        int skip = Math.max(0, conversational.size() - SPILL_OVER);
        // pull in more (never fewer) messages so the tail never starts on an
        // orphaned tool_result or an ai message without its user message
        skip = SpaceChatSessionStore.adjustSkipToPreservePairs(conversational, skip);
        for (int i = skip; i < conversational.size(); i++) {
            final Rec tail = conversational.get(i).second().asRec();
            MessageBuilder.build(tail.tid()).copy(tail.jvm()).create(writePath);
        }
        return sentinel;
    }

}
