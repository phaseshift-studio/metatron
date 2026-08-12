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

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCS_TID;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.type.Agent.agent;
import static studio.phaseshift.metatron.isa.llm.type.Model.model;
import static studio.phaseshift.metatron.isa.llm.type.mTool.LLM_TOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.Fail.FAIL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace.staticObjToFile;
import static studio.phaseshift.metatron.isa.mach.machInstSet.DIR_TID;
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
    public static final fURI LLM_TOOL_TID = LLM_FEATURE_TID.extend(TOOL);
    public static final fURI LLM_SESSION_TID = LLM_FEATURE_TID.extend(SESSION);
    public static final fURI LLM_ITERATION_TID = LLM_ISA_TID.extend(ITERATION);
    public static final fURI LLM_SKILL_TID = LLM_FEATURE_TID.extend(SKILL);
    public static final fURI MESSAGE_TID = LLM_ISA_TID.extend(MESSAGE);
    public static final fURI AI_MESSAGE_TID = MESSAGE_TID.extend(AI);
    public static final fURI USER_MESSAGE_TID = MESSAGE_TID.extend(USER);
    public static final fURI SYSTEM_MESSAGE_TID = MESSAGE_TID.extend(SYSTEM);
    public static final fURI TOOL_REQUEST_MESSAGE_TID = MESSAGE_TID.extend("tool_request");
    public static final fURI TOOL_RESULT_MESSAGE_TID = MESSAGE_TID.extend("tool_result");
    public static final fURI THINKING_MESSAGE_TID = MESSAGE_TID.extend("thinking");
    //public static final fURI MCP_TOOL_TID = LLM_ISA_TID.extend("mcp");
    // public static Obj MTRON_EVAL_TOOL = mModel.Helper.mtronInstToolSpecification(ObjType.insts().stream().filter(i -> i.tid().equals(EVAL_INST_TID)).findFirst().orElse(null));    
    public static final fURI LLM_CHAT_FEATURE_TID = LLM_FEATURE_TID.extend("chat_feature");
    public static final fURI LLM_SESSION_FEATURE_TID = LLM_FEATURE_TID.extend("session_feature");
    public static final fURI LLM_TOOL_FEATURE_TID = LLM_FEATURE_TID.extend("tool_feature");
    public static final fURI LLM_SYSTEM_FEATURE_TID = LLM_FEATURE_TID.extend("system_feature");
    public static final fURI LLM_NOTE_FEATURE_TID = LLM_FEATURE_TID.extend("note_feature");
    public static final fURI LLM_RECALL_FEATURE_TID = LLM_FEATURE_TID.extend("recall_feature");
    public static final fURI LLM_EMBED_FEATURE_TID = LLM_FEATURE_TID.extend("embed_feature");
    public static final fURI LLM_SKILL_FEATURE_TID = LLM_FEATURE_TID.extend("skill_feature");
    public static final fURI LLM_THINK_FEATURE_TID = LLM_FEATURE_TID.extend("think_feature");
    public static final fURI LLM_STAGE_FEATURE_TID = LLM_FEATURE_TID.extend("stage_feature");
    public static final fURI LLM_CONCEPT_FEATURE_TID = LLM_FEATURE_TID.extend("concept_feature");
    public static final fURI LLM_COMMENT_FEATURE_TID = LLM_FEATURE_TID.extend("comment_feature");
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
    public static Type LLM_MESSAGE_TYPE;
    public static Type LLM_TOOL_RESULT_MESSAGE_TYPE;
    public static Type LLM_TOOL_REQUEST_MESSAGE_TYPE;
    public static Type LLM_THINKING_MESSAGE_TYPE;
    public static Type LLM_NOTES_TYPE;
    public static Type LLM_CHAT_RESULT_TYPE;
    public static ObjFactory LLM_OBJ_FACTORY = MObjFactory.of().addExtension(MVec.class, x -> lst(x.jvm().stream().toList()));
    public static Type LLM_FEATURE_TYPE;

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
                                                uri(SIZE).maybe(), DATA_SIZE_TYPE,
                                                uri(QUANT).maybe(), INT_TYPE,
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
                                        uri(COST).maybe(), "the cost per million tokens to use this llm (in/out costs)"),
                                "populate a model reference rec using data from the ai provider's http-endpoint",
                                "model::[provider=>deepseek,host=><http://deepseek.com/api>,protocol=>openai,llm=>deepseek-v4-pro]"),
                        LLM_TOOL_TYPE,
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
                                                uri(CHAT), ALL_TYPE,
                                                uri(TIME), TIME_TYPE,
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
                        docWrap(LLM_MESSAGE_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(MESSAGE_TID)
                                        .isaPredicate(rec(uri(SESSION).maybe().asUri(), URI_TYPE))
                                        .create(),
                                null, null,
                                mutableMap(),
                                "polymorphic chat message — one of system, user, ai, tool_result, or thinking; discriminated by _tid column"),
                        //////////////////////////////////////////////////
                        docWrap(LLM_SKILL_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_SKILL_TID)
                                        .isaPredicate(rec(
                                                uri(NAME), URI_TYPE,
                                                uri(DESC), STR_TYPE,
                                                uri(CONTENT).maybe(), STR_TYPE,
                                                uri(ENTRY).maybe(), lst(rec(uri(DIR), URI_TYPE, uri(CONTENT), STR_TYPE)))).create(),
                                "a skill.md specification", "",
                                mutableMap(
                                        uri(NAME), "skill name",
                                        uri(DESC), "skill description",
                                        uri(CONTENT).maybe(), "skill.md document content",
                                        uri(ENTRY).maybe(), "skill assets, references, and scripts"),
                                "a skill.md specification to augment llm with specialized abilities",
                                "*<local:.agent/skills>.as(skill::T)   [-- see as?skill<=dir() --]"),
                        docWrap(LLM_FEATURE_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_FEATURE_TID)
                                        .isaPredicate(rec(
                                                // hook fields — each is an optional inst a feature can override
                                                uri(SKILL).maybe().asUri(), lst(LLM_SKILL_TYPE),
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
                                        uri(SKILL).maybe(), "skills associated with the feature",
                                        uri(ON_AGENT_CTOR).maybe(), "inst?noobj<=agent(){ [-- one time setup --] }",
                                        uri(ON_BEFORE_CHAT).maybe(), "inst?#{?}<=agent(){ [-- non-noobj to short-circuit --] }",
                                        uri(ON_PARTIAL_RESPONSE).maybe(), "inst?noobj<=agent(text=>str::T)",
                                        uri(ON_PARTIAL_THINKING).maybe(), "inst?noobj<=agent(text=>str::T)",
                                        uri(ON_PARTIAL_TOOL_CALL).maybe(), "inst?noobj<=agent(request=>call::T)",
                                        uri(BEFORE_TOOL_EXECUTION).maybe(), "inst?noobj<=agent(request=>call::T)",
                                        uri(ON_TOOL_EXECUTED).maybe(), "inst?noobj<=agent(result=>call::T)",
                                        uri(ON_COMPLETE_RESPONSE).maybe(), "inst?noobj<=agent(response=>str::T)",
                                        uri(ON_ERROR).maybe(), "inst?noobj<=agent(fail=>fail::T)"),
                                "each concrete feature refines llm_feature::T with its own hook implementations"),
                        LLM_AGENT_TYPE = docWrap(Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_AGENT_TID)
                                        .isaPredicate(rec(
                                                uri(NAME).maybe().asUri(), STR_TYPE,
                                                uri(DESC).maybe(), STR_TYPE,
                                                uri(FEATURE).maybe(), lst(LLM_FEATURE_TYPE)))
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
                                        uri(RESPONSE), rec(uri(TO), ALL_TYPE),
                                        uri(FORMAT).maybe(), ALL_TYPE))
                                .constructor(arg -> createStageLambdas(new ChatFeature(arg.asRec().jvm(), LLM_CHAT_FEATURE_TID, arg.vid())))
                                .create(),
                        Type.Builder.build()
                                .tid(LLM_FEATURE_TID)
                                .vid(LLM_SESSION_FEATURE_TID)
                                .isaPredicate(rec(SESSION, URI_TYPE))
                                .constructor(arg -> createStageLambdas(new SessionFeature(arg.asRec().jvm(), LLM_SESSION_FEATURE_TID, arg.vid())))
                                .create(),
                        Type.Builder.build()
                                .tid(LLM_FEATURE_TID)
                                .vid(LLM_TOOL_FEATURE_TID)
                                .isaPredicate(rec(TOOL, LST_TYPE))
                                .constructor(arg -> createStageLambdas(new ToolFeature(arg.asRec().jvm(), LLM_TOOL_FEATURE_TID, arg.vid())))
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
                        Type.Builder.build()
                                .tid(LLM_FEATURE_TID)
                                .vid(LLM_RECALL_FEATURE_TID)
                                .constructor(arg -> createStageLambdas(new SimilarityRecallFeature(arg.asRec().jvm(), LLM_RECALL_FEATURE_TID, arg.vid())))
                                .create(),
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
                                        .vid(LLM_STAGE_FEATURE_TID)
                                        .constructor(arg -> createStageLambdas(new StageFeature(arg.asRec().jvm(), LLM_STAGE_FEATURE_TID, arg.vid())))
                                        .create(),
                                null, null,
                                mutableMap(),
                                "appends typed stage entries to res(stages). purely observational — no configuration needed."),
                        docWrap(Type.Builder.build()
                                        .tid(LLM_FEATURE_TID)
                                        .vid(LLM_CONCEPT_FEATURE_TID)
                                        .constructor(arg -> createStageLambdas(new ConceptFeature(arg.asRec().jvm(), LLM_CONCEPT_FEATURE_TID, arg.vid())))
                                        .create(),
                                null, null,
                                mutableMap(),
                                "extracts and normalizes concepts from the agent response and thinking stream"),
                        docWrap(Type.Builder.build()
                                        .tid(LLM_FEATURE_TID)
                                        .vid(LLM_COMMENT_FEATURE_TID)
                                        .constructor(arg -> createStageLambdas(new CommentFeature(arg.asRec().jvm(), LLM_COMMENT_FEATURE_TID, arg.vid())))
                                        .create(),
                                null, null, mutableMap(),
                                "allows user to interject with a comment in the current chat lifecycle of the agent"),
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
                        docWrap(Type.Builder.build()
                                        .tid(LLM_FEATURE_TID)
                                        .vid(LLM_LEDGER_FEATURE_TID)
                                        .isaPredicate(rec(uri("init").maybe().asUri(), LST_TYPE))
                                        .constructor(arg -> createStageLambdas(new LedgerFeature(arg.asRec().jvm(), LLM_LEDGER_FEATURE_TID, arg.vid())))
                                        .create(),
                                "ledger feature — persistent agent-owned scratchpad for cross-turn task tracking",
                                "", mutableMap(
                                        uri("init").maybe(), "optional pre-populated task list"),
                                "Never cleared between chat calls. Agent reads via system message injection, writes via <<mtron:ledger>> blocks. Survives the entire session.",
                                "ledger_feature::[init=>['task 1','task 2']]"),
                        docWrap(Type.Builder.build()
                                        .tid(LLM_FEATURE_TID)
                                        .vid(LLM_ITERATION_FEATURE_TID)
                                        .constructor(arg -> createStageLambdas(new IterationFeature(arg.asRec().jvm(), LLM_ITERATION_FEATURE_TID, arg.vid())))
                                        .create(),
                                null, null, mutableMap(),
                                "overlays an iteration graph on the message ledger — each chat turn creates a linked iteration node with prev/next pointers and message back-references",
                                "iteration_feature::[]")),
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
                                        (lhs, inst) -> mTool.mtronDocToTool(QCollection.Docs.doc(lhs.asRec()))),
                                "instruction documentation",
                                "a tool specification",
                                mutableMap(jnt(0), "the tool type"),
                                "maps an instruction doc to a tool specification for llm use",
                                "*eval?docq.as(tool::T)"),
                        docWrap(instC(AS_INST_TID.dom(M_ISA_INST_TID).rng(LLM_TOOL_TID), lst(LLM_TOOL_TYPE), (lhs, inst) -> mTool.mtronInstToTool(inst.asInst())),
                                "an instruction",
                                "a tool specification",
                                mutableMap(jnt(0), "the tool type"),
                                "maps an instruction to a tool specification for llm use",
                                "*eval.as(tool::T)"),
                        docWrap(instC(AS_INST_TID.dom(DIR_TID).rng(LLM_SKILL_TID), lst(LLM_SKILL_TYPE), (lhs, inst) -> mSkill.of(staticObjToFile(lhs))),
                                "a dir containing the llm SKILL.md file",
                                "a mtron encoding of the specified skill",
                                mutableMap(jnt(0), "the skill type"),
                                "maps a directory to an llm skill where the dir follows the standard SKILL.md structure",
                                "*<local:.agent/skills>.as(skill::T)"),
                        // CHAT INSTRUCTION        
                        docWrap(instC(LLM_INST_TID.extend("chat").dom(LLM_AGENT_TID).rng(LLM_CHAT_RESULT_TID), lst(STR_TYPE), (lhs, inst) -> agent(lhs.asRec()).chat(inst.arg(0).strValue())),
                                "a model to chat with",  // dom
                                "chat result rec [chat=>..., time=>..., ?cost=>..., ?stages=>..., ?error=>...]", // rng
                                mutableMap(jnt(0), "the message to send the model"), // args
                                "communicate with an llm that may be enriched with a tool, skill, etc.", // desc
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
                                "*<ollama:qwen3:latest>+[response=>[to=>print(_)],think=>to(/ai/thoughts/_?incrq)].chat('what is 4+2?',[answer=>int::T])"))))
        ;
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
            java.util.function.Function<AbstractFeature, Inst> lambdaFactory
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
            new StageDef(ON_COMPLETE_RESPONSE, "onCompleteResponse", new Class<?>[]{Agent.class, Str.class},
                    f -> instLambda(ALL.maybe(), NOOBJ_TID.zero(), (agent, i) -> {
                        f.onCompleteResponse((Agent) agent, i.arg(0).asStr());
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
