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

package studio.phaseshift.metatron.isa.llm.agent;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_ISA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.INSTSET_TID;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(vid = "/m/llm/agent")
public class agentInstSet extends AbstractInstSet {
    // TODO: this is an experiment exploring instset as an mtron specific way of doing llm skills.
    public static final fURI AGENT_ISA_TID = LLM_ISA_TID.extend(AGENT);
    public static final fURI AGENT_INST_TID = AGENT_ISA_TID.extend(INST);
    public static final fURI AGENT_NOTE_INST_TID = AGENT_INST_TID.extend("note");

    public agentInstSet() {
        super(mutableMap(uri(PATTERN), uri(AGENT_ISA_TID.extend(ALL))), INSTSET_TID, AGENT_ISA_TID);
    }

    @Override
    public void setup() {
        this.jvm().putAll(mutableMap(
                uri(PATTERN), uri(AGENT_ISA_TID.extend(ALL)),
                //  uri(CONST), lst(MTRON_EVAL_TOOL)),
                uri(INST), lst(
                        docWrap(instC(AGENT_NOTE_INST_TID.dom(ALL.maybe()).rng(ALL), lst(URI_TYPE, T(ALL.maybe())),
                                        (lhs, inst) -> inst.arg(1).isNoObj() ?
                                                Router.global().read(inst.arg(0).uriValue()) :
                                                Router.global().write(inst.arg(0).uriValue(), inst.arg(1))),
                                "dom is ignored", "the written note", Map.of(jnt(0), "the entry key", jnt(1), "the note"),
                                CommonUtil.readResource(agentInstSet.class, "NOTE.md", "%s", "/usr/ai/note"),
                                "note(</usr/ai/note/an_entry>, 'this is a note')")
                )));
        docWrap(this, "an agent-oriented instruction set to aid them in the manipulation and analysis of their environment");
        super.setup();
    }
}