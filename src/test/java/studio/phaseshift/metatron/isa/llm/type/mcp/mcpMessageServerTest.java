/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.llm.type.mcp;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.isa.web.space.AbstractMcpHandlerTest;
import studio.phaseshift.metatron.isa.web.type.mcpServer;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.furi.q.QCollection.incrQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.MCP_SERVER_TYPE;


/**
 * Protocol-level tests for the {@code mcp_message} tools
 * (add_message / get_messages / search_messages), driven straight through
 * {@link mcpServer#handleMessage(Obj)} — no transport involved.
 * <p>
 * The ledger under test lives at {@code mcpmsg/message/#} in a per-test
 * memspace; each test gets a fresh empty ledger.
 */
public class mcpMessageServerTest extends AbstractMcpHandlerTest {

    private static String LEDGER;
    private static String MESSAGE_ROOT = "mcpmsg";
    // sessions are vids under the agent root — the native topology (<root>/session/<id>)
    private static final String DSH_SESSION = "mcpmsg/session/dsh-4f2a-harbor";
    private static final String STORM_SESSION = "mcpmsg/session/dsh-storm";
    private static final String FROST_SESSION = "mcpmsg/session/dsh-frost";

    @Override
    protected fURI testSpacePattern() {
        return f(MESSAGE_ROOT + ":#");
    }

    @Override
    protected mcpServer createMcpServer() {
        InstSet.importInstSet(LLM_ISA_TID);
        InstSet.importInstSet(MATH_ISA_TID);
        LEDGER = this.testSpacePattern().retractPattern().toString();
        // the bus is (root, session) over the space — no agent rec needed at
        // the root (the live /usr/dr topology has no record at its root either)
        // the mcp_message type's constructor directly materializes the transport-agnostic mcpServer
        final Obj server = mcpMessageServer.server().constructor().apply(rec());
        if (server.isFail())
            throw new IllegalStateException("mcp_message constructor failed: " + server.toCleanString());
        return server.as();
    }

    @Test
    public void serverIsAnMcpServerType() {
        // the spaces wrap a route target that is an mcp_server — verify mcp_message
        // registers as one so both httpSpace and wsSpace pick it up automatically
        assertTrue(Obj.Helper.specificType(mcpMessageServer.server()).test(MCP_SERVER_TYPE),
                "mcp_message must be an mcp_server type so the spaces wrap it");
    }

    // ========================================
    // Helpers
    // ========================================

    private Rec callTool(final String tool, final Rec arguments) {
        return mcpRequest(request(1, "tools/call", rec(uri(NAME), str(tool), uri("arguments"), arguments)));
    }

    private String callText(final String tool, final Rec arguments) {
        final Rec res = callTool(tool, arguments);
        final Obj result = res.at(uri(RESULT));
        if (result.isNoObj())
            throw new IllegalStateException("tools/call for " + tool + " returned an error: " + res);
        final Obj content = result.asRec().at(uri(CONTENT));
        assertFalse(content.isNoObj(), tool + " must return a content entry: " + res);
        final String text = content.asLst().at(0).asRec().at(uri(TEXT)).toCleanString();
        // the result text is JSON on the wire — parse it back to an Obj so callers
        // keep asserting on the mtron rendering, not the raw JSON
        return ObjJSONSerializer.simple().readString(text).toString();
    }

    /** The uri of one of this server's ledger tools, as exposed on the wire. */
    private String ledgerTool(final String tool) {
        return mcp.at(TOOL).asRec().keys()
                .filter(r -> r.uriValue().name().contains(tool))
                .findFirst().get().uriValue().toString();
    }

    /** Append one message and return the receipt (the mtron rendering). */
    private String addMessage(final Rec arguments) {
        return callText(ledgerTool("add_message"), arguments);
    }

    /** Read a session's ledger back, newest first (the bus's own reading order). */
    private String ledger(final String root, final String session) {
        return callText(ledgerTool("get_messages"), rec(
                uri(ROOT), str(root),
                uri(SESSION), uri(session)));
    }

    /**
     * An absolute ledger root of this test's own — the pairing gate is keyed by
     * the ledger, so a scenario that holds a tool group open must not share a
     * root with the next scenario.  The space serving ledger messages must be
     * registered with {@code addQ(incrQ())} (in Java) — ledger writes are
     * appended at {@code <root>/message/_?incrq}, and a relative root falls
     * through to the ephemeral stack space.
     */
    private static String ledgerRoot(final String scenario) {
        final fURI root = f("/busmsg/" + scenario);
        memSpace.of(root.extend("#"), f("/sys/space/busmsg/" + scenario)).addQ(incrQ());
        return root.toString();
    }

    // ========================================
    // tools/list
    // ========================================

    @ParameterizedTest(name = "tools/list exposes {0}")
    @ValueSource(strings = {"add_message", "get_messages", "search_messages"})
    @Disabled
    public void toolsListExposesTheMessageTools(final String tool) {
        final Rec res = mcpRequest(request(11, "tools/list"));
        final Obj tools = res.at(uri(RESULT)).asRec().at(uri("tools"));
        final boolean found = tools.asLst().lstValue().stream()
                .anyMatch(t -> t.asRec().at(uri(NAME)).toCleanString().equals(tool));
        assertTrue(found, "expected tool " + tool + " in the tools list: " + tools);
    }

    // ========================================
    // add_message
    // ========================================

    // note: tool_result is not in this matrix — its type (LLM_TOOL_RESULT_MESSAGE_TYPE)
    // requires name + text, so it has its own dedicated scenario below
    @ParameterizedTest(name = "add_message writes kind={0}")
    @CsvSource(value = {
            "user        % the harbor light blinks twice before dawn",
            "system      % answer in the voice of a lighthouse keeper",
            "thinking    % the keeper meant the twin lamps, not the single one",
            "compaction  % resume — the keeper tallied the spare bulbs and lit the fog lamp",
    }, delimiter = '%')
    public void addMessageAcceptsEveryKind(final String kind, final String text) {
        final String written = callText(mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("add_message")).findFirst().get().uriValue().toString(), rec(
                uri(ROOT), str(LEDGER),
                uri(KIND), str(kind),
                uri(TEXT), str(text),
                uri(SESSION), str(DSH_SESSION)));
        assertTrue(written.contains(text), "written message should carry its text: " + written);
        assertTrue(written.contains("depth=>1"), "envelope should carry depth 1: " + written);
        if (kind.equals("compaction"))
            assertTrue(written.contains("compaction"), "compaction kind should write the sentinel tid: " + written);
    }

    @Test
    public void addMessageAiCarriesToolRequests() {
        final String root = ledgerRoot("ai-carries-requests");
        final String session = root + "/session/dsh-carries";
        final String written = addMessage(rec(
                uri(ROOT), str(root),
                uri(KIND), str("ai"),
                uri(TEXT), str("let me count the spare bulbs"),
                uri(TOOL_REQUESTS), lst(rec(
                        uri(NAME), str("m_tble_inst_sql"),
                        uri(ARGS), str("{\"0\":\"select count(*) from bulbs\"}"),
                        uri(CONTENTS), str("call_lighthouse_1"))),
                uri(SESSION), str(session)));
        assertTrue(written.contains("tool_requests"), "ai message should carry tool_requests: " + written);
        assertTrue(written.contains("call_lighthouse_1"), "tool_request should carry its call id: " + written);
        assertTrue(written.contains("{\"0\":\"select count(*) from bulbs\"}"), "tool_request should carry its args: " + written);
        assertTrue(written.contains("m_tble_inst_sql({\"0\":\"select count(*) from bulbs\"})"),
                "tool_request text should be the name(args) summary: " + written);
        // the pairing gate holds a tool-calling turn until its results arrive —
        // the receipt says so rather than the ledger getting half a group
        assertTrue(written.contains("status=>parked"), "an unanswered tool group is held: " + written);
        assertFalse(ledger(root, session).contains("spare bulbs"),
                "a held ai message must not reach the ledger: " + ledger(root, session));
    }

    // json wire arguments — an mcp client may deliver tool_requests as a json
    // string; add_message decodes it with the json serializer (the mtron
    // parser garbles json: list-of-recs lands as objs, not recs)
    @ParameterizedTest(name = "add_message decodes json tool_requests: {0}")
    @CsvSource(value = {
            "a lone call  % [{\"name\":\"m_tble_inst_sql\",\"contents\":\"call_tide_1\"}] % call_tide_1",
            "a paired call % [{\"name\":\"m_tble_inst_sql\",\"contents\":\"call_tide_2\"},{\"name\":\"m_probe_buoy\",\"contents\":\"call_tide_3\"}] % call_tide_3",
    }, delimiter = '%', quoteCharacter = '\'')
    public void addMessageDecodesJsonToolRequests(final String desc, final String json, final String marker) {
        final String root = ledgerRoot("json-" + marker);
        final String written = addMessage(rec(
                uri(ROOT), str(root),
                uri(KIND), str("ai"),
                uri(TEXT), str("let the tide ledger keep the tally"),
                uri(TOOL_REQUESTS), str(json),
                uri(SESSION), str(root + "/session/dsh-json")));
        assertTrue(written.contains("tool_requests"), "ai message should carry tool_requests: " + written);
        assertTrue(written.contains(marker), "the json argument should decode to a typed tool_request: " + written);
        assertTrue(written.contains("m_tble_inst_sql"), "the decoded tool_request should keep the tool name: " + written);
        assertTrue(written.contains("status=>parked"), "an unanswered tool group is held: " + written);
    }

    @Test
    public void addMessageToolResultCarriesNameAndCallId() {
        final String root = ledgerRoot("tool-result-shape");
        final String session = root + "/session/dsh-shape";
        // a tool result belongs to the ai message that asked for it — the bus
        // holds it until that ai message arrives (and writes both together)
        addMessage(rec(
                uri(ROOT), str(root), uri(KIND), str("ai"),
                uri(TEXT), str("let me count the spare bulbs"),
                uri(TOOL_REQUESTS), lst(rec(
                        uri(NAME), str("m_tble_inst_sql"),
                        uri(ARGS), str("{\"0\":\"select count(*) from bulbs\"}"),
                        uri(CONTENTS), str("call_lighthouse_1"))),
                uri(SESSION), str(session)));
        final String written = addMessage(rec(
                uri(ROOT), str(root),
                uri(KIND), str("tool_result"),
                uri(TEXT), str("lighthouse log: two bulbs, one spare on the shelf"),
                uri(NAME), str("m_tble_inst_sql"),
                uri(CONTENTS), str("call_lighthouse_1"),
                uri(SESSION), str(session)));
        assertTrue(written.contains("lighthouse log: two bulbs"), "tool_result should carry its text: " + written);
        assertTrue(written.contains("m_tble_inst_sql"), "tool_result should carry the executed tool name: " + written);
        assertTrue(written.contains("call_lighthouse_1"), "tool_result should carry its call id: " + written);
        assertTrue(written.contains("depth=>1"), "envelope should carry depth 1: " + written);
        assertTrue(written.contains("status=>published"), "the result completes its group: " + written);
        final String ledger = ledger(root, session);
        assertTrue(ledger.contains("lighthouse log: two bulbs"), "the complete group reached the ledger: " + ledger);
        assertTrue(ledger.indexOf("lighthouse log") < ledger.indexOf("spare bulbs"),
                "newest first: the result reads before the ai message it answers: " + ledger);
    }

    @ParameterizedTest(name = "add_message rejects kind={0}")
    @ValueSource(strings = {"wizard", "loose_end", "claim"})
    public void addMessageRejectsUnknownKind(final String kind) {
        final Obj res = this.mcp.handleMessage(request(9, "tools/call", rec(
                uri(NAME), str(mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("add_message")).findFirst().get().uriValue().toString()),
                uri("arguments"), rec(uri(ROOT), str(LEDGER), uri(KIND), str(kind), uri(TEXT), str("no such kind"), uri(SESSION), str(DSH_SESSION)))));
        final boolean signaled = res.isFail()
                || (res.isRec() && !res.asRec().at(uri("error")).isNoObj());
        assertTrue(signaled, "unknown kind should produce an error: " + res);
    }

    // ========================================
    // tool group pairing — the ledger's validity invariant
    // ========================================

    private static final String AI_TEXT = "let me count the spare bulbs";

    /** An ai message calling one tool per call id — the shape a harness mirrors. */
    private static Rec aiWithCalls(final String root, final String session, final List<String> callIds) {
        final List<Obj> requests = callIds.stream()
                .map(callId -> (Obj) rec(
                        uri(NAME), str("m_probe_buoy"),
                        uri(ARGS), str("{\"0\":\"count the bulbs in the store shed\"}"),
                        uri(CONTENTS), str(callId)))
                .toList();
        return rec(
                uri(ROOT), str(root),
                uri(KIND), str("ai"),
                uri(TEXT), str(AI_TEXT),
                uri(TOOL_REQUESTS), lst(requests),
                uri(SESSION), str(session),
                uri(CHAT_ID), jnt(7));
    }

    /** One tool result of a group, joined by its call id. */
    private static Rec toolResult(final String root, final String session, final String callId, final String text) {
        return rec(
                uri(ROOT), str(root),
                uri(KIND), str("tool_result"),
                uri(TEXT), str(text),
                uri(NAME), str("m_probe_buoy"),
                uri(CONTENTS), str(callId),
                uri(SESSION), str(session),
                uri(CHAT_ID), jnt(7));
    }

    /**
     * The bus writes tool groups through the same gate the native loop writes
     * through: the ai message is held until every request has its result, then
     * the ai message and its results land together, in request order — a
     * mirrored ledger is exactly as valid as a native one.
     */
    @ParameterizedTest(name = "a tool group of {0} call(s) lands whole")
    @CsvSource(value = {
            "1 % tide",
            "2 % buoy",
            "3 % fog",
    }, delimiter = '%')
    public void addMessageHoldsAToolGroupUntilItsResultsArrive(final int calls, final String scenario) {
        final String root = ledgerRoot(scenario);
        final String session = root + "/session/dsh-" + scenario;
        final List<String> callIds = IntStream.range(0, calls)
                .mapToObj(i -> "call_%s_%d".formatted(scenario, i)).toList();

        // 1. the ai side is held — never half a group in the ledger
        final String aiReceipt = addMessage(aiWithCalls(root, session, callIds));
        assertTrue(aiReceipt.contains("status=>parked"), "an unanswered tool group is held: " + aiReceipt);
        assertFalse(ledger(root, session).contains(AI_TEXT),
                "a held ai message must not reach the ledger: " + ledger(root, session));

        // 2. each result is held too, until the last one completes the group
        for (int i = 0; i < calls; i++) {
            final String resultReceipt = addMessage(toolResult(root, session, callIds.get(i), "bulb tally " + i));
            if (i < calls - 1) {
                assertTrue(resultReceipt.contains("status=>parked"), "a partial group stays out of the ledger: " + resultReceipt);
                assertFalse(ledger(root, session).contains("bulb tally " + i),
                        "no half group in the ledger: " + ledger(root, session));
            } else {
                assertTrue(resultReceipt.contains("status=>published"), "the last result completes the group: " + resultReceipt);
            }
        }

        // 3. the whole group, in request order (the bus reads newest first)
        final String ledger = ledger(root, session);
        assertTrue(ledger.contains(AI_TEXT), "the ai message reached the ledger with its results: " + ledger);
        for (int i = 0; i < calls; i++) {
            assertTrue(ledger.contains("bulb tally " + i), "result %d is in the ledger: %s".formatted(i, ledger));
            assertTrue(ledger.indexOf("bulb tally " + i) < ledger.indexOf(AI_TEXT),
                    "the ai message precedes its results: " + ledger);
        }
        if (calls > 1)
            assertTrue(ledger.indexOf("bulb tally " + (calls - 1)) < ledger.indexOf("bulb tally 0"),
                    "results keep their request order: " + ledger);
    }

    /**
     * The bus's turn end: a user message of a later chat id closes whatever
     * tool group the previous turn left unanswered (a user message of the
     * <em>same</em> chat id does not — the results may still arrive).
     */
    @Test
    public void addMessageClosesAnUnansweredGroupAtTheNextTurn() {
        final String root = ledgerRoot("next-turn");
        final String session = root + "/session/dsh-next-turn";
        addMessage(aiWithCalls(root, session, List.of("call_next_turn")));

        addMessage(rec(
                uri(ROOT), str(root), uri(KIND), str("user"),
                uri(TEXT), str("hold the lamp steady"),
                uri(SESSION), str(session), uri(CHAT_ID), jnt(7)));
        assertFalse(ledger(root, session).contains("lost_tool_result"),
                "the same chat id is not a turn boundary: " + ledger(root, session));
        assertFalse(ledger(root, session).contains(AI_TEXT),
                "the group is still parked within its own turn: " + ledger(root, session));

        addMessage(rec(
                uri(ROOT), str(root), uri(KIND), str("user"),
                uri(TEXT), str("the fog rolled in before the tally"),
                uri(SESSION), str(session), uri(CHAT_ID), jnt(8)));

        final String ledger = ledger(root, session);
        assertTrue(ledger.contains(AI_TEXT), "the abandoned turn still reaches the ledger: " + ledger);
        assertTrue(ledger.contains("lost_tool_result"), "closed with a lost result: " + ledger);
        assertTrue(ledger.contains("call_next_turn"), "the lost result keeps the call id as its join key: " + ledger);
    }

    /** A client that posts the result first is tolerated — the group still pairs. */
    @Test
    public void addMessagePairsAResultThatArrivesBeforeItsRequest() {
        final String root = ledgerRoot("out-of-order");
        final String session = root + "/session/dsh-out-of-order";

        final String held = addMessage(toolResult(root, session, "call_early_tide", "the tide came in early"));
        assertTrue(held.contains("status=>parked"), "a result with no ai message yet is held: " + held);
        assertFalse(ledger(root, session).contains("the tide came in early"),
                "nothing half-written in the ledger: " + ledger(root, session));

        final String published = addMessage(aiWithCalls(root, session, List.of("call_early_tide")));
        assertTrue(published.contains("status=>published"), "the arriving ai message completes the group: " + published);
        final String ledger = ledger(root, session);
        assertTrue(ledger.contains("the tide came in early"), "the held result landed with its ai message: " + ledger);
        assertTrue(ledger.contains(AI_TEXT), "the ai message landed with its result: " + ledger);
    }

    /** A tool result with no call id can never be paired — it is refused, not orphaned. */
    @Test
    public void addMessageRefusesAToolResultWithoutAJoinKey() {
        final String root = ledgerRoot("no-join-key");
        final String session = root + "/session/dsh-no-join-key";
        final String receipt = addMessage(rec(
                uri(ROOT), str(root), uri(KIND), str("tool_result"),
                uri(TEXT), str("a tally with no call id"),
                uri(NAME), str("m_probe_buoy"),
                uri(SESSION), str(session)));
        assertTrue(receipt.contains("status=>unpaired"), "an unjoinable result is refused: " + receipt);
        assertFalse(ledger(root, session).contains("a tally with no call id"),
                "an unjoinable result must not reach the ledger: " + ledger(root, session));
    }

    // ========================================
    // get_messages
    // ========================================

    @Test
    public void getMessagesReturnsLatestFirstWithinSession() {
        for (int chat = 1; chat <= 3; chat++)
            callTool(mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("add_message")).findFirst().get().uriValue().toString(), rec(
                    uri(ROOT), str(LEDGER),
                    uri(KIND), str("user"),
                    uri(TEXT), str("beacon check " + chat),
                    uri(SESSION), uri(DSH_SESSION),
                    uri(CHAT_ID), jnt(chat)));

        final String all = callText(mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("get_messages")).findFirst().get().uriValue().toString(), rec(
                uri(ROOT), str(LEDGER),
                uri(SESSION), uri(DSH_SESSION)));
        assertTrue(all.contains("beacon check 1") && all.contains("beacon check 2") && all.contains("beacon check 3"),
                "all three session messages expected: " + all);
        assertTrue(all.indexOf("beacon check 3") < all.indexOf("beacon check 1"),
                "latest should come first: " + all);
        assertTrue(all.contains("dsh-4f2a-harbor"), "session envelope should be written: " + all);
        assertTrue(all.contains("message/"), "bus records should carry their ledger vids: " + all);
        assertFalse(all.contains("message/noobj"), "bus records should carry their ledger ids, not noobj: " + all);
        assertFalse(all.contains("harbor light blinks"), "session filter should exclude other sessions: " + all);
    }

    @Test
    public void getMessagesShowsThinkingTraces() {
        callTool(mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("add_message")).findFirst().get().uriValue().toString(), rec(
                uri(ROOT), str(LEDGER),
                uri(KIND), str("thinking"),
                uri(TEXT), str("the keeper suspected the fog, not the lamp"),
                uri(SESSION), str(DSH_SESSION)));

        final String out = callText(mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("get_messages")).findFirst().get().uriValue().toString(), rec(
                uri(ROOT), str(LEDGER),
                uri(SESSION), str(DSH_SESSION)));
        assertTrue(out.contains("the keeper suspected the fog"), "the bus is full-fidelity — thinking traces are visible: " + out);
    }

    @Test
    public void getMessagesStopsAtTheCompactionSentinel() {
        final String add = mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("add_message")).findFirst().get().uriValue().toString();
        final String get = mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("get_messages")).findFirst().get().uriValue().toString();
        // tide + gulls, then the roll, then two more entries
        callTool(add, rec(uri(ROOT), str(LEDGER), uri(KIND), str("user"), uri(TEXT), str("tide recorded before the roll"), uri(SESSION), str(DSH_SESSION)));
        callTool(add, rec(uri(ROOT), str(LEDGER), uri(KIND), str("user"), uri(TEXT), str("gulls counted before the roll"), uri(SESSION), str(DSH_SESSION)));
        MessageBuilder.buildCompactionMessage()
                .text("rolled the log: tide and gulls, summarized")
                .session(f(DSH_SESSION))
                .depth(1)
                .time()
                .create(f(LEDGER).extend(MESSAGE).extend("_").addQ(INCRQ));
        callTool(add, rec(uri(ROOT), str(LEDGER), uri(KIND), str("user"), uri(TEXT), str("first light after the roll"), uri(SESSION), str(DSH_SESSION)));
        callTool(add, rec(uri(ROOT), str(LEDGER), uri(KIND), str("user"), uri(TEXT), str("second light after the roll"), uri(SESSION), str(DSH_SESSION)));

        final String out = callText(get, rec(uri(ROOT), str(LEDGER), uri(SESSION), str(DSH_SESSION)));
        assertTrue(out.contains("second light after the roll") && out.contains("first light after the roll"),
                "post-compaction messages expected in the window: " + out);
        assertTrue(out.contains("rolled the log"),
                "the compaction sentinel should close the (latest-first) window: " + out);
        assertFalse(out.contains("tide recorded before the roll"),
                "pre-compaction messages should stay summarized away: " + out);
        assertFalse(out.contains("gulls counted before the roll"),
                "pre-compaction messages should stay summarized away: " + out);
    }

    @Test
    public void getMessagesMaxBoundsTheWindow() {
        for (int chat = 1; chat <= 3; chat++)
            callTool(mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("add_message")).findFirst().get().uriValue().toString(), rec(
                    uri(ROOT), str(LEDGER),
                    uri(KIND), str("user"),
                    uri(TEXT), str("frost rings " + chat),
                    uri(SESSION), str(FROST_SESSION)));

        final String out = callText(mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("get_messages")).findFirst().get().uriValue().toString(), rec(
                uri(ROOT), str(LEDGER),
                uri(SESSION), str(FROST_SESSION),
                uri(MAX), jnt(2)));
        assertTrue(out.contains("frost rings 3") && out.contains("frost rings 2"), "latest two expected: " + out);
        assertFalse(out.contains("frost rings 1"), "max=2 should drop the oldest: " + out);
    }

    // ========================================
    // search_messages
    // ========================================

    @Test
    public void searchMessagesFindsByPattern() {
        callTool(mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("add_message")).findFirst().get().uriValue().toString(), rec(
                uri(ROOT), str(LEDGER),
                uri(KIND), str("user"),
                uri(TEXT), str("the storm lantern flickered over the harbor"),
                uri(SESSION), uri(STORM_SESSION)));

        final String hits = callText(mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("search_messages")).findFirst().get().uriValue().toString(), rec(
                uri(ROOT), str(LEDGER),
                uri(PATTERN), str("storm lantern"),
                uri(SESSION), uri(STORM_SESSION)));
        assertTrue(hits.contains("storm lantern"), "pattern should find its message: " + hits);

        final String miss = callText(mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("search_messages")).findFirst().get().uriValue().toString(), rec(
                uri(ROOT), str(LEDGER),
                uri(PATTERN), str("zzqqx"),
                uri(SESSION), uri(STORM_SESSION)));
        assertFalse(miss.contains("storm lantern"), "no match expected for zzqqx: " + miss);
    }

    // ========================================
    // search_messages — catastrophic pattern guard (SOR regression)
    // ========================================

    @ParameterizedTest
    @Disabled
    @CsvSource(value = {
            "(a+)+$",
            "^(.+)+z",
    }, delimiter = '%')
    public void searchMessagesRejectsCatastrophicPatterns(final String pattern) {
        final String toolName = mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("search_messages")).findFirst().get().uriValue().toString();
        final Obj response = mcp.handleMessage(request(91, "tools/call", rec(
                uri(NAME), str(toolName),
                uri("arguments"), rec(
                        uri(ROOT), str(LEDGER),
                        uri(PATTERN), str(pattern),
                        uri(SESSION), uri(STORM_SESSION)))));
        final boolean rejected = response.isFail()
                || response.isRec() && !response.asRec().at(uri("error")).isNoObj();
        assertTrue(rejected, "nested-quantifier pattern must be rejected, not crash the worker: " + pattern);
        assertTrue(response.toString().contains("nested quantifiers"),
                "the rejection must name the guard: " + response);
    }

    @Test
    public void searchMessagesStillMatchesBenevolentPatterns() {
        callTool(mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("add_message")).findFirst().get().uriValue().toString(), rec(
                uri(ROOT), str(LEDGER),
                uri(KIND), str("user"),
                uri(TEXT), str("the gull slipped past the lighthouse"),
                uri(SESSION), uri(STORM_SESSION)));
        final String hits = callText(mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("search_messages")).findFirst().get().uriValue().toString(), rec(
                uri(ROOT), str(LEDGER),
                uri(PATTERN), str("gull.+past"),
                uri(SESSION), uri(STORM_SESSION)));
        assertTrue(hits.contains("lighthouse"), "dot-pattern should still match: " + hits);
    }
}
