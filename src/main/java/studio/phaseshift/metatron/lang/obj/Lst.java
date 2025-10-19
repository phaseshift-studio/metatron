/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.lang.obj.mtron.MInt.jnt;
import static studio.phaseshift.metatron.lang.obj.mtron.MObjs.objs;
import static studio.phaseshift.metatron.lang.obj.mtron.MUri.uri;

public interface Lst extends Poly {

    @Override
    Lst clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    List<Obj> jvm();

    @Override
    default long count() {
        return this.jvm().size();
    }

    default Lst add(final Obj obj) {
        final ArrayList<Obj> newList = new ArrayList<>(this.lstValue());
        newList.add(obj);
        return this.jvm(newList);
    }

    @Override
    default Iterable<Obj> elements() {
        return this.jvm();
    }

    default Lst at(final Obj key, final Obj value) {
        if (key.isInt()) {
            final ArrayList<Obj> newList = new ArrayList<>(this.lstValue());
            if (value.isNoObj())
                newList.remove(key.intValue().intValue());
            else
                newList.set(key.intValue().intValue(), value);
            return this.clone(newList, this.tid(), this.vid());
        } else if (key.isUri()) {
            final Int k = jnt(Long.parseLong(key.uriValue().segments().get(0)));
            if (key.uriValue().segments().size() == 1) {
                return this.at(k, value);
            } else {
                final Obj v = this.jvm().get(k.intValue().intValue());
                if (v.isPoly()) {
                    return this.at(k, v.<Poly>as().at(uri(key.<Uri>as().uriValue().pretract()), value));
                } else {
                    throw MTronException.of("unknown key value for lst: %s => %s", key, value);
                }
            }
        } else {
            throw MTronException.of("unknown key for lst: %s", key);
        }
    }

    @Override
    default <O extends Obj> O at(final Obj key) {
        if (key.isInt())
            return (O) this.jvm().get(key.<Int>as().intValue().intValue());
        else if (key.isUri()) {
            final String step = key.uriValue().segments().get(0);
            Obj result;
            if (step.equals("+") || step.equals("#")) {
                result = objs(this.elements());
            } else {
                final Int k = jnt(Long.parseLong(step));
                result = this.jvm().get(k.intValue().intValue());
            }
            if (key.uriValue().segments().size() == 1) {
                return (O) result;
            } else {
                return (O) objs(IteratorUtil.stream(result.iterator()).filter(Obj::isPoly).map(r -> r.<Poly>as().at(uri(key.<Uri>as().uriValue().pretract()))));
            }
        } else {
            throw MTronException.of("unknown key for lst: %s", key);
        }
    }

}