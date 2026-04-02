/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.space.modelCatalogSpace.LLM_CATALOG_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.llm.type.MCPServer.MCP_SERVER_TYPE;
import static studio.phaseshift.metatron.isa.llm.type.MCPServer.MCP_TOOL_TYPE;
import static studio.phaseshift.metatron.isa.llm.type.Model.model;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.BYTE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(tid = "/m/llm")
public class llmInstSet extends AbstractInstSet {
    public static final fURI LLM_ISA_TID = MTRON_TID.extend("llm");
    public static final fURI MODEL_TID = LLM_ISA_TID.extend("model");
    public static final fURI LLM_INST_TID = LLM_ISA_TID.extend("inst");
    public static final fURI LLM_SPACE_TID = LLM_ISA_TID.extend("space");
    public static final fURI LLM_TOOL_TID = LLM_ISA_TID.extend("tool");
    public static final fURI LLM_MEMORY_TID = LLM_ISA_TID.extend("memory");
    public static final fURI MCP_SERVER_TID = LLM_SPACE_TID.extend("mcp").extend("mcpserver");
    public static final fURI MCP_TOOL_TID = LLM_ISA_TID.extend("mcp").extend("tool");

    public static Type LLM_MEMORY_TYPE = Type.Builder.build()
            .tid(LST_TID)
            .vid(LLM_MEMORY_TID)
            .isaPredicate(lst())
            .create();
    public static final fURI AI_MEMORY_TID = LLM_MEMORY_TID.extend("ai");
    public static Type LLM_AI_MEMORY_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(AI_MEMORY_TID)
            .isaPredicate(rec(
                    uri(TEXT), STR_TYPE,
                    uri(THINKING), INT_TYPE,
                    uri("attributes"), REC_TYPE,
                    uri(TYPE), uri("AI")))
            .create();
    public static final fURI USER_MEMORY_TID = LLM_MEMORY_TID.extend("user");
    public static Type LLM_USER_MEMORY_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(USER_MEMORY_TID)
            .isaPredicate(rec(
                    uri("contents"), LST_TYPE,
                    uri(TYPE), uri("USER")))
            .create();

    public llmInstSet() {
        super(mutableMap(uri(PATTERN), uri(LLM_ISA_TID.extend(ALL))), LLM_ISA_TID, LLM_ISA_TID);
    }

    @Override
    public void setup() {
        this.jvm().putAll(mutableMap(
                uri(PATTERN), uri(LLM_ISA_TID.extend(ALL)),
                uri(TYPE), lst(
                        LLM_CATALOG_SPACE_TYPE,
                        MCP_TOOL_TYPE,
                        MCP_SERVER_TYPE,
                        docWrap(Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(MODEL_TID).
                                        isaPredicate(rec(
                                                uri(PROVIDER), LLM_CATALOG_SPACE_TYPE,
                                                uri(NAME), URI_TYPE,
                                                uri(THINK).maybe(), rec(uri(TO).maybe().asUri(), T(ALL)),
                                                uri(RESPONSE).maybe(), rec(uri(TO).maybe().asUri(), T(ALL), uri(FORMAT).maybe(), T(ALL)),
                                                uri(SIZE).maybe().asUri(), BYTE_TYPE,
                                                uri(MEMORY).maybe(), rec(uri(FROM).maybe().asUri(), URI_TYPE),
                                                uri(DESC).maybe(), STR_TYPE,
                                                uri(SKILL).maybe(), LST_TYPE,
                                                uri(TOOL).maybe(), LST_TYPE)).create(),
                                "a large language model", "the model construction", Map.of(
                                        uri(NAME), "the name of the model",
                                        uri(HOST).maybe(), "the provider endpoint",
                                        uri(THINK).maybe(), "whether to think before responding",
                                        uri(SIZE).maybe(), "the size of the model in bytes",
                                        uri(MEMORY).maybe(), "a pointer to the llm's memory",
                                        uri(SKILL).maybe(), "the skills to use",
                                        uri(TOOL).maybe(), "the tools to use"), "an mtron interface to a large language model")),
                uri(INST), lst(docWrap(instC(LLM_INST_TID.extend("chat").dom(MODEL_TID).rng(ALL.maybe()),
                                lst(STR_TYPE),
                                (lhs, inst) -> model(lhs.asRec()).chat(inst.arg(0).strValue())),
                        "a model to chat with",  // dom
                        "the models chat response", // rng
                        Map.of(jnt(0), "the message to send the model"), // args
                        "chat with the lhs model", // desc
                        "*<ollama:qwen3:latest>+[response=>[to=>print(_)],think=>[to=>print(_)]].chat('what is a database query language?')"))));
        docWrap(this, "large language model emerge from the metatron");
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
