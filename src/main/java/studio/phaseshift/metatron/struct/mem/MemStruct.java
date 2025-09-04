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

package studio.phaseshift.metatron.struct.mem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.Palette;
import studio.phaseshift.metatron.lang.obj.SObj;
import studio.phaseshift.metatron.struct.Struct;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static studio.phaseshift.metatron.lang.obj.BObj.Obj;
import static studio.phaseshift.metatron.lang.obj.BObj.Poly;

public class MemStruct extends SObj.Rec implements Struct {
    private static final Logger LOG = LoggerFactory.getLogger(MemStruct.class);
    public static final fURI MEMSTRUCT_TID = fURI.of("struct:/mtron/mem");
    private final fURI pattern;

    final Map<fURI, Obj> store = new HashMap<>();

    public MemStruct(final fURI pattern, final fURI vid) {
        super(Map.of(), MEMSTRUCT_TID, vid);
        this.pattern = pattern;
        // this.config = config;
        LOG.info(Graphitty.global().parse("%s loaded at %s !g[!yaddr!g=>!!%s!g]!!\n".formatted(tid().toUri(true), SObj.Uri.of(this.vid), SObj.Uri.of(this.pattern))));
    }

    @Override
    public String toString() {
        return this.toString(Palette.GLOBAL);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.vid, this.pattern, this.tid());
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof MemStruct && this.hashCode() == other.hashCode();
    }

    @Override
    public Map<Obj, Obj> value() {
        return Map.of();
    }

    @Override
    public fURI pattern() {
        return this.pattern;
    }


    @Override
    public Obj read(final fURI addr) {
        return ObjUtil.orNoObj(store.get(addr));
    }

    @Override
    public Obj write(final fURI addr, Obj obj) {
        final Obj current = this.store.get(addr);
        if (null == current) {
            this.store.put(addr, obj);
            return obj;
        } else {
            final Obj next = current.apply(obj);
            this.store.put(addr, next);
            return next;
        }
    }

    @Override
    public void append(final fURI addr, final Obj... obj) {
        Obj poly = this.store.get(addr);
        if (null == poly || poly.isMono())
            this.store.put(addr, new SObj.Objs(Arrays.asList(obj)));
        else {
            Poly ppoly = (Poly) poly;
            ppoly.append(obj);
        }
    }


}
