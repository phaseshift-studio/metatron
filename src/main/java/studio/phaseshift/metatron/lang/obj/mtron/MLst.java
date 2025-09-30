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

package studio.phaseshift.metatron.lang.obj.mtron;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Lst;
import studio.phaseshift.metatron.lang.obj.Obj;

import java.util.List;

import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.LST_TID;

public class MLst extends MObj implements Lst {

    public static Lst lst(final Obj... objs) {
        return MLst.of(objs);
    }

    public static Lst lst(final List<Obj> objs) {
        return MLst.of(objs);
    }

    public MLst(final List<Obj> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public MLst(final List<Obj> value) {
        this(value, LST_TID, fURI.NULL);
    }

    @Override
    public Lst clone(final Object value, final fURI tid, final fURI vid) {
        return super.clone(value, tid, vid, (a, b, c) -> new MLst((List<Obj>) a, b, c));
    }

    @Override
    public List<Obj> value() {
        return (List<Obj>) this.value;
    }

    private static final Lst EMPTY_LST = new MLst(List.of(), LST_TID, fURI.NULL);

    public static Lst of(final Obj... args) {
        return args.length == 0 ? EMPTY_LST : new MLst(List.of(args));
    }

    public static Lst of(final List<Obj> args) {
       // return args.isEmpty() ? EMPTY_LST : new MLst(args);
        return new MLst(args);
    }

    public static Lst of(final List<Obj> args, final fURI tid) {
        // return args.isEmpty() ? EMPTY_LST : new MLst(args);
        return new MLst(args,tid,fURI.NULL);
    }
}
