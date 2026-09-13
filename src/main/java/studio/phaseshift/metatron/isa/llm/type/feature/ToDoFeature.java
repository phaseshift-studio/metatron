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
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SKILL_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_TODO_FEATURE_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ToDoFeature extends AbstractFeature {
    
    public ToDoFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Set<fURI> requires() {
        return Set.of(LLM_SKILL_FEATURE_TID);
    }

    private void registerSkill(final Agent agent) {
        agent.feature(LLM_SKILL_FEATURE_TID).<SkillFeature>as().addSkill(mSkill.of(rec(mutableMap(uri(NAME), uri(LLM_TODO_FEATURE_TID.name()),
                uri(DESC), str("create a todo list and manage them over a session"),
                uri(CONTENT), str("""
                                  To create a todo-list, tag your thoughts or your response with a todo-list watermark.
                                  The obj should be a lst of recs with the schema of each todo of lst being:
                                        [name=>str::T,desc=>str::T,notes=>lst[str]{?}::T]
                                  
                                      <<mtron:todo>>
                                      [[name=>'a task',desc=>"be sure to do this",notes=>['n1','n2']],
                                       [name=>'task 2',desc=>"another thing",notes=>[,]]]@<%s/a_todo_list>
                                      <</mtron:todo>>
                                  
                                  If you want to add notes to an existing todo list, add the following tag to your thoughts
                                  or your response.
                                  
                                    <<mtron::todo>>
                                    @<s/a_todo_list>.where([name=>'a task']) >>= [notes=> +['another idea']]
                                    <</mtron::todo>>
                                  
                                    <<mtron::todo>>
                                    @<s/a_todo_list>.where([name=>'a task']) >>= [notes=> -['another idea']]
                                    <</mtron::todo>>
                                  
                                  **IMPORTANT**: This skill is about formatting your response, not calling a function. The block
                                  is stripped from what the user sees.
                                  """.formatted(agent.at(ROOT)))))));
    }
}
    
    
