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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.util.CommonUtil;

/**
 * A machine component that a frame <em>wraps</em> rather than replaces.
 * <p>
 * It is named "union" for the frame behaviour, not for a set union: the wrapper holds the value this frame
 * introduced ({@link #current()}) alongside the one it inherited ({@link #previous()}), and reads through — its own
 * first, then the parent's. Nothing is copied, so a value inherited from the parent is simply absent from
 * {@code current()}.
 * <p>
 * That last property is the whole point of the shape, because it makes <b>release structural</b>:
 * {@link #close()} closes {@code current()} and nothing else, so a resource shared with the parent cannot be
 * closed by mistake. Ownership is object identity — there is no counter to increment in the wrong order and no
 * "who closed it first" race. It is also what fixes the memory spill: a frame's writes stay in the frame.
 * <p>
 * Only the <em>accumulating</em> components take this shape — {@link studio.phaseshift.metatron.isa.m.type.InstSet}
 * (ISA), memory and network. The <em>constructive</em> components (compiler, processor) are whole-value overrides
 * instead: the Machine supplies methods for constructing them, so a frame either inherits the parent's
 * construction or supplies its own, with nothing to read through.
 * <p>
 * <b>Implementors must not use {@code Obj.orElse(Obj)} for the parent fallback.</b> It takes a value, so
 * {@code current().read(p).orElse(parent().read(p))} evaluates the parent <em>unconditionally</em> and searches it
 * on every hit — the wrong default on the resolution path.
 *
 * @param <T> the machine component being wrapped
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface ComponentUnion<T extends Machine.Component> {

    /**
     * the component this frame inherited — never closed by this frame, never written to.
     * <p>
     * Named {@code previous} rather than {@code parent} deliberately: {@code parent()} is a core {@code Obj}
     * method meaning the <em>tree</em> parent, and {@link Machine.Component#machine()} is written in terms of it.
     * A union that overrode {@code parent()} with this meaning would make every {@code machine()} call traverse
     * the component chain looking for a space tree, and return the zero machine — silently, because a covariant
     * return type makes the override compile with no friction at all.
     * <p>
     * <b>It is a rec entry, not a field.</b> A union keeps its inherited side under {@link
     * studio.phaseshift.metatron.Tokens#PREVIOUS}, so {@code >>previous} reaches it from mtron and this accessor
     * reads the same place mtron does — there is one source of truth, and it cannot disagree with itself. Holding
     * it in a Java field would put the value somewhere the language that composes these machines cannot see.
     */
    T previous();

    /**
     * the component this frame introduced — the only thing this frame writes to or closes
     */
    T current();

    /**
     * release only what this frame owns. A value inherited from the parent is not in {@link #current()}, so it
     * cannot be reached from here, and that is the guarantee rather than a convention.
     */
    default void close() {
        CommonUtil.close(this.current());
    }
}
