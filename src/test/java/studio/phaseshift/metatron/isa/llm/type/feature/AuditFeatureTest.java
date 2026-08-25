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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_AUDIT_FEATURE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class AuditFeatureTest extends AbstractFeatureTest {

    @Override
    protected AuditFeature feature() {
        return new AuditFeature(mutableMap(uri("root"), uri("/usr/test/audit")), LLM_AUDIT_FEATURE_TID, null);
    }

    @Test
    public void testLifecycleAttachesAuditRef() {
        final ChatResult result = runLifecycle(feature());
        assertFalse(result.at(uri("audit")).isNoObj(), "chat_result should carry an audit ref");
    }

    @Test
    public void testTrailPersistedWithPhases() {
        runLifecycle(feature());
        final Obj rows = Router.readFromSpace(f("/usr/test/audit/+"));
        assertFalse(rows.isNoObj(), "audit trail should be persisted");
        final Obj trail = rows.stream().reduce((a, b) -> b).orElse(noobj()).asRec().at(uri("trail"));
        assertFalse(trail.isNoObj(), "audit row should carry the trail");
        final String trailStr = trail.toString();
        assertTrue(trailStr.contains("before_chat"), "trail should have before_chat");
        assertTrue(trailStr.contains("complete"), "trail should have complete");
        assertTrue(trailStr.contains("partialResponses"), "trail should carry the StageFeature-derived counters");
    }
}
