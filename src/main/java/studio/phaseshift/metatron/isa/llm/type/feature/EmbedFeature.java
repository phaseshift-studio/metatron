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
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.llm.type.mModel.model;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.vec.vecInstSet.VEC_EMBEDDING_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class EmbedFeature extends AbstractFeature {

    public EmbedFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    protected static final
    String EMBED_FEATURE_INSTRUCTIONS = """
                                        You can choose to have the current chat result vectorized using an embedding model
                                        and stored in space (presumably a vector space). To accomplish this, add
                                        the following watermark block to your response:
                                        
                                            <<mtron:embed>>
                                                [root  => /usr/agent/chat_result,
                                                 model => model::[provider => ollama,
                                                                  protocol => ollama,
                                                                  host     => <http://localhost:11434>,
                                                                  llm      => <qwen3-embedding:4b>]]
                                            <</mtron:embed>>
                                        
                                        If no root is provided, then the embed_feature::T root will be used: %s
                                        If no model is provided, then the embedding model associated with the embed_feature::T will be used:
                                        
                                        %s
                                        
                                        **IMPORTANT**: This skill is about formatting your response, not calling a function.
                                        """;

    @Override
    public Set<fURI> requires() {
        return Set.of(LLM_SKILL_FEATURE_TID);
    }

    /**
     * Register this feature's skill with the SkillFeature gateway — the
     * gateway is the owner of the skill channel; this feature is a
     * contributor.
     */
    public void registerSkill(final Agent agent) {
        if (!agent.hasFeature(LLM_SKILL_FEATURE_TID))
            return;
        final String instructions = EMBED_FEATURE_INSTRUCTIONS.formatted(this.at(ROOT), this.at(MODEL));
        agent.feature(LLM_SKILL_FEATURE_TID).<SkillFeature>as().addSkill(mSkill.of(rec(
                uri(NAME), uri(LLM_EMBED_FEATURE_TID.name()),
                uri(DESC), str("embed chat results into a vector space for later similarity retrieval"),
                uri(CONTENT), str(instructions))));
    }

    @Override
    public void onCompleteResponse(final Agent agent, final ChatResult result) {
        final Rec signal = result.at(BLOCK).orElse(rec()).at(EMBED).orElse(rec0());
        if (signal.isNoObj())
            return;
        final fURI writeLocation = this.at(ROOT).uriValue().extend("_").addQ(INCRQ);
        LOG.info("writing embedding to %s", writeLocation);
        final Lst vector = model(signal.at(MODEL).orElse(this.at(MODEL))).embed(result);
        final Rec embedding = rec(mutableMap(
                uri(OBJ), result,
                uri(EMBED), vector,
                uri(META), agent.hasFeature(LLM_MESSAGE_FEATURE_TID) ?
                        rec(SESSION, agent.feature(LLM_MESSAGE_FEATURE_TID).asRec().at(SESSION)) :
                        noobj()), VEC_EMBEDDING_TID, null);
        LOG.info("embedding complete: %s", embedding);
        final Obj complete = Router.writeToSpace(writeLocation, embedding);
        result.put(EMBED, complete.hasVID() ? auto_from_(complete.vid()) : noobj());
    }

}
