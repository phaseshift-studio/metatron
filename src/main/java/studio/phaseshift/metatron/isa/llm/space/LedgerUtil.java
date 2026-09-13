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
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * A repair tool for the chat ledger — {@code fsck}, invoked by hand.
 *
 * <h3>Why this is not automatic</h3>
 * {@link ToolPairGate} is the real-time fix: it holds both sides of a tool group
 * and publishes them together, so the ledger never receives a request without its
 * result.  A sweep on boot would quietly paper over that contract — a ledger that
 * needs sweeping regularly means the write path is broken, which is exactly what
 * you want to find out, not what you want hidden.  So this exists for the
 * {@code omg} case: something was interrupted mid-write, or a ledger predates the
 * gate, and you want it consistent again without hand-editing rows.
 *
 * <h3>What it repairs</h3>
 * An {@code ai_message} carrying {@code tool_requests} whose ids have no matching
 * {@code tool_result}.  That is the one corruption that breaks every reader:
 * providers reject a conversation where a tool call has no answer, so the whole
 * history after it is unusable.  A stray tool result with no request is harmless
 * and is left alone.
 *
 * <p>A group parked in memory when the process died leaves <em>nothing</em> in the
 * ledger — the gate had not written it yet — so that case is a hole rather than a
 * corruption, and there is nothing here to repair.
 */
public final class LedgerUtil {

    private static final GraphittyLogger LOG = Graphitty.log(LedgerUtil.class);

    private LedgerUtil() {
        // static fsck
    }

    /**
     * Findings: the same tool group written more than once, listed by call id.
     */
    public static final String DUPLICATE = "duplicate";

    /**
     * Findings: a request whose result is nowhere in the ledger.
     */
    public static final String ORPHAN = "orphan";

    /**
     * Findings: a request whose result <em>is</em> in the ledger, but not next to it.
     */
    public static final String MISPLACED = "misplaced";

    /**
     * Findings: a result row that no request-bearing message precedes.
     */
    public static final String ORPHAN_RESULT = "orphan_result";

    /**
     * Findings: a request whose results <em>are</em> right next to it in the ledger,
     * but stamped into another scope — so the store's projection of the turn tears
     * the group apart.
     */
    public static final String MISSCOPED = "misscoped";

    /** The keys every sweep report carries, in report order. */
    private static final List<String> FINDINGS = List.of(DUPLICATE, ORPHAN, MISPLACED, MISSCOPED, ORPHAN_RESULT);

    /**
     * Find (and optionally repair) tool groups the ledger cannot legally hold.
     *
     * <p>The invariant a provider enforces is stronger than "every request has a
     * result somewhere": the results must sit <b>immediately after</b> the assistant
     * message that requested them — <em>in the sequence the store projects</em>.  That
     * last part is not a detail.  The store reads a session's ledger at one depth (and
     * one chat id past depth 1), so two messages can be neighbours in the ledger and
     * strangers in the model's window; a group whose members disagree on their scope
     * is torn apart for the model even though the raw ledger looks perfect.  So the
     * adjacency checked here is the projected one.
     *
     * @param messageRoot the ledger root to sweep, e.g. {@code /usr/dr} — its
     *                    {@code message} collection is read
     * @param repair      when true, repair as well as report
     * @return a rec with one list of call ids per failure mode — {@link #DUPLICATE},
     *         {@link #ORPHAN}, {@link #MISPLACED}, {@link #MISSCOPED},
     *         {@link #ORPHAN_RESULT}.  Every key is always present (empty when that
     *         mode found nothing), so a caller can count without inspecting the shape;
     *         {@link #clean(Obj)} is the emptiness test.
     */
    public static Rec sweep(final fURI messageRoot, final boolean repair) {
        return sweep(messageRoot, repair, false);
    }

    /**
     * Sweep a <b>session's</b> ledger — naming what you actually want to verify,
     * rather than the root its messages happen to live under.
     *
     * <p>The root is derived the way the store derives it
     * ({@link SpaceChatSessionStore#memoryRootOf}): a session lives at
     * {@code <root>/session/<n>}, so its ledger is at {@code <root>/message}.
     */
    public static Rec sweepSession(final fURI sessionVID, final boolean repair, final boolean prune) {
        return sweep(SpaceChatSessionStore.memoryRootOf(sessionVID), repair, prune);
    }

    /**
     * The ledger root a <b>session row</b> names — its {@code agent} field, which is
     * the session's home and therefore the ledger's.
     *
     * <p>Needed because a row read back from a tble store carries no vid (see
     * {@link #readMessages}), so the {@code session/<n>} retraction the store uses is
     * not available from the rec alone.  In the normal layout the two agree: the
     * session is created with {@code agent => <agent home>} and lives at
     * {@code <agent home>/session/<n>}.
     */
    public static fURI rootOf(final Rec session) {
        final Obj agent = session.at(uri(AGENT));
        if (agent.isNoObj() || !agent.isUri())
            throw MTronException.of("a session must name its agent to locate its ledger: %s", session);
        return agent.uriValue();
    }

    /**
     * The ledger root a session names, however the caller handed the session over —
     * an address, an anchored row, or a row that lost its address on the way here.
     *
     * <p><b>The address wins whenever it is present.</b>  An anchored
     * {@code @/usr/dr/session/1} is a location and carries the vid; dereferencing
     * that same path with {@code *} is a value and does not.  So an anchored session
     * already says where it lives and needs no second opinion —
     * {@link SpaceChatSessionStore#memoryRootOf} retracts its own vid.  Only a row
     * that arrived without one is asked for its {@code agent} field
     * ({@link #rootOf}), which is what a tble store hands back.
     *
     * @param session a session address ({@code uri}), an anchored {@code session::T},
     *                or a {@code session::T} row read without its vid
     * @return the session's ledger root — the parent of its {@code session/<n>} home
     */
    public static fURI rootFor(final Obj session) {
        if (session.isUri())
            return SpaceChatSessionStore.memoryRootOf(session.uriValue());
        if (session.isRec()) {
            final Rec row = session.asRec();
            final fURI vid = row.vid();
            return (null == vid || vid.isEmpty())
                    ? rootOf(row)
                    : SpaceChatSessionStore.memoryRootOf(vid);
        }
        throw MTronException.of("a session must be an address or a session::T row to locate its ledger: %s", session);
    }

    /**
     * As {@link #sweep(fURI, boolean)}, plus {@code prune} — the opt-in to
     * <b>deleting ledger rows</b>.
     *
     * <p>Repair alone never removes a row: an unanswered request is dropped from its
     * message, which restores a valid history while keeping whatever prose that
     * message carried, and a request whose results merely carry the wrong scope has
     * those results moved into its scope rather than lost.  That is the right default
     * for a fsck — a repair tool should not silently delete content on an inference.
     *
     * <p>{@code prune} additionally deletes what <em>cannot</em> be salvaged: a
     * duplicate group (the same requests written twice — its answers were consumed
     * by the first copy, so it can never complete) and a result no request precedes
     * (nothing can be attached to it).  Every deletion is logged with the vid.
     *
     * <p>Note what pruning a duplicate costs: the surviving copy may not carry the
     * same {@code text}, so the prose of the pruned row is gone.  Measured on the
     * drstynx ledger, none of the 502 twins had identical text — which is exactly
     * why deletion is not the default.
     */
    public static Rec sweep(final fURI messageRoot, final boolean repair, final boolean prune) {
        final boolean repairing = repair || prune;
        final List<Rec> messages = readMessages(messageRoot.extend(MESSAGE));

        // The ledger is not what the model sees.  The store projects it per scope —
        // session/depth, and chat id past depth 1 (SpaceChatSessionStore.sessionRels) —
        // so a pair is adjacent only *in that projection*.  Judging adjacency on the
        // raw ledger is what let a torn group pass this sweep while every later chat
        // was rejected; the sort by ledger id that readMessages does is necessary but
        // nowhere near sufficient.
        final List<String> scopes = messages.stream().map(LedgerUtil::scopeOf).toList();
        final int[] next = nextInScope(scopes);

        // per message: the calls it asks for, the results that answer it in its own
        // scope, and the results standing right next to it in a scope that is not its
        // own — the tear the projection hides
        final List<List<String>> calls = new ArrayList<>();
        final List<Set<String>> answered = new ArrayList<>();
        final List<Map<String, Rec>> torn = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            final Rec message = messages.get(i);
            calls.add(message.tid().equals(AI_MESSAGE_TID) && message.has(TOOL_REQUESTS)
                    ? ToolPairGate.toolCallIds(message)
                    : List.of());
            final Set<String> adjacent = new HashSet<>();
            for (int j = next[i]; j >= 0 && isToolResult(messages.get(j)); j = next[j])
                adjacent.add(callIdOf(messages.get(j)));
            answered.add(adjacent);
            // the raw-ledger walk.  A result is built when its tool *returns*, which
            // for a slow tool is not when its turn was running, so a result that
            // answers this message can be standing in another scope.
            final Map<String, Rec> misstamped = new LinkedHashMap<>();
            for (int j = i + 1; j < messages.size() && isToolResult(messages.get(j)); j++) {
                final String id = callIdOf(messages.get(j));
                if (calls.get(i).contains(id) && !scopes.get(j).equals(scopes.get(i)))
                    misstamped.put(id, messages.get(j));
            }
            torn.add(misstamped);
        }

        // every result id the ledger holds, per scope — the difference between an
        // answer that never arrived and one that landed in the wrong place
        final Map<String, Set<String>> anywhere = new HashMap<>();
        for (int i = 0; i < messages.size(); i++)
            if (isToolResult(messages.get(i)))
                anywhere.computeIfAbsent(scopes.get(i), s -> new HashSet<>()).add(callIdOf(messages.get(i)));

        final List<Obj> duplicates = new ArrayList<>();
        final List<Obj> orphans = new ArrayList<>();
        final List<Obj> misplaced = new ArrayList<>();
        final List<Obj> misscoped = new ArrayList<>();
        final List<Obj> orphanResults = new ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            final List<String> callIds = calls.get(i);
            if (callIds.isEmpty())
                continue;
            final int index = i; // captured for the lambda
            final List<String> unanswered = callIds.stream().filter(id -> !answered.get(index).contains(id)).toList();
            if (unanswered.isEmpty())
                continue;
            final Rec message = messages.get(i);
            // A DUPLICATE of a group that IS complete: the same requests, written
            // twice.  The gate's dedup memory (PUBLISHED) is in-JVM and bounded, so
            // a restart lets a re-offered group be published a second time — and the
            // second copy takes no results with it, because the first consumed them.
            // Measured on the drstynx ledger: 502 ai messages have such a twin, and
            // every single adjacency finding was one of them.
            final int twin = twinOf(calls, answered, i);
            if (twin >= 0) {
                unanswered.forEach(id -> duplicates.add(str(id)));
                if (prune) {
                    LOG.warn("pruning duplicate ai message %s — the same tool requests as %s",
                            message.vid(), messages.get(twin).vid());
                    remove(message);
                } else if (repairing) {
                    // non-destructive: the requests can never be answered, so they go,
                    // but the message and its prose stay
                    dropUnanswered(message, unanswered);
                }
                continue;
            }
            // A request can be unanswered in its scope while its results are sitting
            // right there in another one: they were stamped with the depth the agent
            // had drifted to by the time their tools returned.  That is a scope tear,
            // not a lost answer — so it is its own finding, and its own repair, which
            // rescopes the results rather than dropping the request.  Reporting it as
            // misplaced would hand it to the drop-the-request repair and destroy a
            // group whose answer was never missing.
            unanswered.forEach(id -> {
                if (torn.get(index).containsKey(id))
                    misscoped.add(str(id));
                else if (anywhere.getOrDefault(scopes.get(index), Set.of()).contains(id))
                    misplaced.add(str(id));
                else
                    orphans.add(str(id));
            });
            if (repairing) {
                torn.get(index).forEach((id, result) -> {
                    if (unanswered.contains(id))
                        respace(result, message);
                });
                final List<String> stillOpen = unanswered.stream()
                        .filter(id -> !torn.get(index).containsKey(id))
                        .toList();
                if (!stillOpen.isEmpty())
                    dropUnanswered(message, stillOpen);
            }
        }

        // a result that no request-bearing message precedes is a tool message with
        // nothing to answer, which providers reject as well
        final Set<fURI> placed = new HashSet<>();
        for (int i = 0; i < messages.size(); i++)
            if (!calls.get(i).isEmpty()) {
                for (int j = next[i]; j >= 0 && isToolResult(messages.get(j)); j = next[j])
                    placed.add(messages.get(j).vid());
                // a torn result is not an orphan: it has a home, it just carries the
                // wrong stamp, and respace() is what puts it back
                torn.get(i).values().forEach(result -> placed.add(result.vid()));
            }
        for (final Rec message : messages)
            if (isToolResult(message) && !placed.contains(message.vid())) {
                orphanResults.add(str(callIdOf(message)));
                if (prune) {
                    LOG.warn("pruning orphan result %s — no request-bearing message precedes it", message.vid());
                    remove(message);
                }
            }

        return rec(uri(DUPLICATE), lst(duplicates),
                uri(ORPHAN), lst(orphans),
                uri(MISPLACED), lst(misplaced),
                uri(MISSCOPED), lst(misscoped),
                uri(ORPHAN_RESULT), lst(orphanResults));
    }

    /** Whether a message is a tool result — the tid that carries an answer. */
    private static boolean isToolResult(final Rec message) {
        return message.tid().equals(TOOL_RESULT_MESSAGE_TID);
    }

    /** The tool call id a message joins on, which for a result is its {@code contents}. */
    private static String callIdOf(final Rec message) {
        return Str.Helper.cleanString(message.at(uri(CONTENTS)));
    }

    /**
     * The projection scope of a message — the fields the store filters on, and
     * therefore the only grouping in which "one message follows another" means
     * anything to the model.
     *
     * <p>Mirrors {@link SpaceChatSessionStore}'s rule: every message of a session at
     * one depth, and past depth 1 one turn's chat id as well (a deeper recursive turn
     * is isolated, while depth 1 shares the conversation for continuity).  A message
     * with no depth is in no scope at all — the store drops it — so it is given a key
     * of its own rather than being folded in with the messages that do have one.
     */
    private static String scopeOf(final Rec message) {
        final Obj session = message.at(uri(SESSION));
        final Obj depth = message.at(uri(DEPTH));
        final Obj chatId = message.at(uri(CHAT_ID));
        final int d = depth.isInt() ? depth.intValue().intValue() : NO_DEPTH;
        return (session.isUri() ? session.uriValue().toString() : "-")
                + '\u0000' + d
                + (d > 1 && chatId.isInt() ? "\u0000" + chatId.intValue().intValue() : "");
    }

    /** The depth that marks a message as belonging to no scope, below the idle depth 0. */
    private static final int NO_DEPTH = Integer.MIN_VALUE;

    /**
     * For each message, the index of the next message in its own scope — the sequence
     * that scope actually sees, with every other scope's rows stepped over.
     *
     * <p>Always a larger index or {@code -1}, so a walk along it terminates.
     */
    private static int[] nextInScope(final List<String> scopes) {
        final int[] next = new int[scopes.size()];
        final Map<String, Integer> last = new HashMap<>();
        for (int i = scopes.size() - 1; i >= 0; i--) {
            next[i] = last.getOrDefault(scopes.get(i), -1);
            last.put(scopes.get(i), i);
        }
        return next;
    }

    /**
     * Move a mis-stamped result into the scope of the request it answers — the
     * non-destructive repair for a torn group.
     *
     * <p>The result is the right result for the right call id, and it already stands
     * next to its request in the ledger; only the scope stamp is wrong.  So nothing is
     * dropped and nothing is invented — the stamp is corrected, and the group is whole
     * again in the projection the model reads.
     *
     * <p>Note the {@code vid(null)}.  The row is copied to a <em>value</em> first, so
     * the edits below cannot reach back through the anchor into the live row, and the
     * single explicit write is the only write the repair performs.
     */
    private static void respace(final Rec result, final Rec request) {
        final fURI vid = result.vid();
        if (null == vid || vid.isEmpty())
            return;
        try {
            final Obj session = request.at(uri(SESSION));
            final Obj depth = request.at(uri(DEPTH));
            final Obj chatId = request.at(uri(CHAT_ID));
            Rec scoped = result.vid(null);
            if (session.isUri())
                scoped = scoped.at(uri(SESSION), session, MUTABLE);
            if (depth.isInt())
                scoped = scoped.at(uri(DEPTH), jnt(depth.intValue().intValue()), MUTABLE);
            if (chatId.isInt())
                scoped = scoped.at(uri(CHAT_ID), chatId, MUTABLE);
            Router.writeToSpace(vid, scoped);
            LOG.warn("re-scoping tool result %s into %s — it answers a request in that scope",
                    vid, scopeOf(request));
        } catch (final Exception e) {
            // best-effort: a repair that cannot be written is reported, not thrown
        }
    }

    /**
     * Whether a sweep found nothing — every failure list empty.  The one place the
     * report's shape is known, so callers need not be.
     */
    public static boolean clean(final Obj findings) {
        if (!findings.isRec())
            return true;
        for (final String key : FINDINGS) {
            final Obj ids = findings.asRec().at(uri(key));
            if (!ids.isNoObj() && !ids.asLst().isEmpty())
                return false;
        }
        return true;
    }

    /**
     * Another ai message asking for exactly the same calls, whose own requests are
     * all answered — i.e. this message is a second copy of a complete group.
     *
     * @return the index of that twin, or {@code -1}
     */
    private static int twinOf(final List<List<String>> calls, final List<Set<String>> answered, final int i) {
        for (int j = 0; j < calls.size(); j++)
            if (j != i && calls.get(j).equals(calls.get(i)) && answered.get(j).containsAll(calls.get(j)))
                return j;
        return -1;
    }

    /** Delete a ledger row — a duplicate carries nothing of its own to lose. */
    private static void remove(final Rec message) {
        final fURI vid = message.vid();
        if (null == vid || vid.isEmpty())
            return;
        try {
            Router.writeToSpace(vid, noobj());
        } catch (final Exception e) {
            // best-effort: a repair that cannot be written is reported, not thrown
        }
    }

    /**
     * Repair a message that asks for results which did not survive: remove those
     * requests from it.
     *
     * <p>Deliberately <b>not</b> "write the missing results".  Results must sit
     * immediately after their assistant message, and a message already in the
     * ledger cannot have them written next to it — appending to the end puts them
     * in the wrong place, which made an earlier version of this fsck report a
     * ledger the provider still rejected.  Dropping the request is what actually
     * restores a valid history, and it cannot lose a result: the pairing gate holds
     * results until a group is complete, so a request still unanswered here has no
     * result of its own anywhere.
     */
    private static void dropUnanswered(final Rec aiMessage, final List<String> unanswered) {
        final fURI vid = aiMessage.vid();
        if (null == vid || vid.isEmpty())
            return;
        try {
            final List<Obj> kept = aiMessage.at(uri(TOOL_REQUESTS)).asLst().elements()
                    .filter(request -> !unanswered.contains(Str.Helper.cleanString(request.asRec().at(uri(CONTENTS)))))
                    .toList();
            aiMessage.at(uri(TOOL_REQUESTS), kept.isEmpty() ? noobj() : lst(kept), MUTABLE);
            Router.writeToSpace(vid, aiMessage);
        } catch (final Exception e) {
            // best-effort: a repair that cannot be written is reported, not thrown
        }
    }

    /**
     * Every message under a collection.
     *
     * <p>{@code stream()} is what makes this total: an {@code objs} — what a
     * wildcard read returns when it matched several things — streams its members,
     * while every other obj streams itself.  So one code path covers "one match"
     * and "many matches" alike, with no shape inspection and no special cases.
     *
     * <p>The {@code +/} form is the ledger's own read idiom
     * ({@code SpaceChatSessionStore.sessionRels}), whose leaves are rels carrying
     * the message as their second.
     */
    private static List<Rec> readMessages(final fURI messageCollection) {
        final Obj rows = Router.readFromSpace(messageCollection.extend("+/"));
        if (rows.isNoObj())
            return List.of();
        final List<Rec> messages = new ArrayList<>();
        rows.stream()
                .map(Obj::asRel)
                .filter(rel -> rel.second().isRec())
                // the vid lives on the REL, not reliably on the value inside it — a
                // space may or may not stamp the child with its own vid, so take it
                // from the rel and attach it.  Everything downstream needs it: the
                // ledger-id sort (adjacency is meaningless without order) and every
                // repair (which rewrites the message at its vid).
                .map(rel -> rel.second().vid(rel.first().uriValue()).asRec())
                .forEach(messages::add);
        // ledger id order — the gate writes a group contiguously, so adjacency is
        // only meaningful once the messages are in the order the ledger holds them
        messages.sort(Comparator.comparingInt(LedgerUtil::ledgerId));
        return messages;
    }

    /**
     * A message's position in the ledger, from its vid's last segment.
     *
     * <p>Throws rather than falling back to an arbitrary order: adjacency is the
     * whole point of the sweep, and a message whose position cannot be established
     * makes every later comparison meaningless.  Reporting hundreds of findings
     * that are all false is worse than reporting that the ledger cannot be read.
     */
    private static int ledgerId(final Rec message) {
        final fURI vid = message.vid();
        if (null == vid || vid.isEmpty())
            throw MTronException.of("ledger message without a vid — cannot establish its order: %s", message);
        try {
            return Integer.parseInt(vid.name());
        } catch (final NumberFormatException e) {
            throw MTronException.of("ledger message vid is not a ledger id: %s", vid);
        }
    }
}
