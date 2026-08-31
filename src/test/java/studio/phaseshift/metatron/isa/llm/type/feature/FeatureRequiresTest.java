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
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_AGENT_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SKILL_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_TOOL_FEATURE_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The {@code requires()} closure is validated at agent construction: a
 * feature declaring a hard dependency must find it on the agent, and the
 * skill gateway's dependency on the tool gateway applies transitively
 * through the declared set.
 */
public class FeatureRequiresTest extends AbstractMetatronTest {

    @Test
    public void testSkillWithoutToolFailsConstruction() {
        final SkillFeature skill = new SkillFeature(new LinkedHashMap<Obj, Obj>(), LLM_SKILL_FEATURE_TID, null);
        assertThrows(MTronException.class, () -> build("solo-gateway", skill),
                "the skill gateway requires the tool gateway — constructing without it must fail");
    }

    @Test
    public void testSkillAndToolConstructTogether() {
        final SkillFeature skill = new SkillFeature(new LinkedHashMap<Obj, Obj>(), LLM_SKILL_FEATURE_TID, null);
        final ToolFeature tool = new ToolFeature(new LinkedHashMap<Obj, Obj>(), LLM_TOOL_FEATURE_TID, null);
        final Agent agent = build("twin-gateways", skill, tool);
        assertNotNull(agent, "skill + tool must construct together");
        assertTrue(agent.hasFeature(LLM_SKILL_FEATURE_TID), "skill gateway present");
        assertTrue(agent.hasFeature(LLM_TOOL_FEATURE_TID), "tool gateway present");
    }

    // ── a feature requiring a skill (a publisher) ───────────────────

    /**
     * A minimal publisher: requires the skill gateway, so agent construction
     * must either find the gateway already attached or throw naming the gap.
     */
    private static final class RequiringFeature extends AbstractFeature {
        RequiringFeature(final fURI vid) {
            super(new LinkedHashMap<Obj, Obj>(), LLM_AGENT_TID.extend("requiring").extend("feature"), vid);
        }

        @Override
        public java.util.Set<fURI> requires() {
            return java.util.Set.of(LLM_SKILL_FEATURE_TID);
        }
    }

    @Test
    public void testPublisherWithoutSkillFailsConstruction() {
        final RequiringFeature publisher = new RequiringFeature(null);
        assertThrows(MTronException.class, () -> build("lonely-publisher", publisher),
                "a feature requiring the skill gateway must not construct without it");
    }

    @Test
    public void testPublisherWithSkillConstructs() {
        final RequiringFeature publisher = new RequiringFeature(null);
        final SkillFeature skill = new SkillFeature(new LinkedHashMap<Obj, Obj>(), LLM_SKILL_FEATURE_TID, null);
        final ToolFeature tool = new ToolFeature(new LinkedHashMap<Obj, Obj>(), LLM_TOOL_FEATURE_TID, null);
        final Agent agent = build("well-served-publisher", publisher, skill, tool);
        assertNotNull(agent, "publisher + its dependencies must construct");
        assertTrue(agent.hasFeature(LLM_SKILL_FEATURE_TID), "required gateway present");
    }

    private static Agent build(final String name, final Obj... features) {
        final java.util.Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(NAME), str(name));
        map.put(uri(FEATURE), lst(features));
        return Agent.agent(rec(map, LLM_AGENT_TID, null));
    }
}
