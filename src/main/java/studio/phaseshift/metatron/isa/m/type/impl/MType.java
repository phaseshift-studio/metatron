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

package studio.phaseshift.metatron.isa.m.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import static studio.phaseshift.metatron.isa.m.mInstSet.BASE_TYPES;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;


public class MType extends MObj implements Type {

    protected MType(final Tuple.Pair<Call, Call> jvm, final fURI tid, final fURI vid) {
        super(jvm, vid == null ?
                tid.equals(NOOBJ_TID) ? tid.c("0") : tid :
                vid.equals(NOOBJ_TID) ? vid.c("0").big() :
                        vid.big(), null == vid ? null : vid.big()); // TODO: hack until vid/tid pattern is replaced throughout
    }

    public static Type T(final Tuple.Pair<Call, Call> jvm, final fURI tid, final fURI vid) {
        return new MType(jvm, tid, vid);
    }


    public static Type T(final fURI tid) {
        final fURI bigTID = tid.big();
        if (!bigTID.hasPattern() && !BASE_TYPES.contains(bigTID.basePath()) && !bigTID.isGeneric() && Router.loaded()) {
            final Obj obj = Router.global().read(bigTID);
             //if(obj.isNoObj())
             //throw MTronException.of("type not found: %s", bigTID);
            if (obj.isType())
                return bigTID.cV().equals(obj.c()) ?
                        obj.asType() : // type already exists (don't create a new coefficient type)
                        new MType(Tuple.Pair.with(obj.asType().predicate(), obj.asType().constructor()), bigTID, null); // coefficient specific type doesn't exist, create it
        }
        return new MType(Tuple.Pair.with(null, null), bigTID, null);
        
       /*return (tid.hasPattern() ||
                !Router.loaded() ||
                Router.global().read(tid).isNoObj() ||
                Router.global().read(tid).isObjs() ||
                Router.global().read(tid).isCall() ||
                !Router.global().read(tid).isType()) ?
                new MType(Tuple.Pair.with(null, null), tid) : Router.global().read(tid).tid(tid).as();*/
    }

    public static Type T(final fURI tid, final Call predicate) {
        return new MType(Tuple.Pair.with(predicate, null), tid, null);
    }

    public static Type T(final fURI tid, final Call predicate, final Call constructor) {
        final fURI bigTID = tid.big();
        final Obj prev = Router.loaded() ? Router.readFromSpace(bigTID) : noobj();
        if (prev.isNoObj() || !prev.isType())
            return new MType(Tuple.Pair.with(null == predicate || predicate.isNoObj() ? null : predicate, null == constructor || constructor.isNoObj() ? null : constructor), bigTID, null);
        else {
            final Call pre = null == predicate || predicate.isNoObj() ? prev.<Type>as().predicate() : predicate;
            final Call con = null == constructor || constructor.isNoObj() ? prev.<Type>as().constructor() : constructor;
            return new MType(Tuple.Pair.with(pre, con), bigTID, bigTID);
        }
    }

    @Override
    public Type clone(final Object jvm, final fURI tid, final fURI vid) {
        // if (!tid.equals(vid))
        //     throw MTronException.of("a tid and vid of a type must be the same: %s != %s", tid, vid);
        return new MType((Tuple.Pair<Call, Call>) jvm, tid, vid);
    }

    @Override
    public Tuple.Pair<Call, Call> jvm() {
        return (Tuple.Pair<Call, Call>) this.jvm;
    }

}