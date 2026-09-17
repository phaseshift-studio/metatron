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
import studio.phaseshift.metatron.isa.llm.type.ChatFrame;
import studio.phaseshift.metatron.isa.llm.type.ChatStack;
import studio.phaseshift.metatron.isa.llm.type.Frame;
import studio.phaseshift.metatron.isa.llm.type.feature.service.FrameService;
import studio.phaseshift.metatron.isa.llm.type.feature.service.MessageService;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.ROOT;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_FRAME_SERVICE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_MESSAGE_SERVICE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * The frame-feature base — a Feature that provides {@link FrameService} over a {@link ChatStack}.
 * It pushes a root {@link ChatFrame} on {@code onBeforeChat}, pops it on {@code onCompleteResponse},
 * and stores the frame tree under the feature's own {@code root} (the root *is* the address space).
 *
 * <p>The single seam is {@link #onPopped(Frame)} — what {@code pop()} does to the popped frame:
 * a persisted provider leaves it, a transient provider removes it.
 */
public abstract class AbstractFrameFeature extends AbstractFeature implements FrameService {

    private ChatStack stack;

    protected AbstractFrameFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Set<fURI> offers() {
        return Set.of(LLM_FRAME_SERVICE_TID);
    }

    @Override
    public Set<fURI> requires() {
        return Set.of(LLM_MESSAGE_SERVICE_TID);
    }

    /**
     * The frame tree root — stable across providers ({@code frame}, not the provider's own tid
     * segment {@code persisted}/{@code transient}).
     */
    @Override
    public fURI getRoot(final Agent agent) {
        if (this.has(ROOT))
            return this.at(ROOT).uriValue();
        if (agent.has(ROOT))
            return agent.at(ROOT).uriValue().extend("frame");
        throw MTronException.of("no root uri found on feature nor agent: %s", this.tid());
    }

    /**
     * Build the turn's {@link ChatStack} — the pre-chat half, run by {@code Agent.chat()} before
     * the frame is pushed (it needs the advanced {@code chat_id} and the recursion {@code depth}).
     */
    public void prepare(final Agent agent) {
        final MessageService message = agent.requireService(MessageService.class);
        this.stack = new ChatStack(this.getRoot(agent), message.sessionVID(), agent.chatId(), agent.chatDepth());
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        // the stack is built in prepare() (pre-chat phase) — this feature is a pure provider
        return noobj();
    }

    // ── FrameService — delegates to the current turn's ChatStack ─────

    @Override
    public fURI current() {
        return null == this.stack ? null : this.stack.current();
    }

    @Override
    public Frame push(final Frame frame) {
        return this.stack.push(frame);
    }

    @Override
    public Frame pop() {
        final fURI frameURI = this.current();
        final Frame frame = this.stack.pop();
        if (null != frame)
            this.onPopped(frame, frameURI);
        return frame;
    }

    @Override
    public Obj at(final fURI key) {
        return null == this.stack ? noobj() : this.stack.at(key);
    }

    @Override
    public void locals(final fURI key, final Obj value) {
        if (null != this.stack)
            this.stack.locals(key, value);
    }

    @Override
    public fURI parentURI() {
        return null == this.stack ? null : this.stack.parentURI();
    }

    /**
     * What pop does to the popped frame — the persisted/transient seam.
     *
     * @param frame    the frame that was just popped (complete)
     * @param frameURI the frame's address — the URI it was written flat to
     */
    protected abstract void onPopped(Frame frame, fURI frameURI);
}
