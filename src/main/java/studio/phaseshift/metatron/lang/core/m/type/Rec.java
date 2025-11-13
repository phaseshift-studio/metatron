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

package studio.phaseshift.metatron.lang.core.m.type;


import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.obj.NoObj;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

public interface Rec extends Poly, PlusMonoid.O<Rec> {

    @Override
    default Stream<Rel> indexedStream() {
        return this.jvm().entrySet().stream().map(kv -> rel(kv.getKey(), kv.getValue()).c(this.c()).as());
    }

    @Override
    default Rec zero() {
        return rec();
    }

    @Override
    Rec clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    Map<Obj, Obj> jvm();

    @Override
    default long count() {
        return this.jvm().size();
    }

    @Override
    default Stream<Rel> elements() {
        return this.recValue().entrySet().stream().map(kv -> rel(kv.getKey(), kv.getValue()).c(c -> c.mult(this.c())).as());
    }

    @Override
    default Rec jvm(final Object jvm) {
        return this.clone(jvm, this.tid(), this.vid());
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
    default boolean matches(final Obj rhs) {
        if (rhs.isRec()) {
            return rhs.recValue().entrySet().stream().allMatch(r ->
                    this.recValue().entrySet().stream().anyMatch(l -> {
                        if (l.getKey().matches(r.getKey()))
                            return l.getValue().matches(r.getValue());
                        else return false;
                    }));
        } else {
            return Poly.super.matches(rhs);
        }
    }

    @Override
    default <O extends Obj> O at(final Obj key) {
        if (!key.isUri())
            return (O) this.jvm().getOrDefault(key, NoObj.noobj());
        else {
            final String step = key.uriValue().segments().get(0);
            Obj result;
            final Uri asNode = uri(key.uriValue().asNode());
            if (this.recValue().containsKey(asNode))
                return (O) (key.uriValue().isBranch() ? rel(asNode, this.recValue().get(asNode)) : this.recValue().get(asNode));
            if (step.equals("+") || step.equals("#")) {
                result = key.uriValue().isBranch() ? objs(this.recValue().entrySet().stream().map(kv -> rel(kv.getKey(), kv.getValue()))) : objs(this.recValue().values());
            } else {
                final Obj temp = this.jvm().getOrDefault(uri(step), NoObj.noobj());
                result = key.uriValue().isBranch() ? rel(key.uriValue().asNode().toUri(), temp) : temp;
            }
            if (key.uriValue().segments().size() == 1) {
                return (O) result;
            } else {
                final fURI nextKey = key.uriValue().isBranch() ? key.<Uri>as().uriValue().pretract().asBranch() : key.<Uri>as().uriValue().pretract();
                return (O) objs(IteratorUtil.stream(result.iterator()).filter(Obj::isPoly).map(r -> r.<Poly>as().at(uri(nextKey))));
            }
        }
    }

    default <O extends Obj> O at(final String key) {
        return this.at(uri(key));
    }

    Rec put(final Obj key, final Obj value);

    default Rec put(final fURI key, final Obj value) {
        return this.put(uri(key), value);
    }

    default Rec put(final String key, final Obj value) {
        return this.put(uri(key), value);
    }

    @Override
    default Rec vid(final fURI vid) {
        return (Rec) Poly.super.vid(vid);
    }


    @Override
    default Rec tid(final fURI tid) {
        return (Rec) Poly.super.tid(tid);
    }

    @Override
    default Obj append(final Obj obj) {
        if (obj.isNoObj())
            return this;
        if (this.isNoObj())
            return obj;
        return objs(this, obj);
    }
}