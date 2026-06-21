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

package studio.phaseshift.metatron.isa.llm.space;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.data.video.Video;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import studio.phaseshift.metatron.TokenMapper;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class SpaceChatMemoryStore implements ChatMemoryStore {

    private static final GraphittyLogger LOG = Graphitty.log(SpaceChatMemoryStore.class);

    private final Space space;

    /**
     * Token vocabulary mapping for the LC4j API boundary.
     * Only non-identity mappings are registered; identity is the default fallback.
     * <pre>
     *   Context                   Metatron token   LC4j field
     *   TOOL_RESULT_MESSAGE_TID   NAME          -> toolName
     *   LLM_TOOL_TID              ARGS          -> arguments
     * </pre>
     */
    static final TokenMapper VOCAB = new TokenMapper()
            .add(TOOL_RESULT_MESSAGE_TID, NAME, "toolName")
            .add(LLM_TOOL_TID, ARGS, "arguments");

    // -- content-part type discrimination tokens --
    private static final String IMG = "image";
    private static final String AUD = "audio";
    private static final String VID = "video";
    private static final String PF = "pdf";

    public SpaceChatMemoryStore(final Space space) {
        this.space = Objects.requireNonNull(space, "space must not be null");
    }

    public Space space() {
        return this.space;
    }

    ///////////////////////////////////////////////////////////////////////////
    // ChatMemoryStore interface
    //
    // Messages are typed Recs stored inline in the `mem` Lst:
    //   { mem: [system::[...], user::[...], ai::[...], tool_result::[...]], max: N }
    //
    // TID discrimination (system::T, user::T, ai::T, tool_result::T) provides
    // type-aware readback.  Single read, single write -- compatible with every
    // space backend.

    /// ////////////////////////////////////////////////////////////////////////

    @Override
    public List<ChatMessage> getMessages(final Object memoryId) {
        if (!(memoryId instanceof fURI memVID))
            return new ArrayList<>();

        final Obj memoryObj = this.space.read(memVID);
        if (memoryObj.isNoObj() || !memoryObj.isRec()) {
            LOG.warn("memory is not a %s", LLM_MEMORY_TID);
            return new ArrayList<>();
        }

        final Obj memField = memoryObj.asRec().at(uri("mem"));
        if (memField.isNoObj() || !memField.isLst()) {
            LOG.warn("memory entries are not a %s", LLM_MEMORY_TID);
            return new ArrayList<>();
        }

        final List<ChatMessage> result = new ArrayList<>();
        memField.asLst().elements().forEach(el -> {
            try {
                if (el.isRec()) {
                    final ChatMessage cm = recToChatMessage(el.asRec());
                    if (cm != null)
                        result.add(cm);
                }
            } catch (final Exception e) {
                LOG.warn("error reading chat message (ignoring): %s", e);
            }
        });
        LOG.debug("read %d messages for memory %s", result.size(), memVID);
        return result;
    }

    @Override
    public void updateMessages(final Object memoryId, final List<ChatMessage> messages) {
        if (!(memoryId instanceof fURI memoryVID))
            return;

        // Read existing row to preserve non-memory fields (agent_id, name, etc.)
        // and the max window size.  Missing fields in the update would be
        // interpreted as "set to NULL" by tbleSpace's diff engine, which
        // breaks NOT NULL columns.
        final Obj existingObj = Router.readFromSpace(memoryVID);
        final Map<Obj, Obj> memMap = new LinkedHashMap<>();
        if (existingObj.isRec()) {
            final Rec existingRec = existingObj.asRec();
            // Carry forward every field EXCEPT mem (we are replacing it) and max
            // (we read it separately so it survives replacement).
            for (final Obj key : existingRec.keys().toList()) {
                if (key.isUri() && !key.uriValue().name().equals("mem")
                        && !key.uriValue().name().equals(MAX))
                    memMap.put(key, existingRec.at(key));
            }
        }
        final int existingMax = (existingObj.isRec())
                ? existingObj.asRec().at(uri(MAX)).orElse(jnt(15)).intValue().intValue()
                : 15;

        final List<Obj> messageRecs = new ArrayList<>();
        for (final ChatMessage msg : messages) {
            try {
                messageRecs.add(chatMessageToRec(msg));
            } catch (final Exception e) {
                LOG.warn("error converting chat message (ignoring): %s", e);
            }
        }

        memMap.put(uri("mem"), str(new String(ObjSimpleJSONSerializer.single().outputBytes(lst(messageRecs)).array())));
        memMap.put(uri(MAX), jnt(existingMax));
        // final Rec updatedMemory = (Rec) rec(memMap).vid(memVID);
        Router.writeToSpace(memoryVID, rec(memMap).selfVID(memoryVID));
        LOG.debug("wrote %d messages for memory %s", messageRecs.size(), memoryVID);
    }

    @Override
    public void deleteMessages(final Object memoryId) {
        if (memoryId instanceof fURI memVID) {
            final Lst messages = this.space.read(memVID.extend("mem")).orElse(lst());
            messages.lstValue().clear();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // ChatMessage -> typed Rec  (boundary: LC4j field -> metatron token)

    /// ////////////////////////////////////////////////////////////////////////

    public static Rec chatMessageToRec(final ChatMessage message) {

        if (SystemMessage.class.isAssignableFrom(message.type().messageClass()))
            return systemMessageToRec((SystemMessage) message);
        if (UserMessage.class.isAssignableFrom(message.type().messageClass()))
            return userMessageToRec((UserMessage) message);
        if (AiMessage.class.isAssignableFrom(message.type().messageClass()))
            return aiMessageToRec((AiMessage) message);
        if (ToolExecutionResultMessage.class.isAssignableFrom(message.type().messageClass()))
            return toolResultMessageToRec((ToolExecutionResultMessage) message);
            //if (CustomMessage.class.isAssignableFrom(message.type().messageClass()))
            //   return toolResultMessageToRec((ToolExecutionResultMessage) message);
        else
            throw MTronException.of("unsupported message type: %s [%s]", message.type(), message.getClass().getSimpleName());
    }

    private static Rec systemMessageToRec(final SystemMessage msg) {
        return rec(mutableMap(uri(TEXT), str(msg.text()), uri(TYPE), uri(msg.type().name())), SYSTEM_MESSAGE_TID, null);
    }

    private static Rec userMessageToRec(final UserMessage msg) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        if (msg.name() != null && !msg.name().isBlank())
            map.put(uri(NAME), str(msg.name()));
        if (msg.hasSingleText()) {
            map.put(uri(CONTENTS), rec(uri(TEXT), str(msg.singleText())));
        } else {
            final List<Obj> parts = new ArrayList<>();
            for (final Content content : msg.contents())
                parts.add(contentToRec(content));
            map.put(uri(CONTENTS), lst(parts));
        }
        map.put(uri(TYPE), uri(msg.type().name()));
        return rec(map, USER_MESSAGE_TID, null);
    }

    private static Rec contentToRec(final Content content) {
        return switch (content.type()) {
            case TEXT -> rec(uri(TEXT), str(((TextContent) content).text()));
            case IMAGE -> {
                final Image img = ((ImageContent) content).image();
                yield rec(uri(IMG), mediaToRec(img.url(), img.base64Data(), img.mimeType()));
            }
            case AUDIO -> {
                final Audio audio = ((AudioContent) content).audio();
                final Map<Obj, Obj> audMap = new LinkedHashMap<>();
                if (audio.binaryData() != null && audio.binaryData().length > 0)
                    audMap.put(uri(DATA), bytes(ByteBuffer.wrap(audio.binaryData())));
                else if (audio.base64Data() != null && !audio.base64Data().isBlank())
                    audMap.put(uri(DATA), bytes(ByteBuffer.wrap(Base64.getDecoder().decode(audio.base64Data()))));
                if (audio.url() != null)
                    audMap.put(uri(URL), uri(audio.url().toString()));
                if (audio.mimeType() != null && !audio.mimeType().isBlank())
                    audMap.put(uri(MIME_TYPE), str(audio.mimeType()));
                yield rec(uri(AUD), rec(audMap));
            }
            case VIDEO -> {
                final Video video = ((VideoContent) content).video();
                yield rec(uri(VID), mediaToRec(video.url(), video.base64Data(), video.mimeType()));
            }
            case PDF -> {
                final PdfFile pdf = ((PdfFileContent) content).pdfFile();
                yield rec(uri(PF), mediaToRec(pdf.url(), pdf.base64Data(), pdf.mimeType()));
            }
            default -> rec(uri(TEXT), str("none"));
        };
    }

    private static Rec mediaToRec(final java.net.URI url, final String base64Data, final String mimeType) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        if (url != null) map.put(uri(URL), uri(url.toString()));
        if (base64Data != null && !base64Data.isBlank())
            map.put(uri(DATA), bytes(ByteBuffer.wrap(Base64.getDecoder().decode(base64Data))));
        if (mimeType != null && !mimeType.isBlank())
            map.put(uri(MIME_TYPE), str(mimeType));
        if (map.isEmpty()) return rec(uri(TEXT), str("none"));
        return rec(map);
    }

    private static Rec aiMessageToRec(final AiMessage msg) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        if (msg.text() != null && !msg.text().isBlank())
            map.put(uri(TEXT), str(msg.text()));
        if (msg.hasToolExecutionRequests()) {
            final List<Obj> toolReqs = new ArrayList<>();
            for (final ToolExecutionRequest req : msg.toolExecutionRequests())
                toolReqs.add(toolRequestToRec(req));
            map.put(uri(TOOL_REQUESTS), lst(toolReqs, LST_TID, null));
        }
        if (!msg.attributes().isEmpty())
            map.put(uri(ATTRIBUTES), msg.attributes().entrySet().stream().map(kv -> rel(uri(kv.getKey()), uri(kv.getValue().toString()))).collect(new CommonUtil.RecCollector()));
        map.put(uri(TYPE), uri(msg.type().name()));
        return rec(map, AI_MESSAGE_TID, null);
    }

    private static Rec toolRequestToRec(final ToolExecutionRequest req) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        if (req.name() != null && !req.name().isBlank())
            map.put(uri(NAME), str(req.name()));
        if (req.arguments() != null && !req.arguments().isBlank())
            map.put(uri(VOCAB.from(LLM_TOOL_TID, "arguments")), str(req.arguments()));
        if (req.id() != null && !req.id().isBlank())
            map.put(uri(ID), str(req.id()));
        map.put(uri(TYPE), uri("tool_request"));
        return rec(map, TOOL_REQUEST_MESSAGE_TID, null);
    }

    private static Rec toolResultMessageToRec(final ToolExecutionResultMessage msg) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(VOCAB.from(TOOL_RESULT_MESSAGE_TID, "toolName")), str(msg.toolName()));
        map.put(uri(TEXT), msg.text() != null && !msg.text().isBlank() ? str(msg.text()) : str("none"));
        if (msg.id() != null && !msg.id().isBlank())
            map.put(uri(ID), str(msg.id()));
        map.put(uri(TYPE), uri(msg.type().name()));
        return rec(map, TOOL_RESULT_MESSAGE_TID, null);
    }

///////////////////////////////////////////////////////////////////////////
// typed Rec -> ChatMessage  (boundary: metatron token -> LC4j field)

    /// ////////////////////////////////////////////////////////////////////////

    public static ChatMessage recToChatMessage(final Rec rec) {
        final fURI tid = rec.tid();
        final String type = Str.Helper.cleanString(rec.at(TYPE).orElse(str("")));
        if (tid.equals(SYSTEM_MESSAGE_TID) || type.equals(ChatMessageType.SYSTEM.name()))
            return recToSystemMessage(rec);
        if (tid.equals(USER_MESSAGE_TID) || type.equals(ChatMessageType.USER.name())) return recToUserMessage(rec);
        if (tid.equals(AI_MESSAGE_TID) || type.equals(ChatMessageType.AI.name())) return recToAiMessage(rec);
        if (tid.equals(TOOL_REQUEST_MESSAGE_TID) || type.equals(ChatMessageType.CUSTOM.name()))
            return recToToolResultMessage(rec);
        if (tid.equals(TOOL_RESULT_MESSAGE_TID) || type.equals(ChatMessageType.TOOL_EXECUTION_RESULT.name()))
            return recToToolResultMessage(rec);
        LOG.warn("unknown message type: %s", rec);
        return recToSystemMessage(rec);
    }

    private static SystemMessage recToSystemMessage(final Rec rec) {
        return SystemMessage.from(Str.Helper.cleanString(rec.at(uri(TEXT)).orElse(str("none"))));
    }

    private static UserMessage recToUserMessage(final Rec rec) {
        final String name = Str.Helper.cleanString(rec.at(uri(NAME)).orElse(str("none")));
        final String nameOrNull = name.isBlank() ? null : name;
        final Obj contents = rec.at(uri(CONTENTS));
        if (contents.isNoObj())
            return UserMessage.from(nameOrNull, "");

        if (contents.isLst()) {
            final List<Content> contentList = contents.asLst().elements()
                    .filter(Obj::isRec)
                    .map(c -> recToContent(c.asRec()))
                    .filter(Objects::nonNull)
                    .toList();
            if (contentList.isEmpty())
                return UserMessage.from(nameOrNull, "none");
            return UserMessage.from(nameOrNull, contentList.toArray(new Content[0]));
        }

        if (contents.isRec()) {
            final Rec contentRec = contents.asRec();
            if (contentRec.has(uri(TEXT)))
                return UserMessage.from(nameOrNull, Str.Helper.cleanString(contentRec.at(uri(TEXT))));
            final Content content = recToContent(contentRec);
            if (content != null)
                return UserMessage.from(nameOrNull, content);
        }

        return UserMessage.from(nameOrNull, "");
    }

    private static Content recToContent(final Rec part) {
        if (part.has(uri(TEXT)))
            return TextContent.from(Str.Helper.cleanString(part.at(uri(TEXT))));
        if (part.has(uri(IMG)))
            return ImageContent.from(recToImage(part.at(uri(IMG)).asRec()));
        if (part.has(uri(AUD)))
            return AudioContent.from(recToAudio(part.at(uri(AUD)).asRec()));
        if (part.has(uri(VID)))
            return VideoContent.from(recToVideo(part.at(uri(VID)).asRec()));
        if (part.has(uri(PF)))
            return PdfFileContent.from(recToPdf(part.at(uri(PF)).asRec()));
        return null;
    }

    private static Image recToImage(final Rec rec) {
        final Image.Builder b = Image.builder();
        if (rec.has(uri(MIME_TYPE)))
            b.mimeType(rec.at(uri(MIME_TYPE)).strValue());
        if (rec.has(uri(URL)))
            b.url(rec.at(uri(URL)).strValue());
        if (rec.has(uri(DATA)))
            b.base64Data(Base64.getEncoder().encodeToString(rec.at(uri(DATA)).bytesValue().array()));
        return b.build();
    }

    private static Audio recToAudio(final Rec rec) {
        final Audio.Builder b = Audio.builder();
        if (rec.has(uri(MIME_TYPE)))
            b.mimeType(rec.at(uri(MIME_TYPE)).strValue());
        if (rec.has(uri(URL)))
            b.url(rec.at(uri(URL)).strValue());
        if (rec.has(uri(DATA)))
            b.base64Data(Base64.getEncoder().encodeToString(rec.at(uri(DATA)).bytesValue().array()));
        return b.build();
    }

    private static Video recToVideo(final Rec rec) {
        final Video.Builder b = Video.builder();
        if (rec.has(uri(MIME_TYPE)))
            b.mimeType(rec.at(uri(MIME_TYPE)).strValue());
        if (rec.has(uri(URL)))
            b.url(Str.Helper.cleanString(rec.at(uri(URL)).orElse(str(""))));
        if (rec.has(uri(DATA)))
            b.base64Data(Base64.getEncoder().encodeToString(rec.at(uri(DATA)).bytesValue().array()));
        return b.build();
    }

    private static PdfFile recToPdf(final Rec rec) {
        final PdfFile.Builder b = PdfFile.builder();
        if (rec.has(uri(URL)))
            b.url(rec.at(uri(URL)).strValue());
        if (rec.has(uri(DATA)))
            b.base64Data(Base64.getEncoder().encodeToString(rec.at(uri(DATA)).bytesValue().array()));
        return b.build();
    }

    private static AiMessage recToAiMessage(final Rec rec) {
        final String text = Str.Helper.cleanString(rec.at(uri(TEXT)).orElse(str("none")));
        if (rec.has(uri(TOOL_REQUESTS))) {
            final Lst toolReqs = rec.at(uri(TOOL_REQUESTS)).asLst();
            final List<ToolExecutionRequest> requests = toolReqs.elements()
                    .filter(Obj::isRec)
                    .map(tr -> {
                        final Rec trRec = tr.asRec();
                        final String argsToken = VOCAB.from(LLM_TOOL_TID, "arguments");
                        return ToolExecutionRequest.builder()
                                .name(Str.Helper.cleanString(trRec.at(uri(NAME)).orElse(str(""))))
                                .arguments(Str.Helper.cleanString(trRec.at(uri(argsToken)).orElse(str(""))))
                                .id(Str.Helper.cleanString(trRec.at(uri(ID)).orElse(str(""))))
                                .build();
                    })
                    .toList();
            return AiMessage.from(text, requests);
        }
        return AiMessage.from(text);
    }

    private static ToolExecutionResultMessage recToToolResultMessage(final Rec rec) {
        final String nameToken = VOCAB.from(TOOL_RESULT_MESSAGE_TID, "toolName");
        return ToolExecutionResultMessage.from(
                Str.Helper.cleanString(rec.at(uri(ID)).orElse(str(""))),
                Str.Helper.cleanString(rec.at(uri(nameToken)).orElse(str(""))),
                Str.Helper.cleanString(rec.at(uri(TEXT)).orElse(str("none")))
        );
    }
}
