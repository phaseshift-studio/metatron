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

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.res;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.at_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class SpaceChatSessionStore implements ChatMemoryStore {

    private static final GraphittyLogger LOG = Graphitty.log(SpaceChatSessionStore.class);
    private static final ObjChatMessageSerializer SERIALIZER = ObjChatMessageSerializer.instance();

    private final Agent agent;
    private final Space space;

    /**
     * Messages written by this store instance, keyed by TID.
     * Used by ConceptFeature to link concepts to recently written messages.
     */
    private final Set<fURI> currentMessages = new HashSet<>();

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
     * Mirror a pre-built system message Rec to the unified message table.
     * System messages bypass {@code ChatMemoryStore.updateMessages()} — they're
     * injected via {@code AiServices.systemMessage()}.
     */
    public static void mirrorSystemMessage(final Agent agent, final fURI sessionVID, final Rec systemRec) {
        final String hash = contentHash(systemRec);
        systemRec.recValue().put(uri(HASH), str(hash));
        systemRec.recValue().put(uri(TIME), str(Date.from(Instant.now()).toString()));
        systemRec.recValue().put(uri(SESSION), uri(sessionVID));
        synchronized (WRITTEN_HASHES) {
            final Set<String> seen = WRITTEN_HASHES.computeIfAbsent(sessionVID, k -> new LinkedHashSet<>());
            if (!seen.add(hash)) return;
        }
        try {
            Router.writeToSpace(agent.at(ROOT).uriValue().extend(MESSAGE).extend("_").addQ(INCRQ), systemRec);
        } catch (final Exception e) {
            LOG.debug("mirror system message failed (non-blocking): %s", e.getMessage());
        }
    }

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
    // All messages are stored in a single polymorphic llm_message rec{*} under
    // the session's parent path.  The rec{*} is append-only; the sliding window
    // is a read-time view.
    //
    // URI topology:
    //   .../llm_session/1           → session policy (agent, user, algorithm)
    //   .../llm_message/_?incrq     → unified append-only message ledger

    /// ////////////////////////////////////////////////////////////////////////

    @Override
    public List<ChatMessage> getMessages(final Object sessionVID) {
        if (!(sessionVID instanceof fURI sesVID))
            return new ArrayList<>();
        // Read all messages for this session, skip thinking rows
        final fURI msgBase = this.agent.at(ROOT).uriValue().extend(MESSAGE);
        final List<Rec> allMessages = new ArrayList<>();
        final AtomicInteger found = new AtomicInteger(0);
        // from_(msgBase.extend("+").toUri()).where_(rec(uri(SESSION), sesVID.toUri())).apply()
        at_(uri(msgBase.extend("+")))
                .where_(rec(SESSION, uri(sesVID)))
                .tryToInst().apply(jnt(1))
                .stream()
                .forEach(msg -> {
                    if (!msg.isRec()) {
                        LOG.warn("non-message obj in llm messages: %s", msg);
                    } else {
                        final Rec msgRec = msg.asRec();
                        if (msgRec.tid().equals(THINKING_MESSAGE_TID)) return;
                        final Obj sessionField = msgRec.at(uri(SESSION));
                        final fURI sessionURI = sessionField.isUri() ? sessionField.uriValue()
                                : sessionField.isInst() ? sessionField.asInst().vid()
                                : null;
                        if (sessionURI != null && sessionURI.equals(sesVID)) {
                            allMessages.add(msgRec);
                            found.incrementAndGet();
                        }
                    }
                });
        allMessages.sort(Comparator.comparing(a -> Integer.parseInt(a.vid().name())));
        LOG.info("messages found for context window: " + found.get());
        // ChatMemory handles its own windowing (token-based or message-based)
        // after hydrating from the store; return all messages and let it prune
        return allMessages.stream().map(m -> {
            try {
                return SERIALIZER.write(m.vid(null));
            } catch (final Exception e) {
                LOG.warn("error converting stored message to ChatMessage (ignoring): %s", e);
                return null;
            }
        }).filter(m -> !Objects.isNull(m)).toList();
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

        // -- 1. Convert incoming messages to typed Recs + compute hashes -----
        final List<Rec> incomingRecs = new ArrayList<>();
        for (final ChatMessage msg : messages) {
            try {
                final Rec msgRec = SERIALIZER.read(msg).asRec();
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

        // -- 2. Append new messages (hash-based dedup) -----------------------
        LOG.info("appending new messages [size:%d]", messages.size());
        final Set<String> sessionHashes;
        synchronized (WRITTEN_HASHES) {
            sessionHashes = WRITTEN_HASHES.computeIfAbsent(sesVID, k -> new LinkedHashSet<>());
        }
        int written = 0;
        final fURI writePath = this.agent.at(ROOT).uriValue().extend(MESSAGE).extend("_").addQ(INCRQ);
        for (final Rec incomingRec : incomingRecs) {
            final String hash = incomingRec.recValue().get(uri(HASH)).strValue();
            synchronized (WRITTEN_HASHES) {
                if (!sessionHashes.add(hash)) continue; // already written
            }
            try {
                final Obj writtenObj = Router.writeToSpace(writePath, incomingRec);
                LOG.debug("updating current messages [size:%d]", this.currentMessages.size());
                if ((writtenObj.typeId().equals(USER_MESSAGE_TID) ||
                        writtenObj.typeId().equals(AI_MESSAGE_TID)) &&
                        !writtenObj.asRec().has(TOOL_REQUESTS))
                    this.currentMessages.add(writtenObj.vid());
                written++;
            } catch (final Exception e) {
                LOG.warn("error writing message to message (non-blocking): %s", e.getMessage());
            }
        }

        // -- 3. Write thinking row if accumulated ----------------------------
        final Obj thinking = this.agent.at(res(THINKING));
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
                        Router.writeToSpace(writePath, thinkingRec);
                    } catch (final Exception e) {
                        LOG.warn("error writing thinking to message (non-blocking): %s", e.getMessage());
                    }
                }
            }
            // Clear thinking from blackboard so it isn't re-written next turn
            this.agent.at(res(THINKING), noobj(), Poly.MUTABLE);
        }

        LOG.debug("wrote %d messages + %s thinking for session %s",
                written, thinking.isNoObj() ? "no" : "yes", sesVID);
    }

    @Override
    public void deleteMessages(final Object sessionVID) {
        if (!(sessionVID instanceof fURI sesVID))
            return;

        // Read and delete all messages for this session sequentially by id
        final fURI msgBase = this.agent.at(ROOT).uriValue().extend(MESSAGE);
        for (int id = 1; ; id++) {
            try {
                final Obj msgObj = Router.readFromSpace(msgBase.extend(String.valueOf(id)));
                if (msgObj.isNoObj()) break;
                if (!msgObj.isRec()) continue;
                final Rec msgRec = msgObj.asRec();
                final Obj sessionField = msgRec.at(uri(SESSION));
                if (sessionField.isNoObj() || !sessionField.isUri() || !sessionField.uriValue().equals(sesVID))
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
    // Content hashing
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Computes a SHA-256 content hash of the given message Rec.
     * Strips volatile metadata fields and uses sorted-key serialization
     * so the same logical message produces the same hash regardless of
     * map implementation (LinkedHashMap vs HashMap on tbleSpace read-back).
     */
    static String contentHash(final Rec msgRec) {
        final Map<Obj, Obj> saved = new LinkedHashMap<>();
        final Obj[] volatileKeys = {uri(HASH), uri(NAME), uri(TYPE), uri(THINKING), uri(TIME), uri(SESSION), uri(URI)};
        for (final Obj key : volatileKeys) {
            final Obj val = msgRec.recValue().remove(key);
            if (val != null) saved.put(key, val);
        }
        try {
            // Deterministic: sort entries by key, emit key:value.
            // Decode text values so the hash is based on logical content,
            // not mtron encoding (which can vary on whitespace differences).
            final List<String> entries = new ArrayList<>();
            for (final Map.Entry<Obj, Obj> e : msgRec.recValue().entrySet()) {
                final String key = e.getKey().toString();
                final String val = ObjChatMessageSerializer.decodeRawValue(e.getValue());
                entries.add(key + ":" + val);
            }
            entries.sort(String::compareTo);
            final String payload = msgRec.tid() + "|" + String.join("|", entries);
            final byte[] bytes = payload.getBytes();
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            final byte[] digest = md.digest(bytes);
            final StringBuilder sb = new StringBuilder();
            for (final byte b : digest)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw MTronException.of("SHA-256 not available: %s", e);
        } catch (final Exception e) {
            throw MTronException.of(e);
        } finally {
            msgRec.recValue().putAll(saved);
        }
    }
}
