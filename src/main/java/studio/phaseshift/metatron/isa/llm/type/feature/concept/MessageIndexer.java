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

package studio.phaseshift.metatron.isa.llm.type.feature.concept;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.util.*;

public class MessageIndexer implements AutoCloseable {

    private static final GraphittyLogger LOG = Graphitty.log(MessageIndexer.class);

    private static final String FIELD_TEXT = "text";
    private static final String FIELD_TIME = "time";

    private final Directory directory;
    private final Analyzer analyzer;
    private final IndexWriter writer;

    public MessageIndexer(final Set<String> stopWords) {
        try {
            this.directory = new ByteBuffersDirectory();
            // Convert to CharArraySet for StopFilter.  Use the same
            // curated stop-word list that LuceneExtractor applies as a
            // secondary quality gate — no PorterStemFilter here so terms
            // stay in their natural form for predictable concept lookup.
            final CharArraySet stops = new CharArraySet(stopWords, true);
            this.analyzer = new Analyzer() {
                @Override
                protected TokenStreamComponents createComponents(final String fieldName) {
                    final StandardTokenizer src = new StandardTokenizer();
                    TokenStream result = new LowerCaseFilter(src);
                    result = new StopFilter(result, stops);
                    return new TokenStreamComponents(src, result);
                }
            };
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
        // Open on the directory directly rather than on the IndexWriter.
        // DirectoryReader.open(IndexWriter) internally calls flushAllThreads(),
        // applyAllDeletesAndUpdates(), and acquires the writer lock — all
        // synchronized operations that pin a virtual thread to its carrier.
        // When called from deep LangChain4j streaming callback chains, the
        // pinned stack cannot grow and StackOverflowError results.
        // Since indexText() already calls writer.commit() before we are
        // invoked, the directory has the latest committed state.
        try (final DirectoryReader reader = DirectoryReader.open(this.directory)) {
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
        } catch (final IndexNotFoundException e) {
            // No documents indexed yet — return empty list
        } catch (final IOException e) {
            LOG.warn("failed to extract concepts: %s", e.getMessage());
        }
        return concepts;
    }

    /**
     * Tokenize text with the analyzer and return a term→frequency map.
     * Uses the same analyzer as indexing so stemming and stop-word
     * removal are consistent.
     */
    private Map<String, Long> tokenize(final String text) {
        final Map<String, Long> tf = new LinkedHashMap<>();
        try (final TokenStream ts = this.analyzer.tokenStream(FIELD_TEXT, text)) {
            final CharTermAttribute charTerm = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                tf.merge(charTerm.toString(), 1L, Long::sum);
            }
            ts.end();
        } catch (final IOException e) {
            // non-empty text that fails tokenization is pathological
        }
        return tf;
    }

    /**
     * Extract top-N concepts from a specific text by scoring local
     * term frequency against global inverse document frequency.
     * <p>
     * This is per-document extraction — terms that are frequent
     * <em>in this text</em> and rare <em>across the corpus</em>
     * rank highest.  The global IDF is computed from the committed
     * index, so the corpus must contain at least one prior document
     * for IDF weighting to work; with zero docs, falls back to
     * local TF-only scoring.
     */
    public List<Concept> getImportantConcepts(final String text, final int topN) {
        if (text == null || text.isBlank()) return List.of();

        // 1. Local term frequency from the input text
        final Map<String, Long> localTF = tokenize(text);
        if (localTF.isEmpty()) return List.of();

        // 2. Global document frequency from the committed index (for IDF)
        final Map<String, Long> globalDF = new LinkedHashMap<>();
        int numDocs = 0;
        try (final DirectoryReader reader = DirectoryReader.open(this.directory)) {
            numDocs = reader.numDocs();
            for (final LeafReaderContext ctx : reader.leaves()) {
                final Terms terms = ctx.reader().terms(FIELD_TEXT);
                if (terms == null) continue;
                final TermsEnum termsEnum = terms.iterator();
                BytesRef term;
                while ((term = termsEnum.next()) != null) {
                    final String termStr = term.utf8ToString();
                    if (localTF.containsKey(termStr)) {
                        globalDF.merge(termStr, (long) termsEnum.docFreq(), Long::sum);
                    }
                }
            }
        } catch (final IndexNotFoundException e) {
            // No prior docs — fall through to TF-only scoring
        } catch (final IOException e) {
            LOG.warn("failed to compute IDF: %s", e.getMessage());
            return List.of();
        }

        // 3. Score: local TF × IDF, with a floor IDF so terms
        //    with no prior document frequency still get a score.
        final List<Concept> results = new ArrayList<>();
        final double floorIDF = numDocs > 0
                ? Math.log(1.0 + (numDocs + 1.0) / 2.0)   // treat unseen as df=1
                : 1.0;
        for (final Map.Entry<String, Long> e : localTF.entrySet()) {
            final String term = e.getKey();
            final long tf = e.getValue();
            final long df = globalDF.getOrDefault(term, 0L);
            final double idf;
            if (df > 0) {
                idf = Math.log(1.0 + (numDocs + 1.0) / (df + 1.0));
            } else {
                idf = floorIDF; // term not in corpus yet → treat as rare
            }
            final Concept c = new Concept(term, tf);
            c.docFreq = df;
            c.score = tf * idf;
            results.add(c);
        }

        results.sort(Comparator.comparingDouble(Concept::score).reversed());
        return results.stream().limit(topN).toList();
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
