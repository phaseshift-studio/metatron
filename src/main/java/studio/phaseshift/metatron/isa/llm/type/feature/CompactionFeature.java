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
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatResult;
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.thread.CoreThread;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CompactionFeature extends AbstractFeature {

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
        return Set.of(LLM_SKILL_FEATURE_TID, LLM_MESSAGE_FEATURE_TID);
    }

    /**
     * Register this feature's skill with the SkillFeature gateway — the
     * gateway owns the skill channel; this feature is a contributor.  The
     * skill teaches the model to emit a {@code <<mtron:compaction>>} block,
     * whose body is the same argument rec the {@code compact()} instruction
     * takes.
     */
    public void registerSkill(final Agent agent) {
        if (!agent.hasFeature(LLM_SKILL_FEATURE_TID))
            return;
        agent.feature(LLM_SKILL_FEATURE_TID).<SkillFeature>as().addSkill(mSkill.of(rec(mutableMap(
                uri(NAME), uri(LLM_COMPACTION_FEATURE_TID.name()),
                uri(DESC), str("compact the conversation history into a resume summary when the context grows large"),
                uri(CONTENT), str("""
                                  When the conversation history is getting large, you can append a `<<mtron:compaction>>`
                                  block to your response — a deferred `compact()` call, where the block rec is the same
                                  argument rec `compact()` takes (all optional):
                                  
                                      <<mtron:compaction>>[=>]<</mtron:compaction>>
                                  
                                  The compaction runs in the background — acknowledge that it is queued and respond
                                  normally. On the next chat, the conversation history is replaced with a resume summary
                                  plus the most recent messages. Prefer this over continuing with an unwieldy history.
                                  
                                  **IMPORTANT**: This skill is about formatting your response, not calling a function. The
                                  block is stripped from what the user sees.
                                  """)))));
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        this.registerSkill(agent);
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
        if (!agent.hasFeature(LLM_MESSAGE_FEATURE_TID) || !agent.hasFeature(LLM_SYSTEM_FEATURE_TID))
            return;
        final MessageFeature messageFeature = agent.feature(LLM_MESSAGE_FEATURE_TID).<MessageFeature>as();
        final fURI sessionVID = messageFeature.at(SESSION).uriValue();
        if (null == sessionVID || null == messageFeature.store())
            return;
        final List<Rec> window = messageFeature.store().query(sessionVID).stopAt(COMPACTION_MESSAGE_TID).apply();
        if (window.isEmpty() || !window.get(0).tid().equals(COMPACTION_MESSAGE_TID))
            return;
        final String summary = Str.Helper.cleanString(window.get(0).at(TEXT).orElse(str("")));
        if (summary.isBlank())
            return;
        agent.feature(LLM_SYSTEM_FEATURE_TID).<SystemFeature>as().addSystemMessage("""
                                                                                   resume summary from a prior compaction:
                                                                                   
                                                                                   %s
                                                                                   """.formatted(summary));
    }

    @Override
    public void onCompleteResponse(final Agent agent, final ChatResult result) {
        // 1. detect the <<mtron:compaction>> watermark the model emitted
        Rec block = null;
        final Obj blocks = result.at(uri(BLOCK)).orElse(noobj());
        if (!blocks.isNoObj()) {
            final Obj signal = blocks.asRec().at(uri("compaction"));
            if (!signal.isNoObj())
                block = signal.asRec();
        }
        if (!agent.hasFeature(LLM_MESSAGE_FEATURE_TID)) {
            if (null != block)
                LOG.warn("compaction requires the session feature");
            return;
        }
        final MessageFeature messageFeature = agent.feature(LLM_MESSAGE_FEATURE_TID).<MessageFeature>as();
        final fURI sessionVID = messageFeature.at(SESSION).uriValue();
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
        final fURI home = agentHome;
        final fURI sess = sessionVID;
        final CoreThread thread = CoreThread.core(instLambda((lhs, inst) -> {
            try {
                final Obj applied = compactSession(home, sess, config);
                if (applied.isFail())
                    LOG.warn("compaction failed: %s", Str.Helper.cleanString(applied));
                return applied;
            } catch (final Exception e) {
                LOG.error("compaction failed: %s", e.getMessage());
                return noobj();
            }
        }));
        this.compactionTask.set(thread.applyAsync());
        LOG.info("compaction queued for session %s", sess);
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
    private boolean shouldAutoCompact(final MessageFeature messageFeature, final fURI sessionVID) {
        final double threshold = this.at(THRESHOLD).orElse(real(0.8)).realValue();
        final int contextWindow = this.resolveContextWindow();
        if (contextWindow <= 0)
            return false;
        final MessageFeature.DefaultTokenCountEstimator estimator = MessageFeature.DefaultTokenCountEstimator.singleton();
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
}
