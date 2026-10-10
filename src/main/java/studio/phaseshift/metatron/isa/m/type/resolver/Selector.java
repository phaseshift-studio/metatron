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
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.compiler.resolver.SpecificitySelector;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Selector — the per-call strategy of the resolution stage: given a left-hand-side and the user
 * instruction being resolved, it fetches the candidate instructions the call may resolve to,
 * orders them by its own rule, and returns the first candidate its {@link Binder} can make
 * concrete, or {@code null} when nothing matches.
 * <p>
 * A selector is a machine component ({@code selector::T}) and the strategy half of
 * {@code resolver::T}: the resolver owns the walk (threading the type through a whole
 * {@code code::T}) and hands each instruction to its selector. The binder arrives as an argument
 * rather than as a field so that one selector composes with any binder, and neither holds global
 * state:
 * {@code resolver::[selector=>specificity_selector::T, binder=>generic_binder::T]}.
 * <p>
 * The ordering rule is the whole strategy — and it is where the interesting variation lives:
 * {@code specificity_selector::T} scores by signature specificity and takes the best,
 * {@code firstfind_selector::T} takes the first viable candidate in read order. A selector that
 * cannot resolve an instruction returns {@code null} rather than failing, leaving it for runtime
 * resolution.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Selector extends Machine.Component {

    /**
     * Holder for the currently active selector. Lazily defaults to the
     * {@code specificity_selector::T} — the scoring strategy the machine has always used.
     */
    AtomicReference<Selector> INSTANCE = new AtomicReference<>();

    /**
     * Get the currently active selector (defaulting to {@code specificity_selector::T}).
     *
     * @return the current Selector instance
     */
    static Selector get() {
        final Selector selector = INSTANCE.get();
        if (null != selector)
            return selector;
        return INSTANCE.updateAndGet(s -> null == s ? SpecificitySelector.single() : s);
    }

    /**
     * Set the active selector implementation.
     *
     * @param selector the new selector to use
     * @return the previous selector
     */
    static Selector set(final Selector selector) {
        return INSTANCE.getAndSet(selector);
    }

    /**
     * Resolve one user instruction against {@code lhs}: fetch and order its candidates, bind each in
     * order with {@code binder}, and return the first that binds.
     *
     * @param lhs    the left-hand-side object being operated on
     * @param user   the user instruction being resolved (contains args, dom/rng hints)
     * @param binder the binder that makes a candidate concrete or rejects it
     * @return the resolved instruction, or {@code null} if no candidate binds
     */
    Inst apply(final Obj lhs, final Inst user, final Binder binder);
}
