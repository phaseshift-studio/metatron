/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 *  Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.lang.core.m.obj;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.*;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.*;

import static studio.phaseshift.metatron.furi.q.DocQ.Doc.docWrap;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.lang.core.m.type.InstSet.A;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public final class NoObj implements Obj, Inst {

    private static final NoObj SINGLE = new NoObj();
    private static final int HASHCODE = 632862684;

    private NoObj() {
        // singleton
    }

    public static NoObj noobj() {
        return NoObj.SINGLE;
    }

    @Override
    public boolean isResolved(final boolean nested) {
        return true;
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
    public NoObj vid(final fURI vid) {
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
    public NoObj self(Object jvm, fURI tid, fURI vid) {
        return this;
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
    public f f() {
        return f.of(o -> NoObj.noobj());
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
        return NoObj.noobj().type();
    }

    @Override
    public fURI uriValue() {
        return fURI.NOOBJ;
    }

    @Override
    public Long intValue() {
        return 0L;
    }

    @Override
    public String strValue() {
        return "";
    }

    @Override
    public List<Obj> lstValue() {
        return List.of();
    }

    @Override
    public Double realValue() {
        return 0.0d;
    }

    public static final class NoObjType {
        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    docWrap(instC(START_INST_TID.dom(fURI.NOOBJ.zero()).rng(A.maybeSome()), lst(T(A.maybeSome())), (lhs, inst) -> inst.arg(0)),
                            "noobj", "initial objs", Map.of(jnt(0), "initial objs"), "the initial function f()->x")
            ));
        }
    }
}
