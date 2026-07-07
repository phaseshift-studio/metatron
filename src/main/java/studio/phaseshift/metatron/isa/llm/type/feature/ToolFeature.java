package studio.phaseshift.metatron.isa.llm.type.feature;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.service.tool.ToolExecutor;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.llm.type.mcpClient;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.Tuple;

import java.util.HashMap;
import java.util.Map;

import static studio.phaseshift.metatron.furi.q.QCollection.DOCQ;
import static studio.phaseshift.metatron.isa.llm.type.mTool.LLM_TOOL_TYPE;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.MCP_CLIENT_TYPE;

public class ToolFeature extends Feature {

    private Map<ToolSpecification, ToolExecutor> tools;
    private McpToolProvider mcpProvider;

    public ToolFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        if (!this.has(CHEST)) return noobj();

        this.tools = new HashMap<>();
        this.mcpProvider = null;
        this.at(CHEST).asLst().elements()
                .map(e -> e.autoResolve(agent))
                .filter(t -> !t.isNoObj())
                .forEach(t -> {
                    try {
                        if (t.isRec() && t.test(MCP_CLIENT_TYPE)) {
                            this.mcpProvider = McpToolProvider.builder()
                                    .mcpClients(Rec.wrap(t.as(), mcpClient.class).client())
                                    .build();
                        } else if (t.isObjInst()) {
                            if (QCollection.isNoDocs(Router.readFromSpace(t.tid().addQ(DOCQ))))
                                t.logger().warn("ignoring inst as it has no associated ?docq: %s", t);
                            else {
                                final Tuple.Pair<ToolSpecification, ToolExecutor> pair =
                                        mTool.mtronInstToolSpecification(mTool.mtronInstToTool(t.asInst()));
                                this.tools.put(pair.get0(), pair.get1());
                            }
                        } else if (t.isRec() && t.test(LLM_TOOL_TYPE)) {
                            final Tuple.Pair<ToolSpecification, ToolExecutor> pair =
                                    mTool.mtronInstToolSpecification(t.asRec());
                            this.tools.put(pair.get0(), pair.get1());
                        }
                    } catch (final Exception e) {
                        agent.logger().error("unable to set up tool: %s [%s]", t, e);
                    }
                });

        return noobj();
    }

    @Override
    public void onToolExecuted(final Agent agent, final Obj result) {
        if (result.isRec()) {
            final Rec r = result.asRec();
            this.logger().info("tool executed: %s(%s) => %s",
                    r.at(uri(NAME)).strValue(),
                    r.at(uri(TOOL_ARGUMENTS)).strValue(),
                    r.at(uri(RESULT)).strValue());
        }
    }

    public Map<ToolSpecification, ToolExecutor> toolSpecs() { return this.tools; }
    public McpToolProvider mcp() { return this.mcpProvider; }
}
