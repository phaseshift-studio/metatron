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

package studio.phaseshift.metatron.util;


import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.mtron.MObjs;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class ObjUtil {

    private ObjUtil() {

    }

    public static Obj orNoObj(final Obj obj) {
        return null == obj ? NoObj.single() : obj;
    }

    public static boolean isLambda(final Object o) {
        return o == null || o.toString().contains("$$Lambda");
    }

    public static Obj oneNoneOrAll(final List<Obj> objs) {
        if (objs.isEmpty())
            return NoObj.single();
        else if (objs.size() == 1)
            return objs.get(0);
        else
            return MObjs.of(objs);
    }

    public static Obj oneNoneOrAll(final Stream<Obj> objs) {
        return ObjUtil.oneNoneOrAll(objs.iterator());
    }

   /* public static Obj oneNoneOrAll(final Iterator<Obj> objs) {
        if (!objs.hasNext())
            return NoObj.single();
        final IteratorUtil.ExpandableIterator<Obj> itty = IteratorUtil.ExpandableIterator.of(objs);
        final Obj o = itty.next();
        if (!itty.hasNext())
            return o;
        itty.push(o);
        return MObjs.of(IteratorUtil.list(itty));
    } */

    public static Obj oneNoneOrAll(final Iterator<Obj> objs) {
        if (!objs.hasNext())
            return NoObj.single();
        List<Obj> o = IteratorUtil.list(objs);
        if (o.size() == 1)
            return o.get(0);
        else
            return MObjs.of(o);
    }

    public static int objHashCode(final Obj obj) {
        return Objects.hash(obj.value(), obj.tid());
    }

    public static boolean objEquals(final Obj obj, final Object other) {
        return other instanceof Obj &&
                Objects.equals(obj.tid(), ((Obj) other).tid()) &&
                Objects.equals(obj.vid(), ((Obj) other).vid()) &&
                Objects.equals(obj.value(), ((Obj) other).value());
    }

    public static String objToString(final Obj obj) {
        return Graphitty.string(obj);
    }

    public static boolean isNoObj(final Object object) {
        return null == object || object instanceof NoObj;
    }
}
