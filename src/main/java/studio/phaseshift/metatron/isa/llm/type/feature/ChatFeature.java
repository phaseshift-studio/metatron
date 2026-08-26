package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatResult;
import studio.phaseshift.metatron.isa.llm.type.Model;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
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
            if (Router.global().getSpaceFor(agent.at(ROOT).uriValue().extend(MESSAGE)).hasQ(f(INCRQ))) {
                MessageBuilder.build(USER_MESSAGE_TID)
                        .text(Str.Helper.cleanString(str(userMessage).apply()))
                        .contents(userMessage)
                        .time()
                        .session(agent.hasFeature(SESSION)
                                ? agent.feature(SESSION).asRec().at(SESSION).uriValue()
                                : null)
                        .depth(agent.chatDepth())
                        .chatId(agent.chatId())
                        .create(agent.at(ROOT).uriValue().extend(MESSAGE)
                                .extend("_").addQ(INCRQ));
            }
        } catch (final Exception e) {
            this.logger().warn("user message write failed (non-blocking): %s", e.getMessage());
        }
        return noobj();
    }

    @Override
    public void onPartialResponse(final Agent agent, final Str text) {
        agent.feature(CHAT).asRec().at(f(RESPONSE).extend(TO)).apply(text);
    }

    /**
     * Write the assembled {@code chat_result::T} to the chat feature's root
     * space (e.g. {@code /usr/dr/chat_result/_?incrq}).  Called by
     * {@code Agent.chat()} after every feature's {@code onCompleteResponse}
     * hook has run — the Agent owns the chat_result lifecycle, this feature
     * owns the persist logic and the {@code root} the result is stored at.
     */
    public void persist(final Agent agent, final ChatResult result) {
        final Obj root = this.at(ROOT);
        if (root.isNoObj())
            return;
        try {
            Router.writeToSpace(root.uriValue().extend("_").addQ(INCRQ), result);
        } catch (final Exception e) {
            this.logger().warn("failed to persist chat_result: %s", e.getMessage());
        }
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
                uri(NAME), uri(LLM_CHAT_FEATURE_TID.name()),
                uri(DESC), str("chat with the agent"),
                uri(CONTENT), str("""
                                  if you are not accessing this skill via an mcp server, then you are in the metatron.
                                  if you are an agent, note that you are located at *%s.
                                  this means that you have native access to the uri graph and its associated objs.
                                  any time you want to control metatron, simply use mtron str::T templates in any of your 
                                  outputs (thoughts, responses) to invoke template expansion.
                                  this same feature applies to human users -- chat messages can leverage str::T templates.
                                  
                                  There are two forms of template expansion.
                                  
                                  \\{\\{\\{ 1 + 2 \\}\\}\\}
                                  
                                  and
                                  
                                  $\\{ 1.-<[+2,_]>-.sum() \\}
                                  
                                  Both support recursive expansion where the output of the inner template will become a
                                  literal value in the outer expansion until no more templates are left to expand.
                                  
                                  "The magic number {{{ 1 + {{{ 2 + {{{ 3 }}} + 4 }}} + 5 }}} wasn't so magical once I knew what it was."
                                  """.formatted(agent.vidOrTid())),
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
