package studio.phaseshift.metatron.isa.llm.type.feature;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.AgentServices;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.llm.type.mcpClient;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.TOOL_RESULT_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.MCP_CLIENT_TYPE;

public class ToolFeature extends AbstractFeature {

    public ToolFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static void buildTools(final Agent agent, final AiServices<AgentServices> service) {
        agent.features().elements().map(Obj::asRec).filter(f -> f.has(TOOL)).forEach(feature -> {
            final Obj tool = feature.at(TOOL);
            final Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();
            final List<McpClient> mcpClients = new ArrayList<>();
            tool.elements().forEach(t -> {
                try {
                    if (t.isRec() && t.test(MCP_CLIENT_TYPE)) {
                        mcpClients.add(Rec.wrap(t.as(), mcpClient.class).client());
                    } else if (t.isObjInst()) {
                        // if (!QCollection.isNoDocs(Router.readFromSpace(t.tid().addQ(DOCQ)))) {
                        final Tuple.Pair<ToolSpecification, ToolExecutor> pair =
                                mTool.mtronInstToolSpecification(mTool.mtronInstToTool(t.asInst()));
                        tools.put(pair.get0(), pair.get1());
                        //   } else {
                        // TODO: handle when a tool doesn't have docs
                        //   }
                    } /*else if (t.isRec() && t.test(LLM_TOOL_TYPE)) {
                        final Tuple.Pair<ToolSpecification, ToolExecutor> pair =
                                mTool.mtronInstToolSpecification(t.asRec());
                        tools.put(pair.get0(), pair.get1());
                    }*/
                } catch (final Exception e) {
                    feature.logger().warn("unable to build tool from %s (ignoring): %s", t, e.getMessage());
                }
            });
            if (!tools.isEmpty())
                service.tools(tools);
            if (!mcpClients.isEmpty())
                service.toolProvider(McpToolProvider.builder().mcpClients(mcpClients).build());
        });
    }

    @Override
    public void onToolExecuted(final Agent agent, final Obj result) {
        if (result.isRec()) {
            final Rec r = result.asRec();
            this.logger().info("tool executed: %s(%s) => %s",
                    Str.Helper.cleanString(r.at(uri(NAME))),
                    Str.Helper.cleanString(r.at(uri(TOOL_ARGUMENTS))),
                    CommonUtil.clipString(Str.Helper.cleanString(r.at(uri(RESULT))), 50, true));

            // Write ToolResult to the message ledger
            if (agent.hasFeature(SESSION)) {
                try {
                    final String resultText = Str.Helper.cleanString(r.at(uri(RESULT)));
                    final MessageBuilder builder = MessageBuilder.build(TOOL_RESULT_MESSAGE_TID)
                            .put(NAME, uri(Str.Helper.cleanString(r.at(uri(NAME)))))
                            .text(resultText)
                            .contents(Str.Helper.cleanString(r.at(uri(CONTENTS))))
                            .time()
                            .session(agent.feature(SESSION).asRec().at(SESSION).uriValue())
                            .depth(agent.chatDepth())
                            .chatId(agent.chatId());

                    // Retrieve the raw Obj stashed by mTool before LC4j forced it to a string
                    final String toolCallId = Str.Helper.cleanString(r.at(uri(CONTENTS)));
                    final Obj stashed = mTool.resultStash.remove(toolCallId);
                    if (stashed != null && (stashed.isRec() || stashed.isInst()))
                        builder.put(CHAT, stashed);

                    builder.create(agent.at(ROOT).uriValue().extend(MESSAGE)
                            .extend("_").addQ(INCRQ));
                } catch (final Exception e) {
                    this.logger().warn("tool result write failed (non-blocking): %s", e.getMessage());
                }
            }
        } else {
            this.logger().info("tool executed: %s", CommonUtil.clipString(result.toString(), 50, true));
        }
    }
}
