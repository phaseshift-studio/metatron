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
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatResult;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_COST_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.type.Agent.feat;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_USD_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class CostFeatureTest extends AbstractFeatureTest {

    @Override
    protected CostFeature feature() {
        return new CostFeature(mutableMap(
                uri("root"), uri("/usr/test/cost"),
                uri("rate"), rec(
                        uri(IN), real(0.435, MATH_USD_TID, null),
                        uri(OUT), real(0.870, MATH_USD_TID, null))),
                LLM_COST_FEATURE_TID, null);
    }

    @Test
    public void testOnAgentCtorSetsCalculator() {
        final CostFeature cf = feature();
        final Agent agent = agentWith(cf);
        cf.onAgentCtor(agent);
        assertNotNull(agent.costCalculator().get(), "calculator should be set");
        assertEquals(MATH_USD_TID, agent.costCalculator().get().getCurrencyTID(),
                "currency TID from rate/in");
    }

    @Test
    public void testPersistCostWritesRow() {
        final CostFeature cf = feature();
        final Agent agent = agentWith(cf);
        cf.onAgentCtor(agent);
        agent.costCalculator().get().setCost(0.00435, 0.00870);
        cf.persistCost();
        final Obj rows = Router.readFromSpace(cf.at(uri("root")).uriValue().extend("+"));
        assertFalse(rows.isNoObj(), "cost row should be persisted");
        assertEquals(0.01305,
                rows.stream().reduce((a, b) -> b).orElse(noobj()).asRec().at(uri(TOTAL)).realValue(),
                1e-10, "total = in + out");
    }

    @Test
    public void testLifecycleAttachesCostRef() {
        final ChatResult result = runLifecycle(feature());
        assertFalse(result.at(uri("cost")).isNoObj(), "chat_result should carry a cost ref");
    }

    @Test
    public void testMissingRateDefaultsGracefully() {
        final CostFeature cf = new CostFeature(new LinkedHashMap<>(), feat("cost_feature"), null) {
        };
        assertDoesNotThrow(() -> cf.onAgentCtor(agentDummy()),
                "missing rate config should not throw");
    }
}
