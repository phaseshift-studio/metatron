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

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.mtron.MInst;
import studio.phaseshift.metatron.lang.obj.mtron.MLst;
import studio.phaseshift.metatron.space.Space;

import java.util.*;
import java.util.function.BiFunction;

import static studio.phaseshift.metatron.lang.obj.mtron.MObjs.objs;


public interface InstSet extends Space {

    @Override
    Map<fURI, Map<fURI, Set<Inst>>> value();


    @Override
    fURI pattern();

    default Objs types() {
        return objs(List.of());
    }

    @Override
    default Obj read(final fURI vid) {
        List<Inst> result = this.value().getOrDefault(vid.basePath(), Map.of())
                .entrySet()
                .stream()
                .filter(kv -> {
                    // Graphitty.stdout().println("%s matches %s = %s".formatted(lhs.tid().queryless(),kv.getKey().queryless(),lhs.tid().queryless().matches(kv.getKey().queryless())));
                    return vid.queryValue(fURI.DOM, fURI.class, fURI.ANY).basePath().matches(kv.getKey().basePath());
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
                        .query(fURI.DOM, inst.tid().queryValue(fURI.DOM, fURI.class))
                        .query(fURI.RNG, inst.tid().queryValue(fURI.RNG, fURI.class)), inst.args(), (BiFunction) inst.f().func, inst.seed()));
        return obj;
    }

    @Override
    default void append(final fURI addr, final Obj... obj) {
    }
}
