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
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TID;

public class MLst extends MObj implements Lst {

    public static Lst lst(final Obj... objs) {
        return lst(Arrays.stream(objs).collect(Collectors.toCollection(ArrayList::new)), LST_TID,null);
    }

    public static Lst lst(final List<Obj> objs) {
        return lst(objs, LST_TID,null);
    }

    public static Lst lst(final List<Obj> objs, final fURI tid, final fURI vid) {
        return MObj.of(objs, tid, vid, Lst.class);
    }

    public static Lst lst0() {
        return EMPTY_LST;
    }

    public static Lst lst(final Stream<Obj> objs) {
        return lst((List<Obj>) objs.collect(Collectors.toCollection(ArrayList::new)));
    }

    public MLst(final List<Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, null == tid ? LST_TID : tid, vid);
    }

    @Override
    public Lst clone(final Object jvm, final fURI tid, final fURI vid) {
        return super.clone(jvm, tid, vid);
    }

    @Override
    public List<Obj> jvm() {
        return (List<Obj>) this.jvm;
    }

    @Override
    public Lst jvm(final Object jvm) {
        return this.clone(jvm, this.tid(), this.vid());
    }

    private static final Lst EMPTY_LST = new MLst(List.of(), LST_TID,null);

}
