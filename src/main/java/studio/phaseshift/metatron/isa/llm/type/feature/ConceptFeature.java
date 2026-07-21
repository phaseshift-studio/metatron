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
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.Tokens.*;
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
    private static final Pattern CONCEPT_PATTERN = Pattern.compile("<<concept:([^>]+)>>");
    private Agent translatorAgent = null;

    private static final
    String CONCEPT_FEATURE_AGENT_TEMPLATE = """
                                            In any of your responses, you can tag important concepts using a <<concept:>>-block:
                                            For instance, an agent may write:
                                            
                                            "Increasing the size of the <<concept:context windows>> is one way to increase an agent's
                                            <<concept:intelligence>>. However, another way is to provide better <<concept:indexing>> and
                                            <<concept:searching>> capabilities for existing <<concept:memory systems>>."
                                            
                                            Behind the scenes, these tags will form a growing co-location graph that will allow
                                            for the automatic insertion of relevant historic memories the agent can choose
                                            to review. For instance, given the above, the next system message may write:
                                            """;
    String CONCEPT_FEATURE_SYSTEM_TEMPLATE = """
                                             use the mtron eval tool for related historic content:
                                             
                                                 %s
                                             
                                             For related concepts use:
                                             
                                                %s
                                             """;

    public ConceptFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }


    public Obj skill() {
        if (!this.has(MODEL)) {
            return rec(uri(NAME), uri(CONCEPT),
                    uri(DESC), str("In situ concept graph construction w/ spreading activation recommendation"),
                    uri(CONTENT), str(CONCEPT_FEATURE_AGENT_TEMPLATE));
        } else
            return noobj();
    }

    private fURI getBaseURI() {
        return this.at(BASE).uriValue();
    }

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

    private void addConcepts(final Agent agent, final Str text, final boolean blocking) {
        if (null != this.translatorAgent) {
            try {
                LOG.info("using agent to extract concepts from text: %s", this.translatorAgent);
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
                                                                 
                                                                 """ + text.strValue());
                    LOG.debug("agent translation: %s", result);
                    final Set<String> conceptStrings = new LinkedHashSet<>();
                    final Matcher matcher = CONCEPT_PATTERN.matcher(Str.Helper.cleanString(result));
                    while (matcher.find()) {
                        final String concept = CommonUtil.stripStopwords(CommonUtil.normalize(matcher.group(1)));
                        if (!concept.isEmpty()) {
                            if (conceptStrings.add(concept))
                                LOG.info("%s extracted", concept);
                        }
                    }
                    final List<fURI> concepts = this.insertConcepts(agent, conceptStrings);
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
                    return noobj();
                }));
                if (blocking) {
                    thread.applyAsync().get();
                } else {
                    thread.applyAsync();
                }
            } catch (final Exception e) {
                LOG.error(e);
            }
        } else {
            final Set<String> conceptStrings = new LinkedHashSet<>();
            final Matcher matcher = CONCEPT_PATTERN.matcher(text.strValue());
            while (matcher.find()) {
                final String concept = CommonUtil.normalize(matcher.group(1));
                if (conceptStrings.add(concept))
                    LOG.info("%s extracted", concept);
            }
            this.insertConcepts(agent, conceptStrings);
        }
    }

    // ── Streaming (observation) ──────────────────────────────────

    @Override
    public Obj onBeforeChat(final Agent agent) {
        if (this.has(MODEL)) {
            this.translatorAgent = agent(rec(uri(FEATURE), lst(new ChatFeature(mutableMap(uri(MODEL), this.at(MODEL), uri(RESPONSE), rec(uri(TO), id_().tryToInst())), LLM_CHAT_FEATURE_TID, null))));
            LOG.debug("created translator agent: %s", this.translatorAgent);
            this.addConcepts(agent, str(agent.userMessage()), true);
        }
        /*final Inst fetchMemoriesInst = instC(getBaseURI().extend("fetch_memories").dom(ALL.maybe()).rng(ALL.maybeSome()),
                rec(uri(CONCEPT), URI_TYPE,
                        uri("max_messages"), isa_(INT_TYPE).else_(jnt(10))),
                (lhs, inst) -> objs(from_(inst.arg(0)).rshift_(uri(MESSAGE)).rshift_(uri("+")).rshift_(uri(TEXT)).take_(inst.arg(1)).apply()));
        final Inst relatedConceptsInst = instC(getBaseURI().extend("related_concepts").dom(ALL.maybe()).rng(ALL.maybeSome()),
                rec(uri(CONCEPT), URI_TYPE),
                (lhs, inst) -> objs(from_(inst.arg(0)).rshift_(uri(LINK)).apply()));
        LOG.info("writing concept instructions:\n\t%s\n\t%s", fetchMemoriesInst, relatedConceptsInst);
        Router.writeToSpace(this.getBaseURI().extend("fetch_memories"), fetchMemoriesInst);
        Router.writeToSpace(this.getBaseURI().extend("related_concepts"), relatedConceptsInst);*/
        return noobj();
    }

    @Override
    public void onPartialThinking(final Agent agent, final Str text) {
        //this.addConcepts(text);
    }


    @Override
    public void onPartialResponse(final Agent agent, final Str text) {
        //   this.addConcepts(text);
    }

    @Override
    public void onCompleteResponse(final Agent agent, final Str text) {
        this.addConcepts(agent, text, false);
    }

    /*
dog -> [concept   => dog,
        message   => {},
        colocated => {!*cat,!*bird,!*food}]
 */


}
