/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.obj.base;

import org.javatuples.Triplet;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.mtron.MLst;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;


public interface InstSet extends Obj {

    public static final fURI TID = fURI.of("/mtron/inst_set");

    @Override
    InstSet clone(final Object value, final fURI tid, final fURI vid);

    @Override
    Map<fURI, Map<fURI, Set<Inst>>> value();


    default Inst resolve(final Obj lhs, final Inst instA) {
        return this.value().entrySet().stream().filter(kv -> instA.tid().matches(kv.getKey()))
                .map(Map.Entry::getValue)
                .flatMap(m -> m.entrySet().stream())
                .filter(kv -> lhs.tid().matches(kv.getKey()))
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream)
                .filter(i -> i.args().count() == instA.args().count())
                .map(i -> {
                    final List<Obj> resolvedArgs = new ArrayList<>();
                    //final boolean blocking = false; // i.isBlocking();
                    for (int j = 0; j < i.args().count(); j++) {
                        //  resolvedArgs.add(i.arg(j).apply(instA.arg(j)));
                        resolvedArgs.add(instA.arg(j));
                    }
                    return i.clone(new Triplet<>(MLst.of(resolvedArgs),
                            i.f(), i.seed()), i.tid(), instA.vid());
                }).findFirst().orElseThrow(() -> MTronException.of("unable to resolve %s in instruction set %s", instA, this));
    }
}
