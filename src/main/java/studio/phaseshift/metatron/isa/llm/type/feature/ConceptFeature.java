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
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.AI_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.USER_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class ConceptFeature extends Feature {

    private static final String CONCEPT = "concept";
    private static final String LINK = "link";
    private static final Pattern CONCEPT_PATTERN = Pattern.compile("<<concept:([^>]+)>>");

    private final Set<String> concepts = new HashSet<>();

    private static final
    String CONCEPT_FEATURE_INSTRUCTIONS = """
                                          In any of your responses, you can tag important concepts using a <<concept:>>-block:
                                          For instance, an agent may write:
                                          
                                          "Increasing the size of the <<concept:context windows>> is one way to increase an agent's
                                          <<concept:intelligence>>. However, another way is to provide better <<concept:indexing>> and
                                          <<concept:searching>> capabilities for existing <<concept:memory systems>>."
                                          
                                          Behind the scenes, these tags will form a growing co-location graph that will allow
                                          for the automatic insertion of relevant historic memories the agent can choose
                                          to review. For instance, given the above, the next system message may write:
                                          
                                          <<concept>>
                                          use the mtron eval tool for related historic content:
                                          
                                              [drdb::llm_message_user/35
                                               drdb::llm_message_user/32
                                               drdb::llm_message_user/18]>-.*(_)>>text
                                          <</concept>>
                                          """;


    public ConceptFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }


    public Obj skill() {
        return rec(uri(NAME), uri(CONCEPT),
                uri(DESC), str("In situ concept graph construction w/ spreading activation recommendation"),
                uri(CONTENT), str(CONCEPT_FEATURE_INSTRUCTIONS));
    }

    private fURI getBaseURI() {
        return this.at(BASE).uriValue();
    }

    private void addConcepts(final Str text) {
        final Matcher matcher = CONCEPT_PATTERN.matcher(text.strValue());
        while (matcher.find()) {
            final String concept = CommonUtil.normalize(matcher.group(1));
            this.concepts.add(concept);
            LOG.info("%s extracted\n", concept);
        }
    }

    // ── Streaming (observation) ──────────────────────────────────
    @Override
    public void onPartialThinking(final Agent agent, final Str text) {
        this.addConcepts(text);
    }


    @Override
    public void onPartialResponse(final Agent agent, final Str text) {
        this.addConcepts(text);
    }

    @Override
    public void onCompleteResponse(final Agent agent, final Str text) {
        this.addConcepts(text);
        LOG.none("\n");
        for (final String concept : this.concepts) {
            final fURI conceptURI = this.getBaseURI().extend(concept);
            final Rec conceptRec = Router.readFromSpace(conceptURI).orElse(rec());
            final Lst conceptLink = conceptRec.at(LINK).orElse(Router.readFromSpace(conceptURI.extend(LINK)).orElse(lst()));
            final List<Obj> conceptLinkList = conceptLink.jvm();
            conceptLinkList.addAll(this.concepts.stream()
                    .filter(c -> !c.equals(concept))
                    .map(c -> auto_from_(this.getBaseURI().extend(c)).tryToInst()).toList());
            conceptRec.at(LINK, conceptLink, MUTABLE);
            if (!conceptRec.has(CONCEPT))
                conceptRec.at(CONCEPT, uri(concept), MUTABLE);
            if (!agent.feature(SESSION).isNoObj()) {
                final SessionFeature sessionFeature = (SessionFeature) agent.feature(SESSION);
                LOG.info("concept feature has located session feature");
                if (null != sessionFeature.store() && null != sessionFeature.store().getCurrentMessages()) {
                    LOG.info("concept feature preparing to read from session memory: [size:%d]", sessionFeature.store().getCurrentMessages().size());
                    final Set<fURI> messagesIDs = sessionFeature.store().getCurrentMessages().getOrDefault(AI_MESSAGE_TID, Collections.emptySet());
                    LOG.info("concept feature located %d ai messages", messagesIDs.size());
                    conceptRec.at(MESSAGE, lst((List) messagesIDs.stream().map(id -> auto_from_(id).tryToInst()).toList()), MUTABLE);
                }
            }
            Router.writeToSpace(conceptURI, conceptRec);
            LOG.info("extracted concept: %s", conceptURI);
        }
        this.concepts.clear();
    }

    /*
dog -> [concept   => dog,
        message   => {},
        colocated => {!*cat,!*bird,!*food}]
 */


}
