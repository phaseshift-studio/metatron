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
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;

import static studio.phaseshift.metatron.Tokens.DEBUG;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mToolExecutor implements ToolExecutor {

    private final Inst inst;
    private Agent agent = null;

    public mToolExecutor(final Inst inst) {
        this.inst = inst;
    }

    public mToolExecutor agent(final Agent agent) {
        this.agent = agent;
        return this;
    }

    @Override
    public String execute(final ToolExecutionRequest request, final Object memoryId) {
        if (null != this.agent && this.agent.isInterrupted()) {
            return "user interruption -- shutting down";
        }
        final Obj result = this.apply(request.arguments());
        mTool.resultStash.put(request.id(), result);
        final JsonElement json = ObjJSONSerializer.simple().write(this.onToolResult(result, request.id()));
        this.inst.logger().status(DEBUG, "%s => %s", this.inst, json);
        return json.toString();
    }

    /**
     * Offer the tool-result payload to the agent's {@code on_tool_result} stage and
     * hand back whatever the features folded it into.
     *
     * <p>This is the only place a tool result can still be shaped —
     * {@code onToolExecuted} is an observational LC4j listener that cannot alter the
     * payload, and {@code beforeToolExecution} runs before the result exists — so the
     * stage is dispatched from here rather than from the turn (see
     * {@link Agent#dispatchToolResult}).  The executor holds no knowledge of what any
     * feature does with the payload.
     */
    private Obj onToolResult(final Obj result, final String requestId) {
        if (null == this.agent)
            return result;
        return this.agent.dispatchToolResult(result, requestId);
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
