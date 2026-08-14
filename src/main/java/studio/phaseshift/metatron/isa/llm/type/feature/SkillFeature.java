package studio.phaseshift.metatron.isa.llm.type.feature;

import dev.langchain4j.skills.Skill;
import dev.langchain4j.skills.Skills;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.SKILL;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

public class SkillFeature extends AbstractFeature {

    public SkillFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

   /* public static void buildSkills(final Agent agent, final AiServices<AgentServices> service) {
        final List<Skill> allSkills = agent.features().asLst()
                .elements()
                .flatMap(entry -> (entry instanceof Feature feat ?
                        feat.skill(agent) :
                        entry.asRec().at(SKILL).orElse(lst()))
                        .elements()
                        .map(s -> s.isUri() ?
                                mSkill.of(fsSpace.staticObjToFile(s)).toSkill() :
                                mSkill.of(s.apply().asRec()).toSkill()))
                .toList();
        if (allSkills.isEmpty()) {
            agent.logger().warn("no skills available");
            return;
        }
        try {
            final Skills skills = new Skills.Builder().skills(allSkills).build();
            // final ToolProvider skillToolProvider = skills.toolProvider();
            agent.addSystemMessage(
                    "\nYou have access to the following skills:\n" +
                            skills.formatAvailableSkills()
                            + "\nWhen the user's request relates to one of these skills, activate it first using the `activate_skill` tool before proceeding.");
            // service.toolProvider(skillToolProvider);
        } catch (final Exception e) {
            throw MTronException.of("unable to setup skills: %s", e);
        }
    }*/

    @Override
    public Obj onBeforeChat(final Agent agent) {
        final List<Skill> allSkills = agent.features().asLst()
                .elements()
                .flatMap(entry -> (entry instanceof Feature feat ? feat.skill(agent) : entry.asRec().at(SKILL).orElse(lst()))
                        .elements()
                        .map(s -> s.isUri() ?
                                mSkill.of(fsSpace.staticObjToFile(s)).toSkill() :
                                mSkill.of(s.apply().asRec()).toSkill()))
                .toList();
        if (allSkills.isEmpty())
            return noobj();

        try {
            final Skills skills = new Skills.Builder().skills(allSkills).build();
            agent.addSystemMessage(
                    "\nYou have access to the following skills:\n" +
                            skills.formatAvailableSkills()
                            + "\nWhen the user's request relates to one of these skills, activate it first using the `activate_skill` tool before proceeding.");
        } catch (final Exception e) {
            throw MTronException.of("unable to setup skills: %s", e);
        }
        return noobj();
    }
}
