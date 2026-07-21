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

public class SkillFeature extends Feature {

    public SkillFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    // SkillFeature is pure config — skill configs live in the Agent's feature list.
    // AgentUtility.buildSkills reads them via agent.feature(SKILL)

    public static void buildSkills(final Agent agent, final AiServices<AgentServices> service) {
        final List<Skill> allSkills = new ArrayList<>();

        // Collect skills from features that have the skill() method
        for (final Obj entry : agent.features().asLst().elements().toList()) {
            if (!(entry instanceof Feature f)) {
                agent.logger().warn("non-feature obj in agent features: %s", Obj.Helper.specificTypeId(entry));
                continue;
            }
            final Obj skillObj = f.skill();
            if (skillObj.isNoObj()) continue;
            try {
                final var skill = mSkill.of(skillObj.asRec()).toSkill();
                allSkills.add(skill);
                agent.logger().info("adding %s skill from feature %s", skill.name(), f.tid());
            } catch (final Exception e) {
                agent.logger().warn("failed to build skill from feature %s: %s", f.tid(), e.getMessage());
            }
        }

        // Also collect skills from the SkillFeature config
        final Obj skillFeat = agent.feature(SKILL);
        if (!skillFeat.isNoObj()) {
            final Lst skillsObj = skillFeat.asRec().atLst(SKILL);
            if (!skillsObj.isEmpty()) {
                skillsObj.elements()
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
