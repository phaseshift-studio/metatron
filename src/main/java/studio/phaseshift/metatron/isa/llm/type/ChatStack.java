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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.feature.service.FrameService;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * The concrete frame spine — a {@link FrameService} that writes frames flat to
 * {@code <root>/frame/s<sid>/c<cid>/d<depth>} and reads them back derived (rec-ness from the
 * space).  Replaces the static {@code depthMap} counter + {@code currentDepth} field that
 * {@code Agent.chat()} carries today.
 */
public class ChatStack implements FrameService {

    private final fURI root;
    private final fURI session;
    private final int chatId;
    /*
     * ── 1b — threading the parent frame (the depthMap retirement) ─────────────────────────
     *
     * `depth` is the recursion level, injected today from `agent.chatDepth()`, which reads the
     * static `depthMap` counter that `Agent.chat()` increments/decrements.  Recursion here is
     * *cross-agent*: a recursive sub-agent — `Agent.Helper.miniChat`, or an agent exposed as a
     * tool — is a FRESH agent that carries no frame feature, so the frame tree cannot see the
     * nesting and `depthMap` remains the one shared counter.  1b removes it by making the frame
     * tree itself the source of the depth:
     *
     *   1. Thread the return address.  The recursion entry points — the `chat` inst
     *      (`llmInstSet` chat / chat(str,rec)) and `Agent.Helper.miniChat` — capture the caller's
     *      `frameService.current()` (the top frame URI) and hand it to the sub-agent's `chat()`
     *      as the parent.
     *   2. Give sub-agents a frame.  `miniChat`'s translator carries a `ChatFeature` only; it
     *      must attach a frame feature so its `chat()` pushes a frame.
     *   3. Derive, don't count.  `ChatStack.push` reads the parent frame's `depth` back from
     *      space and stamps `depth = parent.depth + 1` — this constructor-injected `depth` field
     *      disappears — while still writing the `parent` link so the whole tree walks.
     *   4. Read depth from the frame.  `Agent.chatDepth()` returns the current frame's `depth`
     *      (via `frameService`) instead of the `currentDepth` field; `currentDepth` is deleted.
     *
     * Ordering caveat that keeps this honest: `chatDepth()` is consumed during `onBeforeChat`
     * (store creation + message stamping) *before* any frame is pushed, so the depth must be
     * resolvable pre-push.  That forces the parent to be threaded into `chat()` up front (so the
     * ChatStack is built with the derived depth before `onBeforeChat` runs) or the frame to be
     * pushed ahead of `onBeforeChat`.  Neither is cheap — which is why 1a (inject the recursion
     * level, keep `depthMap` as the cross-agent seed) shipped first and 1b is its own unit.
     */
    private final int depth;
    private final Deque<fURI> stack = new ArrayDeque<>();

    public ChatStack(final fURI root, final fURI session, final int chatId, final int depth) {
        this.root = root;
        this.session = session;
        this.chatId = chatId;
        this.depth = depth;
    }

    private fURI frameURI(final int depth) {
        return this.root.extend("frame")
                .extend("s" + this.session.name())
                .extend("c" + this.chatId)
                .extend("d" + depth);
    }

    @Override
    public fURI current() {
        return this.stack.peek();
    }

    @Override
    public Frame push(final Frame frame) {
        final fURI parent = this.stack.peek();
        // d<depth> carries the recursion level (1-based, from agent.chatDepth()) plus the
        // shadow-stack position, so a top-level turn lands at d1 and a within-turn shadow at
        // d2 — the same depth the message ledger stamps (SpaceChatSessionStore.sessionRels).
        final int frameDepth = this.depth + this.stack.size();
        final fURI uri = this.frameURI(frameDepth);
        frame.session(this.session).chatId(this.chatId).depth(frameDepth);
        if (null != parent)
            frame.parentURI(parent);
        Router.writeToSpace(uri, frame);
        this.stack.push(uri);
        return frame;
    }

    @Override
    public Frame pop() {
        final fURI uri = this.stack.pop();
        if (null == uri)
            return null;
        final Obj obj = Router.readFromSpace(uri);
        if (!(obj instanceof Frame frame))
            return null;
        frame.complete();
        Router.writeToSpace(uri, frame);
        return frame;
    }

    @Override
    public Obj at(final fURI key) {
        for (final fURI uri : this.stack) {
            final Obj value = Router.readFromSpace(uri.extend(key));
            if (!value.isNoObj())
                return value;
        }
        return noobj();
    }

    @Override
    public void locals(final fURI key, final Obj value) {
        final fURI uri = this.current();
        if (null == uri)
            return;
        Router.writeToSpace(uri.extend(key), value);
    }

    @Override
    public fURI parentURI() {
        final Iterator<fURI> it = this.stack.iterator();
        if (!it.hasNext())
            return null;
        it.next(); // skip the current frame
        return it.hasNext() ? it.next() : null;
    }
}
