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
import studio.phaseshift.metatron.isa.llm.type.ChatResult;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.thread.CoreThread;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SUMMARIZE_FEATURE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.summarizeSession;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst0;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SummarizeFeature extends AbstractFeature {

    /**
     * The background distill currently running, if any.  Queued in
     * {@link #onCompleteResponse} when the agent appends a
     * {@code <<mtron:summarize>>} block; cleared in {@link #onBeforeChat}
     * once the task completes and the recall briefing is injected.
     */
    final AtomicReference<FutureObj<Obj>> summaryTask = new AtomicReference<>();

    public SummarizeFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Lst skill(final Agent agent) {
        return lst(rec(mutableMap(uri(NAME), uri(LLM_SUMMARIZE_FEATURE_TID.name()),
                uri(DESC), str("summarize a session into claims and loose ends, recalling them on demand"),
                uri(CONTENT), str("""
                                  When a session becomes overly complex or you need to recall decisions, problems, and observations
                                  from your past, append a `<<mtron:summarize>>` block to your response — a deferred `summary()`
                                  call, where the block rec is the same argument rec `summary()` takes:
                                  
                                      <<mtron:summarize>>
                                      [scope=>day::2.0, kind=>[problem, decision], concept=>["AgentExtractor"]]
                                      <</mtron:summarize>>
                                  
                                  The summarization runs in the background — acknowledge that recall is queued and respond normally.
                                  On the next chat, a structured briefing is injected into your system context:
                                  
                                      [claim=>[[text=>"a claim",location=>!*/usr/dr/claim/3],...],
                                       loose_end=>[[text=>"an open thread",location=>!*/usr/dr/loose_end/1],...]]
                                  
                                  The `location` fields are mtron deref pointers — follow them (e.g. `*<location>` or
                                  `*<location>/source`) to dig into the underlying records.
                                  
                                  Config (all optional):
                                    scope     — only summarize messages since this time (a time::T like hour::48.0 or day::2.0,
                                                or an absolute datetime::T). Default: all messages.
                                    kind      — focus the follow-on briefing on claims of this kind (decision, problem,
                                                solution, observation). Default: all kinds.
                                    concept   — focus the follow-on briefing on claims whose source messages touch this
                                                concept. Default: no concept filter.
                                  
                                  **IMPORTANT**: This skill is about formatting your response, not calling a function. The block
                                  is stripped from what the user sees.
                                  """))));
    }

    @Override
    public void onCompleteResponse(final Agent agent, final ChatResult result) {
        final Obj blocks = result.at(uri("blocks")).orElse(noobj());
        if (blocks.isNoObj())
            return;
        final Obj signal = blocks.asRec().at(uri("summarize"));
        if (signal.isNoObj())
            return;
        final Rec block = signal.asRec();
        if (!agent.hasFeature(SESSION)) {
            LOG.warn("summarize requires the session feature");
            return;
        }
        final fURI sessionVID = agent.feature(SESSION).asRec().at(SESSION).uriValue();
        if (null == sessionVID || sessionVID.isEmpty()) {
            LOG.warn("summarize requires an anchored session");
            return;
        }
        final fURI agentHome = agent.at(ROOT).uriValue();
        // the block rec is the summary() argument rec — the agent is lazily
        // calling summary() by appending <<mtron:summarize>>; feature defaults
        // fill the keys the block left noobj
        final Rec config = this.resolveConfig(agent, block);
        // queue the distill on a background thread so this turn completes immediately
        final fURI home = agentHome;
        final fURI sess = sessionVID;
        final CoreThread thread = CoreThread.core(instLambda((lhs, inst) -> {
            try {
                final Obj applied = summarizeSession(home, sess, config);
                if (applied.isFail())
                    LOG.warn("summarize failed: %s", Str.Helper.cleanString(applied));
                return applied;
            } catch (final Exception e) {
                LOG.error("summarize failed: %s", e.getMessage());
                return noobj();
            }
        }));
        this.summaryTask.set(thread.applyAsync());
        LOG.info("summarize queued for session %s", sess);
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        final fURI outputBase = this.outputBase(agent);
        // always-on loose-end reminder
        final Obj looseEnds = Router.readFromSpace(outputBase.extend("loose_end").extend("+"));
        if (!looseEnds.isNoObj() && agent.hasFeature(SYSTEM)) {
            agent.feature(SYSTEM).<SystemFeature>as().addSystemMessage("""
                                                                       An analysis of the last summarization identified the following loose ends:
                                                                       
                                                                       %s
                                                                       """.formatted(String.join("\n", looseEnds.stream().map(Str.Helper::cleanString).toList())));
        }
        // gated recall briefing — once the queued summarization has completed,
        // the applied-constraints rec returned by summary() drives the briefing
        final FutureObj<Obj> task = this.summaryTask.get();
        if (task != null && task.isDone()) {
            try {
                final Obj applied = task.get();
                if (!applied.isNoObj() && !applied.isFail() && agent.hasFeature(SYSTEM)) {
                    final Obj briefing = this.buildBriefing(agent, applied.asRec());
                    if (!briefing.isNoObj())
                        agent.feature(SYSTEM).<SystemFeature>as().addSystemMessage(ObjmtronSerializer.compact().write(briefing));
                }
            } catch (final Exception e) {
                LOG.warn("summarize briefing unavailable: %s", e.getMessage());
            }
            this.summaryTask.set(null);
        }
        return noobj();
    }

    /**
     * The argument rec for summary() — the block rec (scope/kinds/concepts)
     * merged over the feature's defaults, plus the output base.  The
     * non-overlapping summary() keys (session, model) are resolved by the
     * caller and helper, so the block is exactly a deferred summary() call.
     */
    Rec resolveConfig(final Agent agent, final Rec block) {
        return rec(uri(SCOPE), block.at(uri(SCOPE)).orElse(this.at(uri(SCOPE))),
                uri(KIND), block.at(uri(KIND)).orElse(this.at(uri(KIND))),
                uri(CONCEPT), block.at(uri(CONCEPT)).orElse(this.at(uri(CONCEPT))),
                uri(TO), uri(this.outputBase(agent)));
    }

    /**
     * The base under which claim/ and loose_end/ are anchored — the feature's
     * {@code root} config when present, else the agent home.
     */
    private fURI outputBase(final Agent agent) {
        final Obj root = this.at(ROOT);
        return root.isNoObj() ? agent.at(ROOT).uriValue() : root.uriValue();
    }

    /**
     * Build the recall briefing for a completed summarization:
     * {@code [claim=>[{text,location},...], loose_end=>[{text,location},...]]}.
     * Claims are filtered by the wanted {@code kinds}; when {@code concepts}
     * are given, only claims whose source messages touch those concepts (the
     * concept root's {@code message} back-refs, intersected with the claim
     * sources) are included.  The {@code location} fields are {@code !*} deref
     * pointers to the underlying recs.
     */
    Obj buildBriefing(final Agent agent, final Rec applied) {
        final Obj out = applied.at(uri(TO));
        final fURI outputBase = out.isNoObj() ? this.outputBase(agent) : out.uriValue();
        final Lst kinds = applied.at(uri(KIND)).orElse(lst0()).asLst();
        final Lst concepts = applied.at(uri(CONCEPT)).orElse(lst0()).asLst();
        // concept → message uris (ConceptFeature root; each concept rec's
        // message field holds !* refs to the ledger messages that used it)
        final Set<fURI> conceptMessages = new HashSet<>();
        if (!concepts.isEmpty() && agent.hasFeature(CONCEPT)) {
            final fURI conceptRoot = agent.feature(CONCEPT).asRec().at(ROOT).uriValue();
            for (final Obj concept : concepts.elements().toList()) {
                final fURI conceptURI = concept.isUri()
                        ? concept.uriValue()
                        : conceptRoot.extend(Str.Helper.cleanString(concept));
                final Obj cRec = Router.readFromSpace(conceptURI).orElse(noobj());
                if (cRec.isNoObj()) {
                    LOG.warn("summarize briefing: no concept rec at %s", conceptURI);
                    continue;
                }
                final Lst msgs = cRec.asRec().at(uri(MESSAGE)).orElse(lst0());
                msgs.elements().forEach(ref -> {
                    if (ref.isInst())
                        conceptMessages.add(ref.asInst().arg(0).uriValue());
                });
            }
        }
        // claims of the wanted kinds (and concept-relevant sources, when concepts given)
        final List<Obj> claimEntries = new ArrayList<>();
        for (final Rel rel : Router.readFromSpace(outputBase.extend("claim").extend("+/")).stream().map(Obj::asRel).toList()) {
            final fURI claimVid = rel.first().uriValue();
            final Rec claimRec = rel.second().asRec();
            final Obj kind = claimRec.at(uri(KIND));
            if (!kinds.isEmpty() && !kinds.elements().anyMatch(k -> Str.Helper.cleanString(k).equals(Str.Helper.cleanString(kind))))
                continue;
            if (!concepts.isEmpty() && !hasConceptSource(claimRec, conceptMessages))
                continue;
            claimEntries.add(rec(uri(TEXT), claimRec.at(uri(TEXT)).orElse(str("")),
                    uri(LOCATION), auto_from_(claimVid).tryToInst()));
        }
        // loose ends
        final List<Obj> looseEndEntries = new ArrayList<>();
        for (final Rel rel : Router.readFromSpace(outputBase.extend("loose_end").extend("+/")).stream().map(Obj::asRel).toList()) {
            final Rec leRec = rel.second().asRec();
            looseEndEntries.add(rec(uri(TEXT), leRec.at(uri(TITLE)).orElse(leRec.at(uri(DESC)).orElse(str(""))),
                    uri(LOCATION), auto_from_(rel.first().uriValue()).tryToInst()));
        }
        if (claimEntries.isEmpty() && looseEndEntries.isEmpty())
            return noobj();
        return rec(uri("claim"), lst(claimEntries), uri("loose_end"), lst(looseEndEntries));
    }

    private static boolean hasConceptSource(final Rec claimRec, final Set<fURI> conceptMessages) {
        final Lst source = claimRec.at(uri(SOURCE)).orElse(lst0());
        return source.elements().anyMatch(ref -> ref.isInst() && conceptMessages.contains(ref.asInst().arg(0).uriValue()));
    }
}
