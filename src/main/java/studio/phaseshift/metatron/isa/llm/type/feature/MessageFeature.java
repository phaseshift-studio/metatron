package studio.phaseshift.metatron.isa.llm.type.feature;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.service.AiServices;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatSessionStore;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.AgentServices;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class MessageFeature extends AbstractFeature {

    private SpaceChatSessionStore store = null;
    private ChatMemory memory = null;

    public MessageFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public SpaceChatSessionStore store() {
        return this.store;
    }

    public ChatMemory memory() {
        return this.memory;
    }

    public static void buildSession(final Agent agent, final AiServices<AgentServices> service) {
        if (agent.hasFeature(LLM_MESSAGE_FEATURE_TID))
            service.chatMemory(agent.feature(LLM_MESSAGE_FEATURE_TID).<MessageFeature>as().memory()).storeRetrievedContentInChatMemory(true);
        if (agent.hasFeature(LLM_TOOL_FEATURE_TID)) {
            ToolFeature toolFeature = agent.feature(LLM_TOOL_FEATURE_TID).as();
            toolFeature.addTool(mTool.tool(instC(f("add_message").dom(ALL.maybe()).rng(MESSAGE_TID), lst(REC_TYPE), (lhs, inst) -> {
                return agent.feature(LLM_MESSAGE_FEATURE_TID).<MessageFeature>as().addMessage(agent, inst.arg(0).asRec());
            })));
        }
    }

    /**
     * Create a session policy record with the canonical fields.
     * Used by {@link #onBeforeChat} to persist new sessions and by tests
     * that need to pre-seed a session before the first chat.
     */
    public static Rec createSession(final String agentName, final String userName,
                                    final String algorithmName, final int max) {
        return rec(mutableMap(
                uri(AGENT), str(agentName),
                uri(USER), str(userName),
                uri(ALGORITHM), rec(mutableMap(
                        uri(NAME), uri(algorithmName),
                        uri(MAX), jnt(max)
                ))
        ));
    }

    public Rec addMessage(final Agent agent, final Rec message) {
        MessageBuilder.build(message.tid()).copy(message.jvm()).time().create();
        // Adding a message is not a new turn — the session's chat_id counter
        // is untouched and the store is built for the turn already in flight.
        final SpaceChatSessionStore store = this.createStore(agent, agent.chatId());
        return store.addMessage(message);
    }

    /**
     * The session store for one chat turn.  {@code chatId} is passed in rather
     * than computed here: the counter belongs to the session rec (advanced once
     * per turn in {@link #onBeforeChat}), and a store is built from more than
     * one place — computing it in the factory made {@code add_message} look
     * like a new turn.
     */
    private SpaceChatSessionStore createStore(final Agent agent, final int chatId) {
        final fURI sessionID = this.at(SESSION).uriValue();
        // the session's home <memoryRoot>/session/<id> is the ledger's root —
        // the store is told which memory system it serves (not derived
        // from the agent's own root)
        final Space space = Router.global().getSpaceFor(sessionID);
        return new SpaceChatSessionStore(agent, space, agent.chatDepth(), chatId, SpaceChatSessionStore.memoryRootOf(sessionID));
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        final fURI sessionID = this.at(SESSION).uriValue();
        Rec session = Router.readFromSpace(sessionID).orElse(rec());
        try {
            // Ensure session exists in space with required fields
            if (session.at(ALGORITHM).isNoObj()) {
                if (!this.asRec().at(ALGORITHM).isNoObj()) {
                    // Create session from feature config
                    final Rec algo = this.asRec().at(ALGORITHM).asRec();
                    session = createSession(
                            agent.at(NAME).orElse(str("default")).strValue(),
                            "default",
                            algo.at(NAME).uriValue().name(),
                            algo.at(MAX).orElse(jnt(50)).intValue().intValue()
                    );
                }
            } else {
                // Session exists — patch missing agent/user if needed
                if (session.at(AGENT).isNoObj())
                    session.at(AGENT, agent.at(NAME).orElse(str("default")), MUTABLE);
                if (session.at(USER).isNoObj())
                    session.at(USER, str("default"), MUTABLE);
            }
            // The monotonic per-session execution counter — advanced once per
            // chat() call and written with the session so chat_id is a real
            // turn boundary: SpaceChatSessionStore scopes cross-turn isolation
            // by it, ToolPairGate keys its parked groups on it, and
            // mcpMessageServer uses it to bound mid-chat message reads.  A
            // counter that never advances silently collapses every one of those
            // to "one turn".
            final int chatId = session.at(uri(CHAT_ID)).orElse(jnt(0)).intValue().intValue() + 1;
            session.at(uri(CHAT_ID), jnt(chatId), MUTABLE);
            agent.setCurrentChatId(chatId);
            // one write per turn: creates the session on the first chat and
            // advances the counter on every one after it
            if (!session.at(ALGORITHM).isNoObj())
                Router.writeToSpace(sessionID, session.selfVID(sessionID));
            this.store = this.createStore(agent, chatId);
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
