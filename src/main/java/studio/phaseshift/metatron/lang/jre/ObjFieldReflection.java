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

package studio.phaseshift.metatron.lang.jre;

import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ObjFieldReflection {
    String tid() default "noobj";

    String typecast() default "";

    public static class Helper {
        private Helper() {
            // do nothing
        }

        public static <O extends Obj> O recAt(final Object source, final String key) {
            return (O) objs(Helper.findField(source, key).stream().map(f -> {
                final O temp = (O) JObjFactory.single().create(f, MTronException.wrap(() -> f.get(source)), null);
                return temp;
            }));
        }

        protected static List<Field> findField(final Object source, final String key) {
            if (source.getClass().getAnnotation(ObjReflection.class) == null)
                return List.of();
            if (null == key)
                return List.of();
            boolean allWildcard = key.endsWith("#");
            return Arrays.stream(source.getClass().getDeclaredFields()).filter(f -> f.getAnnotation(ObjFieldReflection.class) != null).filter(f -> allWildcard || f.getName().equals(key)).toList();
        }
    }
}
