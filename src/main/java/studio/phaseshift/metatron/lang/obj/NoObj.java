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

package studio.phaseshift.metatron.lang.obj;

import org.javatuples.Triplet;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.mtron.MObjs;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.Iterator;
import java.util.List;

import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.NOOBJ_TID;

public class NoObj implements Obj, Inst {

    private static final NoObj SINGLE = new NoObj();
    private static final int HASHCODE = 632862684;

    private NoObj() {
        // singleton
    }

    public static NoObj single() {
        return NoObj.SINGLE;
    }

    @Override
    public Triplet value() {
        return null;
    }

    @Override
    public fURI tid() {
        return NOOBJ_TID.coefficient("0");
    }

    @Override
    public fURI vid() {
        return fURI.NULL;
    }

    @Override
    public NoObj clone(final Object value, final fURI tid, final fURI vid) {
        return this;
    }

    @Override
    public NoObj vid(final fURI furi) {
        return this;
    }

    @Override
    public Objs append(Obj obj) {
        return MObjs.of(List.of());
    }

    @Override
    public Iterator<Obj> iterator() {
        return IteratorUtil.of();
    }

    @Override
    public int hashCode() {
        return HASHCODE;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof NoObj;
    }

    @Override
    public String toString() {
        return ObjUtil.objToString(this);
    }

   // @Override
   /* public Obj apply(final Obj lhs) {
        return lhs;
    }*/
}
