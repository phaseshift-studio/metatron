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

import java.util.Objects;

import static studio.phaseshift.metatron.isa.m.mInstSet.*;


public class MType extends MObj implements Type {

    protected MType(final Tuple.Pair<Call, Call> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid.big(), null == vid ? null : vid.big());
        if (Router.loaded() && null != this.vid() && (this.hasPredicate() || this.hasConstructor()) && !this.isBaseType() && !this.isGeneric() && !this.isPattern()) {
            Router.global().write(this.vid(), this);
        }
    }

    public static Type T(final Tuple.Pair<Call, Call> jvm, final fURI tid, final fURI vid) {
        return T(tid, vid, jvm.get0(), jvm.get1());
    }

    public static Type T(final fURI vid) {
        return T(null, vid, null, null);
    }

    public static Type T(final fURI vid, final Call predicate) {
        return T(null, vid, predicate, null);
    }

    public static Type T(final fURI tid, final fURI vid, final Call predicate, final Call constructor) {
        final fURI bigTID = null == tid ? vid.big() : tid.big();
        final fURI bigVID = null == vid ? null : vid.big();
        final fURI checkID = null == bigVID ? bigTID : bigVID;
        //System.out.println("CC: " + tid +"  "+ vid + "  "+ checkID);
        assert checkID != null;
        if (!checkID.basePath().equals(REL_TID) && !checkID.basePath().equals(LST_TID) && !checkID.basePath().equals(REC_TID) && !checkID.poly().isEmpty())
            throw MTronException.of("only poly types can have polynomials: %s {{r}}X=>{{X}} %s", checkID.basePath(), checkID.poly());
        if (!checkID.hasPattern() && !BASE_TYPES.contains(checkID.basePath()) && !checkID.isGeneric() && Router.loaded()) { // TODO: remove the pattern constraint - why not a type be the set of other types?
            Obj obj = Router.readFromSpace(checkID);
            obj = obj.selfTID(obj.tid().c(checkID.c()));
            if (obj.isType()) {
                if (checkID.c().equals(obj.c()) &&
                        Objects.equals(obj.asType().predicate(), predicate) &&
                        Objects.equals(obj.asType().constructor(), constructor))
                    return obj.asType();
                else
                    return new MType(Tuple.Pair.with(
                            null == predicate || predicate.isNoObj() ? obj.asType().predicate() : predicate,
                            null == constructor || constructor.isNoObj() ? obj.asType().constructor() : constructor), obj.tid(), obj.vid()).selfTID(obj.tid().c(checkID.c())).as(); // coefficient specific type doesn't exist, create it
            }
        }
        final boolean isBaseType = BASE_TYPES.contains(checkID.basePath());
        if (isBaseType || Objects.equals(bigVID, bigTID) || (null != vid && null != tid) || checkID.hasPattern() || checkID.isGeneric())
            return isBaseType ?
                    new MType(Tuple.Pair.with(predicate, constructor), bigTID, bigTID) :
                    new MType(Tuple.Pair.with(predicate, constructor), bigTID, bigVID);
        //throw MTronException.of("type not found: %s@%s", tid, vid); // TODO: a few cases fail --namely around equality checks. fix and then replace the bottom with this/
        return new MType(Tuple.Pair.with(predicate, constructor), null == bigTID ? checkID : bigTID, null == bigVID ? checkID : bigVID).c(checkID.c()).as();
    }

    @Override
    public Type clone(final Object jvm, final fURI tid, final fURI vid) {
        return T((Tuple.Pair<Call, Call>) jvm, tid, vid);
    }

    @Override
    public Tuple.Pair<Call, Call> jvm() {
        return (Tuple.Pair<Call, Call>) this.jvm;
    }

    @Override
    public boolean equals(final Object other) {
        //return other instanceof Type && (Objects.equals(this.vid(), ((Type) other).vid()) || (Objects.equals(this.tid, ((Type) other).tid()) && Objects.equals(this.jvm(), ((Type) other).jvm())));
        return other instanceof Type && Objects.equals(this.vid, ((Type) other).vid()) && Objects.equals(this.tid, ((Type) other).tid());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.tid(), this.jvm());
    }

}