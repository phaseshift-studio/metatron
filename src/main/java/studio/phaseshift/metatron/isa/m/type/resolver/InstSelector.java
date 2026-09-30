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

package studio.phaseshift.metatron.isa.m.type.resolver;

import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.concurrent.atomic.AtomicReference;

/**
 * InstSelector — the per-instruction selection strategy. Given a left-hand-side object and a user
 * instruction being resolved, select the best matching candidate (with bound generics and resolved
 * args), or {@code null} when no candidate matches.
 * <p>
 * This is the type-system resolution unit, used by {@code Inst.resolve(lhs)}. It is distinct from
 * {@link Resolver}, the {@code resolver::T} compiler stage that threads the type through a whole
 * {@code code::T} and calls the active selector once per instruction.
 * <p>
 * Use {@link #get()} to access the current selector and {@link #set(InstSelector)} to change it.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@FunctionalInterface
public interface InstSelector {

    /**
     * Holder for the currently active selector instance. Defaults to {@link ScoringInstResolver}.
     */
    AtomicReference<InstSelector> INSTANCE = new AtomicReference<>(new ScoringInstResolver());

    /**
     * Get the currently active selector.
     *
     * @return the current InstSelector instance
     */
    static InstSelector get() {
        return INSTANCE.get();
    }

    /**
     * Set the active selector implementation.
     *
     * @param selector the new selector to use
     * @return the previous selector
     */
    static InstSelector set(final InstSelector selector) {
        return INSTANCE.getAndSet(selector);
    }

    /**
     * Resolve an instruction with selector-owned candidate fetching. This is the primary resolution
     * method: candidate fetching, selection, generics binding, and argument resolution.
     *
     * @param lhs      the left-hand-side object being operated on
     * @param userInst the user instruction being resolved (contains args, dom/rng hints)
     * @return the resolved instruction with bound generics and resolved args, or {@code null} if no match
     */
    Inst resolveInst(Obj lhs, Inst userInst);
}
