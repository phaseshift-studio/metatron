package studio.phaseshift.metatron.isa.llm.type;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.feature.*;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.FEATURE;
import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * {@link Feature#requires()} — features declare the full feature tids they
 * need, and the agent (the integrator of features) validates the composition
 * at construction: a missing dependency is a composition error with the
 * canonical message, not a "debilitated" feature discovered mid-chat.
 */
public class FeatureRequiresTest extends AbstractMetatronTest {

    private static final fURI DEPS_TID = f("/m/test/deps_feature");

    /**
     * A feature with a hard requirement on the system-message channel.
     */
    static class DepsFeature extends AbstractFeature {
        DepsFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
            super(jvm, tid, vid);
        }

        @Override
        public Set<fURI> requires() {
            return Set.of(LLM_SYSTEM_FEATURE_TID);
        }
    }

    @ParameterizedTest(name = "system attached: {0}")
    @CsvSource(value = {
            "true",
            "false"
    }, delimiter = '%')
    public void testRequiredFeatureValidatedAtConstruction(final boolean systemAttached) {
        final DepsFeature deps = new DepsFeature(mutableMap(), DEPS_TID, null);
        final SystemFeature system = new SystemFeature(mutableMap(), LLM_SYSTEM_FEATURE_TID, null);
        final Obj features = systemAttached ? lst(deps, system) : lst(deps);
        final Obj config = rec(mutableMap(uri(NAME), str("requires-agent"),
                uri(FEATURE), features), LLM_AGENT_TID, null);

        if (systemAttached) {
            final Agent agent = Agent.agent(config.as());
            assertTrue(agent.hasFeature(DEPS_TID), "deps feature should be attached");
            assertTrue(agent.hasFeature(LLM_SYSTEM_FEATURE_TID), "system feature should be attached");
            assertFalse(agent.feature(DEPS_TID).isNoObj(), "feature(full tid) should resolve");
        } else {
            final MTronException failure = assertThrows(MTronException.class, () -> Agent.agent(config.as()));
            assertTrue(failure.getMessage().contains("requires"),
                    "canonical message expected: " + failure.getMessage());
        }
    }

    @Test
    public void testFeatureAccessIsTidExact() {
        // the old substring matching meant a token like "ses" could have hit
        // the wrong feature; only the full tid resolves now
        final Agent agent = Agent.agent(rec(mutableMap(
                        uri(NAME), str("exactness-agent"),
                        uri(FEATURE), lst(new ChatFeature(mutableMap(), LLM_CHAT_FEATURE_TID, null),
                                new SkillFeature(mutableMap(), LLM_SKILL_FEATURE_TID, null),
                                new ToolFeature(mutableMap(), LLM_TOOL_FEATURE_TID, null))),
                LLM_AGENT_TID, null));
        assertTrue(agent.hasFeature(LLM_CHAT_FEATURE_TID), "the attached feature resolves");
        assertFalse(agent.hasFeature(LLM_MESSAGE_FEATURE_TID), "an unattached sibling must not resolve");
        assertTrue(agent.feature(LLM_MESSAGE_FEATURE_TID).isNoObj(), "feature(unattached tid) is noobj");
        assertEquals(LLM_CHAT_FEATURE_TID, agent.feature(LLM_CHAT_FEATURE_TID).tid(), "the attached feature's tid is exact");
    }

    @Test
    public void testAgentWithoutFeaturesConstructs() {
        final Agent agent = Agent.agent(rec(mutableMap(uri(NAME), str("bare-agent")), LLM_AGENT_TID, null));
        assertTrue(agent.features().isEmpty(), "a bare agent has no features");
        assertFalse(agent.hasFeature(LLM_SYSTEM_FEATURE_TID), "and nothing resolves");
    }
}
