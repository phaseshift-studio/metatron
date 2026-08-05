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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.*;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * A capability attached to an {@link Agent}. Features are metatron Recs —
 * their fields are the feature's parameters, their VID is their TID.  The
 * Type system constructs them directly; no manual registry needed.
 *
 * <h3>Registration</h3>
 * Define a Type in the LLM ISA with the feature's TID and a constructor
 * that takes a parameter Rec and returns the Feature instance.  The Type
 * constructor is a {@code Function<Obj, Obj>} — {@code config -> new
 * ChatFeature(config.asRec().jvm(), ...)}.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #onBeforeChat(Agent)} — return {@code noobj()} to continue;
 *       return non-noobj to short-circuit the chat.</li>
 *   <li>Streaming hooks — observe the LLM response as it arrives.</li>
 *   <li>{@link #onCompleteResponse(Agent, Str)} — final response received.</li>
 *   <li>{@link #onError(Agent, Fail)} — chat failed.</li>
 * </ol>
 */
public interface Feature {

    // ── Skill ─────────────────────────────────────────────────────

    /**
     * Optional skill description for features that have a user-facing
     * interaction model.  Returns {@code noobj()} by default — features
     * with no interaction model (observational, internal) return noobj.
     * <p>
     * When non-noobj, the SkillFeature registers this as a lazy-loadable
     * skill so the agent can learn usage details on demand rather than
     * carrying them in every system prompt.
     */
    default Lst skill(final Agent agent) {
        return lst().c(cInt::zero);
    }

    /**
     * Whether this feature is active.  Defaults to checking the
     * {@code active} field on the backing Rec, falling back to {@code true}.
     */
    boolean active();

    // ── Pre-chat ─────────────────────────────────────────────────

    default void onAgentCtor(final Agent agent) {
    }

    /**
     * Called after the raw user message is stored on the Agent but before the
     * LLM is invoked.  Return {@code noobj()} to continue to the next feature
     * or the LLM call.  Return a non-noobj value to short-circuit the chain —
     * that value becomes the chat response immediately.
     * <p>
     * Same contract as {@code QProc}: noobj means "I don't have an answer,
     * keep going."  Non-noobj means "use this, stop processing."
     */
    default Obj onBeforeChat(final Agent agent) {
        return noobj();
    }

    // ── Streaming (observation) ──────────────────────────────────

    default void onPartialResponse(final Agent agent, final Str text) {
    }

    default void onPartialThinking(final Agent agent, final Str text) {
    }

    default void onPartialToolCall(final Agent agent, final Inst request) {
    }

    // ── Tool execution ───────────────────────────────────────────

    default void beforeToolExecution(final Agent agent, final Inst request) {
    }

    default void onToolExecuted(final Agent agent, final Obj result) {
    }

    // ── Completion ───────────────────────────────────────────────

    default void onCompleteResponse(final Agent agent, final Str response) {
    }

    // ── Error ────────────────────────────────────────────────────

    default void onError(final Agent agent, final Fail fail) {
    }

    /*
    A lst of feature tids required to also be active with this feature
     */
    default Lst requires() {
        return lst();
    }
}
    