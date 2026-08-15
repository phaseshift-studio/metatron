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
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
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
    private final int depth;
    private final int chatId;

    /**
     * Messages written by this store instance, keyed by TID.
     * Used by ConceptFeature to link concepts to recently written messages.
     */
    private final Set<fURI> currentMessages = new HashSet<>();

    public SpaceChatSessionStore(final Agent agent, final Space space, final int depth,
                                 final int chatId) {
        this.agent = Objects.requireNonNull(agent, "agent must not be null");
        this.space = Objects.requireNonNull(space, "space must not be null");
        this.depth = depth;
        this.chatId = chatId;
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
        final fURI msgBase = this.agent.at(ROOT).uriValue().extend(MESSAGE);

        // ── Read all session messages (no skip yet — we adjust for pairs) ─
        final List<Rel> allMessages = Router.readFromSpace(msgBase.extend("+/"))
                .stream()
                .map(Obj::asRel)
                .filter(pair -> pair.second().isRec()
                        && (pair.second().asRec().tid().equals(USER_MESSAGE_TID)
                        || pair.second().asRec().tid().equals(AI_MESSAGE_TID)
                        || pair.second().asRec().tid().equals(TOOL_RESULT_MESSAGE_TID)))
                .filter(pair -> {
                    final Obj sessionField = pair.second().asRec().at(uri(SESSION)).orElse(noobj());
                    final fURI stored = sessionField.isUri() ? sessionField.uriValue()
                            : sessionField.isInst() ? sessionField.asInst().vid() : null;
                    return stored != null && stored.equals(sesVID);
                })
                .filter(pair -> {
                    final Obj depthField = pair.second().asRec().at(uri(DEPTH));
                    return !depthField.isNoObj()
                            && depthField.isInt()
                            && depthField.intValue().intValue() == this.depth;
                })
                .filter(pair -> {
                    // Depth >= 2: further isolate by chatId so that a previous
                    // turn's recursive sub-agent messages don't leak into the
                    // current turn's sub-agent context.  Depth 1 shares all
                    // messages at this depth for conversational continuity.
                    if (this.depth <= 1)
                        return true;
                    final Obj chatIdField = pair.second().asRec().at(uri(CHAT_ID));
                    return !chatIdField.isNoObj()
                            && chatIdField.isInt()
                            && chatIdField.intValue().intValue() == this.chatId;
                })
                .sorted(Comparator.comparing(a -> Integer.parseInt(a.first().uriValue().name())))
                .toList();

        // ── Pair-aware window: don't break AiMessage(tool_calls) /
        //     ToolExecutionResultMessage groups ──────────────────────
        final int max = this.getMaxMessages();
        final int rawSkip = Math.max(0, allMessages.size() - max);
        final int skip = adjustSkipToPreservePairs(allMessages, rawSkip);

        return allMessages.stream()
                .skip(skip)
                .peek(pair -> {
                    if (!pair.second().tid().equals(AI_MESSAGE_TID) || !pair.second().asRec().has(TOOL_REQUESTS))
                        this.currentMessages.add(pair.first().uriValue());
                })
                .map(pair -> pair.second().asRec())
                .map(m -> {
                    try {
                        m.recValue().put(uri(WRITTEN_KEY), BOOL_TRUE);
                        return SERIALIZER.write(m.vid(null));
                    } catch (final Exception e) {
                        LOG.warn("error converting stored message to ChatMessage (ignoring): %s", e);
                        return null;
                    }
                }).filter(Objects::nonNull).toList();
    }

    /**
     * AiMessages produced during the LangChain4j tool loop (intermediate
     * responses with {@code tool_calls}) never reach
     * {@code TokenStream.onCompleteResponse()} — only the final text
     * response does.  ToolResults ARE written eagerly by
     * {@code ToolFeature.onToolExecuted()}, so without this store path
     * the AiMessage is missing and its ToolResults appear orphaned on
     * the next chat.
     * <p>
     * Static: dedup must survive across store instances.  LC4j creates a
     * new store per chat, but calls {@code updateMessages()} with the
     * full message list including historical AiMessages from prior chats.
     * Without a static set, every AiMessage is re-written on every chat.
     */
    public static final String WRITTEN_KEY = "_w";   // in-memory marker, rides LC4j attributes

    @Override
    public void updateMessages(final Object sessionVID, final List<ChatMessage> messages) {
        if (!(sessionVID instanceof fURI sesVID) || messages == null || messages.isEmpty())
            return;

        final fURI writePath = this.agent.at(ROOT).uriValue().extend(MESSAGE)
                .extend("_").addQ(INCRQ);

        for (final ChatMessage msg : messages) {
            try {
                final Rec msgRec = SERIALIZER.read(msg).asRec();
                if (!msgRec.tid().equals(AI_MESSAGE_TID))
                    continue;

                // Already persisted — _w was stamped by getMessages(),
                // rode through LangChain4j's ChatMessage.attributes()
                if (!msgRec.at(uri(WRITTEN_KEY)).isNoObj())
                    continue;

                msgRec.recValue().put(uri(TIME), mathInstSet.nowDatetime());
                msgRec.recValue().put(uri(SESSION), uri(sesVID));
                msgRec.recValue().put(uri(DEPTH), jnt(this.depth));
                msgRec.recValue().put(uri(CHAT_ID), jnt(this.chatId));
                Router.writeToSpace(writePath, msgRec);
            } catch (final Exception e) {
                LOG.warn("error writing AiMessage (non-blocking): %s", e.getMessage());
            }
        }
    }

    @Override
    public void deleteMessages(final Object sessionVID) {
        if (!(sessionVID instanceof fURI sesVID))
            return;

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
        LOG.debug("deleted messages for session %s", sesVID);
    }

///////////////////////////////////////////////////////////////////////////
// Window management
///////////////////////////////////////////////////////////////////////////

    /**
     * Returns the store-level window size as a multiple of the LangChain4j
     * {@link dev.langchain4j.memory.chat.MessageWindowChatMemory} window.
     * <p>
     * The store window must be larger than the LC4j window so that pair-aware
     * expansion ({@link #adjustSkipToPreservePairs}) is not immediately
     * undone by {@code MessageWindowChatMemory} trimming back to the
     * configured limit.  With a 3× factor, the store returns enough history
     * that LC4j's internal trim is a safe no-op for normal conversation
     * patterns, while the pair-aware skip still guards the boundary against
     * orphaned {@code ToolExecutionResultMessage}s.
     */
    private static final int STORE_WINDOW_FACTOR = 3;

    private int getMaxMessages() {
        try {
            final Obj sessFeature = this.agent.feature(SESSION);
            if (!sessFeature.isNoObj()) {
                final Obj algo = sessFeature.asRec().at(ALGORITHM);
                if (!algo.isNoObj() && algo.isRec()) {
                    final Obj maxVal = algo.asRec().at(MAX);
                    if (!maxVal.isNoObj() && maxVal.isInt())
                        return Math.max(50, maxVal.intValue().intValue() * STORE_WINDOW_FACTOR);
                }
            }
        } catch (final Exception e) {
            LOG.debug("could not read max messages from session config: %s", e.getMessage());
        }
        return 150; // sensible default (50 × 3) when session config is unavailable
    }

///////////////////////////////////////////////////////////////////////////
// Pair-aware skip
///////////////////////////////////////////////////////////////////////////

    /**
     * Adjust the skip index so the window (a) never starts on an orphaned
     * {@code ToolExecutionResultMessage}, and (b) always starts with a
     * {@code UserMessage} (or at the very beginning).  The chat API
     * requires every non-system message group to begin with a user message.
     *
     * @param messages all session messages, sorted oldest→newest by ID
     * @param skip     the initial skip index (may be 0)
     * @return adjusted skip index, {@code <= skip}
     */
    static int adjustSkipToPreservePairs(final List<Rel> messages, int skip) {
        if (skip <= 0 || skip >= messages.size())
            return skip;

        // ── Rule 1: don't orphan a ToolResult from its AiMessage at
        //     the window boundary ─────────────────────────────────────
        skip = pullInPairedAiMessage(messages, skip);

        // ── Rule 2: window must start with a user message ─────────────
        skip = pullInPrecedingUserMessage(messages, skip);

        // ── Rule 3: scan the entire window for any ToolResult whose
        //     AiMessage was skipped — expand until no orphans remain ──
        skip = pullInAllOrphanedToolResults(messages, skip);

        return skip;
    }

    /**
     * Scan the window for any {@code ToolExecutionResultMessage} whose
     * paired {@code AiMessage} is before the skip boundary.  If found,
     * expand the skip to include it and re-scan until no orphans remain.
     */
    private static int pullInAllOrphanedToolResults(final List<Rel> messages, int skip) {
        boolean changed;
        do {
            changed = false;
            for (int i = skip; i < messages.size(); i++) {
                final Rec msg = messages.get(i).second().as();
                if (!msg.tid().equals(TOOL_RESULT_MESSAGE_TID))
                    continue;
                final String toolCallId = msg.at(uri(CONTENTS)).orElse(str("")).strValue();
                if (toolCallId.isBlank())
                    continue;
                // Find the paired AiMessage
                for (int j = i - 1; j >= 0; j--) {
                    final Rec candidate = messages.get(j).second().as();
                    if (!candidate.tid().equals(AI_MESSAGE_TID))
                        continue;
                    final Obj toolReqs = candidate.at(uri(TOOL_REQUESTS));
                    if (toolReqs.isNoObj() || !toolReqs.isLst())
                        continue;
                    final boolean found = toolReqs.asLst().elements()
                            .filter(Obj::isRec)
                            .anyMatch(tr -> toolCallId.equals(
                                    tr.asRec().at(uri(CONTENTS)).orElse(str("")).strValue()));
                    if (found && j < skip) {
                        skip = j;
                        changed = true;
                        break; // re-scan from the new skip position
                    }
                    if (found) break; // AiMessage is already in window
                }
            }
        } while (changed);
        return skip;
    }

    /**
     * If the window starts on a {@code ToolExecutionResultMessage}, walk
     * backward to find its paired {@code AiMessage} and move the skip
     * boundary to include it.
     */
    static int pullInPairedAiMessage(final List<Rel> messages, final int skip) {
        if (skip <= 0 || skip >= messages.size())
            return skip;

        final Rel first = messages.get(skip);
        if (!first.values().findFirst().get().tid().equals(TOOL_RESULT_MESSAGE_TID))
            return skip;

        final String toolCallId = first.values().findFirst().get().asRec().at(uri(CONTENTS)).orElse(str("")).strValue();
        if (toolCallId.isBlank())
            return skip;

        for (int i = skip - 1; i >= 0; i--) {
            final Rec candidate = messages.get(i).second().as();
            if (!candidate.tid().equals(AI_MESSAGE_TID))
                continue;
            final Obj toolReqs = candidate.at(uri(TOOL_REQUESTS));
            if (toolReqs.isNoObj() || !toolReqs.isLst())
                continue;
            final boolean found = toolReqs.asLst().elements()
                    .filter(Obj::isRec)
                    .anyMatch(tr -> toolCallId.equals(
                            tr.asRec().at(uri(CONTENTS)).orElse(str("")).strValue()));
            if (found)
                return i; // expand window to include the paired AiMessage
        }

        return skip;
    }

    /**
     * If the window starts on an {@code AiMessage} (rather than a
     * {@code UserMessage} or {@code SystemMessage}), walk backward to
     * include the preceding user message.  The chat API requires every
     * turn to begin with a user message.
     */
    static int pullInPrecedingUserMessage(final List<Rel> messages, final int skip) {
        if (skip <= 0 || skip >= messages.size())
            return skip;

        final fURI firstTid = messages.get(skip).second().tid();
        // System message is fine — MessageWindowChatMemory always keeps it first
        if (firstTid.equals(SYSTEM_MESSAGE_TID) || firstTid.equals(USER_MESSAGE_TID))
            return skip;

        // Window starts with AiMessage or ToolResult — find preceding user message
        for (int i = skip - 1; i >= 0; i--) {
            final fURI tid = messages.get(i).second().tid();
            if (tid.equals(USER_MESSAGE_TID))
                return i;
            // Stop at system message — it's already the absolute start
            if (tid.equals(SYSTEM_MESSAGE_TID))
                return i;
        }

        return 0; // no user message found — return everything
    }
}
