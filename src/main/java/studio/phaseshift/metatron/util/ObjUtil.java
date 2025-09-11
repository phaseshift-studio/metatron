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

import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.BObj.NoObj;
import studio.phaseshift.metatron.lang.obj.SObj;
import studio.phaseshift.metatron.lang.obj.base.Obj;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.List;
import java.util.Objects;

public final class ObjUtil {

    private ObjUtil() {

    }

    public static BObj.Obj orNoObj(final BObj.Obj obj) {
        return null == obj ? NoObj.of() : obj;
    }

    public static boolean isLambda(final Object o) {
        return o == null || o.toString().contains("$$Lambda");
    }

    public static BObj.Obj oneNoneOrAll(final List<BObj.Obj> objs) {
        if (objs.isEmpty())
            return NoObj.of();
        else if (objs.size() == 1)
            return objs.get(0);
        else
            return SObj.Objs.of(objs);
    }

    public static int objHashCode(final Obj obj) {
        return Objects.hash(obj.value(), obj.tid());
    }

    public static boolean objEquals(final Obj obj, final Object other) {
        return other instanceof Obj && ((Obj) other).tid().equals(obj.tid()) && ((Obj) other).vid().equals(obj.vid()) && ((Obj) other).value().equals(obj.value());
    }

    public static String objToString(final Obj obj) {
        return Graphitty.string(obj);
    }
}
