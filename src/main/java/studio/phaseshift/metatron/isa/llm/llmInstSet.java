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
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.ObjFactory;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.tble.tbleInstSet;
import studio.phaseshift.metatron.isa.tble.tbleSpace;
import studio.phaseshift.metatron.isa.vec.type.MVec;
import studio.phaseshift.metatron.isa.web.webInstSet;
import studio.phaseshift.metatron.util.MTronException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCS_TID;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.space.modelCatalogSpace.LLM_CATALOG_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.llm.type.mModel.model;
import static studio.phaseshift.metatron.isa.llm.type.mTool.LLM_TOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.BYTE_TYPE;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_CURRENCY_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.split_;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
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
    public static final fURI MODEL_TID = LLM_ISA_TID.extend(MODEL);
    public static final fURI LLM_INST_TID = LLM_ISA_TID.extend(INST);
    public static final fURI LLM_FEATURE_TID = LLM_ISA_TID.extend(FEATURE);
    public static final fURI LLM_SPACE_TID = LLM_ISA_TID.extend(SPACE);
    public static final fURI LLM_TOOL_TID = LLM_FEATURE_TID.extend(TOOL);
    public static final fURI LLM_SESSION_TID = LLM_FEATURE_TID.extend(SESSION);
    public static final fURI LLM_SKILL_TID = LLM_FEATURE_TID.extend(SKILL);
    public static final fURI MESSAGE_TID = LLM_ISA_TID.extend(MESSAGE);
    public static final fURI AI_MESSAGE_TID = MESSAGE_TID.extend(AI);
    public static final fURI USER_MESSAGE_TID = MESSAGE_TID.extend(USER);
    public static final fURI SYSTEM_MESSAGE_TID = MESSAGE_TID.extend(SYSTEM);
    public static final fURI TOOL_REQUEST_MESSAGE_TID = MESSAGE_TID.extend("tool_request");
    public static final fURI TOOL_RESULT_MESSAGE_TID = MESSAGE_TID.extend("tool_result");
    //public static final fURI MCP_TOOL_TID = LLM_ISA_TID.extend("mcp");
    // public static Obj MTRON_EVAL_TOOL = mModel.Helper.mtronInstToolSpecification(ObjType.insts().stream().filter(i -> i.tid().equals(EVAL_INST_TID)).findFirst().orElse(null));    


    public static Type LLM_AI_MESSAGE_TYPE;
    public static Type LLM_USER_MESSAGE_TYPE;
    public static Type LLM_SYSTEM_MESSAGE_TYPE;
    public static Type LLM_SKILL_TYPE;
    public static Type LLM_SESSION_TYPE;
    public static Type LLM_MESSAGE_TYPE;
    public static Type LLM_TOOL_RESULT_MESSAGE_TYPE;
    public static Type LLM_NOTES_TYPE;
    public static ObjFactory LLM_OBJ_FACTORY = MObjFactory.of().addExtension(MVec.class, x -> lst(x.jvm().stream().toList()));
    public static Type LLM_FEATURE_TYPE;

    public llmInstSet() {
        super(mutableMap(uri(PATTERN), uri(LLM_ISA_TID.extend(ALL))), INSTSET_TID, LLM_ISA_TID);
    }

    @Override
    public void setup() {
        this.jvm().putAll(mutableMap(
                //uri(PATTERN), uri(LLM_ISA_TID.extend(ALL)),
                uri(TYPE), lst(
                        LLM_CATALOG_SPACE_TYPE,
                        LLM_TOOL_TYPE,
                        //////////////////////////////////////////////////
                        docWrap(LLM_SESSION_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(LLM_SESSION_TID)
                                        .isaPredicate(rec(uri(AGENT), T(URI_TID.some()), uri(USER), T(URI_TID.some()), uri(MESSAGE), T(URI_TID.maybe()), uri(ALGORITHM), REC_TYPE))
                                        .create(),
                                null, null, mutableMap(
                                        uri(AGENT), "the agent(s) involved in the chat session",
                                        uri(USER), "the user(s) involved in the chat session",
                                        uri(MESSAGE), "a reference to all messages in the chat session",
                                        uri(ALGORITHM), "the algorithm used to manage the chat session (compaction, windowing, summarizing, etc.)"),
                                "llm session session policy with algorithm config and a resolved lst of messages from sub-path */msg/*"),
                        docWrap(LLM_MESSAGE_TYPE = Type.Builder.build()
                                .tid(REC_TID)
                                .vid(MESSAGE_TID)
                                .create(), "a message that with ai, user, and system message refinements"),
                        docWrap(LLM_SYSTEM_MESSAGE_TYPE = Type.Builder.build()
                                .tid(MESSAGE_TID)
                                .vid(SYSTEM_MESSAGE_TID)
                                .isaPredicate(rec(uri(TEXT), STR_TYPE))
                                .create(), "a system message typically provides behavioral and response-style instructions to the model"),
                        docWrap(LLM_USER_MESSAGE_TYPE = Type.Builder.build()
                                        .tid(MESSAGE_TID)
                                        .vid(USER_MESSAGE_TID)
                                        .isaPredicate(rec(
                                                uri(NAME).maybe().asUri(), STR_TYPE,
                                                uri(CONTENTS), rec(uri(TEXT), STR_TYPE)))
                                        .create(),
                                null, null, mutableMap(
                                        uri(NAME).maybe(), "sender identity for multi-user conversations",
                                        uri(CONTENTS), "the message contents"), "a user message"),
                        docWrap(LLM_AI_MESSAGE_TYPE = Type.Builder.build()
                                        .tid(MESSAGE_TID)
                                        .vid(AI_MESSAGE_TID)
                                        .isaPredicate(rec(
                                                uri(NAME).maybe().asUri(), STR_TYPE,
                                                uri(TEXT).maybe().asUri(), STR_TYPE,
                                                uri(THINKING).maybe(), INT_TYPE,
                                                uri(TOOL_REQUESTS).maybe(), LST_TYPE,
                                                uri(ATTRIBUTES).maybe(), REC_TYPE))
                                        .create(),
                                null, null, mutableMap(
                                        uri(NAME), "the assistant identity",
                                        uri(TEXT), "the response text",
                                        uri(THINKING), "the internal reasoning and token count of the model",
                                        uri(TOOL_REQUESTS), "the tool execution requests made by the model",
                                        uri(ATTRIBUTES), "extra metadata from the provider"), "an ai/assistant message"),
                        docWrap(LLM_TOOL_RESULT_MESSAGE_TYPE = Type.Builder.build()
                                        .tid(MESSAGE_TID)
                                        .vid(TOOL_RESULT_MESSAGE_TID)
                                        .isaPredicate(rec(
                                                uri(NAME), STR_TYPE,
                                                uri(TEXT), STR_TYPE,
                                                uri(ID).maybe(), STR_TYPE))
                                        .create(),
                                null, null, mutableMap(
                                        uri(NAME), "the tool that was executed",
                                        uri(TEXT), "the text result of the tool execution",
                                        uri(ID).maybe(), "correlation id matching the tool execution request"),
                                "a tool execution result message"),
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
                                                uri(THINK).maybe().asUri(), ALL_TYPE,
                                                uri(SESSION).maybe(), LLM_SESSION_TYPE.maybe(),
                                                uri(PROMPT).maybe(), ALL_TYPE,
                                                uri(RESPONSE).maybe(), rec(
                                                        uri(TO).maybe().asUri(), INST_TYPE,
                                                        uri(FORMAT).maybe(), ALL_TYPE,
                                                        uri(COST).maybe(), MATH_CURRENCY_TYPE).maybe(),
                                                uri(TOOL).maybe(), lst(split_(lst(
                                                        isa_(LLM_TOOL_TYPE),
                                                        isa_(webInstSet.MCP_SERVER_TYPE))).tryToInst()).maybe(),
                                                uri(SKILL).maybe(), LST_TYPE.maybe(),
                                                uri(RAG).maybe(), rec(
                                                        URI_TYPE, isa_(INT_TYPE).else_(jnt(10)).tryToInst()).maybe(),
                                                uri(NOTE).maybe(), LST_TYPE.maybe()).maybe()).create(),
                                "llm features", "", mutableMap(
                                        uri(RESPONSE).maybe(), "an obj to process llm responses",
                                        uri(THINK).maybe(), "an obj to process llm thoughts",
                                        uri(NOTE).maybe(), "a lst of notes llm will read and react to mid-chat",
                                        uri(PROMPT).maybe(), "a user prompt to prefix chat messages with",
                                        uri(SESSION).maybe(), "current llm session containing chat interactions",
                                        uri(RAG).maybe(), "retrieval augmented generation configuration",
                                        uri(SKILL).maybe(), "skills to extend the llm's abilities",
                                        uri(TOOL).maybe(), "tool functions the llm can use to solve problems"),
                                "llm features"),
                        docWrap(Type.Builder.build().tid(REC_TID).vid(MODEL_TID).
                                        isaPredicate(rec(
                                                uri(PROVIDER), LLM_CATALOG_SPACE_TYPE,
                                                uri(NAME), URI_TYPE,
                                                uri(DESC).maybe(), STR_TYPE,
                                                uri(COST).maybe(), rec(uri(IN), MATH_CURRENCY_TYPE, uri(OUT), MATH_CURRENCY_TYPE).maybe(),
                                                uri(SIZE).maybe().asUri(), BYTE_TYPE,
                                                uri(FEATURE).maybe(), LLM_FEATURE_TYPE)).create(),
                                "a large language model", "the model construction", mutableMap(
                                        uri(PROVIDER), "provider catalog containing llm model",
                                        uri(NAME), "the model name from the host catalog",
                                        uri(COST).maybe(), "the cost per million tokens to use this llm (in/out costs)",
                                        uri(FEATURE).maybe(), "llm features can be moved or shared between multiple models"),
                                "an mtron interface to a large language model")),
                uri(INST), lst(
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
                        docWrap(instC(LLM_INST_TID.extend("chat").dom(MODEL_TID).rng(STR_TID), lst(STR_TYPE), (lhs, inst) -> model(lhs.asRec()).chat(inst.arg(0).strValue())),
                                "a model to chat with",  // dom
                                "the models chat response", // rng
                                mutableMap(jnt(0), "the message to send the model"), // args
                                "communicate with an llm that may be enriched with a tool, skill, etc.", // desc
                                "*<ollama:qwen3:latest>+[response=>[to=>print(_)],think=>to(/ai/thoughts/_?incrq)].chat('what is a database?')"),
                        docWrap(instC(LLM_INST_TID.extend("embed").dom(MODEL_TID).rng(VEC_TID), lst(ALL_TYPE), (lhs, inst) -> model(lhs.asRec()).embed(inst.arg(0))),
                                "a model to embed arg into",  // dom
                                "the obj as a vector embedding", // rng
                                mutableMap(jnt(0), "the object to embed"), // args
                                "embed an object with an llm", // desc
                                "*<ollama:qwen3:latest>.embed('what is a database?')"),
                        /*instC(LLM_INST_TID.extend("chat").dom(MODEL_TID).rng(A.maybe()),
                                lst(STR_TYPE),
                                (lhs, inst) -> model(lhs.asRec()).chat(inst.arg(0).strValue())),*/
                        docWrap(instC(LLM_INST_TID.extend("chat").dom(MODEL_TID).rng(REC_TID),
                                        lst(STR_TYPE, REC_TYPE),
                                        (lhs, inst) -> model(lhs.asRec()).chat(inst.arg(0).strValue(), inst.arg(1).asRec())),
                                "a model to chat with",  // dom
                                "the models chat response", // rng
                                mutableMap(jnt(0), "the message to send the model", jnt(1), "the desired response format"), // args
                                "communicate with am llm enriched by tools, skills, etc. and receive response in particular format", // desc
                                "*<ollama:qwen3:latest>+[response=>[to=>print(_)],think=>to(/ai/thoughts/_?incrq)].chat('what is 4+2?',[answer=>int::T])"))))
        ;
        docWrap(this, "large language model think and reason within the metatron");
        super.setup();
    }
    
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
