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
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatResult;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_THINK_FEATURE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ThinkFeature extends AbstractFeature {
    /** Prose batches at this many characters; a template or watermark flushes at once. */
    private static final int BATCH = 25;

    /** The raw tail not yet thought — a construct split across chunks is held here. */
    private StringBuilder buffer = new StringBuilder();
    /** The thought, in output order — what the turn's thinking row will hold. */
    private StringBuilder full = new StringBuilder();
    private String lastRendered = "";
    private final AtomicBoolean thinkDone = new AtomicBoolean(false);
    /**
     * The thought row persisted for the current chat — attached to the chat_result as a ref.
     */
    private Obj lastThink;

    public ThinkFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * The thinking stage — and this feature owns it.
     *
     * <p>{@code Agent} does not loop over the features for thinking: it calls this, and
     * this runs the loop.  The chunk seeds the thought, the thought is applied to the
     * agent — {@code ${...}} templates resolved, {@code <<...>>} watermark markup left
     * intact for the feature whose tag it is — and the result is cascaded through every
     * other feature that has the stage wired.  What comes back is the thought, and it
     * is cataloged here.
     */
    @Override
    public Obj onPartialThinking(final Agent agent, final Obj thought) {
        this.thinkDone.set(false);
        this.buffer.append(Str.Helper.cleanString(thought));
        return this.think(agent);
    }

    /**
     * Put text into the thought at a moment of the caller's own — a mid-chat message the
     * agent has just read, say.
     *
     * <p>This is the single writer entry point, and that is what keeps the order honest:
     * whatever is still buffered is older than the text being added, so it is thought
     * first.  A foreign writer racing the buffer would otherwise land after text that
     * logically precedes it.
     */
    public void append(final Agent agent, final Str text) {
        this.think(agent);
        this.catalog(agent, text);
    }

    /**
     * Think whatever the buffer can express: the batch rule decides how much, a split
     * construct's tail stays held, and what is expressible is applied, cascaded, and
     * cataloged.
     *
     * @return the thought this pass cataloged, or {@code noobj()} when the buffer was
     *         still filling
     */
    private Obj think(final Agent agent) {
        final String accumulated = this.buffer.toString();
        final String tail = Str.pendingTemplateTail(accumulated);
        final String renderable = accumulated.substring(0, accumulated.length() - tail.length());
        if (renderable.isEmpty())
            return noobj();
        // template presence flushes immediately so evaluations appear as they happen
        if (accumulated.length() < BATCH && !hasTemplate(accumulated))
            return noobj();
        final Obj thought = this.cascade(agent, str(Str.Helper.cleanString(str(renderable).apply(agent))));
        this.buffer = new StringBuilder(tail);
        this.catalog(agent, thought);
        return thought;
    }

    /**
     * Pass the thought through every other feature's {@code on_partial_thinking}, in
     * agent-list order.
     *
     * <p>A feature with no hook wired is skipped — that is the same test
     * {@code createStageLambdas} uses to decide whether to wire one — and a feature that
     * abstains ({@code noobj()}) leaves the thought exactly as it found it.  That is how
     * the markup one feature owns rides past all the ones that do not.
     */
    private Obj cascade(final Agent agent, final Obj thought) {
        Obj value = thought;
        for (final Obj feature : agent.features().elements().toList()) {
            final Rec rec = feature.asRec();
            if (rec.tid().equals(LLM_THINK_FEATURE_TID))
                continue; // this feature drives the cascade; it is not a participant
            final Obj hook = rec.at(uri(ON_PARTIAL_THINKING));
            if (hook.isNoObj())
                continue;
            final Obj folded = (hook.isInst() ? hook.asInst().args(lst(value)) : hook).apply(agent);
            if (!folded.isNoObj())
                value = folded;
        }
        return value;
    }

    /** Catalog a thought: append it to the turn's thinking, and show it as it arrives. */
    private void catalog(final Agent agent, final Obj thought) {
        final String text = Str.Helper.cleanString(thought);
        if (text.isEmpty())
            return;
        this.full.append(text);
        if (text.equals(this.lastRendered))
            return;
        this.at(f(THINK).extend(TO)).apply(str(text));
        this.lastRendered = text;
    }

    /** Whether the text carries something that must not be rendered half-written. */
    private static boolean hasTemplate(final String text) {
        return text.contains("{{{") || text.contains("${") || text.contains("<<");
    }

    @Override
    public void onPartialResponse(final Agent agent, final Str text) {
        if (!this.thinkDone.getAndSet(true)) {
            agent.feature(LLM_THINK_FEATURE_TID).asRec().at(f(THINK).extend(TO)).apply(str(Str.Helper.cleanString(str(this.buffer.toString()).apply(agent))));
            final fURI thinkWriteURI = agent.feature(LLM_THINK_FEATURE_TID).asRec().at(ROOT).orElse(agent.at(ROOT).uriValue().extend(THINK).toUri()).uriValue().extend("_").addQ(INCRQ);
            this.lastThink = MessageBuilder.buildThinkingMessage()
                    .text(this.full.toString().trim())
                    .time()
                    .session(agent.sessionVID())
                    .depth(agent.chatDepth())
                    .chatId(agent.chatId())
                    .create(thinkWriteURI);
            this.buffer = new StringBuilder();
            this.full = new StringBuilder();
            this.lastRendered = "";
        }
    }

    @Override
    public void onCompleteResponse(final Agent agent, final ChatResult result) {
        result.putRef("think", this.lastThink);
    }

}
