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

package studio.phaseshift.metatron.isa.llm.space;

import dev.langchain4j.data.message.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.Model;
import studio.phaseshift.metatron.isa.llm.type.feature.ChatFeature;
import studio.phaseshift.metatron.isa.llm.type.feature.SessionFeature;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_at_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableList;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * Abstract integration test for LLM session persistence across chat turns.
 * <p>
 * Concrete subclasses provide a space backend (tbleSpace with SQLite,
 * MariaDB, PostgreSQL; memSpace; dcmntSpace; etc.) and this class verifies
 * that the full chat→session→recall lifecycle works correctly:
 * <ol>
 *   <li>Chat "remember the word DOG" → verify session message structure</li>
 *   <li>Chat "what word were you asked to remember?" → verify response contains DOG</li>
 *   <li>Verify session messages after the full conversation</li>
 * </ol>
 *
 * <h3>Subclass contract</h3>
 * Subclasses must implement:
 * <ul>
 *   <li>{@link #createSessionSpace()} — create and return a Space with the
 *       {@code llm_session} collection and per-type message collections pre-created</li>
 *   <li>{@link #sessionVID()} — return the fURI of the session policy row</li>
 *   <li>{@link #cleanupSession()} — tear down the space and any resources</li>
 * </ul>
 */
public abstract class AbstractLLMSessionIntegrationTest extends AbstractMetatronTest {

    /* ------------------------------------------------------------
     * Subclass contract
     * ---------------------------------------------------------- */

    /**
     * Create and return a space that stores session rows and message entries.
     */
    protected abstract Space createSessionSpace() throws Exception;

    /**
     * The fURI of the pre-created session policy row in the space.
     */
    protected abstract fURI sessionVID();

    /**
     * Clean up the space and any associated resources (files, connections).
     */
    protected abstract void cleanupSession() throws Exception;

    /* ------------------------------------------------------------
     * State
     * ---------------------------------------------------------- */

    private Space space;
    private SpaceChatSessionStore sessionStore;
    private Agent agent;

    private static final String MODEL_NAME = "qwen3:latest";
    private static final String PROVIDER_HOST = "http://localhost:11434";
    private static final int WINDOW_MAX = 30;

    /* ------------------------------------------------------------
     * Lifecycle
     * ---------------------------------------------------------- */

    @BeforeEach
    void initSession() throws Exception {
        this.space = createSessionSpace();

        // Pre-create the session policy row — updateMessages()
        // reads this row to extract the algorithm config (max, message_count) and
        // preserves non-algorithm fields (agent, name) on write-back.
        preCreateSessionRow();
        this.agent = buildAgent();
        this.sessionStore = new SpaceChatSessionStore(this.agent, this.space);
        // Add a system message — gets mirrored to llm_message_system
        this.agent.addSystemMessage("You are a helpful test assistant.");
    }

    @AfterEach
    void teardownSession() throws Exception {
        this.agent = null;
        this.sessionStore = null;
        cleanupSession();
        this.space = null;
        // Clear the static mirror dedup cache so a fresh space with the same
        // session VID pattern gets fresh mirror writes (tests reuse VIDs).
        SpaceChatSessionStore.clearDedupCache();
    }

    /* ------------------------------------------------------------
     * Tests
     * ---------------------------------------------------------- */

    @Test
    public void testSessionAcrossTurns() {
        // ── Turn 1: "remember the word DOG" ──────────────────────────
        try {
            agent.chat("Remember the word DOG. Just say 'ok' and nothing else.");
        } catch (final MTronException e) {
            if (isConnectionRefused(e))
                return; // Ollama not running — skip test
            throw e;
        }

        // Verify session after turn 1: should have user + ai messages
        final List<ChatMessage> messages1 = sessionStore.getMessages(sessionVID());
        assertTrue(messages1.size() >= 2,
                "turn 1: expected >= 2 messages (user + ai), got " + messages1.size());

        // User message should contain "DOG"
        final Optional<ChatMessage> userMsg1 = messages1.stream()
                .filter(m -> m instanceof UserMessage).findFirst();
        assertTrue(userMsg1.isPresent(), "turn 1: should have a user message");
        final String userText1 = userText(userMsg1.get());
        assertTrue(userText1.toLowerCase().contains("dog"),
                "turn 1: user message should contain 'DOG', got: " + userText1);

        // Should have an AI response
        final Optional<ChatMessage> aiMsg1 = messages1.stream()
                .filter(m -> m instanceof AiMessage).findFirst();
        assertTrue(aiMsg1.isPresent(), "turn 1: should have an AI message");

        // ── Turn 2: "what word?" ─────────────────────────────────────
        try {
            agent.chat("What word were you asked to remember? Just say the word and nothing else.");
        } catch (final MTronException e) {
            if (isConnectionRefused(e)) return;
            throw e;
        }

        final String turn2Response = readLastAiText();
        assertNotNull(turn2Response, "turn 2: should have an AI response");
        assertFalse(turn2Response.isBlank(), "turn 2: AI response should not be blank");
        assertTrue(turn2Response.toLowerCase().contains("dog"),
                "turn 2: response should contain 'DOG', got: " + turn2Response);

        // Verify session grew after turn 2
        final List<ChatMessage> messages2 = sessionStore.getMessages(sessionVID());
        assertTrue(messages2.size() > messages1.size(),
                "session should have grown after turn 2: "
                        + messages1.size() + " → " + messages2.size());

        // ── Turn 3: "what letter does it start with?" ──────────────
        try {
            agent.chat("What letter does the word DOG start with? Just say the letter.");
        } catch (final MTronException e) {
            if (isConnectionRefused(e)) return;
            throw e;
        }

        final String turn3Response = readLastAiText();
        assertNotNull(turn3Response, "turn 3: should have an AI response");
        assertTrue(turn3Response.toLowerCase().contains("d"),
                "turn 3: response should contain 'D', got: " + turn3Response);

        // Verify session after all 3 turns
        final List<ChatMessage> messages3 = sessionStore.getMessages(sessionVID());
        assertTrue(messages3.size() > messages2.size(),
                "session should have grown after turn 3: "
                        + messages2.size() + " → " + messages3.size());
        assertTrue(messages3.stream().filter(m -> m instanceof UserMessage).count() >= 3,
                "should have >= 3 user messages");
        assertTrue(messages3.stream().filter(m -> m instanceof AiMessage).count() >= 3,
                "should have >= 3 ai messages");

        // ── Verify session policy row ──────────────────────────────
        verifySessionPolicyRow();
    }

    @Test
    public void testWindowEnforcement() {
        // Tighten the window to 3 — update both the space row and the model
        final int smallMax = 3;
        final Obj memRow = Router.readFromSpace(sessionVID());
        assertTrue(memRow.isRec(), "session row should exist");
        final Rec updated = rec(new LinkedHashMap<>(memRow.asRec().recValue()));
        updated.recValue().put(uri(ALGORITHM), rec(
                uri(MAX), jnt(smallMax),
                uri(NAME), uri("token_window"),
                uri("message_count"), jnt(0)
        ));
        Router.writeToSpace(sessionVID(), updated);

        // Build a model whose session feature also uses the tight max
        this.agent = buildAgentWithMax(smallMax);
        this.agent.addSystemMessage("You are a test assistant.");
        this.sessionStore = new SpaceChatSessionStore(this.agent, this.space);

        // Chat 5 times — MessageWindowChatMemory evicts beyond 3 internally,
        // and getMessages() also applies the max filter
        for (int i = 1; i <= 5; i++) {
            try {
                agent.chat("Say 'turn" + i + "' and nothing else.");
            } catch (final MTronException e) {
                if (isConnectionRefused(e)) return;
                throw e;
            }
        }

        final List<ChatMessage> windowed = sessionStore.getMessages(sessionVID());
        assertTrue(windowed.size() <= smallMax,
                "window max=" + smallMax + ": expected <= " + smallMax
                        + " messages, got " + windowed.size());
        // The last message should be from turn 5
        final String lastText = readLastAiText();
        assertTrue(lastText.toLowerCase().contains("5") || lastText.toLowerCase().contains("turn"),
                "last message should be from turn 5, got: " + lastText);

        verifySessionPolicyRow();
    }

    /* ------------------------------------------------------------
     * Typed-collection mirror verification
     * ---------------------------------------------------------- */

    @Test
    public void testTypedCollectionPopulation() {
        // Chat three times to exercise the per-type collection mirrors
        try {
            agent.chat("Remember the word DOG. Just say 'ok'.");
            agent.chat("What word were you asked to remember? Just say the word.");
            agent.chat("How many letters are in that word? Just say the number.");
        } catch (final MTronException e) {
            if (isConnectionRefused(e)) return;
            throw e;
        }

        final fURI basePath = sessionVID().retract(2);  // strip entry + collection → scheme/prefix root

        // ── Verify unified llm_message table ──────────────────────
        // Messages are stored in a single polymorphic table with _tid column.
        // Verify that system, user, and ai messages all land in llm_message.
        assertCollectionHasType(basePath, "llm_message", SYSTEM_MESSAGE_TID, "system", 1, 1);
        assertCollectionHasType(basePath, "llm_message", USER_MESSAGE_TID, "user", 3, 3);
        assertCollectionHasType(basePath, "llm_message", AI_MESSAGE_TID, "ai", 3, 3);

        // ── Store remains authoritative ───────────────────────────
        assertTrue(sessionStore.getMessages(sessionVID()).size() >= 6,
                "KV store: expected >= 6 messages");
    }

    @Test
    public void testSessionMessageWriteReadBack() {
        final fURI sessionVID = sessionVID();
        final int memoryId = 1;
        // msg base: retract entry + collection, then extend with "msg"
        final fURI msgBase = sessionVID.retract(2).extend("msg");

        // -- Build typed message Recs (TID = message type discriminator) --
        final Obj systemMsg = rec(uri(TEXT), str("You are helpful."))
                .tid(f("/m/llm/system"));
        final Obj userMsg = rec(uri(NAME), str("marko"),
                uri(CONTENTS), rec(uri(TEXT), str("are you there?")))
                .tid(f("/m/llm/user"));
        final Obj aiMsg = rec(uri(TEXT), str("Yes, I am here."))
                .tid(f("/m/llm/ai"));
        final Obj toolResultMsg = rec(uri(NAME), str("eval"),
                uri(TEXT), str("42"))
                .tid(f("/m/llm/tool_result"));

        final Obj[] messages = {systemMsg, userMsg, aiMsg, toolResultMsg};

        // -- Write each message to its sub-path position -----------------
        for (int i = 0; i < messages.length; i++) {
            Router.writeToSpace(msgBase.extend(String.valueOf(memoryId)).extend(String.valueOf(i)), messages[i]);
        }

        // -- Read back individual messages by position -------------------
        for (int i = 0; i < messages.length; i++) {
            final Obj msg = Router.readFromSpace(msgBase.extend(String.valueOf(memoryId)).extend(String.valueOf(i)));
            assertFalse(msg.isNoObj(), "message at position " + i + " should exist");
            assertTrue(msg.isRec(), "message at position " + i + " should be a Rec");
            assertEquals(messages[i].tid(), msg.asRec().tid(),
                    "message " + i + " should have correct TID");
        }

        // Verify message 0 = system
        final Obj m0 = Router.readFromSpace(msgBase.extend(String.valueOf(memoryId)).extend("0"));
        assertEquals(f("/m/llm/system"), m0.asRec().tid());
        assertEquals(str("You are helpful."), m0.asRec().at(uri(TEXT)));

        // Verify message 1 = user
        final Obj m1 = Router.readFromSpace(msgBase.extend(String.valueOf(memoryId)).extend("1"));
        assertEquals(f("/m/llm/user"), m1.asRec().tid());
        assertEquals(str("marko"), m1.asRec().at(uri(NAME)));

        // Verify message 2 = ai
        final Obj m2 = Router.readFromSpace(msgBase.extend(String.valueOf(memoryId)).extend("2"));
        assertEquals(f("/m/llm/ai"), m2.asRec().tid());
        assertEquals(str("Yes, I am here."), m2.asRec().at(uri(TEXT)));

        // Verify message 3 = tool_result
        final Obj m3 = Router.readFromSpace(msgBase.extend(String.valueOf(memoryId)).extend("3"));
        assertEquals(f("/m/llm/tool_result"), m3.asRec().tid());
        assertEquals(str("eval"), m3.asRec().at(uri(NAME)));
        assertEquals(str("42"), m3.asRec().at(uri(TEXT)));

        // -- Delete a message (simulating window eviction) ---------------
        Router.writeToSpace(msgBase.extend(String.valueOf(memoryId)).extend("0"), noobj());
        final Obj deleted = Router.readFromSpace(msgBase.extend(String.valueOf(memoryId)).extend("0"));
        assertTrue(deleted.isNoObj(), "deleted message should be noobj");

        // Message at position 1 should still exist
        final Obj stillThere = Router.readFromSpace(msgBase.extend(String.valueOf(memoryId)).extend("1"));
        assertFalse(stillThere.isNoObj(), "undeleted message should still exist");
    }



    /**
     * Validate that the unified llm_message table contains messages of a given TID.
     * Reads all rows from the table and filters by rec.tid() (populated from _tid column).
     */
    private void assertCollectionHasType(final fURI basePath, final String tableName,
                                         final fURI expectedTid, final String label,
                                         final int minRows, final int maxRows) {
        final Map<String, Integer> hashCounts = new LinkedHashMap<>();
        int rows = 0;
        for (int id = 1; ; id++) {
            final Obj row = Router.readFromSpace(basePath.extend(tableName).extend(String.valueOf(id)));
            if (row.isNoObj()) break;
            if (!row.isRec()) continue;
            final Rec rec = row.asRec();
            // Filter by TID — the _tid column is restored as rec.tid()
            if (!rec.tid().equals(expectedTid)) continue;
            rows++;

            // Hash uniqueness
            final Obj hf = rec.at(uri("hash"));
            if (!hf.isNoObj() && hf.isStr())
                hashCounts.merge(hf.strValue(), 1, Integer::sum);
        }

        assertTrue(rows >= minRows,
                label + " (" + expectedTid.name() + "): expected >= " + minRows + " rows, got " + rows);
        if (maxRows > 0)
            assertTrue(rows <= maxRows,
                    label + " (" + expectedTid.name() + "): expected <= " + maxRows + " rows, got " + rows);
    }

    /* ------------------------------------------------------------
     * Helpers
     * ---------------------------------------------------------- */

    /**
     * Verify the llm_session policy row has the expected fields.
     */
    private void verifySessionPolicyRow() {
        final Obj row = Router.readFromSpace(sessionVID());
        assertTrue(row.isRec(), "session policy row must be Rec, got: " + row);
        final Rec rec = row.asRec();

        assertFalse(rec.at(uri("agent")).isNoObj(), "agent should exist in: " + row);
        assertFalse(rec.at(uri("agent")).strValue().isBlank(), "agent should not be blank");

        assertFalse(rec.at(uri("user")).isNoObj(), "agent should exist in: " + row);
        assertFalse(rec.at(uri("user")).strValue().isBlank(), "agent should not be blank");

        final Obj algorithm = rec.at(uri(ALGORITHM));
        assertTrue(algorithm.isRec(), "algorithm must be Rec, got: " + algorithm);
        final Rec algo = algorithm.asRec();
        assertFalse(algo.at(uri(MAX)).isNoObj(), "algorithm.max must exist");
       // assertFalse(algo.at(uri("message_count")).isNoObj(), "algorithm.message_count must exist");
       // assertTrue(algo.at(uri("message_count")).intValue() > 0,
       //        "message_count should be > 0, got " + algo.at(uri("message_count")));
    }

    /**
     * Write the session policy row so the space has a target for message storage.
     */
    private void preCreateSessionRow() {
        final Obj row = rec(
                uri("agent"), str("test-agent"),
                uri("name"), str("test-chat"),
                uri(ALGORITHM), rec(
                        uri(MAX), jnt(WINDOW_MAX),
                        uri("message_count"), jnt(0)
                )
        );
        Router.writeToSpace(sessionVID(), row);
    }

    /**
     * Build an Agent wired to our space-backed session with the default max.
     */
    private Agent buildAgent() {
        return buildAgentWithMax(WINDOW_MAX);
    }

    /**
     * Build an mModel with a specific window max.
     */
    private Agent buildAgentWithMax(final int max) {
        final fURI memVID = sessionVID();
        // Build features directly as Feature instances — no ISA lookup needed
        final Model model = Model.model(rec(
                NAME, uri(MODEL_NAME),
                PROVIDER, uri("ollama"),
                PROTOCOL, uri("ollama"),
                HOST, uri(PROVIDER_HOST),
                LLM, uri(MODEL_NAME)));
        final Rec sessionObj = rec(mutableMap(
                uri("mem"), auto_at_(memVID).tryToInst(),
                uri(ALGORITHM), rec(mutableMap(uri(NAME), uri("message_window"), uri(MAX), jnt(max)))), REC_TID, memVID);
        final ChatFeature chat = ChatFeature.chatFeature(model, rec(uri(TO), noobj()));
        final Rec sessionConfig = rec(
                SESSION, uri(memVID),
                REC_TID, null);
        final SessionFeature session = new SessionFeature(sessionConfig.jvm(), LLM_SESSION_FEATURE_TID, null);
        final Rec agentRec = rec(mutableMap(
                uri(NAME), str("llm-session-test-agent"),
                uri(DESC), str("testing llm-session implementation"),
                uri(FEATURE), lst(mutableList(chat, session))), LLM_AGENT_TID, null);
        return Agent.agent(agentRec);
    }

    /**
     * Read the text of the last AI message from session.
     */
    private String readLastAiText() {
        final List<ChatMessage> messages = sessionStore.getMessages(sessionVID());
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AiMessage ai)
                return ai.text() != null ? ai.text() : "";
        }
        return "";
    }

    private static String userText(final ChatMessage msg) {
        if (msg instanceof UserMessage um) {
            if (um.hasSingleText()) return um.singleText();
            return um.contents().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .reduce("", (a, b) -> a + " " + b);
        }
        return "";
    }

    private static boolean isConnectionRefused(final MTronException e) {
        return e.getMessage() != null && e.getMessage().contains("Connection refused");
    }
}
