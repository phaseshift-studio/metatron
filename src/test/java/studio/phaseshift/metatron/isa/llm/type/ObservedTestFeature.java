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

package studio.phaseshift.metatron.isa.llm.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.feature.Feature;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.type.Agent.feat;
import static studio.phaseshift.metatron.isa.llm.type.Agent.res;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A test utility Feature that records every lifecycle hook invocation
 * into the Agent's result blackboard at {@code res("_audit")}.  Each
 * entry is a Rec with {@code phase, args...}.
 * <p>
 * Hooks are self-registered via {@code instLambda} wrappers in the
 * constructor — no ISA or Type registration needed.
 */
public class ObservedTestFeature extends Feature {

    private static final fURI AUDIT = res("_audit");

    public ObservedTestFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        registerHooks();
    }

    /**
     * Convenience factory with no config JVM — just a named observe-only feature.
     */
    public static ObservedTestFeature observe(final String name) {
        final Map<Obj, Obj> jvm = new java.util.LinkedHashMap<>();
        jvm.put(uri(NAME), str(name));
        return new ObservedTestFeature(jvm, feat(name), null);
    }

    // ── Hook registration (self-wiring, no Type system needed) ──

    private void registerHooks() {
        this.at(uri(ON_BEFORE_CHAT), instLambda((agent, ignored) ->
                this.audit((Agent) agent, "onBeforeChat")), MUTABLE);

        this.at(uri(ON_PARTIAL_RESPONSE), instLambda((agent, i) -> {
                this.audit((Agent) agent, "onPartialResponse", i.arg(0));
                return noobj();
        }), MUTABLE);

        this.at(uri(ON_PARTIAL_THINKING), instLambda((agent, i) -> {
                this.audit((Agent) agent, "onPartialThinking", i.arg(0));
                return noobj();
        }), MUTABLE);

        this.at(uri(ON_PARTIAL_TOOL_CALL), instLambda((agent, i) -> {
                this.audit((Agent) agent, "onPartialToolCall", i.arg(0));
                return noobj();
        }), MUTABLE);

        this.at(uri(ON_TOOL_EXECUTED), instLambda((agent, i) -> {
                this.audit((Agent) agent, "onToolExecuted", i.arg(0));
                return noobj();
        }), MUTABLE);

        this.at(uri(ON_COMPLETE_RESPONSE), instLambda((agent, i) -> {
                this.audit((Agent) agent, "onCompleteResponse", i.arg(0));
                return noobj();
        }), MUTABLE);

        this.at(uri("onError"), instLambda((agent, i) -> {
                this.audit((Agent) agent, "onError", i.arg(0));
                return noobj();
        }), MUTABLE);
    }

    // ── Audit trail ────────────────────────────────────────────────

    private Obj audit(final Agent agent, final String phase, final Obj... args) {
        final Obj trail = agent.at(AUDIT);
        final Lst list = trail.isNoObj() ? lst() : trail.asLst();
        final Rec entry = rec(uri("phase"), str(phase), uri("args"), lst(args));
        agent.at(AUDIT, list.add(entry, MUTABLE), MUTABLE);
        return noobj();
    }

    // ── Audit accessor ─────────────────────────────────────────────

    /** Read the full audit trail from the Agent's result blackboard. */
    public static List<Rec> auditTrail(final Agent agent) {
        final Obj trail = agent.at(AUDIT);
        if (trail.isNoObj()) return List.of();
        return trail.asLst().lstValue().stream().map(Obj::asRec).toList();
    }

    /** Read the audit trail from a chat result Rec. */
    public static List<Rec> auditTrail(final Rec result) {
        final Obj trail = result.at(AUDIT);
        if (trail.isNoObj()) return List.of();
        return trail.asLst().lstValue().stream().map(Obj::asRec).toList();
    }
}
