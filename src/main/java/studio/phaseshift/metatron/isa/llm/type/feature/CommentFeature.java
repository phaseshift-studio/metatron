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
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.Tokens.TOOL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_AGENT_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread.virtual;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CommentFeature extends AbstractFeature {

    public CommentFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        final Inst commentInst = instC(f("comment").dom(LLM_AGENT_TID).rng(NOOBJ_TID.zero()), lst(STR_TYPE), (lhs, inst) -> {
            final Agent ag = lhs.as();
            final String agentName = agent.at(NAME).orElse(str("agent")).strValue();
            final String comment = inst.arg(0).strValue();
            ag.interrupt();
            docWrap(virtual(instLambda((l, i) -> l.<Agent>as().chat(i.arg(0).strValue()))).apply(ag), "agent %s processing comment: %s".formatted(agentName, comment));
            LOG.info("%s received comment: %s", agentName, comment);
            return noobj();
        });
        Router.writeToSpace("comment", commentInst);
        this.at(TOOL, lst(commentInst), MUTABLE);
        return noobj();
    }
}
