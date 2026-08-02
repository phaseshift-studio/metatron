package studio.phaseshift.metatron.isa.llm.type.feature;

import dev.langchain4j.service.AiServices;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatSessionStore;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.AgentServices;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.SESSION;
import static studio.phaseshift.metatron.Tokens.TEXT;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.SYSTEM_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class SystemFeature extends AbstractFeature {

    public SystemFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }


    public static void buildSystemMessage(final Agent agent, final AiServices<AgentServices> service) {
        final String systemMessage = String.join("\n", agent.getSystemMessages());
        if (!systemMessage.isBlank())
            service.systemMessage(systemMessage);
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        // System messages are accumulated via agent.addSystemMessage() —
        // this feature ensures they are mirrored to the session store.
        final String systemMessage = String.join("\n", agent.getSystemMessages());
        if (!systemMessage.isBlank() && agent.hasFeature(SESSION)) {
            final Rec sess = agent.feature(SESSION).orElse(noobjRec());
            if (!sess.isNoObj() && sess.vid() != null) {
                try {
                    final Map<Obj, Obj> systemMap = new LinkedHashMap<>();
                    systemMap.put(uri(TEXT), str(systemMessage));
                    final Rec systemRec = rec(systemMap, SYSTEM_MESSAGE_TID, null);
                    SpaceChatSessionStore.mirrorSystemMessage(agent, sess.vid(), systemRec);
                } catch (final Exception e) {
                    this.logger().warn("system message mirror failed: %s", e.getMessage());
                }
            }
        }
        return noobj();
    }
}
