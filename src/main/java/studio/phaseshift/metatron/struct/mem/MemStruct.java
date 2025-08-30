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

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.SObj;
import studio.phaseshift.metatron.struct.Struct;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static studio.phaseshift.metatron.lang.obj.BObj.Obj;
import static studio.phaseshift.metatron.lang.obj.BObj.Poly;

public class MemStruct implements Struct {

    fURI vid;
    final Map<fURI, Obj> store = new HashMap<>();

    public MemStruct(final fURI vid) {
        this.vid = vid;
    }

    @Override
    public Map<fURI, Obj> value() {
        return this.store;
    }

    @Override
    public fURI tid() {
        return new fURI("mem:struct");
    }

    @Override
    public fURI vid() {
        return this.vid;
    }

    @Override
    public Obj vid(fURI furi) {
        this.vid = furi;
        return this;
    }

    @Override
    public MemStruct clone() {
        return this;
    }

    @Override
    public <O extends Obj> O clone(Object value) {
        return (O)this;
    }

    @Override
    public Obj read(final fURI addr) {
        return ObjUtil.orNoObj(store.get(addr));
    }

    @Override
    public void write(final fURI addr, Obj obj) {
        this.store.put(addr, obj);
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
