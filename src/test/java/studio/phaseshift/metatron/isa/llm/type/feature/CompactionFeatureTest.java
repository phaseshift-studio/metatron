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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.incrQ;
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
 * Tests for {@code llmInstSet.writeCompaction} — the LLM-free write-path of
 * {@code compactSession}.  Verifies two guarantees: the sentinel carries the
 * resume summary plus its token-compression stats, and the re-appended
 * recent-tail never orphans a {@code tool_result} from its {@code ai} message.
 */
public class CompactionFeatureTest extends AbstractMetatronTest {

    @BeforeAll
    public static void mountCompactionSpace() {
        memSpace.of(f("/usr/test/#"), f("/sys/space/usr/test")).addQ(incrQ());
    }

    // ── Message factories ────────────────────────────────────────────

    private static Rec U() {
        return rec(mutableMap(uri(TEXT), str("user says hello")), USER_MESSAGE_TID, null);
    }

    private static Rec A() {
        return rec(mutableMap(uri(TEXT), str("ai responds")), AI_MESSAGE_TID, null);
    }

    private static Rec A(final String... toolCallIds) {
        final Map<Obj, Obj> map = mutableMap();
        map.put(uri(TEXT), str("ai calls tools"));
        final List<Obj> toolReqs = new ArrayList<>();
        for (final String id : toolCallIds)
            toolReqs.add(rec(mutableMap(uri(CONTENTS), str(id)), TOOL_REQUEST_MESSAGE_TID, null));
        map.put(uri(TOOL_REQUESTS), lst(toolReqs));
        return rec(map, AI_MESSAGE_TID, null);
    }

    private static Rec T(final String toolCallId) {
        return rec(mutableMap(
                uri(CONTENTS), str(toolCallId),
                uri(TEXT), str("result for " + toolCallId)
        ), TOOL_RESULT_MESSAGE_TID, null);
    }

    private static Rec S() {
        return rec(mutableMap(uri(TEXT), str("system prompt")), SYSTEM_MESSAGE_TID, null);
    }

    private static Rec THINK() {
        return rec(mutableMap(uri(TEXT), str("hmm...")), THINKING_MESSAGE_TID, null);
    }

    private static List<Rel> msgs(final Rec... messages) {
        return new ArrayList<>(Stream.of(messages).map(m -> rel(uri("temp"), m)).toList());
    }

    // ── Sentinel text + stats ────────────────────────────────────────

    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "a concise resume summary % the long conversation digest with many words to compress",
            "resumed the bug fix % user asked to fix the null pointer in the compaction write path",
            "short % a very very long digest spanning many many tokens to summarize away"
    })
    void writeCompactionStampsSummaryAndStats(final String summary, final String digest) {
        final fURI agentHome = f("/usr/test/compact/sentinel");
        final fURI sessionVID = agentHome.extend("session").extend("1");
        final Rec sentinel = writeCompaction(agentHome, sessionVID, msgs(U(), A()), digest, summary);

        assertEquals(COMPACTION_MESSAGE_TID, sentinel.tid(), "sentinel must be a compaction message");
        assertEquals(sessionVID, sentinel.at(uri(SESSION)).uriValue(), "sentinel must carry its session");
        assertEquals(summary, Str.Helper.cleanString(sentinel.at(uri(TEXT))), "sentinel text is the resume summary");

        final MessageFeature.DefaultTokenCountEstimator estimator = MessageFeature.DefaultTokenCountEstimator.singleton();
        final int expectedIn = estimator.estimateTokenCountInText(digest);
        final int expectedOut = estimator.estimateTokenCountInText(summary);
        assertEquals(expectedIn, sentinel.at(uri(IN)).intValue().intValue(), "in is the digest token estimate");
        assertEquals(expectedOut, sentinel.at(uri(OUT)).intValue().intValue(), "out is the summary token estimate");
        assertEquals(1.0 - (double) expectedOut / (double) expectedIn, sentinel.at(uri(COMPRESSION)).realValue(), 1e-9,
                "compression is 1 - out/in");
    }

    // ── Pair-safe recent-tail ────────────────────────────────────────

    @Test
    void writeCompactionTailIsPairSafe() {
        final fURI agentHome = f("/usr/test/compact/tail");
        final fURI sessionVID = agentHome.extend("session").extend("1");
        // [U1, A{c1}, T{c1}, A, U2, A{c2}, T{c2}, A, U3, A{c3}, T{c3}] — 11 msgs.
        // SPILL_OVER=5 → rawSkip=6 → messages[6]=T{c2} is orphaned (A{c2} at 5),
        // so the tail must pull in U2 (and A{c2}) and start with a user message.
        final List<Rel> messages = msgs(
                U(), A("c1"), T("c1"), A(),
                U(), A("c2"), T("c2"), A(),
                U(), A("c3"), T("c3"));
        writeCompaction(agentHome, sessionVID, messages, "digest", "summary");

        final List<Rel> ledger = Router.readFromSpace(agentHome.extend(MESSAGE).extend("+/")).stream()
                .map(Obj::asRel)
                .sorted(Comparator.comparing(p -> Integer.parseInt(p.first().uriValue().name())))
                .toList();
        // sentinel + 7-message tail (raw 5 grew to 7 to keep the pair intact)
        assertEquals(8, ledger.size(), "sentinel plus the pair-safe tail");
        assertEquals(COMPACTION_MESSAGE_TID, ledger.get(0).second().tid(), "sentinel is written first");
        assertEquals(USER_MESSAGE_TID, ledger.get(1).second().tid(),
                "tail starts with a user message, not the orphaned tool_result T{c2}");
    }

    @Test
    void writeCompactionTailExcludesMetatronWorldRecords() {
        final fURI agentHome = f("/usr/test/compact/metatron");
        final fURI sessionVID = agentHome.extend("session").extend("1");
        // a system message (re-written by SystemFeature each turn) and a
        // thinking trace must not leak into the conversational tail — a system
        // message after a user message breaks the model's "system at the
        // beginning" invariant
        final List<Rel> messages = msgs(U(), S(), THINK(), A("c1"), T("c1"));
        writeCompaction(agentHome, sessionVID, messages, "digest", "summary");
        final List<Rel> ledger = Router.readFromSpace(agentHome.extend(MESSAGE).extend("+/")).stream()
                .map(Obj::asRel)
                .sorted(Comparator.comparing(p -> Integer.parseInt(p.first().uriValue().name())))
                .toList();
        assertEquals(4, ledger.size(), "sentinel + conversational tail only — system/thinking excluded");
        assertEquals(COMPACTION_MESSAGE_TID, ledger.get(0).second().tid(), "sentinel first");
        assertEquals(USER_MESSAGE_TID, ledger.get(1).second().tid(), "tail opens on the user message");
        assertEquals(AI_MESSAGE_TID, ledger.get(2).second().tid(), "then the ai message with its tool call");
        assertEquals(TOOL_RESULT_MESSAGE_TID, ledger.get(3).second().tid(), "then its tool result");
    }
}
