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
import studio.phaseshift.metatron.isa.llm.type.Agent;
import studio.phaseshift.metatron.isa.m.type.Bool;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.ACTIVE;

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

    public AbstractFeature(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public boolean active() {
        return this.at(ACTIVE).orElse(Bool.BOOL_TRUE).boolValue();
    }
}
