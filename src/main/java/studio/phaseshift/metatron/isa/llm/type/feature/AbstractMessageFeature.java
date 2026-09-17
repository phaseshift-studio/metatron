/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.llm.type.feature;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
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
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;
import studio.phaseshift.metatron.isa.llm.type.feature.service.MessageService;
import studio.phaseshift.metatron.isa.llm.type.feature.service.ToolService;

/**
 * The shared message/session substrate: owns the ledger store, the LC4j chat memory, and the
 * per-turn session setup.  Every provider ({@code token}, {@code window}) offers the same
 * {@link MessageService} ({@code LLM_MESSAGE_SERVICE_TID}); they differ only in the aggregation
 * algorithm — how messages are trimmed back to a budget — which is the one abstract seam
 * {@link #buildMemory} (plus the descriptive {@link #algorithmName}).
 */
public abstract class AbstractMessageFeature extends AbstractFeature implements MessageService {

    @Override
    public Set<fURI> offers() {
        return Set.of(LLM_MESSAGE_SERVICE_TID);
    }

    private SpaceChatSessionStore store = null;
    private ChatMemory memory = null;

    protected AbstractMessageFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public fURI sessionVID() {
        return this.at(SESSION).uriValue();
    }

    @Override
    public fURI root(final Agent agent) {
        return this.getRoot(agent);
    }

    /**
     * The message ledger root — stable across providers ({@code message}, not the provider's
     * own tid segment {@code window}/{@code token}).
     */
    @Override
    public fURI getRoot(final Agent agent) {
        if (this.has(ROOT))
            return this.at(ROOT).uriValue();
        if (agent.has(ROOT))
            return agent.at(ROOT).uriValue().extend("message");
        throw MTronException.of("no root uri found on feature nor agent: %s", this.tid());
    }

    @Override
    public SpaceChatSessionStore store() {
        return this.store;
    }

    @Override
    public ChatMemory memory() {
        return this.memory;
    }

    /**
     * The configured aggregation budget ({@code algorithm => [max => N]}), in the provider's own
     * unit (tokens for {@code token}, messages for {@code window}).  The store sizes its window
     * from this.
     */
    @Override
    public int max() {
        final Obj algo = this.at(ALGORITHM);
        if (!algo.isNoObj() && algo.isRec()) {
            final Obj maxVal = algo.asRec().at(MAX);
            if (!maxVal.isNoObj() && maxVal.isInt())
                return maxVal.intValue().intValue();
        }
        return 50;
    }

    // ── Provider seams ───────────────────────────────────────────────

    /**
     * Build the LC4j chat memory for the given session and budget — the provider-specific
     * aggregation algorithm.
     */
    protected abstract ChatMemory buildMemory(fURI sessionID, int max, SpaceChatSessionStore store);

    /**
     * The descriptive name this provider stamps on the session policy (e.g. {@code token_window}).
     */
    protected abstract String algorithmName();

    public static void buildSession(final Agent agent, final AiServices<AgentServices> service) {
        if (agent.service(MessageService.class).isPresent())
            service.chatMemory(agent.requireService(MessageService.class).memory()).storeRetrievedContentInChatMemory(true);
        if (agent.hasFeature(LLM_TOOL_FEATURE_TID)) {
            final ToolService toolFeature = agent.requireService(ToolService.class);
            toolFeature.addTool(mTool.tool(instC(f("add_message").dom(ALL.maybe()).rng(LLM_MESSAGE_TID), lst(REC_TYPE), (lhs, inst) -> {
                return agent.requireService(MessageService.class).addMessage(agent, inst.arg(0).asRec());
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

    @Override
    public Rec addMessage(final Agent agent, final Rec message) {
        return MessageBuilder.build(message.tid()).time().copy(message.jvm()).create(this.getRoot(agent).extend("_").addQ(INCRQ));
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
        final Space space = Router.global().getSpaceFor(sessionID);
        return new SpaceChatSessionStore(agent, space, agent.chatDepth(), chatId, SpaceChatSessionStore.memoryRootOf(sessionID));
    }

    @Override
    public int advanceChatId(final Agent agent) {
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
                            this.algorithmName(),
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
            return chatId;
        } catch (final Exception e) {
            throw MTronException.of("unable to setup session: %s", e);
        }
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        final fURI sessionID = this.at(SESSION).uriValue();
        Rec session = Router.readFromSpace(sessionID).orElse(rec());
        try {
            // the turn id was advanced in the pre-chat phase (advanceChatId) — read it back
            final int chatId = agent.chatId();
            this.store = this.createStore(agent, chatId);
            if (session.at(ALGORITHM).isNoObj())
                throw MTronException.of("no message aggregation budget configured on the session");
            final int max = session.at(ALGORITHM).asRec().at(MAX).orElse(jnt(50)).intValue().intValue();
            this.memory = this.buildMemory(sessionID, max, this.store);
        } catch (final Exception e) {
            throw MTronException.of("unable to setup session: %s", e);
        }
        return noobj();
    }

    /**
     * A character-count token estimator — the default for token-windowed memory.
     */
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
