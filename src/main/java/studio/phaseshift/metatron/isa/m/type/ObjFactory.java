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

package studio.phaseshift.metatron.isa.m.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public interface ObjFactory extends Rec {

    final GraphittyLogger LOG = Graphitty.log(ObjFactory.class);

    <OBJ extends Obj> OBJ toObj(final Object value, final fURI tid, final fURI vid);

    <OBJ extends Obj> OBJ toObj(final Object value, final fURI tid, final fURI vid, final Class<OBJ> objClass);

    default Obj toObj(final Object value) {
        return this.toObj(value, null, null);
    }

    default Obj createOrFail(final Object value) {
        try {
            return this.toObj(value);
        } catch (final Exception e) {
            return fail(e);
        }
    }

    default <JVM, OBJ extends Obj> ObjFactory addExtension(final Class<JVM> objClass, final Function<JVM, OBJ> creator) {
        LOG.warn("extensions not supported for %s", this.getClass().getSimpleName());
        return this;
    }

    default <OBJ extends Obj> OBJ toObj(final Object value, final Class<OBJ> objClass) {
        fURI tid;
        if (null == value)
            tid = NOOBJ_TID;
        else if (Bool.class.isAssignableFrom(objClass))
            tid = BOOL_TID;
        else if (Int.class.isAssignableFrom(objClass))
            tid = INT_TID;
        else if (Real.class.isAssignableFrom(objClass))
            tid = REAL_TID;
        else if (Str.class.isAssignableFrom(objClass))
            tid = STR_TID;
        else if (Uri.class.isAssignableFrom(objClass))
            tid = URI_TID;
        else if (Lst.class.isAssignableFrom(objClass))
            tid = LST_TID;
        else if (Rel.class.isAssignableFrom(objClass))
            tid = REL_TID;
        else if (Rec.class.isAssignableFrom(objClass))
            tid = REC_TID;
        else if (Inst.class.isAssignableFrom(objClass))
            tid = INST_TID;
        else if (Code.class.isAssignableFrom(objClass))
            tid = CODE_TID;
        else if (Objs.class.isAssignableFrom(objClass))
            tid = OBJS_TID;
        else if (Type.class.isAssignableFrom(objClass))
            tid = TYPE_INST_TID;
        else if (Fail.class.isAssignableFrom(objClass))
            tid = FAIL_TID;
        else if (NoObj.class.isAssignableFrom(objClass))
            tid = fURI.NOOBJ;
        else
            throw MTronException.of("unable to convert to requested obj class: %s", objClass);
        return this.toObj(value, tid, fURI.fnull, objClass);
    }

    /// //////////////////////////////////////////////////////////////////////////////////////////////////////
    class Helper {
        public static String externalNameToMtronName(final String externalName) {
            String newName = externalName;
            if (newName.toUpperCase().equals(newName))
                newName = newName.toLowerCase();
            if (newName.startsWith("get") || newName.startsWith("has") || newName.startsWith("can"))
                newName = newName.substring(3);
            if (newName.startsWith("is"))
                newName = newName.substring(2);
            return newName
                    .replaceAll("([A-Z])(?=[A-Z])", "$1_")
                    .replaceAll("([a-z])([A-Z])", "$1_$2")
                    .toLowerCase();
        }

        public static boolean containsObjs(final Collection<Object> collection) {
            return collection.stream().allMatch(Helper::containsObjs);
        }

        public static boolean containsObjs(final Map<Object, Object> map) {
            return map.entrySet().stream().allMatch(e -> e.getKey() instanceof Obj && containsObjs(e.getValue()));
        }

        public static boolean containsObjs(final Object object) {
            if (object instanceof Collection)
                return containsObjs((Collection<Object>) object);
            else if (object instanceof Map)
                return containsObjs((Map<Object, Object>) object);
            else
                return object instanceof Obj;
        }

        public static boolean attemptReflection(final Class<?> clazz) {
            return clazz.isPrimitive() ||
                    String.class.isAssignableFrom(clazz) ||
                    URI.class.isAssignableFrom(clazz) ||
                    Character.class.isAssignableFrom(clazz) ||
                    Long.class.isAssignableFrom(clazz) ||
                    Integer.class.isAssignableFrom(clazz) ||
                    Double.class.isAssignableFrom(clazz) ||
                    Float.class.isAssignableFrom(clazz) ||
                    Boolean.class.isAssignableFrom(clazz) ||
                    Enum.class.isAssignableFrom(clazz) ||
                    Collection.class.isAssignableFrom(clazz) ||
                    Record.class.isAssignableFrom(clazz) ||
                    Map.class.isAssignableFrom(clazz);

        }


        public static Map<Obj, Obj> reflectionBasedCreate(final ObjFactory factory, final Object value) {
            final Map<Obj, Obj> map = new LinkedHashMap<>();
            Arrays.stream(value.getClass().getMethods())
                    .filter(m -> (m.getModifiers() & Modifier.PUBLIC) != 0)
                    .filter(m -> m.getParameterCount() == 0)
                    .filter(m -> !m.getName().equals("hashCode")
                            && (m.getName().startsWith("get") ||
                            m.getName().startsWith("is") ||
                            m.getName().startsWith("has") ||
                            m.getName().startsWith("can")))
                    .filter(m -> attemptReflection(m.getReturnType()))
                    .forEach(m -> {
                        try {
                            final Object result = m.invoke(value);
                            map.put(uri(Helper.externalNameToMtronName(m.getName())), factory.toObj(result));
                        } catch (final Exception e) {
                            throw MTronException.of(e);
                        }
                    });
            Arrays.stream(value.getClass().getFields())
                    .filter(f -> (f.getModifiers() & Modifier.PUBLIC) != 0)
                    .filter(f -> attemptReflection(f.getType()))
                    .forEach(f -> {
                        try {
                            final Object result = f.get(value);
                            map.put(uri(Helper.externalNameToMtronName(f.getName())), factory.toObj(result));
                        } catch (final Exception e) {
                            throw MTronException.of(e);
                        }
                    });
            return map;
        }
    }

}
