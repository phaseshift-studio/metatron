/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.isa.m.type.reflect;

import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MStr;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface JDocs {

    String id();

    String name();

    String desc();

    String[] examples();

    public static class Helper {

        private Helper() {
            // do nothing
        }

        public static QCollection.Docs generateDocs(final Object metaObject) {
            if (metaObject instanceof Class<?> metaClass) {
                if (metaClass.isAnnotationPresent(JDocs.class)) {
                    Map<Obj, Obj> map = new LinkedHashMap<>();
                    JDocs jdocs = metaClass.getAnnotation(JDocs.class);
                    map.put(uri(NAME), str(jdocs.name()));
                    map.put(uri(DESC), str(jdocs.desc()));
                    map.put(uri(EXAMPLE), lst(Arrays.stream(jdocs.examples()).map(MStr::str)));
                    return new QCollection.Docs(map, QCollection.DOCS_TID, f(jdocs.id()));
                }
            } else if (metaObject instanceof Method metaMethod) {
                if (metaMethod.isAnnotationPresent(JDocs.class)) {
                    Map<Obj, Obj> map = new LinkedHashMap<>();
                }
            }
            return null;
        }
    }
}
