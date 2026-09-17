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

package studio.phaseshift.metatron.isa.llm.type;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.llm.type.feature.AbstractFeatureTest;
import studio.phaseshift.metatron.isa.llm.type.feature.Feature;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@link RecordingFeature} — the framework's "which hooks fired" fixture.  One {@code @CsvSource}
 * row per stage verifies that dispatching a hook records its phase; the inherited free tests run the
 * recording feature through the whole structural/lifecycle gauntlet, and
 * {@code AbstractAgentTest.hooksDispatchInDeclarationOrder} uses it to pin the dispatch order.
 */
public class RecordingFeatureTest extends AbstractFeatureTest {

    @Override
    protected RecordingFeature feature() {
        return RecordingFeature.record("probe");
    }

    @ParameterizedTest
    @CsvSource({
            "on_agent_ctor, onAgentCtor",
            "on_before_chat, onBeforeChat",
            "on_partial_response, onPartialResponse",
            "on_partial_thinking, onPartialThinking",
            "on_partial_tool_call, onPartialToolCall",
            "on_tool_executed, onToolExecuted",
            "on_tool_result, onToolResult",
            "on_complete_response, onCompleteResponse",
            "on_error, onError",
    })
    public void hookRecordsItsPhase(final Feature.Stage stage, final String phase) {
        final RecordingFeature f = this.feature();
        final Agent a = agentWith(f);
        dispatch(stage, f, a);
        assertEquals(phase, f.phases().getLast(), "dispatching %s records %s".formatted(stage, phase));
    }
}
