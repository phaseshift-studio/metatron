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

package studio.phaseshift.metatron.isa.m.math.cat;

/**
 * The class axis: the set-theoretic classification of a morphism — whether it is self-mapping (endo),
 * invertible (iso), or split (section/retraction, hence mono/epi). Enum constants are lowercase so
 * {@code name()} is the mtron label. Only the sound implications are computed (the conservative classifier
 * {@code clazz(inst)}); a proper (non-split) mono/epi needs composition or a declared class table, which is
 * not wired yet.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public enum MorphClass {
    /**
     * self-mapping — {@code dom(f) = rng(f)}
     */
    endo,
    /**
     * an invertible endomorphism — {@code dom(f) = rng(f)} and {@code f} is iso
     */
    auto,
    /**
     * invertible — a self-inverse (involution); later, any two-sided inverse
     */
    iso,
    /**
     * left-cancellable — {@code f·g = f·h ⇒ g = h} (here: a split monomorphism)
     */
    mono,
    /**
     * right-cancellable — {@code g·f = h·f ⇒ g = h} (here: a split epimorphism)
     */
    epi,
    /**
     * split mono — {@code f} has a left inverse
     */
    section,
    /**
     * split epi — {@code f} has a right inverse
     */
    retraction;
}
