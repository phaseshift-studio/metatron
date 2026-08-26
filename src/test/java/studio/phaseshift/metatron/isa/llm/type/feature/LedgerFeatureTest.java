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
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static studio.phaseshift.metatron.Tokens.CONTENT;
import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_LEDGER_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SYSTEM_FEATURE_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class LedgerFeatureTest extends AbstractFeatureTest {

    @Override
    protected LedgerFeature feature() {
        return new LedgerFeature(mutableMap(uri("ledger"), uri("/usr/test/ledger")), LLM_LEDGER_FEATURE_TID, null);
    }

    @Test
    public void testOnBeforeChatCreatesLedgerInSpace() {
        final LedgerFeature ledger = feature();
        // SystemFeature receives the injected system message (features contribute
        // through it via agent.feature(SYSTEM).<SystemFeature>as()).
        final SystemFeature system = new SystemFeature(mutableMap(), LLM_SYSTEM_FEATURE_TID, null);
        final Agent a = agentWith(ledger, system);
        ledger.onBeforeChat(a);
        assertFalse(Router.readFromSpace(f("/usr/test/ledger")).isNoObj(), "ledger should be created in space");
        assertFalse(system.getSystemMessages().isEmpty(), "onBeforeChat should inject a system message");
    }

    @Test
    public void testOnBeforeChatDebilitatedWithoutSystemFeature() {
        // Without a SystemFeature, the ledger feature proceeds debilitated: it still
        // creates the ledger (its own work), logs the missing cross-feature requirement,
        // and does NOT crash.
        final LedgerFeature ledger = feature();
        final Agent a = agentWith(ledger);   // no SystemFeature
        ledger.onBeforeChat(a);
        assertFalse(Router.readFromSpace(f("/usr/test/ledger")).isNoObj(),
                "ledger should still be created in space even without SystemFeature (debilitated)");
    }

    @Test
    public void testLedgerSkillWellFormed() {
        final LedgerFeature ledger = feature();
        final Rec skill = ledger.skill(agentWith(ledger)).asLst().at(0).asRec();
        assertEquals("ledger", skill.at(uri(NAME)).uriValue().name(), "skill name should be 'ledger'");
        assertFalse(skill.at(uri(CONTENT)).strValue().isBlank(), "skill should have content");
    }
}
