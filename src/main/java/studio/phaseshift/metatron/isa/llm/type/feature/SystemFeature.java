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
import static studio.phaseshift.metatron.isa.llm.llmInstSet.SYSTEM_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class SystemFeature extends Feature {

    private String systemMessage;

    public SystemFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        // Accumulate system messages from all features (user-added + capability-generated)
        this.systemMessage = String.join("\n", agent.getSystemMessages());
        if (!systemMessage.isBlank()) {
            // Mirror to typed table — fire and forget
            final Rec sess = agent.session();
            if (!sess.isNoObj() && sess.vid() != null) {
                try {
                    final Map<Obj, Obj> systemMap = new LinkedHashMap<>();
                    systemMap.put(uri(TEXT), str(this.systemMessage));
                    systemMap.put(uri(TYPE), uri("SYSTEM"));
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

    public String systemMessage() { return this.systemMessage; }
}
