package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.Model;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.ui.console.StatusLine;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_CHAT_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.type.Agent.feat;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class ChatFeature extends Feature {

    final AtomicBoolean isResponding = new AtomicBoolean(false);

    public ChatFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static ChatFeature chatFeature(final Model model, final Obj response) {
        return new ChatFeature(mutableMap(uri(MODEL), model, uri(RESPONSE), response), LLM_CHAT_FEATURE_TID, null);
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        this.isResponding.set(false);
        return noobj();
    }

    @Override
    public void onPartialResponse(final Agent agent, final Str text) {
        if (!isResponding.getAndSet(true))
            this.logger().none(Graphitty.sillyPrint("\nresponding...", true, true));
        agent.feature(CHAT).asRec().at(f(RESPONSE).extend(TO)).apply(text);
    }
    
    @Override
    public void onCompleteResponse(final Agent agent, final Str text) {
        //this.asRec().at(feat(CHAT, RESPONSE, TO)).apply(text);
    }

    public List<String> getPrompts() {
        return new ArrayList<>();
    }
}
