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

package studio.phaseshift.metatron.lang.core.m.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Call;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.util.Tuple;

import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;


public class MType extends MObj implements Type {

    private MType(final Tuple.Pair<Obj, Obj> jvm, final fURI tid) {
        super(jvm, tid.equals(NOOBJ_TID) ? tid.c("0") : tid, tid);
    }

    public static Type T(final Type type) {
        return new MType(Tuple.Pair.with(type,null),TYPE_TID);
    }
 
    public static Type T(final fURI tid) {
        final fURI bigtid = tid.big();
        if (!bigtid.hasPattern() && !BASE_TYPES.contains(bigtid.basePath()) && !bigtid.isGeneric() && Router.loaded()) {
            final Obj obj = Router.global().read(bigtid);
            if (obj.isType())
                return obj.c(bigtid.cV()).as();
        }
        return new MType(Tuple.Pair.with(null, null), bigtid);
        
       /*return (tid.hasPattern() ||
                !Router.loaded() ||
                Router.global().read(tid).isNoObj() ||
                Router.global().read(tid).isObjs() ||
                Router.global().read(tid).isCall() ||
                !Router.global().read(tid).isType()) ?
                new MType(Tuple.Pair.with(null, null), tid) : Router.global().read(tid).tid(tid).as();*/
    }

    public static Type T(final fURI tid, final Obj predicate) {
        return new MType(Tuple.Pair.with(predicate, null), tid);
    }

    public static Type T(final fURI tid, final Obj predicate, final Obj constructor) {
        if(null == predicate && null == constructor)
            return T(tid);
        final Obj prev = Router.loaded() ? Router.readFromSpace(tid) : noobj();
        if (prev.isNoObj() || !prev.isType())
            return new MType(Tuple.Pair.with(null == predicate || predicate.isNoObj() ? null : predicate, null == constructor || constructor.isNoObj() ? null : constructor), tid);
        else {
            final Obj pre = null == predicate || predicate.isNoObj() ? prev.<Type>as().predicate() : predicate;
            final Obj con = null == constructor || constructor.isNoObj() ? prev.<Type>as().constructor() : constructor;
            return new MType(Tuple.Pair.with(pre, con), tid);
        }
    }

    @Override
    public Type clone(final Object jvm, final fURI tid, final fURI vid) {
        // if (!tid.equals(vid))
        //     throw MTronException.of("a tid and vid of a type must be the same: %s != %s", tid, vid);
        return new MType((Tuple.Pair<Obj, Obj>) jvm, tid);
    }

    @Override
    public Tuple.Pair<Obj, Obj> jvm() {
        return (Tuple.Pair<Obj, Obj>) this.jvm;
    }
}
