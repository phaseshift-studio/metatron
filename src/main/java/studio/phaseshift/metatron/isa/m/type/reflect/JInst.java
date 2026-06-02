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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.MTronException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface JInst {
    String tid();

    String dom() default "#";

    String rng() default "#";

    Attach attach();

    public static enum Attach {
        TYPE,
        OBJ
    }

    public static class Helper {
        private Helper() {
            // do nothing
        }

        public static void processInst(final Object source) {
            Arrays.stream(source.getClass().getMethods())
                    .filter(m -> m.isAnnotationPresent(JInst.class))
                    .filter(m -> m.getAnnotation(JInst.class).attach() == Attach.OBJ)
                    .forEach(m -> {
                        final JInst jinst = m.getAnnotation(JInst.class);
                        if (source instanceof Rec sourceRec) {
                            final fURI jrecKey = f(jinst.tid());
                            final fURI jinstTID = sourceRec.vid() == null ? f(jinst.tid()) : sourceRec.vid().extend(jrecKey);
                            sourceRec.at(uri(jrecKey), instC(
                                    jinstTID.dom(f(jinst.dom())).rng(f(jinst.rng())),
                                    m.getParameterCount() == 0 ? lst() : lst(T(ALL), INST_TYPE),
                                    (lhs, inst) -> {
                                        try {
                                            if (m.getParameterCount() == 0)
                                                return (Obj) m.invoke(source);
                                            else if(m.getParameterCount() == 1)
                                                return (Obj) m.invoke(source, inst.arg(0));
                                            return (Obj) m.invoke(source, lhs, inst.args());
                                        } catch (final Exception e) {
                                            throw MTronException.of(e);
                                        }
                                    }), MUTABLE);
                        }
                    });
        }
    }
}
