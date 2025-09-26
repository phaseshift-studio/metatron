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

import java.util.*;
import java.util.function.BiFunction;

import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;


public interface InstSet extends Space {

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
                        .query(fURI.DOM, TypefURI.orNone(inst.tid().queryValue(fURI.DOM, fURI.class)))
                        .query(fURI.RNG, TypefURI.orNone(inst.tid().queryValue(fURI.RNG, fURI.class))), inst.args(), (BiFunction) inst.f().func, inst.seed()));
        return obj;
    }

    @Override
    default void append(final fURI addr, final Obj... obj) {
        return;
    }

    default Inst resolve(final Obj lhs, final Inst instAorB) {
        return this.value().getOrDefault(instAorB.tid().basePath(), Map.of())
                .entrySet()
                .stream()
                .filter(kv -> {
                    final boolean pass = lhs.tid().matches(kv.getKey());
                    this.logger().trace("{{y}}dom{{/y}} filtering: %s => %s [%s]".formatted(lhs.tid(), kv.getKey(), lhs.tid().matches(kv.getKey())));
                    return pass;
                })
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream)
                .filter(i -> {
                    boolean pass = false;
                    if (instAorB.resolution() == Inst.Resolve.A && instAorB.args().isEmpty()) { // TODO: this is a hack as we are using args size as a determiner of resolution level
                        pass = true;
                    } else if (i.args().count() == instAorB.args().count()) {
                        pass = true;
                        for (int k = 0; k < i.args().count(); k++) {
                            final Obj originalArg = i.arg(k);
                            final Obj userArg = instAorB.arg(k);
                            if (!userArg.matches(originalArg)) {
                                pass = false;
                                break;
                            }
                        }
                    }
                    this.logger().trace("{{y}}args{{/y}} filtering: %s => %s(%s) [%s]", lhs.tid(), i,  instAorB.args(), pass);
                    return pass;
                })
                .map(i -> {
                    List<Obj> newArgs = new ArrayList<>();
                    for (int k = 0; k < i.args().count(); k++) {
                        final Obj originalArg = i.arg(k);
                        final Obj userArg = instAorB.arg(k);
                        if (false && originalArg.isType()) {
                            //if(userArg.isCall())
                            //  newArgs.add(userArg.<Call>as().rng(originalArg.<Type>as()));
                            //else
                            newArgs.add(userArg);
                            // newArgs.add(originalArg.value(userArg));
                            //newArgs.add(MInst.instB(MAP_TID, lst(userArg)).rng(originalArg.as()));
                        } else {
                            newArgs.add(userArg);
                        }
                    }
                    final Inst j = i.clone(
                            Triplet.with(lst(newArgs), i.f(), i.seed()),
                            i.tid().query(fURI.DOM, lhs.tid()), instAorB.vid());
                    this.logger().trace("{{y}}inst{{/y}} resolution: %s => %s [%s]", lhs, j, i);
                    return j;
                })
                .findFirst()
                .orElseThrow(() -> this.logger().except("unable to resolve %s => %s in instruction set %s", lhs, instAorB, this.value().get(instAorB.tid())));
    }
}
