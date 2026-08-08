package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.Model;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Str;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.res;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class ChatFeature extends AbstractFeature {

    public ChatFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static ChatFeature chatFeature(final Model model, final Obj response) {
        return new ChatFeature(mutableMap(uri(MODEL), model, uri(RESPONSE), response), LLM_CHAT_FEATURE_TID, null);
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        final String userMessage = agent.userMessage();
        if (userMessage == null || userMessage.isBlank())
            return noobj();

        try {
            MessageBuilder.build(USER_MESSAGE_TID)
                    .text(userMessage)
                    .contents(userMessage)
                    .time()
                    .session(agent.hasFeature(SESSION)
                            ? agent.feature(SESSION).asRec().at(SESSION).uriValue()
                            : null)
                    .create(agent.at(ROOT).uriValue().extend(MESSAGE)
                            .extend("_").addQ(INCRQ));
        } catch (final Exception e) {
            this.logger().warn("user message write failed (non-blocking): %s", e.getMessage());
        }
        return noobj();
    }

    @Override
    public void onPartialResponse(final Agent agent, final Str text) {
        agent.feature(CHAT).asRec().at(f(RESPONSE).extend(TO)).apply(text);
    }

    @Override
    public void onCompleteResponse(final Agent agent, final Str text) {
        agent.at(res(CHAT, RESPONSE), text, MUTABLE);
        // AiMessages are persisted by SpaceChatSessionStore.updateMessages(),
        // which catches both intermediate tool_call responses (that never
        // reach TokenStream.onCompleteResponse) and the final text response.
    }
}
