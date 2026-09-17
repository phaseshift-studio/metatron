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
import studio.phaseshift.metatron.isa.llm.WatermarkUtil;
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.llm.type.ChatFrame;
import studio.phaseshift.metatron.isa.m.type.Bool;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.ACTIVE;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_SYSTEM_FEATURE_TID;
import studio.phaseshift.metatron.isa.llm.type.feature.service.SystemService;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

/**
 * A capability attached to an {@link Agent}.  Features are metatron Recs —
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
public abstract class AbstractFeature extends MRec implements Feature {

    protected final GraphittyLogger LOG = Graphitty.log(this);

    /**
     * Watermark rejections awaiting the next chat, where the model can see them.
     * Recorded when the model addressed a watermark to this feature and its body
     * did not decode — the model is told nothing else, so without this it repeats
     * the same malformed marker indefinitely.
     */
    private final List<String> watermarkRejections = new ArrayList<>();

    public AbstractFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public boolean active() {
        return this.at(ACTIVE).orElse(Bool.BOOL_TRUE).boolValue();
    }

    // ========================================================================
    // Cross-feature dependencies
    // ========================================================================
    //
    // Hard vs soft is declared on the type, not checked here:
    //   requires() — hard: validated at agent construction; access via agent.require(Class)
    //                (guaranteed present, or construction already failed).
    //   uses()     — soft: optional enrichment; access via agent.feature(Class) and check
    //                isPresent() — absent means degraded, not broken.

    // ========================================================================
    // Watermark feedback
    // ========================================================================

    /**
     * Note a watermark rejection for the model to see on the next chat.  The
     * primitive both rejection paths use — the completed-response path via
     * {@link #noteWatermarkFailure} and the streaming path, which has a
     * {@code Hit} rather than a published rec.
     */
    protected void rejectWatermark(final String message) {
        this.watermarkRejections.add(message);
    }

    /**
     * Record a rejection when the model addressed a watermark to this feature's
     * key and its body did not decode.  Called from {@code onCompleteResponse};
     * {@link #surfaceWatermarkRejections(Agent)} hands the report to the model on
     * the next chat.
     *
     * @param result     the chat result the watermarks were published on
     * @param codec      this feature's declared codec, for the marker in the report
     * @param defaultKey this feature's watermark key, unless its config declares one
     */
    protected void noteWatermarkFailure(final ChatFrame result, final String codec, final String defaultKey) {
        final String key = WatermarkUtil.key(this, defaultKey);
        final Obj rejected = WatermarkUtil.failed(result.watermarks(), key);
        if (rejected.isRec())
            this.rejectWatermark(WatermarkUtil.report(codec, key, rejected.asRec()));
    }

    /**
     * Hand any recorded watermark rejections to the model as system context.
     * Called from {@code onBeforeChat} — the system channel is the only place a
     * feature can speak to the model about its own last turn, because
     * {@code SystemFeature} clears its messages at the end of every chat.
     */
    protected void surfaceWatermarkRejections(final Agent agent) {
        if (this.watermarkRejections.isEmpty() || !agent.hasFeature(LLM_SYSTEM_FEATURE_TID))
            return;
        agent.requireService(SystemService.class).addSystemMessage("""
                                                                                   the watermark instructions you were given were not followed:
                                                                                   
                                                                                   %s
                                                                                   """.formatted(String.join("\n", this.watermarkRejections)));
        this.watermarkRejections.clear();
    }
}
