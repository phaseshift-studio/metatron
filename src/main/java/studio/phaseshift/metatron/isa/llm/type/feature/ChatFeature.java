package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Model;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.MODEL;
import static studio.phaseshift.metatron.Tokens.RESPONSE;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_CHAT_FEATURE_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class ChatFeature extends Feature {
    public ChatFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static ChatFeature chatFeature(final Rec chatFeature) {
        return new ChatFeature(chatFeature.jvm(), LLM_CHAT_FEATURE_TID, chatFeature.vid());
    }

    public static ChatFeature chatFeature(final Model model, final Obj response) {
        return new ChatFeature(mutableMap(uri(MODEL), model, uri(RESPONSE), response), LLM_CHAT_FEATURE_TID, null);
    }
}
