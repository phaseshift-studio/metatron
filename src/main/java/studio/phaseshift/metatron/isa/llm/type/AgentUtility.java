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

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.skills.Skills;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.llm.LLMFactory;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatSessionStore;
import studio.phaseshift.metatron.isa.llm.type.feature.Feature;
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.llm.type.mcpClient;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCQ;
import static studio.phaseshift.metatron.isa.llm.type.mTool.LLM_TOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.MCP_CLIENT_TYPE;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * Translates mtron feature configs into LangChain4j objects for the AI
 * service builder.  Reads feature config directly from the Agent's
 * feature list — features ARE the config, no need for push-to-agent
 * intermediary.
 */
final class AgentUtility {

    private AgentUtility() {
    }

    // ── Main entry point ──────────────────────────────────────────

    static void buildService(final Agent agent, final AiServices<AgentServices> service) {
        buildTools(agent, service);
        buildSession(agent, service);
        buildSkills(agent, service);
        buildSystemMessage(agent, service);
    }

    static StreamingChatModel createChatModel(final Agent agent) {
        final Obj chatFeat = agent.feature(CHAT);
        if (chatFeat.isNoObj())
            throw MTronException.of("agent has no chat feature: %s", agent.vidOrTid());
        final Rec chat = chatFeat.asRec();
        return LLMFactory.createChatInteraction(agent,
                chat.at(uri(MODEL)),
                chat.at(uri(RESPONSE)),
                chat.at(uri(FORMAT)));
    }

    // ── Tool capabilities ─────────────────────────────────────────

    static void buildTools(final Agent agent, final AiServices<AgentServices> service) {
        final Obj toolFeat = agent.feature(TOOL);
        if (toolFeat.isNoObj()) return;
        final Poly tool = (Poly) toolFeat;
        final Obj chest = tool.at(uri(CHEST));
        if (chest.isNoObj()) return;

        final Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();
        final List<McpClient> mcpClients = new ArrayList<>();

        chest.elements().forEach(t -> {
            try {
                if (t.isRec() && t.test(MCP_CLIENT_TYPE)) {
                    mcpClients.add(Rec.wrap(t.as(), mcpClient.class).client());
                } else if (t.isObjInst()) {
                    if (!QCollection.isNoDocs(Router.readFromSpace(t.tid().addQ(DOCQ)))) {
                        final Tuple.Pair<ToolSpecification, ToolExecutor> pair =
                                mTool.mtronInstToolSpecification(mTool.mtronInstToTool(t.asInst()));
                        tools.put(pair.get0(), pair.get1());
                    }
                } else if (t.isRec() && t.test(LLM_TOOL_TYPE)) {
                    final Tuple.Pair<ToolSpecification, ToolExecutor> pair =
                            mTool.mtronInstToolSpecification(t.asRec());
                    tools.put(pair.get0(), pair.get1());
                }
            } catch (final Exception e) {
                agent.logger().error("unable to build tool from %s: %s", t, e.getMessage());
            }
        });

        if (!tools.isEmpty())
            service.tools(tools);
        if (!mcpClients.isEmpty())
            service.toolProvider(McpToolProvider.builder().mcpClients(mcpClients).build());
    }

    // ── Session capability ────────────────────────────────────────

    static void buildSession(final Agent agent, final AiServices<AgentServices> service) {
        final Obj sessFeat = agent.feature(SESSION);
        if (sessFeat.isNoObj()) return;
        final Obj sessionObj = ((Poly) sessFeat).at(uri(SESSION));
        if (sessionObj.isNoObj()) return;

        final Rec session = sessionObj.asRec();
        final fURI sessionVID = session.vid();
        if (sessionVID == null) return;

        try {
            final Space space = Router.global().getSpaceFor(sessionVID);
            final SpaceChatSessionStore store = new SpaceChatSessionStore(agent, space);
            final int max = session.at(ALGORITHM).asRec().at(MAX).orElse(jnt(15)).intValue().intValue();
            final ChatMemory chatMemory = MessageWindowChatMemory.builder()
                    .alwaysKeepSystemMessageFirst(true)
                    .maxMessages(max)
                    .id(sessionVID)
                    .chatMemoryStore(store)
                    .build();
            service.chatMemory(chatMemory).storeRetrievedContentInChatMemory(true);
        } catch (final Exception e) {
            throw MTronException.of("unable to setup session: %s", e);
        }
    }

    // ── Skill capability ──────────────────────────────────────────

    static void buildSkills(final Agent agent, final AiServices<AgentServices> service) {
        final List<dev.langchain4j.skills.Skill> allSkills = new ArrayList<>();

        // Collect skills from features that have the skill() method
        for (final Obj entry : agent.features().asLst().elements().toList()) {
            if (!(entry instanceof Feature f)) {
                agent.logger().warn("non-feature obj in agent features: %s", Obj.Helper.specificTypeId(entry));
                continue;
            }
            final Obj skillObj = f.skill();
            if (skillObj.isNoObj()) continue;
            try {
                final var skill = mSkill.of(skillObj.asRec()).toSkill();
                allSkills.add(skill);
                agent.logger().info("adding %s skill from feature %s", skill.name(), f.tid());
            } catch (final Exception e) {
                agent.logger().warn("failed to build skill from feature %s: %s", f.tid(), e.getMessage());
            }
        }

        // Also collect skills from the SkillFeature config
        final Obj skillFeat = agent.feature(SKILL);
        if (!skillFeat.isNoObj()) {
            final Lst skillsObj = skillFeat.asRec().atLst(SKILL);
            if (!skillsObj.isEmpty()) {
                skillsObj.elements()
                        .map(s -> s.isUri() ?
                                mSkill.of(fsSpace.staticObjToFile(s)).toSkill() :
                                mSkill.of(s.apply().asRec()).toSkill())
                        .forEach(allSkills::add);
            }
        }

        if (allSkills.isEmpty()) return;

        try {
            final Skills skills = new Skills.Builder().skills(allSkills).build();
            final ToolProvider skillToolProvider = skills.toolProvider();
            agent.addSystemMessage(
                    "\nYou have access to the following skills:\n" +
                            skills.formatAvailableSkills()
                            + "\nWhen the user's request relates to one of these skills, activate it first using the `activate_skill` tool before proceeding.");
            service.toolProvider(skillToolProvider);
        } catch (final Exception e) {
            throw MTronException.of("unable to setup skills: %s", e);
        }
    }

    // ── System message ────────────────────────────────────────────

    static void buildSystemMessage(final Agent agent, final AiServices<AgentServices> service) {
        final String systemMessage = String.join("\n", agent.getSystemMessages());
        if (!systemMessage.isBlank())
            service.systemMessage(systemMessage);
    }
}
