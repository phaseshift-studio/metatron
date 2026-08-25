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
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.THINKING_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ThinkFeature extends AbstractFeature {
    private StringBuilder buffer = new StringBuilder();
    private StringBuilder full = new StringBuilder();
    private String lastRendered = "";
    private final AtomicBoolean thinkDone = new AtomicBoolean(false);
    /** The thought row persisted for the current chat — attached to the chat_result as a ref. */
    private Obj lastThink;

    public ThinkFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public void onPartialThinking(final Agent agent, final Str text) {
        this.thinkDone.set(false);
        this.buffer.append(text.strValue());
        this.full.append(text.strValue());
        // Templates are evaluated live with the agent as lhs, but a template
        // split across streamed chunks must be held back until it completes —
        // otherwise the partial renders as literal text and the closing
        // delimiter never finds its opener.  Prose still batches at 25 chars;
        // template presence flushes immediately so evaluations appear as they
        // happen.
        final String accumulated = this.buffer.toString();
        final String tail = Str.pendingTemplateTail(accumulated);
        final String renderable = accumulated.substring(0, accumulated.length() - tail.length());
        final boolean hasTemplate = accumulated.indexOf("{{{") >= 0 || accumulated.indexOf("${") >= 0;
        if (accumulated.length() > 25 || hasTemplate) {
            if (!renderable.isEmpty()) {
                final Str rendered = (Str) str(renderable).apply(agent);
                if (!rendered.strValue().equals(this.lastRendered)) {
                    agent.feature(THINK).asRec().at(f(THINK).extend(TO)).apply(rendered);
                    this.lastRendered = rendered.strValue();
                }
            }
            this.buffer = new StringBuilder(tail);
        }
    }

    @Override
    public void onPartialResponse(final Agent agent, final Str text) {
        if (!this.thinkDone.getAndSet(true)) {
            agent.feature(THINK).asRec().at(f(THINK).extend(TO)).apply(str(Str.Helper.cleanString(str(this.buffer.toString()).apply(agent))));
            final fURI thinkWriteURI = agent.feature(THINK).asRec().at(ROOT).orElse(agent.at(ROOT).uriValue().extend(THINK).toUri()).uriValue().extend("_").addQ(INCRQ);
            final Rec thought = rec(mutableMap(uri(TEXT), str(this.full.toString().trim())), THINKING_MESSAGE_TID, null);
            thought.recValue().put(uri(TIME), mathInstSet.nowDatetime());
            thought.recValue().put(uri(SESSION), agent.feature(SESSION).orElse(rec()).at(SESSION));
            thought.recValue().put(uri(DEPTH), jnt(agent.chatDepth()));
            thought.recValue().put(uri(CHAT_ID), jnt(agent.chatId()));
            this.buffer = new StringBuilder();
            this.full = new StringBuilder();
            this.lastRendered = "";
            LOG.debug("writing thought to %s", thinkWriteURI);
            this.lastThink = Router.writeToSpace(thinkWriteURI, thought);
        }
    }

    @Override
    public void onCompleteResponse(final Agent agent, final ChatResult result) {
        result.putRef("think", this.lastThink);
    }

}
