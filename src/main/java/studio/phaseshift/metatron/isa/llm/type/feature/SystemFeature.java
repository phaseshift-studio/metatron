package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatSessionStore;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SESSION_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.SYSTEM_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.noobjRec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class SystemFeature extends Feature {

    public SystemFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        // System messages are accumulated via agent.addSystemMessage() —
        // this feature ensures they are mirrored to the session store.
        final String systemMessage = String.join("\n", agent.getSystemMessages());
        if (!systemMessage.isBlank() && agent.hasFeature(LLM_SESSION_FEATURE_TID)) {
            final Rec sess = agent.feature(SESSION).orElse(noobjRec());
            if (!sess.isNoObj() && sess.vid() != null) {
                try {
                    final Map<Obj, Obj> systemMap = new LinkedHashMap<>();
                    systemMap.put(uri(TEXT), str(systemMessage));
                    final Rec systemRec = rec(systemMap, SYSTEM_MESSAGE_TID, null);
                    final Space space = Router.global().getSpaceFor(sess.vid());
                    SpaceChatSessionStore.mirrorSystemMessage(space, sess.vid(), systemRec);
                } catch (final Exception e) {
                    this.logger().warn("system message mirror failed: %s", e.getMessage());
                }
            }
        }
        return noobj();
    }
}
