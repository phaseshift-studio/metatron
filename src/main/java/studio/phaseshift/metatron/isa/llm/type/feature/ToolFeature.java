package studio.phaseshift.metatron.isa.llm.type.feature;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProvider;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.llm.mToolProvider;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.llm.type.mcpClient;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
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
    private final Set<McpClient> mcpClients = new HashSet<>();

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
                        final McpClient client = Rec.wrap(t.as(), mcpClient.class).client();
                        this.mcpClients.add(client);
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
            this.addToolProvider(McpToolProvider.builder().mcpClients(new ArrayList<>(this.mcpClients)).build());
        return noobj();
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
            if (agent.hasFeature(LLM_MESSAGE_FEATURE_TID)) {
                try {
                    final String resultText = Str.Helper.cleanString(r.at(uri(RESULT)));
                    final MessageBuilder builder = MessageBuilder.build(TOOL_RESULT_MESSAGE_TID)
                            .put(NAME, uri(Str.Helper.cleanString(r.at(uri(NAME)))))
                            .text(resultText)
                            .contents(Str.Helper.cleanString(r.at(uri(CONTENTS))))
                            .time()
                            .session(agent.feature(LLM_MESSAGE_FEATURE_TID).asRec().at(SESSION).uriValue())
                            .depth(agent.chatDepth())
                            .chatId(agent.chatId());

                    // Retrieve the raw Obj stashed by mTool before LC4j forced it to a string
                    final String toolCallId = Str.Helper.cleanString(r.at(uri(CONTENTS)));
                    final Obj stashed = mTool.resultStash.containsKey(toolCallId) ? mTool.resultStash.remove(toolCallId) : null;
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
