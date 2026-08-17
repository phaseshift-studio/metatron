package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.Model;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Str;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.res;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class ChatFeature extends AbstractFeature {

    public ChatFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static ChatFeature chatFeature(final Model model, final Obj response) {
        return new ChatFeature(mutableMap(uri(MODEL), model, uri(RESPONSE), response), LLM_CHAT_FEATURE_TID, null);
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        final String userMessage = agent.userMessage();
        if (userMessage == null || userMessage.isBlank())
            return noobj();

        try {
            MessageBuilder.build(USER_MESSAGE_TID)
                    .text(userMessage)
                    .contents(userMessage)
                    .time()
                    .session(agent.hasFeature(SESSION)
                            ? agent.feature(SESSION).asRec().at(SESSION).uriValue()
                            : null)
                    .depth(agent.chatDepth())
                    .chatId(agent.chatId())
                    .create(agent.at(ROOT).uriValue().extend(MESSAGE)
                            .extend("_").addQ(INCRQ));
        } catch (final Exception e) {
            this.logger().warn("user message write failed (non-blocking): %s", e.getMessage());
        }
        return noobj();
    }

    @Override
    public void onPartialResponse(final Agent agent, final Str text) {
        agent.feature(CHAT).asRec().at(f(RESPONSE).extend(TO)).apply(text);
    }

    @Override
    public void onCompleteResponse(final Agent agent, final Str text) {
        // Formatted responses are already structured Recs (parsed from JSON) —
        // store directly at res(CHAT) so the result is navigable without the
        // extra 'response' wrapper.  Free-text stays wrapped as {response=>...}.
        if (text.isStr())
            agent.at(res(CHAT, RESPONSE), text, MUTABLE);
        else
            agent.at(res(CHAT), text, MUTABLE);
        // AiMessages are persisted by SpaceChatSessionStore.updateMessages(),
        // which catches both intermediate tool_call responses (that never
        // reach TokenStream.onCompleteResponse) and the final text response.
    }

    private static final fURI CHAT_INST_TID = LLM_CHAT_FEATURE_TID.extend(INST).extend("agent_chat");

    /**
     * Expose the agent's primary capability — {@code chat} — as a tool, so that
     * an agent reduced to a {@code skill::T} (and ultimately an MCP server) can
     * be chatted with.  The agent is captured from the {@code skill(agent)}
     * argument; the tool's lhs is noobj.
     */
    @Override
    public Lst skill(final Agent agent) {
        return lst(rec(mutableMap(
                uri(NAME), uri(CHAT),
                uri(DESC), str("chat with the agent"),
                uri(CONTENT), str("send a message to the agent and receive its response"),
                uri(TOOL), lst(docWrap(instC(CHAT_INST_TID.dom(NOOBJ_TID.zero()).rng(LLM_CHAT_RESULT_TID),
                                lst(STR_TYPE),
                                (lhs, inst) -> agent.chat(inst.arg(0).strValue())),
                        "noobj lhs",
                        "the agent's chat response",
                        Map.of(jnt(0), "the message to send the agent"),
                        "chat with the agent and receive its response",
                        "chat('what is a database?')"))), LLM_SKILL_TID, null));
    }
}
