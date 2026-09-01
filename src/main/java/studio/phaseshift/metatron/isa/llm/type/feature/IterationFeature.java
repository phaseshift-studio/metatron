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

package studio.phaseshift.metatron.isa.llm.type.feature;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.space.SpaceChatSessionStore;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatResult;
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.time.Instant;
import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * Overlays an iteration graph on the chat message ledger.
 * <p>
 * Each {@link Agent#chat(String)} call creates one iteration record in a
 * flat {@code llm_iteration} table alongside the existing {@code llm_message}
 * table.  Iterations form a doubly-linked list via {@code prev} / {@code next}
 * fields, and each iteration carries {@code auto_from_} references to the
 * messages written during that turn.
 * <p>
 * This feature is purely an overlay — it reads message VIDs from
 * {@link MessageFeature}'s {@link SpaceChatSessionStore} but never modifies
 * the message schema.  Deleting iterations has no effect on messages.
 *
 * <h3>URI topology</h3>
 * <pre>
 *   .../llm_session/1          ← session policy (unchanged)
 *   .../llm_iteration/_?incrq   ← iteration records (this feature)
 *   .../llm_message/_?incrq     ← messages (unchanged)
 * </pre>
 *
 * <h3>Iteration Rec shape</h3>
 * <pre>
 *   tid:      /m/llm/iteration
 *   session:  parent session VID
 *   index:    1-based ordinal within session
 *   prev:     previous iteration VID (noobj for first)
 *   next:     next iteration VID (noobj for last; back-patched)
 *   message:  lst of auto_from_ refs to message VIDs
 *   time:     creation timestamp
 * </pre>
 */
public class IterationFeature extends AbstractFeature {

    private static final String LLM_ITERATION_TABLE = "iteration";

    private static final fURI PREV_INST_TID = LLM_ITERATION_FEATURE_TID.extend(INST).extend("prev");
    private static final fURI NEXT_INST_TID = LLM_ITERATION_FEATURE_TID.extend(INST).extend("next");

    /**
     * The iteration record created for the current chat — attached to the chat_result as a ref.
     */
    private fURI iterationVid;

    public IterationFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    // =========================================================================
    // Lifecycle hooks
    // =========================================================================

    /**
     * Creates the iteration record for this chat turn and links it into
     * the session's prev/next chain.  The iteration VID is exposed on the
     * agent blackboard at {@code res("iteration")} for other features.
     */
    @Override
    public Obj onBeforeChat(final Agent agent) {
        this.registerSkill(agent);
        if (!agent.hasFeature(LLM_MESSAGE_FEATURE_TID)) return noobj();
        try {
            final MessageFeature messageFeature = agent.feature(LLM_MESSAGE_FEATURE_TID).as();
            final fURI sessionVID = messageFeature.at(SESSION).uriValue();
            final Rec iteration = createIteration(sessionVID);
            this.iterationVid = iteration.vid();
            LOG.debug("created iteration %s for session %s", iteration.vid(), sessionVID);
        } catch (final Exception e) {
            LOG.warn("iteration feature onBeforeChat failed: %s", e.getMessage());
        }
        return noobj();
    }

    /**
     * Links the messages written during this turn to the iteration record.
     * Called after the LLM response is complete and all messages have been
     * persisted by {@link SpaceChatSessionStore#updateMessages}.
     */
    @Override
    public void onCompleteResponse(final Agent agent, final ChatResult result) {
        if (!agent.hasFeature(LLM_MESSAGE_FEATURE_TID)) return;
        try {
            final MessageFeature messageFeature = agent.feature(LLM_MESSAGE_FEATURE_TID).as();
            final SpaceChatSessionStore store = messageFeature.store();
            if (store == null) return;

            final Set<fURI> messageVIDs = store.getCurrentMessages();
            if (messageVIDs.isEmpty()) return;

            final fURI iterationVID = this.iterationVid;
            if (null == iterationVID) return;

            result.putRef("iteration", iterationVID);
            linkMessages(iterationVID, messageVIDs);
            LOG.debug("linked %d messages to iteration %s", messageVIDs.size(), iterationVID);
        } catch (final Exception e) {
            LOG.warn("iteration feature on_complete_response failed: %s", e.getMessage());
        }
    }

    // =========================================================================
    // Skill
    // =========================================================================

    /**
     * Expose {@code prev} and {@code next} navigation tools so the agent can
     * walk the iteration linked list.  Each tool takes an iteration VID and
     * returns the linked iteration Rec (or noobj at the ends of the chain).
     */
    /**
     * Register this feature's skill with the SkillFeature gateway — degrades
     * quietly when the gateway is absent (this feature is parked and does not
     * hard-require it).
     */
    public void registerSkill(final Agent agent) {
        if (!agent.hasFeature(LLM_SKILL_FEATURE_TID))
            return;
        agent.feature(LLM_SKILL_FEATURE_TID).<SkillFeature>as().addSkill(mSkill.of(rec(
                uri(NAME), uri(ITERATION),
                uri(DESC), str("Iteration graph overlay with prev/next linked-list navigation"),
                uri(TOOL), lst(
                        docWrap(instC(PREV_INST_TID.dom(ALL.maybe()).rng(LLM_ITERATION_TID.maybe()),
                                        lst(URI_TYPE),
                                        start_(jnt(0)).from_(id_()).select_(uri(PREV)).from_(id_()).tryToInst()),
                                "maybe an obj",
                                "the previous iteration rec, or noobj if this is the first iteration",
                                Map.<Obj, String>of(jnt(0), "an iteration uri"),
                                "navigate to the previous iteration in the session",
                                PREV_INST_TID + "(iteration_1) [-- returns the iteration before iteration_1 --]"),
                        docWrap(instC(NEXT_INST_TID.dom(ALL.maybe()).rng(LLM_ITERATION_TID.maybe()),
                                        lst(URI_TYPE),
                                        start_(jnt(0)).from_(id_()).select_(uri(NEXT)).from_(id_()).tryToInst()),
                                "maybe an obj",
                                "the next iteration rec, or noobj if this is the last iteration",
                                Map.<Obj, String>of(jnt(0), "an iteration uri"),
                                "navigate to the next iteration in the session",
                                NEXT_INST_TID + "(iteration_1) [-- returns the iteration after iteration_1 --]"))
        )));
    }

    // =========================================================================
    // Core logic
    // =========================================================================

    /**
     * Build the write URI for the iteration table.
     * <pre>
     *   /db/llm_session/1  →  /db/llm_iteration/_?incrq
     * </pre>
     */
    private static fURI llmIterationPath(final fURI sessionVID) {
        return sessionVID.retract(2).extend(LLM_ITERATION_TABLE).extend("_").addQ("incrq");
    }

    /**
     * Create a new iteration record, link it to the previous iteration,
     * and back-patch the previous iteration's {@code next} pointer.
     */
    private Rec createIteration(final fURI sessionVID) {
        final Rec tail = findTail(sessionVID);
        final int newIndex = (tail == null) ? 1 : tail.at(uri(INDEX)).intValue().intValue() + 1;

        final Map<Obj, Obj> fields = new LinkedHashMap<>();
        fields.put(uri(SESSION), uri(sessionVID));
        fields.put(uri(INDEX), jnt(newIndex));
        fields.put(uri(PREV), tail != null ? uri(tail.vid()) : noobj());
        fields.put(uri(NEXT), noobj());
        fields.put(uri(TIME), str(Date.from(Instant.now()).toString()));

        final Obj written = Router.writeToSpace(llmIterationPath(sessionVID),
                rec(fields, LLM_ITERATION_TID, null));

        // Back-patch the previous iteration's next pointer
        if (tail != null) {
            final Rec tailRec = Router.readFromSpace(tail.vid()).asRec();
            tailRec.at(uri(NEXT), uri(written.vid()), Poly.MUTABLE);
            Router.writeToSpace(tail.vid(), tailRec);
        }

        return written.asRec();
    }

    /**
     * Find the tail iteration (the one with {@code next == noobj}) for a session.
     * Returns {@code null} if no iterations exist yet.
     */
    private Rec findTail(final fURI sessionVID) {
        final fURI iterBase = sessionVID.retract(2).extend(LLM_ITERATION_TABLE);
        return at_(uri(iterBase.extend("+"))).tryToInst().apply(jnt(1)).stream()
                .filter(Obj::isRec)
                .map(Obj::asRec)
                .filter(r -> {
                    final Obj sessionField = r.at(uri(SESSION));
                    return !sessionField.isNoObj() && sessionField.isUri()
                            && sessionField.uriValue().equals(sessionVID);
                })
                .filter(r -> r.at(uri(NEXT)).isNoObj())
                .findFirst()
                .orElse(null);
    }

    /**
     * Write {@code auto_from_} message references into the iteration record.
     * Uses read-modify-write to preserve existing fields (prev, next, etc.).
     */
    private void linkMessages(final fURI iterationVID, final Set<fURI> messageVIDs) {
        final Rec iteration = Router.readFromSpace(iterationVID).asRec();
        iteration.at(uri(MESSAGE), lst(
                messageVIDs.stream()
                        .filter(Objects::nonNull)
                        .map(id -> (Obj) auto_from_(id).tryToInst())
                        .toList()
        ), Poly.MUTABLE);
        Router.writeToSpace(iterationVID, iteration);
    }

    // =========================================================================
    // Navigation (public query API)
    // =========================================================================

    /**
     * Return the previous iteration in the linked list, or empty if this is
     * the first iteration.
     */
    public Optional<Rec> prev(final fURI iterationVID) {
        final Obj obj = Router.readFromSpace(iterationVID);
        if (!obj.isRec()) return Optional.empty();
        final Obj prevField = obj.asRec().at(uri(PREV));
        if (prevField.isNoObj()) return Optional.empty();
        final Obj prevObj = Router.readFromSpace(prevField.uriValue());
        return prevObj.isRec() ? Optional.of(prevObj.asRec()) : Optional.empty();
    }

    /**
     * Return the next iteration in the linked list, or empty if this is
     * the last iteration.
     */
    public Optional<Rec> next(final fURI iterationVID) {
        final Obj obj = Router.readFromSpace(iterationVID);
        if (!obj.isRec()) return Optional.empty();
        final Obj nextField = obj.asRec().at(uri(NEXT));
        if (nextField.isNoObj()) return Optional.empty();
        final Obj nextObj = Router.readFromSpace(nextField.uriValue());
        return nextObj.isRec() ? Optional.of(nextObj.asRec()) : Optional.empty();
    }

    /**
     * Return all iteration records for a session, ordered by index.
     */
    public List<Rec> iterations(final fURI sessionVID) {
        final fURI iterBase = sessionVID.retract(2).extend(LLM_ITERATION_TABLE);
        final List<Rec> results = new ArrayList<>();
        at_(uri(iterBase.extend("+"))).tryToInst().apply(jnt(1)).stream()
                .filter(Obj::isRec)
                .map(Obj::asRec)
                .filter(r -> {
                    final Obj sessionField = r.at(uri(SESSION));
                    return !sessionField.isNoObj() && sessionField.isUri()
                            && sessionField.uriValue().equals(sessionVID);
                })
                .forEach(results::add);
        results.sort(Comparator.comparingInt(a -> a.at(uri(INDEX)).intValue().intValue()));
        return results;
    }

    /**
     * Return the message VIDs linked to an iteration.
     */
    public List<fURI> messages(final fURI iterationVID) {
        final Obj obj = Router.readFromSpace(iterationVID);
        if (!obj.isRec()) return List.of();
        final Obj messageField = obj.asRec().at(uri(MESSAGE));
        if (messageField.isNoObj() || !messageField.isLst()) return List.of();
        return messageField.lstValue().stream()
                .filter(Obj::isUri)
                .map(Obj::uriValue)
                .toList();
    }
}
