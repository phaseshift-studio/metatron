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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.llm.type.GGUF;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.MTRON_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Inst.ARGS;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(tid = "/m/llm")
public class llmInstSet extends AbstractInstSet {

    public static final fURI LLM_ISA_TID = MTRON_TID.extend("llm");
    public static final fURI LLM_INST_TID = LLM_ISA_TID.extend("inst");
    public static final fURI LLM_SPACE_TID = LLM_ISA_TID.extend("space");

    public static final fURI TOOL_TID = LLM_ISA_TID.extend("tool");
    public static final fURI MEMORY_TID = LLM_ISA_TID.extend("memory");
    // public static final fURI LOAD_INST_TID = OLLAMA_TID.extend("inst/load");

    public llmInstSet() {
        super(LLM_ISA_TID, LLM_ISA_TID);
    }


    public static final fURI LLM_TID = LLM_ISA_TID.extend("llm");
    public static final Type LLM_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(LLM_TID)
            .isaPredicate(rec(
                    uri(NAME), URI_TYPE,
                    uri(SKILL), LST_TYPE,
                    uri(THINK).c(cInt::maybe), BOOL_TYPE)).create();

    public static final Type TOOL_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(TOOL_TID)
            .isaPredicate(rec(
                    uri(NAME), URI_TYPE,
                    uri(ARGS), rec(URI_TYPE, T(ALL)))).create();

    @Override
    public Set<Type> types() {
        return Set.of(
                LLM_TYPE,
                TOOL_TYPE,
                //T(MEMORY_TID),
                GGUF.GGUF_TYPE,
                GGUF.TENSOR_REF_TYPE);
    }
}