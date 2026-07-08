package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.Model;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_CHAT_FEATURE_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class ChatFeature extends Feature {

    public ChatFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    // ChatFeature is pure config — model, response, format live in its JVM.
    // AgentUtility reads them directly via agent.feature(CHAT).at(...)

    public static ChatFeature chatFeature(final Agent agent, final Rec chatFeature) {
        return new ChatFeature(chatFeature.jvm(), LLM_CHAT_FEATURE_TID, chatFeature.vid());
    }

    public static ChatFeature chatFeature(final Agent agent) {
        final Obj feature = agent.feature(CHAT);
        if (!feature.isNoObj())
            return new ChatFeature(feature.asRec().jvm(), LLM_CHAT_FEATURE_TID, feature.vid());
        throw MTronException.of("agent does not have a chat feature: %s", agent.vidOrTid());
    }

    public static ChatFeature chatFeature(final Model model, final Obj response) {
        return new ChatFeature(mutableMap(uri(MODEL), model, uri(RESPONSE), response), LLM_CHAT_FEATURE_TID, null);
    }

    public List<String> getPrompts() {
        return new ArrayList<>();
    }
}
