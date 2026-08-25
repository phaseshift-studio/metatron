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
import studio.phaseshift.metatron.isa.llm.type.ChatResult;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_LOOP_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.type.Agent.feat;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class LoopFeatureTest extends AbstractFeatureTest {

    @Override
    protected LoopFeature feature() {
        return new LoopFeature(mutableMap(
                uri("root"), uri("/usr/test/loop"),
                uri("max_loop"), jnt(5)),
                LLM_LOOP_FEATURE_TID, null);
    }

    @Test
    public void testLifecycleAttachesLoopResultsRef() {
        final ChatResult result = runLifecycle(feature());
        assertFalse(result.at(uri("loop_results")).isNoObj(), "chat_result should carry a loop_results ref");
    }

    @Test
    public void testIterationsPersisted() {
        runLifecycle(feature());
        final Obj rows = Router.readFromSpace(f("/usr/test/loop/+"));
        assertFalse(rows.isNoObj(), "loop iterations should be persisted");
    }

    @Test
    public void testDelayConfig() {
        final LoopFeature noDelay = new LoopFeature(new LinkedHashMap<>(), feat("loop"), null) {
        };
        assertTrue(noDelay.at(uri("delay")).isNoObj(), "no delay key → default behavior");
        final LoopFeature withDelay = new LoopFeature(mutableMap(uri("delay"), real(3.5d)), feat("loop"), null) {
        };
        assertFalse(withDelay.at(uri("delay")).isNoObj(), "delay should be present in JVM");
    }
}
