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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_CHAT_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.type.Agent.agent;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.id_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread.virtual;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class ConceptFeature extends Feature {

    private static final String CONCEPT = "concept";
    private static final String LINK = "link";
    private static final String MESSAGE = "message";
    private static final Pattern CONCEPT_PATTERN = Pattern.compile("<<concept:([^>]+)>>");

    // ── Config keys ─────────────────────────────────────────────────
    private static final fURI EXTRACTOR = f("extractor");
    private static final fURI EXTRACTOR_TAG = f("tag");
    private static final fURI EXTRACTOR_AGENT = f("agent");
    private static final fURI EXTRACTOR_LUCENE = f("lucene");

    // ── Extractor instances ─────────────────────────────────────────
    private final Extractor extractor;
    private final MessageIndexer indexer; // shared across extractor types for read-side queries

    // ── Templates ───────────────────────────────────────────────────
    private static final String CONCEPT_FEATURE_AGENT_TEMPLATE = """
                                                                 In any of your responses, you can tag important concepts using a <<concept:>>-block:
                                                                 For instance, an agent may write:
                                                                 
                                                                 "Increasing the size of the <<concept:context windows>> is one way to increase an agent's
                                                                 <<concept:intelligence>>. However, another way is to provide better <<concept:indexing>> and
                                                                 <<concept:searching>> capabilities for existing <<concept:memory systems>>."
                                                                 
                                                                 Behind the scenes, these tags will form a growing co-location graph that will allow
                                                                 for the automatic insertion of relevant historic memories the agent can choose
                                                                 to review. For instance, given the above, the next system message may write:
                                                                 """;
    private String CONCEPT_FEATURE_SYSTEM_TEMPLATE = """
                                                     use the mtron eval tool for related historic content:
                                                     
                                                         %s
                                                     
                                                     For related concepts use:
                                                     
                                                        %s
                                                     """;

    // =========================================================================
    // Constructor
    // =========================================================================

    public ConceptFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.indexer = new MessageIndexer();
        this.extractor = resolveExtractor();
    }

    /**
     * Resolve the extractor from configuration.
     * <pre>
     *   extractor => "lucene"  →  LuceneExtractor  (TF-IDF)
     *   extractor => "agent"   →  AgentExtractor   (LLM post-analysis, needs model field)
     *   extractor => "tag"     →  TaggingExtractor (inline {@literal <<concept:>>} tags, default)
     *   (absent) + model set   →  AgentExtractor   (backward compat)
     *   (absent)               →  TaggingExtractor
     * </pre>
     */
    private Extractor resolveExtractor() {
        final fURI mode = this.at(uri(EXTRACTOR)).isNoObj()
                ? (this.has(MODEL) ? EXTRACTOR_AGENT : EXTRACTOR_TAG)
                : this.at(uri(EXTRACTOR)).uriValue();
        if (mode.equals(EXTRACTOR_LUCENE))
            return new LuceneExtractor(this.indexer);
        else if (mode.equals(EXTRACTOR_AGENT))
            return new AgentExtractor();
        else
            return new TaggingExtractor();
    }

    // =========================================================================
    // Skill
    // =========================================================================

    public Obj skill() {
        if (!this.has(MODEL)) {
            return rec(uri(NAME), uri(CONCEPT),
                    uri(DESC), str("In situ concept graph construction w/ spreading activation recommendation"),
                    uri(CONTENT), str(CONCEPT_FEATURE_AGENT_TEMPLATE));
        } else
            return noobj();
    }

    // =========================================================================
    // Shared concept storage
    // =========================================================================

    private fURI getBaseURI() {
        return this.at(BASE).uriValue();
    }

    /**
     * Persist concepts into the space graph with co-location links and
     * message back-references.  Shared by all extractor implementations.
     */
    private List<fURI> insertConcepts(final Agent agent, final Set<String> conceptStrings) {
        final List<fURI> concepts = new ArrayList<>();
        try {
            LOG.debug("concepts to process: %s", conceptStrings);
            for (final String concept : conceptStrings) {
                final fURI conceptURI = this.getBaseURI().extend(concept);
                final Rec conceptRec = Router.readFromSpace(conceptURI).orElse(rec());
                if (!conceptRec.has(CONCEPT)) conceptRec.at(CONCEPT, uri(concept), MUTABLE);
                final Lst conceptLink = conceptRec.at(LINK).orElse(Router.readFromSpace(conceptURI.extend(LINK)).orElse(lst()));
                final Set<Obj> conceptLinkList = new LinkedHashSet<>(conceptLink.jvm());
                final int conceptLinkListSize = conceptLinkList.size();
                conceptLinkList.addAll(conceptStrings.stream()
                        .filter(c -> !c.equals(concept))
                        .map(c -> auto_from_(this.getBaseURI().extend(c)).tryToInst()).toList());
                if (conceptLinkList.size() > conceptLinkListSize) {
                    conceptRec.at(LINK, lst(new ArrayList<>(conceptLinkList)), MUTABLE);
                    if (agent.hasFeature(SESSION)) {
                        final SessionFeature sessionFeature = (SessionFeature) agent.feature(SESSION);
                        LOG.debug("concept feature has located session feature");
                        if (null != sessionFeature.store() && null != sessionFeature.store().getCurrentMessages()) {
                            LOG.debug("concept feature preparing to read from session memory: [size:%d]", sessionFeature.store().getCurrentMessages().size());
                            final Set<fURI> messagesIDs = sessionFeature.store().getCurrentMessages();
                            LOG.debug("concept feature located %s ai messages", messagesIDs);
                            if (!messagesIDs.isEmpty()) {
                                final Lst messages = conceptRec.at(MESSAGE).orElse(lst());
                                final Set<Obj> messageList = new LinkedHashSet<>(messages.lstValue());
                                messageList.addAll(messagesIDs.stream().filter(i -> !Objects.isNull(i)).map(id -> auto_from_(id).tryToInst()).toList());
                                conceptRec.at(MESSAGE, lst(new ArrayList<>(messageList)), MUTABLE);
                            }
                        }
                    }
                }
                Router.writeToSpace(conceptURI, conceptRec);
                concepts.add(conceptURI);
                LOG.debug("extracted concept: %s", conceptURI);
            }
        } catch (final Exception e) {
            LOG.error(e);
        }
        return concepts;
    }

    /**
     * After concepts are stored, optionally inject a system message
     * with mtron eval snippets pointing at relevant historic content.
     */
    private void injectConceptRecommendations(final Agent agent, final List<fURI> concepts) {
        final StringBuilder sb = new StringBuilder();
        new HashSet<>(concepts).stream()
                .map(i -> "\t" + "messages" + "(<" + i + ">)")
                .filter(i -> ObjmtronSerializer.singleNoClip().inputBytes(i + ".take(1).count().gt(0)").apply().boolValue())
                .peek(i -> LOG.info("adding memory recommendation: %s", i))
                .forEach(i -> sb.append(i).append("\n"));
        if (!sb.toString().trim().isEmpty())
            agent.addSystemMessage(CONCEPT_FEATURE_SYSTEM_TEMPLATE.formatted(
                    sb.toString(),
                    "concepts(uri::T)"));
    }

    /**
     * Extract concept strings from text, persist them, and inject recommendations.
     * Called by onCompleteResponse and (if agent-mode) onBeforeChat.
     */
    private void processConcepts(final Agent agent, final String text, final boolean blocking) {
        if (text == null || text.isBlank()) return;
        final Set<String> conceptStrings = this.extractor.extract(agent, text, blocking);
        if (conceptStrings.isEmpty()) return;
        final List<fURI> conceptURIs = this.insertConcepts(agent, conceptStrings);
        this.injectConceptRecommendations(agent, conceptURIs);
    }

    // =========================================================================
    // Streaming lifecycle
    // =========================================================================

    @Override
    public Obj onBeforeChat(final Agent agent) {
        // Agent extractor: prime translator agent on startup, pre-process user message
        if (this.extractor instanceof AgentExtractor ae) {
            ae.init(agent, this); // reads MODEL field, creates translator
            this.processConcepts(agent, agent.userMessage(), true);
        }
        return noobj();
    }

    @Override
    public void onPartialThinking(final Agent agent, final Str text) {
        // No-op: index only on complete response to keep virtual-thread stack shallow
    }

    @Override
    public void onPartialResponse(final Agent agent, final Str text) {
        // No-op: index only on complete response to keep virtual-thread stack shallow
    }

    @Override
    public void onCompleteResponse(final Agent agent, final Str text) {
        if (this.extractor instanceof LuceneExtractor) {
            // Index final chunk, then extract concepts from the full response
            if (text != null && !text.strValue().isBlank())
                this.indexer.indexText(text.strValue());
            this.processConcepts(agent, text != null ? text.strValue() : "", false);
        } else {
            this.processConcepts(agent, text != null ? text.strValue() : "", false);
        }
    }

    // =========================================================================
    // Extractor interface
    // =========================================================================

    /**
     * Pluggable concept extraction strategy.
     */
    public interface Extractor {
        /**
         * Extract concept strings from the given text.
         *
         * @param agent    the current agent (provides session/feature access)
         * @param text     the raw text to analyze
         * @param blocking if true and the implementation is async, block until done
         * @return set of normalized concept strings
         */
        Set<String> extract(Agent agent, String text, boolean blocking);

        /**
         * Human-readable name for logging / debugging.
         */
        default String name() {
            return getClass().getSimpleName();
        }
    }

    // =========================================================================
    // Extractor: Inline {@literal <<concept:>>} tag parsing
    // =========================================================================

    private class TaggingExtractor implements Extractor {
        @Override
        public Set<String> extract(final Agent agent, final String text, final boolean blocking) {
            final Set<String> conceptStrings = new LinkedHashSet<>();
            final Matcher matcher = CONCEPT_PATTERN.matcher(text);
            while (matcher.find()) {
                final String concept = CommonUtil.normalize(matcher.group(1));
                if (!concept.isEmpty() && conceptStrings.add(concept))
                    LOG.info("%s extracted", concept);
            }
            return conceptStrings;
        }
    }

    // =========================================================================
    // Extractor: LLM post-analysis (agent-based)
    // =========================================================================

    private class AgentExtractor implements Extractor {
        private Agent translatorAgent;

        void init(final Agent parent, final ConceptFeature self) {
            if (self.has(MODEL) && this.translatorAgent == null) {
                this.translatorAgent = agent(rec(
                        uri(FEATURE), lst(new ChatFeature(mutableMap(
                                uri(MODEL), self.at(MODEL),
                                uri(RESPONSE), rec(uri(TO), id_().tryToInst())),
                                LLM_CHAT_FEATURE_TID, null))));
                LOG.debug("created translator agent: %s", this.translatorAgent);
            }
        }

        @Override
        public Set<String> extract(final Agent agent, final String text, final boolean blocking) {
            if (this.translatorAgent == null) return Set.of();
            final Set<String> conceptStrings = new LinkedHashSet<>();
            try {
                LOG.info("using agent to extract concepts from text length=%d", text.length());
                final VirtualThread thread = virtual(instLambda((lhs, inst) -> {
                    final Obj result = this.translatorAgent.chat("""
                                                                 Rewrite the following text where key concepts are wrapped in <<concept:a key concept>> tags.
                                                                 For instance, if the text is:
                                                                    "An agent's context window can be indexed like a database."
                                                                 It should be rewritten as:
                                                                    "An agent's <<concept:context window>> can be <<concept:indexed>> like a <<concept:database>>."
                                                                 
                                                                 IMPORTANT:
                                                                   1. Do not wrap common words nor stop words.
                                                                   2. Do not remove spaces (e.g. context window should not be mapped to contextwindow).
                                                                 Finally, it's better to have fewer, highly specific concepts then many general concepts.
                                                                 Thus, if the text has no significant concepts, then simply return the text as is, no changes needed.
                                                                 
                                                                 The text to rewrite is:
                                                                 
                                                                 """ + text);
                    LOG.debug("agent translation: %s", result);
                    final Matcher matcher = CONCEPT_PATTERN.matcher(Str.Helper.cleanString(result));
                    while (matcher.find()) {
                        final String concept = CommonUtil.stripStopwords(CommonUtil.normalize(matcher.group(1)));
                        if (!concept.isEmpty() && conceptStrings.add(concept))
                            LOG.info("%s extracted", concept);
                    }
                    return noobj();
                }));
                if (blocking) thread.applyAsync().get();
                else thread.applyAsync();
            } catch (final Exception e) {
                LOG.error(e);
            }
            return conceptStrings;
        }
    }

    // =========================================================================
    // Extractor: Lucene TF-IDF
    // =========================================================================

    private class LuceneExtractor implements Extractor {

        private final MessageIndexer indexer;

        LuceneExtractor(final MessageIndexer indexer) {
            this.indexer = indexer;
        }

        @Override
        public Set<String> extract(final Agent agent, final String text, final boolean blocking) {
            // Return top-10 concepts from the index (must exceed min doc freq of 1)
            final List<MessageIndexer.Concept> topConcepts = this.indexer.getImportantConcepts(10, 1);
            final Set<String> result = new LinkedHashSet<>();
            for (final MessageIndexer.Concept c : topConcepts) {
                final String term = c.term().toLowerCase();
                // Filter stop words and fragments — Lucene tokenization
                // still passes short stems through the index.
                if (term.length() < 3 || STOP_WORDS.contains(term)) continue;
                final String normalized = CommonUtil.normalize(term);
                if (!normalized.isEmpty())
                    result.add(normalized);
            }
            LOG.debug("lucene extracted %d concepts from %d docs: %s", result.size(), this.indexer.documentCount(), result);
            return result;
        }

        private static final Set<String> STOP_WORDS = Set.of(
                "the", "and", "for", "are", "but", "not", "you", "all", "can",
                "had", "her", "was", "one", "our", "out", "has", "have", "been",
                "some", "than", "that", "this", "with", "from", "they", "will",
                "just", "like", "into", "over", "them", "then", "also", "very",
                "what", "when", "where", "which", "about", "each", "more", "how",
                "its", "get", "got", "did", "does", "any", "who", "why", "well",
                "much", "such", "here", "there", "their", "these", "those",
                "would", "could", "should", "after", "before", "between", "through"
        );
    }

    // =========================================================================
    // Lucene Message Indexer (in-memory)
    // =========================================================================

    /**
     * In-memory Lucene index keyed by message VID for selective retrieval
     * and TF-IDF concept extraction.
     * <p>
     * Text is fed via {@link #indexText(String)} from the streaming callbacks
     * ({@code onPartialThinking}, {@code onPartialResponse}, {@code onCompleteResponse}).
     * Concepts are extracted via {@link #getImportantConcepts(int, int)}.
     */
    public static class MessageIndexer implements AutoCloseable {

        private static final GraphittyLogger LOG = Graphitty.log(MessageIndexer.class);

        private static final String FIELD_TEXT = "text";
        private static final String FIELD_TIME = "time";

        private final Directory directory;
        private final StandardAnalyzer analyzer;
        private final IndexWriter writer;

        public MessageIndexer() {
            try {
                this.directory = new ByteBuffersDirectory();
                this.analyzer = new StandardAnalyzer();
                final IndexWriterConfig config = new IndexWriterConfig(this.analyzer);
                config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
                this.writer = new IndexWriter(this.directory, config);
                this.writer.commit();
            } catch (final IOException e) {
                throw MTronException.of("failed to create message index: %s", e);
            }
        }

        /**
         * Index a chunk of text.  Called incrementally during streaming.
         */
        public void indexText(final String text) {
            try {
                final Document doc = new Document();
                doc.add(new TextField(FIELD_TEXT, text, Field.Store.NO));
                doc.add(new LongPoint(FIELD_TIME, System.currentTimeMillis()));
                doc.add(new StoredField(FIELD_TIME, System.currentTimeMillis()));
                this.writer.addDocument(doc);
                this.writer.commit();
                LOG.debug("indexed text chunk length=%d, total-docs=%d", text.length(), documentCount());
            } catch (final IOException e) {
                LOG.warn("failed to index text: %s", e.getMessage());
            }
        }

        /**
         * Returns the top-N highest-TF-IDF terms in the index.
         *
         * @param topN       max concepts to return
         * @param minDocFreq minimum number of documents a term must appear in
         */
        public List<Concept> getImportantConcepts(final int topN, final int minDocFreq) {
            final List<Concept> concepts = new ArrayList<>();
            try (final DirectoryReader reader = DirectoryReader.open(this.writer)) {
                final int numDocs = reader.numDocs();
                if (numDocs == 0) return concepts;

                final Map<String, Concept> termMap = new LinkedHashMap<>();
                for (final org.apache.lucene.index.LeafReaderContext ctx : reader.leaves()) {
                    final Terms terms = ctx.reader().terms(FIELD_TEXT);
                    if (terms == null) continue;
                    final TermsEnum termsEnum = terms.iterator();
                    BytesRef term;
                    while ((term = termsEnum.next()) != null) {
                        final String termStr = term.utf8ToString();
                        final long docFreq = termsEnum.docFreq();
                        final long totalTermFreq = termsEnum.totalTermFreq();
                        final Concept existing = termMap.get(termStr);
                        if (existing != null) {
                            existing.tf += totalTermFreq;
                            existing.docFreq += docFreq;
                        } else {
                            final Concept c = new Concept(termStr, totalTermFreq);
                            c.docFreq = docFreq;
                            termMap.put(termStr, c);
                        }
                    }
                }

                for (final Concept concept : termMap.values()) {
                    if (concept.docFreq < minDocFreq) continue;
                    final double idf = Math.log(1.0 + (numDocs + 1.0) / (concept.docFreq + 1.0));
                    concept.score = concept.tf * idf;
                }

                concepts.addAll(termMap.values().stream()
                        .filter(c -> c.docFreq >= minDocFreq)
                        .sorted(Comparator.comparingDouble(Concept::score).reversed())
                        .limit(topN)
                        .toList());
            } catch (final IOException e) {
                LOG.warn("failed to extract concepts: %s", e.getMessage());
            }
            return concepts;
        }

        public int documentCount() {
            return this.writer.getDocStats().numDocs;
        }

        @Override
        public void close() {
            try {
                this.writer.close();
                this.analyzer.close();
                this.directory.close();
            } catch (final IOException e) {
                LOG.warn("error closing message index: %s", e.getMessage());
            }
        }

        /**
         * A scored concept extracted from the message index.
         */
        public static class Concept {
            public final String term;
            public double score;
            public long tf;
            public long docFreq;

            Concept(final String term, final long tf) {
                this.term = term;
                this.tf = tf;
            }

            public String term() {
                return term;
            }

            public double score() {
                return score;
            }

            public long termFrequency() {
                return tf;
            }

            public long documentFrequency() {
                return docFreq;
            }

            @Override
            public String toString() {
                return String.format("%s (score=%.2f, tf=%d, docs=%d)", term, score, tf, docFreq);
            }
        }
    }
}
