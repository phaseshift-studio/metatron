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

package studio.phaseshift.metatron.space;

import org.javatuples.Pair;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Poly;
import studio.phaseshift.metatron.lang.obj.mtron.MRec;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.Palette;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public interface Space extends Poly {

    @Override
    Object value();

    fURI pattern();

    default Obj read(final String vid) {
        return this.read(fURI.of(vid));
    }

    Obj read(final fURI vid);

    default Obj write(final String vid, final Obj obj) {
        return this.write(fURI.of(vid), obj);
    }

    Obj write(final fURI vid, final Obj obj);

    void append(final fURI addr, final Obj... obj);

    @Override
    default long count() {
        return 0;
    }

    @Override
    default List<Obj> elements() {
        return List.of();

    }

    @Override
    default Obj vid(final fURI vid) {
        throw new IllegalStateException("structs must umount to change value id (vid)");
    }

    default String toString(final Palette palette) {
        return Graphitty.string("{{b}}%s{{g}}:[{{y}}pattern{{g}}=>{{y}}%s{{g}}]{{X}}".formatted(this.tid().toString(), this.pattern().toString()));
    }

    // @Override
    // default Obj get(final int index) {
    //    return NoObj.of();
    // }

    default void resolveWrite(final fURI addr, final fURI stepAddr, final Obj obj, final BiConsumer<fURI, Obj> resolveWriter) {
        if (obj.isRec()) {
            obj.recValue().forEach((key, value) -> {
                final fURI nextStepAddr = stepAddr.extend(key.uriValue());
                // final fURI resolvedKey = addr.hasPattern() ? addr.extend(resolvedAddr) : extendedKey;
                if (value.isRec() && nextStepAddr.isBranch()) {
                    this.resolveWrite(addr, nextStepAddr, value, resolveWriter);
                } else if (value.isRec()) {
                    final Map<Obj, Obj> submap = new LinkedHashMap<>();
                    value.recValue()
                            .entrySet()
                            .stream()
                            .filter(kv -> nextStepAddr.extend(kv.getKey().uriValue()).matches(addr))
                            .forEach(kv -> submap.put(kv.getKey(), kv.getValue()));
                    resolveWriter.accept(nextStepAddr, new MRec(submap, value.tid(), fURI.NULL));
                } else if (nextStepAddr.matches(addr)) {
                    resolveWriter.accept(nextStepAddr, value);
                }
            });
        } else if (stepAddr.matches(addr)) {
            resolveWriter.accept(stepAddr, obj);
        }
    }


    default Optional<Pair<fURI, Poly>> locateBasePoly(final fURI furi, Predicate<Obj> polyFilter) {
        //if (furi.isAbsolute())
        //    return Optional.empty();
        if (null == polyFilter)
            polyFilter = o -> true;
        fURI newFuri = furi.asNode();
        Obj obj = NoObj.single();
        while (!newFuri.segments().isEmpty()) {
            obj = this.read(newFuri);
            if (!obj.isNoObj() && polyFilter.test(obj)) // obj->is_poly() || obj->is_objs() || obj->is_code())
                break;
            newFuri = newFuri.retract().asNode();
        }
        if (obj.isPoly()) {
            final Obj x = null; //obj.<Poly>as().get(newFuri);
            return x.isPoly() ? Optional.of(Pair.with(newFuri.retractPattern(), x.<Poly>as())) : Optional.empty();
        } else return Optional.empty();
    }

    default Iterator<Pair<fURI, Obj>> nodeBranchProcess(final fURI query, final Obj obj) {
        try {
            if (query.isBranch() && obj.isRec())
                return null;// obj.<Rec>as().get(query.retractPattern().asNode()).recValue().entrySet().stream().map(kv -> Pair.with(query.asNode().extend(kv.getKey().uriValue()), kv.getValue())).iterator();
        } catch (final Exception e) {
            Graphitty.log(this).info("%s is not valid for %s", query, obj);
        }
        return IteratorUtil.of(Pair.with(query, obj));
    }
}
