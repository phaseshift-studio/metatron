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

package studio.phaseshift.metatron.lang.mkv;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.msys.Space;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.impl.MRel;
import studio.phaseshift.metatron.space.MSpace;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.Common;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static studio.phaseshift.metatron.lang.msys.msysInstSet.SPACE_TID;


public class mkvSpace extends MSpace<Map<fURI, Obj>> implements Space {

    public static final fURI KVSPACE_TID = SPACE_TID.extend("kv");
    
    public mkvSpace(final fURI pattern, final fURI vid) {
        super(new HashMap<>(), pattern, KVSPACE_TID, vid);
    }

    public static mkvSpace of(final fURI pattern) {
        return new mkvSpace(pattern, fURI.NULL);
    }

    @Override
    public void close() {
        this.jvm().values().forEach(Common::close);
        super.close();
    }

    @Override
    public Obj read(final fURI vid) {
        return this.qs().processPreRead(vid, vid).orElseGet(() -> {
            Obj result = Space.Helper.resolveRead(this, vid.basePath(), directReader());
            //return result;
            return this.qs().processPostRead(vid, vid, result).orElse(result);
        });
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return this.qs().processPreWrite(vid, vid, obj).orElseGet(() -> {
            Space.Helper.resolveWrite(this, vid.basePath(), obj, this.directWriter(), this.directReader());
            //return obj;
            return this.qs().processPostWrite(vid, vid, obj).orElse(this.qs().processQlessWrite(vid, vid, obj).orElse(obj));
        });
    }

    @Override
    public Function<fURI, Map<fURI, Obj>> directReader() {
        return (pattern) -> {
            if (pattern.equals(fURI.ALL))
                return this.jvm();
            else {
                if (pattern.hasPattern()) {
                    final Map<fURI, Obj> partial = new LinkedHashMap<>();
                    this.jvm().forEach((key, value) -> {
                        if (key.matches(pattern.asNode()))
                            partial.put(key, value);
                        if (value.isPoly())
                            Space.Helper.unrollPoly(partial, key, value.as(), pattern.asNode());
                    });
                    return partial;
                } else {
                    final Obj value = this.jvm().get(pattern);
                    return null == value ? Map.of() : Map.of(pattern, value);
                }
            }
        };
    }

    @Override
    public BiConsumer<fURI, Obj> directWriter() {
        return (pattern, obj) -> {
            if (pattern.hasPattern()) {
                this.directReader().apply(pattern).forEach((key, value) -> this.write(key, obj));
            } else {
                if (obj.isNoObj()) {
                    final Obj current = this.jvm().get(pattern);
                    this.jvm().remove(pattern);
                    if (null != current)
                        Common.close(current);
                } else
                    this.jvm().put(pattern, obj);
            }
        };
    }
}
