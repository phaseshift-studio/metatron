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


import studio.phaseshift.metatron.algebra.Semiring;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.mtron.MRel;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mtron.MObjs.objs;
import static studio.phaseshift.metatron.lang.obj.mtron.MUri.uri;
import static studio.phaseshift.metatron.util.Tuple.Pair;

public interface Rec extends Poly, Semiring<Rec> {

    @Override
    Rec clone(final Object value, final fURI tid, final fURI vid);

    @Override
    Map<Obj, Obj> value();

    @Override
    default long count() {
        return this.value().size();
    }

    @Override
    default Stream<Rel> stream() {
        return IteratorUtil.stream(this.elements());
    }


    @Override
    default Iterable<Rel> elements() {
        return () -> this
                .value()
                .entrySet()
                .stream()
                .map(kv -> (Rel) new MRel(Pair.with(kv.getKey(), kv.getValue()))).iterator();
    }

    @Override
    default Rec value(final Object newValue) {
        return this.clone(newValue, this.tid(), this.vid());
    }

    default Rec at(final Obj key, final Obj value) {
        final Map<Obj, Obj> newMap = new LinkedHashMap<>();
        newMap.putAll(this.recValue());
        if (value.isNoObj())
            newMap.remove(key);
        else
            newMap.put(key, value);
        return this.clone(newMap, this.tid(), this.vid());
    }

    @Override
    default <O extends Obj> O at(final Obj key) {
        if (key.isUri()) {
            fURI k = key.uriValue();
            Obj value = this.recValue().getOrDefault(key, NoObj.single());
            if (!value.isNoObj())
                return (O) value;
            int steps = 0;
            for (final String segment : k.segments()) {
                final fURI segmentF = f(segment);
                steps++;
                Graphitty.log(this).trace("searching for %s in %s", segment, this);
                if (segmentF.hasPattern()) {
                    if (segmentF.equals(fURI.ALL))
                        return (O) (k.isBranch() ? MRel.of(k.asNode().toUri(), objs(this.recValue().values())) : objs(this.recValue().values()));
                    else {
                        final int stepp = steps;
                        return (O) objs(objs(this.recValue().values().stream().flatMap(v -> {
                                    if (v.isRec()) {
                                        return v.<Rec>as().at(k.pretract(stepp).toUri()).stream();
                                    } else {
                                        return stepp == k.segments().size() ? Stream.of(v) : Stream.of(NoObj.single());
                                    }
                                })
                                .filter(v -> !v.isNoObj())
                                .map(v -> k.isBranch() ? MRel.of(k.asNode().toUri(), v) : v).toList()));
                    }
                } else {
                    value = this.recValue().getOrDefault(fURI.of(segment).toUri(), NoObj.single());
                    // if (steps == k.segments().size())
                    //   return (O) (k.isBranch() ? MRel.of(k.asNode().toUri(), value) : value);
                    if (steps == k.segments().size())
                        return (O) (k.isBranch() ? MRel.of(k.asNode().toUri(), value) : value);
                    else if (value.isRec()) {
                        return value.<Rec>as().at(k.pretract(steps).toUri());
                    }
                }
            }
        }
        return (O) this.recValue().getOrDefault(key, NoObj.single());
    }

    ///  TODO: GOT TIRED --- THIS IS A NASTY ALGORITHM.
    //SS  final Obj value = this.value().get(key);
      /*  if (null != value) {
            return (O) value;
        } else if (!key.isUri() || key.uriValue().segments().size() == 1)
            return (O) NoObj.single();
        else {
            Map<Obj, Obj> match = new LinkedHashMap<>();
         /*   final Obj v2 = this.value().get(key.uriValue().head(1));
            if(null != v2)
                match.put(key.uriValue(),v2);
            else
          */


/*        }
        return (O) this.value().getOrDefault(key, NoObj.single());
    }*/
    default <O extends Obj> O at(final String key) {
        return this.at(uri(key));
    }

    Rec put(final Obj key, final Obj value);

    default Rec put(final String key, final Obj value) {
        return this.put(uri(key), value);
    }

    @Override
    default Obj append(final Obj obj) {
        //  return obj.choose(Obj::isNoObj, o -> this, os -> this.value(os.stream().collect(Collectors.toMap(o -> o.relValue().getValue0(), o -> relValue().getValue1(), (a, b) -> b, LinkedHashMap<Obj, Obj>::new));
        if (obj.isNoObj())
            return this;
        final Map<Obj, Obj> map = new LinkedHashMap<>(this.recValue());
        obj.stream().forEach(o -> map.compute(o.relValue().get0(), (k, v) -> null == v ? o.relValue().get1() : v.append(o.relValue().get1())));
        return this.value(map);
        //return this.value(obj.stream().collect(Collectors.toMap(o->o.relValue().getValue0(),o->relValue().getValue1(),(a,b)->a.append(b),LinkedHashMap<Obj,Obj>::new)));
        //return this.value(map);
    }
}