package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.MessageBuilder;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatFrame;
import studio.phaseshift.metatron.isa.llm.type.feature.service.ChatService;
import studio.phaseshift.metatron.isa.llm.type.feature.service.MessageService;
import studio.phaseshift.metatron.isa.llm.type.feature.service.SystemService;
import studio.phaseshift.metatron.isa.llm.type.mModel;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.util.CommonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class ChatFeature extends AbstractFeature implements ChatService {
    public static final fURI FEATURE_TID = studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_CHAT_FEATURE_TID;

    @Override
    public Set<fURI> offers() {
        return Set.of(LLM_CHAT_SERVICE_TID);
    }


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

    public mModel model() {
        return mModel.model(this.at(MODEL));
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        final String userMessage = agent.userMessage();
        if (null == userMessage || userMessage.isBlank())
            return noobj();
        final List<String> languages = new ArrayList<>();
        try (final Stream<String> temp = Files.list(Path.of("conf/nanorc"))
                .filter(f -> f.getFileName().toString().endsWith(".nanorc"))
                //.peek(f -> LOG.info("loading syntax highlighting language: %s", f))
                .map(f -> f.getFileName().toString().split("\\.")[0])) {
            languages.addAll(temp.toList());
        } catch (final IOException e) {
            LOG.error("unable to access conf/nanorc directory");
        }
        if (agent.hasFeature(LLM_SYSTEM_FEATURE_TID)) {
            agent.requireService(SystemService.class).addSystemMessage(
                    """
                    ---[chat_feature]---
                    your underlying inference model is:
                    %s
                    ---[syntax_feature]---
                    your thoughts and responses can be syntax highlighted using triple backtick markup.
                      \\```java
                      public static void method() { }
                      \\```
                    available languages include:
                      %s
                    """.formatted(CommonUtil.indent(this.at(MODEL).toString(), 2), languages));
        }

        try {
            if (agent.service(MessageService.class).isPresent()) {
                this.lastMessage = agent.requireService(MessageService.class)
                        .addMessage(agent, MessageBuilder.build(USER_MESSAGE_TID)
                                .text(Str.Helper.cleanString(str(userMessage).apply()))
                                .contents(userMessage)
                                .time()
                                .session(agent.sessionVID())
                                .depth(agent.chatDepth())
                                .chatId(agent.chatId())
                                .create());
            }
        } catch (final Exception e) {
            this.logger().warn("user message write failed: %s", e.getMessage());
        }
        return noobj();
    }

    @Override
    public void onPartialResponse(final Agent agent, final Str text) {
        agent.feature(LLM_CHAT_FEATURE_TID).asRec().at(f(RESPONSE).extend(TO)).apply(text);
    }

    @Override
    public void onCompleteResponse(final Agent agent, final ChatFrame result) {
        agent.feature(LLM_CHAT_FEATURE_TID).asRec().at(f(RESPONSE).extend("complete")).apply(result);
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
 /*   public void publishMidIterationChat(final Agent agent, final Lst chatMessages) {
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
*/

    // @Override
    // public Set<fURI> requires() {
    //       return Set.of(LLM_SKILL_SERVICE_TID);
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
        agent.requireService(SkillService.class).addSkill(mSkill.of(rec(mutableMap(
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
