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
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatFrame;
import studio.phaseshift.metatron.isa.llm.type.feature.*;
import studio.phaseshift.metatron.isa.llm.type.feature.Feature;
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.vec.type.MVec;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCS_TID;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.type.Agent.agent;
import static studio.phaseshift.metatron.isa.llm.type.mModel.model;
import static studio.phaseshift.metatron.isa.llm.type.mcp.mcpMessageServer.MCP_MESSAGE_SERVER_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Fail.FAIL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.sys.space.fsSpace.staticObjToFile;
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
    public static final fURI LLM_FRAME_TID = LLM_ISA_TID.extend("frame");
    public static final fURI LLM_WATERMARK_TID = LLM_ISA_TID.extend(WATERMARK);
    public static final fURI LLM_FEATURE_TID = LLM_ISA_TID.extend(FEATURE);
    public static final fURI LLM_SPACE_TID = LLM_ISA_TID.extend(SPACE);
    public static final fURI LLM_TOOL_TID = LLM_ISA_TID.extend(TOOL);
    public static final fURI LLM_CONCEPT_TID = LLM_ISA_TID.extend(CONCEPT);
    public static final fURI LLM_TODO_TID = LLM_ISA_TID.extend(TODO);
    public static final fURI LLM_SESSION_TID = LLM_ISA_TID.extend(SESSION);
    public static final fURI LLM_ITERATION_TID = LLM_ISA_TID.extend(ITERATION);
    public static final fURI LLM_CLAIM_TID = LLM_ISA_TID.extend("claim");
    public static final fURI LLM_LOOSE_END_TID = LLM_ISA_TID.extend("loose_end");
    public static final fURI LLM_SKILL_TID = LLM_ISA_TID.extend(SKILL);
    public static final fURI LLM_MESSAGE_TID = LLM_ISA_TID.extend(MESSAGE);
    public static final fURI AI_MESSAGE_TID = LLM_MESSAGE_TID.extend(AI);
    public static final fURI USER_MESSAGE_TID = LLM_MESSAGE_TID.extend(USER);
    public static final fURI SYSTEM_MESSAGE_TID = LLM_MESSAGE_TID.extend(SYSTEM);
    public static final fURI TOOL_REQUEST_MESSAGE_TID = LLM_MESSAGE_TID.extend("tool_request");
    public static final fURI TOOL_RESULT_MESSAGE_TID = LLM_MESSAGE_TID.extend("tool_result");
    public static final fURI THINKING_MESSAGE_TID = LLM_MESSAGE_TID.extend("thinking");
    public static final fURI COMPACTION_MESSAGE_TID = LLM_MESSAGE_TID.extend("compaction");
    /**
     * The mid-chat subtype: an ordinary {@code user_message} or {@code ai_message}
     * that belongs to a mid-iteration exchange rather than to a real turn.
     *
     * <p>A subtype carried in the {@code sub} field rather than a new message tid,
     * so LC4j keeps building its memory from the base tid — a conversation
     * conducted mid-iteration therefore simply <em>becomes</em> history on the next
     * turn, with no projection to maintain.  What the subtype buys is provenance:
     * the window can tell a real prompt from a mid-chat remark.
     */
    public static final fURI USER_MIDCHAT_TID = USER_MESSAGE_TID.extend("midchat");
    public static final fURI AI_MIDCHAT_TID = AI_MESSAGE_TID.extend("midchat");
    //public static final fURI MCP_TOOL_TID = LLM_ISA_TID.extend("mcp");
    // public static Obj MTRON_EVAL_TOOL = mModel.Helper.mtronInstToolSpecification(ObjType.insts().stream().filter(i -> i.tid().equals(EVAL_INST_TID)).findFirst().orElse(null));    
    public static final fURI LLM_CHAT_FEATURE_TID = LLM_FEATURE_TID.extend("chat_feature");
    public static final fURI LLM_TOKEN_MESSAGE_FEATURE_TID = LLM_FEATURE_TID.extend("token_message_feature");
    public static final fURI LLM_WINDOW_MESSAGE_FEATURE_TID = LLM_FEATURE_TID.extend("window_message_feature");
    public static final fURI LLM_PERSISTED_FRAME_FEATURE_TID = LLM_FEATURE_TID.extend("persisted_frame_feature");
    public static final fURI LLM_TRANSIENT_FRAME_FEATURE_TID = LLM_FEATURE_TID.extend("transient_frame_feature");
    public static final fURI LLM_TOOL_FEATURE_TID = LLM_FEATURE_TID.extend("tool_feature");
    public static final fURI LLM_SYSTEM_FEATURE_TID = LLM_FEATURE_TID.extend("system_feature");
    public static final fURI LLM_NOTE_FEATURE_TID = LLM_FEATURE_TID.extend("note_feature");
    public static final fURI LLM_RECALL_FEATURE_TID = LLM_FEATURE_TID.extend("recall_feature");
    public static final fURI LLM_EMBED_FEATURE_TID = LLM_FEATURE_TID.extend("embed_feature");
    public static final fURI LLM_SKILL_FEATURE_TID = LLM_FEATURE_TID.extend("skill_feature");
    public static final fURI LLM_TODO_FEATURE_TID = LLM_FEATURE_TID.extend("todo_feature");
    public static final fURI LLM_THINK_FEATURE_TID = LLM_FEATURE_TID.extend("think_feature");
    public static final fURI LLM_TAGGING_CONCEPT_FEATURE_TID = LLM_FEATURE_TID.extend("tagging_concept_feature");
    public static final fURI LLM_AGENT_CONCEPT_FEATURE_TID = LLM_FEATURE_TID.extend("agent_concept_feature");
    public static final fURI LLM_LUCENE_CONCEPT_FEATURE_TID = LLM_FEATURE_TID.extend("lucene_concept_feature");
    public static final fURI LLM_COMPACTION_FEATURE_TID = LLM_FEATURE_TID.extend("compaction_feature");
    public static final fURI LLM_LAMBDA_FEATURE_TID = LLM_FEATURE_TID.extend("lambda_feature");
    public static final fURI LLM_COMMENT_FEATURE_TID = LLM_FEATURE_TID.extend("comment_feature");
    public static final fURI LLM_SUMMARIZE_FEATURE_TID = LLM_FEATURE_TID.extend("summarize_feature");
    public static final fURI LLM_COST_FEATURE_TID = LLM_FEATURE_TID.extend("cost_feature");
    public static final fURI LLM_AUDIT_FEATURE_TID = LLM_FEATURE_TID.extend("audit_feature");
    public static final fURI LLM_LOOP_FEATURE_TID = LLM_FEATURE_TID.extend("loop_feature");
    public static final fURI LLM_LEDGER_FEATURE_TID = LLM_FEATURE_TID.extend("ledger_feature");
    public static final fURI LLM_MIDCHAT_FEATURE_TID = LLM_FEATURE_TID.extend("midchat_feature");
    public static final fURI LLM_ITERATION_FEATURE_TID = LLM_FEATURE_TID.extend("iteration_feature");
    /// ///////////////////////
    // service tids — the capability registry vocabulary (a feature offers/requires/uses these;
    // non-features can offer them too, so they are distinct from feature tids)
    public static final fURI LLM_SERVICE_TID = LLM_ISA_TID.extend("service");
    public static final fURI LLM_TOOL_SERVICE_TID = LLM_SERVICE_TID.extend("tool");
    public static final fURI LLM_SKILL_SERVICE_TID = LLM_SERVICE_TID.extend("skill");
    public static final fURI LLM_SYSTEM_SERVICE_TID = LLM_SERVICE_TID.extend("system");
    public static final fURI LLM_MESSAGE_SERVICE_TID = LLM_SERVICE_TID.extend("message");
    public static final fURI LLM_CONCEPT_SERVICE_TID = LLM_SERVICE_TID.extend("concept");
    public static final fURI LLM_CHAT_SERVICE_TID = LLM_SERVICE_TID.extend("chat");
    public static final fURI LLM_THINK_SERVICE_TID = LLM_SERVICE_TID.extend("think");
    public static final fURI LLM_FRAME_SERVICE_TID = LLM_SERVICE_TID.extend("frame");
    //public static final fURI LLM_SKILL_FEATU

    public static Type LLM_MODEL_TYPE;
    public static Type LLM_AGENT_TYPE;
    public static Type LLM_AI_MESSAGE_TYPE;
    public static Type LLM_USER_MESSAGE_TYPE;
    public static Type LLM_SYSTEM_MESSAGE_TYPE;
    public static Type LLM_SKILL_TYPE;
    public static Type LLM_SESSION_TYPE;
    public static Type LLM_ITERATION_TYPE;
    public static Type LLM_CONCEPT_TYPE;
    public static Type LLM_CLAIM_TYPE;
    public static Type LLM_LOOSE_END_TYPE;
    public static Type LLM_MESSAGE_TYPE;
    public static Type LLM_TOOL_RESULT_MESSAGE_TYPE;
    public static Type LLM_TOOL_REQUEST_MESSAGE_TYPE;
    public static Type LLM_THINKING_MESSAGE_TYPE;
    public static Type LLM_COMPACTION_MESSAGE_TYPE;
    public static Type LLM_TODO_TYPE;
    public static Type LLM_TOOL_TYPE;
    public static Type LLM_CHAT_RESULT_TYPE;
    public static Type LLM_WATERMARK_TYPE;
    public static ObjFactory LLM_OBJ_FACTORY = MObjFactory.of().addExtension(MVec.class, x -> lst(x.jvm().stream().toList()));
    public static Type LLM_FEATURE_TYPE;

    public llmInstSet() {
        super(mutableMap(uri(PATTERN), uri(LLM_ISA_TID.extend(ALL))), INSTSET_TID, LLM_ISA_TID);
    }

    @Override
    public void setup() {
        // llm types reference mathInstSet.DATETIME_TYPE, which is only populated by
        // mathInstSet.setup(). The ServiceLoader order that loads inst sets is not a
        // stable contract, so ensure math is set up first rather than assuming it.
        if (null == DATETIME_TYPE)
            InstSet.importInstSet(MATH_ISA_TID);
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
                                                uri(TIMEOUT).maybe(), auto_from_(MATH_TIME_TID).tryToInst(),
                                                uri(SIZE).maybe(), auto_from_(MATH_DATASIZE_TID).tryToInst(),
                                                uri(QUANT).maybe(), INT_TYPE,
                                                uri(CONTEXT).maybe(), INT_TYPE,
                                                uri(COST).maybe(), rec(uri(IN), auto_from_(MATH_CURRENCY_TID).tryToInst(), uri(OUT), auto_from_(MATH_CURRENCY_TID).tryToInst()).maybe()))
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
                                                uri(TIME), auto_from_(MATH_TIME_TID).tryToInst()))
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
                        docWrap(LLM_CONCEPT_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_CONCEPT_TID)
                                        .isaPredicate(rec(
                                                uri(NAME), STR_TYPE,
                                                uri(CONCEPT).maybe(), lst(T(LLM_CONCEPT_TID.maybeSome())).maybe(),
                                                uri(MESSAGE).maybe(), lst(T(LLM_MESSAGE_TID.maybeSome())).maybe()))
                                        .create(), "", "", Map.of(
                                        uri(NAME), "the concept name",
                                        uri(CONCEPT).maybe(), "a lst of related concepts",
                                        uri(MESSAGE).maybe(), "a lst of related messages"),
                                """
                                a concept related to other concepts and messages.
                                """),
                        docWrap(LLM_TODO_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_TODO_TID)
                                        .isaPredicate(rec(
                                                uri(TEXT), STR_TYPE,
                                                uri(STATUS).maybe(), isa_(union_(uri(OPEN), uri(RUN), uri(BLOCK), uri(CLOSE))).else_(uri(OPEN)),
                                                uri(TIME).maybe(), isa_(DATETIME_TYPE).else_(instB(MATH_DATETIME_NOW_TID, lst())),
                                                uri(CONCEPT).maybe(), lst(T(LLM_CONCEPT_TID.maybeSome())),
                                                uri(MESSAGE).maybe(), lst(T(LLM_MESSAGE_TID.maybeSome())),
                                                uri(REFERENCE).maybe(), LST_TYPE))
                                        .create(), "", "", Map.of(
                                        uri(TEXT), "the todo information",
                                        uri(STATUS).maybe(), "current status of todo (default: open)",
                                        uri(TIME).maybe(), "datetime of todo creation (default: datetime_now())",
                                        uri(CONCEPT).maybe(), "concepts associated with todo",
                                        uri(MESSAGE).maybe(), "messages associated with todo",
                                        uri(REFERENCE).maybe(), "artifacts associated with todo"),
                                """
                                a todo represents a task to be completed.
                                links to concepts and messages are automatically attached to the todo at creation.
                                reference to artifacts in metatron can be attached.
                                """,
                                "@/agent/todo/1 >>= [reference => +[!*/project/src,!*/project/test]] [-- adding references to a todo --]"),
                        // CLAIM — a distilled proposition with provenance
                        docWrap(LLM_CLAIM_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_CLAIM_TID)
                                        .isaPredicate(rec(
                                                uri(TEXT), STR_TYPE,
                                                uri(KIND), union_(
                                                        uri("decision"),
                                                        uri("problem"),
                                                        uri("solution"),
                                                        uri("observation")).tryToInst(),
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
                                                uri(STATUS), union_(
                                                        uri("open"),
                                                        uri("in_progress"),
                                                        uri("resolved"),
                                                        uri("abandoned")).tryToInst(),
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
                                        .tid(LLM_MESSAGE_TID)
                                        .vid(SYSTEM_MESSAGE_TID)
                                        .isaPredicate(rec(uri(TEXT), STR_TYPE))
                                        //   uri(SIZE), DATA_SIZE_TYPE))
                                        .create(),
                                null, null,
                                Map.of(uri(TEXT), "the system message text body"),
                                //  uri(SIZE), "the data size of the text body"),
                                "a system message provides behavioral and response-style instructions to the model"),
                        docWrap(LLM_WATERMARK_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_WATERMARK_TID)
                                        .isaPredicate(rec(
                                                uri(TAG), STR_TYPE,
                                                uri(KEY), STR_TYPE,
                                                uri(BODY).maybe(), STR_TYPE,
                                                uri(OBJ).maybe(), ALL_TYPE,
                                                uri(ERROR).maybe(), FAIL_TYPE,
                                                uri(INDEX).maybe(), INT_TYPE,
                                                uri(STAGE).maybe(), URI_TYPE))
                                        .create(),
                                null, null, mutableMap(
                                        uri(TAG), "the body codec the marker named — mtron, json, txt, html, md, xml, bson",
                                        uri(KEY), "the feature the model addressed — loop, summarize, compaction, embed, midchat, ...",
                                        uri(BODY).maybe(), "the raw payload text, trimmed of its surrounding whitespace",
                                        uri(OBJ).maybe(), "the body decoded by tag — the deferred call's argument rec",
                                        uri(ERROR).maybe(), "why the body did not decode; the marker is still stripped and still recorded",
                                        uri(INDEX), "ordinal position of the marker in the model's output",
                                        uri(STAGE), "the lifecycle stage it was harvested at — on_complete_response, on_partial_thinking, ..."),
                                "one in-band control marker a model wrote into its own output: which feature it addresses, the argument rec of the call it defers, and markup that is removed from the visible text"),
                        docWrap(LLM_CHAT_RESULT_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_CHAT_RESULT_TID)
                                        .isaPredicate(rec(
                                                uri(CHAT).maybe().asUri(), ALL_TYPE,
                                                uri(TIME).maybe(), auto_from_(MATH_TIME_TID).tryToInst(),
                                                uri(WATERMARK).maybe(), lst(LLM_WATERMARK_TYPE),
                                                uri(ERROR).maybe(), FAIL_TYPE))
                                        .create(),
                                null, null, mutableMap(
                                        uri(CHAT), "the chat response — free-text str or structured rec per response format",
                                        uri(TIME), "elapsed time::T from user message to complete response",
                                        uri(WATERMARK).maybe(), "the in-band markers the model emitted, in order; their markup is stripped from chat",
                                        uri(ERROR).maybe(), "a fail chain if errors occurred"),
                                "a response message from a chat interaction"),
                        docWrap(LLM_USER_MESSAGE_TYPE = Type.Builder.build()
                                        .tid(LLM_MESSAGE_TID)
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
                                        .tid(LLM_MESSAGE_TID)
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
                                        .tid(LLM_MESSAGE_TID)
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
                                        .tid(LLM_MESSAGE_TID)
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
                                        .tid(LLM_MESSAGE_TID)
                                        .vid(THINKING_MESSAGE_TID)
                                        .isaPredicate(rec(
                                                uri(TEXT), STR_TYPE))
                                        .create(),
                                null, null,
                                Map.of(uri(TEXT), "the model's internal reasoning text"),
                                "a thinking/reasoning trace message — stored in the ledger but excluded from the LC4j chat window"),
                        docWrap(LLM_COMPACTION_MESSAGE_TYPE = Type.Builder.build()
                                        .tid(LLM_MESSAGE_TID)
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
                                        .vid(LLM_MESSAGE_TID)
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
                                                uri(TO).maybe().asUri(), ALL_TYPE,
                                                uri(ON_AGENT_CTOR).maybe(), ALL_TYPE,
                                                uri(ON_BEFORE_CHAT).maybe(), ALL_TYPE,
                                                uri(ON_PARTIAL_RESPONSE).maybe(), ALL_TYPE,
                                                uri(ON_PARTIAL_THINKING).maybe(), ALL_TYPE,
                                                uri(ON_PARTIAL_TOOL_CALL).maybe(), ALL_TYPE,
                                                uri(BEFORE_TOOL_EXECUTION).maybe(), ALL_TYPE,
                                                uri(ON_TOOL_EXECUTED).maybe(), ALL_TYPE,
                                                uri(ON_TOOL_RESULT).maybe(), ALL_TYPE,
                                                uri(ON_COMPLETE_RESPONSE).maybe(), ALL_TYPE,
                                                uri(ON_ERROR).maybe(), ALL_TYPE))
                                        .create(),
                                null, null, mutableMap(
                                        uri(ROOT).maybe(), "the root uri location of feature data",
                                        uri(TO).maybe(), "code to evaluate on the feature result",
                                        uri(ON_AGENT_CTOR).maybe(), "inst?noobj<=agent(){ [-- one time setup --] }",
                                        uri(ON_BEFORE_CHAT).maybe(), "inst?#{?}<=agent(){ [-- non-noobj to short-circuit --] }",
                                        uri(ON_PARTIAL_RESPONSE).maybe(), "inst?noobj<=agent(text=>str::T)",
                                        uri(ON_PARTIAL_THINKING).maybe(), "inst?noobj<=agent(text=>str::T)",
                                        uri(ON_PARTIAL_TOOL_CALL).maybe(), "inst?noobj<=agent(request=>call::T)",
                                        uri(BEFORE_TOOL_EXECUTION).maybe(), "inst?noobj<=agent(request=>call::T)",
                                        uri(ON_TOOL_EXECUTED).maybe(), "inst?noobj<=agent(result=>call::T)",
                                        uri(ON_TOOL_RESULT).maybe(), "inst?#{?}<=agent(result=>#{?},request_id=>str::T){ [-- the payload the model is handed: return it unchanged for a pass-through. the only stage dispatched from mToolExecutor, not the turn --] }",
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
                                .constructor(arg -> createStageLambdas(new SummarizeFeature(arg.asRec().jvm(), LLM_SUMMARIZE_FEATURE_TID, arg.vid())))
                                .create(),
                        Type.Builder.build()
                                .tid(LLM_FEATURE_TID)
                                .vid(LLM_TOKEN_MESSAGE_FEATURE_TID)
                                .isaPredicate(rec(SESSION, URI_TYPE))
                                .constructor(arg -> createStageLambdas(new TokenMessageFeature(arg.asRec().jvm(), LLM_TOKEN_MESSAGE_FEATURE_TID, arg.vid())))
                                .create(),
                        Type.Builder.build()
                                .tid(LLM_FEATURE_TID)
                                .vid(LLM_WINDOW_MESSAGE_FEATURE_TID)
                                .isaPredicate(rec(SESSION, URI_TYPE))
                                .constructor(arg -> createStageLambdas(new WindowMessageFeature(arg.asRec().jvm(), LLM_WINDOW_MESSAGE_FEATURE_TID, arg.vid())))
                                .create(),
                        Type.Builder.build()
                                .tid(LLM_FEATURE_TID)
                                .vid(LLM_PERSISTED_FRAME_FEATURE_TID)
                                .constructor(arg -> createStageLambdas(new PersistedFrameFeature(arg.asRec().jvm(), LLM_PERSISTED_FRAME_FEATURE_TID, arg.vid())))
                                .create(),
                        Type.Builder.build()
                                .tid(LLM_FEATURE_TID)
                                .vid(LLM_TRANSIENT_FRAME_FEATURE_TID)
                                .constructor(arg -> createStageLambdas(new TransientFrameFeature(arg.asRec().jvm(), LLM_TRANSIENT_FRAME_FEATURE_TID, arg.vid())))
                                .create(),
                        Type.Builder.build()
                                .tid(LLM_FEATURE_TID)
                                .vid(LLM_TOOL_FEATURE_TID)
                                .isaPredicate(rec(uri(TOOL).maybe().asUri(), T(LST_TID.maybe()), uri(MAX).maybe().asUri(), isa_(INT_TYPE).else_(jnt(-1))))
                                .constructor(arg -> createStageLambdas(new ToolFeature(arg.asRec().jvm(), LLM_TOOL_FEATURE_TID, arg.vid())))
                                .create(),
                        Type.Builder.build()
                                .tid(LLM_FEATURE_TID)
                                .vid(LLM_EMBED_FEATURE_TID)
                                .isaPredicate(rec(uri(f(MODEL)).maybe().asUri(), LLM_MODEL_TYPE))
                                .constructor(arg -> createStageLambdas(new EmbedFeature(arg.asRec().jvm(), LLM_EMBED_FEATURE_TID, arg.vid())))
                                .create(),
                        Type.Builder.build()
                                .tid(LLM_FEATURE_TID)
                                .vid(LLM_SKILL_FEATURE_TID)
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
                                        .vid(LLM_MIDCHAT_FEATURE_TID)
                                        .constructor(arg -> createStageLambdas(new MidChatFeature(arg.asRec().jvm(), LLM_MIDCHAT_FEATURE_TID, arg.vid())))
                                        .create(),
                                null, null,
                                Map.of(),
                                "the mid-chat channel: relays what the model says to the user mid-iteration, and carries what the user says back through the tool result of the call it answered"),
                        docWrap(Type.Builder.build()
                                        .tid(LLM_FEATURE_TID)
                                        .vid(LLM_TAGGING_CONCEPT_FEATURE_TID)
                                        .constructor(arg -> createStageLambdas(new TaggingConceptFeature(arg.asRec().jvm(), LLM_TAGGING_CONCEPT_FEATURE_TID, arg.vid())))
                                        .create(),
                                null, null,
                                mutableMap(),
                                "extracts concepts from the agent response and thinking stream by parsing inline <<concept:>> tags"),
                        docWrap(Type.Builder.build()
                                        .tid(LLM_FEATURE_TID)
                                        .vid(LLM_AGENT_CONCEPT_FEATURE_TID)
                                        .constructor(arg -> createStageLambdas(new AgentConceptFeature(arg.asRec().jvm(), LLM_AGENT_CONCEPT_FEATURE_TID, arg.vid())))
                                        .create(),
                                null, null,
                                mutableMap(),
                                "extracts concepts from the agent response and thinking stream via a translator LLM"),
                        docWrap(Type.Builder.build()
                                        .tid(LLM_FEATURE_TID)
                                        .vid(LLM_LUCENE_CONCEPT_FEATURE_TID)
                                        .constructor(arg -> createStageLambdas(new LuceneConceptFeature(arg.asRec().jvm(), LLM_LUCENE_CONCEPT_FEATURE_TID, arg.vid())))
                                        .create(),
                                null, null,
                                mutableMap(),
                                "extracts concepts from the agent response and thinking stream by TF-IDF over a Lucene message index"),
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
                                                uri(RATE), rec(
                                                        uri(IN), auto_from_(MATH_CURRENCY_TID).tryToInst(),
                                                        uri(OUT), auto_from_(MATH_CURRENCY_TID).tryToInst())))
                                        .constructor(arg -> createStageLambdas(new CostFeature(arg.asRec().jvm(), LLM_COST_FEATURE_TID, arg.vid())))
                                        .create(),
                                null, null, mutableMap(
                                        uri(f(RATE).extend(IN)), "cost per million input tokens",
                                        uri(f(RATE).extend(OUT)), "cost per million output tokens"
                                ),
                                "tracks real token-based LLM costs via CostCalculator, persists in/out/total to space",
                                "cost_feature::[root=>/usr/dr/cost,cost=>[in_cost=>usd_currency::0.065,out_cost=>usd_currency::0.001]]"),
                        docWrap(Type.Builder.build()
                                        .tid(LLM_FEATURE_TID)
                                        .vid(LLM_LAMBDA_FEATURE_TID)
                                        .isaPredicate(rec(
                                                uri(STAGE).maybe().asUri(), rec(union_(Stream.of(Feature.Stage.values()).map(v -> uri(v.name())).toList().toArray(Obj[]::new)).tryToInst(), LST_TYPE).maybe()))
                                        .constructor(arg -> createStageLambdas(new LambdaFeature(arg.asRec().jvm(), LLM_LAMBDA_FEATURE_TID, arg.vid())))
                                        .create(),
                                null, null, mutableMap(uri(STAGE).maybe(), "lambdas to execute at the different lifecycle stages"),
                                "supports arbitrary instructions to be run at the different stages of the llm's lifecycle"),
                        docWrap(Type.Builder.build()
                                        .tid(LLM_FEATURE_TID)
                                        .vid(LLM_TODO_FEATURE_TID)
                                        .constructor(arg -> createStageLambdas(new ToDoFeature(arg.asRec().jvm(), LLM_TODO_FEATURE_TID, arg.vid())))
                                        .create(),
                                "persistent agent-owned todo list for cross-turn task tracking"),
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
                                                uri("max_time").maybe().asUri(), TIME_TYPE,
                                                uri("delay").maybe().asUri(), TIME_TYPE,
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
                        docWrap(Type.Builder.build()
                                        .vid(REC_TID)
                                        .tid(LLM_ISA_TID.extend("session_or_agent"))
                                        .isaPredicate(union_(LLM_AGENT_TYPE, LLM_SESSION_TYPE).tryToInst())
                                        .create(),
                                "a session::T or agent::T union"),
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
                                "communicate with an agent. if the agent is already executing, the chat message is pushed on their stack at *<agent>/message_stack", // desc
                                "@agent.chat('what is a database?')"),
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
                        // LEDGER FSCK — the repair for what interrupt leaves behind.
                        // Deliberately manual: a ledger that needs this regularly means
                        // the write path is broken, which is worth finding out, not
                        // hiding behind a sweep on every boot.

                        docWrap(instC(LLM_INST_TID.extend("sweep").dom(LLM_ISA_TID.extend("session_or_agent").maybe()).rng(REC_TID),
                                        rec(uri("session_or_agent").maybe().asUri(), T(LLM_ISA_TID.extend("session_or_agent").maybe()),
                                                uri("repair").maybe().asUri(), BOOL_TYPE,
                                                uri("prune").maybe().asUri(), BOOL_TYPE),
                                        (lhs, inst) -> {
                                            // the session arrives as the lhs — anchored (@/usr/dr/session/1),
                                            // deref'd (*/usr/dr/session/1) — or handed in as arg 0
                                            final Obj target = inst.arg(f("session_or_agent"), 0).orElse(lhs);
                                            final boolean repair = inst.arg(f("repair"), 1).booleanCheck();
                                            final boolean prune = inst.arg(f("prune"), 2).booleanCheck();
                                            return AbstractMessageFeature.sweep(AbstractMessageFeature.rootFor(target), repair, prune);
                                        }),
                                "the session whose ledger is swept — a session::T row; defaults to the lhs",
                                "a rec of call ids per failure mode — every key always present, so it can be counted without inspecting its shape",
                                mutableMap(jnt(0), "a session or agent to sweep; defaults to the lhs",
                                        jnt(1), "true to repair — drop the unanswered requests; no row is removed",
                                        jnt(2), "true to also DELETE what cannot be salvaged — duplicate messages and orphan results — which is the only way to clear those"),
                                "fsck a chat ledger: [duplicate=>[call ids written twice], orphan=>[requests whose result is nowhere], misplaced=>[requests whose result is not next to them], misscoped=>[requests whose results stand next to them but are stamped into another scope, so the store's projection tears the group apart], orphan_result=>[results no request precedes]]. A provider requires the results of an assistant message's tool_calls to sit immediately after it, as the store projects that turn (one session, one depth, one chat id) — anything else breaks every later chat with insufficient tool messages following tool_calls message. repair restores validity without deleting anything: an unanswered request is dropped, and a misscoped result is moved into the scope of the request it answers; prune is the opt-in to deletion, and is separate because a duplicate's surviving copy may not carry the same text",
                                "@/usr/dr/session/1.sweep().at('duplicate')                            [-- the groups written twice --]",
                                "@/usr/dr/session/1.sweep(repair=>true)                                [-- drop the unanswered requests --]",
                                "@/usr/dr/session/1.sweep(repair=>true,prune=>true).at('duplicate')    [-- and delete the duplicates --]"),
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
                        docWrap(instC(LLM_INST_TID.extend("summary").dom(LLM_ISA_TID.extend("session_or_agent").maybe()).rng(REC_TID), rec(
                                                uri("session_or_agent").maybe().asUri(), T(LLM_ISA_TID.extend("session_or_agent").maybe()),
                                                uri(MODEL).maybe().asUri(), choose_(rec(
                                                        isa_(LLM_MODEL_TYPE).tryToInst(), id_().tryToInst(),
                                                        isa_(LLM_SESSION_TYPE).tryToInst(), from_(rshift_(uri(AGENT)).mult_(uri(MODEL))).tryToInst()))
                                                        .rshift_().tryToInst(),
                                                uri(SCOPE).maybe().asUri(), union_(T(MATH_TIME_TID), T(MATH_DATETIME_TID)).tryToInst(),
                                                uri(KIND).maybe().asUri(), LST_TYPE,
                                                uri(CONCEPT).maybe().asUri(), LST_TYPE),
                                        (lhs, inst) -> {
                                            // The session_or_agent may arrive as the lhs (fluent: @dr/session/1.summarize(_))
                                            // or as arg 0 (function form: summarize(@dr/session/1)).
                                            final Obj sessionOrAgent = inst.arg(f("session_or_agent"), 0).orElse(lhs);
                                            final AbstractMessageFeature.SessionAddress address = AbstractMessageFeature.addressOf(sessionOrAgent);
                                            if (null == address.sessionVID() || address.sessionVID().isEmpty())
                                                return fail("summarize requires an anchored session — use @dr/session/N.summarize()");
                                            // the argument rec — same vocabulary as the <<mtron:summarize>> block
                                            // (session/model are summary()-only keys; the block uses scope/kinds/concepts)
                                            final Rec config = rec(uri(SESSION), uri(address.sessionVID()),
                                                    uri(MODEL), inst.arg(f(MODEL), 1),
                                                    uri(SCOPE), inst.arg(f(SCOPE), 2),
                                                    uri(KIND), inst.arg(f(KIND), 3),
                                                    uri(CONCEPT), inst.arg(f(CONCEPT), 4),
                                                    uri(TO), uri(address.agentHome()));
                                            return SummarizeFeature.summarizeSession(address.agentHome(), address.sessionVID(), config);
                                        }),
                                "a session to distill",
                                "the applied constraints rec — [session, model, scope, kind, concept, to, claim=>[vids], loose_end=>[vids]]",
                                mutableMap(),
                                "distill a session's message ledger into claim::T and loose_end::T recs via a mini-task — the same call as the <<mtron:summarize>> block (they share the argument rec::T vocabulary: session, model, scope, kind, concept, to)",
                                "@dr/session/1.summarize(_)  [-- fluent --]  |  summarize(@dr/session/1)  [-- function --]"),
                        // COMPACT INSTRUCTION — compact a session's ledger into a resume sentinel
                        docWrap(instC(LLM_INST_TID.extend("compact").dom(LLM_ISA_TID.extend("session_or_agent").maybe()).rng(REC_TID), rec(
                                                uri("session_or_agent").maybe().asUri(), T(LLM_ISA_TID.extend("session_or_agent").maybe()),
                                                uri(MODEL).maybe().asUri(), LLM_MODEL_TYPE,
                                                uri(PROMPT).maybe().asUri(), STR_TYPE),
                                        (lhs, inst) -> {
                                            // The session_or_agent may arrive as the lhs (fluent: @dr.compact())
                                            // or as arg 0 (function form: compact(@dr)).
                                            final Obj sessionOrAgent = inst.arg(f("session_or_agent"), 0).orElse(lhs);
                                            final AbstractMessageFeature.SessionAddress address = AbstractMessageFeature.addressOf(sessionOrAgent);
                                            if (null == address.sessionVID() || address.sessionVID().isEmpty())
                                                return fail("compact requires an anchored agent or session — use @dr.compact() or compact(@dr)");
                                            final Rec config = (lhs.isRec() ? lhs.asRec() : rec())
                                                    .at(MODEL, noobj())
                                                    .at(PROMPT, noobj())
                                                    .at(TO, noobj()).plus(
                                                            rec(uri(MODEL), inst.arg(f(MODEL), 1),
                                                                    uri(PROMPT), inst.arg(f(PROMPT), 2),
                                                                    uri(TO), uri(address.agentHome())));
                                            return CompactionFeature.compactSession(address.agentHome(), address.sessionVID(), config);
                                        }),
                                "a session or agent to compact",
                                "the applied constraints rec — [to, compaction=>vid, in, out, compression]",
                                mutableMap(jnt(0), "the session or agent to compact (defaults to the lhs)",
                                        jnt(1), "the summarizer model (default: the agent home model)",
                                        jnt(2), "the summarizer prompt template"),
                                "compact a session's message ledger into a resume summary sentinel — the same call as the <<mtron:compaction>> block (they share the argument rec::T vocabulary: agent, model, prompt)",
                                "@dr.compact()  [-- fluent --]  |  compact(@dr)  [-- function --]"))));
        docWrap(this, "large language model think and reason within the metatron");
        super.setup();
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
            // thinking is the one stage Agent does not dispatch: ThinkFeature owns it,
            // seeds the thought, and cascades it through the features that have this hook
            new StageDef(ON_PARTIAL_THINKING, "onPartialThinking", new Class<?>[]{Agent.class, Obj.class},
                    f -> instLambda(ALL.maybe(), ALL.maybe(), (agent, i) ->
                            f.onPartialThinking((Agent) agent, i.arg(0)))),
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
            // the one stage whose value is consumed: mToolExecutor folds it over the
            // payload instead of the turn dropping it
            new StageDef(ON_TOOL_RESULT, "onToolResult", new Class<?>[]{Agent.class, Obj.class, String.class},
                    f -> instLambda(ALL.maybe(), ALL.maybe(), (agent, i) ->
                            f.onToolResult((Agent) agent, i.arg(0), Str.Helper.cleanString(i.arg(1))))),
            new StageDef(ON_COMPLETE_RESPONSE, "onCompleteResponse", new Class<?>[]{Agent.class, ChatFrame.class},
                    f -> instLambda(ALL.maybe(), NOOBJ_TID.zero(), (agent, i) -> {
                        f.onCompleteResponse((Agent) agent, (ChatFrame) i.arg(0));
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
