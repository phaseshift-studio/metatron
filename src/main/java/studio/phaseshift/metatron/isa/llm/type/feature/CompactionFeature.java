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
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.agent;
import static studio.phaseshift.metatron.isa.llm.type.mModel.model;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CompactionFeature extends AbstractFeature {

    private static final String COMPACTION_PROMPT_1 =
            """
            Summarize the following conversation history concisely.
            
            ## Capture:
            1. **User's Objective** - What the user wants to achieve (be specific and detailed)
            2. **Key Decisions** - What was decided or agreed upon
            3. **Files/Resources** - Files, directories, URLs mentioned
            4. **Actions Taken** - File edits, commands run, etc.
            
            Output a summary message directly. No preamble or explanation.
            
            ## Conversation:
            %s
            """;

    private static final String COMPACTION_PROMPT_2 =
            """
            You have been working on the task described above but have not yet completed it.
            Write a continuation summary that will allow you (or another instance of yourself) to resume work efficiently 
            in a future context window where the conversation history will be replaced with this summary.
            
            Your summary should be structured, concise, and actionable. Include:
            1. **Task Overview**: The user's core request, success criteria, and constraints.
            2. **Current State**: What has been completed, current progress, and any pending steps.\s
            3. **Key Details**: User preferences, domain-specific details, or promises made to the user.\s
            
            Write in a way that enables immediate resumption of the task. Wrap your summary in <summary> tags.\s
            
            ## Conversation:
            %s
            """;

    private static final int EXTRA_MESSAGE_OVERFLOW = 5;

    private Inst compact() {
        return instC(LLM_COMPACTION_FEATURE_TID.extend("compact").dom(ALL.maybe()).rng(COMPACTION_MESSAGE_TID), lst(LLM_AGENT_TYPE, LLM_MODEL_TYPE, lst(LLM_MESSAGE_TYPE)), (lhs, inst) -> {
            // create the conversation history by appending message history together
            final StringBuilder conversationHistory = new StringBuilder();
            final List<Obj> messages = inst.arg(2).lstValue();
            for (final Obj message : messages) {
                final Rec msg = message.asRec();
                conversationHistory.append(msg.toCleanString()).append("\n-----\n");
            }
            // create prompt by using one of the static templates and appending conversation history to it
            final Agent agent = agent(inst.arg(0).asRec());
            final ChatResult result = Agent.Helper.miniTask(agent.at(NAME).strValue() + ":compaction", model(inst.arg(1).asRec()), COMPACTION_PROMPT_2.formatted(conversationHistory.toString()));
            // create compaction message and write it to message store
            final SessionFeature sessionFeature = (SessionFeature) agent.feature(LLM_SESSION_FEATURE_TID);
            final Rec compactionMessage = sessionFeature.store()
                    .addMessage(MessageBuilder
                            .buildCompactionMessage()
                            .time()
                            .text(result.at(RESPONSE).asRec().at(CHAT).toCleanString())
                            .create());
            // add the last n-messages from previous conversation after compaction feature so current context is preserved
            final int messageRange = Math.min(messages.size(), EXTRA_MESSAGE_OVERFLOW);
            for (int i = 0; i < messageRange; i++) {
                sessionFeature.store().addMessage(messages.get(i).asRec());
            }
            return compactionMessage;
        });
    }

    public CompactionFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public void onAgentCtor(final Agent agent) {
        // create mSkill with compact() tool and register with SkillFeature
    }

    @Override
    public Set<fURI> requires() {
        return Set.of(LLM_SKILL_FEATURE_TID);
    }


}
