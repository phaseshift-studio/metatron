package studio.phaseshift.metatron.isa.llm.type.feature.service;

import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.m.type.Lst;

/**
 * The skill capability — the agent's skill registry.  A feature providing this service
 * owns the collection of registered skills (markdown content + their tools).
 */
public interface SkillService {

    /**
     * Register a skill (markdown content + its tools) with the agent's skill channel.
     *
     * @param skill the skill to register
     */
    void addSkill(mSkill skill);

    /**
     * The registered skills.
     *
     * @return the skill list
     */
    Lst skills();
}
