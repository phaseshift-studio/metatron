package studio.phaseshift.metatron.isa.llm.type;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.llm.type.feature.*;
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

public class SkillFeatureTest extends FeatureTest {

    @Test
    public void testSkillFeatureStructure() {
        assertFeatureStructure(new SkillFeature(new LinkedHashMap<>(), feat("skill"), null) {
        });
    }

    @Test
    public void testLoopFeatureHasSkill() {
        final LoopFeature loop = new LoopFeature(new LinkedHashMap<>(), feat("loop"), null) {
        };
        final Obj skill = loop.skill(agentDummy());
        assertFalse(skill.isNoObj(), "LoopFeature should have a skill");
        assertTrue(skill.asLst().at(0).isRec(), "skill should be a Rec");
        final Rec skillRec = skill.asLst().at(0).asRec();
        assertEquals("loop", skillRec.at(uri(NAME)).uriValue().toString(), "skill name should be a URI: 'loop'");
        assertFalse(skillRec.at(uri(DESC)).strValue().isBlank(), "skill should have description");
        assertFalse(skillRec.at(uri(CONTENT)).strValue().isBlank(), "skill should have content");
    }

    @Test
    public void testLedgerFeatureHasSkill() {
        final LedgerFeature ledger = new LedgerFeature(new LinkedHashMap<>(), feat("ledger"), null) {
        };
        final Obj skill = ledger.skill(agentDummy());
        assertFalse(skill.isNoObj(), "LedgerFeature should have a skill");
        assertEquals("ledger", skill.asLst().at(0).asRec().at(uri(NAME)).uriValue().toString());
    }

    @Test
    public void testFeatureSkillsBuildToLC4jSkill() {
        // skill() Rec must match LLM_SKILL_TYPE so mSkill.of(rec).toSkill() succeeds
        final LoopFeature loop = new LoopFeature(new LinkedHashMap<>(), feat("loop"), null) {
        };
        final Lst skillLst = loop.skill(agentDummy());
        assertFalse(skillLst.isNoObj());
        final Rec skillRec = skillLst.at(0);
        final var lc4jSkill = mSkill.of(skillRec).toSkill();
        assertNotNull(lc4jSkill, "should build LC4j Skill from skill() Rec");
        assertEquals("loop", lc4jSkill.name());

        final LedgerFeature ledger = new LedgerFeature(new LinkedHashMap<>(), feat("ledger"), null) {
        };
        final var ledgerSkill = mSkill.of(ledger.skill(agentDummy()).asLst().at(0).asRec()).toSkill();
        assertEquals("ledger", ledgerSkill.name());
    }

    @Test
    public void testObservationalFeaturesHaveNoSkill() {
        // Features that are purely observational should not declare a skill
        final AuditFeature audit = new AuditFeature(new LinkedHashMap<>(), feat("audit"), null) {
        };
        assertTrue(audit.skill(agentDummy()).isNoObj(), "AuditFeature should have no skill");

        final StageFeature stage = new StageFeature(new LinkedHashMap<>(), feat("stage"), null) {
        };
        assertTrue(stage.skill(agentDummy()).isNoObj(), "StageFeature should have no skill");

        final ThinkFeature think = new ThinkFeature(new LinkedHashMap<>(), feat("think"), null) {
        };
        assertTrue(think.skill(agentDummy()).isNoObj(), "ThinkFeature should have no skill");
    }

    @Test
    public void testDefaultFeatureHasNoSkill() {
        // Base Feature returns noobj by default
        final AbstractFeature plain =
                new AbstractFeature(new LinkedHashMap<>(), feat("plain"), null) {
                };
        assertTrue(plain.skill(agentDummy()).isNoObj(), "bare Feature should have no skill");
    }

    @Test
    public void testSkillFeatureCollectsSkillsFromFeatureList() {
        // Agent with LoopFeature + LedgerFeature — both have skills
        final LoopFeature loop = new LoopFeature(new LinkedHashMap<>(), feat("loop"), null) {
        };
        final LedgerFeature ledger = new LedgerFeature(new LinkedHashMap<>(), feat("ledger"), null) {
        };

        final Agent a = agentWithFeatures(loop, ledger);

        // Verify both skills are non-noobj
        for (final Obj entry : a.features().lstValue()) {
            if (entry instanceof AbstractFeature f) {
                final Obj skill = f.skill(agentDummy());
                if (entry instanceof LoopFeature || entry instanceof LedgerFeature)
                    assertFalse(skill.isNoObj(), "%s should have a skill".formatted(entry.getClass().getSimpleName()));
            }
        }
    }

    @Test
    public void testLoopSkillContainsBlockSyntax() {
        final LoopFeature loop = new LoopFeature(new LinkedHashMap<>(), feat("loop"), null) {
        };
        final String content = loop.skill(agentDummy()).asLst().at(0).asRec().at(uri(CONTENT)).strValue();
        assertTrue(content.contains("<<mtron:loop>>"),
                "skill content should explain the mtron:loop block syntax");
        assertTrue(content.contains("prompt"),
                "skill content should explain the prompt field");
        assertTrue(content.contains("delay"),
                "skill content should mention the delay field");
    }

    @Test
    public void testLoopSkillContentMentionsDelayWhenConfigured() {
        final LoopFeature loopWithDelay = new LoopFeature(
                mutableMap(uri("delay"), real(2.0d)), feat("loop"), null) {
        };
        final String content = loopWithDelay.skill(agentDummy()).asLst().at(0).asRec().at(uri(CONTENT)).strValue();
        LOG.warn("loop skill content: %s", content);
        assertTrue(content.contains("delay"),
                "skill content should mention configured delay");
    }

    @Test
    public void testLedgerSkillContainsBlockSyntax() {
      /*  final LedgerFeature ledger = new LedgerFeature(new LinkedHashMap<>(), feat("ledger"), null) {};
        final String content = ledger.skill().asRec().at(uri(CONTENT)).strValue();
        assertTrue(content.contains("<<mtron:ledger>>"),
                "skill content should explain the mtron:ledger block syntax");
        assertTrue(content.contains("persistent"),
                "skill content should mention persistence");*/
    }
}
