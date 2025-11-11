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

package studio.phaseshift.metatron.lang.core.m.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Lst;
import studio.phaseshift.metatron.lang.core.m.type.Obj;

import java.util.List;

import static studio.phaseshift.metatron.lang.core.m.mInstSet.LST_TID;

public class MLst extends MObj implements Lst {

    public static Lst lst(final Obj... objs) {
        return MLst.of(objs);
    }

    public static Lst lst(final List<Obj> objs) {
        return MLst.of(objs);
    }

    public MLst(final List<Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public MLst(final List<Obj> jvm) {
        this(jvm, LST_TID, fURI.NULL);
    }

    @Override
    public Lst clone(final Object jvm, final fURI tid, final fURI vid) {
        return (Lst) super.clone(jvm, tid, vid);
    }

    @Override
    public List<Obj> jvm() {
        return (List<Obj>) this.jvm;
    }

    private static final Lst EMPTY_LST = new MLst(List.of(), LST_TID, fURI.NULL);

    public static Lst of(final Obj... args) {
        return args.length == 0 ? EMPTY_LST : new MLst(List.of(args));
    }

    public static Lst of(final List<Obj> objs) {
        return new MLst(objs);
    }

    public static Lst of(final List<Obj> objs, final fURI tid) {
        return new MLst(objs, tid, fURI.NULL);
    }
}
