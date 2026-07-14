package studio.phaseshift.metatron.isa.llm.type.feature;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.service.AiServices;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatSessionStore;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.AgentServices;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.type.Agent.res;
import static studio.phaseshift.metatron.isa.llm.type.Agent.feat;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class SessionFeature extends Feature {

    private SpaceChatSessionStore store = null;
    private ChatMemory memory = null;

    public SessionFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public SpaceChatSessionStore store() {
        return this.store;
    }

    public ChatMemory memory() {
        return this.memory;
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        final fURI sessionID = this.at(SESSION).uriValue();
        final Rec session = Router.readFromSpace(sessionID).orElse(rec());
        try {
            final Space space = Router.global().getSpaceFor(sessionID);
            final SpaceChatSessionStore store = new SpaceChatSessionStore(agent, space);
            if (session.at(ALGORITHM).isNoObj() || session.at(ALGORITHM).asRec().at(NAME).isNoObj())
                throw MTronException.of("no session memory algorithm provided: token_window or message_window");
            final int max = session.at(ALGORITHM).asRec().at(MAX).orElse(jnt(50)).intValue().intValue();
            if (session.at(ALGORITHM).asRec().at(NAME).uriValue().equals(f("token_window"))) {
                this.memory = TokenWindowChatMemory.builder()
                        .alwaysKeepSystemMessageFirst(true)
                        .maxTokens(max, DefaultTokenCountEstimator.singleton())
                        .id(sessionID)
                        .chatMemoryStore(store)
                        .build();
            } else if (session.at(ALGORITHM).asRec().at(NAME).uriValue().equals(f("message_window"))) {
                this.memory = MessageWindowChatMemory.builder()
                        .alwaysKeepSystemMessageFirst(true)
                        .maxMessages(max)
                        .id(sessionID)
                        .chatMemoryStore(store)
                        .build();
            } else {
                throw MTronException.of("unknown session memory algorithm: %s", this.at(ALGORITHM).asRec().at(NAME));
            }
        } catch (final Exception e) {
            throw MTronException.of("unable to setup session: %s", e);
        }
        return noobj();
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
