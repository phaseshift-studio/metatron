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

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatResult;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_DAY_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_MILLIS_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class SummarizeFeatureTest extends AbstractFeatureTest {

    @Override
    protected AbstractFeature feature() {
        return new SummarizeFeature(mutableMap(uri(ROOT), uri("/usr/test/sum")), LLM_SUMMARIZE_FEATURE_TID, null);
    }

    private static SummarizeFeature summarize() {
        return new SummarizeFeature(mutableMap(uri(ROOT), uri("/usr/test/sum")), LLM_SUMMARIZE_FEATURE_TID, null);
    }

    private static ChatResult chatResultWithSummarizeBlock() {
        return ChatResult.chatResult()
                .put(CHAT, str("I've queued a summarization over the last two days."))
                .put(USER, str("test prompt"))
                .put(TIME, real(42.0, MATH_MILLIS_TID, null))
                .put(BLOCK, rec(uri("summarize"), rec(
                        uri(SCOPE), real(2.0, MATH_DAY_TID, null),
                        uri(KIND), lst(uri("decision")),
                        uri(CONCEPT), lst(str("AgentExtractor")))));
    }

    @Test
    public void testNoBlockLeavesTaskNull() {
        final SummarizeFeature summarize = summarize();
        summarize.onCompleteResponse(agentWith(summarize), chatResultOf("final response", "test prompt"));
        assertNull(summarize.summaryTask.get(), "a chat without a summarize block must not queue a task");
    }

    @Test
    public void testBlockDispatchQueuesTask() {
        final SummarizeFeature summarize = summarize();
        final MessageFeature session = new MessageFeature(mutableMap(uri(SESSION), uri("/usr/test/session/1")), LLM_MESSAGE_FEATURE_TID, null);
        final Agent agent = agentWith(summarize, session);
        summarize.onCompleteResponse(agent, chatResultWithSummarizeBlock());
        assertNotNull(summarize.summaryTask.get(), "a summarize block should queue a background task");
    }

    @Test
    public void testBlockConfigOverridesFeatureDefaults() {
        final SummarizeFeature summarize = new SummarizeFeature(mutableMap(
                uri(ROOT), uri("/usr/test/sum"),
                uri(SCOPE), real(1.0, MATH_DAY_TID, null)),
                LLM_SUMMARIZE_FEATURE_TID, null);
        final Agent agent = agentWith(summarize);
        final Rec block = rec(uri(SCOPE), real(2.0, MATH_DAY_TID, null),
                uri(KIND), lst(uri("decision")),
                uri(CONCEPT), lst(str("AgentExtractor")));
        final Rec config = summarize.resolveConfig(agent, block);
        assertEquals("day", config.at(uri(SCOPE)).tid().name(), "block scope should override the feature default");
        assertEquals("decision", Str.Helper.cleanString(config.at(uri(KIND)).asLst().elements().findFirst().get()));
        assertEquals("AgentExtractor", Str.Helper.cleanString(config.at(uri(CONCEPT)).asLst().elements().findFirst().get()));
        assertEquals("/usr/test/sum", config.at(uri(TO)).uriValue().toString(), "to should be the feature root");
    }

    @Test
    public void testBriefingFiltersByKindsAndConcepts() {
        final fURI claim1 = f("/usr/test/sum/claim/1");
        Router.writeToSpace(claim1, rec(uri(TEXT), str("the decision claim"), uri(KIND), uri("decision"),
                uri(SOURCE), lst(auto_from_(uri("/usr/test/message/28")).tryToInst())).tid(LLM_CLAIM_TID).selfVID(claim1));
        final fURI claim2 = f("/usr/test/sum/claim/2");
        Router.writeToSpace(claim2, rec(uri(TEXT), str("the problem claim"), uri(KIND), uri("problem"),
                uri(SOURCE), lst(auto_from_(uri("/usr/test/message/31")).tryToInst())).tid(LLM_CLAIM_TID).selfVID(claim2));
        final fURI looseEnd = f("/usr/test/sum/loose_end/1");
        Router.writeToSpace(looseEnd, rec(uri(TITLE), str("open thread"), uri(STATUS), uri("open"), uri(DESC), str("...")).tid(LLM_LOOSE_END_TID).selfVID(looseEnd));
        final fURI concept = f("/usr/test/concept/AgentExtractor");
        Router.writeToSpace(concept, rec(uri(MESSAGE), lst(auto_from_(uri("/usr/test/message/28")).tryToInst())).selfVID(concept));

        final SummarizeFeature summarize = summarize();
        // agent carries a plain concept_feature rec (root => /usr/test/concept) —
        // a real ConceptFeature needs the stopword resource not on the test classpath
        final Map<Obj, Obj> agentMap = new LinkedHashMap<>();
        agentMap.put(uri(NAME), str("test-agent"));
        agentMap.put(uri(ROOT), uri(TEST_AGENT_ROOT.toString()));
        agentMap.put(uri(FEATURE), lst(summarize, rec(uri(ROOT), uri("/usr/test/concept")).tid(LLM_CONCEPT_FEATURE_TID),
                new SkillFeature(mutableMap(), LLM_SKILL_FEATURE_TID, null),
                new ToolFeature(mutableMap(), LLM_TOOL_FEATURE_TID, null)));
        final Agent agent = Agent.agent(rec(agentMap, LLM_AGENT_TID, null));
        final Rec config = rec(uri(KIND), lst(uri("decision")), uri(CONCEPT), lst(str("AgentExtractor")));

        final Obj briefing = summarize.buildBriefing(agent, config);
        assertFalse(briefing.isNoObj(), "briefing should be produced");
        // only claim/1 survives: kind=decision AND its source message (28) is in the AgentExtractor concept
        final Obj claimLst = briefing.asRec().at(uri("claim"));
        assertEquals(1, claimLst.asLst().elements().toList().size(), "only the decision claim survives the kind+concept filter");
        final Rec claimEntry = claimLst.asLst().elements().findFirst().get().asRec();
        assertEquals("the decision claim", Str.Helper.cleanString(claimEntry.at(uri(TEXT))));
        assertEquals("/usr/test/sum/claim/1", claimEntry.atDirect(uri(LOCATION)).asInst().arg(0).uriValue().toString());
        // loose ends are always surfaced
        final Obj leLst = briefing.asRec().at(uri("loose_end"));
        assertEquals(1, leLst.asLst().elements().toList().size(), "loose ends should be included");
    }

    @Test
    public void testBriefingAllKindsWhenNoneRequested() {
        final fURI claim1 = f("/usr/test/sum/claim/1");
        Router.writeToSpace(claim1, rec(uri(TEXT), str("the decision claim"), uri(KIND), uri("decision"),
                uri(SOURCE), lst(auto_from_(uri("/usr/test/message/28")).tryToInst())).tid(LLM_CLAIM_TID).selfVID(claim1));
        final fURI claim2 = f("/usr/test/sum/claim/2");
        Router.writeToSpace(claim2, rec(uri(TEXT), str("the problem claim"), uri(KIND), uri("problem"),
                uri(SOURCE), lst(auto_from_(uri("/usr/test/message/31")).tryToInst())).tid(LLM_CLAIM_TID).selfVID(claim2));

        final SummarizeFeature summarize = summarize();
        final Agent agent = agentWith(summarize);
        final Obj briefing = summarize.buildBriefing(agent, rec()); // no kind, no concept
        assertFalse(briefing.isNoObj());
        final Obj claimLst = briefing.asRec().at(uri("claim"));
        assertEquals(2, claimLst.asLst().elements().toList().size(),
                "absent kind means all kinds");
    }
}
