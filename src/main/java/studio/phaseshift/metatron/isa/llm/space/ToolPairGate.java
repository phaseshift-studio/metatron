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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * The tool request/result pairing gate of a message ledger.
 *
 * <p>A <b>group</b> is one ai message carrying {@code tool_requests} plus one
 * {@code tool_result} per request.  The ledger's validity invariant, which this
 * gate enforces for every writer:</p>
 *
 * <ul>
 *   <li>the ai message reaches the ledger only once <em>every</em> request has
 *       its result, and its results follow it immediately, in request order —
 *       so no reader ever meets {@code tool_calls} without the tool messages
 *       that answer them (the "assistant message with 'tool_calls' must be
 *       followed by tool messages" failure);</li>
 *   <li>a tool result never reaches the ledger without the ai message that
 *       asked for it, so no reader meets an orphan tool message.</li>
 * </ul>
 *
 * <p>Both sides are therefore <em>held</em> here until the group is complete,
 * whichever order they arrive in (an out-of-order client is tolerated, it is
 * not corruption).  A group nothing completes is closed by
 * {@link #close(SpaceChatSessionStore, Function)} — the caller's turn-end hook,
 * or the bus's next turn boundary.</p>
 *
 * <p>Two writers go through this gate: the native loop
 * ({@link SpaceChatSessionStore#updateMessages} offers each tool-calling ai
 * message; {@code ToolFeature.onToolExecuted} stages the results the tools
 * produced) and the message bus ({@code mcp_message.add_message} offers an
 * {@code ai} message with {@code tool_requests} and stages each
 * {@code tool_result} a client posts).</p>
 *
 * <p>State is keyed by the ledger's memory root — a tool call id is only
 * unique within the conversation that issued it — and lives here rather than
 * in a store instance because the ledger outlives the per-chat stores
 * LangChain4j and the bus create.</p>
 */
public final class ToolPairGate {

    private static final GraphittyLogger LOG = Graphitty.log(ToolPairGate.class);

    private ToolPairGate() {
        // static gate
    }

    /** What the gate did with an offered or staged message. */
    public enum Status {
        /** the group is complete and in the ledger */
        PUBLISHED,
        /** held — the group is still missing a side (checked again when the rest arrives) */
        PARKED,
        /** nothing can ever pair with it (no join key) — not written */
        UNPAIRED
    }

    /** A verdict: what happened, plus the ledger rec when one was written. */
    public record Verdict(Status status, Rec written) {

        public boolean published() {
            return Status.PUBLISHED == this.status;
        }
    }

    /** A parked ai message: the group waiting for its results. */
    private record Parked(String scope, fURI ledgerPath, Rec aiMessage, String chatId) {
    }

    /** A held result: a tool result waiting for its ai message. */
    private record Held(String scope, fURI ledgerPath, Rec result) {
    }

    /** ai messages held until their results arrive, keyed by ledger scope + tool call id. */
    private static final Map<String, Parked> PARKED = new ConcurrentHashMap<>();

    /** tool results held until their ai message arrives, keyed by ledger scope + tool call id. */
    private static final Map<String, Held> HELD = new ConcurrentHashMap<>();

    /**
     * Groups already published (by scope + call id) — the write side of the
     * dedup, so a replayed or re-offered message never duplicates the ledger.
     * Bounded: a long lived ledger issues unbounded call ids, only the recent
     * ones can recur.
     */
    private static final int PUBLISHED_MEMORY = 1024;
    private static final Map<String, Boolean> PUBLISHED = Collections.synchronizedMap(
            new LinkedHashMap<>(PUBLISHED_MEMORY, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, Boolean> eldest) {
                    return this.size() > PUBLISHED_MEMORY;
                }
            });

    // ── the gate ────────────────────────────────────────────────────

    /**
     * Offer the ai side of a tool group.  The group is written only when every
     * one of its requests has a result at hand — the results the caller passed
     * (the LangChain4j memory list, which is the authoritative "the result
     * exists" signal) plus whatever this gate already holds.  Otherwise the
     * message parks and this returns {@link Status#PARKED}.
     *
     * @param ledger      the ledger the group belongs to
     * @param aiMessage   the enveloped ai message carrying tool_requests
     * @param resultsById results the caller already holds, keyed by tool call id
     * @return the verdict — {@code PUBLISHED} (now, or by an earlier offer),
     *         {@code PARKED} (a result is still outstanding)
     */
    public static Verdict offer(final SpaceChatSessionStore ledger, final Rec aiMessage,
                                final Map<String, Rec> resultsById) {
        final List<String> callIds = toolCallIds(aiMessage);
        if (callIds.isEmpty()) // a plain ai message — nothing to pair
            return new Verdict(Status.PUBLISHED, Router.writeToSpace(ledger.ledgerWritePath(), aiMessage).asRec());
        if (callIds.stream().allMatch(id -> PUBLISHED.containsKey(key(ledger, id))))
            return new Verdict(Status.PUBLISHED, null); // already in the ledger — never write a group twice
        final fURI ledgerPath = ledger.ledgerWritePath();
        final String scope = scope(ledger);
        final String chatId = chatIdOf(aiMessage);
        callIds.forEach(id -> PARKED.put(key(ledger, id), new Parked(scope, ledgerPath, aiMessage, chatId)));
        resultsById.forEach((id, result) -> HELD.putIfAbsent(key(ledger, id), new Held(scope, ledgerPath, result)));
        return publish(ledger, aiMessage, callIds);
    }

    /**
     * Stage the result side of a group — from the native tool channel
     * ({@code ToolFeature.onToolExecuted}) or from a bus client's
     * {@code tool_result}.  The result is written only as part of its complete
     * group; a result whose ai message has not been offered yet is held (the
     * pair may still complete).
     *
     * @param ledger      the ledger the result belongs to
     * @param toolCallId  the call id that joins this result to its request
     * @param result      the enveloped tool_result message
     * @return the verdict — {@code PUBLISHED} (this result completed the
     *         group, or the group is already in the ledger),
     *         {@code PARKED} (held), {@code UNPAIRED} (no join key at all)
     */
    public static Verdict stage(final SpaceChatSessionStore ledger, final String toolCallId, final Rec result) {
        if (null == toolCallId || toolCallId.isBlank()) {
            LOG.warn("tool_result without a call id — nothing can pair with it (not written): %s",
                    CommonUtil.clipString(Str.Helper.cleanString(result.at(uri(TEXT))), 60, true));
            return new Verdict(Status.UNPAIRED, null);
        }
        final String key = key(ledger, toolCallId);
        if (PUBLISHED.containsKey(key))
            return new Verdict(Status.PUBLISHED, null); // its group is already in the ledger — a duplicate
        HELD.put(key, new Held(scope(ledger), ledger.ledgerWritePath(), result));
        final Parked parked = PARKED.get(key);
        if (null == parked)
            return new Verdict(Status.PARKED, null); // held for its ai message
        return publish(ledger, parked.aiMessage(), toolCallIds(parked.aiMessage()));
    }

    /** True when an ai message is parked on this call id — its result is wanted. */
    public static boolean isParked(final SpaceChatSessionStore ledger, final String toolCallId) {
        return PARKED.containsKey(key(ledger, toolCallId));
    }

    /**
     * Close every group parked in this ledger.  An ai message nothing answered
     * is still written — with the caller's {@code lostResultFor} placeholder
     * per unanswered request — so an abandoned turn is recorded consistently
     * instead of vanishing; a stray result that never found its ai message is
     * dropped (writing it would orphan a tool message).
     *
     * <p>Called by {@code Agent.chat()}'s finally (the native turn end) and by
     * the message bus when a turn boundary arrives.</p>
     *
     * @param ledger        the ledger being closed
     * @param lostResultFor the placeholder result for an unanswered call id
     */
    public static void close(final SpaceChatSessionStore ledger, final Function<String, Rec> lostResultFor) {
        close(ledger, null, lostResultFor);
    }

    /**
     * Close the groups parked in this ledger that the given turn boundary ends:
     * a boundary {@code chatId} closes every group of an <em>earlier</em> turn
     * (a group with no chat id is closed too — the boundary cannot be shown to
     * be its own turn); {@code null} closes every group.
     *
     * @param ledger          the ledger being closed
     * @param boundaryChatId  the new turn's chat id, or {@code null} for "all"
     * @param lostResultFor   the placeholder result for an unanswered call id
     */
    public static void closeStale(final SpaceChatSessionStore ledger, final String boundaryChatId,
                                  final Function<String, Rec> lostResultFor) {
        close(ledger, boundaryChatId, lostResultFor);
    }

    private static void close(final SpaceChatSessionStore ledger, final String boundaryChatId,
                              final Function<String, Rec> lostResultFor) {
        final String scope = scope(ledger);
        final List<Parked> parked = PARKED.values().stream().filter(p -> scope.equals(p.scope())).toList();
        if (!parked.isEmpty()) {
            final Set<String> closed = new HashSet<>();
            for (final Parked group : parked) {
                final List<String> callIds = toolCallIds(group.aiMessage());
                // a boundary chat id that matches the group's is the same turn — its results may still arrive
                if (null != boundaryChatId && null != group.chatId() && boundaryChatId.equals(group.chatId()))
                    continue;
                if (!closed.add(String.join("\u0000", callIds)))
                    continue; // a sibling call id already closed this group
                callIds.forEach(id -> HELD.putIfAbsent(key(ledger, id), new Held(scope, group.ledgerPath(), lostResultFor.apply(id))));
                publish(ledger, group.aiMessage(), callIds);
            }
        }
        // results that never found an ai message: writing them would orphan a tool
        // message, so they are dropped — loudly, they are lost conversation
        HELD.entrySet().removeIf(entry -> {
            if (!scope.equals(entry.getValue().scope()))
                return false;
            if (PARKED.containsKey(entry.getKey()))
                return false;
            LOG.warn("tool result with no ai message to answer (dropped at the turn boundary): %s",
                    CommonUtil.clipString(Str.Helper.cleanString(entry.getValue().result().at(uri(CONTENTS))), 60, true));
            return true;
        });
    }

    /** The groups parked in this ledger — diagnostics and tests. */
    public static int parked(final SpaceChatSessionStore ledger) {
        final String scope = scope(ledger);
        return (int) PARKED.values().stream().filter(p -> scope.equals(p.scope())).count();
    }

    /**
     * The tool call ids of an ai message rec, in request order — the ledger
     * keeps each request id in the {@code contents} field of its
     * {@code tool_request} rec.
     */
    public static List<String> toolCallIds(final Rec aiMessage) {
        final Obj requests = aiMessage.at(uri(TOOL_REQUESTS));
        if (requests.isNoObj() || !requests.isLst())
            return List.of();
        return requests.asLst().elements()
                .filter(Obj::isRec)
                .map(request -> Str.Helper.cleanString(request.asRec().at(uri(CONTENTS)).orElse(str("")), true))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    // ── internals ───────────────────────────────────────────────────

    /**
     * Write the group when every request has a result: the ai message first,
     * then its results in request order — the order the model and
     * LangChain4j's window require.
     */
    private static Verdict publish(final SpaceChatSessionStore ledger, final Rec aiMessage, final List<String> callIds) {
        final List<String> outstanding = callIds.stream()
                .filter(id -> !HELD.containsKey(key(ledger, id)))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        if (!outstanding.isEmpty()) {
            LOG.debug("parked ai message — %d of %d tool results at hand, waiting on %s",
                    callIds.size() - outstanding.size(), callIds.size(), outstanding);
            return new Verdict(Status.PARKED, null);
        }
        final Rec written = Router.writeToSpace(ledger.ledgerWritePath(), aiMessage).asRec();
        for (final String callId : callIds) {
            PARKED.remove(key(ledger, callId));
            PUBLISHED.put(key(ledger, callId), Boolean.TRUE);
            final Held held = HELD.remove(key(ledger, callId));
            if (null != held)
                Router.writeToSpace(ledger.ledgerWritePath(), held.result());
        }
        LOG.debug("published tool group %s (%d results)", callIds, callIds.size());
        return new Verdict(Status.PUBLISHED, written);
    }

    /** The ledger scope of a call id — a call id is unique within its conversation, not globally. */
    private static String key(final SpaceChatSessionStore ledger, final String toolCallId) {
        return ledger.memoryRoot() + "\u0000" + toolCallId;
    }

    private static String scope(final SpaceChatSessionStore ledger) {
        return ledger.memoryRoot().toString();
    }

    /** The chat id an ai message was stamped with, or null when it carries none. */
    private static String chatIdOf(final Rec aiMessage) {
        final Obj chatId = aiMessage.at(uri(CHAT_ID));
        if (chatId.isNoObj() || !chatId.isInt())
            return null;
        return String.valueOf(chatId.intValue().intValue());
    }
}
