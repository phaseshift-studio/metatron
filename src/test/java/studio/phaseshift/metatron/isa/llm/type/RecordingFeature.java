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
import studio.phaseshift.metatron.isa.llm.type.feature.AbstractFeature;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.isa.llm.type.Agent.feat;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A test-utility Feature that records every lifecycle hook invocation, so a test asserts "which
 * hooks fired, in what order, with what args" in one line.  Records via Java method overrides —
 * the same path {@link Agent#chat} uses to dispatch a Java Feature.
 *
 * <p>Each invocation is a {@code rec[phase, args]} appended to the feature-local {@link #trail()};
 * {@link #phases()} is the phase names alone.
 */
public class RecordingFeature extends AbstractFeature {

    private final List<String> phases = new ArrayList<>();
    private final List<Rec> trail = new ArrayList<>();

    public RecordingFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * A named, config-less recorder.
     */
    public static RecordingFeature record(final String name) {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>();
        jvm.put(uri(NAME), str(name));
        return new RecordingFeature(jvm, feat(name), null);
    }

    @Override
    public void onAgentCtor(final Agent agent) {
        capture("onAgentCtor");
    }

    @Override
    public Obj onBeforeChat(final Agent agent) {
        capture("onBeforeChat");
        return noobj();
    }

    @Override
    public void onPartialResponse(final Agent agent, final Str text) {
        capture("onPartialResponse", text);
    }

    @Override
    public Obj onPartialThinking(final Agent agent, final Obj thought) {
        capture("onPartialThinking", thought);
        return noobj();
    }

    @Override
    public void onPartialToolCall(final Agent agent, final Inst request) {
        capture("onPartialToolCall", request);
    }

    @Override
    public void onToolExecuted(final Agent agent, final Obj result) {
        capture("onToolExecuted", result);
    }

    @Override
    public Obj onToolResult(final Agent agent, final Obj result, final String requestId) {
        capture("onToolResult", result);
        return result;
    }

    @Override
    public void onCompleteResponse(final Agent agent, final ChatFrame result) {
        capture("onCompleteResponse", result);
    }

    @Override
    public void onError(final Agent agent, final Fail fail) {
        capture("onError", fail);
    }

    private void capture(final String phase, final Obj... args) {
        this.phases.add(phase);
        this.trail.add(rec(uri("phase"), str(phase), uri("args"), lst(args)));
    }

    /** The recorded phases, in invocation order. */
    public List<String> phases() {
        return this.phases;
    }

    /** The recorded invocations (phase + args), in order. */
    public List<Rec> trail() {
        return this.trail;
    }

    /** The phases recorded by every {@link RecordingFeature} on the agent, concatenated. */
    public static List<String> phases(final Agent agent) {
        return agent.features().lstValue().stream()
                .filter(f -> f instanceof RecordingFeature)
                .flatMap(f -> ((RecordingFeature) f).phases().stream())
                .toList();
    }

    /** The invocations recorded by every {@link RecordingFeature} on the agent, concatenated. */
    public static List<Rec> trail(final Agent agent) {
        return agent.features().lstValue().stream()
                .filter(f -> f instanceof RecordingFeature)
                .flatMap(f -> ((RecordingFeature) f).trail().stream())
                .toList();
    }
}
