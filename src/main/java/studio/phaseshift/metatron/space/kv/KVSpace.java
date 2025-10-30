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

package studio.phaseshift.metatron.space.kv;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.impl.MRel;
import studio.phaseshift.metatron.space.MSpace;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.MTRON_SPACE_TID;


public class KVSpace extends MSpace<Map<fURI, Obj>> implements Space {

    public static final fURI KVSPACE_TID = MTRON_SPACE_TID.extend("kv");
    protected final GraphittyLogger LOG = Graphitty.log(this);

    private final Function<fURI, Map<fURI, Obj>> directReader = (pattern) -> {
        if (pattern.equals(fURI.ALL))
            return this.jvm;
        else {
            if (pattern.hasPattern()) {
                return this.jvm
                        .entrySet()
                        .stream()
                        .map(kv -> {
                            Map<fURI, Obj> partial = new LinkedHashMap<>();
                            if (kv.getKey().matches(pattern.asNode()))
                                partial.put(kv.getKey(), kv.getValue());
                            if (kv.getValue().isPoly())
                                Space.Helper.unrollPoly(partial, kv.getKey(), kv.getValue().as(), pattern.asNode());
                            return partial;
                        }).reduce(new LinkedHashMap<>(), (a, b) -> {
                            a.putAll(b);
                            return a;
                        });
            } else {
                final Obj value = this.jvm.get(pattern);
                return null == value ? Map.of() : Map.of(pattern, value);
            }
        }
    };
    private final BiConsumer<fURI, Obj> directWriter = this.jvm::put;

    public KVSpace(final fURI pattern, final fURI vid) {
        super(new HashMap<>(), pattern, KVSPACE_TID, vid);
    }

    public static KVSpace of(final fURI pattern, final fURI vid) {
        return new KVSpace(pattern, vid);
    }


    @Override
    public Obj read(final fURI vid) {
        return this.qs().processPreRead(vid, vid).orElseGet(() -> {
            Obj result = Helper.resolveRead(this, vid, directReader);
            return this.qs().processPostRead(vid, vid, result).orElse(result);
        });
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return this.qs().processPreWrite(vid, vid, obj).orElseGet(() -> {
            Helper.resolveWrite(this, vid.basePath(), obj, this.directWriter, this.directReader);
            return this.qs().processPostWrite(vid, vid, obj).orElse(this.qs().processQlessWrite(vid, vid, obj).orElse(obj));
        });
    }

    @Override
    public Function<fURI, Map<fURI, Obj>> directReader() {
        return this.directReader;
    }

    @Override
    public BiConsumer<fURI, Obj> directWriter() {
        return this.directWriter;
    }

    /*@Override
    public long count() {
        return this.jvm.size();
    }*/

    @Override
    public Iterator<Obj> iterator() {
        return this.jvm.entrySet().stream().map(kv -> MRel.of(kv.getKey().toUri(), kv.getValue())).map(r -> (Obj) r).iterator();
    }
}
