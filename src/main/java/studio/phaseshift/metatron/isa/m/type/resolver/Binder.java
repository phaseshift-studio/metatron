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
import studio.phaseshift.metatron.isa.mach.type.compiler.resolver.GenericBinder;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Binder — the collaborator of a {@link Selector} that makes one chosen candidate concrete against
 * the call being resolved. A binder is a machine component ({@code binder::T}); the concrete
 * {@code generic_binder::T} carries the current algorithm (bind the candidate's generics to the
 * call, re-bind dom/rng from the user's hints, resolve its arguments) and rejects a candidate it
 * cannot make concrete by returning {@code null}.
 * <p>
 * Binding is deliberately <b>not</b> a stage in the compiler schedule. A candidate that fails to
 * bind must not be selectable, so binding gates selection and therefore runs inside the selector's
 * candidate walk rather than after it. The binder is handed to the selector by the resolver, so any
 * selector composes with any binder:
 * {@code resolver::[selector=>specificity_selector::T, binder=>strict_binder::T]}.
 * <p>
 * A binder's lane is narrow: make the candidate concrete or reject it. It does not re-type (that is
 * {@code typer::T}) and does not choose between type-equivalent forms (that is {@code rewriter::T}).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Binder extends Machine.Component {

    /**
     * Holder for the currently active binder. Lazily defaults to the {@code generic_binder::T} —
     * the algorithm the machine has always used, extracted into its own strategy.
     */
    AtomicReference<Binder> INSTANCE = new AtomicReference<>();

    /**
     * Get the currently active binder (defaulting to {@code generic_binder::T}).
     *
     * @return the current Binder instance
     */
    static Binder get() {
        final Binder binder = INSTANCE.get();
        if (null != binder)
            return binder;
        return INSTANCE.updateAndGet(b -> null == b ? GenericBinder.single() : b);
    }

    /**
     * Set the active binder implementation.
     *
     * @param binder the new binder to use
     * @return the previous binder
     */
    static Binder set(final Binder binder) {
        return INSTANCE.getAndSet(binder);
    }

    /**
     * Bind one candidate instruction against the call being resolved — bind its generics to the
     * left-hand-side, gate it on the lhs domain, and resolve its arguments. Returning {@code null}
     * rejects the candidate (the selector moves on).
     * <p>
     * The candidate arrives already shaped with the call's dom/rng hints: how a call's hints apply
     * to a candidate is the selector's decision (its candidate-shaping rule), while making the
     * result concrete is the binder's.
     *
     * @param lhs       the left-hand-side object being operated on
     * @param user      the user instruction being resolved (carries args and dom/rng hints)
     * @param candidate the candidate instruction selected for this call, hints applied
     * @return the bound instruction, or {@code null} to reject the candidate
     */
    Inst apply(final Obj lhs, final Inst user, final Inst candidate);
}
