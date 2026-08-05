package studio.phaseshift.metatron.isa.llm.type;

import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.llm.type.feature.AbstractFeature;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_AGENT_TID;
import static studio.phaseshift.metatron.isa.llm.type.Agent.res;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Base class for feature tests. Provides lifecycle simulation helpers
 * plus structural assertions every feature must satisfy.
 * <p>
 * Subclasses call {@link #assertFeatureStructure(AbstractFeature)} to validate
 * that the feature: is a Rec, has a non-default TID, has a non-null JVM,
 * and round-trips through the agent's feature list.
 */
public abstract class FeatureTest extends AbstractMetatronTest {

    // ── Lifecycle helpers ──────────────────────────────────────────

    /**
     * Create an Agent with the given features in its feature list.
     */
    protected static Agent agentWithFeatures(final AbstractFeature... features) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(NAME), uri("test-agent"));
        final List<Obj> featureObjs = new ArrayList<>();
        for (final AbstractFeature f : features) featureObjs.add(f);
        map.put(uri(FEATURE), lst(featureObjs));
        return Agent.agent(rec(map, LLM_AGENT_TID, null));
    }

    /**
     * Create an Agent with the given features in its feature list.
     */
    protected static Agent agentDummy() {
        return agentWithFeatures();
    }

    /**
     * Dispatch a hook by JVM key to a feature with args.
     */
    protected static void dispatchHook(final Agent agent, final Obj feature,
                                       final String hookKey, final Obj... args) {
        ((Inst) ((Poly) feature).at(uri(hookKey))).args(lst(args)).apply(agent);
    }

    /**
     * Simulate onBeforeChat → onToolExecuted → onCompleteResponse.
     */
    protected static void simulateLifecycle(final Agent agent,
                                            final String toolName, final String toolResult,
                                            final String chatResponse) {
        final List<Obj> features = agent.features().lstValue();

        for (final Obj f : features)
            ((Poly) f).at(uri(ON_BEFORE_CHAT)).apply(agent);

        if (toolName != null) {
            final Rec toolRec = rec(uri(NAME), str(toolName), uri(RESULT), str(toolResult));
            for (final Obj f : features)
                dispatchHook(agent, f, ON_TOOL_EXECUTED, toolRec);
        }

        agent.at(res(CHAT), str(chatResponse), MUTABLE);
        agent.at(res(TIME), jnt(42), MUTABLE);

        for (final Obj f : features)
            dispatchHook(agent, f, ON_COMPLETE_RESPONSE, str(chatResponse));
    }

    // ── Structural contract ────────────────────────────────────────

    /**
     * Assert that a feature satisfies the structural contract:
     * is a Rec, has a non-default TID, has a non-null JVM,
     * round-trips through the agent's feature list.
     */
    protected static void assertFeatureStructure(final AbstractFeature feature) {
        assertNotNull(feature, "feature must not be null");
        assertTrue(feature.isRec(), "feature must be a rec");
        assertFalse(feature.isNoObj(), "feature must not be noobj");
        assertNotNull(feature.tid(), "feature must have a tid");
        assertNotEquals(REC_TID, feature.tid(), "feature tid must not be the default /m/rec");
        assertNotNull(feature.jvm(), "feature JVM must not be null");

        // Round-trip: place in agent → retrieve via feature query
        final Agent a = agentWithFeatures(feature);
        final Obj found = a.features().lstValue().stream()
                .filter(e -> e.tid().equals(feature.tid()))
                .findFirst().orElse(null);
        assertNotNull(found, "feature must be retrievable from agent's feature list");
        assertEquals(feature.tid(), found.tid(), "retrieved feature must have same TID");
    }
}
