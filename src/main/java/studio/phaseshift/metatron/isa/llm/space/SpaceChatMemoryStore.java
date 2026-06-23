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
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class SpaceChatMemoryStore implements ChatMemoryStore {

    private static final GraphittyLogger LOG = Graphitty.log(SpaceChatMemoryStore.class);

    private final Space space;

    /** Hashes already mirrored to typed tables, keyed by memory VID.
     *  Prevents duplicates across instances (mModel creates a new store per
     *  chat call) without leaking state between different memory sessions. */
    private static final Map<fURI, Set<String>> MIRRORED_HASHES = new LinkedHashMap<>();

    /** Clear the static mirror dedup cache.  Called between tests that
     *  reuse the same memory VID pattern on fresh space instances. */
    static void clearMirrorCache() {
        synchronized (MIRRORED_HASHES) {
            MIRRORED_HASHES.clear();
        }
    }

    /** Sub-path under the memory VID where individual messages are stored. */
    private static final String MSG = "msg";

    /** Key for the content hash field embedded in each stored message Rec. */
    private static final String CONTENT_HASH = "content_hash";

    /**
     * Mirror a pre-built system message Rec to the typed table.
     * System messages bypass {@code ChatMemoryStore.updateMessages()} — they're
     * injected via {@code AiServices.systemMessage()}.  This method provides
     * the same fire-and-forget mirror that user/AI messages get.
     */
    public static void mirrorSystemMessage(final Space space, final fURI memoryVID, final Rec systemRec) {
        final String hash = contentHash(systemRec);
        systemRec.recValue().put(uri(CONTENT_HASH), str(hash));
        // System messages bypass ChatMemory — use a synthetic URI for the back-link
        systemRec.recValue().put(uri(URI), str("<" + memoryVID.scheme() + ":msg/" + memoryVID.name() + "/system>"));
        synchronized (MIRRORED_HASHES) {
            final Set<String> seen = MIRRORED_HASHES.computeIfAbsent(memoryVID, k -> new LinkedHashSet<>());
            if (!seen.add(hash)) return;
        }
        try {
            space.write(f(memoryVID.scheme() + ":llm_message_system/+?incrq"), systemRec);
        } catch (final Exception e) {
            LOG.debug("mirror system message failed (non-blocking): %s", e.getMessage());
        }
    }

    /** Key for the message count field within the algorithm_json rec. */
    private static final String MESSAGE_COUNT = "message_count";

    /**
     * Builds a KV-safe base URI for message storage derived from a memory VID.
     * Uses a separate {@code msg} collection prefix (not under the memory table
     * path) to avoid colliding with tbleSpace's table-mapped field-write semantics.
     *
     * <p>Example: memory VID {@code llm:llm_memory/1} → {@code llm:msg/1}
     */
    private static fURI msgPath(final fURI memoryVID) {
        return f(memoryVID.scheme() + ":" + MSG).extend(memoryVID.name());
    }

    /**
     * Fire-and-forget mirror of a message Rec to its per-type table.
     * Uses {@code +?incrq} so tbleSpace's DB-backed incrQ delegates
     * ID generation to AUTO_INCREMENT / SERIAL — no overwrites.
     * Purely additive; the per-type tables grow with every message.
     * <p>
     * TID → table mapping:
     * <pre>
     *   /m/llm/system      → llm_message_system/+?incrq
     *   /m/llm/user        → llm_message_user/+?incrq
     *   /m/llm/AI          → llm_message_ai/+?incrq
     *   /m/llm/tool_result → llm_message_tool_result/+?incrq
     * </pre>
     */
    private void mirrorToTypedTable(final fURI memoryVID, final Rec msgRec, final fURI kvURI) {
        final String table = perTypeTableName(msgRec.tid());
        if (table == null) return;
        final Obj hf = msgRec.at(uri(CONTENT_HASH));
        if (hf.isNoObj()) return;
        final String hash = hf.strValue();
        // Already mirrored for this memory — skip
        synchronized (MIRRORED_HASHES) {
            final Set<String> seen = MIRRORED_HASHES.computeIfAbsent(memoryVID, k -> new LinkedHashSet<>());
            if (!seen.add(hash)) return;
        }
        try {
            // Flatten nested content for SQL-friendly columns: for single-text
            // user messages, promote contents.text → text so the column stores
            // the plain string instead of [text=>"..."].
            final Rec toMirror;
            if (msgRec.tid().equals(USER_MESSAGE_TID)) {
                final Obj contents = msgRec.at(uri(CONTENTS));
                if (contents.isRec() && contents.asRec().has(uri(TEXT))) {
                    final Map<Obj, Obj> flat = new LinkedHashMap<>(msgRec.recValue());
                    flat.put(uri(TEXT), contents.asRec().at(uri(TEXT)));
                    flat.putIfAbsent(uri(NAME), str(""));
                    toMirror = rec(flat, msgRec.tid(), null);
                } else {
                    final Map<Obj, Obj> flat = new LinkedHashMap<>(msgRec.recValue());
                    flat.putIfAbsent(uri(NAME), str(""));
                    toMirror = rec(flat, msgRec.tid(), null);
                }
            } else {
                toMirror = msgRec;
            }
            // Stamp the KV URI so the typed row links back to the authoritative
            // message in the key-value store (reconstruction, navigation).
            toMirror.recValue().put(uri(URI), str("<" + kvURI + ">"));

            this.space.write(f(memoryVID.scheme() + ":" + table + "/+?incrq"), toMirror);
        } catch (final Exception e) {
            LOG.debug("mirror to typed table failed (non-blocking): %s", e.getMessage());
        }
    }

    /** Map a message TID to its per-type table name. */
    private static String perTypeTableName(final fURI tid) {
        if (tid == null) return null;
        if (tid.equals(SYSTEM_MESSAGE_TID)) return "llm_message_system";
        if (tid.equals(USER_MESSAGE_TID)) return "llm_message_user";
        if (tid.equals(AI_MESSAGE_TID)) return "llm_message_ai";
        if (tid.equals(TOOL_RESULT_MESSAGE_TID)) return "llm_message_tool_result";
        if (tid.equals(TOOL_REQUEST_MESSAGE_TID)) return "llm_message_tool_result";
        return null;
    }

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
    // Messages are stored individually at {scheme}:msg/{memId}/{position}.
    // Each message Rec carries a content_hash field for incremental diffing.
    // The memory policy object (memoryVID) carries the algorithm rec with max,
    // message_count, and future algorithm parameters.
    //
    // URI topology:
    //   {scheme}:llm_memory/1         → memory policy (agent_id, name, algorithm)
    //   {scheme}:msg/1/0              → message at position 0
    //   {scheme}:msg/1/1              → message at position 1
    //   ...

    /// ////////////////////////////////////////////////////////////////////////

    @Override
    public List<ChatMessage> getMessages(final Object memoryId) {
        if (!(memoryId instanceof fURI memVID))
            return new ArrayList<>();

        final fURI msgPath = msgPath(memVID);

        // Read memory policy first — gives us max window and message_count
        int windowMax = 15;
        int msgCount = 0;
        try {
            final Obj memoryObj = Router.readFromSpace(memVID);
            if (memoryObj.isRec()) {
                final Obj algorithm = memoryObj.asRec().at(uri(ALGORITHM));
                if (algorithm.isRec()) {
                    windowMax = algorithm.asRec().at(uri(MAX)).orElse(jnt(15)).intValue().intValue();
                    msgCount = algorithm.asRec().at(uri(MESSAGE_COUNT)).orElse(jnt(0)).intValue().intValue();
                }
            }
        } catch (final Exception e) {
            LOG.debug("could not read memory policy for %s (using default max=15): %s", memVID, e);
        }

        // Read messages by position up to message_count (may have gaps from eviction)
        final TreeMap<Integer, Rec> positionMap = new TreeMap<>();
        try {
            for (int pos = 0; pos < msgCount; pos++) {
                final Obj msgObj = Router.readFromSpace(msgPath.extend(String.valueOf(pos)));
                if (msgObj.isRec()) positionMap.put(pos, msgObj.asRec());
            }
        } catch (final Exception e) {
            LOG.warn("error reading messages for memory %s: %s", memVID, e);
            return new ArrayList<>();
        }

        // Convert to ChatMessage list in position order
        final List<ChatMessage> result = new ArrayList<>();
        for (final Rec msgRec : positionMap.values()) {
            try {
                final ChatMessage cm = recToChatMessage(msgRec);
                if (cm != null)
                    result.add(cm);
            } catch (final Exception e) {
                LOG.warn("error converting stored message to ChatMessage (ignoring): %s", e);
            }
        }

        // Apply max window — return only the last N messages
        if (result.size() > windowMax) {
            return result.subList(result.size() - windowMax, result.size());
        }
        LOG.debug("read %d messages for memory %s (window max=%d, stored=%d)",
                result.size(), memVID, windowMax, msgCount);
        return result;
    }

    @Override
    public void updateMessages(final Object memoryId, final List<ChatMessage> messages) {
        if (!(memoryId instanceof fURI memVID))
            return;

        final fURI msgPath = msgPath(memVID);

        // -- 1. Read existing memory policy ----------------------------------
        int windowMax = 15;
        int existingCount = 0;
        final Map<Obj, Obj> existingMemoryFields = new LinkedHashMap<>();
        try {
            final Obj existingObj = Router.readFromSpace(memVID);
            if (existingObj.isRec()) {
                final Rec existingRec = existingObj.asRec();
                // Preserve all existing fields except those we recalculate
                for (final Obj key : existingRec.keys().toList()) {
                    if (key.isUri()) {
                        final String keyName = key.uriValue().name();
                        if (!ALGORITHM.equals(keyName))
                            existingMemoryFields.put(key, existingRec.at(key));
                    }
                }
                // Read algorithm config
                final Obj algorithm = existingRec.at(uri(ALGORITHM));
                if (algorithm.isRec()) {
                    windowMax = algorithm.asRec().at(uri(MAX)).orElse(jnt(15)).intValue().intValue();
                    existingCount = algorithm.asRec().at(uri(MESSAGE_COUNT)).orElse(jnt(0)).intValue().intValue();
                }
            }
        } catch (final Exception e) {
            LOG.debug("could not read existing memory policy for %s (creating new): %s", memVID, e);
        }

        // -- 2. Read stored message hashes (up to existingCount) --------------
        final Map<String, Integer> storedHashMap = new LinkedHashMap<>(); // hash -> position
        try {
            for (int pos = 0; pos < existingCount; pos++) {
                final Obj msgObj = Router.readFromSpace(msgPath.extend(String.valueOf(pos)));
                if (msgObj.isRec()) {
                    final Obj hf = msgObj.asRec().at(uri(CONTENT_HASH));
                    if (!hf.isNoObj())
                        storedHashMap.put(hf.strValue(), pos);
                }
            }
        } catch (final Exception e) {
            LOG.debug("could not read stored messages for %s (starting fresh): %s", memVID, e);
        }

        // -- 3. Convert incoming messages to typed Recs + compute hashes -----
        final List<Rec> incomingRecs = new ArrayList<>();
        final Set<String> incomingHashes = new LinkedHashSet<>();
        for (final ChatMessage msg : messages) {
            try {
                final Rec msgRec = chatMessageToRec(msg);
                final String hash = contentHash(msgRec);
                msgRec.recValue().put(uri(CONTENT_HASH), str(hash));
                incomingRecs.add(msgRec);
                incomingHashes.add(hash);
            } catch (final Exception e) {
                LOG.error("error converting incoming chat message (type=%s, class=%s): %s",
                        msg.type(), msg.getClass().getSimpleName(), e);
            }
        }

        // -- 4. Write new messages & reposition existing ones ----------------
        // Messages are stored at incoming-list-index positions (0..N-1).
        // Messages with matching hashes at different positions are moved.
        // Messages with matching hashes at the same position are skipped.
        // Mirrors are batched: if two messages land on the same KV URI
        // (streaming partial → complete), only the last one is mirrored.
        final Set<Integer> writtenPositions = new HashSet<>();
        final java.util.Map<fURI, Rec> batchedMirrors = new LinkedHashMap<>();
        for (int pos = 0; pos < incomingRecs.size(); pos++) {
            final Rec incomingRec = incomingRecs.get(pos);
            final String incomingHash = incomingRec.recValue().get(uri(CONTENT_HASH)).strValue();

            if (storedHashMap.containsKey(incomingHash)) {
                final int storedPos = storedHashMap.get(incomingHash);
                if (storedPos == pos) {
                    writtenPositions.add(pos);
                    continue; // idempotent
                }
                Router.writeToSpace(msgPath.extend(String.valueOf(storedPos)), noobj());
                Router.writeToSpace(msgPath.extend(String.valueOf(pos)), incomingRec);
            } else {
                final fURI kvURI = msgPath.extend(String.valueOf(pos));
                Router.writeToSpace(kvURI, incomingRec);
                batchedMirrors.put(kvURI, incomingRec);
            }
            writtenPositions.add(pos);
        }
        // Write mirrors — last message per URI wins (streaming dedup)
        for (final Map.Entry<fURI, Rec> e : batchedMirrors.entrySet())
            mirrorToTypedTable(memVID, e.getValue(), e.getKey());

        // -- 5. Delete messages no longer in the window ----------------------
        // Guard: never delete positions we just wrote (hash may have changed
        // between calls, causing step 4's write to be undone by step 5).
        for (final Map.Entry<String, Integer> entry : storedHashMap.entrySet()) {
            if (!incomingHashes.contains(entry.getKey())
                    && !writtenPositions.contains(entry.getValue())) {
                Router.writeToSpace(msgPath.extend(String.valueOf(entry.getValue())), noobj());
            }
        }

        // Also clean up any positions beyond the current window
        for (int pos = incomingRecs.size(); pos <= existingCount + 10; pos++) {
            try {
                final Obj orphan = Router.readFromSpace(msgPath.extend(String.valueOf(pos)));
                if (!orphan.isNoObj()) {
                    Router.writeToSpace(msgPath.extend(String.valueOf(pos)), noobj());
                } else {
                    break; // no more orphaned entries beyond this point
                }
            } catch (final Exception e) {
                break;
            }
        }

        // -- 6. Update memory policy (algorithm + required fields) -----------
        final int newMessageCount = Math.max(existingCount, incomingRecs.size());
        final Map<Obj, Obj> algorithmMap = new LinkedHashMap<>();
        algorithmMap.put(uri(MAX), jnt(windowMax));
        algorithmMap.put(uri(MESSAGE_COUNT), jnt(newMessageCount));

        existingMemoryFields.put(uri(ALGORITHM), rec(algorithmMap));
        // Ensure required columns are always present (first write may not have
        // stored them if createTableFromRecord dropped fields)
        existingMemoryFields.putIfAbsent(uri("agent_id"), str("default"));
        existingMemoryFields.putIfAbsent(uri("name"), str("default"));

        Router.writeToSpace(memVID, rec(existingMemoryFields).selfVID(memVID));
        LOG.debug("wrote %d messages for memory %s (window max=%d, total count=%d)",
                incomingRecs.size(), memVID, windowMax, newMessageCount);
    }

    @Override
    public void deleteMessages(final Object memoryId) {
        if (!(memoryId instanceof fURI memVID))
            return;

        // Read message_count so we know how many positions to scan
        int msgCount = 0;
        try {
            final Obj memoryObj = Router.readFromSpace(memVID);
            if (memoryObj.isRec()) {
                final Obj algorithm = memoryObj.asRec().at(uri(ALGORITHM));
                if (algorithm.isRec())
                    msgCount = algorithm.asRec().at(uri(MESSAGE_COUNT)).orElse(jnt(0)).intValue().intValue();
            }
        } catch (final Exception e) {
            LOG.warn("could not read message_count for deletion: %s", e);
        }

        final fURI msgPath = msgPath(memVID);
        for (int pos = 0; pos < msgCount; pos++) {
            try {
                Router.writeToSpace(msgPath.extend(String.valueOf(pos)), noobj());
            } catch (final Exception e) {
                LOG.warn("error deleting message at position %d for memory %s: %s", pos, memVID, e);
            }
        }
        LOG.debug("deleted %d messages for memory %s", msgCount, memVID);
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

    ///////////////////////////////////////////////////////////////////////////
    // Content hashing for incremental update diffing
    // The hash covers the canonical message Rec (excluding the hash field itself)
    // so that identical messages produce identical hashes regardless of storage
    // position or metadata.

    /// ////////////////////////////////////////////////////////////////////////

    /**
     * Computes a SHA-256 content hash of the given message Rec.
     * Strips volatile metadata fields ({@code content_hash}, {@code name},
     * {@code type}, {@code thinking}) so the same logical message produces
     * the same hash across calls even when LC4j mutates metadata.
     */
    static String contentHash(final Rec msgRec) {
        // Save and strip volatile fields that LC4j may change between calls
        final Map<Obj, Obj> saved = new LinkedHashMap<>();
        final Obj[] volatileKeys = {uri(CONTENT_HASH), uri(NAME), uri(TYPE), uri(THINKING)};
        for (final Obj key : volatileKeys) {
            final Obj val = msgRec.recValue().remove(key);
            if (val != null) saved.put(key, val);
        }
        try {
            final byte[] jsonBytes = ObjSimpleJSONSerializer.single().outputBytes(msgRec).array();
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            final byte[] digest = md.digest(jsonBytes);
            final StringBuilder sb = new StringBuilder();
            for (final byte b : digest)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw MTronException.of("SHA-256 not available: %s", e);
        } finally {
            // Restore stripped fields
            msgRec.recValue().putAll(saved);
        }
    }

}
