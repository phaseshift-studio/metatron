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
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Str;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.CONTENT;
import static studio.phaseshift.metatron.Tokens.FEATURE;
import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.Tokens.ROOT;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.incrQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_AGENT_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_EMBED_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SKILL_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_TOOL_FEATURE_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The embed feature's lifecycle wiring — specifically that its skill reaches the
 * skill channel at all.
 *
 * <p>Only {@code onBeforeChat} is exercised: {@code onCompleteResponse} builds a
 * real embedding model and calls it, which is not something a unit test should do.
 */
public class EmbedFeatureTest extends AbstractMetatronTest {

    private static final fURI AGENT_ROOT = f("/usr/test/agent");

    @BeforeAll
    public static void mountEmbedSpace() {
        InstSet.importInstSet(f("/m/llm"));
        InstSet.importInstSet(MATH_ISA_TID, f("math"));
        memSpace.of(f("/usr/test/#"), f("/sys/space/usr/test")).addQ(incrQ());
    }

    /**
     * The regression this guards: without an {@code onBeforeChat}, the type
     * factory never wires an {@code ON_BEFORE_CHAT} hook
     * ({@code createStageLambdas} only wires what a subclass actually overrides),
     * so {@code registerSkill} is never reached and the model is never told that
     * {@code <<mtron:embed>>} exists.
     */
    @Test
    public void testSkillReachesTheSkillChannel() {
        final EmbedFeature embed = new EmbedFeature(mutableMap(
                uri(ROOT), uri(AGENT_ROOT.toString())), LLM_EMBED_FEATURE_TID, null);
        final SkillFeature skills = new SkillFeature(mutableMap(), LLM_SKILL_FEATURE_TID, null);
        // the skill gateway requires the tool gateway
        final ToolFeature tools = new ToolFeature(mutableMap(), LLM_TOOL_FEATURE_TID, null);
        final Agent agent = agentWith(skills, tools, embed);
        embed.onBeforeChat(agent);
        // assert on the marker rather than the count — the tool gateway contributes
        // skills of its own, so counting only tells us the channel was used at all
        final long embedding = skills.skills().elements()
                .filter(skill -> skill.isRec()
                        && Str.Helper.cleanString(skill.asRec().at(uri(CONTENT))).contains("<<mtron:embed>>"))
                .count();
        assertEquals(1L, embedding, "exactly one contributed skill teaches the embed marker");
    }

    // ── helpers ────────────────────────────────────────────────────

    private static Agent agentWith(final Obj... features) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(NAME), str("test-agent"));
        map.put(uri(ROOT), uri(AGENT_ROOT.toString()));
        map.put(uri(FEATURE), lst(features));
        return Agent.agent(rec(map, LLM_AGENT_TID, null));
    }
}
