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
import studio.phaseshift.metatron.isa.llm.type.ChatFrame;
import studio.phaseshift.metatron.isa.llm.type.feature.service.ChatService;
import studio.phaseshift.metatron.isa.llm.type.feature.service.ConceptService;
import studio.phaseshift.metatron.isa.llm.type.feature.service.SkillService;
import studio.phaseshift.metatron.isa.llm.type.feature.service.SystemService;
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.console.StatusLine;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.TextUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread.virtual;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The shared concept-feature substrate: distills text into concept uris, persists them
 * into a co-location graph, and surfaces historic-memory recommendations.  Every provider
 * ({@code tagging}, {@code agent}, {@code lucene}) offers the same {@link ConceptService}
 * ({@code LLM_CONCEPT_SERVICE_TID}); they differ only in the extraction algorithm and the
 * system message they inject, which are the two abstract seams below.
 */
public abstract class AbstractConceptFeature extends AbstractFeature implements ConceptService {

    @Override
    public Set<fURI> offers() {
        return Set.of(LLM_CONCEPT_SERVICE_TID);
    }

    @Override
    public Set<fURI> requires() {
        return Set.of(LLM_SKILL_SERVICE_TID);
    }

    private static final String CONCEPT = "concept";
    private static final String MESSAGE = "message";
    private static final fURI MESSAGES_INST_TID = LLM_CONCEPT_SERVICE_TID.extend(INST).extend("messages");
    private static final fURI CONCEPTS_INST_TID = LLM_CONCEPT_SERVICE_TID.extend(INST).extend("concepts");

    protected static final TextUtil.StopWordSet GLOBAL_STOP_WORD_SET = TextUtil.StopWordSet.ALL;
    protected static final Pattern CONCEPT_PATTERN = Pattern.compile("<<concept:([^>]+)>>");

    private static final String CONCEPT_FEATURE_SYSTEM_TEMPLATE =
            """
            ---[concept_feature]---
            The following concepts have recently been extracted.
            %s
            To review messages associated with concepts, use tool:
              %s(c1,c2,...)
            To see related adjacent concepts, use tool:
              %s(c1,c2,...)
            Both tools can take 1 or more concept arguments.
            """;


    private final Set<String> conceptRecommendations = new HashSet<>();
    private final Set<String> knownConceptNames = new LinkedHashSet<>();
    private boolean knownConceptNamesLoaded = false;

    protected AbstractConceptFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public fURI root(final Agent agent) {
        return this.getRoot(agent);
    }

    /**
     * The concept namespace root — stable across providers ({@code concept}, not the
     * provider's own tid segment {@code tagging}/{@code agent}/{@code lucene}).
     */
    @Override
    public fURI getRoot(final Agent agent) {
        if (this.has(ROOT))
            return this.at(ROOT).uriValue();
        if (agent.has(ROOT))
            return agent.at(ROOT).uriValue().extend("concept");
        throw MTronException.of("no root uri found on feature nor agent: %s", this.tid());
    }

    // ── Provider seams ───────────────────────────────────────────────

    /**
     * Distill text into normalized concept strings — the provider-specific algorithm.
     */
    protected abstract Set<String> extract(Agent agent, String text, boolean blocking);

    /**
     * The system message this provider injects (explaining how it extracts concepts).
     */
    protected abstract String systemMessage();

    /**
     * The shared inline-tag vocabulary: parse {@code <<concept:...>>} tags from text.
     */
    protected static Set<String> extractTags(final String text) {
        final Set<String> conceptStrings = new LinkedHashSet<>();
        final Matcher matcher = CONCEPT_PATTERN.matcher(text);
        while (matcher.find()) {
            final String concept = CommonUtil.normalize(matcher.group(1));
            if (!concept.isEmpty() && conceptStrings.add(concept))
                StatusLine.message(str("%s extracted".formatted(concept)));
        }
        return conceptStrings;
    }

    // =========================================================================
    // Skill
    // =========================================================================

    /**
     * Register this feature's skill with the SkillFeature gateway — the
     * gateway forwards its tools to the ToolFeature gateway, so no local
     * addTool loop is needed here.
     */
    private void registerSkill(final Agent agent) {
        if (!agent.hasFeature(LLM_SKILL_FEATURE_TID))
            return;
        final String content = this.systemMessage();
        if (content == null) return;
        agent.requireService(SkillService.class).addSkill(mSkill.of(rec(mutableMap(uri(NAME), uri(LLM_CONCEPT_SERVICE_TID.name()),
                uri(DESC), str("in situ concept graph construction w/ spreading activation recommendation"),
                uri(CONTENT), str(content + "\nconcept::T is defined as\n%s\n".formatted(CommonUtil.indent(LLM_CONCEPT_TYPE.toString(), 2))),
                uri(TOOL), lst(
                        docWrap(instC(MESSAGES_INST_TID.dom(NOOBJ_TID.zero()).rng(STR_TID.maybeSome()),
                                        lst(URI_TYPE),
                                        start_(jnt(0)).from_(uri("0")).dedup_().swap_(block_(mult_(uri(this.getRoot(agent))))).from_(id_()).select_(uri(f("message").extend("+").extend("text"))).tryToInst()),
                                "noobj",
                                "a stream of message texts",
                                Map.of(jnt(0), "a concept uri"),
                                "fetches past messages associated with the concept",
                                MESSAGES_INST_TID + "(metatron) [-- returns messages discussing metatron --]"),
                        docWrap(instC(CONCEPTS_INST_TID.dom(NOOBJ_TID.zero()).rng(LST_TID.maybeSome()),
                                        lst(URI_TYPE),
                                        start_(jnt(0)).from_(uri("0")).dedup_().swap_(block_(mult_(uri(this.getRoot(agent))))).from_(id_()).select_(uri("concept")).tryToInst()),
                                "noobj",
                                "a lst of related concepts as auto_ats",
                                Map.of(jnt(0), "a concept uri"),
                                "fetches concepts associated with the provided concept"))))));
    }

    // =========================================================================
    // Shared concept storage
    // =========================================================================

    /**
     * Lazily populate {@link #knownConceptNames} from the concept space.
     * Each concept is stored as a direct child of {@link #getRoot(Agent)};
     * we enumerate them via the {@code +/} branch query.
     */
    private void loadExistingConceptNames(final Agent agent) {
        if (this.knownConceptNamesLoaded) return;
        this.knownConceptNamesLoaded = true;
        try {
            Router.readFromSpace(this.getRoot(agent).extend("+/"))
                    .stream()
                    .forEach(o -> this.knownConceptNames.add(o.asRel().first().uriValue().name()));
            LOG.debug("loaded %d existing concept names from space", this.knownConceptNames.size());
        } catch (final Exception e) {
            // Space may not be ready yet — concepts will accumulate as they arrive
            LOG.debug("could not load existing concept names: %s", e.getMessage());
        }
    }

    /**
     * Persist concepts into the space graph with co-location links and
     * message back-references.  Shared by all providers.
     * <p>
     * Before storing, incoming concept strings are spell-checked against
     * the existing concept names in the space.  If a close match is found
     * (e.g. "inteligence" vs "intelligence"), the existing spelling is used
     * instead, preventing the concept graph from being polluted by typos.
     */
    private Set<fURI> addConceptsToSpace(final Agent agent, final Set<String> conceptStrings) {
        final Set<fURI> concepts = new HashSet<>();
        final Set<String> filtered = conceptStrings.stream()
                .filter(c -> c.length() >= 3)
                .filter(c -> !TextUtil.getStopWords(GLOBAL_STOP_WORD_SET).contains(c.toLowerCase()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (filtered.size() < conceptStrings.size())
            LOG.debug("filtered %d stop word concepts: %s",
                    conceptStrings.size() - filtered.size(),
                    conceptStrings.stream().filter(c -> !filtered.contains(c)).toList());
        loadExistingConceptNames(agent);
        final Set<String> correctedStrings = new LinkedHashSet<>();
        for (final String c : filtered) {
            final String corrected = CommonUtil.correctSpelling(c, this.knownConceptNames);
            if (!corrected.equals(c)) {
                LOG.debug("spell-corrected concept: '%s' -> '%s'", c, corrected);
                StatusLine.message(str("corrected '%s' -> '%s'".formatted(c, corrected)));
            }
            correctedStrings.add(corrected);
        }
        this.knownConceptNames.addAll(correctedStrings);

        try {
            LOG.debug("concepts to process: %s", correctedStrings);
            for (final String concept : correctedStrings) {
                final fURI conceptURI = this.getRoot(agent).extend(concept);
                final Rec conceptRec = Router.readFromSpace(conceptURI).orElse(rec());
                final Lst conceptLink = conceptRec.at(CONCEPT).orElse(lst());
                final Set<Obj> conceptLinkList = new LinkedHashSet<>(conceptLink.jvm());
                final int conceptLinkListSize = conceptLinkList.size();
                conceptLinkList.addAll(correctedStrings.stream()
                        .filter(c -> !c.equals(concept))
                        .map(c -> auto_at_(this.getRoot(agent).extend(c)).tryToInst()).toList());
                if (conceptLinkList.size() > conceptLinkListSize) {
                    conceptRec.jvm().put(uri(CONCEPT), lst(new ArrayList<>(conceptLinkList)));
                    if (agent.hasFeature(LLM_CHAT_FEATURE_TID)) {
                        final Rec message = agent.requireService(ChatService.class).lastMessage();
                        if (!message.isNoObj() && message.hasVID()) {
                            final Lst messages = conceptRec.at(MESSAGE).orElse(lst());
                            final Set<Obj> messageList = new LinkedHashSet<>(messages.lstValue());
                            messageList.add(auto_from_(message.vid()).tryToInst());
                            conceptRec.jvm().put(uri(MESSAGE), lst(new ArrayList<>(messageList)));
                        }
                    }
                }
                Router.writeToSpace(conceptURI, conceptRec.at(NAME, str(conceptURI.name())).tid(LLM_CONCEPT_TID));
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
    @Override
    public Set<fURI> processConcepts(final Agent agent, final String text, final boolean blocking) {
        if (text == null || text.isBlank()) return Set.of();
        final Set<String> conceptStrings = this.extract(agent, text, blocking);
        if (conceptStrings.isEmpty()) return Set.of();
        return this.addConceptsToSpace(agent, conceptStrings);
    }

// =========================================================================
// Streaming lifecycle
// =========================================================================

    @Override
    public Obj onBeforeChat(final Agent agent) {
        this.registerSkill(agent);
        final Rec lastMessage = agent.requireService(ChatService.class).lastMessage();
        final Set<fURI> concepts = this.processConcepts(agent, Str.Helper.cleanString(lastMessage.at(TEXT), true), true);
        this.injectConceptRecommendations(agent, concepts);
        if (!this.conceptRecommendations.isEmpty()) {
            if (agent.hasFeature(LLM_SYSTEM_FEATURE_TID))
                agent.requireService(SystemService.class).addSystemMessage(CONCEPT_FEATURE_SYSTEM_TEMPLATE
                        .formatted(this.conceptRecommendations, MESSAGES_INST_TID, CONCEPTS_INST_TID));
        }
        this.conceptRecommendations.clear();
        return noobj();
    }

    @Override
    public void onCompleteResponse(final Agent agent, final ChatFrame result) {
        final Obj chatObj = result.at(uri(CHAT));
        final String text = chatObj.isStr() ? chatObj.strValue() : "";
        final Set<fURI> newConcepts = this.processConcepts(agent, text, false);

        // Extract <<concept:>> tags from the agent's thinking block — never automatic
        // TF-IDF; only explicit annotations count, so reasoning can bookmark semantic
        // insights without surfacing them in the final response.
        final Obj thinking = result.watermark("thinking");
        if (!thinking.isNoObj() && !thinking.strValue().isBlank()) {
            final Set<String> thoughtConcepts = extractTags(thinking.strValue());
            if (!thoughtConcepts.isEmpty())
                this.addConceptsToSpace(agent, thoughtConcepts);
        }
        if (!newConcepts.isEmpty()) {
            docWrap(virtual(instLambda((a, b) -> {
                this.injectConceptRecommendations(agent, newConcepts);
                return noobj();
            })), "updating concept graph").apply();
        }
    }
}
