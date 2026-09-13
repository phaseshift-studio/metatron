package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatResult;
import studio.phaseshift.metatron.isa.llm.type.mModel;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.console.StatusLine;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class ChatFeature extends AbstractFeature {

    protected Rec lastMessage = rec0();

    public ChatFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static ChatFeature chatFeature(final mModel model, final Obj response) {
        return new ChatFeature(mutableMap(uri(MODEL), model, uri(RESPONSE), response), LLM_CHAT_FEATURE_TID, null);
    }

    public Rec lastMessage() {
        return this.lastMessage;
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        final String userMessage = agent.userMessage();
        if (null == userMessage || userMessage.isBlank())
            return noobj();
        if (agent.hasFeature(LLM_SYSTEM_FEATURE_TID)) {
            agent.feature(LLM_SYSTEM_FEATURE_TID).<SystemFeature>as().addSystemMessage(
                    """
                    your underlying inference model is:
                    %s
                    you are an agent in the metatron (http://metatron.phaseshift.studio).
                    you can control the metatron using the mtron language by either
                      1. calling an mtron eval tool
                      2. generating executable mtron code in your thoughts and responses.
                    any messages you produce containing templates of the form ${ code } will evaluate.
                    e.g. the text
                        the result you wanted is ${ 1.-<[+2,_]>-.sum() }
                    becomes
                        the result you wanted is 4.
                    """.formatted(this.at(MODEL)));
        }
        final Space space = Router.global().getSpaceFor(agent.at(ROOT).uriValue().extend(MESSAGE));
        if (space.hasQ(f(INCRQ))) {
            try {
                this.lastMessage = MessageBuilder.build(USER_MESSAGE_TID)
                        .text(Str.Helper.cleanString(str(userMessage).apply()))
                        .contents(userMessage)
                        .time()
                        .session(agent.hasFeature(LLM_MESSAGE_FEATURE_TID)
                                ? agent.feature(LLM_MESSAGE_FEATURE_TID).asRec().at(SESSION).uriValue()
                                : null)
                        .depth(agent.chatDepth())
                        .chatId(agent.chatId())
                        .create(agent.at(ROOT).uriValue().extend(MESSAGE).extend("_").addQ(INCRQ));
            } catch (final Exception e) {
                this.logger().warn("user message write failed: %s", e.getMessage());
            }
        } else {
            LOG.warn("user message storage requires an incrq space: %s", space.vidOrTid());
        }
        return noobj();
    }

    @Override
    public void onPartialResponse(final Agent agent, final Str text) {
        agent.feature(LLM_CHAT_FEATURE_TID).asRec().at(f(RESPONSE).extend(TO)).apply(text);
    }

    @Override
    public void onCompleteResponse(final Agent agent, final ChatResult result) {
        agent.feature(LLM_CHAT_FEATURE_TID).asRec().at(f(RESPONSE).extend("complete")).apply(result);
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

    /**
     * Write the messages a user sent while this turn was in flight into the
     * ledger, so a conversation conducted mid-iteration is durable instead of
     * confined to the tool result it rode in on.
     *
     * <p>The mid-chat channel belongs to {@code MidChatFeature}, which owns this
     * write and the subtype that tags it.  Kept here only as the fallback used
     * when no mid-chat feature is attached, so a queued message is still
     * persisted rather than stranded.
     */
    public void publishMidIterationChat(final Agent agent, final Lst chatMessages) {
        try {
            chatMessages.elements().forEach(message -> {
                MessageBuilder.buildUserMessage()
                        .sub(USER_MIDCHAT_TID)
                        .depth(agent.chatDepth())
                        .chatId(agent.chatId())
                        .session(agent.sessionVID())
                        .time(message.asRec().at(TIME).uriValue())
                        .text(message.asRec().at(TEXT).strValue())
                        .create(agent.at(ROOT).uriValue().extend(MESSAGE).extend("_").addQ(INCRQ));
            });
        } catch (final Exception e) {
            this.logger().warn("failed to publish mid-iteration chat: %s", e.getMessage());
        }
    }


    // @Override
    // public Set<fURI> requires() {
    //       return Set.of(LLM_SKILL_FEATURE_TID);
    //  }

    /**
     * Register this feature's skill with the SkillFeature gateway — the
     * gateway is the owner of the skill channel; this feature is a
     * contributor.  Exposing the agent's primary capability — {@code chat} —
     * as a tool lets an agent reduced to a {@code skill::T} (and ultimately
     * an MCP server) be chatted with; the gateway forwards the tool to the
     * ToolFeature gateway.
     */
    /*public void registerSkill(final Agent agent) {
        if (!agent.hasFeature(LLM_SKILL_FEATURE_TID))
            return;
        agent.feature(LLM_SKILL_FEATURE_TID).<SkillFeature>as().addSkill(mSkill.of(rec(mutableMap(
                uri(NAME), uri(LLM_CHAT_FEATURE_TID.name()),
                uri(DESC), str("chat with the agent"),
                uri(CONTENT), str("""
                                  if you are not accessing this skill via an mcp server, then you are in the metatron.
                                  if you are an agent, note that you are located at *%s.
                                  this means that you have native access to the uri graph and its associated objs.
                                  any time you want to control metatron, simply use mtron str::T templates in any of your 
                                  outputs (thoughts, responses) to invoke template expansion.
                                  this same feature applies to human users -- chat messages can leverage str::T templates.                               
                                  
                                  $\\{ 1.-<[+2,_]>-.sum() \\}
                                  
                                  Templates support recursive expansion where the output of the inner template will become a
                                  literal value in the outer expansion until no more templates are left to expand.
                                  
                                  "The magic number ${ 1 + ${ 2 + ${ 3 } + 4 } + 5 } wasn't so magical once I knew what it was."
                                  """.formatted(agent.vidOrTid())),
                uri(TOOL), lst(docWrap(instC(CHAT_INST_TID.dom(NOOBJ_TID.zero()).rng(LLM_CHAT_RESULT_TID),
                                lst(STR_TYPE),
                                (lhs, inst) -> agent.chat(inst.arg(0).strValue())),
                        "noobj lhs",
                        "the agent's chat response",
                        Map.of(jnt(0), "the message to send the agent"),
                        "chat with the agent and receive its response",
                        "chat('what is a database?')"))))));
    }*/
}
