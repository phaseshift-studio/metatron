/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.isa.llm.space;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Minimal RAG ContentRetriever that searches Space for relevant content.
 *
 * <h2>How RAG Works (the 30-second version)</h2>
 * <pre>
 * User Question: "How do I use map?"
 *         │
 *         ▼
 * ┌─────────────────────────────────┐
 * │  ContentRetriever.retrieve()   │◄── This class! Searches Space
 * └─────────────────────────────────┘
 *         │
 *         ▼
 * ┌─────────────────────────────────┐
 * │  List&lt;Content&gt; = relevant docs │
 * └─────────────────────────────────┘
 *         │
 *         ▼
 * ┌─────────────────────────────────────────────────┐
 * │  Augmented Prompt to LLM:                       │
 * │  "Context: [docs about map]                     │
 * │   Question: How do I use map?"                  │
 * └─────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Configuration via mtron</h2>
 * <pre>
 * ollama:qwen3:32b[
 *   rag => [pattern => &lt;/sys/docs/#&gt;, max => 5]
 * ].chat("How do I use map?")
 * </pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SpaceContentRetriever implements ContentRetriever {
    private static final GraphittyLogger LOG = Graphitty.log(SpaceContentRetriever.class);

    private final fURI pattern;
    private final int maxResults;

    /**
     * Create a retriever that searches the given Space pattern.
     *
     * @param pattern    fURI pattern to search (e.g., /sys/docs/#)
     * @param maxResults maximum number of results to return
     */
    public SpaceContentRetriever(final fURI pattern, final int maxResults) {
        this.pattern = pattern;
        this.maxResults = maxResults;
        LOG.info("SpaceContentRetriever initialized: pattern=%s, max=%d", pattern, maxResults);
    }

    /**
     * Search Space for content relevant to the query.
     *
     * <p>Current implementation: simple keyword matching.
     * Each word in the query is checked against each object's string representation.
     * Objects are scored by how many query words they contain.
     *
     * <p>Future: could add embedding-based semantic search.
     */
    @Override
    public List<Content> retrieve(final Query query) {
        LOG.debug("RAG retrieve called: query='%s', pattern=%s", query.text(), this.pattern);

        // 1. Read all objects from Space matching the pattern
        final Obj spaceResult = Router.global().read(this.pattern);
        if (spaceResult.isNoObj()) {
            LOG.debug("RAG: no objects found at pattern %s", this.pattern);
            return Collections.emptyList();
        }

        // 2. Extract query keywords (lowercase, split on whitespace)
        final Set<String> queryWords = Arrays.stream(query.text().toLowerCase().split("\\s+"))
                .filter(w -> w.length() > 2)  // ignore tiny words
                .collect(Collectors.toSet());

        if (queryWords.isEmpty()) {
            LOG.debug("RAG: no significant query words");
            return Collections.emptyList();
        }

        // 3. Score each object by keyword matches
        final List<ScoredContent> scored = new ArrayList<>();
        spaceResult.stream().forEach(obj -> {
            final String text = obj.toString().toLowerCase();
            final long matchCount = queryWords.stream().filter(text::contains).count();
            if (matchCount > 0) {
                scored.add(new ScoredContent(obj, matchCount));
            }
        });

        // 4. Sort by score (descending) and take top N
        scored.sort((a, b) -> Long.compare(b.score, a.score));
        final List<Content> results = scored.stream()
                .limit(this.maxResults)
                .map(sc -> Content.from(formatForContext(sc.obj)))
                .collect(Collectors.toList());

        LOG.info("RAG: retrieved %d results for query '%s'", results.size(),
                query.text().length() > 50 ? query.text().substring(0, 50) + "..." : query.text());

        return results;
    }

    /**
     * Format an Obj for inclusion in the LLM context.
     * Includes the object's fURI (if available) so the LLM knows where it came from.
     */
    private String formatForContext(final Obj obj) {
        final StringBuilder sb = new StringBuilder();
        if (obj.vid() != null) {
            sb.append("[").append(obj.vid()).append("]\n");
        }
        sb.append(obj.toString());
        return sb.toString();
    }

    /**
     * Simple holder for obj + score during ranking
     */
    private record ScoredContent(Obj obj, long score) {
    }
}
