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
import studio.phaseshift.metatron.util.Tuple;

import java.util.Objects;

import static studio.phaseshift.metatron.isa.m.mInstSet.BASE_TYPES;


public class MType extends MObj implements Type {

    protected MType(final Tuple.Pair<Call, Call> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid.big(), null == vid ? null : vid.big());
    }

    public static Type T(final Tuple.Pair<Call, Call> jvm, final fURI tid, final fURI vid) {
        return T(tid, vid, jvm.get0(), jvm.get1());
    }


    public static Type T(final fURI tid) {
        return T(tid, null, null, null);
    }

    public static Type T(final fURI tid, final Call predicate) {
        return T(tid, null, predicate, null);
    }

    public static Type T(final fURI tid, final fURI vid, final Call predicate, final Call constructor) {
        final fURI bigTID = tid.big();
        final fURI bigVID = null == vid ? null : vid.big();
        if (!bigTID.hasPattern() && !BASE_TYPES.contains(bigTID.basePath()) && !bigTID.isGeneric() && Router.loaded()) {
            final Obj obj = Router.readFromSpace(bigTID);
            if (obj.isType()) {
                if (tid.cV().equals(obj.c()) &&
                        Objects.equals(obj.asType().predicate(), predicate) &&
                        Objects.equals(obj.asType().constructor(), constructor))
                    return obj.asType();
                else
                    return new MType(Tuple.Pair.with(
                            null == predicate || predicate.isNoObj() ? obj.asType().predicate() : predicate,
                            null == constructor || constructor.isNoObj() ? obj.asType().constructor() : constructor), obj.tid(), bigVID); // coefficient specific type doesn't exist, create it
            }
        }
      
        final MType result = new MType(Tuple.Pair.with(predicate, constructor), bigTID, bigVID);
        return result;

    }

    @Override
    public Type clone(final Object jvm, final fURI tid, final fURI vid) {
        // if (!tid.equals(vid))
        //     throw MTronException.of("a tid and vid of a type must be the same: %s != %s", tid, vid);
        return T((Tuple.Pair<Call, Call>) jvm, tid, vid);
    }

    @Override
    public Tuple.Pair<Call, Call> jvm() {
        return (Tuple.Pair<Call, Call>) this.jvm;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof Type && Objects.equals(this.vid(), ((Type) other).vid()) && Objects.equals(this.tid, ((Type) other).tid());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.tid(), this.jvm());
    }

}