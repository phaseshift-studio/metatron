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
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.SObj;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.Palette;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static studio.phaseshift.metatron.lang.obj.BObj.Obj;
import static studio.phaseshift.metatron.lang.obj.BObj.Poly;

public interface Space extends Poly {

    @Override
    Object value();

    fURI pattern();

    default Obj read(final String addr) {
        return this.read(fURI.of(addr));
    }

    Obj read(final fURI addr);

    default Obj write(final String addr, final Obj obj) {
        return this.write(fURI.of(addr), obj);
    }

    Obj write(final fURI addr, final Obj obj);

    void append(final fURI addr, final Obj... obj);

    @Override
    default Obj vid(final fURI vid) {
        throw new IllegalStateException("structs must umount to change value id (vid)");
    }

    default String toString(final Palette palette) {
        return Graphitty.string("{{b}}%s{{g}}:[{{y}}pattern{{g}}=>{{y}}%s{{g}}]{{X}}".formatted(this.tid().toString(), this.pattern().toString()));
    }

    @Override
    default Obj clone() {
        throw new IllegalStateException(new CloneNotSupportedException("structs can not be cloned"));
    }

    @Override
    default Obj get(final int index) {
        return BObj.NoObj.of();
    }

    default void resolveWrite(final fURI addr, final fURI stepAddr, final BObj.Obj obj, final BiConsumer<fURI, BObj.Obj> resolveWriter) {
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
                    resolveWriter.accept(nextStepAddr, new SObj.Rec(submap, value.tid(), fURI.NONE));
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
        BObj.Obj obj = BObj.NoObj.of();
        while (!newFuri.segments().isEmpty()) {
            obj = this.read(newFuri);
            if (!obj.isNoObj() && polyFilter.test(obj)) // obj->is_poly() || obj->is_objs() || obj->is_code())
                break;
            newFuri = newFuri.retract().asNode();
        }
        if (obj.isPoly()) {
            final BObj.Obj x = obj.<Poly>as().get(newFuri);
            return x.isPoly() ? Optional.of(Pair.with(newFuri.retractPattern(), x.<Poly>as())) : Optional.empty();
        } else return Optional.empty();
    }

    default Iterator<Pair<fURI, Obj>> nodeBranchProcess(final fURI query, final BObj.Obj obj) {
        try {
            if (query.isBranch() && obj.isRec())
                return obj.<BObj.Rec>as().get(query.retractPattern().asNode()).recValue().entrySet().stream().map(kv -> Pair.with(query.asNode().extend(kv.getKey().uriValue()), kv.getValue())).iterator();
        } catch (final Exception e) {
            Graphitty.log(this).info("%s is not valid for %s", query, obj);
        }
        return IteratorUtil.of(Pair.with(query, obj));
    }
}
