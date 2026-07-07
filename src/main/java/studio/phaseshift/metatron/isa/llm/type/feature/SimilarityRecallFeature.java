package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.Map;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

public class SimilarityRecallFeature extends Feature {
    public SimilarityRecallFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        final String rawMessage = agent.userMessage();
        if (rawMessage == null || rawMessage.isBlank()) return noobj();
        try {
            final Obj queryVec = agent.embed(str(rawMessage));
            if (queryVec == null || queryVec.isNoObj()) {
                agent.logger().debug("similarity recall: embed returned noobj");
                return noobj();
            }
            agent.logger().debug("similarity recall: query vector computed (simQ pending)");
        } catch (final Exception e) {
            agent.logger().warn("similarity recall failed (non-blocking): %s", e.getMessage());
        }
        return noobj();
    }
}
