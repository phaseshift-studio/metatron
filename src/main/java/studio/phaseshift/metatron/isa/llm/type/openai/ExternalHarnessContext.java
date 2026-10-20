package studio.phaseshift.metatron.isa.llm.type.openai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.space.ObjChatMessageSerializer;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.CONTENT;
import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.Tokens.ROLE;
import static studio.phaseshift.metatron.Tokens.TOOLS;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.AI_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.SYSTEM_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.TOOL_RESULT_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.USER_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The OpenAI request, projected onto metatron objs once at parse time — the transport's
 * "external context".  Every accessor returns proper metatron types ({@code message::T}
 * recs via {@link ObjChatMessageSerializer}, {@code tool::T} {@code mTool}s), so the
 * {@code openai_feature} downstream never touches JSON strings.
 */
public class ExternalHarnessContext extends MRec {

    public ExternalHarnessContext(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static ExternalHarnessContext of(final Rec request) {
        return new ExternalHarnessContext(new LinkedHashMap<>(request.jvm()), request.tid(), request.vid());
    }

    private List<Rec> rawMessages() {
        final Obj messages = this.at(uri("messages"));
        if (!messages.isLst())
            return List.of();
        final List<Rec> list = new ArrayList<>();
        for (final Obj m : messages.asLst().elements().toList())
            if (m.isRec())
                list.add(m.asRec());
        return list;
    }

    /**
     * The request's {@code messages[]} projected onto metatron {@code message::T} recs —
     * {@code role} maps to the message tid (user/ai/system/tool_result).
     */
    public List<Rec> getMessages() {
        final List<Rec> list = new ArrayList<>();
        for (final Rec r : this.rawMessages())
            list.add(ObjChatMessageSerializer.instance().read(this.toChatMessage(r)).asRec());
        return list;
    }

    /**
     * OpenAI wire message ({@code role}+{@code content}+tool fields) → LC4j ChatMessage,
     * so {@link ObjChatMessageSerializer} can project it onto the full metatron
     * {@code message::T} shape (text, image/audio/video content parts, tool requests,
     * tool results).
     */
    private ChatMessage toChatMessage(final Rec r) {
        final Obj role = r.at(uri(ROLE));
        final Obj content = r.at(uri(CONTENT));
        final String roleStr = role.isNoObj() ? "" : role.toCleanString();
        final String text = content.isNoObj() ? "" : content.toCleanString();
        return switch (roleStr) {
            case "system" -> SystemMessage.from(text);
            case "user" -> UserMessage.from(text);
            case "assistant" -> AiMessage.from(text);
            case "tool" -> ToolExecutionResultMessage.builder()
                    .id(r.at(uri("tool_call_id")).isNoObj() ? null : r.at(uri("tool_call_id")).toCleanString())
                    .toolName(r.at(uri(NAME)).isNoObj() ? null : r.at(uri(NAME)).toCleanString())
                    .text(text)
                    .build();
            default -> UserMessage.from(text);
        };
    }

    /**
     * The request's {@code tools[]} projected onto harness-owned {@code tool::T} {@code mTool}s —
     * each an {@link ExternalTool} whose executor yields (records + interrupts) instead of evaluating.
     */
    public List<mTool> getTools() {
        final Obj tools = this.at(uri(TOOLS));
        if (!tools.isLst())
            return List.of();
        final List<mTool> list = new ArrayList<>();
        for (final Obj t : tools.asLst().elements().toList()) {
            final ExternalTool tool = ExternalTool.from(t);
            if (null != tool)
                list.add(tool);
        }
        return list;
    }

    /**
     * The last {@code role:tool} message (a harness tool result re-entering the turn), or
     * null when the request carries none.
     */
    public Rec toolResultMessage() {
        final List<Rec> messages = this.rawMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            final Obj role = messages.get(i).at(uri(ROLE));
            if (!role.isNoObj() && "tool".equals(role.toCleanString()))
                return messages.get(i);
        }
        return null;
    }

    /**
     * The system message as a {@code message::T} rec (tid {@code /m/llm/message/system}),
     * or null when the request carries none.  Projected through the same
     * {@link ObjChatMessageSerializer} path as {@link #getMessages()}.
     */
    public Rec systemMessage() {
        for (final Rec m : this.getMessages())
            if (m.tid().equals(SYSTEM_MESSAGE_TID))
                return m;
        return null;
    }

    /**
     * The last user message's content — the prompt to chat with.
     */
    public String currentPrompt() {
        final List<Rec> messages = this.rawMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            final Obj role = messages.get(i).at(uri(ROLE));
            if (!role.isNoObj() && "user".equals(role.toCleanString())) {
                final Obj content = messages.get(i).at(uri(CONTENT));
                return content.isNoObj() ? "" : content.toCleanString();
            }
        }
        return null;
    }
}
