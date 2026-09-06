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
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.feat;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * {@code SkillFeature} — the gateway to the agent's skill registry: a
 * single entry point ({@code addSkill}) that every feature publishing a
 * skill goes through; the registry is projected to LC4j skills (the
 * markdown channel) in {@code onBeforeChat}, and each registered skill's
 * tools are forwarded to the tool gateway (the composition point).
 */
public class SkillFeatureTest extends AbstractFeatureTest {

    @Override
    protected SkillFeature feature() {
        return new SkillFeature(new LinkedHashMap<Obj, Obj>(), LLM_SKILL_FEATURE_TID, null);
    }

    // ── publishers → the gateway ────────────────────────────────────

    @Test
    public void testPublisherRegistersSkillWithGateway() {
        final LoopFeature loop = new LoopFeature(new LinkedHashMap<Obj, Obj>(), LLM_LOOP_FEATURE_TID, null);
        final SkillFeature gateway = feature();
        final Agent a = agentWith(loop, gateway);
        loop.registerSkill(a);
        assertEquals(1, gateway.skills().lstValue().size(), "LoopFeature contributes one skill");
        final Rec skillRec = gateway.skills().at(0).asRec();
        assertEquals("loop_feature", skillRec.at(uri(NAME)).uriValue().name(), "skill name should be 'loop_feature'");
        assertFalse(skillRec.at(uri(DESC)).strValue().isBlank(), "skill should have a description");
        assertFalse(skillRec.at(uri(CONTENT)).strValue().isBlank(), "skill should have content");
    }

    @Test
    public void testGatewaySkillsBuildToLC4jSkill() {
        final LoopFeature loop = new LoopFeature(new LinkedHashMap<Obj, Obj>(), LLM_LOOP_FEATURE_TID, null);
        final SkillFeature gateway = feature();
        final Agent a = agentWith(loop, gateway);
        loop.registerSkill(a);
        final var lc4jSkill = gateway.skills().at(0).<mSkill>as().toSkill();
        assertNotNull(lc4jSkill, "a registered skill should build an LC4j skill");
        assertEquals("loop_feature", lc4jSkill.name());
    }

    @Test
    public void testObservationalFeaturesPublishNothing() {
        final SkillFeature gateway = feature();

        final AuditFeature audit = new AuditFeature(new LinkedHashMap<Obj, Obj>(), LLM_AUDIT_FEATURE_TID, null);
        final Agent a = agentWith(audit, gateway);
        audit.onBeforeChat(a);
        assertTrue(gateway.skills().lstValue().isEmpty(), "AuditFeature publishes nothing");

        final ThinkFeature think = new ThinkFeature(new LinkedHashMap<Obj, Obj>(), LLM_THINK_FEATURE_TID, null);
        final Agent b = agentWith(think, gateway);
        think.onBeforeChat(b);
        assertTrue(gateway.skills().lstValue().isEmpty(), "ThinkFeature publishes nothing");

        final AbstractFeature plain = new AbstractFeature(new LinkedHashMap<Obj, Obj>(), feat("plain"), null) {
        };
        final Agent c = agentWith(plain, gateway);
        plain.onBeforeChat(c);
        assertTrue(gateway.skills().lstValue().isEmpty(), "a bare feature publishes nothing");
    }

    @Test
    public void testLoopSkillContainsBlockSyntax() {
        final LoopFeature loop = new LoopFeature(new LinkedHashMap<Obj, Obj>(), LLM_LOOP_FEATURE_TID, null);
        final SkillFeature gateway = feature();
        final Agent a = agentWith(loop, gateway);
        loop.registerSkill(a);
        final String content = gateway.skills().at(0).asRec().at(uri(CONTENT)).strValue();
        assertTrue(content.contains("<<mtron:loop>>"), "skill content should explain the mtron:loop block syntax");
        assertTrue(content.contains("prompt"), "skill content should explain the prompt field");
        assertTrue(content.contains("delay"), "skill content should mention the delay field");
    }

    @Test
    public void testLoopSkillContentMentionsDelayWhenConfigured() {
        final LoopFeature loopWithDelay = new LoopFeature(
                mutableMap(uri("delay"), real(2.0d)), LLM_LOOP_FEATURE_TID, null);
        final SkillFeature gateway = feature();
        final Agent a = agentWith(loopWithDelay, gateway);
        loopWithDelay.registerSkill(a);
        final String content = gateway.skills().at(0).asRec().at(uri(CONTENT)).strValue();
        assertTrue(content.contains("delay"), "skill content should mention the configured delay");
    }

    // ── the registry itself ─────────────────────────────────────────

    @Test
    public void testUpsertBySkillName() {
        final SkillFeature gateway = feature();
        final Agent a = agentWith(gateway);
        gateway.addSkill(mSkill.of(rec(uri(NAME), uri("upsert_skill"), uri(DESC), str("first edition"), uri(CONTENT), str("one"))));
        gateway.addSkill(mSkill.of(rec(uri(NAME), uri("upsert_skill"), uri(DESC), str("second edition"), uri(CONTENT), str("two"))));
        assertEquals(1, gateway.skills().lstValue().size(), "registering the same name upserts");
        assertEquals("second edition", gateway.skills().at(0).asRec().at(uri(DESC)).strValue(), "the later registration wins");
    }

    @Test
    public void testSkillToolsForwardToToolRegistry() {
        // the composition point: a skill's tools flow skill gateway → tool gateway
        final SkillFeature skillFeature = feature();
        final ToolFeature toolFeature = new ToolFeature(new LinkedHashMap<Obj, Obj>(), LLM_TOOL_FEATURE_TID, null);
        final Agent agent = agentWith(skillFeature, toolFeature);
        skillFeature.addSkill(mSkill.of(rec(
                uri(NAME), uri("forwarder_skill"),
                uri(DESC), str("a skill shipping one tool"),
                uri(TOOL), lst(docWrap(instC(f("/m/llm/test/forwarded_tool"), rec0(), (lhs, inst) -> noobj()), "forwards a test payload")))));
        skillFeature.onBeforeChat(agent);
        assertEquals(1, agent.feature(LLM_TOOL_FEATURE_TID).<ToolFeature>as().tools().lstValue().stream().filter(t -> t.asRec().at(NAME).uriValue().toString().contains("list_skills")).count());
        assertEquals(2, agent.feature(LLM_TOOL_FEATURE_TID).<ToolFeature>as().tools().lstValue().size(), "the skill's tool was forwarded to the tool gateway");
    }

    // ── skill aggregation across features ───────────────────────────

    @Test
    public void testSkillsResolvesViaGateway() {
        final LoopFeature loop = new LoopFeature(new LinkedHashMap<Obj, Obj>(), LLM_LOOP_FEATURE_TID, null);
        final SkillFeature gateway = feature();
        final Agent a = agentWith("test-agent", null, loop, gateway);
        loop.registerSkill(a);
        gateway.onBeforeChat(a);
        final Lst skills = mSkill.skills(a);
        assertEquals(1, skills.lstValue().size(), "LoopFeature contributes one skill");
        assertEquals("loop_feature", skills.at(0).asRec().at(uri(NAME)).uriValue().name());
    }

    @Test
    public void testAgentToSkillAggregatesTools() {
        final IterationFeature iteration = new IterationFeature(new LinkedHashMap<Obj, Obj>(), LLM_ITERATION_FEATURE_TID, null);
        final SkillFeature gateway = feature();
        final ToolFeature tools = new ToolFeature(new LinkedHashMap<Obj, Obj>(), LLM_TOOL_FEATURE_TID, null);
        final Agent a = agentWith("test-agent", "with tools", iteration, gateway, tools);
        iteration.registerSkill(a);
        gateway.onBeforeChat(a);
        tools.onBeforeChat(a);
        final mSkill skill = mSkill.agentToSkill(a);
        assertEquals("test-agent", skill.at(uri(NAME)).uriValue().name());
        assertFalse(skill.at(uri(TOOL)).isNoObj(), "IterationFeature exposes tools");
        assertEquals(2, skill.at(uri(TOOL)).asLst().elements().count(), "prev + next tools aggregated");
    }
}
