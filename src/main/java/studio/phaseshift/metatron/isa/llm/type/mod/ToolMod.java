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

package studio.phaseshift.metatron.isa.llm.type.mod;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.llm.type.mAgent;
import studio.phaseshift.metatron.isa.llm.type.mModel;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.llm.type.mcpClient;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.q.QCollection.DOCQ;
import static studio.phaseshift.metatron.isa.llm.type.mTool.LLM_TOOL_TYPE;
import static studio.phaseshift.metatron.isa.web.webInstSet.MCP_CLIENT_TYPE;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ToolMod implements Mod {

    public void apply(final mModel model, final AiServices<mAgent> services) {
        services.hallucinatedToolNameStrategy(tool -> new ToolExecutionResultMessage(ToolExecutionResultMessage.builder().toolName(tool.name()).text("unknown or inaccessible tool")));
        if (model.tools().isPresent()) {
            try {
                final Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();
                model.tools().get()
                        .elements()
                        //.flatMap(e -> e.isObjs() ? e.elements() : Stream.of(e))
                        .map(e -> e.autoResolve(model))
                        .filter(t -> !t.isNoObj())
                        .forEach(t -> {
                            try {
                                if (t.isRec() && t.test(MCP_CLIENT_TYPE)) {
                                    services.toolProvider(McpToolProvider.builder().mcpClients(Rec.wrap(t.as(), mcpClient.class).client()).build()).executeToolsConcurrently(BootLoader.getExecutor());
                                } else if (t.isObjInst()) {
                                    if (QCollection.isNoDocs(Router.readFromSpace(t.tid().addQ(DOCQ))))
                                        t.logger().warn("ignoring inst as it has no associated ?docq: %s", t);
                                    else {
                                        final Tuple.Pair<ToolSpecification, ToolExecutor> pair = mTool.mtronInstToolSpecification(mTool.mtronInstToTool(t.asInst()));
                                        tools.put(pair.get0(), pair.get1());
                                    }
                                } else if (t.isRec() && t.test(LLM_TOOL_TYPE)) {
                                    final Tuple.Pair<ToolSpecification, ToolExecutor> pair = mTool.mtronInstToolSpecification(t.asRec());
                                    tools.put(pair.get0(), pair.get1());
                                }
                            } catch (final Exception e) {
                                model.logger().error("unable to set up tool: %s [%s]", t, e);
                            }
                        });
                if (!tools.isEmpty())
                    services.tools(tools).executeToolsConcurrently(BootLoader.getExecutor());
            } catch (Exception e) {
                throw MTronException.of("unable to setup tools: %s", e);
            }
        }
    }
}
