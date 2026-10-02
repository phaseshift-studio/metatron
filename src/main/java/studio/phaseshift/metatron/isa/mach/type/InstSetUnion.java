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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The ISA of a frame: this frame's instructions read through to the ones it inherited.
 * <p>
 * A frame's {@link #current()} holds <em>only</em> what the frame introduced — the union of the parent's ISA with
 * an imported one is not materialized anywhere. A lookup that misses here falls through to the parent, so a name
 * is visible for exactly as long as the frame that introduced it is on the stack.
 * <p>
 * Writes go to {@link #current()} only, which is what makes an import frame-local: today
 * {@code InstSet.importInstSetStream} does {@code Machine.current().addSpace(isa)} — the global router,
 * unconditionally and permanently. {@link #close()} is inherited from {@link ComponentUnion} and releases only
 * what this frame owns, so popping a frame removes the names it introduced and leaves the parent's intact. An
 * ISA's close is not a no-op either: {@code AbstractInstSet.close()} unregisters the short-name redirects the ISA
 * registered on write, which is exactly the cleanup a frame wants.
 * <p>
 * Note that {@link #read} is consulted by the <b>Compiler</b>, not the Processor — resolution is the Compiler's
 * job and the Processor does not re-resolve — so this sits on the compilation path rather than the evaluation hot
 * path.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class InstSetUnion extends AbstractInstSet implements ComponentUnion<InstSet> {

    private final InstSet previous;
    private final InstSet current;

    /**
     * @param previous the ISA this frame inherited
     * @param current  the ISA this frame introduced — the only one it writes to or closes
     */
    public InstSetUnion(final InstSet previous, final InstSet current) {
        // the inherited tables are unused: every accessor below delegates. Constructing an InstSet is not
        // auto-registering (AbstractSpace declines to register InstSets), so this wrapper stays off the Router.
        super(true);
        this.previous = null == previous ? InstSet.instset0() : previous;
        this.current = null == current ? InstSet.instset0() : current;
    }

    @Override
    public InstSet previous() {
        return this.previous;
    }

    @Override
    public InstSet current() {
        return this.current;
    }

    /**
     * frame first, then parent — and <b>lazily</b>. {@code Obj.orElse(Obj)} takes a value, so
     * {@code current().read(p).orElse(parent().read(p))} would search the parent on every hit; on the resolution
     * path that is the wrong default.
     */
    @Override
    public Obj read(final fURI pattern) {
        final Obj found = this.current.read(pattern);
        return found.isNoObj() ? this.previous.read(pattern) : found;
    }

    /**
     * a frame writes only what it introduced
     */
    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return this.current.write(vid, obj);
    }

    /**
     * Release what this frame introduced, and nothing else.
     * <p>
     * This override is load-bearing rather than decorative: {@code AbstractInstSet} declares its own
     * {@code close()}, and an inherited <b>class method shadows an interface default</b> — so without this,
     * {@link ComponentUnion#close()} would never run, the frame's ISA would never be released, and the union
     * would silently leak everything it introduced.
     */
    @Override
    public void close() {
        ComponentUnion.super.close();
    }

    /**
     * the frame's own entries first, so a frame's instruction wins over the parent's of the same name
     */
    @Override
    public Set<Inst> insts() {
        return this.union(this.previous.insts(), this.current.insts());
    }

    @Override
    public Set<Inst> rewrites() {
        return this.union(this.previous.rewrites(), this.current.rewrites());
    }

    @Override
    public Set<Type> types() {
        return this.union(this.previous.types(), this.current.types());
    }

    @Override
    public Set<Obj> consts() {
        return this.union(this.previous.consts(), this.current.consts());
    }

    private <O> Set<O> union(final Set<O> parent, final Set<O> current) {
        final Set<O> union = new LinkedHashSet<>(current);
        union.addAll(parent);
        return union;
    }
}
