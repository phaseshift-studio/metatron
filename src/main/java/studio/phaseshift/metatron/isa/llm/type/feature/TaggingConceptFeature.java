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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.Map;
import java.util.Set;

/**
 * Concept provider that reads the {@code <<concept:...>>} tags the agent writes inline.
 * No model, no index — the cheapest, most explicit provider.
 */
public final class TaggingConceptFeature extends AbstractConceptFeature {
    public static final fURI FEATURE_TID = studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_TAGGING_CONCEPT_FEATURE_TID;

    public TaggingConceptFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    protected Set<String> extract(final Agent agent, final String text, final boolean blocking) {
        return extractTags(text);
    }

    @Override
    protected String systemMessage() {
        return CONCEPT_EXTRACTOR_TAG_SYSTEM_MESSAGE;
    }

    private static final String CONCEPT_EXTRACTOR_TAG_SYSTEM_MESSAGE =
            """
            In any of your responses, you can tag important concepts using a <<concept:>>-block:
            For instance, an agent may write:
            
            "Increasing the size of the <<concept:context windows>> is one way to increase an agent's
            <<concept:intelligence>>. However, another way is to provide better <<concept:indexing>> and
            <<concept:searching>> capabilities for existing <<concept:memory systems>>."
            
            Behind the scenes, these tags will form a growing co-location graph that will allow
            for the automatic insertion of relevant historic memories the agent can choose
            to review. For instance, given the above, the next system message may write:
            """;

}
