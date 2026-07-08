package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.Map;

public class SkillFeature extends Feature {

    public SkillFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    // SkillFeature is pure config — skill configs live in the Agent's feature list.
    // AgentUtility.buildSkills reads them via agent.feature(SKILL)
}
