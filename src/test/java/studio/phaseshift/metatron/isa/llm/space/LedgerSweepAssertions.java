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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.AI_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The ledger fsck, exercised against a real store rather than an in-memory space.
 *
 * <p>Shared by the session integration tests (every space backend, when their model
 * is reachable) and by a sqlite-backed ledger test that has no model dependency at
 * all — so at least one of them always runs.
 *
 * <p>Why this exists at all: {@link LedgerUtilTest} uses a {@code memSpace}, and the
 * read path differs by backend.  Running against a real store is what caught the
 * value read back from a tble space not carrying its own vid (it lives on the rel)
 * — which silently broke the ledger-id sort, and with it every adjacency comparison,
 * since adjacency <em>is</em> order.  The visible symptom was hundreds of findings
 * reported against a null vid.
 *
 * <p>No chat is involved: this exercises the store, not a model.
 */
final class LedgerSweepAssertions {

    /** A request whose result is written right after it — a group the provider accepts. */
    private static final String HEALTHY_CALL = "call_probe_healthy";

    /** A request nothing ever answered. */
    private static final String ORPHAN_CALL = "call_probe_orphan";

    private LedgerSweepAssertions() {
        // test helper
    }

    /**
     * Write one healthy group and one broken group into {@code sessionVID}'s ledger,
     * and assert the sweep finds exactly the broken one, locates it by a real vid,
     * repairs it without deleting anything, and prunes only when asked.
     *
     * <p>Driven through {@link LedgerUtil#sweepSession} deliberately: that is the
     * production entry point — it derives the ledger root from the session the way
     * the store does — so this exercises the derivation too.
     */
    static void verify(final fURI sessionVID) {
        final fURI memoryRoot = SpaceChatSessionStore.memoryRootOf(sessionVID);
        final fURI ledger = memoryRoot.extend(MESSAGE).extend("_").addQ(INCRQ);

        // a request with its result immediately after it is what the gate writes
        Router.writeToSpace(ledger, aiRequest(HEALTHY_CALL));
        Router.writeToSpace(ledger, toolResult(HEALTHY_CALL));
        assertTrue(LedgerUtil.clean(LedgerUtil.sweepSession(sessionVID, false, false)),
                "a request followed immediately by its result is clean, got: " + LedgerUtil.sweepSession(sessionVID, false, false));

        // an answer that never arrived
        Router.writeToSpace(ledger, aiRequest(ORPHAN_CALL));
        final List<String> findings = ids(LedgerUtil.sweepSession(sessionVID, false, false), LedgerUtil.ORPHAN);
        assertEquals(List.of(ORPHAN_CALL), findings, "the unanswered request is the only finding, got: " + findings);

        // and the repair must actually write, not report and no-op
        LedgerUtil.sweepSession(sessionVID, true, false);
        assertTrue(LedgerUtil.clean(LedgerUtil.sweepSession(sessionVID, false, false)),
                "the repair leaves a valid ledger, got: " + LedgerUtil.sweepSession(sessionVID, false, false));

        // the shape the drstynx ledger actually holds: the same complete group
        // written a second time.  The gate's dedup memory (PUBLISHED) is in-JVM, so
        // a restart lets a re-offered group be published again — and the second copy
        // takes no results, because the first consumed them.
        Router.writeToSpace(ledger, aiRequest(HEALTHY_CALL));
        assertEquals(List.of(HEALTHY_CALL), ids(LedgerUtil.sweepSession(sessionVID, false, false), LedgerUtil.DUPLICATE),
                "the second copy is reported as a duplicate, not as a missing result");

        // repair is non-destructive: the duplicate becomes valid where it stands
        final long before = messageCount(memoryRoot);
        LedgerUtil.sweepSession(sessionVID, true, false);
        assertTrue(LedgerUtil.clean(LedgerUtil.sweepSession(sessionVID, false, false)),
                "the repair leaves a valid ledger, got: " + LedgerUtil.sweepSession(sessionVID, false, false));
        assertEquals(before, messageCount(memoryRoot),
                "and deletes nothing — the duplicate keeps its prose, it just stops asking for answers it cannot get");

        // prune is the explicit opt-in to deleting it
        Router.writeToSpace(ledger, aiRequest(HEALTHY_CALL));
        assertEquals(List.of(HEALTHY_CALL), ids(LedgerUtil.sweepSession(sessionVID, true, true), LedgerUtil.DUPLICATE),
                "the duplicate is reported");
        assertEquals(before, messageCount(memoryRoot), "and pruned — back to the size it was before");
    }

    /** How many rows the ledger holds. */
    private static long messageCount(final fURI memoryRoot) {
        final Obj rows = Router.readFromSpace(memoryRoot.extend(MESSAGE).extend("+/"));
        if (rows.isNoObj())
            return 0L;
        return rows.stream().map(Obj::asRel).map(Rel::second).filter(Obj::isRec).count();
    }

    /** The call ids a sweep reported under one failure mode. */
    private static List<String> ids(final Obj findings, final String mode) {
        final Obj list = findings.asRec().at(studio.phaseshift.metatron.isa.m.type.impl.MUri.uri(mode));
        return list.isNoObj() ? List.of()
                : list.asLst().elements().map(Str.Helper::cleanString).toList();
    }

    /** An ai message asking for one tool call. */
    private static Rec aiRequest(final String callId) {
        return MessageBuilder.build(AI_MESSAGE_TID)
                .text("running a probe")
                .put(TOOL_REQUESTS, lst(rec(mutableMap(
                        uri(NAME), uri("probe_tool"),
                        uri(TEXT), str("probe_tool()"),
                        uri(CONTENTS), str(callId)))))
                .time()
                .create();
    }

    /** The result answering it. */
    private static Rec toolResult(final String callId) {
        return MessageBuilder.buildToolResultMessage()
                .put(NAME, uri("probe_tool"))
                .text("the probe answer")
                .contents(callId)
                .time()
                .create();
    }
}
