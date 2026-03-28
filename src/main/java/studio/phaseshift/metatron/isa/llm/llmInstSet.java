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

import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.llm.type.Model;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.DocQ.Doc.docWrap;
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
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

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

    private static final Set<Type> TYPES = new LinkedHashSet<>();
    private static final Set<Inst> INSTS = new LinkedHashSet<>();


    /*
     [
   text                 =>'The file permission `rwxr-xr-x` corresp'...,
   thinking             =>'Okay, the user is asking about the file'...,
   toolExecutionRequests=>[,],
   attributes           =>[=>],
   type                 =>AI],
  [

     */
    public static Type LLM_MEMORY_TYPE = Type.Builder.build()
            .tid(LST_TID)
            .vid(LLM_MEMORY_TID)
            .isaPredicate(lst())
            .create(TYPES, INSTS);
    public static final fURI AI_MEMORY_TID = LLM_MEMORY_TID.extend("ai");
    public static Type LLM_AI_MEMORY_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(AI_MEMORY_TID)
            .isaPredicate(rec(
                    uri(TEXT), STR_TYPE,
                    uri(THINKING), INT_TYPE,
                    uri("attributes"), REC_TYPE,
                    uri(TYPE), uri("AI")))
            .create(TYPES, INSTS);
    public static final fURI USER_MEMORY_TID = LLM_MEMORY_TID.extend("user");
    public static Type LLM_USER_MEMORY_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(USER_MEMORY_TID)
            .isaPredicate(rec(
                    uri("contents"), LST_TYPE,
                    uri(TYPE), uri("USER")))
            .create(TYPES, INSTS);

    public static Type LLM_TYPE = docWrap(Type.Builder.build()
                    .tid(REC_TID)
                    .vid(MODEL_TID).
                    isaPredicate(rec(
                            uri(PROVIDER), URI_TYPE,
                            uri(NAME), URI_TYPE,
                            uri(THINK).maybe(), URI_TYPE,
                            uri(SIZE), BYTE_TYPE,
                            uri(MEMORY).maybe(), URI_TYPE,
                            uri(SKILL).maybe(), LST_TYPE,
                            uri(TOOL).maybe(), LST_TYPE))
                    .inst(instC(LLM_INST_TID.extend("query").dom(MODEL_TID).rng(REC_TID), lst(REC_TYPE), (lhs, inst) -> {
                        final ResponseFormat responseFormat = new ResponseFormat.Builder()
                                .jsonSchema(new JsonSchema.Builder().rootElement(Model.Helper.objToSchema(REC_TYPE, inst.arg(0).asRec().at(FORMAT), "response")).build())
                                .type(ResponseFormatType.JSON).build();
                        return lhs;
                    }))
                    .inst(instC(LLM_INST_TID.extend("chat").dom(MODEL_TID).rng(STR_TID), lst(STR_TYPE, T(ALL.maybe())),
                            (lhs, inst) -> model(lhs.asRec()).chat(inst.arg(0).strValue())))
                    .create(TYPES, INSTS),
            "a large language model", "the model construction", Map.of(
                    uri(NAME), "the name of the model",
                    uri(HOST), "the provider endpoint",
                    uri(THINK).maybe(), "whether to think before responding",
                    uri(SIZE).maybe(), "the size of the model in bytes",
                    uri(MEMORY).maybe(), "a pointer to the llm's memory",
                    uri(SKILL).maybe(), "the skills to use",
                    uri(TOOL).maybe(), "the tools to use"), "an mtron interface to a large language model");

    public llmInstSet() {
        super(LLM_ISA_TID, LLM_ISA_TID);
    }

    public Set<Type> types() {
        TYPES.add(LLM_CATALOG_SPACE_TYPE);
        TYPES.add(MCP_TOOL_TYPE);
        TYPES.add(MCP_SERVER_TYPE);
        return TYPES;
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

    public Set<Inst> insts() {
        return INSTS;
    }
}
