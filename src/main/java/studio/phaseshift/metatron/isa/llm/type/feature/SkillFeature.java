package studio.phaseshift.metatron.isa.llm.type.feature;

import dev.langchain4j.skills.Skills;
import dev.langchain4j.service.tool.ToolProvider;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

public class SkillFeature extends Feature {

    private ToolProvider skillToolProvider;

    public SkillFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        if (!agent.skills().isPresent()) return noobj();
        try {
            final Skills skills = new Skills.Builder().skills(
                    agent.skills().get()
                            .elements()
                            .filter(s -> !s.isUri())
                            .map(s -> mSkill.of(s.apply().asRec()).toSkill())
                            .toList()).build();
            this.skillToolProvider = skills.toolProvider();
            agent.addSystemMessage(
                    "\nYou have access to the following skills:\n" +
                            skills.formatAvailableSkills()
                            + "\nWhen the user's request relates to one of these skills, activate it first using the `activate_skill` tool before proceeding.");
        } catch (final Exception e) {
            throw MTronException.of("unable to setup skills: %s", e);
        }
        return noobj();
    }

    public ToolProvider toolProvider() { return this.skillToolProvider; }
}
