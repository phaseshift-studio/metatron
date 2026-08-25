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
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.feat;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class SkillFeatureTest extends AbstractFeatureTest {

    @Override
    protected SkillFeature feature() {
        return new SkillFeature(new LinkedHashMap<>(), feat("skill"), null) {
        };
    }

    @Test
    public void testLoopFeatureHasSkill() {
        final LoopFeature loop = new LoopFeature(new LinkedHashMap<>(), feat("loop"), null) {
        };
        final Obj skill = loop.skill(agentDummy());
        assertFalse(skill.isNoObj(), "LoopFeature should have a skill");
        final Rec skillRec = skill.asLst().at(0).asRec();
        assertEquals("loop", skillRec.at(uri(NAME)).uriValue().name(), "skill name should be 'loop'");
        assertFalse(skillRec.at(uri(DESC)).strValue().isBlank(), "skill should have description");
        assertFalse(skillRec.at(uri(CONTENT)).strValue().isBlank(), "skill should have content");
    }

    @Test
    public void testFeatureSkillsBuildToLC4jSkill() {
        final LoopFeature loop = new LoopFeature(new LinkedHashMap<>(), feat("loop"), null) {
        };
        final var lc4jSkill = mSkill.of(loop.skill(agentDummy()).asLst().at(0).asRec()).toSkill();
        assertNotNull(lc4jSkill, "should build LC4j Skill from skill() Rec");
        assertEquals("loop", lc4jSkill.name());
    }

    @Test
    public void testObservationalFeaturesHaveNoSkill() {
        final AuditFeature audit = new AuditFeature(new LinkedHashMap<>(), feat("audit"), null) {
        };
        assertTrue(audit.skill(agentDummy()).lstValue().isEmpty(), "AuditFeature should have no skill");
        final ThinkFeature think = new ThinkFeature(new LinkedHashMap<>(), feat("think"), null) {
        };
        assertTrue(think.skill(agentDummy()).lstValue().isEmpty(), "ThinkFeature should have no skill");
        final AbstractFeature plain = new AbstractFeature(new LinkedHashMap<>(), feat("plain"), null) {
        };
        assertTrue(plain.skill(agentDummy()).lstValue().isEmpty(), "bare Feature should have no skill");
    }

    @Test
    public void testLoopSkillContainsBlockSyntax() {
        final LoopFeature loop = new LoopFeature(new LinkedHashMap<>(), feat("loop"), null) {
        };
        final String content = loop.skill(agentDummy()).asLst().at(0).asRec().at(uri(CONTENT)).strValue();
        assertTrue(content.contains("<<mtron:loop>>"), "skill content should explain the mtron:loop block syntax");
        assertTrue(content.contains("prompt"), "skill content should explain the prompt field");
        assertTrue(content.contains("delay"), "skill content should mention the delay field");
    }

    @Test
    public void testLoopSkillContentMentionsDelayWhenConfigured() {
        final LoopFeature loopWithDelay = new LoopFeature(
                mutableMap(uri("delay"), real(2.0d)), feat("loop"), null) {
        };
        final String content = loopWithDelay.skill(agentDummy()).asLst().at(0).asRec().at(uri(CONTENT)).strValue();
        assertTrue(content.contains("delay"), "skill content should mention configured delay");
    }

    // ── Skill aggregation across features (folded from mSkillTest) ──

    @Test
    public void testSkillsResolvesFeatureSkills() {
        final LoopFeature loop = new LoopFeature(new LinkedHashMap<>(), feat("loop"), null) {};
        final Lst skills = mSkill.skills(agentWith("test-agent", null, loop));
        assertEquals(1, skills.elements().count(), "LoopFeature contributes one skill");
        assertEquals("loop", skills.at(0).asRec().at(uri(NAME)).uriValue().name());
    }

    @Test
    public void testAgentToSkillAggregatesTools() {
        final IterationFeature iteration = new IterationFeature(new LinkedHashMap<>(), feat("iteration"), null) {};
        final mSkill skill = mSkill.agentToSkill(agentWith("test-agent", "with tools", iteration));
        assertEquals("test-agent", skill.at(uri(NAME)).uriValue().name());
        assertFalse(skill.at(uri(TOOL)).isNoObj(), "IterationFeature exposes tools");
        assertEquals(2, skill.at(uri(TOOL)).asLst().elements().count(), "prev + next tools aggregated");
    }
}
