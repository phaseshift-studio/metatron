package studio.phaseshift.metatron.isa.llm.type.feature;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.skills.Skill;
import dev.langchain4j.skills.Skills;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.AgentServices;
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.SKILL;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst0;

public class SkillFeature extends AbstractFeature {

    public SkillFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static void buildSkills(final Agent agent, final AiServices<AgentServices> service) {
        final List<Skill> allSkills = new ArrayList<>();

        // Collect skills from features that have the skill() method
        for (final Obj entry : agent.features().asLst().elements().toList()) {
            if (entry != agent.feature(SKILL)) {
                Lst skillObj = lst0().zero();
                if (entry instanceof Feature feat)
                    skillObj = feat.skill(agent);
                if (skillObj.isNoObj() || skillObj.asLst().isEmpty())
                    skillObj = entry.asRec().at(SKILL).orElse(lst());
                if (skillObj.isNoObj() || skillObj.asLst().isEmpty()) continue;
                try {
                    skillObj.asLst().elements().forEach(s -> {
                        final Skill skill = (s.isStr() ? mSkill.of(s.asStr()) : mSkill.of(s.asRec())).toSkill();
                        allSkills.add(skill);
                        agent.logger().info("adding %s skill from feature %s", skill.name(), entry.tid());
                    });
                } catch (final Exception e) {
                    agent.logger().warn("failed to build skill from feature %s: %s", entry.tid(), e.getMessage());
                }
            }
        }

        // Also collect skills from the SkillFeature config
        final Obj skillFeat = agent.feature(SKILL);
        if (!skillFeat.isNoObj()) {
            final Lst skillLst = skillFeat.asRec().at(SKILL).orElse(lst());
            if (!skillLst.isEmpty()) {
                skillLst.elements()
                        .map(s -> s.isUri() ?
                                mSkill.of(fsSpace.staticObjToFile(s)).toSkill() :
                                mSkill.of(s.apply().asRec()).toSkill())
                        .forEach(allSkills::add);
            }
        }

        if (allSkills.isEmpty()) return;

        try {
            final Skills skills = new Skills.Builder().skills(allSkills).build();
            final ToolProvider skillToolProvider = skills.toolProvider();
            agent.addSystemMessage(
                    "\nYou have access to the following skills:\n" +
                            skills.formatAvailableSkills()
                            + "\nWhen the user's request relates to one of these skills, activate it first using the `activate_skill` tool before proceeding.");
            service.toolProvider(skillToolProvider);
        } catch (final Exception e) {
            throw MTronException.of("unable to setup skills: %s", e);
        }
    }
}
