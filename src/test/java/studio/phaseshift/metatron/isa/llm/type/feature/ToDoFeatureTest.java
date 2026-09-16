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

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Rec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class ToDoFeatureTest extends AbstractFeatureTest {

    @Override
    protected ToDoFeature feature() {
        return new ToDoFeature(mutableMap(), LLM_LEDGER_FEATURE_TID, null);
    }

    @Test
    public void testOnBeforeChatCreatesToDoInSpace() {
        final ToDoFeature todo = feature();
        // SystemFeature receives the injected system message (features contribute
        // through it via agent.feature(SYSTEM).<SystemFeature>as()).
        final SystemFeature system = new SystemFeature(mutableMap(), LLM_SYSTEM_FEATURE_TID, null);
        final Agent agent = agentWith(todo, system);
        todo.onBeforeChat(agent);
        assertTrue(todo.todos(agent).isLst());

    }

    @Test
    public void testOnBeforeChatDebilitatedWithoutSystemFeature() {
        // Without a SystemFeature, the ledger feature proceeds debilitated: it still
        // creates the ledger (its own work), logs the missing cross-feature requirement,
        // and does NOT crash.
        final ToDoFeature todoFeature = feature();
        final Agent a = agentWith(todoFeature);   // no SystemFeature
        todoFeature.onBeforeChat(a);
    }

    @Test
    public void testLedgerSkillWellFormed() {
        final ToDoFeature todoFeature = feature();
        final SkillFeature skillFeature = new SkillFeature(mutableMap(), LLM_SKILL_FEATURE_TID, null);
        final Agent agent = agentWith(todoFeature, skillFeature);
        todoFeature.onBeforeChat(agent);
        final Rec skill = skillFeature.skills().at(0).asRec();
        assertEquals("todo_feature", skill.at(uri(NAME)).uriValue().name(), "skill name should be 'todo_feature'");
    }
}
