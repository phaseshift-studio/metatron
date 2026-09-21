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
import studio.phaseshift.metatron.isa.llm.WatermarkUtil;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatFrame;
import studio.phaseshift.metatron.isa.llm.type.feature.service.ConceptService;
import studio.phaseshift.metatron.isa.llm.type.feature.service.MessageService;
import studio.phaseshift.metatron.isa.llm.type.feature.service.SkillService;
import studio.phaseshift.metatron.isa.llm.type.feature.service.SystemService;
import studio.phaseshift.metatron.isa.llm.type.mModel;
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.thread.CoreThread;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.llm.type.mModel.model;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst0;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SummarizeFeature extends AbstractFeature {
    public static final fURI FEATURE_TID = studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SUMMARIZE_FEATURE_TID;


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
    public Set<fURI> requires() {
        return Set.of(LLM_SKILL_SERVICE_TID);
    }

    @Override
    public Set<fURI> uses() {
        // concept enrichment is opportunistic — the briefing still works without it
        return Set.of(LLM_CONCEPT_SERVICE_TID);
    }

    /**
     * Register this feature's skill with the SkillFeature gateway — the
     * gateway is the owner of the skill channel; this feature is a
     * contributor.
     */
    /**
     * This feature's watermark identity.  Declared on the config rec as
     * {@code watermark => [key=>..., tag=>...]} to override either; these are the
     * defaults, and the single place the server-side lookup and the skill prose
     * both take their marker from.
     */
    static final String WATERMARK_KEY = "summarize";
    static final String WATERMARK_CODEC = "mtron";

    private static final String SUMMARIZE_INSTRUCTIONS =
            """
            ---[summarize_feature]---
            when a session becomes overly complex or you need to recall decisions, problems, and observations
            from your past, append a `<<mtron:summarize>>` watermark to your response — a deferred `summary()`
            call, where the watermark body is the same argument rec `summary()` takes:
            
                <<mtron:summarize>>
                [scope=>day::2.0, kind=>[problem, decision], concept=>["AgentExtractor"]]
                <</mtron:summarize>>
            
            the summarization runs in the background — acknowledge that recall is queued and respond normally.
            On the next chat, a structured briefing is injected into your system context:
            
                [claim=>[[text=>"a claim",location=>!*/usr/dr/claim/3],...],
                 loose_end=>[[text=>"an open thread",location=>!*/usr/dr/loose_end/1],...]]
            
            the `location` fields are mtron deref pointers — follow them (e.g. `*<location>` or
            `*<location>/source`) to dig into the underlying records.
            
            config (all optional):
              scope     — only summarize messages since this time (a time::T like hour::48.0 or day::2.0,
                          or an absolute datetime::T). Default: all messages.
              kind      — focus the follow-on briefing on claims of this kind (decision, problem,
                          solution, observation). Default: all kinds.
              concept   — focus the follow-on briefing on claims whose source messages touch this
                          concept. Default: no concept filter.
            """;

    public void registerSkill(final Agent agent) {
        if (!agent.hasFeature(LLM_SKILL_FEATURE_TID))
            return;
        final String instructions = WatermarkUtil.instructions(WATERMARK_CODEC,
                WatermarkUtil.key(this, WATERMARK_KEY), SUMMARIZE_INSTRUCTIONS);
        agent.requireService(SkillService.class).addSkill(mSkill.of(rec(mutableMap(
                uri(NAME), uri(LLM_SUMMARIZE_FEATURE_TID.name()),
                uri(DESC), str("summarize a session into claims and loose ends, recalling them on demand"),
                uri(CONTENT), str(instructions)))));
    }

    @Override
    public void onCompleteResponse(final Agent agent, final ChatFrame result) {
        this.noteWatermarkFailure(result, WATERMARK_CODEC, WATERMARK_KEY);
        final Obj signal = result.watermark(WatermarkUtil.key(this, WATERMARK_KEY));
        if (signal.isNoObj())
            return;
        final Rec block = signal.asRec();
        if (agent.service(MessageService.class).isEmpty()) {
            LOG.warn("summarize requires the session feature");
            return;
        }
        final fURI sessionVID = agent.service(MessageService.class).map(MessageService::sessionVID).orElse(null);
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
        final CoreThread thread = CoreThread.core(instLambda((lhs, inst) -> {
            try {
                final Obj applied = summarizeSession(agent, config);
                if (applied.isFail())
                    LOG.warn("summarize failed: %s", Str.Helper.cleanString(applied));
                return applied;
            } catch (final Exception e) {
                LOG.error("summarize failed: %s", e.getMessage());
                return noobj();
            }
        }));
        this.summaryTask.set(thread.applyAsync());
        LOG.info("summarize queued for session %s", sessionVID);
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        this.registerSkill(agent);
        this.surfaceWatermarkRejections(agent);
        final fURI outputBase = this.outputBase(agent);
        // always-on loose-end reminder
        final Obj looseEnds = Router.readFromSpace(outputBase.extend("loose_end").extend("+"));
        if (!looseEnds.isNoObj() && agent.hasFeature(LLM_SYSTEM_FEATURE_TID)) {
            agent.requireService(SystemService.class).addSystemMessage("""
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
                if (!applied.isNoObj() && !applied.isFail() && agent.hasFeature(LLM_SYSTEM_FEATURE_TID)) {
                    final Obj briefing = this.buildBriefing(agent, applied.asRec());
                    if (!briefing.isNoObj())
                        agent.requireService(SystemService.class).addSystemMessage(ObjmtronSerializer.compact().write(briefing));
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
        // concept → message uris (the concept service's root; each concept rec's
        // message field holds !* refs to the ledger messages that used it)
        final Set<fURI> conceptMessages = new HashSet<>();
        if (!concepts.isEmpty() && agent.service(ConceptService.class).isPresent()) {
            final fURI conceptRoot = agent.service(ConceptService.class).get().root(agent);
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

    /**
     * Distill prompt for {@code summarize()}: asks the model to emit one or more
     * {@code <<json:claim>>} watermarks, each containing a single claim rec shaped like
     * {@code [text=>'...', kind=>decision|problem|solution|observation]}.  The watermarks
     * are scanned by {@link WatermarkUtil} into the
     * ChatFrame's {@code watermark} lst and anchored by {@code summarize()} as
     * {@code claim::T} at {@code <agent>/claim/}.
     * The {@code source} (message vids) is stamped by the inst, not the model — the
     * model never sees message vids, only the digest text.
     */
    private static final String SUMMARIZE_PROMPT = """
                                                   You are distilling a past metatron session into claims and loose ends. A claim is a terse
                                                   proposition (1-3 sentences) capturing a decision, problem, solution, or observation — what
                                                   a future agent would need to understand what happened and why. A loose end is an OPEN
                                                   continuation point a DIFFERENT session could pick up cold — work that is still owed.
                                                   
                                                   Output exactly TWO json blocks. The first is a JSON array of claim objects, the second a
                                                   JSON array of loose end objects:
                                                   
                                                   <<json:claim>>[{"text":"...","kind":"decision","source":[...]},{"text":"...","kind":"problem"}]<</json:claim>>
                                                   <<json:loose_end>>[{"title":"...","desc":"...","status":"open"}]<</json:loose_end>>
                                                   
                                                   Rules for claims:
                                                   1. kind is one of: decision, problem, solution, observation.
                                                   2. source is a list of messages (by vid) that inspired you to create the claim.
                                                     - ["/example/message/1","/example/message/5"]
                                                   3. A decision without a rationale is not worth recording — say why in the text.
                                                   4. Prefer specific over general; if nothing significant happened, emit an empty array: <<json:claim>>[]<</json:claim>>
                                                   
                                                   Rules for loose ends:
                                                   4. The cold test: could a session with no access to this transcript act on it? If reading it
                                                      requires knowing what happened here, it is not a loose end. Most sessions justify 0-2; if
                                                      you are writing a third, you are recording rather than continuing.
                                                   5. These do NOT earn a loose end: something this session finished; a current-state observation;
                                                      a defect the operator should queue; a restatement of a decision (that is already a claim).
                                                   6. If nothing is left open, emit an empty array: <<json:loose_end>>[]<</json:loose_end>>
                                                   
                                                   Do NOT emit session ids, timestamps, source refs, or ids — those are stamped from the record.
                                                   
                                                   The session transcript:
                                                   
                                                   """;

    /**
     * Distill a session's message ledger into claim::T and loose_end::T recs
     * via a mini-task, appending them under the config's {@code output} base.
     * Shared by the {@code summary} inst and the SummarizeFeature's background
     * thread — the config rec has the same vocabulary as the
     * {@code <<mtron:summarize>>} block (session, model, scope, kinds,
     * concepts, output), so the block is simply a deferred summary() call.
     *
     * @param agent  the agent root — the model rec is resolved from
     *               {@code <agentHome>/model} when the config's model is noobj
     * @param config the argument/block rec — {@code scope} filters the
     *               message set (a time::T duration or datetime::T cutoff);
     *               {@code kind} and {@code concept} are recall hints
     *               echoed back for the follow-on briefing; {@code to} is
     *               the anchor base (default: the agent home)
     * @return the applied-constraints rec — the resolved
     * [session, model, scope, kind, concept, to] plus the written
     * claim/ and loose_end/ vids; a fail::T on error
     */
    public static Obj summarizeSession(final Agent agent, final Rec config) {
        final Obj modelArg = config.at(uri(MODEL));
        final Obj scope = config.at(uri(SCOPE));
        final Obj kinds = config.at(uri(KIND));
        final Obj concepts = config.at(uri(CONCEPT));
        final Obj output = config.at(uri(TO));
        final fURI outputBase = output.isNoObj() ? agent.root() : output.uriValue();
        // 1. collect this session's messages from the ledger as rels
        //    (vid => rec) — the rel key IS the message vid (branch read)
        final fURI messagesLocation = agent.root().extend(MESSAGE).extend("+/");
        final List<Rel> messages = Router.readFromSpace(messagesLocation)
                .stream()
                .map(Obj::asRel)
                .filter(pair -> !pair.second().tid().equals(LLM_TOOL_RESULT_MESSAGE_TYPE.vid()))
                .filter(pair -> {
                    final Obj sessionUri = pair.second().asRec().at(SESSION);
                    return sessionUri.isUri() && sessionUri.uriValue().equals(agent.sessionVID());
                })
                .filter(pair -> withinScope(pair.second().asRec(), scope))
                .sorted(Comparator.comparing(pair -> Integer.parseInt(pair.first().uriValue().name())))
                .toList();
        if (messages.isEmpty())
            return fail("no messages found for session %s at %s", agent.sessionVID(), messagesLocation);
        // 2. build the distill digest — vid ==> text so the model can cite real vids
        final String digest = messages.stream()
                .filter(pair -> !Str.Helper.cleanString(pair.second().asRec().at(TEXT)).isBlank())
                .map(pair -> Graphitty.strip(Str.Helper.cleanString(pair.first()) + "==>" + Str.Helper.cleanString(pair.second().asRec().at(TEXT).orElse(str(""))))) // remove color coding annotations
                .collect(Collectors.joining("\n"))
                .replace("%", ""); // remove all string formatting meta-characters
        // 3. the model — from the agent home (matches <agent>/model)
        final mModel model = modelArg.isNoObj() ? mModel.model(Router.readFromSpace(agent.root().extend(MODEL)).asRec()) : mModel.model(modelArg.asRec());
        // 4. distill via a mini-task
        final ChatFrame result = Agent.Helper.miniChat("session_summarizer", model(model.at(TIMEOUT, real(10.0, MATH_MINUTE_TID, null))), SUMMARIZE_PROMPT + digest);
        // 5. parse the <<json:claim>> and <<json:loose_end>> watermarks into vids
        final List<Obj> claimVids = new ArrayList<>();
        final List<Obj> looseEndVids = new ArrayList<>();
        // claims first: a loose end refers to the claims distilled in this same pass
        for (final String keyStr : List.of("claim", "loose_end")) {
            final Obj body = result.watermark(keyStr);
            if (!body.isNoObj()) {
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
                        final Lst source = rec.at(uri(SOURCE)).orElse(lst());
                        if (!source.isEmpty()) {
                            rec.at(uri(SOURCE), lst(source.elements()
                                    .map(s -> (Obj) auto_from_(uri(Str.Helper.cleanString(s))).tryToInst())
                                    .toList()), MUTABLE);
                        }
                        rec = rec.tid(LLM_CLAIM_TID);
                        final fURI vid = Router.writeToSpace(outputBase.extend("claim").extend("_").addQ(INCRQ), rec).vid();
                        claimVids.add(uri(vid));
                    } else if (keyStr.equals("loose_end")) {
                        // JSON parses status as a string ("open") — coerce to a uri
                        // as loose_end::T expects (status => union of uris)
                        final Obj status = rec.at(uri(STATUS));
                        if (status.isStr())
                            rec.at(uri(STATUS), uri(status.strValue()), MUTABLE);
                        // source: lst of !* auto_from refs to the message vids — same as claims
                        final Lst source = rec.at(uri(SOURCE)).orElse(lst());
                        if (!source.isEmpty()) {
                            rec.at(uri(SOURCE), lst(source.elements()
                                    .map(s -> (Obj) auto_from_(uri(Str.Helper.cleanString(s))).tryToInst())
                                    .toList()), MUTABLE);
                        }
                        // claim: !* auto_from refs to the claims distilled in this same
                        // pass — the loose end's justifying propositions
                        if (!claimVids.isEmpty())
                            rec.at(uri("claim"), lst(claimVids.stream()
                                    .map(v -> (Obj) auto_from_(v.uriValue()).tryToInst())
                                    .toList()), MUTABLE);
                        // time is stamped by the inst, not the model
                        rec.at(uri(TIME), nowDatetime(), MUTABLE);
                        rec = rec.tid(LLM_LOOSE_END_TID);
                        final fURI vid = Router.writeToSpace(outputBase.extend("loose_end").extend("_").addQ(INCRQ), rec).vid();
                        looseEndVids.add(uri(vid));
                    }
                }
            }
        }
        // 6. the applied constraints — the config echoed back with defaults resolved
        return rec(uri(SESSION), uri(agent.sessionVID()),
                uri(MODEL), model,
                uri(SCOPE), scope,
                uri(KIND), kinds,
                uri(CONCEPT), concepts,
                uri(TO), uri(outputBase),
                uri("claim"), lst(claimVids),
                uri("loose_end"), lst(looseEndVids));
    }

    /**
     * Scope filter: keep messages whose {@code time} is at or after the cutoff
     * implied by {@code scope} — a time::T duration (relative to now) or an
     * absolute datetime::T.  Noobj (or an unrecognized shape) means no filter.
     */
    private static boolean withinScope(final Rec message, final Obj scope) {
        if (scope.isNoObj())
            return true;
        final Obj time = message.at(uri(TIME));
        if (time.isNoObj() || !time.isUri())
            return true;
        final long cutoff;
        if (scope.test(DATETIME_TYPE)) {
            cutoff = datetimeToMillis(scope.asUri());
        } else if (scope.test(TIME_TYPE)) {
            cutoff = System.currentTimeMillis() - scope.tid(MATH_MILLIS_TID).realValue().longValue();
        } else {
            return true; // unrecognized scope — don't filter
        }
        return datetimeToMillis(time.asUri()) >= cutoff;
    }

}
