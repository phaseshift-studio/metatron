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
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.agent;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread.virtual;
import static studio.phaseshift.metatron.util.CommonUtil.mutableList;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class ConceptFeature extends Feature {

    private static final String CONCEPT = "concept";
    private static final String LINK = "link";
    private static final Pattern CONCEPT_PATTERN = Pattern.compile("<<concept:([^>]+)>>");

    private final Set<String> concepts = new ConcurrentSkipListSet<>();
    private Agent translatorAgent = null;

    private static final
    String CONCEPT_FEATURE_INSTRUCTIONS_TEMPLATE = """
                                          In any of your responses, you can tag important concepts using a <<concept:>>-block:
                                          For instance, an agent may write:
                                          
                                          "Increasing the size of the <<concept:context windows>> is one way to increase an agent's
                                          <<concept:intelligence>>. However, another way is to provide better <<concept:indexing>> and
                                          <<concept:searching>> capabilities for existing <<concept:memory systems>>."
                                          
                                          Behind the scenes, these tags will form a growing co-location graph that will allow
                                          for the automatic insertion of relevant historic memories the agent can choose
                                          to review. For instance, given the above, the next system message may write:
                                          
                                          use the mtron eval tool for related historic content:
                                         
                                              %s
                                          """;
    private String CONCEPT_FEATURE_INSTRUCTION = "";


    public ConceptFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }


    public Obj skill() {
        return rec(uri(NAME), uri(CONCEPT),
                uri(DESC), str("In situ concept graph construction w/ spreading activation recommendation"),
                uri(CONTENT), str(CONCEPT_FEATURE_INSTRUCTION));
    }

    private fURI getBaseURI() {
        return this.at(BASE).uriValue();
    }

    private List<fURI> insertConcepts(final Agent agent) {
        final List<fURI> concepts = new ArrayList<>();
        try {
            LOG.info("concepts to process: %s", this.concepts);
            for (final String concept : this.concepts) {
                final fURI conceptURI = this.getBaseURI().extend(concept);
                final Rec conceptRec = Router.readFromSpace(conceptURI).orElse(rec());
                final Lst conceptLink = conceptRec.at(LINK).orElse(Router.readFromSpace(conceptURI.extend(LINK)).orElse(lst()));
                final List<Obj> conceptLinkList = mutableList(conceptLink.jvm());
                conceptLinkList.addAll(this.concepts.stream()
                        .filter(c -> !c.equals(concept))
                        .map(c -> auto_from_(this.getBaseURI().extend(c)).tryToInst()).toList());
                conceptRec.at(LINK, lst(conceptLinkList), MUTABLE);
                if (!conceptRec.has(CONCEPT))
                    conceptRec.at(CONCEPT, uri(concept), MUTABLE);
                if (!agent.feature(SESSION).isNoObj()) {
                    final SessionFeature sessionFeature = (SessionFeature) agent.feature(SESSION);
                    LOG.info("concept feature has located session feature");
                    if (null != sessionFeature.store() && null != sessionFeature.store().getCurrentMessages()) {
                        LOG.info("concept feature preparing to read from session memory: [size:%d]", sessionFeature.store().getCurrentMessages().size());
                        final Set<fURI> messagesIDs = sessionFeature.store().getCurrentMessages();
                        LOG.info("concept feature located %s ai messages", messagesIDs);
                        LOG.info("messages linked: %s", messagesIDs.stream().filter(i -> !Objects.isNull(i)).map(id -> auto_from_(id).tryToInst()).collect(new CommonUtil.LstCollector()));
                        conceptRec.at(MESSAGE, messagesIDs.stream().filter(i -> !Objects.isNull(i)).map(id -> auto_from_(id).tryToInst()).collect(new CommonUtil.LstCollector()), MUTABLE);
                        LOG.info("new concept rec: %s", conceptRec);
                    }
                }
                Router.writeToSpace(conceptURI, conceptRec);
                concepts.add(conceptURI);
                LOG.info("extracted concept: %s", conceptURI);
            }
        } catch (final Exception e) {
            LOG.error(e);
        }
        this.concepts.clear();
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
                                                                 
                                                                 The text to rewrite is:
                                                                 
                                                                 """ + text.strValue());
                    LOG.info("agent translation: %s", result);
                    final Matcher matcher = CONCEPT_PATTERN.matcher(Str.Helper.cleanString(result));
                    while (matcher.find()) {
                        final String concept = CommonUtil.normalize(matcher.group(1));
                        this.concepts.add(concept);
                        LOG.info("%s extracted", concept);
                    }
                    final List<fURI> concepts = this.insertConcepts(agent);
                    final StringBuilder sb = new StringBuilder();
                     concepts.stream().map(i -> "\t*<" + i + ">.>>message.>>").forEach(i -> {
                        sb.append(i).append("\n");
                    });
                    CONCEPT_FEATURE_INSTRUCTION = CONCEPT_FEATURE_INSTRUCTIONS_TEMPLATE.formatted(sb.toString());
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
            final Matcher matcher = CONCEPT_PATTERN.matcher(text.strValue());
            while (matcher.find()) {
                final String concept = CommonUtil.normalize(matcher.group(1));
                this.concepts.add(concept);
                LOG.info("%s extracted", concept);
            }
            this.insertConcepts(agent);
        }
    }

    // ── Streaming (observation) ──────────────────────────────────

    @Override
    public Obj onBeforeChat(final Agent agent) {
        if (!this.at(MODEL).isNoObj()) {
            this.translatorAgent = agent(rec(uri(FEATURE), lst(new ChatFeature(mutableMap(uri(MODEL), this.at(MODEL), uri(RESPONSE), rec(uri(TO), id_().tryToInst())), LLM_CHAT_FEATURE_TID, null))));
            LOG.info("created translator agent: %s", this.translatorAgent);
            this.addConcepts(agent, str(agent.userMessage()), true);
            LOG.info("processed user message into: " + this.concepts);
        }
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
