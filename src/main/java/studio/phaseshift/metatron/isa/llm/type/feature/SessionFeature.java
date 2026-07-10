package studio.phaseshift.metatron.isa.llm.type.feature;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.TokenCountEstimator;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.res;
import static studio.phaseshift.metatron.isa.llm.type.Agent.feat;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class SessionFeature extends Feature {

    public SessionFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    // SessionFeature is pure config — the session Ref lives in its JVM at uri(SESSION).
    // AgentUtility.buildSession reads it via agent.feature(SESSION).at(uri(SESSION))

    @Override
    public void onCompleteResponse(final Agent agent, final Str response) {
        // Mirror the completed response to the session store.
        final Obj chatResult = agent.at(res(CHAT));
        if (chatResult.isNoObj()) return;

        final Obj sessionConfig = agent.feature(SESSION);
        if (sessionConfig.isNoObj()) return;

        // Mirror handled by SpaceChatSessionStore — deferred until session mirroring
        // is fully refactored into SessionFeature.
    }

    public static class DefaultTokenCountEstimator implements TokenCountEstimator {

        private static final DefaultTokenCountEstimator INSTANCE = new DefaultTokenCountEstimator();

        @Override
        public int estimateTokenCountInText(final String text) {
            return Math.round(((float) text.length()) / 4.0f);
        }

        @Override
        public int estimateTokenCountInMessage(final ChatMessage message) {
            return this.estimateTokenCountInText(message.toString());
        }

        @Override
        public int estimateTokenCountInMessages(final Iterable<ChatMessage> messages) {
            return IteratorUtil.stream(messages).mapToInt(this::estimateTokenCountInMessage).sum();
        }

        public static DefaultTokenCountEstimator singleton() {
            return INSTANCE;
        }
    }
}
