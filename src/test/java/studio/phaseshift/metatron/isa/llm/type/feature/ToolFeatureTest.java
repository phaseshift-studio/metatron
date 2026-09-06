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
 * MERCHANTABILITY or FITNESS TO A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.llm.type.feature;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SKILL_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_TOOL_FEATURE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * {@code ToolFeature} — the gateway to the agent's tool registry: a single
 * entry point ({@code addTool}) that anything publishing a tool goes through
 * (features directly, or the skill gateway on behalf of a skill's tools);
 * the registry is projected onto the agent's LC4j tool bag in
 * {@code onBeforeChat}.
 */
public class ToolFeatureTest extends AbstractFeatureTest {

    @Override
    protected ToolFeature feature() {
        return new ToolFeature(new LinkedHashMap<Obj, Obj>(), LLM_TOOL_FEATURE_TID, null);
    }

    // ── the registry itself ─────────────────────────────────────────

    @Test
    public void testUpsertByToolName() {
        final ToolFeature tf = feature();
        tf.addTool(mTool.tool(rec(uri(INST), instLambda((lhs, inst) -> noobj()), uri(NAME), uri("alpha_tool"), uri(DESC), str("first edition"))));
        tf.addTool(mTool.tool(rec(uri(INST), instLambda((lhs, inst) -> noobj()), uri(NAME), uri("alpha_tool"), uri(DESC), str("second edition"))));
        tf.addTool(mTool.tool(rec(uri(INST), instLambda((lhs, inst) -> noobj()), uri(NAME), uri("beta_tool"), uri(DESC), str("steady state"))));
        final Lst tools = tf.tools();
        assertEquals(2, tools.lstValue().size(), "registering the same name upserts");
        assertEquals("second edition", tools.at(0).asRec().at(uri(DESC)).strValue(), "the later registration wins");
    }

    // ── publishing + projection ─────────────────────────────────────

    @Test
    public void testToolFeaturePublishesUsageSkill() {
        final ToolFeature tf = feature();
        final SkillFeature gateway = new SkillFeature(new LinkedHashMap<Obj, Obj>(), LLM_SKILL_FEATURE_TID, null);
        final Agent a = agentWith(gateway, tf);
        tf.onBeforeChat(a);
        assertTrue(gateway.skills().lstValue().stream()
                        .anyMatch(s -> "tool_feature".equals(s.asRec().at(uri(NAME)).uriValue().name())),
                "onBeforeChat should publish the tool feature's usage skill to the skill gateway");
    }

    @Test
    public void testDirectAddToolLandsInProjection() {
        final ToolFeature tf = feature();
        final Agent a = agentWith(tf);
        tf.addTool(mTool.tool(rec(uri(INST), instLambda((lhs, inst) -> noobj()), uri(NAME), uri("direct_tool"), uri(DESC), str("a directly registered tool"))));
        tf.onBeforeChat(a);
        assertEquals(2, tf.tools().lstValue().size(), "directly added tools persist in the registry across chats");
    }
}
