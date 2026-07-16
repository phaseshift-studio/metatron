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
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.res;
import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_BYTE_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class SpaceChatSessionStore implements ChatMemoryStore {

    private static final GraphittyLogger LOG = Graphitty.log(SpaceChatSessionStore.class);

    private final Agent agent;
    private final Space space;

    /**
     * Messages written by this store instance, keyed by TID.
     * Used by ConceptFeature to link concepts to recently written messages.
     */
    private final Set<fURI> currentMessages = new HashSet<>();

    /**
     * Name of the unified polymorphic message table stored under the session path.
     */
    private static final String LLM_MESSAGE_TABLE = "llm_message";

    /**
     * Per-session dedup: set of content hashes already written, keyed by session VID.
     * Prevents duplicates across store instances (LC4j creates a new store per chat call)
     * without leaking state between different sessions.
     */
    private static final Map<fURI, Set<String>> WRITTEN_HASHES = new LinkedHashMap<>();

    /**
     * Clear the static dedup cache.  Called between tests that reuse the same
     * session VID pattern on fresh space instances.
     */
    static void clearDedupCache() {
        synchronized (WRITTEN_HASHES) {
            WRITTEN_HASHES.clear();
        }
    }

    /**
     * Build the write URI for the unified message table.
     * <pre>
     *   llm:llm_session/1  →  llm:llm_message/_?incrq
     *   /db/llm_session/1  →  /db/llm_message/_?incrq
     * </pre>
     */
    private static fURI llmMessagePath(final fURI sessionVID) {
        return sessionVID.retract(2).extend(LLM_MESSAGE_TABLE).extend("_").addQ("incrq");
    }

    /**
     * Mirror a pre-built system message Rec to the unified message table.
     * System messages bypass {@code ChatMemoryStore.updateMessages()} — they're
     * injected via {@code AiServices.systemMessage()}.
     */
    public static void mirrorSystemMessage(final Space space, final fURI sessionVID, final Rec systemRec) {
        final String hash = contentHash(systemRec);
        systemRec.recValue().put(uri(HASH), str(hash));
        systemRec.recValue().put(uri(TIME), str(Date.from(Instant.now()).toString()));
        systemRec.recValue().put(uri(SESSION), uri(sessionVID));
        synchronized (WRITTEN_HASHES) {
            final Set<String> seen = WRITTEN_HASHES.computeIfAbsent(sessionVID, k -> new LinkedHashSet<>());
            if (!seen.add(hash)) return;
        }
        try {
            Router.writeToSpace(llmMessagePath(sessionVID), systemRec);
        } catch (final Exception e) {
            LOG.debug("mirror system message failed (non-blocking): %s", e.getMessage());
        }
    }

    // -- content-part type discrimination tokens --
    private static final String IMG = "image";
    private static final String AUD = "audio";
    private static final String VID = "video";
    private static final String PF = "pdf";

    // -- Known structural field sets per message type -------------------------
    // Fields NOT in these sets are treated as attributes during Rec→ChatMessage
    // conversion.  System fields (_tid, session, time, hash) are stripped by
    // the read path and never reach extractAttributes.

    private static final Set<String> SYSTEM_KNOWN_KEYS = Set.of(TEXT);
    private static final Set<String> USER_KNOWN_KEYS = Set.of(NAME, CONTENTS);
    private static final Set<String> AI_KNOWN_KEYS = Set.of(TEXT, THINKING, TOOL_REQUESTS);
    private static final Set<String> TOOL_RESULT_KNOWN_KEYS = Set.of(TEXT, ID, NAME);

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
            if (HASH.equals(keyName) || TIME.equals(keyName) || SESSION.equals(keyName)) continue;
            attrs.put(keyName, Str.Helper.cleanString(e.getValue()));
        }
        return attrs;
    }

    // -- Token vocabulary ----------------------------------------------------
    static final TokenMapper VOCAB = new TokenMapper()
            .add(TOOL_RESULT_MESSAGE_TID, NAME, "toolName")
            .add(LLM_TOOL_TID, ARGS, "arguments");

    // -- HASH key ------------------------------------------------------------
    private static final String HASH = "hash";

    public SpaceChatSessionStore(final Agent agent, final Space space) {
        this.agent = Objects.requireNonNull(agent, "agent must not be null");
        this.space = Objects.requireNonNull(space, "space must not be null");
    }

    public Space space() {
        return this.space;
    }

    public Set<fURI> getCurrentMessages() {
        return this.currentMessages;
    }

    ///////////////////////////////////////////////////////////////////////////
    // ChatMemoryStore interface
    //
    // All messages are stored in a single polymorphic llm_message table under
    // the session's parent path.  The table is append-only; the sliding window
    // is a read-time view.  Message type is carried by the rec's TID, which
    // tbleSpace persists in the _tid column.
    //
    // URI topology:
    //   .../llm_session/1           → session policy (agent, user, algorithm)
    //   .../llm_message/_?incrq     → unified append-only message ledger

    /// ////////////////////////////////////////////////////////////////////////

    @Override
    public List<ChatMessage> getMessages(final Object sessionVID) {
        if (!(sessionVID instanceof fURI sesVID))
            return new ArrayList<>();

        // Read session policy for window config
        int windowMax = 15;
        try {
            final Obj sessionObj = Router.readFromSpace(sesVID);
            if (sessionObj.isRec()) {
                final Obj algorithm = sessionObj.asRec().at(uri(ALGORITHM));
                if (algorithm.isRec()) {
                    windowMax = algorithm.asRec().at(uri(MAX)).orElse(jnt(15)).intValue().intValue();
                }
            }
        } catch (final Exception e) {
            LOG.warn("could not read session policy for %s (using default max=15): %s", sesVID, e);
        }

        // Read messages sequentially by incrQ-assigned id (1, 2, 3, ...).
        // Collect messages matching this session, skip thinking rows, take last N.
        final fURI msgBase = sesVID.retract(2).extend(LLM_MESSAGE_TABLE);
        final List<Rec> allMessages = new ArrayList<>();
        final AtomicInteger found = new AtomicInteger(0);
        /*from_(msgBase.extend("+").toUri()).where_(rec(uri(SESSION), sesVID.toUri())).apply(jnt(1)).stream().forEach(msg -> {
            if (!msg.isRec()) {
                LOG.warn("non-message obj in llm messages: %s", msg);
            } else {
                final Rec msgRec = msg.asRec();
                final Obj sessionField = msgRec.at(uri(SESSION));
                if (!sessionField.isNoObj() && sessionField.isUri() && sessionField.uriValue().equals(sesVID) && !msgRec.tid().equals(THINKING_MESSAGE_TID)) {
                    allMessages.add(msgRec);
                    found.incrementAndGet();
                }
            }
        });*/
        for (int id = 1; ; id++) {
            final fURI readURI = msgBase.extend(String.valueOf(id));
            try {
                final Obj msgObj = Router.readFromSpace(readURI);
                if (null == msgObj || msgObj.isNoObj()) break;
                if (!msgObj.isRec()) continue;
                final Rec msgRec = msgObj.asRec();
                // Filter by session — the table is shared across sessions
                final Obj sessionField = msgRec.at(uri(SESSION));
                if (sessionField.isNoObj() || !sessionField.isUri() || !sessionField.uriValue().equals(sesVID)) {
                    continue;
                }
                // Skip thinking rows — they're for the ledger, not the LC4j window
                if (msgRec.tid().equals(THINKING_MESSAGE_TID)) continue;
                allMessages.add(msgRec);
                found.incrementAndGet();
            } catch (final Exception e) {
                LOG.warn("unable to process message %s (ignoring): %s", readURI, e);
            }
        }
        try {
            allMessages.sort(Comparator.comparing(a -> LocalDateTime.parse(Str.Helper.cleanString(a.at(TIME)))));
        } catch (final DateTimeException e) {
            LOG.debug("unable to form datetime: %s", e);
        }
        LOG.info("messages found for context window: " + found.get());
        final List<ChatMessage> result = new ArrayList<>();
        final int start = Math.max(0, allMessages.size() - windowMax);
        for (int i = start; i < allMessages.size(); i++) {
            try {
                final ChatMessage cm = recToChatMessage(allMessages.get(i));
                if (cm != null)
                    result.add(cm);
            } catch (final Exception e) {
                LOG.warn("error converting stored message to ChatMessage (ignoring): %s", e);
            }
        }

        LOG.info("read %d messages for session %s (window max=%d, total stored=%d)", result.size(), sesVID, windowMax, allMessages.size());
        return result;
    }

    @Override
    public void updateMessages(final Object sessionVID, final List<ChatMessage> messages) {
        if (!(sessionVID instanceof fURI sesVID)) {
            LOG.warn("session obj is not a uri: %s", sessionVID);
            return;
        }
        if (null == messages || messages.isEmpty())
            return;

        LOG.info("updating messages %s", messages);

        // -- 1. Read session policy -------------------------------------------
        int windowMax = 15;
        fURI name = null;
        final Map<Obj, Obj> existingSessionFields = new LinkedHashMap<>();
        try {
            final Obj existingObj = Router.readFromSpace(sesVID);
            if (existingObj.isRec()) {
                final Rec existingRec = existingObj.asRec();
                for (final Obj key : existingRec.keys().toList()) {
                    if (key.isUri()) {
                        final String keyName = key.uriValue().name();
                        if (!ALGORITHM.equals(keyName))
                            existingSessionFields.put(key, existingRec.at(key));
                    }
                }
                final Obj algorithm = existingRec.at(uri(ALGORITHM));
                if (algorithm.isRec()) {
                    windowMax = algorithm.asRec().at(uri(MAX)).orElse(jnt(15)).intValue().intValue();
                    name = algorithm.asRec().at(uri(NAME)).orThrow(MTronException.of("no algorithm name specified")).uriValue();
                }
            }
        } catch (final Exception e) {
            LOG.debug("could not read existing session policy for %s (creating new): %s", sesVID, e);
        }

        // -- 2. Convert incoming messages to typed Recs + compute hashes -----
        final List<Rec> incomingRecs = new ArrayList<>();
        // final Set<String> incomingHashes = new LinkedHashSet<>();
        for (final ChatMessage msg : messages) {
            try {
                final Rec msgRec = chatMessageToRec(msg);
                final String hash = contentHash(msgRec);
                msgRec.recValue().put(uri(HASH), str(hash));
                msgRec.recValue().put(uri(TIME), str(Date.from(Instant.now()).toString()));
                msgRec.recValue().put(uri(SESSION), uri(sesVID));
                incomingRecs.add(msgRec);
                //         incomingHashes.add(hash);
            } catch (final Exception e) {
                LOG.error("error converting incoming chat message (type=%s, class=%s): %s",
                        msg.type(), msg.getClass().getSimpleName(), e);
            }
        }

        // -- 3. Append new messages (hash-based dedup) -----------------------
        LOG.info("appending new messages [size:%d]", messages.size());
        final Set<String> sessionHashes;
        synchronized (WRITTEN_HASHES) {
            sessionHashes = WRITTEN_HASHES.computeIfAbsent(sesVID, k -> new LinkedHashSet<>());
        }
        int written = 0;
        for (final Rec incomingRec : incomingRecs) {
            final String hash = incomingRec.recValue().get(uri(HASH)).strValue();
            synchronized (WRITTEN_HASHES) {
                if (!sessionHashes.add(hash)) continue; // already written
            }
            try {
                final fURI writePath = llmMessagePath(sesVID);
                final Obj writtenObj = Router.writeToSpace(writePath, incomingRec);
                LOG.debug("updating current messages [size:%d]", this.currentMessages.size());
                this.currentMessages.add(writtenObj.vid());
                written++;
            } catch (final Exception e) {
                LOG.warn("error writing message to llm_message (non-blocking): %s", e.getMessage());
            }
        }

        // -- 4. Write thinking row if accumulated ----------------------------
        final Obj thinking = this.agent.at(res("thinking"));
        if (!thinking.isNoObj() && !thinking.strValue().isBlank()) {
            final Map<Obj, Obj> thinkingMap = new LinkedHashMap<>();
            thinkingMap.put(uri(TEXT), str(thinking.strValue()));
            thinkingMap.put(uri(TIME), str(Date.from(Instant.now()).toString()));
            thinkingMap.put(uri(SESSION), uri(sesVID));
            final Rec thinkingRec = rec(thinkingMap, THINKING_MESSAGE_TID, null);
            final String thinkingHash = contentHash(thinkingRec);
            thinkingRec.recValue().put(uri(HASH), str(thinkingHash));
            synchronized (WRITTEN_HASHES) {
                if (sessionHashes.add(thinkingHash)) {
                    try {
                        Router.writeToSpace(llmMessagePath(sesVID), thinkingRec);
                    } catch (final Exception e) {
                        LOG.warn("error writing thinking to llm_message (non-blocking): %s", e.getMessage());
                    }
                }
            }
            // Clear thinking from blackboard so it isn't re-written next turn
            this.agent.at(res("thinking"), noobj(), Poly.MUTABLE);
        }

        // -- 5. Update session policy ----------------------------------------
        final Map<Obj, Obj> algorithmMap = new LinkedHashMap<>();
        algorithmMap.put(uri(MAX), jnt(windowMax));
        algorithmMap.put(uri(NAME), uri(name));
        existingSessionFields.put(uri(ALGORITHM), rec(algorithmMap));
        existingSessionFields.putIfAbsent(uri(AGENT), str("default"));
        existingSessionFields.putIfAbsent(uri(USER), str("default"));
        Router.writeToSpace(sesVID, rec(existingSessionFields).selfVID(sesVID));

        LOG.debug("wrote %d messages + %s thinking for session %s (window name=%s, max=%d)",
                written, thinking.isNoObj() ? "no" : "yes", sesVID, name, windowMax);
    }

    @Override
    public void deleteMessages(final Object sessionVID) {
        if (!(sessionVID instanceof fURI sesVID))
            return;

        // Read and delete all messages for this session sequentially by id
        final fURI msgBase = sesVID.retract(2).extend(LLM_MESSAGE_TABLE);
        for (int id = 1; ; id++) {
            try {
                final Obj msgObj = Router.readFromSpace(msgBase.extend(String.valueOf(id)));
                if (msgObj.isNoObj()) break;
                if (!msgObj.isRec()) continue;
                final Rec msgRec = msgObj.asRec();
                final Obj sessionField = msgRec.at(uri(SESSION));
                if (sessionField.isNoObj() || !sessionField.isUri()
                        || !sessionField.uriValue().equals(sesVID))
                    continue;
                Router.writeToSpace(msgBase.extend(String.valueOf(id)), noobj());
            } catch (final Exception e) {
                break; // no more entries
            }
        }

        synchronized (WRITTEN_HASHES) {
            WRITTEN_HASHES.remove(sesVID);
        }
        LOG.debug("deleted messages for session %s", sesVID);
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
        else
            throw MTronException.of("unsupported message type: %s [%s]", message.type(), message.getClass().getSimpleName());
    }

    private static Rec systemMessageToRec(final SystemMessage msg) {
        return rec(mutableMap(uri(TEXT), str(msg.text())), SYSTEM_MESSAGE_TID, null);
    }

    private static Rec userMessageToRec(final UserMessage msg) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        if (msg.name() != null && !msg.name().isBlank())
            map.put(uri(NAME), str(msg.name()));
        if (msg.hasSingleText()) {
            map.put(uri(CONTENTS), rec(uri(TEXT), str(msg.singleText())));
            map.put(uri(TEXT), str(msg.singleText()));
        } else {
            final List<Obj> parts = new ArrayList<>();
            for (final Content content : msg.contents())
                parts.add(contentToRec(content));
            map.put(uri(CONTENTS), lst(parts));
            map.put(uri(TEXT), str(parts.stream().map(x -> x.jvm() + "").reduce("", (a, b) -> a + ";" + b)));
        }
        msg.attributes().forEach((k, v) -> map.putIfAbsent(uri(k), str(String.valueOf(v))));
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
            default -> rec(uri(TEXT), str(content.toString()));
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
            map.put(uri(TOOL_REQUESTS), lst(toolReqs));
            if (!map.containsKey(uri(CONTENTS)))
                map.put(uri(CONTENTS), str(toolReqs.toString()));
        }
        msg.attributes().forEach((k, v) -> map.putIfAbsent(uri(k), str(String.valueOf(v))));
        return rec(map, AI_MESSAGE_TID, null);
    }

    private static Rec toolRequestToRec(final ToolExecutionRequest toolRequest) {
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

    private static Rec toolResultMessageToRec(final ToolExecutionResultMessage msg) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(VOCAB.from(TOOL_RESULT_MESSAGE_TID, "toolName")), uri(msg.toolName()));
        // Serialize the text through ObjmtronSerializer.writeStr() so the stored
        // value is mtron-escaped (e.g. 'text' or """text""").  This prevents
        // tbleSpace's {/[ heuristic in readColumnWithMetadata from mis-parsing
        // tool-result text that happens to look like structured mtron data.
        final String rawText = msg.hasSingleText() && msg.text() != null && !msg.text().isBlank() ? msg.text() : msg.toString();
        map.put(uri(TEXT), str(ObjmtronSerializer.singleNoClip().write(str(rawText))));
        if (msg.id() != null && !msg.id().isBlank())
            map.put(uri(CONTENTS), str(msg.id()));
        msg.attributes().forEach((k, v) -> map.putIfAbsent(uri(k), str(String.valueOf(v))));
        return rec(map, TOOL_RESULT_MESSAGE_TID, null);
    }

    ///////////////////////////////////////////////////////////////////////////
    // typed Rec -> ChatMessage (boundary: metatron token -> LC4j field)
    // Message type is determined by rec.tid() (populated from _tid column
    // on read), not by a redundant "type" field.

    /// ////////////////////////////////////////////////////////////////////////

    public static ChatMessage recToChatMessage(final Rec rec) {
        final fURI tid = rec.tid();
        if (tid.equals(SYSTEM_MESSAGE_TID))
            return recToSystemMessage(rec);
        if (tid.equals(USER_MESSAGE_TID))
            return recToUserMessage(rec);
        if (tid.equals(AI_MESSAGE_TID))
            return recToAiMessage(rec);
        if (tid.equals(TOOL_REQUEST_MESSAGE_TID) || tid.equals(TOOL_RESULT_MESSAGE_TID))
            return recToToolResultMessage(rec);
        // Fallback: check legacy "type" field for compatibility with old data
        final String type = Str.Helper.cleanString(rec.at(uri(TYPE)).orElse(str("")));
        if (type.equals(ChatMessageType.SYSTEM.name()))
            return recToSystemMessage(rec);
        if (type.equals(ChatMessageType.USER.name()))
            return recToUserMessage(rec);
        if (type.equals(ChatMessageType.AI.name()))
            return recToAiMessage(rec);
        if (type.equals(ChatMessageType.TOOL_EXECUTION_RESULT.name()))
            return recToToolResultMessage(rec);
        LOG.warn("unknown message type (tid=%s): %s", tid, rec);
        return recToSystemMessage(rec);
    }

    private static SystemMessage recToSystemMessage(final Rec rec) {
        return SystemMessage.from(Str.Helper.cleanString(rec.at(uri(TEXT)).orElse(str("none"))));
    }

    private static UserMessage recToUserMessage(final Rec rec) {
        final String name = Str.Helper.cleanString(rec.at(uri(NAME)).orElse(str("")));
        final String nameOrNull = "none".equals(name) || name.isBlank() ? null : name;
        final Map<String, Object> attrs = extractAttributes(rec, USER_KNOWN_KEYS);
        final Obj contents = rec.at(uri(CONTENTS));

        final UserMessage.Builder builder = UserMessage.builder()
                .name(nameOrNull)
                .attributes(attrs);

        if (contents.isNoObj()) {
            builder.addContent(TextContent.from(""));
        } else if (contents.isLst()) {
            final List<Content> contentList = contents.asLst().elements()
                    .filter(Obj::isRec)
                    .map(c -> recToContent(c.asRec()))
                    .filter(Objects::nonNull)
                    .toList();
            if (contentList.isEmpty())
                builder.addContent(TextContent.from("none"));
            else
                builder.contents(contentList);
        } else if (contents.isRec()) {
            final Rec contentRec = contents.asRec();
            if (contentRec.has(uri(TEXT)))
                builder.addContent(TextContent.from(Str.Helper.cleanString(contentRec.at(uri(TEXT)))));
            else {
                final Content content = recToContent(contentRec);
                if (content != null)
                    builder.addContent(content);
                else
                    builder.addContent(TextContent.from(""));
            }
        } else {
            builder.addContent(TextContent.from(""));
        }

        return builder.build();
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
        final Map<String, Object> attrs = extractAttributes(rec, AI_KNOWN_KEYS);
        final AiMessage.Builder builder = AiMessage.builder()
                .text(text)
                .attributes(attrs);
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
                                .id(Str.Helper.cleanString(trRec.at(uri(CONTENTS)).orElse(str(""))))
                                .build();
                    })
                    .toList();
            builder.toolExecutionRequests(requests);
        }
        return builder.build();
    }

    private static ToolExecutionResultMessage recToToolResultMessage(final Rec rec) {
        final String nameToken = VOCAB.from(TOOL_RESULT_MESSAGE_TID, "toolName");
        final Map<String, Object> attrs = extractAttributes(rec, TOOL_RESULT_KNOWN_KEYS);
        // If the text was serialized through ObjmtronSerializer.writeStr()
        // (starts with a quote), deserialize it back.  Otherwise use as-is.
        final String rawText = Str.Helper.cleanString(rec.at(uri(TEXT)).orElse(str("none")));
        final String text;
        if (!rawText.isEmpty() && (rawText.charAt(0) == '\'' || rawText.charAt(0) == '"')) {
            final Obj parsed = ObjmtronSerializer.singleNoClip().inputBytes(rawText);
            text = parsed.isFail() ? rawText : Str.Helper.cleanString(parsed);
        } else {
            text = rawText;
        }
        return ToolExecutionResultMessage.builder()
                .id(Str.Helper.cleanString(rec.at(uri(CONTENTS)).orElse(str(""))))
                .toolName(Str.Helper.cleanString(rec.at(uri(nameToken)).orElse(str(""))))
                .text(text)
                .attributes(attrs)
                .build();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Content hashing
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Computes a SHA-256 content hash of the given message Rec.
     * Strips volatile metadata fields so the same logical message produces
     * the same hash across calls.
     */
    static String contentHash(final Rec msgRec) {
        final Map<Obj, Obj> saved = new LinkedHashMap<>();
        final Obj[] volatileKeys = {uri(HASH), uri(NAME), uri(TYPE), uri(THINKING), uri(TIME), uri(SESSION), uri(URI)};
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
            msgRec.recValue().putAll(saved);
        }
    }
}
