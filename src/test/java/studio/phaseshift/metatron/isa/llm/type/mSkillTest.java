package studio.phaseshift.metatron.isa.llm.type;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.llm.type.feature.AbstractFeature;
import studio.phaseshift.metatron.isa.llm.type.feature.IterationFeature;
import studio.phaseshift.metatron.isa.llm.type.feature.LoopFeature;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_AGENT_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SKILL_TID;
import static studio.phaseshift.metatron.isa.llm.type.Agent.feat;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Tests for the {@code agent => skill} mapping ({@code as?skill<=agent}) and the
 * supporting {@link mSkill} helpers ({@link mSkill#skills(Agent)},
 * {@link mSkill#agentToSkill(Agent)}, {@link mSkill#tools()}).
 */
public class mSkillTest extends FeatureTest {

    private static Agent agent(final String name, final String desc, final AbstractFeature... features) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(NAME), str(name));
        if (null != desc)
            map.put(uri(DESC), str(desc));
        final List<Obj> objs = new ArrayList<>();
        Collections.addAll(objs, features);
        if (!objs.isEmpty())
            map.put(uri(FEATURE), lst(objs));
        return Agent.agent(rec(map, LLM_AGENT_TID, null));
    }

    @Test
    public void testAgentToSkillNameAndDesc() {
        final mSkill skill = mSkill.agentToSkill(agent("test-agent", "a test agent"));
        assertEquals("test-agent", skill.at(uri(NAME)).uriValue().toString(), "skill name should be the agent name as a uri");
        assertEquals("a test agent", skill.at(uri(DESC)).strValue(), "skill desc should be the agent desc");
        assertTrue(skill.at(uri(TOOL)).isNoObj(), "no features -> no tools");
        assertTrue(skill.at(uri(RESOURCE)).isNoObj(), "no features -> no resources");
        assertNull(skill.vid(), "derived skill should have a null vid");
    }

    @Test
    public void testAgentToSkillDefaultDesc() {
        final mSkill skill = mSkill.agentToSkill(agent("test-agent", null));
        assertEquals("an agent named test-agent", skill.at(uri(DESC)).strValue(), "missing desc falls back to a default");
    }

    @Test
    public void testSkillsResolvesFeatureSkills() {
        final LoopFeature loop = new LoopFeature(new LinkedHashMap<>(), feat("loop"), null) {
        };
        final Lst skills = mSkill.skills(agent("test-agent", null, loop));
        assertEquals(1, skills.elements().count(), "LoopFeature contributes one skill");
        assertEquals("loop", skills.at(0).asRec().at(uri(NAME)).uriValue().toString());
    }

    @Test
    public void testAgentToSkillAggregatesTools() {
        final IterationFeature iteration = new IterationFeature(new LinkedHashMap<>(), feat("iteration"), null) {
        };
        final mSkill skill = mSkill.agentToSkill(agent("test-agent", "with tools", iteration));
        assertEquals("test-agent", skill.at(uri(NAME)).uriValue().toString());
        assertFalse(skill.at(uri(TOOL)).isNoObj(), "IterationFeature exposes tools");
        assertEquals(2, skill.at(uri(TOOL)).asLst().elements().count(), "prev + next tools aggregated");
    }

    @Test
    public void testAnthropicVocab() {
        assertEquals("description", mSkill.ANTHROPIC_VOCAB.to(LLM_SKILL_TID, DESC), "desc maps to description at the anthropic boundary");
        assertEquals("name", mSkill.ANTHROPIC_VOCAB.to(LLM_SKILL_TID, NAME), "unmapped tokens fall back to identity");
    }
}
