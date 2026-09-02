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

package studio.phaseshift.metatron.isa.llm;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatSessionStore;
import studio.phaseshift.metatron.isa.llm.type.*;
import studio.phaseshift.metatron.isa.llm.type.feature.*;
import studio.phaseshift.metatron.isa.llm.type.feature.Feature;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.vec.type.MVec;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.agent;
import static studio.phaseshift.metatron.isa.llm.type.mModel.model;
import static studio.phaseshift.metatron.isa.llm.type.mcp.mcpMessageServer.MCP_MESSAGE_SERVER_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Fail.FAIL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace.staticObjToFile;
import static studio.phaseshift.metatron.isa.vec.vecInstSet.VEC_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(vid = "/m/llm")
public class llmInstSet extends AbstractInstSet {
    public static final fURI LLM_ISA_TID = M_ISA_TID.extend(LLM);
    public static final fURI LLM_MODEL_TID = LLM_ISA_TID.extend(MODEL);
    public static final fURI LLM_AGENT_TID = LLM_ISA_TID.extend(AGENT);
    public static final fURI LLM_INST_TID = LLM_ISA_TID.extend(INST);
    public static final fURI LLM_CHAT_RESULT_TID = LLM_ISA_TID.extend("chat_result");
    public static final fURI LLM_FEATURE_TID = LLM_ISA_TID.extend(FEATURE);
    public static final fURI LLM_SPACE_TID = LLM_ISA_TID.extend(SPACE);
    public static final fURI LLM_TOOL_TID = LLM_ISA_TID.extend(TOOL);
    public static final fURI LLM_SESSION_TID = LLM_ISA_TID.extend(SESSION);
    public static final fURI LLM_ITERATION_TID = LLM_ISA_TID.extend(ITERATION);
    public static final fURI LLM_CLAIM_TID = LLM_ISA_TID.extend("claim");
    public static final fURI LLM_LOOSE_END_TID = LLM_ISA_TID.extend("loose_end");
    public static final fURI LLM_SKILL_TID = LLM_ISA_TID.extend(SKILL);
    public static final fURI MESSAGE_TID = LLM_ISA_TID.extend(MESSAGE);
    public static final fURI AI_MESSAGE_TID = MESSAGE_TID.extend(AI);
    public static final fURI USER_MESSAGE_TID = MESSAGE_TID.extend(USER);
    public static final fURI SYSTEM_MESSAGE_TID = MESSAGE_TID.extend(SYSTEM);
    public static final fURI TOOL_REQUEST_MESSAGE_TID = MESSAGE_TID.extend("tool_request");
    public static final fURI TOOL_RESULT_MESSAGE_TID = MESSAGE_TID.extend("tool_result");
    public static final fURI THINKING_MESSAGE_TID = MESSAGE_TID.extend("thinking");
    public static final fURI COMPACTION_MESSAGE_TID = MESSAGE_TID.extend("compaction");
    //public static final fURI MCP_TOOL_TID = LLM_ISA_TID.extend("mcp");
    // public static Obj MTRON_EVAL_TOOL = mModel.Helper.mtronInstToolSpecification(ObjType.insts().stream().filter(i -> i.tid().equals(EVAL_INST_TID)).findFirst().orElse(null));    
    public static final fURI LLM_CHAT_FEATURE_TID = LLM_FEATURE_TID.extend("chat_feature");
    public static final fURI LLM_MESSAGE_FEATURE_TID = LLM_FEATURE_TID.extend("message_feature");
    public static final fURI LLM_TOOL_FEATURE_TID = LLM_FEATURE_TID.extend("tool_feature");
    public static final fURI LLM_SYSTEM_FEATURE_TID = LLM_FEATURE_TID.extend("system_feature");
    public static final fURI LLM_NOTE_FEATURE_TID = LLM_FEATURE_TID.extend("note_feature");
    public static final fURI LLM_RECALL_FEATURE_TID = LLM_FEATURE_TID.extend("recall_feature");
    public static final fURI LLM_EMBED_FEATURE_TID = LLM_FEATURE_TID.extend("embed_feature");
    //public static final fURI LLM_MESSAGE_FEATURE_TID = f(LLM_MESSAGE_FEATURE_TID_STRING);
    public static final fURI LLM_SKILL_FEATURE_TID = LLM_FEATURE_TID.extend("skill_feature");
    public static final fURI LLM_THINK_FEATURE_TID = LLM_FEATURE_TID.extend("think_feature");
    public static final fURI LLM_CONCEPT_FEATURE_TID = LLM_FEATURE_TID.extend("concept_feature");
    public static final fURI LLM_COMPACTION_FEATURE_TID = LLM_FEATURE_TID.extend("compaction_feature");
    public static final fURI LLM_COMMENT_FEATURE_TID = LLM_FEATURE_TID.extend("comment_feature");
    public static final fURI LLM_SUMMARIZE_FEATURE_TID = LLM_FEATURE_TID.extend("summarize_feature");
    public static final fURI LLM_COST_FEATURE_TID = LLM_FEATURE_TID.extend("cost_feature");
    public static final fURI LLM_AUDIT_FEATURE_TID = LLM_FEATURE_TID.extend("audit_feature");
    public static final fURI LLM_LOOP_FEATURE_TID = LLM_FEATURE_TID.extend("loop_feature");
    public static final fURI LLM_LEDGER_FEATURE_TID = LLM_FEATURE_TID.extend("ledger_feature");
    public static final fURI LLM_ITERATION_FEATURE_TID = LLM_FEATURE_TID.extend("iteration_feature");
    //public static final fURI LLM_SKILL_FEATU

    public static Type LLM_MODEL_TYPE;
    public static Type LLM_AGENT_TYPE;
    public static Type LLM_AI_MESSAGE_TYPE;
    public static Type LLM_USER_MESSAGE_TYPE;
    public static Type LLM_SYSTEM_MESSAGE_TYPE;
    public static Type LLM_SKILL_TYPE;
    public static Type LLM_SESSION_TYPE;
    public static Type LLM_ITERATION_TYPE;
    public static Type LLM_CLAIM_TYPE;
    public static Type LLM_LOOSE_END_TYPE;
    public static Type LLM_MESSAGE_TYPE;
    public static Type LLM_TOOL_RESULT_MESSAGE_TYPE;
    public static Type LLM_TOOL_REQUEST_MESSAGE_TYPE;
    public static Type LLM_THINKING_MESSAGE_TYPE;
    public static Type LLM_COMPACTION_MESSAGE_TYPE;
    public static Type LLM_NOTES_TYPE;
    public static Type LLM_TOOL_TYPE;
    public static Type LLM_CHAT_RESULT_TYPE;
    public static ObjFactory LLM_OBJ_FACTORY = MObjFactory.of().addExtension(MVec.class, x -> lst(x.jvm().stream().toList()));
    public static Type LLM_FEATURE_TYPE;

    /**
     * Distill prompt for {@code summarize()}: asks the model to emit one or more
     * {@code <<mtron:claim>>} blocks, each containing a single claim rec shaped like
     * {@code [text=>'...', kind=>decision|problem|solution|observation]}.  The blocks
     * are parsed by {@code Agent.chat()} into the ChatResult's {@code blocks} rec and
     * anchored by {@code summarize()} as {@code claim::T} at {@code <agent>/claim/}.
     * The {@code source} (message vids) is stamped by the inst, not the model — the
     * model never sees message vids, only the digest text.
     */
    public static final String SUMMARIZE_PROMPT = """
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
                                                  
                                                  %s
                                                  """;

    /**
     * Distill prompt for {@code compact()}: asks the model to write a
     * continuation summary that replaces the conversation history in a future
     * context window.  The summary becomes the {@code text} of the
     * {@code compaction_message::T} sentinel — the model never sees the raw
     * transcript again, only the resume summary.
     */
    public static final String COMPACT_PROMPT = """
                                                You have been working on the task described above but have not yet completed it.
                                                Write a continuation summary that will allow you (or another instance of yourself) to resume work efficiently
                                                in a future context window where the conversation history will be replaced with this summary.
                                                
                                                Your summary should be structured, concise, and actionable. Include:
                                                1. **Task Overview**: The user's core request, success criteria, and constraints.
                                                2. **Current State**: What has been completed, current progress, and any pending steps.
                                                3. **Key Details**: User preferences, domain-specific details, or promises made to the user.
                                                
                                                Write in a way that enables immediate resumption of the task.
                                                
                                                ## Conversation:
                                                %s
                                                """;


    public llmInstSet() {
        super(mutableMap(uri(PATTERN), uri(LLM_ISA_TID.extend(ALL))), INSTSET_TID, LLM_ISA_TID);
    }

    @Override
    public void setup() {
        this.jvm().putAll(mutableMap(
                uri(TYPE), lst(
                        LLM_MODEL_TYPE = docWrap(Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_MODEL_TID)
                                        .isaPredicate(rec(
                                                uri(PROVIDER).maybe().asUri(), URI_TYPE,
                                                uri(HOST), URI_TYPE,
                                                uri(PROTOCOL), URI_TYPE,
                                                uri(LLM), URI_TYPE,
                                                uri(API_KEY).maybe(), STR_TYPE,
                                                uri(TIMEOUT).maybe(), TIME_TYPE,
                                                uri(SIZE).maybe(), DATA_SIZE_TYPE,
                                                uri(QUANT).maybe(), INT_TYPE,
                                                uri(CONTEXT).maybe(), INT_TYPE,
                                                uri(COST).maybe(), rec(uri(IN), MATH_CURRENCY_TYPE, uri(OUT), MATH_CURRENCY_TYPE).maybe()))
                                        .constructor(arg -> LLMFactory.createModel(arg.asRec()))
                                        .create(),
                                null, null,
                                Map.of(uri(PROVIDER).maybe(), "optional name of ai model provider",
                                        uri(HOST), "the ai model provider's http rest endpoint",
                                        uri(PROTOCOL), "the http rest endpoint protocol (ollama, openai, anthropic)",
                                        uri(LLM), "the name of a model offered by the ai provider",
                                        uri(SIZE).maybe(), "the size of the model",
                                        uri(QUANT).maybe(), "the level of quantization of the model",
                                        uri(CONTEXT).maybe(), "the model's context window size in tokens",
                                        uri(COST).maybe(), "the cost per million tokens to use this llm (in/out costs)"),
                                "populate a model reference rec using data from the ai provider's http-endpoint",
                                "model::[provider=>deepseek,host=><http://deepseek.com/api>,protocol=>openai,llm=>deepseek-v4-pro]"),
                        docWrap(LLM_TOOL_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_TOOL_TID)
                                        .isaPredicate(rec(
                                                uri(INST), ALL_TYPE,
                                                uri(NAME), URI_TYPE,
                                                uri(DESC), STR_TYPE,
                                                uri(ARG).maybe(), ALL_TYPE /*rec(URI_TYPE, T(ALL)).maybe())*/))
                                        .create(),
                                "a tool specification", "",
                                Map.of(
                                        uri(INST), "tool instruction",
                                        uri(NAME), "tool name",
                                        uri(DESC), "tool description",
                                        uri(ARG).maybe(), "tool arguments"),
                                "a tool function for the llm to use"),
                        //////////////////////////////////////////////////
                        docWrap(LLM_SESSION_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_SESSION_TID)
                                        .isaPredicate(rec(
                                                uri(AGENT), T(URI_TID.some()),
                                                uri(USER), T(URI_TID.some()),
                                                uri(ALGORITHM), REC_TYPE))
                                        .create(),
                                null, null, mutableMap(
                                        uri(AGENT), "the agent(s) involved in the chat session",
                                        uri(USER), "the user(s) involved in the chat session",
                                        uri(ALGORITHM), "the algorithm used to manage the chat session (compaction, windowing, summarizing, etc.)"),
                                "llm session session policy with algorithm config and a resolved lst of messages from sub-path */msg/*"),
                        docWrap(LLM_ITERATION_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_ITERATION_TID)
                                        .isaPredicate(rec(
                                                uri(SESSION), URI_TYPE,
                                                uri(INDEX), INT_TYPE,
                                                uri(PREV).maybe(), URI_TYPE,
                                                uri(NEXT).maybe(), URI_TYPE,
                                                uri(MESSAGE).maybe(), LST_TYPE,
                                                uri(TIME), STR_TYPE))
                                        .create(),
                                null, null, mutableMap(
                                        uri(SESSION), "the parent session",
                                        uri(INDEX), "1-based ordinal within the session",
                                        uri(PREV).maybe(), "previous iteration VID in the linked list",
                                        uri(NEXT).maybe(), "next iteration VID in the linked list",
                                        uri(MESSAGE).maybe(), "auto_from references to message VIDs in this iteration",
                                        uri(TIME), "creation timestamp"),
                                "an iteration groups the messages of a single chat turn within a session and links to prev/next iterations"),
                        //////////////////////////////////////////////////
                        // CLAIM — a distilled proposition with provenance
                        docWrap(LLM_CLAIM_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_CLAIM_TID)
                                        .isaPredicate(rec(
                                                uri(TEXT), STR_TYPE,
                                                uri(KIND), union_(lst(
                                                        uri("decision"),
                                                        uri("problem"),
                                                        uri("solution"),
                                                        uri("observation"))).tryToInst(),
                                                uri(SOURCE).maybe(), lst(T(ALL.maybe())),
                                                uri(CONCEPT).maybe(), lst(T(ALL.maybe())),
                                                uri("tier").maybe(), isa_(NAT_TYPE).else_(jnt(1))))
                                        .create(),
                                null, null, mutableMap(
                                        uri(TEXT), "the distilled proposition",
                                        uri(KIND), "claim kind — decision, problem, solution, or observation",
                                        uri(SOURCE).maybe(), "message vids and/or external uris the claim derives from",
                                        uri(CONCEPT).maybe(), "concept graph links (auto_from refs)",
                                        uri("tier"), "trust tier (nat), bounded by min(source tiers)"),
                                "a distilled claim with provenance — the proposition layer above concept nouns",
                                "claim::[text=>\"the scratch project writes do not persist because there is no backing write primitive\"," +
                                        "\tkind=>problem," +
                                        "\tsource=>[/usr/dr/message/4]," +
                                        "\tconcept=>[/usr/dr/concept/persistence]," +
                                        "\ttier=>nat::1]"),
                        //////////////////////////////////////////////////
                        // LOOSE_END — an open problem a future session can pick up cold
                        docWrap(LLM_LOOSE_END_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_LOOSE_END_TID)
                                        .isaPredicate(rec(
                                                uri(TITLE), STR_TYPE,
                                                uri(DESC), STR_TYPE,
                                                uri(STATUS), union_(lst(
                                                        uri("open"),
                                                        uri("in_progress"),
                                                        uri("resolved"),
                                                        uri("abandoned"))).tryToInst(),
                                                uri(SOURCE).maybe(), lst(T(ALL.maybe())),
                                                uri("claim").maybe(), lst(T(ALL.maybe())),
                                                uri(TIME).maybe(), DATETIME_TYPE))
                                        .create(),
                                null, null, mutableMap(
                                        uri(TITLE), "short actionable title",
                                        uri(DESC), "what needs to happen and why",
                                        uri(STATUS), "open, in_progress, resolved, or abandoned",
                                        uri(SOURCE).maybe(), "message vids and/or external uris the loose end derives from",
                                        uri("claim").maybe(), "claims that define/resolve this loose end (auto_from refs)",
                                        uri(TIME), "last updated timestamp"),
                                "an open problem a future session can pick up cold — the continuation point carried across sessions",
                                "loose_end::[title=>\"wire the mcp_stdio transport\"," +
                                        "\tdesc=>\"expose /mcp over stdin so harness can spawn metatron directly\"," +
                                        "\tstatus=>open," +
                                        "\tsource=>[/usr/dr/message/4]," +
                                        "\tclaim=>[/usr/dr/claim/7]," +
                                        "\ttime=>datetime::<//2026.08:25/15/48/02/251?tz=+0000>]"),
                        // LLM_MESSAGE_TYPE defined below after all message sub-types
                        docWrap(LLM_SYSTEM_MESSAGE_TYPE = Type.Builder.build()
                                        .tid(MESSAGE_TID)
                                        .vid(SYSTEM_MESSAGE_TID)
                                        .isaPredicate(rec(uri(TEXT), STR_TYPE))
                                        //   uri(SIZE), DATA_SIZE_TYPE))
                                        .create(),
                                null, null,
                                Map.of(uri(TEXT), "the system message text body"),
                                //  uri(SIZE), "the data size of the text body"),
                                "a system message provides behavioral and response-style instructions to the model"),
                        docWrap(LLM_CHAT_RESULT_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_CHAT_RESULT_TID)
                                        .isaPredicate(rec(
                                                uri(CHAT).maybe().asUri(), ALL_TYPE,
                                                uri(TIME).maybe(), TIME_TYPE,
                                                uri(ERROR).maybe(), FAIL_TYPE))
                                        .create(),
                                null, null, mutableMap(
                                        uri(CHAT), "the chat response — free-text str or structured rec per response format",
                                        uri(TIME), "elapsed time::T from user message to complete response",
                                        uri(ERROR).maybe(), "a fail chain if errors occurred"),
                                "a response message from a chat interaction"),
                        docWrap(LLM_USER_MESSAGE_TYPE = Type.Builder.build()
                                        .tid(MESSAGE_TID)
                                        .vid(USER_MESSAGE_TID)
                                        .isaPredicate(rec(
                                                uri(NAME).maybe().asUri(), STR_TYPE,
                                                uri(TEXT).maybe(), STR_TYPE,
                                                uri(CONTENTS).maybe(), T(ALL_STAR)))
                                        //uri(SIZE).maybe(), DATA_SIZE_TYPE))
                                        .create(),
                                null, null, mutableMap(
                                        uri(NAME).maybe(), "sender identity for multi-user conversations",
                                        uri(TEXT).maybe(), "text of a single content message",
                                        uri(CONTENTS).maybe(), "the message contents"
                                        /*  uri(SIZE), "the data size of the message content"*/), "a user message"),
                        docWrap(LLM_TOOL_REQUEST_MESSAGE_TYPE = Type.Builder.build()
                                        .tid(MESSAGE_TID)
                                        .vid(TOOL_REQUEST_MESSAGE_TID)
                                        .isaPredicate(rec(
                                                uri(NAME), URI_TYPE,
                                                uri(ARGS).maybe(), STR_TYPE,
                                                uri(TEXT), STR_TYPE,
                                                uri(CONTENTS).maybe(), STR_TYPE))
                                        .create(),
                                null, null, mutableMap(
                                        uri(NAME), "the tool name",
                                        uri(ARGS), "the tool arguments (mapped from LC4j 'arguments' via VOCAB)",
                                        uri(TEXT), "formatted name(args) summary",
                                        uri(CONTENTS).maybe(), "the tool execution request id"),
                                "a tool execution request — nested inside an ai message's tool_requests list"),
                        docWrap(LLM_AI_MESSAGE_TYPE = Type.Builder.build()
                                        .tid(MESSAGE_TID)
                                        .vid(AI_MESSAGE_TID)
                                        .isaPredicate(rec(
                                                uri(TEXT).maybe().asUri(), STR_TYPE,
                                                uri(TOOL_REQUESTS).maybe(), lst(LLM_TOOL_REQUEST_MESSAGE_TYPE)))
                                        .create(),
                                null, null, mutableMap(
                                        uri(TEXT), "the response text",
                                        uri(TOOL_REQUESTS), "the tool execution requests made by the model",
                                        uri("attributes"), "extra provider metadata is stored as top-level fields on the rec"),
                                "an ai/assistant message"),
                        docWrap(LLM_TOOL_RESULT_MESSAGE_TYPE = Type.Builder.build()
                                        .tid(MESSAGE_TID)
                                        .vid(TOOL_RESULT_MESSAGE_TID)
                                        .isaPredicate(rec(
                                                uri(NAME), URI_TYPE,
                                                uri(TEXT), STR_TYPE,
                                                uri(CHAT).maybe(), ALL_TYPE,
                                                //      uri(SIZE), DATA_SIZE_TYPE,
                                                uri(ID).maybe(), STR_TYPE))
                                        .create(),
                                null, null, mutableMap(
                                        uri(NAME), "the tool that was executed",
                                        uri(TEXT), "the text result of the tool execution",
                                        uri(CHAT).maybe(), "mtron-serialized chat_result::T when the tool was a recursive chat call",
                                        //   uri(SIZE), "the data size of the message text",
                                        uri(ID).maybe(), "correlation id matching the tool execution request"),
                                "a tool execution result message"),
                        docWrap(LLM_THINKING_MESSAGE_TYPE = Type.Builder.build()
                                        .tid(MESSAGE_TID)
                                        .vid(THINKING_MESSAGE_TID)
                                        .isaPredicate(rec(
                                                uri(TEXT), STR_TYPE))
                                        .create(),
                                null, null,
                                Map.of(uri(TEXT), "the model's internal reasoning text"),
                                "a thinking/reasoning trace message — stored in the ledger but excluded from the LC4j chat window"),
                        docWrap(LLM_COMPACTION_MESSAGE_TYPE = Type.Builder.build()
                                        .tid(MESSAGE_TID)
                                        .vid(COMPACTION_MESSAGE_TID)
                                        .isaPredicate(rec(
                                                uri(TEXT), STR_TYPE,
                                                uri(IN).maybe(), INT_TYPE,
                                                uri(OUT).maybe(), INT_TYPE,
                                                uri(COMPRESSION).maybe(), REAL_TYPE))
                                        .create(),
                                null, null,
                                Map.of(uri(TEXT), "the summary of all previous messages and compactions",
                                        uri(IN).maybe(), "total tokens processed (the input digest estimate)",
                                        uri(OUT).maybe(), "total tokens generated (the summary estimate)",
                                        uri(COMPRESSION).maybe(), "fraction of tokens removed — 1 - out/in (0.0 to 1.0)"),
                                "a compaction represents a stop point for message retrieval and provides a summary of all previous messages"),
                        docWrap(LLM_MESSAGE_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(MESSAGE_TID)
                                        .isaPredicate(rec(uri(SESSION).maybe().asUri(), URI_TYPE))
                                        .create(),
                                null, null,
                                mutableMap(),
                                "polymorphic chat message — one of system, user, ai, tool_result, thinking, or compaction; discriminated by _tid column"),
                        //////////////////////////////////////////////////
                        docWrap(LLM_SKILL_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_SKILL_TID)
                                        .isaPredicate(rec(
                                                uri(NAME), URI_TYPE,
                                                uri(DESC), STR_TYPE,
                                                uri(CONTENT).maybe(), STR_TYPE,
                                                uri(RESOURCE).maybe(), lst(rec(
                                                        uri(URI), URI_TYPE,
                                                        uri(NAME).maybe(), STR_TYPE,
                                                        uri(DESC).maybe(), STR_TYPE,
                                                        uri(TEXT), STR_TYPE)),
                                                uri(TOOL).maybe(), lst(ALL_TYPE))).create(),
                                "a skill.md specification", "",
                                mutableMap(
                                        uri(NAME), "skill name",
                                        uri(DESC), "skill description",
                                        uri(CONTENT).maybe(), "skill.md document content",
                                        uri(RESOURCE).maybe(), "skill assets, references, and scripts",
                                        uri(TOOL).maybe(), "skill tools"),
                                "a skill.md specification to augment llm with specialized abilities",
                                "*<local:.agent/skills>.as(skill::T)   [-- see as?skill<=dir() --]"),
                        docWrap(LLM_FEATURE_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_FEATURE_TID)
                                        .isaPredicate(rec(
                                                // hook fields — each is an optional inst a feature can override
                                                uri(ROOT).maybe().asUri(), URI_TYPE,
                                                uri(SKILL).maybe(), lst(LLM_SKILL_TYPE),
                                                uri(ON_AGENT_CTOR).maybe(), ALL_TYPE,
                                                uri(ON_BEFORE_CHAT).maybe(), ALL_TYPE,
                                                uri(ON_PARTIAL_RESPONSE).maybe(), ALL_TYPE,
                                                uri(ON_PARTIAL_THINKING).maybe(), ALL_TYPE,
                                                uri(ON_PARTIAL_TOOL_CALL).maybe(), ALL_TYPE,
                                                uri(BEFORE_TOOL_EXECUTION).maybe(), ALL_TYPE,
                                                uri(ON_TOOL_EXECUTED).maybe(), ALL_TYPE,
                                                uri(ON_COMPLETE_RESPONSE).maybe(), ALL_TYPE,
                                                uri(ON_ERROR).maybe(), ALL_TYPE))
                                        .create(),
                                null, null, mutableMap(
                                        uri(ROOT).maybe(), "the root uri location of feature data",
                                        uri(SKILL).maybe(), "skills associated with the feature",
                                        uri(ON_AGENT_CTOR).maybe(), "inst?noobj<=agent(){ [-- one time setup --] }",
                                        uri(ON_BEFORE_CHAT).maybe(), "inst?#{?}<=agent(){ [-- non-noobj to short-circuit --] }",
                                        uri(ON_PARTIAL_RESPONSE).maybe(), "inst?noobj<=agent(text=>str::T)",
                                        uri(ON_PARTIAL_THINKING).maybe(), "inst?noobj<=agent(text=>str::T)",
                                        uri(ON_PARTIAL_TOOL_CALL).maybe(), "inst?noobj<=agent(request=>call::T)",
                                        uri(BEFORE_TOOL_EXECUTION).maybe(), "inst?noobj<=agent(request=>call::T)",
                                        uri(ON_TOOL_EXECUTED).maybe(), "inst?noobj<=agent(result=>call::T)",
                                        uri(ON_COMPLETE_RESPONSE).maybe(), "inst?noobj<=agent(result=>chat_result::T)",
                                        uri(ON_ERROR).maybe(), "inst?noobj<=agent(fail=>fail::T)"),
                                "each concrete feature refines llm_feature::T with its own hook implementations"),
                        LLM_AGENT_TYPE = docWrap(Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_AGENT_TID)
                                        .isaPredicate(rec(
                                                uri(NAME), STR_TYPE,
                                                uri(DESC).maybe(), STR_TYPE,
                                                uri(FEATURE).maybe(), LST_TYPE))
                                        .constructor(arg -> new Agent(arg.recValue(), LLM_AGENT_TID, arg.vid()))
                                        .create(), null, null, Map.of(
                                        uri(NAME), "a convenient name for the agent",
                                        uri(DESC), "a description of the agent given to the agent in their system prompt",
                                        uri(FEATURE), "the ordered lst of capabilities attached to the agent"),
                                "an agent is an llm enriched with embodied capabilities"),
                        // -- concrete feature types ------------------------------------------
                        Type.Builder.build()
                                .tid(LLM_FEATURE_TID)
                                .vid(LLM_CHAT_FEATURE_TID)
                                .isaPredicate(rec(
                                        uri(MODEL), LLM_MODEL_TYPE,
                                        uri(RESPONSE).maybe(), rec(
                                                uri(TO).maybe().asUri(), ALL_TYPE,
                                                uri("complete").maybe(), ALL_TYPE).maybe(),
                                        uri(FORMAT).maybe(), ALL_TYPE))
                                .constructor(arg -> createStageLambdas(new ChatFeature(arg.asRec().jvm(), LLM_CHAT_FEATURE_TID, arg.vid())))
                                .create(),
                        Type.Builder.build()
                                .tid(LLM_FEATURE_TID)
                                .vid(LLM_SUMMARIZE_FEATURE_TID)
                                .isaPredicate(rec(ROOT, URI_TYPE))
                                .constructor(arg -> createStageLambdas(new SummarizeFeature(arg.asRec().jvm(), LLM_SUMMARIZE_FEATURE_TID, arg.vid())))
                                .create(),
                        Type.Builder.build()
                                .tid(LLM_FEATURE_TID)
                                .vid(LLM_MESSAGE_FEATURE_TID)
                                .isaPredicate(rec(SESSION, URI_TYPE))
                                .constructor(arg -> createStageLambdas(new MessageFeature(arg.asRec().jvm(), LLM_MESSAGE_FEATURE_TID, arg.vid())))
                                .create(),
                        Type.Builder.build()
                                .tid(LLM_FEATURE_TID)
                                .vid(LLM_TOOL_FEATURE_TID)
                                .isaPredicate(rec(uri(f(TOOL)).maybe().asUri(), T(LST_TID.maybe())))
                                .constructor(arg -> createStageLambdas(new ToolFeature(arg.asRec().jvm(), LLM_TOOL_FEATURE_TID, arg.vid())))
                                .create(),
                        Type.Builder.build()
                                .tid(LLM_FEATURE_TID)
                                .vid(LLM_EMBED_FEATURE_TID)
                                .isaPredicate(rec(
                                        uri(ROOT), URI_TYPE,
                                        uri(f(MODEL)).maybe(), LLM_MODEL_TYPE))
                                .constructor(arg -> createStageLambdas(new EmbedFeature(arg.asRec().jvm(), LLM_EMBED_FEATURE_TID, arg.vid())))
                                .create(),
                        Type.Builder.build()
                                .tid(LLM_FEATURE_TID)
                                .vid(LLM_SKILL_FEATURE_TID)
                                // .isaPredicate(rec(SKILL, LST_TYPE))
                                .constructor(arg -> createStageLambdas(new SkillFeature(arg.asRec().jvm(), LLM_SKILL_FEATURE_TID, arg.vid())))
                                .create(),
                        Type.Builder.build()
                                .tid(LLM_FEATURE_TID)
                                .vid(LLM_SYSTEM_FEATURE_TID)
                                .constructor(arg -> createStageLambdas(new SystemFeature(arg.asRec().jvm(), LLM_SYSTEM_FEATURE_TID, arg.vid())))
                                .create(),
                        // [parked stub] SimilarityRecall — out of the active roster during the
                        // channel refactor (skill/tool/message owners); un-comment to revive.
//                         Type.Builder.build()
//                                 .tid(LLM_FEATURE_TID)
//                                 .vid(LLM_RECALL_FEATURE_TID)
//                                 .constructor(arg -> createStageLambdas(new SimilarityRecallFeature(arg.asRec().jvm(), LLM_RECALL_FEATURE_TID, arg.vid())))
//                                 .create(),
                        docWrap(Type.Builder.build()
                                        .tid(LLM_FEATURE_TID)
                                        .vid(LLM_THINK_FEATURE_TID)
                                        .constructor(arg -> createStageLambdas(new ThinkFeature(arg.asRec().jvm(), LLM_THINK_FEATURE_TID, arg.vid())))
                                        .create(),
                                null, null,
                                mutableMap(),
                                "think feature captures thinking text during response generation"),
                        docWrap(Type.Builder.build()
                                        .tid(LLM_FEATURE_TID)
                                        .vid(LLM_CONCEPT_FEATURE_TID)
                                        .constructor(arg -> createStageLambdas(new ConceptFeature(arg.asRec().jvm(), LLM_CONCEPT_FEATURE_TID, arg.vid())))
                                        .create(),
                                null, null,
                                mutableMap(),
                                "extracts and normalizes concepts from the agent response and thinking stream"),
                        // [parked stub] Comment — out of the active roster during the
                        // channel refactor (skill/tool/message owners); un-comment to revive.
//                         docWrap(Type.Builder.build()
//                                         .tid(LLM_FEATURE_TID)
//                                         .vid(LLM_COMMENT_FEATURE_TID)
//                                         .constructor(arg -> createStageLambdas(new CommentFeature(arg.asRec().jvm(), LLM_COMMENT_FEATURE_TID, arg.vid())))
//                                         .create(),
//                                 null, null, mutableMap(),
//                                 "allows user to interject with a comment in the current chat lifecycle of the agent"),
                        docWrap(Type.Builder.build()
                                        .tid(LLM_FEATURE_TID)
                                        .vid(LLM_COST_FEATURE_TID)
                                        .isaPredicate(rec(
                                                uri(ROOT), URI_TYPE,
                                                uri(RATE), rec(
                                                        uri(IN), MATH_CURRENCY_TYPE,
                                                        uri(OUT), MATH_CURRENCY_TYPE)))
                                        .constructor(arg -> createStageLambdas(new CostFeature(arg.asRec().jvm(), LLM_COST_FEATURE_TID, arg.vid())))
                                        .create(),
                                null, null, mutableMap(
                                        uri(ROOT), "the URI prefix where cost data is persisted (e.g., /usr/dr/cost)",
                                        uri(f(RATE).extend(IN)), "cost per million input tokens",
                                        uri(f(RATE).extend(OUT)), "cost per million output tokens"
                                ),
                                "tracks real token-based LLM costs via CostCalculator, persists in/out/total to space",
                                "cost_feature::[root=>/usr/dr/cost,cost=>[in_cost=>usd_currency::0.065,out_cost=>usd_currency::0.001]]"),
                        docWrap(Type.Builder.build()
                                        .tid(LLM_FEATURE_TID)
                                        .vid(LLM_COMPACTION_FEATURE_TID)
                                        .isaPredicate(rec(
                                                uri(MODEL).maybe().asUri(), LLM_MODEL_TYPE,
                                                uri(THRESHOLD).maybe().asUri(), REAL_TYPE,
                                                uri(CONTEXT).maybe().asUri(), INT_TYPE))
                                        .constructor(arg -> createStageLambdas(new CompactionFeature(arg.asRec().jvm(), LLM_COMPACTION_FEATURE_TID, arg.vid())))
                                        .create(),
                                null, null, mutableMap(uri(MODEL), "the model to analyze message history",
                                        uri(THRESHOLD), "auto-compaction trigger — fraction of the model context window full (default 0.8)",
                                        uri(CONTEXT), "context window size in tokens, overriding the model's advertised value"),
                                "compacts historic messages and inserts a compaction message into message stream which acts as a stop sentinel for agents history introspection"),
                        docWrap(Type.Builder.build()
                                        .tid(LLM_FEATURE_TID)
                                        .vid(LLM_AUDIT_FEATURE_TID)
                                        .constructor(arg -> createStageLambdas(new AuditFeature(arg.asRec().jvm(), LLM_AUDIT_FEATURE_TID, arg.vid())))
                                        .create(),
                                null, null, mutableMap(),
                                "lifecycle audit trail with table text and widget result"),
                        docWrap(Type.Builder.build()
                                        .tid(LLM_FEATURE_TID)
                                        .vid(LLM_LOOP_FEATURE_TID)
                                        .isaPredicate(rec(
                                                uri("max_loop").maybe().asUri(), INT_TYPE,
                                                uri("max_time").maybe().asUri(), ALL_TYPE,
                                                uri("delay").maybe().asUri(), ALL_TYPE,
                                                uri("preserve").maybe().asUri(), LST_TYPE))
                                        .constructor(arg -> createStageLambdas(new LoopFeature(arg.asRec().jvm(), LLM_LOOP_FEATURE_TID, arg.vid())))
                                        .create(),
                                null, null, mutableMap(
                                        uri("max_loop").maybe(), "max iterations (default 10)",
                                        uri("max_time").maybe(), "wall-clock ceiling (time::T)",
                                        uri("delay").maybe(), "delay between iterations for polling (time::T)",
                                        uri("preserve").maybe(), "fields to carry forward across iterations"),
                                "multi-pass reasoning loop with iteration control and polling",
                                "loop_feature::[max_loop=>5,delay=>second::2]"),
                        // [parked stub] Ledger — out of the active roster during the
                        // channel refactor (skill/tool/message owners); un-comment to revive.
//                         docWrap(Type.Builder.build()
//                                         .tid(LLM_FEATURE_TID)
//                                         .vid(LLM_LEDGER_FEATURE_TID)
//                                         .isaPredicate(rec(uri("init").maybe().asUri(), LST_TYPE))
//                                         .constructor(arg -> createStageLambdas(new LedgerFeature(arg.asRec().jvm(), LLM_LEDGER_FEATURE_TID, arg.vid())))
//                                         .create(),
//                                 "ledger feature — persistent agent-owned scratchpad for cross-turn task tracking",
//                                 "", mutableMap(
//                                         uri("init").maybe(), "optional pre-populated task list"),
//                                 "Never cleared between chat calls. Agent reads via system message injection, writes via <<mtron:ledger>> blocks. Survives the entire session.",
//                                 "ledger_feature::[init=>['task 1','task 2']]"),
                        // [parked stub] Iteration — out of the active roster during the
                        // channel refactor (skill/tool/message owners); un-comment to revive.
//                         docWrap(Type.Builder.build()
//                                         .tid(LLM_FEATURE_TID)
//                                         .vid(LLM_ITERATION_FEATURE_TID)
//                                         .constructor(arg -> createStageLambdas(new IterationFeature(arg.asRec().jvm(), LLM_ITERATION_FEATURE_TID, arg.vid())))
//                                         .create(),
//                                 null, null, mutableMap(),
//                                 "overlays an iteration graph on the message ledger — each chat turn creates a linked iteration node with prev/next pointers and message back-references",
//                                 "iteration_feature::[]"),
                        //////////////////////////////////////////////////////////
                        MCP_MESSAGE_SERVER_TYPE),
                uri(INST), lst(
                        docWrap(instC(AS_INST_TID.dom(REC_TID).rng(LLM_MODEL_TID),
                                        lst(LLM_MODEL_TYPE),
                                        (lhs, inst) -> lhs.tid(LLM_MODEL_TID)),
                                "a rec",
                                "a model",
                                mutableMap(jnt(0), "a rec shaped like a model"),
                                "maps a rec to a model"),
                        docWrap(instC(AS_INST_TID.dom(DOCS_TID).rng(LLM_TOOL_TID),
                                        lst(LLM_TOOL_TYPE),
                                        (lhs, inst) -> mTool.tool(QCollection.Docs.doc(lhs.asRec()))),
                                "instruction documentation",
                                "a tool specification",
                                mutableMap(jnt(0), "the tool type"),
                                "maps an instruction doc to a tool specification for llm use",
                                "*eval?docq.as(tool::T)"),
                        docWrap(instC(AS_INST_TID.dom(M_ISA_INST_TID).rng(LLM_TOOL_TID), lst(LLM_TOOL_TYPE), (lhs, inst) -> mTool.mtronInstToDocs(inst.asInst())),
                                "an instruction",
                                "a tool specification",
                                mutableMap(jnt(0), "the tool type"),
                                "maps an instruction to a tool specification for llm use",
                                "*eval.as(tool::T)"),
                        docWrap(instC(AS_INST_TID.dom(URI_TID).rng(LLM_SKILL_TID), lst(LLM_SKILL_TYPE), (lhs, inst) -> mSkill.of(staticObjToFile(lhs))),
                                "a dir uri containing the llm SKILL.md file",
                                "a mtron encoding of the specified skill",
                                mutableMap(jnt(0), "the skill type"),
                                "maps a directory to an llm skill where the dir follows the standard SKILL.md structure",
                                "*<local:.agent/skills>.as(skill::T)"),
                        docWrap(instC(AS_INST_TID.dom(LLM_AGENT_TID).rng(LLM_SKILL_TID), lst(LLM_SKILL_TYPE), (lhs, inst) -> mSkill.agentToSkill(lhs.<Agent>as())),
                                "an agent",
                                "a skill aggregating the agent's capabilities",
                                mutableMap(jnt(0), "the skill type"),
                                "maps an agent to a skill by aggregating its features' tools and resources",
                                "*<ollama:qwen3:latest>+[response=>[to=>print(_)]].as(skill::T)"),
                        // CHAT INSTRUCTION
                        docWrap(instC(LLM_INST_TID.extend("chat").dom(LLM_AGENT_TID).rng(LLM_CHAT_RESULT_TID), lst(STR_TYPE), (lhs, inst) -> agent(lhs.asRec()).chat(inst.arg(0).strValue())),
                                "an agent to chat with",  // dom
                                "chat result rec — monos inline (chat, user, time), feature outputs as !* refs", // rng
                                mutableMap(jnt(0), "the message to send the agent"), // args
                                "communicate with an agent that may be enriched with a tool, skill, etc.", // desc
                                "*<ollama:qwen3:latest>+[response=>[to=>print(_)],think=>to(/ai/thoughts/_?incrq)].chat('what is a database?')"),
                        docWrap(instC(LLM_INST_TID.extend("embed").dom(LLM_MODEL_TID).rng(VEC_TID), lst(ALL_TYPE), (lhs, inst) -> model(lhs.asRec()).embed(inst.arg(0))),
                                "a model to embed arg into",  // dom
                                "the obj as a vector embedding", // rng
                                mutableMap(jnt(0), "the object to embed"), // args
                                "embed an object with an llm", // desc
                                "*<ollama:qwen3:latest>.embed('what is a database?')"),
                        docWrap(instC(LLM_INST_TID.extend("interrupt").dom(LLM_AGENT_TID).rng(NOOBJ_TID.zero()), lst(), (lhs, inst) -> {
                            lhs.<Agent>as().interrupt();
                            return noobj();
                        }), "interrupt the agent mid-process"),
                        /*instC(LLM_INST_TID.extend("chat").dom(MODEL_TID).rng(A.maybe()),
                                lst(STR_TYPE),
                                (lhs, inst) -> model(lhs.asRec()).chat(inst.arg(0).strValue())),*/
                        docWrap(instC(LLM_INST_TID.extend("chat").dom(LLM_AGENT_TID).rng(LLM_CHAT_RESULT_TID),
                                        lst(STR_TYPE, REC_TYPE),
                                        (lhs, inst) -> agent(lhs.asRec()).chat(inst.arg(0).strValue(), inst.arg(1).asRec())),
                                "a model to chat with",  // dom
                                "the models chat response", // rng
                                mutableMap(jnt(0), "the message to send the model", jnt(1), "the desired response format"), // args
                                "communicate with am llm enriched by tools, skills, etc. and receive response in particular format", // desc
                                "*<ollama:qwen3:latest>+[response=>[to=>print(_)],think=>to(/ai/thoughts/_?incrq)].chat('what is 4+2?',[answer=>int::T])"),
                        // SUMMARIZE INSTRUCTION — distill a session into claim::T recs
                        docWrap(instC(LLM_INST_TID.extend("summary").dom(LLM_SESSION_TID.maybe()).rng(REC_TID), rec(
                                                uri(SESSION).maybe().asUri(), T(LLM_SESSION_TID.maybe()),
                                                uri(MODEL).maybe().asUri(), choose_(rec(
                                                        isa_(LLM_MODEL_TYPE).tryToInst(), id_().tryToInst(),
                                                        isa_(LLM_SESSION_TYPE).tryToInst(), from_(rshift_(uri(AGENT)).mult_(uri(MODEL))).tryToInst()))
                                                        .rshift_().tryToInst(),
                                                uri(SCOPE).maybe().asUri(), union_(lst(isa_(TIME_TYPE).tryToInst(), isa_(DATETIME_TYPE).tryToInst())).tryToInst(),
                                                uri(KIND).maybe().asUri(), LST_TYPE,
                                                uri(CONCEPT).maybe().asUri(), LST_TYPE),
                                        (lhs, inst) -> {
                                            // The session may arrive as the lhs (fluent: @dr/session/1.summarize(_))
                                            // or as arg 0 (function form: summarize(@dr/session/1)).
                                            final Rec session = inst.arg(f(SESSION), 0).orElse(lhs.asRec());
                                            final fURI sessionVID = session.vid();
                                            if (null == sessionVID || sessionVID.isEmpty())
                                                return fail("summarize requires an anchored session — use @dr/session/N.summarize()");
                                            final fURI agentHome = session.at(AGENT).uriValue();
                                            // the argument rec — same vocabulary as the <<mtron:summarize>> block
                                            // (session/model are summary()-only keys; the block uses scope/kinds/concepts)
                                            final Rec config = rec(uri(SESSION), uri(sessionVID),
                                                    uri(MODEL), inst.arg(f(MODEL), 1),
                                                    uri(SCOPE), inst.arg(f(SCOPE), 2),
                                                    uri(KIND), inst.arg(f(KIND), 3),
                                                    uri(CONCEPT), inst.arg(f(CONCEPT), 4),
                                                    uri(TO), uri(agentHome));
                                            return summarizeSession(agentHome, sessionVID, config);
                                        }),
                                "a session to distill",
                                "the applied constraints rec — [session, model, scope, kind, concept, to, claim=>[vids], loose_end=>[vids]]",
                                mutableMap(),
                                "distill a session's message ledger into claim::T and loose_end::T recs via a mini-task — the same call as the <<mtron:summarize>> block (they share the argument rec::T vocabulary: session, model, scope, kind, concept, to)",
                                "@dr/session/1.summarize(_)  [-- fluent --]  |  summarize(@dr/session/1)  [-- function --]"),
                        // COMPACT INSTRUCTION — compact a session's ledger into a resume sentinel
                        docWrap(instC(LLM_INST_TID.extend("compact").dom(LLM_AGENT_TID.maybe()).rng(REC_TID), rec(
                                                uri(AGENT).maybe().asUri(), LLM_AGENT_TYPE,
                                                uri(MODEL).maybe().asUri(), LLM_MODEL_TYPE,
                                                uri(PROMPT).maybe().asUri(), STR_TYPE),
                                        (lhs, inst) -> {
                                            // The agent may arrive as the lhs (fluent: @dr.compact())
                                            // or as arg 0 (function form: compact(@dr)).
                                            final Rec agentRec = inst.arg(f(AGENT), 0).orElse(lhs.asRec());
                                            final Agent a = agent(agentRec);
                                            if (!a.hasFeature(LLM_MESSAGE_FEATURE_TID))
                                                return fail("compact requires the agent to have a session feature");
                                            final fURI agentHome = a.at(ROOT).uriValue();
                                            final fURI sessionVID = a.feature(LLM_MESSAGE_FEATURE_TID).asRec().at(SESSION).uriValue();
                                            final Rec config = rec(uri(MODEL), inst.arg(f(MODEL), 1),
                                                    uri(PROMPT), inst.arg(f(PROMPT), 2),
                                                    uri(TO), uri(agentHome));
                                            return compactSession(agentHome, sessionVID, config);
                                        }),
                                "an agent to compact",
                                "the applied constraints rec — [to, compaction=>vid, in, out, compression]",
                                mutableMap(jnt(0), "the agent to compact (defaults to the lhs)",
                                        jnt(1), "the summarizer model (default: the agent home model)",
                                        jnt(2), "the summarizer prompt template"),
                                "compact a session's message ledger into a resume summary sentinel — the same call as the <<mtron:compaction>> block (they share the argument rec::T vocabulary: agent, model, prompt)",
                                "@dr.compact()  [-- fluent --]  |  compact(@dr)  [-- function --]"))));
        docWrap(this, "large language model think and reason within the metatron");
        super.setup();
    }

    /**
     * Distill a session's message ledger into claim::T and loose_end::T recs
     * via a mini-task, appending them under the config's {@code output} base.
     * Shared by the {@code summary} inst and the SummarizeFeature's background
     * thread — the config rec has the same vocabulary as the
     * {@code <<mtron:summarize>>} block (session, model, scope, kinds,
     * concepts, output), so the block is simply a deferred summary() call.
     *
     * @param agentHome  the agent root — the model rec is resolved from
     *                   {@code <agentHome>/model} when the config's model is noobj
     * @param sessionVID the session whose ledger messages are distilled
     * @param config     the argument/block rec — {@code scope} filters the
     *                   message set (a time::T duration or datetime::T cutoff);
     *                   {@code kind} and {@code concept} are recall hints
     *                   echoed back for the follow-on briefing; {@code to} is
     *                   the anchor base (default: the agent home)
     * @return the applied-constraints rec — the resolved
     * [session, model, scope, kind, concept, to] plus the written
     * claim/ and loose_end/ vids; a fail::T on error
     */
    public static Obj summarizeSession(final fURI agentHome, final fURI sessionVID, final Rec config) {
        final Obj modelArg = config.at(uri(MODEL));
        final Obj scope = config.at(uri(SCOPE));
        final Obj kinds = config.at(uri(KIND));
        final Obj concepts = config.at(uri(CONCEPT));
        final Obj output = config.at(uri(TO));
        final fURI outputBase = output.isNoObj() ? agentHome : output.uriValue();
        // 1. collect this session's messages from the ledger as rels
        //    (vid => rec) — the rel key IS the message vid (branch read)
        final fURI messagesLocation = agentHome.extend(MESSAGE).extend("+/");
        final List<Rel> messages = Router.readFromSpace(messagesLocation)
                .stream()
                .map(Obj::asRel)
                .filter(pair -> {
                    final Obj sessionUri = pair.second().asRec().at(SESSION);
                    return sessionUri.isUri() && sessionUri.uriValue().equals(sessionVID);
                })
                .filter(pair -> withinScope(pair.second().asRec(), scope))
                .sorted(Comparator.comparing(pair -> Integer.parseInt(pair.first().uriValue().name())))
                .toList();
        if (messages.isEmpty())
            return fail("no messages found for session %s at %s", sessionVID, messagesLocation);
        // 2. build the distill digest — vid ==> text so the model can cite real vids
        final String digest = messages.stream()
                .filter(pair -> !Str.Helper.cleanString(pair.second().asRec().at(TEXT)).isBlank())
                .map(pair -> Str.Helper.cleanString(pair.first()) + "==>" + Str.Helper.cleanString(pair.second().asRec().at(TEXT).orElse(str(""))))
                .collect(Collectors.joining("\n"));
        // 3. the model — from the agent home (matches <agent>/model)
        final mModel model = modelArg.isNoObj() ? mModel.model(Router.readFromSpace(agentHome.extend(MODEL)).asRec()) : mModel.model(modelArg.asRec());
        // 4. distill via a mini-task
        final ChatResult result = Agent.Helper.miniChat("session_summarizer", model(model.at(TIMEOUT, real(5.0, MATH_MINUTE_TID, null))), SUMMARIZE_PROMPT.formatted(digest));
        // 5. parse the <<json:claim>> and <<json:loose_end>> blocks into vids
        final List<Obj> claimVids = new ArrayList<>();
        final List<Obj> looseEndVids = new ArrayList<>();
        final Obj blocks = result.at(uri(BLOCK)).orElse(noobj());
        if (!blocks.isNoObj()) {
            final Rec blocksRec = blocks.asRec();
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
        return rec(uri(SESSION), uri(sessionVID),
                uri(MODEL), model,
                uri(SCOPE), scope,
                uri(KIND), kinds,
                uri(CONCEPT), concepts,
                uri(TO), uri(outputBase),
                uri("claim"), lst(claimVids),
                uri("loose_end"), lst(looseEndVids));
    }

    /**
     * Compact a session's message ledger into a single {@code compaction_message::T}
     * sentinel whose {@code text} is a resume summary, stamped with the token
     * compression stats ({@code in}, {@code out}, {@code compression}).  The
     * trailing few messages are re-appended after the sentinel so the immediate
     * context is not lost in the summary.  Shared by the {@code compact} inst and
     * the CompactionFeature's background thread — the config rec has the same
     * vocabulary as the {@code <<mtron:compaction>>} block (agent, model, prompt).
     *
     * @param agentHome  the agent root — the model rec is resolved from
     *                   {@code <agentHome>/model} when the config's model is noobj
     * @param sessionVID the session whose ledger messages are compacted
     * @param config     the argument/block rec — {@code model} and {@code prompt}
     *                   override the summarizer's model and prompt template
     * @return the applied-constraints rec — the resolved [to, compaction=>vid]
     * plus the [in, out, compression] stats; a fail::T on error
     */
    public static Obj compactSession(final fURI agentHome, final fURI sessionVID, final Rec config) {
        final Obj modelArg = config.at(uri(MODEL));
        final Obj promptArg = config.at(uri(PROMPT));
        final Obj output = config.at(uri(TO));
        final fURI outputBase = output.isNoObj() ? agentHome : output.uriValue();
        // 1. collect this session's messages from the ledger, oldest -> newest
        final fURI messagesLocation = agentHome.extend(MESSAGE).extend("+/");
        final List<Rel> messages = Router.readFromSpace(messagesLocation)
                .stream()
                .map(Obj::asRel)
                .filter(pair -> {
                    final Obj sessionUri = pair.second().asRec().at(SESSION);
                    return sessionUri.isUri() && sessionUri.uriValue().equals(sessionVID);
                })
                .sorted(Comparator.comparing(pair -> Integer.parseInt(pair.first().uriValue().name())))
                .toList();
        if (messages.isEmpty())
            return fail("no messages found for session %s at %s", sessionVID, messagesLocation);
        // 2. build the conversation digest — text only, so the model sees content not vids
        final String digest = messages.stream()
                .map(pair -> Str.Helper.cleanString(pair.second().asRec().at(TEXT).orElse(str(""))))
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("\n-----\n"));
        // 3. the summarizer model (agent home model when not given) and prompt
        final mModel model = modelArg.isNoObj()
                ? mModel.model(Router.readFromSpace(agentHome.extend(MODEL)).asRec())
                : mModel.model(modelArg.asRec());
        final String prompt = promptArg.isNoObj() ? COMPACT_PROMPT : promptArg.strValue();
        // 4. distill via a mini-task
        final ChatResult result = Agent.Helper.miniChat("session_compactor", model(model.at(TIMEOUT, real(5.0, MATH_MINUTE_TID, null))), prompt.formatted(digest));
        final String summary = Str.Helper.cleanString(result.at(CHAT).orElse(str("")));
        // 5. write the sentinel + pair-safe recent-tail
        final Rec sentinel = writeCompaction(agentHome, sessionVID, messages, digest, summary);
        return rec(uri(TO), uri(outputBase),
                uri("compaction"), uri(sentinel.vid()),
                uri(IN), sentinel.at(uri(IN)),
                uri(OUT), sentinel.at(uri(OUT)),
                uri(COMPRESSION), sentinel.at(uri(COMPRESSION)));
    }

    /**
     * Write the compaction sentinel — its {@code text} is the resume summary,
     * stamped with {@code in}/{@code out}/{@code compression} token stats — then
     * re-append the recent-tail after it, pair-safe (a {@code tool_result} is
     * never orphaned from its {@code ai} message).  Extracted from
     * {@link #compactSession} so the write-path is testable without an LLM
     * round-trip.
     *
     * @param agentHome  the agent root — the sentinel/tail write under {@code <agentHome>/message/}
     * @param sessionVID the session the sentinel belongs to
     * @param messages   the session's messages, oldest -> newest, as ledger rels
     * @param digest     the conversation digest (drives the {@code in} token stat)
     * @param summary    the resume summary (the sentinel's {@code text})
     * @return the written sentinel rec (text + in/out/compression + session/depth)
     */
    public static Rec writeCompaction(final fURI agentHome, final fURI sessionVID, final List<Rel> messages, final String digest, final String summary) {
        final MessageFeature.DefaultTokenCountEstimator estimator = MessageFeature.DefaultTokenCountEstimator.singleton();
        final int tokensIn = estimator.estimateTokenCountInText(digest);
        final int tokensOut = estimator.estimateTokenCountInText(summary);
        final double compression = tokensIn == 0 ? 0.0 : 1.0 - ((double) tokensOut / (double) tokensIn);
        final fURI writePath = agentHome.extend(MESSAGE).extend("_").addQ(INCRQ);
        final Rec sentinel = MessageBuilder.build(COMPACTION_MESSAGE_TID)
                .text(summary)
                .time()
                .session(sessionVID)
                .depth(1)
                .put(IN, jnt(tokensIn))
                .put(OUT, jnt(tokensOut))
                .put(COMPRESSION, real(compression))
                .create(writePath);
        final int SPILL_OVER = 5; // recent-tail — keep the immediate context raw, not just in the summary
        // only re-append the conversational kinds — system/thinking/compaction
        // are metatron-world records (SystemFeature re-writes the system message
        // each turn), and a system message in the tail would break the model's
        // "system message must be at the beginning" invariant
        final List<Rel> conversational = messages.stream()
                .filter(pair -> {
                    final fURI tid = pair.second().tid();
                    return tid.equals(USER_MESSAGE_TID) || tid.equals(AI_MESSAGE_TID) || tid.equals(TOOL_RESULT_MESSAGE_TID);
                })
                .toList();
        int skip = Math.max(0, conversational.size() - SPILL_OVER);
        // pull in more (never fewer) messages so the tail never starts on an
        // orphaned tool_result or an ai message without its user message
        skip = SpaceChatSessionStore.adjustSkipToPreservePairs(conversational, skip);
        for (int i = skip; i < conversational.size(); i++) {
            final Rec tail = conversational.get(i).second().asRec();
            MessageBuilder.build(tail.tid()).copy(tail.jvm()).create(writePath);
        }
        return sentinel;
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

    /**
     * Registers lifecycle hook lambdas on a feature by reflectively detecting
     * which stage methods the feature's concrete class overrides.
     * Only methods that are directly implemented (not inherited from the
     * no-op defaults in {@link AbstractFeature})
     * get wired up — no manual stage lists needed.
     *
     * @param feature the feature to register hooks on
     */
    @SuppressWarnings("unchecked")
    private static Obj createStageLambdas(final Obj feature) {
        for (final StageDef def : STAGE_DEFS) {
            try {
                if (feature instanceof AbstractFeature featureObj) {
                    if (featureObj.at(uri(def.stageName)).isNoObj()) {
                        final Method method = featureObj.getClass().getMethod(def.methodName, def.paramTypes);
                        if (method.getDeclaringClass() != AbstractFeature.class &&
                                method.getDeclaringClass() != Feature.class) {
                            featureObj.at(uri(def.stageName), def.lambdaFactory.apply(featureObj), MUTABLE);
                        }
                    }
                } else if (feature instanceof Rec) {
                    feature.logger().warn("mtron native feature loaded: %s", feature.tid());
                }
            } catch (final NoSuchMethodException e) {
                // All methods are declared on Feature — this should never happen
            }
        }
       /* try {
            if (f instanceof AbstractFeature featureObj) {
                if (featureObj.at(uri(SKILL)).isNoObj()) {
                    final Method skillMethod = featureObj.getClass().getMethod(SKILL);
                    if (skillMethod.getDeclaringClass() != AbstractFeature.class) {
                        featureObj.at(uri(SKILL), featureObj.skill(), MUTABLE);
                    }
                }
            }
        } catch (final NoSuchMethodException e) {
            // All methods are declared on Feature — this should never happen
        }*/
        return feature;
    }

    // ---- stage hook definitions -----------------------------------------

    private record StageDef(
            String stageName,
            String methodName,
            Class<?>[] paramTypes,
            java.util.function.Function<AbstractFeature, Call> lambdaFactory
    ) {
    }

    private static final List<StageDef> STAGE_DEFS = List.of(
            new StageDef(ON_AGENT_CTOR, "onAgentCtor", new Class<?>[]{Agent.class},
                    f -> instLambda((agent, ignored) -> {
                        f.onAgentCtor((Agent) agent);
                        return noobj();
                    })),
            new StageDef(ON_BEFORE_CHAT, "onBeforeChat", new Class<?>[]{Agent.class},
                    f -> instLambda(ALL.maybe(), ALL.maybeSome(), (agent, ignored) -> f.onBeforeChat((Agent) agent))),
            new StageDef(ON_PARTIAL_RESPONSE, "onPartialResponse", new Class<?>[]{Agent.class, Str.class},
                    f -> instLambda(ALL.maybe(), NOOBJ_TID.zero(), (agent, i) -> {
                        f.onPartialResponse((Agent) agent, i.arg(0).asStr());
                        return noobj();
                    })),
            new StageDef(ON_PARTIAL_THINKING, "onPartialThinking", new Class<?>[]{Agent.class, Str.class},
                    f -> instLambda(ALL.maybe(), NOOBJ_TID.zero(), (agent, i) -> {
                        f.onPartialThinking((Agent) agent, i.arg(0).asStr());
                        return noobj();
                    })),
            new StageDef(ON_PARTIAL_TOOL_CALL, "onPartialToolCall", new Class<?>[]{Agent.class, Inst.class},
                    f -> instLambda(ALL.maybe(), NOOBJ_TID.zero(), (agent, i) -> {
                        f.onPartialToolCall((Agent) agent, (Inst) i.arg(0));
                        return noobj();
                    })),
            new StageDef(BEFORE_TOOL_EXECUTION, "beforeToolExecution", new Class<?>[]{Agent.class, Inst.class},
                    f -> instLambda(ALL.maybe(), NOOBJ_TID.zero(), (agent, i) -> {
                        f.beforeToolExecution((Agent) agent, (Inst) i.arg(0));
                        return noobj();
                    })),
            new StageDef(ON_TOOL_EXECUTED, "onToolExecuted", new Class<?>[]{Agent.class, Obj.class},
                    f -> instLambda(ALL.maybe(), NOOBJ_TID.zero(), (agent, i) -> {
                        f.onToolExecuted((Agent) agent, i.arg(0));
                        return noobj();
                    })),
            new StageDef(ON_COMPLETE_RESPONSE, "onCompleteResponse", new Class<?>[]{Agent.class, ChatResult.class},
                    f -> instLambda(ALL.maybe(), NOOBJ_TID.zero(), (agent, i) -> {
                        f.onCompleteResponse((Agent) agent, (ChatResult) i.arg(0));
                        return noobj();
                    })),
            new StageDef(ON_ERROR, "onError", new Class<?>[]{Agent.class, Fail.class},
                    f -> instLambda(ALL.maybe(), NOOBJ_TID.zero(), (agent, ignored) -> {
                        f.onError((Agent) agent, null);
                        return noobj();
                    }))
    );

    /*
       return new LinkedHashMap<>() {{
            put(uri(NAME), uri(model.getModelName()));
            put(uri("size"), jnt(model.getSize()));
            put(uri("quant"), uri(model.getModelMeta().getQuantizationLevel()));
            put(uri("family"), uri(model.getModelMeta().getFamily()));
            //   put(uri("card"), rec(model.get1().getModelInfo(), MObjFactory.of()));
        }};
     */
}
