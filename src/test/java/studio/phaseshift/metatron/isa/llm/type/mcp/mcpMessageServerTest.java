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
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.web.space.AbstractMcpHandlerTest;
import studio.phaseshift.metatron.isa.web.type.mcpServer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;


/**
 * Protocol-level tests for the {@code mcp_message_server} tools
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
        // apply(Obj) on a Type is the predicate test — instantiation goes through the constructor
        final Obj server = mcpMessageServer.wsHandler().constructor().apply(rec());
        if (server.isFail())
            throw new IllegalStateException("mcp_message_server constructor failed: " + server.toCleanString());
        return new mcpServer(server.recValue(), server.tid(), server.vid());
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
        return content.asLst().at(0).asRec().at(uri(TEXT)).toCleanString();
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
    }, delimiter = '%')
    public void addMessageAcceptsEveryKind(final String kind, final String text) {
        final String written = callText(mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("add_message")).findFirst().get().uriValue().toString(), rec(
                uri(ROOT), str(LEDGER),
                uri(KIND), str(kind),
                uri(TEXT), str(text),
                uri(SESSION), str(DSH_SESSION)));
        assertTrue(written.contains(text), "written message should carry its text: " + written);
        assertTrue(written.contains("depth=>1"), "envelope should carry depth 1: " + written);
    }

    @Test
    public void addMessageAiCarriesToolRequests() {
        final String written = callText(mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("add_message")).findFirst().get().uriValue().toString(), rec(
                uri(ROOT), str(LEDGER),
                uri(KIND), str("ai"),
                uri(TEXT), str("let me count the spare bulbs"),
                uri(TOOL_REQUESTS), lst(rec(
                        uri(NAME), str("m_tble_inst_sql"),
                        uri(ARGS), str("{\"0\":\"select count(*) from bulbs\"}"),
                        uri(CONTENTS), str("call_lighthouse_1"))),
                uri(SESSION), str(DSH_SESSION)));
        assertTrue(written.contains("tool_requests"), "ai message should carry tool_requests: " + written);
        assertTrue(written.contains("call_lighthouse_1"), "tool_request should carry its call id: " + written);
        assertTrue(written.contains("{\"0\":\"select count(*) from bulbs\"}"), "tool_request should carry its args: " + written);
        assertTrue(written.contains("m_tble_inst_sql({\"0\":\"select count(*) from bulbs\"})"),
                "tool_request text should be the name(args) summary: " + written);
    }

    @Test
    public void addMessageToolResultCarriesNameAndCallId() {
        final String written = callText(mcp.at(TOOL).asRec().keys().filter(r -> r.uriValue().name().contains("add_message")).findFirst().get().uriValue().toString(), rec(
                uri(ROOT), str(LEDGER),
                uri(KIND), str("tool_result"),
                uri(TEXT), str("lighthouse log: two bulbs, one spare on the shelf"),
                uri(NAME), str("m_tble_inst_sql"),
                uri(CONTENTS), str("call_lighthouse_1"),
                uri(SESSION), str(DSH_SESSION)));
        assertTrue(written.contains("lighthouse log: two bulbs"), "tool_result should carry its text: " + written);
        assertTrue(written.contains("m_tble_inst_sql"), "tool_result should carry the executed tool name: " + written);
        assertTrue(written.contains("call_lighthouse_1"), "tool_result should carry its call id: " + written);
        assertTrue(written.contains("depth=>1"), "envelope should carry depth 1: " + written);
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
}
