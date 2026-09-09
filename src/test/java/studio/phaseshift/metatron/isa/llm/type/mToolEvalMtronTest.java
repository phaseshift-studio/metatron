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

package studio.phaseshift.metatron.isa.llm.type;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.EvalMtronCases;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.isa.web.type.mcpMetatronBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * Runs the shared {@link EvalMtronCases} through the <em>direct</em> tool-call
 * path — the same schema-aware parse + apply that {@code mTool}/{@code mToolExecutor}
 * use — rather than the full MCP transport.  Proves the LC4j agent avenue and the
 * MCP server avenue share one invocation path with identical argument disambiguation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class mToolEvalMtronTest extends AbstractMetatronTest {

    private Inst evalInst;

    @BeforeAll
    public void setupEvalInst() {
        InstSet.importInstSet(WEB_ISA_TID);
        final Map<Obj, Obj> jvm = mcpMetatronBuilder.build(new LinkedHashMap<>(), f("/test/mtool/eval"));
        final Rec tools = jvm.get(uri(TOOL)).asRec();
        this.evalInst = tools.at(uri("m_web_mcp_mcp_mtron_eval_mtron")).asInst();
    }

    /**
     * The direct-tool-call core: serialize the code argument to JSON, parse it
     * schema-aware against the inst's declared arg types, then apply — the same
     * steps {@link mToolExecutor} runs (minus the stash + result string).
     */
    private Obj evalMtronDirect(final String code) {
        try {
            final String jsonArgs = ObjJSONSerializer.simple().write(rec(uri(CODE), str(code))).toString();
            final Obj arguments = new ObjJSONSerializer()
                    .schema(this.evalInst.args().asRec())
                    .readString(jsonArgs);
            return mTool.applyArguments(this.evalInst, arguments);
        } catch (final Exception e) {
            // a schema'd argument failed to parse (e.g. malformed code::T) — fail, like the MCP server
            return fail(e);
        }
    }

    @Test
    public void testEvalMtronDirectTool() {
        EvalMtronCases.run(LOG, "tool", this::evalMtronDirect);
    }
}
