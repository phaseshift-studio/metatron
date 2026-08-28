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
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatResult;
import studio.phaseshift.metatron.isa.llm.type.Model;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCQ;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.nowDatetime;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst0;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SummarizeFeature extends AbstractFeature {

    public static final fURI SUMMARIZE_INST_TID = LLM_SUMMARIZE_FEATURE_TID.extend(INST).extend("summarize");

    public SummarizeFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Lst skill(final Agent agent) {
        final Lst skills = lst(rec(mutableMap(uri(NAME), uri(LLM_SUMMARIZE_FEATURE_TID.name()),
                uri(DESC), str("summarize a session by generating high-level over-arching constructs"),
                uri(CONTENT), str("""
                                  When a session becomes overly complex, use the summarization tool to have the session analyzed.
                                  
                                    %s()
                                  
                                  The generate a collection of claim::T and loose_end::T uris can be accessed using the m_inst_eval tool:
                                  
                                    *<summarization_uri>
                                  
                                  The claim::T and loose_end::T values contain high-level descriptions of long running session themes as well as references to particular
                                  messages supporting derived claims and loose ends.
                                  """.formatted(SUMMARIZE_INST_TID)),
                uri(TOOL), lst(
                        docWrap(instC(SUMMARIZE_INST_TID.dom(ALL.maybe()).rng(LST_TID), lst(),
                                        (lhs, inst) -> {
                                            // The session may arrive as the lhs (fluent: @dr/session/1.summarize(_))
                                            // or as arg 0 (function form: summarize(@dr/session/1)).
                                            final Rec session = agent.feature(SESSION).asRec();
                                            final Obj modelArg = noobj(); //agent.feature(CHAT).<ChatFeature>as().at(MODEL);
                                            final fURI sessionVID = session.at(SESSION).uriValue();
                                            final fURI agentHome = agent.at(ROOT).uriValue();
                                            if (null == sessionVID || sessionVID.isEmpty())
                                                return fail("summarize requires an anchored session — use @dr/session/N.summarize()");
                                            // 1. collect this session's messages from the ledger as rels
                                            //    (vid => rec) — the rel key IS the message vid (branch read)
                                            final List<Rel> messages = Router.readFromSpace(agentHome.extend(MESSAGE).extend("+/"))
                                                    .stream()
                                                    .map(Obj::asRel)
                                                    .filter(pair -> {
                                                        final Obj sess = pair.second().asRec().at(uri(SESSION)).orElse(noobj());
                                                        return sess.isUri() && sess.uriValue().equals(sessionVID);
                                                    })
                                                    .sorted(Comparator.comparing(pair -> pair.first().uriValue().name()))
                                                    .toList();
                                            if (messages.isEmpty())
                                                return fail("no messages found for session %s", sessionVID);
                                            // 2. build the distill digest
                                            final String digest = messages.stream()
                                                    .map(pair -> Str.Helper.cleanString(pair.second().asRec().at(TEXT).orElse(str(""))))
                                                    .filter(s -> !s.isBlank())
                                                    .collect(Collectors.joining("\n"));
                                            // 3. the model — from the agent home (matches <agent>/model)
                                            final Model model = modelArg.isNoObj() ? Model.model(Router.readFromSpace(agentHome.extend(MODEL)).asRec()) : Model.model(modelArg.asRec());
                                            LOG.debug("summarize model: %s", model);
                                            // 4. distill via a mini-task
                                            final ChatResult result = Agent.Helper.miniTask("session_summarizer", model, SUMMARIZE_PROMPT.formatted(digest));
                                            LOG.debug("summarize result: %s", result);
                                            // 5. parse the <<json:claim>> and <<json:loose_end>> blocks into vids
                                            final List<Obj> resultVids = new ArrayList<>();
                                            final List<Obj> claimVids = new ArrayList<>();
                                            final Obj blocks = result.at(uri("blocks")).orElse(noobj());
                                            LOG.debug("summarize blocks: %s", blocks);
                                            if (!blocks.isNoObj()) {
                                                final Rec blocksRec = blocks.asRec();
                                                int claimIndex = 0;
                                                int looseEndIndex = 0;
                                                for (final Rel entry : blocksRec.elements().toList()) {
                                                    final String keyStr = Str.Helper.cleanString(entry.first());
                                                    final Obj body = entry.second();
                                                    final Lst bodyLst = body.isLst() ? body.asLst() : lst(body);
                                                    for (final Obj bodyObj : bodyLst.elements().toList()) {
                                                        Rec rec = bodyObj.asRec();
                                                        if (keyStr.equals("claim")) {
                                                            // JSON parses kind as a string ("observation") — coerce to a uri
                                                            // as claim::T expects (kind => union of uris)
                                                            final Obj kind = rec.at(uri(KIND));
                                                            if (kind.isStr())
                                                                rec.at(uri(KIND), uri(kind.strValue()), MUTABLE);
                                                            // source: lst of !* auto_from refs to the message vids — the same
                                                            // storage form concept uses for its {uri} collections (tble
                                                            // round-trips lst fine; objs/coefficient collections do not)
                                                            rec.at(uri(SOURCE), lst(messages.stream()
                                                                    .map(pair -> (Obj) auto_from_(pair.first().uriValue()).tryToInst())
                                                                    .toList()), MUTABLE);
                                                            rec = rec.tid(LLM_CLAIM_TID);
                                                            final fURI vid = agentHome.extend("claim").extend(String.valueOf(claimIndex++));
                                                            rec = rec.selfVID(vid);
                                                            Router.writeToSpace(vid, rec);
                                                            resultVids.add(uri(vid));
                                                            claimVids.add(uri(vid));
                                                        } else if (keyStr.equals("loose_end")) {
                                                            // JSON parses status as a string ("open") — coerce to a uri
                                                            // as loose_end::T expects (status => union of uris)
                                                            final Obj status = rec.at(uri(STATUS));
                                                            if (status.isStr())
                                                                rec.at(uri(STATUS), uri(status.strValue()), MUTABLE);
                                                            // source: lst of !* auto_from refs to the message vids — same as claims
                                                            rec.at(uri(SOURCE), lst(messages.stream()
                                                                    .map(pair -> (Obj) auto_from_(pair.first().uriValue()).tryToInst())
                                                                    .toList()), MUTABLE);
                                                            // claim: !* auto_from refs to the claims distilled in this same
                                                            // pass — the loose end's justifying propositions
                                                            if (!claimVids.isEmpty())
                                                                rec.at(uri("claim"), lst(claimVids.stream()
                                                                        .map(v -> (Obj) auto_from_(v.uriValue()).tryToInst())
                                                                        .toList()), MUTABLE);
                                                            // time is stamped by the inst, not the model
                                                            rec.at(uri(TIME), nowDatetime(), MUTABLE);
                                                            rec = rec.tid(LLM_LOOSE_END_TID);
                                                            final fURI vid = agentHome.extend("loose_end").extend(String.valueOf(looseEndIndex++));
                                                            rec = rec.selfVID(vid);
                                                            Router.writeToSpace(vid, rec);
                                                            resultVids.add(uri(vid));
                                                        }
                                                    }
                                                }
                                            }
                                            return lst(resultVids);
                                        }),
                                "maybe an obj",
                                "a lst of claim and loose_end vids distilled from the session",
                                mutableMap(),
                                "distill a session's message ledger into claim::T and loose_end::T recs via a mini-task; the model emits <<json:claim>> and <<json:loose_end>> blocks that are parsed and anchored at <agent>/claim/ and <agent>/loose_end/")))));
        skills.elements().map(s -> s.asRec().at(TOOL).orElse(lst0())).flatMap(t -> t.asLst().elements()).forEach(t -> {
            Router.readFromSpace(((Obj) t).tid().addQ(DOCQ)).stream().forEach(doc -> {
                agent.addTool((QCollection.Docs) doc);
            });
        });
        return skills;
    }

    @Override
    public Obj onBeforeChat(Agent agent) {
        final Obj looseEnds = Router.readFromSpace(agent.at(ROOT).uriValue().extend("loose_end").extend("+"));
        if (!looseEnds.isNoObj() && agent.hasFeature(SYSTEM)) {
            agent.feature(SYSTEM).<SystemFeature>as().addSystemMessage("""
                                                                       An analysis of the last summarization identified the following loose ends:
                                                                       
                                                                       %s
                                                                       """.formatted(String.join("\n", looseEnds.stream().map(Str.Helper::cleanString).toList())));
        }
        return noobj();
    }
}
