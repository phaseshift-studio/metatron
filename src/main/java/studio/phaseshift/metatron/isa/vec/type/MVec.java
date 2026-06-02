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

package studio.phaseshift.metatron.isa.vec.type;

//import jdk.incubator.vector.Vector;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.impl.MObj;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;

import java.util.Vector;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.vec.vecInstSet.VEC_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MVec<E> extends MObj implements Vec {

    public MVec(final Vector<E> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public Vector<E> jvm() {
        return this.jvm();
    }

    @Override
    public long count() {
        return this.jvm().size();
    }

    @Override
    public <O extends Obj> Stream<O> elements() {
        return this.jvm().stream().map(e -> (O) MObjFactory.single().toObj(e));
    }

    @Override
    public <O extends Obj> Stream<O> valueElements() {
        return this.elements();
    }

    @Override
    public Poly<?, ?> zero() {
        return new MVec<>(new Vector<>(this.stream().map(e -> real(0.0d)).toList()), VEC_TID, null);
    }

    @Override
    public <O extends Obj> O at(final Obj key) {
        return key.isInt() ? (O) this.jvm().elementAt(key.asInt().intValue().intValue()) : (O) noobj();
    }
}
