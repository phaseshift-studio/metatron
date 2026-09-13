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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.llm.space.ToolPairGate;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.furi.q.QCollection.incrQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.AI_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.TOOL_RESULT_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The ledger fsck: it reports the one corruption that breaks every reader — an ai
 * message whose tool requests were never answered — and can write the missing
 * results on request.
 */
public class LedgerUtilTest extends AbstractMetatronTest {

    private static final AtomicInteger ROOTS = new AtomicInteger();
    private static final String CALL_ID = "call_probe_7";

    @BeforeAll
    public static void mountLedgerSpace() {
        InstSet.importInstSet(f("/m/llm"));
        InstSet.importInstSet(MATH_ISA_TID, f("math"));
        memSpace.of(f("/usr/test/#"), f("/sys/space/usr/test")).addQ(incrQ());
    }

    @Test
    public void testACleanLedgerReportsNothing() {
        final fURI root = freshRoot();
        writeAiRequest(root, CALL_ID);
        writeToolResult(root, CALL_ID);
        assertTrue(LedgerUtil.clean(LedgerUtil.sweep(root, false)),
                "a request with its result is not a finding: " + LedgerUtil.sweep(root, false));
    }

    @Test
    public void testAnUnansweredRequestIsReported() {
        final fURI root = freshRoot();
        writeAiRequest(root, CALL_ID);
        final List<String> findings = ids(LedgerUtil.sweep(root, false), LedgerUtil.ORPHAN);
        assertEquals(List.of(CALL_ID), findings,
                "the unanswered request is reported under orphan, by call id: " + findings);
    }

    @Test
    public void testAClassifiedRequestIsNotAFinding() {
        final fURI root = freshRoot();
        writeAiRequest(root, CALL_ID);
        writeToolResult(root, "call_some_other_probe");
        final List<String> findings = ids(LedgerUtil.sweep(root, false), LedgerUtil.ORPHAN);
        assertEquals(List.of(CALL_ID), findings,
                "only the id with no result is unanswered — findings: " + findings);
    }

    @Test
    public void testRepairDropsTheRequestAndLeavesAValidLedger() {
        final fURI root = freshRoot();
        writeAiRequest(root, CALL_ID);
        assertEquals(List.of(CALL_ID), ids(LedgerUtil.sweep(root, true), LedgerUtil.ORPHAN),
                "the repair still reports what it fixed");
        assertTrue(LedgerUtil.clean(LedgerUtil.sweep(root, false)),
                "and a second sweep finds nothing: " + LedgerUtil.sweep(root, false));
        assertTrue(requests(root).isEmpty(),
                "the unanswered request is gone from its message — a provider needs the answer to sit "
                        + "immediately after the ask, and nothing can be appended next to a written message");
    }

    /**
     * The shape an earlier, wrong repair left behind: the results were appended at
     * the end of the ledger, so they exist but are nowhere near the message that
     * asked for them.  The provider rejects that, so it must be a finding.
     */
    @Test
    public void testAResultThatIsNotAdjacentIsAFinding() {
        final fURI root = freshRoot();
        writeAiRequest(root, CALL_ID);
        writeUnrelatedMessage(root);
        writeToolResult(root, CALL_ID);
        assertEquals(List.of(CALL_ID), ids(LedgerUtil.sweep(root, false), LedgerUtil.MISPLACED),
                "the request is reported as misplaced — its result exists, but not next to it");
    }

    /**
     * Repair never deletes a ledger row.  The stranded result is reported, and
     * removing it takes an explicit prune — the fsck should not delete content on
     * its own initiative.
     */
    @Test
    public void testRepairReportsTheStrandedResultAndPruneRemovesIt() {
        final fURI root = freshRoot();
        writeAiRequest(root, CALL_ID);
        writeUnrelatedMessage(root);
        writeToolResult(root, CALL_ID);
        final Obj findings = LedgerUtil.sweep(root, true); // repair, explicitly without prune
        assertEquals(List.of(CALL_ID), ids(findings, LedgerUtil.MISPLACED), "the request it dropped");
        assertEquals(List.of(CALL_ID), ids(LedgerUtil.sweep(root, false), LedgerUtil.ORPHAN_RESULT),
                "the stranded result is still there — repair removes no row");
        assertEquals(List.of(CALL_ID), ids(LedgerUtil.sweep(root, true, true), LedgerUtil.ORPHAN_RESULT),
                "and prune is what removes it");
        assertTrue(LedgerUtil.clean(LedgerUtil.sweep(root, false)),
                "leaving a valid ledger: " + LedgerUtil.sweep(root, false));
    }

    /**
     * A complete group written twice — the corruption the drstynx ledger actually
     * held.  Its answers were consumed by the first copy, so the second can never
     * complete; repair makes it valid without deleting it, and prune is the opt-in
     * to removing it outright.
     */
    @Test
    public void testADuplicateIsRepairedWithoutLosingTheMessage() {
        final fURI root = freshRoot();
        writeAiRequest(root, CALL_ID);
        writeToolResult(root, CALL_ID);
        writeAiRequest(root, CALL_ID); // the same requests, a second time
        assertEquals(List.of(CALL_ID), ids(LedgerUtil.sweep(root, false), LedgerUtil.DUPLICATE),
                "recognised as a duplicate rather than a missing result");
        LedgerUtil.sweep(root, true); // repair, explicitly without prune
        assertTrue(LedgerUtil.clean(LedgerUtil.sweep(root, false)),
                "the repair leaves a valid ledger, got: " + LedgerUtil.sweep(root, false));
        assertEquals(3L, messageCount(root),
                "and removes no row — the duplicate keeps its prose, it just stops asking for results it cannot get");
    }

    @Test
    public void testPruneDeletesTheDuplicate() {
        final fURI root = freshRoot();
        writeAiRequest(root, CALL_ID);
        writeToolResult(root, CALL_ID);
        writeAiRequest(root, CALL_ID);
        assertEquals(List.of(CALL_ID), ids(LedgerUtil.sweep(root, true, true), LedgerUtil.DUPLICATE),
                "the duplicate is reported");
        assertEquals(2L, messageCount(root), "and pruned — only the complete group remains");
        assertTrue(LedgerUtil.clean(LedgerUtil.sweep(root, false)), "leaving a valid ledger");
    }

    /**
     * A finding must locate the message it is about.  Reporting "in null" not only
     * says nothing useful, it was the visible symptom of the vid being dropped on
     * the way out of the read — which also broke the ledger-id sort, and with it
     * every adjacency comparison the sweep makes.
     */
    @Test
    public void testFindingsNameTheMessageByItsLedgerVid() {
        final fURI root = freshRoot();
        writeAiRequest(root, CALL_ID);
        assertEquals(List.of(CALL_ID), ids(LedgerUtil.sweep(root, false), LedgerUtil.ORPHAN),
                "the unanswered request is the finding");
    }

    @Test
    public void testAnEmptyLedgerIsClean() {
        assertTrue(LedgerUtil.clean(LedgerUtil.sweep(freshRoot(), false)), "nothing written, nothing to repair");
    }

    // ── fixture ────────────────────────────────────────────────────

    private static fURI freshRoot() {
        return f("/usr/test/ledger" + ROOTS.incrementAndGet());
    }

    /** An ai message whose tool_requests carry the given call id. */
    private static void writeAiRequest(final fURI root, final String callId) {
        writeAiRequestAt(root.extend(MESSAGE).extend("_").addQ(INCRQ), callId);
    }

    /**
     * The same message at a named row — for a test whose rows share a ledger and must
     * therefore overwrite one fixture rather than accumulate several.
     */
    private static void writeAiRequestAt(final fURI location, final String callId) {
        MessageBuilder.build(AI_MESSAGE_TID)
                .text("running a probe")
                .put(TOOL_REQUESTS, lst(rec(mutableMap(
                        uri(NAME), uri("probe_tool"),
                        uri(TEXT), str("probe_tool()"),
                        uri(CONTENTS), str(callId)))))
                .time()
                .create(location);
    }

    /** Any message at all — used to break the adjacency between a request and its result. */
    private static void writeUnrelatedMessage(final fURI root) {
        MessageBuilder.buildUserMessage()
                .text("meanwhile, on another subject")
                .time()
                .create(root.extend(MESSAGE).extend("_").addQ(INCRQ));
    }

    /**
     * A session names its own ledger root — and which of the two ways it is asked
     * depends on how it arrived.  {@code @} is a location and carries the vid, so an
     * anchored session locates itself; {@code *} is a value and does not, so a row
     * that lost its address is asked for its {@code agent} field instead.
     *
     * <p>The fixture's address and its {@code agent} field <b>deliberately
     * disagree</b> ({@code /usr/test/ledger11} vs {@code /usr/test/ledger99}), so no
     * row here can pass by consulting the wrong one.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "@/usr/test/ledger11/session/1 % /usr/test/ledger11 % an anchored session is located by its own address",
            "*/usr/test/ledger11/session/1 % /usr/test/ledger99 % a row read without its vid falls back to its agent field",
            "/usr/test/ledger11/session/1  % /usr/test/ledger11 % a bare session address retracts to its ledger root",
            "29                            % <ERROR>          % something that is not a session is refused",
    }, delimiter = '%')
    public void testHowASessionNamesItsLedgerRoot(final String session, final String expected, final String why) {
        ObjmtronSerializer.parse("/usr/test/ledger11/session/1 -> "
                + "[agent=>/usr/test/ledger99,user=>/usr/test/ann,algorithm=>[policy=>'window']]").apply();
        final Obj target = ObjmtronSerializer.parse(session.trim()).apply();
        if ("<ERROR>".equals(expected))
            assertThrows(MTronException.class, () -> LedgerUtil.rootFor(target), why);
        else
            assertEquals(f(expected), LedgerUtil.rootFor(target), why);
    }

    /**
     * The instruction form.  A session already says where its ledger lives, so
     * sweeping one names no root — but only the anchored spelling carries the vid
     * that root is derived from, which is why the examples lead with {@code @}.
     * Both spellings must reach the same ledger.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "@/usr/test/ledger12/session/1.sweep()            % the fluent form, on an anchored session",
            "sweep(session=>@/usr/test/ledger12/session/1)    % the function form, session handed in as an arg",
    }, delimiter = '%')
    public void testAnInstructionSweepsASessionsLedger(final String sweep, final String why) {
        ObjmtronSerializer.parse("/usr/test/ledger12/session/1 -> "
                + "[agent=>/usr/test/ledger12,user=>/usr/test/ann,algorithm=>[policy=>'window']]").apply();
        writeAiRequestAt(f("/usr/test/ledger12/message/1"), CALL_ID);
        assertEquals(List.of(CALL_ID), ids(ObjmtronSerializer.parse(sweep).apply(), LedgerUtil.ORPHAN), why);
    }

    /**
     * A group whose members disagree on their scope is <em>torn</em> in the model's
     * window even though the raw ledger is perfect: the store reads a session at one
     * depth, so the results are filtered out from under their own request and the
     * assistant message is left holding {@code tool_calls} that nothing answers.
     *
     * <p>The results are right there — only the stamp is wrong, because a result is
     * built when its tool returns, which for a slow tool is after its turn unwound.
     * So this must be its own finding with its own repair, and the repair must
     * <b>move</b> the result rather than drop the request: reporting it as misplaced
     * would hand a complete group to the drop-the-request repair and destroy the very
     * answer that was never missing.
     */
    @Test
    public void testAResultStampedIntoAnotherScopeIsMovedNotLost() {
        final fURI root = freshRoot();
        final fURI session = root.extend(SESSION).extend("1");
        writeScopedAiRequest(root, session, CALL_ID);
        writeScopedToolResult(root, session, CALL_ID, 0); // the depth the agent had drifted to

        final Obj findings = LedgerUtil.sweep(root, false);
        assertEquals(List.of(CALL_ID), ids(findings, LedgerUtil.MISSCOPED), "the tear is its own finding");
        assertEquals(List.of(), ids(findings, LedgerUtil.MISPLACED), "it is not a result that landed in the wrong place");
        assertEquals(List.of(), ids(findings, LedgerUtil.ORPHAN_RESULT), "nor an orphan result — it has a home");

        LedgerUtil.sweep(root, true);

        assertTrue(LedgerUtil.clean(LedgerUtil.sweep(root, false)),
                "the repair leaves a valid ledger, got: " + LedgerUtil.sweep(root, false));
        assertEquals(2L, messageCount(root), "and drops nothing: the result was moved, not discarded");
        final Rec result = Router.readFromSpace(root.extend(MESSAGE).extend("+/")).stream()
                .map(Obj::asRel)
                .map(Rel::second)
                .map(Obj::asRec)
                .filter(message -> message.tid().equals(TOOL_RESULT_MESSAGE_TID))
                .findFirst()
                .orElseThrow();
        assertEquals(1, result.at(uri(DEPTH)).intValue().intValue(),
                "the result now sits in the scope of the request that asked for it");
    }

    /** An ai request stamped with an explicit scope. */
    private static void writeScopedAiRequest(final fURI root, final fURI session, final String callId) {
        MessageBuilder.build(AI_MESSAGE_TID)
                .text("running a probe")
                .put(TOOL_REQUESTS, lst(rec(mutableMap(
                        uri(NAME), uri("probe_tool"),
                        uri(TEXT), str("probe_tool()"),
                        uri(CONTENTS), str(callId)))))
                .session(session)
                .depth(1)
                .chatId(1)
                .time()
                .create(root.extend(MESSAGE).extend("_").addQ(INCRQ));
    }

    /** The result that answers it, stamped with whatever depth it was written at. */
    private static void writeScopedToolResult(final fURI root, final fURI session, final String callId, final int depth) {
        MessageBuilder.build(TOOL_RESULT_MESSAGE_TID)
                .put(NAME, uri("probe_tool"))
                .text("the probe answered")
                .contents(callId)
                .session(session)
                .depth(depth)
                .chatId(1)
                .time()
                .create(root.extend(MESSAGE).extend("_").addQ(INCRQ));
    }

    /** How many rows the ledger holds. */
    private static long messageCount(final fURI root) {
        final Obj rows = Router.readFromSpace(root.extend(MESSAGE).extend("+/"));
        if (rows.isNoObj())
            return 0L;
        return rows.stream().map(Obj::asRel).map(Rel::second).filter(Obj::isRec).count();
    }

    /** The call ids a sweep reported under one failure mode. */
    private static List<String> ids(final Obj findings, final String mode) {
        final Obj list = findings.asRec().at(uri(mode));
        return list.isNoObj() ? List.of()
                : list.asLst().elements().map(Str.Helper::cleanString).toList();
    }

    /** Every tool request id still carried by the ledger's ai messages. */
    private static List<String> requests(final fURI root) {
        final Obj rows = Router.readFromSpace(root.extend(MESSAGE).extend("+/"));
        if (rows.isNoObj())
            return List.of();
        return rows.stream()
                .map(Obj::asRel)
                .map(Rel::second)
                .filter(Obj::isRec)
                .map(Obj::asRec)
                .filter(message -> message.has(TOOL_REQUESTS))
                .flatMap(message -> ToolPairGate.toolCallIds(message).stream())
                .toList();
    }

    /** A tool result answering the given call id. */
    private static void writeToolResult(final fURI root, final String callId) {
        MessageBuilder.buildToolResultMessage()
                .put(NAME, uri("probe_tool"))
                .text("the probe answer")
                .contents(callId)
                .time()
                .create(root.extend(MESSAGE).extend("_").addQ(INCRQ));
    }
}
