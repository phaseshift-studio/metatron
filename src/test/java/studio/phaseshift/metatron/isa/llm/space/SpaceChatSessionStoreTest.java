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

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * Tests for {@link SpaceChatSessionStore}'s pair-aware message windowing.
 * <p>
 * The core guarantee: when trimming a message list to a target window size,
 * the skip boundary must never split an {@code AiMessage(tool_calls)} from
 * its {@code ToolExecutionResultMessage}s, and the window must always start
 * with a {@code UserMessage} (the chat API requires every turn to begin
 * with a user message).
 */
public class SpaceChatSessionStoreTest extends AbstractMetatronTest {

    // ── Message factories ────────────────────────────────────────────

    /** A bare system message. */
    private static Rec S() {
        return rec(mutableMap(uri(TEXT), str("system prompt")), SYSTEM_MESSAGE_TID, null);
    }

    /** A bare user message. */
    private static Rec U() {
        return rec(mutableMap(uri(TEXT), str("user says hello")), USER_MESSAGE_TID, null);
    }

    /** An AI message without tool calls. */
    private static Rec A() {
        return rec(mutableMap(uri(TEXT), str("ai responds")), AI_MESSAGE_TID, null);
    }

    /**
     * An AI message with one or more tool execution requests.
     * Each tool call id is stored in the {@code contents} field of a
     * {@code TOOL_REQUEST_MESSAGE_TID} rec inside the {@code tool_requests} list.
     */
    private static Rec A(final String... toolCallIds) {
        final Map<Obj, Obj> map = mutableMap();
        map.put(uri(TEXT), str("ai calls tools"));
        final List<Obj> toolReqs = new ArrayList<>();
        for (final String id : toolCallIds)
            toolReqs.add(rec(mutableMap(uri(CONTENTS), str(id)), TOOL_REQUEST_MESSAGE_TID, null));
        map.put(uri(TOOL_REQUESTS), lst(toolReqs));
        return rec(map, AI_MESSAGE_TID, null);
    }

    /** A tool execution result message keyed by its tool call id. */
    private static Rec T(final String toolCallId) {
        return rec(mutableMap(
                uri(CONTENTS), str(toolCallId),
                uri(TEXT), str("result for " + toolCallId)
        ), TOOL_RESULT_MESSAGE_TID, null);
    }

    /** Varargs → List. */
    private static List<Rec> msgs(final Rec... messages) {
        return new ArrayList<>(List.of(messages));
    }

    // ── pullInPairedAiMessage ────────────────────────────────────────

    @Test
    void pullInPairedAiMessage_skipZero_unchanged() {
        final List<Rec> m = msgs(U(), A("c1"), T("c1"));
        assertEquals(0, SpaceChatSessionStore.pullInPairedAiMessage(m, 0));
    }

    @Test
    void pullInPairedAiMessage_skipAtSize_unchanged() {
        final List<Rec> m = msgs(U(), A("c1"), T("c1"));
        assertEquals(3, SpaceChatSessionStore.pullInPairedAiMessage(m, 3));
    }

    @Test
    void pullInPairedAiMessage_firstIsUser_unchanged() {
        // Window: [U, A{c1}, T{c1}] — starts with user, no orphans
        final List<Rec> m = msgs(S(), U(), A("c1"), T("c1"));
        assertEquals(1, SpaceChatSessionStore.pullInPairedAiMessage(m, 1));
    }

    @Test
    void pullInPairedAiMessage_firstIsAi_unchanged() {
        // AiMessage is not a ToolResult — rule 1 doesn't apply
        final List<Rec> m = msgs(S(), U(), A("c1"), T("c1"));
        assertEquals(2, SpaceChatSessionStore.pullInPairedAiMessage(m, 2));
    }

    @Test
    void pullInPairedAiMessage_orphanedToolResult_pullsInAiMessage() {
        // [2]=T{c1} is orphaned — its paired AiMessage is at [1]
        final List<Rec> m = msgs(S(), A("c1"), T("c1"));
        assertEquals(1, SpaceChatSessionStore.pullInPairedAiMessage(m, 2));
    }

    @Test
    void pullInPairedAiMessage_orphanedMiddleToolResult_pullsInAiMessage() {
        // [2]=T{c1} orphaned, but AiMessage at [0] has both c1 and c2
        final List<Rec> m = msgs(A("c1", "c2"), T("c1"), T("c2"));
        assertEquals(0, SpaceChatSessionStore.pullInPairedAiMessage(m, 2));
    }

    @Test
    void pullInPairedAiMessage_toolCallIdNotFound_unchanged() {
        // T{c99} references an id that doesn't exist in any AiMessage
        final List<Rec> m = msgs(S(), U(), A("c1"), T("c99"));
        assertEquals(2, SpaceChatSessionStore.pullInPairedAiMessage(m, 2));
    }

    @Test
    void pullInPairedAiMessage_noToolCallId_unchanged() {
        // ToolResult without a contents/id field
        final Rec orphan = rec(mutableMap(uri(TEXT), str("no id")), TOOL_RESULT_MESSAGE_TID, null);
        final List<Rec> m = msgs(S(), U(), A("c1"), orphan);
        assertEquals(3, SpaceChatSessionStore.pullInPairedAiMessage(m, 3));
    }

    // ── pullInPrecedingUserMessage ───────────────────────────────────

    @Test
    void pullInPrecedingUserMessage_skipZero_unchanged() {
        final List<Rec> m = msgs(U(), A());
        assertEquals(0, SpaceChatSessionStore.pullInPrecedingUserMessage(m, 0));
    }

    @Test
    void pullInPrecedingUserMessage_firstIsUser_unchanged() {
        final List<Rec> m = msgs(S(), U(), A());
        assertEquals(1, SpaceChatSessionStore.pullInPrecedingUserMessage(m, 1));
    }

    @Test
    void pullInPrecedingUserMessage_firstIsSystem_unchanged() {
        // System message at boundary is fine — it's the absolute start
        final List<Rec> m = msgs(S(), U(), A());
        assertEquals(0, SpaceChatSessionStore.pullInPrecedingUserMessage(m, 0));
    }

    @Test
    void pullInPrecedingUserMessage_firstIsAi_pullsInUser() {
        // Window starts with AiMessage — need preceding UserMessage
        final List<Rec> m = msgs(S(), U(), A("c1"), T("c1"));
        assertEquals(1, SpaceChatSessionStore.pullInPrecedingUserMessage(m, 2));
    }

    @Test
    void pullInPrecedingUserMessage_firstIsToolResult_pullsInUser() {
        // Window starts with ToolResult (already paired, but still need UserMessage)
        final List<Rec> m = msgs(S(), U(), A("c1"), T("c1"));
        assertEquals(1, SpaceChatSessionStore.pullInPrecedingUserMessage(m, 3));
    }

    @Test
    void pullInPrecedingUserMessage_stopsAtSystem() {
        // No UserMessage between system and AiMessage — fall back to system
        final List<Rec> m = msgs(S(), A("c1"), T("c1"));
        assertEquals(0, SpaceChatSessionStore.pullInPrecedingUserMessage(m, 1));
    }

    @Test
    void pullInPrecedingUserMessage_noUserMessage_returnsZero() {
        // No UserMessage or SystemMessage at all — return everything
        final List<Rec> m = msgs(A("c1"), T("c1"));
        assertEquals(0, SpaceChatSessionStore.pullInPrecedingUserMessage(m, 1));
    }

    // ── adjustSkipToPreservePairs (combined rules) ──────────────────

    @Test
    void adjustSkip_aiMessageAtBoundary_pullsInUser() {
        // Window starts on A() — rule 2 pulls in preceding UserMessage
        // [S, U, A, U, A], skip=2 → first=[2]=A()
        final List<Rec> m = msgs(S(), U(), A(), U(), A());
        assertEquals(1, SpaceChatSessionStore.adjustSkipToPreservePairs(m, 2));
    }

    @Test
    void adjustSkip_aiAtBoundary_scansThroughToolResultsToFindUser() {
        // skip=5 drops [0..4]; first=[5]=A() which needs a user.
        // Rule 2 scans backward through T{c2}, T{c1}, A{c1,c2}, finds U() at [1].
        final List<Rec> m = msgs(
                S(), U(), A("c1", "c2"), T("c1"), T("c2"),  // [0..4]
                A(), U(), A("c3"), T("c3")                  // [5..8]
        );
        assertEquals(1, SpaceChatSessionStore.adjustSkipToPreservePairs(m, 5));
    }

    @Test
    void adjustSkip_toolResultAtBoundary_pullsInAiThenUser() {
        // skip=3 drops [0..2]; first=[3]=T{c1} — orphaned tool result!
        // Rule 1: T{c1} → find paired AiMessage at [2] → skip=2
        // Rule 2: [2]=A{c1} needs user → find U() at [1] → skip=1
        final List<Rec> m = msgs(S(), U(), A("c1"), T("c1"), U(), A());
        assertEquals(1, SpaceChatSessionStore.adjustSkipToPreservePairs(m, 3));
    }

    @Test
    void adjustSkip_userAtBoundary_noAdjustment() {
        // skip=4 drops [0..3]; first=[4]=U() — already a UserMessage, no change
        final List<Rec> m = msgs(S(), U(), A("c1"), T("c1"), U(), A());
        assertEquals(4, SpaceChatSessionStore.adjustSkipToPreservePairs(m, 4));
    }

    @Test
    void adjustSkip_skipZero_unchanged() {
        final List<Rec> m = msgs(U(), A("c1"), T("c1"));
        assertEquals(0, SpaceChatSessionStore.adjustSkipToPreservePairs(m, 0));
    }

    @Test
    void adjustSkip_skipAtSize_unchanged() {
        final List<Rec> m = msgs(U(), A("c1"), T("c1"));
        assertEquals(3, SpaceChatSessionStore.adjustSkipToPreservePairs(m, 3));
    }

    @Test
    void adjustSkip_multipleRoundsOfTools_preservesAllPairs() {
        // Simulates a real conversation with 3 rounds of tool calls
        // Round 1: U, A{c1,c2}, T{c1}, T{c2}, A
        // Round 2: U, A{c3}, T{c3}, A
        // Round 3: U, A{c4,c5}, T{c4}, T{c5}
        final List<Rec> m = msgs(
                S(),           // [0]
                U(),           // [1]  user round 1
                A("c1", "c2"), // [2]  ai with 2 tool calls
                T("c1"),       // [3]
                T("c2"),       // [4]
                A(),           // [5]  ai text response
                U(),           // [6]  user round 2
                A("c3"),       // [7]  ai with 1 tool call
                T("c3"),       // [8]
                A(),           // [9]  ai text response
                U(),           // [10] user round 3
                A("c4", "c5"), // [11] ai with 2 tool calls
                T("c4"),       // [12]
                T("c5")        // [13]
        );

        // rawSkip = max(0, 14-7) = 7, first=[7]=A{c3}
        // Rule 1: not ToolResult → unchanged (skip still 7)
        // Rule 2: AiMessage → scan back: [6]=U() → return 6
        assertEquals(6, SpaceChatSessionStore.adjustSkipToPreservePairs(m, 7));

        // rawSkip = max(0, 14-6) = 8, first=[8]=T{c3}
        // Rule 1: ToolResult {c3} → scan back for AiMessage with c3 → [7]=A{c3} → return 7
        // Rule 2: skip=7, first=[7]=A{c3} → scan back for UserMessage → [6]=U() → return 6
        assertEquals(6, SpaceChatSessionStore.adjustSkipToPreservePairs(m, 8));

        // rawSkip = max(0, 14-3) = 11, first=[11]=A{c4,c5}
        // Rule 1: not ToolResult → unchanged
        // Rule 2: AiMessage → scan back: [10]=U() → return 10
        assertEquals(10, SpaceChatSessionStore.adjustSkipToPreservePairs(m, 11));
    }

    @Test
    void adjustSkip_windowLargerThanMessages_skipZero() {
        // More window slots than messages — nothing to skip
        final List<Rec> m = msgs(S(), U(), A());
        assertEquals(0, SpaceChatSessionStore.adjustSkipToPreservePairs(m, 0));
    }
}
