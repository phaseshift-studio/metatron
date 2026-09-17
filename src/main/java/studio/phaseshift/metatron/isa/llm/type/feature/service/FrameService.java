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

package studio.phaseshift.metatron.isa.llm.type.feature.service;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.Frame;
import studio.phaseshift.metatron.isa.m.type.Obj;

/**
 * The frame capability — a push/pop stack of activation records (the "compute memory" of an
 * evaluation, "agent as function").  A feature may provide it, but so may any other obj — a
 * space is the natural provider, since the frame store is space-native.
 */
public interface FrameService {

    /**
     * The current (top) frame's URI — {@code frame/s<sid>/c<cid>/d<depth>}.
     *
     * @return the top frame uri, or null when the stack is empty
     */
    fURI current();

    /**
     * Allocate a shadow frame at depth+1, stamp its identity and {@code parent} link, write it
     * flat to its URI, and return it.
     *
     * @param frame the frame to push (its argument already set)
     * @return the pushed frame, now addressed
     */
    Frame push(Frame frame);

    /**
     * Mark the top frame complete and return it — never deleted, it just stops being top.
     *
     * @return the completed top frame, or null when the stack is empty
     */
    Frame pop();

    /**
     * Read a key from the current frame, resolving shadow → root (walk the stack down until the
     * key is found).
     *
     * @param key the relative path into the frame's locals/core
     * @return the value, or noobj when no frame in the chain carries it
     */
    Obj at(fURI key);

    /**
     * Write a locals key into the current frame — a flat write to {@code <frame>/<key>}
     * (project to root).
     *
     * @param key   the relative path into the frame's locals
     * @param value the value to store
     */
    void locals(fURI key, Obj value);

    /**
     * The caller frame's URI — the return address.
     *
     * @return the parent frame uri, or null when the current frame is the root
     */
    fURI parentURI();
}
