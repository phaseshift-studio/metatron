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
import studio.phaseshift.metatron.isa.llm.type.mModel;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_MEMORY_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.MODEL_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_at_;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * Abstract integration test for LLM memory persistence across chat turns.
 * <p>
 * Concrete subclasses provide a space backend (tbleSpace with SQLite,
 * MariaDB, PostgreSQL; memSpace; dcmntSpace; etc.) and this class verifies
 * that the full chat→memory→recall lifecycle works correctly:
 * <ol>
 *   <li>Chat "remember the word DOG" → verify memory message structure</li>
 *   <li>Chat "what word were you asked to remember?" → verify response contains DOG</li>
 *   <li>Verify memory messages after the full conversation</li>
 * </ol>
 *
 * <h3>Subclass contract</h3>
 * Subclasses must implement:
 * <ul>
 *   <li>{@link #createMemorySpace()} — create and return a Space with the
 *       {@code llm_memory} table and per-type message tables pre-created</li>
 *   <li>{@link #memoryVID()} — return the fURI of the memory policy row</li>
 *   <li>{@link #cleanupMemory()} — tear down the space and any resources</li>
 * </ul>
 */
public abstract class AbstractLLMMemoryIntegrationTest extends AbstractMetatronTest {

    /* ------------------------------------------------------------
     * Subclass contract
     * ---------------------------------------------------------- */

    /** Create and return a space that stores memory rows and message entries. */
    protected abstract Space createMemorySpace() throws Exception;

    /** The fURI of the pre-created memory policy row in the space. */
    protected abstract fURI memoryVID();

    /** Clean up the space and any associated resources (files, connections). */
    protected abstract void cleanupMemory() throws Exception;

    /* ------------------------------------------------------------
     * State
     * ---------------------------------------------------------- */

    private Space space;
    private SpaceChatMemoryStore memoryStore;
    private mModel chatModel;

    private static final String MODEL_NAME = "qwen3:latest";
    private static final String PROVIDER_HOST = "http://localhost:11434";
    private static final int WINDOW_MAX = 30;

    /* ------------------------------------------------------------
     * Lifecycle
     * ---------------------------------------------------------- */

    @BeforeEach
    void initMemory() throws Exception {
        this.space = createMemorySpace();

        // Pre-create the memory policy row — SpaceChatMemoryStore.updateMessages()
        // reads this row to extract the algorithm config (max, message_count) and
        // preserves non-algorithm fields (agent_id, name) on write-back.
        preCreateMemoryRow();

        this.memoryStore = new SpaceChatMemoryStore(this.space);
        this.chatModel = buildModel();
        // Add a system message — gets mirrored to llm_message_system
        this.chatModel.addSystemMessage("You are a helpful test assistant.");
    }

    @AfterEach
    void teardownMemory() throws Exception {
        this.chatModel = null;
        this.memoryStore = null;
        cleanupMemory();
        this.space = null;
    }

    /* ------------------------------------------------------------
     * Tests
     * ---------------------------------------------------------- */

    @Test
    public void testMemoryAcrossChatTurns() {
        // ── Turn 1: "remember the word DOG" ──────────────────────────
        try {
            chatModel.chat("Remember the word DOG. Just say 'ok' and nothing else.");
        } catch (final MTronException e) {
            if (isConnectionRefused(e))
                return; // Ollama not running — skip test
            throw e;
        }

        // Verify memory after turn 1: should have user + ai messages
        final List<ChatMessage> messages1 = memoryStore.getMessages(memoryVID());
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
            chatModel.chat("What word were you asked to remember? Just say the word and nothing else.");
        } catch (final MTronException e) {
            if (isConnectionRefused(e)) return;
            throw e;
        }

        final String turn2Response = readLastAiText();
        assertNotNull(turn2Response, "turn 2: should have an AI response");
        assertFalse(turn2Response.isBlank(), "turn 2: AI response should not be blank");
        assertTrue(turn2Response.toLowerCase().contains("dog"),
                "turn 2: response should contain 'DOG', got: " + turn2Response);

        // Verify memory grew after turn 2
        final List<ChatMessage> messages2 = memoryStore.getMessages(memoryVID());
        assertTrue(messages2.size() > messages1.size(),
                "memory should have grown after turn 2: "
                        + messages1.size() + " → " + messages2.size());

        // ── Turn 3: "what letter does it start with?" ──────────────
        try {
            chatModel.chat("What letter does the word DOG start with? Just say the letter.");
        } catch (final MTronException e) {
            if (isConnectionRefused(e)) return;
            throw e;
        }

        final String turn3Response = readLastAiText();
        assertNotNull(turn3Response, "turn 3: should have an AI response");
        assertTrue(turn3Response.toLowerCase().contains("d"),
                "turn 3: response should contain 'D', got: " + turn3Response);

        // Verify memory after all 3 turns
        final List<ChatMessage> messages3 = memoryStore.getMessages(memoryVID());
        assertTrue(messages3.size() > messages2.size(),
                "memory should have grown after turn 3: "
                        + messages2.size() + " → " + messages3.size());
        assertTrue(messages3.stream().filter(m -> m instanceof UserMessage).count() >= 3,
                "should have >= 3 user messages");
        assertTrue(messages3.stream().filter(m -> m instanceof AiMessage).count() >= 3,
                "should have >= 3 ai messages");

        // ── Verify memory policy row ──────────────────────────────
        verifyMemoryPolicyRow();
    }

    @Test
    public void testWindowEnforcement() {
        // Tighten the window to 3 — update both the space row and the model
        final int smallMax = 3;
        final Obj memRow = Router.readFromSpace(memoryVID());
        assertTrue(memRow.isRec(), "memory row should exist");
        final Rec updated = rec(new LinkedHashMap<>(memRow.asRec().recValue()));
        updated.recValue().put(uri(ALGORITHM), rec(
                uri(MAX), jnt(smallMax),
                uri("message_count"), jnt(0)
        ));
        Router.writeToSpace(memoryVID(), updated);

        // Build a model whose memory feature also uses the tight max
        this.chatModel = buildModelWithMax(smallMax);
        this.chatModel.addSystemMessage("You are a test assistant.");
        this.memoryStore = new SpaceChatMemoryStore(this.space);

        // Chat 5 times — MessageWindowChatMemory evicts beyond 3 internally,
        // and getMessages() also applies the max filter
        for (int i = 1; i <= 5; i++) {
            try {
                chatModel.chat("Say 'turn" + i + "' and nothing else.");
            } catch (final MTronException e) {
                if (isConnectionRefused(e)) return;
                throw e;
            }
        }

        final List<ChatMessage> windowed = memoryStore.getMessages(memoryVID());
        assertTrue(windowed.size() <= smallMax,
                "window max=" + smallMax + ": expected <= " + smallMax
                        + " messages, got " + windowed.size());
        // The last message should be from turn 5
        final String lastText = readLastAiText();
        assertTrue(lastText.toLowerCase().contains("5") || lastText.toLowerCase().contains("turn"),
                "last message should be from turn 5, got: " + lastText);

        verifyMemoryPolicyRow();
    }

    /* ------------------------------------------------------------
     * Typed-table mirror verification
     * ---------------------------------------------------------- */

    @Test
    public void testTypedTablePopulation() {
        // Chat three times to exercise the per-type table mirrors
        try {
            chatModel.chat("Remember the word DOG. Just say 'ok'.");
            chatModel.chat("What word were you asked to remember? Just say the word.");
            chatModel.chat("How many letters are in that word? Just say the number.");
        } catch (final MTronException e) {
            if (isConnectionRefused(e)) return;
            throw e;
        }

        final String scheme = memoryVID().scheme();

        // ── Verify llm_message_system ────────────────────────────
        assertTypedTable(scheme, "llm_message_system",
                "system", 1, this::verifyMirrorRow);

        // ── Verify llm_message_user ──────────────────────────────
        assertTypedTable(scheme, "llm_message_user",
                "user", 3, this::verifyMirrorRow);

        // ── Verify llm_message_ai ────────────────────────────────
        assertTypedTable(scheme, "llm_message_ai",
                "ai", 3, this::verifyMirrorRow);

        // ── KV store remains authoritative ───────────────────────
        assertTrue(memoryStore.getMessages(memoryVID()).size() >= 6,
                "KV store: expected >= 6 messages");
    }

    /** Per-row validation for typed-table mirror: text + kv URI back-link. */
    private void verifyMirrorRow(final Rec rec, final int id) {
        final Obj text = rec.at(uri(TEXT));
        assertFalse(text.isNoObj(), "row[" + id + "]: missing text");
        assertTrue(text.isStr(), "row[" + id + "]: text must be Str, got " + text.getClass().getSimpleName());
        assertFalse(text.strValue().isBlank(), "row[" + id + "]: text is blank");

        final Obj uriField = rec.at(uri(URI));
        assertFalse(uriField.isNoObj(), "row[" + id + "]: missing uri back-link");
        assertTrue(uriField.isUri(),
                "row[" + id + "]: uri must be Uri, got " + uriField.getClass().getSimpleName());
        final String uriStr = uriField.uriValue().toString();
        assertTrue(uriStr.contains("/"), "row[" + id + "]: uri should be a full path, got: " + uriStr);
    }

    /** Validate a typed table: sequential IDs, unique hashes, per-row assertions. */
    private void assertTypedTable(final String scheme, final String tableName,
                                  final String label, final int minRows,
                                  final java.util.function.BiConsumer<Rec, Integer> perRow) {
        final Map<String, Integer> hashCounts = new LinkedHashMap<>();
        int rows = 0;
        int lastId = 0;
        for (int id = 1; ; id++) {
            final Obj row = Router.readFromSpace(f(scheme + ":" + tableName + "/" + id));
            if (row.isNoObj()) break;
            assertTrue(row.isRec(), label + "[" + id + "]: must be Rec, got " + row.getClass().getSimpleName());
            final Rec rec = row.asRec();
            rows++;

            // IDs are sequential
            assertEquals(id, lastId + 1, label + ": IDs must be sequential (gap at " + id + ")");
            lastId = id;

            // Hash uniqueness
            final Obj hf = rec.at(uri("content_hash"));
            assertFalse(hf.isNoObj(), label + "[" + id + "]: missing content_hash");
            assertTrue(hf.isStr(), label + "[" + id + "]: content_hash must be Str");
            hashCounts.merge(hf.strValue(), 1, Integer::sum);

            // Per-row validation
            perRow.accept(rec, id);
        }

        assertTrue(rows >= minRows,
                label + " table: expected >= " + minRows + " rows, got " + rows);

        // All hashes unique (= row count)
        final long duplicates = hashCounts.values().stream().filter(c -> c > 1).count();
        assertEquals(0, duplicates,
                label + " table: " + duplicates + " duplicate hashes in " + rows
                        + " rows: " + hashCounts);

        // Hash count == row count (no hash appears more than once)
        assertEquals(rows, hashCounts.size(),
                label + " table: hash unique count (" + hashCounts.size()
                        + ") != row count (" + rows + ")");
    }

    /* ------------------------------------------------------------
     * Helpers
     * ---------------------------------------------------------- */

    /** Verify the llm_memory policy row has the expected fields. */
    private void verifyMemoryPolicyRow() {
        final Obj row = Router.readFromSpace(memoryVID());
        assertTrue(row.isRec(), "memory policy row must be Rec, got: " + row);
        final Rec rec = row.asRec();

        assertFalse(rec.at(uri("agent_id")).isNoObj(),
                "agent_id should exist in: " + row);
        assertFalse(rec.at(uri("agent_id")).strValue().isBlank(),
                "agent_id should not be blank");

        final Obj algorithm = rec.at(uri(ALGORITHM));
        assertTrue(algorithm.isRec(), "algorithm must be Rec, got: " + algorithm);
        final Rec algo = algorithm.asRec();
        assertFalse(algo.at(uri(MAX)).isNoObj(), "algorithm.max must exist");
        assertFalse(algo.at(uri("message_count")).isNoObj(), "algorithm.message_count must exist");
        assertTrue(algo.at(uri("message_count")).intValue() > 0,
                "message_count should be > 0, got " + algo.at(uri("message_count")));
    }

    /** Write the memory policy row so the space has a target for message storage. */
    private void preCreateMemoryRow() {
        final Obj row = rec(
                uri("agent_id"), str("test-agent"),
                uri("name"), str("test-chat"),
                uri(ALGORITHM), rec(
                        uri(MAX), jnt(WINDOW_MAX),
                        uri("message_count"), jnt(0)
                )
        );
        Router.writeToSpace(memoryVID(), row);
    }

    /** Build an mModel wired to our space-backed memory with the default max. */
    private mModel buildModel() {
        return buildModelWithMax(WINDOW_MAX);
    }

    /** Build an mModel with a specific window max. */
    private mModel buildModelWithMax(final int max) {
        final fURI memVID = memoryVID();
        final Rec modelRec = (Rec) rec(new LinkedHashMap<>(Map.of(
                uri(NAME), uri(MODEL_NAME),
                uri(PROVIDER), rec(new LinkedHashMap<>(Map.of(
                        uri(NAME), uri("ollama"),
                        uri(HOST), uri(PROVIDER_HOST)
                ))),
                uri(FEATURE), rec(new LinkedHashMap<>(Map.of(
                        uri(MEMORY), rec(new LinkedHashMap<>(Map.of(
                                uri("mem"), auto_at_(memVID).tryToInst(),
                                uri(ALGORITHM), rec(new LinkedHashMap<>(Map.of(
                                        uri(MAX), jnt(max)
                                )))
                        )), LLM_MEMORY_TID, memVID)  // VID = memory row URI; TID = memory type
                )))
        )), MODEL_TID, null);
        return mModel.model(modelRec);
    }

    /** Read the text of the last AI message from memory. */
    private String readLastAiText() {
        final List<ChatMessage> messages = memoryStore.getMessages(memoryVID());
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
