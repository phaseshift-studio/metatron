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
import studio.phaseshift.metatron.TokenMapper;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.io.type.AbstractObjSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * Serializer that converts between LangChain4j {@link ChatMessage} instances
 * and metatron typed {@link Rec} objects.
 *
 * <h3>Direction</h3>
 * <ul>
 *   <li>{@link #read(ChatMessage)} — LC4j → space (ChatMessage → typed Rec)</li>
 *   <li>{@link #write(Obj)} — space → LC4j (typed Rec → ChatMessage)</li>
 * </ul>
 *
 * <h3>Text encoding</h3>
 * All text is stored as <b>raw plain strings</b> in the Rec — no mtron
 * quoting.  Legacy data that was stored with mtron-encoded text
 * ({@code '...'}, {@code """..."""}) is decoded transparently during
 * {@link #write(Obj)}.
 */
public class ObjChatMessageSerializer extends AbstractObjSerializer<ChatMessage> {

    public static final fURI OBJ_CHAT_MESSAGE_SERIALIZER_TID =
            fURI.Singleton.f("/m/llm/serializer/chat_message");
    public static final fURI OBJ_CHAT_MESSAGE_SERIALIZER_VID =
            OBJ_CHAT_MESSAGE_SERIALIZER_TID;

    // -- content-part type discrimination tokens --
    private static final String IMG = "image";
    private static final String AUD = "audio";
    private static final String VID = "video";
    private static final String PF = "pdf";

    // -- Known structural field sets per message type --
    // Fields NOT in these sets are treated as attributes during Rec→ChatMessage
    // conversion.  System fields (hash, time, session) are stripped by the
    // store and never reach extractAttributes.

    private static final Set<String> SYSTEM_KNOWN_KEYS = Set.of(TEXT);
    private static final Set<String> USER_KNOWN_KEYS = Set.of(NAME, CONTENTS);
    private static final Set<String> AI_KNOWN_KEYS = Set.of(TEXT, THINKING, TOOL_REQUESTS);
    private static final Set<String> TOOL_RESULT_KNOWN_KEYS = Set.of(TEXT, ID, NAME);

    // -- Token vocabulary ----------------------------------------------------
    static final TokenMapper VOCAB = new TokenMapper()
            .add(TOOL_RESULT_MESSAGE_TID, NAME, "toolName")
            .add(LLM_TOOL_TID, ARGS, "arguments");

    // -- Singleton -----------------------------------------------------------

    private static final ObjChatMessageSerializer INSTANCE = new ObjChatMessageSerializer();

    public static ObjChatMessageSerializer instance() {
        return INSTANCE;
    }

    public ObjChatMessageSerializer() {
        super(OBJ_CHAT_MESSAGE_SERIALIZER_TID, OBJ_CHAT_MESSAGE_SERIALIZER_VID);
    }

    // =========================================================================
    // ObjSerializer contract
    // =========================================================================

    /**
     * Convert a typed Rec into a LangChain4j ChatMessage.
     * Dispatches on the Rec's {@code _tid}.
     */
    @Override
    public ChatMessage write(final Obj obj) {
        if (obj instanceof Rec rec)
            return writeRec(rec);
        throw MTronException.of("ObjChatMessageSerializer.write expects a Rec, got: %s",
                obj.getClass().getSimpleName());
    }

    public ChatMessage writeRec(final Rec rec) {
        final fURI tid = rec.tid();
        if (tid.equals(SYSTEM_MESSAGE_TID))
            return toSystemMessage(rec);
        if (tid.equals(USER_MESSAGE_TID))
            return toUserMessage(rec);
        if (tid.equals(AI_MESSAGE_TID))
            return toAiMessage(rec);
        if (tid.equals(TOOL_REQUEST_MESSAGE_TID) || tid.equals(TOOL_RESULT_MESSAGE_TID))
            return toToolResultMessage(rec);
        throw MTronException.of("unknown message tid: %s", tid);
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) {
        throw MTronException.of("ObjChatMessageSerializer.inputBytes not supported — use read(ChatMessage) directly");
    }

    /**
     * Convert a LangChain4j ChatMessage into a typed Rec.
     * Dispatches on {@code instanceof}.
     */
    @Override
    public Obj read(final ChatMessage message) {
        final Obj messageRec = switch (message) {
            case SystemMessage msg -> fromSystemMessage(msg);
            case UserMessage msg -> fromUserMessage(msg);
            case AiMessage msg -> fromAiMessage(msg);
            case ToolExecutionResultMessage msg -> fromToolResultMessage(msg);
            default -> throw MTronException.of("unsupported chat message type: %s [%s]",
                    message.type(), message.getClass().getSimpleName());
        };
        return messageRec;
    }

    // =========================================================================
    // read: ChatMessage → Rec  (LC4j → space)
    // =========================================================================

    private static Rec fromSystemMessage(final SystemMessage msg) {
        return rec(mutableMap(uri(TEXT), str(msg.text())), SYSTEM_MESSAGE_TID, null);
    }

    private static Rec fromUserMessage(final UserMessage msg) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        if (msg.name() != null && !msg.name().isBlank())
            map.put(uri(NAME), str(msg.name()));
        if (msg.hasSingleText()) {
            map.put(uri(TEXT), str(msg.singleText()));
            map.put(uri(CONTENTS), str(msg.singleText()));
        } else {
            final List<Obj> parts = new ArrayList<>();
            for (final Content content : msg.contents())
                parts.add(fromContent(content));
            map.put(uri(CONTENTS), lst(parts));
            map.put(uri(TEXT), str(parts.stream().map(x -> x.jvm() + "").reduce("", (a, b) -> a + ";" + b)));
        }
        msg.attributes().forEach((k, v) -> map.putIfAbsent(uri(k), str(String.valueOf(v))));
        return rec(map, USER_MESSAGE_TID, null);
    }

    private static Rec fromContent(final Content content) {
        return switch (content.type()) {
            case TEXT -> rec(uri(TEXT), str(((TextContent) content).text()));
            case IMAGE -> {
                final Image img = ((ImageContent) content).image();
                yield rec(uri(IMG), fromMedia(img.url(), img.base64Data(), img.mimeType()));
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
                yield rec(uri(VID), fromMedia(video.url(), video.base64Data(), video.mimeType()));
            }
            case PDF -> {
                final PdfFile pdf = ((PdfFileContent) content).pdfFile();
                yield rec(uri(PF), fromMedia(pdf.url(), pdf.base64Data(), pdf.mimeType()));
            }
            default -> rec(uri(TEXT), str(content.toString()));
        };
    }

    private static Rec fromMedia(final java.net.URI url, final String base64Data,
                                 final String mimeType) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        if (url != null) map.put(uri(URL), uri(url.toString()));
        if (base64Data != null && !base64Data.isBlank())
            map.put(uri(DATA), bytes(ByteBuffer.wrap(Base64.getDecoder().decode(base64Data))));
        if (mimeType != null && !mimeType.isBlank())
            map.put(uri(MIME_TYPE), str(mimeType));
        if (map.isEmpty()) return rec(uri(TEXT), str("none"));
        return rec(map);
    }

    private static Rec fromAiMessage(final AiMessage msg) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        if (msg.text() != null && !msg.text().isBlank())
            map.put(uri(TEXT), str(msg.text()));
        if (msg.hasToolExecutionRequests()) {
            final List<Obj> toolReqs = new ArrayList<>();
            for (final ToolExecutionRequest req : msg.toolExecutionRequests())
                toolReqs.add(fromToolRequest(req));
            map.put(uri(TOOL_REQUESTS), lst(toolReqs));
            if (!map.containsKey(uri(CONTENTS)))
                map.put(uri(CONTENTS), str(toolReqs.toString()));
        }
        msg.attributes().forEach((k, v) -> map.putIfAbsent(uri(k), str(String.valueOf(v))));
        return rec(map, AI_MESSAGE_TID, null);
    }

    private static Rec fromToolRequest(final ToolExecutionRequest toolRequest) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        if (toolRequest.name() != null && !toolRequest.name().isBlank())
            map.put(uri(NAME), uri(toolRequest.name()));
        if (toolRequest.arguments() != null && !toolRequest.arguments().isBlank())
            map.put(uri(VOCAB.from(LLM_TOOL_TID, "arguments")), str(toolRequest.arguments()));
        if (toolRequest.id() != null && !toolRequest.id().isBlank())
            map.put(uri(CONTENTS), str(toolRequest.id()));
        map.put(uri(TEXT), str(toolRequest.name() + "(" + toolRequest.arguments() + ")"));
        return rec(map, TOOL_REQUEST_MESSAGE_TID, null);
    }

    private static Rec fromToolResultMessage(final ToolExecutionResultMessage msg) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(VOCAB.from(TOOL_RESULT_MESSAGE_TID, "toolName")), uri(msg.toolName()));
        final String rawText = msg.hasSingleText() && msg.text() != null && !msg.text().isBlank()
                ? msg.text() : msg.toString();
        // If the tool returned mtron-encoded text, decode it so we store
        // the plain form.  This avoids strings-within-strings that break
        // content-hash dedup.  The TEXT field is explicitly typed as Str —
        // tbleSpace must respect that regardless of whether the value
        // happens to start with { or [.
        final String textValue;
        if (!rawText.isEmpty() && (rawText.charAt(0) == '\'' || rawText.charAt(0) == '"')) {
            textValue = decodeRawValue(str(rawText));
        } else {
            textValue = rawText;
        }
        map.put(uri(TEXT), str(textValue));
        if (msg.id() != null && !msg.id().isBlank())
            map.put(uri(CONTENTS), str(msg.id()));
        msg.attributes().forEach((k, v) -> map.putIfAbsent(uri(k), str(String.valueOf(v))));
        return rec(map, TOOL_RESULT_MESSAGE_TID, null);
    }

    // =========================================================================
    // write: Rec → ChatMessage  (space → LC4j)
    // =========================================================================

    private static SystemMessage toSystemMessage(final Rec rec) {
        return SystemMessage.from(decodeText(rec, TEXT));
    }

    private static UserMessage toUserMessage(final Rec rec) {
        final String name = Str.Helper.cleanString(rec.at(uri(NAME)).orElse(str("")), true);
        final String nameOrNull = name.isBlank() || "none".equals(name) ? null : name;
        final Map<String, Object> attrs = extractAttributes(rec, USER_KNOWN_KEYS);

        final UserMessage.Builder builder = UserMessage.builder()
                .name(nameOrNull)
                .attributes(attrs);

        final Obj contents = rec.at(uri(CONTENTS));
        if (contents.isNoObj()) {
            builder.addContent(TextContent.from(""));
        } else if (contents.isLst()) {
            final List<Content> contentList = contents.asLst().elements()
                    .filter(Obj::isRec)
                    .map(c -> toContent(c.asRec()))
                    .filter(Objects::nonNull)
                    .toList();
            if (contentList.isEmpty())
                builder.addContent(TextContent.from("none"));
            else
                builder.contents(contentList);
        } else {
            builder.addContent(TextContent.from(decodeRawValue(contents)));
        }
        return builder.build();
    }

    private static Content toContent(final Rec part) {
        if (part.has(uri(TEXT)))
            return TextContent.from(decodeText(part, TEXT));
        if (part.has(uri(IMG)))
            return ImageContent.from(toImage(part.at(uri(IMG)).asRec()));
        if (part.has(uri(AUD)))
            return AudioContent.from(toAudio(part.at(uri(AUD)).asRec()));
        if (part.has(uri(VID)))
            return VideoContent.from(toVideo(part.at(uri(VID)).asRec()));
        if (part.has(uri(PF)))
            return PdfFileContent.from(toPdf(part.at(uri(PF)).asRec()));
        return null;
    }

    private static Image toImage(final Rec rec) {
        final Image.Builder b = Image.builder();
        if (rec.has(uri(MIME_TYPE))) b.mimeType(rec.at(uri(MIME_TYPE)).strValue());
        if (rec.has(uri(URL))) b.url(rec.at(uri(URL)).strValue());
        if (rec.has(uri(DATA)))
            b.base64Data(Base64.getEncoder().encodeToString(rec.at(uri(DATA)).bytesValue().array()));
        return b.build();
    }

    private static Audio toAudio(final Rec rec) {
        final Audio.Builder b = Audio.builder();
        if (rec.has(uri(MIME_TYPE))) b.mimeType(rec.at(uri(MIME_TYPE)).strValue());
        if (rec.has(uri(URL))) b.url(rec.at(uri(URL)).strValue());
        if (rec.has(uri(DATA)))
            b.base64Data(Base64.getEncoder().encodeToString(rec.at(uri(DATA)).bytesValue().array()));
        return b.build();
    }

    private static Video toVideo(final Rec rec) {
        final Video.Builder b = Video.builder();
        if (rec.has(uri(MIME_TYPE))) b.mimeType(rec.at(uri(MIME_TYPE)).strValue());
        if (rec.has(uri(URL))) b.url(Str.Helper.cleanString(rec.at(uri(URL)).orElse(str("")), true));
        if (rec.has(uri(DATA)))
            b.base64Data(Base64.getEncoder().encodeToString(rec.at(uri(DATA)).bytesValue().array()));
        return b.build();
    }

    private static PdfFile toPdf(final Rec rec) {
        final PdfFile.Builder b = PdfFile.builder();
        if (rec.has(uri(URL))) b.url(rec.at(uri(URL)).strValue());
        if (rec.has(uri(DATA)))
            b.base64Data(Base64.getEncoder().encodeToString(rec.at(uri(DATA)).bytesValue().array()));
        return b.build();
    }

    private static AiMessage toAiMessage(final Rec rec) {
        final String text = decodeText(rec, TEXT);
        final Map<String, Object> attrs = extractAttributes(rec, AI_KNOWN_KEYS);
        final AiMessage.Builder builder = AiMessage.builder()
                .attributes(attrs);
        if (!text.isEmpty())
            builder.text(text);

        // Tool execution requests — may be a proper Lst from _mtron_meta
        // type coercion, or a raw Str from a read path that didn't apply
        // column type metadata.
        final List<ToolExecutionRequest> requests = parseToolRequests(rec);
        if (!requests.isEmpty())
            builder.toolExecutionRequests(requests);

        return builder.build();
    }

    private static List<ToolExecutionRequest> parseToolRequests(final Rec rec) {
        Obj trObj = rec.at(uri(TOOL_REQUESTS));
        if (trObj.isNoObj())
            return List.of();
        // tbleSpace may store nested Lst as a mtron string in TEXT columns;
        // parse it back if readMaybeJSON returned a plain Str.
        if (trObj.isStr()) {
            try {
                trObj = ObjmtronSerializer.compact().inputBytes(trObj.strValue().getBytes(StandardCharsets.UTF_8));
            } catch (final Exception ignored) { /* leave as-is */ }
        }
        if (!trObj.isLst())
            return List.of();
        final Lst toolReqs = trObj.asLst();
        if (toolReqs.lstValue().isEmpty())
            return List.of();
        final String argsToken = VOCAB.from(LLM_TOOL_TID, "arguments");
        return toolReqs.elements()
                .filter(Obj::isRec)
                .map(tr -> {
                    final Rec trRec = tr.asRec();
                    return ToolExecutionRequest.builder()
                            .name(Str.Helper.cleanString(trRec.at(uri(NAME)).orElse(str("")), true))
                            .arguments(trRec.at(uri(argsToken)).orElse(str("")).strValue())
                            .id(Str.Helper.cleanString(trRec.at(uri(CONTENTS)).orElse(str("")), true))
                            .build();
                })
                .toList();
    }

    private static ToolExecutionResultMessage toToolResultMessage(final Rec rec) {
        final String nameToken = VOCAB.from(TOOL_RESULT_MESSAGE_TID, "toolName");
        final Map<String, Object> attrs = extractAttributes(rec, TOOL_RESULT_KNOWN_KEYS);
        final String text = decodeText(rec, TEXT);
        return ToolExecutionResultMessage.builder()
                .id(Str.Helper.cleanString(rec.at(uri(CONTENTS)).orElse(str("")), true))
                .toolName(Str.Helper.cleanString(rec.at(uri(nameToken)).orElse(str("")), true))
                .text(text)
                .attributes(attrs)
                .build();
    }

    // =========================================================================
    // Text decoding — handles both raw and legacy mtron-encoded text
    // =========================================================================

    /**
     * Extract and decode the text at {@code key} from the Rec.
     * Handles three formats:
     * <ol>
     *   <li><b>Raw string</b> — used as-is (current format).</li>
     *   <li><b>Legacy mtron-quoted</b> ({@code '...'}, {@code "..."},
     *       {@code """..."""}) — decoded transparently.</li>
     *   <li><b>Missing</b> — returns empty string.</li>
     * </ol>
     */
    static String decodeText(final Rec rec, final String key) {
        final Obj val = rec.at(uri(key)).orElse(str(""));
        return decodeRawValue(val);
    }

    /**
     * Decode a single Obj value to its plain string representation.
     * Loops until no more mtron quote wrapping is detected so that
     * nested encoding (strings within strings) is fully unwound.
     * This is critical for content-hash stability: if different
     * layers of mtron encoding use different quote styles
     * ({@code '}, {@code "}, {@code """}, {@code '''}), the same
     * logical text must still produce the same decoded form.
     */
    static String decodeRawValue(final Obj val) {
        if (val.isNoObj()) return "";
        String raw = Str.Helper.cleanString(val, true);
        if (raw.isEmpty()) return raw;

        // Loop to handle arbitrarily nested mtron encoding.
        // cleanString() strips leading/trailing quotes from the raw
        // string value; if what remains still looks mtron-encoded,
        // parse it and repeat until stable.
        while (true) {
            final char first = raw.charAt(0);
            if (first != '\'' && first != '"') break;
            try {
                final Obj parsed = ObjmtronSerializer.singleNoClip().inputBytes(raw);
                if (parsed.isFail()) break;
                final String decoded = Str.Helper.cleanString(parsed, true);
                if (decoded.equals(raw)) break; // stable — prevent infinite loop
                raw = decoded;
            } catch (final Exception ignored) {
                // Not valid mtron — use as-is
                break;
            }
        }
        return raw;
    }

    // =========================================================================
    // Attribute extraction
    // =========================================================================

    /**
     * Collect extra rec fields as a string-keyed attribute map for placement
     * into {@code ChatMessage.attributes()}.
     */
    private static Map<String, Object> extractAttributes(final Rec rec, final Set<String> knownKeys) {
        final Map<String, Object> attrs = new LinkedHashMap<>();
        for (final Map.Entry<Obj, Obj> e : rec.recValue().entrySet()) {
            if (!e.getKey().isUri()) continue;
            final String keyName = e.getKey().uriValue().name();
            if (knownKeys.contains(keyName)) continue;
            // Internal infrastructure — never expose to LC4j
            if ("hash".equals(keyName) || TIME.equals(keyName) || SESSION.equals(keyName) || DEPTH.equals(keyName) || CHAT_ID.equals(keyName) || CHAT.equals(keyName)) continue;
            attrs.put(keyName, Str.Helper.cleanString(e.getValue(), true));
        }
        return attrs;
    }
}
