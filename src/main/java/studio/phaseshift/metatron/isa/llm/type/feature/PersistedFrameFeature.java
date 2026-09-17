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
import studio.phaseshift.metatron.isa.llm.type.Frame;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.Map;

import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_PERSISTED_FRAME_FEATURE_TID;

/**
 * A frame provider that never discards frames — {@code pop()} marks the frame complete and
 * leaves it in space, so the whole frame tree stays introspectable ("compute memory").
 */
public final class PersistedFrameFeature extends AbstractFrameFeature {

    public static final fURI FEATURE_TID = LLM_PERSISTED_FRAME_FEATURE_TID;

    public PersistedFrameFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    protected void onPopped(final Frame frame, final fURI frameURI) {
        // persisted — leave the frame in space for introspection and analysis
    }
}
