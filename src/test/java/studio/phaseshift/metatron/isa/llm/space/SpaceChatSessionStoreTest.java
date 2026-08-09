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
import studio.phaseshift.metatron.isa.m.type.Rel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
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

    /**
     * A bare system message.
     */
    private static Rec S() {
        return rec(mutableMap(uri(TEXT), str("system prompt")), SYSTEM_MESSAGE_TID, null);
    }

    /**
     * A bare user message.
     */
    private static Rec U() {
        return rec(mutableMap(uri(TEXT), str("user says hello")), USER_MESSAGE_TID, null);
    }

    /**
     * An AI message without tool calls.
     */
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

    /**
     * A tool execution result message keyed by its tool call id.
     */
    private static Rec T(final String toolCallId) {
        return rec(mutableMap(
                uri(CONTENTS), str(toolCallId),
                uri(TEXT), str("result for " + toolCallId)
        ), TOOL_RESULT_MESSAGE_TID, null);
    }

    /**
     * Varargs → List.
     */
    private static List<Rel> msgs(final Rec... messages) {
        return new ArrayList<>(Stream.of(messages).map(m -> rel(uri("temp"), m)).toList());
    }

    // ── pullInPairedAiMessage ────────────────────────────────────────

    @Test
    void pullInPairedAiMessage_skipZero_unchanged() {
        final List<Rel> m = msgs(U(), A("c1"), T("c1"));
        assertEquals(0, SpaceChatSessionStore.pullInPairedAiMessage(m, 0));
    }

    @Test
    void pullInPairedAiMessage_skipAtSize_unchanged() {
        final List<Rel> m = msgs(U(), A("c1"), T("c1"));
        assertEquals(3, SpaceChatSessionStore.pullInPairedAiMessage(m, 3));
    }

    @Test
    void pullInPairedAiMessage_firstIsUser_unchanged() {
        // Window: [U, A{c1}, T{c1}] — starts with user, no orphans
        final List<Rel> m = msgs(S(), U(), A("c1"), T("c1"));
        assertEquals(1, SpaceChatSessionStore.pullInPairedAiMessage(m, 1));
    }

    @Test
    void pullInPairedAiMessage_firstIsAi_unchanged() {
        // AiMessage is not a ToolResult — rule 1 doesn't apply
        final List<Rel> m = msgs(S(), U(), A("c1"), T("c1"));
        assertEquals(2, SpaceChatSessionStore.pullInPairedAiMessage(m, 2));
    }

    @Test
    void pullInPairedAiMessage_orphanedToolResult_pullsInAiMessage() {
        // [2]=T{c1} is orphaned — its paired AiMessage is at [1]
        final List<Rel> m = msgs(S(), A("c1"), T("c1"));
        assertEquals(1, SpaceChatSessionStore.pullInPairedAiMessage(m, 2));
    }

    @Test
    void pullInPairedAiMessage_orphanedMiddleToolResult_pullsInAiMessage() {
        // [2]=T{c1} orphaned, but AiMessage at [0] has both c1 and c2
        final List<Rel> m = msgs(A("c1", "c2"), T("c1"), T("c2"));
        assertEquals(0, SpaceChatSessionStore.pullInPairedAiMessage(m, 2));
    }

    @Test
    void pullInPairedAiMessage_toolCallIdNotFound_unchanged() {
        // T{c99} references an id that doesn't exist in any AiMessage
        final List<Rel> m = msgs(S(), U(), A("c1"), T("c99"));
        assertEquals(2, SpaceChatSessionStore.pullInPairedAiMessage(m, 2));
    }

    @Test
    void pullInPairedAiMessage_noToolCallId_unchanged() {
        // ToolResult without a contents/id field
        final Rec orphan = rec(mutableMap(uri(TEXT), str("no id")), TOOL_RESULT_MESSAGE_TID, null);
        final List<Rel> m = msgs(S(), U(), A("c1"), orphan);
        assertEquals(3, SpaceChatSessionStore.pullInPairedAiMessage(m, 3));
    }

    // ── pullInPrecedingUserMessage ───────────────────────────────────

    @Test
    void pullInPrecedingUserMessage_skipZero_unchanged() {
        final List<Rel> m = msgs(U(), A());
        assertEquals(0, SpaceChatSessionStore.pullInPrecedingUserMessage(m, 0));
    }

    @Test
    void pullInPrecedingUserMessage_firstIsUser_unchanged() {
        final List<Rel> m = msgs(S(), U(), A());
        assertEquals(1, SpaceChatSessionStore.pullInPrecedingUserMessage(m, 1));
    }

    @Test
    void pullInPrecedingUserMessage_firstIsSystem_unchanged() {
        // System message at boundary is fine — it's the absolute start
        final List<Rel> m = msgs(S(), U(), A());
        assertEquals(0, SpaceChatSessionStore.pullInPrecedingUserMessage(m, 0));
    }

    @Test
    void pullInPrecedingUserMessage_firstIsAi_pullsInUser() {
        // Window starts with AiMessage — need preceding UserMessage
        final List<Rel> m = msgs(S(), U(), A("c1"), T("c1"));
        assertEquals(1, SpaceChatSessionStore.pullInPrecedingUserMessage(m, 2));
    }

    @Test
    void pullInPrecedingUserMessage_firstIsToolResult_pullsInUser() {
        // Window starts with ToolResult (already paired, but still need UserMessage)
        final List<Rel> m = msgs(S(), U(), A("c1"), T("c1"));
        assertEquals(1, SpaceChatSessionStore.pullInPrecedingUserMessage(m, 3));
    }

    @Test
    void pullInPrecedingUserMessage_stopsAtSystem() {
        // No UserMessage between system and AiMessage — fall back to system
        final List<Rel> m = msgs(S(), A("c1"), T("c1"));
        assertEquals(0, SpaceChatSessionStore.pullInPrecedingUserMessage(m, 1));
    }

    @Test
    void pullInPrecedingUserMessage_noUserMessage_returnsZero() {
        // No UserMessage or SystemMessage at all — return everything
        final List<Rel> m = msgs(A("c1"), T("c1"));
        assertEquals(0, SpaceChatSessionStore.pullInPrecedingUserMessage(m, 1));
    }

    // ── adjustSkipToPreservePairs (combined rules) ──────────────────

    @Test
    void adjustSkip_aiMessageAtBoundary_pullsInUser() {
        // Window starts on A() — rule 2 pulls in preceding UserMessage
        // [S, U, A, U, A], skip=2 → first=[2]=A()
        final List<Rel> m = msgs(S(), U(), A(), U(), A());
        assertEquals(1, SpaceChatSessionStore.adjustSkipToPreservePairs(m, 2));
    }

    @Test
    void adjustSkip_aiAtBoundary_scansThroughToolResultsToFindUser() {
        // skip=5 drops [0..4]; first=[5]=A() which needs a user.
        // Rule 2 scans backward through T{c2}, T{c1}, A{c1,c2}, finds U() at [1].
        final List<Rel> m = msgs(
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
        final List<Rel> m = msgs(S(), U(), A("c1"), T("c1"), U(), A());
        assertEquals(1, SpaceChatSessionStore.adjustSkipToPreservePairs(m, 3));
    }

    @Test
    void adjustSkip_userAtBoundary_noAdjustment() {
        // skip=4 drops [0..3]; first=[4]=U() — already a UserMessage, no change
        final List<Rel> m = msgs(S(), U(), A("c1"), T("c1"), U(), A());
        assertEquals(4, SpaceChatSessionStore.adjustSkipToPreservePairs(m, 4));
    }

    @Test
    void adjustSkip_skipZero_unchanged() {
        final List<Rel> m = msgs(U(), A("c1"), T("c1"));
        assertEquals(0, SpaceChatSessionStore.adjustSkipToPreservePairs(m, 0));
    }

    @Test
    void adjustSkip_skipAtSize_unchanged() {
        final List<Rel> m = msgs(U(), A("c1"), T("c1"));
        assertEquals(3, SpaceChatSessionStore.adjustSkipToPreservePairs(m, 3));
    }

    @Test
    void adjustSkip_multipleRoundsOfTools_preservesAllPairs() {
        // Simulates a real conversation with 3 rounds of tool calls
        // Round 1: U, A{c1,c2}, T{c1}, T{c2}, A
        // Round 2: U, A{c3}, T{c3}, A
        // Round 3: U, A{c4,c5}, T{c4}, T{c5}
        final List<Rel> m = msgs(
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
    void adjustSkip_orphanDeepInWindow_rule3Fires() {
        // Rule 3: boundary passes rules 1+2 (starts with user), but a
        // ToolResult inside the window is orphaned — its AiMessage is
        // before the skip boundary.
        // [0]=A{c0}, [1]=U, [2]=T{c0}
        // rawSkip=2 → [1]=U, [2]=T{c0}
        // Rule 1: [1]=U → no change. Rule 2: [1]=U → no change.
        // Rule 3: [2]=T{c0} → find A{c0} at [0] < skip(1) → skip=0.
        final List<Rel> m = msgs(A("c0"), U(), T("c0"));
        assertEquals(0, SpaceChatSessionStore.adjustSkipToPreservePairs(m, 2));
    }

    @Test
    void adjustSkip_windowLargerThanMessages_skipZero() {
        // More window slots than messages — nothing to skip
        final List<Rel> m = msgs(S(), U(), A());
        assertEquals(0, SpaceChatSessionStore.adjustSkipToPreservePairs(m, 0));
    }

    // ── System-message interleaving (production bug) ──────────────────

    /**
     * Mirror the filter that {@code getMessages()} applies:
     * thinking and system messages are excluded from the LLM context.
     */
    private static List<Rec> filterStoreMessages(final List<Rec> messages) {
        return messages.stream()
                .filter(m -> !m.tid().equals(THINKING_MESSAGE_TID) && !m.tid().equals(SYSTEM_MESSAGE_TID))
                .toList();
    }

    @Test
    void filter_excludesSystemMessages() {
        final List<Rel> m = msgs(U(), S(), A(), S(), T("c1"));
        final List<Rec> filtered = filterStoreMessages(m.stream().map(r -> r.second().asRec()).toList());
        // System messages stripped
        assertEquals(3, filtered.size());
        assertEquals(USER_MESSAGE_TID, filtered.get(0).tid());
        assertEquals(AI_MESSAGE_TID, filtered.get(1).tid());
        assertEquals(TOOL_RESULT_MESSAGE_TID, filtered.get(2).tid());
    }

    @Test
    void filter_excludesThinkingMessages() {
        final Rec thinking = rec(mutableMap(uri(TEXT), str("hmm")), THINKING_MESSAGE_TID, null);
        final List<Rel> m = msgs(U(), thinking, A());
        final List<Rec> filtered = filterStoreMessages(m.stream().map(x -> (Rec) x.second()).toList());
        assertEquals(2, filtered.size());
        assertEquals(USER_MESSAGE_TID, filtered.get(0).tid());
        assertEquals(AI_MESSAGE_TID, filtered.get(1).tid());
    }

    @Test
    void systemMessageInterleavedBetweenToolResults_pairsStayIntact() {
        // Exact reproduction of the production bug:
        // AiMessage(tool_calls: [c0, c1, c2])
        // ToolResult(c0)
        // ToolResult(c1)
        // SystemMessage         ← injected by SystemFeature.onBeforeChat() during tool loop
        // ToolResult(c2)        ← becomes orphaned if system message not filtered
        final List<Rel> m = msgs(
                U(),
                A("c0", "c1", "c2"),
                T("c0"),
                T("c1"),
                S(),             // interleaved system message — the bug
                T("c2")
        );
        final List<Rec> filtered = filterStoreMessages(m.stream().map(x -> (Rec) x.second()).toList());

        // After filtering: U, A{c0,c1,c2}, T{c0}, T{c1}, T{c2}
        assertEquals(5, filtered.size());
        assertEquals(USER_MESSAGE_TID, filtered.get(0).tid());
        assertEquals(AI_MESSAGE_TID, filtered.get(1).tid());
        assertEquals(TOOL_RESULT_MESSAGE_TID, filtered.get(2).tid());
        assertEquals(TOOL_RESULT_MESSAGE_TID, filtered.get(3).tid());
        assertEquals(TOOL_RESULT_MESSAGE_TID, filtered.get(4).tid());

        // Pair-aware skip with a tight window (max=3): rawSkip = 5-3 = 2
        // first=[2]=T{c0} → pullInPairedAiMessage → skip=1 (A{c0,c1,c2})
        // → pullInPrecedingUserMessage → skip=0 (U) — window must start with user
        final int skip = SpaceChatSessionStore.adjustSkipToPreservePairs(filtered.stream().map(r -> rel(uri("temp"), r)).toList(), 2);
        assertEquals(0, skip);

        final List<Rec> window = filtered.subList(skip, filtered.size());
        assertEquals(5, window.size());
        assertEquals(USER_MESSAGE_TID, window.get(0).tid());          // starts with user
        assertEquals(AI_MESSAGE_TID, window.get(1).tid());            // AiMessage with tool_calls
        assertEquals(TOOL_RESULT_MESSAGE_TID, window.get(2).tid());   // T{c0}
        assertEquals(TOOL_RESULT_MESSAGE_TID, window.get(3).tid());   // T{c1}
        assertEquals(TOOL_RESULT_MESSAGE_TID, window.get(4).tid());   // T{c2}
    }

    @Test
    void multipleSystemMessagesInterleaved_allStripped() {
        // Multiple system message injections during multi-round tool calls
        final List<Rel> m = msgs(
                U(),
                A("c0", "c1"),
                T("c0"),
                S(),             // injected
                T("c1"),
                S(),             // injected
                A(),
                S(),             // injected
                U(),
                A("c2"),
                S(),             // injected
                T("c2")
        );
        final List<Rec> filtered = filterStoreMessages(m.stream().map(x -> (Rec) x.second()).toList());

        // Should be: U, A{c0,c1}, T{c0}, T{c1}, A, U, A{c2}, T{c2}
        assertEquals(8, filtered.size());
        // Verify no system messages remain
        assertTrue(filtered.stream().noneMatch(r -> r.tid().equals(SYSTEM_MESSAGE_TID)));
        // Verify tool-call/result pairs are consecutive
        // First pair: A{c0,c1} @ idx 1, T{c0} @ idx 2, T{c1} @ idx 3
        assertEquals(AI_MESSAGE_TID, filtered.get(1).tid());
        assertEquals(TOOL_RESULT_MESSAGE_TID, filtered.get(2).tid());
        assertEquals(TOOL_RESULT_MESSAGE_TID, filtered.get(3).tid());
        // Second pair: A{c2} @ idx 6, T{c2} @ idx 7
        assertEquals(AI_MESSAGE_TID, filtered.get(6).tid());
        assertEquals(TOOL_RESULT_MESSAGE_TID, filtered.get(7).tid());

        // Pair-aware skip: rawSkip = 8-3 = 5, first=[5]=U → unchanged
        assertEquals(5, SpaceChatSessionStore.adjustSkipToPreservePairs(filtered.stream().map(r -> rel(uri("temp"), r)).toList(), 5));
    }
}
