package studio.phaseshift.metatron.isa.llm.type;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.llm.type.feature.CostFeature;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_COST_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.type.Agent.feat;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_USD_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class CostFeatureTest extends FeatureTest {

    @BeforeAll
    public static void setupMemSpace() {
        memSpace.of(f("/usr/#"), f("/sys/space/cost_feature/test")).addQ(QCollection.incrQ());
    }

    @Test
    public void testCostFeatureStructure() {
        assertFeatureStructure(new CostFeature(new LinkedHashMap<>(), feat("cost_feature"), null) {
        });
    }

    @Test
    public void testOnAgentCtorSetsCalculator() {
        final CostFeature cf = new CostFeature(mutableMap(
                uri("root"), uri("/usr/test_cost"),
                uri("rate"), rec(
                        uri(IN), real(0.435, MATH_USD_TID, null),
                        uri(OUT), real(0.870, MATH_USD_TID, null)
                )
        ), feat("cost_feature"), null);

        final Agent agent = agentDummy();
        cf.onAgentCtor(agent);

        assertNotNull(agent.costCalculator().get(), "calculator should be set");
        assertEquals(MATH_USD_TID, agent.costCalculator().get().getCurrencyTID(),
                "currency TID from rate/in");
    }

    @Test
    public void testPersistCostOutputHasCurrencyTid() {
        final CostFeature cf = new CostFeature(mutableMap(
                uri("root"), uri("/usr/test_cost"),
                uri("rate"), rec(
                        uri(IN), real(0.435, MATH_USD_TID, null),
                        uri(OUT), real(0.870, MATH_USD_TID, null)
                )
        ), LLM_COST_FEATURE_TID, null);

        final Agent agent = agentDummy();
        cf.onAgentCtor(agent);
        agent.costCalculator().get().setCost(0.00435, 0.00870);
        cf.persistCost();

        assertFalse(Router.readFromSpace(cf.at(ROOT).uriValue().extend("+")).isNoObj(), "cost should be on blackboard");
        assertEquals(0.01305, Router.readFromSpace(cf.at(f(ROOT)).uriValue().extend("+").extend(TOTAL)).realValue(), 1e-10,
                "total = in + out");
    }

    @Test
    public void testMissingRateDefaultsGracefully() {
        final CostFeature cf = new CostFeature(new LinkedHashMap<>(), feat("cost_feature"), null) {
        };

        final Agent agent = agentDummy();
        assertDoesNotThrow(() -> cf.onAgentCtor(agent),
                "missing rate config should not throw");
    }
}
