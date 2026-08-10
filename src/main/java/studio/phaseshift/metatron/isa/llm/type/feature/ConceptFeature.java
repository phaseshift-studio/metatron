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
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.thread.CoreThread;
import studio.phaseshift.metatron.isa.mach.type.ui.console.StatusLine;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.res;
import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread.virtual;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class ConceptFeature extends AbstractFeature {

    private static final String CONCEPT = "concept";
    private static final String MESSAGE = "message";
    private static final fURI MESSAGES_INST_TID = LLM_CONCEPT_FEATURE_TID.extend(INST).extend("messages");
    private static final fURI CONCEPTS_INST_TID = LLM_CONCEPT_FEATURE_TID.extend(INST).extend("concepts");
    private static final Pattern CONCEPT_PATTERN = Pattern.compile("<<concept:([^>]+)>>");
    // ── Config keys ─────────────────────────────────────────────────
    private static final fURI EXTRACTOR = f("extractor");
    private static final fURI EXTRACTOR_TAG = f("tag");
    private static final fURI EXTRACTOR_AGENT = f("agent");
    private static final fURI EXTRACTOR_LUCENE = f("lucene");

    // ── Extractor instances ─────────────────────────────────────────
    private final Extractor extractor;
    private final MessageIndexer indexer; // shared across extractor types for read-side queries
    private final List<String> conceptRecommendations = new ArrayList<>();
    private final Set<String> knownConceptNames = new LinkedHashSet<>();
    private boolean knownConceptNamesLoaded = false;
    // ── Templates ───────────────────────────────────────────────────

    /**
     * System message injected when using {@link TaggingExtractor}.
     * The agent is instructed to manually wrap key concepts in
     * {@code <<concept:...>>} inline tags, which are parsed via regex
     * to build a co-location graph.
     */
    private static final String CONCEPT_EXTRACTOR_TAG_SYSTEM_MESSAGE = """
                                                                       In any of your responses, you can tag important concepts using a <<concept:>>-block:
                                                                       For instance, an agent may write:
                                                                       
                                                                       "Increasing the size of the <<concept:context windows>> is one way to increase an agent's
                                                                       <<concept:intelligence>>. However, another way is to provide better <<concept:indexing>> and
                                                                       <<concept:searching>> capabilities for existing <<concept:memory systems>>."
                                                                       
                                                                       Behind the scenes, these tags will form a growing co-location graph that will allow
                                                                       for the automatic insertion of relevant historic memories the agent can choose
                                                                       to review. For instance, given the above, the next system message may write:
                                                                       """;

    /**
     * System message injected when using {@link AgentExtractor}.
     * A separate translator agent (LLM) post-processes the main agent's
     * responses to extract concepts automatically.  The main agent does
     * not need to tag concepts manually.
     */
    private static final String CONCEPT_EXTRACTOR_AGENT_SYSTEM_MESSAGE = """
                                                                         As you respond, a separate analysis agent running behind the scenes automatically
                                                                         extracts key concepts from your output using a language model.  These concepts are
                                                                         organized into a co-location graph that connects related ideas across the conversation.
                                                                         
                                                                         When relevant historic memories are identified, they will be surfaced via the mtron
                                                                         eval tool so you can review them before continuing.
                                                                         
                                                                         You do not need to tag concepts manually — the extraction happens automatically.
                                                                         Respond naturally and the concept graph will build itself.
                                                                         """;

    /**
     * System message injected when using {@link LuceneExtractor}.
     * TF-IDF statistical analysis over the accumulated message corpus
     * automatically identifies important terms.  The agent does not need
     * to tag concepts manually.
     */
    private static final String CONCEPT_EXTRACTOR_LUCENE_SYSTEM_MESSAGE = """
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

    private static final String CONCEPT_FEATURE_SYSTEM_TEMPLATE = """
                                                                  The following concepts have recently been extracted.
                                                                  
                                                                  %s
                                                                  
                                                                  To review messages associated with concepts, use tool:
                                                                    %s(c1,c2,...)
                                                                  To see related adjacent concepts, use tool:
                                                                    %s(c1,c2,...)
                                                                  Both tools can take 1 or more concept arguments.
                                                                  """;

    // =========================================================================
    // Constructor
    // =========================================================================

    public ConceptFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.indexer = new MessageIndexer(LuceneExtractor.STOP_WORDS);
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

    public Lst skill(final Agent agent) {
        final String content = switch (this.extractor) {
            case TaggingExtractor ignored -> CONCEPT_EXTRACTOR_TAG_SYSTEM_MESSAGE;
            case AgentExtractor ignored -> CONCEPT_EXTRACTOR_AGENT_SYSTEM_MESSAGE;
            case LuceneExtractor ignored -> CONCEPT_EXTRACTOR_LUCENE_SYSTEM_MESSAGE;
            case null, default -> null;
        };
        if (content == null) return lst();
        return lst(rec(mutableMap(uri(NAME), uri(CONCEPT),
                uri(DESC), str("In situ concept graph construction w/ spreading activation recommendation"),
                uri(CONTENT), str(content),
                uri(TOOL), lst(
                        docWrap(instC(MESSAGES_INST_TID.dom(ALL.maybe()).rng(STR_TID.maybeSome()),
                                        lst(URI_TYPE),
                                        start_(jnt(0)).from_(uri("0")).dedup_().swap_(block_(mult_(uri(this.getBaseURI())))).from_(id_()).select_(uri(f("message").extend("+").extend("text"))).tryToInst()),
                                "maybe an obj",
                                "a stream of message texts",
                                Map.of(jnt(0), "a concept uri"),
                                "fetches past messages associated with the concept",
                                MESSAGES_INST_TID + "(metatron) [-- returns messages discussing metatron --]"),
                        docWrap(instC(CONCEPTS_INST_TID.dom(ALL.maybe()).rng(LST_TID.maybeSome()),
                                        lst(URI_TYPE),
                                        start_(jnt(0)).from_(uri("0")).dedup_().swap_(block_(mult_(uri(this.getBaseURI())))).from_(id_()).select_(uri("concept")).tryToInst()),
                                "maybe an obj",
                                "a lst of related concept auto_froms",
                                Map.of(jnt(0), "a concept uri"),
                                "fetches concepts associated with the provided concept"))), LLM_SKILL_TID, null));
        
            /*
          docWrap(instC(LLM_CONCEPT_FEATURE_TID.extend(INST).extend("messages").dom(ALL.maybe()).rng(STR_TID.maybeSome()),
                                        lst(MType.T(URI_TID.some())),
                                        from_(uri("0")).swap_(block_(mult_(uri(this.getBaseURI())))).from_(id_()).select_(uri(f("message").extend("+").extend("text"))).split_(lst(id_().tryToInst())).sum_().tryToInst()),
                                "maybe an obj",
                                "a stream of message texts",
                                Map.of(jnt(0), "a concept uri"),
                                "fetches past messages associated with the concept",
                                "messages(metatron) [-- returns messages discussing metatron --]"),
                        docWrap(instC(LLM_CONCEPT_FEATURE_TID.extend(INST).extend("concepts").dom(ALL.maybe()).rng(LST_TID.maybeSome()),
                                        lst(MType.T(URI_TID.some())),
                                        from_(uri("0")).swap_(block_(mult_(uri(this.getBaseURI())))).from_(id_()).select_(uri("concept")).sum_().tryToInst()),
                                "maybe an obj",
                                "a lst of related concepts",
                                Map.of(jnt(0), "a concept uri"),
                                "fetches concepts associated with the provided concept"))));
         */

    }

    // =========================================================================
    // Shared concept storage
    // =========================================================================

    private fURI getBaseURI() {
        return this.at(BASE).uriValue();
    }

    /**
     * Lazily populate {@link #knownConceptNames} from the concept space.
     * Each concept is stored as a direct child of {@link #getBaseURI()};
     * we enumerate them via the {@code +/} branch query.
     */
    private void loadExistingConceptNames() {
        if (this.knownConceptNamesLoaded) return;
        this.knownConceptNamesLoaded = true;
        try {
            final Obj children = Router.readFromSpace(this.getBaseURI().extend("+/"));
            if (children.isLst()) {
                children.asLst().stream()
                        .filter(o -> !o.isNoObj())
                        .forEach(o -> {
                            final fURI conceptURI = o.asRel().first().uriValue();
                            final String name = conceptURI.name();
                            if (name != null && !name.isBlank() && name.length() >= 4) {
                                this.knownConceptNames.add(name);
                            }
                        });
                LOG.debug("loaded %d existing concept names from space", this.knownConceptNames.size());
            }
        } catch (final Exception e) {
            // Space may not be ready yet — concepts will accumulate as they arrive
            LOG.debug("could not load existing concept names: %s", e.getMessage());
        }
    }

    /**
     * Persist concepts into the space graph with co-location links and
     * message back-references.  Shared by all extractor implementations.
     * <p>
     * Before storing, incoming concept strings are spell-checked against
     * the existing concept names in the space.  If a close match is found
     * (e.g. "inteligence" vs "intelligence"), the existing spelling is used
     * instead, preventing the concept graph from being polluted by typos.
     */
    private Set<fURI> addConceptsToSpace(final Agent agent, final Set<String> conceptStrings) {
        final Set<fURI> concepts = new HashSet<>();

        // ── Spell correction ─────────────────────────────────────────
        // Check incoming concept strings against existing concept names
        // so typos don't fragment the concept graph.
        loadExistingConceptNames();
        final Set<String> correctedStrings = new LinkedHashSet<>();
        for (final String c : conceptStrings) {
            final String corrected = CommonUtil.correctSpelling(c, this.knownConceptNames);
            if (!corrected.equals(c)) {
                LOG.debug("spell-corrected concept: '%s' -> '%s'", c, corrected);
                StatusLine.message(str("corrected '%s' -> '%s'".formatted(c, corrected)));
            }
            correctedStrings.add(corrected);
        }
        this.knownConceptNames.addAll(correctedStrings);
        // ── End spell correction ─────────────────────────────────────

        try {
            LOG.debug("concepts to process: %s", correctedStrings);
            for (final String concept : correctedStrings) {
                final fURI conceptURI = this.getBaseURI().extend(concept);
                final Rec conceptRec = Router.readFromSpace(conceptURI).orElse(rec());
                //if (!conceptRec.has(CONCEPT)) conceptRec.at(CONCEPT, uri(concept), MUTABLE);
                final Lst conceptLink = conceptRec.at(CONCEPT).orElse(lst());
                final Set<Obj> conceptLinkList = new LinkedHashSet<>(conceptLink.jvm());
                final int conceptLinkListSize = conceptLinkList.size();
                conceptLinkList.addAll(correctedStrings.stream()
                        .filter(c -> !c.equals(concept))
                        .map(c -> auto_from_(this.getBaseURI().extend(c)).tryToInst()).toList());
                if (conceptLinkList.size() > conceptLinkListSize) {
                    conceptRec.jvm().put(uri(CONCEPT), lst(new ArrayList<>(conceptLinkList)));
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
                                conceptRec.jvm().put(uri(MESSAGE), lst(new ArrayList<>(messageList)));
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
    private void injectConceptRecommendations(final Agent agent, final Set<fURI> concepts) {

        for (final fURI conceptURI : new HashSet<>(concepts)) {
            try {
                // check if concept has associated messages
                final Obj conceptObj = Router.readFromSpace(conceptURI);
                if (conceptObj.isRec()) {
                    final Rec conceptRec = conceptObj.asRec();
                    if (conceptRec.has(MESSAGE)) {
                        final Obj msgObj = conceptRec.at(MESSAGE);
                        if (!msgObj.isLst()) {
                            LOG.warn("concept messages are not structured correctly: %s", msgObj);
                            continue;
                        }
                        final Lst messages = msgObj.asLst();
                        if (!messages.lstValue().isEmpty()) {
                            this.conceptRecommendations.add(conceptURI.name());
                            LOG.debug("adding recommendation: %s", conceptURI.name());
                        }
                    }
                }
            } catch (final Exception e) {
                LOG.warn("failed to check concept messages for %s: %s", conceptURI, e.getMessage());
            }
        }
    }

    /**
     * Extract concept strings from text, persist them, and inject recommendations.
     * Called by onCompleteResponse and (if agent-mode) onBeforeChat.
     */
    private Set<fURI> processConcepts(final Agent agent, final String text, final boolean blocking) {
        if (text == null || text.isBlank()) return Set.of();
        final Set<String> conceptStrings = this.extractor.extract(agent, text, blocking);
        if (conceptStrings.isEmpty()) return Set.of();
        return this.addConceptsToSpace(agent, conceptStrings);
    }

    // =========================================================================
    // Streaming lifecycle
    // =========================================================================

    @Override
    public Obj onBeforeChat(final Agent agent) {
        final Set<fURI> concepts = this.processConcepts(agent, agent.userMessage(), true);
        this.injectConceptRecommendations(agent, concepts);
        if (!this.conceptRecommendations.isEmpty()) {
            agent.addSystemMessage(CONCEPT_FEATURE_SYSTEM_TEMPLATE
                    .formatted(this.conceptRecommendations.stream().reduce("", (a, b) -> a + b + "\n"),
                            MESSAGES_INST_TID,
                            CONCEPTS_INST_TID));
        }
        this.conceptRecommendations.clear();
        return noobj();
    }

    @Override
    public void onCompleteResponse(final Agent agent, final Str text) {
        final Set<fURI> newConcepts = this.processConcepts(agent, text != null ? text.strValue() : "", false);

        // Extract <<concept:>> tags from the agent's thinking text.
        // Thoughts are never run through automatic TF-IDF extraction — only
        // explicit annotations count.  This lets the agent bookmark semantic
        // insights discovered during reasoning without surfacing them in the
        // final response.
        final Obj thinking = agent.at(res(THINKING));
        if (!thinking.isNoObj() && !thinking.strValue().isBlank()) {
            final Set<String> thoughtConcepts = new TaggingExtractor().extract(agent, thinking.strValue(), false);
            if (!thoughtConcepts.isEmpty()) {
                this.addConceptsToSpace(agent, thoughtConcepts);
            }
            // newConcepts.addAll(thoughtConcepts);
        }
        if (!newConcepts.isEmpty()) {
            docWrap(virtual(instLambda((a, b) -> {
                this.injectConceptRecommendations(agent, newConcepts);
                return noobj();
            })), "updating concept graph").apply();
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
                if (!concept.isEmpty() && conceptStrings.add(concept)) {
                    StatusLine.message(str("%s extracted".formatted(concept)));
                    // LOG.info("%s extracted", concept);
                }
            }
            return conceptStrings;
        }
    }

    // =========================================================================
    // Extractor: LLM post-analysis (agent-based)
    // =========================================================================

    private class AgentExtractor implements Extractor {
        private Agent translatorAgent;

        @Override
        public Set<String> extract(final Agent agent, final String text, final boolean blocking) {
            if (this.translatorAgent == null && agent.feature(CONCEPT).asRec().has(MODEL)) {
                this.translatorAgent = new Agent(mutableMap(
                        uri(FEATURE), lst(new ChatFeature(mutableMap(
                                uri(MODEL), agent.feature(CONCEPT).asRec().at(MODEL),
                                uri(RESPONSE), rec(uri(TO), id_().tryToInst())),
                                LLM_CHAT_FEATURE_TID, null))), LLM_AGENT_TID, null);
                LOG.debug("created translator agent: %s", this.translatorAgent);
            }
            if (null == this.translatorAgent)
                return Set.of();
            final Set<String> conceptStrings = new LinkedHashSet<>();
            try {
                LOG.info("using agent to extract concepts from text length=%d", text.length());
                final CoreThread thread = CoreThread.core(instLambda((lhs, inst) -> {
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
        private static final Set<String> STOP_WORDS = Set.of("able", "about", "above", "abroad", "according", "accordingly", "across", "actually", "adj", "after", "afterwards", "again", "against", "ago", "ahead", "ain't", "all", "allow", "allows", "almost", "alone", "along", "alongside", "already", "also", "although", "always", "am", "amid", "amidst", "among", "amongst", "an", "and", "another", "any", "anybody", "anyhow", "anyone", "anything", "anyway", "anyways", "anywhere", "apart", "appear", "appreciate", "appropriate", "are", "aren't", "around", "as", "a's", "aside", "ask", "asking", "associated", "at", "available", "away", "awfully", "back", "backward", "backwards", "be", "became", "because", "become", "becomes", "becoming", "been", "before", "beforehand", "begin", "behind", "being", "believe", "below", "beside", "besides", "best", "better", "between", "beyond", "both", "brief", "but", "by", "came", "can", "cannot", "cant", "can't", "caption", "cause", "causes", "certain", "certainly", "changes", "clearly", "c'mon", "co", "co.", "com", "come", "comes", "concerning", "consequently", "consider", "considering", "contain", "containing", "contains", "corresponding", "could", "couldn't", "course", "c's", "currently", "dare", "daren't", "definitely", "described", "despite", "did", "didn't", "different", "directly", "do", "does", "doesn't", "doing", "done", "don't", "down", "downwards", "during", "each", "edu", "eg", "eight", "eighty", "either", "else", "elsewhere", "end", "ending", "enough", "entirely", "especially", "et", "etc", "even", "ever", "evermore", "every", "everybody", "everyone", "everything", "everywhere", "ex", "exactly", "example", "except", "fairly", "far", "farther", "few", "fewer", "fifth", "first", "five", "followed", "following", "follows", "for", "forever", "former", "formerly", "forth", "forward", "found", "four", "from", "further", "furthermore", "get", "gets", "getting", "given", "gives", "go", "goes", "going", "gone", "got", "gotten", "greetings", "had", "hadn't", "half", "happens", "hardly", "has", "hasn't", "have", "haven't", "having", "he", "he'd", "he'll", "hello", "help", "hence", "her", "here", "hereafter", "hereby", "herein", "here's", "hereupon", "hers", "herself", "he's", "hi", "him", "himself", "his", "hither", "hopefully", "how", "howbeit", "however", "hundred", "i'd", "ie", "if", "ignored", "i'll", "i'm", "immediate", "in", "inasmuch", "inc", "inc.", "indeed", "indicate", "indicated", "indicates", "inner", "inside", "insofar", "instead", "into", "inward", "is", "isn't", "it", "it'd", "it'll", "its", "it's", "itself", "i've", "just", "k", "keep", "keeps", "kept", "know", "known", "knows", "last", "lately", "later", "latter", "latterly", "least", "less", "lest", "let", "let's", "like", "liked", "likely", "likewise", "little", "look", "looking", "looks", "low", "lower", "ltd", "made", "mainly", "make", "makes", "many", "may", "maybe", "mayn't", "me", "mean", "meantime", "meanwhile", "merely", "might", "mightn't", "mine", "minus", "miss", "more", "moreover", "most", "mostly", "mr", "mrs", "much", "must", "mustn't", "my", "myself", "name", "namely", "nd", "near", "nearly", "necessary", "need", "needn't", "needs", "neither", "never", "neverf", "neverless", "nevertheless", "new", "next", "nine", "ninety", "no", "nobody", "non", "none", "nonetheless", "noone", "no-one", "nor", "normally", "not", "nothing", "notwithstanding", "novel", "now", "nowhere", "obviously", "of", "off", "often", "oh", "ok", "okay", "old", "on", "once", "one", "ones", "one's", "only", "onto", "opposite", "or", "other", "others", "otherwise", "ought", "oughtn't", "our", "ours", "ourselves", "out", "outside", "over", "overall", "own", "particular", "particularly", "past", "per", "perhaps", "placed", "please", "plus", "possible", "presumably", "probably", "provided", "provides", "que", "quite", "qv", "rather", "rd", "re", "really", "reasonably", "recent", "recently", "regarding", "regardless", "regards", "relatively", "respectively", "right", "round", "said", "same", "saw", "say", "saying", "says", "second", "secondly", "see", "seeing", "seem", "seemed", "seeming", "seems", "seen", "self", "selves", "sensible", "sent", "serious", "seriously", "seven", "several", "shall", "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "since", "six", "so", "some", "somebody", "someday", "somehow", "someone", "something", "sometime", "sometimes", "somewhat", "somewhere", "soon", "sorry", "specified", "specify", "specifying", "still", "sub", "such", "sup", "sure", "take", "taken", "taking", "tell", "tends", "th", "than", "thank", "thanks", "thanx", "that", "that'll", "thats", "that's", "that've", "the", "their", "theirs", "them", "themselves", "then", "thence", "there", "thereafter", "thereby", "there'd", "therefore", "therein", "there'll", "there're", "theres", "there's", "thereupon", "there've", "these", "they", "they'd", "they'll", "they're", "they've", "thing", "things", "think", "third", "thirty", "this", "thorough", "thoroughly", "those", "though", "three", "through", "throughout", "thru", "thus", "till", "to", "together", "too", "took", "toward", "towards", "tried", "tries", "truly", "try", "trying", "t's", "twice", "two", "un", "under", "underneath", "undoing", "unfortunately", "unless", "unlike", "unlikely", "until", "unto", "up", "upon", "upwards", "us", "use", "used", "useful", "uses", "using", "usually", "v", "value", "various", "versus", "very", "via", "viz", "vs", "want", "wants", "was", "wasn't", "way", "we", "we'd", "welcome", "well", "we'll", "went", "were", "we're", "weren't", "we've", "what", "whatever", "what'll", "what's", "what've", "when", "whence", "whenever", "where", "whereafter", "whereas", "whereby", "wherein", "where's", "whereupon", "wherever", "whether", "which", "whichever", "while", "whilst", "whither", "who", "who'd", "whoever", "whole", "who'll", "whom", "whomever", "who's", "whose", "why", "will", "willing", "wish", "with", "within", "without", "wonder", "won't", "would", "wouldn't", "yes", "yet", "you", "you'd", "you'll", "your", "you're", "yours", "yourself", "yourselves", "you've", "zero", "a", "how's", "i", "when's", "why's", "b", "c", "d", "e", "f", "g", "h", "j", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "uucp", "w", "x", "y", "z", "I", "www", "amount", "bill", "bottom", "call", "computer", "con", "couldnt", "cry", "de", "describe", "detail", "due", "eleven", "empty", "fifteen", "fifty", "fill", "find", "fire", "forty", "front", "full", "give", "hasnt", "herse", "himse", "interest", "itse”", "mill", "move", "myse”", "part", "put", "show", "side", "sincere", "sixty", "system", "ten", "thick", "thin", "top", "twelve", "twenty", "abst", "accordance", "act", "added", "adopted", "affected", "affecting", "affects", "ah", "announce", "anymore", "apparently", "approximately", "aren", "arent", "arise", "auth", "beginning", "beginnings", "begins", "biol", "briefly", "ca", "date", "ed", "effect", "et-al", "ff", "fix", "gave", "giving", "heres", "hes", "hid", "home", "id", "im", "immediately", "importance", "important", "index", "information", "invention", "itd", "keys", "kg", "km", "largely", "lets", "line", "'ll", "means", "mg", "million", "ml", "mug", "na", "nay", "necessarily", "nos", "noted", "obtain", "obtained", "omitted", "ord", "owing", "page", "pages", "poorly", "possibly", "potentially", "pp", "predominantly", "present", "previously", "primarily", "promptly", "proud", "quickly", "ran", "readily", "ref", "refs", "related", "research", "resulted", "resulting", "results", "run", "sec", "section", "shed", "shes", "showed", "shown", "showns", "shows", "significant", "significantly", "similar", "similarly", "slightly", "somethan", "specifically", "state", "states", "stop", "strongly", "substantially", "successfully", "sufficiently", "suggest", "thered", "thereof", "therere", "thereto", "theyd", "theyre", "thou", "thoughh", "thousand", "throug", "til", "tip", "ts", "ups", "usefully", "usefulness", "'ve", "vol", "vols", "wed", "whats", "wheres", "whim", "whod", "whos", "widely", "words", "world", "youd", "youre");

        private final MessageIndexer indexer;

        LuceneExtractor(final MessageIndexer indexer) {
            this.indexer = indexer;
        }

        @Override
        public Set<String> extract(final Agent agent, final String text, final boolean blocking) {
            if (text != null && !text.isBlank())
                this.indexer.indexText(text);
            // Per-document extraction: score terms by their frequency
            // in *this* text, weighted by corpus-wide IDF for distinctiveness.
            // This gives concepts relevant to the current message rather
            // than the same global top-10 for every message.
            final List<MessageIndexer.Concept> topConcepts =
                    this.indexer.getImportantConcepts(text, 10);
            final Set<String> result = new LinkedHashSet<>();
            for (final MessageIndexer.Concept c : topConcepts) {
                final String term = c.term().toLowerCase();
                if (term.length() < 3 || STOP_WORDS.contains(term) || term.contains(":")) continue;
                final String normalized = CommonUtil.normalize(term);
                if (!normalized.isEmpty())
                    result.add(normalized);
            }
            LOG.info("lucene extracted %d concepts from %d docs: %s",
                    result.size(), this.indexer.documentCount(), result);
            result.addAll(new TaggingExtractor().extract(agent, text, blocking));
            return result;
        }
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
}
