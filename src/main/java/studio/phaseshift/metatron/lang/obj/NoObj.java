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
import studio.phaseshift.metatron.lang.obj.mtron.c.cInt;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Iterator;

import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public final class NoObj implements Obj, Inst {

    private static final NoObj SINGLE = new NoObj();
    private static final int HASHCODE = 632862684;

    private NoObj() {
        // singleton
    }

    public static NoObj single() {
        return NoObj.SINGLE;
    }

    @Override
    public Triplet jvm() {
        return null;
    }

    @Override
    public Poly args() {
        return lst();
        //throw MTronException.of("%s has no accessible arguments", this);
    }

    @Override
    public fURI tid() {
        return fURI.NOOBJ.zero();
    }

    @Override
    public Obj apply(final Obj lhs) {
        return this;
    } // TODO: should resolve to noobj (thus, like all other mono objs)

    @Override
    public fURI vid() {
        return fURI.NOOBJ;
    }

    @Override
    public NoObj clone(final Object jvm, final fURI tid, final fURI vid) {
        return this;
    }

    @Override
    public NoObj vid(final fURI furi) {
        return this;
    }

    @Override
    public Obj append(final Obj obj) {
        return obj;
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
        return other instanceof Obj && ((Obj) other).isNoObj();
    }

    @Override
    public String toString() {
        return Helper.objToString(this);
    }

    @Override
    public NoObj clone() {
        return SINGLE;
    }

    @Override
    public cInt uniqueC() {
        return cInt.of(0L);
    }

    @Override
    public NoObj c(final cInt c) {
        return this;
    }

    @Override
    public NoObj tid(final fURI tid) {
        return this;
    }

    @Override
    public Call plus(final Call rhs) { // a no-op branch
        return rhs;
    }

    @Override
    public Call mult(final Call rhs) { // a no-op sink
        return this;
    }

    @Override
    public Type rng() {
        return NoObj.single().type();
    }
}
