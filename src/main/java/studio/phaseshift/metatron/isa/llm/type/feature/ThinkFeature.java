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
    private final AtomicBoolean thinkDone = new AtomicBoolean(false);

    public ThinkFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public void onPartialThinking(final Agent agent, final Str text) {
        this.thinkDone.set(false);
        this.buffer.append(text.strValue());
        this.full.append(text.strValue());
        if (this.buffer.length() > 25) {
            agent.feature(THINK).asRec().at(f(THINK).extend(TO)).apply(str(buffer.toString()));
            this.buffer = new StringBuilder();

        }
    }

    @Override
    public void onPartialResponse(final Agent agent, final Str text) {
        if (!this.thinkDone.getAndSet(true)) {
            this.buffer.append("\n\n");
            agent.feature(THINK).asRec().at(f(THINK).extend(TO)).apply(str(this.buffer.toString()));
            final fURI thinkWriteURI = agent.feature(THINK).asRec().at(ROOT).orElse(agent.at(ROOT).uriValue().extend(THINK).toUri()).uriValue().extend("_").addQ(INCRQ);
            final Rec thought = rec(mutableMap(uri(TEXT), str(this.full.toString().trim())), THINKING_MESSAGE_TID, null);
            thought.recValue().put(uri(TIME), mathInstSet.nowDatetime());
            thought.recValue().put(uri(SESSION), agent.feature(SESSION).asRec().at(SESSION));
            thought.recValue().put(uri(DEPTH), jnt(agent.chatDepth()));
            thought.recValue().put(uri(CHAT_ID), jnt(agent.chatId()));
            this.buffer = new StringBuilder();
            this.full = new StringBuilder();
            LOG.debug("writing thought to %s", thinkWriteURI);
            Router.writeToSpace(thinkWriteURI, thought);
        }
    }
}
