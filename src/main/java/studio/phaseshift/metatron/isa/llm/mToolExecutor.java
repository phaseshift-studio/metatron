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

import com.google.gson.JsonElement;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;

import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mToolExecutor implements ToolExecutor {

    private final Inst inst;

    public mToolExecutor(final Inst inst) {
        this.inst = inst;
    }

    @Override
    public String execute(final ToolExecutionRequest request, final Object memoryId) {
        final Obj result = this.apply(request.arguments());
        // stash the raw Obj so ToolFeature can recover nested rec/inst structure
        mTool.resultStash.put(request.id(), result);
        final JsonElement json = ObjJSONSerializer.simple().write(result);
        this.inst.logger().info("%s => %s", this.inst, json);
        return json.toString();
    }

    /**
     * Schema-aware parse + apply — use the inst's declared arg types
     * (str/uri/code/inst), not a blind JSON guess, matching the MCP server path.
     * A malformed argument (e.g. unparseable {@code code::T}) becomes a {@code fail},
     * not a thrown exception, so both tool-call avenues behave identically.
     */
    private Obj apply(final String rawJsonArgs) {
        try {
            final ObjJSONSerializer serializer = new ObjJSONSerializer();
            if (this.inst.args().isRec())
                serializer.schema(this.inst.args().asRec());
            final Obj arguments = serializer.readString(rawJsonArgs);
            return mTool.applyArguments(this.inst, arguments);
        } catch (final Exception e) {
            return fail(e);
        }
    }

}
