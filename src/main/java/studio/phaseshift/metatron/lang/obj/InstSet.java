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

package studio.phaseshift.metatron.lang.obj;

import org.javatuples.Triplet;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.base.furi.TypefURI;
import studio.phaseshift.metatron.lang.obj.mtron.MInst;
import studio.phaseshift.metatron.lang.obj.mtron.MLst;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.function.BiFunction;

import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.BLOCK_TID;


public interface InstSet extends Space {

    @Override
    default InstSet clone(final Object value, final fURI tid, final fURI vid) {
        return this;
    }

    @Override
    Map<fURI, Map<fURI, Set<Inst>>> value();


    @Override
    fURI pattern();

    @Override
    default Obj read(final fURI vid) {
        List<Inst> result = this.value().getOrDefault(vid.basePath(), Map.of())
                .entrySet()
                .stream()
                .filter(kv -> {
                    // Graphitty.stdout().println("%s matches %s = %s".formatted(lhs.tid().queryless(),kv.getKey().queryless(),lhs.tid().queryless().matches(kv.getKey().queryless())));
                    return vid.queryValue(fURI.DOM, fURI.class).basePath().matches(kv.getKey().basePath());
                }).flatMap(kv -> kv.getValue().stream()).toList();
        if (result.isEmpty())
            return NoObj.single();
        return MLst.of((List) result);
    }

    @Override
    default Obj write(final fURI vid, final Obj obj) {
        final Inst inst = obj.<Inst>as();
         this.value().computeIfAbsent(inst.tid().basePath(), k -> new LinkedHashMap<>())
                .computeIfAbsent(inst.tid().queryValue(fURI.DOM, fURI.class), k -> new LinkedHashSet<>())
                .add(MInst.instC(inst.tid()
                        .query(fURI.DOM, TypefURI.orNone(inst.tid().queryValue(fURI.DOM,fURI.class)))
                        .query(fURI.RNG, TypefURI.orNone(inst.tid().queryValue(fURI.RNG,fURI.class))), inst.args(), (BiFunction) inst.f().func, inst.seed()));
        return obj;
    }

    @Override
    default void append(fURI addr, Obj... obj) {
        return;
    }

    default Inst resolve(final Obj lhs, final Inst instAorB) {
        return this.value().getOrDefault(instAorB.tid().basePath(),Map.of())
                .entrySet()
                .stream()
                .filter(kv -> {
                    //Graphitty.stdout().println("%s matches %s = %s".formatted(lhs.tid(),kv.getKey(),lhs.tid().matches(kv.getKey())));
                    return lhs.tid().matches(kv.getKey()) || lhs.tid().basePath().equals(fURI.of("#"));
                })
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream)
                .filter(i -> (instAorB.resolution() == Inst.Resolve.A) || (i.args().count() == instAorB.args().count()))
                .map(i -> {
                    final List<Obj> resolvedArgs = new ArrayList<>();
                  //  final boolean blocking = instAorB.tid().basePath().equals(BLOCK_TID);
                    for (int j = 0; j < i.args().count(); j++) {
                        //  resolvedArgs.add(i.arg(j).apply(instA.arg(j)));
                        resolvedArgs.add(instAorB.arg(j));
                    }
                    return i.clone(new Triplet<>(MLst.of(resolvedArgs),
                            i.f(), i.seed()), i.tid(), instAorB.vid());
                }).findFirst().orElseThrow(() -> MTronException.of("unable to resolve %s => %s in instruction set %s", lhs, instAorB, this.value().get(instAorB.tid())));
    }
}
