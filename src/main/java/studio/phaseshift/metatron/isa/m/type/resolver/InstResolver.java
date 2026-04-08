/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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
import java.util.stream.Stream;

/**
 * Strategy interface for instruction resolution.
 * <p>
 * Given a left-hand-side object (lhs), a user instruction being resolved,
 * and a stream of candidate instructions fetched from the router,
 * the resolver selects the best matching instruction.
 * <p>
 * Different implementations can use different selection strategies:
 * <ul>
 *   <li>{@link FirstFindInstResolver} - uses first matching candidate (original behavior)</li>
 *   <li>{@link ScoringInstResolver} - scores candidates by specificity, selects best match</li>
 * </ul>
 * <p>
 * Use {@link #get()} to access the current resolver and {@link #set(InstResolver)} to change it.
 */
@FunctionalInterface
public interface InstResolver {

    /**
     * Holder for the currently active resolver instance.
     * Defaults to {@link ScoringInstResolver}.
     */
    AtomicReference<InstResolver> INSTANCE = new AtomicReference<>(new ScoringInstResolver());

    /**
     * Get the currently active resolver.
     *
     * @return the current InstResolver instance
     */
    static InstResolver get() {
        return INSTANCE.get();
    }

    /**
     * Set the active resolver implementation.
     *
     * @param resolver the new resolver to use
     * @return the previous resolver
     */
    static InstResolver set(final InstResolver resolver) {
        return INSTANCE.getAndSet(resolver);
    }

    /**
     * Resolve an instruction by selecting the best match from candidates.
     *
     * @param lhs        the left-hand-side object being operated on
     * @param userInst   the user instruction being resolved (contains args, dom/rng hints)
     * @param candidates stream of candidate instructions fetched from router
     * @return the resolved instruction with bound generics and resolved args, or null if no match
     */
    Inst resolve(Obj lhs, Inst userInst, Stream<Obj> candidates);
}
