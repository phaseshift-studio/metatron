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

package studio.phaseshift.metatron.lang.db.vec.type.impl;

import jdk.incubator.vector.DoubleVector;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;

import java.util.stream.Stream;


/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MRealVec extends MVec<Double> {

    public MRealVec(final DoubleVector value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

  /*  public MRealVec vec(final Lst lst) {
        final double[] array = new double[lst.lstValue().size()];
        for (int i = 0; i < lst.lstValue().size(); i++) {
            array[i] = lst.lstValue().get(i).realValue();
        }
        return new MRealVec(DoubleVector.fromArray(DoubleVector.SPECIES_64, array, 0), RVEC_TID, fURI.NULL);
    }*/

    @Override
    public DoubleVector jvm() {
        return (DoubleVector) this.jvm;
    }

    @Override
    public long count() {
        return this.jvm().length();
    }

    @Override
    public <O extends Obj> Stream<O> elements() {
        return Stream.empty();
    }

    @Override
    public <O extends Obj> O at(Obj key) {
        return null;
    }
}
