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
import studio.phaseshift.metatron.isa.llm.type.feature.concept.MessageIndexer;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.TextUtil;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.DEBUG;

/**
 * Concept provider that extracts concepts by TF-IDF over an in-memory Lucene index of the
 * message corpus, folded with any inline {@code <<concept:...>>} tags the agent wrote.
 */
public final class LuceneConceptFeature extends AbstractConceptFeature {
    public static final fURI FEATURE_TID = studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_LUCENE_CONCEPT_FEATURE_TID;

    private final MessageIndexer indexer;

    public LuceneConceptFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.indexer = new MessageIndexer(TextUtil.getStopWords(GLOBAL_STOP_WORD_SET));
    }

    @Override
    protected Set<String> extract(final Agent agent, final String text, final boolean blocking) {
        if (text != null && !text.isBlank())
            this.indexer.indexText(text);
        final List<MessageIndexer.Concept> topConcepts =
                this.indexer.getImportantConcepts(text, 10);
        final Set<String> result = new LinkedHashSet<>();
        for (final MessageIndexer.Concept c : topConcepts) {
            final String term = c.term().toLowerCase();
            if (term.length() < 3 || term.contains(":") || term.contains("/"))
                continue;
            final String normalized = CommonUtil.normalize(term);
            if (!normalized.isEmpty())
                result.add(normalized);
        }
        LOG.status(DEBUG, "lucene extracted %d concepts from %d docs: %s",
                result.size(), this.indexer.documentCount(), result);
        result.addAll(extractTags(text));
        return result;
    }

    @Override
    protected String systemMessage() {
        return CONCEPT_EXTRACTOR_LUCENE_SYSTEM_MESSAGE;
    }

    private static final String CONCEPT_EXTRACTOR_LUCENE_SYSTEM_MESSAGE =
            """
            As you respond, your messages are indexed and analyzed using statistical (TF-IDF)
            analysis to automatically identify key concepts.  These concepts are organized into
            a co-location graph that connects related ideas across the conversation.
            
            When relevant historic memories are identified, they will be surfaced via the mtron
            eval tool so you can review them before continuing.
            
            You do not need to tag concepts manually — the extraction happens automatically.
            Respond naturally and the concept graph will build itself. However, if you want to emphasize
            that a particular concept should be extracted (and not leave it to chance), then tag
            the concept in your response as such:
            
            "Increasing the size of the <<concept:context window>> is one way to increase an agent's
             <<concept:intelligence>>. However, another way is to provide better <<concept:indexing>> and
             <<concept:searching>> capabilities for existing <<concept:memory systems>>."
            
            Finally, your thoughts can be indexed in the concept graph only through manual tagging on your part.
            No automatic extraction techniques are used when you think.
            """;

}
