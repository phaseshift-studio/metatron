package studio.phaseshift.metatron.isa.llm.type.feature.service;

import dev.langchain4j.memory.ChatMemory;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatSessionStore;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.feature.Feature;
import studio.phaseshift.metatron.isa.m.type.Rec;

/**
 * The session/ledger capability — the agent's chat memory and its backing space store.
 */
public interface MessageService {

    /**
     * Append a message rec to the agent's ledger (time-stamped and sequenced).
     *
     * @param agent   the agent owning the ledger
     * @param message the message rec to persist
     * @return the created message rec
     */
    Rec addMessage(final Agent agent, final Rec message);

    /**
     * The backing session store — the space the ledger lives in.
     */
    SpaceChatSessionStore store();

    /**
     * The LC4j chat memory the agent's current turn reads and writes.
     */
    ChatMemory memory();

    /**
     * The current session vid for this agent.
     */
    fURI sessionVID();

    /**
     * Advance the session's monotonic turn counter and stamp it on the agent — the pre-chat
     * half of the message feature's setup, split out so the agent can run it <em>before</em>
     * the frame is pushed (the frame's {@code c<cid>} segment needs the turn id).
     *
     * <p>{@link Feature#onBeforeChat} (the store/memory half) runs afterwards and reads the already
     * advanced id back from {@code agent.chatId()}.
     *
     * @param agent the agent whose turn this is
     * @return the advanced turn id (1-based, monotonic per session)
     */
    int advanceChatId(final Agent agent);

    /**
     * The configured aggregation budget ({@code algorithm => [max => N]}), in the provider's
     * own unit — tokens for the token provider, messages for the window provider.
     *
     * @return the budget (defaults to 50 when unset)
     */
    int max();

    /**
     * The message/session namespace root for the given agent.
     *
     * @param agent the agent whose root to resolve
     * @return the message/session root uri
     */
    fURI root(final Agent agent);
}
