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

package studio.phaseshift.metatron.struct;

import org.javatuples.Pair;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.Palette;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static studio.phaseshift.metatron.lang.obj.BObj.Obj;
import static studio.phaseshift.metatron.lang.obj.BObj.Poly;

public interface Struct extends Poly {

    @Override
    Object value();

    fURI pattern();

    Obj read(final fURI addr);

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

    default List<Pair<fURI, Obj>> generateWritePairs(final fURI addr, final BObj.Obj obj) {
        final List<Pair<fURI, Obj>> writes = new ArrayList<>();
        if (obj.isRec() && addr.isBranch()) {
            obj.recValue().forEach((key, value) -> {
                final fURI key2 = addr.extend(key.uriValue());
                if (value.isRec() && key2.isBranch()) {
                    writes.addAll(this.generateWritePairs(key2, value));
                } else
                    writes.add(Pair.with(key2, value));
            });
        } else {
            writes.add(Pair.with(addr, obj));
        }
        return writes;
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
