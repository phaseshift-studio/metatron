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

package studio.phaseshift.metatron.isa.mach.type.machine;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class IteratorMachine extends AbstractMachine {

    protected Supplier<Iterator<Obj>> start;
    protected List<Inst> resolvedInsts;
    protected List<Iterator<Obj>> chainedIterators;

    public IteratorMachine(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.resolvedInsts = new ArrayList<>();
        this.chainedIterators = new ArrayList<>();
    }


    @Override
    public Machine clone(Object jvm, fURI tid, fURI vid) {
        return null;
    }

    @Override
    public Map<Obj, Obj> jvm() {
        return null;
    }

    @Override
    public Machine onHalt(final Consumer<Obj> halted) {
        return null;
    }

    @Override
    public Consumer<Obj> onHalt() {
        return null;
    }

    @Override
    public fURI tid() {
        return null;
    }

    @Override
    public fURI vid() {
        return null;
    }

    @Override
    public Machine resolve(final Obj lhs) {
        Obj running = lhs;
        this.chainedIterators = new ArrayList<>();
        this.chainedIterators.add(IteratorUtil.of());
        for (final Inst inst : this.insts()) {
            final Inst rinst = inst.resolve(running);
            this.resolvedInsts.add(rinst);
            running = rinst.rng();
        }
        return this;
    }

    @Override
    public Obj clone() {
        return null;
    }

    @Override
    public <O extends Obj> O self(Object jvm, fURI tid, fURI vid) {
        return null;
    }

    @Override
    public Obj apply(final Obj lhs) {
        this.chainedIterators.add(lhs.iterator());
        for (final Inst inst : this.resolvedInsts) {
            this.chainedIterators.add(IteratorUtil.flatMap(this.chainedIterators.getLast(), o -> inst.apply(o).iterator()));
        }
        final Iterator<Obj> last = this.chainedIterators.getLast();
        return objs(last);
    }
}
