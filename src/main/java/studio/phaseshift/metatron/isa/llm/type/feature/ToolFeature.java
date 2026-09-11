package studio.phaseshift.metatron.isa.llm.type.feature;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.service.tool.ToolProvider;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.llm.mToolProvider;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatSessionStore;
import studio.phaseshift.metatron.isa.llm.space.ToolPairGate;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.llm.type.mcp.mcpClient;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.NOOBJ;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrapDocs;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.MCP_CLIENT_TYPE;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The gatekeeper of the agent's tool channel.
 *
 * <p>One owner per channel: {@code ToolFeature} owns the
 * {@code Collection<mTool>} of tools (inst registrations) and
 * {@link SkillFeature} owns the {@code Collection<mSkill>} of skills
 * (markdown content).  Contributors publish by calling
 * {@link #addTool(mTool)}; skills that carry tools are forwarded here by
 * the skill gateway.</p>
 *
 * <p>Registration flow (list order — gatekeepers last, tool after skill):
 * publishers register in {@code onBeforeChat} → {@code SkillFeature}
 * forwards each skill's tools here → this feature projects the registry
 * onto the agent's LC4j tool bag.</p>
 */
public class ToolFeature extends AbstractFeature {

    /**
     * The registered tools — canonical mTool elements, upserted by name.
     */
    private final mToolProvider toolProvider = new mToolProvider();

    public void addToolProvider(final ToolProvider toolProvider) {
        this.toolProvider.addToolProvider(toolProvider);
    }

    public ToolProvider getToolProvider() {
        return this.toolProvider;
    }

    /**
     * The mcp clients gathered from this feature's {@code tool} config surface.
     */
    private final Set<mcpClient> mcpClients = new HashSet<>();

    public ToolFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * Register a tool with this feature — the single entry point to the
     * agent's tool registry.  Upsert semantics: re-registering under the
     * same name replaces the earlier entry, so per-chat registration is
     * idempotent.
     *
     * @param tool the tool to register
     */
    public void addTool(final mTool tool) {
        this.toolProvider.addTool(tool);
    }

    /**
     * The tools currently registered with this feature.
     *
     * @return the registered tools
     */
    public Lst tools() {
        return this.toolProvider.getTools().stream().collect(new CommonUtil.LstCollector());
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        this.toolProvider.agent(agent);
        this.addTool(mTool.tool(docWrapDocs(instC(f("list_tools").dom(NOOBJ.zero()).rng(LST_TID), lst(),
                        (lhs, inst) -> lst(agent.feature(LLM_TOOL_FEATURE_TID).<ToolFeature>as().tools())),
                "no domain",
                "a lst of tools",
                Map.of(),
                "generates a lst of available tools")));
        // ── 1. register this feature's own tool extensions (its config surface) ──
        if (this.has(TOOL)) {
            this.at(TOOL).elements().forEach(t -> {
                LOG.status(DEBUG, "preparing %s as a tool", t.isRec() && t.asRec().has(NAME) ? t.asRec().at(NAME).toCleanString() : t.vidOrTid());
                try {
                    if (t.isNothing()) {
                        // do nothing
                    } else if (t instanceof mTool) {
                        this.addTool((mTool) t);
                    } else if (t.isRec() && t.test(MCP_CLIENT_TYPE)) {
                        this.mcpClients.add(Rec.wrap(t.as(), mcpClient.class));
                    } else
                        this.addTool(mTool.tool(t));

                } catch (final Exception e) {
                    this.logger().warn("unable to build tool from %s (ignoring): %s", t, e.getMessage());
                }
            });
        }
        // ── 2. own usage-doc skill → the skill gateway (its tools are registered first-class) ──
        if (agent.hasFeature(LLM_SKILL_FEATURE_TID))
            agent.feature(LLM_SKILL_FEATURE_TID).<SkillFeature>as().addSkill(mSkill.of(rec(mutableMap(
                    uri(NAME), uri(LLM_TOOL_FEATURE_TID.name()),
                    uri(DESC), str("tool extensions intended for llm use"),
                    uri(CONTENT), str("any mtron inst can be added to tool feature and it will be mapped to an mcp tool")))));
        // ── 3. project the registry onto the agent's LC4j tool bag ──
        LOG.status(DEBUG, "registering %s tools", this.toolProvider.getTools().size());
        if (!this.mcpClients.isEmpty())
            this.addToolProvider(McpToolProvider.builder().mcpClients(this.mcpClients.stream().map(mcpClient::client).toList()).build());
        return noobj();
    }

    /**
     * The ledger this agent's conversation lives in — the session store that
     * serves it, and with it the pairing gate's scope.  Null when the agent
     * has no session (nothing of its is persisted, so nothing pairs either).
     */
    private static SpaceChatSessionStore ledger(final Agent agent) {
        if (!agent.hasFeature(LLM_MESSAGE_FEATURE_TID))
            return null;
        return agent.feature(LLM_MESSAGE_FEATURE_TID).<MessageFeature>as().store();
    }

    /**
     * Close the tool channel for a chat.  Every call id the loop never
     * produced a result for is a lost result, and every parked ai message is
     * then published as a complete group — with a synthetic
     * {@code lost_tool_result} for each request nothing answered.  Called from
     * {@code Agent.chat()}'s finally, so an interrupted turn is recorded
     * consistently (ai message + a result that says the result was lost)
     * rather than as an ai message whose requests have no results at all.
     *
     * @param agent   the agent whose chat is closing
     * @param callIds the tool call ids the loop abandoned
     */
    public void handleOrphanToolRequests(final Agent agent, final Set<String> callIds) {
        final SpaceChatSessionStore ledger = ledger(agent);
        if (null == ledger)
            return;
        if (!callIds.isEmpty())
            this.logger().debug("closing tool channel with %d unanswered requests: %s", callIds.size(), callIds);
        ToolPairGate.close(ledger, callId -> this.lostToolResult(agent, callId));
    }

    /**
     * The synthetic result for a request nothing answered — the result that
     * lets its ai message still be published as a pair.  Its {@code contents}
     * is the tool call id (the pair's join key, which the window rules read);
     * {@code lost_tool_result} rides the tool name, where the ledger keeps the
     * name of whatever answered the request.
     */
    private Rec lostToolResult(final Agent agent, final String toolCallId) {
        return MessageBuilder.buildToolResultMessage()
                .put(NAME, uri("lost_tool_result"))
                .text("noobj")
                .contents(toolCallId)
                .chatId(agent.chatId())
                .depth(agent.chatDepth())
                .time()
                .create();
    }

    @Override
    public void onToolExecuted(final Agent agent, final Obj result) {
        if (result.isRec()) {
            final Rec r = result.asRec();
            this.logger().debug("tool executed: %s(%s) => %s",
                    Str.Helper.cleanString(r.at(uri(NAME))),
                    Str.Helper.cleanString(r.at(uri(TOOL_ARGUMENTS))),
                    CommonUtil.clipString(Str.Helper.cleanString(r.at(uri(RESULT))), 50, true));

            // stage the result for the ai message parked on it — the pairing gate
            // is what writes the ledger (never this hook), so a result whose ai
            // side already published (an interrupted turn closed with a lost
            // result) is dropped rather than written orphaned
            final SpaceChatSessionStore ledger = ledger(agent);
            if (null != ledger) {
                try {
                    final String toolCallId = Str.Helper.cleanString(r.at(uri(CONTENTS)));
                    if (!ToolPairGate.isParked(ledger, toolCallId)) {
                        this.logger().debug("tool result for an unpublished request (ignored): %s", toolCallId);
                        return;
                    }
                    final String resultText = Str.Helper.cleanString(r.at(uri(RESULT)));
                    final MessageBuilder builder = MessageBuilder.build(TOOL_RESULT_MESSAGE_TID)
                            .put(NAME, uri(Str.Helper.cleanString(r.at(uri(NAME)))))
                            .text(resultText)
                            .contents(toolCallId)
                            .time()
                            .session(agent.feature(LLM_MESSAGE_FEATURE_TID).asRec().at(SESSION).uriValue())
                            .depth(agent.chatDepth())
                            .chatId(agent.chatId());
                    // Retrieve the raw Obj stashed by mTool before LC4j forced it to a string
                    final Obj stashed = mTool.resultStash.containsKey(toolCallId) ? mTool.resultStash.remove(toolCallId) : null;
                    if (stashed != null && (stashed.isRec() || stashed.isInst()))
                        builder.put(CHAT, stashed);
                    ToolPairGate.stage(ledger, toolCallId, builder.create());
                } catch (final Exception e) {
                    this.logger().warn("tool result staging failed (non-blocking): %s", e.getMessage());
                }
            }
        } else {
            this.logger().info("tool executed: %s", CommonUtil.clipString(result.toString(), 50, true));
        }
    }
}
